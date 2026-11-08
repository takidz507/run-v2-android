package com.takidz.runv2.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.olm.OlmAccount
import org.matrix.olm.OlmInboundGroupSession
import org.matrix.olm.OlmManager
import org.matrix.olm.OlmMessage
import org.matrix.olm.OlmOutboundGroupSession
import org.matrix.olm.OlmSession
import org.matrix.olm.OlmUtility
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

/**
 * Real Matrix End-to-End Encryption (Olm/Megolm) engine built on the official
 * Matrix `org.matrix.android:olm-sdk` (`libolm` JNI bindings).
 *
 * Implements standard Matrix E2EE specifications interoperable with Element:
 * - Device identity keys (`curve25519` & `ed25519`) and one-time keys (`signed_curve25519`)
 * - Matrix Canonical JSON serialization and Ed25519 signing/verification (`OlmAccount.signMessage`, `OlmUtility.verifyEd25519Signature`)
 * - 1-to-1 Olm channels (`m.olm.v1.curve25519-aes-sha2`) for exchanging `m.room_key` to-device events
 * - Outbound & Inbound Megolm group sessions (`m.megolm.v1.aes-sha2`) for `m.room.encrypted` timeline messages
 * - Standard Megolm session export/import (`OlmInboundGroupSession.export` / `importSession`) for 6-digit PIN key backup
 */
class OlmCryptoManager(private val sessionStore: SecureSessionStore) {

    // Ensures native libolm.so is loaded on instantiation
    @Suppress("unused")
    private val olmManager = OlmManager()

    private var olmAccount: OlmAccount? = null

    data class MegolmDecryptionOutcome(
        val plaintextBody: String,
        val decryptionFailed: Boolean,
        val senderDeviceVerified: Boolean,
        val sessionId: String = "",
        val senderKey: String = ""
    )

    data class OutboundRoomKeyShareData(
        val roomId: String,
        val sessionId: String,
        val sessionKey: String,
        val chainIndex: Int
    )

    // =========================================================================
    // 1. OLM ACCOUNT & DEVICE IDENTITY KEYS
    // =========================================================================

    @Synchronized
    fun getOrCreateOlmAccount(): OlmAccount {
        olmAccount?.let { return it }

        val savedB64 = sessionStore.getSerializedOlmAccount()
        if (!savedB64.isNullOrBlank()) {
            val restored = deserializeOlmObject<OlmAccount>(savedB64)
            if (restored != null) {
                olmAccount = restored
                return restored
            }
        }

        val fresh = OlmAccount()
        olmAccount = fresh
        persistOlmAccount(fresh)
        return fresh
    }

    @Synchronized
    private fun persistOlmAccount(account: OlmAccount) {
        val b64 = serializeOlmObject(account)
        if (b64.isNotBlank()) {
            sessionStore.saveSerializedOlmAccount(b64)
        }
    }

    @Synchronized
    fun getDeviceCurve25519Key(): String {
        val keys = getOrCreateOlmAccount().identityKeys()
        return keys[OlmAccount.JSON_KEY_IDENTITY_KEY].orEmpty()
    }

    @Synchronized
    fun getDeviceEd25519Key(): String {
        val keys = getOrCreateOlmAccount().identityKeys()
        return keys[OlmAccount.JSON_KEY_FINGER_PRINT_KEY].orEmpty()
    }

    @Synchronized
    fun getFormattedDeviceFingerprint(): String {
        return getDeviceEd25519Key().chunked(4).joinToString(" ")
    }

    @Synchronized
    fun resetCryptoState() {
        runCatching { olmAccount?.releaseAccount() }
        olmAccount = null
    }

    // =========================================================================
    // 2. MATRIX CANONICAL JSON & ED25519 SIGNATURES
    // =========================================================================

    /**
     * Converts any JSONObject / JSONArray / primitive into Matrix Canonical JSON
     * (keys sorted lexicographically by UTF-8 code units, zero insignificant whitespace).
     */
    fun toCanonicalJson(value: Any?): String {
        return when (value) {
            null, JSONObject.NULL -> "null"
            is JSONObject -> {
                val sortedKeys = mutableListOf<String>()
                val iter = value.keys()
                while (iter.hasNext()) {
                    sortedKeys.add(iter.next())
                }
                sortedKeys.sort()
                buildString {
                    append("{")
                    sortedKeys.forEachIndexed { index, key ->
                        if (index > 0) append(",")
                        append(JSONObject.quote(key))
                        append(":")
                        append(toCanonicalJson(value.opt(key)))
                    }
                    append("}")
                }
            }
            is JSONArray -> {
                buildString {
                    append("[")
                    for (i in 0 until value.length()) {
                        if (i > 0) append(",")
                        append(toCanonicalJson(value.opt(i)))
                    }
                    append("]")
                }
            }
            is String -> JSONObject.quote(value)
            is Boolean -> value.toString()
            is Number -> value.toString()
            else -> JSONObject.quote(value.toString())
        }
    }

