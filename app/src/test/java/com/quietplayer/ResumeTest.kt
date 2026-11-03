package com.quietplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeTest {
    private val movie = "content://media/external/video/media/1"
    private val other = "content://media/external/video/media/2"
    private val hour = 3_600_000L

    @Test fun `closing a video remembers its position`() {
        assertEquals(Resume(movie, 600_000L), nextResume(null, movie, 600_000L, hour))
    }

    @Test fun `only one video is remembered`() {
        assertEquals(Resume(other, 60_000L), nextResume(Resume(movie, 600_000L), other, 60_000L, hour))
    }

    @Test fun `closing before the duration is known keeps the memory`() {
        val saved = Resume(movie, 600_000L)
        assertEquals(saved, nextResume(saved, movie, 600_000L, -1L))
    }

    @Test fun `finishing a video forgets it so it restarts next time`() {
        assertEquals(null, nextResume(Resume(movie, 600_000L), movie, hour, hour))
    }

    @Test fun `closing in the last seconds counts as finished`() {
        assertEquals(null, nextResume(Resume(movie, 600_000L), movie, hour - 4_000L, hour))
    }

    @Test fun `finishing a video keeps the memory of another one`() {
        val saved = Resume(movie, 600_000L)
        assertEquals(saved, nextResume(saved, other, hour, hour))
    }

    @Test fun `opening another video by mistake keeps the memory`() {
        val saved = Resume(movie, 600_000L)
        assertEquals(saved, nextResume(saved, other, 1_000L, hour))
    }

    @Test fun `seeking the same video back to the start is remembered`() {
        assertEquals(Resume(movie, 1_000L), nextResume(Resume(movie, 600_000L), movie, 1_000L, hour))
    }

    @Test fun `start position comes from the memory of the same video`() {
        assertEquals(600_000L, startPosition(Resume(movie, 600_000L), movie))
    }

    @Test fun `another video starts from the beginning`() {
        assertEquals(0L, startPosition(Resume(movie, 600_000L), other))
    }

    @Test fun `no memory starts from the beginning`() {
        assertEquals(0L, startPosition(null, movie))
    }

    @Test fun `indexed video missing from the index is deleted`() {
        assertEquals(true, isDeleted(movie, setOf(other), "content://media/external/video/media"))
    }

    @Test fun `indexed video still in the index is not deleted`() {
        assertEquals(false, isDeleted(movie, setOf(movie), "content://media/external/video/media"))
    }

    @Test fun `video opened from a file manager is not judged by the index`() {
        val fromFileManager = "content://com.files.provider/clip.mkv"
        assertEquals(false, isDeleted(fromFileManager, emptySet(), "content://media/external/video/media"))
    }
}
