from pathlib import Path
import re
root=Path('src/main/java')
def file(n):return next(root.rglob(n+'.java'))
def edit(n, pairs):
 p=file(n);s=p.read_text(encoding='utf-8')
 for a,b in pairs:s=s.replace(a,b)
 p.write_text(s,encoding='utf-8')
for n in ['BlockDirection','MatterFurnace','Pedestal']:
 p=file(n);s=p.read_text(encoding='utf-8');s=re.sub(r'\t@Override\n\t@Deprecated\n\tpublic void onRemove\(.*?\n\t}\n','',s,flags=re.S);p.write_text(s,encoding='utf-8')
edit('EmcBlockEntity', [('private boolean updateComparators;', '''@Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            var handler = moze_intel.projecte.utils.ItemTransferAdapter.legacy(
                    level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, pos, state, this, null));
            WorldHelper.dropInventory(handler, level, pos);
        }
    }

    private boolean updateComparators;''')])
edit('BlockDirection',[('EnumProperty<Direction>', 'EnumProperty<net.minecraft.core.Direction>')])
for p in (root/'moze_intel/projecte/gameObjs/blocks').glob('*.java'):
 s=p.read_text(encoding='utf-8')
 s=re.sub(r'(public int getAnalogOutputSignal\([^\n]*BlockPos pos)\)', r'\1, net.minecraft.core.Direction direction)',s)
 s=s.replace('super.getAnalogOutputSignal(state, level, pos)', 'super.getAnalogOutputSignal(state, level, pos, direction)')
 s=re.sub(r'public BlockState updateShape\(@NotNull BlockState state, @NotNull Direction facing, @NotNull BlockState facingState, @NotNull LevelAccessor level,\s*@NotNull BlockPos currentPos, @NotNull BlockPos facingPos\)', 'public BlockState updateShape(@NotNull BlockState state, net.minecraft.world.level.LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos currentPos, Direction facing, BlockPos facingPos, BlockState facingState, net.minecraft.util.RandomSource random)',s)
 s=s.replace('level.scheduleTick(currentPos,', 'ticks.scheduleTick(currentPos,').replace('super.updateShape(state, facing, facingState, level, currentPos, facingPos)', 'super.updateShape(state, level, ticks, currentPos, facing, facingPos, facingState, random)')
 p.write_text(s,encoding='utf-8')
edit('AlchemicalChest',[('RenderShape.ENTITYBLOCK_ANIMATED', 'RenderShape.INVISIBLE'),('PiglinAi.angerNearbyPiglins(player, true)', 'PiglinAi.angerNearbyPiglins((ServerLevel) level, player, true)')])
edit('Pedestal',[('Player player, boolean willHarvest,', 'Player player, ItemStack tool, boolean willHarvest,'),('super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid)', 'super.onDestroyedByPlayer(state, level, pos, player, tool, willHarvest, fluid)'),('@NotNull Block neighbor, @NotNull BlockPos neighborPos, boolean isMoving', '@NotNull Block neighbor, net.minecraft.world.level.redstone.Orientation orientation, boolean isMoving')])
edit('ProjectETNT',[('public void onCaughtFire(', 'public boolean onCaughtFire('),('\n\t}\n\n\tpublic void createAndAddEntity', '\n\t\treturn true;\n\t}\n\n\tpublic void createAndAddEntity'),('public void wasExploded(Level level,', 'public void wasExploded(net.minecraft.server.level.ServerLevel level,')])
for n in ['Collector','Relay','Pedestal']:
 edit(n,[('\t@Override\n\tpublic void appendHoverText(', '\tpublic void appendHoverText('),('\t\tsuper.appendHoverText(stack, context, display, tooltip, flags);\n','')])
edit('ItemDeferredRegister', [('new NoDurabilityItemProperties().stacksTo(1)', 'new NoDurabilityItemProperties().setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM, moze_intel.projecte.PECore.rl(name))).stacksTo(1)'),('propertyModifier.apply(new Item.Properties())', 'propertyModifier.apply(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM, moze_intel.projecte.PECore.rl(name))))'),('private static class NoDurabilityItemProperties extends Item.Properties {','''private static class NoDurabilityItemProperties extends Item.Properties {
        @Override
        public Item.Properties enchantable(int value) { return this; }
''')])
edit('BlockDeferredRegister',[('new Block(properties)', 'new Block(properties.setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, moze_intel.projecte.PECore.rl(name))))'),('new BlockItem(block, new Item.Properties())', 'new moze_intel.projecte.gameObjs.items.PEBlockItem(block, itemProperties(block))'),('blockSupplier.apply(baseProperties)', 'blockSupplier.apply(baseProperties.setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, moze_intel.projecte.PECore.rl(name))))'),('baseProperties.lootFrom(primaryObject)', 'BlockBehaviour.Properties.ofFullCopy(primaryObject.get()).setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, moze_intel.projecte.PECore.rl("wall_" + name))).overrideLootTable(primaryObject.get().getLootTable())'),('new Item.Properties(), Direction.DOWN', 'Direction.DOWN, itemProperties(primaryObject.get())'),('public class BlockDeferredRegister extends DoubleDeferredRegister<Block, Item> {','''public class BlockDeferredRegister extends DoubleDeferredRegister<Block, Item> {
    public static Item.Properties itemProperties(Block block) {
        return new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM,
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block))).useBlockDescriptionPrefix();
    }
''')])
p=file('PEBlocks');s=p.read_text(encoding='utf-8');s=s.replace('new BlockItem(block, new Item.Properties()', 'new moze_intel.projecte.gameObjs.items.PEBlockItem(block, BlockDeferredRegister.itemProperties(block)')
lines=s.splitlines()
for i,l in enumerate(lines):
 if 'BlockBehaviour.Properties.of()' in l:
  key=re.search(r'BLOCKS.register\("([^"]+)"',l)
  if key:name='"'+key[1]+'"'
  elif 'INTERDICTION_TORCH' in l:continue
  else:name='name'
  lines[i]=l.replace('BlockBehaviour.Properties.of()',f'properties({name})')
s='\n'.join(lines)+'\n';s=s.replace('\tpublic static final BlockDeferredRegister BLOCKS', '''    private static BlockBehaviour.Properties properties(String name) {
        return BlockBehaviour.Properties.of().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, PECore.rl(name)));
    }

\tpublic static final BlockDeferredRegister BLOCKS''');p.write_text(s,encoding='utf-8')
