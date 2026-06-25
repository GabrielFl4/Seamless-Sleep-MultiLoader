package net.aqualoco.sec.handshake;

import java.util.UUID;

public record HandshakeFailureRecord(UUID playerId,
                                     String playerName,
                                     ServerSeamlessClientPresenceState state,
                                     String reason,
                                     long timestampMillis) {
}
