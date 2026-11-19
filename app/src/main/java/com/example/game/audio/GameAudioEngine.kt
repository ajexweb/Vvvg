package com.example.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Low-latency native Android PCM audio synthesizer for Lobby ambient music,
 * dynamic footsteps, UI clicks, jump whooshes, and zone discovery chimes.
 */
class GameAudioEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var musicJob: Job? = null

    @Volatile
    var musicVolume: Float = 0.65f

    @Volatile
    var sfxVolume: Float = 0.85f

    @Volatile
    private var isMusicEnabled = false

    private val sampleRate = 22050

    fun startLobbyMusic() {
        if (isMusicEnabled && musicJob?.isActive == true) return
        isMusicEnabled = true
        musicJob = scope.launch {
            val bufferSamples = 2205 // 100ms chunks
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(bufferSamples * 2)

            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull() ?: return@launch

            val pcm = ShortArray(bufferSamples)
            // Warm chill urban synth chord progression (Am9 - Fmaj7 - Cmaj7 - G6)
            val chords = arrayOf(
                floatArrayOf(220.0f, 261.63f, 329.63f, 493.88f),
                floatArrayOf(174.61f, 220.0f, 261.63f, 329.63f),
                floatArrayOf(130.81f, 196.0f, 246.94f, 329.63f),
                floatArrayOf(196.0f, 246.94f, 293.66f, 329.63f)
            )
            var sampleIndex = 0L

            try {
                track.play()
                while (isActive && isMusicEnabled) {
                    val vol = musicVolume.coerceIn(0f, 1f) * 0.22f
                    for (i in 0 until bufferSamples) {
                        val t = sampleIndex.toDouble() / sampleRate
                        val chordIdx = ((t / 2.4).toInt()) % chords.size
                        val chordPos = (t % 2.4) / 2.4
                        val padEnv = sin(PI * chordPos).coerceIn(0.15, 1.0)
                        val notes = chords[chordIdx]

                        var mix = 0.0
                        for (n in notes.indices) {
                            mix += sin(2.0 * PI * notes[n] * t) * (0.28 - n * 0.03)
                        }
                        // Soft sub-bass pulse on beat
                        val beatPos = (t % 0.6) / 0.6
                        val bassEnv = exp(-beatPos * 5.0)
                        val bassFreq = notes[0] * 0.5
                        mix += sin(2.0 * PI * bassFreq * t) * bassEnv * 0.45

                        val sample = (mix * padEnv * vol * Short.MAX_VALUE).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        pcm[i] = sample.toShort()
                        sampleIndex++
                    }
                    track.write(pcm, 0, pcm.size)
                }
            } catch (_: Throwable) {
            } finally {
                runCatching {
                    track.pause()
                    track.flush()
                    track.release()
                }
            }
        }
    }

    fun stopLobbyMusic() {
        isMusicEnabled = false
        musicJob?.cancel()
        musicJob = null
    }

    fun playClick() {
        if (sfxVolume <= 0.01f) return
        playTone(durationMs = 55) { t, progress ->
            val freq = 720.0 + 380.0 * progress
            sin(2.0 * PI * freq * t) * exp(-progress * 6.0) * 0.35
        }
    }

    fun playJump() {
        if (sfxVolume <= 0.01f) return
        playTone(durationMs = 140) { t, progress ->
            val freq = 240.0 + 360.0 * progress
            sin(2.0 * PI * freq * t) * exp(-progress * 3.5) * 0.40
        }
    }

    fun playFootstep(isSprint: Boolean) {
        if (sfxVolume <= 0.01f) return
        val dur = if (isSprint) 65 else 80
        playTone(durationMs = dur) { t, progress ->
            val thud = sin(2.0 * PI * (115.0 - 50.0 * progress) * t)
            val grit = (Random.nextDouble() * 2.0 - 1.0) * 0.25
            (thud * 0.8 + grit) * exp(-progress * 9.0) * (if (isSprint) 0.34 else 0.24)
        }
    }

    fun playPickup() {
        if (sfxVolume <= 0.01f) return
        playTone(durationMs = 220) { t, progress ->
            val freq = if (progress < 0.45) 784.0 else 1174.66
            sin(2.0 * PI * freq * t) * exp(-progress * 4.0) * 0.42
        }
    }

    fun playZoneDiscovered() {
        if (sfxVolume <= 0.01f) return
        scope.launch {
            val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
            notes.forEachIndexed { index, freq ->
                playTone(durationMs = 190) { t, progress ->
                    sin(2.0 * PI * freq * t) * exp(-progress * 3.2) * 0.38
                }
                if (index < notes.lastIndex) delay(85)
            }
        }
    }

    private fun playTone(durationMs: Int, generator: (t: Double, progress: Double) -> Double) {
        scope.launch {
            val totalSamples = (sampleRate * durationMs) / 1000
            if (totalSamples <= 0) return@launch
            val pcm = ShortArray(totalSamples)
            val vol = sfxVolume.coerceIn(0f, 1f)
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val progress = i.toDouble() / totalSamples
                val v = generator(t, progress) * vol
                pcm[i] = (v * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }

            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(totalSamples * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            }.getOrNull() ?: return@launch

            try {
                track.write(pcm, 0, pcm.size)
                track.play()
                delay(durationMs.toLong() + 40L)
            } catch (_: Throwable) {
            } finally {
                runCatching {
                    track.stop()
                    track.release()
                }
            }
        }
    }

    fun release() {
        stopLobbyMusic()
    }
}
