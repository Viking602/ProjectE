package moze_intel.projecte.gameObjs;

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
