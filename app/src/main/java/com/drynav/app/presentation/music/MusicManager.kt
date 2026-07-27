package com.drynav.app.presentation.music

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.drynav.app.R
import com.drynav.app.data.prefs.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide looping background music. Runs for as long as the app is in the
 * foreground (see [start]/[stop], driven from
 * [com.drynav.app.presentation.navigation.DryNavGraph]'s ON_START/ON_STOP),
 * independent of which screen is showing.
 *
 * [volume] is the user's chosen "normal" level (75% by default). While
 * actively turn-by-turn navigating ([setMoving]), playback is ducked to the
 * same 30%-of-75% ratio the user asked for, scaled against whatever normal
 * level they've picked — so raising/lowering the slider doesn't fight the
 * driving duck.
 */
@Singleton
class MusicManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var isForeground = false
    private var isMoving = false

    var enabled by mutableStateOf(true); private set
    var volume by mutableFloatStateOf(0.75f); private set

    init {
        scope.launch {
            userPreferences.musicEnabled.collect {
                enabled = it
                applyVolume()
                syncPlayback()
            }
        }
        scope.launch {
            userPreferences.musicVolume.collect {
                volume = it
                applyVolume()
            }
        }
    }

    fun start() {
        isForeground = true
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(context, R.raw.music_loop)?.apply {
                isLooping = true
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
            }
            applyVolume()
        }
        syncPlayback()
    }

    fun stop() {
        isForeground = false
        mediaPlayer?.pause()
    }

    /** Called with `true` while a turn-by-turn trip is active, `false` otherwise. */
    fun setMoving(moving: Boolean) {
        if (isMoving == moving) return
        isMoving = moving
        applyVolume()
    }

    fun updateEnabled(value: Boolean) {
        scope.launch { userPreferences.setMusicEnabled(value) }
    }

    fun updateVolume(value: Float) {
        scope.launch { userPreferences.setMusicVolume(value) }
    }

    private fun syncPlayback() {
        val player = mediaPlayer ?: return
        if (isForeground && enabled) {
            if (!player.isPlaying) player.start()
        } else if (player.isPlaying) {
            player.pause()
        }
    }

    private fun applyVolume() {
        val effective = if (!enabled) 0f else volume * (if (isMoving) MOVING_SCALE else 1f)
        mediaPlayer?.setVolume(effective, effective)
    }

    private companion object {
        // 30% while driving / 75% normal — preserved as a ratio so it still
        // holds whatever "normal" level the user's slider is set to.
        const val MOVING_SCALE = 0.30f / 0.75f
    }
}
