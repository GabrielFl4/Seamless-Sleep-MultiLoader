package net.aqualoco.sec.block;

import net.aqualoco.sec.AquaSec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {

    public static final Block SLEEP_BARRIER = registerSleepBarrier("sleep_barrier");

    private ModBlocks() {}

    private static Block registerSleepBarrier(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AquaSec.MOD_ID, name);

        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);

        BlockBehaviour.Properties settings = BlockBehaviour.Properties.of()
                .strength(-1.0F, 3600000.0F)
                .noLootTable()
                .noTerrainParticles()
                .noOcclusion();

        Block block = new Block(settings);

        Registry.register(BuiltInRegistries.BLOCK, blockKey, block);

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);

        Item.Properties itemSettings = new Item.Properties();

        Registry.register(
                BuiltInRegistries.ITEM,
                itemKey,
                new BlockItem(block, itemSettings)
        );

        return block;
    }

    public static void registerModBlocks() {
        AquaSec.LOGGER.info("Registrando blocos do Seamless Sleep");
    }
}
