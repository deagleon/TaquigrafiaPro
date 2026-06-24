package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionDao {
    @Query("SELECT * FROM transcriptions ORDER BY timestamp DESC")
    fun getAllTranscriptions(): Flow<List<TranscriptionEntity>>

    @Query("SELECT * FROM transcriptions WHERE id = :id LIMIT 1")
    fun getTranscriptionById(id: Int): Flow<TranscriptionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscription(transcription: TranscriptionEntity): Long

    @Delete
    suspend fun deleteTranscription(transcription: TranscriptionEntity)

    @Query("DELETE FROM transcriptions WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE transcriptions SET title = :newTitle WHERE id = :id")
    suspend fun updateTitle(id: Int, newTitle: String)
}
