package moze_intel.projecte.gameObjs.registries;

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
