package com.example

import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.TranscriptionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TranscriptionDaoTest {

    @Test fun `peaksJson round-trips with the entity`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val dao = db.transcriptionDao()
            val peaks = "[0.0,0.5,1.0]"
            dao.insertTranscription(
                TranscriptionEntity(
                    title = "t",
                    fileName = "f.mp3",
                    fileSize = 1L,
                    mimeType = "audio/mpeg",
                    transcriptText = "x",
                    modelUsed = "m",
                    peaksJson = peaks
                )
            )
            val all = dao.getAllTranscriptions().first()
            assertEquals(1, all.size)
            assertEquals(peaks, all[0].peaksJson)
        } finally {
            db.close()
        }
    }
}
