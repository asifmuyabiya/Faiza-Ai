package com.faiza.ai.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.contacts.FavoriteContact
import com.faiza.ai.ai.contacts.FavoriteContacts
import com.faiza.ai.ai.contacts.PhoneNumbers
import com.faiza.ai.ai.skills.BuiltInSkills
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * My people (video 24:26 + the owner's idea): name, up to 3 nicknames ("বউ", "Faiza"), phone and WhatsApp
 * numbers, VIP (always gets through a Life mode) and SOS. One-tap Call and WhatsApp on every card.
 */
class ContactsActivity : AppCompatActivity() {

    private val prefs get() = FaizaApp.instance.prefs

    /** The system contact picker grants access to just the chosen number — no Contacts permission needed. */
    private val pickContact = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) return@registerForActivityResult
        val picked = runCatching {
            contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) (c.getString(0).orEmpty() to c.getString(1).orEmpty()) else null }
        }.getOrNull()
        if (picked == null || picked.second.none { it.isDigit() }) {
            Toast.makeText(this, "Couldn't read that contact's number", Toast.LENGTH_SHORT).show()
        } else {
            edit(null, picked.first.ifBlank { picked.second }, picked.second)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnPickContact).setOnClickListener {
            runCatching {
                pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
            }.onFailure { Toast.makeText(this, "No contacts app found", Toast.LENGTH_SHORT).show() }
        }
        findViewById<View>(R.id.btnAddManual).setOnClickListener { edit(null) }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    // ===== Add / edit =====

    private fun edit(existing: FavoriteContact?, presetName: String = "", presetNumber: String = "") {
        val name = field("Name (e.g. Irin)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val nick1 = field("Nickname 1 (e.g. বউ)", InputType.TYPE_CLASS_TEXT)
        val nick2 = field("Nickname 2 (optional)", InputType.TYPE_CLASS_TEXT)
        val nick3 = field("Nickname 3 (optional)", InputType.TYPE_CLASS_TEXT)
        val phone = field("Phone number", InputType.TYPE_CLASS_PHONE)
        val wa = field("WhatsApp number (if different)", InputType.TYPE_CLASS_PHONE)
        val vip = SwitchMaterial(this).apply {
            text = "VIP — calls always ring, even in Driving or Sleep mode"
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
        }
        existing?.let { c ->
            name.setText(c.name)
            listOf(nick1, nick2, nick3).forEachIndexed { i, f -> f.setText(c.nicknames.getOrNull(i).orEmpty()) }
            phone.setText(c.number)
            wa.setText(c.whatsapp)
            vip.isChecked = c.vip
        } ?: run {
            name.setText(presetName)
            phone.setText(presetNumber)
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            listOf(name, nick1, nick2, nick3, phone, wa).forEachIndexed { i, f ->
                addView(f, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { if (i > 0) topMargin = dp(8) })
            }
            addView(vip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(12) })
        }
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle(if (existing == null) "Add a person" else "Edit ${existing.name}")
            .setView(android.widget.ScrollView(this).apply { addView(form) })
            .setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim()
                val num = phone.text.toString().trim()
                val waNum = wa.text.toString().trim()
                val nicks = listOf(nick1, nick2, nick3).map { it.text.toString().trim() }.filter { it.isNotEmpty() }.distinct()
                when {
                    n.isEmpty() -> toast("Please enter a name")
                    num.count { it.isDigit() } < 5 -> toast("Please enter a valid phone number")
                    waNum.isNotEmpty() && waNum.count { it.isDigit() } < 5 -> toast("Please check the WhatsApp number")
                    else -> save(existing, n, nicks, num, waNum, vip.isChecked)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun save(existing: FavoriteContact?, name: String, nicks: List<String>, number: String, whatsapp: String, vip: Boolean) {
        val list = FavoriteContacts.load(prefs)
        if (existing == null && list.any { PhoneNumbers.sameNumber(it.number, number) }) {
            toast("$name is already in My people")
            return
        }
        val updated = if (existing == null) {
            // New people become SOS contacts automatically while there is room.
            val sosRoom = list.count { it.sos } < FavoriteContacts.MAX_SOS
            list + FavoriteContact(name = name, number = number, sos = sosRoom, nicknames = nicks, whatsapp = whatsapp, vip = vip)
        } else {
            list.map { if (it.id == existing.id) it.copy(name = name, number = number, nicknames = nicks, whatsapp = whatsapp, vip = vip) else it }
        }
        FavoriteContacts.save(prefs, updated)
        render()
    }

    // ===== Actions =====

    private fun call(c: FavoriteContact) {
        val uri = Uri.parse("tel:" + Uri.encode(c.number))
        val canCall = checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        runCatching { startActivity(Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri)) }
    }

    /** Straight into their WhatsApp chat — no searching. */
    private fun openWhatsApp(c: FavoriteContact) {
        val digits = BuiltInSkills.internationalDigits(this, c.whatsappNumber)
        val link = Uri.parse("https://api.whatsapp.com/send?phone=$digits")
        val opened = listOf("com.whatsapp", "com.whatsapp.w4b").any { pkg ->
            try {
                startActivity(Intent(Intent.ACTION_VIEW, link).setPackage(pkg))
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
        if (!opened) toast("WhatsApp is not installed")
    }

    // ===== List =====

    private fun render() {
        val list = FavoriteContacts.load(prefs)
        val sosCount = FavoriteContacts.sosContacts(list).size
        val vipCount = list.count { it.vip }
        findViewById<TextView>(R.id.tvContactsHeader).text =
            if (list.isEmpty()) "My people" else "My people (${list.size}) · VIP: $vipCount · SOS: $sosCount"
        val container = findViewById<LinearLayout>(R.id.contactsList)
        container.removeAllViews()
        if (list.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "No one yet. Tap “+ Add a person”: give a name, nicknames like “বউ” or “Ammu”, and their numbers. " +
                        "Then just say “বউকে কল দাও”."
                textSize = 13f
                setTextColor(getColor(R.color.text_muted))
                setPadding(dp(16), dp(18), dp(16), dp(18))
            })
            return
        }
        list.forEachIndexed { i, c -> container.addView(card(list, i, c)) }
    }

    private fun card(list: List<FavoriteContact>, index: Int, c: FavoriteContact): View {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(10) }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(10), dp(8))
        }
        card.addView(body)

        // Header: avatar, name, nicknames, chips
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = c.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            gravity = Gravity.CENTER
            textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_avatar)
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        })
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
            addView(TextView(context).apply {
                text = c.name
                textSize = 16f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColor(R.color.text_primary))
            })
            if (c.nicknames.isNotEmpty()) addView(TextView(context).apply {
                text = "Also: " + c.nicknames.joinToString(", ")
                textSize = 13f
                setTextColor(getColor(R.color.primary_light))
            })
        })
        header.addView(chip("VIP", c.vip, R.drawable.bg_pill_success, R.color.success) {
            FavoriteContacts.save(prefs, list.map { if (it.id == c.id) it.copy(vip = !it.vip) else it })
            render()
        })
        header.addView(chip("SOS", c.sos, R.drawable.bg_pill_warning, R.color.danger) {
            if (!c.sos && list.count { it.sos } >= FavoriteContacts.MAX_SOS) {
                toast("SOS can have at most ${FavoriteContacts.MAX_SOS} people")
            } else {
                FavoriteContacts.save(prefs, list.map { if (it.id == c.id) it.copy(sos = !it.sos) else it })
                render()
            }
        })
        body.addView(header)

        // Numbers
        body.addView(TextView(this).apply {
            text = "📞 ${c.number}" + if (c.whatsapp.isNotBlank()) "   ·   WhatsApp ${c.whatsapp}" else "   ·   WhatsApp same"
            textSize = 13f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(dp(54), dp(4), 0, dp(6))
        })

        // Actions
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(46), 0, 0, 0)
        }
        actions.addView(action(R.drawable.ic_phone, "Call", R.color.tile_green) { call(c) })
        actions.addView(action(R.drawable.ic_chat, "WhatsApp", R.color.tile_green) { openWhatsApp(c) })
        actions.addView(action(R.drawable.ic_edit, "Edit", R.color.primary_light) { edit(c) })
        actions.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        actions.addView(iconButton(R.drawable.ic_arrow_back, "Move ${c.name} up", rotation = 90f, enabled = index > 0) {
            val m = list.toMutableList()
            val item = m.removeAt(index)
            m.add(index - 1, item)
            FavoriteContacts.save(prefs, m)
            render()
        })
        actions.addView(iconButton(R.drawable.ic_delete, "Delete ${c.name}", rotation = 0f, enabled = true) {
            AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
                .setTitle("Remove ${c.name}?")
                .setPositiveButton("Remove") { _, _ ->
                    FavoriteContacts.save(prefs, list.filter { it.id != c.id })
                    render()
                }
                .setNegativeButton("Cancel", null)
                .show()
        })
        body.addView(actions)
        return card
    }

    private fun chip(label: String, on: Boolean, onBg: Int, onColor: Int, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 11f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(9), dp(3), dp(9), dp(3))
        setBackgroundResource(if (on) onBg else R.drawable.bg_pill)
        setTextColor(getColor(if (on) onColor else R.color.text_muted))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { marginStart = dp(6) }
        setOnClickListener { onClick() }
    }

    private fun action(icon: Int, label: String, tint: Int, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(8), dp(12), dp(8))
        val ripple = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
        setBackgroundResource(ripple.resourceId)
        addView(ImageView(context).apply {
            setImageResource(icon)
            imageTintList = getColorStateList(tint)
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
        })
        addView(TextView(context).apply {
            text = label
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(getColor(R.color.text_primary))
            setPadding(dp(6), 0, 0, 0)
        })
        setOnClickListener { onClick() }
    }

    private fun iconButton(icon: Int, description: String, rotation: Float, enabled: Boolean, onClick: () -> Unit) =
        ImageView(this).apply {
            setImageResource(icon)
            this.rotation = rotation
            imageTintList = getColorStateList(if (enabled) R.color.text_secondary else R.color.stroke)
            contentDescription = description
            isEnabled = enabled
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            val ripple = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
            setBackgroundResource(ripple.resourceId)
            setOnClickListener { if (enabled) onClick() }
        }

    private fun field(hint: String, type: Int) = EditText(this).apply {
        this.hint = hint
        inputType = type
        textSize = 15f
        setTextColor(getColor(R.color.text_primary))
        setHintTextColor(getColor(R.color.text_muted))
        setBackgroundResource(R.drawable.bg_input)
        setPadding(dp(16), dp(12), dp(16), dp(12))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
