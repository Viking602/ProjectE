package moze_intel.projecte.port;

import moze_intel.projecte.gameObjs.block_entities.WrappedItemHandler;
import moze_intel.projecte.utils.ItemTransferAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransferCompatibilityTest {
    @Test void simulationDoesNotConsumeOrDuplicateItems() {
        var inventory = new ItemStackHandler(1);
        inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND, 8));
        var handler = ItemTransferAdapter.of(inventory);
        try (var tx = Transaction.openRoot()) {
            assertEquals(3, handler.extract(0, ItemResource.of(Items.DIAMOND), 3, tx));
            assertEquals(5, inventory.getStackInSlot(0).getCount());
        }
        assertEquals(8, inventory.getStackInSlot(0).getCount());
        try (var tx = Transaction.openRoot()) {
            assertEquals(56, handler.insert(0, ItemResource.of(Items.DIAMOND), 64, tx));
            tx.commit();
        }
        assertEquals(64, inventory.getStackInSlot(0).getCount());
    }

    @Test void nestedRollbackAcrossSidedViewsRestoresSharedInventory() {
        var inventory = new ItemStackHandler(1);
        inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND, 8));
        var input = ItemTransferAdapter.of(new WrappedItemHandler(inventory, WrappedItemHandler.WriteMode.IN));
        var output = ItemTransferAdapter.of(new WrappedItemHandler(inventory, WrappedItemHandler.WriteMode.OUT));
        var diamond = ItemResource.of(Items.DIAMOND);
        try (var outer = Transaction.openRoot()) {
            assertEquals(4, input.insert(0, diamond, 4, outer));
            try (var inner = Transaction.open(outer)) {
                assertEquals(9, output.extract(0, diamond, 9, inner));
                inner.commit();
            }
            assertEquals(3, inventory.getStackInSlot(0).getCount());
        }
        assertEquals(8, inventory.getStackInSlot(0).getCount());
    }

    @Test void sidedRestrictionsAndLegacySimulationArePreserved() {
        var inventory = new ItemStackHandler(1);
        inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND, 8));
        var handler = ItemTransferAdapter.of(new WrappedItemHandler(inventory, WrappedItemHandler.WriteMode.OUT));
        try (var tx = Transaction.openRoot()) {
            assertEquals(0, handler.insert(0, ItemResource.of(Items.DIAMOND), 1, tx));
            assertEquals(0, handler.extract(0, ItemResource.of(Items.EMERALD), 1, tx));
            tx.commit();
        }
        var legacy = ItemTransferAdapter.legacy(handler);
        assertEquals(5, legacy.extractItem(0, 5, true).getCount());
        assertEquals(8, inventory.getStackInSlot(0).getCount());
        assertEquals(5, legacy.extractItem(0, 5, false).getCount());
        assertEquals(3, inventory.getStackInSlot(0).getCount());
    }
}
