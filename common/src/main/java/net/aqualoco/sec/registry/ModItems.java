package net.aqualoco.sec.registry;

import net.aqualoco.sec.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import java.util.function.BiConsumer;

// Registers item forms for the mod blocks.
public final class ModItems {

    private static Item sleepBarrierItem;

    private ModItems() {
    }

    public static void register(BiConsumer<Item, ResourceLocation> consumer) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sleep_barrier");
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        sleepBarrierItem = new BlockItem(
                ModBlocks.getSleepBarrier(),
                new Item.Properties()
                        .setId(key)
                        .useBlockDescriptionPrefix()
        );
        consumer.accept(sleepBarrierItem, id);
    }

    public static Item getSleepBarrierItem() {
        if (sleepBarrierItem == null) {
            throw new IllegalStateException("Sleep barrier item ainda nao foi registrado.");
        }
        return sleepBarrierItem;
    }
}
