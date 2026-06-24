package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcriptions")
data class TranscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val transcriptText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val modelUsed: String,
    val audioUri: String? = null
)
