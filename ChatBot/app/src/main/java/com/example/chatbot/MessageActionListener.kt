package com.example.chatbot

interface MessageActionListener {
    fun onCopy(message: Message, position: Int)
    fun onShare(message: Message, position: Int)
    fun onDelete(message: Message, position: Int)
    fun onRegenerate(message: Message, position: Int) { /* 任意: 使わないなら空でOK */ }
}