package net.aqualoco.sec.client.sleepindicator.biomeclock;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.util.Locale;

// Converts the player's current biome id into the biome clock's small visual category set.
public final class BiomeClockBiomeResolver {
    private static final ResolvedBiome FALLBACK = new ResolvedBiome("unknown", BiomeClockCategory.DEFAULT);

    private BiomeClockBiomeResolver() {
    }

    public static ResolvedBiome resolve(ClientLevel level, LocalPlayer player) {
        if (level == null || player == null) {
            return FALLBACK;
        }

        try {
            Holder<Biome> biome = level.getBiome(player.blockPosition());
            Identifier id = biome.unwrapKey()
                    .map(ResourceKey::identifier)
                    .orElse(null);
            return resolve(id);
        } catch (RuntimeException ignored) {
            return FALLBACK;
        }
    }

    private static ResolvedBiome resolve(Identifier id) {
        if (id == null) {
            return FALLBACK;
        }

        String rawId = id.toString();
        BiomeClockCategory exactCategory = resolveExact(rawId);
        if (exactCategory != BiomeClockCategory.DEFAULT) {
            return new ResolvedBiome(rawId, exactCategory);
        }

        return new ResolvedBiome(rawId, resolvePath(id.getPath()));
    }

    private static BiomeClockCategory resolveExact(String rawId) {
        return switch (rawId) {
            case "minecraft:snowy_plains",
                 "minecraft:ice_spikes",
                 "minecraft:snowy_taiga",
                 "minecraft:frozen_river",
                 "minecraft:snowy_slopes",
                 "minecraft:grove",
                 "minecraft:jagged_peaks",
                 "minecraft:frozen_peaks" -> BiomeClockCategory.SNOW;
            case "minecraft:desert",
                 "minecraft:badlands",
                 "minecraft:wooded_badlands",
                 "minecraft:eroded_badlands" -> BiomeClockCategory.DESERT;
            case "minecraft:savanna",
                 "minecraft:savanna_plateau",
                 "minecraft:windswept_savanna" -> BiomeClockCategory.SAVANNA;
            case "minecraft:jungle",
                 "minecraft:sparse_jungle",
                 "minecraft:bamboo_jungle" -> BiomeClockCategory.JUNGLE;
            case "minecraft:cherry_grove" -> BiomeClockCategory.CHERRY;
            case "minecraft:mangrove_swamp" -> BiomeClockCategory.MANGROVE;
            case "minecraft:swamp",
                 "minecraft:swamp_hills" -> BiomeClockCategory.SWAMP;
            case "minecraft:ocean",
                 "minecraft:deep_ocean",
                 "minecraft:cold_ocean",
                 "minecraft:deep_cold_ocean",
                 "minecraft:lukewarm_ocean",
                 "minecraft:deep_lukewarm_ocean",
                 "minecraft:warm_ocean",
                 "minecraft:frozen_ocean",
                 "minecraft:deep_frozen_ocean" -> BiomeClockCategory.OCEAN;
            case "minecraft:forest",
                 "minecraft:flower_forest",
                 "minecraft:birch_forest",
                 "minecraft:old_growth_birch_forest",
                 "minecraft:dark_forest",
                 "minecraft:taiga",
                 "minecraft:old_growth_pine_taiga",
                 "minecraft:old_growth_spruce_taiga" -> BiomeClockCategory.FOREST;
            case "minecraft:plains",
                 "minecraft:sunflower_plains",
                 "minecraft:meadow",
                 "minecraft:beach" -> BiomeClockCategory.PLAINS;
            default -> BiomeClockCategory.DEFAULT;
        };
    }

    private static BiomeClockCategory resolvePath(String path) {
        String normalizedPath = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (containsAny(normalizedPath, "ocean")) {
            return BiomeClockCategory.OCEAN;
        }
        if (containsAny(normalizedPath, "jungle")) {
            return BiomeClockCategory.JUNGLE;
        }
        if (containsAny(normalizedPath, "savanna")) {
            return BiomeClockCategory.SAVANNA;
        }
        if (containsAny(normalizedPath, "cherry")) {
            return BiomeClockCategory.CHERRY;
        }
        if (containsAny(normalizedPath, "mangrove")) {
            return BiomeClockCategory.MANGROVE;
        }
        if (containsAny(normalizedPath, "swamp")) {
            return BiomeClockCategory.SWAMP;
        }
        if (containsAny(normalizedPath, "snow", "frozen", "ice", "cold", "peak", "grove")) {
            return BiomeClockCategory.SNOW;
        }
        if (containsAny(normalizedPath, "desert", "badlands", "mesa", "dunes")) {
            return BiomeClockCategory.DESERT;
        }
        if (containsAny(normalizedPath, "forest", "wood", "birch", "taiga")) {
            return BiomeClockCategory.FOREST;
        }
        if (containsAny(normalizedPath, "plains", "meadow", "field", "grassland", "beach")) {
            return BiomeClockCategory.PLAINS;
        }
        return BiomeClockCategory.DEFAULT;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    // Carries the raw biome id with the resolved visual category.
    public record ResolvedBiome(String rawBiomeId, BiomeClockCategory category) {
    }
}
