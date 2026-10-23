from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'src/main/java/moze_intel/projecte'
def edit(path, fn):
    p = JAVA / path
    p.write_text(fn(p.read_text(encoding='utf-8')), encoding='utf-8')

for p in (ROOT / 'src').rglob('*.java'):
    s = p.read_text(encoding='utf-8')
    s = s.replace('import net.minecraft.world.item.ArmorItem;', 'import net.minecraft.world.item.equipment.ArmorType;')
    s = s.replace('ArmorItem.Type', 'ArmorType').replace('net.minecraft.world.item.ArmorMaterial', 'net.minecraft.world.item.equipment.ArmorMaterial')
    s = s.replace('InteractionResultHolder<ItemStack>', 'InteractionResult').replace('import net.minecraft.world.InteractionResultHolder;', 'import net.minecraft.world.InteractionResult;')
    s = re.sub(r'InteractionResultHolder\.(success|consume)\(([^;]+)\)', lambda m: 'InteractionResult.'+m[1].upper()+'.heldItemTransformedTo('+m[2]+')', s)
    s = re.sub(r'InteractionResultHolder\.(pass|fail)\(([^;]+)\)', lambda m: 'InteractionResult.'+m[1].upper(), s)
    s = s.replace('ItemInteractionResult', 'InteractionResult').replace('InteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION', 'InteractionResult.TRY_WITH_EMPTY_HAND')
    s = re.sub(r'InteractionResult.sidedSuccess\([^;]*?\)', 'InteractionResult.SUCCESS', s)
    # Preserve tooltip builders as lists locally, then forward to the new consumer.
    s = re.sub(r'(public void appendHoverText\([^\n]*?), @NotNull List<Component> tooltip, (@NotNull TooltipFlag \w+\) \{)', r'\1, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, \2', s)
    if 'Consumer<Component> tooltip' in s:
        s = s.replace('super.appendHoverText(stack, context, tooltip, flags)', 'super.appendHoverText(stack, context, display, tooltip, flags)')
        # All item tooltip.add calls in these classes belong to appendHoverText.
        s = s.replace('tooltip.add(', 'tooltip.accept(')
    s = s.replace('inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot, boolean isHeld)', 'inventoryTick(@NotNull ItemStack stack, @NotNull net.minecraft.server.level.ServerLevel level, @NotNull Entity entity, net.minecraft.world.entity.EquipmentSlot slot)')
    s = s.replace('super.inventoryTick(stack, level, entity, slot, isHeld);', 'super.inventoryTick(stack, level, entity, slot);\n\t\tboolean isHeld = slot == net.minecraft.world.entity.EquipmentSlot.MAINHAND;')
    s = re.sub(r'\t@Override\n(\tpublic boolean isEnchantable\()', r'\1', s)
    # ContainerInput was renamed along with the two click actions.
    s = s.replace('DirectionProperty FACING', 'net.minecraft.world.level.block.state.properties.EnumProperty<Direction> FACING').replace('import net.minecraft.world.level.block.state.properties.DirectionProperty;\n','')
    # Deduplicate imports after combining interaction-result classes.
    seen=set(); lines=[]
    for line in s.splitlines(True):
        if line.startswith('import '):
            if line in seen: continue
            seen.add(line)
        lines.append(line)
    p.write_text(''.join(lines), encoding='utf-8')

edit('utils/ItemHelper.java', lambda s: re.sub(r'return switch \(type\) \{.*?\n\t\t};', 'return type;', s, count=1, flags=re.S))
edit('gameObjs/IMatterType.java', lambda _: '''package moze_intel.projecte.gameObjs;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.Block;

public interface IMatterType {
    float getChargeModifier();
    int getMatterTier();
    int getUses();
    float getSpeed();
    float getAttackDamageBonus();
    TagKey<Block> getIncorrectBlocksForDrops();
    int getEnchantmentValue();

    default ToolMaterial toolMaterial() {
        return new ToolMaterial(getIncorrectBlocksForDrops(), 1, getSpeed(), getAttackDamageBonus(), 1,
                net.minecraft.tags.ItemTags.DIAMOND_TOOL_MATERIALS);
    }
}
''')
edit('gameObjs/EnumMatterType.java', lambda s: re.sub(r'\n\t@NotNull\n\t@Override\n\tpublic Ingredient getRepairIngredient\(\) \{.*?\n\t}', '', s, flags=re.S))
edit('gameObjs/registries/PEArmorMaterials.java', lambda _: '''package moze_intel.projecte.gameObjs.registries;

import java.util.Map;
import moze_intel.projecte.PECore;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

public final class PEArmorMaterials {
    private static final Map<ArmorType, Integer> DEFENSE = Map.of(
            ArmorType.BOOTS, 3, ArmorType.LEGGINGS, 6, ArmorType.CHESTPLATE, 8, ArmorType.HELMET, 3, ArmorType.BODY, 11);
    public static final ArmorMaterial DARK_MATTER = create("dark_matter", 0.1F);
    public static final ArmorMaterial RED_MATTER = create("red_matter", 0.2F);
    public static final ArmorMaterial GEM_ARMOR = create("gem_armor", 0.25F);
    private static ArmorMaterial create(String name, float knockback) {
        return new ArmorMaterial(1, DEFENSE, 0, SoundEvents.ARMOR_EQUIP_NETHERITE, 2, knockback,
                ItemTags.REPAIRS_DIAMOND_ARMOR, ResourceKey.create(EquipmentAssets.ROOT_ID, PECore.rl(name)));
    }
    private PEArmorMaterials() {}
}
''')
edit('PECore.java', lambda s:s.replace('\t\tPEArmorMaterials.ARMOR_MATERIALS.register(modEventBus);\n',''))
edit('gameObjs/items/armor/PEArmor.java', lambda s:s.replace('extends ArmorItem', 'extends Item').replace('Holder<ArmorMaterial> material','ArmorMaterial material').replace('super(material, armorPiece, props);', '''super(props.attributes(material.createAttributes(armorPiece))
                .component(net.minecraft.core.component.DataComponents.EQUIPPABLE,
                    net.minecraft.world.item.equipment.Equippable.builder(armorPiece.getSlot())
                        .setEquipSound(material.equipSound()).setAsset(material.assetId()).setDamageOnHurt(false).build()));
        this.type = armorPiece;''').replace('protected PEArmor(', 'protected final ArmorType type;\n\n\tprotected PEArmor(').replace('protected static boolean isArmorSlot(int slot)', 'protected static boolean isArmorSlot(net.minecraft.world.entity.EquipmentSlot slot)').replace('return slot >= Inventory.INVENTORY_SIZE && slot < Inventory.INVENTORY_SIZE + 4;', 'return slot != null && slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR;'))

