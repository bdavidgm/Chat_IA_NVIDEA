package com.bdavidgm.glm_chat.data.local

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bdavidgm.glm_chat.data.MessageRole

@Keep
@Entity(tableName = "chat_threads")
data class ChatThread(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastMessageAt: Long = System.currentTimeMillis()
)

@Keep
@Entity(tableName = "messages")
data class LocalMessage(
    @PrimaryKey val id: String,
    val threadId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String? = null,
    val imageBase64: String? = null,
    val imageType: String? = null,
    val model: String? = null,
)
