package moze_intel.projecte.utils;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class NovaExplosion extends ServerExplosion {
    private final List<BlockPos> particlePositions = new ArrayList<>();

    public NovaExplosion(ServerLevel level, @Nullable Entity entity, double x, double y, double z, float radius, Explosion.BlockInteraction mode) {
        super(level, entity, null, null, new Vec3(x, y, z), radius, false, mode);
    }

    @Override
    public void interactWithBlocks(List<BlockPos> positions) {
        NonNullList<ItemStack> drops = NonNullList.create();
        Util.shuffle(positions, level().getRandom());
        for (BlockPos pos : positions) {
            var state = level().getBlockState(pos);
            if (!state.isAir()) particlePositions.add(pos.immutable());
            state.onExplosionHit(level(), pos, this, (stack, position) -> drops.add(stack));
        }
        var placer = getIndirectSourceEntity();
        WorldHelper.createLootDrop(drops, level(), placer == null ? center() : placer.position());
    }

    public List<BlockPos> particlePositions() { return List.copyOf(particlePositions); }
}
