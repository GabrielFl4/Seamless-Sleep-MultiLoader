package net.aqualoco.sec;

import net.aqualoco.sec.command.SeamlessSleepCommands;
import net.minecraftforge.event.RegisterCommandsEvent;

// Bridges Forge's command event to the shared command registration code.
final class SeamlessSleepCommandRegistration {

    private SeamlessSleepCommandRegistration() {
    }

    static void register(RegisterCommandsEvent event) {
        SeamlessSleepCommands.register(event.getDispatcher());
    }
}
