package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerLogicTest {

    @Test fun `resume goes 1_5s back`() {
        assertEquals(3500, resumeTargetMs(5000))
    }

    @Test fun `resume clamps at zero`() {
        assertEquals(0, resumeTargetMs(1000))
        assertEquals(0, resumeTargetMs(1500))
        assertEquals(0, resumeTargetMs(0))
    }
}
