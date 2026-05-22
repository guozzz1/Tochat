package com.gzzz.toimage.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChatSessionEntity::class, ChatMessageEntity::class, PromptTemplateEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun promptTemplateDao(): PromptTemplateDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN messageType TEXT NOT NULL DEFAULT 'image'")
            }
        }
    }
}
