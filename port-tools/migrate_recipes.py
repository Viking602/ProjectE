from pathlib import Path
import re
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'src/main/java/moze_intel/projecte'
def edit(rel,fn):
 p=JAVA/rel;p.write_text(fn(p.read_text(encoding='utf-8')),encoding='utf-8')
for p in (ROOT/'src').rglob('*.java'):
 s=p.read_text(encoding='utf-8')
 s=s.replace('ItemStack::getItemHolder','ItemStack::typeHolder').replace('FluidStack::getFluidHolder','FluidStack::typeHolder')
 s=s.replace('ItemStack.SINGLE_ITEM_CODEC','ItemStack.CODEC').replace('ItemStack.LIST_STREAM_CODEC','ItemStack.STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list())')
 s=s.replace('.registryOrThrow(','.lookupOrThrow(').replace('BuiltInRegistries.POTION.holders()', 'BuiltInRegistries.POTION.listElements()')
 s=re.sub(r'(BuiltInRegistries\.\w+)\.getTag\(',r'\1.get(',s)
 s=s.replace('ingredient.getItems()', 'moze_intel.projecte.utils.RecipeHelper.stacks(ingredient)').replace('conversion.ingredient().getItems()', 'moze_intel.projecte.utils.RecipeHelper.stacks(conversion.ingredient())')
 s=s.replace('Identifier recipeID = recipeHolder.id();','Identifier recipeID = recipeHolder.id().identifier();')
 s=s.replace('item.hasCraftingRemainingItem(stack)','stack.getCraftingRemainder() != null').replace('item.getCraftingRemainingItem(stack)','stack.getCraftingRemainder().create()')
 s=s.replace('recipe.getResultItem(registryAccess)','moze_intel.projecte.utils.RecipeHelper.result(recipe, registryAccess)')
 s=s.replace('recipeManager.getAllRecipesFor((RecipeType) recipeType)','recipeManager.recipeMap().byType((RecipeType) recipeType)')
 s=s.replace('typeRegistryKey.location()', 'typeRegistryKey.identifier()').replace('entry.getKey().location()', 'entry.getKey().identifier()')
 s=s.replace('return recipe.getIngredients();', 'return recipe.placementInfo().ingredients();')
 s=s.replace('List.of(transformRecipe.base, transformRecipe.addition, transformRecipe.template)', 'transformRecipe.placementInfo().ingredients()')
 s=s.replace('blocks.get(entry.getKey())','blocks.getValue(entry.getKey())')
 # The vanilla method was removed; supportsEnchantment already rejects all enchantments.
 s=re.sub(r'\t@Override\n(\tpublic boolean isBookEnchantable\()',r'\1',s)
 p.write_text(s,encoding='utf-8')

edit('gameObjs/customRecipes/PERecipeSerializer.java', lambda s:s.replace('public record PERecipeSerializer<RECIPE extends Recipe<?>>(MapCodec<RECIPE> codec, StreamCodec<RegistryFriendlyByteBuf, RECIPE> streamCodec) implements RecipeSerializer<RECIPE>', 'public final class PERecipeSerializer').replace('PERecipeSerializer<RECIPE> wrapped', 'RecipeSerializer<RECIPE> wrapped').replace('new PERecipeSerializer<>', 'new RecipeSerializer<>').replace('RecipeSerializer.SHAPELESS_RECIPE', 'ShapelessRecipe.SERIALIZER'))
edit('gameObjs/registries/PERecipeSerializers.java', lambda s:s.replace('import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;','').replace('SimpleCraftingRecipeSerializer<', 'RecipeSerializer<').replace('new SimpleCraftingRecipeSerializer<>(RecipesCovalenceRepair::new)', 'new RecipeSerializer<>(com.mojang.serialization.MapCodec.unit(RecipesCovalenceRepair::new), net.minecraft.network.codec.StreamCodec.unit(new RecipesCovalenceRepair()))').replace('new SimpleCraftingRecipeSerializer<>(PhiloStoneSmeltingRecipe::new)', 'new RecipeSerializer<>(com.mojang.serialization.MapCodec.unit(PhiloStoneSmeltingRecipe::new), net.minecraft.network.codec.StreamCodec.unit(new PhiloStoneSmeltingRecipe()))'))

