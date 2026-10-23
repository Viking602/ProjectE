package moze_intel.projecte.gameObjs.registration.impl;

import java.util.function.Function;
import java.util.function.Supplier;
import moze_intel.projecte.gameObjs.registration.DoubleDeferredRegister;
import moze_intel.projecte.gameObjs.registration.impl.BlockRegistryObject.WallOrFloorBlockRegistryObject;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;

public class BlockDeferredRegister extends DoubleDeferredRegister<Block, Item> {
    public static Item.Properties itemProperties(Block block) {
        return new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM,
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block))).useBlockDescriptionPrefix();
    }


	public BlockDeferredRegister(String modid) {
		super(Registries.BLOCK, new ItemDeferredRegister(modid), modid);
	}

	public BlockRegistryObject<Block, BlockItem> register(String name, BlockBehaviour.Properties properties) {
		return register(name, () -> new Block(properties.setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, moze_intel.projecte.PECore.rl(name)))));
	}

	public <BLOCK extends Block> BlockRegistryObject<BLOCK, BlockItem> register(String name, Supplier<? extends BLOCK> blockSupplier) {
		return register(name, blockSupplier, block -> new moze_intel.projecte.gameObjs.items.PEBlockItem(block, itemProperties(block)));
	}

	public <BLOCK extends Block, WALL_BLOCK extends Block> WallOrFloorBlockRegistryObject<BLOCK, WALL_BLOCK, StandingAndWallBlockItem> registerWallOrFloorItem(String name,
			Function<BlockBehaviour.Properties, BLOCK> blockSupplier, Function<BlockBehaviour.Properties, WALL_BLOCK> wallBlockSupplier,
			BlockBehaviour.Properties baseProperties) {
		DeferredHolder<Block, BLOCK> primaryObject = primaryRegister.register(name, () -> blockSupplier.apply(baseProperties.setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, moze_intel.projecte.PECore.rl(name)))));
		DeferredHolder<Block, WALL_BLOCK> wallObject = primaryRegister.register("wall_" + name, () -> wallBlockSupplier.apply(BlockBehaviour.Properties.ofFullCopy(primaryObject.get()).setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, moze_intel.projecte.PECore.rl("wall_" + name))).overrideLootTable(primaryObject.get().getLootTable())));
		return new WallOrFloorBlockRegistryObject<>(primaryObject, wallObject, secondaryRegister.register(name, () -> new StandingAndWallBlockItem(primaryObject.get(), wallObject.get(),
				Direction.DOWN, itemProperties(primaryObject.get()))));
	}

	public <BLOCK extends Block, ITEM extends BlockItem> BlockRegistryObject<BLOCK, ITEM> register(String name, Supplier<? extends BLOCK> blockSupplier,
			Function<BLOCK, ITEM> itemCreator) {
		return register(name, blockSupplier, itemCreator, BlockRegistryObject::new);
	}
}