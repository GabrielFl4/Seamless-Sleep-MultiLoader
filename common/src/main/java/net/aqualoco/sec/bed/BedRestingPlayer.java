package net.aqualoco.sec.bed;

public interface BedRestingPlayer {

    boolean seamlesssleep$isCountedForSleep();

    void seamlesssleep$setCountedForSleep(boolean countedForSleep);

    int seamlesssleep$getFallAsleepDelayCounter();

    void seamlesssleep$resetFallAsleepDelayCounter();

    void seamlesssleep$incrementFallAsleepDelayCounter();

    float seamlesssleep$getBedLookYaw();

    float seamlesssleep$getBedLookPitch();

    void seamlesssleep$setBedLookYaw(float yaw);

    void seamlesssleep$setBedLookPitch(float pitch);

    default void seamlesssleep$setBedLook(float yaw, float pitch) {
        this.seamlesssleep$setBedLookYaw(yaw);
        this.seamlesssleep$setBedLookPitch(pitch);
    }

    default float seamlesssleep$getVisualBedLookYaw(float partialTick) {
        return this.seamlesssleep$getBedLookYaw();
    }

    default float seamlesssleep$getVisualBedLookPitch(float partialTick) {
        return this.seamlesssleep$getBedLookPitch();
    }
}
