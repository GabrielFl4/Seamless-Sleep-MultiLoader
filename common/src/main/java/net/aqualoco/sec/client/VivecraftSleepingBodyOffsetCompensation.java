package net.aqualoco.sec.client;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.Pose;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

// Applies the model-space inverse of Seamless' VR bed room Y offset while Vivecraft animates a sleeping VR body.
public final class VivecraftSleepingBodyOffsetCompensation {
    /*
     * Vivecraft's room-origin offset changes first-person bed height and also enters RotInfo used by
     * the body model. The body render receives the opposite local-position delta here, converted by
     * ModelUtils.worldToModel's scale/flip rules.
     */
    private static final double BODY_POSITION_COMPENSATION_SIGN = -1.0D;
    private static final float PLAYER_MODEL_BASE_SCALE = 0.9375F;

    private static final ThreadLocal<Boolean> SLEEPING_VR_BODY_SCOPE = new ThreadLocal<>();
    private static final ThreadLocal<Double> SLEEPING_VR_BODY_ROOM_Y_OFFSET = new ThreadLocal<>();
    private static final ClassValue<RotInfoAccessors> ROT_INFO_ACCESSORS = new ClassValue<>() {
        @Override
        protected RotInfoAccessors computeValue(Class<?> type) {
            return new RotInfoAccessors(findPublicField(type, "worldScale"), findPublicField(type, "heightScale"));
        }
    };
    private static final ClassValue<TotalScaleAccessor> TOTAL_SCALE_ACCESSORS = new ClassValue<>() {
        @Override
        protected TotalScaleAccessor computeValue(Class<?> type) {
            return new TotalScaleAccessor(findPublicMethod(type, "vivecraft$getTotalScale"));
        }
    };

    private VivecraftSleepingBodyOffsetCompensation() {
    }

    public static void begin(AvatarRenderState renderState) {
        boolean active = isSleepingWithBedOrientation(renderState);
        SLEEPING_VR_BODY_SCOPE.set(active);
        if (active) {
            SLEEPING_VR_BODY_ROOM_Y_OFFSET.set(VivecraftClientCompat.vrBedRoomYOffsetForRenderedEntity(renderState.id));
        } else {
            SLEEPING_VR_BODY_ROOM_Y_OFFSET.remove();
        }
    }

    public static void end() {
        SLEEPING_VR_BODY_SCOPE.remove();
        SLEEPING_VR_BODY_ROOM_Y_OFFSET.remove();
    }

    public static void compensateWorldToModel(HumanoidRenderState renderState,
                                              Object rotInfo,
                                              boolean useWorldScale,
                                              Vector3f out) {
        if (!Boolean.TRUE.equals(SLEEPING_VR_BODY_SCOPE.get())
                || renderState == null
                || rotInfo == null
                || out == null) {
            return;
        }

        Double scopedRoomYOffset = SLEEPING_VR_BODY_ROOM_Y_OFFSET.get();
        double roomYOffset = scopedRoomYOffset == null ? 0.0D : scopedRoomYOffset;
        if (roomYOffset == 0.0D) {
            return;
        }

        RotInfoAccessors rotInfoAccessors = ROT_INFO_ACCESSORS.get(rotInfo.getClass());
        float coordinateScale = useWorldScale
                ? readFloatField(rotInfo, rotInfoAccessors.worldScale(), 1.0F)
                : readTotalScale(renderState);
        float modelScale = PLAYER_MODEL_BASE_SCALE
                * readFloatField(rotInfo, rotInfoAccessors.heightScale(), 1.0F);
        if (!isUsableScale(coordinateScale) || !isUsableScale(modelScale)) {
            return;
        }

        /*
         * Delta-only worldToModel for (0, compensationY, 0):
         * no center subtraction, no yaw effect on pure Y, then model-unit scale and final Y flip.
         */
        double compensationY = roomYOffset * BODY_POSITION_COMPENSATION_SIGN;
        float modelYOffset = (float) (compensationY / coordinateScale) * (16.0F / modelScale) * -1.0F;
        out.add(0.0F, modelYOffset, 0.0F);
    }

    private static boolean isSleepingWithBedOrientation(AvatarRenderState renderState) {
        return renderState != null
                && renderState.hasPose(Pose.SLEEPING)
                && renderState.bedOrientation != null;
    }

    private static float readFloatField(Object instance, Field field, float fallback) {
        if (field == null) {
            return fallback;
        }
        try {
            Object value = field.get(instance);
            return value instanceof Number number ? number.floatValue() : fallback;
        } catch (IllegalAccessException | LinkageError | RuntimeException ignored) {
            return fallback;
        }
    }

    private static float readTotalScale(HumanoidRenderState renderState) {
        Method method = TOTAL_SCALE_ACCESSORS.get(renderState.getClass()).method();
        if (method == null) {
            return 1.0F;
        }
        try {
            Object value = method.invoke(renderState);
            return value instanceof Number number ? number.floatValue() : 1.0F;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return 1.0F;
        }
    }

    private static Field findPublicField(Class<?> owner, String name) {
        try {
            return owner.getField(name);
        } catch (NoSuchFieldException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static Method findPublicMethod(Class<?> owner, String name) {
        try {
            return owner.getMethod(name);
        } catch (NoSuchMethodException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isUsableScale(float value) {
        return Float.isFinite(value) && Math.abs(value) > 1.0E-6F;
    }

    private record RotInfoAccessors(Field worldScale, Field heightScale) {
    }

    private record TotalScaleAccessor(Method method) {
    }
}
