package com.takidz.runv2.data

enum class ThemeMode {
    WARM_NIGHT,
    WARM_PAPER,
    SYSTEM
}

enum class CallQualityMode(val labelAr: String, val maxWidth: Int, val maxHeight: Int, val maxFps: Int, val maxBitrateKbps: Int) {
    DEFAULT_480P("480p قياسي (افتراضي)", 854, 480, 24, 650),
    DATA_SAVER("توفير البيانات (Data saver)", 426, 240, 15, 250),
    AUDIO_ONLY("صوت فقط (بدون فيديو)", 0, 0, 0, 64)
}

enum class PresenceState {
    ONLINE,
    UNAVAILABLE,
    OFFLINE
}

data class RunAccount(
    val internalUserId: String,
    val publicHandle: String,
    val displayName: String,
    val deviceId: String,
    val isPinConfigured: Boolean
)

data class RoomSummary(
    val roomId: String,
    val name: String,
    val topic: String,
    val roomCode: String,
    val isDirect: Boolean,
    val directPeerUserId: String?,
    val memberCount: Int,
    val onlineCount: Int,
    val lastMessageSender: String?,
    val lastMessageBody: String,
    val lastMessageTimestamp: Long,
    val unreadCount: Int,
    val isEncrypted: Boolean = false,
    val avatarSeedColor: Long,
    val peerPresence: PresenceState = PresenceState.OFFLINE
)

data class ChatMessage(
    val eventId: String,
    val roomId: String,
    val senderUserId: String,
    val senderHandle: String,
    val senderDisplayName: String,
    val body: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val isEncrypted: Boolean = false,
    val decryptionFailed: Boolean = false,
    val isDelivered: Boolean = true
)

data class RoomMember(
    val userId: String,
    val publicHandle: String,
    val displayName: String,
    val roleLabel: String,
    val membership: String, // "join" or "invite"
    val presence: PresenceState = PresenceState.OFFLINE,
    val avatarSeedColor: Long
)

data class UserSearchResult(
    val internalUserId: String,
    val publicHandle: String,
    val displayName: String,
    val presence: PresenceState,
    val statusText: String,
    val avatarSeedColor: Long,
    val relationState: FriendRelationState = FriendRelationState.NONE
)

enum class FriendRelationState {
    NONE,
    OUTGOING_PENDING,
    INCOMING_PENDING,
    FRIEND
}

data class FriendRequestItem(
    val roomId: String,
    val internalUserId: String,
    val publicHandle: String,
    val displayName: String,
    val isIncoming: Boolean,
    val presence: PresenceState = PresenceState.OFFLINE,
    val statusText: String = "",
    val avatarSeedColor: Long
)

data class PublicRoomResult(
    val roomId: String,
    val name: String,
    val topic: String,
    val roomCode: String,
    val memberCount: Int,
    val isJoined: Boolean,
    val avatarSeedColor: Long
)

enum class CallDirection {
    OUTGOING,
    INCOMING,
    MISSED
}

data class CallLogEntry(
    val id: String,
    val roomId: String,
    val peerName: String,
    val isVideo: Boolean,
    val direction: CallDirection,
    val durationSeconds: Int,
    val timestamp: Long,
    val avatarSeedColor: Long
)

enum class CallPhase {
    IDLE,
    OUTGOING_RINGING,
    INCOMING_RINGING,
    CONNECTING,
    CONNECTED,
    ENDED
}

data class ActiveCallState(
    val callId: String = "",
    val roomId: String = "",
    val peerName: String = "",
    val peerHandle: String = "",
    val isVideo: Boolean = true,
    val phase: CallPhase = CallPhase.IDLE,
    val durationSeconds: Int = 0,
    val isMicMuted: Boolean = false,
    val isCameraEnabled: Boolean = true,
    val isSpeakerOn: Boolean = true,
    val remoteOfferSdp: String? = null,
    val avatarSeedColor: Long = 0xFF8A6D3BL
)

data class TurnCredentials(
    val uris: List<String>,
    val username: String,
    val password: String,
    val ttlSeconds: Int
)

data class SignalingEvent(
    val type: String, // m.call.invite, m.call.answer, m.call.candidates, m.call.hangup
    val roomId: String,
    val senderUserId: String,
    val callId: String,
    val sdp: String? = null,
    val candidates: List<IceCandidatePayload> = emptyList(),
    val isVideo: Boolean = true
)

data class IceCandidatePayload(
    val sdpMid: String,
    val sdpMLineIndex: Int,
    val candidate: String
)

data class SyncResult(
    val nextBatch: String,
    val rooms: List<RoomSummary>,
    val incomingFriendInvites: List<FriendRequestItem>,
    val outgoingFriendInvites: List<FriendRequestItem>,
    val friends: List<UserSearchResult>,
    val newMessagesByRoom: Map<String, List<ChatMessage>>,
    val signalingEvents: List<SignalingEvent>
)

object AvatarPalette {
    private val colors = listOf(
        0xFF8A6D3BL,
        0xFF4E7A51L,
        0xFF7A5C8FL,
        0xFF3F6D8EL,
        0xFFA0526BL,
        0xFFD95D28L
    )

    fun colorFor(seed: String): Long {
        val hash = seed.fold(0) { acc, c -> acc * 31 + c.code }
        val idx = (hash and 0x7FFFFFFF) % colors.size
        return colors[idx]
    }
}
