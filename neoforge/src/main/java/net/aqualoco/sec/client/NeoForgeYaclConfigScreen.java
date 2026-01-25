package net.aqualoco.sec.client;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import net.aqualoco.sec.config.SeamlessSleepClientConfig;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class NeoForgeYaclConfigScreen {

    private NeoForgeYaclConfigScreen() {
    }

    public static Screen create(Screen parent) {
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
                .save(() -> {
                    cfg.clamp();
                    SeamlessSleepClientConfigManager.save();
                })
                .build()
                .generateScreen(parent);
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
}
