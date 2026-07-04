package net.aqualoco.sec.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

// Registers a simple Forge config screen that points users to file-based configuration.
public final class ForgeConfigScreens {

    private ForgeConfigScreens() {
    }

    public static void register(ModLoadingContext context) {
        context.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (client, parent) -> ConfigFallbackScreen.forgeFileOnly(parent)
                )
        );
    }
}
