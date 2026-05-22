package net.aqualoco.sec.client.sound;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.network.SleepAnimationStartPayload;
import net.aqualoco.sec.network.SleepAnimationStopPayload;
import net.aqualoco.sec.sleep.SleepAnimationStopReason;

public final class SleepSoundManager {
    private static final SleepWakeSoundResolver RESOLVER = new SleepWakeSoundResolver();

    private static long activeSessionId = -1L;
    private static SleepSoundProfile activeProfile = SleepSoundProfile.MUTED;

    private SleepSoundManager() {
    }

    public static void onSleepStart(SleepAnimationStartPayload payload) {
        if (payload == null) {
            return;
        }

        activeSessionId = payload.sessionId();
        activeProfile = SleepSoundProfile.from(payload.soundMode());
        if (activeProfile == SleepSoundProfile.MUTED) {
            return;
        }

        SleepSoundContext context = new SleepSoundContext(
                payload.sessionId(),
                payload.mode(),
                payload.visualContext(),
                activeProfile
        );
        RESOLVER.resolveStart(context);
        Constants.debug("Prepared sleep sound profile {} for session {}.", activeProfile, activeSessionId);
    }

    public static void onSleepStop(SleepAnimationStopPayload payload) {
        if (payload == null || payload.sessionId() != activeSessionId) {
            return;
        }

        SleepSoundCueType cueType = payload.reason() == SleepAnimationStopReason.FINISHED
                ? SleepSoundCueType.FINISH
                : SleepSoundCueType.CANCEL;
        RESOLVER.resolveStop(
                new SleepSoundContext(activeSessionId, null, null, activeProfile),
                cueType
        );
        reset("sleep_stop_" + payload.reason().name().toLowerCase(java.util.Locale.ROOT));
    }

    public static void reset(String reason) {
        if (activeSessionId >= 0L) {
            Constants.debug("Sleep sound state reset: {}.", reason);
        }
        activeSessionId = -1L;
        activeProfile = SleepSoundProfile.MUTED;
    }
}
