package net.aqualoco.sec.acceleration;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Session-local acceleration cache. Nothing is persisted and all entries are
 * discarded when nature acceleration ends or its compiled policy changes.
 */
public final class WorldSleepNatureSessionCache {
    public static final int MIN_POSITION_CACHED_SECTIONS = 16_384;
    public static final int MAX_POSITION_CACHED_SECTIONS = 131_072;
    private static final int POSITION_CACHED_SECTIONS_PER_ADDITIONAL_PLAYER = 16_384;
    private static final int TRACKED_SECTION_MULTIPLIER = 2;
    private static final int IRRELEVANT_SECTION_RECHECK_TICKS = 20;
    private static final int POSITION_COUNT = 16 * 16 * 16;
    private static final int POSITION_WORD_COUNT = POSITION_COUNT / Long.SIZE;
    private static final long POSITION_CACHE_BYTES_PER_SECTION =
            POSITION_WORD_COUNT * Long.BYTES * 2L;

    private final Map<LevelChunkSection, SectionCache> sections = new IdentityHashMap<>();

    private boolean active;
    private boolean diagnosticsEnabled;
    private long sessionId = Long.MIN_VALUE;
    private int policyCacheKey = Integer.MIN_VALUE;
    private boolean recheckIrrelevantSections;
    private int affectedPlayersForLimit = 1;
    private int maxPositionCachedSections = MIN_POSITION_CACHED_SECTIONS;
    private int maxTrackedSections = MIN_POSITION_CACHED_SECTIONS * TRACKED_SECTION_MULTIPLIER;
    private boolean positionalCacheLimitAtMinimum = true;
    private boolean positionalCacheLimitAtMaximum;
    private int positionCachedSections;
    private boolean sectionTrackingLimitReached;
    private boolean positionalCacheLimitReached;
    private long fallbackToNormalPathCount;
    private long sectionRelevantCacheHits;
    private long sectionRelevantCacheMisses;
    private long sectionRelevantRechecks;
    private Snapshot lastCompletedSnapshot = Snapshot.empty();

    public void updateSession(boolean sessionActive,
                              long accelerationSessionId,
                              int activePolicyCacheKey,
                              boolean recheckIrrelevantSectionsDuringAcceleration,
                              int affectedPlayerCount,
                              boolean diagnosticsEnabledDuringAcceleration) {
        if (!sessionActive) {
            if (active) {
                finishSession();
            }
            return;
        }

        if (!active
                || sessionId != accelerationSessionId
                || policyCacheKey != activePolicyCacheKey
                || recheckIrrelevantSections != recheckIrrelevantSectionsDuringAcceleration
                || affectedPlayersForLimit != Math.max(1, affectedPlayerCount)) {
            if (active) {
                finishSession();
            }
            startSession(
                    accelerationSessionId,
                    activePolicyCacheKey,
                    recheckIrrelevantSectionsDuringAcceleration,
                    affectedPlayerCount,
                    diagnosticsEnabledDuringAcceleration
            );
            return;
        }

        if (diagnosticsEnabled != diagnosticsEnabledDuringAcceleration) {
            diagnosticsEnabled = diagnosticsEnabledDuringAcceleration;
            resetDiagnosticState();
        }
    }

    public SectionCache getOrCreateSection(LevelChunkSection section) {
        if (!active || section == null) {
            return null;
        }

        SectionCache existing = sections.get(section);
        if (existing != null) {
            return existing;
        }
        if (sections.size() >= maxTrackedSections) {
            if (diagnosticsEnabled) {
                sectionTrackingLimitReached = true;
                fallbackToNormalPathCount++;
            }
            return null;
        }

        SectionCache created = new SectionCache();
        sections.put(section, created);
        return created;
    }

