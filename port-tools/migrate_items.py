from pathlib import Path
import re
ROOT=Path(__file__).resolve().parents[1];JAVA=ROOT/'src/main/java/moze_intel/projecte'
def edit(rel,fn):
 p=JAVA/rel;p.write_text(fn(p.read_text(encoding='utf-8')),encoding='utf-8')
for p in JAVA.rglob('*.java'):
 s=p.read_text(encoding='utf-8')
 s=re.sub(r'\b(level|world|serverLevel)\.random\b',r'\1.getRandom()',s)
 s=s.replace('player.getInventory().items','player.getInventory().getNonEquipmentItems()')
 s=re.sub(r'(getInventory\(\)|playerInventory)\.selected\b',r'\1.getSelectedSlot()',s)
 s=s.replace('.serverLevel()', '.level()').replace('clientPlayer.input.jumping','clientPlayer.input.keyPresses.jump()')
 s=s.replace('hotBarOrOffHand(slot)', 'hotBarOrOffHand(slot, entity, stack)')
 s=s.replace('.getMaxBuildHeight()', '.getMaxY() + 1')
 s=s.replace('level.getProfiler()', 'net.minecraft.util.profiling.Profiler.get()').replace('this.net.minecraft.util.profiling.Profiler', 'net.minecraft.util.profiling.Profiler')
 s=s.replace('.getChunkSource().broadcastAndSend(','.getChunkSource().sendToTrackingPlayersAndSelf(')
 s=s.replace('player.getScoreboard()', 'player.level().getScoreboard()')
 s=s.replace('cooldowns.isOnCooldown(item)', 'cooldowns.isOnCooldown(new ItemStack(item))').replace('cooldowns.addCooldown(item, cooldown)','cooldowns.addCooldown(new ItemStack(item), cooldown)')
 s=s.replace('stack.isRepairable()', 'stack.has(net.minecraft.core.component.DataComponents.REPAIRABLE)')
 s=s.replace('stack.getTags()', 'stack.typeHolder().tags()').replace('current.getTags()', 'current.typeHolder().tags()')
 s=s.replace('Screen.hasShiftDown()', 'moze_intel.projecte.utils.ClientKeyHelper.hasShiftDown()')
 s=s.replace('PacketDistributor.sendToServer(', 'net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(')
 s=s.replace('getDefaultAttributeModifiers()', 'getDefaultAttributeModifiers(ItemStack attributeStack)') if 'public ItemAttributeModifiers getDefaultAttributeModifiers()' in s else s
 s=s.replace('super.getDefaultAttributeModifiers(ItemStack attributeStack)', 'super.getDefaultAttributeModifiers(new ItemStack(this))')
 s=s.replace('new Snowball(level, player)', 'new Snowball(level, player, new ItemStack(Items.SNOWBALL))')
 s=s.replace('ItemAbilities.DEFAULT_PICKAXE_ACTIONS.contains(toolAction) || ', '').replace('ItemAbilities.DEFAULT_SWORD_ACTIONS.contains(toolAction)', 'toolAction == ItemAbilities.SWORD_SWEEP')
 s=re.sub(r'canPerformAction\(@NotNull ItemStack stack, @NotNull ItemAbility (\w+)\) \{',r'canPerformAction(@NotNull net.minecraft.world.item.ItemInstance instance, @NotNull ItemAbility \1) {\n\t\tItemStack stack = moze_intel.projecte.utils.ItemHelper.asStack(instance);',s)
 s=re.sub(r'\t@Override\n\tpublic boolean hasCraftingRemainingItem\(@NotNull ItemStack stack\) \{\n\t\treturn true;\n\t}\n','',s)
 s=s.replace('public ItemStack getCraftingRemainingItem(ItemStack stack) {\n\t\treturn stack.copy();','public net.minecraft.world.item.ItemStackTemplate getCraftingRemainder(net.minecraft.world.item.ItemInstance stack) {\n\t\treturn net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(moze_intel.projecte.utils.ItemHelper.asStack(stack));')
 s=s.replace('newType.create(level)', 'newType.create(level, net.minecraft.world.entity.EntitySpawnReason.CONVERSION)').replace('ent.getType().create(level)', 'ent.getType().create(level, net.minecraft.world.entity.EntitySpawnReason.CONVERSION)').replace('EntityType.LIGHTNING_BOLT.create(level)', 'EntityType.LIGHTNING_BOLT.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED)')
 s=s.replace('lightning.moveTo(', 'lightning.setPos(')
 s=s.replace('level.getCurrentDifficultyAt(entityPosition)','((ServerLevel) level).getCurrentDifficultyAt(entityPosition)')
 s=s.replace('aboveState.isSolidRender(level, abovePos)','aboveState.isSolidRender()')
 s=s.replace('target.spawnShearedDrop(level, pos, drop)', 'target.spawnShearedDrop((net.minecraft.server.level.ServerLevel) level, pos, drop)')
 s=s.replace('RecipeTypes.FUELING','RecipeTypes.FUEL')
 s=s.replace('entType.is(', 'entType.builtInRegistryHolder().is(').replace('toExclude.is(tagKey)','toExclude.builtInRegistryHolder().is(tagKey)').replace('entity.getType().is(blacklistTag)','entity.getType().builtInRegistryHolder().is(blacklistTag)')
 s=s.replace('registry.getTag(tagKey)', 'registry.get(tagKey)').replace('ChunkPos.asLong(pos)', 'new ChunkPos(pos).toLong()')
 s=s.replace('chunkPos.x, chunkPos.z','chunkPos.x(), chunkPos.z()')
 s=s.replace('item.getDescription()', 'new ItemStack(item).getHoverName()')
 s=s.replace('level.getLevelData() instanceof ServerLevelData worldInfo', 'level instanceof net.minecraft.server.level.ServerLevel weatherLevel')
 s=re.sub(r'worldInfo\.(setRainTime|setThunderTime|setRaining|setThundering)\(',r'weatherLevel.getWeatherData().\1(',s)
 p.write_text(s,encoding='utf-8')

