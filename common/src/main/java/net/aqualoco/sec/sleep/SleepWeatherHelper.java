package net.aqualoco.sec.sleep;

import net.aqualoco.sec.mixin.sleep.ServerLevelWeatherInvoker;
import net.minecraft.server.level.ServerLevel;

public final class SleepWeatherHelper {

    private SleepWeatherHelper() {
    }

    public static void clearWeatherCycle(ServerLevel world) {
        if (world != null) {
            ((ServerLevelWeatherInvoker) world).seamlesssleep$invokeResetWeatherCycle();
        }
    }
}
