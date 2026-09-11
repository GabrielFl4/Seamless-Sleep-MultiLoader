package net.aqualoco.sec.mixin.client.compat;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.client.BetterCloudsSleepTimeAccess;
import net.aqualoco.sec.client.CloudAccelerationController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

// Reuses the vanilla sleep acceleration curve for Better Clouds by overriding the custom renderer time inputs.
@Pseudo
@Mixin(targets = "com.qendolin.betterclouds.rendering.CloudRenderer", remap = false)
public abstract class BetterCloudsRendererSleepAccelerationMixin implements BetterCloudsSleepTimeAccess {

    @Unique
    private static boolean seamlesssleep$loggedHookOnce;

    @Unique
    private final CloudAccelerationController seamlesssleep$cloudController = new CloudAccelerationController("Better Clouds");

    @Unique
    private float seamlesssleep$preparedExtraTicks;

    @ModifyArgs(
            method = "updateGenerator(Lorg/joml/Vector3d;JJF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/qendolin/betterclouds/generator/ChunkedGenerator;update(Lorg/joml/Vector3d;JJFLcom/qendolin/betterclouds/config/Config;F)V"
            ),
            remap = false,
            require = 0
    )
    private void seamlesssleep$adjustBetterCloudsGeneratorTime(Args args) {
        if (!seamlesssleep$loggedHookOnce) {
            seamlesssleep$loggedHookOnce = true;
            Constants.debug("Better Clouds acceleration hook active: CloudRenderer.updateGenerator and renderer visual time override.");
        }

        long now = System.currentTimeMillis();
        long cloudTicks = args.get(1);
        float tickDelta = args.get(3);
        var sample = seamlesssleep$cloudController.sample(tickDelta, Minecraft.getInstance().level, now);
        seamlesssleep$preparedExtraTicks = sample.extraTicks();

        // Keep the full cloud clock and the generator's client tick counter independent.
        args.set(1, cloudTicks + sample.wholeTicks());
        args.set(3, sample.partialTick());

        float baseTime = cloudTicks + tickDelta;
        seamlesssleep$cloudController.logApplied(now, baseTime, baseTime + sample.extraTicks());
    }

    @Override
    public float seamlesssleep$getCloudPhaseOffset() {
        return seamlesssleep$preparedExtraTicks;
    }
}
