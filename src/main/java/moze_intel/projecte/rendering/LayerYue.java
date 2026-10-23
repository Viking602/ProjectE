package moze_intel.projecte.rendering;

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
