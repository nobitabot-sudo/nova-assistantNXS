package com.blurr.voice.api

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.blurr.voice.MyApplication
import com.blurr.voice.utilities.NetworkConnectivityManager
import com.blurr.voice.utilities.NetworkNotifier
import com.google.ai.client.generativeai.type.TextPart
import kotlinx.coroutines.delay

/**
 * Nova: the old name is kept so existing callers keep working, but this no longer talks to Gemini
 * or to the Panda cloud proxy. Every call goes to the OpenAI-compatible endpoint the user typed into
 * "AI Settings" (Base URL + API key + model), through [LlmClient].
 *
 * The chat format is unchanged: a list of (role, parts). Roles are "user" or "model".
 * Images are ignored, because most free gateways only accept text.
 */
object GeminiApi {
    private const val TAG = "GeminiApi"

    /**
     * Returns the model's text answer, or null if the AI could not be reached after all retries.
     * [modelName] is accepted only for source compatibility; the model from AI Settings is used.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun generateContent(
        chat: List<Pair<String, List<Any>>>,
        images: List<Bitmap> = emptyList(),
        modelName: String = "",
        maxRetry: Int = 3,
        context: Context? = null
    ): String? {
        try {
            val appCtx = context ?: MyApplication.appContext
            if (!NetworkConnectivityManager(appCtx).isNetworkAvailable()) {
                Log.e(TAG, "No internet connection. Skipping AI call.")
                NetworkNotifier.notifyOffline()
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network check failed, assuming offline. ${e.message}")
            return null
        }

        val messages = chat.mapNotNull { (role, parts) ->
            val text = parts.filterIsInstance<TextPart>().joinToString("\n") { it.text }
            if (text.isBlank()) {
                null
            } else {
                val apiRole = if (role.equals("model", ignoreCase = true) ||
                    role.equals("assistant", ignoreCase = true)
                ) "assistant" else "user"
                LlmClient.Msg(apiRole, text)
            }
        }

        var attempt = 0
        while (attempt < maxRetry) {
            try {
                // The Nova prompts ask for a JSON object, so ask the provider for JSON when it can.
                val raw = LlmClient.complete(messages, jsonMode = true)
                return LlmClient.cleanJson(raw)
            } catch (e: LlmClient.LlmException) {
                Log.e(TAG, "AI call failed (attempt ${attempt + 1}/$maxRetry): ${e.message}")
                // Bad key / bad URL / missing setup will not fix themselves by retrying.
                if (e.httpCode == 401 || e.httpCode == 403 || e.httpCode == 404 ||
                    !LlmConfig.isConfigured() || LlmConfig.getApiKeys().isEmpty()
                ) {
                    return null
                }
            } catch (e: Exception) {
                Log.e(TAG, "AI call error (attempt ${attempt + 1}/$maxRetry): ${e.message}", e)
            }
            attempt++
            if (attempt < maxRetry) delay(1000L * attempt)
        }
        return null
    }
}
