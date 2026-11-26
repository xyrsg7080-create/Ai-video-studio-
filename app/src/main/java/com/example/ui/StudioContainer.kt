package com.example.ui

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.example.core.security.SecureKeyStore
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.remote.AiStudioRepository
import com.example.data.remote.InteractionsVideoClient
import com.example.data.remote.StockMediaRepository
import com.example.data.remote.YouTubeRepository
import com.example.media.VideoRenderEngine
import com.example.media.VoiceStudioManager

@UnstableApi
class StudioContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }
    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(appContext) }
    val keyStore: SecureKeyStore by lazy { SecureKeyStore(appContext) }
    val aiRepository: AiStudioRepository by lazy { AiStudioRepository() }
    val interactionsVideoClient: InteractionsVideoClient by lazy { InteractionsVideoClient(appContext, keyStore) }
    val stockMediaRepository: StockMediaRepository by lazy { StockMediaRepository(appContext, keyStore) }
    val youTubeRepository: YouTubeRepository by lazy { YouTubeRepository(appContext, keyStore) }
    val voiceStudioManager: VoiceStudioManager by lazy { VoiceStudioManager(appContext) }
    val videoRenderEngine: VideoRenderEngine by lazy { VideoRenderEngine(appContext) }
}
