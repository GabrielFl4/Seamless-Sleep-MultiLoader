package net.aqualoco.sec.registry;

import net.aqualoco.sec.Constants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import java.util.function.BiConsumer;

public final class ModItems {

    public static final Item SLEEP_BARRIER_ITEM =
            new BlockItem(ModBlocks.SLEEP_BARRIER, new Item.Properties());

    private ModItems() {
    }

    public static void register(BiConsumer<Item, ResourceLocation> consumer) {
        consumer.accept(SLEEP_BARRIER_ITEM, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sleep_barrier"));
    }
}
