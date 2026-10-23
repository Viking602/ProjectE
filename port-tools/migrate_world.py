from pathlib import Path
import re
root=Path('src/main/java')
def file(name):
    return next(root.rglob(name+'.java'))
def edit(name, changes):
    p=file(name); s=p.read_text(encoding='utf-8')
    for a,b in changes: s=s.replace(a,b)
    p.write_text(s,encoding='utf-8')
for p in root.rglob('*.java'):
    s=p.read_text(encoding='utf-8')
    for a,b in [('.getRecipeManager()', '.getServer().getRecipeManager()'),
                ('getItemUseAnimationation','getUseAnimation'),
                ('.createItemStack(1, false)', '.createItemStack(1)'),
                ('key.location()', 'key.identifier()'),
                ('chunk.setUnsaved(true)', 'chunk.markUnsaved()'),
                ('.noCollission()', '.noCollision()'),
                ('level().random', 'level().getRandom()'),
                ('this.inGround', 'this.isInGround()')]:
        s=s.replace(a,b)
    # The server itself already owns the manager.
    s=s.replace('server.getServer().getRecipeManager()', 'server.getRecipeManager()')
    p.write_text(s,encoding='utf-8')
edit('PECore', [('AddReloadListenerEvent','AddServerReloadListenersEvent'),('event.addListener((ResourceManagerReloadListener)', 'event.addListener(rl("emc_reload_context"), (ResourceManagerReloadListener)'),('event.addListener(WorldTransmutationManager.INSTANCE)', 'event.addListener(rl("world_transmutations"), WorldTransmutationManager.INSTANCE)')])
edit('WorldTransmutationManager', [('extends SimpleJsonResourceReloadListener {','extends SimpleJsonResourceReloadListener<JsonElement> {'),('super(GSON, "pe_world_transmutations")','super(net.minecraft.util.ExtraCodecs.JSON, net.minecraft.resources.FileToIdConverter.json("pe_world_transmutations"))')])
edit('ContainerProcessor',[('component.stream().map(net.minecraft.world.item.ItemStackTemplate::create)', 'component.nonEmptyItemCopyStream()')])
edit('DecoratedShieldProcessor',[('BannerBlock.byColor(baseColor)', 'net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(baseColor.getName() + "_banner"))')])
edit('ShulkerRecoloringMapper',[('ShulkerBoxBlock.getBlockByColor(color)', 'net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(color.getName() + "_shulker_box"))')])
edit('CraftingMapper',[('recipeManager.recipeMap().byType((RecipeType) recipeType)', 'java.util.List.copyOf(recipeManager.recipeMap().byType((RecipeType) recipeType))')])
edit('EmcChestBlockEntity',[('protected boolean isOwnContainer','public boolean isOwnContainer'),('incrementOpeners(player, level, getBlockPos(), getBlockState())', 'incrementOpeners(player, level, getBlockPos(), getBlockState(), player.getContainerInteractionRange())')])
edit('CollectorMK1BlockEntity',[('level.dimensionType().ultraWarm()', 'level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES, getBlockPos())')])
p=file('CondenserBlockEntity');s=p.read_text(encoding='utf-8');s=re.sub(r'\t\tif \(tag.contains\("lock"\)\) \{.*?\n\t\t}', '\t\tlockInfo = tag.read("lock", ItemInfo.CODEC).orElse(null);',s,flags=re.S);s=re.sub(r'\t\tif \(lockInfo != null\) \{\n\t\t\tDataResult<Tag>.*?\n\t\t}', '\t\tif (lockInfo != null) tag.store("lock", ItemInfo.CODEC, lockInfo);',s,flags=re.S);p.write_text(s,encoding='utf-8')
edit('DMFurnaceBlockEntity', [('copy.getItem().getCraftingRemainingItem(copy)', 'moze_intel.projecte.utils.ItemHelper.craftingRemainder(copy)'),('quickCheck.getRecipeFor(recipeInput, level)', 'quickCheck.getRecipeFor(recipeInput, (ServerLevel) level)'),('assemble(recipeInput, level.registryAccess())', 'assemble(recipeInput)'),('stack.getBurnTime(RecipeType.SMELTING)', 'stack.getBurnTime(RecipeType.SMELTING, level == null ? moze_intel.projecte.utils.RecipeHelper.fuelValues() : level.fuelValues())'),('.getCookingTime()', '.cookingTime()'),('.getExperience()', '.experience()'),('tag.getCompound("recipes_used")','tag.read("recipes_used", CompoundTag.CODEC).orElseGet(CompoundTag::new)'),('usedRecipes.getAllKeys()', 'usedRecipes.keySet()'),('usedRecipes.getInt(recipeId)', 'usedRecipes.getIntOr(recipeId, 0)'),('tag.put("recipes_used", usedRecipes)', 'tag.store("recipes_used", CompoundTag.CODEC, usedRecipes)'),('this.recipesUsed.addTo(recipeHolder.id(), 1)', 'this.recipesUsed.addTo(recipeHolder.id().identifier(), 1)'),('recipeManager.byKey(entry.getKey())', 'recipeManager.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, entry.getKey()))')])
edit('ItemHelper',[('public final class ItemHelper {','''public final class ItemHelper {
    public static ItemStack craftingRemainder(ItemStack stack) {
        var remainder = stack.getCraftingRemainder();
        return remainder == null ? ItemStack.EMPTY : remainder.create();
    }''')])
