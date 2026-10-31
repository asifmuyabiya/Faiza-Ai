package com.faiza.ai.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.faiza.ai.R
import com.faiza.ai.util.PrefsManager
import com.google.android.material.button.MaterialButton
import com.faiza.ai.ai.provider.GeminiProvider
import com.faiza.ai.ai.provider.ProviderType
import kotlinx.coroutines.*

class ApiSetupActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private var currentStep = 1
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_setup)
        prefs = PrefsManager(this)

        setupBackButton()
        setupStep1()
        setupStep2()
        setupStep3()
        setupSuccess()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun setupBackButton() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupStep1() {
        findViewById<MaterialButton>(R.id.btnStep1Next).setOnClickListener {
            showStep(2)
        }
    }

    private fun setupStep2() {
        findViewById<MaterialButton>(R.id.btnOpenAIStudio).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")))
        }
        findViewById<MaterialButton>(R.id.btnStep2Next).setOnClickListener {
            showStep(3)
        }
    }

    private fun setupStep3() {
        findViewById<MaterialButton>(R.id.btnVerifyKey).setOnClickListener {
            val keyInput = findViewById<EditText>(R.id.etApiKey)
            val key = keyInput.text.toString().trim()

            if (key.isEmpty()) {
                showKeyStatus("Please paste your API key", false)
                return@setOnClickListener
            }

            if (key.length < 30) {
                showKeyStatus("This doesn't look like a valid Gemini API key", false)
                return@setOnClickListener
            }

            verifyApiKey(key)
        }
    }

    private fun setupSuccess() {
        findViewById<MaterialButton>(R.id.btnGoToDashboard).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            })
            finish()
        }
    }

    private fun verifyApiKey(key: String) {
        val progress = findViewById<ProgressBar>(R.id.progressVerify)
        val btnVerify = findViewById<MaterialButton>(R.id.btnVerifyKey)

        progress.visibility = View.VISIBLE
        btnVerify.isEnabled = false
        btnVerify.text = "Checking…"
        showKeyStatus("Testing your API key...", null)

        scope.launch {
            val isValid = testGeminiKey(key)

            progress.visibility = View.GONE
            btnVerify.isEnabled = true
            btnVerify.text = "Verify & save"

            if (isValid) {
                prefs.geminiApiKey = key
                prefs.setModel(ProviderType.GEMINI, ProviderType.GEMINI.defaultModel)
                prefs.aiProvider = ProviderType.GEMINI.id
                showKeyStatus("API Key verified successfully!", true)

                delay(800)
                showStep(4)
            } else {
                showKeyStatus("Invalid API key. Please check and try again.", false)
            }
        }
    }

    private suspend fun testGeminiKey(key: String): Boolean = try {
        GeminiProvider(key, ProviderType.GEMINI.defaultModel).listModels().isNotEmpty()
    } catch (_: Exception) {
        false
    }

    private fun showKeyStatus(message: String, success: Boolean?) {
        val tv = findViewById<TextView>(R.id.tvKeyStatus)
        tv.visibility = View.VISIBLE
        tv.text = message
        tv.setTextColor(getColor(when (success) {
            true -> R.color.success
            false -> R.color.danger
            null -> R.color.warning
        }))
    }

    private fun showStep(step: Int) {
        currentStep = step

        findViewById<View>(R.id.step1).visibility = if (step == 1) View.VISIBLE else View.GONE
        findViewById<View>(R.id.step2).visibility = if (step == 2) View.VISIBLE else View.GONE
        findViewById<View>(R.id.step3).visibility = if (step == 3) View.VISIBLE else View.GONE
        findViewById<View>(R.id.stepSuccess).visibility = if (step == 4) View.VISIBLE else View.GONE

        // Update step indicator
        val activeColor = getColor(R.color.primary)
        val inactiveColor = getColor(R.color.stroke)
        val doneColor = getColor(R.color.success)

        findViewById<View>(R.id.step1Dot).setBackgroundColor(if (step > 1) doneColor else if (step == 1) activeColor else inactiveColor)
        findViewById<View>(R.id.step2Dot).setBackgroundColor(if (step > 2) doneColor else if (step == 2) activeColor else inactiveColor)
        findViewById<View>(R.id.step3Dot).setBackgroundColor(if (step > 3) doneColor else if (step == 3) activeColor else inactiveColor)

        if (step == 4) {
            findViewById<View>(R.id.stepIndicator).visibility = View.GONE
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentStep > 1 && currentStep < 4) {
            showStep(currentStep - 1)
        } else {
            super.onBackPressed()
        }
    }
}
