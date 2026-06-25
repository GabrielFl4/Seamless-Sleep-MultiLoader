package net.aqualoco.sec.handshake;

import java.util.UUID;

public record SeamlessClientPresenceSnapshot(UUID playerId,
                                             String playerName,
                                             ServerSeamlessClientPresenceState state,
                                             int protocolVersion,
                                             String modVersion,
                                             int featureFlags,
                                             long ageMillis) {
}
