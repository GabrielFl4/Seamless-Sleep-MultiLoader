package net.aqualoco.sec.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.aqualoco.sec.Constants;
import net.aqualoco.sec.client.sleepindicator.VivecraftSleepWristIndicatorRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

// Shared Vivecraft wrist-panel geometry used by both the InteractModule hitbox and the indicator render.
public final class VivecraftSleepWristPanel {
    private static final ResourceLocation PANEL_ID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sleep_indicator");
    private static final String VIVECRAFT_HOTBAR_MODULE_ID = "vivecraft:interactive_hotbar";
    private static final int INTERACT_PRIORITY_AFTER_HOTBAR = 100;

    // Physical square size for the approved plane and hitbox before Vivecraft world scale.
    private static final float PANEL_SIZE = 0.10F;
    // 1.0 tries one side of the wrist; -1.0 flips the panel to the opposite side.
    private static final float PANEL_TOP_NORMAL_SIGN = 1.0F;
    // Moves the panel away from the arm surface along panelNormal.
    private static final float PANEL_TOP_OFFSET = 0.055F;
    // Moves the panel along the forearm axis.
    private static final float PANEL_FOREARM_OFFSET = -0.1375F;
    // Moves the panel laterally inside its own plane.
    private static final float PANEL_SIDE_OFFSET = 0.0F;
    // Hitbox depth around the panel plane; higher is easier to hover, lower is more precise.
    private static final float PANEL_NORMAL_TOLERANCE = 0.055F;
    private static final long WAKE_COOLDOWN_MS = 200L;

    private static InteractionHand hoveredHand;
    private static Object sleepIndicatorModuleProxy;
    private static long lastWakeRequestMillis;
    private static int lastHoverPlayerTick = Integer.MIN_VALUE;
    private static boolean loggedPoseFailure;
    private static boolean menuHandBridgeInitAttempted;
    private static boolean menuHandBridgeReady;
    private static boolean loggedMenuHandBridgeFailure;
    private static Method dataHolderGetInstanceMethod;
    private static Field menuHandMainField;
    private static Field menuHandOffField;
    private static final ClassValue<PanelPoseAccessor> PANEL_POSE_ACCESSORS = new ClassValue<>() {
        @Override
        protected PanelPoseAccessor computeValue(Class<?> type) {
            return new PanelPoseAccessor(findPublicMethod(type, "getHand", InteractionHand.class));
        }
    };
    private static final ClassValue<HandPoseAccessor> HAND_POSE_ACCESSORS = new ClassValue<>() {
        @Override
        protected HandPoseAccessor computeValue(Class<?> type) {
            return new HandPoseAccessor(
                    findPublicMethod(type, "getPos"),
                    findPublicMethod(type, "getRotation")
            );
        }
    };
    private static final ClassValue<ModuleIdAccessor> MODULE_ID_ACCESSORS = new ClassValue<>() {
        @Override
        protected ModuleIdAccessor computeValue(Class<?> type) {
            return new ModuleIdAccessor(findPublicMethod(type, "getId"));
        }
    };

    private VivecraftSleepWristPanel() {
    }

    public static Object createInteractModuleProxy(Class<?> interactModuleClass) {
        Object proxy = Proxy.newProxyInstance(
                interactModuleClass.getClassLoader(),
                new Class<?>[]{interactModuleClass},
                new SleepIndicatorInteractModuleHandler()
        );
        sleepIndicatorModuleProxy = proxy;
        return proxy;
    }

    public static boolean isSleepIndicatorModule(Object module) {
        return module != null && module == sleepIndicatorModuleProxy;
    }

    public static boolean shouldAllowManagedBedInteractModule(Object module) {
        return isSleepIndicatorModule(module) || hasModuleId(module, VIVECRAFT_HOTBAR_MODULE_ID);
    }

    public static boolean shouldShowMenuHandMain() {
        return shouldShowMenuHand(InteractionHand.MAIN_HAND);
    }

    public static boolean shouldShowMenuHandOff() {
        return shouldShowMenuHand(InteractionHand.OFF_HAND);
    }

