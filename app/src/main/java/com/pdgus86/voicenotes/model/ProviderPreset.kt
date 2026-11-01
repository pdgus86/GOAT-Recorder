package com.pdgus86.voicenotes.model

/**
 * Built-in presets. Custom leaves the URL and model blank so any
 * OpenAI-compatible endpoint can be typed in: localhost, a Tailscale
 * address, Ollama on a PC, LM Studio, or any other API.
 *
 * Local (on-device) is the offline path: the model runs on the phone
 * itself, the same LiteRT/Gemma pattern Kai uses, so it needs no URL,
 * no API key, and no internet at all.
 */
enum class ProviderPreset(
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    val isLocal: Boolean = false
) {
    LOCAL(
        displayName = "Local (on-device)",
        baseUrl = "",
        defaultModel = "gemma-4-e2b-it",
        isLocal = true
    ),
    OPEN_ROUTER(
        displayName = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "openrouter/auto"
    ),
    OPEN_AI(
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini"
    ),
    DEEP_SEEK(
        displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-chat"
    ),
    CUSTOM(
        displayName = "Custom",
        baseUrl = "",
        defaultModel = ""
    )
}
