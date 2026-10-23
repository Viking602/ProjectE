package moze_intel.projecte.emc.mappers.recipe.special;

import moze_intel.projecte.api.mapper.collector.IMappingCollector;
import moze_intel.projecte.api.mapper.recipe.INSSFakeGroupManager;
import moze_intel.projecte.api.mapper.recipe.RecipeTypeMapper;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.api.nss.NormalizedSimpleStack;
import moze_intel.projecte.config.PEConfigTranslations;
import moze_intel.projecte.utils.Constants;
import moze_intel.projecte.utils.EMCHelper;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.level.block.ShulkerBoxBlock;

@RecipeTypeMapper
public class ShulkerRecoloringMapper extends SpecialRecipeMapper<TransmuteRecipe> {

	@Override
	protected Class<TransmuteRecipe> getRecipeClass() {
		return TransmuteRecipe.class;
	}

	@Override
	protected boolean handleRecipe(IMappingCollector<NormalizedSimpleStack, Long> mapper, RegistryAccess registryAccess, INSSFakeGroupManager fakeGroupManager) {
		NSSItem nssShulker = NSSItem.createItem(Items.SHULKER_BOX);
		for (DyeColor color : Constants.COLORS) {
			mapper.addConversion(1, NSSItem.createItem(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(color.getName() + "_shulker_box"))), EMCHelper.intMapOf(
					nssShulker, 1,
					NSSItem.createTag(color.getTag()), 1
			));
		}
		return true;
	}

	@Override
	public String getName() {
		return PEConfigTranslations.MAPPING_CRAFTING_MAPPER_SHULKER_RECOLORING.title();
	}

	@Override
	public String getTranslationKey() {
		return PEConfigTranslations.MAPPING_CRAFTING_MAPPER_SHULKER_RECOLORING.getTranslationKey();
	}

	@Override
	public String getDescription() {
		return PEConfigTranslations.MAPPING_CRAFTING_MAPPER_SHULKER_RECOLORING.tooltip();
	}

    @Override
    protected boolean matchesRecipe(net.minecraft.world.item.crafting.RecipeHolder<?> holder, RegistryAccess registries) {
        if (!super.matchesRecipe(holder, registries)) return false;
        var stack = moze_intel.projecte.utils.RecipeHelper.result(holder.value(), registries);
        return stack.getItem() instanceof net.minecraft.world.item.BlockItem block && block.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock;
    }
}
