package moze_intel.projecte.rendering;

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
