package moze_intel.projecte.smoke;

import com.buuz135.industrial.item.infinity.item.ItemInfinityBackpack;
import com.buuz135.industrial.utils.IFAttachments;
import com.buuz135.industrial.worlddata.BackpackDataManager;
import com.hrznstudio.titanium.network.locator.PlayerInventoryFinder;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;

final class PortTitaniumAudit {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static void run(MinecraftServer server, BiConsumer<String, Runnable> test) {
        var level = server.overworld();
        test.accept("titanium_all_inventory_slots_read_write", () -> {
            var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "TitaniumAudit"));
            var finder = PlayerInventoryFinder.get(PlayerInventoryFinder.MAIN).orElseThrow();
            check(finder.getSlotAmountGetter().apply(player) == player.getInventory().getContainerSize(), "incomplete inventory scan");
            for (int invalid : new int[]{-1, player.getInventory().getContainerSize(), Integer.MAX_VALUE}) {
                check(finder.getStackGetter().apply(player, invalid).isEmpty(), "invalid slot returned an item");
                finder.getStackSetter().consume(player, invalid, new ItemStack(Items.DIAMOND));
            }
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                finder.getStackSetter().consume(player, slot, new ItemStack(Items.DIAMOND, 2));
                check(finder.getStackGetter().apply(player, slot).getCount() == 2, "finder write lost at " + slot);
                check(player.getInventory().getItem(slot).is(Items.DIAMOND), "wrong inventory index " + slot);
                finder.getStackSetter().consume(player, slot, ItemStack.EMPTY);
            }
        });
        if (!net.neoforged.fml.ModList.get().isLoaded("industrialforegoing")) return;
        test.accept("industrial_normal_item_pickup_without_backpack", () -> {
            var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "PickupAudit"));
            var drop = new ItemEntity(level, 32, 100, 32, new ItemStack(Items.DIAMOND, 3));
            drop.playerTouch(player);
            check(player.getInventory().countItem(Items.DIAMOND) == 3 && drop.isRemoved(), "normal pickup lost items");
            drop.discard();
        });
        for (int slot : new int[]{0, 10, 35, 40}) test.accept("industrial_backpack_pickup_slot_" + slot, () -> {
            var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "BackpackAudit"));
            var backpack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("industrialforegoing:infinity_backpack")));
            check(backpack.getItem() instanceof ItemInfinityBackpack, "missing backpack");
            var id = UUID.randomUUID();
            var manager = BackpackDataManager.getData(level);
            manager.createBackPack(id);
            var handler = manager.getBackpack(id.toString());
            backpack.set(IFAttachments.INFINITY_BACKPACK_ID, id.toString());
            ItemInfinityBackpack.setPickUpMode(backpack, 1);
            player.getInventory().setItem(slot, backpack);
            handler.insertItem(0, new ItemStack(Items.DIAMOND), false);
            var targets = ItemInfinityBackpack.findAllBackpacks(player);
            check(targets.size() == 1 && targets.getFirst().getSlot() == slot, "backpack not located");
            var drop = new ItemEntity(level, 32, 100, 32, new ItemStack(Items.DIAMOND, 7));
            drop.playerTouch(player);
            check(handler.getStackInSlot(0).getCount() == 8 && drop.getItem().isEmpty(), "backpack pickup did not conserve items");
            check(player.getInventory().countItem(Items.DIAMOND) == 0, "pickup duplicated into inventory");
            ItemInfinityBackpack.setPickUpMode(backpack, 2);
            var disabled = new ItemEntity(level, 32, 100, 32, new ItemStack(Items.DIAMOND, 3));
            disabled.playerTouch(player);
            check(handler.getStackInSlot(0).getCount() == 8 && player.getInventory().countItem(Items.DIAMOND) == 3, "disabled backpack blocked normal pickup");
            drop.discard(); disabled.discard(); manager.itemHandlers.remove(id.toString()); manager.setDirty();
        });
    }
}
