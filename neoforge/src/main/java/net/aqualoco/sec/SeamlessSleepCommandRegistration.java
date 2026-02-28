package net.aqualoco.sec;

import net.aqualoco.sec.command.SeamlessSleepCommands;
import net.minecraftforge.event.RegisterCommandsEvent;

// Bridges NeoForge 1.20.1's Forge-packaged command event to shared registration.
final class SeamlessSleepCommandRegistration {

    private SeamlessSleepCommandRegistration() {
    }

    static void register(RegisterCommandsEvent event) {
        SeamlessSleepCommands.register(event.getDispatcher());
    }
}
