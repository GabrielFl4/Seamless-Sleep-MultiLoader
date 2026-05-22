package net.aqualoco.sec.client.sound;

import net.aqualoco.sec.sleep.SleepAnimationMode;
import net.aqualoco.sec.sleep.SleepAnimationVisualContext;

public record SleepSoundContext(long sessionId,
                                SleepAnimationMode mode,
                                SleepAnimationVisualContext visualContext,
                                SleepSoundProfile profile) {
}
