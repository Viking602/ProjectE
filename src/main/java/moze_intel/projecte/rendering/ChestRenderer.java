package moze_intel.projecte.rendering;

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
