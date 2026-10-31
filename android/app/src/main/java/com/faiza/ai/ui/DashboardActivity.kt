package com.faiza.ai.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.Persona
import com.faiza.ai.ai.memory.MemoryStore
import com.faiza.ai.ai.macros.MacroCenter
import com.faiza.ai.util.CrashReporter
import com.faiza.ai.ui.orb.OrbDesign
import com.faiza.ai.ui.orb.OrbMode
import com.faiza.ai.ui.orb.OrbTheme
import com.faiza.ai.ai.provider.ProviderType
import com.faiza.ai.databinding.ActivityDashboardBinding
import com.faiza.ai.service.FloatingOrbService

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val prefs by lazy { FaizaApp.instance.prefs }
    private var isAiRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        updateStatus()
        showLastCrashIfAny()
        // First start: walk the new owner through the permissions, one screen each.
        if (!FaizaApp.instance.prefs.setupWizardDone) startActivity(Intent(this, SetupWizardActivity::class.java))
    }

    /** After a crash, show what happened so the user can screenshot it for the developer. */
    private fun showLastCrashIfAny() {
        val report = CrashReporter.takeLastCrash(this) ?: return
        val preview = report.lines().take(18).joinToString("\n")
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Faiza stopped unexpectedly")
            .setMessage("Please take a screenshot of this and send it to the developer:\n\n$preview")
            .setPositiveButton("Copy details") { _, _ ->
                val cm = getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("Faiza crash", report))
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun setupUI() {
        // Plan status
        updatePlanStatus()

        // Settings button
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Toggle AI button
        binding.btnToggleAI.setOnClickListener {
            if (isAiRunning) {
                stopFaizaAI()
            } else {
                startFaizaAI()
            }
        }

        binding.cardModes.setOnClickListener { startActivity(Intent(this, ModesActivity::class.java)) }
        binding.cardRoutines.setOnClickListener { startActivity(Intent(this, RoutinesActivity::class.java)) }

        setupHomeChrome()

        // Type to Faiza: answered out loud.
        binding.btnAsk.setOnClickListener { sendTyped() }
        binding.etAsk.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) { sendTyped(); true } else false
        }

        // Personas card
        binding.cardPersonas.setOnClickListener {
            showPersonaSelector()
        }

        // Macros card
        binding.cardMacros.setOnClickListener {
            if (!FaizaApp.instance.licenseManager.isPro()) {
                showProRequired("Macros")
            } else {
                startActivity(Intent(this, MacrosActivity::class.java))
            }
        }

        // SOS card
        // Safety feature: SOS works for everyone, licensed or not.
        binding.cardSOS.setOnClickListener {
            startActivity(Intent(this, SosActivity::class.java))
        }

        // Skills card
        binding.cardMemory.setOnClickListener {
            startActivity(Intent(this, MemoryActivity::class.java))
        }
        binding.cardProfile.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsHub.EXTRA_SECTION, "About you"))
        }

        binding.cardSkills.setOnClickListener {
            startActivity(Intent(this, SkillsActivity::class.java))
        }

        // Floating Orb card
        binding.cardOrb.setOnClickListener {
            startActivity(Intent(this, OrbSettingsActivity::class.java))
        }

        // API Setup card
        binding.cardApiSetup.setOnClickListener {
            if (!prefs.isAiConfigured() && prefs.activeProvider == ProviderType.GEMINI) {
                startActivity(Intent(this, ApiSetupActivity::class.java))
            } else {
                ProviderDialogs.showProviderPicker(this) { updateApiStatus() }
            }
        }

        updateApiStatus()
    }

    private fun openRoom(id: String) =
        startActivity(Intent(this, ChatActivity::class.java).putExtra(ChatActivity.EXTRA_WORKSPACE, id))

    private fun renderModeCard() {
        val m = runCatching { com.faiza.ai.ai.modes.ModeCenter.current(this) }.getOrNull()
        binding.tvModeEmoji.text = m?.emoji ?: "🚗"
        binding.tvModeTitle.text = if (m != null) "${m.name} mode is on" else "Life modes"
        binding.tvModeSub.text = if (m != null) "Calls get a polite text · tap to turn off"
        else "Driving · Sleeping · Prayer · Meeting…"
    }

    override fun onResume() {
        super.onResume()
        isAiRunning = FloatingOrbService.isRunning
        if (isAiRunning) {
            // We're on screen, so Android allows the service to claim the microphone type now.
            runCatching { startForegroundService(Intent(this, FloatingOrbService::class.java)) }
        }
        val active = com.faiza.ai.ai.routines.RoutineCenter.tasks().count { it.enabled } +
                com.faiza.ai.ai.routines.RoutineCenter.watches().count { it.enabled }
        binding.tvRoutineCount.text = when (active) {
            0 -> "Reminders and alerts"
            1 -> "1 active"
            else -> "$active active"
        }
        renderModeCard()
        val macroCount = MacroCenter.all().size
        binding.tvMacroCount.text = when (macroCount) {
            0 -> "Record and replay tasks"
            1 -> "1 macro saved"
            else -> "$macroCount macros saved"
        }
        val count = MemoryStore.facts().size
        binding.tvMemoryCount.text = when {
            !prefs.isMemoryEnabled -> "Memory is off"
            count == 0 -> "Nothing yet"
            count == 1 -> "1 thing remembered"
            else -> "$count things remembered"
        }
        updatePlanStatus()
        updateApiStatus()
        updateStatus()
        updateGreeting()
        updateHomeCards()
        showUpdateBanner(com.faiza.ai.util.UpdateChecker.savedUpdate(this))
        // At most once a day, quietly ask the server for a newer version.
        lifecycleScope.launch {
            com.faiza.ai.util.UpdateChecker.check(this@DashboardActivity)
            showUpdateBanner(com.faiza.ai.util.UpdateChecker.savedUpdate(this@DashboardActivity))
        }
    }

    private fun showUpdateBanner(info: com.faiza.ai.util.UpdateChecker.UpdateInfo?) {
        binding.cardUpdate.visibility = if (info == null) android.view.View.GONE else android.view.View.VISIBLE
        if (info != null) {
            binding.tvUpdateTitle.text = "Update available · ${info.versionName}"
            binding.cardUpdate.setOnClickListener { UpdateDialog.show(this, info) }
        }
    }

    private fun sendTyped() {
        val text = binding.etAsk.text.toString().trim()
        if (text.isEmpty()) return
        val service = FloatingOrbService.instance
        if (service != null) {
            service.handleTyped(text)
        } else {
            // Not running yet: start her and answer this as her first words.
            FloatingOrbService.pendingTypedText = text
            startFaizaAI()
            if (!isAiRunning) {
                FloatingOrbService.pendingTypedText = null
                return
            }
        }
        binding.etAsk.setText("")
        getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.etAsk.windowToken, 0)
        Toast.makeText(this, "${prefs.assistantName(Persona.from(prefs.selectedPersona))} will answer out loud", Toast.LENGTH_SHORT).show()
    }

    private fun updateGreeting() {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        binding.tvGreeting.text = when (hour) {
            in 5..11 -> "Good morning,"
            in 12..16 -> "Good afternoon,"
            in 17..21 -> "Good evening,"
            else -> "Good night,"
        }
        val full = prefs.userName.orEmpty().trim()
        val name = full.substringBefore(' ')
        binding.tvHello.text = name.ifEmpty { "Welcome" }
        val initial = full.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        binding.btnSettings.text = initial
        binding.drawerAvatar.text = initial
        binding.drawerName.text = full.ifEmpty { "Add your name in Settings" }
        binding.drawerPlan.text = binding.tvPlanStatus.text
        binding.tvAppTitle.text = prefs.assistantName(Persona.from(prefs.selectedPersona))
    }

    // ===== MAYA-style home: drawer, bottom bar, chips, cards =====

    private fun setupHomeChrome() {
        binding.btnMenu.setOnClickListener { binding.drawer.openDrawer(androidx.core.view.GravityCompat.START) }
        binding.btnBell.setOnClickListener { startActivity(Intent(this, ActivityLogActivity::class.java)) }
        binding.btnQuiet.setOnClickListener {
            val nowQuiet = !com.faiza.ai.util.QuietMode.manualActive()
            com.faiza.ai.util.QuietMode.set(nowQuiet)
            Toast.makeText(
                this,
                if (nowQuiet) "Quiet mode on — she won't speak or listen on her own" else "Everything is back on",
                Toast.LENGTH_SHORT
            ).show()
            updateStatus()
        }

        binding.navHome.setOnClickListener { binding.dashScroll.smoothScrollTo(0, 0) }
        binding.navScan.setOnClickListener { startActivity(Intent(this, ScanActivity::class.java)) }
        binding.navMemories.setOnClickListener { startActivity(Intent(this, MemoryActivity::class.java)) }
        binding.navChat.setOnClickListener { startActivity(Intent(this, ChatActivity::class.java)) }
        binding.btnMic.setOnClickListener {
            val service = FloatingOrbService.instance
            if (service == null) startFaizaAI() else service.activateFromAssist()
        }

        binding.chipMusic.setOnClickListener { askFaiza("Play my favorite music.") }
        binding.chipStudy.setOnClickListener {
            askFaiza("[STUDY MODE] The user wants to study. Ask in one short sentence what they're studying today, then tutor them.")
        }
        binding.chipJournal.setOnClickListener {
            askFaiza("[JOURNAL] The user wants to write in their journal. Gently ask how their day was, listen, then save_journal.")
        }
        binding.cardWeather.setOnClickListener { askFaiza("How's the weather today and tomorrow?") }
        binding.cardToday.setOnClickListener { startActivity(Intent(this, RoutinesActivity::class.java)) }
        binding.cardMood.setOnClickListener { askFaiza("Ask me how I'm feeling, kindly, in one short sentence.") }

        // Grouped, with Bangla words too, so the search finds "নামাজ" as well as "prayer".
        data class Item(val icon: Int, val label: String, val words: String, val action: () -> Unit)
        val groups = listOf(
            "⭐ প্রতিদিন · Everyday" to listOf(
                Item(R.drawable.ic_moon, "Prayer times", "নামাজ আজান কিবলা সেহরি ইফতার namaz") { startActivity(Intent(this, PrayerActivity::class.java)) },
                Item(R.drawable.ic_timer, "Routines", "রিমাইন্ডার রুটিন ওষুধ alarm") { startActivity(Intent(this, RoutinesActivity::class.java)) },
                Item(R.drawable.ic_moon, "Life modes", "ঘুম মিটিং ড্রাইভিং মোড") { startActivity(Intent(this, ModesActivity::class.java)) },
                Item(R.drawable.ic_place, "Driving", "গাড়ি ড্রাইভ car") { startActivity(Intent(this, DrivingActivity::class.java)) },
                Item(R.drawable.ic_bell, "Notifications", "নোটিফিকেশন ইনবক্স") { startActivity(Intent(this, ActivityLogActivity::class.java)) }
            ),
            "💰 টাকা-পয়সা · Money" to listOf(
                Item(R.drawable.ic_book, "Money book", "খাতা হিসাব বাকি খরচ bkash nagad") { startActivity(Intent(this, KhataActivity::class.java)) },
                Item(R.drawable.ic_today, "Markets", "বাজার শেয়ার দাম") { openRoom("markets") }
            ),
            "🎮 মজা · Fun" to listOf(
                Item(R.drawable.ic_bolt, "Gaming mode", "গেম ফ্রি ফায়ার free fire pubg") { startActivity(Intent(this, GamingActivity::class.java)) },
                Item(R.drawable.ic_palette, "Floating orb", "বাবল অর্ব ডিজাইন") { startActivity(Intent(this, OrbSettingsActivity::class.java)) }
            ),
            "💼 কাজ · Work" to listOf(
                Item(R.drawable.ic_globe, "Deep research", "রিসার্চ খবর গবেষণা") { startActivity(Intent(this, ResearchActivity::class.java)) },
                Item(R.drawable.ic_chip, "Code Studio", "কোড ওয়েবসাইট অ্যাপ বানাও") { startActivity(Intent(this, CodeStudioActivity::class.java)) },
                Item(R.drawable.ic_chip, "Website / Coding", "কোডিং রুম") { openRoom("coding") },
                Item(R.drawable.ic_campaign, "Marketing", "মার্কেটিং পোস্ট বিজ্ঞাপন") { openRoom("marketing") },
                Item(R.drawable.ic_book, "Documents", "ডকুমেন্ট ফাইল চিঠি") { openRoom("documents") },
                Item(R.drawable.ic_edit, "Study / Whiteboard", "পড়া পড়াশোনা হোমওয়ার্ক") { openRoom("study") }
            ),
            "🧠 ফাইজাকে শেখান · Teach Faiza" to listOf(
                Item(R.drawable.ic_heart, "My people", "মানুষ পরিবার কন্টাক্ট ডাকনাম") { startActivity(Intent(this, ContactsActivity::class.java)) },
                Item(R.drawable.ic_memory, "Memories", "মনে রাখা স্মৃতি") { startActivity(Intent(this, MemoryActivity::class.java)) },
                Item(R.drawable.ic_check, "Faiza rules", "নিয়ম রুল") { startActivity(Intent(this, RulesActivity::class.java)) },
                Item(R.drawable.ic_bolt, "Macros", "ম্যাক্রো শর্টকাট") { startActivity(Intent(this, MacrosActivity::class.java)) },
                Item(R.drawable.ic_star, "Skills & store", "স্কিল দক্ষতা") { startActivity(Intent(this, SkillsActivity::class.java)) }
            ),
            "🛡️ নিরাপত্তা · Safety" to listOf(
                Item(R.drawable.ic_shield, "SOS", "বিপদ সাহায্য জরুরি emergency") { startActivity(Intent(this, SosActivity::class.java)) },
                Item(R.drawable.ic_mic, "Voice Guardian", "ভয়েস পাহারা নিরাপত্তা") { startActivity(Intent(this, VoiceGuardianActivity::class.java)) }
            ),
            "⚙️ অ্যাপ · App" to listOf(
                Item(R.drawable.ic_info, "How to use", "সাহায্য গাইড শেখা help") { startActivity(Intent(this, HelpActivity::class.java)) },
            ) + (if (com.faiza.ai.BuildConfig.DEBUG) listOf(
                // Test APK only: the owner's phone checklist.
                Item(R.drawable.ic_check, "🧪 Phone test", "টেস্ট চেকলিস্ট") { startActivity(Intent(this, TestChecklistActivity::class.java)) }
            ) else emptyList()) + listOf(
                Item(R.drawable.ic_settings, "Settings", "সেটিংস") { startActivity(Intent(this, SettingsActivity::class.java)) },
                Item(R.drawable.ic_lock, "License & updates", "লাইসেন্স আপডেট কেনা") {
                    startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsHub.EXTRA_SECTION, "License"))
                }
            )
        )
        val box = binding.drawerMenu
        box.removeAllViews()
        val dp = resources.displayMetrics.density
        val list = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL }
        fun render(query: String) {
            list.removeAllViews()
            val q = query.trim().lowercase()
            groups.forEach { (title, items) ->
                val shown = if (q.isEmpty()) items else items.filter { (it.label + " " + it.words).lowercase().contains(q) }
                if (shown.isEmpty()) return@forEach
                list.addView(android.widget.TextView(this).apply {
                    text = title; textSize = 12f
                    setTextColor(getColor(R.color.primary_light))
                    setPadding((20 * dp).toInt(), (14 * dp).toInt(), (20 * dp).toInt(), (4 * dp).toInt())
                })
                shown.forEach { item ->
                    list.addView(android.widget.LinearLayout(this).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        minimumHeight = (48 * dp).toInt()
                        setPadding((20 * dp).toInt(), 0, (20 * dp).toInt(), 0)
                        val out = android.util.TypedValue()
                        theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                        setBackgroundResource(out.resourceId)
                        addView(android.widget.ImageView(context).apply {
                            setImageResource(item.icon)
                            imageTintList = getColorStateList(R.color.primary_light)
                            layoutParams = android.widget.LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt())
                        })
                        addView(android.widget.TextView(context).apply {
                            text = item.label; textSize = 15f
                            setTextColor(getColor(R.color.text_primary))
                            setPadding((18 * dp).toInt(), 0, 0, 0)
                        })
                        setOnClickListener {
                            binding.drawer.closeDrawers()
                            item.action()
                        }
                    })
                }
            }
            if (list.childCount == 0) list.addView(android.widget.TextView(this).apply {
                text = "কিছু পাওয়া যায়নি — ফাইজাকে মুখে বলুন"; textSize = 14f
                setTextColor(getColor(R.color.text_secondary)); setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), 0)
            })
        }
        box.addView(android.widget.EditText(this).apply {
            hint = "🔍 খুঁজুন · Search"; textSize = 14f; maxLines = 1; isSingleLine = true
            setTextColor(getColor(R.color.text_primary)); setHintTextColor(getColor(R.color.text_muted))
            setBackgroundResource(R.drawable.bg_input)
            setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (4 * dp).toInt())
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(e: android.text.Editable?) { render(e?.toString().orEmpty()) }
            })
        })
        box.addView(list)
        render("")
    }

    /** Typed or tapped requests: Faiza answers out loud (starting her first if needed). */
    private fun askFaiza(text: String) {
        val service = FloatingOrbService.instance
        if (service != null) {
            service.handleTyped(text)
            return
        }
        FloatingOrbService.pendingTypedText = text
        startFaizaAI()
        if (!isAiRunning) FloatingOrbService.pendingTypedText = null
    }

    private fun updateHomeCards() {
        binding.bellDot.visibility =
            if (com.faiza.ai.util.ActivityLog.hasUnread()) android.view.View.VISIBLE else android.view.View.GONE

        val now = java.util.Date()
        binding.tvToday.text = java.text.SimpleDateFormat("d", java.util.Locale.ENGLISH).format(now)
        binding.tvTodaySub.text = java.text.SimpleDateFormat("EEE, MMM", java.util.Locale.ENGLISH).format(now)

        val mood = prefs.userMood
        val fresh = System.currentTimeMillis() - prefs.userMoodAt < 12 * 3600_000L
        if (mood.isNotEmpty() && fresh) {
            binding.tvMood.text = mood.replaceFirstChar { it.uppercase() }
            binding.tvMoodSub.text = when (mood) {
                "happy", "excited", "loving", "calm" -> "All good"
                "tired", "sleepy" -> "Rest a little"
                "sad", "stressed", "angry", "sick" -> "I'm here for you"
                else -> "Noted today"
            }
        } else {
            binding.tvMood.text = "—"
            binding.tvMoodSub.text = "Talk to me"
        }

        if (!com.faiza.ai.ai.sos.LocationHelper.hasPermission(this)) {
            binding.tvWeather.text = "—"
            binding.tvWeatherSub.text = "Allow location"
        } else {
            if (binding.tvWeather.text.isNullOrBlank() || binding.tvWeather.text == "29°") {
                binding.tvWeather.text = "…"
                binding.tvWeatherSub.text = "Loading"
            }
            lifecycleScope.launch {
                val w = com.faiza.ai.ai.web.WebSkills.homeWeatherSummary(this@DashboardActivity)
                binding.tvWeather.text = w?.first ?: "—"
                binding.tvWeatherSub.text = w?.second ?: "No data"
            }
        }
    }

    private fun updatePlanStatus() {
        val pill = binding.tvPlanStatus
        if (prefs.isLicenseValid) {
            val plan = prefs.licensePlan?.replaceFirstChar { it.uppercase() } ?: "Pro"
            pill.text = "$plan plan · Active"
            pill.setBackgroundResource(R.drawable.bg_pill_success)
            pill.setTextColor(getColor(R.color.success))
        } else if (FaizaApp.UNLOCK_ALL_FOR_TESTING) {
            pill.text = "⭐ Premium · সব খোলা"
            pill.setBackgroundResource(R.drawable.bg_pill_success)
            pill.setTextColor(getColor(R.color.success))
        } else {
            val left = (10f - prefs.trialMinutesUsed).coerceAtLeast(0f)
            pill.text = if (left > 0f) "Free trial · %.0f min left".format(left) else "Trial ended · Get a license"
            pill.setBackgroundResource(R.drawable.bg_pill_warning)
            pill.setTextColor(getColor(R.color.warning))
        }
    }

    private fun updateApiStatus() {
        if (prefs.isAiConfigured()) {
            val live = if (prefs.canUseLive()) " · Live voice" else ""
            binding.tvApiStatus.text = "${prefs.activeProvider.displayName} connected$live"
            binding.tvApiStatus.setTextColor(getColor(R.color.success))
        } else {
            binding.tvApiStatus.text = "Add your free Gemini key"
            binding.tvApiStatus.setTextColor(getColor(R.color.warning))
        }
    }

    private fun startFaizaAI() {
        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
                .setTitle("Allow display over other apps")
                .setMessage("Faiza needs this to show her floating orb on top of other apps.")
                .setPositiveButton("Allow") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        // Check if API key is set
        if (!prefs.isAiConfigured()) {
            if (prefs.activeProvider == ProviderType.GEMINI) {
                startActivity(Intent(this, ApiSetupActivity::class.java))
            } else {
                ProviderDialogs.showProviderSetup(this, prefs.activeProvider) { updateApiStatus() }
            }
            return
        }

        // Start the floating orb service
        val intent = Intent(this, FloatingOrbService::class.java)
        startForegroundService(intent)

        isAiRunning = true
        updateStatus()
        Toast.makeText(this, "Faiza is on — talk to her anytime", Toast.LENGTH_SHORT).show()
    }

    private fun stopFaizaAI() {
        val intent = Intent(this, FloatingOrbService::class.java)
        stopService(intent)

        isAiRunning = false
        updateStatus()
        Toast.makeText(this, "Faiza is off", Toast.LENGTH_SHORT).show()
    }

    private fun updateHeroOrb() {
        val design = OrbDesign.from(prefs.floatingOrbDesign)
        val orb = binding.orbHero
        orb.design = if (design.isPro && !FaizaApp.instance.licenseManager.isPro()) OrbDesign.SPHERE else design
        orb.theme = OrbTheme.from(prefs.orbTheme)
        orb.setIconRes(R.drawable.ic_wave)
        orb.mode = if (isAiRunning) OrbMode.LISTENING else OrbMode.IDLE
        // Avatar instead of the orb (living picture).
        val useAvatar = com.faiza.ai.ui.orb.AvatarStore.inUse(this)
        orb.visibility = if (useAvatar) android.view.View.GONE else android.view.View.VISIBLE
        binding.avatarHero.visibility = if (useAvatar) android.view.View.VISIBLE else android.view.View.GONE
        if (useAvatar) {
            binding.avatarHero.theme = OrbTheme.from(prefs.orbTheme)
            binding.avatarHero.reload()
            binding.avatarHero.mode = if (isAiRunning) OrbMode.LISTENING else OrbMode.IDLE
        }
    }

    private fun updateStatus() {
        updateHeroOrb()
        val persona = Persona.from(prefs.selectedPersona)
        val name = prefs.assistantName(persona)
        binding.tvPersona.text = if (name == persona.displayName) "$name · ${persona.tagline}"
        else "$name · ${persona.displayName} persona"
        if (isAiRunning) {
            binding.btnToggleAI.text = "Stop $name"
            binding.btnToggleAI.setIconResource(R.drawable.ic_stop)
            binding.btnToggleAI.backgroundTintList = getColorStateList(R.color.surface_2)
            binding.btnToggleAI.strokeColor = getColorStateList(R.color.stroke)
            binding.btnToggleAI.strokeWidth = resources.displayMetrics.density.toInt()
            binding.tvAiStatus.text = "$name is on — tap the orb to talk"
        } else {
            binding.btnToggleAI.text = "Start $name"
            binding.btnToggleAI.setIconResource(R.drawable.ic_play)
            binding.btnToggleAI.backgroundTintList = getColorStateList(R.color.primary)
            binding.btnToggleAI.strokeWidth = 0
            binding.tvAiStatus.text = "Ready when you are"
        }
        // A Life mode shows first: calls are being declined.
        val lifeMode = runCatching { com.faiza.ai.ai.modes.ModeCenter.current(this) }.getOrNull()
        if (lifeMode != null) {
            binding.ivLiveStatus.setImageResource(R.drawable.ic_moon)
            binding.ivLiveStatus.imageTintList = getColorStateList(R.color.warning)
            binding.tvLiveStatus.text = "${lifeMode.emoji} ${lifeMode.name} mode — calls get a polite text"
            return
        }
        // Quiet mode shows next: it's what matters when you're out.
        val quiet = com.faiza.ai.util.QuietMode.manualActive()
        binding.btnQuiet.imageTintList = getColorStateList(if (quiet) R.color.warning else R.color.text_primary)
        if (quiet) {
            binding.ivLiveStatus.setImageResource(R.drawable.ic_moon)
            binding.ivLiveStatus.imageTintList = getColorStateList(R.color.warning)
            binding.tvLiveStatus.text = com.faiza.ai.util.QuietMode.statusText()
            return
        }
        // MAYA: "Maya is asleep — say 'Hey Maya'".
        if (isAiRunning) {
            binding.ivLiveStatus.setImageResource(R.drawable.ic_wave)
            binding.ivLiveStatus.imageTintList = getColorStateList(R.color.success)
            binding.tvLiveStatus.text = "$name is awake and listening"
        } else {
            binding.ivLiveStatus.setImageResource(R.drawable.ic_moon)
            binding.ivLiveStatus.imageTintList = getColorStateList(R.color.text_secondary)
            binding.tvLiveStatus.text = if (prefs.isWakeWordEnabled) "$name is asleep — say “Hey $name”"
            else "$name is asleep — tap the mic to wake her"
        }
    }

    private fun showPersonaSelector() {
        val all = Persona.values()
        val isPro = FaizaApp.instance.licenseManager.isPro()
        val labels = all.map { p ->
            val lock = if (p.isPro && !isPro) "  · PRO" else ""
            "${p.displayName} — ${p.tagline}$lock"
        }.toTypedArray()
        val currentIndex = all.indexOf(Persona.from(prefs.selectedPersona))

        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Choose a persona")
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val chosen = all[which]
                dialog.dismiss()
                if (chosen.isPro && !isPro) {
                    showProRequired("${chosen.displayName} Persona")
                    return@setSingleChoiceItems
                }
                prefs.selectedPersona = chosen.id
                updateStatus()
                Toast.makeText(this, "${chosen.displayName} is now active", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showProRequired(feature: String) {
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Pro feature")
            .setMessage("$feature is part of the Pro and Lifetime plans.")
            .setPositiveButton("See plans") { _, _ ->
                startActivity(Intent(this, LicenseActivity::class.java))
            }
            .setNegativeButton("Later", null)
            .show()
    }
}
