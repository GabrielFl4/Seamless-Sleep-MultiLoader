package net.aqualoco.sec.network;

import net.aqualoco.sec.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record ServerConfigUpdateC2SPayload(int baseRevision,
                                           Map<ServerConfigField, String> values) implements CustomPacketPayload {
    private static final int MAX_FIELD_VALUE_LENGTH = 256;

    public static final Type<ServerConfigUpdateC2SPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "server_config_update"));

    public static final StreamCodec<FriendlyByteBuf, ServerConfigUpdateC2SPayload> CODEC =
            CustomPacketPayload.codec(ServerConfigUpdateC2SPayload::write, ServerConfigUpdateC2SPayload::read);

    public ServerConfigUpdateC2SPayload {
        EnumMap<ServerConfigField, String> copied = new EnumMap<>(ServerConfigField.class);
        if (values != null) {
            for (Map.Entry<ServerConfigField, String> entry : values.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    copied.put(entry.getKey(), entry.getValue());
                }
            }
        }
        values = Collections.unmodifiableMap(copied);
    }

    private static void write(ServerConfigUpdateC2SPayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.baseRevision());
        buf.writeVarInt(payload.values().size());
        for (Map.Entry<ServerConfigField, String> entry : payload.values().entrySet()) {
            buf.writeVarInt(entry.getKey().ordinal());
            buf.writeUtf(entry.getValue(), MAX_FIELD_VALUE_LENGTH);
        }
    }

    private static ServerConfigUpdateC2SPayload read(FriendlyByteBuf buf) {
        int baseRevision = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > ServerConfigField.values().length) {
            throw new IllegalArgumentException("Invalid server config patch field count: " + count);
        }

        EnumMap<ServerConfigField, String> values = new EnumMap<>(ServerConfigField.class);
        ServerConfigField[] fields = ServerConfigField.values();
        for (int i = 0; i < count; i++) {
            int ordinal = buf.readVarInt();
            if (ordinal < 0 || ordinal >= fields.length) {
                throw new IllegalArgumentException("Invalid server config patch field id: " + ordinal);
            }
            values.put(fields[ordinal], buf.readUtf(MAX_FIELD_VALUE_LENGTH));
        }
        return new ServerConfigUpdateC2SPayload(baseRevision, values);
    }

    @Override
    public Type<ServerConfigUpdateC2SPayload> type() {
        return ID;
    }
}
