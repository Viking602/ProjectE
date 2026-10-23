package moze_intel.projecte.utils;

import java.util.List;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Exposes shared backing inventories so all sided views use the same transaction journal. */
public class CombinedInvWrapper extends net.neoforged.neoforge.items.wrapper.CombinedInvWrapper {
    public CombinedInvWrapper(IItemHandlerModifiable... inventories) { super(inventories); }
    public List<IItemHandlerModifiable> backingInventories() { return List.of(itemHandler); }
}
