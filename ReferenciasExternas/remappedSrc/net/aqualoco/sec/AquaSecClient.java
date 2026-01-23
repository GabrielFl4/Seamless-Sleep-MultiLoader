package net.aqualoco.sec;

import net.aqualoco.sec.client.SleepStatusOverlay;
import net.aqualoco.sec.config.AquaSecClientConfigManager;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.aqualoco.sec.sleep.ClientSleepAnimationState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;

public class AquaSecClient implements ClientModInitializer {

    public static final ClientSleepAnimationState CLIENT_SLEEP_ANIMATION = new ClientSleepAnimationState();

    @Override
    public void onInitializeClient() {
        AquaSecClientConfigManager.init();
        SleepAnimationNetworking.initClient();
        

        WorldRenderEvents.START.register(context -> {
            ClientLevel world = context.world();
            if (world == null) {
                return;
            }

            if (!world.dimension().equals(Level.OVERWORLD)) {
                return;
            }

            if (CLIENT_SLEEP_ANIMATION.isActive()) {
                CLIENT_SLEEP_ANIMATION.tick(world);
            }
        });
        SleepStatusOverlay.register(CLIENT_SLEEP_ANIMATION);
    }
}
