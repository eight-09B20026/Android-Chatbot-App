package com.example.chatbot

enum class Role { USER, ASSISTANT, SYSTEM }

data class Message(
    val id: Long,            // 安定ID（時刻やインクリメントでOK）
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
