package net.aqualoco.sec.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.aqualoco.sec.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

// Shared Vivecraft wrist-panel geometry used by both the InteractModule hitbox and the line render.
public final class VivecraftSleepWristPanel {
    private static final Identifier PANEL_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sleep_indicator");
    private static final String VIVECRAFT_HOTBAR_MODULE_ID = "vivecraft:interactive_hotbar";
    private static final int INTERACT_PRIORITY_AFTER_HOTBAR = 100;

    // Physical square size in meters/blocks before Vivecraft world scale.
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
    private static final float PANEL_LINE_WIDTH = 2.0F;
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
        boolean showMain = shouldShowMenuHandMain();
        boolean showOff = shouldShowMenuHandOff();
        if (!showMain && !showOff) {
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
            if (showMain) {
                menuHandMainField.setBoolean(dataHolder, true);
            }
            if (showOff) {
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
        if (!isEligible(player) || client.options.hideGui) {
            hoveredHand = null;
            lastHoverPlayerTick = Integer.MIN_VALUE;
            return;
        }

        PanelPose panel = resolvePanelPose(VivecraftClientCompat.getWorldRenderPose());
        if (panel == null) {
            return;
        }

        boolean active = hoveredHand != null;
        Vec3 cameraPos = cameraRenderState.pos;

        poseStack.pushPose();
        poseStack.translate(
                panel.center.x() - cameraPos.x(),
                panel.center.y() - cameraPos.y(),
                panel.center.z() - cameraPos.z()
        );
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.lines(),
                (pose, vertexConsumer) -> drawPanel(pose, vertexConsumer, panel, active)
        );
        poseStack.popPose();
    }

    private static boolean isActive(LocalPlayer player, InteractionHand hand, Vec3 handPosition) {
        if (!isEligible(player) || hand != interactorHand()) {
            clearHover(hand);
            return false;
        }

        PanelPose panel = resolvePanelPose(VivecraftClientCompat.getPreTickWorldPose());
        if (panel == null || !panel.contains(handPosition)) {
            clearHover(hand);
            return false;
        }

        hoveredHand = hand;
        lastHoverPlayerTick = player.tickCount;
        return true;
    }

    private static boolean press(LocalPlayer player, InteractionHand hand) {
        if (!isEligible(player) || hand != interactorHand()) {
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

    private static boolean isEligible(LocalPlayer player) {
        return player != null
                && Minecraft.getInstance().level != null
                && VivecraftClientCompat.shouldUseVrBedPolicy(player)
                && ClientBedWorkflow.isManagedBedState(player);
    }

    private static InteractionHand wristHand() {
        return VivecraftClientCompat.isLeftHandedLocal() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    private static InteractionHand interactorHand() {
        return wristHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private static void clearHover(InteractionHand hand) {
        if (hoveredHand == hand) {
            hoveredHand = null;
            lastHoverPlayerTick = Integer.MIN_VALUE;
        }
    }

    private static boolean shouldShowMenuHand(InteractionHand hand) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        return !client.options.hideGui
                && isEligible(player)
                && hoveredHand == hand
                && player.tickCount - lastHoverPlayerTick <= 1;
    }

    private static PanelPose resolvePanelPose(Object vrPose) {
        if (vrPose == null) {
            return null;
        }

        try {
            Object handData = vrPose.getClass().getMethod("getHand", InteractionHand.class).invoke(vrPose, wristHand());
            if (handData == null) {
                return null;
            }

            Vec3 handPos = (Vec3) handData.getClass().getMethod("getPos").invoke(handData);
            Quaternionfc rotation = (Quaternionfc) handData.getClass().getMethod("getRotation").invoke(handData);
            float worldScale = VivecraftClientCompat.getWorldScale();

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
            logPoseFailure(exception);
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

    private static void drawPanel(PoseStack.Pose pose, VertexConsumer vertexConsumer, PanelPose panel, boolean active) {
        Vec3 x = panel.horizontal.scale(panel.halfSize);
        Vec3 y = panel.vertical.scale(panel.halfSize);
        Vec3 bottomLeft = x.scale(-1.0D).add(y.scale(-1.0D));
        Vec3 bottomRight = x.add(y.scale(-1.0D));
        Vec3 topRight = x.add(y);
        Vec3 topLeft = x.scale(-1.0D).add(y);

        int red = active ? 80 : 255;
        int green = active ? 255 : 210;
        int blue = active ? 120 : 40;
        int alpha = 255;

        line(vertexConsumer, pose, bottomLeft, bottomRight, panel.normal, red, green, blue, alpha);
        line(vertexConsumer, pose, bottomRight, topRight, panel.normal, red, green, blue, alpha);
        line(vertexConsumer, pose, topRight, topLeft, panel.normal, red, green, blue, alpha);
        line(vertexConsumer, pose, topLeft, bottomLeft, panel.normal, red, green, blue, alpha);
        line(vertexConsumer, pose, bottomLeft, topRight, panel.normal, 60, 180, 255, alpha);
        line(vertexConsumer, pose, bottomRight, topLeft, panel.normal, 60, 180, 255, alpha);
        line(vertexConsumer, pose, Vec3.ZERO, panel.normal.scale(0.045D), panel.normal, 255, 80, 180, alpha);
    }

    private static void line(VertexConsumer vertexConsumer,
                             PoseStack.Pose pose,
                             Vec3 start,
                             Vec3 end,
                             Vec3 normal,
                             int red,
                             int green,
                             int blue,
                             int alpha) {
        vertexConsumer.addVertex(pose, (float) start.x(), (float) start.y(), (float) start.z())
                .setColor(red, green, blue, alpha)
                .setNormal(pose, (float) normal.x(), (float) normal.y(), (float) normal.z())
                .setLineWidth(PANEL_LINE_WIDTH);
        vertexConsumer.addVertex(pose, (float) end.x(), (float) end.y(), (float) end.z())
                .setColor(red, green, blue, alpha)
                .setNormal(pose, (float) normal.x(), (float) normal.y(), (float) normal.z())
                .setLineWidth(PANEL_LINE_WIDTH);
    }

    private static void logPoseFailure(Throwable exception) {
        if (loggedPoseFailure) {
            return;
        }

        loggedPoseFailure = true;
        Constants.warn("Vivecraft wrist panel pose could not be resolved: {}", exception.getClass().getSimpleName());
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

        try {
            Object id = module.getClass().getMethod("getId").invoke(module);
            return id != null && expectedId.equals(id.toString());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private record PanelPose(
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
