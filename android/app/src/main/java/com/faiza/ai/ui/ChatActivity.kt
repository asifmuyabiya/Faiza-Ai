package com.faiza.ai.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.FaizaAgent
import com.faiza.ai.ai.Persona
import com.faiza.ai.ai.provider.AIProviderException
import com.faiza.ai.ai.provider.AIProviders
import com.faiza.ai.ai.provider.Attachment
import com.faiza.ai.ui.orb.OrbDesign
import com.faiza.ai.ui.orb.OrbTheme
import com.faiza.ai.util.AttachmentReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The chat thread survives leaving the screen (until the app is closed or the user clears it). */
object ChatSession {
    data class Message(val fromUser: Boolean, val text: String, val files: List<Attachment> = emptyList(), val isError: Boolean = false)

    val messages = mutableListOf<Message>()
    var agent: FaizaAgent? = null
    /** Which provider/model the agent was built for, so a settings change gets a fresh one. */
    var agentKey: String? = null
    /** The work room this thread belongs to (null = normal chat). */
    var workspace: com.faiza.ai.ai.Workspace? = null

    fun clear() {
        messages.clear()
        agent?.clearHistory()
    }
}

/** Chat (video 30:51): text in, text out, with photos, PDFs and text files to analyse. */
class ChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WORKSPACE = "workspace"
    }

    private val prefs get() = FaizaApp.instance.prefs
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private val pending = mutableListOf<Attachment>()
    private var sending: Job? = null
    private var typingBubble: View? = null

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { addAttachment(it) }
    }
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { addAttachment(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        list = findViewById(R.id.messages)
        scroll = findViewById(R.id.scroll)
        input = findViewById(R.id.etMessage)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnClear).setOnClickListener {
            if (sending?.isActive == true) return@setOnClickListener
            ChatSession.clear()
            render()
        }
        findViewById<ImageButton>(R.id.btnSend).setOnClickListener { send() }
        findViewById<ImageButton>(R.id.btnAttach).setOnClickListener { showAttachMenu(it) }
        listOf(R.id.chip1, R.id.chip2, R.id.chip3).forEach { id ->
            findViewById<TextView>(id).setOnClickListener { v ->
                input.setText((v as TextView).text)
                input.setSelection(input.text.length)
                input.requestFocus()
            }
        }

        val persona = Persona.from(prefs.selectedPersona)
        findViewById<TextView>(R.id.tvChatSubtitle).text = "${prefs.assistantName(persona)} · replies in text"
        // A work room from the side menu: its own thread, title and starter ideas.
        val room = com.faiza.ai.ai.Workspace.from(intent.getStringExtra(EXTRA_WORKSPACE))
        if (room != ChatSession.workspace) {
            ChatSession.clear()
            ChatSession.workspace = room
        }
        if (room != null) {
            findViewById<TextView>(R.id.tvChatTitle).text = "${room.emoji} ${room.title}"
            findViewById<TextView>(R.id.tvChatSubtitle).text = room.subtitle
            listOf(R.id.chip1, R.id.chip2, R.id.chip3).forEachIndexed { i, id ->
                findViewById<TextView>(id).text = room.chips[i]
            }
        }
        findViewById<com.faiza.ai.ui.orb.OrbView>(R.id.orbEmpty).apply {
            val design = OrbDesign.from(prefs.floatingOrbDesign)
            this.design = if (design.isPro && !FaizaApp.instance.licenseManager.isPro()) OrbDesign.SPHERE else design
            theme = OrbTheme.from(prefs.orbTheme)
        }
        render()
    }

    // ===== Sending =====

    private fun agent(): FaizaAgent? {
        val key = "${prefs.aiProvider}|${prefs.modelFor(prefs.activeProvider)}|${prefs.apiKeyFor(prefs.activeProvider)?.hashCode()}"
        if (ChatSession.agent == null || ChatSession.agentKey != key) {
            ChatSession.agent = AIProviders.fromPrefs(prefs)?.let { FaizaAgent(applicationContext, it) }
            ChatSession.agentKey = key
        }
        return ChatSession.agent
    }

    /** The room's instructions ride along with every message (short, and survives history trimming). */
    private fun roomPrefix(): String =
        ChatSession.workspace?.let { "[${it.title.uppercase()} ROOM — ${it.instruction}]\n\n" } ?: ""

    private fun send() {
        if (sending?.isActive == true) return
        val typed = input.text.toString().trim()
        if (typed.isEmpty() && pending.isEmpty()) return
        val files = pending.toList()
        val text = typed.ifEmpty { if (files.size == 1) "What is this? Describe it." else "What are these? Describe them." }
        input.setText("")
        pending.clear()
        renderAttachments()
        hideKeyboard()

        ChatSession.messages += ChatSession.Message(true, text, files)
        render()

        val agent = agent()
        if (agent == null) {
            ChatSession.messages += ChatSession.Message(
                false, "I need an AI key first. Open Settings → AI engine → API key, then try again.", isError = true
            )
            render()
            return
        }
        showTyping(true)
        sending = lifecycleScope.launch {
            val reply = try {
                ChatSession.Message(false, agent.handle(roomPrefix() + text, files))
            } catch (e: AIProviderException) {
                val msg = when {
                    e.isAuthError -> "The API key was rejected. Check it in Settings → AI engine."
                    e.isRateLimit -> "Too many requests right now. Wait a minute and try again."
                    else -> "Something went wrong: ${e.message}"
                }
                ChatSession.Message(false, msg, isError = true)
            } catch (e: Exception) {
                ChatSession.Message(false, "Couldn't reach the AI. Check your internet and try again.", isError = true)
            }
            showTyping(false)
            ChatSession.messages += reply
            render()
        }
    }

    // ===== Attachments =====

    private fun showAttachMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "Photo")
            menu.add(0, 2, 1, "File (PDF or text)")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    else -> pickFile.launch(arrayOf("application/pdf", "text/*", "application/json", "image/*"))
                }
                true
            }
        }.show()
    }

    private fun addAttachment(uri: Uri) {
        if (pending.size >= 4) {
            Toast.makeText(this, "You can attach up to 4 files at a time", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { AttachmentReader.read(applicationContext, uri) } }
            result.onSuccess {
                pending += it
                renderAttachments()
            }.onFailure { e ->
                val msg = (e as? AttachmentReader.UnsupportedFileException)?.message ?: "Couldn't open that file."
                Toast.makeText(this@ChatActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderAttachments() {
        val bar = findViewById<View>(R.id.attachBar)
        val chips = findViewById<LinearLayout>(R.id.attachChips)
        chips.removeAllViews()
        bar.visibility = if (pending.isEmpty()) View.GONE else View.VISIBLE
        pending.forEach { a ->
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_pill)
                setPadding(dp(12), dp(6), dp(6), dp(6))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { marginEnd = dp(8) }
            }
            chip.addView(TextView(this).apply {
                text = (if (a.isImage) "Photo · " else if (a.isPdf) "PDF · " else "Text · ") + a.name.take(24)
                textSize = 12f
                setTextColor(getColor(R.color.text_primary))
            })
            chip.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_close)
                imageTintList = getColorStateList(R.color.text_secondary)
                setPadding(dp(4), dp(4), dp(4), dp(4))
                contentDescription = "Remove ${a.name}"
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginStart = dp(4) }
                setOnClickListener {
                    pending.remove(a)
                    renderAttachments()
                }
            })
            chips.addView(chip)
        }
    }

    // ===== Rendering =====

    private fun render() {
        list.removeAllViews()
        typingBubble = null
        findViewById<View>(R.id.emptyState).visibility = if (ChatSession.messages.isEmpty()) View.VISIBLE else View.GONE
        ChatSession.messages.forEach { list.addView(bubble(it)) }
        if (sending?.isActive == true) showTyping(true)
        scrollToEnd()
    }

    private fun bubble(m: ChatSession.Message): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (m.fromUser) Gravity.END else Gravity.START
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(10) }
        }
        val maxWidth = (resources.displayMetrics.widthPixels * 0.8).toInt()
        m.files.forEach { f ->
            val thumb = if (f.isImage) f.base64?.let { decodeThumb(it) } else null
            if (thumb != null) {
                wrap.addView(ImageView(this).apply {
                    setImageBitmap(thumb)
                    adjustViewBounds = true
                    setMaxWidth(dp(220))
                    setMaxHeight(dp(220))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    background = getDrawable(R.drawable.bg_bubble_ai)
                    clipToOutline = true
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        .apply { bottomMargin = dp(6) }
                })
            } else {
                wrap.addView(TextView(this).apply {
                    text = (if (f.isPdf) "PDF · " else "File · ") + f.name
                    textSize = 12f
                    setTextColor(getColor(R.color.text_secondary))
                    setBackgroundResource(R.drawable.bg_pill)
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        .apply { bottomMargin = dp(6) }
                })
            }
        }
        wrap.addView(TextView(this).apply {
            text = m.text
            textSize = 15f
            setLineSpacing(0f, 1.15f)
            setTextColor(getColor(if (m.isError) R.color.warning else R.color.text_primary))
            setBackgroundResource(if (m.fromUser) R.drawable.bg_bubble_user else R.drawable.bg_bubble_ai)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setMaxWidth(maxWidth)
            setTextIsSelectable(true)
        })
        return wrap
    }

    private fun showTyping(show: Boolean) {
        typingBubble?.let { list.removeView(it) }
        typingBubble = null
        if (!show) return
        val name = prefs.assistantName(Persona.from(prefs.selectedPersona))
        typingBubble = TextView(this).apply {
            text = "$name is thinking…"
            textSize = 13f
            setTypeface(typeface, Typeface.ITALIC)
            setTextColor(getColor(R.color.text_muted))
            setBackgroundResource(R.drawable.bg_bubble_ai)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(10) }
        }
        list.addView(typingBubble)
        findViewById<View>(R.id.emptyState).visibility = View.GONE
        scrollToEnd()
    }

    private fun decodeThumb(b64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }.getOrNull()

    private fun scrollToEnd() = scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
