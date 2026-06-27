package net.aqualoco.sec.client.sleepvisual;

import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

// Owns the once-per-sleep-session chance roll and active Z glyphs for one player.
public final class SleepZzzEmitter {

    private final UUID playerId;
    private final Random random = new Random();
    private final List<SleepZzzGlyph> glyphs = new ArrayList<>();

    private boolean sessionActive;
    private boolean approvedForSession;
    private boolean stopping = true;
    private int spawnCooldown;
    private int sequentialBurstRemaining;

    public SleepZzzEmitter(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return this.playerId;
    }

    public List<SleepZzzGlyph> glyphs() {
        return this.glyphs;
    }

    public void tick(Player player, boolean countedForSleep, int chance, SleepZzzStyle style) {
        this.tick(player, countedForSleep, chance, style, 1);
    }

    public void tick(Player player, boolean countedForSleep, int chance, SleepZzzStyle style, int ticksToAdvance) {
        if (countedForSleep) {
            this.startSessionIfNeeded(chance, style);
        } else {
            this.stopSession();
        }

        int safeTicks = Math.max(0, ticksToAdvance);
        for (int tick = 0; tick < safeTicks; tick++) {
            this.tickGlyphs();

            if (this.sessionActive && this.approvedForSession) {
                this.tickSpawning(player, style);
            }
        }
    }

    public void stopSession() {
        this.sessionActive = false;
        this.approvedForSession = false;
        this.stopping = true;
        this.sequentialBurstRemaining = 0;
    }

    public void tickStopped() {
        this.tickStopped(1);
    }

    public void tickStopped(int ticksToAdvance) {
        this.stopSession();
        int safeTicks = Math.max(0, ticksToAdvance);
        for (int tick = 0; tick < safeTicks; tick++) {
            this.tickGlyphs();
        }
    }

    public boolean canRemove() {
        return this.stopping && this.glyphs.isEmpty();
    }

    private void startSessionIfNeeded(int chance, SleepZzzStyle style) {
        if (this.sessionActive) {
            this.stopping = false;
            return;
        }

        this.sessionActive = true;
        this.stopping = false;
        this.approvedForSession = chance >= 100 || (chance > 0 && this.random.nextInt(100) < chance);
        this.spawnCooldown = this.approvedForSession ? initialSpawnDelay(style) : Integer.MAX_VALUE;
        this.sequentialBurstRemaining = 0;
    }

    private void tickGlyphs() {
        Iterator<SleepZzzGlyph> iterator = this.glyphs.iterator();
        while (iterator.hasNext()) {
            SleepZzzGlyph glyph = iterator.next();
            glyph.tick(this.random);
            if (!glyph.isAlive()) {
                iterator.remove();
            }
        }
    }

    private void tickSpawning(Player player, SleepZzzStyle style) {
        if (style == SleepZzzStyle.SEQUENTIAL_TRAIL) {
            this.tickSequentialBurst(player);
            return;
        }

        if (this.glyphs.size() >= maxGlyphs(style)) {
            return;
        }

        if (this.spawnCooldown > 0) {
            this.spawnCooldown--;
            return;
        }

        SleepZzzPositioning.Spawn spawn = SleepZzzPositioning.resolve(player, 1.0F, this.random);
        this.glyphs.add(new SleepZzzGlyph(style, spawn, this.random));
        this.spawnCooldown = nextCartoonSpawnInterval();
    }

    private void tickSequentialBurst(Player player) {
        if (this.spawnCooldown > 0) {
            this.spawnCooldown--;
            return;
        }

        if (this.sequentialBurstRemaining <= 0) {
            this.sequentialBurstRemaining = nextSequentialBurstSize();
        }

        if (this.glyphs.size() >= maxGlyphs(SleepZzzStyle.SEQUENTIAL_TRAIL)) {
            this.spawnCooldown = nextSequentialInBurstInterval();
            return;
        }

        SleepZzzPositioning.Spawn spawn = SleepZzzPositioning.resolve(player, 1.0F, this.random);
        this.glyphs.add(new SleepZzzGlyph(SleepZzzStyle.SEQUENTIAL_TRAIL, spawn, this.random));
        this.sequentialBurstRemaining--;
        this.spawnCooldown = this.sequentialBurstRemaining > 0
                ? nextSequentialInBurstInterval()
                : nextSequentialBreathPause();
    }

    private int maxGlyphs(SleepZzzStyle style) {
        return style == SleepZzzStyle.SEQUENTIAL_TRAIL ? 7 : 3;
    }

    private int initialSpawnDelay(SleepZzzStyle style) {
        return style == SleepZzzStyle.SEQUENTIAL_TRAIL
                ? this.random.nextInt(8)
                : 8 + this.random.nextInt(14);
    }

    private int nextCartoonSpawnInterval() {
        return 45 + this.random.nextInt(21);
    }

    private int nextSequentialBurstSize() {
        return 3 + this.random.nextInt(2);
    }

    private int nextSequentialInBurstInterval() {
        return 7 + this.random.nextInt(5);
    }

    private int nextSequentialBreathPause() {
        return 58 + this.random.nextInt(25);
    }
}
