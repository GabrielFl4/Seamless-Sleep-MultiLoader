package net.aqualoco.sec.mixin.bed;

import com.mojang.datafixers.util.Either;
import net.aqualoco.sec.bed.BedRestingHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Enters vanilla sleeping immediately, then separates "lying down" from "counted for skip" with synced logic.
@Mixin(BedBlock.class)
public abstract class BedBlockRestingMixin {

    @Redirect(
            method = "useWithoutItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;startSleepInBed(Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Either;"
            )
    )
    private Either<Player.BedSleepingProblem, Unit> seamlesssleep$startManagedSleep(Player player, BlockPos bedPos, BlockState state, Level level, BlockPos pos, Player ignoredPlayer, BlockHitResult hitResult) {
        if (!(player instanceof ServerPlayer serverPlayer) || !BedRestingHelper.isOverworldWorkflow(player)) {
            return player.startSleepInBed(bedPos);
        }

        Either<Player.BedSleepingProblem, Unit> sleepResult = player.startSleepInBed(bedPos);
        if (sleepResult.right().isPresent()) {
            BedRestingHelper.initializeAuthoritativeBedLook(serverPlayer, bedPos);
            BedRestingHelper.syncManagedSleepState(serverPlayer, true);
            BedRestingHelper.showLeaveBedHint(serverPlayer);
            return sleepResult;
        }

        Player.BedSleepingProblem problem = sleepResult.left().orElse(null);
        if (!BedRestingHelper.isManagedSleepFallbackProblem(problem)) {
            return sleepResult;
        }

        serverPlayer.startSleeping(bedPos);
        BedRestingHelper.initializeAuthoritativeBedLook(serverPlayer, bedPos);
        boolean countedForSleep = BedRestingHelper.canCountForSleep(serverPlayer, bedPos);
        BedRestingHelper.syncManagedSleepState(serverPlayer, countedForSleep);
        BedRestingHelper.showLeaveBedHint(serverPlayer);
        if (!countedForSleep) {
            BedRestingHelper.showBedHudMessage(serverPlayer, problem.message());
        }
        return Either.right(Unit.INSTANCE);
    }
}
