package com.example

import java.io.File

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.robolectric.shadows.ShadowMediaPlayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.onNodeWithContentDescription
import org.robolectric.shadows.util.DataSource
import androidx.compose.ui.test.doubleClick
import com.example.data.SegmentUtils
import com.example.data.TranscriptionEntity
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class DetailViewScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  private var createdPlayer: android.media.MediaPlayer? = null

  @Test fun `DetailView shows all paras for long transcript`() {
    val longText = (1..40).joinToString("\n\n") { "Parágrafo $it" }

    // Logical layer: displayParas / timedParagraphs size must be 40
    val displayParas = SegmentUtils.splitParagraphs(longText)
    assertEquals("displayParas should be 40 for 40-paragraph transcript", 40, displayParas.size)

    val timedParagraphs = SegmentUtils.buildTimedParagraphs(displayParas, null, 326_000)
    assertEquals("timedParagraphs should be 40", 40, timedParagraphs.size)

    // UI layer: render DetailView with entity transcriptText = 40 paras and assert LazyColumn shows all
    val entity = TranscriptionEntity(
      title = "Test 5:26",
      fileName = "test.mp3",
      fileSize = 1234L,
      mimeType = "audio/mpeg",
      transcriptText = longText,
      modelUsed = "test-model",
      audioDurationMs = 326_000,
      audioUri = null,
      segmentsJson = null
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        DetailView(
          entity = entity,
          onDelete = {},
          onRename = {},
          onUpdateText = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    // Container exists
    composeTestRule.onNodeWithTag("transcript_body_text").assertExists()

    // First paragraph visible
    composeTestRule.onNodeWithTag("paragraph_0").assertExists()
    composeTestRule.onNodeWithTag("paragraph_0").assertIsDisplayed()

    // Verify all 40 can be scrolled into view (LazyColumn virtualization requires scroll)
    // Scroll to last index and assert it appears. This proves LazyColumn holds 40 items, not clipped to 10.
    try {
      composeTestRule.onNodeWithTag("transcript_body_text").performScrollToIndex(39)
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("paragraph_39").assertExists()
      composeTestRule.onNodeWithTag("paragraph_39").assertIsDisplayed()
    } catch (_: AssertionError) {
      // Fallback: at least check via semantics that timed count is 40 (already asserted logically)
      // and that mid-point paragraph can be found after scroll
      throw AssertionError("DetailView LazyColumn should contain 40 paragraphs but paragraph_39 not found after scroll — UI clips transcript")
    }

    // Extra sanity: paragraph_19 (mid) should also be reachable
    composeTestRule.onNodeWithTag("transcript_body_text").performScrollToIndex(19)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("paragraph_19").assertExists()
  }
  private fun entityWithAudio(): TranscriptionEntity {
    val tmp = File.createTempFile("fake-audio", ".mp3").apply { deleteOnExit() }
    ShadowMediaPlayer.addMediaInfo(
      DataSource.toDataSource(tmp.absolutePath),
      ShadowMediaPlayer.MediaInfo(326_000, 0)
    )
    return TranscriptionEntity(
      title = "Test audio",
      fileName = "test.mp3",
      fileSize = 1234L,
      mimeType = "audio/mpeg",
      transcriptText = "Parágrafo um.\n\nParágrafo dois.",
      modelUsed = "test-model",
      audioDurationMs = 326_000,
      audioUri = "file://${tmp.absolutePath}",
      segmentsJson = null
    )
  }
  private fun showPlayer() {
    composeTestRule.setContent {
      MyApplicationTheme {
        DetailView(entity = entityWithAudio(), onDelete = {}, onRename = {}, onUpdateText = {})
      }
    }
    composeTestRule.waitForIdle()
  }
  @Test fun `PlayerCard keeps test tag in read mode`() {
    showPlayer()
    composeTestRule.onNodeWithTag("player_card").assertExists()
  }

  @Test fun `PlayerCard stays composed after entering edit mode`() {
    showPlayer()
    composeTestRule.onNodeWithTag("expand_text_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("player_card").assertExists()
  }

  @Test fun `Skip forward moves position by 5 seconds`() {
    showPlayer()
    composeTestRule.onNodeWithTag("skip_forward_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("00:05").assertExists()
  }

  @Test fun `Speed button walks all six speeds and back to 1x`() {
    showPlayer()
    val speed = composeTestRule.onNodeWithTag("speed_button")
    speed.performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("1.15x").assertExists()
    speed.performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("1.25x").assertExists()
    speed.performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("1.5x").assertExists()
    speed.performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("0.75x").assertExists()
    speed.performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("0.85x").assertExists()
    speed.performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("1x").assertExists()
  }
  @Test fun `Skip back from 5s returns to zero`() {
    showPlayer()
    composeTestRule.onNodeWithTag("skip_forward_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("skip_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("00:00").assertExists()
  }

  @Test fun `Compact edit mode keeps skip and speed in reach`() {
    showEditor()
    composeTestRule.onNodeWithTag("player_card").assertExists()
    composeTestRule.onNodeWithTag("skip_back_button").assertExists()
    composeTestRule.onNodeWithTag("skip_forward_button").assertExists()
    composeTestRule.onNodeWithTag("speed_button").assertExists()
  }

  @Test fun `resume after pause goes 1_5s back`() {
    showPlayer()
    composeTestRule.onNodeWithTag("skip_forward_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("play_pause_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("play_pause_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("play_pause_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("00:03").assertExists()
  }

  @Test fun `moving while paused cancels the retrocesso`() {
    showPlayer()
    composeTestRule.onNodeWithTag("skip_forward_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("skip_forward_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("play_pause_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("play_pause_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("skip_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("play_pause_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("00:05").assertExists()
  }

  @Test fun `paragraph with word timings renders full text`() {
    val segmentsJson = """[{"id":0,"seek":0,"start":0.0,"end":30.0,"text":"alpha beta gama","words":[{"word":"alpha","start":0.0,"end":10.0},{"word":"beta","start":10.0,"end":20.0},{"word":"gama","start":20.0,"end":30.0}]}]"""
    val entity = TranscriptionEntity(
      title = "Test words",
      fileName = "test.mp3",
      fileSize = 1234L,
      mimeType = "audio/mpeg",
      transcriptText = "alpha beta gama",
      modelUsed = "test-model",
      audioDurationMs = 30_000,
      audioUri = null,
      segmentsJson = segmentsJson
    )
    composeTestRule.setContent {
      MyApplicationTheme {
        DetailView(entity = entity, onDelete = {}, onRename = {}, onUpdateText = {})
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("paragraph_0").assertExists()
    composeTestRule.onNodeWithText("alpha beta gama").assertExists()
  }

  private fun entityWithPeaks(): TranscriptionEntity {
    val tmp = File.createTempFile("fake-audio", ".mp3").apply { deleteOnExit() }
    ShadowMediaPlayer.addMediaInfo(
      DataSource.toDataSource(tmp.absolutePath),
      ShadowMediaPlayer.MediaInfo(326_000, 0)
    )
    return TranscriptionEntity(
      title = "Test peaks",
      fileName = "test.mp3",
      fileSize = 1234L,
      mimeType = "audio/mpeg",
      transcriptText = "Parágrafo um.",
      modelUsed = "test-model",
      audioDurationMs = 326_000,
      audioUri = "file://${tmp.absolutePath}",
      segmentsJson = null,
      peaksJson = "[0.1,0.3,0.2,0.8,0.9,0.4,0.1,0.0,0.2,0.6,0.7,0.3]"
    )
  }
  private fun showWaveform() {
    composeTestRule.setContent {
      MyApplicationTheme {
        DetailView(entity = entityWithPeaks(), onDelete = {}, onRename = {}, onUpdateText = {})
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test fun `waveform renders when peaks exist`() {
    showWaveform()
    composeTestRule.onNodeWithTag("waveform_bar").assertExists()
  }

  @Test fun `no waveform without peaks`() {
    showPlayer()
    composeTestRule.onNodeWithTag("waveform_bar").assertDoesNotExist()
  }

  @Test fun `tap center seeks to the middle`() {
    showWaveform()
    composeTestRule.onNodeWithTag("waveform_bar").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("02:43").assertExists()
  }

  @Test fun `drag across scrubs to the drop point`() {
    showWaveform()
    val node = composeTestRule.onNodeWithTag("waveform_bar")
    val size = node.fetchSemanticsNode().size
    node.performTouchInput {
      swipe(
        Offset(size.width * 0.1f, size.height / 2f),
        Offset(size.width * 0.9f, size.height / 2f)
      )
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("04:53").assertExists()
  }
  @Test fun `waveform drag releases seeking so player never freezes`() {
    // Regressão: o arraste marcava seeking e nunca fechava — o tempo congelava com o áudio rolando.
    showWaveform()
    val node = composeTestRule.onNodeWithTag("waveform_bar")
    val size = node.fetchSemanticsNode().size
    node.performTouchInput {
      swipe(
        Offset(size.width * 0.1f, size.height / 2f),
        Offset(size.width * 0.9f, size.height / 2f)
      )
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("04:53").assertExists()
    composeTestRule.onNodeWithTag("skip_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("04:48").assertExists()
  }

  @Test fun `loop button stays hidden while logic is preserved`() {
    // Laco oculto temporariamente na UI; logica segue coberta em PlayerLogicTest.
    showWaveform()
    composeTestRule.onNodeWithTag("loop_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("waveform_bar").assertExists()
  }

  @Test fun `player title shows once without duplicated duration`() {
    showPlayer()
    composeTestRule.onNodeWithTag("player_title").assertExists()
    composeTestRule.onNodeWithText("Áudio original").assertExists()
    composeTestRule.onNodeWithText("Áudio original ·", substring = true).assertDoesNotExist()
  }

  @Test fun `timeline second line splits current and total`() {
    showPlayer()
    composeTestRule.onNodeWithTag("time_current").assertExists()
    composeTestRule.onNodeWithTag("time_total").assertExists()
    composeTestRule.onNodeWithText("05:26").assertExists()
  }

  @Test fun `transcript header never clips first paragraph`() {
    showPlayer()
    composeTestRule.onNodeWithTag("transcript_header").assertExists()
    composeTestRule.onNodeWithTag("paragraph_0").assertExists()
    composeTestRule.onNodeWithTag("paragraph_0").assertIsDisplayed()
  }


  private fun waveNode() = composeTestRule.onNodeWithTag("waveform_bar")

  private fun waveSize() = waveNode().fetchSemanticsNode().size

  @Test fun `double-tap left goes back 5s from current`() {
    showWaveform()
    repeat(2) {
      composeTestRule.onNodeWithTag("skip_forward_button").performClick()
      composeTestRule.waitForIdle()
    }
    val size = waveSize()
    waveNode().performTouchInput { doubleClick(Offset(size.width * 0.1f, size.height / 2f)) }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("00:05").assertExists()
  }

  @Test fun `double-tap right advances 5s from current`() {
    showWaveform()
    repeat(2) {
      composeTestRule.onNodeWithTag("skip_forward_button").performClick()
      composeTestRule.waitForIdle()
    }
    val size = waveSize()
    waveNode().performTouchInput { doubleClick(Offset(size.width * 0.9f, size.height / 2f)) }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("00:15").assertExists()
  }

  @Test fun `double-tap center toggles play`() {
    showWaveform()
    composeTestRule.onNodeWithTag("waveform_bar").performTouchInput { doubleClick() }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithContentDescription("Tocar").assertDoesNotExist()
  }

  @Test fun `text taps stay plain seeks without gesture math`() {
    showWaveform()
    repeat(2) {
      composeTestRule.onNodeWithTag("skip_forward_button").performClick()
      composeTestRule.waitForIdle()
    }
    composeTestRule.onNodeWithTag("paragraph_0").performTouchInput { doubleClick() }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("00:00").assertExists()
    composeTestRule.onNodeWithContentDescription("Tocar").assertExists()
  }

  @Test fun `tapping a paragraph seeks without autoplaying`() {
    showWaveform()
    composeTestRule.onNodeWithTag("paragraph_0").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("00:00").assertExists()
    composeTestRule.onNodeWithContentDescription("Tocar").assertExists()
  }

  @Test fun `opening the transcription never starts audio by itself`() {
    // Regressão: a tela abre pausada em 00:00 e nada deve começar a tocar sem
    // o toque no Play, nem por efeito do player sendo preparado.
    ShadowMediaPlayer.setCreateListener { mp, _ -> createdPlayer = mp }
    showWaveform()
    composeTestRule.waitUntil(timeoutMillis = 5_000) { createdPlayer != null }
    createdPlayer!!.start()
    composeTestRule.mainClock.advanceTimeBy(1_500)
    composeTestRule.waitForIdle()
    assertFalse(
      "abrir a transcrição não pode iniciar o áudio sozinho",
      createdPlayer!!.isPlaying
    )
    composeTestRule.onNodeWithContentDescription("Tocar").assertExists()
  }

  @Test fun `audio resumed by the device while paused is silenced`() {
    // Regressão: alguns aparelhos retomam a reprodução sozinhos depois de um
    // seekTo em estado Prepared. O som tocava sem ninguém pedir e a UI, que
    // continuava "pausada", congelava com o áudio rolando.
    ShadowMediaPlayer.setCreateListener { mp, _ -> createdPlayer = mp }
    showWaveform()
    composeTestRule.waitUntil(timeoutMillis = 5_000) { createdPlayer != null }
    composeTestRule.onNodeWithTag("paragraph_0").performClick()
    composeTestRule.mainClock.advanceTimeBy(150)
    createdPlayer!!.start()
    composeTestRule.mainClock.advanceTimeBy(1_000)
    composeTestRule.waitForIdle()
    assertFalse(
      "app deve silenciar áudio iniciado sem pedido do usuário",
      createdPlayer!!.isPlaying
    )
    composeTestRule.onNodeWithContentDescription("Tocar").assertExists()
  }

  @Test fun `hold advances continuously until release`() {
    showWaveform()
    val size = waveSize()
    waveNode().performTouchInput {
      swipe(
        Offset(size.width * 0.75f, size.height / 2f),
        Offset(size.width * 0.75f, size.height / 2f),
        durationMillis = 1200
      )
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("00:00").assertDoesNotExist()
    composeTestRule.onNodeWithText("00:0", substring = true).assertExists()
  }

  private fun showEditor(onUpdateText: (String) -> Unit = {}) {
    composeTestRule.setContent {
      MyApplicationTheme {
        DetailView(entity = entityWithAudio(), onDelete = {}, onRename = {}, onUpdateText = onUpdateText)
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("expand_text_button").performClick()
    composeTestRule.waitForIdle()
  }

  @Test fun `typing then pausing autosaves without pressing any button`() {
    val saved = mutableListOf<String>()
    showEditor(onUpdateText = { saved.add(it) })
    composeTestRule.onNodeWithTag("expanded_text_field").performTextInput(" mais")
    composeTestRule.mainClock.advanceTimeBy(2_500)
    composeTestRule.waitForIdle()
    assertTrue("expected autosave after pause, got $saved", saved.isNotEmpty())
    assertTrue(saved.last().contains("mais"))
    assertTrue(saved.last().contains("Parágrafo um"))
  }

  @Test fun `manual save button is gone in edit mode`() {
    showEditor()
    composeTestRule.onNodeWithTag("expanded_text_field").performTextInput(" mais")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("save_edit_button").assertDoesNotExist()
  }
  @Test fun `leaving screen with pending edit saves`() {
    val saved = mutableListOf<String>()
    var show by mutableStateOf(true)
    composeTestRule.setContent {
      MyApplicationTheme {
        if (show) {
          DetailView(entity = entityWithAudio(), onDelete = {}, onRename = {}, onUpdateText = { saved.add(it) })
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("expand_text_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("expanded_text_field").performTextInput(" x")
    show = false
    composeTestRule.waitForIdle()
    assertTrue("expected save on leave, got $saved", saved.isNotEmpty())
    assertTrue(saved.last().contains("x"))
  }
}
