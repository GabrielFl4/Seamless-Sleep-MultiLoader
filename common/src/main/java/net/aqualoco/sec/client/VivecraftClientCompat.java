package net.aqualoco.sec.client;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.compat.VivecraftCompat;
import net.aqualoco.sec.config.SeamlessSleepClientConfig;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.aqualoco.sec.network.VivecraftBedOffsetC2SPayload;
import net.aqualoco.sec.network.VivecraftBedOffsetS2CPayload;
import net.aqualoco.sec.network.VivecraftVrStatePayload;
import net.aqualoco.sec.platform.Services;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

// Client-only reflection bridge for Vivecraft public APIs. This keeps Seamless loadable without Vivecraft.
public final class VivecraftClientCompat {
    private static final int VR_STATE_HEARTBEAT_TICKS = 100;
    private static final double DEFAULT_VR_BED_ROOM_Y_OFFSET = SeamlessSleepClientConfig.DEFAULT_VIVECRAFT_BED_ROOM_Y_OFFSET;
    private static final double MIN_VR_BED_ROOM_Y_OFFSET = SeamlessSleepClientConfig.MIN_VIVECRAFT_BED_ROOM_Y_OFFSET;
    private static final double MAX_VR_BED_ROOM_Y_OFFSET = SeamlessSleepClientConfig.MAX_VIVECRAFT_BED_ROOM_Y_OFFSET;
    private static final double BED_OFFSET_SYNC_EPSILON = 1.0E-4D;
    private static final int SCREEN_TRANSITION_CROUCH_WAKE_SUPPRESS_TICKS = 4;

    private static boolean clientApiInitAttempted;
    private static boolean clientApiReady;
    private static boolean playerApiInitAttempted;
    private static boolean playerApiReady;
    private static boolean autoCalibrationInitAttempted;
    private static boolean autoCalibrationReady;
    private static boolean vrInputInitAttempted;
    private static boolean vrInputReady;
    private static boolean clientIntegrationsRegistrationAttempted;
    private static boolean sleepIndicatorInteractModuleRegistered;
    private static boolean loggedClientApiFailure;
    private static boolean loggedPlayerApiFailure;
    private static boolean loggedAutoCalibrationFailure;
    private static boolean loggedVrInputFailure;
    private static boolean loggedClientRegistrationFailure;
    private static boolean loggedInvocationFailure;

    @Nullable
    private static Object clientApi;
    @Nullable
    private static Method clientApiInstanceMethod;
    @Nullable
    private static Method isVrActiveMethod;
    @Nullable
    private static Method addClientRegistrationHandlerMethod;
    @Nullable
    private static Method getLatestRoomPoseMethod;
    @Nullable
    private static Method isLeftHandedMethod;
    @Nullable
    private static Method getPreTickWorldPoseMethod;
    @Nullable
    private static Method getWorldRenderPoseMethod;
    @Nullable
    private static Method getWorldScaleMethod;

    @Nullable
    private static Object playerApi;
    @Nullable
    private static Method playerApiInstanceMethod;
    @Nullable
    private static Method isVrPlayerMethod;

    @Nullable
    private static Method autoCalibrationGetPlayerHeightMethod;
    @Nullable
    private static Method mcvrGetMethod;
    @Nullable
    private static Method mcvrGetInputActionMethod;
    @Nullable
    private static Method vrInputActionIsButtonChangedMethod;
    private static final ClassValue<RoomPoseAccessor> ROOM_POSE_ACCESSORS = new ClassValue<>() {
        @Override
        protected RoomPoseAccessor computeValue(Class<?> type) {
            return new RoomPoseAccessor(optionalMethod(type, "getHead"));
        }
    };
    private static final ClassValue<HeadPoseAccessor> HEAD_POSE_ACCESSORS = new ClassValue<>() {
        @Override
        protected HeadPoseAccessor computeValue(Class<?> type) {
            return new HeadPoseAccessor(optionalMethod(type, "getPos"));
        }
    };

