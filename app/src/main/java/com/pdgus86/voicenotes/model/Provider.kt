package com.pdgus86.voicenotes.model

import java.util.UUID

/**
 * One AI provider, following the Kai and RPClient pattern:
 * a provider is a name plus a base URL plus an API key plus a model name.
 * A local provider runs on the phone itself (the LiteRT/Gemma pattern
 * Kai uses), so it has no base URL and no API key, and it works with
 * zero connectivity. No network calls are made from this class. Phase 2
 * wires these settings up to transcription, summaries, and action items.
 */
data class Provider(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val isLocal: Boolean = false
)
