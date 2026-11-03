package com.quietplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerMathTest {
    @Test fun `full width swipe moves two minutes on long video`() {
        assertEquals(720_000L, seekTarget(600_000L, 1f, 3_600_000L))
    }

    @Test fun `full width swipe spans whole video when it is short`() {
        assertEquals(30_000L, seekTarget(0L, 1f, 30_000L))
    }

    @Test fun `seek cannot go before the start`() {
        assertEquals(0L, seekTarget(10_000L, -1f, 3_600_000L))
    }

    @Test fun `seek cannot go past the end`() {
        assertEquals(3_600_000L, seekTarget(3_590_000L, 1f, 3_600_000L))
    }

    @Test fun `unknown duration leaves position alone`() {
        assertEquals(5_000L, seekTarget(5_000L, 1f, 0L))
    }

    @Test fun `level adds change and clamps at both ends`() {
        assertEquals(0.7f, adjustLevel(0.5f, 0.2f), 0.0001f)
        assertEquals(1f, adjustLevel(0.9f, 0.5f), 0f)
        assertEquals(0f, adjustLevel(0.1f, -0.5f), 0f)
    }

    @Test fun `wide video letterboxes in a tall box`() {
        assertEquals(1000 to 500, fitInside(1920, 960, 1f, 1000, 2000))
    }

    @Test fun `tall video pillarboxes in a wide box`() {
        assertEquals(500 to 1000, fitInside(960, 1920, 1f, 2000, 1000))
    }

    @Test fun `pixel aspect ratio widens the video`() {
        assertEquals(1000 to 500, fitInside(1000, 1000, 2f, 1000, 1000))
    }

    @Test fun `unknown video size fills the box`() {
        assertEquals(1000 to 500, fitInside(0, 0, 1f, 1000, 500))
    }

    @Test fun `volume swiped on the right puts its meter at the left edge`() {
        assertEquals(40f, meterX(GestureEvent.Volume(0.1f), 40f, 960f, 30f))
    }

    @Test fun `brightness swiped on the left puts its meter at the right edge`() {
        assertEquals(930f, meterX(GestureEvent.Brightness(0.1f), 40f, 960f, 30f))
    }

    @Test fun `events without a meter do not move it`() {
        assertEquals(null, meterX(GestureEvent.Seek(0.1f), 40f, 960f, 30f))
        assertEquals(null, meterX(GestureEvent.Tap, 40f, 960f, 30f))
        assertEquals(null, meterX(GestureEvent.SeekEnd, 40f, 960f, 30f))
        assertEquals(null, meterX(GestureEvent.AdjustEnd, 40f, 960f, 30f))
    }
}
