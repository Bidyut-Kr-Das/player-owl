package com.playerowl

import android.content.Context

private const val FINISHED_WITHIN_MS = 5_000L
private const val ACCIDENTAL_OPEN_MS = 2_000L

/** The one video the app remembers, and where to pick it up. */
data class Resume(val uri: String, val positionMs: Long)

/** What to remember after closing [uri]. A video that has only just been opened cannot replace the remembered one. */
fun nextResume(saved: Resume?, uri: String, positionMs: Long, durationMs: Long): Resume? {
    val sameVideo = saved?.uri == uri
    return when {
        durationMs <= 0 -> saved
        durationMs - positionMs <= FINISHED_WITHIN_MS -> if (sameVideo) null else saved
        positionMs < ACCIDENTAL_OPEN_MS && !sameVideo -> saved
        else -> Resume(uri, positionMs)
    }
}

fun startPosition(saved: Resume?, uri: String): Long = if (saved?.uri == uri) saved.positionMs else 0L

/** Only media index URIs can be judged by the index. A file manager's URI stays until replaced. */
fun isDeleted(savedUri: String, indexedUris: Set<String>, indexPrefix: String): Boolean =
    savedUri.startsWith(indexPrefix) && savedUri !in indexedUris

private fun Context.resumePrefs() = getSharedPreferences("resume", Context.MODE_PRIVATE)

fun Context.readResume(): Resume? {
    val prefs = resumePrefs()
    return prefs.getString("uri", null)?.let { Resume(it, prefs.getLong("position", 0)) }
}

fun Context.writeResume(resume: Resume?) {
    val editor = resumePrefs().edit().clear()
    if (resume != null) editor.putString("uri", resume.uri).putLong("position", resume.positionMs)
    editor.apply()
}
