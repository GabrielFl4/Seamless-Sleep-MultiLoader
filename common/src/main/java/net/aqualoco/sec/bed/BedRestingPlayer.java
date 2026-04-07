package net.aqualoco.sec.bed;

import net.minecraft.core.BlockPos;

import java.util.Optional;

public interface BedRestingPlayer {

    boolean seamlesssleep$isResting();

    Optional<BlockPos> seamlesssleep$getRestingBedPos();

    boolean seamlesssleep$startResting(BlockPos bedPos);

    void seamlesssleep$stopResting(boolean releaseBed, boolean syncPosition);
}
