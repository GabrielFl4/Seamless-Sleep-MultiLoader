package net.aqualoco.sec.mixin.bed;

import com.mojang.datafixers.util.Either;
import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.bed.BedRestingPlayer;
import net.aqualoco.sec.network.BedHudNetworking;
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

// Lets beds fall back into the managed resting flow when real vanilla sleep is not available yet.
@Mixin(BedBlock.class)
public abstract class BedBlockRestingMixin {

    @Redirect(
            method = "useWithoutItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;startSleepInBed(Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Either;"
            )
    )
    private Either<Player.BedSleepingProblem, Unit> seamlesssleep$startRestingInsteadOfFailing(Player player, BlockPos bedPos, BlockState state, Level level, BlockPos pos, Player ignoredPlayer, BlockHitResult hitResult) {
        if (!(player instanceof BedRestingPlayer restingPlayer) || !BedRestingHelper.isOverworldWorkflow(player)) {
            return player.startSleepInBed(bedPos);
        }

        var bedRule = level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.BED_RULE, bedPos);
        if (!bedRule.canSleep(level) && restingPlayer.seamlesssleep$startResting(bedPos)) {
            if (player instanceof ServerPlayer serverPlayer) {
                BedRestingHelper.showLeaveBedHint(serverPlayer);
                BedRestingHelper.showBedHudMessage(serverPlayer, BedRestingHelper.getCurrentBedProblemMessage(level, bedPos));
            }
            return Either.right(Unit.INSTANCE);
        }

        Either<Player.BedSleepingProblem, Unit> sleepResult = player.startSleepInBed(bedPos);
        if (sleepResult.right().isPresent()) {
            if (player instanceof ServerPlayer serverPlayer) {
                BedRestingHelper.showLeaveBedHint(serverPlayer);
                BedHudNetworking.syncSleepProgress((ServerLevel) serverPlayer.level());
            }
            return sleepResult;
        }

        Player.BedSleepingProblem problem = sleepResult.left().orElse(null);
        if (problem == Player.BedSleepingProblem.NOT_SAFE && restingPlayer.seamlesssleep$startResting(bedPos)) {
            if (player instanceof ServerPlayer serverPlayer) {
                BedRestingHelper.showLeaveBedHint(serverPlayer);
                BedRestingHelper.showBedHudMessage(serverPlayer, problem.message());
            }
            return Either.right(Unit.INSTANCE);
        }

        return sleepResult;
    }
}
