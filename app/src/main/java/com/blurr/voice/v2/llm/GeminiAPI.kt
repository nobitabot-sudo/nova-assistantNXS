package com.blurr.voice.v2.llm

import android.content.Context
import android.util.Log
import com.blurr.voice.api.LlmClient
import com.blurr.voice.utilities.ApiKeyManager
import com.blurr.voice.v2.AgentOutput
import com.blurr.voice.v2.logging.TaskLogger
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Nova: the client the phone-operating agent uses to ask the AI for its next action.
 *
 * The class name and constructor are unchanged so the rest of the agent code is untouched,
 * but it now sends everything to the OpenAI-compatible endpoint configured in "AI Settings"
 * (Base URL + API key + model) via [LlmClient]. [modelName] and [apiKeyManager] are ignored.
 */
@Suppress("UNUSED_PARAMETER")
class GeminiApi(
    private val modelName: String,
    private val apiKeyManager: ApiKeyManager,
    private val context: Context,
    private val maxRetry: Int = 3
) {

    companion object {
        private const val TAG = "NovaAgentLlm"
    }

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Asks the model for the next step and parses it into an [AgentOutput].
     * Returns null if the AI could not be reached or its answer was not valid JSON.
     */
    suspend fun generateAgentOutput(messages: List<GeminiMessage>): AgentOutput? {
        val rawText = retryWithBackoff(times = maxRetry) {
            performApiCall(messages)
        } ?: return null

        val jsonString = LlmClient.cleanJson(rawText)

        try {
            val input = jsonParser.encodeToString(messages)
            TaskLogger.log(context, input, jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log task: ${e.message}")
        }

        return try {
            Log.d(TAG, "Parsing JSON response: $jsonString")
            jsonParser.decodeFromString<AgentOutput>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON into AgentOutput. Error: ${e.message}", e)
            null
        }
    }

    private suspend fun performApiCall(messages: List<GeminiMessage>): String {
        val converted = messages.mapIndexedNotNull { index, message ->
            val text = message.parts.filterIsInstance<TextPart>().joinToString("\n") { it.text }
            if (text.isBlank()) {
                null
            } else {
                // The agent's big instruction prompt is the first message (stored with the MODEL role).
                // OpenAI-style APIs expect that to be the "system" message.
                val role = when {
                    index == 0 && message.role == MessageRole.MODEL -> "system"
                    message.role == MessageRole.MODEL -> "assistant"
                    else -> "user"
                }
                LlmClient.Msg(role, text)
            }
        }
        return LlmClient.complete(converted, jsonMode = true)
    }
}

/**
 * Custom exception kept for source compatibility.
 */
class ContentBlockedException(message: String) : Exception(message)

/**
 * A higher-order function that provides a generic retry mechanism with exponential backoff.
 * Returns the result of the block if successful, or null if all retries fail. Setup problems
 * (missing URL/model, wrong key, wrong address) are not retried because retrying cannot fix them.
 */
private suspend fun <T> retryWithBackoff(
    times: Int,
    initialDelay: Long = 1000L,
    maxDelay: Long = 16000L,
    factor: Double = 2.0,
    block: suspend () -> T
): T? {
    var currentDelay = initialDelay
    repeat(times) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            Log.e("RetryUtil", "Attempt ${attempt + 1}/$times failed: ${e.message}", e)
            val code = (e as? LlmClient.LlmException)?.httpCode
            if (code == 401 || code == 403 || code == 404) {
                Log.e("RetryUtil", "AI setup problem (HTTP $code). Not retrying.")
                return null
            }
            if (attempt == times - 1) {
                Log.e("RetryUtil", "All $times retry attempts failed.")
                return null
            }
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
    }
    return null
}