for name in ['PEAxe','PEHoe','PEShovel']:
    edit(f'gameObjs/items/tools/{name}.java', lambda s:re.sub(r'super\(matterType, props.attributes\(createAttributes\(matterType, ([^,]+), ([^)]+)\)\)', r'super(matterType.toolMaterial(), \1, \2, props',s))
edit('gameObjs/items/tools/PETool.java', lambda s:s.replace('import net.minecraft.world.item.DiggerItem;','').replace('extends DiggerItem', 'extends Item').replace('super(matterType, blocks, props.component', 'super(moze_intel.projecte.utils.MatterToolProperties.apply(matterType, blocks, props).component').replace('createAttributes(', 'moze_intel.projecte.utils.MatterToolProperties.attributes('))
edit('gameObjs/items/tools/PEPickaxe.java', lambda s:s.replace('import net.minecraft.world.item.PickaxeItem;','').replace('extends PickaxeItem','extends Item').replace('super(matterType, props.attributes(createAttributes(matterType, 4, -2.8F))', 'super(moze_intel.projecte.utils.MatterToolProperties.apply(matterType, net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE, props).attributes(moze_intel.projecte.utils.MatterToolProperties.attributes(matterType, 4, -2.8F))'))
for name in ['PEHammer','PEKatar','PEMorningStar']:
    edit(f'gameObjs/items/tools/{name}.java',lambda s:s.replace('createAttributes(', 'moze_intel.projecte.utils.MatterToolProperties.attributes('))
edit('gameObjs/items/tools/PESword.java', lambda s:re.sub(r'super\(matterType, props.attributes\(createAttributes\(matterType, damage, -2.4F\)\).*?\n\t\t\);', '''super(moze_intel.projecte.utils.MatterToolProperties.apply(matterType, PETags.Blocks.MINEABLE_WITH_PE_SWORD, props)
                .attributes(moze_intel.projecte.utils.MatterToolProperties.attributes(matterType, damage, -2.4F))
                .component(PEDataComponentTypes.CHARGE, 0).component(PEDataComponentTypes.STORED_EMC, 0L));''',s,flags=re.S).replace('import net.minecraft.world.item.SwordItem;','').replace('extends SwordItem', 'extends Item'))
for p in (JAVA/'gameObjs/items/tools').glob('*.java'):
    s=p.read_text(encoding='utf-8')
    s=re.sub(r'public boolean hurtEnemy(.*?)\{(.*?)\n\t}',lambda m:'public void hurtEnemy'+m[1]+'{'+m[2].replace('\n\t\treturn true;','')+'\n\t}',s,flags=re.S)
    p.write_text(s,encoding='utf-8')

# Migrate the block entities' persisted inventory structure in place.
for p in (JAVA/'gameObjs/block_entities').glob('*.java'):
    s=p.read_text(encoding='utf-8')
    s=s.replace('loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries)', 'loadAdditional(@NotNull net.minecraft.world.level.storage.ValueInput tag)')
    s=s.replace('saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries)', 'saveAdditional(@NotNull net.minecraft.world.level.storage.ValueOutput tag)')
    s=s.replace('super.loadAdditional(tag, registries)','super.loadAdditional(tag)').replace('super.saveAdditional(tag, registries)','super.saveAdditional(tag)')
    s=re.sub(r'(\w+(?:\(\))?)\.deserializeNBT\(registries, tag.getCompound\("([^"]+)"\)\)',r'\1.deserialize(tag.childOrEmpty("\2"))',s)
    s=re.sub(r'(\w+)\.deserializeNBT\(registries, tag\)',r'\1.deserialize(tag)',s)
    s=re.sub(r'tag.put\("([^"]+)", (\w+(?:\(\))?)\.serializeNBT\(registries\)\)',r'\2.serialize(tag.child("\1"))',s)
    s=re.sub(r'tag.merge\((\w+)\.serializeNBT\(registries\)\)',r'\1.serialize(tag)',s)
    for typ,default in [('Long','0L'),('Int','0'),('Boolean','false'),('Double','0D'),('Float','0F')]:
        s=re.sub(r'tag.get'+typ+r'\("([^"]+)"\)',r'tag.get'+typ+r'Or("\1", '+default+r')',s)
    p.write_text(s,encoding='utf-8')
