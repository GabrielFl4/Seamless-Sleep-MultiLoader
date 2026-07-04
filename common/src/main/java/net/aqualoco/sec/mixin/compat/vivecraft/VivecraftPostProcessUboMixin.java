package net.aqualoco.sec.mixin.compat.vivecraft;

import net.aqualoco.sec.client.VivecraftClientCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.render.ubos.PostProcessUBO", remap = false)
public abstract class VivecraftPostProcessUboMixin {

    @ModifyVariable(
            method = "updateBuffer(FFFFFFFFFFI)V",
            at = @At("HEAD"),
            ordinal = 9,
            argsOnly = true,
            require = 0,
            remap = false
    )
    private float seamlesssleep$neutralizeSleepBlackAlpha(float blackAlpha) {
        return VivecraftClientCompat.shouldNeutralizeSleepBlackAlpha(blackAlpha) ? 0.0F : blackAlpha;
    }
}
