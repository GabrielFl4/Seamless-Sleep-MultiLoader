package net.aqualoco.sec.mixin.client.render;

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
// Feeds the active Seamless animation speed into vanilla clouds so they keep up with the sky transition.
@Mixin(CloudRenderer.class)
public abstract class CloudRendererSleepAccelerationMixin {

    @Unique
    private static final CloudAccelerationController seamlesssleep$cloudController = new CloudAccelerationController("Vanilla");

    @Unique
    private static boolean seamlesssleep$loggedHookOnce;

    @ModifyVariable(
            method = "render(ILnet/minecraft/client/CloudStatus;FLnet/minecraft/world/phys/Vec3;F)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 5
    )
    private float seamlesssleep$injectExtraPhaseIntoCloudTime(float vanillaCloudTime) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        long now = System.currentTimeMillis();

        if (!seamlesssleep$loggedHookOnce) {
            seamlesssleep$loggedHookOnce = true;
            Constants.debug("Cloud acceleration hook active: CloudRenderer.render -> cloudTime argument.");
        }

        var sample = seamlesssleep$cloudController.sample(vanillaCloudTime, level, now);
        seamlesssleep$cloudController.logApplied(now, vanillaCloudTime, sample.adjustedValue());
        return sample.adjustedValue();
    }
}