    public boolean enablePositionCache(SectionCache sectionCache) {
        if (!active || sectionCache == null) {
            return false;
        }
        if (sectionCache.hasPositionCache()) {
            return true;
        }
        if (positionCachedSections >= maxPositionCachedSections) {
            if (diagnosticsEnabled) {
                positionalCacheLimitReached = true;
                fallbackToNormalPathCount++;
            }
            return false;
        }

        sectionCache.enablePositionCache();
        positionCachedSections++;
        return true;
    }

    public void recordSectionRelevantCacheHit() {
        if (diagnosticsEnabled) {
            sectionRelevantCacheHits++;
        }
    }

    public void recordSectionRelevantCacheMiss() {
        if (diagnosticsEnabled) {
            sectionRelevantCacheMisses++;
        }
    }

    public void recordSectionRelevantRecheck() {
        if (diagnosticsEnabled) {
            sectionRelevantRechecks++;
        }
    }

    public boolean isIrrelevantSectionRecheckEnabled() {
        return recheckIrrelevantSections;
    }

    public Snapshot snapshot() {
        return active ? currentSnapshot(true) : lastCompletedSnapshot;
    }

    private void startSession(long accelerationSessionId,
                              int activePolicyCacheKey,
                              boolean recheckIrrelevantSectionsDuringAcceleration,
                              int affectedPlayerCount,
                              boolean diagnosticsEnabledDuringAcceleration) {
        active = true;
        diagnosticsEnabled = diagnosticsEnabledDuringAcceleration;
        sessionId = accelerationSessionId;
        policyCacheKey = activePolicyCacheKey;
        recheckIrrelevantSections = recheckIrrelevantSectionsDuringAcceleration;
        affectedPlayersForLimit = Math.max(1, affectedPlayerCount);
        long requestedPositionCaches = MIN_POSITION_CACHED_SECTIONS
                + (long) POSITION_CACHED_SECTIONS_PER_ADDITIONAL_PLAYER
                * Math.max(0, affectedPlayersForLimit - 1);
        maxPositionCachedSections = (int) Math.max(
                MIN_POSITION_CACHED_SECTIONS,
                Math.min(MAX_POSITION_CACHED_SECTIONS, requestedPositionCaches)
        );
        maxTrackedSections = Math.max(
                maxPositionCachedSections,
                maxPositionCachedSections * TRACKED_SECTION_MULTIPLIER
        );
        positionalCacheLimitAtMinimum = maxPositionCachedSections == MIN_POSITION_CACHED_SECTIONS;
        positionalCacheLimitAtMaximum = maxPositionCachedSections == MAX_POSITION_CACHED_SECTIONS;
        positionCachedSections = 0;
        resetDiagnosticState();
        sections.clear();
    }

    private void resetDiagnosticState() {
        sectionTrackingLimitReached = false;
        positionalCacheLimitReached = false;
        fallbackToNormalPathCount = 0L;
        sectionRelevantCacheHits = 0L;
        sectionRelevantCacheMisses = 0L;
        sectionRelevantRechecks = 0L;
    }

    private void finishSession() {
        lastCompletedSnapshot = diagnosticsEnabled ? currentSnapshot(false) : Snapshot.empty();
        active = false;
        diagnosticsEnabled = false;
        sessionId = Long.MIN_VALUE;
        policyCacheKey = Integer.MIN_VALUE;
        recheckIrrelevantSections = false;
        affectedPlayersForLimit = 1;
        maxPositionCachedSections = MIN_POSITION_CACHED_SECTIONS;
        maxTrackedSections = MIN_POSITION_CACHED_SECTIONS * TRACKED_SECTION_MULTIPLIER;
        positionalCacheLimitAtMinimum = true;
        positionalCacheLimitAtMaximum = false;
        positionCachedSections = 0;
        sections.clear();
    }

