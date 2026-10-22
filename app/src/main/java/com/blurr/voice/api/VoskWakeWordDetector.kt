package com.blurr.voice.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.blurr.voice.ConversationalAgentService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

/**
 * Nova wake word detector: says "Jarvis" or "Nova" wakes the assistant.
 *
 * It uses Vosk, a free offline speech engine, so there is no account, no access key and no cloud.
 * The small speech model is bundled in the APK under assets/vosk-model (the GitHub Actions build
 * downloads it). Recognition is limited to the two wake words, so it stays light.
 *
 * Same public shape as the old detector (start / stop and two callbacks), so the service did not change.
 */
class VoskWakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit,
    private val onApiFailure: () -> Unit
) : RecognitionListener {

    companion object {
        private const val TAG = "VoskWakeWordDetector"
        private const val SAMPLE_RATE = 16000.0f
        private const val GRAMMAR = "[\"jarvis\", \"nova\", \"[unk]\"]"
        private val WAKE_WORDS = listOf("jarvis", "nova")
        private const val RETRIGGER_GAP_MS = 3000L

        // Unpacking the model takes a few seconds, so keep it for the life of the process.
        @Volatile
        private var cachedModel: Model? = null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var scope: CoroutineScope? = null
    @Volatile
    private var isListening = false
    private var lastTriggerAt = 0L

    fun start() {
        if (isListening) {
            Log.d(TAG, "Already started.")
            return
        }
        isListening = true
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        val ready = cachedModel
        if (ready != null) {
            startRecognizer(ready)
            return
        }
        try {
            StorageService.unpack(
                context,
                "vosk-model",
                "vosk-model-dir",
                { model: Model ->
                    cachedModel = model
                    if (isListening) startRecognizer(model)
                },
                { error: IOException ->
                    Log.e(TAG, "Could not unpack the wake word model: ${error.message}", error)
                    fail()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not start unpacking the wake word model: ${e.message}", e)
            fail()
        }
    }

    private fun startRecognizer(model: Model) {
        try {
            val rec = Recognizer(model, SAMPLE_RATE, GRAMMAR)
            val service = SpeechService(rec, SAMPLE_RATE)
            recognizer = rec
            speechService = service
            service.startListening(this)
            Log.d(TAG, "Wake word listening started (say Jarvis or Nova).")
        } catch (e: Exception) {
            Log.e(TAG, "Could not start wake word listening: ${e.message}", e)
            fail()
        }
    }

    private fun stopRecognizer() {
        try {
            speechService?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "stop failed: ${e.message}")
        }
        try {
            speechService?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "shutdown failed: ${e.message}")
        }
        speechService = null
        try {
            recognizer?.close()
        } catch (e: Exception) {
            Log.w(TAG, "recognizer close failed: ${e.message}")
        }
        recognizer = null
    }

    private fun fail() {
        isListening = false
        stopRecognizer()
        mainHandler.post { onApiFailure() }
    }

    fun stop() {
        if (!isListening && speechService == null) {
            Log.d(TAG, "Already stopped.")
            return
        }
        isListening = false
        stopRecognizer()
        scope?.cancel()
        scope = null
        Log.d(TAG, "Wake word listening stopped.")
    }

    /** Vosk gives JSON such as {"partial":"jarvis"} or {"text":"jarvis"}. */
    private fun handleHypothesis(hypothesis: String?) {
        if (hypothesis.isNullOrBlank() || !isListening) return
        val spoken = try {
            val json = JSONObject(hypothesis)
            json.optString("text", json.optString("partial", ""))
        } catch (e: Exception) {
            hypothesis
        }.lowercase()

        if (WAKE_WORDS.none { spoken.contains(it) }) return

        val now = System.currentTimeMillis()
        if (now - lastTriggerAt < RETRIGGER_GAP_MS) return
        lastTriggerAt = now

        Log.d(TAG, "Wake word heard: $spoken")
        // Free the microphone for the conversation, then listen again when it is over.
        stopRecognizer()
        mainHandler.post { onWakeWordDetected() }
        resumeAfterConversation()
    }

    private fun resumeAfterConversation() {
        scope?.launch {
            delay(2500) // give the conversation service time to start
            var waitedSeconds = 0
            while (isListening && ConversationalAgentService.isRunning && waitedSeconds < 900) {
                delay(1000)
                waitedSeconds++
            }
            val model = cachedModel
            if (isListening && model != null) {
                startRecognizer(model)
            }
        }
    }

    override fun onPartialResult(hypothesis: String?) {
        handleHypothesis(hypothesis)
    }

    override fun onResult(hypothesis: String?) {
        handleHypothesis(hypothesis)
    }

    override fun onFinalResult(hypothesis: String?) {
        // Nothing to do. Wake words are caught in partial and normal results.
    }

    override fun onError(exception: Exception?) {
        Log.e(TAG, "Vosk error: ${exception?.message}", exception)
        if (isListening) fail()
    }

    override fun onTimeout() {
        // Not used: we listen until stopped.
    }
}
