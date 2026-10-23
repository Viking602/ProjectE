package moze_intel.projecte.client;

import com.mojang.serialization.MapCodec;
import moze_intel.projecte.api.capabilities.item.IModeChanger;
import moze_intel.projecte.gameObjs.registries.PEDataComponentTypes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public enum PEItemProperty implements RangeSelectItemModelProperty {
    ACTIVE, MODE;

    private final MapCodec<PEItemProperty> codec = MapCodec.unit(this);
    @Override public MapCodec<PEItemProperty> type() { return codec; }
    @Override public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        return switch (this) {
            case ACTIVE -> stack.getOrDefault(PEDataComponentTypes.ACTIVE, false) ? 1 : 0;
            case MODE -> stack.getItem() instanceof IModeChanger<?> changer ? ((Enum<?>) changer.getMode(stack)).ordinal() : 0;
        };
    }
}
