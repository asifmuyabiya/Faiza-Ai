package com.faiza.ai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.code.CodeAgent
import com.faiza.ai.ai.code.CodeParsers
import com.faiza.ai.ai.code.CodeProject
import com.faiza.ai.ai.code.CodeProjects
import com.faiza.ai.ai.code.LocalServer
import com.faiza.ai.ai.code.TermuxBridge
import com.faiza.ai.ui.windows.BrowserWindow
import com.faiza.ai.ui.windows.WindowCenter
import com.faiza.ai.ui.windows.WindowKind
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Code Studio: ask for an app or website, watch the team (manager → coder → tester → runner → reviewer → fixer) work. */
class CodeStudioActivity : AppCompatActivity() {

    private val prefs get() = FaizaApp.instance.prefs
    private lateinit var input: EditText
    private lateinit var startBtn: MaterialButton
    private lateinit var targetLine: TextView
    private lateinit var brainBox: LinearLayout
    private lateinit var filesBox: LinearLayout
    /** Show the key box even though a key is saved ("change"). */
    private var editingKey = false
    private lateinit var stageRow: LinearLayout
    private lateinit var logView: TextView
    private lateinit var resultCard: TextView
    private lateinit var projectsBox: LinearLayout
    /** Project to change (null = build a new one). */
    private var target: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocalServer.ensure(CodeProjects.root(this))
        val scroll = ScrollView(this).apply { setBackgroundColor(getColor(R.color.bg)); isFillViewport = true }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(32)) }
        scroll.addView(col)
        setContentView(scroll)

        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            addView(ImageButton(context).apply {
                setImageResource(R.drawable.ic_arrow_back); background = null
                imageTintList = getColorStateList(R.color.text_primary)
                setOnClickListener { finish() }
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            })
            addView(TextView(context).apply {
                text = "💻 Code Studio"; textSize = 22f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColor(R.color.text_primary)); setPadding(dp(8), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = "🐧"; textSize = 20f; setPadding(dp(10), dp(6), dp(6), dp(6))
                contentDescription = "Termux setup"
                setOnClickListener { termuxDialog() }
            })
            addView(TextView(context).apply {
                text = "⚙️"; textSize = 20f; setPadding(dp(10), dp(6), dp(6), dp(6))
                contentDescription = "Settings"
                setOnClickListener { settingsDialog() }
            })
        })
        col.addView(caption("Say what you want — a website, a web app, a game, a Python or Node script. A team of AI agents plans it, writes it, tests it, runs it, looks at it, and fixes it again and again until it's right. Websites open right here on the phone; you can export a ZIP to upload anywhere."))

        targetLine = caption("")
        col.addView(targetLine)
        input = EditText(this).apply {
            hint = "e.g. A modern website for my clothing shop with a product gallery, prices in taka and a WhatsApp order button"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3; gravity = Gravity.TOP or Gravity.START; textSize = 15f
            setTextColor(getColor(R.color.text_primary)); setHintTextColor(getColor(R.color.text_muted))
            setBackgroundResource(R.drawable.bg_input); setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        col.addView(input, lp(top = 8))

        startBtn = MaterialButton(this).apply { setOnClickListener { startOrStop() } }
        col.addView(startBtn, lp(top = 10))
        brainBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(12)); setBackgroundResource(R.drawable.bg_input)
        }
        col.addView(brainBox, lp(top = 10))

        stageRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        col.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(stageRow) })

        logView = TextView(this).apply {
            textSize = 13f; setTextColor(getColor(R.color.text_secondary)); setLineSpacing(0f, 1.3f)
            setPadding(dp(14), dp(12), dp(14), dp(12)); setBackgroundResource(R.drawable.bg_empty_card)
            setTextIsSelectable(true); visibility = View.GONE
        }
        col.addView(logView, lp(top = 10))

        resultCard = TextView(this).apply {
            textSize = 15f; setTextColor(getColor(R.color.text_primary)); setLineSpacing(0f, 1.3f)
            setPadding(dp(16), dp(14), dp(16), dp(14)); setBackgroundResource(R.drawable.bg_input_focused)
            visibility = View.GONE
            setOnClickListener { CodeAgent.outcome.value?.let { o -> CodeProjects.find(this@CodeStudioActivity, o.slug)?.let { projectMenu(it) } } }
        }
        col.addView(resultCard, lp(top = 10))

        col.addView(TextView(this).apply {
            text = "Projects"; textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(getColor(R.color.text_primary)); setPadding(dp(4), dp(22), 0, dp(4))
        })
        projectsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(projectsBox)

        col.addView(TextView(this).apply {
            text = "📁 ফাইজার সেভ করা ফাইল (Download/Faiza AI)"; textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(getColor(R.color.text_primary)); setPadding(dp(4), dp(22), 0, dp(4))
        })
        col.addView(caption("Website / Coding রুম বা চ্যাট থেকে সেভ করা কোড, রিপোর্ট, টেবিল। চাপ দিলে খুলবে, 🗑️ দিলে মুছবে।"))
        filesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(filesBox)

        lifecycleScope.launch { CodeAgent.log.collect { renderLog(it) } }
        lifecycleScope.launch { CodeAgent.stage.collect { renderStages(it) } }
        lifecycleScope.launch { CodeAgent.outcome.collect { renderOutcome(it); renderProjects() } }
        renderTarget()
    }

    override fun onResume() {
        super.onResume()
        renderBrain()
        renderProjects()
        renderFiles()
    }

    private fun startOrStop() {
        if (CodeAgent.running) { CodeAgent.cancel(); return }
        val q = input.text.toString().trim()
        if (q.length < 5) { toast("Write what you want built"); return }
        if (!CodeAgent.start(this, q, target, tellWhenDone = true)) toast("Code Studio is already working")
        else { input.setText(""); renderLog(CodeAgent.log.value) }
    }

    private fun renderTarget() {
        val p = target?.let { CodeProjects.find(this, it) }
        targetLine.text = if (p == null) "✨ New project" else "✏️ Changing \"${p.meta.name}\" — tap here for a new project instead"
        targetLine.setOnClickListener { if (target != null) { target = null; renderTarget() } }
        startBtn.text = when {
            CodeAgent.running -> "⏹ Stop"
            p == null -> "Build it"
            else -> "Apply change"
        }
    }

    private fun renderStages(now: CodeAgent.Stage) {
        stageRow.removeAllViews()
        CodeAgent.Stage.values().filter { it != CodeAgent.Stage.IDLE }.forEach { s ->
            val on = s == now
            stageRow.addView(TextView(this).apply {
                text = "${s.emoji} ${s.label}"; textSize = 12f
                setTextColor(getColor(if (on) R.color.text_primary else R.color.text_muted))
                setBackgroundResource(if (on) R.drawable.bg_input_focused else R.drawable.bg_input)
                setPadding(dp(10), dp(6), dp(10), dp(6))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
        }
        renderTarget()
    }

    private fun renderLog(lines: List<String>) {
        logView.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
        logView.text = lines.takeLast(18).joinToString("\n")
        renderTarget()
    }

    private fun renderOutcome(o: CodeAgent.Outcome?) {
        if (o == null) { resultCard.visibility = View.GONE; return }
        resultCard.visibility = View.VISIBLE
        resultCard.text = (if (o.approved) "✅ " else "⚠️ ") + "${o.name}\n" + o.summary.lineSequence().first() +
                (o.previewUrl?.let { "\n🌐 $it" } ?: "") + "\n\nTap for preview, files, ZIP…"
    }

    private fun renderProjects() {
        projectsBox.removeAllViews()
        val all = CodeProjects.list(this)
        if (all.isEmpty()) { projectsBox.addView(caption("No projects yet.")); return }
        all.forEach { p ->
            projectsBox.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_input); layoutParams = lp(top = 8)
                addView(TextView(context).apply {
                    val icon = when (p.kind) { CodeParsers.Kind.WEB -> "🌐"; CodeParsers.Kind.PYTHON -> "🐍"; CodeParsers.Kind.NODE -> "🟩" }
                    val status = when (p.meta.status) { "approved" -> "✅ ready"; "working" -> "⏳ working"; "" -> ""; else -> "⚠️ ${p.meta.status}" }
                    text = "$icon  ${p.meta.name}\n${p.files().size} files · $status · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(p.meta.updatedAt))}"
                    textSize = 14f; setTextColor(getColor(R.color.text_primary)); setLineSpacing(0f, 1.25f)
                    setPadding(dp(14), dp(12), dp(8), dp(12))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { projectMenu(p) }
                })
                addView(trashButton { confirmDelete(p) })
            })
        }
        if (all.size >= 2) projectsBox.addView(TextView(this).apply {
            text = "🗑️ সব প্রজেক্ট মুছুন (${all.size})"; textSize = 14f; gravity = Gravity.CENTER
            setTextColor(getColor(R.color.warning)); setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener { confirmDeleteAll(all) }
        }, lp(top = 8))
    }

    private fun trashButton(onClick: () -> Unit) = TextView(this).apply {
        text = "🗑️"; textSize = 20f; gravity = Gravity.CENTER
        contentDescription = "Delete"
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setOnClickListener { onClick() }
    }

    /** Deletes the folder and checks it's really gone. */
    private fun deleteProject(p: CodeProject): Boolean {
        if (CodeAgent.running && CodeAgent.currentSlug.value == p.slug) {
            toast("এই প্রজেক্টে এখন কাজ চলছে — আগে ⏹ Stop চাপুন"); return false
        }
        if (target == p.slug) { target = null; renderTarget() }
        p.dir.deleteRecursively()
        if (p.dir.exists()) p.dir.walkBottomUp().forEach { it.delete() }
        if (CodeAgent.outcome.value?.slug == p.slug) CodeAgent.outcome.value = null
        return !p.dir.exists()
    }

    private fun confirmDeleteAll(all: List<CodeProject>) {
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("সব ${all.size}টা প্রজেক্ট মুছবেন?")
            .setMessage("সব ফাইল ফোন থেকে মুছে যাবে। রাখতে চাইলে আগে ZIP করে নিন।")
            .setPositiveButton("সব মুছুন") { _, _ ->
                val failed = all.count { !deleteProject(it) }
                toast(if (failed == 0) "সব মুছে গেছে" else "$failed টা মোছা যায়নি")
                renderProjects()
            }
            .setNegativeButton("না", null).show()
    }

    // ---------------- Saved files (Download/Faiza AI) ----------------

    private fun renderFiles() {
        filesBox.removeAllViews()
        val items = com.faiza.ai.ai.files.SavedFiles.list(this)
        if (items.isEmpty()) { filesBox.addView(caption("কোনো ফাইল নেই।")); return }
        items.take(60).forEach { f ->
            filesBox.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_input); layoutParams = lp(top = 6)
                addView(TextView(context).apply {
                    text = "${fileIcon(f.name)}  ${f.name}\n${com.faiza.ai.ai.files.SavedFiles.sizeText(f.size)} · " +
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(f.modified))
                    textSize = 14f; setTextColor(getColor(R.color.text_primary)); setLineSpacing(0f, 1.25f)
                    setPadding(dp(14), dp(10), dp(8), dp(10))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { openSaved(f) }
                })
                addView(trashButton {
                    AlertDialog.Builder(this@CodeStudioActivity, R.style.Theme_FaizaAI_Dialog)
                        .setTitle("\"${f.name}\" মুছবেন?")
                        .setPositiveButton("মুছুন") { _, _ ->
                            toast(if (com.faiza.ai.ai.files.SavedFiles.delete(this@CodeStudioActivity, f)) "মুছে গেছে" else "মোছা যায়নি")
                            renderFiles()
                        }
                        .setNegativeButton("না", null).show()
                })
            })
        }
        if (items.size >= 2) filesBox.addView(TextView(this).apply {
            text = "🗑️ সব ফাইল মুছুন (${items.size})"; textSize = 14f; gravity = Gravity.CENTER
            setTextColor(getColor(R.color.warning)); setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener {
                AlertDialog.Builder(this@CodeStudioActivity, R.style.Theme_FaizaAI_Dialog)
                    .setTitle("সব ${items.size}টা ফাইল মুছবেন?")
                    .setPositiveButton("সব মুছুন") { _, _ ->
                        val failed = items.count { !com.faiza.ai.ai.files.SavedFiles.delete(this@CodeStudioActivity, it) }
                        toast(if (failed == 0) "সব মুছে গেছে" else "$failed টা মোছা যায়নি")
                        renderFiles()
                    }
                    .setNegativeButton("না", null).show()
            }
        }, lp(top = 8))
    }

    private fun fileIcon(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "🌐"; "css" -> "🎨"; "js" -> "🟨"; "py" -> "🐍"; "csv" -> "📊"; "md", "txt" -> "📄"; else -> "📁"
    }

    private fun openSaved(f: com.faiza.ai.ai.files.SavedFiles.Item) {
        val uri = if (f.file != null) androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", f.file) else f.uri
        val mime = com.faiza.ai.ai.files.FilePlanner.mimeType(f.name)
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }.onFailure { toast("এই ফাইল খোলার অ্যাপ নেই") }
    }

    // ---------------- Brain: Gemini key & model for Code Studio ----------------

    private fun renderBrain() {
        brainBox.removeAllViews()
        val claude = CodeAgent.claudeKey().isNotEmpty()
        brainBox.addView(TextView(this).apply {
            text = "🧠 ${CodeAgent.brainLabel()}"; textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setTextColor(getColor(R.color.text_primary))
        })
        brainBox.addView(caption(
            (if (claude) "Claude key দেওয়া আছে, তাই Claude কাজ করছে (⚙️ থেকে বদলান)।\n" else "") +
                    (if (CodeAgent.hasOwnGeminiKey()) "🔑 Code Studio-র নিজস্ব Gemini key সেট করা আছে ✓"
                    else "🔑 এখন ফাইজার মূল Gemini key দিয়ে চলছে।") + " · সর্বোচ্চ ${prefs.codeMaxRounds} রাউন্ড রিভিউ"
        ).apply { setPadding(0, dp(4), 0, 0) })

        val showKeyBox = !CodeAgent.hasOwnGeminiKey() || editingKey
        if (showKeyBox) {
            val box = EditText(this).apply {
                hint = "Code Studio-র জন্য আলাদা Gemini API key (AIza…)"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                textSize = 14f; setTextColor(getColor(R.color.text_primary)); setHintTextColor(getColor(R.color.text_muted))
                setBackgroundResource(R.drawable.bg_input_focused); setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            brainBox.addView(box, lp(top = 8))
            brainBox.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(chip("💾 সেভ") { saveKey(box.text.toString().trim()) })
                addView(chip("🔗 Key নিন") { openInBrowser("https://aistudio.google.com/app/apikey") })
                if (editingKey) addView(chip("বাতিল") { editingKey = false; renderBrain() })
            }, lp(top = 6))
            brainBox.addView(caption("না দিলে ফাইজার মূল key দিয়েই চলবে। একবার দিলে এই ঘরটা লুকিয়ে যাবে।"))
        } else {
            brainBox.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(chip("🔑 Key বদলান") { editingKey = true; renderBrain() })
                addView(chip("✖ Key মুছুন") {
                    prefs.codeGeminiKey = ""; prefs.codeGeminiModelAuto = ""; toast("এখন থেকে ফাইজার মূল key দিয়ে চলবে"); renderBrain()
                })
            }, lp(top = 8))
        }
        brainBox.addView(chip("🧩 মডেল: " + prefs.codeGeminiModel.ifBlank { "সর্বশেষ Gemini (অটো)" }) { chooseModel() }, lp(top = 6))
    }

    private fun chip(label: String, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 13f; setTextColor(getColor(R.color.text_primary))
        setBackgroundResource(R.drawable.bg_input_focused); setPadding(dp(12), dp(8), dp(12), dp(8))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) }
        setOnClickListener { action() }
    }

    /** Checks the key with Google before saving it. */
    private fun saveKey(key: String) {
        if (key.length < 20) { toast("পুরো key টা পেস্ট করুন"); return }
        toast("Key যাচাই করছি…")
        lifecycleScope.launch {
            val models = withContext(Dispatchers.IO) { runCatching { com.faiza.ai.ai.provider.GeminiProvider(key, "").listModels() } }
            models.onSuccess { list ->
                prefs.codeGeminiKey = key
                prefs.codeGeminiModelAuto = ""
                editingKey = false
                val best = com.faiza.ai.ai.web.ResearchParsers.bestGeminiModel(list)
                toast("✅ Key সেভ হয়েছে" + (best?.let { " · সর্বশেষ মডেল: $it" } ?: ""))
                renderBrain()
            }.onFailure { toast("❌ Key কাজ করছে না: ${it.message?.take(80)}") }
        }
    }

    /** Models this key can use, newest first; "automatic" = always the newest. */
    private fun chooseModel() {
        val key = CodeAgent.geminiKey()
        if (key.isBlank()) { toast("আগে একটা Gemini key দিন"); return }
        toast("মডেলের লিস্ট আনছি…")
        lifecycleScope.launch {
            val models = withContext(Dispatchers.IO) {
                runCatching { com.faiza.ai.ai.provider.GeminiProvider(key, "").listModels() }.getOrNull()
            }?.filter { it.startsWith("gemini") }?.sortedDescending()
            if (models.isNullOrEmpty()) { toast("লিস্ট আনা গেল না (ইন্টারনেট/key দেখুন)"); return@launch }
            val best = com.faiza.ai.ai.web.ResearchParsers.bestGeminiModel(models)
            val labels = (listOf("⭐ অটো — সবসময় সর্বশেষ (${best ?: "?"})") + models).toTypedArray()
            AlertDialog.Builder(this@CodeStudioActivity, R.style.Theme_FaizaAI_Dialog)
                .setTitle("Code Studio মডেল")
                .setItems(labels) { _, i ->
                    prefs.codeGeminiModel = if (i == 0) "" else models[i - 1]
                    prefs.codeGeminiModelAuto = ""
                    renderBrain()
                }
                .show()
        }
    }

    private fun projectMenu(p: CodeProject) {
        val web = p.kind == CodeParsers.Kind.WEB
        val items = mutableListOf<Pair<String, () -> Unit>>()
        if (web) {
            items += "🌐 Preview (floating window)" to { preview(p) }
            items += "🔗 Open in phone browser" to { openInBrowser(LocalServer.url(p.slug)) }
        } else {
            items += "▶️ Run in Termux" to { runInTermux(p) }
        }
        items += "✏️ Change this project" to { target = p.slug; renderTarget(); input.requestFocus() }
        items += "📄 Files" to { filesDialog(p) }
        items += "📦 Export ZIP (to upload)" to { exportZip(p) }
        items += "🚀 How to put it online" to { publishHelp(p) }
        items += "🗑️ Delete project" to { confirmDelete(p) }
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle(p.meta.name)
            .setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }
            .show()
    }

    private fun preview(p: CodeProject) {
        if (!Settings.canDrawOverlays(this)) { openInBrowser(LocalServer.url(p.slug)); return }
        (WindowCenter.show(this, WindowKind.BROWSER) as? BrowserWindow)?.go(LocalServer.url(p.slug))
    }

    private fun openInBrowser(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { toast("No browser found") }
    }

    private fun runInTermux(p: CodeProject) {
        TermuxBridge.problem(this)?.let { toast(it); termuxDialog(); return }
        val plan = p.meta.plan
        fun line(k: String) = Regex("""(?im)^\s*$k:\s*`?(.+?)`?\s*$""").find(plan)?.groupValues?.get(1)?.takeIf { !it.equals("none", true) }
        val serve = line("SERVE_CMD")
        val cmd = serve ?: line("RUN_CMD") ?: if (p.kind == CodeParsers.Kind.PYTHON) "python main.py" else "node index.js"
        toast("Running: $cmd")
        lifecycleScope.launch {
            val r = if (serve != null) TermuxBridge.startBackground(this@CodeStudioActivity, serve, p.dir.absolutePath)
            else TermuxBridge.run(this@CodeStudioActivity, cmd, p.dir.absolutePath, 120)
            val port = line("PORT")?.filter { it.isDigit() }
            AlertDialog.Builder(this@CodeStudioActivity, R.style.Theme_FaizaAI_Dialog)
                .setTitle("▶️ $cmd")
                .setMessage(r.report(4000))
                .apply { if (serve != null && !port.isNullOrEmpty()) setNeutralButton("Open :$port") { _, _ -> openInBrowser("http://127.0.0.1:$port/") } }
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun filesDialog(p: CodeProject) {
        val files = p.files()
        if (files.isEmpty()) { toast("No files"); return }
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("${p.meta.name} · ${files.size} files")
            .setItems(files.toTypedArray()) { _, i ->
                val text = if (CodeParsers.isText(files[i])) p.read(files[i]).orEmpty() else "(binary file)"
                val tv = TextView(this).apply {
                    this.text = text.take(60_000); textSize = 12f; typeface = Typeface.MONOSPACE
                    setTextColor(getColor(R.color.text_primary)); setTextIsSelectable(true)
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                }
                AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
                    .setTitle(files[i])
                    .setView(ScrollView(this).apply { addView(HorizontalScrollView(this@CodeStudioActivity).apply { addView(tv) }) })
                    .setPositiveButton("Copy") { _, _ -> copy(text); toast("Copied") }
                    .setNegativeButton("Close", null)
                    .show()
            }
            .show()
    }

    private fun exportZip(p: CodeProject) {
        lifecycleScope.launch {
            val where = withContext(Dispatchers.IO) { runCatching { p.exportZip(this@CodeStudioActivity) } }
            where.onSuccess { toast("Saved: $it") }.onFailure { toast("Couldn't export: ${it.message}") }
        }
    }

    private fun publishHelp(p: CodeProject) {
        val web = p.kind == CodeParsers.Kind.WEB
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("🚀 Put it online")
            .setMessage(
                if (web) "1. Tap \"Export ZIP\" — it goes to Downloads/Faiza Code/${p.slug}.zip\n" +
                        "2. Open Netlify Drop (free) and choose that ZIP file\n" +
                        "3. You get a live link in a few seconds. Sign in to keep it and to use your own domain.\n\n" +
                        "Other free hosts: GitHub Pages, Cloudflare Pages, Vercel."
                else "Python/Node apps need a server host (e.g. Render, Railway, PythonAnywhere). Export the ZIP and " +
                        "upload it there, then set the start command:\n${Regex("""(?im)^\s*(SERVE_CMD|RUN_CMD):\s*(.+)$""").find(p.meta.plan)?.groupValues?.get(2) ?: "see the project's README"}"
            )
            .apply { if (web) setNeutralButton("Open Netlify Drop") { _, _ -> openInBrowser("https://app.netlify.com/drop") } }
            .setPositiveButton("Export ZIP") { _, _ -> exportZip(p) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun confirmDelete(p: CodeProject) {
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Delete \"${p.meta.name}\"?")
            .setMessage("All its files will be removed from the phone. Export a ZIP first if you want to keep a copy.")
            .setPositiveButton("Delete") { _, _ ->
                toast(if (deleteProject(p)) "মুছে গেছে" else "মোছা যায়নি")
                renderProjects()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Termux: install, settings line, permission, all-files access. */
    private fun termuxDialog() {
        val installed = TermuxBridge.installed(this)
        val perm = TermuxBridge.hasPermission(this)
        val files = CodeProjects.sharedStorage()
        val msg = "Websites need nothing extra. For Python / Node projects Faiza uses Termux:\n\n" +
                "${if (installed) "✅" else "❌"} 1. Termux installed (from F-Droid or GitHub — not Play Store)\n" +
                "${if (files) "✅" else "❌"} 2. Faiza has All files access (so Termux sees the project folder)\n" +
                "${if (perm) "✅" else "❌"} 3. Faiza may run commands in Termux\n" +
                "4. Once, in Termux, paste this command (tap \"Copy command\") and press Enter:\n\n${TermuxBridge.SETUP_COMMAND}"
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("🐧 Termux setup")
            .setMessage(msg)
            .setPositiveButton("Copy command") { _, _ ->
                copy(TermuxBridge.SETUP_COMMAND); toast("Copied — paste it in Termux")
                packageManager.getLaunchIntentForPackage(TermuxBridge.PKG)?.let { startActivity(it) }
            }
            .setNeutralButton(
                when { !installed -> "Get Termux"; !files -> "All files access"; !perm -> "Allow"; else -> "Close" }
            ) { _, _ ->
                when {
                    !installed -> openInBrowser("https://f-droid.org/packages/com.termux/")
                    !files && Build.VERSION.SDK_INT >= 30 -> runCatching {
                        startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                    }
                    !perm -> ActivityCompat.requestPermissions(this, arrayOf(TermuxBridge.PERMISSION), 41)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun settingsDialog() {
        fun box(value: String, hint: String, secret: Boolean = false) = EditText(this).apply {
            setText(value); this.hint = hint; textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or (if (secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else 0)
            setTextColor(getColor(R.color.text_primary)); setHintTextColor(getColor(R.color.text_muted))
            setBackgroundResource(R.drawable.bg_input); setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val claude = box(prefs.codeClaudeKey, "Claude API key (sk-ant-…) — best quality", secret = true)
        val claudeModel = box(prefs.codeClaudeModel, "Claude model (empty = claude-opus-5-5)")
        val rounds = box(prefs.codeMaxRounds.toString(), "Max review rounds (1–30)").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0)
            listOf(claude, claudeModel, rounds).forEach { addView(it, lp(top = 8)) }
            addView(caption("Gemini key আর মডেল Code Studio পেজের উপরের ঘরে। No Claude key? Code Studio uses Gemini. Each round calls the AI 2–3 times — more rounds = better result but more cost and time."))
        }
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Code Studio brain")
            .setView(ScrollView(this).apply { addView(col) })
            .setPositiveButton("Save") { _, _ ->
                prefs.codeClaudeKey = claude.text.toString()
                prefs.codeClaudeModel = claudeModel.text.toString()
                prefs.codeMaxRounds = rounds.text.toString().toIntOrNull()?.coerceIn(1, 30) ?: 6
                onResume()
            }
            .setNeutralButton("Get Claude key") { _, _ -> openInBrowser("https://console.anthropic.com/settings/keys") }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun copy(t: String) =
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Faiza", t))

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    private fun caption(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(getColor(R.color.text_secondary))
        setLineSpacing(0f, 1.25f); setPadding(dp(4), dp(10), dp(4), 0)
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = dp(top) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