edit('RecipeHelper',[('private RecipeHelper() {}','''private RecipeHelper() {}
    public static net.minecraft.world.level.block.entity.FuelValues fuelValues() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) return server.fuelValues();
        return net.minecraft.world.level.block.entity.FuelValues.vanillaBurnTimes(
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS);
    }''')])
edit('SlotPredicates',[('input.getBurnTime(RecipeType.SMELTING)', 'input.getBurnTime(RecipeType.SMELTING, moze_intel.projecte.utils.RecipeHelper.fuelValues())')])
for name in ['EntityFireProjectile','EntityLensProjectile','EntitySWRGProjectile']:
    p=file(name); s=p.read_text(encoding='utf-8');s=re.sub(r'(readAdditionalSaveData\(@NotNull )CompoundTag',r'\1net.minecraft.world.level.storage.ValueInput',s);s=re.sub(r'(addAdditionalSaveData\(@NotNull )CompoundTag',r'\1net.minecraft.world.level.storage.ValueOutput',s);s=s.replace('compound.getBoolean("fromArcana")','compound.getBooleanOr("fromArcana", false)').replace('nbt.getInt("charge")','nbt.getIntOr("charge", 0)');p.write_text(s,encoding='utf-8')
for name in ['EntityFireProjectile','EntityLavaProjectile']:
    edit(name,[('ent.hurt(level().damageSources().inFire(), 5)', 'ent.hurtServer((net.minecraft.server.level.ServerLevel) level(), level().damageSources().inFire(), 5)')])
for name in ['EntityLavaProjectile','EntityWaterProjectile']:
    edit(name,[('weatherLevel.getWeatherData()', '((net.minecraft.server.level.ServerLevel) level).getWeatherData()')])
edit('EntitySWRGProjectile',[('level().getLevelData() instanceof ServerLevelData levelData', 'level() instanceof net.minecraft.server.level.ServerLevel weatherLevel'),('levelData.setThundering(true)', 'weatherLevel.getWeatherData().setThundering(true)'),('EntityType.LIGHTNING_BOLT.create(level())', 'EntityType.LIGHTNING_BOLT.create(level(), net.minecraft.world.entity.EntitySpawnReason.TRIGGERED)'),('bonus.moveTo(', 'bonus.setPos(')])
edit('EntityMobRandomizer',[('randomized.moveTo(ent.position(), ent.getYRot(), ent.getXRot())', 'randomized.snapTo(ent.position(), ent.getYRot(), ent.getXRot())')])
edit('NoGravityThrowableProjectile',[('super(type, shooter, level);','super(type, shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ(), level);\n\t\tsetOwner(shooter);')])
edit('BlockEntityTypeDeferredRegister',[('BlockEntityType.Builder.<BE>of(factory, block.getBlocks()).build(null)', 'new BlockEntityType<BE>(factory, block.getBlocks())')])
edit('EntityTypeDeferredRegister',[('build(rl.getPath())', 'build(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, rl))')])
for name in ['RecipesCovalenceRepair','PhiloStoneSmeltingRecipe']:
    edit('PERecipeSerializers',[(f'new RecipeSerializer<>({name}::new)', f'new RecipeSerializer<>(com.mojang.serialization.MapCodec.unit({name}::new), net.minecraft.network.codec.StreamCodec.unit(new {name}()))')])
