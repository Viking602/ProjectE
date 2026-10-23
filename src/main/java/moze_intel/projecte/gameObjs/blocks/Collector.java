package moze_intel.projecte.gameObjs.blocks;

import java.util.List;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.capabilities.item.IItemEmcHolder;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.EnumCollectorTier;
import moze_intel.projecte.gameObjs.block_entities.CollectorMK1BlockEntity;
import moze_intel.projecte.gameObjs.registration.impl.BlockEntityTypeRegistryObject;
import moze_intel.projecte.gameObjs.registries.PEBlockEntityTypes;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.MathUtils;
import moze_intel.projecte.utils.WorldHelper;
import moze_intel.projecte.utils.text.PELang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Collector extends BlockDirection implements PEEntityBlock<CollectorMK1BlockEntity> {

	private final EnumCollectorTier tier;

	public Collector(EnumCollectorTier tier, Properties props) {
		super(props);
		this.tier = tier;
	}

	public EnumCollectorTier getTier() {
		return tier;
	}

	@NotNull
	@Override
	@Deprecated
	protected InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		CollectorMK1BlockEntity collector = WorldHelper.getBlockEntity(CollectorMK1BlockEntity.class, level, pos, true);
		if (collector != null) {
			player.openMenu(collector, pos);
		}
		return InteractionResult.CONSUME;
	}

	public void appendHoverText(@NotNull ItemStack stack, @NotNull Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, @NotNull TooltipFlag flags) {
		if (ProjectEConfig.client.statToolTips.get()) {
			tooltip.accept(PELang.EMC_MAX_GEN_RATE.translateColored(ChatFormatting.DARK_PURPLE, ChatFormatting.BLUE, EMCHelper.formatEmc(tier.getGenRate())));
			tooltip.accept(PELang.EMC_MAX_STORAGE.translateColored(ChatFormatting.DARK_PURPLE, ChatFormatting.BLUE, EMCHelper.formatEmc(tier.getStorage())));
		}
	}

	@Nullable
	@Override
	public BlockEntityTypeRegistryObject<? extends CollectorMK1BlockEntity> getType() {
		return switch (tier) {
			case MK1 -> PEBlockEntityTypes.COLLECTOR;
			case MK2 -> PEBlockEntityTypes.COLLECTOR_MK2;
			case MK3 -> PEBlockEntityTypes.COLLECTOR_MK3;
		};
	}

	@Override
	@Deprecated
	public boolean triggerEvent(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, int id, int param) {
		super.triggerEvent(state, level, pos, id, param);
		return triggerBlockEntityEvent(state, level, pos, id, param);
	}

	@Override
	@Deprecated
	public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
		return true;
	}

	@Override
	@Deprecated
	public int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, net.minecraft.core.Direction direction) {
		CollectorMK1BlockEntity collector = WorldHelper.getBlockEntity(CollectorMK1BlockEntity.class, level, pos, true);
		if (collector == null) {
			//If something went wrong fallback to default implementation
			return super.getAnalogOutputSignal(state, level, pos, direction);
		}
		IItemHandler handler = moze_intel.projecte.utils.ItemTransferAdapter.legacy(WorldHelper.getCapability(level, Capabilities.Item.BLOCK, pos, state, collector, Direction.UP));
		if (handler == null) {
			//If something went wrong fallback to default implementation
			return super.getAnalogOutputSignal(state, level, pos, direction);
		}
		ItemStack charging = handler.getStackInSlot(CollectorMK1BlockEntity.UPGRADING_SLOT);
		if (charging.isEmpty()) {
			return MathUtils.scaleToRedstone(collector.getStoredEmc(), collector.getMaximumEmc());
		}
		IItemEmcHolder emcHolder = charging.getCapability(PECapabilities.EMC_HOLDER_ITEM_CAPABILITY);
		if (emcHolder != null) {
			return MathUtils.scaleToRedstone(emcHolder.getStoredEmc(charging), emcHolder.getMaximumEmc(charging));
		}
		return MathUtils.scaleToRedstone(collector.getStoredEmc(), collector.getEmcToNextGoal());
	}

}