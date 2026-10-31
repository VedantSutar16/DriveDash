package com.apru.drivedash

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioDeviceCallback
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Free, group ("mesh") voice calling for a DriveDash room.
 *
 * How it works:
 * - Every rider opens a direct WebRTC audio connection to every other rider currently in the room.
 * - Firebase Realtime Database is used purely as a signaling "mailbox" to swap
 *   the WebRTC handshake messages (offer/answer/ICE candidates) between pairs of riders.
 * - Uses JavaAudioDeviceModule to ensure audio is recorded via AudioRecord and played out
 *   of the phone's main loudspeaker (or Bluetooth helmet) on all modern Android devices.
 */
class WebRtcCallManager(
    private val context: Context,
    private val roomCode: String,
    private val myUid: String
) {
    private val TAG = "DriveDashCall"

    private val database: DatabaseReference =
        FirebaseDatabase.getInstance("https://drivedash-5b3be-default-rtdb.asia-southeast1.firebasedatabase.app")
            .getReference("rooms/$roomCode/signaling")

    private val eglBase: EglBase = EglBase.create()

    private val iceServers: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer(),
        // Free public TURN fallback (Open Relay Project)
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
            .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
            .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
            .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer()
    )

    private var audioDeviceModule: JavaAudioDeviceModule? = null

    private val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        val adm = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported())
            .setUseHardwareNoiseSuppressor(JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                override fun onWebRtcAudioRecordInitError(msg: String?) { Log.e(TAG, "AudioRecord init error: $msg") }
                override fun onWebRtcAudioRecordStartError(code: JavaAudioDeviceModule.AudioRecordStartErrorCode?, msg: String?) { Log.e(TAG, "AudioRecord start error: $code $msg") }
                override fun onWebRtcAudioRecordError(msg: String?) { Log.e(TAG, "AudioRecord error: $msg") }
            })
            .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                override fun onWebRtcAudioTrackInitError(msg: String?) { Log.e(TAG, "AudioTrack init error: $msg") }
                override fun onWebRtcAudioTrackStartError(code: JavaAudioDeviceModule.AudioTrackStartErrorCode?, msg: String?) { Log.e(TAG, "AudioTrack start error: $code $msg") }
                override fun onWebRtcAudioTrackError(msg: String?) { Log.e(TAG, "AudioTrack error: $msg") }
            })
            .setAudioRecordStateCallback(object : JavaAudioDeviceModule.AudioRecordStateCallback {
                override fun onWebRtcAudioRecordStart() {
                    Log.i(TAG, "WebRTC AudioRecord (Microphone) started capturing")
                }
                override fun onWebRtcAudioRecordStop() {
                    Log.i(TAG, "WebRTC AudioRecord (Microphone) stopped")
                }
            })
            .setAudioTrackStateCallback(object : JavaAudioDeviceModule.AudioTrackStateCallback {
                override fun onWebRtcAudioTrackStart() {
                    Log.i(TAG, "WebRTC AudioTrack (Speaker) started playback")
                }
                override fun onWebRtcAudioTrackStop() {
                    Log.i(TAG, "WebRTC AudioTrack (Speaker) stopped")
                }
            })
            .createAudioDeviceModule()
        audioDeviceModule = adm

        PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    private var audioSource: AudioSource? = null

    private val localAudioTrack: AudioTrack by lazy {
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        val source = factory.createAudioSource(audioConstraints)
        audioSource = source
        factory.createAudioTrack("AUDIO_$myUid", source).apply {
            setEnabled(true)
        }
    }

    private data class PeerSession(
        val connection: PeerConnection,
        val offerListener: ValueEventListener? = null,
        val answerListener: ValueEventListener? = null,
        val candidatesListener: ChildEventListener? = null
    )

    private val sessions = mutableMapOf<String, PeerSession>()
    private val queuedCandidates = mutableMapOf<String, MutableList<IceCandidate>>()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var started = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { /* audio focus listener */ }

    private fun requestCallAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build()
                audioFocusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "requestCallAudioFocus error", e)
        }
    }

    private fun abandonCallAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusChangeListener)
            }
        } catch (_: Exception) {}
    }

    /** Set this to get live updates of who is currently talking (uid -> speaking true/false) */
    var onSpeakingChanged: ((Map<String, Boolean>) -> Unit)? = null

    /** Called whenever the ICE connection state changes for a peer */
    var onPeerStateChanged: ((String, PeerConnection.IceConnectionState) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val statsIntervalMs = 400L
    private val speakingThreshold = 0.02 // WebRTC audioLevel is 0.0 (silence) - 1.0 (loud)
    private val statsRunnable = object : Runnable {
        override fun run() {
            pollSpeakingLevels()
            if (started) {
                mainHandler.postDelayed(this, statsIntervalMs)
            }
        }
    }

    /** Called whenever audio output route changes (isBluetooth: Boolean, deviceName: String) */
    var onAudioRouteChanged: ((Boolean, String) -> Unit)? = null

    private var scoReceiverRegistered = false
    private var isBluetoothConnected = false

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Audio devices added - evaluating communication route")
            mainHandler.post { routeAudio() }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Audio devices removed - evaluating communication route")
            mainHandler.post { routeAudio() }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.d(TAG, "Bluetooth broadcast received: $action")
            mainHandler.post { routeAudio() }
        }
    }

    var isSpeakerphoneOn: Boolean = true
        private set

    fun setSpeakerphone(enable: Boolean) {
        isSpeakerphoneOn = enable
        mainHandler.post { routeAudio() }
    }

    /**
     * Prioritizes Bluetooth devices (helmets, headsets, intercoms), then wired headsets,
     * and falls back to the phone loudspeaker so the rider can hear hands-free while driving.
     */
    fun routeAudio() {
        if (!started) return
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            // Ensure voice call volume is maximum so audio is clearly audible
            try {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVol, 0)
            } catch (_: Exception) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val available = audioManager.availableCommunicationDevices
                Log.d(TAG, "Available audio devices: ${available.map { "${it.productName ?: "unknown"}(type=${it.type})" }}")

                // 1. Highest priority: Bluetooth helmet/headset/intercom communication devices
                val btDevice = available.firstOrNull { dev ->
                    dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    dev.type == AudioDeviceInfo.TYPE_HEARING_AID
                }

                // 2. Wired headsets
                val wiredDevice = available.firstOrNull { dev ->
                    dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                }

                // 3. Built-in speakerphone (loudspeaker)
                val speakerDevice = available.firstOrNull { dev ->
                    dev.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }

                // 4. Built-in earpiece
                val earpieceDevice = available.firstOrNull { dev ->
                    dev.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }

                val target = if (btDevice != null) {
                    btDevice
                } else if (wiredDevice != null) {
                    wiredDevice
                } else if (isSpeakerphoneOn) {
                    speakerDevice
                } else {
                    earpieceDevice
                }

                if (target != null) {
                    val current = audioManager.communicationDevice
                    if (current?.id != target.id) {
                        val success = audioManager.setCommunicationDevice(target)
                        Log.i(TAG, "setCommunicationDevice: ${target.productName ?: "device"} (type=${target.type}, success=$success)")
                    }
                } else {
                    audioManager.clearCommunicationDevice()
                }

                val isBt = target == btDevice
                val isSpeaker = target == speakerDevice
                val name = target?.productName?.toString() ?: if (isBt) "Bluetooth Helmet" else if (isSpeaker) "Speaker" else "Earpiece"
                isBluetoothConnected = isBt
                onAudioRouteChanged?.invoke(isBt, name)
            } else {
                routeAudioLegacy()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio routing error: ${e.message}", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun routeAudioLegacy() {
        try {
            val hasBt = audioManager.isBluetoothScoAvailableOffCall || audioManager.isBluetoothA2dpOn
            if (hasBt) {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                audioManager.isSpeakerphoneOn = false
                isBluetoothConnected = true
                onAudioRouteChanged?.invoke(true, "Bluetooth")
                Log.i(TAG, "Legacy: Bluetooth SCO started")
            } else {
                audioManager.isSpeakerphoneOn = isSpeakerphoneOn
                isBluetoothConnected = false
                onAudioRouteChanged?.invoke(false, if (isSpeakerphoneOn) "Speaker" else "Earpiece")
                Log.i(TAG, "Legacy: Speakerphone = $isSpeakerphoneOn")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Legacy audio routing error: ${e.message}")
            audioManager.isSpeakerphoneOn = isSpeakerphoneOn
            isBluetoothConnected = false
            onAudioRouteChanged?.invoke(false, if (isSpeakerphoneOn) "Speaker" else "Earpiece")
        }
    }

    /** Call once when the rider enters the room and has RECORD_AUDIO permission granted. */
    fun start() {
        if (started) return
        started = true
        requestCallAudioFocus()

        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set audio mode", e)
        }

        localAudioTrack.setEnabled(true)

        // Register dynamic audio device callback
        try {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
        } catch (e: Exception) {
            Log.w(TAG, "registerAudioDeviceCallback failed: ${e.message}")
        }

        // Register Bluetooth broadcast receiver for connection state changes
        try {
            val filter = IntentFilter().apply {
                addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(context, bluetoothReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            scoReceiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "registerReceiver failed: ${e.message}")
        }

        // Route immediately, and schedule follow-ups to catch delayed Bluetooth registration
        routeAudio()
        mainHandler.postDelayed({ routeAudio() }, 500L)
        mainHandler.postDelayed({ routeAudio() }, 1500L)

        mainHandler.post(statsRunnable)
    }

    fun setMuted(muted: Boolean) {
        localAudioTrack.setEnabled(!muted)
    }

    /** Call whenever the set of riders currently in the room changes. */
    fun syncPeers(currentUids: Set<String>) {
        if (!started || myUid.isBlank()) return
        val others = currentUids.filter { it.isNotBlank() && it != myUid }.toSet()

        // connect to anyone new
        for (uid in others) {
            if (!sessions.containsKey(uid)) connectTo(uid)
        }
        // disconnect anyone who left
        val gone = sessions.keys - others
        for (uid in gone) disconnectFrom(uid)
    }

    private fun pairKey(a: String, b: String) = listOf(a, b).sorted().joinToString("_")

    private fun handleRemoteCandidate(connection: PeerConnection, otherUid: String, candidate: IceCandidate) {
        if (connection.remoteDescription != null) {
            val mid = if (candidate.sdpMid.isNullOrBlank()) "0" else candidate.sdpMid
            connection.addIceCandidate(IceCandidate(mid, candidate.sdpMLineIndex, candidate.sdp))
        } else {
            synchronized(queuedCandidates) {
                queuedCandidates.getOrPut(otherUid) { mutableListOf() }.add(candidate)
            }
        }
    }

    private fun drainQueuedCandidates(connection: PeerConnection, otherUid: String) {
        val list = synchronized(queuedCandidates) {
            queuedCandidates.remove(otherUid)
        } ?: return
        for (candidate in list) {
            val mid = if (candidate.sdpMid.isNullOrBlank()) "0" else candidate.sdpMid
            connection.addIceCandidate(IceCandidate(mid, candidate.sdpMLineIndex, candidate.sdp))
        }
        Log.d(TAG, "Drained ${list.size} queued ICE candidates for $otherUid")
    }

    private fun connectTo(otherUid: String) {
        if (myUid.isBlank() || otherUid.isBlank() || myUid == otherUid) return
        val pair = pairKey(myUid, otherUid)
        val pairRef = database.child(pair)
        val iAmCaller = myUid < otherUid

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val data = mapOf(
                    "sdpMid" to (candidate.sdpMid ?: ""),
                    "sdpMLineIndex" to candidate.sdpMLineIndex,
                    "candidate" to candidate.sdp
                )
                pairRef.child("candidates").child(myUid).push().setValue(data)
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "ICE state with $otherUid: $state")
                mainHandler.post {
                    onPeerStateChanged?.invoke(otherUid, state)
                }
                if (state == PeerConnection.IceConnectionState.FAILED ||
                    state == PeerConnection.IceConnectionState.DISCONNECTED
                ) {
                    mainHandler.postDelayed({
                        val stillBad = sessions[otherUid]?.connection?.iceConnectionState() in listOf(
                            PeerConnection.IceConnectionState.FAILED,
                            PeerConnection.IceConnectionState.DISCONNECTED
                        )
                        if (stillBad && sessions.containsKey(otherUid) && started) {
                            Log.d(TAG, "Reconnecting to $otherUid after $state")
                            disconnectFrom(otherUid)
                            connectTo(otherUid)
                        }
                    }, 2500L)
                }
            }

            override fun onTrack(transceiver: RtpTransceiver) {
                Log.d(TAG, "onTrack from $otherUid: ${transceiver.receiver.track()?.kind()}")
                val track = transceiver.receiver.track()
                if (track is AudioTrack) {
                    track.setEnabled(true)
                    track.setVolume(2.0)
                }
            }

            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                Log.d(TAG, "onAddTrack from $otherUid: ${receiver.track()?.kind()}")
                val track = receiver.track()
                if (track is AudioTrack) {
                    track.setEnabled(true)
                    track.setVolume(2.0)
                }
            }

            override fun onAddStream(stream: MediaStream) {
                for (track in stream.audioTracks) {
                    track.setEnabled(true)
                    track.setVolume(2.0)
                }
            }

            override fun onRemoveStream(stream: MediaStream) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onDataChannel(channel: org.webrtc.DataChannel) {}
            override fun onRenegotiationNeeded() {}
        }

        val connection = factory.createPeerConnection(rtcConfig, observer) ?: return

        // Add local audio track for transmission
        connection.addTrack(localAudioTrack, listOf("drivedash_audio_$myUid"))

        val sdpConstraints = MediaConstraints()

        var offerListener: ValueEventListener? = null
        var answerListener: ValueEventListener? = null

        if (iAmCaller) {
            // Fresh pair session: wipe previous pair signaling so old offers/answers/candidates don't interfere
            pairRef.removeValue().addOnCompleteListener {
                if (!started || !sessions.containsKey(otherUid)) return@addOnCompleteListener

                connection.createOffer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(desc: SessionDescription) {
                        connection.setLocalDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                Log.d(TAG, "Local description (offer) set for $otherUid")
                                pairRef.child("offer").setValue(mapOf("type" to desc.type.canonicalForm(), "sdp" to desc.description))
                            }
                            override fun onSetFailure(error: String?) {
                                Log.e(TAG, "setLocalDescription (offer) failed with $otherUid: $error")
                            }
                        }, desc)
                    }
                    override fun onCreateFailure(error: String?) {
                        Log.e(TAG, "createOffer failed with $otherUid: $error")
                    }
                }, sdpConstraints)
            }

            answerListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val sdp = snapshot.child("sdp").getValue(String::class.java) ?: return
                    if (connection.remoteDescription == null) {
                        connection.setRemoteDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                Log.d(TAG, "Remote description (answer) set successfully with $otherUid")
                                drainQueuedCandidates(connection, otherUid)
                            }
                            override fun onSetFailure(error: String?) {
                                Log.e(TAG, "setRemoteDescription (answer) failed with $otherUid: $error")
                            }
                        }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            pairRef.child("answer").addValueEventListener(answerListener)
        } else {
            offerListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val sdp = snapshot.child("sdp").getValue(String::class.java) ?: return
                    if (connection.remoteDescription != null) return

                    // Set remote description first; only create answer AFTER remote description succeeds
                    connection.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Remote description (offer) set successfully with $otherUid, creating answer")
                            drainQueuedCandidates(connection, otherUid)

                            connection.createAnswer(object : SimpleSdpObserver() {
                                override fun onCreateSuccess(desc: SessionDescription) {
                                    connection.setLocalDescription(object : SimpleSdpObserver() {
                                        override fun onSetSuccess() {
                                            Log.d(TAG, "Local description (answer) set successfully for $otherUid")
                                            pairRef.child("answer").setValue(mapOf("type" to desc.type.canonicalForm(), "sdp" to desc.description))
                                        }
                                        override fun onSetFailure(error: String?) {
                                            Log.e(TAG, "setLocalDescription (answer) failed with $otherUid: $error")
                                        }
                                    }, desc)
                                }
                                override fun onCreateFailure(error: String?) {
                                    Log.e(TAG, "createAnswer failed with $otherUid: $error")
                                }
                            }, sdpConstraints)
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "setRemoteDescription (offer) failed with $otherUid: $error")
                        }
                    }, SessionDescription(SessionDescription.Type.OFFER, sdp))
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            pairRef.child("offer").addValueEventListener(offerListener)
        }

        val candidatesListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val sdpMid = snapshot.child("sdpMid").getValue(String::class.java) ?: ""
                val sdpMLineIndex = (snapshot.child("sdpMLineIndex").value as? Number)?.toInt() ?: 0
                val sdp = snapshot.child("candidate").getValue(String::class.java) ?: return
                handleRemoteCandidate(connection, otherUid, IceCandidate(sdpMid, sdpMLineIndex, sdp))
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        // Listen to candidates posted by the other rider
        pairRef.child("candidates").child(otherUid).addChildEventListener(candidatesListener)

        sessions[otherUid] = PeerSession(connection, offerListener, answerListener, candidatesListener)
    }

    private fun disconnectFrom(otherUid: String) {
        val session = sessions.remove(otherUid) ?: return
        val pairRef = database.child(pairKey(myUid, otherUid))
        session.offerListener?.let { pairRef.child("offer").removeEventListener(it) }
        session.answerListener?.let { pairRef.child("answer").removeEventListener(it) }
        session.candidatesListener?.let { pairRef.child("candidates").child(otherUid).removeEventListener(it) }
        try {
            session.connection.close()
            session.connection.dispose()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing connection to $otherUid", e)
        }
        synchronized(queuedCandidates) {
            queuedCandidates.remove(otherUid)
        }
    }

    /** Call when the rider leaves the room / closes the app. */
    fun stop() {
        mainHandler.removeCallbacks(statsRunnable)
        for (uid in sessions.keys.toList()) disconnectFrom(uid)
        started = false

        try {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (_: Exception) {}

        if (scoReceiverRegistered) {
            try {
                context.unregisterReceiver(bluetoothReceiver)
            } catch (_: Exception) {}
            scoReceiverRegistered = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.clearCommunicationDevice()
            } catch (_: Exception) {}
        }

        @Suppress("DEPRECATION")
        try {
            if (audioManager.isBluetoothScoOn) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
            audioManager.isSpeakerphoneOn = false
        } catch (_: Exception) {}

        try {
            localAudioTrack.setEnabled(false)
            localAudioTrack.dispose()
        } catch (_: Exception) {}

        try {
            audioSource?.dispose()
            audioSource = null
        } catch (_: Exception) {}

        try {
            audioDeviceModule?.release()
            audioDeviceModule = null
        } catch (_: Exception) {}

        try {
            factory.dispose()
        } catch (_: Exception) {}

        try {
            eglBase.release()
        } catch (_: Exception) {}

        audioManager.mode = AudioManager.MODE_NORMAL
        abandonCallAudioFocus()
    }

    // Polls WebRTC's per-connection stats for audio levels (0.0-1.0) and reports who's talking
    private fun pollSpeakingLevels() {
        val activeSessions = sessions.entries.toList()
        if (activeSessions.isEmpty()) {
            onSpeakingChanged?.invoke(emptyMap())
            return
        }
        val result = mutableMapOf<String, Boolean>()
        var myLevel = 0.0
        val total = activeSessions.size
        var completed = 0

        for ((uid, session) in activeSessions) {
            session.connection.getStats { report ->
                var remoteLevel = 0.0
                var connMyLevel = 0.0
                for (stats in report.statsMap.values) {
                    val kind = stats.members["kind"] as? String
                    if (stats.type == "inbound-rtp" && kind == "audio") {
                        (stats.members["audioLevel"] as? Double)?.let { if (it > remoteLevel) remoteLevel = it }
                    }
                    if (stats.type == "media-source" && kind == "audio") {
                        (stats.members["audioLevel"] as? Double)?.let { if (it > connMyLevel) connMyLevel = it }
                    }
                }
                mainHandler.post {
                    result[uid] = remoteLevel > speakingThreshold
                    if (connMyLevel > myLevel) myLevel = connMyLevel
                    completed++
                    if (completed >= total) {
                        result[myUid] = myLevel > speakingThreshold
                        onSpeakingChanged?.invoke(result.toMap())
                    }
                }
            }
        }
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) { Log.e("DriveDashCall", "SDP create failed: $error") }
        override fun onSetFailure(error: String?) { Log.e("DriveDashCall", "SDP set failed: $error") }
    }
}