    /**
     * Signs a Matrix JSON object according to the Matrix Signing JSON specification:
     * removes `signatures` and `unsigned`, encodes to Canonical JSON, signs with the
     * device's Ed25519 key via `OlmAccount.signMessage`, and attaches `signatures`.
     */
    @Synchronized
    fun signMatrixJson(
        target: JSONObject,
        userId: String,
        deviceId: String
    ): JSONObject {
        val account = getOrCreateOlmAccount()
        val copyForSigning = JSONObject(target.toString())
        val unsignedBackup = copyForSigning.optJSONObject("unsigned")
        copyForSigning.remove("signatures")
        copyForSigning.remove("unsigned")

        val canonical = toCanonicalJson(copyForSigning)
        val signatureB64 = account.signMessage(canonical)

        val signaturesObj = target.optJSONObject("signatures") ?: JSONObject()
        val userSigs = signaturesObj.optJSONObject(userId) ?: JSONObject()
        userSigs.put("ed25519:$deviceId", signatureB64)
        signaturesObj.put(userId, userSigs)

        copyForSigning.put("signatures", signaturesObj)
        if (unsignedBackup != null) {
            copyForSigning.put("unsigned", unsignedBackup)
        }
        return copyForSigning
    }

    /**
     * Verifies an Ed25519 signature on a Matrix JSON object (such as `device_keys` or
     * `signed_curve25519` one-time keys) using `OlmUtility.verifyEd25519Signature`.
     */
    fun verifyMatrixJsonSignature(
        signedJson: JSONObject,
        signerUserId: String,
        signerDeviceId: String,
        ed25519PublicKey: String
    ): Boolean {
        if (ed25519PublicKey.isBlank()) return false
        val signature = signedJson
            .optJSONObject("signatures")
            ?.optJSONObject(signerUserId)
            ?.optString("ed25519:$signerDeviceId", "")
            .orEmpty()
        if (signature.isBlank()) return false

        val copyToVerify = JSONObject(signedJson.toString())
        copyToVerify.remove("signatures")
        copyToVerify.remove("unsigned")
        val canonical = toCanonicalJson(copyToVerify)

        val util = OlmUtility()
        return try {
            util.verifyEd25519Signature(signature, ed25519PublicKey, canonical)
            true
        } catch (_: Exception) {
            false
        } finally {
            runCatching { util.releaseUtility() }
        }
    }

    // =========================================================================
    // 3. /keys/upload PAYLOAD GENERATION (Device Keys & Signed One-Time Keys)
    // =========================================================================

    @Synchronized
    fun buildKeysUploadPayload(
        userId: String,
        deviceId: String,
        currentServerOneTimeKeyCount: Int,
        forceIncludeDeviceKeys: Boolean = false
    ): JSONObject? {
        val account = getOrCreateOlmAccount()
        val includeDeviceKeys = forceIncludeDeviceKeys || !sessionStore.areDeviceKeysUploaded()
        val maxKeys = account.maxOneTimeKeys().toInt().coerceIn(20, 100)
        val targetPool = maxKeys / 2
        val neededKeys = (targetPool - currentServerOneTimeKeyCount).coerceAtLeast(0)

        if (!includeDeviceKeys && neededKeys <= 0) {
            return null
        }

        val payload = JSONObject()

        if (includeDeviceKeys) {
            val curveKey = getDeviceCurve25519Key()
            val edKey = getDeviceEd25519Key()
            val unsignedDeviceKeys = JSONObject().apply {
                put("user_id", userId)
                put("device_id", deviceId)
                put(
                    "algorithms",
                    JSONArray()
                        .put(RunConfig.OLM_ALGORITHM)
                        .put(RunConfig.ROOM_ENCRYPTION_ALGORITHM)
                )
                put(
                    "keys",
                    JSONObject().apply {
                        put("curve25519:$deviceId", curveKey)
                        put("ed25519:$deviceId", edKey)
                    }
                )
            }
            val signedDeviceKeys = signMatrixJson(unsignedDeviceKeys, userId, deviceId)
            payload.put("device_keys", signedDeviceKeys)
        }

        if (neededKeys > 0) {
            account.generateOneTimeKeys(neededKeys)
            persistOlmAccount(account)
        }

        val unpublishedOtkMap = account.oneTimeKeys()[OlmAccount.JSON_KEY_ONE_TIME_KEY].orEmpty()
        if (unpublishedOtkMap.isNotEmpty()) {
            val oneTimeKeysJson = JSONObject()
            for ((keyId, pubKeyB64) in unpublishedOtkMap) {
                val otkObj = JSONObject().apply {
                    put("key", pubKeyB64)
                }
                val signedOtk = signMatrixJson(otkObj, userId, deviceId)
                oneTimeKeysJson.put("signed_curve25519:$keyId", signedOtk)
            }
            payload.put("one_time_keys", oneTimeKeysJson)
        }

        if (!payload.has("device_keys") && !payload.has("one_time_keys")) {
            return null
        }
        return payload
    }

