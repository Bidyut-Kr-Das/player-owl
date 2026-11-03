package com.quietplayer

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.widget.ImageView
import java.util.concurrent.Executors

data class Video(val id: Long, val name: String, val folderPath: String, val durationMs: Long, val size: Long, val modified: Long)
data class Folder(val path: String, val name: String, val videos: List<Video>, val totalSize: Long)

fun toFolders(videos: List<Video>): List<Folder> =
    videos.groupBy { it.folderPath }
        .map { (path, group) ->
            Folder(
                path = path,
                name = path.trimEnd('/').substringAfterLast('/').ifEmpty { "Internal storage" },
                videos = group.sortedByDescending { it.modified },
                totalSize = group.sumOf { it.size },
            )
        }
        .sortedByDescending { it.videos.first().modified }

private val videosUri: Uri by lazy { MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) }

fun videoUri(id: Long): Uri = ContentUris.withAppendedId(videosUri, id)

fun loadVideos(context: Context): List<Video> {
    val columns = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.RELATIVE_PATH,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DATE_MODIFIED,
    )
    return context.contentResolver.query(videosUri, columns, null, null, null)?.use { c ->
        List(c.count) {
            c.moveToNext()
            Video(
                id = c.getLong(0),
                name = c.getString(1).orEmpty(),
                folderPath = c.getString(2).orEmpty(),
                durationMs = c.getLong(3),
                size = c.getLong(4),
                modified = c.getLong(5),
            )
        }
    }.orEmpty()
}

object Thumbnails {
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val executor = Executors.newFixedThreadPool(2)

    /** The view's tag holds the wanted id, so a recycled row drops a stale or queued result. */
    fun bind(image: ImageView, id: Long, width: Int) {
        val key = "$id/$width"
        image.tag = key
        cache.get(key)?.let {
            image.animate().cancel()
            image.alpha = 1f
            image.setImageBitmap(it)
            return
        }
        image.setImageDrawable(null)
        val resolver = image.context.contentResolver
        executor.execute {
            if (image.tag != key) return@execute
            val bitmap = try {
                resolver.loadThumbnail(videoUri(id), Size(width, width * 9 / 16), null)
            } catch (_: Exception) {
                return@execute // No thumbnail for this file: the row keeps its placeholder.
            }
            cache.put(key, bitmap)
            image.post {
                if (image.tag == key) {
                    image.alpha = 0f
                    image.setImageBitmap(bitmap)
                    image.animate().alpha(1f).setDuration(160).start()
                }
            }
        }
    }
}
