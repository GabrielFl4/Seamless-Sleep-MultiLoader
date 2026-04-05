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
            method = "render(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            at = @At("STORE"),
            index = 15
    )
    // 26.1 bytecode: local slot 15 is the cloudOffset/phase accumulator before cloudX is derived.
    private float seamlesssleep$injectExtraPhaseIntoF2(float vanillaPhase) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        long now = System.currentTimeMillis();

        if (!seamlesssleep$loggedHookOnce) {
            seamlesssleep$loggedHookOnce = true;
            Constants.debug("Cloud acceleration hook active: CloudRenderer.render -> cloudOffset STORE (local index 15).");
        }

        var sample = seamlesssleep$cloudController.sample(vanillaPhase, level, now);
        seamlesssleep$cloudController.logApplied(now, vanillaPhase, sample.adjustedValue());
        return sample.adjustedValue();
    }
}
