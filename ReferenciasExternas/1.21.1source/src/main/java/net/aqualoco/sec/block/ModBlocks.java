package net.aqualoco.sec.block;

import net.aqualoco.sec.AquaSec;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModBlocks {

    public static final Block SLEEP_BARRIER = registerSleepBarrier("sleep_barrier");

    private ModBlocks() {}

    private static Block registerSleepBarrier(String name) {
        Identifier id = Identifier.of(AquaSec.MOD_ID, name);

        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);

        AbstractBlock.Settings settings = AbstractBlock.Settings.create()
                .strength(-1.0F, 3600000.0F)
                .dropsNothing()
                .noBlockBreakParticles()
                .nonOpaque();

        Block block = new Block(settings);

        Registry.register(Registries.BLOCK, blockKey, block);

        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);

        Item.Settings itemSettings = new Item.Settings();

        Registry.register(
                Registries.ITEM,
                itemKey,
                new BlockItem(block, itemSettings)
        );

        return block;
    }

    public static void registerModBlocks() {
        AquaSec.LOGGER.info("Registrando blocos do Seamless Sleep");
    }
}
