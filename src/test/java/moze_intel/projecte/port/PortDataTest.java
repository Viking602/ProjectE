package moze_intel.projecte.port;

import java.util.List;
import moze_intel.projecte.PECore;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.gameObjs.block_entities.CondenserBlockEntity;
import moze_intel.projecte.gameObjs.registries.PEBlocks;
import moze_intel.projecte.gameObjs.registries.PEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class PortDataTest {
    @Test void diviningRodModesStayWithinTheirTier(MinecraftServer server) {
        var rods = List.of(PEItems.LOW_DIVINING_ROD.get(), PEItems.MEDIUM_DIVINING_ROD.get(), PEItems.HIGH_DIVINING_ROD.get());
        var component = moze_intel.projecte.gameObjs.registries.PEDataComponentTypes.DIVINING_ROD_MODE;
        for (int tier = 1; tier <= rods.size(); tier++) {
            var rod = rods.get(tier - 1);
            var stack = new ItemStack(rod);
            for (int step = 0; step < 12; step++) {
                assertEquals(step % tier, rod.getMode(stack).ordinal());
                stack.set(component, rod.getMode(stack).next(stack));
            }
            if (tier < 3) {
                stack.set(component, moze_intel.projecte.gameObjs.items.DiviningRod.DiviningMode.HIGH);
                assertEquals(0, rod.getMode(stack).ordinal(), "Invalid saved modes must recover to the lowest range");
            }
        }
    }
    @Test void mercurialEyeSlotsAcceptClientSynchronization(MinecraftServer server) {
        var eye = new ItemStack(PEItems.MERCURIAL_EYE.get());
        var capability = eye.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.ITEM,
                net.neoforged.neoforge.transfer.access.ItemAccess.forStack(eye));
        var inventory = moze_intel.projecte.utils.ItemTransferAdapter.legacy(capability);
        assertNotNull(inventory);
        var slot = new moze_intel.projecte.gameObjs.container.slots.InventoryContainerCopySlot(inventory, 0, 0, 0);
        slot.set(new ItemStack(PEItems.KLEIN_STAR_EIN.get()));
        assertTrue(slot.getItem().is(PEItems.KLEIN_STAR_EIN));
        slot.set(ItemStack.EMPTY);
        assertTrue(slot.getItem().isEmpty());
    }
    @Test void everyProjectERecipeCanBeSynchronized(MinecraftServer server) {
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
        try {
            for (var holder : server.getRecipeManager().recipeMap().values()) {
                if (holder.id().identifier().getNamespace().equals(PECore.MODID)) {
                    buffer.clear();
                    net.minecraft.world.item.crafting.Recipe.STREAM_CODEC.encode(buffer, holder.value());
                    var decoded = net.minecraft.world.item.crafting.Recipe.STREAM_CODEC.decode(buffer);
                    assertSame(holder.value().getSerializer(), decoded.getSerializer(), holder.id().toString());
                    assertFalse(buffer.isReadable());
                }
            }
        } finally {
            buffer.release();
        }
    }
    @Test void condenserInventoryAndTargetSurviveNbtRoundTrip(MinecraftServer server) {
        var state = PEBlocks.CONDENSER.defaultState();
        var condenser = new CondenserBlockEntity(BlockPos.ZERO, state);
        condenser.getInput().setStackInSlot(5, new ItemStack(Items.DIAMOND, 12));
        condenser.setLockInfoFromPacket(ItemInfo.fromItem(Items.EMERALD));
        CompoundTag data = condenser.saveWithFullMetadata(server.registryAccess());
        data.putLong("emc", 123456);
        var copy = (CondenserBlockEntity) BlockEntity.loadStatic(BlockPos.ZERO, state, data, server.registryAccess());
        assertNotNull(copy);
        assertEquals(123456, copy.getStoredEmc());
        assertEquals(12, copy.getInput().getStackInSlot(5).getCount());
        assertTrue(copy.getInput().getStackInSlot(5).is(Items.DIAMOND));
        assertEquals(data.getCompound("lock"), copy.saveWithFullMetadata(server.registryAccess()).getCompound("lock"));
        data.putLong("emc", -1);
        copy.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), data));
        assertEquals(0, copy.getStoredEmc());
    }

    @Test void matterEquipmentIsIndestructibleAndEquippable(MinecraftServer server) {
        var boots = new ItemStack(PEItems.GEM_BOOTS.get());
        assertTrue(boots.has(DataComponents.EQUIPPABLE));
        assertFalse(boots.isDamageableItem());
        assertFalse(boots.has(DataComponents.ENCHANTABLE));
        var pick = new ItemStack(PEItems.DARK_MATTER_PICKAXE.get());
        assertTrue(pick.has(DataComponents.TOOL));
        assertFalse(pick.isDamageableItem());
        assertFalse(pick.has(DataComponents.ENCHANTABLE));
    }

    @Test void stoneConversionRecipeLoadsAndReturnsCatalyst(MinecraftServer server) {
        var holder = server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, PECore.rl("conversions/diamond_to_emerald"))).orElseThrow();
        var recipe = (CraftingRecipe) holder.value();
        var input = CraftingInput.of(3, 1, List.of(new ItemStack(PEItems.PHILOSOPHERS_STONE.get()), new ItemStack(Items.DIAMOND), new ItemStack(Items.DIAMOND)));
        assertTrue(recipe.assemble(input).is(Items.EMERALD));
        assertTrue(recipe.getRemainingItems(input).getFirst().is(PEItems.PHILOSOPHERS_STONE));
    }
}
