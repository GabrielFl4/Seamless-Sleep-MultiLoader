package net.aqualoco.sec.registry;

import net.aqualoco.sec.Constants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import java.util.function.BiConsumer;

public final class ModItems {

    private static Item sleepBarrierItem;

    private ModItems() {
    }

    public static void register(BiConsumer<Item, ResourceLocation> consumer) {
        sleepBarrierItem = new BlockItem(ModBlocks.getSleepBarrier(), new Item.Properties());
        consumer.accept(sleepBarrierItem, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sleep_barrier"));
    }

    public static Item getSleepBarrierItem() {
        if (sleepBarrierItem == null) {
            throw new IllegalStateException("Sleep barrier item ainda nao foi registrado.");
        }
        return sleepBarrierItem;
    }
}
