package com.example.media

import android.content.Context
import com.example.data.local.SubtitleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

data class BuiltInMusicTrack(
    val id: String,
    val title: String,
    val mood: String,
    val bpm: Int,
    val baseFreqHz: Double,
    val license: String = "CC0 Royalty-Free (Generated On-Device)"
)

data class BuiltInSfx(
    val id: String,
    val title: String
)

object AudioMixerAndBeatDetector {
    val builtInMusicTracks = listOf(
        BuiltInMusicTrack("ambient_pulse", "Ambient Pulse (110 BPM)", "Tech / Explainer", 110, 220.0),
        BuiltInMusicTrack("lofi_focus", "Lo-Fi Creator Chill (88 BPM)", "Tutorial / Study", 88, 196.0),
        BuiltInMusicTrack("shorts_energy", "Shorts Drive Beat (128 BPM)", "Gaming / Viral Shorts", 128, 261.63),
        BuiltInMusicTrack("cinematic_rise", "Cinematic Horizon (96 BPM)", "Documentary / News", 96, 174.61)
    )

    val builtInSfx = listOf(
        BuiltInSfx("WHOOSH", "Transition Whoosh"),
        BuiltInSfx("POP", "Caption Pop"),
        BuiltInSfx("RISER", "Tension Riser"),
        BuiltInSfx("CHIME", "Fact Highlight Chime")
    )

    suspend fun ensureMusicTrackWav(context: Context, trackId: String, durationMs: Long = 16_000L): File =
        withContext(Dispatchers.IO) {
            val track = builtInMusicTracks.firstOrNull { it.id == trackId } ?: builtInMusicTracks.first()
            val dir = File(context.filesDir, "audio_library").apply { mkdirs() }
            val file = File(dir, "${track.id}.wav")
            if (file.exists() && file.length() > 1024L) return@withContext file

            val sampleRate = 22050
            val totalSamples = ((durationMs.coerceIn(4000L, 45_000L) * sampleRate) / 1000L).toInt()
            val pcm = ShortArray(totalSamples)
            val beatIntervalSamples = (sampleRate * 60.0 / track.bpm).toInt().coerceAtLeast(1)

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val beatPos = (i % beatIntervalSamples).toDouble() / sampleRate
                val kickEnv = exp(-beatPos * 14.0)
                val kick = sin(2.0 * PI * (55.0 + 90.0 * exp(-beatPos * 25.0)) * beatPos) * kickEnv * 0.45
                val chord = (
                    sin(2.0 * PI * track.baseFreqHz * t) +
                        0.6 * sin(2.0 * PI * track.baseFreqHz * 1.25 * t) +
                        0.5 * sin(2.0 * PI * track.baseFreqHz * 1.5 * t)
                    ) * 0.14
                val sample = ((kick + chord) * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                pcm[i] = sample.toShort()
            }
            writePcm16MonoWav(file, pcm, sampleRate)
            file
        }

    suspend fun ensureSfxWav(context: Context, sfxId: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "audio_library").apply { mkdirs() }
        val file = File(dir, "sfx_${sfxId.lowercase(Locale.US)}.wav")
        if (file.exists() && file.length() > 512L) return@withContext file

        val sampleRate = 22050
        val durationSec = when (sfxId) {
            "POP" -> 0.25
            "CHIME" -> 0.8
            "RISER" -> 1.2
            else -> 0.55
        }
        val totalSamples = (sampleRate * durationSec).toInt()
        val pcm = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val norm = i.toDouble() / totalSamples
            val valDouble = when (sfxId) {
                "POP" -> sin(2.0 * PI * (300.0 + 600.0 * (1.0 - norm)) * t) * exp(-t * 18.0) * 0.65
                "CHIME" -> (sin(2.0 * PI * 880.0 * t) + 0.5 * sin(2.0 * PI * 1318.5 * t)) * exp(-t * 5.0) * 0.45
                "RISER" -> sin(2.0 * PI * (180.0 + 700.0 * norm * norm) * t) * norm * 0.5
                else -> { // WHOOSH
                    val env = sin(PI * norm)
                    val carrier = sin(2.0 * PI * (140.0 + 240.0 * sin(PI * norm)) * t)
                    carrier * env * 0.5
                }
            }
            pcm[i] = (valDouble * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        writePcm16MonoWav(file, pcm, sampleRate)
        file
    }

