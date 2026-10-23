package moze_intel.projecte.gameObjs.items;

import java.util.function.Consumer;
import moze_intel.projecte.gameObjs.blocks.Collector;
import moze_intel.projecte.gameObjs.blocks.Relay;
import moze_intel.projecte.gameObjs.blocks.Pedestal;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

public class PEBlockItem extends BlockItem {
    public PEBlockItem(Block block, Properties properties) { super(block, properties); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        switch (getBlock()) {
            case Collector collector -> collector.appendHoverText(stack, context, display, tooltip, flag);
            case Relay relay -> relay.appendHoverText(stack, context, display, tooltip, flag);
            case Pedestal pedestal -> pedestal.appendHoverText(stack, context, display, tooltip, flag);
            default -> {}
        }
    }
}
