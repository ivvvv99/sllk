package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.*
import com.example.service.CloudinaryUploader
import com.example.webrtc.WebRtcManager
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.VideoTrack
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val prefs = application.getSharedPreferences("omnichat_prefs", Context.MODE_PRIVATE)

    // Configuration Settings
    private val _cloudinaryCloudName = MutableStateFlow(prefs.getString("cloud_name", "") ?: "")
    val cloudinaryCloudName: StateFlow<String> = _cloudinaryCloudName.asStateFlow()

    private val _cloudinaryUploadPreset = MutableStateFlow(prefs.getString("upload_preset", "") ?: "")
    val cloudinaryUploadPreset: StateFlow<String> = _cloudinaryUploadPreset.asStateFlow()

    private val _adminPassword = MutableStateFlow(prefs.getString("admin_password", "1234") ?: "1234")
    private val _modPassword = MutableStateFlow(prefs.getString("mod_password", "5678") ?: "5678")

    // UI States
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _onlineUsers = MutableStateFlow<List<User>>(emptyList())
    val onlineUsers: StateFlow<List<User>> = _onlineUsers.asStateFlow()

    private val _typingUsers = MutableStateFlow<List<String>>(emptyList())
    val typingUsers: StateFlow<List<String>> = _typingUsers.asStateFlow()

    private val _bannedUsersList = MutableStateFlow<List<User>>(emptyList())
    val bannedUsersList: StateFlow<List<User>> = _bannedUsersList.asStateFlow()

    private val _mutedUsersList = MutableStateFlow<List<User>>(emptyList())
    val mutedUsersList: StateFlow<List<User>> = _mutedUsersList.asStateFlow()

    private val _moderationLogs = MutableStateFlow<List<ModerationLog>>(emptyList())
    val moderationLogs: StateFlow<List<ModerationLog>> = _moderationLogs.asStateFlow()

    // WebRTC Calling States
    private val _activeCall = MutableStateFlow<CallSession?>(null)
    val activeCall: StateFlow<CallSession?> = _activeCall.asStateFlow()

    private val _callStateText = MutableStateFlow("")
    val callStateText: StateFlow<String> = _callStateText.asStateFlow()

    private val _isMicrophoneEnabled = MutableStateFlow(true)
    val isMicrophoneEnabled: StateFlow<Boolean> = _isMicrophoneEnabled.asStateFlow()

    private val _isCameraEnabled = MutableStateFlow(true)
    val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()

    val webRtcManager = WebRtcManager(application)
    val localVideoTrack: StateFlow<VideoTrack?> = webRtcManager.localTrackFlow
    val remoteVideoTrack: StateFlow<VideoTrack?> = webRtcManager.remoteTrackFlow
    val peerConnectionState: StateFlow<PeerConnection.PeerConnectionState> = webRtcManager.connectionState

    // Listeners
    private var messagesListener: ListenerRegistration? = null
    private var usersListener: ListenerRegistration? = null
    private var typingListener: ListenerRegistration? = null
    private var callListener: ListenerRegistration? = null
    private var callerCandidatesListener: ListenerRegistration? = null
    private var receiverCandidatesListener: ListenerRegistration? = null
    private var logsListener: ListenerRegistration? = null

    private var typingJob: Job? = null
    private var isCallingActive = false

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val id = prefs.getString("user_id", "")
        if (!id.isNullOrEmpty()) {
            val name = prefs.getString("user_name", "Anonymous") ?: "Anonymous"
            val avatarUrl = prefs.getString("avatar_url", "") ?: ""
            val role = prefs.getString("role", "User") ?: "User"
            
            _currentUser.value = User(
                id = id,
                name = name,
                avatarUrl = avatarUrl,
                role = role
            )
            observeDatabase()
        }
    }

    fun saveCloudinaryConfig(cloudName: String, preset: String) {
        _cloudinaryCloudName.value = cloudName
        _cloudinaryUploadPreset.value = preset
        prefs.edit()
            .putString("cloud_name", cloudName)
            .putString("upload_preset", preset)
            .apply()
    }

    fun registerUserProfile(name: String, avatarUrl: String, role: String, pinCode: String): String? {
        // Validate Admin/Mod pin
        var finalRole = "User"
        if (role == "Admin") {
            if (pinCode == _adminPassword.value) {
                finalRole = "Admin"
            } else {
                return "Invalid Admin PIN code!"
            }
        } else if (role == "Mod") {
            if (pinCode == _modPassword.value) {
                finalRole = "Mod"
            } else {
                return "Invalid Moderator PIN code!"
            }
        }

        val id = prefs.getString("user_id", "") ?: UUID.randomUUID().toString()
        val user = User(
            id = id,
            name = name,
            avatarUrl = avatarUrl,
            role = finalRole
        )

        prefs.edit()
            .putString("user_id", id)
            .putString("user_name", name)
            .putString("avatar_url", avatarUrl)
            .putString("role", finalRole)
            .apply()

        _currentUser.value = user
        
        // Save user profile directly in Firestore
        viewModelScope.launch {
            try {
                db.collection("global_users").document(id).set(user).await()
                observeDatabase()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error saving profile", e)
            }
        }
        return null
    }

    fun logout() {
        val user = _currentUser.value
        if (user != null) {
            viewModelScope.launch {
                try {
                    db.collection("global_users").document(user.id).update("lastActive", 0L).await()
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error signing out user online state", e)
                }
            }
        }

        prefs.edit()
            .remove("user_id")
            .remove("user_name")
            .remove("avatar_url")
            .remove("role")
            .apply()

        _currentUser.value = null
        stopObserving()
    }

    private fun observeDatabase() {
        stopObserving()
        val userId = _currentUser.value?.id ?: return

        // 1. Keep user online state updated periodically and handle ban/mute state verification
        viewModelScope.launch {
            try {
                // Fetch initially or create
                val doc = db.collection("global_users").document(userId).get().await()
                if (doc.exists()) {
                    val firestoreUser = doc.toObject(User::class.java)
                    if (firestoreUser != null) {
                        // Keep roles in sync or update local
                        if (firestoreUser.isBanned) {
                            _currentUser.value = firestoreUser
                            return@launch
                        }
                        _currentUser.value = firestoreUser.copy(lastActive = System.currentTimeMillis())
                        db.collection("global_users").document(userId).set(_currentUser.value!!).await()
                    }
                } else {
                    db.collection("global_users").document(userId).set(_currentUser.value!!).await()
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to sync user online state", e)
            }
        }

        // 2. Listen to users list (for typing, online, banned, muted status)
        usersListener = db.collection("global_users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val list = snapshot?.toObjects(User::class.java) ?: emptyList()
                
                // Active online user threshold (active in last 5 minutes)
                val fiveMinutesAgo = System.currentTimeMillis() - 5 * 60 * 1000
                _onlineUsers.value = list.filter { it.lastActive > fiveMinutesAgo && !it.isBanned }
                _bannedUsersList.value = list.filter { it.isBanned }
                _mutedUsersList.value = list.filter { it.isMuted }

                // Check if current user gets banned or muted in real-time
                val currentInDb = list.find { it.id == userId }
                if (currentInDb != null) {
                    _currentUser.value = currentInDb
                }
            }

        // 3. Listen to messages (latest 100)
        messagesListener = db.collection("global_chat")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatViewModel", "Messages error", error)
                    return@addSnapshotListener
                }
                _messages.value = snapshot?.toObjects(Message::class.java) ?: emptyList()
            }

        // 4. Listen to typing indicators
        typingListener = db.collection("global_typing_states")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val typing = ArrayList<String>()
                snapshot?.documents?.forEach { doc ->
                    val isTyping = doc.getBoolean("isTyping") ?: false
                    val userName = doc.getString("userName") ?: ""
                    val tUserId = doc.id
                    val lastActive = doc.getLong("lastActive") ?: 0L
                    
                    // Filter out current user and stale states (older than 4s)
                    if (isTyping && tUserId != userId && (System.currentTimeMillis() - lastActive < 4000)) {
                        typing.add(userName)
                    }
                }
                _typingUsers.value = typing
            }

        // 5. Listen to call sessions
        callListener = db.collection("global_calls")
            .document("active_call")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                handleCallDocumentUpdate(snapshot)
            }

        // 6. Listen to moderation logs
        logsListener = db.collection("moderation_logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                _moderationLogs.value = snapshot?.toObjects(ModerationLog::class.java) ?: emptyList()
            }
    }

    private fun stopObserving() {
        messagesListener?.remove()
        usersListener?.remove()
        typingListener?.remove()
        callListener?.remove()
        callerCandidatesListener?.remove()
        receiverCandidatesListener?.remove()
        logsListener?.remove()
    }

    // --- Media & Message Sending ---

    fun updateTypingState(isTyping: Boolean) {
        val user = _currentUser.value ?: return
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            try {
                db.collection("global_typing_states").document(user.id).set(
                    mapOf(
                        "isTyping" to isTyping,
                        "userName" to user.name,
                        "lastActive" to System.currentTimeMillis()
                    )
                ).await()
                
                if (isTyping) {
                    delay(2500)
                    updateTypingState(false)
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Typing update failed", e)
            }
        }
    }

    suspend fun uploadPfp(uri: Uri): String {
        return CloudinaryUploader.uploadImage(
            context = getApplication(),
            uri = uri,
            cloudName = _cloudinaryCloudName.value,
            uploadPreset = _cloudinaryUploadPreset.value
        )
    }

    fun sendMessage(text: String, imageUri: Uri? = null, onUploadProgress: (Boolean) -> Unit = {}) {
        val user = _currentUser.value ?: return
        if (user.isBanned) return
        if (user.isMuted) {
            // Check if mute is temporary and expired
            if (user.mutedUntil > 0L && System.currentTimeMillis() > user.mutedUntil) {
                // Unmute automatically
                viewModelScope.launch {
                    try {
                        db.collection("global_users").document(user.id).update("isMuted", false, "mutedUntil", 0L).await()
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Auto-unmute failed", e)
                    }
                }
            } else {
                return // Still muted
            }
        }

        viewModelScope.launch {
            try {
                var mediaUrl = ""
                if (imageUri != null) {
                    onUploadProgress(true)
                    mediaUrl = CloudinaryUploader.uploadImage(
                        context = getApplication(),
                        uri = imageUri,
                        cloudName = _cloudinaryCloudName.value,
                        uploadPreset = _cloudinaryUploadPreset.value
                    )
                    onUploadProgress(false)
                }

                if (text.trim().isEmpty() && mediaUrl.isEmpty()) return@launch

                val msgId = db.collection("global_chat").document().id
                val message = Message(
                    id = msgId,
                    senderId = user.id,
                    senderName = user.name,
                    senderAvatarUrl = user.avatarUrl,
                    senderRole = user.role,
                    text = text,
                    mediaUrl = mediaUrl,
                    timestamp = System.currentTimeMillis()
                )

                db.collection("global_chat").document(msgId).set(message).await()
                updateTypingState(false)
            } catch (e: Exception) {
                onUploadProgress(false)
                Log.e("ChatViewModel", "Error sending message", e)
            }
        }
    }

    // --- WebRTC Calling Logic ---

    fun initiateCall(receiver: User, callType: String = "video") {
        val caller = _currentUser.value ?: return
        if (isCallingActive) return
        isCallingActive = true
        _callStateText.value = "Initiating WebRTC stream..."

        viewModelScope.launch {
            try {
                // Initialize WebRTC engine locally
                webRtcManager.startLocalStream(videoEnabled = (callType == "video"), audioEnabled = true)
                _isCameraEnabled.value = (callType == "video")
                _isMicrophoneEnabled.value = true

                webRtcManager.initPeerConnection { iceCandidate ->
                    sendIceCandidate(iceCandidate, isCaller = true)
                }

                // Generate SDP Offer
                webRtcManager.createOffer { sdpOffer ->
                    val session = CallSession(
                        id = UUID.randomUUID().toString(),
                        callerId = caller.id,
                        callerName = caller.name,
                        callerAvatar = caller.avatarUrl,
                        receiverId = receiver.id,
                        receiverName = receiver.name,
                        receiverAvatar = receiver.avatarUrl,
                        status = "ringing",
                        callType = callType,
                        offerSdp = sdpOffer.description
                    )

                    viewModelScope.launch {
                        // Clear old candidates
                        clearCandidateSubcollections()
                        // Publish call offer
                        db.collection("global_calls").document("active_call").set(session).await()
                        _activeCall.value = session
                        _callStateText.value = "Ringing receiver..."
                        
                        // Observe receiver candidates
                        observeIceCandidates(isCaller = true)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Call setup error", e)
                endCallSession()
            }
        }
    }

    fun acceptIncomingCall() {
        val call = _activeCall.value ?: return
        _callStateText.value = "Connecting call..."
        isCallingActive = true

        viewModelScope.launch {
            try {
                webRtcManager.startLocalStream(videoEnabled = (call.callType == "video"), audioEnabled = true)
                _isCameraEnabled.value = (call.callType == "video")
                _isMicrophoneEnabled.value = true

                webRtcManager.initPeerConnection { iceCandidate ->
                    sendIceCandidate(iceCandidate, isCaller = false)
                }

                // Answer offer
                webRtcManager.handleOffer(call.offerSdp) { sdpAnswer ->
                    viewModelScope.launch {
                        db.collection("global_calls").document("active_call")
                            .update(
                                "status", "accepted",
                                "answerSdp", sdpAnswer.description
                            ).await()
                        _callStateText.value = "Active"
                        observeIceCandidates(isCaller = false)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Accept call error", e)
                declineIncomingCall()
            }
        }
    }

    fun declineIncomingCall() {
        viewModelScope.launch {
            try {
                db.collection("global_calls").document("active_call").update("status", "rejected").await()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Decline call error", e)
            } finally {
                endCallSession()
            }
        }
    }

    fun hangUpCall() {
        viewModelScope.launch {
            try {
                db.collection("global_calls").document("active_call").update("status", "ended").await()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Hangup call error", e)
            } finally {
                endCallSession()
            }
        }
    }

    private fun endCallSession() {
        isCallingActive = false
        webRtcManager.endCall()
        _activeCall.value = null
        _callStateText.value = "Call Ended"
        
        callerCandidatesListener?.remove()
        receiverCandidatesListener?.remove()
        
        // Quietly delete session document if we are the caller to cleanup
        val caller = _currentUser.value
        val call = _activeCall.value
        if (call != null && caller != null && call.callerId == caller.id) {
            db.collection("global_calls").document("active_call").delete()
        }
    }

    private fun handleCallDocumentUpdate(snapshot: DocumentSnapshot?) {
        val session = snapshot?.toObject(CallSession::class.java)
        if (session == null) {
            // Document deleted, if we were in a call, close it
            if (_activeCall.value != null) {
                endCallSession()
            }
            return
        }

        val myId = _currentUser.value?.id ?: return
        
        // Verify if I am part of this call (Caller or Receiver)
        if (session.callerId != myId && session.receiverId != myId) {
            return
        }

        val previousCall = _activeCall.value
        _activeCall.value = session

        when (session.status) {
            "ringing" -> {
                if (session.callerId == myId) {
                    _callStateText.value = "Calling ${session.receiverName}..."
                } else {
                    _callStateText.value = "Incoming ${session.callType} call from ${session.callerName}!"
                }
            }
            "accepted" -> {
                _callStateText.value = "Active Connection"
                // If I am the caller, I receive B's SDP Answer
                if (session.callerId == myId && previousCall?.status == "ringing") {
                    Log.d("ChatViewModel", "Setting remote SDP Answer")
                    webRtcManager.handleAnswer(session.answerSdp)
                }
            }
            "rejected" -> {
                _callStateText.value = "Call Rejected"
                viewModelScope.launch {
                    delay(1500)
                    endCallSession()
                }
            }
            "ended" -> {
                _callStateText.value = "Call Terminated"
                viewModelScope.launch {
                    delay(1500)
                    endCallSession()
                }
            }
        }
    }

    private fun sendIceCandidate(candidate: IceCandidate, isCaller: Boolean) {
        val call = _activeCall.value ?: return
        val subCollection = if (isCaller) "caller_candidates" else "receiver_candidates"
        
        val item = IceCandidateModel(
            id = UUID.randomUUID().toString(),
            sdpMid = candidate.sdpMid,
            sdpMLineIndex = candidate.sdpMLineIndex,
            sdp = candidate.sdp,
            senderId = _currentUser.value?.id ?: ""
        )

        db.collection("global_calls")
            .document("active_call")
            .collection(subCollection)
            .document(item.id)
            .set(item)
    }

    private fun observeIceCandidates(isCaller: Boolean) {
        // If Caller, listen to B's Candidates ("receiver_candidates")
        // If Receiver, listen to A's Candidates ("caller_candidates")
        val targetSubcollection = if (isCaller) "receiver_candidates" else "caller_candidates"
        
        val listener = db.collection("global_calls")
            .document("active_call")
            .collection(targetSubcollection)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val model = change.document.toObject(IceCandidateModel::class.java)
                        val rtcCandidate = IceCandidate(model.sdpMid, model.sdpMLineIndex, model.sdp)
                        webRtcManager.addIceCandidate(rtcCandidate)
                    }
                }
            }

        if (isCaller) {
            receiverCandidatesListener = listener
        } else {
            callerCandidatesListener = listener
        }
    }

    private suspend fun clearCandidateSubcollections() {
        try {
            val callers = db.collection("global_calls").document("active_call").collection("caller_candidates").get().await()
            callers.documents.forEach { it.reference.delete() }
            val receivers = db.collection("global_calls").document("active_call").collection("receiver_candidates").get().await()
            receivers.documents.forEach { it.reference.delete() }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error cleaning candidates", e)
        }
    }

    fun toggleCamera() {
        val enabled = !_isCameraEnabled.value
        _isCameraEnabled.value = enabled
        webRtcManager.toggleCamera(enabled)
    }

    fun toggleMicrophone() {
        val enabled = !_isMicrophoneEnabled.value
        _isMicrophoneEnabled.value = enabled
        webRtcManager.toggleMic(enabled)
    }

    fun switchCamera() {
        webRtcManager.switchCamera()
    }

    // --- Moderation Functions (Admin / Mod) ---

    fun deleteMessage(message: Message) {
        val modUser = _currentUser.value ?: return
        if (modUser.role != "Admin" && modUser.role != "Mod") return

        viewModelScope.launch {
            try {
                db.collection("global_chat").document(message.id)
                    .update("isDeleted", true, "deletedBy", modUser.name)
                    .await()
                
                logModerationAction(
                    action = "DELETE_MSG",
                    targetUserId = message.senderId,
                    targetUserName = message.senderName,
                    reason = "Inappropriate content"
                )
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error deleting msg", e)
            }
        }
    }

    fun muteUser(target: User, durationMinutes: Int = 10, reason: String = "Spamming") {
        val modUser = _currentUser.value ?: return
        if (modUser.role != "Admin" && modUser.role != "Mod") return

        val muteEndTime = System.currentTimeMillis() + durationMinutes * 60 * 1000

        viewModelScope.launch {
            try {
                db.collection("global_users").document(target.id)
                    .update("isMuted", true, "mutedUntil", muteEndTime)
                    .await()

                logModerationAction(
                    action = "MUTE",
                    targetUserId = target.id,
                    targetUserName = target.name,
                    reason = "$reason ($durationMinutes minutes)"
                )
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Mute user error", e)
            }
        }
    }

    fun unmuteUser(target: User) {
        val modUser = _currentUser.value ?: return
        if (modUser.role != "Admin" && modUser.role != "Mod") return

        viewModelScope.launch {
            try {
                db.collection("global_users").document(target.id)
                    .update("isMuted", false, "mutedUntil", 0L)
                    .await()

                logModerationAction(
                    action = "UNMUTE",
                    targetUserId = target.id,
                    targetUserName = target.name,
                    reason = "Mute lifted"
                )
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Unmute user error", e)
            }
        }
    }

    fun banUser(target: User, reason: String = "Policy violation") {
        val modUser = _currentUser.value ?: return
        if (modUser.role != "Admin") return // Ban is Admin-only

        viewModelScope.launch {
            try {
                db.collection("global_users").document(target.id)
                    .update("isBanned", true)
                    .await()

                logModerationAction(
                    action = "BAN",
                    targetUserId = target.id,
                    targetUserName = target.name,
                    reason = reason
                )
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Ban user error", e)
            }
        }
    }

    fun unbanUser(target: User) {
        val modUser = _currentUser.value ?: return
        if (modUser.role != "Admin") return // Unban is Admin-only

        viewModelScope.launch {
            try {
                db.collection("global_users").document(target.id)
                    .update("isBanned", false)
                    .await()

                logModerationAction(
                    action = "UNBAN",
                    targetUserId = target.id,
                    targetUserName = target.name,
                    reason = "Ban lifted"
                )
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Unban user error", e)
            }
        }
    }

    private suspend fun logModerationAction(
        action: String,
        targetUserId: String,
        targetUserName: String,
        reason: String
    ) {
        val modUser = _currentUser.value ?: return
        val logId = db.collection("moderation_logs").document().id
        val log = ModerationLog(
            id = logId,
            action = action,
            targetUserId = targetUserId,
            targetUserName = targetUserName,
            performedByUserId = modUser.id,
            performedByUserName = modUser.name,
            reason = reason,
            timestamp = System.currentTimeMillis()
        )
        db.collection("moderation_logs").document(logId).set(log).await()
    }

    override fun onCleared() {
        super.onCleared()
        stopObserving()
        webRtcManager.clear()
    }
}
