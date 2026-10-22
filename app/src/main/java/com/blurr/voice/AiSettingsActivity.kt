package com.blurr.voice

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blurr.voice.api.LlmClient
import com.blurr.voice.api.LlmConfig
import kotlinx.coroutines.launch

/**
 * Nova "AI Settings" screen. The whole screen is built in code, so there is no layout file.
 *
 * The user enters:
 *  - Base URL of any OpenAI-compatible API (OpenRouter, Groq, a free multi-provider gateway, ...)
 *  - API key (several keys can be separated by commas; they are used in turn)
 *  - Model name
 */
class AiSettingsActivity : AppCompatActivity() {

    private lateinit var editBaseUrl: EditText
    private lateinit var editApiKey: EditText
    private lateinit var editModel: EditText
    private lateinit var switchJson: Switch
    private lateinit var switchResume: Switch
    private lateinit var textResult: TextView
    private lateinit var buttonTest: Button

    private val bg = Color.parseColor("#0E1116")
    private val fieldBg = Color.parseColor("#1B212B")
    private val textColor = Color.WHITE
    private val hintColor = Color.parseColor("#8A94A6")
    private val accent = Color.parseColor("#4FC3F7")

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "AI Settings"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }

        root.addView(label("Nova AI Settings", 22f, bold = true))
        root.addView(
            label(
                "Nova works with any OpenAI-compatible API. Fill in the address, key and model " +
                    "of the service you use. Everything is saved only on this phone.",
                14f,
                color = hintColor
            ).also { (it.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(12) }
        )

        root.addView(label("Base URL", 14f, bold = true))
        editBaseUrl = field("https://openrouter.ai/api/v1", InputType.TYPE_TEXT_VARIATION_URI)
        root.addView(editBaseUrl)
        root.addView(label("Without /chat/completions at the end. Example: https://api.groq.com/openai/v1", 12f, color = hintColor))

        root.addView(label("API key", 14f, bold = true))
        editApiKey = field(
            "sk-...   (several keys: key1,key2,key3)",
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        )
        root.addView(editApiKey)
        root.addView(label("Leave empty only if your gateway needs no key.", 12f, color = hintColor))

        root.addView(label("Model", 14f, bold = true))
        editModel = field("e.g. google/gemini-2.0-flash-001", InputType.TYPE_TEXT_VARIATION_URI)
        root.addView(editModel)

        switchJson = Switch(this).apply {
            text = "Ask the AI for JSON output (turn off if your gateway fails)"
            setTextColor(textColor)
            isChecked = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        }
        root.addView(switchJson)

        switchResume = Switch(this).apply {
            text = "Resume my video/music after Nova finishes talking"
            setTextColor(textColor)
            isChecked = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        }
        root.addView(switchResume)

        root.addView(label("Wake word", 16f, bold = true).also {
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(26)
        })
        root.addView(
            label(
                "Say \"Jarvis\" or \"Nova\" to wake Nova. It works offline and needs no account or key. " +
                    "Turn it on from the Settings tab (Enable wake word).",
                12f,
                color = hintColor
            )
        )

        val save = button("Save")
        save.setOnClickListener {
            saveAll()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }
        root.addView(save)

        buttonTest = button("Save and test connection")
        buttonTest.setOnClickListener { saveAndTest() }
        root.addView(buttonTest)

        textResult = label("", 14f)
        root.addView(textResult)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            addView(root)
        })

        loadAll()
    }

    private fun loadAll() {
        editBaseUrl.setText(LlmConfig.getBaseUrl(this))
        editApiKey.setText(LlmConfig.getApiKeyRaw(this))
        editModel.setText(LlmConfig.getModel(this))
        switchJson.isChecked = LlmConfig.isJsonModeEnabled(this)
        switchResume.isChecked = com.blurr.voice.utilities.MediaResumer.isEnabled(this)
    }

    private fun saveAll() {
        com.blurr.voice.utilities.MediaResumer.setEnabled(this, switchResume.isChecked)
        LlmConfig.save(
            this,
            baseUrl = editBaseUrl.text.toString(),
            apiKey = editApiKey.text.toString(),
            model = editModel.text.toString(),
            jsonMode = switchJson.isChecked
        )
    }

    private fun saveAndTest() {
        saveAll()
        if (!LlmConfig.isConfigured(this)) {
            showResult("Please fill in Base URL and Model first.", ok = false)
            return
        }
        buttonTest.isEnabled = false
        showResult("Testing...", ok = true)
        lifecycleScope.launch {
            try {
                val answer = LlmClient.complete(
                    listOf(LlmClient.Msg("user", "Reply with exactly one word: ready")),
                    jsonMode = false
                )
                showResult("Connected. The AI answered: ${answer.take(80)}", ok = true)
            } catch (e: Exception) {
                showResult("Failed: ${e.message}", ok = false)
            } finally {
                buttonTest.isEnabled = true
            }
        }
    }

    private fun showResult(message: String, ok: Boolean) {
        textResult.text = message
        textResult.setTextColor(if (ok) Color.parseColor("#81C784") else Color.parseColor("#E57373"))
    }

    // ---------------------------------------------------------------- small UI helpers

    private fun label(
        text: String,
        size: Float,
        bold: Boolean = false,
        color: Int = textColor
    ): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) }
    }

    private fun field(hint: String, variation: Int): EditText = EditText(this).apply {
        this.hint = hint
        setHintTextColor(hintColor)
        setTextColor(textColor)
        inputType = InputType.TYPE_CLASS_TEXT or variation or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        isSingleLine = true
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            setColor(fieldBg)
            cornerRadius = dp(10).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) }
    }

    private fun button(text: String): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.BLACK)
        background = GradientDrawable().apply {
            setColor(accent)
            cornerRadius = dp(12).toFloat()
        }
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(52)
        ).apply { topMargin = dp(16) }
    }
}
