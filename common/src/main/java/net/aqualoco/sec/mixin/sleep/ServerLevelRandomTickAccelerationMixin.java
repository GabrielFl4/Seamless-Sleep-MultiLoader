package net.aqualoco.sec.mixin.sleep;

import net.aqualoco.sec.acceleration.WorldSleepAccelerationManager;
import net.aqualoco.sec.acceleration.WorldSleepAccelerationModuleStatus;
import net.aqualoco.sec.acceleration.WorldSleepAccelerationStatus;
import net.aqualoco.sec.acceleration.WorldSleepAccelerationFilterPolicy;
import net.aqualoco.sec.acceleration.WorldSleepNatureSessionCache;
import net.aqualoco.sec.acceleration.WorldSleepAccelerationTelemetry;
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

        WorldSleepAccelerationTelemetry telemetry = status.getTelemetry();
        boolean telemetryEnabled = telemetry != null && telemetry.isEnabled();
        WorldSleepAccelerationFilterPolicy filterPolicy = status.getFilterPolicy();
        WorldSleepNatureSessionCache sessionCache = status.getNatureSessionCache();
        int minBlockX = chunk.getPos().getMinBlockX();
        int minBlockZ = chunk.getPos().getMinBlockZ();
        LevelChunkSection[] sections = chunk.getSections();
        int currentTick = self.getServer().getTickCount();
        if (telemetryEnabled) {
            telemetry.recordChunkVisited();
        }
        if (sections.length == 0) {
            return;
        }

        RandomSource random = self.getRandom();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section == null || !section.isRandomlyTickingBlocks()) {
                if (telemetryEnabled) {
                    telemetry.recordSkippedSection();
                }
                continue;
            }

            WorldSleepNatureSessionCache.SectionCache sectionCache =
                    sessionCache == null ? null : sessionCache.getOrCreateSection(section);
            boolean sectionRelevant;
            boolean recheckIrrelevantSections = sessionCache != null
                    && sessionCache.isIrrelevantSectionRecheckEnabled();
            if (sectionCache != null
                    && sectionCache.isRelevanceKnown(currentTick, recheckIrrelevantSections)) {
                if (telemetryEnabled) {
                    sessionCache.recordSectionRelevantCacheHit();
                }
                sectionRelevant = sectionCache.isRelevant();
            } else {
                if (telemetryEnabled && sectionCache != null) {
                    if (sectionCache.isRelevanceClassified()) {
                        sessionCache.recordSectionRelevantRecheck();
                    } else {
                        sessionCache.recordSectionRelevantCacheMiss();
                    }
                }
                sectionRelevant = section.maybeHas(filterPolicy::mayContainRelevantState);
                if (sectionCache != null) {
                    sectionCache.setRelevant(sectionRelevant, currentTick);
                }
            }
            if (!sectionRelevant) {
                if (telemetryEnabled) {
                    telemetry.recordSectionRelevantSkip();
                }
                continue;
            }

            int attempts = wholeAttempts;
            if (fractionalAttempts > 0.0D && random.nextDouble() < fractionalAttempts) {
                attempts++;
            }
            if (telemetryEnabled) {
                telemetry.recordRandomTickingSection(attempts);
            }
            if (attempts <= 0) {
                continue;
            }

            int sectionY = chunk.getSectionYFromSectionIndex(sectionIndex);
            int minBlockY = SectionPos.sectionToBlockCoord(sectionY);
            int rejectedByFilter = 0;
            int acceptedRandomTicks = 0;
            int blockStateReads = 0;
            int knownNegativeSkips = 0;
            int knownPositiveHits = 0;
            int unknownClassifications = 0;
            boolean positionCacheEnabled = sessionCache != null
                    && sessionCache.enablePositionCache(sectionCache);

            for (int i = 0; i < attempts; i++) {
                BlockPos pos = self.getBlockRandomPos(minBlockX, minBlockY, minBlockZ, 15);
                int localX = pos.getX() - minBlockX;
                int localY = pos.getY() - minBlockY;
                int localZ = pos.getZ() - minBlockZ;
                int positionIndex = (localY << 8) | (localZ << 4) | localX;

                boolean knownPosition = positionCacheEnabled && sectionCache.isKnown(positionIndex);
                if (knownPosition && !sectionCache.isEligible(positionIndex)) {
                    if (telemetryEnabled) {
                        knownNegativeSkips++;
                        rejectedByFilter++;
                    }
                    continue;
                }

                BlockState state = section.getBlockState(
                        localX,
                        localY,
                        localZ
                );
                if (telemetryEnabled) {
                    blockStateReads++;
                    if (knownPosition) {
                        knownPositiveHits++;
                    } else {
                        unknownClassifications++;
                    }
                }

                boolean eligible = filterPolicy.allows(state);
                if (positionCacheEnabled) {
                    sectionCache.remember(positionIndex, eligible);
                }
                if (!eligible) {
                    if (telemetryEnabled) {
                        rejectedByFilter++;
                    }
                    continue;
                }

                if (telemetryEnabled) {
                    acceptedRandomTicks++;
                }
                state.randomTick(self, pos, random);
            }
            if (telemetryEnabled) {
                telemetry.recordAttemptResults(
                        rejectedByFilter,
                        acceptedRandomTicks,
                        blockStateReads,
                        knownNegativeSkips,
                        knownPositiveHits,
                        unknownClassifications
                );
            }
        }
    }
}
