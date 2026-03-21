package net.aqualoco.sec.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
                        (client, parent) -> new FileOnlyConfigScreen(parent)
                )
        );
    }

    // Lightweight info screen shown because Forge uses file-only configuration.
    private static final class FileOnlyConfigScreen extends Screen {
        private final Screen parent;

        private FileOnlyConfigScreen(Screen parent) {
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
            super.render(graphics, mouseX, mouseY, delta);
            int x = this.width / 2;
            int y = this.height / 2 - 20;
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.seamlesssleep.forge_config.notice").withStyle(ChatFormatting.YELLOW),
                    x,
                    y,
                    0xFFFFFF);
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.seamlesssleep.forge_config.hint"),
                    x,
                    y + 12,
                    0xFFFFFF);
        }
    }
}
