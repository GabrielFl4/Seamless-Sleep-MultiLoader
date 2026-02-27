package net.aqualoco.sec.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

// Cross-loader packet contract used by the 1.20.1 networking bridges.
public interface SeamlessSleepPacket {

    ResourceLocation id();

    void write(FriendlyByteBuf buf);
}
