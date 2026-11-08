package com.takidz.runv2.webrtc

import android.content.Context
import android.media.AudioManager
import com.takidz.runv2.data.CallQualityMode
import com.takidz.runv2.data.IceCandidatePayload
import com.takidz.runv2.data.TurnCredentials
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Real 1-to-1 P2P WebRTC Call Manager for Run v2.
 *
 * - Uses TURN credentials fetched from `/_matrix/client/v3/voip/turnServer` + public STUN fallback
 *   so calls connect reliably over mobile data (4G/5G) as well as Wi-Fi.
 * - Respects user call quality preference:
 *   * `DEFAULT_480P` -> 854x480 @ 24fps (650 kbps max video bitrate)
 *   * `DATA_SAVER` -> 426x240 @ 15fps (250 kbps max video bitrate)
 *   * `AUDIO_ONLY` -> Audio track only, camera capture disabled
 * - Implements real hardware controls: Mic mute/unmute, Camera enable/disable,
 *   Speakerphone toggle via Android `AudioManager`, and full PeerConnection teardown on hangup.
 */
class WebRtcCallManager(
    private val context: Context,
    private val onLocalIceCandidates: (List<IceCandidatePayload>) -> Unit,
    private val onConnectionConnected: () -> Unit,
    private val onConnectionEnded: () -> Unit
) {
    val eglBase: EglBase = EglBase.create()

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null

    private val pendingRemoteCandidates = CopyOnWriteArrayList<IceCandidatePayload>()
    private var isRemoteDescriptionSet = false

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        initFactory()
    }

    private fun initFactory() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .createInitializationOptions()
        )
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    fun bindRenderers(localView: SurfaceViewRenderer?, remoteView: SurfaceViewRenderer?) {
        localRenderer = localView
        remoteRenderer = remoteView
        localVideoTrack?.let { track ->
            localView?.let { renderer -> runCatching { track.addSink(renderer) } }
        }
        remoteVideoTrack?.let { track ->
            remoteView?.let { renderer -> runCatching { track.addSink(renderer) } }
        }
    }

    fun startOutgoingCall(
        turnCredentials: TurnCredentials?,
        qualityMode: CallQualityMode,
        requestedVideo: Boolean,
        onOfferCreated: (String) -> Unit
    ) {
        val enableVideo = requestedVideo && qualityMode != CallQualityMode.AUDIO_ONLY
        setupPeerConnection(turnCredentials, qualityMode, enableVideo)

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(
                MediaConstraints.KeyValuePair(
                    "OfferToReceiveVideo",
                    if (enableVideo) "true" else "false"
                )
            )
        }

        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        applyBitrateLimit(qualityMode)
                        onOfferCreated(desc.description)
                    }
                }, desc)
            }
        }, constraints)
    }

    fun answerIncomingCall(
        offerSdp: String,
        turnCredentials: TurnCredentials?,
        qualityMode: CallQualityMode,
        requestedVideo: Boolean,
        onAnswerCreated: (String) -> Unit
    ) {
        val enableVideo = requestedVideo && qualityMode != CallQualityMode.AUDIO_ONLY
        setupPeerConnection(turnCredentials, qualityMode, enableVideo)

        val remoteOffer = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                isRemoteDescriptionSet = true
                drainPendingRemoteCandidates()

                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(
                        MediaConstraints.KeyValuePair(
                            "OfferToReceiveVideo",
                            if (enableVideo) "true" else "false"
                        )
                    )
                }

                peerConnection?.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(answerDesc: SessionDescription?) {
                        if (answerDesc == null) return
                        peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                applyBitrateLimit(qualityMode)
                                onAnswerCreated(answerDesc.description)
                            }
                        }, answerDesc)
                    }
                }, constraints)
            }
        }, remoteOffer)
    }

    fun onRemoteAnswerReceived(answerSdp: String) {
        val desc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                isRemoteDescriptionSet = true
                drainPendingRemoteCandidates()
            }
        }, desc)
    }

    fun onRemoteIceCandidatesReceived(candidates: List<IceCandidatePayload>) {
        if (!isRemoteDescriptionSet || peerConnection == null) {
            pendingRemoteCandidates.addAll(candidates)
            return
        }
        candidates.forEach { c ->
            peerConnection?.addIceCandidate(
                IceCandidate(c.sdpMid, c.sdpMLineIndex, c.candidate)
            )
        }
    }

    private fun drainPendingRemoteCandidates() {
        val copy = pendingRemoteCandidates.toList()
        pendingRemoteCandidates.clear()
        copy.forEach { c ->
            peerConnection?.addIceCandidate(
                IceCandidate(c.sdpMid, c.sdpMLineIndex, c.candidate)
            )
        }
    }

    private fun setupPeerConnection(
        turnCredentials: TurnCredentials?,
        qualityMode: CallQualityMode,
        enableVideo: Boolean
    ) {
        releaseCallResources()
        isRemoteDescriptionSet = false
        pendingRemoteCandidates.clear()

        val iceServers = mutableListOf<PeerConnection.IceServer>()
        iceServers.add(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        if (turnCredentials != null && turnCredentials.uris.isNotEmpty()) {
            turnCredentials.uris.forEach { uri ->
                iceServers.add(
                    PeerConnection.IceServer.builder(uri)
                        .setUsername(turnCredentials.username)
                        .setPassword(turnCredentials.password)
                        .createIceServer()
                )
            }
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val factory = peerConnectionFactory ?: return
        peerConnection = factory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}

                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                    when (newState) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> onConnectionConnected()
                        PeerConnection.IceConnectionState.DISCONNECTED,
                        PeerConnection.IceConnectionState.FAILED,
                        PeerConnection.IceConnectionState.CLOSED -> onConnectionEnded()
                        else -> {}
                    }
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}

                override fun onIceCandidate(candidate: IceCandidate?) {
                    if (candidate != null) {
                        onLocalIceCandidates(
                            listOf(
                                IceCandidatePayload(
                                    sdpMid = candidate.sdpMid ?: "0",
                                    sdpMLineIndex = candidate.sdpMLineIndex,
                                    candidate = candidate.sdp
                                )
                            )
                        )
                    }
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onAddStream(stream: MediaStream?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(dataChannel: DataChannel?) {}
                override fun onRenegotiationNeeded() {}

                override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                    val track = receiver?.track()
                    if (track is VideoTrack) {
                        remoteVideoTrack = track
                        remoteRenderer?.let { renderer ->
                            runCatching { track.addSink(renderer) }
                        }
                    }
                }
            }
        )

        // Configure Audio
        configureAudioRoute(speakerOn = enableVideo)
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        }
        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("RUNV2_AUDIO_TRACK", audioSource)
        localAudioTrack?.let { peerConnection?.addTrack(it, listOf("RUNV2_STREAM")) }

        // Configure Video (480p default or 240p Data Saver)
        if (enableVideo && qualityMode.maxWidth > 0) {
            val capturer = createCameraCapturer()
            if (capturer != null) {
                videoCapturer = capturer
                surfaceTextureHelper = SurfaceTextureHelper.create("RunV2CaptureThread", eglBase.eglBaseContext)
                videoSource = factory.createVideoSource(capturer.isScreencast)
                capturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
                capturer.startCapture(qualityMode.maxWidth, qualityMode.maxHeight, qualityMode.maxFps)

                localVideoTrack = factory.createVideoTrack("RUNV2_VIDEO_TRACK", videoSource)
                localVideoTrack?.setEnabled(true)
                localRenderer?.let { renderer ->
                    runCatching { localVideoTrack?.addSink(renderer) }
                }
                localVideoTrack?.let { peerConnection?.addTrack(it, listOf("RUNV2_STREAM")) }
            }
        }
    }

    private fun applyBitrateLimit(qualityMode: CallQualityMode) {
        val senders = peerConnection?.senders ?: return
        for (sender in senders) {
            val track = sender.track() ?: continue
            if (track.kind() == "video" && qualityMode.maxBitrateKbps > 0) {
                val params = sender.parameters
                if (params.encodings.isNotEmpty()) {
                    params.encodings.forEach { encoding ->
                        encoding.maxBitrateBps = qualityMode.maxBitrateKbps * 1000
                        encoding.maxFramerate = qualityMode.maxFps
                    }
                    sender.parameters = params
                }
            }
        }
    }

    fun setMicMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
        audioManager.isMicrophoneMute = muted
    }

    fun setCameraEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun setSpeakerphoneEnabled(enabled: Boolean) {
        configureAudioRoute(speakerOn = enabled)
    }

    @Suppress("DEPRECATION")
    private fun configureAudioRoute(speakerOn: Boolean) {
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = speakerOn
        }
    }

    fun endCall() {
        releaseCallResources()
        runCatching {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isMicrophoneMute = false
        }
    }

    private fun releaseCallResources() {
        runCatching {
            localRenderer?.let { localVideoTrack?.removeSink(it) }
            remoteRenderer?.let { remoteVideoTrack?.removeSink(it) }
        }
        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        videoCapturer = null

        runCatching { videoSource?.dispose() }
        videoSource = null

        runCatching { surfaceTextureHelper?.dispose() }
        surfaceTextureHelper = null

        runCatching { audioSource?.dispose() }
        audioSource = null

        localVideoTrack = null
        remoteVideoTrack = null
        localAudioTrack = null

        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        // Prefer front camera for 1-to-1 video calls
        for (name in deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                enumerator.createCapturer(name, null)?.let { return it }
            }
        }
        for (name in deviceNames) {
            if (!enumerator.isFrontFacing(name)) {
                enumerator.createCapturer(name, null)?.let { return it }
            }
        }
        return null
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}
