package com.pdgus86.voicenotes.data

import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Sends one GET to {baseUrl}/models with the API key, only when the
 * user taps Test connection in the provider dialog. Works for any
 * OpenAI-compatible provider: OpenRouter, OpenAI, DeepSeek, Custom,
 * and a PC over Tailscale or localhost (Ollama, LM Studio, or any
 * other OpenAI-compatible server).
 *
 * Runs on a background thread; never call it on the main thread.
 * Timeouts are short on purpose so a bad URL or a dead server fails
 * fast instead of hanging the dialog.
 */
object ProviderConnectionTester {

    sealed class Result {
        data object Success : Result()
        data class Failure(val reason: Reason, val httpCode: Int = 0) : Result()
    }

    enum class Reason {
        NO_URL,
        BAD_URL,
        NO_NETWORK,
        REFUSED,
        TIMEOUT,
        BAD_KEY,
        FORBIDDEN,
        NOT_FOUND,
        SERVER_ERROR
    }

    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    fun test(baseUrl: String, apiKey: String): Result {
        val trimmed = baseUrl.trim().removeSuffix("/")
        if (trimmed.isEmpty()) return Result.Failure(Reason.NO_URL)
        val url = try {
            URL("$trimmed/models")
        } catch (e: MalformedURLException) {
            return Result.Failure(Reason.BAD_URL)
        }
        var connection: HttpURLConnection? = null
        return try {
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            when (val code = connection.responseCode) {
                200 -> Result.Success
                401 -> Result.Failure(Reason.BAD_KEY)
                403 -> Result.Failure(Reason.FORBIDDEN)
                404 -> Result.Failure(Reason.NOT_FOUND)
                else -> Result.Failure(Reason.SERVER_ERROR, code)
            }
        } catch (e: SocketTimeoutException) {
            Result.Failure(Reason.TIMEOUT)
        } catch (e: UnknownHostException) {
            Result.Failure(Reason.NO_NETWORK)
        } catch (e: ConnectException) {
            Result.Failure(Reason.REFUSED)
        } catch (e: IOException) {
            Result.Failure(Reason.NO_NETWORK)
        } catch (e: Exception) {
            Result.Failure(Reason.NO_NETWORK)
        } finally {
            connection?.disconnect()
        }
    }
}
