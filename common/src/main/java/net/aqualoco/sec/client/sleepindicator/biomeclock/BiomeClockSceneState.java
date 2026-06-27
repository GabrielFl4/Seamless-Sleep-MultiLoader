package net.aqualoco.sec.client.sleepindicator.biomeclock;

import net.minecraft.util.Mth;

// Debounces scene changes and exposes per-scene alpha for biome clock crossfades.
public final class BiomeClockSceneState {
    private static final long SCENE_CHANGE_DEBOUNCE_NANOS = 120_000_000L;
    private static final long CAVERN_TRANSITION_NANOS = 280_000_000L;
    private static final long DIMENSION_TRANSITION_NANOS = 280_000_000L;

    private BiomeClockSceneKind currentScene = BiomeClockSceneKind.NORMAL;
    private BiomeClockSceneKind fromScene = BiomeClockSceneKind.NORMAL;
    private BiomeClockSceneKind targetScene = BiomeClockSceneKind.NORMAL;
    private BiomeClockSceneKind pendingScene;
    private long pendingSinceNanos;
    private long transitionStartNanos;
    private long transitionDurationNanos;
    private boolean transitioning;

    public void update(BiomeClockSceneKind requestedScene, long nowNanos) {
        BiomeClockSceneKind safeScene = requestedScene == null ? BiomeClockSceneKind.NORMAL : requestedScene;
        advance(nowNanos);

        BiomeClockSceneKind effectiveTarget = this.transitioning ? this.targetScene : this.currentScene;
        if (safeScene == effectiveTarget) {
            this.pendingScene = null;
            return;
        }

        if (this.pendingScene != safeScene) {
            this.pendingScene = safeScene;
            this.pendingSinceNanos = nowNanos;
            return;
        }

        if (nowNanos - this.pendingSinceNanos >= SCENE_CHANGE_DEBOUNCE_NANOS) {
            beginTransition(safeScene, nowNanos);
        }
    }

    public float alphaFor(BiomeClockSceneKind scene, long nowNanos) {
        if (scene == null) {
            return 0.0F;
        }
        if (!this.transitioning) {
            return scene == this.currentScene ? 1.0F : 0.0F;
        }

        float progress = transitionProgress(nowNanos);
        float eased = smoothstep(progress);
        if (scene == this.fromScene) {
            return 1.0F - eased;
        }
        if (scene == this.targetScene) {
            return eased;
        }
        return 0.0F;
    }

    private void beginTransition(BiomeClockSceneKind target, long nowNanos) {
        this.fromScene = dominantScene(nowNanos);
        this.targetScene = target;
        this.transitionStartNanos = nowNanos;
        this.transitionDurationNanos = transitionDuration(this.fromScene, this.targetScene);
        this.pendingScene = null;

        if (this.fromScene == this.targetScene) {
            this.currentScene = target;
            this.transitioning = false;
            return;
        }

        this.transitioning = true;
    }

    private void advance(long nowNanos) {
        if (!this.transitioning) {
            this.fromScene = this.currentScene;
            this.targetScene = this.currentScene;
            return;
        }
        if (transitionProgress(nowNanos) >= 1.0F) {
            this.currentScene = this.targetScene;
            this.fromScene = this.currentScene;
            this.transitioning = false;
        }
    }

    private BiomeClockSceneKind dominantScene(long nowNanos) {
        if (!this.transitioning) {
            return this.currentScene;
        }
        return transitionProgress(nowNanos) < 0.5F ? this.fromScene : this.targetScene;
    }

    private float transitionProgress(long nowNanos) {
        if (this.transitionDurationNanos <= 0L) {
            return 1.0F;
        }
        return Mth.clamp(
                (nowNanos - this.transitionStartNanos) / (float) this.transitionDurationNanos,
                0.0F,
                1.0F
        );
    }

    private static long transitionDuration(BiomeClockSceneKind from, BiomeClockSceneKind to) {
        if (isNormalCavernPair(from, to)) {
            return CAVERN_TRANSITION_NANOS;
        }
        return DIMENSION_TRANSITION_NANOS;
    }

    private static boolean isNormalCavernPair(BiomeClockSceneKind from, BiomeClockSceneKind to) {
        return (from == BiomeClockSceneKind.NORMAL && to == BiomeClockSceneKind.CAVERNS)
                || (from == BiomeClockSceneKind.CAVERNS && to == BiomeClockSceneKind.NORMAL);
    }

    private static float smoothstep(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
