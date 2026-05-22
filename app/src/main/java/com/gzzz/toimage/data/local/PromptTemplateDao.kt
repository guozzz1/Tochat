package com.gzzz.toimage.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptTemplateDao {

    @Query("SELECT * FROM prompt_templates ORDER BY category, name")
    fun observeAll(): Flow<List<PromptTemplateEntity>>

    @Query("SELECT * FROM prompt_templates WHERE category = :category ORDER BY name")
    fun observeByCategory(category: String): Flow<List<PromptTemplateEntity>>

    @Query("SELECT * FROM prompt_templates WHERE name LIKE '%' || :query || '%' OR prompt LIKE '%' || :query || '%' ORDER BY name")
    fun search(query: String): Flow<List<PromptTemplateEntity>>

    @Query("SELECT DISTINCT category FROM prompt_templates ORDER BY category")
    fun observeCategories(): Flow<List<String>>

    @Query("SELECT * FROM prompt_templates WHERE id = :id")
    suspend fun getById(id: Long): PromptTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: PromptTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<PromptTemplateEntity>)

    @Delete
    suspend fun delete(template: PromptTemplateEntity)

    @Query("DELETE FROM prompt_templates WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM prompt_templates")
    suspend fun count(): Int
}
