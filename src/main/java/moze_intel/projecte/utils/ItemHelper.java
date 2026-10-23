package moze_intel.projecte.utils;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Helpers for Inventories, ItemStacks, Items, and the Ore Dictionary Notice: Please try to keep methods tidy and alphabetically ordered. Thanks!
 */
public final class ItemHelper {
    public static ItemStack craftingRemainder(ItemStack stack) {
        var remainder = stack.getCraftingRemainder();
        return remainder == null ? ItemStack.EMPTY : remainder.create();
    }
    public static ItemStack asStack(net.minecraft.world.item.ItemInstance instance) {
        return switch (instance) {
            case ItemStack stack -> stack;
            case net.minecraft.world.item.ItemStackTemplate template -> template.create();
            default -> new ItemStack(instance.typeHolder());
        };
    }
    public static java.util.List<ItemStack> armor(net.minecraft.world.entity.LivingEntity entity) {
        return java.util.List.of(entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD),
                entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST),
                entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS),
                entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
    }


	/**
	 * Gets an ActionResult based on a type
	 */
	public static InteractionResult actionResultFromType(InteractionResult type, ItemStack stack) {
		return type;
	}

	/**
	 * Compacts an inventory and returns if the inventory is/was empty.
	 *
	 * @return True if the inventory was empty.
	 */
	public static boolean compactInventory(IItemHandlerModifiable inventory) {
		List<ItemStack> temp = new ArrayList<>();
		for (int i = 0, slots = inventory.getSlots(); i < slots; i++) {
			ItemStack stackInSlot = inventory.getStackInSlot(i);
			if (!stackInSlot.isEmpty()) {
				temp.add(stackInSlot);
				inventory.setStackInSlot(i, ItemStack.EMPTY);
			}
		}
		for (ItemStack s : temp) {
			ItemHandlerHelper.insertItemStacked(inventory, s, false);
		}
		return temp.isEmpty();
	}

	public static IItemHandlerModifiable immutableCopy(IItemHandler toCopy) {
		int slots = toCopy.getSlots();
		final List<ItemStack> list = new ArrayList<>(slots);
		for (int i = 0; i < slots; i++) {
			list.add(toCopy.getStackInSlot(i).copy());
		}
		return new IItemHandlerModifiable() {
			@Override
			public void setStackInSlot(int slot, @NotNull ItemStack stack) {
			}

			@Override
			public int getSlots() {
				return list.size();
			}

			@NotNull
			@Override
			public ItemStack getStackInSlot(int slot) {
				return list.get(slot);
			}

			@NotNull
			@Override
			public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
				return stack;
			}

			@NotNull
			@Override
			public ItemStack extractItem(int slot, int amount, boolean simulate) {
				return ItemStack.EMPTY;
			}

			@Override
			public int getSlotLimit(int slot) {
				return getStackInSlot(slot).getMaxStackSize();
			}

			@Override
			public boolean isItemValid(int slot, @NotNull ItemStack stack) {
				return true;
			}
		};
	}

	public static boolean isRepairableDamagedItem(ItemStack stack) {
		return stack.isDamageableItem() && stack.has(net.minecraft.core.component.DataComponents.REPAIRABLE) && stack.getDamageValue() > 0;
	}

	/**
	 * @return The amount of the given stack that could not fit. If it all fit, zero is returned
	 */
	public static int simulateFit(NonNullList<ItemStack> inv, ItemStack stack) {
		int remainder = stack.getCount();
		for (ItemStack invStack : inv) {
			if (invStack.isEmpty()) {
				//Slot is empty, just put it all there
				return 0;
			}
			if (ItemStack.isSameItemSameComponents(stack, invStack)) {
				int amountSlotNeeds = invStack.getMaxStackSize() - invStack.getCount();
				//Double check we don't have an over sized stack
				if (amountSlotNeeds > 0) {
					if (remainder <= amountSlotNeeds) {
						//If the slot can accept it all, return it all fit
						return 0;
					}
					//Otherwise take that many items out and
					remainder -= amountSlotNeeds;
				}
			}
		}
		return remainder;
	}

	public static ItemStack size(ItemStack stack, int size) {
		if (size <= 0 || stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return stack.copyWithCount(size);
	}

	public static BlockState stackToState(ItemStack stack, @Nullable BlockPlaceContext context) {
		if (stack.getItem() instanceof BlockItem blockItem) {
			if (context == null) {
				return blockItem.getBlock().defaultBlockState();
			}
			return blockItem.getBlock().getStateForPlacement(context);
		}
		return null;
	}
}
