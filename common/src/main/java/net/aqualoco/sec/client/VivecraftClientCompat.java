package net.aqualoco.sec.client;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.compat.VivecraftCompat;
import net.aqualoco.sec.network.VivecraftVrStatePayload;
import net.aqualoco.sec.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

// Client-only reflection bridge for Vivecraft public APIs. This keeps Seamless loadable without Vivecraft.
public final class VivecraftClientCompat {
    private static final int VR_STATE_HEARTBEAT_TICKS = 100;
    private static final double VR_BED_ROOM_Y_OFFSET = -1.35D;

    private static boolean clientApiInitAttempted;
    private static boolean clientApiReady;
    private static boolean playerApiInitAttempted;
    private static boolean playerApiReady;
    private static boolean clientIntegrationsRegistrationAttempted;
    private static boolean sleepIndicatorInteractModuleRegistered;
    private static boolean loggedClientApiFailure;
    private static boolean loggedPlayerApiFailure;
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

    private static boolean hasSentVrState;
    private static boolean lastSentVrState;
    private static int ticksSinceVrStateSync = VR_STATE_HEARTBEAT_TICKS;

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
        syncVrStateToServer(isVrActiveLocal(), forceVrStateSync);
    }

    public static void resetClientSync() {
        hasSentVrState = false;
        lastSentVrState = false;
        ticksSinceVrStateSync = VR_STATE_HEARTBEAT_TICKS;
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
        return player != null && isVrActiveLocal();
    }

    public static boolean shouldApplyVrBedRoomYOffset() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        return player != null
                && client.level != null
                && shouldUseVrBedPolicy(player)
                && ClientBedWorkflow.isManagedBedState(player);
    }

    public static double vrBedRoomYOffset() {
        return VR_BED_ROOM_Y_OFFSET;
    }

    public static boolean shouldNeutralizeSleepBlackAlpha(float blackAlpha) {
        if (blackAlpha <= 0.0F || !isVrActiveLocal()) {
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
}
