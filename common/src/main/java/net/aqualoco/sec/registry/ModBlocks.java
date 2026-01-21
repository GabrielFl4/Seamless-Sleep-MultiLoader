package net.aqualoco.sec.registry;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.block.SleepBarrier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.BiConsumer;

public class ModBlocks {

    public static final Block SLEEP_BARRIER = new SleepBarrier(BlockBehaviour.Properties.of());

    public static void register(BiConsumer<Block, ResourceLocation> consumer) {
        consumer.accept(SLEEP_BARRIER, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sleep_barrier"));
    }
}
