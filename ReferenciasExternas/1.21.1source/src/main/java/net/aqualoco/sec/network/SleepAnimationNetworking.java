package net.aqualoco.sec.network;

import net.aqualoco.sec.AquaSec;
import net.aqualoco.sec.AquaSecClient;
import net.aqualoco.sec.sleep.SleepAnimationState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public final class SleepAnimationNetworking {

    private SleepAnimationNetworking() {
    }

    public static void initCommon() {
        PayloadTypeRegistry.playS2C().register(
                SleepAnimationStartPayload.ID,
                SleepAnimationStartPayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                SleepAnimationStopPayload.ID,
                SleepAnimationStopPayload.CODEC
        );
        AquaSec.LOGGER.info("Registered sleep animation payload types (S2C).");
    }

    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                SleepAnimationStartPayload.ID,
                (payload, context) -> {
                    MinecraftClient client = context.client();
                    ClientWorld world = client.world;
                    if (world == null) {
                        return;
                    }

                    Identifier worldId = world.getRegistryKey().getValue();
                    if (!worldId.equals(payload.worldId())) {
                        return;
                    }

                    if (!world.getRegistryKey().equals(World.OVERWORLD)) {
                        return;
                    }

                    client.execute(() -> {
                        AquaSecClient.CLIENT_SLEEP_ANIMATION.start(
                                payload.startTimeOfDay(),
                                payload.endTimeOfDay(),
                                payload.durationTicks(),
                                payload.startMillis()
                        );
                        AquaSec.LOGGER.debug(
                                "Sleep animation (client) started: {} -> {} ({} ticks)",
                                payload.startTimeOfDay(),
                                payload.endTimeOfDay(),
                                payload.durationTicks()
                        );
                    });
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                SleepAnimationStopPayload.ID,
                (payload, context) -> {
                    MinecraftClient client = context.client();
                    ClientWorld world = client.world;
                    if (world == null) {
                        return;
                    }

                    Identifier worldId = world.getRegistryKey().getValue();
                    if (!worldId.equals(payload.worldId())) {
                        return;
                    }

                    client.execute(() -> {
                        AquaSecClient.CLIENT_SLEEP_ANIMATION.reset();
                        AquaSec.LOGGER.debug(
                                "Sleep animation (client) cancelled for world {}",
                                worldId
                        );
                    });
                }
        );

        AquaSec.LOGGER.info("Registered client handlers for sleep animation.");
    }

    /**
     * Called by the ServerWorld mixin when the animation starts.
     */
    public static void sendStart(ServerWorld world, SleepAnimationState state) {
        Identifier worldId = world.getRegistryKey().getValue();
        long startTime = state.getStartTimeOfDay();
        long endTime = state.getEndTimeOfDay();
        int duration = state.getDurationTicks();
        long startMillis = state.getStartMillis();

        SleepAnimationStartPayload payload = new SleepAnimationStartPayload(
                worldId, startTime, endTime, duration, startMillis
        );

        for (ServerPlayerEntity player : world.getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }

        AquaSec.LOGGER.debug(
                "Sent sleep animation payload (start) to {} players ({} -> {}, {} ticks)",
                world.getPlayers().size(), startTime, endTime, duration
        );
    }

    /**
     * Called when the server-side animation is cancelled early.
     */
    public static void sendStop(ServerWorld world) {
        Identifier worldId = world.getRegistryKey().getValue();
        SleepAnimationStopPayload payload = new SleepAnimationStopPayload(worldId);

        for (ServerPlayerEntity player : world.getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }

        AquaSec.LOGGER.debug(
                "Sent sleep animation payload (stop) to {} players",
                world.getPlayers().size()
        );
    }
}
