package net.aqualoco.sec.client;

import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.mixin.client.ui.GuiOverlayMessageAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;

// Keeps the two temporary bed HUD message slots in sync with the current client state.
// This is intentionally scoped to the bed workflow instead of becoming a general HUD system.
public final class BedHudMessageManager {

    private static final int DIRECT_SLEEP_ENTRY_GRACE_TICKS = 10;
    private static final int VANILLA_OVERLAY_DURATION_TICKS = 80;
    private static final int CONTEXT_DURATION_TICKS = 120;
    private static final int HINT_DURATION_TICKS = 80;
    private static final float CONTEXT_SCALE = 1.0F;
    private static final float HINT_SCALE = 0.85F;
    private static final int CONTEXT_COLOR_RGB = 0xFFFFFF;
    private static final int HINT_COLOR_RGB = 0xD8D8D8;
    private static final float CONTEXT_ALPHA_MULTIPLIER = 1.0F;
    private static final float HINT_ALPHA_MULTIPLIER = 0.92F;

    @Nullable
    private static TimedMessage seamlesssleep$topMessage;

    @Nullable
    private static TimedMessage seamlesssleep$bottomMessage;

    private static int seamlesssleep$pendingDirectSleepContextUntilTick;

    @Nullable
    private static Component seamlesssleep$pendingSleepProgressText;

    private static int seamlesssleep$pendingSleepProgressExpiresAtTick;

    private BedHudMessageManager() {
    }

    public static void showContextMessage(Component text) {
        seamlesssleep$clearDirectSleepContextReservation();
        seamlesssleep$clearPendingSleepProgress();
        seamlesssleep$bottomMessage = new TimedMessage(
                text,
                seamlesssleep$getHudTick() + CONTEXT_DURATION_TICKS,
                CONTEXT_SCALE,
                CONTEXT_COLOR_RGB,
                CONTEXT_ALPHA_MULTIPLIER,
                MessageKind.CONTEXT
        );
    }

    public static void showHintMessage(Component text) {
        seamlesssleep$topMessage = new TimedMessage(
                text,
                seamlesssleep$getHudTick() + HINT_DURATION_TICKS,
                HINT_SCALE,
                HINT_COLOR_RGB,
                HINT_ALPHA_MULTIPLIER,
                MessageKind.HINT
        );
    }

    public static void syncManagedBedState(@Nullable LocalPlayer player) {
        if (player == null) {
            clearAll();
            return;
        }

        boolean managedBedState = ClientBedWorkflow.isManagedBedState(player);
        boolean pendingContextReservation = seamlesssleep$hasPendingDirectSleepContext();
        if (!managedBedState && !pendingContextReservation) {
            clearAll();
            return;
        }

        seamlesssleep$clearVanillaOverlayMessage();

        if (!ClientBedWorkflow.isCountedForSleep(player) || SeamlessSleepClientState.SLEEP_ANIMATION.isActive()) {
            seamlesssleep$clearSleepProgressContext();
        } else if (seamlesssleep$hasPendingSleepProgress()) {
            seamlesssleep$showSleepProgressContext(seamlesssleep$pendingSleepProgressText);
            seamlesssleep$clearPendingSleepProgress();
        }
    }

    public static void clearAll() {
        seamlesssleep$topMessage = null;
        seamlesssleep$bottomMessage = null;
        seamlesssleep$pendingDirectSleepContextUntilTick = 0;
        seamlesssleep$clearPendingSleepProgress();
    }

    public static boolean captureOverlayMessage(@Nullable Component message) {
        if (message == null) {
            return false;
        }

        if (!(message.getContents() instanceof TranslatableContents contents)) {
            return false;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        boolean managedBedState = player != null && BedRestingHelper.isOverworldWorkflow(player) && ClientBedWorkflow.isManagedBedState(player);
        boolean pendingDirectSleepContext = seamlesssleep$hasPendingDirectSleepContext();
        String key = contents.getKey();

        if ("sleep.skipping_night".equals(key)) {
            seamlesssleep$clearSleepProgressContext();
            seamlesssleep$clearPendingSleepProgress();
            return true;
        }

        if ("seamlesssleep.text.leave_bed".equals(key)) {
            if (player != null && BedRestingHelper.isOverworldWorkflow(player) && !managedBedState) {
                seamlesssleep$reserveDirectSleepContext();
            }
            showHintMessage(message);
            return true;
        }

        if ("sleep.players_sleeping".equals(key)) {
            if (managedBedState || pendingDirectSleepContext) {
                seamlesssleep$clearVanillaOverlayMessage();
                return true;
            }
            return false;
        }

        if (player == null || !BedRestingHelper.isOverworldWorkflow(player)) {
            return false;
        }

        if (seamlesssleep$isEntryContextKey(key)) {
            seamlesssleep$clearDirectSleepContextReservation();
            showContextMessage(message);
            return true;
        }

        if (!managedBedState && !pendingDirectSleepContext) {
            return false;
        }

        if (seamlesssleep$isManagedContextKey(key)) {
            showContextMessage(message);
            return true;
        }

        return false;
    }

    static void pruneExpired() {
        int hudTick = seamlesssleep$getHudTick();
        if (seamlesssleep$topMessage != null && hudTick >= seamlesssleep$topMessage.expiresAtTick()) {
            seamlesssleep$topMessage = null;
        }
        if (seamlesssleep$bottomMessage != null && hudTick >= seamlesssleep$bottomMessage.expiresAtTick()) {
            seamlesssleep$bottomMessage = null;
        }
    }

    @Nullable
    static TimedMessage getTopMessage() {
        return seamlesssleep$topMessage;
    }

    @Nullable
    static TimedMessage getBottomMessage() {
        return seamlesssleep$bottomMessage;
    }

    static boolean shouldReserveBottomSlot() {
        return seamlesssleep$bottomMessage != null || seamlesssleep$hasPendingSleepProgress();
    }

    static float getHudTime(float partialTick) {
        return seamlesssleep$getHudTick() + partialTick;
    }

    public static void handleSleepProgressPayload(int sleepingPlayers, int sleepersNeeded, boolean active) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !BedRestingHelper.isOverworldWorkflow(player)) {
            clearAll();
            return;
        }

