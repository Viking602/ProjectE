from pathlib import Path
import re
root=Path('src/main/java')
def file(n):return next(root.rglob(n+'.java'))
def edit(n, pairs):
 p=file(n);s=p.read_text(encoding='utf-8')
 for a,b in pairs:s=s.replace(a,b)
 p.write_text(s,encoding='utf-8')
edit('CraftingMapper',[('serverResources.getServer().getRecipeManager()', 'serverResources.getRecipeManager()')])
edit('CollectorMK1BlockEntity',[('EnvironmentAttributes.WATER_EVAPORATES, getBlockPos()', 'EnvironmentAttributes.WATER_EVAPORATES, pos'),('public int getSunLevel() {','''@Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        clearLocked();
        super.preRemoveSideEffects(pos, state);
    }

    public int getSunLevel() {''')])
p=file('Collector');s=p.read_text(encoding='utf-8');s=re.sub(r'\t@Override\n\t@Deprecated\n\tpublic void onRemove\(.*?\n\t}\n','',s,flags=re.S);p.write_text(s,encoding='utf-8')
edit('DiviningRod',[('furnaceRecipes = ((net.minecraft.server.level.ServerLevel) level).getServer().getRecipeManager().recipeMap().byType(RecipeType.SMELTING);','furnaceRecipes = java.util.List.copyOf(((net.minecraft.server.level.ServerLevel) level).getServer().getRecipeManager().recipeMap().byType(RecipeType.SMELTING));')])
edit('TimeWatch',[('ChunkPos.containing(pos).toLong()', 'ChunkPos.containing(pos).pack()')])
edit('ShowBagCMD',[('new ItemStack(PEItems', 'new net.minecraft.world.item.ItemStack(PEItems')])
edit('WorldHelper',[('level.getGameRules().getBoolean(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY)', 'serverLevel.getGameRules().get(GameRules.TNT_EXPLOSION_DROP_DECAY)'),('new NovaExplosion(level,', 'new NovaExplosion(serverLevel,'),('explosion.finalizeExplosion()', 'explosion.particlePositions()'),('explosion.getExplosionSound()', 'net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE')])
edit('EntityNovaPrimed',[('protected void explode()', 'public void explode()')])
at=Path('src/main/resources/META-INF/accesstransformer.cfg');s=at.read_text(encoding='utf-8');s=re.sub(r'^public net.minecraft.world.item.crafting.Firework.*\n','',s,flags=re.M);s+='''
public net.minecraft.world.entity.item.PrimedTnt explode()V
public net.minecraft.world.level.ServerExplosion interactWithBlocks(Ljava/util/List;)V
public net.minecraft.world.item.crafting.SmithingTrimRecipe pattern
public net.minecraft.world.item.crafting.FireworkRocketRecipe fuel
public net.minecraft.world.item.crafting.FireworkRocketRecipe result
public net.minecraft.world.item.crafting.FireworkStarRecipe shapes
public net.minecraft.world.item.crafting.FireworkStarRecipe trail
public net.minecraft.world.item.crafting.FireworkStarRecipe twinkle
public net.minecraft.world.item.crafting.FireworkStarRecipe result
''';at.write_text(s,encoding='utf-8')
edit('FireworkProcessor',[('powderEmc = IComponentProcessorHelper.INSTANCE.getMinEmcFor(emcLookup, FireworkRocketRecipe.GUNPOWDER_INGREDIENT);','''powderEmc = 0;
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) for (var holder : server.getRecipeManager().recipeMap().byType(net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
            if (holder.value() instanceof FireworkRocketRecipe recipe && recipe.result.is(Items.FIREWORK_ROCKET)) {
                long value = IComponentProcessorHelper.INSTANCE.getMinEmcFor(emcLookup, recipe.fuel);
                if (value > 0) powderEmc = powderEmc == 0 ? value : Math.min(powderEmc, value);
            }
        }''')])
p=file('FireworkStarProcessor');s=p.read_text(encoding='utf-8');a=s.index('\t\tfor (Map.Entry<Item, FireworkExplosion.Shape>');b=s.index('\n\t}',a);s=s[:a]+'''        trailEmc = twinkleEmc = 0;
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) for (var holder : server.getRecipeManager().recipeMap().byType(net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
            if (holder.value() instanceof FireworkStarRecipe recipe && recipe.result.is(net.minecraft.world.item.Items.FIREWORK_STAR)) {
                recipe.shapes.forEach((shape, ingredient) -> {
                    if (shape != FireworkExplosion.Shape.SMALL_BALL) {
                        long emc = IComponentProcessorHelper.INSTANCE.getMinEmcFor(emcLookup, ingredient);
                        if (emc > 0) shapeEmcLookup.mergeLong(shape, emc, Math::min);
                    }
                });
                long trail = IComponentProcessorHelper.INSTANCE.getMinEmcFor(emcLookup, recipe.trail);
                long twinkle = IComponentProcessorHelper.INSTANCE.getMinEmcFor(emcLookup, recipe.twinkle);
                if (trail > 0) trailEmc = trailEmc == 0 ? trail : Math.min(trailEmc, trail);
                if (twinkle > 0) twinkleEmc = twinkleEmc == 0 ? twinkle : Math.min(twinkleEmc, twinkle);
            }
        }'''+s[b:];p.write_text(s,encoding='utf-8')
p=file('ArmorTrimProcessor');s=p.read_text(encoding='utf-8');a=s.index('\t\tHolder<Item> material');b=s.index('\n\t}',a);s=s[:a]+'''        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return 0;
        long minimum = Long.MAX_VALUE;
        var base = info.createStack();
        for (var holder : server.getRecipeManager().recipeMap().byType(net.minecraft.world.item.crafting.RecipeType.SMITHING)) {
            if (holder.value() instanceof net.minecraft.world.item.crafting.SmithingTrimRecipe recipe
                    && recipe.pattern.equals(trim.pattern()) && recipe.base.test(base)) {
                long templateEmc = moze_intel.projecte.api.proxy.IComponentProcessorHelper.INSTANCE.getMinEmcFor(IEMCProxy.INSTANCE::getValue, recipe.template);
                if (templateEmc == 0) continue;
                long materialEmc = Long.MAX_VALUE;
                for (var stack : moze_intel.projecte.utils.RecipeHelper.stacks(recipe.addition)) {
                    if (trim.material().equals(stack.get(DataComponents.PROVIDES_TRIM_MATERIAL))) {
                        long value = IEMCProxy.INSTANCE.getValue(stack);
                        if (value > 0) materialEmc = Math.min(materialEmc, value);
                    }
                }
                if (materialEmc != Long.MAX_VALUE) minimum = Math.min(minimum, Math.addExact(templateEmc, materialEmc));
            }
        }
        return minimum == Long.MAX_VALUE ? 0 : Math.addExact(currentEMC, minimum);'''+s[b:];p.write_text(s,encoding='utf-8')