    /**
     * Energy-onset beat detection on 16-bit PCM WAV files for beat-synchronized cuts.
     */
    suspend fun detectBeatTimestampsMs(wavFile: File): List<Long> = withContext(Dispatchers.IO) {
        if (!wavFile.exists() || wavFile.length() <= 44L) return@withContext emptyList()
        val bytes = wavFile.readBytes()
        if (bytes.size <= 44) return@withContext emptyList()
        val sampleRate = ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int.coerceIn(8000, 48000)
        val channels = ByteBuffer.wrap(bytes, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt().coerceIn(1, 2)
        val windowSamples = (sampleRate * 0.05).toInt().coerceAtLeast(1) // 50ms windows
        val frameBytes = 2 * channels
        val totalFrames = (bytes.size - 44) / frameBytes
        val energies = mutableListOf<Double>()

        var frame = 0
        while (frame < totalFrames) {
            var sum = 0.0
            var count = 0
            val end = (frame + windowSamples).coerceAtMost(totalFrames)
            for (f in frame until end) {
                val offset = 44 + f * frameBytes
                if (offset + 1 < bytes.size) {
                    val lo = bytes[offset].toInt() and 0xFF
                    val hi = bytes[offset + 1].toInt()
                    val sample = (hi shl 8) or lo
                    sum += abs(sample.toDouble())
                    count++
                }
            }
            energies.add(if (count > 0) sum / count else 0.0)
            frame += windowSamples
        }

        if (energies.size < 4) return@withContext emptyList()
        val avg = energies.average()
        val threshold = avg * 1.32
        val beats = mutableListOf<Long>()
        var lastBeatMs = -500L
        for (idx in 1 until energies.size - 1) {
            if (energies[idx] > threshold && energies[idx] >= energies[idx - 1] && energies[idx] >= energies[idx + 1]) {
                val ms = idx * 50L
                if (ms - lastBeatMs >= 350L) {
                    beats.add(ms)
                    lastBeatMs = ms
                }
            }
        }
        beats
    }

    /**
     * Mixes voiceover WAV + ducked background music WAV with fade-in/fade-out into a single WAV for export.
     */
    suspend fun mixAudioTracksToWav(
        context: Context,
        voiceoverFiles: List<Pair<Long, File>>, // (startMs, file)
        musicFile: File?,
        musicVolume: Float,
        voiceVolume: Float,
        duckingEnabled: Boolean,
        totalDurationMs: Long,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val sampleRate = 22050
            val totalSamples = ((totalDurationMs.coerceAtLeast(1000L) * sampleRate) / 1000L).toInt()
            val mix = FloatArray(totalSamples)

            // 1. Place voiceover segments and track active speech mask for ducking
            val speechActive = BooleanArray(totalSamples)
            for ((startMs, voFile) in voiceoverFiles) {
                if (!voFile.exists() || voFile.length() <= 44L) continue
                val voPcm = readWavAsMono22k(voFile)
                val startSample = ((startMs * sampleRate) / 1000L).toInt().coerceAtLeast(0)
                for (i in voPcm.indices) {
                    val idx = startSample + i
                    if (idx >= totalSamples) break
                    val sampleVal = voPcm[i] * voiceVolume
                    mix[idx] += sampleVal
                    if (abs(voPcm[i]) > 0.01f) {
                        speechActive[idx] = true
                    }
                }
            }

            // 2. Mix background music (looped) with fade-in/out and voice ducking
            if (musicFile != null && musicFile.exists() && musicFile.length() > 44L && musicVolume > 0.01f) {
                val musicPcm = readWavAsMono22k(musicFile)
                if (musicPcm.isNotEmpty()) {
                    val fadeSamples = (sampleRate * 1.2).toInt().coerceAtLeast(1)
                    for (i in 0 until totalSamples) {
                        val mSample = musicPcm[i % musicPcm.size]
                        val fadeIn = (i.toFloat() / fadeSamples).coerceIn(0f, 1f)
                        val fadeOut = ((totalSamples - i).toFloat() / fadeSamples).coerceIn(0f, 1f)
                        val duckGain = if (duckingEnabled && speechActive[i]) 0.35f else 1.0f
                        mix[i] += mSample * musicVolume * fadeIn * fadeOut * duckGain
                    }
                }
            }

            val outPcm = ShortArray(totalSamples) { idx ->
                val clamped = mix[idx].coerceIn(-0.98f, 0.98f)
                (clamped * Short.MAX_VALUE).toInt().toShort()
            }
            writePcm16MonoWav(outputFile, outPcm, sampleRate)
            outputFile
        }
    }

