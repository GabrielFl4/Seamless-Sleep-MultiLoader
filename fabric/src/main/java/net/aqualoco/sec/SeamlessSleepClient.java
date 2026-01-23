package net.aqualoco.sec;

import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.client.SleepStatusOverlay;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;

public class SeamlessSleepClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SleepAnimationNetworking.initClient();

        WorldRenderEvents.START.register(context -> {
            ClientLevel world = context.world();
            if (world == null) {
                return;
            }

            if (!world.dimension().equals(Level.OVERWORLD)) {
                return;
            }

            if (SeamlessSleepClientState.SLEEP_ANIMATION.isActive()) {
                SeamlessSleepClientState.SLEEP_ANIMATION.tick(world);
            }
        });

        HudRenderCallback.EVENT.register(
                (graphics, tickDelta) -> SleepStatusOverlay.render(
                        graphics,
                        SeamlessSleepClientState.SLEEP_ANIMATION
                )
        );
    }
}
