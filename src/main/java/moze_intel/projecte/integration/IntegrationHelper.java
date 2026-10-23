package moze_intel.projecte.integration;

import moze_intel.projecte.integration.curios.CurioItemCapability;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;

public class IntegrationHelper {

	public static final String CURIO_MODID = "curios";
	public static final String EMI_MODID = "emi";
	public static final String TOP_MODID = "theoneprobe";

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static final EntityCapability<net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>, Void> CURIO_ITEM_HANDLER =
			(EntityCapability) EntityCapability.createVoid(Identifier.fromNamespaceAndPath(CURIO_MODID, "item_handler"), net.neoforged.neoforge.transfer.ResourceHandler.class);

	public static void sendIMCMessages(InterModEnqueueEvent event) {
		if (ModList.get().isLoaded(TOP_MODID)) {
			moze_intel.projecte.integration.top.TOPIntegration.sendIMC(event);
		}
	}

	public static void registerCuriosCapability(RegisterCapabilitiesEvent event, Item item) {
		if (ModList.get().isLoaded(CURIO_MODID)) {
			CurioItemCapability.register(event, item);
		}
	}

	public static net.neoforged.neoforge.items.IItemHandlerModifiable getEquippedCurios(net.minecraft.world.entity.player.Player player) {
		return ModList.get().isLoaded(CURIO_MODID) ? CurioItemCapability.getEquippedCurios(player) : null;
	}
}
