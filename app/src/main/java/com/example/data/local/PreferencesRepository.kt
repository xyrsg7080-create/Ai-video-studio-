package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.localization.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(name = "studio_prefs")

data class StudioPreferences(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val offlineLocalMode: Boolean = false,
    val batterySaverRender: Boolean = false,
    val proxyPreviewEnabled: Boolean = true,
    val autosaveSeconds: Int = 15,
    val onboardingCompleted: Boolean = false,
    val youtubeChannelId: String = "",
    val youtubeChannelTitle: String = "",
    val youtubeChannelSubs: String = "",
    val aiRequestsToday: Int = 0,
    val youtubeQuotaUnitsToday: Int = 0,
    val lastActiveProjectId: String = "",
    val voiceConsentGranted: Boolean = false
)

class PreferencesRepository(private val context: Context) {
    private val dataStore = context.applicationContext.studioDataStore

    val preferencesFlow: Flow<StudioPreferences> = dataStore.data.map { prefs ->
        val langCode = prefs[KEY_LANGUAGE] ?: AppLanguage.ENGLISH.code
        StudioPreferences(
            language = if (langCode == AppLanguage.HINDI.code) AppLanguage.HINDI else AppLanguage.ENGLISH,
            offlineLocalMode = prefs[KEY_OFFLINE_MODE] ?: false,
            batterySaverRender = prefs[KEY_BATTERY_SAVER] ?: false,
            proxyPreviewEnabled = prefs[KEY_PROXY_PREVIEW] ?: true,
            autosaveSeconds = prefs[KEY_AUTOSAVE_SEC] ?: 15,
            onboardingCompleted = prefs[KEY_ONBOARDING] ?: false,
            youtubeChannelId = prefs[KEY_YT_CHANNEL_ID] ?: "",
            youtubeChannelTitle = prefs[KEY_YT_CHANNEL_TITLE] ?: "",
            youtubeChannelSubs = prefs[KEY_YT_CHANNEL_SUBS] ?: "",
            aiRequestsToday = prefs[KEY_AI_REQUESTS] ?: 0,
            youtubeQuotaUnitsToday = prefs[KEY_YT_QUOTA] ?: 0,
            lastActiveProjectId = prefs[KEY_LAST_PROJECT_ID] ?: "",
            voiceConsentGranted = prefs[KEY_VOICE_CONSENT] ?: false
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[KEY_LANGUAGE] = language.code }
    }

    suspend fun setOfflineLocalMode(enabled: Boolean) {
        dataStore.edit { it[KEY_OFFLINE_MODE] = enabled }
    }

    suspend fun setBatterySaverRender(enabled: Boolean) {
        dataStore.edit { it[KEY_BATTERY_SAVER] = enabled }
    }

    suspend fun setProxyPreviewEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_PROXY_PREVIEW] = enabled }
    }

    suspend fun setAutosaveSeconds(seconds: Int) {
        dataStore.edit { it[KEY_AUTOSAVE_SEC] = seconds.coerceIn(5, 120) }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING] = completed }
    }

    suspend fun setYouTubeChannel(id: String, title: String, subs: String) {
        dataStore.edit {
            it[KEY_YT_CHANNEL_ID] = id
            it[KEY_YT_CHANNEL_TITLE] = title
            it[KEY_YT_CHANNEL_SUBS] = subs
        }
    }

    suspend fun incrementAiRequestCount() {
        dataStore.edit { prefs ->
            prefs[KEY_AI_REQUESTS] = (prefs[KEY_AI_REQUESTS] ?: 0) + 1
        }
    }

    suspend fun addYouTubeQuotaUnits(units: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_YT_QUOTA] = (prefs[KEY_YT_QUOTA] ?: 0) + units
        }
    }

    suspend fun setLastActiveProjectId(projectId: String) {
        dataStore.edit { it[KEY_LAST_PROJECT_ID] = projectId }
    }

    suspend fun setVoiceConsentGranted(granted: Boolean) {
        dataStore.edit { it[KEY_VOICE_CONSENT] = granted }
    }

    suspend fun resetAllPreferences() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_LANGUAGE = stringPreferencesKey("language_code")
        val KEY_OFFLINE_MODE = booleanPreferencesKey("offline_local_mode")
        val KEY_BATTERY_SAVER = booleanPreferencesKey("battery_saver_render")
        val KEY_PROXY_PREVIEW = booleanPreferencesKey("proxy_preview_enabled")
        val KEY_AUTOSAVE_SEC = intPreferencesKey("autosave_seconds")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_completed")
        val KEY_YT_CHANNEL_ID = stringPreferencesKey("yt_channel_id")
        val KEY_YT_CHANNEL_TITLE = stringPreferencesKey("yt_channel_title")
        val KEY_YT_CHANNEL_SUBS = stringPreferencesKey("yt_channel_subs")
        val KEY_AI_REQUESTS = intPreferencesKey("ai_requests_today")
        val KEY_YT_QUOTA = intPreferencesKey("yt_quota_units_today")
        val KEY_LAST_PROJECT_ID = stringPreferencesKey("last_active_project_id")
        val KEY_VOICE_CONSENT = booleanPreferencesKey("voice_consent_granted")
    }
}