    @Synchronized
    fun onKeysUploadSuccess(uploadedDeviceKeys: Boolean, uploadedOneTimeKeys: Boolean) {
        if (uploadedDeviceKeys) {
            sessionStore.setDeviceKeysUploaded(true)
        }
        if (uploadedOneTimeKeys) {
            val account = getOrCreateOlmAccount()
            runCatching { account.markOneTimeKeysAsPublished() }
            persistOlmAccount(account)
        }
    }

    // =========================================================================
    // 4. DEVICE TRACKING & VERIFICATION (/keys/query)
    // =========================================================================

    @Synchronized
    fun processKeysQueryResponse(response: JSONObject): List<DeviceIdentity> {
        val deviceKeysRoot = response.optJSONObject("device_keys") ?: return emptyList()
        val storedRoot = JSONObject(sessionStore.getKnownDevicesJson())
        val updatedList = mutableListOf<DeviceIdentity>()

        val userIter = deviceKeysRoot.keys()
        while (userIter.hasNext()) {
            val userId = userIter.next()
            val userDevicesObj = deviceKeysRoot.optJSONObject(userId) ?: continue
            val storedUserObj = storedRoot.optJSONObject(userId) ?: JSONObject()

            val devIter = userDevicesObj.keys()
            while (devIter.hasNext()) {
                val deviceId = devIter.next()
                val devJson = userDevicesObj.optJSONObject(deviceId) ?: continue

                if (devJson.optString("user_id") != userId || devJson.optString("device_id") != deviceId) {
                    continue
                }

                val keysObj = devJson.optJSONObject("keys") ?: continue
                val curveKey = keysObj.optString("curve25519:$deviceId")
                val edKey = keysObj.optString("ed25519:$deviceId")
                if (curveKey.isBlank() || edKey.isBlank()) continue

                val sigValid = verifyMatrixJsonSignature(devJson, userId, deviceId, edKey)
                if (!sigValid) continue

                val displayName = devJson
                    .optJSONObject("unsigned")
                    ?.optString("device_display_name", "")
                    .orEmpty()

                val previousEntry = storedUserObj.optJSONObject(deviceId)
                val manuallyVerified = previousEntry?.optBoolean("manually_verified", false) ?: false

                // Prevent key substitution for an already-known device ID
                val prevEdKey = previousEntry?.optString("ed25519", "").orEmpty()
                if (prevEdKey.isNotBlank() && prevEdKey != edKey) {
                    continue
                }

                val entryJson = JSONObject().apply {
                    put("user_id", userId)
                    put("device_id", deviceId)
                    put("curve25519", curveKey)
                    put("ed25519", edKey)
                    put("display_name", displayName)
                    put("signature_verified", true)
                    put("manually_verified", manuallyVerified)
                }
                storedUserObj.put(deviceId, entryJson)

                updatedList.add(
                    DeviceIdentity(
                        userId = userId,
                        deviceId = deviceId,
                        curve25519Key = curveKey,
                        ed25519Key = edKey,
                        displayName = displayName,
                        signatureVerified = true,
                        manuallyVerified = manuallyVerified
                    )
                )
            }
            storedRoot.put(userId, storedUserObj)
        }

        sessionStore.saveKnownDevicesJson(storedRoot.toString())
        return updatedList
    }

    @Synchronized
    fun getKnownDevicesForUsers(userIds: Collection<String>): List<DeviceIdentity> {
        val storedRoot = JSONObject(sessionStore.getKnownDevicesJson())
        val result = mutableListOf<DeviceIdentity>()
        for (userId in userIds) {
            val userObj = storedRoot.optJSONObject(userId) ?: continue
            val devIter = userObj.keys()
            while (devIter.hasNext()) {
                val deviceId = devIter.next()
                val d = userObj.optJSONObject(deviceId) ?: continue
                result.add(
                    DeviceIdentity(
                        userId = userId,
                        deviceId = deviceId,
                        curve25519Key = d.optString("curve25519"),
                        ed25519Key = d.optString("ed25519"),
                        displayName = d.optString("display_name"),
                        signatureVerified = d.optBoolean("signature_verified", false),
                        manuallyVerified = d.optBoolean("manually_verified", false)
                    )
                )
            }
        }
        return result
    }

    @Synchronized
    fun setDeviceManuallyVerified(userId: String, deviceId: String, verified: Boolean) {
        val storedRoot = JSONObject(sessionStore.getKnownDevicesJson())
        val userObj = storedRoot.optJSONObject(userId) ?: return
        val devObj = userObj.optJSONObject(deviceId) ?: return
        devObj.put("manually_verified", verified)
        userObj.put(deviceId, devObj)
        storedRoot.put(userId, userObj)
        sessionStore.saveKnownDevicesJson(storedRoot.toString())
    }

