package moze_intel.projecte.rendering;

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
