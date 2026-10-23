package moze_intel.projecte.smoke;

import java.math.BigInteger;
import java.util.Set;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.gameObjs.block_entities.*;
import moze_intel.projecte.gameObjs.blocks.TransmutationStone;
import moze_intel.projecte.gameObjs.registries.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Only loaded by -PportSmoke, never included in the distributable. */
@Mod("projecte_smoke")
public class PortSmoke {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final String[] MACHINES = {"alchemical_chest", "condenser_mk1", "condenser_mk2", "dm_furnace", "rm_furnace", "collector_mk1", "collector_mk2", "collector_mk3", "relay_mk1", "relay_mk2", "relay_mk3"};
    private ServerPlayer player;
    private int ticks;
    public PortSmoke() {
        NeoForge.EVENT_BUS.addListener(this::login);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private BlockPos pos(int i) { return new BlockPos((i % 6) * 2, 100, (i / 6) * 4); }
    private void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.getGameProfile().name().equals("ProjectEPortTest")) return;
        if (Boolean.getBoolean("projecte.smoke.audit")) {
            player = p;
            ticks = 0;
            return;
        }
        player = p;
        ticks = 0;
        p.setGameMode(GameType.CREATIVE);
        var level = p.level();
        for (int x = -3; x < 16; x++) for (int z = -4; z < 10; z++) {
            level.setBlock(new BlockPos(x, 99, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            for (int y = 100; y <= 106; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        for (int i = 0; i < MACHINES.length; i++) {
            level.setBlock(pos(i), BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("projecte", MACHINES[i])).defaultBlockState(), 3);
            var be = level.getBlockEntity(pos(i));
            if (be instanceof CondenserBlockEntity condenser) {
                condenser.setLockInfoFromPacket(ItemInfo.fromItem(Items.DIAMOND));
                condenser.getInput().setStackInSlot(0, new ItemStack(Items.EMERALD));
            } else if (be instanceof DMFurnaceBlockEntity furnace) {
                furnace.getInput().insertItem(0, new ItemStack(Items.SAND, 4), false);
                furnace.getFuel().insertItem(0, new ItemStack(Items.COAL), false);
            }
            if (i >= 5 && i <= 7) level.setBlock(pos(i).above(), Blocks.GLOWSTONE.defaultBlockState(), 3);
        }
        var pedestalPos = new BlockPos(12, 100, 0);
        level.setBlock(pedestalPos, PEBlocks.DARK_MATTER_PEDESTAL.defaultState(), 3);
        ((DMPedestalBlockEntity) level.getBlockEntity(pedestalPos)).getInventory().setStackInSlot(0, new ItemStack(PEItems.PHILOSOPHERS_STONE.get()));
        // Rebuilding a previously used fixture can drop its old machine inventories.
        // Remove those leftovers before starting the menu-only scenario.
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(-4, 98, -5, 17, 108, 11))
                .forEach(net.minecraft.world.entity.Entity::discard);
        p.teleportTo(level, 4, 100, -3, Set.of(), 0, 10, true);
        p.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 16));
        p.getInventory().setItem(2, new ItemStack(PEItems.PHILOSOPHERS_STONE.get()));
        p.getInventory().setItem(3, new ItemStack(PEItems.KLEIN_STAR_OMEGA.get()));
        var knowledge = p.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        knowledge.addKnowledge(new ItemStack(Items.DIAMOND));
        knowledge.addKnowledge(new ItemStack(Items.IRON_INGOT));
        knowledge.setEmc(BigInteger.valueOf(65536));
        knowledge.sync(p);
        LOG.info("PORT_SMOKE: fixture prepared");
    }
    private void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("PORT_SMOKE FAILED: " + message);
        LOG.info("PORT_SMOKE PASS: {}", message);
    }
    private void tick(ServerTickEvent.Post event) {
        if (player == null || player.isRemoved()) return;
        ticks++;
        if (Boolean.getBoolean("projecte.smoke.audit")) {
            if (ticks == 40) {
                PortGameplayAudit.run(player.level().getServer());
                player = null;
            }
            return;
        }
        if (ticks == 1400) {
            var knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            check(knowledge.getEmc().equals(BigInteger.valueOf(65536 - moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE.getValue(Items.DIAMOND))), "network transmutation click charges exactly the mapped diamond EMC");
            check(player.containerMenu.getCarried().is(Items.DIAMOND) && player.containerMenu.getCarried().getCount() == 1, "network transmutation returns one diamond");
        }
        if (ticks == 60) {
            int before = player.getInventory().countItem(Items.STICK);
            var drop = new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.STICK, 7));
            drop.playerTouch(player);
            check(drop.isRemoved() && player.getInventory().countItem(Items.STICK) == before + 7,
                    "real server player pickup with Titanium/Industrial Foregoing conserves seven items");
            drop.discard();
        }
        if (ticks == 200) {
            for (int i = 1; i <= 2; i++) {
                var c = (CondenserBlockEntity) player.level().getBlockEntity(pos(i));
                int diamonds = 0;
                for (int s = 0; s < c.getOutput().getSlots(); s++) {
                    var stack = c.getOutput().getStackInSlot(s);
                    if (stack.is(Items.DIAMOND)) diamonds += stack.getCount();
                }
                long diamondEmc = moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE.getValue(Items.DIAMOND);
                long emeraldEmc = moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE.getSellValue(new ItemStack(Items.EMERALD));
                check(diamonds > 0 && diamonds * diamondEmc + c.getStoredEmc() == emeraldEmc, MACHINES[i] + " emerald -> diamonds with conserved EMC");
            }
            for (int i = 3; i <= 4; i++) {
                var f = (DMFurnaceBlockEntity) player.level().getBlockEntity(pos(i));
                int glass = 0;
                for (int s = 0; s < f.getOutput().getSlots(); s++) if (f.getOutput().getStackInSlot(s).is(Items.GLASS)) glass += f.getOutput().getStackInSlot(s).getCount();
                check(glass == 4, MACHINES[i] + " smelts four sand into glass");
            }
            for (int i = 5; i <= 7; i++) check(((CollectorMK1BlockEntity) player.level().getBlockEntity(pos(i))).getStoredEmc() > 0, MACHINES[i] + " produces EMC under glowstone");
        }
        if (ticks >= 240 && (ticks - 240) % 100 == 0) {
            int stage = (ticks - 240) / 100;
            player.closeContainer();
            if (stage < MACHINES.length) {
                var target = pos(stage);
                player.teleportTo(player.level(), target.getX() + .5, 100, target.getZ() - 2, Set.of(), 0, 15, true);
                player.openMenu((MenuProvider) player.level().getBlockEntity(target), target);
                LOG.info("PORT_SMOKE: opening {}", MACHINES[stage]);
            } else if (stage == 11) {
                player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> new moze_intel.projecte.gameObjs.container.TransmutationContainer(id, inv), net.minecraft.network.chat.Component.literal("Transmutation")), b -> b.writeBoolean(false));
            } else if (stage == 12) {
                var stack = new ItemStack(PEItems.WHITE_ALCHEMICAL_BAG.get());
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                player.inventoryMenu.broadcastChanges();
                stack.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
            } else if (stage == 13) {
                var stack = new ItemStack(PEItems.GEM_OF_ETERNAL_DENSITY.get());
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                player.inventoryMenu.broadcastChanges();
                stack.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
            } else if (stage == 14) {
                var stack = new ItemStack(PEItems.MERCURIAL_EYE.get());
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                player.inventoryMenu.broadcastChanges();
                PEItems.MERCURIAL_EYE.get().doExtraFunction(player, stack, InteractionHand.MAIN_HAND);
            } else if (stage == 15) {
                player.teleportTo(player.level(), 5, 102, -4, Set.of(), 0, 20, true);
                LOG.info("PORT_SMOKE SERVER COMPLETE");
                player = null;
            }
        }
    }
}
