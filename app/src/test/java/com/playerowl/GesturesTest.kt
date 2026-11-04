package com.playerowl

import org.junit.Assert.assertEquals
import org.junit.Test

class GesturesTest {
    private val gestures = Gestures(slop = 10f, sideEdge = 40f)

    private fun start(x: Float, y: Float) = gestures.down(x, y, width = 1000f, height = 500f)

    @Test fun `finger lifted without moving is a tap`() {
        start(500f, 250f)
        assertEquals(GestureEvent.Tap, gestures.up())
    }

    @Test fun `movement inside slop is still a tap`() {
        start(500f, 250f)
        assertEquals(null, gestures.move(505f, 255f))
        assertEquals(GestureEvent.Tap, gestures.up())
    }

    @Test fun `vertical drag on right half controls volume`() {
        start(800f, 250f)
        assertEquals(GestureEvent.Volume(0.2f), gestures.move(800f, 150f))
    }

    @Test fun `vertical drag on left half controls brightness`() {
        start(200f, 250f)
        assertEquals(GestureEvent.Brightness(0.2f), gestures.move(200f, 150f))
    }

    @Test fun `dragging down reports a negative change`() {
        start(800f, 100f)
        assertEquals(GestureEvent.Volume(-0.2f), gestures.move(800f, 200f))
    }

    @Test fun `horizontal drag seeks by fraction of width`() {
        start(200f, 250f)
        assertEquals(GestureEvent.Seek(0.25f), gestures.move(450f, 250f))
    }

    @Test fun `drag to the left seeks backwards`() {
        start(800f, 250f)
        assertEquals(GestureEvent.Seek(-0.3f), gestures.move(500f, 250f))
    }

    @Test fun `axis stays locked after the drag is classified`() {
        start(200f, 250f)
        gestures.move(300f, 250f)
        assertEquals(GestureEvent.Seek(0.1f), gestures.move(300f, 50f))
    }

    @Test fun `lifting after a seek drag commits the seek`() {
        start(200f, 250f)
        gestures.move(450f, 250f)
        assertEquals(GestureEvent.SeekEnd, gestures.up())
    }

    @Test fun `lifting after a volume drag reports only the end`() {
        start(800f, 250f)
        gestures.move(800f, 150f)
        assertEquals(GestureEvent.AdjustEnd, gestures.up())
    }

    @Test fun `vertical drag from top system edge is ignored`() {
        start(800f, 5f)
        assertEquals(null, gestures.move(800f, 100f))
        assertEquals(null, gestures.up())
    }

    @Test fun `vertical drag from bottom system edge is ignored`() {
        start(200f, 495f)
        assertEquals(null, gestures.move(200f, 300f))
        assertEquals(null, gestures.up())
    }

    @Test fun `tap in the system edge still counts`() {
        start(500f, 5f)
        assertEquals(GestureEvent.Tap, gestures.up())
    }

    @Test fun `horizontal drag in the system edge still seeks`() {
        start(200f, 5f)
        assertEquals(GestureEvent.Seek(0.1f), gestures.move(300f, 5f))
    }

    @Test fun `horizontal drag from the left side edge does not seek`() {
        start(20f, 250f)
        assertEquals(null, gestures.move(200f, 250f))
        assertEquals(null, gestures.up())
    }

    @Test fun `horizontal drag from the right side edge does not seek`() {
        start(980f, 250f)
        assertEquals(null, gestures.move(800f, 250f))
        assertEquals(null, gestures.up())
    }

    @Test fun `horizontal drag just inside the side edge still seeks`() {
        start(41f, 250f)
        assertEquals(GestureEvent.Seek(0.1f), gestures.move(141f, 250f))
    }

    @Test fun `vertical drag on the left side edge still controls brightness`() {
        start(20f, 250f)
        assertEquals(GestureEvent.Brightness(0.2f), gestures.move(20f, 150f))
    }

    @Test fun `vertical drag on the right side edge still controls volume`() {
        start(980f, 250f)
        assertEquals(GestureEvent.Volume(0.2f), gestures.move(980f, 150f))
    }

    @Test fun `tap on the side edge still counts`() {
        start(20f, 250f)
        assertEquals(GestureEvent.Tap, gestures.up())
    }

    @Test fun `second finger cancels the gesture without a tap`() {
        start(500f, 250f)
        gestures.cancel()
        assertEquals(null, gestures.up())
    }

    @Test fun `cancelling a seek drag still ends it so playback recovers`() {
        start(200f, 250f)
        gestures.move(450f, 250f)
        assertEquals(GestureEvent.SeekEnd, gestures.cancel())
        assertEquals(null, gestures.up())
    }

    @Test fun `moves after cancel are ignored`() {
        start(200f, 250f)
        gestures.cancel()
        assertEquals(null, gestures.move(450f, 250f))
    }
}