    private Snapshot currentSnapshot(boolean currentlyActive) {
        int trackedSections = sections.size();
        return new Snapshot(
                currentlyActive,
                trackedSections,
                positionCachedSections,
                maxTrackedSections,
                maxPositionCachedSections,
                affectedPlayersForLimit,
                positionalCacheLimitAtMinimum,
                positionalCacheLimitAtMaximum,
                sectionTrackingLimitReached,
                positionalCacheLimitReached,
                fallbackToNormalPathCount,
                sectionRelevantCacheHits,
                sectionRelevantCacheMisses,
                sectionRelevantRechecks,
                positionCachedSections * POSITION_CACHE_BYTES_PER_SECTION,
                recheckIrrelevantSections
        );
    }

    public static final class SectionCache {
        private static final byte RELEVANCE_UNKNOWN = 0;
        private static final byte RELEVANT = 1;
        private static final byte IRRELEVANT = 2;

        private byte relevance = RELEVANCE_UNKNOWN;
        private int nextRelevanceCheckTick = Integer.MIN_VALUE;
        private long[] knownPositions;
        private long[] eligiblePositions;

        public boolean isRelevanceKnown(int currentTick, boolean recheckIrrelevantSections) {
            return relevance == RELEVANT
                    || (relevance == IRRELEVANT
                    && (!recheckIrrelevantSections || currentTick < nextRelevanceCheckTick));
        }

        public boolean isRelevant() {
            return relevance == RELEVANT;
        }

        public boolean isRelevanceClassified() {
            return relevance != RELEVANCE_UNKNOWN;
        }

        public void setRelevant(boolean relevant, int currentTick) {
            relevance = relevant ? RELEVANT : IRRELEVANT;
            nextRelevanceCheckTick = relevant
                    ? Integer.MAX_VALUE
                    : currentTick + IRRELEVANT_SECTION_RECHECK_TICKS;
        }

        public boolean hasPositionCache() {
            return knownPositions != null;
        }

        public boolean isKnown(int positionIndex) {
            if (knownPositions == null) {
                return false;
            }
            int wordIndex = positionIndex >>> 6;
            long bit = 1L << (positionIndex & 63);
            return (knownPositions[wordIndex] & bit) != 0L;
        }

        public boolean isEligible(int positionIndex) {
            if (eligiblePositions == null) {
                return false;
            }
            int wordIndex = positionIndex >>> 6;
            long bit = 1L << (positionIndex & 63);
            return (eligiblePositions[wordIndex] & bit) != 0L;
        }

        public void remember(int positionIndex, boolean eligible) {
            if (knownPositions == null) {
                return;
            }
            int wordIndex = positionIndex >>> 6;
            long bit = 1L << (positionIndex & 63);
            knownPositions[wordIndex] |= bit;
            if (eligible) {
                eligiblePositions[wordIndex] |= bit;
            } else {
                eligiblePositions[wordIndex] &= ~bit;
            }
        }

        private void enablePositionCache() {
            knownPositions = new long[POSITION_WORD_COUNT];
            eligiblePositions = new long[POSITION_WORD_COUNT];
        }
    }

    public record Snapshot(boolean active,
                           int trackedSections,
                           int positionalCachesUsed,
                           int maxTrackedSections,
                           int maxPositionalCaches,
                           int affectedPlayersForLimit,
                           boolean positionalCacheLimitAtMinimum,
                           boolean positionalCacheLimitAtMaximum,
                           boolean sectionTrackingLimitReached,
                           boolean positionalCacheLimitReached,
                           long fallbackToNormalPathCount,
                           long sectionRelevantCacheHits,
                           long sectionRelevantCacheMisses,
                           long sectionRelevantRechecks,
                           long estimatedCacheMemoryBytes,
                           boolean irrelevantSectionRecheckEnabled) {
        private static Snapshot empty() {
            return new Snapshot(
                    false,
                    0,
                    0,
                    MIN_POSITION_CACHED_SECTIONS * TRACKED_SECTION_MULTIPLIER,
                    MIN_POSITION_CACHED_SECTIONS,
                    1,
                    true,
                    false,
                    false,
                    false,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    false
            );
        }

        public double estimatedCacheMemoryMegabytes() {
            return estimatedCacheMemoryBytes / (1024.0D * 1024.0D);
        }
    }
}
