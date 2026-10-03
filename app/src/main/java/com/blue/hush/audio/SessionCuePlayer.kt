package com.blue.hush.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.blue.hush.R

/** Short, non-looping cues survive normal service shutdown and release after playback. */
object SessionCuePlayer {
    private val active = mutableSetOf<MediaPlayer>()

    fun play(context: Context, finished: Boolean) {
        val player = MediaPlayer()
        active += player
        fun release() { if (active.remove(player)) player.release() }
        try {
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            player.setOnCompletionListener { release() }
            player.setOnErrorListener { _, _, _ -> release(); true }
            player.setOnPreparedListener { it.setVolume(0.85f, 0.85f); it.start() }
            val resource = if (finished) R.raw.session_end else R.raw.session_start
            context.applicationContext.resources.openRawResourceFd(resource).use {
                player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            player.prepareAsync()
        } catch (error: Exception) {
            release()
            Log.e("HushSessionCue", "Unable to play session cue", error)
        }
    }
}
