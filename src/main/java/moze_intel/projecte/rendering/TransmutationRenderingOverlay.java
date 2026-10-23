package moze_intel.projecte.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.items.PhilosophersStone;
import moze_intel.projecte.gameObjs.items.PhilosophersStone.PhilosophersStoneMode;
import moze_intel.projecte.gameObjs.registries.PEItems;
import moze_intel.projecte.utils.Constants;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TransmutationRenderingOverlay implements GuiLayer {

	private final Minecraft mc = Minecraft.getInstance();
	@Nullable
	private Block transmutationResult;
	private long lastGameTime;

	public TransmutationRenderingOverlay() {
		NeoForge.EVENT_BUS.addListener(this::onOverlay);
	}

	@Override
	public void render(@NotNull GuiGraphicsExtractor graphics, @NotNull DeltaTracker delta) {
		if (!mc.options.hideGui && transmutationResult != null) {
			if (transmutationResult instanceof LiquidBlock liquidBlock) {
				var model = mc.getModelManager().getFluidStateModelSet().get(liquidBlock.fluid.defaultFluidState());
				int color = model.fluidTintSource() == null ? -1 : model.fluidTintSource().colorAsStack(new net.neoforged.neoforge.fluids.FluidStack(liquidBlock.fluid, 1000));
				float red = (color >> 16 & 0xFF) / 255.0F;
				float green = (color >> 8 & 0xFF) / 255.0F;
				float blue = (color & 0xFF) / 255.0F;
				float alpha = (color >> 24 & 0xFF) / 255.0F;
				TextureAtlasSprite sprite = model.stillMaterial().sprite();
				graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite, 1, 1, 16, 16, color);
			} else {
				//Just render it normally instead of with the given model as some block's don't render properly then as an item
				// for example glass panes
				graphics.item(new ItemStack(transmutationResult), 1, 1);
			}
			long gameTime = mc.level == null ? 0 : mc.level.getGameTime();
			if (lastGameTime != gameTime) {
				//If the game time changed, so we aren't actually still hovering a block set our
				// result to null. We do this after rendering it just in case there is a single
				// frame where this may actually be valid based on the order the events are fired
				transmutationResult = null;
				lastGameTime = gameTime;
			}
		}
	}

	private void onOverlay(ExtractBlockOutlineRenderStateEvent event) {
		Camera activeRenderInfo = event.getCamera();
		if (!(activeRenderInfo.entity() instanceof Player player)) {
			return;
		}
		Level level = player.level();
		lastGameTime = level.getGameTime();
		ItemStack stack = player.getMainHandItem();
		if (stack.isEmpty()) {
			stack = player.getOffhandItem();
		}
		if (stack.isEmpty() || !stack.is(PEItems.PHILOSOPHERS_STONE)) {
			transmutationResult = null;
			return;
		}
		boolean isSneaking = player.isSecondaryUseActive();
		PhilosophersStone philoStone = (PhilosophersStone) stack.getItem();
		//Note: We use the philo stone's ray trace instead of the event's ray trace as we want to make sure that we
		// can properly take fluid into account/ignore it when needed
		BlockHitResult rtr = philoStone.getHitBlock(level, player, isSneaking);
		if (rtr.getType() == HitResult.Type.BLOCK) {
			int charge = philoStone.getCharge(stack);
			PhilosophersStoneMode mode = philoStone.getMode(stack);
			Object2ReferenceMap<BlockPos, BlockState> changes = PhilosophersStone.getChanges(level, rtr.getBlockPos(), rtr.getDirection(), player.getDirection(),
					isSneaking, mode, charge);
			if (changes.isEmpty()) {
				transmutationResult = null;
			} else {
				transmutationResult = changes.values().iterator().next().getBlock();
				Vec3 viewPosition = activeRenderInfo.position();
				float alpha = ProjectEConfig.client.pulsatingOverlay.get() ? getPulseProportion() * 0.60F : 0.35F;
                java.util.List<net.minecraft.world.phys.AABB> boxes = new java.util.ArrayList<>();
                CollisionContext selectionContext = CollisionContext.of(player);
                for (BlockPos pos : changes.keySet()) {
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir()) state.getShape(level, pos, selectionContext).forAllBoxes((x1,y1,z1,x2,y2,z2) ->
                            boxes.add(new net.minecraft.world.phys.AABB(x1,y1,z1,x2,y2,z2).move(
                                    pos.getX() - viewPosition.x, pos.getY() - viewPosition.y, pos.getZ() - viewPosition.z)));
                }
                var snapshot = java.util.List.copyOf(boxes);
                event.addCustomRenderer((outline, buffers, pose, translucent, renderState) -> {
                    VertexConsumer builder = buffers.getBuffer(PERenderType.TRANSMUTATION_OVERLAY);
                    for (var box : snapshot) drawBox(pose.last(), builder, box, alpha);
                    return false;
                });
			}
		} else {
			transmutationResult = null;
		}
	}

    private static void drawBox(PoseStack.Pose pose, VertexConsumer buffer, net.minecraft.world.phys.AABB b, float alpha) {
        float x=(float)b.minX, y=(float)b.minY, z=(float)b.minZ, X=(float)b.maxX, Y=(float)b.maxY, Z=(float)b.maxZ;
        float[][] vertices = {
            {x,y,z},{X,y,z},{X,Y,z},{x,Y,z}, {x,y,Z},{x,Y,Z},{X,Y,Z},{X,y,Z},
            {x,y,z},{x,Y,z},{x,Y,Z},{x,y,Z}, {X,y,z},{X,y,Z},{X,Y,Z},{X,Y,z},
            {x,y,z},{x,y,Z},{X,y,Z},{X,y,z}, {x,Y,z},{X,Y,z},{X,Y,Z},{x,Y,Z}
        };
        for (float[] v : vertices) buffer.addVertex(pose,v[0],v[1],v[2]).setColor(1F,1F,1F,alpha);
    }

	private float getPulseProportion() {
		return (float) (0.5F * Math.sin(System.currentTimeMillis() / 350.0) + 0.5F);
	}
}
