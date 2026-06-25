package net.aqualoco.sec.client.sleepindicator;

// Smooths the sleep indicator in and out without making individual renderers own animation state.
public final class SleepIndicatorPresentationState {
    private static final long ENTER_DURATION_MS = 200L;
    private static final long EXIT_DURATION_MS = 160L;
    private static final long VISIBILITY_DEBOUNCE_MS = 60L;

    private static final float ENTER_OFFSET_TOP = -12.0F;
    private static final float ENTER_OFFSET_BOTTOM = 12.0F;
    private static final float ENTER_OFFSET_CENTER = -6.0F;
    private static final float ENTER_SCALE_CENTER = 0.97F;
    private static final float ENTER_SCALE_OTHER = 0.985F;

    private static final float EXIT_OFFSET_TOP = -6.0F;
    private static final float EXIT_OFFSET_BOTTOM = 6.0F;
    private static final float EXIT_OFFSET_CENTER = -4.0F;
    private static final float EXIT_SCALE = 1.0F;

    private SleepIndicatorPresenceState state = SleepIndicatorPresenceState.HIDDEN;
    private SleepIndicatorAnchor transitionAnchor = SleepIndicatorAnchor.TOP_LEFT;
    private boolean targetVisible;
    private boolean exitPending;
    private long exitPendingSinceMs;
    private long transitionStartMs;
    private float transitionStartAlpha;
    private float transitionStartOffsetY;
    private float transitionStartScaleMultiplier = 1.0F;
    private float alpha;
    private float offsetY;
    private float scaleMultiplier = 1.0F;

    public void update(boolean shouldBeVisible, SleepIndicatorAnchor anchor, long nowMs) {
        update(shouldBeVisible, anchor, nowMs, 0L);
    }

    public void update(boolean shouldBeVisible, SleepIndicatorAnchor anchor, long nowMs, long exitLingerMs) {
        SleepIndicatorAnchor safeAnchor = anchor == null ? SleepIndicatorAnchor.TOP_LEFT : anchor;
        long safeExitLingerMs = Math.max(0L, exitLingerMs);
        advance(nowMs);

        if (shouldBeVisible) {
            this.exitPending = false;
            if (!this.targetVisible || this.state == SleepIndicatorPresenceState.EXITING || this.state == SleepIndicatorPresenceState.HIDDEN) {
                this.targetVisible = true;
                beginEntering(safeAnchor, nowMs);
            }
        } else if (this.targetVisible) {
            if (!this.exitPending) {
                this.exitPending = true;
                this.exitPendingSinceMs = nowMs;
            }
            if (nowMs - this.exitPendingSinceMs >= VISIBILITY_DEBOUNCE_MS + safeExitLingerMs) {
                this.targetVisible = false;
                this.exitPending = false;
                beginExiting(safeAnchor, nowMs);
            }
        }

        advance(nowMs);
    }

    public void reset() {
        this.state = SleepIndicatorPresenceState.HIDDEN;
        this.targetVisible = false;
        this.exitPending = false;
        this.transitionAnchor = SleepIndicatorAnchor.TOP_LEFT;
        this.transitionStartMs = 0L;
        this.transitionStartAlpha = 0.0F;
        this.transitionStartOffsetY = 0.0F;
        this.transitionStartScaleMultiplier = 1.0F;
        this.alpha = 0.0F;
        this.offsetY = 0.0F;
        this.scaleMultiplier = 1.0F;
    }

    public boolean shouldRender() {
        return this.state != SleepIndicatorPresenceState.HIDDEN && this.alpha > 0.001F;
    }

    public float alpha() {
        return this.alpha;
    }

    public float offsetY() {
        return this.offsetY;
    }

    public float scaleMultiplier() {
        return this.scaleMultiplier;
    }

    private void beginEntering(SleepIndicatorAnchor anchor, long nowMs) {
        if (this.state == SleepIndicatorPresenceState.HIDDEN || this.alpha <= 0.001F) {
            this.alpha = 0.0F;
            this.offsetY = enterOffset(anchor);
            this.scaleMultiplier = enterScale(anchor);
        }

        beginTransition(SleepIndicatorPresenceState.ENTERING, anchor, nowMs);
    }

