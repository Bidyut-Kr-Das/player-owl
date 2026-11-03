package com.quietplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryTest {
    private fun video(path: String, modified: Long, size: Long = 1) =
        Video(id = modified, name = "$modified.mp4", folderPath = path, durationMs = 1, size = size, modified = modified)

    @Test fun `videos in the same path form one folder`() {
        val folders = toFolders(listOf(video("Download/", 1), video("Download/", 2)))
        assertEquals(1, folders.size)
        assertEquals(2, folders[0].videos.size)
    }

    @Test fun `folder name is the last path segment`() {
        assertEquals("Shows", toFolders(listOf(video("Movies/Shows/", 1)))[0].name)
    }

    @Test fun `videos at storage root get a readable name`() {
        assertEquals("Internal storage", toFolders(listOf(video("", 1)))[0].name)
    }

    @Test fun `same folder name in different paths stays separate`() {
        assertEquals(2, toFolders(listOf(video("A/Clips/", 1), video("B/Clips/", 2))).size)
    }

    @Test fun `most recently changed folder comes first`() {
        val folders = toFolders(listOf(video("Old/", 1), video("Download/", 9), video("Mid/", 5)))
        assertEquals(listOf("Download", "Mid", "Old"), folders.map { it.name })
    }

    @Test fun `videos sort newest first inside a folder`() {
        val folder = toFolders(listOf(video("D/", 1), video("D/", 3), video("D/", 2)))[0]
        assertEquals(listOf(3L, 2L, 1L), folder.videos.map { it.modified })
    }

    @Test fun `folder total size adds its videos`() {
        assertEquals(30L, toFolders(listOf(video("D/", 1, size = 10), video("D/", 2, size = 20)))[0].totalSize)
    }
}
