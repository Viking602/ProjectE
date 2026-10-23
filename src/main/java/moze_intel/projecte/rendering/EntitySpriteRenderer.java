package moze_intel.projecte.rendering;

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
