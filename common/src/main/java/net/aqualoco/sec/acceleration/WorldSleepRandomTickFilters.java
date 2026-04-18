package net.aqualoco.sec.acceleration;

import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import net.aqualoco.sec.config.WorldSleepNatureFilterProfile;
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
import net.minecraft.world.level.block.state.BlockState;

public final class WorldSleepRandomTickFilters {
    private static final byte UNCACHED_FLAGS = (byte) -1;
    private static final byte FLAG_FARM = 1;
    private static final byte FLAG_VANILLA_NATURE = 1 << 1;
    private static final byte FLAG_MODDED_NATURE = 1 << 2;
    private static final byte FLAG_EXCLUDED = 1 << 3;

    private static final String[] MODDED_NATURE_PATH_HINTS = {
            "bamboo",
            "berry",
            "blossom",
            "bush",
            "cactus",
            "cane",
            "crop",
            "flower",
            "fung",
            "grass",
            "herb",
            "kelp",
            "leaf",
            "leaves",
            "mushroom",
            "mycel",
            "nether_wart",
            "nyli",
            "petal",
            "plant",
            "propagule",
            "reed",
            "root",
            "sapling",
            "shoot",
            "sprout",
            "stem",
            "sugar_cane",
            "vine",
            "wart"
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

    public static boolean isEligible(WorldSleepNatureFilterProfile profile, BlockState state) {
        if (profile == null || state == null || !state.isRandomlyTicking()) {
            return false;
        }

        byte flags = getOrComputeFlags(state.getBlock());
        if ((flags & FLAG_EXCLUDED) != 0) {
            return false;
        }

        return switch (profile) {
            case VANILLA_ONLY -> (flags & FLAG_VANILLA_NATURE) != 0;
            case FARM_ONLY -> (flags & FLAG_FARM) != 0;
            case ALL -> (flags & (FLAG_VANILLA_NATURE | FLAG_MODDED_NATURE)) != 0;
        };
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

        boolean farmLike = isFarmLikeBlock(block);
        if (farmLike) {
            flags |= FLAG_FARM;
            flags |= FLAG_VANILLA_NATURE;
        }

        if (isVanillaNatureBlock(block)) {
            flags |= FLAG_VANILLA_NATURE;
        }

        if (isModdedNatureCandidate(block)) {
            flags |= FLAG_MODDED_NATURE;
        }

        return flags;
    }

    private static boolean isVanillaNatureBlock(Block block) {
        return block instanceof SpreadingSnowyDirtBlock
                || block instanceof NyliumBlock
                || block instanceof LeavesBlock
                || block instanceof KelpBlock
                || block instanceof MushroomBlock
                || block instanceof ChorusFlowerBlock
                || block instanceof GrowingPlantHeadBlock
                || isFarmLikeBlock(block);
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

    private static boolean isModdedNatureCandidate(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null || "minecraft".equals(id.getNamespace())) {
            return false;
        }

        String path = id.getPath();
        if (containsAny(path, MODDED_EXCLUDED_PATH_HINTS)) {
            return false;
        }
        return containsAny(path, MODDED_NATURE_PATH_HINTS);
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
