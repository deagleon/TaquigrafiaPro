package com.example

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertNull

class PlayerLogicTest {

    @Test fun `resume goes 1_5s back`() {
        assertEquals(3500, resumeTargetMs(5000))
    }

    @Test fun `loop repeats only past its end`() {
        val loop = 4000 to 8000
        assertNull(loopRepeatTarget(5000, loop))
        assertNull(loopRepeatTarget(3999, loop))
        assertEquals(4000, loopRepeatTarget(8000, loop))
        assertEquals(4000, loopRepeatTarget(9000, loop))
        assertNull(loopRepeatTarget(5000, null))
    }
    @Test fun `resume clamps at zero`() {
        assertEquals(0, resumeTargetMs(1000))
        assertEquals(0, resumeTargetMs(1500))
        assertEquals(0, resumeTargetMs(0))
    }
    @Test fun `loop select orders and enforces min span`() {
        assertEquals(4000 to 8000, resolveLoopSelect(4000, 8000))
        assertEquals(4000 to 8000, resolveLoopSelect(8000, 4000))
        assertNull(resolveLoopSelect(4000, 4499))
        assertEquals(4000 to 4500, resolveLoopSelect(4000, 4500))
    }
}
