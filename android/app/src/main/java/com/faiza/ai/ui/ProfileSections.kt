package com.faiza.ai.ui

import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.AddressOptions
import com.faiza.ai.ai.Persona
import com.faiza.ai.ai.contacts.FavoriteContacts
import com.faiza.ai.ai.contacts.PhoneNumbers
import com.faiza.ai.ai.contacts.SimHelper
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Settings → profile card, Assistant, About you, Memory & privacy (video 10:55, 13:09, 18:26).
 * The rows live in activity_settings.xml (generated, so they all look the same); this class
 * only fills in their values and handles taps.
 */
class ProfileSections(
    private val activity: AppCompatActivity,
    private val onSaveBackup: () -> Unit,
    private val onRestoreBackup: () -> Unit
) {
    private val prefs get() = FaizaApp.instance.prefs
    private val refreshers = mutableListOf<() -> Unit>()

    fun build() {
        refreshers.clear()
        bindProfileCard()
        bindAssistant()
        bindAboutYou()
        bindMemory()
        bindServices()
        refresh()
    }

    fun refresh() = refreshers.forEach { it() }

    // ===== Profile card =====

    private fun bindProfileCard() {
        activity.findViewById<View>(R.id.cardProfile).setOnClickListener { editName() }
        refreshers += {
            val name = prefs.userName.orEmpty().trim()
            activity.findViewById<TextView>(R.id.tvProfileName).text = name.ifEmpty { "Add your name" }
            activity.findViewById<TextView>(R.id.tvAvatar).text =
                name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            val persona = Persona.from(prefs.selectedPersona)
            val assistant = prefs.assistantName(persona)
            activity.findViewById<TextView>(R.id.tvProfileSub).text =
                if (assistant == persona.displayName) "$assistant · ${persona.tagline}"
                else "$assistant · ${persona.displayName} persona"
            val plan = activity.findViewById<TextView>(R.id.tvProfilePlan)
            when {
                prefs.isLicenseValid -> {
                    val p = prefs.licensePlan?.replaceFirstChar { it.uppercase() } ?: "Pro"
                    plan.text = "$p · Active"
                    plan.setBackgroundResource(R.drawable.bg_pill_success)
                    plan.setTextColor(activity.getColor(R.color.success))
                }
                FaizaApp.UNLOCK_ALL_FOR_TESTING -> {
                    plan.text = "⭐ Premium · সব খোলা"
                    plan.setBackgroundResource(R.drawable.bg_pill_success)
                    plan.setTextColor(activity.getColor(R.color.success))
                }
                else -> {
                    plan.text = "Free trial"
                    plan.setBackgroundResource(R.drawable.bg_pill_warning)
                    plan.setTextColor(activity.getColor(R.color.warning))
                }
            }
        }
    }

    private fun editName() =
        editText("Your name", prefs.userName.orEmpty(), "e.g. Rahim",
            help = "Faiza will call you by this name.") { prefs.userName = it }

    // ===== Assistant =====

    private fun bindAssistant() {
        nav(R.id.rowAssistantName, R.id.subAssistantName, {
            prefs.assistantName() + if (prefs.assistantNameSetting.isBlank()) " (default)" else ""
        }, "") {
            editText(
                "Assistant name", prefs.assistantNameSetting, "e.g. Maya",
                help = "She'll call herself this and wake up to \"Hey <name>\". Leave empty to use the persona's own name."
            ) { prefs.assistantNameSetting = it }
        }
        nav(R.id.rowPersona, R.id.subPersona, {
            val p = Persona.from(prefs.selectedPersona)
            "${p.displayName} · ${p.tagline}"
        }, "") { pickPersona() }
        nav(R.id.rowGirlfriend, R.id.subGirlfriend, {
            com.faiza.ai.ai.Closeness.from(prefs.closenessLevel).let { "Girlfriend mode · " + it.title }
        }, "") { activity.startActivity(android.content.Intent(activity, com.faiza.ai.ui.SecretSettingsActivity::class.java)) }
        switch(R.id.rowProactiveChat, R.id.swProactiveChat, { prefs.isProactiveChat }, { prefs.isProactiveChat = it })
        nav(R.id.rowSleepTimer, R.id.subSleepTimer, {
            when (val m = prefs.liveSleepMinutes) { 0 -> "Never"; else -> "$m min" }
        }, "") { pickSleepTimer() }
        switch(R.id.rowNarrate, R.id.swNarrate, { prefs.isNarrateActions }, { prefs.isNarrateActions = it })
        switch(R.id.rowCinematic, R.id.swCinematic, { prefs.isCinematicStartup }, { prefs.isCinematicStartup = it })
    }

    private fun pickPersona() {
        val all = Persona.values()
        val isPro = FaizaApp.instance.licenseManager.isPro()
        val labels = all.map { p ->
            "${p.displayName} — ${p.tagline}" + if (p.isPro && !isPro) "  · PRO" else ""
        }.toTypedArray()
        AlertDialog.Builder(activity, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Persona")
            .setSingleChoiceItems(labels, all.indexOf(Persona.from(prefs.selectedPersona))) { d, which ->
                d.dismiss()
                val chosen = all[which]
                if (chosen.isPro && !isPro) {
                    Toast.makeText(activity, "${chosen.displayName} is a Pro persona", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.selectedPersona = chosen.id
                    refresh()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickSleepTimer() {
        val mins = listOf(0, 5, 10, 20, 45)
        val labels = arrayOf("Never", "5 min", "10 min", "20 min", "45 min")
        AlertDialog.Builder(activity, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Sleep when quiet")
            .setMessage("A live session drops to standby after this much silence. Saves battery and data.")
            .setSingleChoiceItems(labels, mins.indexOf(prefs.liveSleepMinutes).coerceAtLeast(0)) { d, which ->
                prefs.liveSleepMinutes = mins[which]
                d.dismiss()
                refresh()
            }
            .show()
    }

    // ===== About you =====

    private fun pickGender() {
        val ids = listOf("male", "female", "")
        val labels = arrayOf("Male", "Female", "Prefer not to say")
        AlertDialog.Builder(activity, R.style.Theme_FaizaAI_Dialog)
            .setTitle("You are")
            .setMessage("So she speaks to you the right way.")
            .setSingleChoiceItems(labels, ids.indexOf(prefs.userGender).coerceAtLeast(0)) { d, which ->
                prefs.userGender = ids[which]
                d.dismiss()
                refresh()
                // The suggestions change with gender: offer to pick what she calls you right away.
                if (prefs.userAddress.isBlank() && which < 2) pickAddress()
            }
            .show()
    }

    /** What she calls you. The list follows the gender you picked; "Custom…" lets you type anything. */
    private fun pickAddress() {
        val options = AddressOptions.forGender(prefs.userGender)
        val name = prefs.userName?.trim().orEmpty()
        val labels = (listOf(if (name.isNotEmpty()) "By my name ($name)" else "By my name") + options + "Custom…").toTypedArray()
        val current = prefs.userAddress
        val checked = when {
            current.isBlank() -> 0
            current in options -> options.indexOf(current) + 1
            else -> labels.size - 1
        }
        AlertDialog.Builder(activity, R.style.Theme_FaizaAI_Dialog)
            .setTitle("She calls you")
            .setSingleChoiceItems(labels, checked) { d, which ->
                d.dismiss()
                when (which) {
                    0 -> prefs.userAddress = ""
                    labels.size - 1 -> editText(
                        "She calls you", if (current in options) "" else current, "e.g. Shona, Hero, Rani",
                        help = "Any word you like — she'll use it when she talks to you."
                    ) { prefs.userAddress = it }
                    else -> prefs.userAddress = options[which - 1]
                }
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun bindAboutYou() {
        nav(R.id.rowYourName, R.id.subYourName, { prefs.userName.orEmpty().trim() }, "Add your name") {
            editName()
        }
        nav(R.id.rowGender, R.id.subGender, {
            when (prefs.userGender) { "male" -> "Male"; "female" -> "Female"; else -> "" }
        }, "Not set — tap to choose") { pickGender() }
        nav(R.id.rowAddress, R.id.subAddress, {
            prefs.userAddress.ifBlank { prefs.userName?.trim()?.takeIf { it.isNotEmpty() }?.let { "By your name ($it)" } ?: "" }
        }, "By your name") { pickAddress() }
        nav(R.id.rowContacts, R.id.subContacts, {
            val list = FavoriteContacts.load(prefs)
            if (list.isEmpty()) ""
            else {
                val sos = FavoriteContacts.sosContacts(list).joinToString { it.name }
                "${list.size} saved" + if (sos.isNotEmpty()) " · SOS: $sos" else ""
            }
        }, "Called first · used for SOS") {
            activity.startActivity(Intent(activity, ContactsActivity::class.java))
        }
        nav(R.id.rowCountry, R.id.subCountry, {
            val dial = prefs.countryDialCode
            if (dial.isEmpty()) "Automatic (from your SIM)"
            else PhoneNumbers.countryForDial(dial)?.let { "${it.name} (+${it.dialCode})" } ?: "+$dial"
        }, "") { pickCountry() }
        nav(R.id.rowSim, R.id.subSim, {
            when (prefs.preferredSim) {
                1 -> "Always SIM 1"
                2 -> "Always SIM 2"
                else -> "Ask the first time"
            }
        }, "") { pickSim() }
        nav(R.id.rowMusicApp, R.id.subMusicApp, { musicAppLabel(prefs.favoriteMusicApp) }, "YouTube") {
            val ids = arrayOf("youtube", "youtube_music", "spotify")
            val labels = ids.map { musicAppLabel(it) }.toTypedArray()
            AlertDialog.Builder(activity, R.style.Theme_FaizaAI_Dialog)
                .setTitle("Favorite music app")
                .setSingleChoiceItems(labels, ids.indexOf(prefs.favoriteMusicApp).coerceAtLeast(0)) { d, which ->
                    prefs.favoriteMusicApp = ids[which]
                    refresh()
                    d.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        nav(R.id.rowSong, R.id.subSong, { prefs.favoriteSong }, "Not set · \"play my favorite song\"") {
            editText("Favorite song", prefs.favoriteSong, "e.g. Tum Hi Ho – Arijit Singh") { prefs.favoriteSong = it }
        }
        nav(R.id.rowYoutube, R.id.subYoutube, { prefs.youtubeHandle }, "Not set · e.g. @yourchannel") {
            editText("YouTube channel", prefs.youtubeHandle, "@yourchannel") { prefs.youtubeHandle = it }
        }
        nav(
            R.id.rowYoutubeKey, R.id.subYoutubeKey,
            { if (prefs.youtubeApiKey.isBlank()) "" else "Added (${prefs.youtubeApiKey.take(6)}…)" },
            "Optional · shows your channel stats"
        ) {
            editText(
                "YouTube API key", prefs.youtubeApiKey, "AIza…", secret = true,
                help = "In Google Cloud Console, enable \"YouTube Data API v3\", then go to Credentials → Create API key.",
                helpUrl = "https://console.cloud.google.com/apis/library/youtube.googleapis.com"
            ) { prefs.youtubeApiKey = it }
        }
    }

    private fun pickCountry() {
        val labels = listOf("Automatic (from your SIM)") + PhoneNumbers.COUNTRIES.map { "${it.name} (+${it.dialCode})" }
        val codes = listOf("") + PhoneNumbers.COUNTRIES.map { it.dialCode }
        AlertDialog.Builder(activity, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Country code")
            .setSingleChoiceItems(labels.toTypedArray(), codes.indexOf(prefs.countryDialCode).coerceAtLeast(0)) { d, which ->
                prefs.countryDialCode = codes[which]
                refresh()
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickSim() {
        val sims = SimHelper.sims(activity)
        val labels = arrayOf(
            "Ask the first time",
            "SIM 1" + (sims.firstOrNull { it.slot == 1 }?.let { " · ${it.label}" } ?: ""),
            "SIM 2" + (sims.firstOrNull { it.slot == 2 }?.let { " · ${it.label}" } ?: "")
        )
        AlertDialog.Builder(activity, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Calling SIM")
            .setSingleChoiceItems(labels, prefs.preferredSim) { d, which ->
                prefs.preferredSim = which
                refresh()
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun musicAppLabel(id: String) = when (id) {
        "spotify" -> "Spotify"
        "youtube_music" -> "YouTube Music"
        else -> "YouTube"
    }

    // ===== Memory & privacy =====

    private fun bindMemory() {
        switch(R.id.rowMemory, R.id.swMemory, { prefs.isMemoryEnabled }, { prefs.isMemoryEnabled = it })
        switch(R.id.rowIncognito, R.id.swIncognito, { prefs.isIncognito }) { on ->
            if (!on) {
                AlertDialog.Builder(activity, R.style.Theme_FaizaAI_Dialog)
                    .setTitle("Turn off Incognito?")
                    .setMessage("Faiza could then remember passwords, OTPs, PINs or card numbers you say out loud. Keeping Incognito on is safer.")
                    .setPositiveButton("Turn off") { _, _ -> prefs.isIncognito = false; refresh() }
                    .setNegativeButton("Keep on") { _, _ -> refresh() }
                    .setOnCancelListener { refresh() }
                    .show()
            } else prefs.isIncognito = true
        }
        activity.findViewById<View>(R.id.rowManageMemories).setOnClickListener {
            activity.startActivity(Intent(activity, MemoryActivity::class.java))
        }
        activity.findViewById<View>(R.id.rowSaveBackup).setOnClickListener { onSaveBackup() }
        activity.findViewById<View>(R.id.rowRestoreBackup).setOnClickListener { onRestoreBackup() }
    }

    // ===== Extra services (video 29:43) =====

    private fun keyStatus(key: String, empty: String) = if (key.isBlank()) empty else "Added (${key.take(6)}…)"

    private fun bindServices() {
        nav(R.id.rowMapsKey, R.id.subMapsKey, { keyStatus(prefs.mapsApiKey, "") },
            "Optional · ratings, opening hours, traffic") {
            editText(
                "Google Maps key", prefs.mapsApiKey, "AIza…", secret = true,
                help = "In Google Cloud Console, enable \"Places API (New)\" and \"Routes API\", then create an API key. " +
                        "Without it, Faiza uses free OpenStreetMap (no ratings or live traffic).",
                helpUrl = "https://console.cloud.google.com/google/maps-apis/start"
            ) { prefs.mapsApiKey = it }
        }
        nav(R.id.rowSearchKey, R.id.subSearchKey, { keyStatus(prefs.tavilyApiKey, "") },
            "Optional · sharper answers from the web") {
            editText(
                "Web search key (Tavily)", prefs.tavilyApiKey, "tvly-…", secret = true,
                help = "Optional. Faiza already searches with DuckDuckGo for free. A free Tavily key gives better summaries.",
                helpUrl = "https://app.tavily.com"
            ) { prefs.tavilyApiKey = it }
        }
        nav(R.id.rowBraveKey, R.id.subBraveKey, { keyStatus(prefs.braveApiKey, "") }, "Optional · backup search") {
            editText(
                "Brave Search key", prefs.braveApiKey, "BSA…", secret = true,
                help = "Optional. Used when the other searches don't answer. Free plan at brave.com/search/api.",
                helpUrl = "https://brave.com/search/api/"
            ) { prefs.braveApiKey = it }
        }
        nav(R.id.rowSerpKey, R.id.subSerpKey, { keyStatus(prefs.serpApiKey, "") }, "Optional · Google results") {
            editText(
                "SerpAPI key", prefs.serpApiKey, "key", secret = true,
                help = "Optional. Real Google results (with quick answers). Free plan at serpapi.com.",
                helpUrl = "https://serpapi.com/manage-api-key"
            ) { prefs.serpApiKey = it }
        }
        nav(R.id.rowImageToken, R.id.subImageToken, { keyStatus(prefs.pollinationsToken, "") },
            "Optional · faster picture creation") {
            editText(
                "Image token (Pollinations)", prefs.pollinationsToken, "token", secret = true,
                help = "Optional. Pictures work without it; a free Pollinations token makes them faster.",
                helpUrl = "https://auth.pollinations.ai"
            ) { prefs.pollinationsToken = it }
        }
    }

    // ===== Row helpers =====

    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

    /** A tappable row whose subtitle shows the current value (or [emptyText] when there is none). */
    private fun nav(rowId: Int, subId: Int, value: () -> String, emptyText: String, onClick: () -> Unit) {
        activity.findViewById<View>(rowId).setOnClickListener { onClick() }
        val subtitle = activity.findViewById<TextView>(subId)
        refreshers += {
            subtitle.text = value().ifBlank { emptyText }
            subtitle.visibility = if (subtitle.text.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    /** An on/off row; tapping anywhere on the row flips the switch. */
    private fun switch(rowId: Int, switchId: Int, get: () -> Boolean, set: (Boolean) -> Unit) {
        val sw = activity.findViewById<SwitchMaterial>(switchId)
        activity.findViewById<View>(rowId).setOnClickListener { sw.toggle() }
        refreshers += {
            sw.setOnCheckedChangeListener(null)
            sw.isChecked = get()
            sw.setOnCheckedChangeListener { _, on -> set(on) }
        }
    }

    private fun editText(
        title: String, current: String, hint: String,
        secret: Boolean = false, help: String? = null, helpUrl: String? = null,
        save: (String) -> Unit
    ) {
        val input = EditText(activity).apply {
            setText(current)
            setSelection(text.length)
            this.hint = hint
            textSize = 15f
            setTextColor(activity.getColor(R.color.text_primary))
            setHintTextColor(activity.getColor(R.color.text_muted))
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            inputType = InputType.TYPE_CLASS_TEXT or
                    (if (secret) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_FLAG_CAP_WORDS)
            isSingleLine = true
        }
        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val builder = AlertDialog.Builder(activity, R.style.Theme_FaizaAI_Dialog)
            .setTitle(title)
            .setView(form)
            .setPositiveButton("Save") { _, _ ->
                save(input.text.toString().trim())
                refresh()
            }
            .setNegativeButton("Cancel", null)
        help?.let { builder.setMessage(it) }
        helpUrl?.let { url ->
            builder.setNeutralButton("Open") { _, _ ->
                runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        }
        builder.show()
    }
}
