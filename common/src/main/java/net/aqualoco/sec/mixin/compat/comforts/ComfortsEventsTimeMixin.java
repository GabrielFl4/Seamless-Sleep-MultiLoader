package net.aqualoco.sec.mixin.compat.comforts;

import net.aqualoco.sec.compat.ComfortsCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.illusivesoulworks.comforts.common.ComfortsEvents", remap = false)
public abstract class ComfortsEventsTimeMixin {

    @Inject(method = "checkTime", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void seamlesssleep$allowManagedDaySleep(Level level,
                                                          BlockPos pos,
                                                          CallbackInfoReturnable<Object> cir) {
        Object result = cir.getReturnValue();
        if (!ComfortsCompat.shouldAllowSeamlessDaySleep(level, pos, result)) {
            return;
        }

        Object allow = ComfortsCompat.comfortsTimeResultLike(result, "ALLOW");
        if (allow != null) {
            cir.setReturnValue(allow);
        }
    }
}
