package net.aqualoco.sec.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;

// NeoForge 1.20.1 still exposes the Forge config screen extension point.
public final class NeoForgeConfigScreens {

    private NeoForgeConfigScreens() {
    }

    public static void register(ModLoadingContext context) {
        context.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (client, parent) -> createScreen(parent)
                )
        );
    }

    private static Screen createScreen(Screen parent) {
        if (!ModList.get().isLoaded("yet_another_config_lib_v3")) {
            return new UnavailableConfigScreen(parent);
        }
        return NeoForgeYaclConfigScreen.create(parent);
    }

    // Lightweight fallback screen shown when YACL is unavailable.
    private static final class UnavailableConfigScreen extends Screen {
        private final Screen parent;

        private UnavailableConfigScreen(Screen parent) {
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
            this.renderBackground(graphics);
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
