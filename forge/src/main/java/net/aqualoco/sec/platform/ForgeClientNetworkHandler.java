package net.aqualoco.sec.platform;

import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.config.SeamlessSleepServerConfigSnapshot;
import net.aqualoco.sec.network.ServerConfigSyncPayload;
import net.aqualoco.sec.network.SleepAnimationStartPayload;
import net.aqualoco.sec.network.SleepAnimationStopPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

final class ForgeClientNetworkHandler implements ForgeNetworkHelper.ClientHandler {

    @Override
    public void handleStart(SleepAnimationStartPayload payload) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null) {
            return;
        }

        Identifier worldId = world.dimension().identifier();
        if (!worldId.equals(payload.worldId())) {
            return;
        }

        if (!world.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        SeamlessSleepClientState.SLEEP_ANIMATION.start(
                payload.worldId(),
                payload.startTimeOfDay(),
                payload.endTimeOfDay(),
                payload.durationTicks(),
                payload.startMillis()
        );
    }

    @Override
    public void handleStop(SleepAnimationStopPayload payload) {
        if (SeamlessSleepClientState.SLEEP_ANIMATION.isBoundToWorld(payload.worldId())) {
            SeamlessSleepClientState.SLEEP_ANIMATION.reset();
        }
    }

    @Override
    public void handleServerConfig(ServerConfigSyncPayload payload) {
        SeamlessSleepServerConfigSnapshot.update(
                payload.sleepWeatherClearChancePercent(),
                payload.sleepAnimationDurationMultiplier()
        );
    }
}
