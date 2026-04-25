package net.aqualoco.sec.acceleration;

public enum WorldSleepAccelerationGovernorAction {
    NONE,
    AREA,
    SPEED,
    AREA_AND_SPEED;

    public static WorldSleepAccelerationGovernorAction combine(WorldSleepAccelerationGovernorAction left,
                                                               WorldSleepAccelerationGovernorAction right) {
        if (left == AREA_AND_SPEED || right == AREA_AND_SPEED) {
            return AREA_AND_SPEED;
        }
        if ((left == AREA && right == SPEED) || (left == SPEED && right == AREA)) {
            return AREA_AND_SPEED;
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
