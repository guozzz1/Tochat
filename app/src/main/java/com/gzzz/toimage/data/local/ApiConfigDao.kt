package com.gzzz.toimage.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiConfigDao {

    @Query("SELECT * FROM api_configs ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ApiConfigEntity>>

    @Query("SELECT * FROM api_configs WHERE type = :type ORDER BY createdAt ASC")
    fun observeByType(type: String): Flow<List<ApiConfigEntity>>

    @Query("SELECT * FROM api_configs ORDER BY createdAt ASC")
    suspend fun getAll(): List<ApiConfigEntity>

    @Query("SELECT * FROM api_configs WHERE type = :type ORDER BY createdAt ASC")
    suspend fun getByType(type: String): List<ApiConfigEntity>

    @Query("SELECT * FROM api_configs WHERE id = :id")
    suspend fun getById(id: String): ApiConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: ApiConfigEntity)

    @Update
    suspend fun update(config: ApiConfigEntity)

    @Query("DELETE FROM api_configs WHERE id = :id")
    suspend fun deleteById(id: String)
}
