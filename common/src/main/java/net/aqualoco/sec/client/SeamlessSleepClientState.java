package net.aqualoco.sec.client;

import net.aqualoco.sec.sleep.ClientSleepAnimationState;

// Shared client sleep state used by mixins and HUD.
public final class SeamlessSleepClientState {

    public static final ClientSleepAnimationState SLEEP_ANIMATION = new ClientSleepAnimationState();

    private SeamlessSleepClientState() {
    }
}
