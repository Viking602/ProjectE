package moze_intel.projecte.utils;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

public final class FluidTransferHelper {
    private FluidTransferHelper() {}
    public static @Nullable IFluidHandler legacy(@Nullable ResourceHandler<FluidResource> handler) {
        return handler == null ? null : IFluidHandler.of(handler);
    }
    public static final ResourceHandler<FluidResource> INFINITE_WATER = new ResourceHandler<>() {
        @Override public int size() { return 1; }
        @Override public FluidResource getResource(int index) { return FluidResource.of(Fluids.WATER); }
        @Override public long getAmountAsLong(int index) { return Integer.MAX_VALUE; }
        @Override public long getCapacityAsLong(int index, FluidResource resource) { return Integer.MAX_VALUE; }
        @Override public boolean isValid(int index, FluidResource resource) { return resource.is(FluidTags.WATER); }
        @Override public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            return index == 0 && isValid(index, resource) ? amount : 0;
        }
        @Override public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            return index == 0 && resource.equals(FluidResource.of(Fluids.WATER)) ? amount : 0;
        }
    };
}
