package net.aqualoco.sec.mixin.client;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.client.CloudAccelerationController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Cloud phase boost while the sleep transition is running.
@Mixin(CloudRenderer.class)
public abstract class CloudRendererSleepAccelerationMixin {

    @Unique
    private static final CloudAccelerationController seamlesssleep$cloudController = new CloudAccelerationController("Vanilla");

    @Unique
    private static boolean seamlesssleep$loggedHookOnce;

    @ModifyVariable(
            method = "render(ILnet/minecraft/client/CloudStatus;FLnet/minecraft/world/phys/Vec3;JF)V",
            at = @At("STORE"),
            index = 14
    )
    // 1.21.11 bytecode: local slot 14 is the cloud phase accumulator before the final offset is consumed.
    private float seamlesssleep$injectExtraPhaseIntoF2(float vanillaPhase) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        long now = System.currentTimeMillis();

        if (!seamlesssleep$loggedHookOnce) {
            seamlesssleep$loggedHookOnce = true;
            Constants.debug("Cloud acceleration hook active: CloudRenderer.render -> cloud phase STORE (local index 14).");
        }

        var sample = seamlesssleep$cloudController.sample(vanillaPhase, level, now);
        seamlesssleep$cloudController.logApplied(now, vanillaPhase, sample.adjustedValue());
        return sample.adjustedValue();
    }
}
