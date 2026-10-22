package com.blurr.voice.api

import android.content.Context
import android.util.Log
import com.blurr.voice.BuildConfig
import com.blurr.voice.MyApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Nova LLM settings. Stored on the phone (SharedPreferences) and editable from the
 * "AI Settings" screen. Anything left empty falls back to the values from local.properties
 * (LLM_BASE_URL / LLM_API_KEY / LLM_MODEL) that were baked in at build time.
 */
object LlmConfig {
    private const val PREFS = "nova_llm_config"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_JSON_MODE = "json_mode"

    private fun prefs(context: Context = MyApplication.appContext) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getBaseUrl(context: Context = MyApplication.appContext): String =
        prefs(context).getString(KEY_BASE_URL, null)?.trim().orEmpty()
            .ifEmpty { BuildConfig.LLM_BASE_URL.trim() }

    /** Comma separated list of keys. They are used round-robin. */
    fun getApiKeyRaw(context: Context = MyApplication.appContext): String =
        prefs(context).getString(KEY_API_KEY, null)?.trim().orEmpty()
            .ifEmpty { BuildConfig.LLM_API_KEY.trim() }

    fun getApiKeys(context: Context = MyApplication.appContext): List<String> =
        getApiKeyRaw(context).split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun getModel(context: Context = MyApplication.appContext): String =
        prefs(context).getString(KEY_MODEL, null)?.trim().orEmpty()
            .ifEmpty { BuildConfig.LLM_MODEL.trim() }

    fun isJsonModeEnabled(context: Context = MyApplication.appContext): Boolean =
        prefs(context).getBoolean(KEY_JSON_MODE, true)

    fun save(
        context: Context,
        baseUrl: String,
        apiKey: String,
        model: String,
        jsonMode: Boolean
    ) {
        prefs(context).edit()
            .putString(KEY_BASE_URL, baseUrl.trim())
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_MODEL, model.trim())
            .putBoolean(KEY_JSON_MODE, jsonMode)
            .apply()
    }

    fun isConfigured(context: Context = MyApplication.appContext): Boolean =
        getBaseUrl(context).isNotEmpty() && getModel(context).isNotEmpty()
}

/**
 * Minimal OpenAI-compatible chat-completions client.
 * Works with OpenRouter, OpenAI, Groq, Gemini's OpenAI endpoint, a self hosted "free LLM gateway",
 * LM Studio / Ollama (/v1), etc. - anything that serves POST {base}/chat/completions.
 */
object LlmClient {
    private const val TAG = "LlmClient"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val keyIndex = AtomicInteger(0)

    class LlmException(message: String, val httpCode: Int? = null) : Exception(message)

    /** role is "system", "user" or "assistant". */
    data class Msg(val role: String, val text: String)

    private fun nextKey(): String? {
        val keys = LlmConfig.getApiKeys()
        if (keys.isEmpty()) return null
        return keys[Math.floorMod(keyIndex.getAndIncrement(), keys.size)]
    }

    private fun chatUrl(base: String): String {
        val b = base.trim().trimEnd('/')
        return if (b.endsWith("/chat/completions")) b else "$b/chat/completions"
    }

    /** Joins consecutive messages of the same role; some gateways reject two "user" turns in a row. */
    private fun normalize(messages: List<Msg>): List<Msg> {
        val out = mutableListOf<Msg>()
        for (m in messages) {
            if (m.text.isBlank()) continue
            val last = out.lastOrNull()
            if (last != null && last.role == m.role) {
                out[out.size - 1] = Msg(last.role, last.text + "\n\n" + m.text)
            } else {
                out.add(m)
            }
        }
        return out
    }

    /**
     * One completion call. Throws [LlmException] on any failure so callers can retry.
     */
    suspend fun complete(
        messages: List<Msg>,
        jsonMode: Boolean = false,
        modelOverride: String? = null
    ): String = withContext(Dispatchers.IO) {
        val base = LlmConfig.getBaseUrl()
        val model = modelOverride?.takeIf { it.isNotBlank() } ?: LlmConfig.getModel()
        if (base.isEmpty() || model.isEmpty()) {
            throw LlmException("AI is not set up. Open AI Settings and add Base URL, API key and Model.")
        }

        val msgs = normalize(messages)
        if (msgs.isEmpty()) throw LlmException("Nothing to send to the model.")

        val useJsonMode = jsonMode && LlmConfig.isJsonModeEnabled()
        try {
            return@withContext post(base, model, msgs, useJsonMode)
        } catch (e: LlmException) {
            // Many free gateways do not understand response_format. Retry once without it.
            if (useJsonMode && e.httpCode == 400) {
                Log.w(TAG, "Provider rejected json mode, retrying without it.")
                return@withContext post(base, model, msgs, false)
            }
            throw e
        }
    }

    private fun post(base: String, model: String, msgs: List<Msg>, jsonMode: Boolean): String {
        val body = JSONObject().apply {
            put("model", model)
            put("stream", false)
            put("messages", JSONArray().apply {
                msgs.forEach { m ->
                    put(JSONObject().put("role", m.role).put("content", m.text))
                }
            })
            if (jsonMode) put("response_format", JSONObject().put("type", "json_object"))
        }

        val builder = Request.Builder()
            .url(chatUrl(base))
            .post(body.toString().toRequestBody(JSON))
            .addHeader("Content-Type", "application/json")
        nextKey()?.let { builder.addHeader("Authorization", "Bearer $it") }

        return http.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string()
            if (!response.isSuccessful || raw.isNullOrBlank()) {
                Log.e(TAG, "HTTP ${response.code}: ${raw?.take(400)}")
                throw LlmException("AI request failed (HTTP ${response.code}): ${raw?.take(200)}", response.code)
            }
            parseContent(raw)
        }
    }

    private fun parseContent(raw: String): String {
        try {
            val root = JSONObject(raw)
            if (root.has("error") && !root.has("choices")) {
                throw LlmException("AI error: ${root.opt("error")}")
            }
            val choices = root.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                throw LlmException("AI returned no choices: ${raw.take(200)}")
            }
            val message = choices.getJSONObject(0).optJSONObject("message")
            val contentAny = message?.opt("content")
            val text = when (contentAny) {
                is String -> contentAny
                is JSONArray -> buildString {
                    for (i in 0 until contentAny.length()) {
                        val part = contentAny.optJSONObject(i)
                        if (part != null && part.has("text")) append(part.optString("text"))
                    }
                }
                else -> choices.getJSONObject(0).optString("text", "")
            }
            val cleaned = stripThinking(text).trim()
            if (cleaned.isEmpty()) throw LlmException("AI returned an empty answer.")
            return cleaned
        } catch (e: LlmException) {
            throw e
        } catch (e: Exception) {
            throw LlmException("Could not read AI response: ${e.message}")
        }
    }

    /** Removes <think>...</think> blocks that some reasoning models put in the answer. */
    private fun stripThinking(text: String): String =
        text.replace(Regex("(?s)<think>.*?</think>"), "")

    /**
     * Models often wrap JSON in ```json fences or add a sentence around it.
     * Returns just the JSON object if one can be found, otherwise the original text.
     */
    fun cleanJson(text: String): String {
        var t = text.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim()
            t = t.removeSuffix("```").trim()
        }
        if (t.startsWith("{") && t.endsWith("}")) return t
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        return if (start in 0 until end) t.substring(start, end + 1) else t
    }
}
