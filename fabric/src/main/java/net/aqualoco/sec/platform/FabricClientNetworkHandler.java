package net.aqualoco.sec.platform;

import net.aqualoco.sec.client.BedHudMessageManager;
import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.network.BedHudSleepProgressPayload;
import net.aqualoco.sec.config.SeamlessSleepServerConfigSnapshot;
import net.aqualoco.sec.network.ServerConfigSyncPayload;
import net.aqualoco.sec.network.SleepAnimationStartPayload;
import net.aqualoco.sec.network.SleepAnimationStopPayload;
import net.aqualoco.sec.sleep.SleepAnimationStopReason;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

// Fabric client packet handlers that start/stop animation and apply synced server config.
@Environment(EnvType.CLIENT)
final class FabricClientNetworkHandler {

    private FabricClientNetworkHandler() {
    }

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                SleepAnimationStartPayload.ID,
                (payload, context) -> {
                    Minecraft client = context.client();
                    client.execute(() -> handleStartPayload(client, payload));
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                SleepAnimationStopPayload.ID,
                (payload, context) -> {
                    Minecraft client = context.client();
                    client.execute(() -> handleStopPayload(client, payload));
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                ServerConfigSyncPayload.ID,
                (payload, context) -> context.client().execute(
                        () -> SeamlessSleepServerConfigSnapshot.update(
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
                                payload.processesSpeedPercent()
                        )
                )
        );

        ClientPlayNetworking.registerGlobalReceiver(
                BedHudSleepProgressPayload.ID,
                (payload, context) -> {
                    Minecraft client = context.client();
                    ClientLevel world = client.level;
                    if (world == null) {
                        return;
                    }

                    Identifier worldId = world.dimension().identifier();
                    if (!worldId.equals(payload.worldId())) {
                        return;
                    }

                    client.execute(() -> BedHudMessageManager.handleSleepProgressPayload(
                            payload.sleepingPlayers(),
                            payload.sleepersNeeded(),
                            payload.active()
                    ));
                }
        );
    }

    private static void handleStartPayload(Minecraft client, SleepAnimationStartPayload payload) {
        ClientLevel world = client.level;
        if (!isMatchingOverworld(world, payload.worldId())) {
            SeamlessSleepClientState.SLEEP_ANIMATION.resetForWorldExit("start_payload_world_mismatch");
            return;
        }

        SeamlessSleepClientState.SLEEP_ANIMATION.start(
                world,
                payload.sessionId(),
                payload.sequenceId(),
                payload.mode(),
                payload.phase(),
                payload.visualContext(),
                payload.soundMode(),
                payload.startTimeOfDay(),
                payload.endTimeOfDay(),
                payload.durationTicks(),
                payload.serverStartGameTime(),
                payload.serverGameTimeAtSend(),
                payload.currentDayTime()
        );
    }

    private static void handleStopPayload(Minecraft client, SleepAnimationStopPayload payload) {
        ClientLevel world = client.level;
        if (!isMatchingOverworld(world, payload.worldId())) {
            SeamlessSleepClientState.SLEEP_ANIMATION.resetForWorldExit("stop_payload_world_mismatch");
            return;
        }

        if (payload.reason() == SleepAnimationStopReason.FINISHED) {
            BedHudMessageManager.suppressSleepProgressMessagesForFinish();
        }
        SeamlessSleepClientState.SLEEP_ANIMATION.finish(
                world,
                payload.sessionId(),
                payload.finalDayTime(),
                payload.reason()
        );
    }

    private static boolean isMatchingOverworld(ClientLevel world, Identifier payloadWorldId) {
        if (world == null) {
            return false;
        }

        Identifier worldId = world.dimension().identifier();
        return worldId.equals(payloadWorldId) && world.dimension().equals(Level.OVERWORLD);
    }
}
