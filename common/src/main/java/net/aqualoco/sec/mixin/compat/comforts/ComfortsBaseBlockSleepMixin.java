package net.aqualoco.sec.mixin.compat.comforts;

import com.mojang.datafixers.util.Either;
import net.aqualoco.sec.compat.ComfortsCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.illusivesoulworks.comforts.common.block.BaseComfortsBlock", remap = false)
public abstract class ComfortsBaseBlockSleepMixin {

    @Inject(method = "trySleep", at = @At("RETURN"), remap = false, require = 0)
    private static void seamlesssleep$initializeManagedComfortsSleep(ServerPlayer player,
                                                                     BlockPos at,
                                                                     boolean dryRun,
                                                                     CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
        Either<Player.BedSleepingProblem, Unit> result = cir.getReturnValue();
        if (dryRun || result == null || result.right().isEmpty()) {
            return;
        }

        ComfortsCompat.initializeManagedSleep(player, at);
    }
}
