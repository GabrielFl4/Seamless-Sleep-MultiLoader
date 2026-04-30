package net.aqualoco.sec;

import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.client.SleepStatusOverlay;
import net.aqualoco.sec.network.SleepAnimationNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;

// Fabric-side bootstrap for sleep render hooks.
public class SeamlessSleepClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SleepAnimationNetworking.initClient();

        HudRenderCallback.EVENT.register(
                (graphics, tickDelta) -> {
                    ClientLevel world = Minecraft.getInstance().level;
                    if (world == null) {
                        SeamlessSleepClientState.SLEEP_ANIMATION.resetForWorldExit("fabric_hud_world_null");
                    } else if (!world.dimension().equals(Level.OVERWORLD)) {
                        SeamlessSleepClientState.SLEEP_ANIMATION.resetForWorldExit("fabric_hud_non_overworld");
                    } else {
                        SeamlessSleepClientState.SLEEP_ANIMATION.resetIfWorldMismatch(world, "fabric_hud_world_changed");
                    }

                    SleepStatusOverlay.render(
                            graphics,
                            SeamlessSleepClientState.SLEEP_ANIMATION
                    );
                }
        );
    }
}
