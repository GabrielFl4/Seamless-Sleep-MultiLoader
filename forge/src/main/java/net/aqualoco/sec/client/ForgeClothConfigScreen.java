package net.aqualoco.sec.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.aqualoco.sec.config.SeamlessSleepClientConfig;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ForgeClothConfigScreen {

    private ForgeClothConfigScreen() {
    }

    public static Screen create(Screen parent) {
        SeamlessSleepClientConfig cfg = SeamlessSleepClientConfigManager.get();
        cfg.clamp();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.seamlesssleep.title"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory chat = builder.getOrCreateCategory(
                Component.translatable("config.seamlesssleep.category.chat")
        );

        chat.addEntry(entryBuilder
                .startDoubleField(
                        Component.translatable("config.seamlesssleep.sleep.chat_text_opacity"),
                        cfg.sleepChatTextOpacityMultiplier
                )
                .setDefaultValue(0.5D)
                .setMin(0.0D)
                .setMax(1.0D)
                .setTooltip(Component.translatable("config.seamlesssleep.sleep.chat_text_opacity.desc"))
                .setSaveConsumer(value -> cfg.sleepChatTextOpacityMultiplier = value)
                .build()
        );

        chat.addEntry(entryBuilder
                .startDoubleField(
                        Component.translatable("config.seamlesssleep.sleep.chat_background_opacity"),
                        cfg.sleepChatBackgroundOpacityMultiplier
                )
                .setDefaultValue(0.4D)
                .setMin(0.0D)
                .setMax(1.0D)
                .setTooltip(Component.translatable("config.seamlesssleep.sleep.chat_background_opacity.desc"))
                .setSaveConsumer(value -> cfg.sleepChatBackgroundOpacityMultiplier = value)
                .build()
        );

        builder.setSavingRunnable(() -> {
            cfg.clamp();
            SeamlessSleepClientConfigManager.save();
        });

        return builder.build();
    }
}
