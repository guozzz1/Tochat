package com.gzzz.toimage.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prompt_templates")
data class PromptTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val prompt: String,
    val negativePrompt: String? = null,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