edit('gameObjs/items/ItemPE.java',lambda s:s.replace('public static boolean hotBarOrOffHand(int slot) {\n\t\treturn slot < Inventory.getSelectionSize() || slot == Inventory.SLOT_OFFHAND;', '''public static boolean hotBarOrOffHand(net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.world.entity.Entity owner, ItemStack stack) {
        if (slot == net.minecraft.world.entity.EquipmentSlot.OFFHAND || slot == net.minecraft.world.entity.EquipmentSlot.MAINHAND) return true;
        if (owner instanceof Player player) {
            for (int index = 0; index < Inventory.getSelectionSize(); index++) {
                if (player.getInventory().getItem(index) == stack) return true;
            }
        }
        return false;'''))
edit('utils/ItemHelper.java',lambda s:s.replace('public final class ItemHelper {','''public final class ItemHelper {
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
'''))
for name in ['events/PlayerEvents.java','events/TickEvents.java']:
 edit(name,lambda s:s.replace('event.getEntity().getArmorSlots()','moze_intel.projecte.utils.ItemHelper.armor(event.getEntity())').replace('player.getArmorSlots()', 'moze_intel.projecte.utils.ItemHelper.armor(player)').replace('evt.getEntity().getServer()', '((ServerPlayer) evt.getEntity()).level().getServer()'))
edit('utils/ClientKeyHelper.java',lambda s:s.replace('public class ClientKeyHelper {','''public class ClientKeyHelper {
    public static boolean hasShiftDown() {
        var window = net.minecraft.client.Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, 340) || InputConstants.isKeyDown(window, 344);
    }
''').replace('keyCode, PELang.PROJECTE.getTranslationKey()', 'keyCode, CATEGORY').replace('private static class', 'private static class'))
edit('utils/ClientKeyHelper.java',lambda s:s.replace('public class ClientKeyHelper {','public class ClientKeyHelper {\n    private static final net.minecraft.client.KeyMapping.Category CATEGORY = net.minecraft.client.KeyMapping.Category.register(moze_intel.projecte.PECore.rl("main"));'))
edit('gameObjs/items/rings/MindStone.java',lambda s:re.sub(r'orb.value = ([^;]+);',r'orb.setValue(\1);',s).replace('orb.value','orb.getValue()'))
edit('utils/WorldHelper.java',lambda s:s.replace('import net.minecraft.world.level.block.MossBlock;\n','').replace('import net.minecraft.world.level.block.WaterlilyBlock;\n','').replace('block instanceof WaterlilyBlock','block == Blocks.LILY_PAD').replace('block instanceof MossBlock','block == Blocks.MOSS_BLOCK || block == Blocks.PALE_MOSS_BLOCK').replace('for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet())','for (Property<?> property : state.getProperties())').replace('entry.getKey().getName().equals("age")','property.getName().equals("age")').replace('entry.getValue() instanceof IntegerProperty intProperty','property instanceof IntegerProperty intProperty').replace('state.onDestroyedByPlayer(level, pos, player, true,','state.onDestroyedByPlayer(level, pos, player, player.getMainHandItem(), true,').replace('level.dimensionType().ultraWarm()', 'level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES, pos)'))
edit('gameObjs/items/EvertideAmulet.java',lambda s:s.replace('level.dimensionType().ultraWarm()', 'level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES, player.position())'))
edit('utils/PlayerHelper.java',lambda s:s.replace('player.mayInteract(level, pos)','player.mayInteract((ServerLevel) level, pos)'))
edit('PEPermissions.java',lambda s:s.replace('int fallbackLevel','net.minecraft.server.permissions.PermissionCheck fallbackLevel').replace('player.hasPermissions(Commands.LEVEL_GAMEMASTERS)','Commands.LEVEL_GAMEMASTERS.check(player.permissions())').replace('player.hasPermissions(Commands.LEVEL_ALL)','Commands.LEVEL_ALL.check(player.permissions())').replace('source.hasPermission(fallbackLevel)','fallbackLevel.check(source.permissions())'))
edit('gameObjs/items/rings/TimeWatch.java',lambda s:s.replace('level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)', 'moze_intel.projecte.utils.ClockHelper.isRunning(level)').replace('serverWorld.setDayTime(', 'moze_intel.projecte.utils.ClockHelper.setTime(serverWorld, ').replace('level.getDayTime()', 'moze_intel.projecte.utils.ClockHelper.getTime(level)'))
edit('gameObjs/items/DiviningRod.java',lambda s:s.replace('level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)', '((net.minecraft.server.level.ServerLevel) level).getRecipeManager().recipeMap().byType(RecipeType.SMELTING)').replace('furnaceRecipe.getIngredients()', 'furnaceRecipe.placementInfo().ingredients()').replace('furnaceRecipe.getResultItem(level.registryAccess())','moze_intel.projecte.utils.RecipeHelper.result(furnaceRecipe, level.registryAccess())'))

ats=ROOT/'src/main/resources/META-INF/accesstransformer.cfg'
with ats.open('a',encoding='utf-8') as f:
 f.write('\npublic net.minecraft.world.entity.ExperienceOrb setValue(I)V\npublic net.minecraft.world.entity.animal.rabbit.Rabbit setVariant(Lnet/minecraft/world/entity/animal/rabbit/Rabbit$Variant;)V\n')
