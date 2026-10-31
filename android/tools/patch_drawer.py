import os
p = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai\ui\DashboardActivity.kt'
s = open(p, encoding='utf-8').read()
a = s.index('        val menu = listOf(')
b = s.index('''    /** Typed or tapped requests: Faiza answers out loud''')
new = '''        // Grouped, with Bangla words too, so the search finds "নামাজ" as well as "prayer".
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

'''
s = s[:a] + new + s[b:]
open(p, 'w', encoding='utf-8').write(s)
print('ok')
