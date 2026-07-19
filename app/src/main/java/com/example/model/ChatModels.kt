package com.example.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class User(
    val id: String = "",
    val name: String = "",
    val avatarUrl: String = "",
    val role: String = "User", // "Admin", "Mod", "User"
    val isMuted: Boolean = false,
    val isBanned: Boolean = false,
    val mutedUntil: Long = 0L, // timestamp in ms
    val isTyping: Boolean = false,
    val lastActive: Long = System.currentTimeMillis()
)

data class Message(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderAvatarUrl: String = "",
    val senderRole: String = "User", // "Admin", "Mod", "User"
    val text: String = "",
    val mediaUrl: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedBy: String = ""
)

data class CallSession(
    val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val callerAvatar: String = "",
    val receiverId: String = "",
    val receiverName: String = "",
    val receiverAvatar: String = "",
    val status: String = "ringing", // "ringing", "accepted", "rejected", "ended"
    val callType: String = "video", // "video", "audio"
    val offerSdp: String = "",
    val answerSdp: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class IceCandidateModel(
    val id: String = "",
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val sdp: String = "",
    val senderId: String = ""
)

data class ModerationLog(
    val id: String = "",
    val action: String = "", // "BAN", "UNBAN", "MUTE", "UNMUTE", "DELETE_MSG"
    val targetUserId: String = "",
    val targetUserName: String = "",
    val performedByUserId: String = "",
    val performedByUserName: String = "",
    val reason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
