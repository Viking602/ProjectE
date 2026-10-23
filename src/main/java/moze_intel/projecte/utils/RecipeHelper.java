package moze_intel.projecte.utils;

import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class RecipeHelper {
    private RecipeHelper() {}
    public static net.minecraft.world.level.block.entity.FuelValues fuelValues() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) return server.fuelValues();
        return net.minecraft.world.level.block.entity.FuelValues.vanillaBurnTimes(
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS);
    }

    public static ContextMap context(HolderLookup.Provider registries) {
        return new ContextMap.Builder().withParameter(SlotDisplayContext.REGISTRIES, registries).create(SlotDisplayContext.CONTEXT);
    }

    public static ItemStack[] stacks(Ingredient ingredient) {
        if (ingredient.isSimple()) {
            return ingredient.items().map(ItemStack::new).toArray(ItemStack[]::new);
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        var registries = server == null ? RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY) : server.registryAccess();
        return ingredient.display().resolveForStacks(context(registries)).stream().filter(ingredient::test).toArray(ItemStack[]::new);
    }

    /** Only static, unambiguous outputs are safe for automatic EMC conversion. */
    public static ItemStack result(Recipe<?> recipe, HolderLookup.Provider registries) {
        List<ItemStack> results = recipe.display().stream()
                .flatMap(display -> display.result().resolveForStacks(context(registries)).stream()).toList();
        if (results.isEmpty()) return ItemStack.EMPTY;
        ItemStack first = results.getFirst();
        return results.stream().allMatch(stack -> ItemStack.matches(first, stack)) ? first.copy() : ItemStack.EMPTY;
    }
}
