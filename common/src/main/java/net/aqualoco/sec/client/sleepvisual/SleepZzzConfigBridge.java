package net.aqualoco.sec.client.sleepvisual;

import net.aqualoco.sec.config.SeamlessSleepClientConfigManager;
import net.minecraft.util.Mth;

import java.util.Locale;

// Keeps the visual system insulated from config parsing and older TOML values.
public final class SleepZzzConfigBridge {

    public static final int DEFAULT_CHANCE = 70;
    public static final SleepZzzStyle DEFAULT_STYLE = SleepZzzStyle.CARTOON_DRIFT;

    private SleepZzzConfigBridge() {
    }

    public static int chance() {
        return Mth.clamp(SeamlessSleepClientConfigManager.get().sleepZzzChance, 0, 100);
    }

    public static SleepZzzStyle style() {
        return parseStyle(SeamlessSleepClientConfigManager.get().sleepZzzStyle);
    }

    public static SleepZzzStyle parseStyle(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_STYLE;
        }

        try {
            return SleepZzzStyle.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return DEFAULT_STYLE;
        }
    }
}
