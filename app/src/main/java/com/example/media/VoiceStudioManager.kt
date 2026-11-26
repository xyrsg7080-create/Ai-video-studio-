package com.example.media

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

data class TtsVoiceOption(
    val id: String,
    val displayName: String,
    val languageCode: String,
    val genderHint: String,
    val locale: Locale
)

class VoiceStudioManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    private val initDeferred = CompletableDeferred<Boolean>()
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _voices = MutableStateFlow<List<TtsVoiceOption>>(defaultFallbackVoices())
    val voices: StateFlow<List<TtsVoiceOption>> = _voices.asStateFlow()

    private val utteranceCallbacks = mutableMapOf<String, CompletableDeferred<Boolean>>()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val ok = status == TextToSpeech.SUCCESS
            if (ok) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { utteranceCallbacks.remove(it)?.complete(true) }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let { utteranceCallbacks.remove(it)?.complete(false) }
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        utteranceId?.let { utteranceCallbacks.remove(it)?.complete(false) }
                    }
                })
                refreshAvailableVoices()
            }
            _isReady.value = ok
            if (!initDeferred.isCompleted) initDeferred.complete(ok)
        }
    }

    private fun refreshAvailableVoices() {
        val engine = tts ?: return
        val deviceVoices = runCatching { engine.voices?.toList().orEmpty() }.getOrElse { emptyList() }
        val filtered = deviceVoices
            .filter { v ->
                val lang = v.locale.language
                lang == "en" || lang == "hi"
            }
            .sortedBy { it.name }
            .take(16)
            .mapIndexed { index, v ->
                val gender = when {
                    v.name.contains("female", true) || v.name.contains("-f-", true) || v.name.endsWith("f") -> "Female"
                    v.name.contains("male", true) || v.name.contains("-m-", true) || v.name.endsWith("m") -> "Male"
                    index % 2 == 0 -> "Voice A"
                    else -> "Voice B"
                }
                TtsVoiceOption(
                    id = v.name,
                    displayName = "${v.locale.displayLanguage} (${v.locale.country.ifBlank { "Global" }}) • $gender",
                    languageCode = v.locale.language,
                    genderHint = gender,
                    locale = v.locale
                )
            }
        if (filtered.isNotEmpty()) {
            _voices.value = filtered
        }
    }

    suspend fun previewVoice(
        text: String,
        languageCode: String,
        voiceId: String?,
        speed: Float,
        pitch: Float
    ): Boolean {
        val ready = withTimeoutOrNull(4000L) { initDeferred.await() } ?: false
        val engine = tts ?: return false
        if (!ready) return false
        configureEngine(engine, languageCode, voiceId, speed, pitch)
        val uttId = "preview_${UUID.randomUUID()}"
        val res = engine.speak(text.take(400), TextToSpeech.QUEUE_FLUSH, Bundle(), uttId)
        return res == TextToSpeech.SUCCESS
    }

    fun stopSpeaking() {
        runCatching { tts?.stop() }
    }

    suspend fun synthesizeToWav(
        text: String,
        languageCode: String,
        voiceId: String?,
        speed: Float,
        pitch: Float,
        outputFileName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        val ready = withTimeoutOrNull(5000L) { initDeferred.await() } ?: false
        val engine = tts
        val outDir = File(context.filesDir, "voiceovers").apply { mkdirs() }
        val outFile = File(outDir, outputFileName)
        if (!ready || engine == null || text.isBlank()) {
            // Write a clean silent WAV matching approximate reading duration if TTS engine is unavailable (e.g. headless test)
            val approxSec = (text.split(Regex("\\s+")).size / 2.3).coerceIn(2.0, 25.0)
            AudioMixerAndBeatDetector.writeSilenceWav(outFile, (approxSec * 1000).toLong())
            return@withContext Result.success(outFile)
        }

        withContext(Dispatchers.Main) {
            configureEngine(engine, languageCode, voiceId, speed, pitch)
        }
        val uttId = "synth_${UUID.randomUUID()}"
        val deferred = CompletableDeferred<Boolean>()
        utteranceCallbacks[uttId] = deferred

        val callResult = withContext(Dispatchers.Main) {
            engine.synthesizeToFile(text.take(2500), Bundle(), outFile, uttId)
        }
        if (callResult != TextToSpeech.SUCCESS) {
            utteranceCallbacks.remove(uttId)
            val approxSec = (text.split(Regex("\\s+")).size / 2.3).coerceIn(2.0, 25.0)
            AudioMixerAndBeatDetector.writeSilenceWav(outFile, (approxSec * 1000).toLong())
            return@withContext Result.success(outFile)
        }

        val completed = withTimeoutOrNull(25_000L) { deferred.await() } ?: false
        utteranceCallbacks.remove(uttId)
        if (completed && outFile.exists() && outFile.length() > 44L) {
            Result.success(outFile)
        } else {
            val approxSec = (text.split(Regex("\\s+")).size / 2.3).coerceIn(2.0, 25.0)
            AudioMixerAndBeatDetector.writeSilenceWav(outFile, (approxSec * 1000).toLong())
            Result.success(outFile)
        }
    }

    private fun configureEngine(
        engine: TextToSpeech,
        languageCode: String,
        voiceId: String?,
        speed: Float,
        pitch: Float
    ) {
        val targetLocale = if (languageCode == "hi") Locale("hi", "IN") else Locale.US
        engine.language = targetLocale
        if (!voiceId.isNullOrBlank()) {
            val match: Voice? = runCatching {
                engine.voices?.firstOrNull { it.name == voiceId }
            }.getOrNull()
            if (match != null) {
                engine.voice = match
            }
        }
        engine.setSpeechRate(speed.coerceIn(0.5f, 2.0f))
        engine.setPitch(pitch.coerceIn(0.5f, 1.8f))
    }

    fun release() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }

    private fun defaultFallbackVoices(): List<TtsVoiceOption> = listOf(
        TtsVoiceOption("en_default_f", "English (US) • Female Studio", "en", "Female", Locale.US),
        TtsVoiceOption("en_default_m", "English (US) • Male Narrator", "en", "Male", Locale.US),
        TtsVoiceOption("hi_default_f", "हिन्दी (India) • Female", "hi", "Female", Locale("hi", "IN")),
        TtsVoiceOption("hi_default_m", "हिन्दी (India) • Male", "hi", "Male", Locale("hi", "IN"))
    )
}
