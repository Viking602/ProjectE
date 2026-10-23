package moze_intel.projecte.utils;

import java.util.List;
import moze_intel.projecte.gameObjs.IMatterType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.block.Block;

/** Shared material rules for the indestructible matter tools. */
public final class MatterToolProperties {
    private MatterToolProperties() {}

    public static Item.Properties apply(IMatterType material, TagKey<Block> blocks, Item.Properties properties) {
        var lookup = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
        return properties.component(DataComponents.TOOL, new Tool(List.of(
                Tool.Rule.deniesDrops(lookup.getOrThrow(material.getIncorrectBlocksForDrops())),
                Tool.Rule.minesAndDrops(lookup.getOrThrow(blocks), material.getSpeed())), 1, 0, true))
                .component(DataComponents.WEAPON, new Weapon(0));
    }

    public static ItemAttributeModifiers attributes(IMatterType material, float damage, float speed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                        damage + material.getAttackDamageBonus(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                        speed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build();
    }
}
