package com.playerowl

import kotlin.math.min
import kotlin.math.roundToInt

private const val FULL_SWIPE_MS = 120_000L

fun seekTarget(startMs: Long, fraction: Float, durationMs: Long): Long {
    if (durationMs <= 0) return startMs
    val span = min(FULL_SWIPE_MS, durationMs)
    return (startMs + (fraction * span).toLong()).coerceIn(0, durationMs)
}

fun adjustLevel(start: Float, change: Float): Float = (start + change).coerceIn(0f, 1f)

/** Meter position from the left of the screen. It hugs the edge opposite the swiping thumb, so the thumb never covers it. */
fun meterX(event: GestureEvent, leftEdge: Float, rightEdge: Float, meterWidth: Float): Float? = when (event) {
    is GestureEvent.Volume -> leftEdge
    is GestureEvent.Brightness -> rightEdge - meterWidth
    else -> null
}

fun fitInside(videoW: Int, videoH: Int, pixelRatio: Float, boxW: Int, boxH: Int): Pair<Int, Int> {
    if (videoW <= 0 || videoH <= 0) return boxW to boxH
    val ratio = videoW * pixelRatio / videoH
    return if (ratio > boxW.toFloat() / boxH) boxW to (boxW / ratio).roundToInt()
    else (boxH * ratio).roundToInt() to boxH
}
