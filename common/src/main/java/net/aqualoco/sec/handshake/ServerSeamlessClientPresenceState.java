package net.aqualoco.sec.handshake;

public enum ServerSeamlessClientPresenceState {
    PENDING,
    CONFIRMED,
    MISSING_CLIENT_MOD,
    PROTOCOL_MISMATCH,
    VERSION_MISMATCH,
    DISCONNECTED,
    INTERNAL_ERROR
}
