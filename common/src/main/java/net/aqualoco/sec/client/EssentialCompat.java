package net.aqualoco.sec.client;

import net.aqualoco.sec.platform.Services;

// Minimal compatibility gate for Essential-specific render adjustments.
public final class EssentialCompat {

    private static final String ESSENTIAL_MOD_ID = "essential";

    private EssentialCompat() {
    }

    public static boolean shouldSuppressOwnNameTagForBedCameraBody() {
        return Services.PLATFORM.isModLoaded(ESSENTIAL_MOD_ID);
    }
}
