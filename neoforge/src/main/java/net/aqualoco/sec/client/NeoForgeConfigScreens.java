package net.aqualoco.sec.client;

import net.aqualoco.sec.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
            return new MissingYaclScreen(parent);
        }
        return NeoForgeYaclConfigScreen.create(parent);
    }

    // Lightweight fallback screen shown when YACL is unavailable.
    private static final class MissingYaclScreen extends Screen {
        private final Screen parent;

        private MissingYaclScreen(Screen parent) {
            super(Component.literal("Seamless Sleep"));
            this.parent = parent;
        }

        @Override
        public void onClose() {
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
        }

        @Override
        protected void init() {
            if (minecraft == null) {
                return;
            }
            this.addRenderableWidget(Button.builder(
                            Component.translatable("gui.back"),
                            b -> this.onClose())
                    .bounds(this.width / 2 - 50, this.height / 2 + 10, 100, 20)
                    .build());
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            this.renderBackground(graphics, mouseX, mouseY, delta);
            int x = this.width / 2;
            int y = this.height / 2 - 20;
            graphics.drawCenteredString(this.font,
                    Component.literal("YACL not found").withStyle(ChatFormatting.RED),
                    x,
                    y,
                    0xFFFFFF);
            graphics.drawCenteredString(this.font,
                    Component.literal("Install YetAnotherConfigLib v3 to edit configs."),
                    x,
                    y + 12,
                    0xFFFFFF);
            super.render(graphics, mouseX, mouseY, delta);
        }
    }
}
