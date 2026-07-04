package net.aqualoco.sec.client.sleepindicator.biomeclock;

// Client-side handoff point from the vanilla lightning hook to the biome clock renderer.
public final class BiomeClockLightningSignal {
    private static final long SAME_ENTITY_EVENT_WINDOW_NANOS = 2_000_000_000L;

    private static volatile Signal latestSignal = Signal.empty();

    private BiomeClockLightningSignal() {
    }

    public static void record(int entityId, int entityTick, long gameTime, double x, double y, double z) {
        Signal current = latestSignal;
        long nowNanos = System.nanoTime();
        if (current.entityId() == entityId
                && current.entityTick() == entityTick
                && current.gameTime() == gameTime) {
            return;
        }

        boolean sameEntityEvent = current.entityId() == entityId
                && nowNanos - current.recordedNanos() <= SAME_ENTITY_EVENT_WINDOW_NANOS;
        long firstSeenNanos = sameEntityEvent ? current.firstSeenNanos() : nowNanos;
        latestSignal = new Signal(
                entityId,
                entityTick,
                gameTime,
                x,
                y,
                z,
                firstSeenNanos,
                nowNanos
        );
    }

    public static Signal latest() {
        return latestSignal;
    }

    public record Signal(
            int entityId,
            int entityTick,
            long gameTime,
            double x,
            double y,
            double z,
            long firstSeenNanos,
            long recordedNanos
    ) {
        private static Signal empty() {
            return new Signal(
                    Integer.MIN_VALUE,
                    Integer.MIN_VALUE,
                    Long.MIN_VALUE,
                    0.0D,
                    0.0D,
                    0.0D,
                    Long.MIN_VALUE,
                    Long.MIN_VALUE
            );
        }

        public boolean present() {
            return this.recordedNanos != Long.MIN_VALUE;
        }
    }
}
