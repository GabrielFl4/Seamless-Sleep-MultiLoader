package net.aqualoco.sec.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.aqualoco.sec.config.SeamlessSleepClientConfig;
import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.aqualoco.sec.config.SeamlessSleepServerConfig;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.config.SeamlessSleepServerConfigSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ForgeClothConfigScreen {

    private ForgeClothConfigScreen() {
    }

    public static Screen create(Screen parent) {
        SeamlessSleepClientConfig cfg = SeamlessSleepClientConfigManager.get();
        cfg.clamp();
        SeamlessSleepServerConfig serverCfg = SeamlessSleepServerConfigManager.get();
        serverCfg.clamp();

        Minecraft client = Minecraft.getInstance();
        boolean connectedToRemote = client.getConnection() != null && !client.hasSingleplayerServer();
        boolean canEditServerConfig = !connectedToRemote;
        boolean serverToggleValue = canEditServerConfig
                ? serverCfg.sleepClearsWeather
                : SeamlessSleepServerConfigSnapshot.getSleepClearsWeather();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.seamlesssleep.title"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory chat = builder.getOrCreateCategory(
                Component.translatable("config.seamlesssleep.category.chat")
        );
        ConfigCategory sleep = builder.getOrCreateCategory(
                Component.translatable("config.seamlesssleep.category.sleep")
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

        Component clearsWeatherDesc =
                Component.translatable("config.seamlesssleep.sleep.clears_weather.desc");
        Component serverControlled =
                Component.translatable("config.seamlesssleep.server_controlled");

        sleep.addEntry(entryBuilder
                .startBooleanToggle(
                        Component.translatable("config.seamlesssleep.sleep.clears_weather"),
                        serverToggleValue
                )
                .setDefaultValue(true)
                .setTooltip(canEditServerConfig
                        ? new Component[]{clearsWeatherDesc}
                        : new Component[]{clearsWeatherDesc, serverControlled})
                .setSaveConsumer(value -> {
                    if (canEditServerConfig) {
                        serverCfg.sleepClearsWeather = value;
                    }
                })
                .setRequirement(() -> canEditServerConfig)
                .build()
        );

        builder.setSavingRunnable(() -> {
            cfg.clamp();
            SeamlessSleepClientConfigManager.save();
            if (canEditServerConfig) {
                serverCfg.clamp();
                SeamlessSleepServerConfigManager.save();
            }
        });

        return builder.build();
    }
}
