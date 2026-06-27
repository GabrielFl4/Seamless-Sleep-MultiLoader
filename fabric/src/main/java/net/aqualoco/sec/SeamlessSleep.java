package net.aqualoco.sec;

import net.fabricmc.api.ModInitializer;

// Fabric bootstrap that initializes common code and server hooks.
public class SeamlessSleep implements ModInitializer {
    
    @Override
    public void onInitialize() {
        SeamlessSleepCommon.init();
        SeamlessSleepServerEvents.register();
        SeamlessSleepCommandRegistration.register();
    }
}
