package net.aqualoco.sec.platform;

import net.aqualoco.sec.client.BedHudMessageManager;
import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.network.BedHudSleepProgressPayload;
import net.aqualoco.sec.config.SeamlessSleepServerConfigSnapshot;
import net.aqualoco.sec.network.ServerConfigSyncPayload;
import net.aqualoco.sec.network.SleepAnimationStartPayload;
import net.aqualoco.sec.network.SleepAnimationStopPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

// Forge client packet handlers that start/stop animation and apply synced server config.
@OnlyIn(Dist.CLIENT)
final class ForgeClientNetworkHandler implements ForgeNetworkHelper.ClientHandler {

    @Override
    public void handleBedHudSleepProgress(BedHudSleepProgressPayload payload) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null) {
            return;
        }

        Identifier worldId = world.dimension().identifier();
        if (!worldId.equals(payload.worldId())) {
            return;
        }

        BedHudMessageManager.handleSleepProgressPayload(
                payload.sleepingPlayers(),
                payload.sleepersNeeded(),
                payload.active()
        );
    }

    @Override
    public void handleStart(SleepAnimationStartPayload payload) {
        Minecraft client = Minecraft.getInstance();
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
                payload.startTimeOfDay(),
                payload.endTimeOfDay(),
                payload.durationTicks(),
                payload.serverStartGameTime(),
                payload.serverGameTimeAtSend(),
                payload.currentDayTime()
        );
    }

    @Override
    public void handleStop(SleepAnimationStopPayload payload) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (!isMatchingOverworld(world, payload.worldId())) {
            SeamlessSleepClientState.SLEEP_ANIMATION.resetForWorldExit("stop_payload_world_mismatch");
            return;
        }

        SeamlessSleepClientState.SLEEP_ANIMATION.finish(
                world,
                payload.sessionId(),
                payload.finalDayTime(),
                payload.reason()
        );
    }

    @Override
    public void handleServerConfig(ServerConfigSyncPayload payload) {
        SeamlessSleepServerConfigSnapshot.update(
                payload.sleepWeatherClearChancePercent(),
                payload.sleepAnimationDurationMultiplier(),
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