    public static void applyHoveredMenuHandToVivecraft() {
        MenuHandVisibility visibility = resolveMenuHandVisibility();
        if (!visibility.showMain() && !visibility.showOff()) {
            return;
        }

        try {
            if (!ensureMenuHandBridgeReady()) {
                return;
            }

            Object dataHolder = dataHolderGetInstanceMethod.invoke(null);
            if (dataHolder == null) {
                return;
            }
            if (visibility.showMain()) {
                menuHandMainField.setBoolean(dataHolder, true);
            }
            if (visibility.showOff()) {
                menuHandOffField.setBoolean(dataHolder, true);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logMenuHandBridgeFailure(exception);
        }
    }

    public static void submitRender(PoseStack poseStack,
                                    CameraRenderState cameraRenderState,
                                    SubmitNodeCollector submitNodeCollector) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.options.hideGui) {
            clearHoverState();
            VivecraftSleepWristIndicatorRenderer.resetTransientState();
            return;
        }
        VivecraftClientCompat.WristPanelSnapshot snapshot =
                VivecraftClientCompat.captureWorldRenderWristPanelSnapshot(player);
        if (!isRenderEligible(player, snapshot)) {
            clearHoverState();
            VivecraftSleepWristIndicatorRenderer.resetTransientState();
            return;
        }
        InteractionHand wristHand = wristHand(snapshot);
        boolean targetWristHandModeled = !isMenuHandActive(wristHand);
        if (!targetWristHandModeled) {
            clearHoverState();
            VivecraftSleepWristIndicatorRenderer.resetTransientState();
            return;
        }
        if (!isInteractionEligible(player, snapshot, targetWristHandModeled)) {
            clearHoverState();
        }

        PanelPose panel = resolvePanelPose(snapshot.pose(), wristHand, snapshot.worldScale());
        if (panel == null) {
            return;
        }

