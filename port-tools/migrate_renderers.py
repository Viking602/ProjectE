from pathlib import Path
root=Path('src/main/java/moze_intel/projecte')
def write(path,s):(root/path).write_text(s,encoding='utf-8')
write(Path('utils/NovaExplosion.java'),'''package moze_intel.projecte.utils;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class NovaExplosion extends ServerExplosion {
    private final List<BlockPos> particlePositions = new ArrayList<>();

    public NovaExplosion(ServerLevel level, @Nullable Entity entity, double x, double y, double z, float radius, Explosion.BlockInteraction mode) {
        super(level, entity, null, null, new Vec3(x, y, z), radius, false, mode);
    }

    @Override
    public void interactWithBlocks(List<BlockPos> positions) {
        NonNullList<ItemStack> drops = NonNullList.create();
        Util.shuffle(positions, level().getRandom());
        for (BlockPos pos : positions) {
            var state = level().getBlockState(pos);
            if (!state.isAir()) particlePositions.add(pos.immutable());
            state.onExplosionHit(level(), pos, this, (stack, position) -> drops.add(stack));
        }
        var placer = getIndirectSourceEntity();
        WorldHelper.createLootDrop(drops, level(), placer == null ? center() : placer.position());
    }

    public List<BlockPos> particlePositions() { return List.copyOf(particlePositions); }
}
''')
write(Path('rendering/PERenderType.java'),'''package moze_intel.projecte.rendering;

import java.util.function.Function;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.resources.Identifier;

public final class PERenderType {
    private PERenderType() {}
    public static final Function<Identifier, RenderType> SPRITE_RENDERER = RenderTypes::entityCutout;
    public static final Function<Identifier, RenderType> YEU_RENDERER = RenderTypes::entityTranslucentEmissive;
    public static final RenderType TRANSMUTATION_OVERLAY = RenderType.create("projecte_transmutation_overlay",
            RenderSetup.builder(RenderPipelines.DEBUG_QUADS).sortOnUpload()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).createRenderSetup());
}
''')
write(Path('rendering/EntitySpriteRenderer.java'),'''package moze_intel.projecte.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

public class EntitySpriteRenderer<E extends Entity> extends EntityRenderer<E, EntityRenderState> {
    private final RenderType renderType;
    public EntitySpriteRenderer(EntityRendererProvider.Context context, Identifier texture) {
        super(context);
        renderType = PERenderType.SPRITE_RENDERER.apply(texture);
    }
    @Override protected int getBlockLightLevel(E entity, BlockPos pos) { return 15; }
    @Override public EntityRenderState createRenderState() { return new EntityRenderState(); }
    @Override
    public void submit(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.scale(0.5F, 0.5F, 0.5F);
        pose.mulPose(camera.orientation);
        collector.submitCustomGeometry(pose, renderType, (matrix, buffer) -> {
            vertex(buffer, matrix, state.lightCoords, 0, 0, 0, 1);
            vertex(buffer, matrix, state.lightCoords, 1, 0, 1, 1);
            vertex(buffer, matrix, state.lightCoords, 1, 1, 1, 0);
            vertex(buffer, matrix, state.lightCoords, 0, 1, 0, 0);
        });
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }
    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, int light, float x, float y, float u, float v) {
        buffer.addVertex(pose, x - 0.5F, y, 0).setColor(-1).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
    }
}
''')
write(Path('rendering/ChestRenderer.java'),'''package moze_intel.projecte.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import moze_intel.projecte.gameObjs.block_entities.EmcChestBlockEntity;
import moze_intel.projecte.gameObjs.registration.impl.BlockRegistryObject;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class ChestRenderer implements BlockEntityRenderer<EmcChestBlockEntity, ChestRenderState> {
    private final ChestModel model;
    private final Identifier texture;
    public ChestRenderer(BlockEntityRendererProvider.Context context, Identifier texture, BlockRegistryObject<?, ?> type) {
        model = new ChestModel(context.bakeLayer(ModelLayers.CHEST));
        this.texture = texture;
    }
    @Override public ChestRenderState createRenderState() { return new ChestRenderState(); }
    @Override
    public void extractRenderState(EmcChestBlockEntity chest, ChestRenderState state, float partialTick, Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(chest, state, partialTick, cameraPos, overlay);
        state.facing = chest.getBlockState().getOptionalValue(BlockStateProperties.HORIZONTAL_FACING).orElse(Direction.NORTH);
        state.open = chest.getOpenNess(partialTick);
    }
    @Override
    public void submit(ChestRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.mulPose(net.minecraft.client.renderer.blockentity.ChestRenderer.modelTransformation(state.facing));
        float closed = 1 - state.open;
        collector.submitModel(model, 1 - closed * closed * closed, pose, texture, state.lightCoords,
                OverlayTexture.NO_OVERLAY, 0, state.breakProgress);
        pose.popPose();
    }
}
''')
write(Path('rendering/PedestalRenderer.java'),'''package moze_intel.projecte.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import moze_intel.projecte.gameObjs.block_entities.DMPedestalBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;

public class PedestalRenderer implements BlockEntityRenderer<DMPedestalBlockEntity, PedestalRenderer.State> {
    public static class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        float time;
        @Nullable AABB bounds;
    }
    private final ItemModelResolver items;
    public PedestalRenderer(BlockEntityRendererProvider.Context context) { items = context.itemModelResolver(); }
    private static boolean showBounds() { return Minecraft.getInstance().debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES); }
    @Override public State createRenderState() { return new State(); }
    @Override
    public void extractRenderState(DMPedestalBlockEntity pedestal, State state, float partialTick, Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(pedestal, state, partialTick, cameraPos, overlay);
        var pos = pedestal.getBlockPos();
        state.bounds = showBounds() ? pedestal.getEffectBounds().move(-pos.getX(), -pos.getY(), -pos.getZ()) : null;
        state.time = pedestal.getLevel() == null ? 0 : (pedestal.getLevel().getGameTime() % 100000L) + partialTick;
        items.updateForTopItem(state.item, pedestal.getInventory().getStackInSlot(0), ItemDisplayContext.GROUND,
                pedestal.getLevel(), null, (int) pos.asLong());
    }
    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.bounds != null) {
            var shape = Shapes.create(state.bounds);
            // Capture an independent pose for the deferred render callback.
            PoseStack linesPose = new PoseStack();
            linesPose.mulPose(pose.last().pose());
            collector.submitCustomGeometry(pose, RenderTypes.lines(), (matrix, buffer) ->
                    ShapeRenderer.renderShape(linesPose, buffer, shape, 0, 0, 0, 0xFFFF00FF, 1));
        }
        pose.pushPose();
        pose.translate(0.5, 0.8 + Mth.sin(state.time / 10) * 0.1, 0.5);
        pose.scale(0.75F, 0.75F, 0.75F);
        pose.mulPose(Axis.YP.rotation(state.time / 20));
        state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
    @Override public AABB getRenderBoundingBox(DMPedestalBlockEntity pedestal) {
        return showBounds() ? pedestal.getEffectBounds() : BlockEntityRenderer.super.getRenderBoundingBox(pedestal);
    }
}
''')
write(Path('rendering/LayerYue.java'),'''package moze_intel.projecte.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.UUID;
import moze_intel.projecte.PECore;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

public class LayerYue extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final UUID SIN_UUID = UUID.fromString("5f86012c-ca4b-451a-989c-8fab167af647");
    private static final UUID CLAR_UUID = UUID.fromString("e5c59746-9cf7-4940-a849-d09e1f1efc13");
    private static final Identifier HEART = PECore.rl("textures/models/heartcircle.png");
    private static final Identifier YUE = PECore.rl("textures/models/yuecircle.png");
    private static final ContextKey<Identifier> TEXTURE = new ContextKey<>(PECore.rl("yue_texture"));
    public LayerYue(AvatarRenderer<?> renderer) { super(renderer); }
    public static void registerState(RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override public <T extends net.minecraft.world.entity.Avatar & net.minecraft.client.entity.ClientAvatarEntity>
                    void accept(T avatar, AvatarRenderState state) {
                UUID id = avatar.getUUID();
                state.setRenderData(TEXTURE, !FMLEnvironment.isProduction() || SIN_UUID.equals(id) || CLAR_UUID.equals(id)
                        ? (CLAR_UUID.equals(id) ? HEART : YUE) : null);
            }
        });
    }
    @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yaw, float pitch) {
        Identifier texture = state.getRenderData(TEXTURE);
        if (state.isInvisible || texture == null) return;
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        double shift = -0.498;
        if (state.isCrouching) { pose.mulPose(Axis.XN.rotation(0.5F)); shift = -0.44; }
        pose.mulPose(Axis.ZP.rotation(Mth.PI));
        pose.scale(3, 3, 3);
        pose.translate(-0.5, shift, -0.5);
        collector.submitCustomGeometry(pose, PERenderType.YEU_RENDERER.apply(texture), (matrix, buffer) -> {
            float[][] points = {{0,0},{0,1},{1,1},{1,0}};
            for (float[] p : points) buffer.addVertex(matrix, p[0], 0, p[1]).setUv(p[0],p[1]).setColor(0,255,0,255)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(matrix,0,1,0);
        });
        pose.popPose();
    }
}
''')
p=root/'emc/components/processor/ArmorTrimProcessor.java';s=p.read_text(encoding='utf-8').replace('api.proxy.IComponentProcessorHelper','api.components.IComponentProcessorHelper');p.write_text(s,encoding='utf-8')
