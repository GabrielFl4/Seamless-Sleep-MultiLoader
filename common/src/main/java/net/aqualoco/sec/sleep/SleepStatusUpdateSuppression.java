package net.aqualoco.sec.sleep;

// Scoped guard used during the natural Seamless wake path to avoid vanilla
// sleep-count broadcasts such as "Players Sleeping 0/2".
public final class SleepStatusUpdateSuppression {
    private static int naturalFinishWakeDepth;

    private SleepStatusUpdateSuppression() {
    }

    public static void beginNaturalFinishWake() {
        naturalFinishWakeDepth++;
    }

    public static void endNaturalFinishWake() {
        if (naturalFinishWakeDepth > 0) {
            naturalFinishWakeDepth--;
        }
    }

    public static boolean isNaturalFinishWakeSuppressed() {
        return naturalFinishWakeDepth > 0;
    }
}
