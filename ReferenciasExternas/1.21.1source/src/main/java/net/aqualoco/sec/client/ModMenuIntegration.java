package net.aqualoco.sec.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import net.aqualoco.sec.config.AquaSecClientConfig;
import net.aqualoco.sec.config.AquaSecClientConfigManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            if (!FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
                return new MissingYaclScreen(parent);
            }

            AquaSecClientConfig cfg = AquaSecClientConfigManager.get();
            cfg.clamp();

            return YetAnotherConfigLib.createBuilder()
                    .title(Text.translatable("config.seamlesssleep.title"))
                    .category(
                            ConfigCategory.createBuilder()
                                    .name(Text.translatable("config.seamlesssleep.category.chat"))
                                    .option(buildSlider(
                                            Text.translatable("config.seamlesssleep.sleep.chat_text_opacity"),
                                            Text.translatable("config.seamlesssleep.sleep.chat_text_opacity.desc"),
                                            0.5D,
                                            () -> cfg.sleepChatTextOpacityMultiplier,
                                            val -> cfg.sleepChatTextOpacityMultiplier = val
                                    ))
                                    .option(buildSlider(
                                            Text.translatable("config.seamlesssleep.sleep.chat_background_opacity"),
                                            Text.translatable("config.seamlesssleep.sleep.chat_background_opacity.desc"),
                                            0.4D,
                                            () -> cfg.sleepChatBackgroundOpacityMultiplier,
                                            val -> cfg.sleepChatBackgroundOpacityMultiplier = val
                                    ))
                                    .build()
                    )
                    .save(() -> {
                        cfg.clamp();
                        AquaSecClientConfigManager.save();
                    })
                    .build()
                    .generateScreen(parent);
        };
    }

    private static Option<Double> buildSlider(Text name,
                                              Text description,
                                              double def,
                                              java.util.function.Supplier<Double> getter,
                                              java.util.function.Consumer<Double> setter) {
        return Option.<Double>createBuilder()
                .name(name)
                .description(OptionDescription.of(description))
                .binding(def, getter::get, setter::accept)
                .controller(opt -> DoubleSliderControllerBuilder.create(opt)
                        .range(0.0D, 1.0D)
                        .step(0.05D))
                .build();
    }

    private static class MissingYaclScreen extends Screen {
        private final Screen parent;

        protected MissingYaclScreen(Screen parent) {
            super(Text.literal("Seamless Sleep"));
            this.parent = parent;
        }

        @Override
        public void close() {
            this.client.setScreen(parent);
        }

        @Override
        protected void init() {
            if (client == null) return;
            this.addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(
                            Text.translatable("gui.back"),
                            b -> this.close())
                    .dimensions(this.width / 2 - 50, this.height / 2 + 10, 100, 20)
                    .build());
        }

        @Override
        public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);
            int x = this.width / 2;
            int y = this.height / 2 - 20;
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("YACL nao encontrado").formatted(Formatting.RED), x, y, 0xFFFFFF);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Instale YetAnotherConfigLib v3 para editar a config."), x, y + 12, 0xFFFFFF);
            super.render(context, mouseX, mouseY, delta);
        }
    }
}
