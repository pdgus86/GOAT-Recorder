package com.pdgus86.voicenotes.data

import android.content.Context
import com.pdgus86.voicenotes.model.Provider
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores providers in SharedPreferences as a JSON array. No network
 * access. Phase 2 reads the same stored providers to make its calls.
 */
class ProviderRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun listProviders(): List<Provider> {
        val json = prefs.getString(KEY_PROVIDERS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val out = mutableListOf<Provider>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                out.add(
                    Provider(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        baseUrl = obj.optString("baseUrl"),
                        apiKey = obj.optString("apiKey"),
                        model = obj.optString("model"),
                        isLocal = obj.optBoolean("isLocal", false)
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveProvider(provider: Provider) {
        val current = listProviders().toMutableList()
        val index = current.indexOfFirst { it.id == provider.id }
        if (index >= 0) {
            current[index] = provider
        } else {
            current.add(provider)
        }
        persist(current)
    }

    fun deleteProvider(provider: Provider) {
        val current = listProviders().filter { it.id != provider.id }
        persist(current)
    }

    /**
     * The provider queued jobs are sent to when they run. Phase 2 reads
     * this when a job runs; null means no provider is selected yet, and
     * the job stays pending. There is no UI for picking it in phase 1.
     */
    fun getSelectedProviderId(): String? {
        return prefs.getString(KEY_SELECTED_PROVIDER_ID, null)
    }

    fun setSelectedProviderId(id: String?) {
        prefs.edit().putString(KEY_SELECTED_PROVIDER_ID, id).apply()
    }

    private fun persist(providers: List<Provider>) {
        val array = JSONArray()
        for (provider in providers) {
            array.put(
                JSONObject().apply {
                    put("id", provider.id)
                    put("name", provider.name)
                    put("baseUrl", provider.baseUrl)
                    put("apiKey", provider.apiKey)
                    put("model", provider.model)
                    put("isLocal", provider.isLocal)
                }
            )
        }
        prefs.edit().putString(KEY_PROVIDERS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "voicenotes_providers"
        private const val KEY_PROVIDERS = "providers_json"
        private const val KEY_SELECTED_PROVIDER_ID = "selected_provider_id"
    }
}
