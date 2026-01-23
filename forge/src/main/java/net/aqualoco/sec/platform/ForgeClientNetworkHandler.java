package net.aqualoco.sec.platform;

import net.aqualoco.sec.client.SeamlessSleepClientState;
import net.aqualoco.sec.network.SleepAnimationStartPayload;
import net.aqualoco.sec.network.SleepAnimationStopPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class ForgeClientNetworkHandler implements ForgeNetworkHelper.ClientHandler {

    @Override
    public void handleStart(SleepAnimationStartPayload payload) {
        Minecraft client = Minecraft.getInstance();
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

    @Override
    public void handleStop(SleepAnimationStopPayload payload) {
        Minecraft client = Minecraft.getInstance();
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
