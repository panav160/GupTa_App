package com.voicecommand.app.correction

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CorrectionDao {

    @Query("SELECT * FROM corrections ORDER BY count DESC")
    fun getAll(): Flow<List<CorrectionEntity>>

    @Query("SELECT * FROM corrections WHERE rawText = :rawText LIMIT 1")
    suspend fun findByRawText(rawText: String): CorrectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(correction: CorrectionEntity)

    @Query("UPDATE corrections SET count = count + 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun incrementCount(id: Long, updatedAt: Long = System.currentTimeMillis())
}
