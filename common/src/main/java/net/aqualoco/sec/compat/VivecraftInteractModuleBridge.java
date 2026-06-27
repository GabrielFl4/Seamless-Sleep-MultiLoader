package net.aqualoco.sec.compat;

import net.aqualoco.sec.Constants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;

// Reflection bridge for Vivecraft InteractModule; kept outside mixin-owned packages for runtime safety.
public final class VivecraftInteractModuleBridge {
    private static final ClassValue<InteractModuleAccess> INTERACT_MODULE_ACCESS = new ClassValue<>() {
        @Override
        protected InteractModuleAccess computeValue(Class<?> type) {
            return new InteractModuleAccess(findIsActiveMethod(type));
        }
    };
    private static boolean loggedInteractModuleFailure;

    private VivecraftInteractModuleBridge() {
    }

    public static boolean isActive(Object module,
                                   LocalPlayer player,
                                   InteractionHand hand,
                                   Vec3 handPosition) {
        if (module == null) {
            return false;
        }

        Method isActiveMethod = INTERACT_MODULE_ACCESS.get(module.getClass()).isActive();
        if (isActiveMethod == null) {
            return false;
        }

        try {
            Object result = isActiveMethod.invoke(module, player, hand, handPosition);
            return result instanceof Boolean active && active;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            logInteractModuleFailure(exception);
            return false;
        }
    }

    private static Method findIsActiveMethod(Class<?> type) {
        try {
            return type.getMethod("isActive", LocalPlayer.class, InteractionHand.class, Vec3.class);
        } catch (NoSuchMethodException | LinkageError | RuntimeException exception) {
            logInteractModuleFailure(exception);
            return null;
        }
    }

    private static void logInteractModuleFailure(Throwable exception) {
        if (loggedInteractModuleFailure) {
            return;
        }
        loggedInteractModuleFailure = true;
        Constants.warn("Vivecraft InteractModule#isActive bridge failed: {}", exception.getClass().getSimpleName());
    }

    private record InteractModuleAccess(Method isActive) {
    }
}
