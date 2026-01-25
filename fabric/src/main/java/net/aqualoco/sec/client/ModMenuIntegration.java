package net.aqualoco.sec.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import net.aqualoco.sec.config.SeamlessSleepClientConfig;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            if (!FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
                return new MissingYaclScreen(parent);
            }

            SeamlessSleepClientConfig cfg = SeamlessSleepClientConfigManager.get();
            cfg.clamp();

            return YetAnotherConfigLib.createBuilder()
                    .title(Component.translatable("config.seamlesssleep.title"))
                    .category(
                            ConfigCategory.createBuilder()
                                    .name(Component.translatable("config.seamlesssleep.category.chat"))
                                    .option(buildSlider(
                                            Component.translatable("config.seamlesssleep.sleep.chat_text_opacity"),
                                            Component.translatable("config.seamlesssleep.sleep.chat_text_opacity.desc"),
                                            0.5D,
                                            () -> cfg.sleepChatTextOpacityMultiplier,
                                            val -> cfg.sleepChatTextOpacityMultiplier = val
                                    ))
                                    .option(buildSlider(
                                            Component.translatable("config.seamlesssleep.sleep.chat_background_opacity"),
                                            Component.translatable("config.seamlesssleep.sleep.chat_background_opacity.desc"),
                                            0.4D,
                                            () -> cfg.sleepChatBackgroundOpacityMultiplier,
                                            val -> cfg.sleepChatBackgroundOpacityMultiplier = val
                                    ))
                                    .build()
                    )
                    .category(
                            ConfigCategory.createBuilder()
                                    .name(Component.translatable("config.seamlesssleep.category.sleep"))
                                    .option(buildToggle(
                                            Component.translatable("config.seamlesssleep.sleep.clears_weather"),
                                            Component.translatable("config.seamlesssleep.sleep.clears_weather.desc"),
                                            true,
                                            () -> cfg.sleepClearsWeather,
                                            val -> cfg.sleepClearsWeather = val
                                    ))
                                    .build()
                    )
                    .save(() -> {
                        cfg.clamp();
                        SeamlessSleepClientConfigManager.save();
                    })
                    .build()
                    .generateScreen(parent);
        };
    }

    private static Option<Double> buildSlider(Component name,
                                              Component description,
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

    private static Option<Boolean> buildToggle(Component name,
                                               Component description,
                                               boolean def,
                                               java.util.function.Supplier<Boolean> getter,
                                               java.util.function.Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(name)
                .description(OptionDescription.of(description))
                .binding(def, getter::get, setter::accept)
                .controller(opt -> BooleanControllerBuilder.create(opt))
                .build();
    }

    private static class MissingYaclScreen extends Screen {
        private final Screen parent;

        protected MissingYaclScreen(Screen parent) {
            super(Component.literal("Seamless Sleep"));
            this.parent = parent;
        }

        @Override
        public void onClose() {
            this.minecraft.setScreen(parent);
        }

        @Override
        protected void init() {
            if (minecraft == null) {
                return;
            }
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                            Component.translatable("gui.back"),
                            b -> this.onClose())
                    .bounds(this.width / 2 - 50, this.height / 2 + 10, 100, 20)
                    .build());
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);
            int x = this.width / 2;
            int y = this.height / 2 - 20;
            context.drawCenteredString(this.font,
                    Component.literal("YACL nao encontrado").withStyle(ChatFormatting.RED),
                    x,
                    y,
                    0xFFFFFF);
            context.drawCenteredString(this.font,
                    Component.literal("Instale YetAnotherConfigLib v3 para editar a config."),
                    x,
                    y + 12,
                    0xFFFFFF);
            super.render(context, mouseX, mouseY, delta);
        }
    }
}
