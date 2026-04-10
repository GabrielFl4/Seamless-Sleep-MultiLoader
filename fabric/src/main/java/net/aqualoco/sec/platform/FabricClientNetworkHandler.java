package net.aqualoco.sec.platform;

import net.aqualoco.sec.client.BedHudMessageManager;
import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.network.BedHudSleepProgressPayload;
import net.aqualoco.sec.config.SeamlessSleepServerConfigSnapshot;
import net.aqualoco.sec.network.ServerConfigSyncPayload;
import net.aqualoco.sec.network.SleepAnimationStartPayload;
import net.aqualoco.sec.network.SleepAnimationStopPayload;
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

                    client.execute(() -> SeamlessSleepClientState.SLEEP_ANIMATION.start(
                            payload.startTimeOfDay(),
                            payload.endTimeOfDay(),
                            payload.durationTicks(),
                            payload.startMillis()
                    ));
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                SleepAnimationStopPayload.ID,
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

                    client.execute(() -> SeamlessSleepClientState.SLEEP_ANIMATION.reset());
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                ServerConfigSyncPayload.ID,
                (payload, context) -> context.client().execute(
                        () -> SeamlessSleepServerConfigSnapshot.update(
                                payload.sleepWeatherClearChancePercent(),
                                payload.sleepAnimationDurationMultiplier()
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
}
