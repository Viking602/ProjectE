package moze_intel.projecte.emc;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import moze_intel.projecte.PECore;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.emc.components.DataComponentManager;
import moze_intel.projecte.utils.RecipeHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/** Validates the integer prices actually used by transmutation, including recipe-specific returns.
 * Fixed seeds are upper bounds here: keeping an inconsistent seed would permit unlimited EMC.
 * Values only decrease, so this also closes chains through previously corrected recipes.
 */
public final class RecipeEmcValidator {
    private RecipeEmcValidator() {}

    public static void excludeFractionalReversals(moze_intel.projecte.api.mapper.collector.IMappingCollector<moze_intel.projecte.api.nss.NormalizedSimpleStack, Long> mapper,
            RecipeManager recipes, net.minecraft.core.RegistryAccess registries) {
        excludeFractionalStairs(mapper, recipes, registries, "dirt", net.minecraft.world.item.Items.DIRT);
        excludeFractionalStairs(mapper, recipes, registries, "grass", net.minecraft.world.item.Items.GRASS_BLOCK);
    }

    private static void excludeFractionalStairs(moze_intel.projecte.api.mapper.collector.IMappingCollector<moze_intel.projecte.api.nss.NormalizedSimpleStack, Long> mapper,
            RecipeManager recipes, net.minecraft.core.RegistryAccess registries, String material, net.minecraft.world.item.Item block) {
        var id = net.minecraft.resources.Identifier.parse("prefab:block_" + material + "_stairs");
        var stairs = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (stairs == null) return;
        // Six blocks <-> four stairs is lossless physically, but 1.5 EMC cannot be
        // represented by ProjectE's integer prices. Exclude only the subdivision,
        // rather than rounding the reverse conversion down and removing block EMC.
        boolean forward = false, reverse = false;
        for (var holder : recipes.recipeMap().values()) {
            String recipeId = holder.id().identifier().toString();
            if (recipeId.equals("prefab:" + material + "_stairs")) forward = simpleConversion(holder, block, 6, stairs, 4, registries);
            else if (recipeId.equals("prefab:" + material + "_stairs_reverse")) reverse = simpleConversion(holder, stairs, 2, block, 3, registries);
        }
        if (forward && reverse) {
            var key = moze_intel.projecte.api.nss.NSSItem.createItem(stairs);
            mapper.setValueBefore(key, 0L);
            mapper.setValueAfter(key, 0L);
        }
    }

    private static boolean simpleConversion(RecipeHolder<?> holder, net.minecraft.world.item.Item input, int inputCount,
            net.minecraft.world.item.Item output, int outputCount, net.minecraft.core.RegistryAccess registries) {
        var recipe = holder.value();
        if (!(recipe instanceof ShapedRecipe)) return false;
        var placement = recipe.placementInfo();
        var result = RecipeHelper.result(recipe, registries);
        return result.is(output) && result.getCount() == outputCount
                && placement.slotsToIngredientIndex().intStream().filter(i -> i >= 0).count() == inputCount
                && placement.ingredients().stream().allMatch(ingredient -> ingredient.items().allMatch(item -> item.value() == input));
    }

