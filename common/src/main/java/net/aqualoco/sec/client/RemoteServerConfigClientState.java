package net.aqualoco.sec.client;

import net.aqualoco.sec.config.SeamlessSleepServerConfigSnapshot;
import net.aqualoco.sec.network.ServerConfigAccessRequestC2SPayload;
import net.aqualoco.sec.network.ServerConfigAccessS2CPayload;
import net.aqualoco.sec.network.ServerConfigSyncPayload;
import net.aqualoco.sec.network.ServerConfigUpdateC2SPayload;
import net.aqualoco.sec.network.ServerConfigUpdateResultS2CPayload;
import net.aqualoco.sec.platform.Services;

public final class RemoteServerConfigClientState {
    private static boolean canEditServerConfig;
    private static int requiredPermissionLevel;
    private static int serverConfigRevision;
    private static Listener activeListener;

    private RemoteServerConfigClientState() {
    }

    public static boolean canEditServerConfig() {
        return canEditServerConfig;
    }

    public static int requiredPermissionLevel() {
        return requiredPermissionLevel;
    }

    public static int serverConfigRevision() {
        return serverConfigRevision;
    }

    public static void reset() {
        canEditServerConfig = false;
        requiredPermissionLevel = 0;
        serverConfigRevision = 0;
        activeListener = null;
        SeamlessSleepServerConfigSnapshot.reset();
    }

    public static void applyServerConfig(ServerConfigSyncPayload payload) {
        SeamlessSleepServerConfigSnapshot.update(
                payload.sleepWeatherClearChancePercent(),
                payload.sleepAnimationDurationMultiplier(),
                payload.fallAsleepDelayTicks(),
                payload.overrideOverlayText(),
                payload.overlayCustomText(),
                payload.sleepEligibility(),
                payload.madeInHeavenChancePercent(),
                payload.serverSimulationDistance(),
                payload.worldSleepAccelerationMode(),
                payload.worldSleepAutomaticMode(),
                payload.worldSleepAccelerationPlayersAffected(),
                payload.manualAccelerationRadiusChunks(),
                payload.manualAccelerationSpeedPercent(),
                payload.grassAndFoliageAccelerationEnabled(),
                payload.cropsAndSaplingsAccelerationEnabled(),
                payload.kelpAccelerationEnabled(),
                payload.vanillaOnlyAcceleration(),
                payload.processesAccelerationEnabled(),
                payload.processesSpeedPercent(),
                payload.betterDaysCompatibilityEnabled()
        );

        Listener listener = activeListener();
        if (listener != null) {
            listener.onServerConfigSnapshotUpdated();
        }
    }

    public static void applyAccess(ServerConfigAccessS2CPayload payload) {
        canEditServerConfig = payload.canEditServerConfig();
        requiredPermissionLevel = payload.requiredPermissionLevel();
        serverConfigRevision = payload.serverConfigRevision();

        Listener listener = activeListener();
        if (listener != null) {
            listener.onServerConfigAccessUpdated();
        }
    }

    public static void applyUpdateResult(ServerConfigUpdateResultS2CPayload payload) {
        serverConfigRevision = payload.serverConfigRevision();

        Listener listener = activeListener();
        if (listener != null) {
            listener.onServerConfigUpdateResult(payload);
        }
    }

    public static void requestAccessRefresh() {
        Services.NETWORK.sendToServer(new ServerConfigAccessRequestC2SPayload());
    }

    public static void sendUpdate(ServerConfigUpdateC2SPayload payload) {
        Services.NETWORK.sendToServer(payload);
    }

    public static void setActiveListener(Listener listener) {
        activeListener = listener;
    }

    private static Listener activeListener() {
        Listener listener = activeListener;
        if (listener != null && !listener.isActive()) {
            activeListener = null;
            return null;
        }
        return listener;
    }

    public interface Listener {
        default boolean isActive() {
            return true;
        }

        void onServerConfigSnapshotUpdated();

        void onServerConfigAccessUpdated();

        void onServerConfigUpdateResult(ServerConfigUpdateResultS2CPayload payload);
    }
}
