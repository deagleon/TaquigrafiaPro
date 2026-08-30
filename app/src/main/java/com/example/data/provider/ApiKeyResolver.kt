package com.example.data.provider

import android.content.SharedPreferences
import com.example.BuildConfig

class ApiKeyResolver(private val prefs: SharedPreferences) {

    private fun readPrefKey(name: String): String? =
        prefs.getString(name, "")
            ?.replace(Regex("[\\r\\n\\t\\s]+"), "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun geminiKey(): String? {
        val fromPrefs = readPrefKey("custom_api_key")
        if (fromPrefs != null) return fromPrefs
        return BuildConfig.GEMINI_API_KEY.takeIf { it.isNotEmpty() && it != "MY_GEMINI_API_KEY" }
    }

    fun openRouterKey(): String? {
        val fromPrefs = readPrefKey("openrouter_api_key")
        if (fromPrefs != null) return fromPrefs
        return BuildConfig.OPENROUTER_API_KEY.takeIf { it.isNotEmpty() && it != "MY_OPENROUTER_API_KEY" }
    }

    fun hasEffectiveKey(providerId: String): Boolean = when (providerId) {
        "openrouter" -> !openRouterKey().isNullOrEmpty()
        else -> !geminiKey().isNullOrEmpty()
    }
}
