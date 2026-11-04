package com.playerowl

import kotlin.math.abs
import kotlin.math.hypot

sealed interface GestureEvent {
    data object Tap : GestureEvent
    data class Volume(val change: Float) : GestureEvent
    data class Brightness(val change: Float) : GestureEvent
    data class Seek(val fraction: Float) : GestureEvent
    data object SeekEnd : GestureEvent
    data object AdjustEnd : GestureEvent
}

/** Turns raw touch points into player gestures. Knows nothing about Android or the player. */
class Gestures(private val slop: Float, private val sideEdge: Float) {
    private enum class Mode { IDLE, PENDING, SEEK, VOLUME, BRIGHTNESS, IGNORED }

    private var mode = Mode.IDLE
    private var startX = 0f
    private var startY = 0f
    private var width = 1f
    private var height = 1f

    fun down(x: Float, y: Float, width: Float, height: Float) {
        mode = Mode.PENDING
        startX = x
        startY = y
        this.width = width
        this.height = height
    }

    fun move(x: Float, y: Float): GestureEvent? {
        if (mode == Mode.PENDING) classify(x - startX, y - startY)
        return when (mode) {
            Mode.SEEK -> GestureEvent.Seek((x - startX) / width)
            Mode.VOLUME -> GestureEvent.Volume((startY - y) / height)
            Mode.BRIGHTNESS -> GestureEvent.Brightness((startY - y) / height)
            else -> null
        }
    }

    fun up(): GestureEvent? = if (mode == Mode.PENDING) finish(GestureEvent.Tap) else cancel()

    /** A second finger or a system takeover. A running drag still ends, so its UI can clean up. */
    fun cancel(): GestureEvent? = when (mode) {
        Mode.SEEK -> finish(GestureEvent.SeekEnd)
        Mode.VOLUME, Mode.BRIGHTNESS -> finish(GestureEvent.AdjustEnd)
        else -> finish(null)
    }

    private fun finish(event: GestureEvent?): GestureEvent? {
        mode = Mode.IDLE
        return event
    }

    private fun classify(dx: Float, dy: Float) {
        if (hypot(dx, dy) <= slop) return
        val startedOnSystemEdge = startY < height * EDGE || startY > height * (1 - EDGE)
        val startedOnSideEdge = startX < sideEdge || startX > width - sideEdge
        mode = when {
            abs(dx) > abs(dy) && startedOnSideEdge -> Mode.IGNORED // Left to the system's back gesture.
            abs(dx) > abs(dy) -> Mode.SEEK
            startedOnSystemEdge -> Mode.IGNORED // The swipe that reveals the system bars.
            startX < width / 2 -> Mode.BRIGHTNESS
            else -> Mode.VOLUME
        }
    }

    private companion object {
        const val EDGE = 0.05f
    }
}
