package com.playerowl

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.format.DateUtils
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.abs
import kotlin.math.roundToInt

private const val HIDE_CONTROLS_MS = 2500L
private const val HIDE_HUD_MS = 500L
private const val TICK_MS = 250L
private const val METER_MARGIN_DP = 12
private const val MIN_BRIGHTNESS = 0.01f // Exactly 0 turns some panels fully off.
private const val DISABLED_ALPHA = 0.38f
private const val BACK_GESTURE_EDGE_DP = 48 // Wider than the system's back zone at its highest sensitivity setting.

/** Media index ids of the videos that previous and next step through. A file manager's intent has none. */
const val EXTRA_PLAYLIST = "playlist"

@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {
    private lateinit var player: ExoPlayer
    private lateinit var audio: AudioManager
    private lateinit var gestures: Gestures

    private lateinit var root: FrameLayout
    private lateinit var surface: SurfaceView
    private lateinit var controls: View
    private lateinit var playPause: ImageButton
    private lateinit var previous: ImageButton
    private lateinit var next: ImageButton
    private lateinit var videoTitle: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var position: TextView
    private lateinit var duration: TextView
    private lateinit var error: TextView
    private lateinit var hud: View
    private lateinit var meter: View
    private lateinit var meterIcon: ImageView
    private lateinit var meterLevel: ProgressBar
    private lateinit var hudTitle: TextView
    private lateinit var hudDetail: TextView

    private var playlist = emptyList<Uri>()
    private var current = 0
    private var videoSize = VideoSize.UNKNOWN
    private var seekStart: Long? = null
    private var adjustStart: Float? = null
    private var volumeStep = 0
    private var safeLeft = 0
    private var safeRight = 0
    private val volumeMax by lazy { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    private val hideControls = Runnable {
        if (wantsPlay && !seekBar.isPressed && error.visibility != View.VISIBLE) setControlsVisible(false)
    }
    private val wantsPlay get() = player.playWhenReady && player.playbackState != Player.STATE_ENDED
    private var controlsShown = false
    private val hideHud = Runnable {
        for (view in listOf(hud, meter)) {
            view.animate().alpha(0f).setDuration(180).withEndAction { view.visibility = View.GONE }.start()
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            updateProgress()
            root.postDelayed(this, TICK_MS)
        }
    }

    private val listener = object : Player.Listener {
        override fun onVideoSizeChanged(size: VideoSize) {
            videoSize = size
            if (size.width > 0 && size.height > 0) {
                requestedOrientation =
                    if (size.width >= size.height) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            fitVideo()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            syncPlayState()
            if (!playWhenReady) setControlsVisible(true) // Also covers pauses we did not start, like a phone call.
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                seekBar.max = player.duration.coerceAtLeast(0).toInt()
                duration.text = formatTime(player.duration)
            }
            if (state == Player.STATE_ENDED) setControlsVisible(true)
            syncPlayState()
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (tracks.containsType(C.TRACK_TYPE_VIDEO) && !tracks.isTypeSupported(C.TRACK_TYPE_VIDEO)) {
                showError(R.string.video_unsupported)
            }
        }

        override fun onPlayerError(e: PlaybackException) = showError(R.string.cannot_play)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data ?: return finish()
        setContentView(R.layout.activity_player)
        // Android 14 and older shrink the content for visible bars by default, which shrinks the video with it.
        window.setDecorFitsSystemWindows(false)
        volumeControlStream = AudioManager.STREAM_MUSIC
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        gestures = Gestures(
            ViewConfiguration.get(this).scaledTouchSlop.toFloat(),
            BACK_GESTURE_EDGE_DP * resources.displayMetrics.density,
        )

        root = findViewById(R.id.root)
        surface = findViewById(R.id.surface)
        controls = findViewById(R.id.controls)
        playPause = findViewById(R.id.play_pause)
        previous = findViewById(R.id.previous)
        next = findViewById(R.id.next)
        videoTitle = findViewById(R.id.video_title)
        seekBar = findViewById(R.id.seek_bar)
        position = findViewById(R.id.position)
        duration = findViewById(R.id.duration)
        error = findViewById(R.id.error)
        hud = findViewById(R.id.hud)
        meter = findViewById(R.id.meter)
        meterIcon = findViewById(R.id.meter_icon)
        meterLevel = findViewById(R.id.meter_level)
        hudTitle = findViewById(R.id.hud_title)
        hudDetail = findViewById(R.id.hud_detail)

        playlist = intent.getLongArrayExtra(EXTRA_PLAYLIST)?.map(::videoUri).orEmpty().ifEmpty { listOf(uri) }
        current = playlist.indexOf(uri).coerceAtLeast(0)
        if (playlist.size == 1) {
            previous.visibility = View.GONE
            next.visibility = View.GONE
        }

        findViewById<View>(R.id.back).setOnClickListener { finish() }
        playPause.setOnClickListener { togglePlay() }
        previous.setOnClickListener { switchTo(current - 1) }
        next.setOnClickListener { switchTo(current + 1) }
        root.setOnClickListener { if (error.visibility != View.VISIBLE) setControlsVisible(!controlsShown) }
        root.setOnTouchListener { view, event -> onTouch(view, event) }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            root.post(::fitVideo) // Changing a child's size during layout can be dropped; do it right after.
        }
        seekBar.setOnSeekBarChangeListener(seekBarListener)
        applyInsets()
        hideSystemBars()

        player = ExoPlayer.Builder(
            this,
            DefaultRenderersFactory(this).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON),
        )
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(listener)
        player.setVideoSurfaceView(surface)
        playCurrent()

        setControlsVisible(true)
    }

    override fun onStop() {
        super.onStop()
        if (::player.isInitialized) {
            player.pause()
            // Every way out of the app passes through here: back, home, or swiping it away from recents.
            saveResume()
        }
    }

    private fun saveResume() {
        if (player.playerError == null) {
            writeResume(nextResume(readResume(), playlist[current].toString(), player.currentPosition, player.duration))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::player.isInitialized) {
            root.removeCallbacks(tick)
            root.removeCallbacks(hideControls)
            root.removeCallbacks(hideHud)
            player.release()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun onTouch(view: View, event: MotionEvent): Boolean {
        val result = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestures.down(event.x, event.y, view.width.toFloat(), view.height.toFloat())
                null
            }
            MotionEvent.ACTION_MOVE -> gestures.move(event.x, event.y)
            MotionEvent.ACTION_UP -> gestures.up()
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> gestures.cancel()
            else -> null
        }
        result?.let(::apply)
        return true
    }

    private fun apply(event: GestureEvent) {
        val margin = METER_MARGIN_DP * resources.displayMetrics.density
        meterX(event, safeLeft + margin, root.width - safeRight - margin, meter.layoutParams.width.toFloat())
            ?.let { meter.translationX = it }
        when (event) {
            GestureEvent.Tap -> root.performClick()
            is GestureEvent.Volume -> changeVolume(event.change)
            is GestureEvent.Brightness -> changeBrightness(event.change)
            is GestureEvent.Seek -> scrub(event.fraction)
            GestureEvent.SeekEnd -> {
                endScrub()
                scheduleHudHide()
            }
            GestureEvent.AdjustEnd -> {
                adjustStart = null
                scheduleHudHide()
            }
        }
    }

    private fun switchTo(index: Int) {
        saveResume()
        current = index
        playCurrent()
    }

    private fun playCurrent() {
        val uri = playlist[current]
        videoTitle.text = displayName(uri)
        error.visibility = View.GONE // The last video may have failed; this one gets a fresh start.
        playPause.visibility = View.VISIBLE
        setNavEnabled(previous, current > 0)
        setNavEnabled(next, current < playlist.lastIndex)
        player.setMediaItem(MediaItem.fromUri(uri), startPosition(readResume(), uri.toString()))
        player.prepare()
        player.playWhenReady = true
    }

    private fun setNavEnabled(button: View, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else DISABLED_ALPHA
    }

    private fun togglePlay() {
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
            player.playWhenReady = true
        } else {
            player.playWhenReady = !player.playWhenReady
        }
    }

    /** The single place that keeps the icon and the auto-hide timer in line with the player, whoever changed it. */
    private fun syncPlayState() {
        playPause.setImageResource(if (wantsPlay) R.drawable.ic_pause else R.drawable.ic_play)
        playPause.contentDescription = getString(if (wantsPlay) R.string.pause else R.string.play)
        root.removeCallbacks(hideControls)
        if (wantsPlay && controlsShown) root.postDelayed(hideControls, HIDE_CONTROLS_MS)
    }

    private fun setControlsVisible(visible: Boolean) {
        controlsShown = visible
        syncPlayState()
        root.removeCallbacks(tick)
        if (visible) {
            controls.visibility = View.VISIBLE
            controls.animate().alpha(1f).setDuration(120).withEndAction(null).start()
            tick.run()
        } else {
            controls.animate().alpha(0f).setDuration(220).withEndAction { controls.visibility = View.GONE }.start()
        }
    }

    private fun updateProgress() {
        val now = player.currentPosition
        seekBar.progress = now.toInt()
        position.text = formatTime(now)
    }

    private val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onStartTrackingTouch(bar: SeekBar) {
            root.removeCallbacks(tick)
            root.removeCallbacks(hideControls)
            player.setScrubbingModeEnabled(true)
        }

        override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) {
                player.seekTo(progress.toLong())
                position.text = formatTime(progress.toLong())
            }
        }

        override fun onStopTrackingTouch(bar: SeekBar) {
            player.setScrubbingModeEnabled(false)
            tick.run()
            syncPlayState()
        }
    }

    private fun scrub(fraction: Float) {
        val start = seekStart ?: player.currentPosition.also {
            seekStart = it
            player.setScrubbingModeEnabled(true)
        }
        val target = seekTarget(start, fraction, player.duration.coerceAtLeast(0))
        player.seekTo(target)
        val delta = target - start
        hudTitle.text = "${if (delta < 0) "−" else "+"}${formatTime(abs(delta))}"
        hudDetail.text = "${formatTime(target)} / ${formatTime(player.duration)}"
        reveal(hud, meter)
    }

    private fun endScrub() {
        seekStart = null
        player.setScrubbingModeEnabled(false)
    }

    private fun changeVolume(change: Float) {
        val start = adjustStart ?: run {
            volumeStep = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            (volumeStep.toFloat() / volumeMax).also { adjustStart = it }
        }
        val level = adjustLevel(start, change)
        val step = (level * volumeMax).roundToInt()
        // Each call blocks on a system service, so only make it when the step really changes.
        if (step != volumeStep) {
            volumeStep = step
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0)
        }
        showMeter(R.drawable.ic_volume, level)
    }

    private fun changeBrightness(change: Float) {
        val attributes = window.attributes
        val start = adjustStart ?: currentBrightness(attributes.screenBrightness).also { adjustStart = it }
        val level = adjustLevel(start, change).coerceAtLeast(MIN_BRIGHTNESS)
        attributes.screenBrightness = level
        window.attributes = attributes
        showMeter(R.drawable.ic_brightness, level)
    }

    // ponytail: the system setting is not linear on every phone, so the first drag can jump slightly.
    private fun currentBrightness(window: Float) =
        if (window >= 0) window
        else Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f

    private fun showMeter(icon: Int, level: Float) {
        meterIcon.setImageResource(icon)
        meterLevel.progress = (level * meterLevel.max).roundToInt()
        reveal(meter, hud)
    }

    /** Only one indicator shows at a time, even when a new drag starts before the last one has faded. */
    private fun reveal(shown: View, other: View) {
        root.removeCallbacks(hideHud)
        other.animate().cancel()
        other.alpha = 0f
        other.visibility = View.GONE
        // No fade-in. This runs on every finger move, and a fade restarted that often barely becomes visible.
        shown.animate().cancel()
        shown.alpha = 1f
        shown.visibility = View.VISIBLE
    }

    private fun scheduleHudHide() = root.postDelayed(hideHud, HIDE_HUD_MS)

    private fun showError(message: Int) {
        error.setText(message)
        error.visibility = View.VISIBLE
        playPause.visibility = View.INVISIBLE // Keeps previous and next in place, to skip past this file.
        setControlsVisible(true)
    }

    private fun fitVideo() {
        val (w, h) = fitInside(videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio, root.width, root.height)
        val params = surface.layoutParams
        if (params.width != w || params.height != h) {
            params.width = w
            params.height = h
            surface.layoutParams = params
        }
    }

    private fun applyInsets() {
        val top = findViewById<View>(R.id.top_bar)
        val bottom = findViewById<View>(R.id.bottom_bar)
        val topPad = top.paddingTop
        val bottomPad = bottom.paddingBottom
        root.setOnApplyWindowInsetsListener { _, insets ->
            val safe = insets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout())
            safeLeft = safe.left
            safeRight = safe.right
            top.setPadding(safe.left, topPad + safe.top, safe.right, top.paddingBottom)
            bottom.setPadding(safe.left, bottom.paddingTop, safe.right, bottomPad + safe.bottom)
            // BEHAVIOR_DEFAULT leaves revealed bars on screen, so put them away again.
            if (insets.isVisible(WindowInsets.Type.systemBars())) root.postDelayed(::hideSystemBars, HIDE_CONTROLS_MS)
            insets
        }
    }

    private fun hideSystemBars() {
        window.insetsController?.run {
            hide(WindowInsets.Type.systemBars())
            // The transient behavior spends the first edge swipe on showing the bars, so back would take two swipes.
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
        }
    }

    private fun displayName(uri: Uri): String {
        val name = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: Exception) {
            null // Some file managers refuse this query; the path below still names the file.
        }
        return name ?: uri.lastPathSegment.orEmpty()
    }

    private fun formatTime(ms: Long) = DateUtils.formatElapsedTime(ms.coerceAtLeast(0) / 1000)
}
