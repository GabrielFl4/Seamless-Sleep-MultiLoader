package net.aqualoco.sec.mixin.client.render;

import net.aqualoco.sec.client.sleepindicator.biomeclock.BiomeClockLightningSignal;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.client.renderer.entity.state.LightningBoltRenderState;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Records real client-side lightning while the vanilla renderer still has the entity.
@Mixin(LightningBoltRenderer.class)
public abstract class LightningBoltRendererBiomeClockMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/LightningBolt;Lnet/minecraft/client/renderer/entity/state/LightningBoltRenderState;F)V",
            at = @At("HEAD")
    )
    private void seamlesssleep$recordBiomeClockLightning(LightningBolt lightningBolt,
                                                         LightningBoltRenderState renderState,
                                                         float tickDelta,
                                                         CallbackInfo ci) {
        BiomeClockLightningSignal.record(
                lightningBolt.getId(),
                lightningBolt.tickCount,
                lightningBolt.level().getGameTime(),
                lightningBolt.getX(),
                lightningBolt.getY(),
                lightningBolt.getZ()
        );
    }
}
