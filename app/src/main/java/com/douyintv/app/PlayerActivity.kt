package com.douyintv.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.douyintv.app.api.DouyinApi
import com.douyintv.app.databinding.ActivityPlayerBinding
import com.douyintv.app.model.DouyinVideo
import okhttp3.OkHttpClient

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var prefs: Prefs
    private var player: ExoPlayer? = null

    private var videos: List<DouyinVideo> = emptyList()
    private var currentIndex: Int = 0
    private val speeds = floatArrayOf(1.0f, 1.25f, 1.5f, 2.0f, 0.75f)
    private var speedIndex = 0

    private val handler = Handler(Looper.getMainLooper())
    private val hideOsdRunnable = Runnable { binding.osdOverlay.visibility = View.GONE }
    private val progressRunnable = object : Runnable {
        override fun run() {
            player?.let { p ->
                if (p.duration > 0) {
                    binding.playerProgress.progress =
                        ((p.currentPosition * 1000L) / p.duration).toInt()
                }
            }
            handler.postDelayed(this, 400L)
        }
    }

    companion object {
        const val EXTRA_VIDEOS = "extra_videos"
        const val EXTRA_INDEX = "extra_index"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = Prefs(this)

        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        videos = (intent.getSerializableExtra(EXTRA_VIDEOS) as? ArrayList<DouyinVideo>) ?: emptyList()
        currentIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, (videos.size - 1).coerceAtLeast(0))

        if (videos.isEmpty()) {
            finish()
            return
        }

        initPlayer()
        playAtIndex(currentIndex)
    }

    private fun initPlayer() {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", DouyinApi.USER_AGENT)
                    .header("Referer", DouyinApi.REFERER)
                    .build()
                chain.proceed(req)
            }
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                repeatMode = if (prefs.loopSingle) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        binding.playerBuffering.visibility =
                            if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                        if (state == Player.STATE_ENDED && prefs.autoPlayNext && !prefs.loopSingle) {
                            switchVideo(1)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Toast.makeText(
                            this@PlayerActivity,
                            "播放失败，自动切换下一条 (${error.errorCodeName})",
                            Toast.LENGTH_SHORT
                        ).show()
                        switchVideo(1)
                    }
                })
            }

        binding.playerView.player = player
        handler.post(progressRunnable)
    }

    private fun playAtIndex(index: Int) {
        if (videos.isEmpty()) return
        currentIndex = index
        val video = videos[index]

        binding.tvPlayerAuthor.text = "@${video.authorName}"
        binding.tvPlayerTitle.text = video.desc
        binding.tvPlayerIndex.text = "${index + 1} / ${videos.size}"
        binding.tvPlayerSpeed.text = "${speeds[speedIndex]}x"
        showOsdBriefly()

        player?.let { p ->
            p.setMediaItem(MediaItem.fromUri(video.playUrl))
            p.prepare()
            p.playWhenReady = true
        }
    }

    private fun switchVideo(delta: Int) {
        if (videos.isEmpty()) return
        val next = (currentIndex + delta + videos.size) % videos.size
        playAtIndex(next)
    }

    private fun showOsdBriefly() {
        binding.osdOverlay.visibility = View.VISIBLE
        handler.removeCallbacks(hideOsdRunnable)
        handler.postDelayed(hideOsdRunnable, 3500L)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    switchVideo(-1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    switchVideo(1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    player?.let { p ->
                        p.seekTo((p.currentPosition - 10_000L).coerceAtLeast(0L))
                    }
                    showOsdBriefly()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    player?.let { p ->
                        val target = p.currentPosition + 10_000L
                        p.seekTo(if (p.duration > 0) target.coerceAtMost(p.duration) else target)
                    }
                    showOsdBriefly()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    player?.let { p ->
                        if (p.isPlaying) p.pause() else p.play()
                    }
                    showOsdBriefly()
                    return true
                }
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> {
                    speedIndex = (speedIndex + 1) % speeds.size
                    val spd = speeds[speedIndex]
                    player?.playbackParameters = PlaybackParameters(spd)
                    binding.tvPlayerSpeed.text = "${spd}x"
                    showOsdBriefly()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        super.onDestroy()
    }
}