    public static void validate(Object2LongMap<ItemInfo> values, RecipeManager recipes, ServerLevel level) {
        Set<ItemInfo> originallyValued = new HashSet<>(values.keySet());
        List<RecipeHolder<?>> supported = recipes.recipeMap().values().stream()
                .filter(holder -> holder.value() instanceof CraftingRecipe || holder.value() instanceof AbstractCookingRecipe
                        || holder.value() instanceof SingleItemRecipe)
                .sorted(Comparator.comparing(holder -> holder.id().identifier().toString())).toList();
        Set<ItemInfo> changed = new HashSet<>();
        Set<Object> invalid = new HashSet<>();
        int pass = 0;
        boolean dirty;
        do {
            dirty = false;
            pass++;
            for (var holder : supported) {
                if (invalid.contains(holder.id())) continue;
                try {
                    var recipe = holder.value();
                    ItemStack output;
                    List<ItemStack> inputs = new ArrayList<>();
                    List<ItemStack> returned = List.of();
                    if (recipe instanceof CraftingRecipe crafting) {
                        var placement = crafting.placementInfo();
                        if (placement.isImpossibleToPlace() || placement.slotsToIngredientIndex().size() > 9) continue;
                        int width = crafting instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
                        int height = crafting instanceof ShapedRecipe shaped ? shaped.getHeight() : 3;
                        inputs.addAll(Collections.nCopies(width * height, ItemStack.EMPTY));
                        boolean known = true;
                        for (int slot = 0; slot < placement.slotsToIngredientIndex().size(); slot++) {
                            int ingredient = placement.slotsToIngredientIndex().getInt(slot);
                            if (ingredient < 0) continue;
                            ItemStack cheapest = cheapest(placement.ingredients().get(ingredient), originallyValued);
                            if (cheapest.isEmpty()) { known = false; break; }
                            inputs.set(slot, cheapest);
                        }
                        if (!known) continue;
                        var input = CraftingInput.of(width, height, inputs);
                        if (!crafting.matches(input, level)) continue;
                        output = crafting.assemble(copy(input));
                        returned = crafting.getRemainingItems(copy(input));
                        // Repair Talismans can restore returned tools for free. Charging the output
                        // for that durability would create a craft -> repair -> transmute loop.
                        for (int slot = 0; slot < returned.size() && slot < inputs.size(); slot++) {
                            ItemStack remainder = returned.get(slot), original = inputs.get(slot);
                            if (!remainder.isEmpty() && remainder.isDamageableItem() && remainder.is(original.getItem())) {
                                ItemStack repaired = remainder.copy();
                                repaired.setDamageValue(original.getDamageValue());
                                if (ItemStack.isSameItemSameComponents(repaired, original)) returned.set(slot, repaired);
                            }
                        }
                    } else {
                        var ingredients = recipe.placementInfo().ingredients();
                        if (ingredients.size() != 1) continue;
                        ItemStack cheapest = cheapest(ingredients.getFirst(), originallyValued);
                        if (cheapest.isEmpty()) continue;
                        inputs.add(cheapest);
                        var input = new SingleRecipeInput(cheapest);
                        if (recipe instanceof AbstractCookingRecipe cooking) {
                            if (!cooking.matches(input, level)) continue;
                            output = cooking.assemble(input);
                        } else {
                            var single = (SingleItemRecipe) recipe;
                            if (!single.matches(input, level)) continue;
                            output = single.assemble(input);
                        }
                        // Ignoring furnace fuel is conservative; it cannot create an EMC gain.
                    }
                    if (output.isEmpty()) continue;
                    long current = value(output);
                    if (current <= 0) continue;
                    long affordable = affordableUnit(total(inputs), total(returned), output.getCount());
                    if (current <= affordable) continue;
                    ItemInfo key = ItemInfo.fromStack(output);
                    if (!values.containsKey(key)) key = key.itemOnly();
                    long base = values.getLong(key);
                    if (base <= 0) continue;
                    // Apply the price deficit to the base, retaining component processors (damage, contents, etc.).
                    long corrected = Math.max(0, base - (current - affordable));
                    // Pathological cycles must not hold up world loading indefinitely.
                    if (pass > 128) corrected = 0;
                    if (corrected == 0) values.removeLong(key);
                    else values.put(key, corrected);
                    changed.add(key);
                    dirty = true;
                    DataComponentManager.updateCachedValues(values::getLong);
                    PECore.debugLog("EMC recipe correction {}: {} -> {} ({})", key, base, corrected, holder.id().identifier());
                } catch (RuntimeException exception) {
                    invalid.add(holder.id());
                    PECore.LOGGER.warn("Could not validate EMC for recipe {}", holder.id().identifier(), exception);
                }
            }
        } while (dirty);
        PECore.LOGGER.info("EMC recipe validation: {} values corrected in {} passes, {} recipes could not be evaluated",
                changed.size(), pass, invalid.size());
    }

    private static ItemStack cheapest(Ingredient ingredient, Set<ItemInfo> originallyValued) {
        return java.util.Arrays.stream(RecipeHelper.stacks(ingredient)).filter(ingredient::test)
                .filter(stack -> originallyValued.contains(ItemInfo.fromStack(stack)) || originallyValued.contains(ItemInfo.fromItem(stack.getItem())))
                .min(Comparator.comparingLong(RecipeEmcValidator::value)).map(ItemStack::copy).orElse(ItemStack.EMPTY);
    }

    private static CraftingInput copy(CraftingInput input) {
        return CraftingInput.of(input.width(), input.height(), input.items().stream().map(ItemStack::copy).toList());
    }

    private static long value(ItemStack stack) {
        return stack.isEmpty() ? 0 : IEMCProxy.INSTANCE.getValue(stack);
    }

    private static BigInteger total(List<ItemStack> stacks) {
        BigInteger result = BigInteger.ZERO;
        for (ItemStack stack : stacks) result = result.add(BigInteger.valueOf(value(stack)).multiply(BigInteger.valueOf(stack.getCount())));
        return result;
    }

    static long affordableUnit(BigInteger inputs, BigInteger returned, int outputCount) {
        if (outputCount <= 0) throw new IllegalArgumentException("Output count must be positive");
        return inputs.subtract(returned).max(BigInteger.ZERO).divide(BigInteger.valueOf(outputCount))
                .min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
    }
}
