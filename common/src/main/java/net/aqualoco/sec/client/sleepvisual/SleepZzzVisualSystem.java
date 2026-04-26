package net.aqualoco.sec.client.sleepvisual;

import com.mojang.blaze3d.vertex.PoseStack;
import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.client.ClientBedWorkflow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Coordinates client-side sleep Z emitters without registering vanilla particles or sending packets.
public final class SleepZzzVisualSystem {

    private static final Map<UUID, SleepZzzEmitter> EMITTERS = new LinkedHashMap<>();
    private static ClientLevel activeLevel;

    private SleepZzzVisualSystem() {
    }

    public static void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
            clear();
            activeLevel = null;
            return;
        }

        if (activeLevel != level) {
            clear();
            activeLevel = level;
        }

        int chance = SleepZzzConfigBridge.chance();
        if (chance <= 0) {
            clear();
            return;
        }

        SleepZzzStyle style = SleepZzzConfigBridge.style();
        Set<UUID> seen = new HashSet<>();
        for (Player player : level.players()) {
            UUID playerId = player.getUUID();
            seen.add(playerId);
            boolean countedForSleep = isCountedForSleep(player);
            SleepZzzEmitter emitter = EMITTERS.get(playerId);
            if (countedForSleep) {
                if (emitter == null) {
                    emitter = new SleepZzzEmitter(playerId);
                    EMITTERS.put(playerId, emitter);
                }
                emitter.tick(player, true, chance, style);
            } else if (emitter != null) {
                emitter.tick(player, false, chance, style);
            }
        }

        Iterator<Map.Entry<UUID, SleepZzzEmitter>> iterator = EMITTERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SleepZzzEmitter> entry = iterator.next();
            if (!seen.contains(entry.getKey())) {
                entry.getValue().tickStopped();
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
        float partialTick = resolvePartialTick(client);
        UUID localPlayerId = client.player == null ? null : client.player.getUUID();
        boolean hideLocalFirstPerson = client.player != null
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

    private static boolean isCountedForSleep(Player player) {
        if (player instanceof LocalPlayer localPlayer) {
            return ClientBedWorkflow.isCountedForSleep(localPlayer);
        }
        return BedRestingHelper.isCountedForSleep(player);
    }

    private static float resolvePartialTick(Minecraft client) {
        if (client.gameRenderer == null) {
            return 1.0F;
        }
        Camera camera = client.gameRenderer.getMainCamera();
        return camera == null ? 1.0F : camera.getPartialTickTime();
    }
}
