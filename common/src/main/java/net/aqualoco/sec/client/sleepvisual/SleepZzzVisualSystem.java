package net.aqualoco.sec.client.sleepvisual;

import com.mojang.blaze3d.vertex.PoseStack;
import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.client.ClientBedWorkflow;
import net.aqualoco.sec.client.ReplayPlaybackCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

// Coordinates client-side sleep Z emitters without registering vanilla particles or sending packets.
public final class SleepZzzVisualSystem {

    private static final long REPLAY_ZZZ_SEEK_THRESHOLD_MILLIS = 1000L;

    private static final Map<UUID, SleepZzzEmitter> EMITTERS = new LinkedHashMap<>();
    private static ClientLevel activeLevel;
    private static boolean replayMode;
    private static long lastReplayZzzMillis = -1L;
    private static double replayZzzTickRemainder;

    private SleepZzzVisualSystem() {
    }

    public static void tick(Minecraft client) {
        if (ReplayPlaybackCompat.isReplayPlaybackActive()) {
            return;
        }
        replayMode = false;
        resetReplayClock();
        tickInternal(client, false, 1);
    }

    public static void tickReplay(Minecraft client) {
        if (!ReplayPlaybackCompat.isReplayPlaybackActive()) {
            if (replayMode) {
                clear();
                replayMode = false;
            }
            resetReplayClock();
            return;
        }
        replayMode = true;
        tickInternal(client, true, computeReplayTicksToAdvance());
    }

    private static int computeReplayTicksToAdvance() {
        OptionalLong replayTimeline = ReplayPlaybackCompat.getReplayTimelineMillis();
        if (replayTimeline.isEmpty()) {
            return 0;
        }

        long replayNowMillis = replayTimeline.getAsLong();
        if (lastReplayZzzMillis < 0L) {
            lastReplayZzzMillis = replayNowMillis;
            replayZzzTickRemainder = 0.0D;
            return 0;
        }

        long deltaMillis = replayNowMillis - lastReplayZzzMillis;
        lastReplayZzzMillis = replayNowMillis;
        if (deltaMillis < 0L || deltaMillis > REPLAY_ZZZ_SEEK_THRESHOLD_MILLIS) {
            clear();
            replayZzzTickRemainder = 0.0D;
            return 0;
        }
        if (deltaMillis == 0L) {
            return 0;
        }

        double replayTicks = deltaMillis / 50.0D + replayZzzTickRemainder;
        int wholeTicks = Math.max(0, (int) Math.floor(replayTicks));
        replayZzzTickRemainder = replayTicks - wholeTicks;
        return wholeTicks;
    }

    private static void tickInternal(Minecraft client, boolean replay, int ticksToAdvance) {
        ClientLevel level = client.level;
        if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
            clear();
            activeLevel = null;
            resetReplayClock();
            return;
        }

        if (activeLevel != level) {
            clear();
            activeLevel = level;
            resetReplayClock();
        }

        int chance = SleepZzzConfigBridge.chance();
        if (chance <= 0) {
            clear();
            resetReplayClock();
            return;
        }

        SleepZzzStyle style = SleepZzzConfigBridge.style();
        int safeTicks = Math.max(0, ticksToAdvance);
        Set<UUID> seen = new HashSet<>();
        for (Player player : level.players()) {
            UUID playerId = player.getUUID();
            seen.add(playerId);
            boolean countedForSleep = replay ? isReplaySleepingCandidate(player) : isCountedForSleep(player);
            SleepZzzEmitter emitter = EMITTERS.get(playerId);
            if (countedForSleep) {
                if (emitter == null) {
                    emitter = new SleepZzzEmitter(playerId);
                    EMITTERS.put(playerId, emitter);
                }
                emitter.tick(player, true, chance, style, safeTicks);
            } else if (emitter != null) {
                emitter.tick(player, false, chance, style, safeTicks);
            }
        }

        Iterator<Map.Entry<UUID, SleepZzzEmitter>> iterator = EMITTERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SleepZzzEmitter> entry = iterator.next();
            if (!seen.contains(entry.getKey())) {
                entry.getValue().tickStopped(safeTicks);
            }
            if (entry.getValue().canRemove()) {
                iterator.remove();
            }
        }
    }

    public static void submitRender(PoseStack poseStack,
                                    CameraRenderState cameraRenderState,
                                    SubmitNodeCollector submitNodeCollector) {
        if (EMITTERS.isEmpty() || SleepZzzConfigBridge.chance() <= 0) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        float partialTick = resolveRenderPartialTick(client);
        UUID localPlayerId = client.player == null ? null : client.player.getUUID();
        boolean hideLocalFirstPerson = !ReplayPlaybackCompat.isReplayPlaybackActive()
                && client.player != null
                && client.getCameraEntity() == client.player
                && client.options.getCameraType().isFirstPerson();

        for (SleepZzzEmitter emitter : EMITTERS.values()) {
            if (hideLocalFirstPerson && emitter.playerId().equals(localPlayerId)) {
                continue;
            }

            for (SleepZzzGlyph glyph : emitter.glyphs()) {
                if (glyph.renderPosition(partialTick).distanceToSqr(cameraRenderState.pos) < 0.09D) {
                    continue;
                }
                SleepZzzRenderer.submit(poseStack, cameraRenderState, submitNodeCollector, glyph, partialTick);
            }
        }
    }

    public static void clear() {
        EMITTERS.clear();
    }

    private static void resetReplayClock() {
        lastReplayZzzMillis = -1L;
        replayZzzTickRemainder = 0.0D;
    }

    private static boolean isCountedForSleep(Player player) {
        if (player instanceof LocalPlayer localPlayer) {
            return ClientBedWorkflow.isCountedForSleep(localPlayer);
        }
        return BedRestingHelper.isCountedForSleep(player);
    }

    private static boolean isReplaySleepingCandidate(Player player) {
        return player != null && (player.isSleeping() || player.getPose() == Pose.SLEEPING);
    }

    private static float resolveRenderPartialTick(Minecraft client) {
        if (ReplayPlaybackCompat.isReplayPlaybackActive()) {
            return Mth.clamp((float) replayZzzTickRemainder, 0.0F, 1.0F);
        }
        return resolvePartialTick(client);
    }

    private static float resolvePartialTick(Minecraft client) {
        if (client.gameRenderer == null) {
            return 1.0F;
        }
        Camera camera = client.gameRenderer.getMainCamera();
        return camera == null ? 1.0F : camera.getPartialTickTime();
    }
}