edit('gameObjs/customRecipes/WrappedShapelessRecipe.java', lambda _: '''package moze_intel.projecte.gameObjs.customRecipes;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;

public abstract class WrappedShapelessRecipe implements CraftingRecipe {
    private final ShapelessRecipe internal;
    protected WrappedShapelessRecipe(ShapelessRecipe internal) { this.internal = internal; }
    public ShapelessRecipe getInternal() { return internal; }
    @Override public CraftingBookCategory category() { return internal.category(); }
    @Override public abstract ItemStack assemble(CraftingInput input);
    @Override public boolean matches(CraftingInput input, Level level) { return internal.matches(input, level) && !assemble(input).isEmpty(); }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input) { return internal.getRemainingItems(input); }
    @Override public boolean isSpecial() { return internal.isSpecial(); }
    @Override public String group() { return internal.group(); }
    @Override public boolean showNotification() { return internal.showNotification(); }
    @Override public PlacementInfo placementInfo() { return internal.placementInfo(); }
    @Override public List<RecipeDisplay> display() { return internal.display(); }
}
''')
for name in ['PhiloStoneSmeltingRecipe','RecipesCovalenceRepair','RecipeShapelessKleinStar']:
 def migrate(s):
  s=s.replace('(CraftingBookCategory category)', '()').replace('super(category);','super();')
  s=s.replace(', @NotNull HolderLookup.Provider registryAccess','').replace('getInternal().assemble(inv, registryAccess)', 'getInternal().assemble(inv)')
  s=s.replace('RecipeSerializer<?> getSerializer()',f'RecipeSerializer<{name}> getSerializer()')
  s=s.replace('matchingRecipes.stream().findFirst().get().value().getResultItem(registryAccess)', 'matchingRecipes.iterator().next().value().assemble(new SingleRecipeInput(ItemStack.EMPTY))')
  s=s.replace('level.getRecipeManager().getRecipesFor(RecipeType.SMELTING, furnaceInput, level)', '((net.minecraft.server.level.ServerLevel) level).getRecipeManager().recipeMap().getRecipesFor(RecipeType.SMELTING, furnaceInput, level).toList()')
  s=re.sub(r'\t@Override\n(\tpublic boolean canCraftInDimensions)',r'\1',s)
  return s
 edit(f'gameObjs/customRecipes/{name}.java',migrate)

# Removed special classes are now data-driven recipes, distinguished by output.
edit('emc/mappers/recipe/special/SpecialRecipeMapper.java',lambda s:s.replace('RECIPE extends CustomRecipe','RECIPE extends net.minecraft.world.item.crafting.CraftingRecipe').replace('getRecipeClass().isInstance(recipeHolder.value())','matchesRecipe(recipeHolder, registryAccess)').replace('\tprotected abstract Class<RECIPE> getRecipeClass();','''\tprotected abstract Class<RECIPE> getRecipeClass();
    protected boolean matchesRecipe(RecipeHolder<?> holder, RegistryAccess registries) {
        return getRecipeClass().isInstance(holder.value());
    }'''))
for name,old,new,item in [('ShulkerRecoloringMapper','ShulkerBoxColoring','TransmuteRecipe','shulker'),('SuspiciousStewMapper','SuspiciousStewRecipe','TransmuteRecipe','stew'),('TippedArrowMapper','TippedArrowRecipe','ImbueRecipe','arrow')]:
 def migrate(s):
  s=s.replace(old,new)
  test={'shulker':'stack.getItem() instanceof net.minecraft.world.item.BlockItem block && block.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock','stew':'stack.is(net.minecraft.world.item.Items.SUSPICIOUS_STEW)','arrow':'stack.is(net.minecraft.world.item.Items.TIPPED_ARROW)'}[item]
  extra='''
    @Override
    protected boolean matchesRecipe(net.minecraft.world.item.crafting.RecipeHolder<?> holder, RegistryAccess registries) {
        if (!super.matchesRecipe(holder, registries)) return false;
        var stack = moze_intel.projecte.utils.RecipeHelper.result(holder.value(), registries);
        return '''+test+''';
    }
'''
  return s[:s.rfind('}')]+extra+'}\n'
 edit(f'emc/mappers/recipe/special/{name}.java',migrate)
edit('emc/mappers/recipe/special/SpecialRecipeMarkHandledMapper.java',lambda s:s.replace('import net.minecraft.world.item.crafting.MapCloningRecipe;\n','').replace(' || recipe instanceof MapCloningRecipe',''))
edit('emc/components/processor/BundleProcessor.java',lambda s:s.replace('return component.items();','return component.items().stream().map(net.minecraft.world.item.ItemStackTemplate::create).toList();'))
edit('emc/components/processor/ContainerProcessor.java',lambda s:s.replace('return component.nonEmptyItems();','return component.stream().map(net.minecraft.world.item.ItemStackTemplate::create).toList();'))
