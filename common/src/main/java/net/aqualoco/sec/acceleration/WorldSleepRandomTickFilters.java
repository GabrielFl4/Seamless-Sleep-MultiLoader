package net.aqualoco.sec.acceleration;

import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MangrovePropaguleBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.NyliumBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SpreadingSnowyDirtBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;

public final class WorldSleepRandomTickFilters {
    private static final byte UNCACHED_FLAGS = (byte) -1;
    private static final byte FLAG_GRASS_AND_FOLIAGE = 1;
    private static final byte FLAG_CROPS_AND_SAPLINGS = 1 << 1;
    private static final byte FLAG_KELP = 1 << 2;
    private static final byte FLAG_VANILLA = 1 << 3;
    private static final byte FLAG_EXCLUDED = 1 << 4;

    private static final String[] MODDED_GRASS_AND_FOLIAGE_HINTS = {
            "blossom",
            "bloom",
            "bush",
            "flower",
            "fung",
            "grass",
            "herb",
            "leaf",
            "leaves",
            "mushroom",
            "mycel",
            "nyli",
            "petal",
            "plant",
            "root",
            "shoot",
            "sprout",
            "vine"
    };

    private static final String[] MODDED_CROPS_AND_SAPLINGS_HINTS = {
            "bamboo",
            "berry",
            "cactus",
            "cane",
            "crop",
            "nether_wart",
            "propagule",
            "reed",
            "sapling",
            "stem",
            "wart"
    };

    private static final String[] MODDED_KELP_HINTS = {
            "kelp"
    };

    private static final String[] MODDED_EXCLUDED_PATH_HINTS = {
            "burn",
            "fire",
            "flame",
            "lightning",
            "storm",
            "thunder",
            "weather"
    };

    private static final Reference2ByteOpenHashMap<Block> ELIGIBILITY_FLAGS = new Reference2ByteOpenHashMap<>();

    static {
        ELIGIBILITY_FLAGS.defaultReturnValue(UNCACHED_FLAGS);
    }

    private WorldSleepRandomTickFilters() {
    }

    public static boolean isEligible(WorldSleepAccelerationFilterPolicy policy, BlockState state) {
        if (policy == null || !policy.isAnyEnabled() || state == null || !state.isRandomlyTicking()) {
            return false;
        }

        byte flags = getOrComputeFlags(state.getBlock());
        if ((flags & FLAG_EXCLUDED) != 0) {
            return false;
        }

        if (policy.isVanillaOnly() && (flags & FLAG_VANILLA) == 0) {
            return false;
        }

        if (policy.isCropsAndSaplingsEnabled() && (flags & FLAG_CROPS_AND_SAPLINGS) != 0) {
            return true;
        }
        if (policy.isGrassAndFoliageEnabled() && (flags & FLAG_GRASS_AND_FOLIAGE) != 0) {
            return true;
        }
        return policy.isKelpEnabled() && (flags & FLAG_KELP) != 0;
    }

    private static byte getOrComputeFlags(Block block) {
        byte cachedFlags = ELIGIBILITY_FLAGS.getByte(block);
        if (cachedFlags != UNCACHED_FLAGS) {
            return cachedFlags;
        }

        byte computedFlags = computeFlags(block);
        ELIGIBILITY_FLAGS.put(block, computedFlags);
        return computedFlags;
    }

    private static byte computeFlags(Block block) {
        byte flags = 0;

        if (block instanceof BaseFireBlock) {
            return FLAG_EXCLUDED;
        }

        if (isVanillaBlock(block)) {
            flags |= FLAG_VANILLA;
        }

        if (block instanceof KelpBlock || isModdedCandidate(block, MODDED_KELP_HINTS)) {
            flags |= FLAG_KELP;
        }

        if (isFarmLikeBlock(block) || isModdedCandidate(block, MODDED_CROPS_AND_SAPLINGS_HINTS)) {
            flags |= FLAG_CROPS_AND_SAPLINGS;
        }

        if (isGrassAndFoliageBlock(block) || isModdedCandidate(block, MODDED_GRASS_AND_FOLIAGE_HINTS)) {
            flags |= FLAG_GRASS_AND_FOLIAGE;
        }

        return flags;
    }

    private static boolean isGrassAndFoliageBlock(Block block) {
        return block instanceof SpreadingSnowyDirtBlock
                || block instanceof NyliumBlock
                || block instanceof LeavesBlock
                || block instanceof MushroomBlock
                || block instanceof ChorusFlowerBlock
                || block instanceof WeatheringCopper
                || (block instanceof GrowingPlantHeadBlock && !(block instanceof KelpBlock));
    }

    private static boolean isFarmLikeBlock(Block block) {
        return block instanceof CropBlock
                || block instanceof StemBlock
                || block instanceof NetherWartBlock
                || block instanceof SaplingBlock
                || block instanceof SugarCaneBlock
                || block instanceof CactusBlock
                || block instanceof BambooStalkBlock
                || block instanceof BambooSaplingBlock
                || block instanceof CocoaBlock
                || block instanceof SweetBerryBushBlock
                || block instanceof MangrovePropaguleBlock;
    }

    private static boolean isVanillaBlock(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return id != null && "minecraft".equals(id.getNamespace());
    }

    private static boolean isModdedCandidate(Block block, String[] hints) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null || "minecraft".equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        if (containsAny(path, MODDED_EXCLUDED_PATH_HINTS)) {
            return false;
        }
        return containsAny(path, hints);
    }

    private static boolean containsAny(String path, String[] hints) {
        for (String hint : hints) {
            if (path.contains(hint)) {
                return true;
            }
        }
        return false;
    }
}
