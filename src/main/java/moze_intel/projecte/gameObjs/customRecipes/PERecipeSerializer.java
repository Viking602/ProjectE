package moze_intel.projecte.gameObjs.customRecipes;

import com.mojang.serialization.MapCodec;
import java.util.function.Function;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;

public final class PERecipeSerializer {

	public static <RECIPE extends Recipe<?>> RecipeSerializer<RECIPE> singleton(RECIPE recipe) {
		return new RecipeSerializer<>(MapCodec.unit(recipe), StreamCodec.unit(recipe));
	}

	public static <RECIPE extends WrappedShapelessRecipe> RecipeSerializer<RECIPE> wrapped(Function<ShapelessRecipe, RECIPE> wrapper) {
		return new RecipeSerializer<>(
				ShapelessRecipe.SERIALIZER.codec().xmap(wrapper, WrappedShapelessRecipe::getInternal),
				ShapelessRecipe.SERIALIZER.streamCodec().map(wrapper, WrappedShapelessRecipe::getInternal)
		);
	}
}
