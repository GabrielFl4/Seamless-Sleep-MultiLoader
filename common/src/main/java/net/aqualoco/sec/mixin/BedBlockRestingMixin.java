package net.aqualoco.sec.mixin;

import com.mojang.datafixers.util.Either;
import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.bed.BedRestingPlayer;
import net.minecraft.core.BlockPos;
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
        if (player instanceof BedRestingPlayer restingPlayer
                && BedRestingHelper.isOverworldWorkflow(player)
                && !level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.BED_RULE, bedPos).canSleep(level)
                && restingPlayer.seamlesssleep$startResting(bedPos)) {
            return Either.right(Unit.INSTANCE);
        }

        Either<Player.BedSleepingProblem, Unit> sleepResult = player.startSleepInBed(bedPos);
        if (!level.isClientSide()
                && sleepResult.right().isPresent()
                && player instanceof ServerPlayer serverPlayer
                && BedRestingHelper.isOverworldWorkflow(player)) {
            serverPlayer.displayClientMessage(BedRestingHelper.getLeaveBedHintMessage(), true);
        }
        return sleepResult;
    }
}
