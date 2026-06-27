package net.aqualoco.sec.acceleration;

import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
    static final byte FLAG_GRASS_AND_FOLIAGE = 1;
    static final byte FLAG_CROPS_AND_SAPLINGS = 1 << 1;
    static final byte FLAG_KELP = 1 << 2;
    static final byte FLAG_VINES_AND_BAMBOO = 1 << 3;
    private static final byte FLAG_VANILLA = 1 << 4;
    private static final byte FLAG_EXCLUDED = 1 << 5;
    private static final int CATEGORY_MASK =
            FLAG_GRASS_AND_FOLIAGE
                    | FLAG_CROPS_AND_SAPLINGS
                    | FLAG_KELP
                    | FLAG_VINES_AND_BAMBOO;

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

    static boolean isEligible(int allowedCategoryMask, boolean vanillaOnly, BlockState state) {
        if (allowedCategoryMask == 0 || state == null || !state.isRandomlyTicking()) {
            return false;
        }
        byte flags = getOrComputeFlags(state.getBlock());
        if ((flags & FLAG_EXCLUDED) != 0) {
            return false;
        }

        if (vanillaOnly && (flags & FLAG_VANILLA) == 0) {
            return false;
        }
        return (flags & allowedCategoryMask) != 0;
    }

    static boolean mayContainRelevantState(int allowedCategoryMask,
                                           boolean vanillaOnly,
                                           BlockState state) {
        if (allowedCategoryMask == 0 || state == null || !state.isRandomlyTicking()) {
            return false;
        }

        byte flags = getOrComputeFlags(state.getBlock());
        if ((flags & FLAG_EXCLUDED) != 0) {
            return false;
        }

        boolean vanilla = (flags & FLAG_VANILLA) != 0;
        if (vanillaOnly && !vanilla) {
            return false;
        }
        if ((flags & allowedCategoryMask) != 0) {
            return true;
        }

        // Preserve compatibility for unclassified modded random-ticking blocks.
        // They still pass through the normal position filter, but they cannot make
        // the section gate incorrectly discard a potentially supported mod block.
        return !vanillaOnly && !vanilla && (flags & CATEGORY_MASK) == 0;
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

        boolean vinesAndBamboo = isVanillaVinesAndBambooBlock(block);
        if (vinesAndBamboo) {
            flags |= FLAG_VINES_AND_BAMBOO;
        }

        if (!vinesAndBamboo
                && (isFarmLikeBlock(block)
                || isModdedCandidate(block, MODDED_CROPS_AND_SAPLINGS_HINTS))) {
            flags |= FLAG_CROPS_AND_SAPLINGS;
        }

        if (!vinesAndBamboo
                && (isGrassAndFoliageBlock(block)
                || isModdedCandidate(block, MODDED_GRASS_AND_FOLIAGE_HINTS))) {
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

    private static boolean isVanillaVinesAndBambooBlock(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null || !"minecraft".equals(id.getNamespace())) {
            return false;
        }
        return switch (id.getPath()) {
            case "bamboo",
                 "bamboo_sapling",
                 "vine",
                 "cave_vines",
                 "cave_vines_plant",
                 "mangrove_propagule" -> true;
            default -> false;
        };
    }

    private static boolean isVanillaBlock(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id != null && "minecraft".equals(id.getNamespace());
    }

    private static boolean isModdedCandidate(Block block, String[] hints) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
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
