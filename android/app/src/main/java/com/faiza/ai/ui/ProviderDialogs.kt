package com.faiza.ai.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.provider.AIProviderException
import com.faiza.ai.ai.provider.AIProviders
import com.faiza.ai.ai.provider.ProviderType
import kotlinx.coroutines.launch
import java.io.IOException

object ProviderDialogs {

    fun showProviderPicker(activity: AppCompatActivity, onSaved: () -> Unit) {
        val prefs = FaizaApp.instance.prefs
        val isPro = FaizaApp.instance.licenseManager.isPro()
        val types = ProviderType.values()
        val labels = types.map { t ->
            val lock = if (t.isPro && !isPro) "  · PRO" else ""
            val connected = if (!prefs.apiKeyFor(t).isNullOrBlank() || (t == ProviderType.CUSTOM && !prefs.customApiUrl.isNullOrBlank())) "  ✓" else ""
            t.displayName + connected + lock
        }.toTypedArray()

        AlertDialog.Builder(activity, R.style.Theme_FaizaAI_Dialog)
            .setTitle("🧠 Choose AI Provider")
            .setSingleChoiceItems(labels, types.indexOf(prefs.activeProvider)) { dialog, which ->
                dialog.dismiss()
                val type = types[which]
                if (type.isPro && !isPro) {
                    AlertDialog.Builder(activity, R.style.Theme_FaizaAI_Dialog)
                        .setTitle("Pro feature")
                        .setMessage("${type.displayName} is available in the Pro plan. Gemini is free for everyone.")
                        .setPositiveButton("View Plans") { _, _ ->
                            activity.startActivity(Intent(activity, LicenseActivity::class.java))
                        }
                        .setNegativeButton("Later", null)
                        .show()
                } else {
                    showProviderSetup(activity, type, onSaved)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showProviderSetup(activity: AppCompatActivity, type: ProviderType, onSaved: () -> Unit) {
        val prefs = FaizaApp.instance.prefs
        val dp = activity.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        fun field(hint: String, value: String?, secret: Boolean = false) = EditText(activity).apply {
            this.hint = hint
            setText(value.orEmpty())
            setTextColor(Color.parseColor("#E2E8F0"))
            setHintTextColor(Color.parseColor("#64748B"))
            setBackgroundResource(R.drawable.bg_input)
            setPadding(px(12), px(12), px(12), px(12))
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or
                    (if (secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = px(10) }
        }

        val urlField = field("Server URL, e.g. http://192.168.0.5:11434/v1", prefs.customApiUrl)
        val keyField = field(type.keyHint, prefs.apiKeyFor(type), secret = true)
        val modelField = field("Model (leave empty to choose from a list)", prefs.modelFor(type))
        val status = TextView(activity).apply {
            textSize = 13f
            visibility = View.GONE
            setPadding(0, px(10), 0, 0)
        }

        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(4), px(20), 0)
            if (type == ProviderType.CUSTOM) addView(urlField)
            addView(keyField)
            addView(modelField)
            addView(status)
        }

        val builder = AlertDialog.Builder(activity, R.style.Theme_FaizaAI_Dialog)
            .setTitle(type.displayName)
            .setMessage("Your key is stored only on this phone and is sent only to ${type.displayName}.")
            .setView(form)
            .setPositiveButton("Test & Save", null)
            .setNegativeButton("Cancel", null)
        if (type.keyUrl.isNotEmpty()) builder.setNeutralButton("Get Key", null)
        val dialog = builder.show()

        fun showStatus(text: String, color: String) {
            status.visibility = View.VISIBLE
            status.text = text
            status.setTextColor(Color.parseColor(color))
        }

        fun save(model: String) {
            prefs.setApiKey(type, keyField.text.toString().trim())
            prefs.setModel(type, model)
            if (type == ProviderType.CUSTOM) prefs.customApiUrl = urlField.text.toString().trim()
            prefs.aiProvider = type.id
            Toast.makeText(activity, "✅ ${type.displayName} connected ($model)", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            onSaved()
        }

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(type.keyUrl)))
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { button ->
            val key = keyField.text.toString().trim()
            val url = urlField.text.toString().trim()
            val model = modelField.text.toString().trim()
            when {
                type == ProviderType.CUSTOM && !url.startsWith("http") ->
                    return@setOnClickListener showStatus("Enter a server URL starting with http:// or https://", "#EF4444")
                type != ProviderType.CUSTOM && key.isEmpty() ->
                    return@setOnClickListener showStatus("Paste your API key first", "#EF4444")
            }

            button.isEnabled = false
            showStatus("Checking your key…", "#F59E0B")
            activity.lifecycleScope.launch {
                try {
                    val models = AIProviders.create(type, key, model, url).listModels()
                    when {
                        model.isNotEmpty() && (models.isEmpty() || model in models) -> save(model)
                        models.isEmpty() -> showStatus("Key works, but no model list was returned. Type a model name.", "#F59E0B")
                        else -> pickModel(activity, type, models) { save(it) }
                    }
                } catch (e: AIProviderException) {
                    showStatus(if (e.isAuthError) "❌ Invalid API key" else "❌ ${e.message}", "#EF4444")
                } catch (e: IOException) {
                    showStatus("❌ Can't reach the server. Check the internet / URL.", "#EF4444")
                } finally {
                    button.isEnabled = true
                }
            }
        }
    }

    private fun pickModel(
        activity: AppCompatActivity,
        type: ProviderType,
        models: List<String>,
        onPicked: (String) -> Unit
    ) {
        val ordered = models.sortedBy { if (it == type.defaultModel) 0 else 1 }
        val labels = ordered.map { if (it == type.defaultModel) "$it  ⭐ recommended" else it }.toTypedArray()
        AlertDialog.Builder(activity, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Choose a model")
            .setItems(labels) { _, which -> onPicked(ordered[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