    @Synchronized
    fun isSenderCurve25519Verified(senderUserId: String, senderCurve25519Key: String): Boolean {
        if (senderCurve25519Key.isBlank()) return false
        if (senderCurve25519Key == getDeviceCurve25519Key()) return true
        val devices = getKnownDevicesForUsers(listOf(senderUserId))
        return devices.any { it.curve25519Key == senderCurve25519Key && it.signatureVerified }
    }

    // =========================================================================
    // 5. OLM 1-TO-1 SESSIONS & TO-DEVICE ROOM KEY EXCHANGE
    // =========================================================================

    @Synchronized
    fun hasOlmSessionWithDevice(theirCurve25519Key: String): Boolean {
        val vault = JSONObject(sessionStore.getOlmSessionsJson())
        val arr = vault.optJSONArray(theirCurve25519Key)
        return arr != null && arr.length() > 0
    }

    @Synchronized
    private fun getOlmSessionsForDevice(theirCurve25519Key: String): MutableList<OlmSession> {
        val vault = JSONObject(sessionStore.getOlmSessionsJson())
        val arr = vault.optJSONArray(theirCurve25519Key) ?: return mutableListOf()
        val list = mutableListOf<OlmSession>()
        for (i in 0 until arr.length()) {
            val b64 = arr.optString(i)
            val s = deserializeOlmObject<OlmSession>(b64)
            if (s != null) list.add(s)
        }
        return list
    }

    @Synchronized
    private fun saveOlmSessionForDevice(theirCurve25519Key: String, session: OlmSession) {
        val vault = JSONObject(sessionStore.getOlmSessionsJson())
        val existingList = getOlmSessionsForDevice(theirCurve25519Key)
        val targetId = runCatching { session.sessionIdentifier() }.getOrDefault("")
        val filtered = existingList.filterNot {
            runCatching { it.sessionIdentifier() }.getOrDefault("") == targetId
        }
        val updated = listOf(session) + filtered.take(3)
        val arr = JSONArray()
        updated.forEach { s ->
            val b64 = serializeOlmObject(s)
            if (b64.isNotBlank()) arr.put(b64)
        }
        vault.put(theirCurve25519Key, arr)
        sessionStore.saveOlmSessionsJson(vault.toString())
    }

    /**
     * Processes `/keys/claim` response and initializes outbound 1-to-1 Olm sessions
     * for each claimed `signed_curve25519` one-time key after verifying its Ed25519 signature.
     */
    @Synchronized
    fun processClaimedOneTimeKeys(
        claimResponse: JSONObject,
        targetDevicesByUserAndId: Map<Pair<String, String>, DeviceIdentity>
    ) {
        val oneTimeKeysRoot = claimResponse.optJSONObject("one_time_keys") ?: return
        val account = getOrCreateOlmAccount()

        val userIter = oneTimeKeysRoot.keys()
        while (userIter.hasNext()) {
            val userId = userIter.next()
            val devicesObj = oneTimeKeysRoot.optJSONObject(userId) ?: continue
            val devIter = devicesObj.keys()
            while (devIter.hasNext()) {
                val deviceId = devIter.next()
                val deviceIdentity = targetDevicesByUserAndId[Pair(userId, deviceId)] ?: continue
                val keyContainer = devicesObj.optJSONObject(deviceId) ?: continue

                val keyIter = keyContainer.keys()
                while (keyIter.hasNext()) {
                    val keyFullId = keyIter.next()
                    if (!keyFullId.startsWith("signed_curve25519:")) continue
                    val signedOtkObj = keyContainer.optJSONObject(keyFullId) ?: continue
                    val oneTimeKeyPub = signedOtkObj.optString("key")
                    if (oneTimeKeyPub.isBlank()) continue

                    val sigOk = verifyMatrixJsonSignature(
                        signedJson = signedOtkObj,
                        signerUserId = userId,
                        signerDeviceId = deviceId,
                        ed25519PublicKey = deviceIdentity.ed25519Key
                    )
                    if (!sigOk) continue

                    runCatching {
                        val olmSession = OlmSession()
                        olmSession.initOutboundSession(
                            account,
                            deviceIdentity.curve25519Key,
                            oneTimeKeyPub
                        )
                        saveOlmSessionForDevice(deviceIdentity.curve25519Key, olmSession)
                    }
                }
            }
        }
    }

