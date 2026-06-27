package net.aqualoco.sec.compat;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.bed.BedRestingHelper;
import net.aqualoco.sec.config.SeamlessSleepServerConfigManager;
import net.aqualoco.sec.sleep.SleepDimensionSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class ComfortsCompat {

    public static final String MOD_ID = "comforts";
    public static final String BASE_COMFORTS_BLOCK_RESOURCE =
            "com/illusivesoulworks/comforts/common/block/BaseComfortsBlock.class";
    public static final String COMFORTS_EVENTS_RESOURCE =
            "com/illusivesoulworks/comforts/common/ComfortsEvents.class";

    private static final String BASE_COMFORTS_BLOCK_CLASS =
            "com.illusivesoulworks.comforts.common.block.BaseComfortsBlock";
    private static final String SLEEPING_BAG_BLOCK_CLASS =
            "com.illusivesoulworks.comforts.common.block.SleepingBagBlock";
    private static final String HAMMOCK_BLOCK_CLASS =
            "com.illusivesoulworks.comforts.common.block.HammockBlock";
    private static final double SLEEPING_BAG_SLEEPING_BODY_Y_OFFSET = -0.375D;
    private static final double HAMMOCK_SLEEPING_BODY_Y_OFFSET = -0.5D;
    private static final TagKey<Block> SLEEPING_BAGS = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "sleeping_bags")
    );
    private static final TagKey<Block> HAMMOCKS = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "hammocks")
    );

    private ComfortsCompat() {
    }

    public static void initializeManagedSleep(ServerPlayer player, BlockPos bedPos) {
        if (player == null
                || bedPos == null
                || !player.isSleeping()
                || !BedRestingHelper.isManagedBedWorkflowSupported(player, bedPos)) {
            return;
        }

        boolean countedForSleep = BedRestingHelper.canCountForSleep(player, bedPos)
                || canCountAcceptedComfortsSleep(player, bedPos);

        BedRestingHelper.initializeAuthoritativeBedLook(player, bedPos);
        BedRestingHelper.syncManagedSleepState(player, countedForSleep);
        BedRestingHelper.showLeaveBedHint(player);
        Constants.debug("Comforts sleep surface entered Seamless managed sleep workflow at {}.", bedPos);
    }

    public static boolean shouldAllowSeamlessDaySleep(Level level, BlockPos pos, @Nullable Object comfortsResult) {
        // Comforts returns DEFAULT for its vanilla-time fallback, and DENY for explicit rules
        // like NONE or day-only hammocks at night. Seamless only replaces the DEFAULT daytime
        // blocker, so Comforts-specific hard denials stay authoritative.
        if (level == null
                || pos == null
                || !isDefaultComfortsTimeResult(comfortsResult)
                || !level.isBrightOutside()
                || !SeamlessSleepServerConfigManager.get().sleepEligibility.allowsDaySleep()
                || !SleepDimensionSupport.supportsManagedBedWorkflow(level, pos)) {
            return false;
        }

        return isComfortsSleepSurface(level.getBlockState(pos));
    }

    @Nullable
    public static Object comfortsTimeResultLike(Object currentResult, String name) {
        if (!(currentResult instanceof Enum<?> currentEnum)) {
            return null;
        }

        try {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object resolved = Enum.valueOf((Class) currentEnum.getDeclaringClass(), name);
            return resolved;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean isComfortsSleepSurface(BlockState state) {
        if (state == null) {
            return false;
        }
        // Current Comforts surfaces are SleepingBagBlock and HammockBlock, exposed through
        // these tags and both inheriting BaseComfortsBlock.
        if (state.is(SLEEPING_BAGS) || state.is(HAMMOCKS)) {
            return true;
        }

        Block block = state.getBlock();
        return block != null && hasBaseComfortsBlockClass(block.getClass());
    }

    public static double getSleepingBodyYOffset(Level level, @Nullable BlockPos bedPos) {
        if (level == null || bedPos == null || !level.isLoaded(bedPos)) {
            return 0.0D;
        }

        BlockState state = level.getBlockState(bedPos);
        if (isSleepingBag(state)) {
            return SLEEPING_BAG_SLEEPING_BODY_Y_OFFSET;
        }
        if (isHammock(state)) {
            return HAMMOCK_SLEEPING_BODY_Y_OFFSET;
        }
        return 0.0D;
    }

    public static boolean isSleepingBag(BlockState state) {
        if (state == null) {
            return false;
        }
        if (state.is(SLEEPING_BAGS)) {
            return true;
        }
        Block block = state.getBlock();
        return block != null && hasClassInHierarchy(block.getClass(), SLEEPING_BAG_BLOCK_CLASS);
    }

    public static boolean isHammock(BlockState state) {
        if (state == null) {
            return false;
        }
        if (state.is(HAMMOCKS)) {
            return true;
        }
        Block block = state.getBlock();
        return block != null && hasClassInHierarchy(block.getClass(), HAMMOCK_BLOCK_CLASS);
    }

    private static boolean canCountAcceptedComfortsSleep(ServerPlayer player, BlockPos bedPos) {
        if (!player.isAlive()
                || SeamlessSleepServerConfigManager.get().sleepEligibility.preventsSleepSkip()
                || !BedRestingHelper.isManagedBedWorkflowSupported(player, bedPos)) {
            return false;
        }

        BlockState state = player.level().getBlockState(bedPos);
        if (!isComfortsSleepSurface(state)) {
            return false;
        }

        Direction direction = BedRestingHelper.getBedDirection(player.level(), bedPos);
        return direction != null && BedRestingHelper.canStartResting(player, bedPos, direction);
    }

    private static boolean isDefaultComfortsTimeResult(@Nullable Object result) {
        return result instanceof Enum<?> enumResult
                && "DEFAULT".equals(enumResult.name())
                && "com.illusivesoulworks.comforts.common.ComfortsEvents$Result"
                .equals(enumResult.getDeclaringClass().getName());
    }

    private static boolean hasBaseComfortsBlockClass(Class<?> blockClass) {
        return hasClassInHierarchy(blockClass, BASE_COMFORTS_BLOCK_CLASS);
    }

    private static boolean hasClassInHierarchy(Class<?> blockClass, String className) {
        Class<?> current = blockClass;
        while (current != null) {
            if (className.equals(current.getName())) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }
}
