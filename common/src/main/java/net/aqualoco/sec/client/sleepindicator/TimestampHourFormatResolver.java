package net.aqualoco.sec.client.sleepindicator;

import net.minecraft.client.Minecraft;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

// Keeps locale-specific 12h/24h decisions isolated for future language tuning.
public final class TimestampHourFormatResolver {
    private TimestampHourFormatResolver() {
    }

    public static boolean usesTwelveHourClock(Minecraft client) {
        String languageCode = resolveLanguageCode(client);
        if ("en_us".equals(languageCode)) {
            return true;
        }
        if ("pt_br".equals(languageCode)) {
            return false;
        }

        Locale locale = toLocale(languageCode);
        if (locale == null) {
            return false;
        }

        try {
            String formatted = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                    .withLocale(locale)
                    .format(LocalTime.of(13, 0));
            String normalized = formatted.toLowerCase(Locale.ROOT);
            return normalized.contains("pm") || !formatted.contains("13");
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String resolveLanguageCode(Minecraft client) {
        if (client == null || client.getLanguageManager() == null) {
            return "en_us";
        }

        String selected = client.getLanguageManager().getSelected();
        if (selected == null || selected.isBlank()) {
            return "en_us";
        }
        return selected.trim().replace('-', '_').toLowerCase(Locale.ROOT);
    }

    private static Locale toLocale(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return null;
        }

        Locale locale = Locale.forLanguageTag(languageCode.replace('_', '-'));
        return locale.getLanguage().isBlank() ? null : locale;
    }
}
