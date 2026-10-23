package moze_intel.projecte.utils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

/** Transactional boundary around ProjectE's existing, sided inventory rules. */
public final class ItemTransferAdapter implements ResourceHandler<ItemResource> {
    private static final Map<IItemHandler, WeakReference<ItemTransferAdapter>> ADAPTERS = new WeakHashMap<>();
    private static final Map<IItemHandlerModifiable, WeakReference<InventoryJournal>> JOURNALS = new WeakHashMap<>();
    private final IItemHandlerModifiable inventory;
    private final List<InventoryJournal> journals;

    private ItemTransferAdapter(IItemHandlerModifiable inventory) {
        this.inventory = inventory;
        var backing = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<IItemHandlerModifiable, Boolean>());
        findBacking(inventory, backing);
        journals = backing.stream().map(ItemTransferAdapter::journal).toList();
    }

    private static void findBacking(IItemHandlerModifiable inventory, java.util.Set<IItemHandlerModifiable> result) {
        if (inventory instanceof moze_intel.projecte.gameObjs.block_entities.WrappedItemHandler wrapped) {
            findBacking(wrapped.backingInventory(), result);
        } else if (inventory instanceof CombinedInvWrapper combined) {
            combined.backingInventories().forEach(child -> findBacking(child, result));
        } else {
            result.add(inventory);
        }
    }

    private static InventoryJournal journal(IItemHandlerModifiable inventory) {
        var reference = JOURNALS.get(inventory);
        var journal = reference == null ? null : reference.get();
        if (journal == null) {
            journal = new InventoryJournal(inventory);
            JOURNALS.put(inventory, new WeakReference<>(journal));
        }
        return journal;
    }

    private void updateSnapshots(TransactionContext transaction) {
        journals.forEach(journal -> journal.snapshot(transaction));
    }

    public static synchronized @Nullable ItemTransferAdapter of(@Nullable IItemHandler inventory) {
        if (inventory == null) return null;
        var ref = ADAPTERS.get(inventory);
        var adapter = ref == null ? null : ref.get();
        if (adapter == null) {
            if (!(inventory instanceof IItemHandlerModifiable writable)) {
                throw new IllegalArgumentException("ProjectE inventory must support rollback");
            }
            adapter = new ItemTransferAdapter(writable);
            ADAPTERS.put(inventory, new WeakReference<>(adapter));
        }
        return adapter;
    }

    public static @Nullable IItemHandler legacy(@Nullable ResourceHandler<ItemResource> handler) {
        // Preserve direct setters for our component-backed menu slots during client synchronization.
        if (handler instanceof ItemTransferAdapter adapter) return adapter.inventory;
        return handler == null ? null : IItemHandler.of(handler);
    }

    @Override public int size() { return inventory.getSlots(); }
    @Override public ItemResource getResource(int index) { return ItemResource.of(inventory.getStackInSlot(index)); }
    @Override public long getAmountAsLong(int index) { return inventory.getStackInSlot(index).getCount(); }
    @Override public long getCapacityAsLong(int index, ItemResource resource) {
        return resource.isEmpty() ? inventory.getSlotLimit(index) : Math.min(inventory.getSlotLimit(index), resource.getMaxStackSize());
    }
    @Override public boolean isValid(int index, ItemResource resource) { return !resource.isEmpty() && inventory.isItemValid(index, resource.toStack()); }
    @Override public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (amount == 0) return 0;
        int accepted = amount - inventory.insertItem(index, resource.toStack(amount), true).getCount();
        if (accepted == 0) return 0;
        updateSnapshots(transaction);
        return amount - inventory.insertItem(index, resource.toStack(amount), false).getCount();
    }
    @Override public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (amount == 0 || !resource.matches(inventory.getStackInSlot(index))) return 0;
        int available = inventory.extractItem(index, amount, true).getCount();
        if (available == 0) return 0;
        updateSnapshots(transaction);
        return inventory.extractItem(index, amount, false).getCount();
    }
    private static final class InventoryJournal extends SnapshotJournal<List<ItemStack>> {
        private final IItemHandlerModifiable inventory;
        private InventoryJournal(IItemHandlerModifiable inventory) { this.inventory = inventory; }
        private void snapshot(TransactionContext transaction) { updateSnapshots(transaction); }
        @Override protected List<ItemStack> createSnapshot() {
            List<ItemStack> result = new ArrayList<>(inventory.getSlots());
            for (int i = 0; i < inventory.getSlots(); i++) result.add(inventory.getStackInSlot(i).copy());
            return result;
        }
        @Override protected void revertToSnapshot(List<ItemStack> snapshot) {
            for (int i = 0; i < snapshot.size(); i++) inventory.setStackInSlot(i, snapshot.get(i));
        }
    }
}
