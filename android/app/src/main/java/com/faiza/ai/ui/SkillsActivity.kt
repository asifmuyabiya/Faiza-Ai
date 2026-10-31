package com.faiza.ai.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.skills.BuiltInSkills
import com.faiza.ai.ai.skills.CustomSkill
import com.faiza.ai.ai.skills.SubAgent
import com.faiza.ai.ai.skills.UserSkillStore
import com.faiza.ai.service.FaizaAccessibilityService
import com.google.android.material.switchmaterial.SwitchMaterial

class SkillsActivity : AppCompatActivity() {

    private val prefs get() = FaizaApp.instance.prefs
    private val isPro get() = FaizaApp.instance.licenseManager.isPro()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_skills)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAddSkill).setOnClickListener {
            if (isPro) editCustomSkill(null) else showProRequired("Custom Skills")
        }
        findViewById<View>(R.id.btnAddAgent).setOnClickListener {
            if (isPro) editSubAgent(null) else showProRequired("Sub-Agents")
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<View>(R.id.badgeCustomPro).visibility = if (isPro) View.GONE else View.VISIBLE
        findViewById<View>(R.id.badgeAgentsPro).visibility = if (isPro) View.GONE else View.VISIBLE
        findViewById<View>(R.id.tvSkillsNote).visibility = if (prefs.hasFullAccess) View.GONE else View.VISIBLE
        renderBuiltIn()
        renderCustomSkills()
        renderSubAgents()
        renderStore()
    }

    // ========== Skill store (MAYA) ==========

    private fun renderStore() {
        lifecycleScope.launch {
            val catalog = com.faiza.ai.ai.skills.SkillStore.catalog()
            val list = findViewById<LinearLayout>(R.id.storeList)
            list.removeAllViews()
            val installed = UserSkillStore.customSkills(prefs)
            catalog.forEachIndexed { i, s ->
                if (i > 0) list.addView(divider())
                val done = com.faiza.ai.ai.skills.SkillStore.isInstalled(installed, s)
                val button = TextView(this@SkillsActivity).apply {
                    text = if (done) "Installed" else "Install"
                    textSize = 13f
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                    setTextColor(getColor(if (done) R.color.success else R.color.primary_light))
                    setBackgroundResource(if (done) R.drawable.bg_pill_success else R.drawable.bg_pill)
                    setPadding(dp(14), dp(7), dp(14), dp(7))
                    isEnabled = !done
                    setOnClickListener {
                        if (!isPro) {
                            Toast.makeText(this@SkillsActivity, "Custom routines are a Pro feature", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        UserSkillStore.saveCustomSkills(prefs, UserSkillStore.customSkills(prefs) + com.faiza.ai.ai.skills.SkillStore.install(s))
                        Toast.makeText(this@SkillsActivity, "Installed “${s.name}” — say “${s.trigger.substringBefore('/').trim()}”", Toast.LENGTH_LONG).show()
                        renderCustomSkills()
                        renderStore()
                    }
                }
                list.addView(row(s.emoji, s.name, s.description, null, button, null))
            }
        }
    }

    // ========== Lists ==========

    private fun renderBuiltIn() {
        val list = findViewById<LinearLayout>(R.id.builtInList)
        list.removeAllViews()
        val accessibilityOn = FaizaAccessibilityService.instance != null

        (BuiltInSkills.all + com.faiza.ai.ai.web.WebSkills.skills() + com.faiza.ai.ai.routines.RoutineCenter.skills() +
                com.faiza.ai.ai.files.FileSkills.skills() + com.faiza.ai.ai.skills.ExtraSkills.skills()).forEachIndexed { i, skill ->
            if (i > 0) list.addView(divider())
            val note = if (skill.needsAccessibility && !accessibilityOn) "Needs Accessibility (Settings → Phone access)" else null
            val switch = SwitchMaterial(this).apply {
                isChecked = skill.id !in prefs.disabledSkills
                setOnCheckedChangeListener { _, on ->
                    prefs.disabledSkills = if (on) prefs.disabledSkills - skill.id else prefs.disabledSkills + skill.id
                }
            }
            list.addView(row(skill.emoji, skill.title, skill.summary, note, switch, null))
        }
    }

    private fun renderCustomSkills() {
        val list = findViewById<LinearLayout>(R.id.customList)
        list.removeAllViews()
        val skills = UserSkillStore.customSkills(prefs)
        if (skills.isEmpty()) {
            list.addView(emptyText("No custom skills yet"))
            return
        }
        skills.forEachIndexed { i, skill ->
            if (i > 0) list.addView(divider())
            val switch = SwitchMaterial(this).apply {
                isChecked = skill.enabled
                isEnabled = isPro
                setOnCheckedChangeListener { _, on ->
                    UserSkillStore.saveCustomSkills(
                        prefs, UserSkillStore.customSkills(prefs).map { if (it.id == skill.id) it.copy(enabled = on) else it }
                    )
                }
            }
            list.addView(row("⚡", skill.name, "Say: \"${skill.trigger}\"", null, switch) {
                if (isPro) editCustomSkill(skill) else showProRequired("Custom Skills")
            })
        }
    }

    private fun renderSubAgents() {
        val list = findViewById<LinearLayout>(R.id.agentsList)
        list.removeAllViews()
        val agents = UserSkillStore.subAgents(prefs)
        if (agents.isEmpty()) {
            list.addView(emptyText("No sub-agents yet"))
            return
        }
        agents.forEachIndexed { i, agent ->
            if (i > 0) list.addView(divider())
            list.addView(row(agent.emoji, agent.name, agent.instructions.take(70) + "…", null, null) {
                if (isPro) editSubAgent(agent) else showProRequired("Sub-Agents")
            })
        }
    }

    // ========== Editors ==========

    private fun editCustomSkill(existing: CustomSkill?) {
        val name = field("Name, e.g. Office Mode", existing?.name)
        val trigger = field("Trigger, e.g. I'm at the office", existing?.trigger)
        val instructions = field(
            "What should Faiza do? e.g. Set volume to 10 percent and read my notifications",
            existing?.instructions, multiline = true
        )

        val dialog = AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle(if (existing == null) "New custom skill" else "Edit skill")
            .setView(form(name, trigger, instructions))
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .apply {
                if (existing != null) setNeutralButton("Delete") { _, _ ->
                    UserSkillStore.saveCustomSkills(prefs, UserSkillStore.customSkills(prefs).filter { it.id != existing.id })
                    renderCustomSkills()
                }
            }
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val n = name.text.toString().trim()
            val t = trigger.text.toString().trim()
            val ins = instructions.text.toString().trim()
            if (n.isEmpty() || t.isEmpty() || ins.isEmpty()) {
                Toast.makeText(this, "Please fill in all three fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val current = UserSkillStore.customSkills(prefs)
            val updated = if (existing == null) {
                current + CustomSkill(name = n, trigger = t, instructions = ins)
            } else {
                current.map { if (it.id == existing.id) it.copy(name = n, trigger = t, instructions = ins) else it }
            }
            UserSkillStore.saveCustomSkills(prefs, updated)
            renderCustomSkills()
            dialog.dismiss()
        }
    }

    private fun editSubAgent(existing: SubAgent?) {
        val emoji = field("Emoji, e.g. 🧑‍🍳", existing?.emoji)
        val name = field("Name, e.g. Chef", existing?.name)
        val instructions = field(
            "Who is this agent and how should it answer? e.g. You are a Bangladeshi chef. Give simple recipes.",
            existing?.instructions, multiline = true
        )

        val dialog = AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle(if (existing == null) "🤝 New Sub-Agent" else "🤝 Edit Sub-Agent")
            .setView(form(emoji, name, instructions))
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .apply {
                if (existing != null) setNeutralButton("Delete") { _, _ ->
                    UserSkillStore.saveSubAgents(prefs, UserSkillStore.subAgents(prefs).filter { it.id != existing.id })
                    renderSubAgents()
                }
            }
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val e = emoji.text.toString().trim().ifEmpty { "🤖" }
            val n = name.text.toString().trim()
            val ins = instructions.text.toString().trim()
            val current = UserSkillStore.subAgents(prefs)
            when {
                n.isEmpty() || ins.isEmpty() -> {
                    Toast.makeText(this, "Name and instructions are required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                current.any { it.name.equals(n, ignoreCase = true) && it.id != existing?.id } -> {
                    Toast.makeText(this, "A sub-agent with this name already exists", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }
            val updated = if (existing == null) {
                current + SubAgent(name = n, emoji = e, instructions = ins)
            } else {
                current.map { if (it.id == existing.id) it.copy(name = n, emoji = e, instructions = ins) else it }
            }
            UserSkillStore.saveSubAgents(prefs, updated)
            renderSubAgents()
            dialog.dismiss()
        }
    }

    // ========== View helpers ==========

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun row(
        emoji: String, title: String, subtitle: String, warning: String?,
        trailing: View?, onClick: (() -> Unit)?
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(64)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        if (onClick != null) {
            val ripple = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            setBackgroundResource(ripple.resourceId)
            isClickable = true
            setOnClickListener { onClick() }
        }

        addView(TextView(context).apply {
            text = emoji
            textSize = 18f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_icon_tile)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(14); marginEnd = dp(8) }
            addView(TextView(context).apply {
                text = title; textSize = 15f; setTextColor(getColor(R.color.text_primary))
            })
            addView(TextView(context).apply {
                text = subtitle; textSize = 13f; setTextColor(getColor(R.color.text_secondary))
            })
            warning?.let {
                addView(TextView(context).apply {
                    text = it; textSize = 12f; setTextColor(getColor(R.color.warning))
                })
            }
        })
        trailing?.let { addView(it) }
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(getColor(R.color.stroke))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            .apply { marginStart = dp(70) }
    }

    private fun emptyText(msg: String) = TextView(this).apply {
        text = msg
        textSize = 13f
        setTextColor(getColor(R.color.text_muted))
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }

    private fun field(hint: String, value: String?, multiline: Boolean = false) = EditText(this).apply {
        this.hint = hint
        setText(value.orEmpty())
        textSize = 14f
        setTextColor(Color.parseColor("#E2E8F0"))
        setHintTextColor(Color.parseColor("#64748B"))
        setBackgroundResource(R.drawable.bg_input)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        if (multiline) {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) }
    }

    private fun form(vararg fields: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(4), dp(20), 0)
        fields.forEach { addView(it) }
    }

    private fun showProRequired(feature: String) {
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Pro feature")
            .setMessage("$feature are available in the Pro plan.\n\nBuilt-in skills stay free for everyone.")
            .setPositiveButton("View Plans") { _, _ ->
                startActivity(Intent(this, LicenseActivity::class.java))
            }
            .setNegativeButton("Later", null)
            .show()
    }
}
