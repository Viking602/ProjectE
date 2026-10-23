package moze_intel.projecte.utils;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;

public final class ClockHelper {
    private ClockHelper() {}
    private static Holder<WorldClock> clock(ServerLevel level) {
        return level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
    }
    public static long getTime(ServerLevel level) { return level.clockManager().getTotalTicks(clock(level)); }
    public static void setTime(ServerLevel level, long ticks) { level.clockManager().setTotalTicks(clock(level), ticks); }
    public static boolean isRunning(ServerLevel level) {
        var state = level.clockManager().packState().clocks().get(clock(level));
        return state == null || !state.paused();
    }
}
