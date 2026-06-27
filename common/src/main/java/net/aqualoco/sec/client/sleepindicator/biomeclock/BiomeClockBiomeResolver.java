package net.aqualoco.sec.client.sleepindicator.biomeclock;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
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
            ResourceLocation id = biome.unwrapKey()
                    .map(ResourceKey::location)
                    .orElse(null);
            return resolve(id);
        } catch (RuntimeException ignored) {
            return FALLBACK;
        }
    }

    private static ResolvedBiome resolve(ResourceLocation id) {
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
            case "minecraft:snowy_plains" -> BiomeClockCategory.SNOW_PLAINS;
            case "minecraft:ice_spikes" -> BiomeClockCategory.ICE_PLAINS;
            case "minecraft:snowy_taiga",
                 "minecraft:snowy_slopes",
                 "minecraft:grove",
                 "minecraft:jagged_peaks",
                 "minecraft:frozen_peaks" -> BiomeClockCategory.SNOW_PLAINS;
            case "minecraft:desert" -> BiomeClockCategory.DESERT;
            case "minecraft:badlands",
                 "minecraft:wooded_badlands",
                 "minecraft:eroded_badlands" -> BiomeClockCategory.BADLANDS;
            case "minecraft:savanna",
                 "minecraft:savanna_plateau",
                 "minecraft:windswept_savanna" -> BiomeClockCategory.SAVANNA;
            case "minecraft:jungle",
                 "minecraft:sparse_jungle",
                 "minecraft:bamboo_jungle" -> BiomeClockCategory.JUNGLE;
            case "minecraft:cherry_grove" -> BiomeClockCategory.CHERRY;
            case "minecraft:pale_garden" -> BiomeClockCategory.PALE_GARDEN;
            case "minecraft:mangrove_swamp" -> BiomeClockCategory.MANGROVE;
            case "minecraft:swamp",
                 "minecraft:swamp_hills" -> BiomeClockCategory.SWAMP;
            case "minecraft:mushroom_fields",
                 "minecraft:mushroom_field_shore" -> BiomeClockCategory.MUSHROOMS;
            case "minecraft:river" -> BiomeClockCategory.RIVER;
            case "minecraft:frozen_river" -> BiomeClockCategory.RIVER_FROZEN;
            case "minecraft:stony_peaks" -> BiomeClockCategory.STONY_PEAKS;
            case "minecraft:windswept_hills",
                 "minecraft:windswept_gravelly_hills" -> BiomeClockCategory.WINDSWEPT;
            case "minecraft:frozen_ocean",
                 "minecraft:deep_frozen_ocean" -> BiomeClockCategory.OCEAN_FROZEN;
            case "minecraft:warm_ocean",
                 "minecraft:lukewarm_ocean",
                 "minecraft:deep_lukewarm_ocean" -> BiomeClockCategory.OCEAN_WARM;
            case "minecraft:deep_ocean",
                 "minecraft:deep_cold_ocean" -> BiomeClockCategory.OCEAN_DEEP;
            case "minecraft:ocean",
                 "minecraft:cold_ocean" -> BiomeClockCategory.OCEAN;
            case "minecraft:birch_forest",
                 "minecraft:old_growth_birch_forest" -> BiomeClockCategory.FOREST_BIRCH;
            case "minecraft:dark_forest" -> BiomeClockCategory.FOREST_DARK;
            case "minecraft:taiga",
                 "minecraft:old_growth_pine_taiga",
                 "minecraft:old_growth_spruce_taiga" -> BiomeClockCategory.FOREST_TAIGA;
            case "minecraft:forest",
                 "minecraft:flower_forest" -> BiomeClockCategory.FOREST;
            case "minecraft:plains",
                 "minecraft:sunflower_plains" -> BiomeClockCategory.PLAINS;
            case "minecraft:meadow" -> BiomeClockCategory.MEADOW;
            case "minecraft:stony_shore" -> BiomeClockCategory.STONY_SHORES;
            case "minecraft:beach" -> BiomeClockCategory.BEACH;
            default -> BiomeClockCategory.DEFAULT;
        };
    }

    private static BiomeClockCategory resolvePath(String path) {
        String normalizedPath = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (containsAny(normalizedPath, "frozen_ocean", "ocean_frozen")) {
            return BiomeClockCategory.OCEAN_FROZEN;
        }
        if (containsAny(normalizedPath, "warm_ocean", "lukewarm_ocean", "ocean_warm", "ocean_lukewarm")) {
            return BiomeClockCategory.OCEAN_WARM;
        }
        if (containsAny(normalizedPath, "deep_ocean", "ocean_deep")) {
            return BiomeClockCategory.OCEAN_DEEP;
        }
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
        if (containsAny(normalizedPath, "pale_garden", "palegarden")) {
            return BiomeClockCategory.PALE_GARDEN;
        }
        if (containsAny(normalizedPath, "mangrove")) {
            return BiomeClockCategory.MANGROVE;
        }
        if (containsAny(normalizedPath, "swamp")) {
            return BiomeClockCategory.SWAMP;
        }
        if (containsAny(normalizedPath, "mushroom")) {
            return BiomeClockCategory.MUSHROOMS;
        }
        if (containsAny(normalizedPath, "frozen_river", "river_frozen")) {
            return BiomeClockCategory.RIVER_FROZEN;
        }
        if (containsAny(normalizedPath, "river")) {
            return BiomeClockCategory.RIVER;
        }
        if ("stony_peaks".equals(normalizedPath)) {
            return BiomeClockCategory.STONY_PEAKS;
        }
        if (containsAny(normalizedPath, "ice_plains", "ice_spikes")) {
            return BiomeClockCategory.ICE_PLAINS;
        }
        if (containsAny(normalizedPath, "snowy_plains", "snow_plains")) {
            return BiomeClockCategory.SNOW_PLAINS;
        }
        if (containsAny(normalizedPath, "snow", "frozen", "ice", "cold", "peak", "grove")) {
            return BiomeClockCategory.SNOW_PLAINS;
        }
        if (containsAny(normalizedPath, "badlands", "mesa")) {
            return BiomeClockCategory.BADLANDS;
        }
        if (containsAny(normalizedPath, "stony_shore", "stony_shores")) {
            return BiomeClockCategory.STONY_SHORES;
        }
        if (containsAny(normalizedPath, "beach", "shore")) {
            return BiomeClockCategory.BEACH;
        }
        if (containsAny(normalizedPath, "desert", "dunes")) {
            return BiomeClockCategory.DESERT;
        }
        if (containsAny(normalizedPath, "birch")) {
            return BiomeClockCategory.FOREST_BIRCH;
        }
        if (containsAny(normalizedPath, "dark_forest", "forest_dark")) {
            return BiomeClockCategory.FOREST_DARK;
        }
        if (containsAny(normalizedPath, "taiga", "forest_taiga")) {
            return BiomeClockCategory.FOREST_TAIGA;
        }
        if (containsAny(normalizedPath, "forest", "wood", "birch", "taiga")) {
            return BiomeClockCategory.FOREST;
        }
        if (containsAny(normalizedPath, "meadow")) {
            return BiomeClockCategory.MEADOW;
        }
        if (containsAny(normalizedPath, "plains", "field", "grassland")) {
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