    private static boolean hasSentVrState;
    private static boolean lastSentVrState;
    private static int ticksSinceVrStateSync = VR_STATE_HEARTBEAT_TICKS;
    private static boolean localBedOffsetSessionActive;
    @Nullable
    private static UUID localBedOffsetPlayerId;
    private static double localBedRoomYOffset = DEFAULT_VR_BED_ROOM_Y_OFFSET;
    private static boolean hasSentBedOffsetState;
    private static boolean lastSentBedOffsetActive;
    private static double lastSentBedRoomYOffset = DEFAULT_VR_BED_ROOM_Y_OFFSET;
    private static boolean lastManualCrouchKeyDown;
    @Nullable
    private static Screen lastManualCrouchScreen;
    private static int screenTransitionCrouchWakeSuppressTicks;
    private static final Map<UUID, Double> REMOTE_BED_ROOM_Y_OFFSETS = new HashMap<>();

    private VivecraftClientCompat() {
    }

    public static void registerClientIntegrations() {
        if (clientIntegrationsRegistrationAttempted || !VivecraftCompat.isVivecraftLoaded()) {
            return;
        }

        clientIntegrationsRegistrationAttempted = true;
        try {
            if (!ensureClientApiReady() || addClientRegistrationHandlerMethod == null) {
                return;
            }

            Class<?> interactModuleClass = resolveClass("org.vivecraft.api.client.InteractModule");
            Object moduleProxy = VivecraftSleepWristPanel.createInteractModuleProxy(interactModuleClass);
            Consumer<Object> registrationHandler = event ->
                    registerSleepIndicatorInteractModule(event, interactModuleClass, moduleProxy);
            addClientRegistrationHandlerMethod.invoke(clientApi, registrationHandler);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logClientRegistrationFailure("Vivecraft InteractModule registration failed", exception);
        }
    }

    public static void tick(LocalPlayer player, boolean forceVrStateSync) {
        if (!isVivecraftCompatibilityEnabled()) {
            clearBedRoomYOffsetStateForDisabledConfig(player);
            syncVrStateToServer(isVrActiveLocal(), forceVrStateSync);
            return;
        }
        updateBedRoomYOffsetSession(player);
        syncVrStateToServer(isVrActiveLocal(), forceVrStateSync);
    }

    public static void resetClientSync() {
        hasSentVrState = false;
        lastSentVrState = false;
        ticksSinceVrStateSync = VR_STATE_HEARTBEAT_TICKS;
        clearLocalBedRoomYOffsetSession();
        hasSentBedOffsetState = false;
        lastSentBedOffsetActive = false;
        lastSentBedRoomYOffset = DEFAULT_VR_BED_ROOM_Y_OFFSET;
        lastManualCrouchKeyDown = false;
        clearCrouchWakeScreenSuppression();
        REMOTE_BED_ROOM_Y_OFFSETS.clear();
    }

    public static void sendVrStateToServer(boolean force) {
        syncVrStateToServer(isVrActiveLocal(), force);
    }

    public static boolean isVrActiveLocal() {
        if (!VivecraftCompat.isVivecraftLoaded()) {
            return false;
        }

        try {
            if (!ensureClientApiReady() || isVrActiveMethod == null) {
                return false;
            }

            Object value = isVrActiveMethod.invoke(clientApi);
            return value instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException | LinkageError exception) {
            logInvocationFailure("Vivecraft VR active check failed", exception);
            return false;
        }
    }

    public static boolean shouldUseVrBedPolicy(@Nullable LocalPlayer player) {
        return player != null && isVivecraftCompatibilityEnabled() && isVrActiveLocal();
    }

    public static WristPanelSnapshot captureWristPanelStateSnapshot(@Nullable LocalPlayer player) {
        return captureWristPanelSnapshot(player, PoseSnapshotKind.NONE);
    }

    public static WristPanelSnapshot captureWorldRenderWristPanelSnapshot(@Nullable LocalPlayer player) {
        return captureWristPanelSnapshot(player, PoseSnapshotKind.WORLD_RENDER);
    }

    public static WristPanelSnapshot capturePreTickWristPanelSnapshot(@Nullable LocalPlayer player) {
        return captureWristPanelSnapshot(player, PoseSnapshotKind.PRE_TICK);
    }

    public static void onVivecraftCompatibilityConfigChanged(boolean enabled) {
        if (enabled) {
            return;
        }
        clearBedRoomYOffsetStateForDisabledConfig(Minecraft.getInstance().player);
        lastManualCrouchKeyDown = false;
        clearCrouchWakeScreenSuppression();
    }

