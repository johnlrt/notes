package com.johnlrt.freenotes.data

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID

/**
 * Lightweight voice note recorder & player supporting lecture recording while writing,
 * 10-second seek forward/backward, and variable playback speed (0.75x – 2.0x).
 */
class AudioRecorderManager(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var currentFile: File? = null
    private var recordStartMs: Long = 0L

    var isRecording: Boolean = false
        private set

    var isPlaying: Boolean = false
        private set

    fun startRecording(): File? {
        stopPlayback()
        val audioDir = File(context.filesDir, "media/audio").apply { mkdirs() }
        val outFile = File(audioDir, "voice_${UUID.randomUUID()}.m4a")

        return runCatching {
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(96_000)
            mr.setAudioSamplingRate(44_100)
            mr.setOutputFile(outFile.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            currentFile = outFile
            recordStartMs = System.currentTimeMillis()
            isRecording = true
            outFile
        }.getOrNull()
    }

    fun stopRecording(title: String = "Voice Note"): VoiceRecording? {
        if (!isRecording) return null
        val file = currentFile
        val duration = (System.currentTimeMillis() - recordStartMs).coerceAtLeast(1000L)
        runCatching {
            recorder?.stop()
            recorder?.release()
        }
        recorder = null
        isRecording = false
        currentFile = null
        return file?.takeIf { it.exists() }?.let {
            VoiceRecording(
                title = title,
                filePath = it.absolutePath,
                durationMs = duration
            )
        }
    }

    fun play(filePath: String, speed: Float = 1.0f, onComplete: () -> Unit = {}) {
        stopPlayback()
        runCatching {
            val mp = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                playbackParams = playbackParams.setSpeed(speed.coerceIn(0.5f, 2.0f))
                setOnCompletionListener {
                    isPlaying = false
                    onComplete()
                }
                start()
            }
            player = mp
            isPlaying = true
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        runCatching {
            player?.let { mp ->
                mp.playbackParams = mp.playbackParams.setSpeed(speed.coerceIn(0.5f, 2.0f))
            }
        }
    }

    fun seekBy(deltaMs: Int) {
        runCatching {
            player?.let { mp ->
                val target = (mp.currentPosition + deltaMs).coerceIn(0, mp.duration)
                mp.seekTo(target)
            }
        }
    }

    fun stopPlayback() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        isPlaying = false
    }

    fun release() {
        stopRecording()
        stopPlayback()
    }
}
