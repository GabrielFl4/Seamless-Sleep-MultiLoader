package net.aqualoco.sec.client;

import java.util.OptionalLong;

// Aggregates supported replay mods so animation code has a single compatibility entrypoint.
public final class ReplayPlaybackCompat {

    private ReplayPlaybackCompat() {
    }

    public static boolean isReplayPlaybackActive() {
        return ReplayModCompat.isReplayPlaybackActive() || FlashbackCompat.isReplayPlaybackActive();
    }

    public static OptionalLong getReplayTimelineMillis() {
        OptionalLong replayModTimeline = ReplayModCompat.getReplayTimelineMillis();
        if (replayModTimeline.isPresent()) {
            return replayModTimeline;
        }

        return FlashbackCompat.getReplayTimelineMillis();
    }
}
