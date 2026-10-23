package moze_intel.projecte.smoke;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.List;
import java.math.BigInteger;
import moze_intel.projecte.PECore;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.capabilities.block_entity.IEmcStorage.EmcAction;
import moze_intel.projecte.gameObjs.block_entities.*;
import moze_intel.projecte.gameObjs.registries.PEBlocks;
import moze_intel.projecte.gameObjs.registries.PEAttachmentTypes;
import moze_intel.projecte.handlers.InternalAbilities;
import moze_intel.projecte.utils.ClockHelper;
import moze_intel.projecte.utils.ItemTransferAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.item.ItemResource;
import moze_intel.projecte.gameObjs.registries.PEDataComponentTypes;
import moze_intel.projecte.gameObjs.registries.PEItems;
import moze_intel.projecte.utils.EMCHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Functional assertions in a disposable, fully initialized Minecraft world. */
public final class PortGameplayAudit {
    public static volatile boolean completed;
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private int passed, failed;
    private final MinecraftServer server;
    private PortGameplayAudit(MinecraftServer server) { this.server = server; }
    private FakePlayer player() {
        var player = new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), "ProjectEAudit"));
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(32.5, 100, 32.5);
        return player;
    }
    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private void test(String name, Runnable action) {
        try { action.run(); passed++; LOG.info("PORT_AUDIT PASS: {}", name); }
        catch (Throwable error) { failed++; LOG.error("PORT_AUDIT FAIL: " + name, error); }
    }
    public static void run(MinecraftServer server) {
        var audit = new PortGameplayAudit(server);
        audit.runTests();
        audit.auditCraftingEconomy();
        LOG.info("PORT_AUDIT COMPLETE: {} passed, {} failed", audit.passed, audit.failed);
        completed = true;
    }
    private void runTests() {
        test("klein_star_real_inventory_fuel_debit", () -> {
            var player = player();
            var star = new ItemStack(PEItems.KLEIN_STAR_EIN.get());
            star.set(PEDataComponentTypes.STORED_EMC, 1_000L);
            player.getInventory().setItem(10, star);
            check(EMCHelper.consumePlayerFuel(player, 64) == 64, "fuel extraction failed");
            check(player.getInventory().getItem(10).get(PEDataComponentTypes.STORED_EMC) == 936L, "fuel was credited without debiting the actual star");
        });
        test("repair_talisman_real_player_inventory", () -> {
            var player = player();
            var tool = new ItemStack(Items.IRON_PICKAXE);
            tool.setDamageValue(10);
            player.getInventory().setItem(10, tool);
            new ItemStack(PEItems.REPAIR_TALISMAN.get()).inventoryTick(server.overworld(), player, null);
            check(player.getInventory().getItem(10).getDamageValue() == 9, "actual inventory item was not repaired");
        });
        registryTests();
        survivalTests();
        worldTests();
        storageTests();
        curiosTests();
        recipeTests();
        advancedTests();
        optionalIntegrationTests();
        if (net.neoforged.fml.ModList.get().isLoaded("titanium")) PortTitaniumAudit.run(server, this::test);
        if (net.neoforged.fml.ModList.get().isLoaded("mekanism")) PortMekanismAudit.run(server, this::test);
        economyRegressionTests();
    }

    private ItemStack charged(net.minecraft.world.level.ItemLike item) {
        var stack = new ItemStack(item);
        stack.set(PEDataComponentTypes.STORED_EMC, 100_000L);
        stack.set(PEDataComponentTypes.ACTIVE, true);
        return stack;
    }

    private void registryTests() {
        var level = server.overworld();
        for (var item : BuiltInRegistries.ITEM) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals("projecte")) continue;
            test("item_codec_and_inventory_ticks/" + id.getPath(), () -> {
                var stack = new ItemStack(item);
                var player = player();
                player.getInventory().setItem(0, stack);
                stack.inventoryTick(level, player, EquipmentSlot.MAINHAND);
                if (stack.has(PEDataComponentTypes.ACTIVE)) stack.set(PEDataComponentTypes.ACTIVE, true);
                if (stack.has(PEDataComponentTypes.STORED_EMC)) stack.set(PEDataComponentTypes.STORED_EMC, 4096L);
                stack.inventoryTick(level, player, EquipmentSlot.MAINHAND);
                player.getInventory().setItem(0, ItemStack.EMPTY);
                player.getInventory().setItem(10, stack);
                stack.inventoryTick(level, player, null);
                var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
                try {
                    ItemStack.STREAM_CODEC.encode(buffer, stack);
                    check(ItemStack.matches(stack, ItemStack.STREAM_CODEC.decode(buffer)), "stack network round trip changed components");
                    check(!buffer.isReadable(), "unread stack data");
                } finally { buffer.release(); }
                var ops = server.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
                var encoded = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                check(ItemStack.matches(stack, ItemStack.CODEC.parse(ops, encoded).getOrThrow()), "stack save round trip changed components");
            });
            var stack = new ItemStack(item);
            var charge = stack.getCapability(PECapabilities.CHARGE_ITEM_CAPABILITY);
            if (charge != null) test("charge_bounds/" + id.getPath(), () -> {
                var player = player();
                int max = charge.getNumCharges(stack);
                for (int i = 0; i < max; i++) check(charge.changeCharge(player, stack, InteractionHand.MAIN_HAND), "charge did not advance");
                check(charge.getCharge(stack) == max, "charge maximum mismatch");
                check(!charge.changeCharge(player, stack, InteractionHand.MAIN_HAND), "charge exceeded maximum");
                player.setShiftKeyDown(true);
                for (int i = max; i > 0; i--) check(charge.changeCharge(player, stack, InteractionHand.MAIN_HAND), "discharge did not advance");
                check(charge.getCharge(stack) == 0, "discharge minimum mismatch");
            });
            var mode = stack.getCapability(PECapabilities.MODE_CHANGER_ITEM_CAPABILITY);
            if (mode != null) test("mode_cycle/" + id.getPath(), () -> {
                var player = player();
                var before = mode.getMode(stack);
                if (stack.is(PEItems.LOW_DIVINING_ROD)) {
                    check(!mode.changeMode(player, stack, InteractionHand.MAIN_HAND), "single-mode rod changed mode");
                    check(before.equals(mode.getMode(stack)), "single-mode rod left its supported mode");
                    return;
                }
                check(mode.changeMode(player, stack, InteractionHand.MAIN_HAND), "mode action failed");
                check(!before.equals(mode.getMode(stack)), "mode did not change");
                boolean wrapped = false;
                for (int i = 0; i < 16; i++) {
                    if (before.equals(mode.getMode(stack))) { wrapped = true; break; }
                    mode.changeMode(player, stack, InteractionHand.MAIN_HAND);
                }
                check(wrapped, "mode did not wrap");
            });
            var holder = stack.getCapability(PECapabilities.EMC_HOLDER_ITEM_CAPABILITY);
            if (holder != null) test("emc_storage_limits/" + id.getPath(), () -> {
                long max = holder.getMaximumEmc(stack);
                check(holder.insertEmc(stack, Long.MAX_VALUE, EmcAction.SIMULATE) == max, "simulation capacity");
                check(holder.getStoredEmc(stack) == 0, "simulation changed storage");
                check(holder.insertEmc(stack, Long.MAX_VALUE, EmcAction.EXECUTE) == max, "capacity clamp");
                check(holder.extractEmc(stack, max + 1, EmcAction.EXECUTE) == max, "extraction clamp");
                check(holder.getStoredEmc(stack) == 0, "extraction left invalid EMC");
            });
            var shooter = stack.getCapability(PECapabilities.PROJECTILE_SHOOTER_ITEM_CAPABILITY);
            if (shooter != null) test("projectile_spawn/" + id.getPath(), () -> {
                var player = player();
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                stack.set(PEDataComponentTypes.STORED_EMC, 100_000L);
                int before = level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, player.getBoundingBox().inflate(4)).size();
                check(shooter.shootProjectile(player, stack, InteractionHand.MAIN_HAND), "shoot action failed");
                check(level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, player.getBoundingBox().inflate(4)).size() > before, "no projectile spawned");
            });
            var pedestalItem = stack.getCapability(PECapabilities.PEDESTAL_ITEM_CAPABILITY);
            if (pedestalItem != null) test("pedestal_callback/" + id.getPath(), () -> {
                var pos = new BlockPos(40, 100, 40);
                level.setBlockAndUpdate(pos, PEBlocks.DARK_MATTER_PEDESTAL.defaultState());
                var pedestal = (DMPedestalBlockEntity) level.getBlockEntity(pos);
                pedestal.getInventory().setStackInSlot(0, stack.copy());
                pedestal.setActive(level, pos, true);
                DMPedestalBlockEntity.tickServer(level, pos, pedestal.getBlockState(), pedestal);
                check(pedestal.getActive(), "valid pedestal item was disabled");
                DMPedestalBlockEntity.tickServer(level, pos, pedestal.getBlockState(), pedestal);
                level.removeBlock(pos, false);
            });
        }
    }

    private void survivalTests() {
        var level = server.overworld();
        test("fuel_shortage_is_atomic", () -> {
            var p = player(); var star = new ItemStack(PEItems.KLEIN_STAR_EIN.get());
            star.set(PEDataComponentTypes.STORED_EMC, 32L); p.getInventory().setItem(3, star);
            check(EMCHelper.consumePlayerFuel(p, 64) == -1, "shortage did not fail");
            check(star.get(PEDataComponentTypes.STORED_EMC) == 32L, "shortage consumed partial fuel");
        });
        test("coal_fuel_preserves_change", () -> {
            var p = player(); p.getInventory().setItem(3, new ItemStack(Items.COAL));
            var ring = new ItemStack(PEItems.BODY_STONE.get());
            long fuelEmc = moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE.getValue(Items.COAL);
            check(fuelEmc > 1, "coal lost usable EMC");
            long cost = fuelEmc / 2;
            check(moze_intel.projecte.gameObjs.items.ItemPE.consumeFuel(p, ring, cost, true), "coal was not consumed");
            check(p.getInventory().getItem(3).isEmpty(), "coal not debited");
            check(ring.get(PEDataComponentTypes.STORED_EMC) == fuelEmc - cost, "change was lost");
        });
        for (var item : List.of(PEItems.BODY_STONE.get(), PEItems.SOUL_STONE.get(), PEItems.LIFE_STONE.get())) {
            test("healing_and_feeding/" + BuiltInRegistries.ITEM.getKey(item).getPath(), () -> {
                var p = player(); p.setHealth(10); p.getFoodData().setFoodLevel(10);
                var stack = charged(item); p.getInventory().setItem(0, stack);
                stack.inventoryTick(level, p, EquipmentSlot.MAINHAND);
                if (item != PEItems.SOUL_STONE.get()) check(p.getFoodData().getFoodLevel() > 10, "food did not improve");
                if (item != PEItems.BODY_STONE.get()) check(p.getHealth() > 10, "health did not improve");
                check(stack.get(PEDataComponentTypes.STORED_EMC) < 100_000L, "healing was free");
            });
        }
        test("mind_stone_xp_round_trip", () -> {
            var p = player(); p.giveExperiencePoints(100);
            var stone = charged(PEItems.MIND_STONE); p.setItemInHand(InteractionHand.MAIN_HAND, stone);
            stone.inventoryTick(level, p, EquipmentSlot.MAINHAND);
            check(stone.get(PEDataComponentTypes.STORED_EXP) == 50, "XP not stored");
            stone.set(PEDataComponentTypes.ACTIVE, false);
            stone.getItem().use(level, p, InteractionHand.MAIN_HAND);
            check(stone.get(PEDataComponentTypes.STORED_EXP) == 0 && p.totalExperience == 100, "XP not conserved");
        });
        test("gem_helmet_heal_night_vision", () -> {
            var p = player(); p.setHealth(10); var helm = new ItemStack(PEItems.GEM_HELMET.get());
            helm.set(PEDataComponentTypes.NIGHT_VISION, true); p.setItemSlot(EquipmentSlot.HEAD, helm);
            helm.inventoryTick(level, p, EquipmentSlot.HEAD);
            check(p.getHealth() > 10 && p.hasEffect(MobEffects.NIGHT_VISION), "helmet effects missing");
            helm.set(PEDataComponentTypes.NIGHT_VISION, false); helm.inventoryTick(level, p, EquipmentSlot.HEAD);
            check(!p.hasEffect(MobEffects.NIGHT_VISION), "night vision did not toggle off");
        });
        test("gem_chest_feeding_fire_protection", () -> {
            var p = player(); p.getFoodData().setFoodLevel(10); var chest = new ItemStack(PEItems.GEM_CHESTPLATE.get());
            p.setItemSlot(EquipmentSlot.CHEST, chest); chest.inventoryTick(level, p, EquipmentSlot.CHEST);
            check(p.getFoodData().getFoodLevel() > 10, "chest does not feed");
            check(moze_intel.projecte.events.TickEvents.shouldPlayerResistFire(p), "chest fire immunity missing");
        });
        test("gem_boots_fall_speed_step_attributes", () -> {
            var p = player(); var boots = new ItemStack(PEItems.GEM_BOOTS.get());
            p.setItemSlot(EquipmentSlot.FEET, boots); p.fallDistance = 15;
            boots.inventoryTick(level, p, EquipmentSlot.FEET); check(p.fallDistance == 0, "fall distance not reset");
            boots.set(PEDataComponentTypes.STEP_ASSIST, true);
            var found = new java.util.HashSet<net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>>();
            boots.forEachModifier(EquipmentSlot.FEET, (attribute, modifier) -> found.add(attribute));
            check(found.contains(Attributes.MOVEMENT_SPEED) && found.contains(Attributes.STEP_HEIGHT), "boot attributes missing");
            boots.set(PEDataComponentTypes.STEP_ASSIST, false); found.clear();
            boots.forEachModifier(EquipmentSlot.FEET, (attribute, modifier) -> found.add(attribute));
            check(!found.contains(Attributes.STEP_HEIGHT), "step assist did not turn off");
        });
        for (var item : List.of(PEItems.ARCANA_RING.get(), PEItems.SWIFTWOLF_RENDING_GALE.get())) {
            test("flight_grant_and_remove/" + BuiltInRegistries.ITEM.getKey(item).getPath(), () -> {
                var p = player(); p.getInventory().setItem(3, charged(item)); InternalAbilities.tick(p);
                check(p.getAttributeValue(net.neoforged.neoforge.common.NeoForgeMod.CREATIVE_FLIGHT) > 0, "flight not granted");
                p.getInventory().setItem(3, ItemStack.EMPTY); InternalAbilities.tick(p);
                check(p.getAttributeValue(net.neoforged.neoforge.common.NeoForgeMod.CREATIVE_FLIGHT) == 0, "flight remained after removal");
            });
        }
        test("evertide_infinite_water_capability", () -> {
            var p = player(); p.getInventory().setItem(3, new ItemStack(PEItems.EVERTIDE_AMULET.get()));
            check(moze_intel.projecte.utils.PlayerHelper.checkHotbarCurios(p, (ignored, stack) -> stack.is(PEItems.EVERTIDE_AMULET)), "amulet cannot be detected");
            var water = charged(PEItems.EVERTIDE_AMULET);
            var handler = water.getCapability(Capabilities.Fluid.ITEM, net.neoforged.neoforge.transfer.access.ItemAccess.forStack(water));
            check(handler != null, "water capability absent");
            try (var tx = Transaction.openRoot()) {
                check(handler.extract(0, net.neoforged.neoforge.transfer.fluid.FluidResource.of(net.minecraft.world.level.material.Fluids.WATER), 1000, tx) == 1000, "water extraction failed");
                check(handler.extract(0, net.neoforged.neoforge.transfer.fluid.FluidResource.of(net.minecraft.world.level.material.Fluids.LAVA), 1000, tx) == 0, "wrong fluid extracted");
                tx.commit();
            }
            check(handler.getAmountAsInt(0) == Integer.MAX_VALUE, "infinite source depleted");
        });
        test("time_watch_forward_rewind", () -> {
            var p = player(); var watch = new ItemStack(PEItems.WATCH_OF_FLOWING_TIME.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, watch); long old = ClockHelper.getTime(level);
            ClockHelper.setTime(level, 1000);
            watch.set(PEDataComponentTypes.TIME_WATCH_MODE, moze_intel.projecte.gameObjs.items.rings.TimeWatch.TimeWatchMode.FAST_FORWARD);
            watch.inventoryTick(level, p, EquipmentSlot.MAINHAND); check(ClockHelper.getTime(level) == 1004, "time did not advance");
            watch.set(PEDataComponentTypes.TIME_WATCH_MODE, moze_intel.projecte.gameObjs.items.rings.TimeWatch.TimeWatchMode.REWIND);
            watch.inventoryTick(level, p, EquipmentSlot.MAINHAND); check(ClockHelper.getTime(level) == 1000, "time did not rewind");
            ClockHelper.setTime(level, old);
        });
    }

    private UseOnContext context(FakePlayer player, ItemStack stack, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(pos.getCenter(), Direction.UP, pos, false));
    }
    private void worldTests() {
        var level = server.overworld(); var pos = new BlockPos(32, 100, 32);
        for (int x = 27; x < 47; x++) for (int z = 27; z < 47; z++) {
            level.setBlock(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 100; y < 105; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        test("philosopher_stone_world_transmutation", () -> {
            var p = player(); level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            var stone = new ItemStack(PEItems.PHILOSOPHERS_STONE.get());
            stone.getItem().onItemUseFirst(stone, context(p, stone, pos));
            check(level.getBlockState(pos).is(Blocks.COBBLESTONE), "stone did not transmute");
            level.removeBlock(pos, false);
        });
        for (var item : List.of(PEItems.DARK_MATTER_HOE.get(), PEItems.RED_MATTER_HOE.get())) test("hoe_tilling/" + BuiltInRegistries.ITEM.getKey(item).getPath(), () -> {
            var p = player(); level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
            item.useOn(context(p, new ItemStack(item), pos));
            check(level.getBlockState(pos).is(Blocks.FARMLAND), "hoe did not till"); level.removeBlock(pos, false);
        });
        test("destruction_catalyst_break_and_bedrock_guard", () -> {
            var p = player(); var catalyst = charged(PEItems.DESTRUCTION_CATALYST);
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            catalyst.getItem().useOn(context(p, catalyst, pos));
            check(level.getBlockState(pos).isAir(), "catalyst failed to break stone");
            check(catalyst.get(PEDataComponentTypes.STORED_EMC) < 100_000, "catalyst did not charge EMC");
            level.setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
            catalyst.getItem().useOn(context(p, catalyst, pos));
            check(level.getBlockState(pos).is(Blocks.BEDROCK), "catalyst destroyed bedrock"); level.removeBlock(pos, false);
        });
        test("zero_ring_freezes_water", () -> {
            var p = player(); level.setBlockAndUpdate(pos.east(), Blocks.WATER.defaultBlockState());
            var ring = charged(PEItems.ZERO_RING); p.setItemInHand(InteractionHand.MAIN_HAND, ring);
            ring.getItem().use(level, p, InteractionHand.MAIN_HAND);
            check(level.getBlockState(pos.east()).is(Blocks.ICE), "water not frozen"); level.removeBlock(pos.east(), false);
        });
        test("interdiction_torch_repels_hostile", () -> {
            level.setBlockAndUpdate(pos, PEBlocks.INTERDICTION_TORCH.defaultState());
            var zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
            zombie.setPos(34, 100, 32.5); level.addFreshEntity(zombie);
            InterdictionBlockEntity.tick(level, pos, level.getBlockState(pos), (InterdictionBlockEntity) level.getBlockEntity(pos));
            check(zombie.getDeltaMovement().x() > 0, "zombie not repelled"); zombie.discard(); level.removeBlock(pos, false);
        });
        test("pedestal_weather_amulets", () -> {
            level.setBlockAndUpdate(pos, PEBlocks.DARK_MATTER_PEDESTAL.defaultState()); var ped = (DMPedestalBlockEntity) level.getBlockEntity(pos);
            PEItems.EVERTIDE_AMULET.get().updateInPedestal(new ItemStack(PEItems.EVERTIDE_AMULET.get()), level, pos, ped);
            check(level.getWeatherData().isRaining(), "rain was not enabled"); ped.setActivityCooldown(level, pos, 0);
            PEItems.VOLCANITE_AMULET.get().updateInPedestal(new ItemStack(PEItems.VOLCANITE_AMULET.get()), level, pos, ped);
            check(!level.getWeatherData().isRaining(), "rain was not cleared"); level.removeBlock(pos, false);
        });
        for (String name : List.of("relay_mk1", "relay_mk2", "relay_mk3")) test("relay_charge_conserves_emc/" + name, () -> {
            var block = BuiltInRegistries.BLOCK.getValue(PECore.rl(name)); level.setBlockAndUpdate(pos, block.defaultBlockState());
            var relay = (RelayMK1BlockEntity) level.getBlockEntity(pos);
            relay.getInput().insertItem(0, new ItemStack(Items.DIAMOND), false);
            relay.getOutput().insertItem(0, new ItemStack(PEItems.KLEIN_STAR_EIN.get()), false);
            RelayMK1BlockEntity.tickServer(level, pos, relay.getBlockState(), relay);
            var star = relay.getOutput().getStackInSlot(0);
            long emc = star.getOrDefault(PEDataComponentTypes.STORED_EMC, 0L);
            check(emc > 0 && emc + relay.getStoredEmc() == moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE.getValue(Items.DIAMOND), "relay lost or created EMC");
            var side = level.getCapability(Capabilities.Item.BLOCK, pos, Direction.UP);
            try (var tx = Transaction.openRoot()) { check(side.extract(0, ItemResource.of(star), 1, tx) == 0, "partly charged star extracted by automation"); }
            level.removeBlock(pos, false);
        });
        test("alchemical_chest_break_drops_inventory_once", () -> {
            var area = new AABB(pos).inflate(2); level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);
            level.setBlockAndUpdate(pos, PEBlocks.ALCHEMICAL_CHEST.defaultState());
            var handler = level.getCapability(Capabilities.Item.BLOCK, pos, null);
            try (var tx = Transaction.openRoot()) { check(handler.insert(ItemResource.of(Items.DIAMOND), 3, tx) == 3, "chest insert failed"); tx.commit(); }
            level.destroyBlock(pos, true);
            int count = level.getEntitiesOfClass(ItemEntity.class, area).stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum();
            check(count == 3, "breaking chest dropped " + count + " diamonds instead of 3");
        });
        test("black_hole_pedestal_inserts_adjacent_chest", () -> {
            level.setBlockAndUpdate(pos, PEBlocks.DARK_MATTER_PEDESTAL.defaultState());
            level.setBlockAndUpdate(pos.east(), PEBlocks.ALCHEMICAL_CHEST.defaultState());
            var ped = (DMPedestalBlockEntity) level.getBlockEntity(pos);
            var drop = new ItemEntity(level, pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5, new ItemStack(Items.EMERALD, 2)); level.addFreshEntity(drop);
            PEItems.BLACK_HOLE_BAND.get().updateInPedestal(new ItemStack(PEItems.BLACK_HOLE_BAND.get()), level, pos, ped);
            var handler = level.getCapability(Capabilities.Item.BLOCK, pos.east(), null); int found = 0;
            for (int i=0; i<handler.size(); i++) if (handler.getResource(i).is(Items.EMERALD)) found += handler.getAmountAsInt(i);
            check(found == 2 && !drop.isAlive(), "pedestal transfer failed"); level.removeBlock(pos, false); level.removeBlock(pos.east(), false);
        });
    }

    private void storageTests() {
        test("all_bag_colors_isolated_and_saved", () -> {
            var p = player(); var bags = p.getData(PEAttachmentTypes.ALCHEMICAL_BAGS);
            for (var color : DyeColor.values()) bags.getBag(color).setStackInSlot(103, new ItemStack(Items.DIAMOND, color.getId()+1));
            var ops = server.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            var codec = moze_intel.projecte.impl.capability.AlchBagImpl.AlchemicalBagAttachment.CODEC;
            var copy = codec.parse(ops, codec.encodeStart(ops, bags).getOrThrow()).getOrThrow();
            for (var color : DyeColor.values()) check(copy.getBag(color).getStackInSlot(103).getCount() == color.getId()+1, "bag colors mixed after save");
            var other = player().getData(PEAttachmentTypes.ALCHEMICAL_BAGS);
            check(other.getBag(DyeColor.WHITE).getStackInSlot(103).isEmpty(), "bags shared between players");
        });
        test("knowledge_emc_persistence_and_copy_on_death", () -> {
            var p = player(); var knowledge = p.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            knowledge.addKnowledge(ItemInfo.fromItem(Items.DIAMOND)); knowledge.setEmc(new BigInteger("123456789012345678901234567890"));
            var attachment = p.getData(PEAttachmentTypes.KNOWLEDGE);
            var ops = server.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            var codec = moze_intel.projecte.impl.capability.KnowledgeImpl.KnowledgeAttachment.CODEC;
            var saved = codec.parse(ops, codec.encodeStart(ops, attachment).getOrThrow()).getOrThrow();
            var copy = moze_intel.projecte.impl.capability.KnowledgeImpl.wrapAttachment(saved);
            check(copy.hasKnowledge(ItemInfo.fromItem(Items.DIAMOND)) && copy.getEmc().equals(knowledge.getEmc()), "saved knowledge/EMC mismatch");
            var deadCopy = moze_intel.projecte.impl.capability.KnowledgeImpl.wrapAttachment(attachment.copy(player(), server.registryAccess()));
            knowledge.clearKnowledge(); check(deadCopy.hasKnowledge(ItemInfo.fromItem(Items.DIAMOND)), "death copy shares mutable knowledge");
        });
        test("density_gem_condenses_without_emc_loss", () -> {
            var p = player(); var gem = charged(PEItems.GEM_OF_ETERNAL_DENSITY); gem.set(PEDataComponentTypes.STORED_EMC, 0L);
            p.getInventory().setItem(0, gem); p.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 64));
            for (int i=2; i<6; i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            for (int i=0; i<40; i++) gem.inventoryTick(server.overworld(), p, EquipmentSlot.MAINHAND);
            long total = gem.get(PEDataComponentTypes.STORED_EMC); int iron = 0;
            for (var stack : p.getInventory().getNonEquipmentItems()) if (stack != gem) {
                total += moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE.getValue(stack) * stack.getCount();
                if (stack.is(Items.IRON_INGOT)) iron += stack.getCount();
            }
            check(iron > 0 && total == 320, "density EMC mismatch: " + total + ", iron=" + iron);
        });
    }

    private void curiosTests() {
        if (!net.neoforged.fml.ModList.get().isLoaded("curios")) return;
        test("curios_star_fuel_debit", () -> {
            var p = player(); var inventory = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).orElseThrow();
            inventory.loadDatapacks(); var equipped = inventory.getEquippedCurios();
            check(equipped.getSlots() > 0, "test player has no curios slots");
            var star = new ItemStack(PEItems.KLEIN_STAR_EIN.get()); star.set(PEDataComponentTypes.STORED_EMC, 1000L);
            equipped.setStackInSlot(0, star); check(EMCHelper.consumePlayerFuel(p, 64) == 64, "curio star not used");
            check(equipped.getStackInSlot(0).get(PEDataComponentTypes.STORED_EMC) == 936L, "curio star EMC not debited");
        });
        test("curios_ring_flight_and_fuel_buffer", () -> {
            var p = player(); var inventory = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).orElseThrow(); inventory.loadDatapacks();
            var equipped = inventory.getEquippedCurios(); var ring = new ItemStack(PEItems.SWIFTWOLF_RENDING_GALE.get());
            equipped.setStackInSlot(0, ring); p.getInventory().setItem(10, new ItemStack(Items.GUNPOWDER, 64));
            InternalAbilities.tick(p);
            check(p.getAttributeValue(net.neoforged.neoforge.common.NeoForgeMod.CREATIVE_FLIGHT) > 0, "curio flight missing");
            check(equipped.getStackInSlot(0).getOrDefault(PEDataComponentTypes.STORED_EMC, 0L) > 0, "curio fuel buffer lost");
        });
    }

    private void recipeTests() {
        for (var holder : server.getRecipeManager().recipeMap().values()) {
            if (!holder.id().identifier().getNamespace().equals("projecte")) continue;
            if (!(holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe recipe)) continue;
            var placement = recipe.placementInfo();
            // Dynamic special recipes have no placement information; their paths are tested separately.
            if (placement.isImpossibleToPlace()) continue;
            test("craft_recipe/" + holder.id().identifier().getPath(), () -> {
                int width = 3, height = 3;
                if (recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) {
                    width = shaped.getWidth(); height = shaped.getHeight();
                }
                var slots = new java.util.ArrayList<ItemStack>(java.util.Collections.nCopies(width * height, ItemStack.EMPTY));
                for (int i = 0; i < placement.slotsToIngredientIndex().size(); i++) {
                    int ingredient = placement.slotsToIngredientIndex().getInt(i);
                    if (ingredient >= 0) slots.set(i, new ItemStack(placement.ingredients().get(ingredient).items().findFirst().orElseThrow()));
                }
                var input = net.minecraft.world.item.crafting.CraftingInput.of(width, height, slots);
                check(recipe.matches(input, server.overworld()), "declared ingredients do not match recipe");
                check(!recipe.assemble(input).isEmpty(), "empty crafting result");
                var remainders = recipe.getRemainingItems(input);
                for (int i = 0; i < slots.size(); i++) if (slots.get(i).is(PEItems.PHILOSOPHERS_STONE)) {
                    check(remainders.get(i).is(PEItems.PHILOSOPHERS_STONE), "philosopher's stone consumed by recipe");
                }
            });
        }
    }

    private void advancedTests() {
        var level = server.overworld(); var pos = new BlockPos(32, 100, 32);
        test("time_watch_accelerates_furnace_and_slows_mobs", () -> {
            var p = player(); var watch = charged(PEItems.WATCH_OF_FLOWING_TIME); p.setItemInHand(InteractionHand.MAIN_HAND, watch);
            level.setBlockAndUpdate(pos.east(), Blocks.FURNACE.defaultBlockState());
            var furnace = (net.minecraft.world.level.block.entity.FurnaceBlockEntity) level.getBlockEntity(pos.east());
            furnace.setItem(0, new ItemStack(Items.SAND)); furnace.setItem(1, new ItemStack(Items.COAL));
            net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.serverTick(level, pos.east(), furnace.getBlockState(), furnace);
            int before = furnace.saveWithFullMetadata(server.registryAccess()).getIntOr("cooking_time_spent", 0);
            var zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED); zombie.setPos(34, 100, 32); zombie.setDeltaMovement(1, 0, 1); level.addFreshEntity(zombie);
            watch.inventoryTick(level, p, EquipmentSlot.MAINHAND);
            int after = furnace.saveWithFullMetadata(server.registryAccess()).getIntOr("cooking_time_spent", 0);
            check(after >= before + 8, "watch did not add furnace ticks: " + before + " -> " + after);
            check(zombie.getDeltaMovement().x() < 1, "watch did not slow mob");
            level.setBlockAndUpdate(pos, PEBlocks.DARK_MATTER_PEDESTAL.defaultState());
            PEItems.WATCH_OF_FLOWING_TIME.get().updateInPedestal(watch, level, pos, (DMPedestalBlockEntity) level.getBlockEntity(pos));
            check(furnace.saveWithFullMetadata(server.registryAccess()).getIntOr("cooking_time_spent", 0) > after, "pedestal did not accelerate furnace");
            zombie.discard(); level.removeBlock(pos, false); level.removeBlock(pos.east(), false);
        });
        test("harvest_ring_plants_and_consumes_seeds", () -> {
            level.setBlockAndUpdate(pos, Blocks.FARMLAND.defaultBlockState()); level.removeBlock(pos.above(), false);
            var p = player(); p.getInventory().setItem(1, new ItemStack(Items.WHEAT_SEEDS)); var ring = charged(PEItems.HARVEST_GODDESS_BAND);
            ring.getItem().useOn(context(p, ring, pos));
            check(level.getBlockState(pos.above()).is(Blocks.WHEAT), "harvest ring did not plant");
            check(p.getInventory().getItem(1).isEmpty(), "planting did not consume seed"); level.removeBlock(pos.above(), false); level.removeBlock(pos, false);
        });
        test("ignition_ring_lights_and_extinguishes_fire", () -> {
            var p = player(); level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()); level.removeBlock(pos.above(), false);
            var ring = charged(PEItems.IGNITION_RING); ring.getItem().useOn(context(p, ring, pos));
            check(level.getBlockState(pos.above()).is(Blocks.FIRE), "ring did not light fire");
            ring.set(PEDataComponentTypes.ACTIVE, false); ring.inventoryTick(level, p, EquipmentSlot.MAINHAND);
            check(level.getBlockState(pos.above()).isAir(), "ring did not extinguish fire"); level.removeBlock(pos, false);
            level.setBlockAndUpdate(pos, Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, false));
            ring.getItem().useOn(context(p, ring, pos));
            check(level.getBlockState(pos).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT), "ring did not light campfire");
            level.setBlockAndUpdate(pos, Blocks.TNT.defaultBlockState());
            ring.getItem().useOn(context(p, ring, pos));
            check(level.getBlockState(pos).isAir(), "ring did not prime TNT");
            var primed = level.getEntitiesOfClass(net.minecraft.world.entity.item.PrimedTnt.class, new AABB(pos).inflate(2));
            check(!primed.isEmpty(), "primed TNT not spawned"); primed.forEach(net.minecraft.world.entity.Entity::discard);
        });
        test("custom_emc_commands_and_disk_reload", () -> {
            var key = moze_intel.projecte.api.nss.NSSItem.createItem(Items.STONE);
            long previous = moze_intel.projecte.config.CustomEMCParser.currentEntries.entries().getLong(key);
            try {
                var dispatcher = server.getCommands().getDispatcher(); var source = server.createCommandSourceStack();
                check(dispatcher.execute("projecte setemc 7 minecraft:stone", source) == 1, "setemc command failed");
                moze_intel.projecte.config.CustomEMCParser.init(server.registryAccess());
                check(moze_intel.projecte.config.CustomEMCParser.currentEntries.entries().getLong(key) == 7, "custom EMC did not persist");
                check(dispatcher.execute("projecte resetemc minecraft:stone", source) == 1, "resetemc command failed");
                check(!moze_intel.projecte.config.CustomEMCParser.currentEntries.entries().containsKey(key), "custom EMC not removed");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) { throw new AssertionError(error); }
            finally {
                if (previous >= 0) moze_intel.projecte.config.CustomEMCParser.addToFile(key, previous);
                else moze_intel.projecte.config.CustomEMCParser.removeFromFile(key);
                moze_intel.projecte.config.CustomEMCParser.flush(server.registryAccess());
            }
        });
        for (var block : BuiltInRegistries.BLOCK) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals("projecte") || !(block instanceof net.minecraft.world.level.block.EntityBlock entityBlock)) continue;
            test("block_entity_save_round_trip/" + id.getPath(), () -> {
                var entity = entityBlock.newBlockEntity(pos, block.defaultBlockState());
                check(entity != null, "missing block entity");
                if (entity instanceof EmcBlockEntity storage) storage.insertEmc(1234, EmcAction.EXECUTE);
                var tag = entity.saveWithFullMetadata(server.registryAccess());
                var copy = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos, block.defaultBlockState(), tag, server.registryAccess());
                check(copy != null && copy.getClass() == entity.getClass(), "block entity not restored");
                check(tag.equals(copy.saveWithFullMetadata(server.registryAccess())), "block entity save changed on load");
            });
        }
        test("server_key_packet_charge_mode_and_armor_toggles", () -> {
            var p = player();
            var context = proxy(net.neoforged.neoforge.network.handling.IPayloadContext.class, (obj, method, args) -> method.getName().equals("player") ? p : null);
            var stack = new ItemStack(PEItems.RED_MATTER_SWORD.get()); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var buffer = io.netty.buffer.Unpooled.buffer();
            try {
                var packet = new moze_intel.projecte.network.packets.to_server.KeyPressPKT(moze_intel.projecte.utils.PEKeybind.CHARGE);
                moze_intel.projecte.network.packets.to_server.KeyPressPKT.STREAM_CODEC.encode(buffer, packet);
                moze_intel.projecte.network.packets.to_server.KeyPressPKT.STREAM_CODEC.decode(buffer).handle(context);
                check(stack.get(PEDataComponentTypes.CHARGE) == 1, "key packet did not charge item");
            } finally { buffer.release(); }
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(PEItems.GEM_HELMET.get()));
            new moze_intel.projecte.network.packets.to_server.KeyPressPKT(moze_intel.projecte.utils.PEKeybind.HELMET_TOGGLE).handle(context);
            check(p.getItemBySlot(EquipmentSlot.HEAD).getOrDefault(PEDataComponentTypes.NIGHT_VISION, false), "helmet toggle packet failed");
            p.setItemSlot(EquipmentSlot.FEET, new ItemStack(PEItems.GEM_BOOTS.get()));
            boolean previous = p.getItemBySlot(EquipmentSlot.FEET).getOrDefault(PEDataComponentTypes.STEP_ASSIST, false);
            new moze_intel.projecte.network.packets.to_server.KeyPressPKT(moze_intel.projecte.utils.PEKeybind.BOOTS_TOGGLE).handle(context);
            check(p.getItemBySlot(EquipmentSlot.FEET).getOrDefault(PEDataComponentTypes.STEP_ASSIST, false) != previous, "boots toggle packet failed");
            var rod = new ItemStack(PEItems.MEDIUM_DIVINING_ROD.get()); p.setItemInHand(InteractionHand.MAIN_HAND, rod);
            new moze_intel.projecte.network.packets.to_server.KeyPressPKT(moze_intel.projecte.utils.PEKeybind.MODE).handle(context);
            check(rod.get(PEDataComponentTypes.DIVINING_ROD_MODE).ordinal() == 1, "mode packet failed");
        });
        for (var item : List.of(PEItems.EVERTIDE_AMULET.get(), PEItems.VOLCANITE_AMULET.get())) test("amulet_fluid_placement/" + BuiltInRegistries.ITEM.getKey(item).getPath(), () -> {
            var p = player(); var stack = charged(item); level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            level.removeBlock(pos.above(), false); item.useOn(context(p, stack, pos));
            var fluid = item == PEItems.EVERTIDE_AMULET.get() ? net.minecraft.world.level.material.Fluids.WATER : net.minecraft.world.level.material.Fluids.LAVA;
            check(level.getFluidState(pos.above()).is(fluid), "amulet did not place fluid");
            if (item == PEItems.VOLCANITE_AMULET.get()) check(stack.get(PEDataComponentTypes.STORED_EMC) == 99968L, "lava EMC cost mismatch");
            level.removeBlock(pos.above(), false); level.removeBlock(pos, false);
        });
        test("mind_stone_full_capacity_preserves_player_xp", () -> {
            var p = player(); p.giveExperiencePoints(100); var stone = charged(PEItems.MIND_STONE);
            stone.set(PEDataComponentTypes.STORED_EXP, Integer.MAX_VALUE - 10); p.setItemInHand(InteractionHand.MAIN_HAND, stone);
            stone.inventoryTick(level, p, EquipmentSlot.MAINHAND);
            check(stone.get(PEDataComponentTypes.STORED_EXP) == Integer.MAX_VALUE && p.totalExperience == 90, "XP lost when mind stone reaches capacity");
            stone.inventoryTick(level, p, EquipmentSlot.MAINHAND);
            check(p.totalExperience == 90, "full mind stone consumed XP");
        });
        test("transmutation_learn_unlearn_tome_and_star_balance", () -> {
            var p = player(); var inventory = new moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory(p);
            inventory.handleKnowledge(new ItemStack(Items.DIAMOND));
            check(inventory.provider.hasKnowledge(ItemInfo.fromItem(Items.DIAMOND)), "diamond not learned");
            inventory.handleUnlearn(new ItemStack(Items.DIAMOND));
            check(!inventory.provider.hasKnowledge(ItemInfo.fromItem(Items.DIAMOND)), "diamond not unlearned");
            inventory.handleKnowledge(new ItemStack(PEItems.TOME_OF_KNOWLEDGE.get()));
            check(inventory.provider.hasFullKnowledge() && inventory.provider.hasKnowledge(ItemInfo.fromItem(Items.DIAMOND)), "tome did not grant knowledge");
            inventory.handleUnlearn(new ItemStack(PEItems.TOME_OF_KNOWLEDGE.get()));
            check(!inventory.provider.hasFullKnowledge(), "tome knowledge not removed");
            inventory.handleKnowledge(new ItemStack(Items.DIAMOND));
            inventory.setStackInSlot(0, new ItemStack(PEItems.KLEIN_STAR_EIN.get()));
            inventory.addEmc(BigInteger.valueOf(10000));
            check(inventory.getStackInSlot(0).get(PEDataComponentTypes.STORED_EMC) == 10000L, "star did not charge in table");
            inventory.writeIntoOutputSlot(0, new ItemStack(Items.DIAMOND));
            var output = new moze_intel.projecte.gameObjs.container.slots.transmutation.SlotOutput(inventory, 11, 0, 0);
            check(output.remove(1).is(Items.DIAMOND), "cannot transmute diamond");
            check(inventory.getAvailableEmc().equals(BigInteger.valueOf(10000 - moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE.getValue(Items.DIAMOND))), "transmutation EMC mismatch");
            check(output.remove(1).isEmpty(), "insufficient EMC allowed another diamond");
        });
        test("mercurial_eye_creation_debits_internal_star", () -> {
            var p = player(); p.setPos(30, 100, 32); level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()); level.removeBlock(pos.above(), false);
            var eye = new ItemStack(PEItems.MERCURIAL_EYE.get());
            var inv = (IItemHandlerModifiable) ItemTransferAdapter.legacy(eye.getCapability(Capabilities.Item.ITEM, net.neoforged.neoforge.transfer.access.ItemAccess.forStack(eye)));
            var star = new ItemStack(PEItems.KLEIN_STAR_EIN.get()); star.set(PEDataComponentTypes.STORED_EMC, 100_000L);
            inv.setStackInSlot(0, star); inv.setStackInSlot(1, new ItemStack(Items.DIAMOND_BLOCK));
            eye.getItem().useOn(context(p, eye, pos));
            check(level.getBlockState(pos.above()).is(Blocks.DIAMOND_BLOCK), "eye failed to create target block");
            check(inv.getStackInSlot(0).get(PEDataComponentTypes.STORED_EMC) == 100_000L - moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE.getValue(Items.DIAMOND_BLOCK), "eye EMC cost not persisted");
            check(inv.getStackInSlot(1).getCount() == 1, "eye consumed template");
            level.removeBlock(pos.above(), false); level.removeBlock(pos, false);
        });
        for (var item : List.of(PEItems.DARK_MATTER_AXE.get(), PEItems.RED_MATTER_AXE.get(), PEItems.RED_MATTER_KATAR.get())) test("tool_log_stripping/" + BuiltInRegistries.ITEM.getKey(item).getPath(), () -> {
            level.setBlockAndUpdate(pos, Blocks.OAK_LOG.defaultBlockState()); var p = player();
            item.useOn(context(p, new ItemStack(item), pos));
            check(level.getBlockState(pos).is(Blocks.STRIPPED_OAK_LOG), "log not stripped"); level.removeBlock(pos, false);
        });
        for (var item : List.of(PEItems.DARK_MATTER_SHOVEL.get(), PEItems.RED_MATTER_SHOVEL.get(), PEItems.RED_MATTER_MORNING_STAR.get())) test("tool_path_creation/" + BuiltInRegistries.ITEM.getKey(item).getPath(), () -> {
            level.removeBlock(pos.above(), false); level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState()); var p = player();
            item.useOn(context(p, new ItemStack(item), pos));
            check(level.getBlockState(pos).is(Blocks.DIRT_PATH), "path not created"); level.removeBlock(pos, false);
        });
        for (var item : List.of(PEItems.DARK_MATTER_SHEARS.get(), PEItems.RED_MATTER_SHEARS.get(), PEItems.RED_MATTER_KATAR.get())) test("tool_shearing/" + BuiltInRegistries.ITEM.getKey(item).getPath(), () -> {
            var sheep = EntityType.SHEEP.create(level, EntitySpawnReason.TRIGGERED); sheep.setPos(34, 100, 32); level.addFreshEntity(sheep);
            var p = player(); var stack = new ItemStack(item); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
            item.use(level, p, InteractionHand.MAIN_HAND); check(sheep.isSheared(), "sheep not sheared"); sheep.discard();
        });
        for (var item : List.of(PEItems.DARK_MATTER_SWORD.get(), PEItems.RED_MATTER_SWORD.get(), PEItems.RED_MATTER_KATAR.get())) test("charged_weapon_damage/" + BuiltInRegistries.ITEM.getKey(item).getPath(), () -> {
            var target = EntityType.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED); target.setPos(34, 100, 32); level.addFreshEntity(target);
            var p = player(); var stack = new ItemStack(item); stack.set(PEDataComponentTypes.CHARGE, 2); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
            float before = target.getHealth(); item.hurtEnemy(stack, target, p);
            check(target.getHealth() < before, "charged weapon did no damage"); target.discard();
        });
        test("hostile_only_aoe_preserves_passive_mobs", () -> {
            var p = player(); var stack = charged(PEItems.RED_MATTER_KATAR); stack.set(PEDataComponentTypes.CHARGE, 2);
            var zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED); zombie.setPos(34, 100, 32); level.addFreshEntity(zombie);
            var sheep = EntityType.SHEEP.create(level, EntitySpawnReason.TRIGGERED); sheep.setPos(34, 100, 33); level.addFreshEntity(sheep);
            moze_intel.projecte.utils.ToolHelper.attackAOE(stack, p, false, 5, 0, InteractionHand.MAIN_HAND);
            check(zombie.getHealth() < zombie.getMaxHealth() && sheep.getHealth() == sheep.getMaxHealth(), "hostile filter incorrect");
            moze_intel.projecte.utils.ToolHelper.attackAOE(stack, p, true, 5, 0, InteractionHand.MAIN_HAND);
            check(sheep.getHealth() < sheep.getMaxHealth(), "slay-all mode did not hit passive mob"); zombie.discard(); sheep.discard();
        });
        test("nova_explosion_preserves_block_and_inventory_drops", () -> {
            var area = new AABB(pos).inflate(5); level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);
            level.setBlockAndUpdate(pos, Blocks.DIAMOND_BLOCK.defaultBlockState()); level.setBlockAndUpdate(pos.east(), Blocks.CHEST.defaultBlockState());
            ((net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(pos.east())).setItem(0, new ItemStack(Items.EMERALD, 7));
            var explosion = new moze_intel.projecte.utils.NovaExplosion(level, null, 32.5, 100.5, 32.5, 4, net.minecraft.world.level.Explosion.BlockInteraction.DESTROY);
            explosion.interactWithBlocks(new java.util.ArrayList<>(List.of(pos, pos.east())));
            var drops = level.getEntitiesOfClass(ItemEntity.class, area);
            check(level.getBlockState(pos).isAir() && level.getBlockState(pos.east()).isAir(), "nova did not destroy blocks");
            check(drops.stream().filter(e -> e.getItem().is(Items.DIAMOND_BLOCK)).mapToInt(e -> e.getItem().getCount()).sum() == 1, "nova lost block drop");
            check(drops.stream().filter(e -> e.getItem().is(Items.EMERALD)).mapToInt(e -> e.getItem().getCount()).sum() == 7, "nova lost or duplicated inventory");
        });
        test("repair_talisman_alchemical_bag_and_chest", () -> {
            var p = player(); var bag = p.getData(PEAttachmentTypes.ALCHEMICAL_BAGS).getBag(DyeColor.RED);
            var tool = new ItemStack(Items.IRON_PICKAXE); tool.setDamageValue(10); bag.setStackInSlot(1, tool);
            PEItems.REPAIR_TALISMAN.get().updateInAlchBag(bag, p, new ItemStack(PEItems.REPAIR_TALISMAN.get()));
            check(bag.getStackInSlot(1).getDamageValue() == 9, "bag repair failed");
            level.setBlockAndUpdate(pos, PEBlocks.ALCHEMICAL_CHEST.defaultState());
            var chest = ItemTransferAdapter.legacy(level.getCapability(Capabilities.Item.BLOCK, pos, null)); chest.insertItem(1, tool.copy(), false);
            PEItems.REPAIR_TALISMAN.get().updateInAlchChest(level, pos, new ItemStack(PEItems.REPAIR_TALISMAN.get()));
            check(chest.getStackInSlot(1).getDamageValue() == 9, "chest repair failed"); level.removeBlock(pos, false);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    private void auditCraftingEconomy() {
        var findings = new java.util.ArrayList<java.util.Map<String, Object>>();
        var checked = new java.util.ArrayList<java.util.Map<String, Object>>();
        var repairFindings = new java.util.ArrayList<String>();
        for (var holder : server.getRecipeManager().recipeMap().values()) {
            if (!(holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe recipe)) continue;
            var placement = recipe.placementInfo();
            if (placement.isImpossibleToPlace() || placement.slotsToIngredientIndex().size() > 9) continue;
            try {
                int width = 3, height = 3;
                if (recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) { width = shaped.getWidth(); height = shaped.getHeight(); }
                var slots = new java.util.ArrayList<ItemStack>(java.util.Collections.nCopies(width * height, ItemStack.EMPTY));
                boolean allValued = true;
                long inputEmc = 0;
                for (int i = 0; i < placement.slotsToIngredientIndex().size(); i++) {
                    int index = placement.slotsToIngredientIndex().getInt(i);
                    if (index < 0) continue;
                    var ingredient = placement.ingredients().get(index);
                    var cheapest = ingredient.items().map(ItemStack::new).filter(ingredient::test)
                            .filter(stack -> moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE.getValue(stack) > 0)
                            .min(java.util.Comparator.comparingLong(stack -> moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE.getValue(stack)));
                    if (cheapest.isEmpty()) { allValued = false; break; }
                    slots.set(i, cheapest.get());
                    inputEmc = Math.addExact(inputEmc, moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE.getValue(cheapest.get()));
                }
                if (!allValued) continue;
                var input = net.minecraft.world.item.crafting.CraftingInput.of(width, height, slots);
                if (!recipe.matches(input, server.overworld())) continue;
                var output = recipe.assemble(input);
                long outputEmc = Math.multiplyExact(EMCHelper.getEmcSellValue(moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE.getValue(output)), output.getCount());
                long returnedEmc = 0;
                long repairedReturnedEmc = 0;
                var returns = recipe.getRemainingItems(input);
                for (int slot = 0; slot < returns.size(); slot++) {
                    var remainder = returns.get(slot);
                    returnedEmc = Math.addExact(returnedEmc, Math.multiplyExact(EMCHelper.getEmcSellValue(moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE.getValue(remainder)), remainder.getCount()));
                    var repaired = remainder.copy();
                    if (!repaired.isEmpty() && repaired.isDamageableItem()) repaired.setDamageValue(0);
                    repairedReturnedEmc = Math.addExact(repairedReturnedEmc, Math.multiplyExact(EMCHelper.getEmcSellValue(moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE.getValue(repaired)), repaired.getCount()));
                }
                long gain = Math.subtractExact(Math.addExact(outputEmc, returnedEmc), inputEmc);
                long repairedGain = Math.subtractExact(Math.addExact(outputEmc, repairedReturnedEmc), inputEmc);
                if (repairedGain > 0 && outputEmc > 0) repairFindings.add(holder.id().identifier().toString());
                checked.add(java.util.Map.of("recipe", holder.id().identifier().toString(), "inputEmc", inputEmc,
                        "outputSellEmc", outputEmc, "remainderSellEmc", returnedEmc, "gain", gain, "repairedGain", repairedGain));
                if (gain > 0 && outputEmc > 0) findings.add(java.util.Map.of(
                        "recipe", holder.id().identifier().toString(), "inputEmc", inputEmc, "outputSellEmc", outputEmc,
                        "remainderSellEmc", returnedEmc, "gain", gain,
                        "ingredients", slots.stream().filter(s -> !s.isEmpty()).map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).toList(),
                        "output", BuiltInRegistries.ITEM.getKey(output.getItem()).toString(), "outputCount", output.getCount()));
            } catch (RuntimeException exception) {
                LOG.warn("PORT_ECONOMY recipe could not be evaluated: {}", holder.id().identifier(), exception);
            }
        }
        findings.sort(java.util.Comparator.comparingLong(entry -> -((Number) entry.get("gain")).longValue()));
        test("economy_all_enumerated_crafting_recipes_no_profit", () -> check(findings.isEmpty(), "profitable crafting recipes: " + findings));
        test("economy_returned_tools_cannot_profit_after_free_repair", () -> check(repairFindings.isEmpty(), "repair cycles: " + repairFindings));
        try {
            java.nio.file.Files.writeString(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("projecte-economy-audit.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(findings));
            java.nio.file.Files.writeString(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("projecte-economy-checked.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(checked));
            LOG.info("PORT_ECONOMY: {} craftable positive-EMC recipes recorded, {} recipes evaluated", findings.size(), checked.size());
        } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }
    private void economyRegressionTests() {
        var emc = moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE;
        for (var item : List.of(Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.EMERALD, Items.COAL, Items.CHARCOAL, Items.DIRT, Items.GRASS_BLOCK,
                PEItems.ALCHEMICAL_COAL.get(), PEItems.MOBIUS_FUEL.get(), PEItems.AETERNALIS_FUEL.get(),
                PEItems.PHILOSOPHERS_STONE.get(), PEItems.DARK_MATTER.get(), PEItems.RED_MATTER.get())) {
            test("economy_core_item_retains_value/" + BuiltInRegistries.ITEM.getKey(item), () -> check(emc.getValue(item) > 0, "core item lost EMC"));
        }
        for (var holder : server.getRecipeManager().recipeMap().values()) {
            if (!(holder.value() instanceof net.minecraft.world.item.crafting.FireworkRocketRecipe recipe)) continue;
            for (int fuel = 1; fuel <= 3; fuel++) for (int stars = 0; stars <= 2; stars++) {
                final int fuelCount = fuel, starCount = stars;
                test("economy_firework_" + fuel + "_fuel_" + stars + "_stars/" + holder.id().identifier(), () -> {
                    var stacks = new java.util.ArrayList<ItemStack>(); stacks.add(new ItemStack(Items.PAPER));
                    for (int i = 0; i < fuelCount; i++) stacks.add(new ItemStack(Items.GUNPOWDER));
                    for (int i = 0; i < starCount; i++) {
                        var star = new ItemStack(Items.FIREWORK_STAR);
                        star.set(DataComponents.FIREWORK_EXPLOSION, net.minecraft.world.item.component.FireworkExplosion.DEFAULT);
                        stacks.add(star);
                    }
                    while (stacks.size() < 9) stacks.add(ItemStack.EMPTY);
                    var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, stacks);
                    check(recipe.matches(input, server.overworld()), "invalid firework fixture");
                    var output = recipe.assemble(input);
                    long cost = stacks.stream().filter(s -> !s.isEmpty()).mapToLong(emc::getValue).sum();
                    check(emc.getValue(output) > 0 && emc.getSellValue(output) * output.getCount() <= cost, "fireworks create EMC");
                });
            }
        }
        try {
            var prices = new java.util.TreeMap<String, Long>();
            for (var item : BuiltInRegistries.ITEM) {
                long value = emc.getValue(item);
                if (value > 0) prices.put(BuiltInRegistries.ITEM.getKey(item).toString(), value);
            }
            java.nio.file.Files.writeString(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("projecte-final-prices.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(prices));
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private void optionalIntegrationTests() {
        if (net.neoforged.fml.ModList.get().isLoaded("theoneprobe")) test("optional_top_emc_provider", () -> {
            var lines = new java.util.ArrayList<net.minecraft.network.chat.Component>();
            var info = proxy(mcjty.theoneprobe.api.IProbeInfo.class, (obj, method, args) -> {
                if (method.getName().equals("mcText")) lines.add((net.minecraft.network.chat.Component) args[0]);
                return obj;
            });
            var provider = new moze_intel.projecte.integration.top.PEProbeInfoProvider();
            provider.addProbeInfo(mcjty.theoneprobe.api.ProbeMode.NORMAL, info, player(), server.overworld(), Blocks.DIAMOND_BLOCK.defaultBlockState(), null);
            check(lines.equals(List.of(EMCHelper.getEmcTextComponent(moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE.getValue(Items.DIAMOND_BLOCK), 1))), "TOP EMC display mismatch");
            var registered = new java.util.ArrayList<Object>();
            provider.apply(proxy(mcjty.theoneprobe.api.ITheOneProbe.class, (obj, method, args) -> { if (method.getName().equals("registerProvider")) registered.add(args[0]); return null; }));
            check(registered.contains(provider), "TOP registration callback failed");
        });
        if (net.neoforged.fml.ModList.get().isLoaded("wthit")) test("optional_wthit_emc_provider", () -> {
            var lines = new java.util.ArrayList<net.minecraft.network.chat.Component>();
            var tooltip = proxy(mcp.mobius.waila.api.ITooltip.class, (obj, method, args) -> {
                if (method.getName().equals("addLine")) lines.add((net.minecraft.network.chat.Component) args[0]); return null;
            });
            var accessor = proxy(mcp.mobius.waila.api.IBlockAccessor.class, (obj, method, args) -> method.getName().equals("getBlock") ? Blocks.DIAMOND_BLOCK : null);
            new moze_intel.projecte.integration.wthit.WTHITDataProvider().appendBody(tooltip, accessor, null);
            check(lines.equals(List.of(EMCHelper.getEmcTextComponent(moze_intel.projecte.api.proxy.IEMCProxy.INSTANCE.getValue(Items.DIAMOND_BLOCK), 1))), "WTHIT EMC display mismatch");
        });
    }
}
