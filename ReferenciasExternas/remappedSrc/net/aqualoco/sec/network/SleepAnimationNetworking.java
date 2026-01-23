package net.aqualoco.sec.network;

import net.aqualoco.sec.AquaSec;
import net.aqualoco.sec.AquaSecClient;
import net.aqualoco.sec.sleep.SleepAnimationState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

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
                    Minecraft client = context.client();
                    ClientLevel world = client.level;
                    if (world == null) {
                        return;
                    }

                    ResourceLocation worldId = world.dimension().location();
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
    public static void sendStart(ServerLevel world, SleepAnimationState state) {
        ResourceLocation worldId = world.dimension().location();
        long startTime = state.getStartTimeOfDay();
        long endTime = state.getEndTimeOfDay();
        int duration = state.getDurationTicks();
        long startMillis = state.getStartMillis();

        SleepAnimationStartPayload payload = new SleepAnimationStartPayload(
                worldId, startTime, endTime, duration, startMillis
        );

        for (ServerPlayer player : world.players()) {
            ServerPlayNetworking.send(player, payload);
        }

        AquaSec.LOGGER.debug(
                "Sent sleep animation payload (start) to {} players ({} -> {}, {} ticks)",
                world.players().size(), startTime, endTime, duration
        );
    }

    /**
     * Called when the server-side animation is cancelled early.
     */
    public static void sendStop(ServerLevel world) {
        ResourceLocation worldId = world.dimension().location();
        SleepAnimationStopPayload payload = new SleepAnimationStopPayload(worldId);

        for (ServerPlayer player : world.players()) {
            ServerPlayNetworking.send(player, payload);
        }

        AquaSec.LOGGER.debug(
                "Sent sleep animation payload (stop) to {} players",
                world.players().size()
        );
    }
}
