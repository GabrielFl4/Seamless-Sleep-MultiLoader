package net.aqualoco.sec.client.sleepindicator.biomeclock;

// Names the visual biome layers available to the biome clock renderer.
public enum BiomeClockCategory {
    PLAINS("plains"),
    FOREST("forest"),
    DESERT("desert"),
    SNOW_PLAINS("snow_plains"),
    SAVANNA("savanna"),
    OCEAN("ocean"),
    OCEAN_DEEP("ocean_deep"),
    OCEAN_WARM("ocean_warm"),
    OCEAN_FROZEN("ocean_frozen"),
    JUNGLE("jungle"),
    CHERRY("cherry"),
    MANGROVE("mangrove"),
    SWAMP("swamp"),
    PALE_GARDEN("palegarden"),
    BADLANDS("badlands"),
    BEACH("beach"),
    STONY_SHORES("stony_shores"),
    MUSHROOMS("mushrooms"),
    RIVER("river"),
    RIVER_FROZEN("frozen_river"),
    MEADOW("meadow"),
    STONY_PEAKS("stonypeaks"),
    WINDSWEPT("windswept"),
    ICE_PLAINS("ice_plains"),
    FOREST_BIRCH("forest_birch"),
    FOREST_DARK("forest_dark"),
    FOREST_TAIGA("forest_taiga"),
    DEFAULT("default");

    private final String textureId;

    BiomeClockCategory(String textureId) {
        this.textureId = textureId;
    }

    public String textureId() {
        return this.textureId;
    }
}
