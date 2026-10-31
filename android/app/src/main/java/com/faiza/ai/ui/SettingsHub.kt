package com.faiza.ai.ui

import android.content.Intent
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.faiza.ai.R
import com.google.android.material.card.MaterialCardView

/**
 * MAYA-style Settings: the main page is a short grouped list (Account · Assistant · Memory & data · System);
 * each item opens a page with just that section. The long settings layout is reused — sections are simply
 * shown or hidden, so every row keeps working exactly as before.
 */
object SettingsHub {

    const val EXTRA_SECTION = "settings_section"
    const val PHONE_ACCESS = "Phone access"

    private data class Item(val section: String, val icon: Int, val color: String, val title: String, val sub: String)

    private val GROUPS = listOf(
        "Account" to listOf(
            Item("About you", R.drawable.ic_person, "green", "Personal", "Contacts, country, SIM, music, YouTube"),
            Item(PHONE_ACCESS, R.drawable.ic_shield, "green", "Phone access", "Permissions Faiza needs")
        ),
        "Assistant" to listOf(
            Item("Assistant", R.drawable.ic_sparkle, "pink", "Faiza", "Persona, voice, speaking style, language"),
            Item("Listening", R.drawable.ic_mic, "cyan", "Listening", "Live voice, wake word, Voice Guardian"),
            Item("Announcements", R.drawable.ic_campaign, "blue", "Announcements", "Messages and callers")
        ),
        "Memory & data" to listOf(
            Item("Memory & privacy", R.drawable.ic_memory, "violet", "Memory & backup", "Memory, incognito, backup & restore")
        ),
        "System" to listOf(
            Item("Appearance", R.drawable.ic_palette, "violet", "Appearance", "Floating orb, edge glow, typing"),
            Item("AI engine", R.drawable.ic_chip, "amber", "AI engine", "Provider, API key, live model"),
            Item("Extra services", R.drawable.ic_globe, "blue", "Optional", "Maps, web search, images"),
            Item("License", R.drawable.ic_lock, "amber", "License & updates", "Plan, app updates, move license")
        )
    )

    private val SECTION_NAMES = GROUPS.flatMap { g -> g.second.map { it.section } }.toSet()

    /** Call after setContentView. [section] null = the hub. */
    fun arrange(activity: AppCompatActivity, section: String?) {
        val root = (activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView)
            .getChildAt(0) as LinearLayout
        val topBar = root.getChildAt(0) as ViewGroup
        val title = (0 until topBar.childCount).map { topBar.getChildAt(it) }.filterIsInstance<TextView>().firstOrNull()

        // Which views belong to which section (a section header and everything after it).
        val groups = linkedMapOf<String, MutableList<View>>()
        var current: String? = null
        for (i in 1 until root.childCount) {
            val v = root.getChildAt(i)
            when {
                v.id == R.id.cardProfile || v.id == R.id.tvVersion -> current = null
                v.id == R.id.cardPermissions -> groups.getOrPut(PHONE_ACCESS) { mutableListOf() } += v
                v is TextView && v.text.toString() in SECTION_NAMES -> {
                    current = v.text.toString()
                    groups.getOrPut(current) { mutableListOf() } += v
                }
                current != null -> groups.getOrPut(current) { mutableListOf() } += v
            }
        }

        if (section == null) {
            groups.values.flatten().forEach { it.visibility = View.GONE }
            val profileIndex = (0 until root.childCount).first { root.getChildAt(it).id == R.id.cardProfile }
            root.addView(hubView(activity), profileIndex + 1)
            return
        }
        title?.text = GROUPS.flatMap { it.second }.firstOrNull { it.section == section }?.title ?: section
        root.findViewById<View>(R.id.cardProfile).visibility = View.GONE
        root.findViewById<View>(R.id.tvVersion).visibility = View.GONE
        groups.forEach { (name, views) ->
            views.forEachIndexed { i, v ->
                v.visibility = when {
                    name != section -> View.GONE
                    i == 0 && v is TextView && name != PHONE_ACCESS -> View.GONE // the page title says it already
                    else -> View.VISIBLE
                }
            }
        }
        // Phone access opens with its list showing.
        if (section == PHONE_ACCESS) root.findViewById<View>(R.id.permissionsList)?.visibility = View.VISIBLE
    }

    private fun hubView(a: AppCompatActivity): View {
        val dp = a.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        GROUPS.forEachIndexed { gi, pair ->
            val (group, items) = pair
            box.addView(TextView(a).apply {
                text = group.uppercase()
                textSize = 11.5f
                letterSpacing = 0.14f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(a.getColor(R.color.primary_light))
                setPadding(px(10), px(if (gi == 0) 20 else 28), 0, px(10))
            })
            val card = MaterialCardView(a).apply {
                radius = px(24).toFloat()
                cardElevation = 0f
                setCardBackgroundColor(a.getColor(R.color.surface))
                strokeWidth = px(1)
                setStrokeColor(a.getColorStateList(R.color.stroke))
            }
            val list = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, px(4), 0, px(4))
            }
            items.forEachIndexed { i, item ->
                if (i > 0) list.addView(View(a).apply {
                    setBackgroundColor(a.getColor(R.color.divider))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(1)).apply {
                        marginStart = px(66); marginEnd = px(16)
                    }
                })
                list.addView(row(a, item, ::px))
            }
            card.addView(list)
            box.addView(card)
        }
        box.setPadding(0, 0, 0, px(20))
        return box
    }

    private fun row(a: AppCompatActivity, item: Item, px: (Int) -> Int): View {
        val res = a.resources
        val pkg = a.packageName
        val bg = res.getIdentifier("bg_tile_${item.color}", "drawable", pkg)
        val tint = res.getIdentifier("tile_${item.color}", "color", pkg)
        return LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = px(70)
            setPadding(px(14), px(13), px(16), px(13))
            val out = TypedValue()
            a.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
            setBackgroundResource(out.resourceId)
            addView(ImageView(a).apply {
                setImageResource(item.icon)
                setBackgroundResource(bg)
                imageTintList = a.getColorStateList(tint)
                setPadding(px(11), px(11), px(11), px(11))
                layoutParams = LinearLayout.LayoutParams(px(44), px(44))
            })
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = px(14); marginEnd = px(8)
                }
                addView(TextView(a).apply {
                    text = item.title
                    textSize = 15f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(a.getColor(R.color.text_primary))
                })
                addView(TextView(a).apply {
                    text = item.sub
                    textSize = 13f
                    setTextColor(a.getColor(R.color.text_secondary))
                    setPadding(0, px(3), 0, 0)
                })
            })
            addView(ImageView(a).apply {
                setImageResource(R.drawable.ic_chevron_right)
                imageTintList = a.getColorStateList(R.color.text_muted)
                layoutParams = LinearLayout.LayoutParams(px(20), px(20))
            })
            setOnClickListener {
                if (item.section == "Appearance") {
                    a.startActivity(Intent(a, OrbSettingsActivity::class.java))
                } else {
                    a.startActivity(Intent(a, SettingsActivity::class.java).putExtra(EXTRA_SECTION, item.section))
                }
            }
        }
    }
}
