package com.drynav.app.presentation.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.drynav.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Tiny UI sound effects — [SoundPool] rather than `MediaPlayer` since these
 * are short, low-latency, and can overlap if the user taps fast.
 * [playClick] randomizes across 3 clips so rapid button-tapping doesn't
 * sound like the same note repeating.
 *
 * Uses `USAGE_MEDIA` (the same audio stream as [com.drynav.app.presentation.music.MusicManager]'s
 * background music) rather than `USAGE_ASSISTANCE_SONIFICATION` — the latter
 * is routed to the device's "touch sounds"/system-sound stream on many
 * phones, which a lot of OEM skins mute by default independent of media
 * volume, silencing every UI sound effect even though media volume (and
 * therefore the music) is perfectly audible.
 *
 * [SoundPool.load] decodes asynchronously — [loadedIds] tracks which sound
 * IDs have actually finished decoding so a very-early tap (before the app
 * has had even a moment to decode these short clips) doesn't get silently
 * dropped by [SoundPool.play] on a not-yet-ready sound.
 */
@Singleton
class SoundManager @Inject constructor(@ApplicationContext context: Context) {

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val loadedIds = mutableSetOf<Int>()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedIds.add(sampleId)
        }
    }

    private val clickSoundIds = intArrayOf(
        soundPool.load(context, R.raw.sfx_click_1, 1),
        soundPool.load(context, R.raw.sfx_click_2, 1),
        soundPool.load(context, R.raw.sfx_click_3, 1)
    )
    private val victorySoundId = soundPool.load(context, R.raw.sfx_victory, 1)

    fun playClick() {
        val candidates = clickSoundIds.filter { it in loadedIds }.ifEmpty { clickSoundIds.toList() }
        val id = candidates[Random.nextInt(candidates.size)]
        soundPool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun playVictory() {
        soundPool.play(victorySoundId, 1f, 1f, 1, 0, 1f)
    }
}
