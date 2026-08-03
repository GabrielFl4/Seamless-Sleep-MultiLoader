package net.aqualoco.sec.mixin.compat.vivecraft;

import net.aqualoco.sec.client.VivecraftClientCompat;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.render.helpers.ShaderHelper", remap = false)
public abstract class VivecraftShaderHelperBlackAlphaMixin {
    @Shadow(remap = false)
    private static float BLACK;

    @Redirect(
            method = "doVrPostProcess",
            at = @At(
                    value = "FIELD",
                    target = "Lorg/vivecraft/client_vr/render/helpers/ShaderHelper;BLACK:F",
                    opcode = Opcodes.GETSTATIC,
                    remap = false
            ),
            require = 0,
            remap = false
    )
    private static float seamlesssleep$neutralizeSleepBlackAlphaUniform() {
        return VivecraftClientCompat.shouldNeutralizeSleepBlackAlpha(BLACK) ? 0.0F : BLACK;
    }
}