        VivecraftSleepWristIndicatorRenderer.submitRender(
                poseStack,
                cameraRenderState,
                submitNodeCollector,
                panel,
                isHoveredRecently(player)
        );
    }

    private static boolean isActive(LocalPlayer player, InteractionHand hand, Vec3 handPosition) {
        VivecraftClientCompat.WristPanelSnapshot snapshot =
                VivecraftClientCompat.capturePreTickWristPanelSnapshot(player);
        if (!isRenderEligible(player, snapshot)) {
            clearHover(hand);
            return false;
        }
        InteractionHand wristHand = wristHand(snapshot);
        boolean targetWristHandModeled = !isMenuHandActive(wristHand);
        if (!isInteractionEligible(player, snapshot, targetWristHandModeled) || hand != interactorHand(wristHand)) {
            clearHover(hand);
            return false;
        }

        PanelPose panel = resolvePanelPose(snapshot.pose(), wristHand, snapshot.worldScale());
        if (panel == null || !panel.contains(handPosition)) {
            clearHover(hand);
            return false;
        }

        hoveredHand = hand;
        lastHoverPlayerTick = player.tickCount;
        return true;
    }

    private static boolean press(LocalPlayer player, InteractionHand hand) {
        VivecraftClientCompat.WristPanelSnapshot snapshot =
                VivecraftClientCompat.captureWristPanelStateSnapshot(player);
        if (!isRenderEligible(player, snapshot)) {
            return false;
        }
        InteractionHand wristHand = wristHand(snapshot);
        boolean targetWristHandModeled = !isMenuHandActive(wristHand);
        if (!isInteractionEligible(player, snapshot, targetWristHandModeled) || hand != interactorHand(wristHand)) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastWakeRequestMillis < WAKE_COOLDOWN_MS) {
            return false;
        }

        boolean sent = ClientBedWorkflow.tryWakeFromLeaveBedIntent(player);
        if (sent) {
            lastWakeRequestMillis = now;
        }
        return sent;
    }

    private static void reset(InteractionHand hand) {
        clearHover(hand);
    }

    private static boolean isRenderEligible(LocalPlayer player, VivecraftClientCompat.WristPanelSnapshot snapshot) {
        return player != null
                && Minecraft.getInstance().level != null
                && snapshot.vrBedPolicyActive();
    }

    private static boolean isInteractionEligible(LocalPlayer player,
                                                 VivecraftClientCompat.WristPanelSnapshot snapshot,
                                                 boolean targetWristHandModeled) {
        return player != null
                && Minecraft.getInstance().level != null
                && snapshot.vrBedPolicyActive()
                && ClientBedWorkflow.isManagedBedState(player)
                && targetWristHandModeled
                && VivecraftSleepWristIndicatorRenderer.shouldRenderForPlayer(player);
    }

    private static InteractionHand wristHand(VivecraftClientCompat.WristPanelSnapshot snapshot) {
        return snapshot.leftHanded() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    private static InteractionHand interactorHand(InteractionHand wristHand) {
        return wristHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private static boolean isMenuHandActive(InteractionHand hand) {
        try {
            if (!ensureMenuHandBridgeReady()) {
                return false;
            }

            Object dataHolder = dataHolderGetInstanceMethod.invoke(null);
            if (dataHolder == null) {
                return false;
            }

            Field field = hand == InteractionHand.MAIN_HAND ? menuHandMainField : menuHandOffField;
            return field.getBoolean(dataHolder);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logMenuHandBridgeFailure(exception);
            return false;
        }
    }

    private static void clearHover(InteractionHand hand) {
        if (hoveredHand == hand) {
            clearHoverState();
        }
    }

    private static void clearHoverState() {
        hoveredHand = null;
        lastHoverPlayerTick = Integer.MIN_VALUE;
    }

    public static boolean isHoveredRecently(LocalPlayer player) {
        return player != null && hoveredHand != null && player.tickCount - lastHoverPlayerTick <= 1;
    }

    private static boolean shouldShowMenuHand(InteractionHand hand) {
        MenuHandVisibility visibility = resolveMenuHandVisibility();
        return hand == InteractionHand.MAIN_HAND ? visibility.showMain() : visibility.showOff();
    }

    private static MenuHandVisibility resolveMenuHandVisibility() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.options.hideGui) {
            return MenuHandVisibility.NONE;
        }
        VivecraftClientCompat.WristPanelSnapshot snapshot =
                VivecraftClientCompat.captureWristPanelStateSnapshot(player);
        if (!isRenderEligible(player, snapshot)) {
            return MenuHandVisibility.NONE;
        }
        InteractionHand wristHand = wristHand(snapshot);
        boolean targetWristHandModeled = !isMenuHandActive(wristHand);
        boolean visible = isInteractionEligible(player, snapshot, targetWristHandModeled)
                && player.tickCount - lastHoverPlayerTick <= 1;
        if (!visible) {
            return MenuHandVisibility.NONE;
        }
        return new MenuHandVisibility(
                hoveredHand == InteractionHand.MAIN_HAND,
                hoveredHand == InteractionHand.OFF_HAND
        );
    }

    private static PanelPose resolvePanelPose(Object vrPose, InteractionHand wristHand, float worldScale) {
        if (vrPose == null) {
            return null;
        }

        try {
            Method getHandMethod = PANEL_POSE_ACCESSORS.get(vrPose.getClass()).getHand();
            if (getHandMethod == null) {
                logPoseFailure("NoSuchMethodException");
                return null;
            }
            Object handData = getHandMethod.invoke(vrPose, wristHand);
            if (handData == null) {
                return null;
            }

            HandPoseAccessor handAccessor = HAND_POSE_ACCESSORS.get(handData.getClass());
            if (!handAccessor.available()) {
                logPoseFailure("NoSuchMethodException");
                return null;
            }
            Vec3 handPos = (Vec3) handAccessor.getPos().invoke(handData);
            Quaternionfc rotation = (Quaternionfc) handAccessor.getRotation().invoke(handData);

            Vec3 sideAxis = axis(rotation, -1.0F, 0.0F, 0.0F);
            Vec3 forearmAxis = axis(rotation, 0.0F, 0.0F, -1.0F);
            Vec3 hudNormalAxis = axis(rotation, 0.0F, 1.0F, 0.0F);
            if (!isUsableAxis(sideAxis) || !isUsableAxis(forearmAxis) || !isUsableAxis(hudNormalAxis)) {
                return null;
            }

            Vec3 panelHorizontal = hudNormalAxis;
            Vec3 panelVertical = forearmAxis;
            Vec3 panelNormal = sideAxis.scale(PANEL_TOP_NORMAL_SIGN);
            if (!isUsableAxis(panelNormal)) {
                return null;
            }

            Vec3 center = handPos
                    .add(panelNormal.scale(PANEL_TOP_OFFSET * worldScale))
                    .add(forearmAxis.scale(PANEL_FOREARM_OFFSET * worldScale))
                    .add(panelHorizontal.scale(PANEL_SIDE_OFFSET * worldScale));
            float halfSize = PANEL_SIZE * worldScale * 0.5F;
            float normalTolerance = PANEL_NORMAL_TOLERANCE * worldScale;
            return new PanelPose(center, panelHorizontal, panelVertical, panelNormal, halfSize, normalTolerance);
        } catch (ReflectiveOperationException | ClassCastException | LinkageError exception) {
            logPoseFailure(exception.getClass().getSimpleName());
            return null;
        }
    }

    private static Vec3 axis(Quaternionfc rotation, float x, float y, float z) {
        org.joml.Vector3f axis = new org.joml.Vector3f(x, y, z);
        rotation.transform(axis);
        Vec3 vec = new Vec3(axis.x(), axis.y(), axis.z());
        return isUsableAxis(vec) ? vec.normalize() : Vec3.ZERO;
    }

    private static boolean isUsableAxis(Vec3 axis) {
        return axis.lengthSqr() > 1.0E-6D;
    }

    private static void logPoseFailure(String failureType) {
        if (loggedPoseFailure) {
            return;
        }

        loggedPoseFailure = true;
        Constants.warn("Vivecraft wrist panel pose could not be resolved: {}", failureType);
    }

    private static boolean ensureMenuHandBridgeReady() {
        if (menuHandBridgeInitAttempted) {
            return menuHandBridgeReady;
        }

        menuHandBridgeInitAttempted = true;
        try {
            Class<?> dataHolderClass = resolveClass("org.vivecraft.client_vr.ClientDataHolderVR");
            dataHolderGetInstanceMethod = dataHolderClass.getMethod("getInstance");
            menuHandMainField = dataHolderClass.getField("menuHandMain");
            menuHandOffField = dataHolderClass.getField("menuHandOff");
            menuHandBridgeReady = true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            menuHandBridgeReady = false;
            logMenuHandBridgeFailure(exception);
        }
        return menuHandBridgeReady;
    }

    private static void logMenuHandBridgeFailure(Throwable exception) {
        if (loggedMenuHandBridgeFailure) {
            return;
        }

        loggedMenuHandBridgeFailure = true;
        Constants.warn("Vivecraft menu hand bridge could not be resolved: {}", exception.getClass().getSimpleName());
    }

    private static Class<?> resolveClass(String className) throws ClassNotFoundException {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            try {
                return Class.forName(className, false, contextClassLoader);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return Class.forName(className, false, VivecraftSleepWristPanel.class.getClassLoader());
    }

    private static boolean hasModuleId(Object module, String expectedId) {
        if (module == null) {
            return false;
        }

        Method getIdMethod = MODULE_ID_ACCESSORS.get(module.getClass()).getId();
        if (getIdMethod == null) {
            return false;
        }
        try {
            Object id = getIdMethod.invoke(module);
            return id != null && expectedId.equals(id.toString());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static Method findPublicMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    public record PanelPose(
            Vec3 center,
            Vec3 horizontal,
            Vec3 vertical,
            Vec3 normal,
            float halfSize,
            float normalTolerance
    ) {
        private boolean contains(Vec3 worldPos) {
            Vec3 delta = worldPos.subtract(center);
            return Math.abs(delta.dot(horizontal)) <= halfSize
                    && Math.abs(delta.dot(vertical)) <= halfSize
                    && Math.abs(delta.dot(normal)) <= normalTolerance;
        }
    }

    private record PanelPoseAccessor(Method getHand) {
    }

    private record HandPoseAccessor(Method getPos, Method getRotation) {
        private boolean available() {
            return getPos != null && getRotation != null;
        }
    }

    private record ModuleIdAccessor(Method getId) {
    }

    private record MenuHandVisibility(boolean showMain, boolean showOff) {
        private static final MenuHandVisibility NONE = new MenuHandVisibility(false, false);
    }

    private static final class SleepIndicatorInteractModuleHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("getId".equals(name)) {
                return PANEL_ID;
            }
            if ("getPriority".equals(name)) {
                return INTERACT_PRIORITY_AFTER_HOTBAR;
            }
            if ("isActive".equals(name) && args != null && args.length == 3) {
                return isActive((LocalPlayer) args[0], (InteractionHand) args[1], (Vec3) args[2]);
            }
            if ("onPress".equals(name) && args != null && args.length == 2) {
                return press((LocalPlayer) args[0], (InteractionHand) args[1]);
            }
            if ("reset".equals(name) && args != null && args.length == 2) {
                reset((InteractionHand) args[1]);
                return null;
            }
            if ("swingsArm".equals(name)) {
                return false;
            }
            if ("equals".equals(name) && args != null && args.length == 1) {
                return proxy == args[0];
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("toString".equals(name)) {
                return "SeamlessSleepVivecraftSleepIndicatorInteractModule";
            }
            return defaultValue(method.getReturnType());
        }

        private Object defaultValue(Class<?> returnType) {
            if (returnType == Boolean.TYPE) {
                return false;
            }
            if (returnType == Integer.TYPE) {
                return 0;
            }
            if (returnType == Void.TYPE) {
                return null;
            }
            return null;
        }
    }
}
