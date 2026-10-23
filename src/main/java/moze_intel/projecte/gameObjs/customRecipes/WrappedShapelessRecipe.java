package moze_intel.projecte.gameObjs.customRecipes;
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
