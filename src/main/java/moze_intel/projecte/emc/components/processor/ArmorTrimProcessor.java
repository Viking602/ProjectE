package moze_intel.projecte.emc.components.processor;

import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.components.DataComponentProcessor;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.config.PEConfigTranslations;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

@DataComponentProcessor
public class ArmorTrimProcessor extends PersistentComponentProcessor<ArmorTrim> {

	@Override
	public String getName() {
		return PEConfigTranslations.DCP_ARMOR_TRIM.title();
	}

	@Override
	public String getTranslationKey() {
		return PEConfigTranslations.DCP_ARMOR_TRIM.getTranslationKey();
	}

	@Override
	public String getDescription() {
		return PEConfigTranslations.DCP_ARMOR_TRIM.tooltip();
	}

	@Override
	@Range(from = 0, to = Long.MAX_VALUE)
	public long recalculateEMC(@NotNull ItemInfo info, @Range(from = 1, to = Long.MAX_VALUE) long currentEMC, @NotNull ArmorTrim trim) throws ArithmeticException {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return 0;
        long minimum = Long.MAX_VALUE;
        var base = info.createStack();
        for (var holder : server.getRecipeManager().recipeMap().byType(net.minecraft.world.item.crafting.RecipeType.SMITHING)) {
            if (holder.value() instanceof net.minecraft.world.item.crafting.SmithingTrimRecipe recipe
                    && recipe.pattern.equals(trim.pattern()) && recipe.base.test(base)) {
                long templateEmc = moze_intel.projecte.api.components.IComponentProcessorHelper.INSTANCE.getMinEmcFor(IEMCProxy.INSTANCE::getValue, recipe.template);
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
        return minimum == Long.MAX_VALUE ? 0 : Math.addExact(currentEMC, minimum);
	}

	@Override
	protected boolean validItem(@NotNull ItemInfo info) {
		return info.getItem().is(ItemTags.TRIMMABLE_ARMOR);
	}

	@Override
	protected boolean shouldPersist(@NotNull ItemInfo info, @NotNull ArmorTrim component) {
		return true;
	}

	@Override
	protected DataComponentType<ArmorTrim> getComponentType(@NotNull ItemInfo info) {
		return DataComponents.TRIM;
	}
}
