package net.aqualoco.sec.client;

import net.aqualoco.sec.sleep.ClientSleepAnimationState;

// Global client state holder shared by rendering and packet handlers.
public final class SeamlessSleepClientState {

    public static final ClientSleepAnimationState SLEEP_ANIMATION = new ClientSleepAnimationState();

    private SeamlessSleepClientState() {
    }
}
