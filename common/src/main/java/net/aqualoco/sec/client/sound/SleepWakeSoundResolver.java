package net.aqualoco.sec.client.sound;

import java.util.List;

public final class SleepWakeSoundResolver {

    public List<SleepSoundCandidate> resolveStart(SleepSoundContext context) {
        return List.of();
    }

    public List<SleepSoundCandidate> resolveStop(SleepSoundContext context, SleepSoundCueType cueType) {
        return List.of();
    }
}
