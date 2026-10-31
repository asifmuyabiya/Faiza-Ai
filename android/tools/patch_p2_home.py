import os
os.chdir(r"C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main")
J = "java/com/faiza/ai/"


def patch(p, pairs):
    s = open(p, encoding="utf-8").read()
    for a, b in pairs:
        assert s.count(a) == 1, p + " :: " + a[:80]
        s = s.replace(a, b)
    open(p, "w", encoding="utf-8").write(s)


# ---------- Prefs ----------
patch(J + "util/PrefsManager.kt", [
 ("""    /** In-app update check (video 7:15). */""", """    // Home screen (MAYA): the 🔔 inbox and the Mood card.
    var activityLogJson: String?
        get() = prefs.getString("activity_log", null)
        set(value) = prefs.edit().putString("activity_log", value).apply()

    var activityLogSeenAt: Long
        get() = prefs.getLong("activity_log_seen", 0L)
        set(value) = prefs.edit().putLong("activity_log_seen", value).apply()

    /** The mood Faiza noticed ("happy", "tired"…) and when. */
    var userMood: String
        get() = prefs.getString("user_mood", "") ?: ""
        set(value) = prefs.edit().putString("user_mood", value).putLong("user_mood_at", System.currentTimeMillis()).apply()

    val userMoodAt: Long get() = prefs.getLong("user_mood_at", 0L)

    /** In-app update check (video 7:15). */"""),
])

# ---------- Manifest ----------
patch("AndroidManifest.xml", [
 ("""        <!-- Macros -->""", """        <!-- Notifications inbox (MAYA 🔔) -->
        <activity android:name=".ui.ActivityLogActivity"
            android:theme="@style/Theme.FaizaAI" />

        <!-- Macros -->"""),
])

# ---------- Mood & journal tools ----------
patch(J + "ai/skills/ExtraSkills.kt", [
 ("""        Skill(
            "teach_command",""", """        Skill(
            "set_mood", "💗", "Mood", "Notices how you feel",
            "Quietly note the user's current mood when it's clear from what they say or how they sound (shown on the " +
                    "home screen). Don't announce it.",
            listOf(
                ToolParam("mood", ParamType.STRING, "Their mood", enum = listOf(
                    "happy", "excited", "calm", "loving", "tired", "sleepy", "sad", "stressed", "angry", "sick", "bored"
                ))
            )
        ) { _, args ->
            prefs.userMood = args.require("mood")
            "OK: mood noted (don't mention it unless it helps)."
        },

        Skill(
            "save_journal", "📔", "Journal", "\\"Write in my journal\\"",
            "Save a short journal entry about the user's day (in their words, first person, 1-4 sentences).",
            listOf(ToolParam("entry", ParamType.STRING, "The journal entry"))
        ) { _, args ->
            val entry = args.require("entry")
            val date = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.ENGLISH).format(java.util.Date())
            com.faiza.ai.ai.memory.MemoryStore.remember("Journal $date: $entry".take(300))
            com.faiza.ai.util.ActivityLog.add("journal", "Journal · $date", entry)
            "OK: saved in the journal for $date."
        },

        Skill(
            "teach_command","""),
])

patch(J + "ai/FaizaAgent.kt", [
 ("""            - CALLS: while a call is ringing""", """            - MOOD: when the user's mood is clear (happy, tired, sad, stressed…), quietly call set_mood.
            - [STUDY MODE] → be a patient tutor: ask what they're studying, explain step by step with small examples,
              then ask a quick question to check. [JOURNAL] → ask gently about their day, then save_journal.
            - CALLS: while a call is ringing"""),
])

# ---------- Weather summary for the home card ----------
patch(J + "ai/web/WebSkills.kt", [
 ("""    // ===== Nearby places =====""", """    /** "29°" and "Partly cloudy" for the home card, from the last known location (cached 30 min). */
    @Volatile private var homeWeather: Triple<Long, String, String>? = null

    suspend fun homeWeatherSummary(ctx: Context): Pair<String, String>? {
        homeWeather?.takeIf { System.currentTimeMillis() - it.first < 30 * 60_000L }?.let { return it.second to it.third }
        val loc = LocationHelper.lastKnown(ctx) ?: return null
        return runCatching {
            val root = getJson(
                "https://api.open-meteo.com/v1/forecast?latitude=${loc.latitude}&longitude=${loc.longitude}" +
                        "&current=temperature_2m,weather_code&timezone=auto"
            )
            val cur = root.getAsJsonObject("current")
            val temp = "${Math.round(cur.get("temperature_2m").asDouble)}°"
            val sky = WebParsers.weatherText(cur.get("weather_code").asDouble.toInt()).replaceFirstChar { it.uppercase() }
            homeWeather = Triple(System.currentTimeMillis(), temp, sky)
            temp to sky
        }.getOrNull()
    }

    // ===== Nearby places ====="""),
])

