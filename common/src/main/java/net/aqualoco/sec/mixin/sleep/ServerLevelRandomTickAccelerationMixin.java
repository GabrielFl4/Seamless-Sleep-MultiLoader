package net.aqualoco.sec.mixin.sleep;

import net.aqualoco.sec.acceleration.WorldSleepAccelerationManager;
import net.aqualoco.sec.acceleration.WorldSleepAccelerationModuleStatus;
import net.aqualoco.sec.acceleration.WorldSleepAccelerationStatus;
import net.aqualoco.sec.acceleration.WorldSleepRandomTickFilters;
import net.aqualoco.sec.config.WorldSleepNatureFilterProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelRandomTickAccelerationMixin {
    @Inject(method = "tickChunk", at = @At("TAIL"))
    private void seamlesssleep$applyExtraRandomTicks(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        WorldSleepAccelerationStatus status = WorldSleepAccelerationManager.getStatus(self);
        WorldSleepAccelerationModuleStatus natureStatus = status.getNature();
        if (!status.isActive()
                || !natureStatus.isActive()
                || !natureStatus.coversChunk(chunk.getPos().toLong())) {
            return;
        }

        int wholeAttempts = natureStatus.getExtraRandomTickWholeAttemptsPerSection();
        double fractionalAttempts = natureStatus.getExtraRandomTickFractionalAttemptsPerSection();
        if (wholeAttempts <= 0 && fractionalAttempts <= 0.0D) {
            return;
        }

        WorldSleepNatureFilterProfile profile = status.getNatureFilterProfile();
        int minBlockX = chunk.getPos().getMinBlockX();
        int minBlockZ = chunk.getPos().getMinBlockZ();
        LevelChunkSection[] sections = chunk.getSections();
        RandomSource random = self.getRandom();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (!section.isRandomlyTickingBlocks()) {
                continue;
            }

            int attempts = wholeAttempts;
            if (fractionalAttempts > 0.0D && random.nextDouble() < fractionalAttempts) {
                attempts++;
            }
            if (attempts <= 0) {
                continue;
            }

            int sectionY = chunk.getSectionYFromSectionIndex(sectionIndex);
            int minBlockY = SectionPos.sectionToBlockCoord(sectionY);

            for (int i = 0; i < attempts; i++) {
                BlockPos pos = self.getBlockRandomPos(minBlockX, minBlockY, minBlockZ, 15);
                BlockState state = section.getBlockState(
                        pos.getX() - minBlockX,
                        pos.getY() - minBlockY,
                        pos.getZ() - minBlockZ
                );
                if (!WorldSleepRandomTickFilters.isEligible(profile, state)) {
                    continue;
                }

                state.randomTick(self, pos, random);
            }
        }
    }
}
