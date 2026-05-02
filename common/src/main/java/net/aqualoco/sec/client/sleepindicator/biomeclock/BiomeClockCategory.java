package net.aqualoco.sec.client.sleepindicator.biomeclock;

// Names the visual biome layers available to the biome clock renderer.
public enum BiomeClockCategory {
    PLAINS("plains"),
    FOREST("forest"),
    DESERT("desert"),
    SNOW("snow"),
    SAVANNA("savanna"),
    OCEAN("ocean"),
    JUNGLE("jungle"),
    CHERRY("cherry"),
    MANGROVE("mangrove"),
    SWAMP("swamp"),
    PALE_GARDEN("palegarden"),
    BADLANDS("badlands"),
    BEACH("beach"),
    MUSHROOMS("mushrooms"),
    RIVER("river"),
    RIVER_FROZEN("frozen_river"),
    MEADOW("meadow"),
    STONY_PEAKS("stonypeaks"),
    DEFAULT("default");

    private final String textureId;

    BiomeClockCategory(String textureId) {
        this.textureId = textureId;
    }

    public String textureId() {
        return this.textureId;
    }
}
