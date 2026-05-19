package net.aqualoco.sec.client.sleepindicator.biomeclock;

// Debounces scene switches so cave entrances and skylight edge cases do not flicker.
public final class BiomeClockSceneState {
    private static final long SCENE_CHANGE_DEBOUNCE_MS = 120L;

    private BiomeClockSceneKind currentScene = BiomeClockSceneKind.NORMAL;
    private BiomeClockSceneKind pendingScene;
    private long pendingSinceMs;

    public BiomeClockSceneKind update(BiomeClockSceneKind requestedScene, long nowMs) {
        BiomeClockSceneKind safeScene = requestedScene == null ? BiomeClockSceneKind.NORMAL : requestedScene;
        if (safeScene == this.currentScene) {
            this.pendingScene = null;
            return this.currentScene;
        }

        if (this.pendingScene != safeScene) {
            this.pendingScene = safeScene;
            this.pendingSinceMs = nowMs;
            return this.currentScene;
        }

        if (nowMs - this.pendingSinceMs >= SCENE_CHANGE_DEBOUNCE_MS) {
            this.currentScene = safeScene;
            this.pendingScene = null;
        }

        return this.currentScene;
    }
}
