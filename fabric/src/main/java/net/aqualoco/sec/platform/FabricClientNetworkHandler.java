package net.aqualoco.sec.platform;

import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.config.SeamlessSleepServerConfigSnapshot;
import net.aqualoco.sec.network.ServerConfigSyncPayload;
import net.aqualoco.sec.network.SleepAnimationStartPayload;
import net.aqualoco.sec.network.SleepAnimationStopPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

// Fabric client packet handlers that start/stop animation and apply synced server config.
@Environment(EnvType.CLIENT)
final class FabricClientNetworkHandler {

    private FabricClientNetworkHandler() {
    }

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                SleepAnimationStartPayload.ID,
                (client, handler, buf, responseSender) -> {
                    SleepAnimationStartPayload payload = SleepAnimationStartPayload.read(buf);
                    client.execute(() -> applyStart(client, payload));
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                SleepAnimationStopPayload.ID,
                (client, handler, buf, responseSender) -> {
                    SleepAnimationStopPayload payload = SleepAnimationStopPayload.read(buf);
                    client.execute(() -> applyStop(client, payload));
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                ServerConfigSyncPayload.ID,
                (client, handler, buf, responseSender) -> {
                    ServerConfigSyncPayload payload = ServerConfigSyncPayload.read(buf);
                    client.execute(() -> SeamlessSleepServerConfigSnapshot.update(
                            payload.sleepClearsWeather(),
                            payload.sleepAnimationDurationMultiplier()
                    ));
                }
        );
    }

    private static void applyStart(Minecraft client, SleepAnimationStartPayload payload) {
        ClientLevel world = client.level;
        if (world == null) {
            return;
        }

        ResourceLocation worldId = world.dimension().location();
        if (!worldId.equals(payload.worldId())) {
            return;
        }

        if (!world.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        SeamlessSleepClientState.SLEEP_ANIMATION.start(
                payload.startTimeOfDay(),
                payload.endTimeOfDay(),
                payload.durationTicks(),
                payload.startMillis()
        );
    }

    private static void applyStop(Minecraft client, SleepAnimationStopPayload payload) {
        ClientLevel world = client.level;
        if (world == null) {
            return;
        }

        ResourceLocation worldId = world.dimension().location();
        if (!worldId.equals(payload.worldId())) {
            return;
        }

        SeamlessSleepClientState.SLEEP_ANIMATION.reset();
    }
}
