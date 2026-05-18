package com.deadlinemate.deepseek

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ApiKeyStore(
    private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "deadline_mate_deepseek_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveDeepSeekConfig(config: DeepSeekConfig) {
        prefs.edit()
            .putString(KEY_API_KEY, config.apiKey)
            .putString(KEY_BASE_URL, config.baseUrl)
            .putString(KEY_MODEL, config.model)
            .putBoolean(KEY_ENABLED, config.enabled)
            .apply()
    }

    fun getDeepSeekConfig(): DeepSeekConfig? {
        val apiKey = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() } ?: return null
        return DeepSeekConfig(
            apiKey = apiKey,
            baseUrl = prefs.getString(KEY_BASE_URL, DeepSeekConfig.DEFAULT_BASE_URL)
                ?.takeIf { it.isNotBlank() }
                ?: DeepSeekConfig.DEFAULT_BASE_URL,
            model = prefs.getString(KEY_MODEL, DeepSeekConfig.DEFAULT_MODEL)
                ?.takeIf { it.isNotBlank() }
                ?: DeepSeekConfig.DEFAULT_MODEL,
            enabled = prefs.getBoolean(KEY_ENABLED, false)
        )
    }

    fun clearDeepSeekConfig() {
        prefs.edit().clear().apply()
    }

    fun hasValidApiKey(): Boolean {
        val config = getDeepSeekConfig()
        return config != null && config.apiKey.isNotBlank()
    }

    private companion object {
        const val KEY_API_KEY = "deepseek.api_key"
        const val KEY_BASE_URL = "deepseek.base_url"
        const val KEY_MODEL = "deepseek.model"
        const val KEY_ENABLED = "deepseek.enabled"
    }
}
