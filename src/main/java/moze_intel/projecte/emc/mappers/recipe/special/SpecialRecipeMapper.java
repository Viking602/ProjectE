package moze_intel.projecte.emc.mappers.recipe.special;

import moze_intel.projecte.api.mapper.collector.IMappingCollector;
import moze_intel.projecte.api.mapper.recipe.INSSFakeGroupManager;
import moze_intel.projecte.api.mapper.recipe.IRecipeTypeMapper;
import moze_intel.projecte.api.nss.NormalizedSimpleStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

public abstract class SpecialRecipeMapper<RECIPE extends net.minecraft.world.item.crafting.CraftingRecipe> implements IRecipeTypeMapper {

	protected abstract Class<RECIPE> getRecipeClass();
    protected boolean matchesRecipe(RecipeHolder<?> holder, RegistryAccess registries) {
        return getRecipeClass().isInstance(holder.value());
    }

	protected abstract boolean handleRecipe(IMappingCollector<NormalizedSimpleStack, Long> mapper, RegistryAccess registryAccess, INSSFakeGroupManager fakeGroupManager);

	@Override
	public final boolean handleRecipe(IMappingCollector<NormalizedSimpleStack, Long> mapper, RecipeHolder<?> recipeHolder, RegistryAccess registryAccess,
			INSSFakeGroupManager fakeGroupManager) {
		if (matchesRecipe(recipeHolder, registryAccess)) {
			return handleRecipe(mapper, registryAccess, fakeGroupManager);
		}
		return false;
	}

	@Override
	public final boolean canHandle(RecipeType<?> recipeType) {
		return recipeType == RecipeType.CRAFTING;
	}
}