    private void beginExiting(SleepIndicatorAnchor anchor, long nowMs) {
        if (this.state == SleepIndicatorPresenceState.HIDDEN || this.alpha <= 0.001F) {
            reset();
            return;
        }

        beginTransition(SleepIndicatorPresenceState.EXITING, anchor, nowMs);
    }

    private void beginTransition(SleepIndicatorPresenceState nextState, SleepIndicatorAnchor anchor, long nowMs) {
        this.state = nextState;
        this.transitionAnchor = anchor;
        this.transitionStartMs = nowMs;
        this.transitionStartAlpha = this.alpha;
        this.transitionStartOffsetY = this.offsetY;
        this.transitionStartScaleMultiplier = this.scaleMultiplier;
    }

    private void advance(long nowMs) {
        switch (this.state) {
            case HIDDEN -> {
                this.alpha = 0.0F;
                this.offsetY = 0.0F;
                this.scaleMultiplier = 1.0F;
            }
            case VISIBLE -> {
                this.alpha = 1.0F;
                this.offsetY = 0.0F;
                this.scaleMultiplier = 1.0F;
            }
            case ENTERING -> advanceEntering(nowMs);
            case EXITING -> advanceExiting(nowMs);
        }
    }

    private void advanceEntering(long nowMs) {
        float progress = transitionProgress(nowMs, ENTER_DURATION_MS);
        float eased = easeOutCubic(progress);
        this.alpha = lerp(this.transitionStartAlpha, 1.0F, eased);
        this.offsetY = lerp(this.transitionStartOffsetY, 0.0F, eased);
        this.scaleMultiplier = lerp(this.transitionStartScaleMultiplier, 1.0F, eased);

        if (progress >= 1.0F) {
            this.state = SleepIndicatorPresenceState.VISIBLE;
            this.alpha = 1.0F;
            this.offsetY = 0.0F;
            this.scaleMultiplier = 1.0F;
        }
    }

    private void advanceExiting(long nowMs) {
        float progress = transitionProgress(nowMs, EXIT_DURATION_MS);
        float eased = easeInQuad(progress);
        this.alpha = lerp(this.transitionStartAlpha, 0.0F, eased);
        this.offsetY = lerp(this.transitionStartOffsetY, exitOffset(this.transitionAnchor), eased);
        this.scaleMultiplier = lerp(this.transitionStartScaleMultiplier, EXIT_SCALE, eased);

        if (progress >= 1.0F) {
            reset();
        }
    }

    private float transitionProgress(long nowMs, long durationMs) {
        if (durationMs <= 0L) {
            return 1.0F;
        }
        return clamp01((nowMs - this.transitionStartMs) / (float) durationMs);
    }

    private static float enterOffset(SleepIndicatorAnchor anchor) {
        if (isTop(anchor)) {
            return ENTER_OFFSET_TOP;
        }
        if (isBottom(anchor)) {
            return ENTER_OFFSET_BOTTOM;
        }
        return ENTER_OFFSET_CENTER;
    }

    private static float exitOffset(SleepIndicatorAnchor anchor) {
        if (isTop(anchor)) {
            return EXIT_OFFSET_TOP;
        }
        if (isBottom(anchor)) {
            return EXIT_OFFSET_BOTTOM;
        }
        return EXIT_OFFSET_CENTER;
    }

    private static float enterScale(SleepIndicatorAnchor anchor) {
        return anchor == SleepIndicatorAnchor.CENTER ? ENTER_SCALE_CENTER : ENTER_SCALE_OTHER;
    }

    private static boolean isTop(SleepIndicatorAnchor anchor) {
        return anchor == SleepIndicatorAnchor.TOP_LEFT
                || anchor == SleepIndicatorAnchor.TOP_CENTER
                || anchor == SleepIndicatorAnchor.TOP_RIGHT;
    }

    private static boolean isBottom(SleepIndicatorAnchor anchor) {
        return anchor == SleepIndicatorAnchor.BOTTOM_RIGHT;
    }

    private static float easeOutCubic(float value) {
        float t = clamp01(value);
        float inverted = 1.0F - t;
        return 1.0F - inverted * inverted * inverted;
    }

    private static float easeInQuad(float value) {
        float t = clamp01(value);
        return t * t;
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * clamp01(progress);
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
