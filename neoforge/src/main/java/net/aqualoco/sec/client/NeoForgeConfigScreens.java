package net.aqualoco.sec.client;

import net.aqualoco.sec.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.util.function.Supplier;

public final class NeoForgeConfigScreens {

    private NeoForgeConfigScreens() {
    }

    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (Supplier<IConfigScreenFactory>) () -> NeoForgeConfigScreens::createScreen);
    }

    private static Screen createScreen(ModContainer modContainer, Screen parent) {
        if (!Services.PLATFORM.isModLoaded("yet_another_config_lib_v3")) {
            return new MissingYaclScreen(parent);
        }
        return NeoForgeYaclConfigScreen.create(parent);
    }

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
                            button -> this.onClose())
                    .bounds(this.width / 2 - 50, this.height / 2 + 10, 100, 20)
                    .build());
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            super.extractRenderState(context, mouseX, mouseY, delta);
            int x = this.width / 2;
            int y = this.height / 2 - 20;
            Component notice = Component.translatable("screen.seamlesssleep.missing_yacl.notice").withStyle(ChatFormatting.RED);
            Component hint = Component.translatable("screen.seamlesssleep.missing_yacl.hint");
            context.centeredText(this.font, notice, x, y, 0xFFFFFFFF);
            context.centeredText(this.font, hint, x, y + 12, 0xFFFFFFFF);
        }
    }
}
