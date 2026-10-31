import os
p = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai\ui\CodeStudioActivity.kt'
s = open(p, encoding='utf-8').read()


def rep(a, b):
    global s
    assert s.count(a) == 1, (a[:70], s.count(a))
    s = s.replace(a, b)


rep('''    private lateinit var brainLine: TextView''', '''    private lateinit var brainBox: LinearLayout
    private lateinit var filesBox: LinearLayout
    /** Show the key box even though a key is saved ("change"). */
    private var editingKey = false''')
rep('''        brainLine = caption("")
        col.addView(brainLine)''', '''        brainBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(12)); setBackgroundResource(R.drawable.bg_input)
        }
        col.addView(brainBox, lp(top = 10))''')
rep('''        projectsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(projectsBox)''', '''        projectsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(projectsBox)

        col.addView(TextView(this).apply {
            text = "📁 ফাইজার সেভ করা ফাইল (Download/Faiza AI)"; textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(getColor(R.color.text_primary)); setPadding(dp(4), dp(22), 0, dp(4))
        })
        col.addView(caption("Website / Coding রুম বা চ্যাট থেকে সেভ করা কোড, রিপোর্ট, টেবিল। চাপ দিলে খুলবে, 🗑️ দিলে মুছবে।"))
        filesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(filesBox)''')
rep('''        brainLine.text = "🧠 Brain: ${CodeAgent.brainLabel()} · up to ${prefs.codeMaxRounds} review rounds"
        renderProjects()''', '''        renderBrain()
        renderProjects()
        renderFiles()''')

# Projects with a visible delete button + delete all.
a = s.index('    private fun renderProjects() {')
b = s.index('    private fun projectMenu(p: CodeProject) {')
s = s[:a] + '''    private fun renderProjects() {
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
                    text = "$icon  ${p.meta.name}\\n${p.files().size} files · $status · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(p.meta.updatedAt))}"
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
                    text = "${fileIcon(f.name)}  ${f.name}\\n${com.faiza.ai.ai.files.SavedFiles.sizeText(f.size)} · " +
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(f.modified))
                    textSize = 14f; setTextColor(getColor(R.color.text_primary)); setLineSpacing(0f, 1.25f)
                    setPadding(dp(14), dp(10), dp(8), dp(10))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { openSaved(f) }
                })
                addView(trashButton {
                    AlertDialog.Builder(this@CodeStudioActivity, R.style.Theme_FaizaAI_Dialog)
                        .setTitle("\\"${f.name}\\" মুছবেন?")
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
            (if (claude) "Claude key দেওয়া আছে, তাই Claude কাজ করছে (⚙️ থেকে বদলান)।\\n" else "") +
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

''' + s[b:]

# Settings dialog: Gemini model moved to the brain card.
rep('''        val geminiModel = box(prefs.codeGeminiModel, "Gemini model (empty = best available)")
''', '')
rep('''            listOf(claude, claudeModel, geminiModel, rounds).forEach { addView(it, lp(top = 8)) }''',
    '''            listOf(claude, claudeModel, rounds).forEach { addView(it, lp(top = 8)) }''')
rep('''                prefs.codeGeminiModel = geminiModel.text.toString()
''', '')
rep('''            addView(caption("No Claude key? Code Studio uses your Gemini key.''', '''            addView(caption("Gemini key আর মডেল Code Studio পেজের উপরের ঘরে। No Claude key? Code Studio uses Gemini.''')

# The old confirmDelete: use the checked delete.
rep('''            .setPositiveButton("Delete") { _, _ ->
                if (target == p.slug) { target = null; renderTarget() }
                p.dir.deleteRecursively(); renderProjects()
            }''', '''            .setPositiveButton("Delete") { _, _ ->
                toast(if (deleteProject(p)) "মুছে গেছে" else "মোছা যায়নি")
                renderProjects()
            }''')
open(p, 'w', encoding='utf-8').write(s)
print('ok')
