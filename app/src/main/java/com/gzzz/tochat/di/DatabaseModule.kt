package com.gzzz.tochat.di

import android.content.Context
import androidx.room.Room
import com.gzzz.tochat.data.local.ApiConfigDao
import com.gzzz.tochat.data.local.AppDatabase
import com.gzzz.tochat.data.local.ChatMessageDao
import com.gzzz.tochat.data.local.ChatSessionDao
import com.gzzz.tochat.data.local.KnowledgeDao
import com.gzzz.tochat.data.local.PromptTemplateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "tochat.db"
        ).addMigrations(
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10,
            AppDatabase.MIGRATION_10_11
        )
         .fallbackToDestructiveMigration()
         .build()
    }

    @Provides
    fun provideChatSessionDao(db: AppDatabase): ChatSessionDao = db.chatSessionDao()

    @Provides
    fun provideChatMessageDao(db: AppDatabase): ChatMessageDao = db.chatMessageDao()

    @Provides
    fun providePromptTemplateDao(db: AppDatabase): PromptTemplateDao = db.promptTemplateDao()

    @Provides
    fun provideApiConfigDao(db: AppDatabase): ApiConfigDao = db.apiConfigDao()

    @Provides
    fun provideKnowledgeDao(db: AppDatabase): KnowledgeDao = db.knowledgeDao()
}
