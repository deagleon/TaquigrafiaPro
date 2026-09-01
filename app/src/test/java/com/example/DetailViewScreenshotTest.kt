package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
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
}
