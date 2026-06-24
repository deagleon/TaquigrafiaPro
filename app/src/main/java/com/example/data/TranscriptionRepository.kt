package com.example.data

import kotlinx.coroutines.flow.Flow

class TranscriptionRepository(private val transcriptionDao: TranscriptionDao) {
    val allTranscriptions: Flow<List<TranscriptionEntity>> = transcriptionDao.getAllTranscriptions()

    fun getTranscriptionById(id: Int): Flow<TranscriptionEntity?> {
        return transcriptionDao.getTranscriptionById(id)
    }

    suspend fun insert(transcription: TranscriptionEntity): Long {
        return transcriptionDao.insertTranscription(transcription)
    }

    suspend fun delete(transcription: TranscriptionEntity) {
        transcriptionDao.deleteTranscription(transcription)
    }

    suspend fun deleteById(id: Int) {
        transcriptionDao.deleteById(id)
    }

    suspend fun updateTitle(id: Int, title: String) {
        transcriptionDao.updateTitle(id, title)
    }
}
