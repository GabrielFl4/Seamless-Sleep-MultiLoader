package net.aqualoco.sec.client.sleepindicator.biomeclock;

// Crossfades biome clock layers after the player remains in a new biome category briefly.
public final class BiomeClockTransitionState {
    private static final long TRANSITION_MS = 280L;
    private static final long BIOME_CHANGE_DEBOUNCE_MS = 120L;

    private BiomeClockCategory currentCategory = BiomeClockCategory.DEFAULT;
    private BiomeClockCategory fromCategory = BiomeClockCategory.DEFAULT;
    private BiomeClockCategory toCategory = BiomeClockCategory.DEFAULT;
    private BiomeClockCategory pendingCategory;
    private long pendingSinceMs;
    private long transitionStartMs;
    private float progress = 1.0F;
    private boolean transitioning;
    private boolean initialized;

    public void update(BiomeClockCategory requestedCategory, long nowMs) {
        BiomeClockCategory safeCategory = requestedCategory == null ? BiomeClockCategory.DEFAULT : requestedCategory;
        if (!this.initialized) {
            initialize(safeCategory);
            return;
        }

        advance(nowMs);

        BiomeClockCategory effectiveTarget = this.transitioning ? this.toCategory : this.currentCategory;
        if (safeCategory == effectiveTarget) {
            this.pendingCategory = null;
            return;
        }

        if (this.pendingCategory != safeCategory) {
            this.pendingCategory = safeCategory;
            this.pendingSinceMs = nowMs;
            return;
        }

        if (nowMs - this.pendingSinceMs >= BIOME_CHANGE_DEBOUNCE_MS) {
            beginTransition(safeCategory, nowMs);
        }
    }

    private void initialize(BiomeClockCategory category) {
        this.currentCategory = category;
        this.fromCategory = category;
        this.toCategory = category;
        this.pendingCategory = null;
        this.progress = 1.0F;
        this.transitioning = false;
        this.initialized = true;
    }

    public BiomeClockCategory fromCategory() {
        return this.transitioning ? this.fromCategory : this.currentCategory;
    }

    public BiomeClockCategory toCategory() {
        return this.toCategory;
    }

    public float fromAlpha() {
        return this.transitioning ? 1.0F - easedProgress() : 1.0F;
    }

    public float toAlpha() {
        return this.transitioning ? easedProgress() : 0.0F;
    }

    public boolean transitioning() {
        return this.transitioning;
    }

    private void beginTransition(BiomeClockCategory targetCategory, long nowMs) {
        this.fromCategory = primaryVisibleCategory();
        this.toCategory = targetCategory;
        this.transitionStartMs = nowMs;
        this.progress = 0.0F;
        this.transitioning = this.fromCategory != this.toCategory;
        this.pendingCategory = null;

        if (!this.transitioning) {
            this.currentCategory = targetCategory;
            this.progress = 1.0F;
        }
    }

    private void advance(long nowMs) {
        if (!this.transitioning) {
            this.progress = 1.0F;
            this.fromCategory = this.currentCategory;
            this.toCategory = this.currentCategory;
            return;
        }

        this.progress = clamp01((nowMs - this.transitionStartMs) / (float) TRANSITION_MS);
        if (this.progress >= 1.0F) {
            this.transitioning = false;
            this.currentCategory = this.toCategory;
            this.fromCategory = this.currentCategory;
            this.toCategory = this.currentCategory;
        }
    }

    private BiomeClockCategory primaryVisibleCategory() {
        if (!this.transitioning) {
            return this.currentCategory;
        }
        return this.progress < 0.5F ? this.fromCategory : this.toCategory;
    }

    private float easedProgress() {
        float t = clamp01(this.progress);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value) || value <= 0.0F) {
            return 0.0F;
        }
        if (value >= 1.0F) {
            return 1.0F;
        }
        return value;
    }
}
