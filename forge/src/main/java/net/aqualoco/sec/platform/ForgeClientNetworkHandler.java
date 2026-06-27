package net.aqualoco.sec.platform;

import net.aqualoco.sec.client.BedHudMessageManager;
import net.aqualoco.sec.client.RemoteServerConfigClientState;
import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.client.VivecraftClientCompat;
import net.aqualoco.sec.client.sound.SleepSoundManager;
import net.aqualoco.sec.handshake.ClientHandshakeState;
import net.aqualoco.sec.network.BedHudSleepProgressPayload;
import net.aqualoco.sec.network.ServerConfigAccessS2CPayload;
import net.aqualoco.sec.network.ServerConfigSyncPayload;
import net.aqualoco.sec.network.ServerConfigUpdateResultS2CPayload;
import net.aqualoco.sec.network.ServerHelloS2CPayload;
import net.aqualoco.sec.network.SleepAnimationStartPayload;
import net.aqualoco.sec.network.SleepAnimationStopPayload;
import net.aqualoco.sec.network.VivecraftBedOffsetS2CPayload;
import net.aqualoco.sec.sleep.SleepDimensionSupport;
import net.aqualoco.sec.sleep.SleepAnimationStopReason;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
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

        ResourceLocation worldId = world.dimension().location();
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
        if (!isMatchingSupportedWorld(world, payload.worldId())) {
            SeamlessSleepClientState.SLEEP_ANIMATION.resetForWorldExit("start_payload_world_mismatch");
            SleepSoundManager.reset("start_payload_world_mismatch");
            return;
        }

        boolean accepted = SeamlessSleepClientState.SLEEP_ANIMATION.start(
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
        if (accepted) {
            SleepSoundManager.onSleepStart(payload);
        }
    }

    @Override
    public void handleStop(SleepAnimationStopPayload payload) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (!isMatchingSupportedWorld(world, payload.worldId())) {
            SeamlessSleepClientState.SLEEP_ANIMATION.resetForWorldExit("stop_payload_world_mismatch");
            SleepSoundManager.reset("stop_payload_world_mismatch");
            return;
        }

        boolean accepted = SeamlessSleepClientState.SLEEP_ANIMATION.finish(
                world,
                payload.sessionId(),
                payload.finalDayTime(),
                payload.reason()
        );
        if (!accepted) {
            return;
        }
        if (payload.reason() == SleepAnimationStopReason.FINISHED) {
            BedHudMessageManager.suppressSleepProgressMessagesForFinish();
        }
        SleepSoundManager.onSleepStop(payload);
    }

    @Override
    public void handleServerConfig(ServerConfigSyncPayload payload) {
        RemoteServerConfigClientState.applyServerConfig(payload);
    }

    @Override
    public void handleServerConfigAccess(ServerConfigAccessS2CPayload payload) {
        RemoteServerConfigClientState.applyAccess(payload);
    }

    @Override
    public void handleServerConfigUpdateResult(ServerConfigUpdateResultS2CPayload payload) {
        RemoteServerConfigClientState.applyUpdateResult(payload);
    }

    @Override
    public void handleServerHello(ServerHelloS2CPayload payload) {
        ClientHandshakeState.handleServerHello(payload);
    }

    @Override
    public void handleVivecraftBedOffset(VivecraftBedOffsetS2CPayload payload) {
        VivecraftClientCompat.applySyncedBedRoomYOffset(payload);
    }

    private static boolean isMatchingSupportedWorld(ClientLevel world, ResourceLocation payloadWorldId) {
        if (world == null) {
            return false;
        }

        ResourceLocation worldId = world.dimension().location();
        return worldId.equals(payloadWorldId) && SleepDimensionSupport.supportsClientSleepAnimation(world);
    }
}