    fun writeSilenceWav(file: File, durationMs: Long, sampleRate: Int = 22050) {
        val totalSamples = ((durationMs.coerceAtLeast(500L) * sampleRate) / 1000L).toInt()
        val pcm = ShortArray(totalSamples)
        writePcm16MonoWav(file, pcm, sampleRate)
    }

    private fun readWavAsMono22k(file: File): FloatArray {
        val bytes = file.readBytes()
        if (bytes.size <= 44) return FloatArray(0)
        val channels = ByteBuffer.wrap(bytes, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt().coerceIn(1, 2)
        val srcRate = ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int.coerceIn(8000, 48000)
        val frameBytes = 2 * channels
        val totalFrames = (bytes.size - 44) / frameBytes
        val mono = FloatArray(totalFrames)
        for (f in 0 until totalFrames) {
            val offset = 44 + f * frameBytes
            val lo = bytes[offset].toInt() and 0xFF
            val hi = bytes[offset + 1].toInt()
            val sample = (hi shl 8) or lo
            mono[f] = sample.toFloat() / Short.MAX_VALUE
        }
        if (srcRate == 22050) return mono
        val ratio = 22050.0 / srcRate
        val targetLen = (mono.size * ratio).toInt().coerceAtLeast(1)
        return FloatArray(targetLen) { i ->
            val srcIdx = (i / ratio).toInt().coerceIn(0, mono.lastIndex)
            mono[srcIdx]
        }
    }

    private fun writePcm16MonoWav(file: File, pcm: ShortArray, sampleRate: Int) {
        val dataSize = pcm.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1.toShort()) // PCM format
            putShort(1.toShort()) // Mono
            putInt(sampleRate)
            putInt(sampleRate * 2) // byte rate
            putShort(2.toShort()) // block align
            putShort(16.toShort()) // bits per sample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()

        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            raf.write(header)
            val dataBuf = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcm) dataBuf.putShort(s)
            raf.write(dataBuf.array())
        }
    }
}

object SrtCodec {
    fun exportToSrt(subtitles: List<SubtitleEntity>): String {
        return subtitles.sortedBy { it.startTimeMs }.mapIndexed { index, sub ->
            "${index + 1}\n${formatSrtTime(sub.startTimeMs)} --> ${formatSrtTime(sub.endTimeMs)}\n${sub.text.trim()}\n"
        }.joinToString("\n")
    }

    fun parseSrt(projectId: String, rawSrt: String): List<SubtitleEntity> {
        val blocks = rawSrt.trim().split(Regex("\\r?\\n\\s*\\r?\\n"))
        val out = mutableListOf<SubtitleEntity>()
        blocks.forEachIndexed { index, block ->
            val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }
            val timeLineIdx = lines.indexOfFirst { it.contains("-->") }
            if (timeLineIdx != -1) {
                val parts = lines[timeLineIdx].split("-->")
                if (parts.size == 2) {
                    val startMs = parseSrtTime(parts[0].trim())
                    val endMs = parseSrtTime(parts[1].trim()).coerceAtLeast(startMs + 500L)
                    val text = lines.drop(timeLineIdx + 1).joinToString(" ")
                    if (text.isNotBlank()) {
                        out.add(
                            SubtitleEntity(
                                id = UUID.randomUUID().toString(),
                                projectId = projectId,
                                orderIndex = index,
                                startTimeMs = startMs,
                                endTimeMs = endMs,
                                text = text
                            )
                        )
                    }
                }
            }
        }
        return out
    }

    private fun formatSrtTime(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        val h = total / 3_600_000L
        val m = (total % 3_600_000L) / 60_000L
        val s = (total % 60_000L) / 1000L
        val rem = total % 1000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, rem)
    }

    private fun parseSrtTime(text: String): Long {
        val clean = text.replace(".", ",")
        val parts = clean.split(":", ",")
        if (parts.size < 4) return 0L
        val h = parts[0].trim().toLongOrNull() ?: 0L
        val m = parts[1].trim().toLongOrNull() ?: 0L
        val s = parts[2].trim().toLongOrNull() ?: 0L
        val ms = parts[3].trim().take(3).toLongOrNull() ?: 0L
        return h * 3_600_000L + m * 60_000L + s * 1000L + ms
    }
}
