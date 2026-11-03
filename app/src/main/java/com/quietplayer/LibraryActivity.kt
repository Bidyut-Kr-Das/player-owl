package com.quietplayer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView

private class Row(val title: String, val subtitle: String, val badge: String, val thumbId: Long, val stack: Int, val open: () -> Unit)

/** Shows all folders, or the videos of one folder when started with [EXTRA_FOLDER]. */
class LibraryActivity : Activity() {
    private val folderPath by lazy { intent.getStringExtra(EXTRA_FOLDER) }
    private val permission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    private var askedForPermission = false
    private var rows = emptyList<Row>()

    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var empty: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyBody: TextView
    private lateinit var emptyAction: Button

    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(if (folderPath == null) R.layout.item_folder else R.layout.item_row, parent, false)
            val row = rows[position]
            view.findViewById<TextView>(R.id.row_title).text = row.title
            view.findViewById<TextView>(R.id.row_subtitle).text = row.subtitle
            view.findViewById<TextView?>(R.id.row_badge)?.text = row.badge
            view.findViewById<View?>(R.id.sheet_front)?.visibility = if (row.stack > 1) View.VISIBLE else View.INVISIBLE
            view.findViewById<View?>(R.id.sheet_back)?.visibility = if (row.stack > 2) View.VISIBLE else View.INVISIBLE
            val thumbWidth = if (folderPath == null) 640 else 320
            Thumbnails.bind(view.findViewById<ImageView>(R.id.row_thumb), row.thumbId, thumbWidth)
            return view
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        val list = findViewById<ListView>(R.id.list)
        val header = layoutInflater.inflate(R.layout.library_header, list, false)
        title = header.findViewById(R.id.title)
        subtitle = header.findViewById(R.id.subtitle)
        val back = header.findViewById<View>(R.id.back)
        back.visibility = if (folderPath == null) View.GONE else View.VISIBLE
        back.setOnClickListener { finish() }
        list.addHeaderView(header, null, false)
        list.adapter = adapter
        list.setOnItemClickListener { parent, _, position, _ -> (parent.getItemAtPosition(position) as Row).open() }

        empty = findViewById(R.id.empty)
        emptyTitle = findViewById(R.id.empty_title)
        emptyBody = findViewById(R.id.empty_body)
        emptyAction = findViewById(R.id.empty_action)
        emptyAction.setOnClickListener { askPermission() }

        val scrim = findViewById<View>(R.id.status_scrim)
        val basePadding = list.paddingBottom
        list.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom + basePadding)
            scrim.layoutParams = scrim.layoutParams.apply { height = bars.top }
            insets
        }
    }

    override fun onStart() {
        super.onStart()
        when {
            checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED -> reload()
            askedForPermission -> showNeedsPermission()
            else -> askPermission()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) reload() else showNeedsPermission()
    }

    private fun askPermission() {
        if (askedForPermission && !shouldShowRequestPermissionRationale(permission)) {
            // Android stops showing the dialog after repeated denial; only Settings can grant it now.
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } else {
            askedForPermission = true
            requestPermissions(arrayOf(permission), 1)
        }
    }

    private fun showNeedsPermission() {
        title.setText(R.string.app_name)
        subtitle.text = ""
        showEmpty(R.string.permission_title, R.string.permission_body, showAction = true)
        rows = emptyList()
        adapter.notifyDataSetChanged()
    }

    private fun showEmpty(titleRes: Int, bodyRes: Int, showAction: Boolean) {
        empty.visibility = View.VISIBLE
        emptyTitle.setText(titleRes)
        emptyBody.setText(bodyRes)
        emptyAction.visibility = if (showAction) View.VISIBLE else View.GONE
    }

    /** Runs on every return to the screen, so newly downloaded videos appear without a manual refresh. */
    private fun reload() {
        Thread {
            val folders = toFolders(loadVideos(this))
            runOnUiThread { if (!isDestroyed) show(folders) }
        }.start()
    }

    private fun show(folders: List<Folder>) {
        val folder = folders.firstOrNull { it.path == folderPath }
        when {
            folderPath == null -> {
                title.setText(R.string.folders)
                subtitle.text = count(folders.sumOf { it.videos.size })
                rows = folders.map { it.toRow() }
            }
            folder != null -> {
                title.text = folder.name
                subtitle.text = "${count(folder.videos.size)}, ${size(folder.totalSize)}"
                rows = folder.videos.map { it.toRow() }
            }
            else -> {
                finish() // Everything in this folder was deleted while we were away.
                return
            }
        }
        if (rows.isEmpty()) showEmpty(R.string.no_videos_title, R.string.no_videos_body, showAction = false)
        else empty.visibility = View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun Folder.toRow() = Row(name, "${count(videos.size)}, ${size(totalSize)}", "", videos.first().id, videos.size) {
        startActivity(Intent(this@LibraryActivity, LibraryActivity::class.java).putExtra(EXTRA_FOLDER, path))
    }

    private fun Video.toRow() = Row(name, size(size), DateUtils.formatElapsedTime(durationMs / 1000), id, 1) {
        startActivity(Intent(Intent.ACTION_VIEW, videoUri(id), this@LibraryActivity, PlayerActivity::class.java))
    }

    private fun count(n: Int) = resources.getQuantityString(R.plurals.videos, n, n)
    private fun size(bytes: Long) = Formatter.formatShortFileSize(this, bytes)

    private companion object {
        const val EXTRA_FOLDER = "folder"
    }
}
