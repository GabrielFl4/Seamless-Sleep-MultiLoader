package net.aqualoco.sec.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            if (!FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
                return new MissingYaclScreen(parent);
            }
            return FabricYaclConfigScreen.create(parent);
        };
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
                            b -> this.onClose())
                    .bounds(this.width / 2 - 50, this.height / 2 + 10, 100, 20)
                    .build());
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
            int x = this.width / 2;
            int y = this.height / 2 - 20;
            context.drawCenteredString(this.font,
                    Component.translatable("screen.seamlesssleep.missing_yacl.notice").withStyle(ChatFormatting.RED),
                    x,
                    y,
                    0xFFFFFFFF);
            context.drawCenteredString(this.font,
                    Component.translatable("screen.seamlesssleep.missing_yacl.hint"),
                    x,
                    y + 12,
                    0xFFFFFFFF);
        }
    }
}