# ---------- Inbox entries from things she does on her own ----------
patch(J + "ai/routines/RoutineCenter.kt", [
 ("""        notify(ctx, t.label, t.instruction, id.hashCode())""",
  """        notify(ctx, t.label, t.instruction, id.hashCode())
        com.faiza.ai.util.ActivityLog.add("reminder", t.label, t.instruction)"""),
 ("""                    if (ok) notify(ctx, "Faiza replied to $sender", "“${w.replyText}” ($app)", key.hashCode())""",
  """                    if (ok) {
                        notify(ctx, "Faiza replied to $sender", "“${w.replyText}” ($app)", key.hashCode())
                        com.faiza.ai.util.ActivityLog.add("reply", "Replied to $sender", "“${w.replyText}” on $app")
                    }"""),
 ("""                WatchAction.TELL -> {
                    ask(ctx,""", """                WatchAction.TELL -> {
                    com.faiza.ai.util.ActivityLog.add("watch", "$sender on $app", text.take(120))
                    ask(ctx,"""),
])
patch(J + "ai/macros/MacroRunner.kt", [
 ("""        return "OK: macro “${macro.name}” finished (${macro.steps.size} steps).\"""",
  """        com.faiza.ai.util.ActivityLog.add("macro", "Macro “${macro.name}” finished", "${macro.steps.size} steps")
        return "OK: macro “${macro.name}” finished (${macro.steps.size} steps).\""""),
])

# ---------- Dashboard ----------
patch(J + "ui/DashboardActivity.kt", [
 ("""        binding.cardChat.setOnClickListener { startActivity(Intent(this, ChatActivity::class.java)) }
        binding.cardScan.setOnClickListener { startActivity(Intent(this, ScanActivity::class.java)) }
""", """        setupHomeChrome()
"""),
 ("""        updateGreeting()
        showUpdateBanner(com.faiza.ai.util.UpdateChecker.savedUpdate(this))""",
  """        updateGreeting()
        updateHomeCards()
        showUpdateBanner(com.faiza.ai.util.UpdateChecker.savedUpdate(this))"""),
 ("""    private fun updateGreeting() {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        binding.tvGreeting.text = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
        val name = prefs.userName.orEmpty().trim().substringBefore(' ')
        binding.tvHello.text = if (name.isEmpty()) "Welcome back" else "Hi, $name"
    }""", """    private fun updateGreeting() {
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

        val menu = listOf(
            Triple(R.drawable.ic_settings, "Settings") { startActivity(Intent(this, SettingsActivity::class.java)) },
            Triple(R.drawable.ic_bell, "Notifications") { startActivity(Intent(this, ActivityLogActivity::class.java)) },
            Triple(R.drawable.ic_timer, "Routines") { startActivity(Intent(this, RoutinesActivity::class.java)) },
            Triple(R.drawable.ic_bolt, "Macros") { startActivity(Intent(this, MacrosActivity::class.java)) },
            Triple(R.drawable.ic_star, "Skills & store") { startActivity(Intent(this, SkillsActivity::class.java)) },
            Triple(R.drawable.ic_memory, "Memories") { startActivity(Intent(this, MemoryActivity::class.java)) },
            Triple(R.drawable.ic_heart, "Favorite contacts") { startActivity(Intent(this, ContactsActivity::class.java)) },
            Triple(R.drawable.ic_shield, "SOS") { startActivity(Intent(this, SosActivity::class.java)) },
            Triple(R.drawable.ic_palette, "Floating orb") { startActivity(Intent(this, OrbSettingsActivity::class.java)) },
            Triple(R.drawable.ic_mic, "Voice Guardian") { startActivity(Intent(this, VoiceGuardianActivity::class.java)) },
            Triple(R.drawable.ic_lock, "License & updates") { startActivity(Intent(this, LicenseActivity::class.java)) }
        )
        val box = binding.drawerMenu
        box.removeAllViews()
        val dp = resources.displayMetrics.density
        menu.forEach { (icon, label, action) ->
            box.addView(android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                minimumHeight = (52 * dp).toInt()
                setPadding((20 * dp).toInt(), 0, (20 * dp).toInt(), 0)
                val out = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                setBackgroundResource(out.resourceId)
                addView(android.widget.ImageView(context).apply {
                    setImageResource(icon)
                    imageTintList = getColorStateList(R.color.primary_light)
                    layoutParams = android.widget.LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt())
                })
                addView(android.widget.TextView(context).apply {
                    text = label
                    textSize = 15f
                    setTextColor(getColor(R.color.text_primary))
                    setPadding((18 * dp).toInt(), 0, 0, 0)
                })
                setOnClickListener {
                    binding.drawer.closeDrawers()
                    action()
                }
            })
        }
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
    }"""),
])
print("home patched")