    /**
     * Encrypts a Megolm `m.room_key` event via Olm (`m.olm.v1.curve25519-aes-sha2`)
     * for a specific recipient device. Interoperable with Element.
     */
    @Synchronized
    fun buildOlmEncryptedRoomKeyToDeviceEvent(
        myUserId: String,
        myDeviceId: String,
        recipientDevice: DeviceIdentity,
        shareData: OutboundRoomKeyShareData
    ): JSONObject? {
        val sessions = getOlmSessionsForDevice(recipientDevice.curve25519Key)
        val activeSession = sessions.firstOrNull() ?: return null

        val olmPlaintextEvent = JSONObject().apply {
            put("sender", myUserId)
            put("sender_device", myDeviceId)
            put(
                "keys",
                JSONObject().apply {
                    put("ed25519", getDeviceEd25519Key())
                }
            )
            put("recipient", recipientDevice.userId)
            put(
                "recipient_keys",
                JSONObject().apply {
                    put("ed25519", recipientDevice.ed25519Key)
                }
            )
            put("type", "m.room_key")
            put(
                "content",
                JSONObject().apply {
                    put("algorithm", RunConfig.ROOM_ENCRYPTION_ALGORITHM)
                    put("room_id", shareData.roomId)
                    put("session_id", shareData.sessionId)
                    put("session_key", shareData.sessionKey)
                    put("chain_index", shareData.chainIndex)
                }
            )
        }

        return try {
            val olmMessage = activeSession.encryptMessage(olmPlaintextEvent.toString())
            saveOlmSessionForDevice(recipientDevice.curve25519Key, activeSession)

            JSONObject().apply {
                put("algorithm", RunConfig.OLM_ALGORITHM)
                put("sender_key", getDeviceCurve25519Key())
                put(
                    "ciphertext",
                    JSONObject().apply {
                        put(
                            recipientDevice.curve25519Key,
                            JSONObject().apply {
                                put("type", olmMessage.mType)
                                put("body", olmMessage.mCipherText)
                            }
                        )
                    }
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Processes incoming `to_device` events from `/sync` (such as `m.room.encrypted`
     * containing `m.room_key` or `m.forwarded_room_key` sent by Element or Run).
     */
    @Synchronized
    fun processIncomingToDeviceEvents(toDeviceEvents: JSONArray?, myUserId: String) {
        if (toDeviceEvents == null || toDeviceEvents.length() == 0) return
        val myCurveKey = getDeviceCurve25519Key()
        val account = getOrCreateOlmAccount()

        for (i in 0 until toDeviceEvents.length()) {
            val ev = toDeviceEvents.optJSONObject(i) ?: continue
            val type = ev.optString("type")
            if (type != RunConfig.ROOM_EVENT_ENCRYPTED) continue

            val content = ev.optJSONObject("content") ?: continue
            if (content.optString("algorithm") != RunConfig.OLM_ALGORITHM) continue

            val senderKey = content.optString("sender_key")
            if (senderKey.isBlank()) continue

            val ciphertextContainer = content.optJSONObject("ciphertext") ?: continue
            val myCipherObj = ciphertextContainer.optJSONObject(myCurveKey) ?: continue

            val msgType = myCipherObj.optLong("type", 0L)
            val msgBody = myCipherObj.optString("body")
            if (msgBody.isBlank()) continue

            val decryptedPayloadStr = decryptOlmCiphertext(account, senderKey, msgType, msgBody)
                ?: continue

            runCatching {
                val payload = JSONObject(decryptedPayloadStr)
                val recipient = payload.optString("recipient")
                if (recipient.isNotBlank() && recipient != myUserId) return@runCatching

                val innerType = payload.optString("type")
                val innerContent = payload.optJSONObject("content") ?: return@runCatching
                if (innerContent.optString("algorithm") != RunConfig.ROOM_ENCRYPTION_ALGORITHM) {
                    return@runCatching
                }

                val roomId = innerContent.optString("room_id")
                val sessionId = innerContent.optString("session_id")
                val sessionKey = innerContent.optString("session_key")
                if (roomId.isBlank() || sessionId.isBlank() || sessionKey.isBlank()) {
                    return@runCatching
                }

                when (innerType) {
                    "m.room_key" -> {
                        val inbound = OlmInboundGroupSession(sessionKey)
                        storeInboundMegolmSession(roomId, sessionId, senderKey, inbound)
                    }
                    "m.forwarded_room_key" -> {
                        val inbound = OlmInboundGroupSession.importSession(sessionKey)
                        storeInboundMegolmSession(roomId, sessionId, senderKey, inbound)
                    }
                }
            }
        }
    }

    @Synchronized
    private fun decryptOlmCiphertext(
        account: OlmAccount,
        senderCurveKey: String,
        msgType: Long,
        msgBody: String
    ): String? {
        val olmMsg = OlmMessage().apply {
            mType = msgType
            mCipherText = msgBody
        }

        // 1. Try existing OlmSessions for this senderCurveKey
        val existingSessions = getOlmSessionsForDevice(senderCurveKey)
        for (session in existingSessions) {
            try {
                if (msgType == OlmMessage.MESSAGE_TYPE_PRE_KEY.toLong()) {
                    if (session.matchesInboundSessionFrom(senderCurveKey, msgBody)) {
                        val plain = session.decryptMessage(olmMsg)
                        saveOlmSessionForDevice(senderCurveKey, session)
                        return plain
                    }
                } else {
                    val plain = session.decryptMessage(olmMsg)
                    saveOlmSessionForDevice(senderCurveKey, session)
                    return plain
                }
            } catch (_: Exception) {
                // Continue trying other sessions
            }
        }

        // 2. If Pre-Key message (type 0), create a new inbound OlmSession
        if (msgType == OlmMessage.MESSAGE_TYPE_PRE_KEY.toLong()) {
            return try {
                val newSession = OlmSession()
                newSession.initInboundSessionFrom(account, senderCurveKey, msgBody)
                runCatching { account.removeOneTimeKeys(newSession) }
                persistOlmAccount(account)
                val plain = newSession.decryptMessage(olmMsg)
                saveOlmSessionForDevice(senderCurveKey, newSession)
                plain
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    // =========================================================================
    // 6. MEGOLM GROUP SESSIONS (Encrypting & Decrypting `m.room.encrypted`)
    // =========================================================================

    @Synchronized
    fun getOrCreateOutboundRoomKeyShareData(roomId: String): OutboundRoomKeyShareData {
        val outboundSession = getOrCreateOutboundMegolmSession(roomId)
        val sessionId = outboundSession.sessionIdentifier()
        val sessionKey = outboundSession.sessionKey()
        val messageIndex = outboundSession.messageIndex()

        // Ensure we also have a corresponding InboundMegolmSession for our own messages
        if (getInboundMegolmSession(roomId, sessionId) == null) {
            val selfInbound = OlmInboundGroupSession(sessionKey)
            storeInboundMegolmSession(roomId, sessionId, getDeviceCurve25519Key(), selfInbound)
        }

        return OutboundRoomKeyShareData(
            roomId = roomId,
            sessionId = sessionId,
            sessionKey = sessionKey,
            chainIndex = messageIndex
        )
    }

    @Synchronized
    fun filterDevicesMissingRoomSession(
        roomId: String,
        sessionId: String,
        devices: List<DeviceIdentity>
    ): List<DeviceIdentity> {
        val sharedRoot = JSONObject(sessionStore.getSharedMegolmTargetsJson())
        val sharedArr = sharedRoot.optJSONArray("$roomId|$sessionId") ?: JSONArray()
        val alreadyShared = mutableSetOf<String>()
        for (i in 0 until sharedArr.length()) {
            alreadyShared.add(sharedArr.optString(i))
        }
        return devices.filterNot { d ->
            alreadyShared.contains("${d.userId}|${d.deviceId}")
        }
    }

    @Synchronized
    fun markDevicesReceivedRoomSession(
        roomId: String,
        sessionId: String,
        devices: List<DeviceIdentity>
    ) {
        val sharedRoot = JSONObject(sessionStore.getSharedMegolmTargetsJson())
        val key = "$roomId|$sessionId"
        val sharedArr = sharedRoot.optJSONArray(key) ?: JSONArray()
        val set = mutableSetOf<String>()
        for (i in 0 until sharedArr.length()) {
            set.add(sharedArr.optString(i))
        }
        devices.forEach { d ->
            set.add("${d.userId}|${d.deviceId}")
        }
        val newArr = JSONArray()
        set.forEach { newArr.put(it) }
        sharedRoot.put(key, newArr)
        sessionStore.saveSharedMegolmTargetsJson(sharedRoot.toString())
    }

    /**
     * Encrypts a plaintext chat message into a standard Matrix `m.room.encrypted`
     * (`m.megolm.v1.aes-sha2`) event content object interoperable with Element.
     */
    @Synchronized
    fun encryptMegolmRoomMessage(
        roomId: String,
        myDeviceId: String,
        plaintext: String
    ): JSONObject {
        val outbound = getOrCreateOutboundMegolmSession(roomId)
        val sessionId = outbound.sessionIdentifier()
        val sessionKey = outbound.sessionKey()

        if (getInboundMegolmSession(roomId, sessionId) == null) {
            storeInboundMegolmSession(
                roomId = roomId,
                sessionId = sessionId,
                senderKey = getDeviceCurve25519Key(),
                session = OlmInboundGroupSession(sessionKey)
            )
        }

        val innerPlaintextEvent = JSONObject().apply {
            put("type", "m.room.message")
            put("room_id", roomId)
            put(
                "content",
                JSONObject().apply {
                    put("msgtype", "m.text")
                    put("body", plaintext)
                }
            )
        }

        val ciphertext = outbound.encryptMessage(innerPlaintextEvent.toString())
        saveOutboundMegolmSession(roomId, outbound)

        return JSONObject().apply {
            put("algorithm", RunConfig.ROOM_ENCRYPTION_ALGORITHM)
            put("sender_key", getDeviceCurve25519Key())
            put("ciphertext", ciphertext)
            put("session_id", sessionId)
            put("device_id", myDeviceId)
        }
    }

    /**
     * Decrypts a standard Matrix `m.room.encrypted` (`m.megolm.v1.aes-sha2`) timeline event
     * sent by Element or Run.
     */
    @Synchronized
    fun decryptMegolmRoomMessage(
        roomId: String,
        senderUserId: String,
        content: JSONObject
    ): MegolmDecryptionOutcome {
        val algorithm = content.optString("algorithm")
        val sessionId = content.optString("session_id")
        val senderKey = content.optString("sender_key")
        val ciphertext = content.optString("ciphertext")

        if (algorithm != RunConfig.ROOM_ENCRYPTION_ALGORITHM ||
            sessionId.isBlank() ||
            ciphertext.isBlank()
        ) {
            return MegolmDecryptionOutcome(
                plaintextBody = "🔒 حدث مشفر بصيغة غير مدعومة",
                decryptionFailed = true,
                senderDeviceVerified = false,
                sessionId = sessionId,
                senderKey = senderKey
            )
        }

        val inboundSession = getInboundMegolmSession(roomId, sessionId)
            ?: return MegolmDecryptionOutcome(
                plaintextBody = "🔒 رسالة مشفرة (Megolm) — بانتظار مفتاح الجلسة أو استعادة النسخة برمز PIN",
                decryptionFailed = true,
                senderDeviceVerified = false,
                sessionId = sessionId,
                senderKey = senderKey
            )

        return try {
            val decryptResult = inboundSession.decryptMessage(ciphertext)
            storeInboundMegolmSession(roomId, sessionId, senderKey, inboundSession)

            val innerJson = JSONObject(decryptResult.mDecryptedMessage)
            val innerRoomId = innerJson.optString("room_id")
            if (innerRoomId.isNotBlank() && innerRoomId != roomId) {
                return MegolmDecryptionOutcome(
                    plaintextBody = "🔒 تعذر التحقق من تطابق معرف الغرفة للرسالة المشفرة",
                    decryptionFailed = true,
                    senderDeviceVerified = false,
                    sessionId = sessionId,
                    senderKey = senderKey
                )
            }

            val body = innerJson.optJSONObject("content")?.optString("body").orEmpty()
            val verified = isSenderCurve25519Verified(senderUserId, senderKey)
            MegolmDecryptionOutcome(
                plaintextBody = body.ifBlank { "(رسالة مشفرة فارغة)" },
                decryptionFailed = false,
                senderDeviceVerified = verified,
                sessionId = sessionId,
                senderKey = senderKey
            )
        } catch (_: Exception) {
            MegolmDecryptionOutcome(
                plaintextBody = "🔒 تعذر فك تشفير الرسالة (مفتاح جلسة Megolm غير مطابق)",
                decryptionFailed = true,
                senderDeviceVerified = false,
                sessionId = sessionId,
                senderKey = senderKey
            )
        }
    }

    @Synchronized
    private fun getOrCreateOutboundMegolmSession(roomId: String): OlmOutboundGroupSession {
        val vault = JSONObject(sessionStore.getOutboundMegolmSessionsJson())
        val existingB64 = vault.optString(roomId, "")
        if (existingB64.isNotBlank()) {
            val restored = deserializeOlmObject<OlmOutboundGroupSession>(existingB64)
            if (restored != null && restored.messageIndex() < 200) {
                return restored
            }
        }
        val fresh = OlmOutboundGroupSession()
        saveOutboundMegolmSession(roomId, fresh)
        return fresh
    }

    @Synchronized
    private fun saveOutboundMegolmSession(roomId: String, session: OlmOutboundGroupSession) {
        val vault = JSONObject(sessionStore.getOutboundMegolmSessionsJson())
        val b64 = serializeOlmObject(session)
        if (b64.isNotBlank()) {
            vault.put(roomId, b64)
            sessionStore.saveOutboundMegolmSessionsJson(vault.toString())
        }
    }

    @Synchronized
    private fun storeInboundMegolmSession(
        roomId: String,
        sessionId: String,
        senderKey: String,
        session: OlmInboundGroupSession
    ) {
        val vault = JSONObject(sessionStore.getInboundMegolmSessionsJson())
        val b64 = serializeOlmObject(session)
        if (b64.isBlank()) return

        val exportedKey = runCatching {
            session.export(session.firstKnownIndex)
        }.getOrDefault("")

        val entry = JSONObject().apply {
            put("room_id", roomId)
            put("session_id", sessionId)
            put("sender_key", senderKey)
            put("serialized_b64", b64)
            if (exportedKey.isNotBlank()) {
                put("exported_session_key", exportedKey)
                put("first_known_index", session.firstKnownIndex)
            }
        }
        vault.put("$roomId|$sessionId", entry)
        sessionStore.saveInboundMegolmSessionsJson(vault.toString())
    }

    @Synchronized
    private fun getInboundMegolmSession(roomId: String, sessionId: String): OlmInboundGroupSession? {
        val vault = JSONObject(sessionStore.getInboundMegolmSessionsJson())
        val directEntry = vault.optJSONObject("$roomId|$sessionId")
        if (directEntry != null) {
            val b64 = directEntry.optString("serialized_b64")
            deserializeOlmObject<OlmInboundGroupSession>(b64)?.let { return it }
            val exported = directEntry.optString("exported_session_key")
            if (exported.isNotBlank()) {
                return runCatching { OlmInboundGroupSession.importSession(exported) }.getOrNull()
            }
        }
        // Fallback lookup by sessionId if room prefix differed
        val iter = vault.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            if (k.endsWith("|$sessionId")) {
                val entry = vault.optJSONObject(k) ?: continue
                val b64 = entry.optString("serialized_b64")
                deserializeOlmObject<OlmInboundGroupSession>(b64)?.let { return it }
            }
        }
        return null
    }

    // =========================================================================
    // 7. EXPORT & IMPORT FOR 6-DIGIT PIN KEY BACKUP (Standard Megolm Export)
    // =========================================================================

    /**
     * Exports all Megolm inbound group sessions in standard Matrix export format
     * (`OlmInboundGroupSession.export(firstKnownIndex)`) so `PinKeyBackupManager`
     * can encrypt them with the user's 6-digit PIN.
     */
    @Synchronized
    fun exportMegolmSessionsForPinBackup(): JSONObject {
        val vault = JSONObject(sessionStore.getInboundMegolmSessionsJson())
        val exportedArray = JSONArray()
        val iter = vault.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            val entry = vault.optJSONObject(k) ?: continue
            val roomId = entry.optString("room_id")
            val sessionId = entry.optString("session_id")
            val senderKey = entry.optString("sender_key")
            var exportedKey = entry.optString("exported_session_key")
            var firstKnownIndex = entry.optLong("first_known_index", 0L)

            if (exportedKey.isBlank()) {
                val sess = deserializeOlmObject<OlmInboundGroupSession>(entry.optString("serialized_b64"))
                if (sess != null) {
                    firstKnownIndex = runCatching { sess.firstKnownIndex }.getOrDefault(0L)
                    exportedKey = runCatching { sess.export(firstKnownIndex) }.getOrDefault("")
                }
            }

            if (roomId.isNotBlank() && sessionId.isNotBlank() && exportedKey.isNotBlank()) {
                exportedArray.put(
                    JSONObject().apply {
                        put("algorithm", RunConfig.ROOM_ENCRYPTION_ALGORITHM)
                        put("room_id", roomId)
                        put("session_id", sessionId)
                        put("sender_key", senderKey)
                        put("first_known_index", firstKnownIndex)
                        put("session_key", exportedKey)
                    }
                )
            }
        }

        return JSONObject().apply {
            put("format", "matrix_megolm_export_v2")
            put("sessions", exportedArray)
        }
    }

    /**
     * Imports Megolm inbound group sessions decrypted from the user's 6-digit PIN backup
     * using `OlmInboundGroupSession.importSession(exportedSessionKey)`.
     */
    @Synchronized
    fun importMegolmSessionsFromPinBackup(decryptedBackupJson: JSONObject): Int {
        val sessionsArr = decryptedBackupJson.optJSONArray("sessions") ?: return 0
        var importedCount = 0
        for (i in 0 until sessionsArr.length()) {
            val item = sessionsArr.optJSONObject(i) ?: continue
            val roomId = item.optString("room_id")
            val sessionId = item.optString("session_id")
            val senderKey = item.optString("sender_key")
            val exportedSessionKey = item.optString("session_key")
            if (roomId.isBlank() || sessionId.isBlank() || exportedSessionKey.isBlank()) continue

            runCatching {
                val importedSession = OlmInboundGroupSession.importSession(exportedSessionKey)
                storeInboundMegolmSession(roomId, sessionId, senderKey, importedSession)
                importedCount++
            }
        }
        return importedCount
    }

    // =========================================================================
    // JAVA SERIALIZATION HELPERS FOR LIBOLM JNI OBJECTS
    // =========================================================================

    private fun serializeOlmObject(obj: Serializable): String {
        return try {
            val baos = ByteArrayOutputStream()
            ObjectOutputStream(baos).use { oos ->
                oos.writeObject(obj)
            }
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Serializable> deserializeOlmObject(base64: String): T? {
        if (base64.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            ObjectInputStream(ByteArrayInputStream(bytes)).use { ois ->
                ois.readObject() as? T
            }
        } catch (_: Exception) {
            null
        }
    }
}
