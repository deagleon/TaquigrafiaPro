package com.example.data.provider

import android.content.SharedPreferences
import com.example.data.api.RetrofitClient

class ProviderRegistry(private val providers: Map<String, TranscriptionProvider>) {

    fun get(id: String): TranscriptionProvider? = providers[id]

    fun all(): List<TranscriptionProvider> = providers.values.toList()

    companion object {
        fun create(prefs: SharedPreferences, resolver: ApiKeyResolver): ProviderRegistry {
            val gemini = GeminiTranscriptionProvider(resolver, RetrofitClient.service)
            val openRouter = OpenRouterTranscriptionProvider(resolver, RetrofitClient.openRouterService, prefs)
            return ProviderRegistry(mapOf(gemini.id to gemini, openRouter.id to openRouter))
        }
    }
}
