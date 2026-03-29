package net.aqualoco.sec.client;

import net.aqualoco.sec.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModContainer;

import java.util.function.Supplier;

public final class ForgeConfigScreens {

    private ForgeConfigScreens() {
    }

    public static void register(ModContainer container) {
        container.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                (Supplier<ConfigScreenHandler.ConfigScreenFactory>) () -> new ConfigScreenHandler.ConfigScreenFactory(ForgeConfigScreens::createScreen)
        );
    }

    private static Screen createScreen(Screen parent) {
        return new FileOnlyConfigScreen(parent);
    }

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
                            Component.translatable("screen.seamlesssleep.forge_config.open_folder"),
                            button -> Util.getPlatform().openFile(Services.PLATFORM.getConfigDir().toFile()))
                    .bounds(this.width / 2 - 102, this.height / 2 + 10, 100, 20)
                    .build());

            this.addRenderableWidget(Button.builder(
                            Component.translatable("gui.back"),
                            button -> this.onClose())
                    .bounds(this.width / 2 + 2, this.height / 2 + 10, 100, 20)
                    .build());
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            super.extractRenderState(context, mouseX, mouseY, delta);
            int x = this.width / 2;
            int y = this.height / 2 - 20;
            Component notice = Component.translatable("screen.seamlesssleep.forge_config.notice").withStyle(ChatFormatting.YELLOW);
            Component hint = Component.translatable("screen.seamlesssleep.forge_config.hint");
            context.centeredText(this.font, notice, x, y, 0xFFFFFFFF);
            context.centeredText(this.font, hint, x, y + 12, 0xFFFFFFFF);
        }
    }
}
