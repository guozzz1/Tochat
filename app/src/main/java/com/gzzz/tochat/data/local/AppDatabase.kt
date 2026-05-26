package com.gzzz.tochat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        PromptTemplateEntity::class,
        ApiConfigEntity::class,
        KnowledgeDocumentEntity::class,
        KnowledgeChunkEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun promptTemplateDao(): PromptTemplateDao
    abstract fun apiConfigDao(): ApiConfigDao
    abstract fun knowledgeDao(): KnowledgeDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN messageType TEXT NOT NULL DEFAULT 'image'")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS api_configs (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        baseUrl TEXT NOT NULL,
                        apiKey TEXT NOT NULL,
                        models TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN attachmentFileName TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE api_configs ADD COLUMN type TEXT NOT NULL DEFAULT 'chat'")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE api_configs ADD COLUMN providerId TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS knowledge_documents (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        mimeType TEXT,
                        contentHash TEXT NOT NULL,
                        charCount INTEGER NOT NULL,
                        chunkCount INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        errorMessage TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_documents_createdAt ON knowledge_documents(createdAt)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_knowledge_documents_contentHash ON knowledge_documents(contentHash)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS knowledge_chunks (
                        id TEXT NOT NULL PRIMARY KEY,
                        documentId TEXT NOT NULL,
                        chunkIndex INTEGER NOT NULL,
                        text TEXT NOT NULL,
                        charStart INTEGER NOT NULL,
                        charEnd INTEGER NOT NULL,
                        tokenCount INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_chunks_documentId ON knowledge_chunks(documentId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_knowledge_chunks_documentId_chunkIndex ON knowledge_chunks(documentId, chunkIndex)")
            }
        }
    }
}
