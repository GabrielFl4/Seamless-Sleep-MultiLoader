package net.aqualoco.sec.platform;

import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.network.SleepAnimationStartPayload;
import net.aqualoco.sec.network.SleepAnimationStopPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

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

                    ResourceLocation worldId = world.dimension().location();
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

                    ResourceLocation worldId = world.dimension().location();
                    if (!worldId.equals(payload.worldId())) {
                        return;
                    }

                    client.execute(() -> SeamlessSleepClientState.SLEEP_ANIMATION.reset());
                }
        );
    }
}
