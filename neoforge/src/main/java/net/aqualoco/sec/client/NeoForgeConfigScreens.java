package net.aqualoco.sec.client;

import net.aqualoco.sec.platform.Services;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.util.function.Supplier;

// Registers the NeoForge config screen entry point and fallback when YACL is missing.
public final class NeoForgeConfigScreens {

    private NeoForgeConfigScreens() {
    }

    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (Supplier<IConfigScreenFactory>) () -> (modContainer, parent) -> createScreen(parent));
    }

    private static Screen createScreen(Screen parent) {
        if (!Services.PLATFORM.isModLoaded("yet_another_config_lib_v3")) {
            return ConfigFallbackScreen.missingYacl(parent);
        }
        return NeoForgeYaclConfigScreen.create(parent);
    }
}
