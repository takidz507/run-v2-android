package com.takidz.runv2.data

/**
 * Internal network constants.
 * STRICT RULE: Never expose HOMESERVER_BASE_URL, SERVER_DOMAIN, "Matrix", "server",
 * or "protocol" in any user-facing string. The user only sees "@username" and "Run account".
 */
internal object RunConfig {
    const val HOMESERVER_BASE_URL = "https://matrix.tchncs.de"
    const val SERVER_DOMAIN = "tchncs.de"
    const val ACCOUNT_DATA_KEY_BACKUP_EVENT = "m.runv2.key_backup.v2"
    const val ROOM_EVENT_ENCRYPTED = "m.room.encrypted"
    const val ROOM_ENCRYPTION_ALGORITHM = "m.megolm.v1.aes-sha2"
    const val APP_SCHEME = "run"

    /**
     * Converts a raw user input (e.g. "taki" or "@taki") into the full internal identifier
     * required by the backend API ("@taki:tchncs.de").
     */
    fun toInternalUserId(handleOrLocalpart: String): String {
        val clean = handleOrLocalpart.trim().removePrefix("@")
        val localpart = clean.substringBefore(":")
        return "@$localpart:$SERVER_DOMAIN"
    }

    /**
     * Strips the internal domain and returns ONLY "@username" for display in the Run UI.
     */
    fun toPublicHandle(internalUserId: String): String {
        val localpart = internalUserId.trim().removePrefix("@").substringBefore(":")
        return if (localpart.isEmpty()) "@user" else "@$localpart"
    }

    /**
     * Extracts the raw localpart without '@' or ':domain'.
     */
    fun toLocalpart(handleOrUserId: String): String {
        return handleOrUserId.trim().removePrefix("@").substringBefore(":").lowercase()
    }

    /**
     * Converts a room code (e.g. "PHY-D5" or "run://join/PHY-D5") into an internal alias
     * "#phy-d5:tchncs.de" or returns a room ID if it starts with '!'.
     */
    fun toInternalRoomAliasOrId(codeOrLink: String): String {
        val cleaned = codeOrLink.trim()
            .removePrefix("run://join/")
            .removePrefix("run://friend/")
            .trim()
        if (cleaned.startsWith("!") && cleaned.contains(":")) {
            return cleaned
        }
        val aliasLocal = cleaned.removePrefix("#").substringBefore(":").lowercase()
        return "#$aliasLocal:$SERVER_DOMAIN"
    }

    /**
     * Formats an internal alias "#code:tchncs.de" or roomId into a clean uppercase room code
     * (e.g. "PHY-D5") with zero domain exposure.
     */
    fun toPublicRoomCode(aliasOrRoomId: String?): String {
        if (aliasOrRoomId.isNullOrBlank()) return ""
        val raw = aliasOrRoomId.trim()
            .removePrefix("#")
            .removePrefix("!")
            .substringBefore(":")
        return raw.uppercase()
    }

    /**
     * Builds the deep link `run://join/<code>` for sharing a room.
     */
    fun buildRoomInviteLink(roomCode: String): String {
        return "run://join/${roomCode.trim()}"
    }

    /**
     * Builds the deep link `run://friend/@username` for sharing a user profile.
     */
    fun buildFriendInviteLink(publicHandle: String): String {
        val normalized = toPublicHandle(publicHandle)
        return "run://friend/$normalized"
    }
}
