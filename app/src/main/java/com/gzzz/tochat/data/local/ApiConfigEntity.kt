package com.gzzz.tochat.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_configs")
data class ApiConfigEntity(
    @PrimaryKey
    val id: String,
    val name: String,           // 厂商名
    val baseUrl: String,        // API 地址
    val apiKey: String,         // API Key
    val models: String = "",    // JSON 数组字符串，如 ["gpt-4o","gpt-4o-mini"]
    val type: String = "chat",  // "chat" 或 "image"
    val providerId: String? = null,
    val chatPath: String = "v1/chat/completions",
    val chatProtocol: String = "chat_completions",
    val createdAt: Long = System.currentTimeMillis()
)
