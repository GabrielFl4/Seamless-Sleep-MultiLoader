package net.aqualoco.sec.platform;

import net.aqualoco.sec.network.BedHudSleepProgressPayload;
import net.aqualoco.sec.network.BedLookNetworking;
import net.aqualoco.sec.network.BedLookSyncPayload;
import net.aqualoco.sec.network.ServerConfigSyncPayload;
import net.aqualoco.sec.network.SleepAnimationStartPayload;
import net.aqualoco.sec.network.SleepAnimationStopPayload;
import net.aqualoco.sec.platform.services.INetworkHelper;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// Fabric networking bridge for payload registration and player sends.
public class FabricNetworkHelper implements INetworkHelper {

    private static Method seamlesssleep$clientSendMethod;
    private static boolean seamlesssleep$clientSendResolved;

    @Override
    public void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(
                SleepAnimationStartPayload.ID,
                SleepAnimationStartPayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                SleepAnimationStopPayload.ID,
                SleepAnimationStopPayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                ServerConfigSyncPayload.ID,
                ServerConfigSyncPayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                BedHudSleepProgressPayload.ID,
                BedHudSleepProgressPayload.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
                BedLookSyncPayload.ID,
                BedLookSyncPayload.CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(
                BedLookSyncPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> BedLookNetworking.handleServer(context.player(), payload)
                )
        );
    }

    @Override
    public void registerClientHandlers() {
        FabricClientNetworkHandler.register();
    }

    @Override
    public void sendToPlayers(ServerLevel world, CustomPacketPayload payload) {
        for (ServerPlayer player : world.players()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        Method sendMethod = seamlesssleep$resolveClientSendMethod();
        if (sendMethod == null) {
            throw new IllegalStateException("Fabric ClientPlayNetworking.send(CustomPacketPayload) is unavailable");
        }

        try {
            sendMethod.invoke(null, payload);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new RuntimeException("Failed to send Fabric client payload", exception);
        }
    }

    private static Method seamlesssleep$resolveClientSendMethod() {
        if (seamlesssleep$clientSendResolved) {
            return seamlesssleep$clientSendMethod;
        }

        seamlesssleep$clientSendResolved = true;
        try {
            Class<?> clientNetworkingClass = Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking");
            seamlesssleep$clientSendMethod = clientNetworkingClass.getMethod("send", CustomPacketPayload.class);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            seamlesssleep$clientSendMethod = null;
        }

        return seamlesssleep$clientSendMethod;
    }
}
