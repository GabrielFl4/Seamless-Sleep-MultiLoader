package net.aqualoco.sec.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;

// Registers the Forge config screen entry point and fallback when Cloth is missing.
public final class ForgeConfigScreens {

    private ForgeConfigScreens() {
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
        if (!ModList.get().isLoaded("cloth_config")) {
            return new MissingClothScreen(parent);
        }
        return ForgeClothConfigScreen.create(parent);
    }

    // Lightweight fallback screen shown when Cloth Config is unavailable.
    private static final class MissingClothScreen extends Screen {
        private final Screen parent;

        private MissingClothScreen(Screen parent) {
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
                    Component.literal("Cloth Config not found").withStyle(ChatFormatting.RED),
                    x,
                    y,
                    0xFFFFFF);
            graphics.drawCenteredString(this.font,
                    Component.literal("Install Cloth Config to edit configs."),
                    x,
                    y + 12,
                    0xFFFFFF);
            super.render(graphics, mouseX, mouseY, delta);
        }
    }
}
