package net.aqualoco.sec.acceleration;

public enum WorldSleepAccelerationGovernorAction {
    NONE,
    AREA,
    INTENSITY,
    AREA_AND_INTENSITY;

    public static WorldSleepAccelerationGovernorAction combine(WorldSleepAccelerationGovernorAction left,
                                                               WorldSleepAccelerationGovernorAction right) {
        if (left == AREA_AND_INTENSITY || right == AREA_AND_INTENSITY) {
            return AREA_AND_INTENSITY;
        }
        if ((left == AREA && right == INTENSITY) || (left == INTENSITY && right == AREA)) {
            return AREA_AND_INTENSITY;
        }
        if (left == NONE) {
            return right;
        }
        if (right == NONE) {
            return left;
        }
        return left;
    }
}
