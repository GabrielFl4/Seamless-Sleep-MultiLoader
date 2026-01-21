package net.aqualoco.sec;

import net.aqualoco.sec.client.SleepStatusOverlay;
import net.aqualoco.sec.config.AquaSecClientConfigManager;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.aqualoco.sec.sleep.ClientSleepAnimationState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;

public class AquaSecClient implements ClientModInitializer {

    public static final ClientSleepAnimationState CLIENT_SLEEP_ANIMATION = new ClientSleepAnimationState();

    @Override
    public void onInitializeClient() {
        AquaSecClientConfigManager.init();
        SleepAnimationNetworking.initClient();
        

        WorldRenderEvents.START.register(context -> {
            ClientWorld world = context.world();
            if (world == null) {
                return;
            }

            if (!world.getRegistryKey().equals(World.OVERWORLD)) {
                return;
            }

            if (CLIENT_SLEEP_ANIMATION.isActive()) {
                CLIENT_SLEEP_ANIMATION.tick(world);
            }
        });
        SleepStatusOverlay.register(CLIENT_SLEEP_ANIMATION);
    }
}