edit('WorldTransmuteEntry',[('getCloneItemStack(null, null, null, null)', 'getCloneItemStack(null, null, false, null)')])
edit('SearchQueryParser',[('item.getCreatorModId(stack)', 'item.getCreatorModId(net.minecraft.client.Minecraft.getInstance().level.registryAccess(), stack)')])
edit('MatterFurnaceOutputSlot',[('stack.onCraftedBy(player.level(), player, removeCount)', 'stack.onCraftedBy(player, removeCount)'),('EventHooks.firePlayerSmeltedEvent(player, stack)', 'EventHooks.firePlayerSmeltedEvent(player, stack, removeCount)')])
edit('Arcana',[('new ItemStack(Items.SNOWBALL)', 'new ItemStack(net.minecraft.world.item.Items.SNOWBALL)')])
edit('TimeWatch',[('new ChunkPos(pos).toLong()', 'ChunkPos.containing(pos).toLong()')])
edit('VoidRing',[('inventoryTick(stack, level, entity, slot, isHeld)', 'inventoryTick(stack, level, entity, slot)'),('cooldowns.isOnCooldown(this)', 'cooldowns.isOnCooldown(new ItemStack(this))'),('cooldowns.addCooldown(this,', 'cooldowns.addCooldown(new ItemStack(this),'),('new EntityTeleportEvent(player,', 'new EntityTeleportEvent(player, (net.minecraft.server.level.ServerLevel) player.level(),')])
edit('PEShears',[('Tool.Rule.minesAndDrops(PETags.Blocks.MINEABLE_WITH_PE_SHEARS,', 'Tool.Rule.minesAndDrops(net.minecraft.core.registries.BuiltInRegistries.acquireBootstrapRegistrationLookup(net.minecraft.core.registries.BuiltInRegistries.BLOCK).getOrThrow(PETags.Blocks.MINEABLE_WITH_PE_SHEARS),'),('Tool.Rule.overrideSpeed(BlockTags.LEAVES,', 'Tool.Rule.overrideSpeed(net.minecraft.core.registries.BuiltInRegistries.acquireBootstrapRegistrationLookup(net.minecraft.core.registries.BuiltInRegistries.BLOCK).getOrThrow(BlockTags.LEAVES),'),('Tool.Rule.overrideSpeed(BlockTags.WOOL,', 'Tool.Rule.overrideSpeed(net.minecraft.core.registries.BuiltInRegistries.acquireBootstrapRegistrationLookup(net.minecraft.core.registries.BuiltInRegistries.BLOCK).getOrThrow(BlockTags.WOOL),'),('Tool.Rule.overrideSpeed(List.of(Blocks.VINE, Blocks.GLOW_LICHEN),', 'Tool.Rule.overrideSpeed(net.minecraft.core.HolderSet.direct(Blocks.VINE.builtInRegistryHolder(), Blocks.GLOW_LICHEN.builtInRegistryHolder()),'),('), 1.0F, 1)', '), 1.0F, 0, false)')])
edit('PEDamageTypes',[('.getHolderOrThrow(key())', '.getOrThrow(key())')])
for name in ['ShowBagCMD','TransmutationOffline']:
    p=file(name);s=p.read_text(encoding='utf-8').replace('.contains(AttachmentHolder.ATTACHMENTS_NBT_KEY, Tag.TAG_COMPOUND)', '.contains(AttachmentHolder.ATTACHMENTS_NBT_KEY)').replace('.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY)', '.getCompoundOrEmpty(AttachmentHolder.ATTACHMENTS_NBT_KEY)').replace('attachmentData.getCompound(', 'attachmentData.getCompoundOrEmpty(');p.write_text(s,encoding='utf-8')
edit('ShowBagCMD',[('PEItems.getBag(color).getDescription()', 'new ItemStack(PEItems.getBag(color)).getHoverName()'),('Optional<GameProfile> profileByUUID = server.getProfileCache() == null ? Optional.empty() : server.getProfileCache().get(target)', 'Optional<net.minecraft.server.players.NameAndId> profileByUUID = server.services().nameToIdCache().get(target)'),('profileByUUID.get().getName()', 'profileByUUID.get().name()')])
edit('CurioItemCapability',[('Inventory.SLOT_OFFHAND, false', 'net.minecraft.world.entity.EquipmentSlot.OFFHAND')])
edit('DumpMissingEmc',[('case BundleItem bundleItem when !enabledFeatures.contains(FeatureFlags.BUNDLE) -> true;', ''),('source.hasPermission(Commands.LEVEL_GAMEMASTERS)', 'Commands.LEVEL_GAMEMASTERS.check(source.permissions())')])
edit('ThreadCheckUpdate',[('new ClickEvent(ClickEvent.Action.OPEN_URL, curseURL)', 'new ClickEvent.OpenUrl(java.net.URI.create(curseURL))')])
edit('PEAttachmentTypes',[('.serialize(AlchemicalBagAttachment.CODEC)', '.serialize(com.mojang.serialization.MapCodec.assumeMapUnsafe(AlchemicalBagAttachment.CODEC))'),('.serialize(KnowledgeAttachment.CODEC)', '.serialize(com.mojang.serialization.MapCodec.assumeMapUnsafe(KnowledgeAttachment.CODEC))'),('.serialize(Codec.BOOL, val -> val)', '.serialize(Codec.BOOL.fieldOf("active"), val -> val)')])
