package net.aqualoco.sec;

import net.aqualoco.sec.command.SeamlessSleepCommands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

final class SeamlessSleepCommandRegistration {

    private SeamlessSleepCommandRegistration() {
    }

    static void register(RegisterCommandsEvent event) {
        SeamlessSleepCommands.register(event.getDispatcher());
    }
}
