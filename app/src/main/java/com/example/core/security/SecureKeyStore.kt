package com.example.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed Android Keystore AES-256-GCM vault for optional user-supplied
 * third-party API keys and OAuth tokens (never hardcoded, never stored in plaintext).
 */
class SecureKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "studio_secure_vault",
        Context.MODE_PRIVATE
    )

    fun putSecret(key: String, value: String) {
        if (value.isBlank()) {
            removeSecret(key)
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val packed = ByteBuffer.allocate(4 + iv.size + ciphertext.size)
                .putInt(iv.size)
                .put(iv)
                .put(ciphertext)
                .array()
            val encoded = Base64.encodeToString(packed, Base64.NO_WRAP)
            prefs.edit().putString(key, encoded).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt secret for key=$key", e)
        }
    }

    fun getSecret(key: String): String {
        val encoded = prefs.getString(key, null) ?: return ""
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(packed)
            val ivLen = buffer.int
            if (ivLen <= 0 || ivLen > 32) return ""
            val iv = ByteArray(ivLen)
            buffer.get(iv)
            val ciphertext = ByteArray(buffer.remaining())
            buffer.get(ciphertext)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(128, iv)
            )
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt secret for key=$key", e)
            ""
        }
    }

    fun hasSecret(key: String): Boolean = getSecret(key).isNotBlank()

    fun removeSecret(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val TAG = "SecureKeyStore"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "ai_video_studio_vault_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        const val KEY_PEXELS_API = "pexels_api_key"
        const val KEY_PIXABAY_API = "pixabay_api_key"
        const val KEY_CUSTOM_GEMINI_API = "custom_gemini_api_key"
        const val KEY_YOUTUBE_CLIENT_ID = "youtube_oauth_client_id"
        const val KEY_YOUTUBE_ACCESS_TOKEN = "youtube_access_token"
        const val KEY_YOUTUBE_REFRESH_TOKEN = "youtube_refresh_token"
        const val KEY_YOUTUBE_TOKEN_EXPIRY = "youtube_token_expiry"
    }
}
