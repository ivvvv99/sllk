package com.example.webrtc

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.webrtc.*
import java.util.ArrayList

class WebRtcManager(private val context: Context) {

    companion object {
        private const val TAG = "WebRtcManager"
    }

    val eglContext: EglBase.Context = EglBase.create().eglBaseContext

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null

    private val _localTrackFlow = MutableStateFlow<VideoTrack?>(null)
    val localTrackFlow: StateFlow<VideoTrack?> = _localTrackFlow

    private val _remoteTrackFlow = MutableStateFlow<VideoTrack?>(null)
    val remoteTrackFlow: StateFlow<VideoTrack?> = _remoteTrackFlow

    private val _connectionState = MutableStateFlow(PeerConnection.PeerConnectionState.NEW)
    val connectionState: StateFlow<PeerConnection.PeerConnectionState> = _connectionState

    private var iceCandidateCallback: ((IceCandidate) -> Unit)? = null

    init {
        initPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglContext, true, true))
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    fun startLocalStream(videoEnabled: Boolean, audioEnabled: Boolean) {
        val factory = peerConnectionFactory ?: return

        // 1. Audio Track Setup
        if (audioEnabled) {
            val audioConstraints = MediaConstraints()
            localAudioSource = factory.createAudioSource(audioConstraints)
            localAudioTrack = factory.createAudioTrack("local_audio", localAudioSource)
        }

        // 2. Video Track Setup
        if (videoEnabled) {
            videoCapturer = createVideoCapturer()
            if (videoCapturer != null) {
                localVideoSource = factory.createVideoSource(false)
                videoCapturer?.initialize(
                    SurfaceTextureHelper.create("CaptureThread", eglContext),
                    context,
                    localVideoSource?.capturerObserver
                )
                videoCapturer?.startCapture(640, 480, 30)
                localVideoTrack = factory.createVideoTrack("local_video", localVideoSource)
                _localTrackFlow.value = localVideoTrack
            }
        }
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        // Prefer front-facing camera
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }

        // Fallback to back-facing or any other
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }
        return null
    }

    fun initPeerConnection(onIceCandidate: (IceCandidate) -> Unit) {
        this.iceCandidateCallback = onIceCandidate
        val factory = peerConnectionFactory ?: return

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "New ICE candidate gathered: $candidate")
                iceCandidateCallback?.invoke(candidate)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            
            override fun onDataChannel(dataChannel: DataChannel) {}
            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                val track = receiver.track()
                if (track is VideoTrack) {
                    Log.d(TAG, "Remote video track added")
                    _remoteTrackFlow.value = track
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                Log.d(TAG, "Connection state changed: $newState")
                _connectionState.value = newState
            }
        }

        peerConnection = factory.createPeerConnection(rtcConfig, observer)

        // Add local tracks to peer connection
        localAudioTrack?.let { peerConnection?.addTrack(it, listOf("stream_val")) }
        localVideoTrack?.let { peerConnection?.addTrack(it, listOf("stream_val")) }
    }

    fun createOffer(callback: (SessionDescription) -> Unit) {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                pc.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d(TAG, "Local description set for Offer")
                        callback(desc)
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create offer: $error")
            }
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun handleOffer(offerSdpString: String, callback: (SessionDescription) -> Unit) {
        val pc = peerConnection ?: return
        val offerSdp = SessionDescription(SessionDescription.Type.OFFER, offerSdpString)

        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set for Offer. Creating Answer...")
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }
                pc.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answerDesc: SessionDescription) {
                        pc.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {
                                Log.d(TAG, "Local description set for Answer")
                                callback(answerDesc)
                            }
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(p0: String?) {}
                        }, answerDesc)
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(error: String?) {
                        Log.e(TAG, "Failed to create answer: $error")
                    }
                    override fun onSetFailure(p0: String?) {}
                }, constraints)
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Failed to set remote description for offer: $error")
            }
        }, offerSdp)
    }

    fun handleAnswer(answerSdpString: String) {
        val pc = peerConnection ?: return
        val answerSdp = SessionDescription(SessionDescription.Type.ANSWER, answerSdpString)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set for Answer successfully")
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Failed to set remote description for answer: $error")
            }
        }, answerSdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
        Log.d(TAG, "ICE candidate added successfully: $candidate")
    }

    fun toggleMic(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun toggleCamera(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        val capturer = videoCapturer as? CameraVideoCapturer ?: return
        capturer.switchCamera(null)
    }

    fun endCall() {
        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capture", e)
        }
        
        peerConnection?.close()
        peerConnection = null
        
        localAudioTrack = null
        localAudioSource = null
        localVideoTrack = null
        localVideoSource = null
        videoCapturer = null
        
        _localTrackFlow.value = null
        _remoteTrackFlow.value = null
        _connectionState.value = PeerConnection.PeerConnectionState.CLOSED
        iceCandidateCallback = null
    }

    fun clear() {
        endCall()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
    }
}
