package net.aqualoco.sec.client;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

// Reflection bridge for FirstPersonModel so Seamless Sleep can disable its camera-body render only during bed flow.
public final class FirstPersonModelCompat {

    private static final String FIRSTPERSON_MOD_ID = "firstperson";
    private static final String FIRSTPERSON_API_CLASS = "dev.tr7zw.firstperson.api.FirstPersonAPI";
    private static final String ACTIVATION_HANDLER_CLASS = "dev.tr7zw.firstperson.api.ActivationHandler";

    private static boolean reflectionInitAttempted;
    private static boolean reflectionReady;
    private static boolean activationHandlerRegistered;
    private static boolean loggedReflectionFailure;
    private static boolean loggedInvocationFailure;

    private static Method registerPlayerHandlerMethod;
    private static Method isEnabledMethod;
    private static Method isRenderingPlayerMethod;
    private static Class<?> activationHandlerType;

    private FirstPersonModelCompat() {
    }

    public static void ensureBedCompatibilityInstalled() {
        if (!Services.PLATFORM.isModLoaded(FIRSTPERSON_MOD_ID)) {
            return;
        }

        if (!ensureReflectionReady() || activationHandlerRegistered || registerPlayerHandlerMethod == null || activationHandlerType == null) {
            return;
        }

        try {
            InvocationHandler handler = (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> "SeamlessSleepFirstPersonActivationHandler";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    };
                }

                if ("preventFirstperson".equals(method.getName())) {
                    LocalPlayer player = Minecraft.getInstance().player;
                    return player != null && ClientBedWorkflow.isManagedBedState(player);
                }

                return false;
            };

            Object activationHandler = Proxy.newProxyInstance(
                    activationHandlerType.getClassLoader(),
                    new Class<?>[]{activationHandlerType},
                    handler
            );
            registerPlayerHandlerMethod.invoke(null, activationHandler);
            activationHandlerRegistered = true;
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            logInvocationFailure(e);
        }
    }

    public static boolean shouldDeferCameraBodyToFirstPersonModel() {
        if (!Services.PLATFORM.isModLoaded(FIRSTPERSON_MOD_ID)) {
            return false;
        }

        ensureBedCompatibilityInstalled();

        if (activationHandlerRegistered) {
            return false;
        }

        try {
            if (!ensureReflectionReady()) {
                return false;
            }

            boolean enabled = invokeBoolean(isEnabledMethod);
            boolean renderingPlayer = invokeBoolean(isRenderingPlayerMethod);
            return enabled && renderingPlayer;
        } catch (ReflectiveOperationException e) {
            logInvocationFailure(e);
            return false;
        }
    }

    private static boolean ensureReflectionReady() {
        if (reflectionInitAttempted) {
            return reflectionReady;
        }

        reflectionInitAttempted = true;
        try {
            Class<?> apiClass = Class.forName(FIRSTPERSON_API_CLASS);
            activationHandlerType = Class.forName(ACTIVATION_HANDLER_CLASS);

            registerPlayerHandlerMethod = apiClass.getMethod("registerPlayerHandler", Object.class);
            isEnabledMethod = apiClass.getMethod("isEnabled");
            isRenderingPlayerMethod = apiClass.getMethod("isRenderingPlayer");

            reflectionReady = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            reflectionReady = false;
            if (!loggedReflectionFailure) {
                loggedReflectionFailure = true;
                Constants.debug("FirstPersonModel reflection bridge initialization failed.", e);
            }
        }

        return reflectionReady;
    }

    private static boolean invokeBoolean(Method method) throws ReflectiveOperationException {
        if (method == null) {
            return false;
        }

        Object value = method.invoke(null);
        return value instanceof Boolean && (Boolean) value;
    }

    private static void logInvocationFailure(Exception e) {
        if (!loggedInvocationFailure) {
            loggedInvocationFailure = true;
            Constants.debug("FirstPersonModel reflection bridge invocation failed.", e);
        }
    }
}
