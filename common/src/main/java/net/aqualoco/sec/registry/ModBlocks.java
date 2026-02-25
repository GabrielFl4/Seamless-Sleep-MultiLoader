package net.aqualoco.sec.registry;

import net.aqualoco.sec.Constants;
import net.aqualoco.sec.block.SleepBarrier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.BiConsumer;

// Registers the custom blocks shared by all loaders.
public class ModBlocks {

    private static final Identifier SLEEP_BARRIER_ID =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sleep_barrier");
    private static final ResourceKey<Block> SLEEP_BARRIER_KEY =
            ResourceKey.create(Registries.BLOCK, SLEEP_BARRIER_ID);

    private static Block sleepBarrier;

    public static void register(BiConsumer<Block, Identifier> consumer) {
        sleepBarrier = createSleepBarrier(SLEEP_BARRIER_KEY);
        consumer.accept(sleepBarrier, SLEEP_BARRIER_ID);
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

    private static Block createSleepBarrier(ResourceKey<Block> key) {
        return new SleepBarrier(
                BlockBehaviour.Properties.of()
                        .strength(-1.0F, 3600000.0F)
                        .noLootTable()
                        .noTerrainParticles()
                        .noOcclusion()
                        .setId(key)
        );
    }
}