        boolean pendingDirectSleepContext = seamlesssleep$hasPendingDirectSleepContext();
        if (!active || SeamlessSleepClientState.SLEEP_ANIMATION.isActive()) {
            seamlesssleep$clearSleepProgressContext();
            seamlesssleep$clearPendingSleepProgress();
            return;
        }

        Component text = Component.translatable("sleep.players_sleeping", sleepingPlayers, sleepersNeeded);
        if (!ClientBedWorkflow.isCountedForSleep(player) && !pendingDirectSleepContext) {
            seamlesssleep$clearSleepProgressContext();
            seamlesssleep$storePendingSleepProgress(text);
            return;
        }

        seamlesssleep$showSleepProgressContext(text);
        seamlesssleep$clearPendingSleepProgress();
        seamlesssleep$clearVanillaOverlayMessage();
    }

    private static int seamlesssleep$getHudTick() {
        Minecraft client = Minecraft.getInstance();
        if (client.gui == null) {
            return 0;
        }
        return client.gui.getGuiTicks();
    }

    private static boolean seamlesssleep$isEntryContextKey(String key) {
        return "block.minecraft.bed.no_sleep".equals(key)
                || "block.minecraft.bed.not_safe".equals(key);
    }

    private static boolean seamlesssleep$isManagedContextKey(String key) {
        return "sleep.not_possible".equals(key) || seamlesssleep$isManagedOnlyContextKey(key);
    }

    private static boolean seamlesssleep$isManagedOnlyContextKey(String key) {
        return "block.minecraft.bed.obstructed".equals(key)
                || "block.minecraft.bed.too_far_away".equals(key);
    }

    private static void seamlesssleep$showSleepProgressContext(Component text) {
        seamlesssleep$clearDirectSleepContextReservation();
        seamlesssleep$bottomMessage = new TimedMessage(
                text,
                seamlesssleep$getHudTick() + VANILLA_OVERLAY_DURATION_TICKS,
                CONTEXT_SCALE,
                CONTEXT_COLOR_RGB,
                CONTEXT_ALPHA_MULTIPLIER,
                MessageKind.SLEEP_PROGRESS
        );
    }

    private static void seamlesssleep$clearSleepProgressContext() {
        if (seamlesssleep$bottomMessage != null && seamlesssleep$bottomMessage.kind() == MessageKind.SLEEP_PROGRESS) {
            seamlesssleep$bottomMessage = null;
        }
    }

    private static void seamlesssleep$storePendingSleepProgress(Component text) {
        seamlesssleep$pendingSleepProgressText = text;
        seamlesssleep$pendingSleepProgressExpiresAtTick = seamlesssleep$getHudTick() + VANILLA_OVERLAY_DURATION_TICKS;
    }

    private static void seamlesssleep$clearPendingSleepProgress() {
        seamlesssleep$pendingSleepProgressText = null;
        seamlesssleep$pendingSleepProgressExpiresAtTick = 0;
    }

    private static boolean seamlesssleep$hasPendingSleepProgress() {
        return seamlesssleep$pendingSleepProgressText != null
                && seamlesssleep$pendingSleepProgressExpiresAtTick > seamlesssleep$getHudTick();
    }

    private static void seamlesssleep$clearVanillaOverlayMessage() {
        Minecraft client = Minecraft.getInstance();
        if (client.gui instanceof GuiOverlayMessageAccessor accessor) {
            accessor.seamlesssleep$setOverlayMessageString(null);
            accessor.seamlesssleep$setOverlayMessageTime(0);
        }
    }

    private static void seamlesssleep$reserveDirectSleepContext() {
        seamlesssleep$pendingDirectSleepContextUntilTick = seamlesssleep$getHudTick() + DIRECT_SLEEP_ENTRY_GRACE_TICKS;
    }

    private static void seamlesssleep$clearDirectSleepContextReservation() {
        seamlesssleep$pendingDirectSleepContextUntilTick = 0;
    }

    private static boolean seamlesssleep$hasPendingDirectSleepContext() {
        return seamlesssleep$pendingDirectSleepContextUntilTick > seamlesssleep$getHudTick();
    }

    private enum MessageKind {
        HINT,
        CONTEXT,
        SLEEP_PROGRESS
    }

    // Simple immutable payload for one HUD line.
    static record TimedMessage(Component text, int expiresAtTick, float scale, int colorRgb, float alphaMultiplier, MessageKind kind) {
    }
}