    public static boolean shouldApplyVrBedRoomYOffset() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        boolean managedVrBed = player != null
                && client.level != null
                && shouldUseVrBedPolicy(player)
                && ClientBedWorkflow.isManagedBedState(player);
        if (managedVrBed) {
            ensureLocalBedRoomYOffsetSession(player);
        }
        return managedVrBed;
    }

    public static double vrBedRoomYOffset() {
        if (!isVivecraftCompatibilityEnabled()) {
            return 0.0D;
        }
        return localBedOffsetSessionActive ? localBedRoomYOffset : configuredVrBedRoomYOffset();
    }

    public static double vrBedRoomYOffsetForRenderedEntity(int entityId) {
        if (!isVivecraftCompatibilityEnabled()) {
            return 0.0D;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return configuredVrBedRoomYOffset();
        }

        Entity entity = client.level.getEntity(entityId);
        if (!(entity instanceof Player player)) {
            return configuredVrBedRoomYOffset();
        }

        LocalPlayer localPlayer = client.player;
        if (localPlayer != null && player.getUUID().equals(localPlayer.getUUID())) {
            return vrBedRoomYOffset();
        }

        return REMOTE_BED_ROOM_Y_OFFSETS.getOrDefault(player.getUUID(), configuredVrBedRoomYOffset());
    }

    public static void applySyncedBedRoomYOffset(VivecraftBedOffsetS2CPayload payload) {
        if (!isVivecraftCompatibilityEnabled()) {
            REMOTE_BED_ROOM_Y_OFFSETS.clear();
            return;
        }
        if (payload == null || payload.playerId() == null) {
            return;
        }

        if (payload.active() && Double.isFinite(payload.yOffset())) {
            REMOTE_BED_ROOM_Y_OFFSETS.put(payload.playerId(), clampBedRoomYOffset(payload.yOffset()));
        } else {
            REMOTE_BED_ROOM_Y_OFFSETS.remove(payload.playerId());
        }
    }

    public static boolean shouldNeutralizeSleepBlackAlpha(float blackAlpha) {
        if (blackAlpha <= 0.0F || !isVivecraftCompatibilityEnabled() || !isVrActiveLocal()) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        return player != null && player.isSleeping() && ClientBedWorkflow.isManagedBedState(player);
    }

    public static boolean isVrPlayer(Player player) {
        if (player == null) {
            return false;
        }
        if (player instanceof LocalPlayer) {
            return isVrActiveLocal();
        }
        if (!VivecraftCompat.isVivecraftLoaded()) {
            return false;
        }

        try {
            if (!ensurePlayerApiReady() || isVrPlayerMethod == null) {
                return false;
            }

            Object value = isVrPlayerMethod.invoke(playerApi, player);
            return value instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException | LinkageError exception) {
            logInvocationFailure("Vivecraft remote player VR check failed", exception);
            return false;
        }
    }

    public static boolean shouldPreserveVrPlayerRender(Player player) {
        return isVivecraftCompatibilityEnabled() && isVrPlayer(player);
    }

    public static boolean pollManualCrouchButtonPress() {
        if (!isVivecraftCompatibilityEnabled() || !isVrActiveLocal()) {
            lastManualCrouchKeyDown = false;
            clearCrouchWakeScreenSuppression();
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        boolean suppressForScreen = shouldSuppressCrouchWakeForScreenTransition(client);
        Boolean vrActionPress = pollVrCrouchActionPress();
        if (vrActionPress != null) {
            return !suppressForScreen && vrActionPress;
        }

        boolean keyDown = client.options.keyShift.isDown();
        if (suppressForScreen) {
            lastManualCrouchKeyDown = keyDown;
            return false;
        }

        boolean pressed = keyDown && !lastManualCrouchKeyDown;
        lastManualCrouchKeyDown = keyDown;
        return pressed;
    }

    public static boolean isLeftHandedLocal() {
        if (!VivecraftCompat.isVivecraftLoaded()) {
            return false;
        }

        try {
            if (!ensureClientApiReady() || isLeftHandedMethod == null) {
                return false;
            }
            Object value = isLeftHandedMethod.invoke(clientApi);
            return value instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException | LinkageError exception) {
            logInvocationFailure("Vivecraft left-handed mode check failed", exception);
            return false;
        }
    }

    public static float getWorldScale() {
        if (!VivecraftCompat.isVivecraftLoaded()) {
            return 1.0F;
        }

        try {
            if (!ensureClientApiReady() || getWorldScaleMethod == null) {
                return 1.0F;
            }
            Object value = getWorldScaleMethod.invoke(clientApi);
            return value instanceof Number number ? number.floatValue() : 1.0F;
        } catch (ReflectiveOperationException | LinkageError exception) {
            logInvocationFailure("Vivecraft world scale lookup failed", exception);
            return 1.0F;
        }
    }

    @Nullable
    public static Object getPreTickWorldPose() {
        return invokeClientPoseMethod(getPreTickWorldPoseMethod, "Vivecraft pre-tick world pose lookup failed");
    }

    @Nullable
    public static Object getWorldRenderPose() {
        return invokeClientPoseMethod(getWorldRenderPoseMethod, "Vivecraft world render pose lookup failed");
    }

    public static boolean isSleepIndicatorInteractModuleRegistered() {
        return sleepIndicatorInteractModuleRegistered;
    }

    private static void clearBedRoomYOffsetStateForDisabledConfig(@Nullable LocalPlayer player) {
        UUID playerId = localBedOffsetPlayerId != null
                ? localBedOffsetPlayerId
                : player == null ? null : player.getUUID();
        boolean hadActiveSession = localBedOffsetSessionActive;
        clearLocalBedRoomYOffsetSession();
        REMOTE_BED_ROOM_Y_OFFSETS.clear();
        if (hadActiveSession) {
            syncBedRoomYOffsetToServer(playerId, false, 0.0D);
        }
    }

    private static void updateBedRoomYOffsetSession(@Nullable LocalPlayer player) {
        boolean managedVrBed = player != null
                && Minecraft.getInstance().level != null
                && shouldUseVrBedPolicy(player)
                && ClientBedWorkflow.isManagedBedState(player);
        if (managedVrBed) {
            ensureLocalBedRoomYOffsetSession(player);
            return;
        }

        if (localBedOffsetSessionActive) {
            UUID playerId = localBedOffsetPlayerId != null
                    ? localBedOffsetPlayerId
                    : player == null ? null : player.getUUID();
            clearLocalBedRoomYOffsetSession();
            syncBedRoomYOffsetToServer(playerId, false, configuredVrBedRoomYOffset());
        }
    }

    private static void ensureLocalBedRoomYOffsetSession(LocalPlayer player) {
        UUID playerId = player.getUUID();
        if (localBedOffsetSessionActive && playerId.equals(localBedOffsetPlayerId)) {
            return;
        }

        localBedOffsetSessionActive = true;
        localBedOffsetPlayerId = playerId;
        localBedRoomYOffset = computeEffectiveBedRoomYOffset();
        REMOTE_BED_ROOM_Y_OFFSETS.put(playerId, localBedRoomYOffset);
        syncBedRoomYOffsetToServer(playerId, true, localBedRoomYOffset);
    }

    private static void clearLocalBedRoomYOffsetSession() {
        if (localBedOffsetPlayerId != null) {
            REMOTE_BED_ROOM_Y_OFFSETS.remove(localBedOffsetPlayerId);
        }
        localBedOffsetSessionActive = false;
        localBedOffsetPlayerId = null;
        localBedRoomYOffset = configuredVrBedRoomYOffset();
    }

    private static double computeEffectiveBedRoomYOffset() {
        double baseOffset = configuredVrBedRoomYOffset();
        if (baseOffset == 0.0D) {
            return 0.0D;
        }

        Double currentHeight = currentRoomHeadHeight();
        Double calibratedHeight = calibratedPlayerHeight();
        if (currentHeight == null || calibratedHeight == null) {
            return baseOffset;
        }

        double rawCompensation = calibratedHeight - currentHeight;
        double dynamicCompensation = clamp(
                rawCompensation,
                MIN_VR_BED_ROOM_Y_OFFSET - baseOffset,
                MAX_VR_BED_ROOM_Y_OFFSET - baseOffset
        );
        return clampBedRoomYOffset(baseOffset + dynamicCompensation);
    }

    private static double configuredVrBedRoomYOffset() {
        if (!isVivecraftCompatibilityEnabled()) {
            return 0.0D;
        }
        return clampBedRoomYOffset(SeamlessSleepClientConfigManager.get().vivecraftBedRoomYOffset);
    }

    private static double clampBedRoomYOffset(double value) {
        return clamp(value, MIN_VR_BED_ROOM_Y_OFFSET, MAX_VR_BED_ROOM_Y_OFFSET);
    }

    private static boolean isVivecraftCompatibilityEnabled() {
        return SeamlessSleepClientConfigManager.get().vivecraftCompatibilityEnabled;
    }

    private static boolean shouldSuppressCrouchWakeForScreenTransition(Minecraft client) {
        Screen currentScreen = client.screen;
        if (currentScreen != lastManualCrouchScreen) {
            lastManualCrouchScreen = currentScreen;
            screenTransitionCrouchWakeSuppressTicks = SCREEN_TRANSITION_CROUCH_WAKE_SUPPRESS_TICKS;
        }

        if (currentScreen != null) {
            return true;
        }

        if (screenTransitionCrouchWakeSuppressTicks > 0) {
            screenTransitionCrouchWakeSuppressTicks--;
            return true;
        }

        return false;
    }

    private static void clearCrouchWakeScreenSuppression() {
        lastManualCrouchScreen = null;
        screenTransitionCrouchWakeSuppressTicks = 0;
    }

    @Nullable
    private static Boolean pollVrCrouchActionPress() {
        if (!ensureVrInputReady()
                || mcvrGetMethod == null
                || mcvrGetInputActionMethod == null
                || vrInputActionIsButtonChangedMethod == null) {
            return null;
        }

        try {
            Object mcvr = mcvrGetMethod.invoke(null);
            if (mcvr == null) {
                return null;
            }
            KeyMapping sneakKey = Minecraft.getInstance().options.keyShift;
            Object action = mcvrGetInputActionMethod.invoke(mcvr, sneakKey);
            if (action == null) {
                return null;
            }
            Object changed = vrInputActionIsButtonChangedMethod.invoke(action);
            return changed instanceof Boolean changedBool ? changedBool : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logInvocationFailure("Vivecraft crouch input action lookup failed", exception);
            return null;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Nullable
    private static Double currentRoomHeadHeight() {
        Object pose = invokeClientPoseMethod(getLatestRoomPoseMethod, "Vivecraft latest room pose lookup failed");
        if (pose == null) {
            return null;
        }

        try {
            Method getHeadMethod = ROOM_POSE_ACCESSORS.get(pose.getClass()).getHead();
            if (getHeadMethod == null) {
                return null;
            }
            Object head = getHeadMethod.invoke(pose);
            if (head == null) {
                return null;
            }
            Method getPosMethod = HEAD_POSE_ACCESSORS.get(head.getClass()).getPos();
            if (getPosMethod == null) {
                return null;
            }
            Object pos = getPosMethod.invoke(head);
            if (pos instanceof Vec3 vec3 && Double.isFinite(vec3.y)) {
                return vec3.y;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logInvocationFailure("Vivecraft room HMD height lookup failed", exception);
        }
        return null;
    }

    @Nullable
    private static Double calibratedPlayerHeight() {
        if (!ensureAutoCalibrationReady() || autoCalibrationGetPlayerHeightMethod == null) {
            return null;
        }

        try {
            Object value = autoCalibrationGetPlayerHeightMethod.invoke(null);
            if (value instanceof Number number) {
                double height = number.doubleValue();
                return Double.isFinite(height) && height > 0.0D ? height : null;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logInvocationFailure("Vivecraft calibrated height lookup failed", exception);
        }
        return null;
    }

    private static boolean ensureAutoCalibrationReady() {
        if (autoCalibrationInitAttempted) {
            return autoCalibrationReady;
        }

        autoCalibrationInitAttempted = true;
        try {
            Class<?> autoCalibrationClass = resolveClass("org.vivecraft.client_vr.settings.AutoCalibration");
            autoCalibrationGetPlayerHeightMethod = autoCalibrationClass.getMethod("getPlayerHeight");
            autoCalibrationReady = true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            autoCalibrationReady = false;
            if (!loggedAutoCalibrationFailure) {
                loggedAutoCalibrationFailure = true;
                Constants.warn("Vivecraft AutoCalibration could not be resolved. Dynamic VR bed height will use the base offset: {}", rootMessage(exception));
            }
        }

        return autoCalibrationReady;
    }

    private static boolean ensureVrInputReady() {
        if (vrInputInitAttempted) {
            return vrInputReady;
        }

        vrInputInitAttempted = true;
        try {
            Class<?> mcvrClass = resolveClass("org.vivecraft.client_vr.provider.MCVR");
            Class<?> vrInputActionClass = resolveClass("org.vivecraft.client_vr.provider.openvr_lwjgl.VRInputAction");
            mcvrGetMethod = mcvrClass.getMethod("get");
            mcvrGetInputActionMethod = mcvrClass.getMethod("getInputAction", KeyMapping.class);
            vrInputActionIsButtonChangedMethod = vrInputActionClass.getMethod("isButtonChanged");
            vrInputReady = true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            vrInputReady = false;
            if (!loggedVrInputFailure) {
                loggedVrInputFailure = true;
                Constants.warn("Vivecraft VR input action API could not be resolved. VR crouch wake will use key state fallback: {}", rootMessage(exception));
            }
        }

        return vrInputReady;
    }

    private static void syncBedRoomYOffsetToServer(@Nullable UUID playerId, boolean active, double yOffset) {
        if (playerId == null) {
            return;
        }
        if (active && !isVivecraftCompatibilityEnabled()) {
            return;
        }
        if (hasSentBedOffsetState
                && lastSentBedOffsetActive == active
                && Math.abs(lastSentBedRoomYOffset - yOffset) < BED_OFFSET_SYNC_EPSILON) {
            return;
        }

        try {
            if (!Services.NETWORK.canSendToServer(VivecraftBedOffsetC2SPayload.ID)) {
                return;
            }
            Services.NETWORK.sendToServer(new VivecraftBedOffsetC2SPayload(active, yOffset));
            hasSentBedOffsetState = true;
            lastSentBedOffsetActive = active;
            lastSentBedRoomYOffset = yOffset;
        } catch (RuntimeException exception) {
            logInvocationFailure("Failed to sync Vivecraft bed room offset to server", exception);
        }
    }

    private static void syncVrStateToServer(boolean vrActive, boolean force) {
        if (!force && hasSentVrState && lastSentVrState == vrActive && ticksSinceVrStateSync < VR_STATE_HEARTBEAT_TICKS) {
            ticksSinceVrStateSync++;
            return;
        }

        try {
            if (!Services.NETWORK.canSendToServer(VivecraftVrStatePayload.ID)) {
                return;
            }
            Services.NETWORK.sendToServer(new VivecraftVrStatePayload(vrActive));
            hasSentVrState = true;
            lastSentVrState = vrActive;
            ticksSinceVrStateSync = 0;
        } catch (RuntimeException exception) {
            logInvocationFailure("Failed to sync Vivecraft VR state to server", exception);
        }
    }

    private static boolean ensureClientApiReady() {
        if (clientApiInitAttempted) {
            return clientApiReady;
        }

        clientApiInitAttempted = true;
        try {
            Class<?> clientApiClass = resolveClass("org.vivecraft.api.client.VRClientAPI");
            clientApiInstanceMethod = clientApiClass.getMethod("instance");
            clientApi = clientApiInstanceMethod.invoke(null);
            isVrActiveMethod = clientApiClass.getMethod("isVRActive");
            addClientRegistrationHandlerMethod = optionalMethod(clientApiClass, "addClientRegistrationHandler", Consumer.class);
            getLatestRoomPoseMethod = optionalMethod(clientApiClass, "getLatestRoomPose");
            isLeftHandedMethod = optionalMethod(clientApiClass, "isLeftHanded");
            getPreTickWorldPoseMethod = optionalMethod(clientApiClass, "getPreTickWorldPose");
            getWorldRenderPoseMethod = optionalMethod(clientApiClass, "getWorldRenderPose");
            getWorldScaleMethod = optionalMethod(clientApiClass, "getWorldScale");
            clientApiReady = true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            clientApiReady = false;
            if (!loggedClientApiFailure) {
                loggedClientApiFailure = true;
                Constants.warn("Vivecraft client API could not be resolved. VR compatibility will stay inactive: {}", rootMessage(exception));
            }
        }

        return clientApiReady;
    }

    private static boolean ensurePlayerApiReady() {
        if (playerApiInitAttempted) {
            return playerApiReady;
        }

        playerApiInitAttempted = true;
        try {
            Class<?> apiClass = resolveClass("org.vivecraft.api.VRAPI");
            playerApiInstanceMethod = apiClass.getMethod("instance");
            playerApi = playerApiInstanceMethod.invoke(null);
            isVrPlayerMethod = apiClass.getMethod("isVRPlayer", Player.class);
            playerApiReady = true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            playerApiReady = false;
            if (!loggedPlayerApiFailure) {
                loggedPlayerApiFailure = true;
                Constants.warn("Vivecraft player API could not be resolved. Remote VR player preservation will use default Seamless rendering: {}", rootMessage(exception));
            }
        }

        return playerApiReady;
    }

    @Nullable
    private static Object invokeClientPoseMethod(@Nullable Method method, String failureMessage) {
        if (!VivecraftCompat.isVivecraftLoaded()) {
            return null;
        }

        try {
            if (!ensureClientApiReady() || method == null) {
                return null;
            }
            return method.invoke(clientApi);
        } catch (ReflectiveOperationException | LinkageError exception) {
            logInvocationFailure(failureMessage, exception);
            return null;
        }
    }

    private static WristPanelSnapshot captureWristPanelSnapshot(@Nullable LocalPlayer player, PoseSnapshotKind poseKind) {
        if (player == null || !isVivecraftCompatibilityEnabled() || !VivecraftCompat.isVivecraftLoaded()) {
            return WristPanelSnapshot.inactive();
        }
        if (!isVrActiveLocal()) {
            return WristPanelSnapshot.inactive();
        }

        boolean leftHanded = isLeftHandedLocal();
        float worldScale = getWorldScale();
        Object pose = switch (poseKind) {
            case NONE -> null;
            case PRE_TICK -> invokeClientPoseMethod(getPreTickWorldPoseMethod, "Vivecraft pre-tick world pose lookup failed");
            case WORLD_RENDER -> invokeClientPoseMethod(getWorldRenderPoseMethod, "Vivecraft world render pose lookup failed");
        };
        return new WristPanelSnapshot(true, leftHanded, worldScale, pose);
    }

    private static void registerSleepIndicatorInteractModule(
            Object event,
            Class<?> interactModuleClass,
            Object moduleProxy
    ) {
        try {
            Class<?> interactModuleArrayClass = Array.newInstance(interactModuleClass, 0).getClass();
            Method registerInteractModulesMethod = event.getClass()
                    .getMethod("registerInteractModules", interactModuleArrayClass);
            Object modules = Array.newInstance(interactModuleClass, 1);
            Array.set(modules, 0, moduleProxy);
            registerInteractModulesMethod.invoke(event, modules);
            sleepIndicatorInteractModuleRegistered = true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logClientRegistrationFailure("Vivecraft sleep indicator InteractModule could not be registered", exception);
        }
    }

    @Nullable
    private static Method optionalMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private static Class<?> resolveClass(String className) throws ClassNotFoundException {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            try {
                return Class.forName(className, false, contextClassLoader);
            } catch (ClassNotFoundException ignored) {
            }
        }

        return Class.forName(className, false, VivecraftClientCompat.class.getClassLoader());
    }

    private static void logInvocationFailure(String message, Throwable exception) {
        if (loggedInvocationFailure) {
            return;
        }

        loggedInvocationFailure = true;
        Constants.warn("{}: {}", message, rootMessage(exception));
    }

    private static void logClientRegistrationFailure(String message, Throwable exception) {
        if (loggedClientRegistrationFailure) {
            return;
        }

        loggedClientRegistrationFailure = true;
        Constants.warn("{}: {}", message, rootMessage(exception));
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        if (throwable instanceof InvocationTargetException invocationTargetException
                && invocationTargetException.getTargetException() != null) {
            root = invocationTargetException.getTargetException();
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private record RoomPoseAccessor(Method getHead) {
    }

    private record HeadPoseAccessor(Method getPos) {
    }

    private enum PoseSnapshotKind {
        NONE,
        PRE_TICK,
        WORLD_RENDER
    }

    public record WristPanelSnapshot(
            boolean vrBedPolicyActive,
            boolean leftHanded,
            float worldScale,
            @Nullable Object pose
    ) {
        private static final WristPanelSnapshot INACTIVE = new WristPanelSnapshot(false, false, 1.0F, null);

        private static WristPanelSnapshot inactive() {
            return INACTIVE;
        }
    }
}
