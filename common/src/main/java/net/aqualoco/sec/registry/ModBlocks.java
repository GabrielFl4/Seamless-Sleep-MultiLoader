package net.aqualoco.sec.registry;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.block.SleepBarrier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.BiConsumer;

// Registers the custom blocks shared by all loaders.
public class ModBlocks {

    private static Block sleepBarrier;

    public static void register(BiConsumer<Block, ResourceLocation> consumer) {
        sleepBarrier = createSleepBarrier();
        consumer.accept(sleepBarrier, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sleep_barrier"));
    }

    public static void registerModBlocks() {
        Constants.info("Registering blocks.");
    }

    public static Block getSleepBarrier() {
        if (sleepBarrier == null) {
            throw new IllegalStateException("Sleep barrier has not been registered yet.");
        }
        return sleepBarrier;
    }

    private static Block createSleepBarrier() {
        return new SleepBarrier(
                BlockBehaviour.Properties.of()
                        .strength(-1.0F, 3600000.0F)
                        .noLootTable()
                        .noTerrainParticles()
                        .noOcclusion()
        );
    }
}
