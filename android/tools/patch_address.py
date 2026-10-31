base = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai'
s = ''


def load(p):
    global s
    s = open(p, encoding='utf-8').read()


def save(p):
    open(p, 'w', encoding='utf-8').write(s)


def rep(a, b, cnt=1):
    global s
    assert s.count(a) == cnt, (a[:70], s.count(a))
    s = s.replace(a, b)


# ---- Prefs ----
p = base + r'\util\PrefsManager.kt'
load(p)
rep('''    // ====== Closeness (Secret settings) ======''', '''    // ====== How she addresses the user ======
    /** "male", "female" or "" (not said). */
    var userGender: String
        get() = prefs.getString("user_gender", "") ?: ""
        set(value) = prefs.edit().putString("user_gender", value).apply()
    /** What she calls the user: "Boss", "Jaan", "Jaanu", "Sir", "Ma'am"… Empty = by their name. */
    var userAddress: String
        get() = prefs.getString("user_address", "") ?: ""
        set(value) = prefs.edit().putString("user_address", value.trim()).apply()

    // ====== Closeness (Secret settings) ======''')
save(p)

# ---- Settings rows ----
p = base + r'\ui\ProfileSections.kt'
load(p)
rep('''    private fun bindAboutYou() {''', '''    private fun bindAboutYou() {
        nav(R.id.rowYourName, R.id.subYourName, { prefs.userName.orEmpty().trim() }, "Add your name") {
            editName()
        }
        nav(R.id.rowGender, R.id.subGender, {
            when (prefs.userGender) { "male" -> "Male"; "female" -> "Female"; else -> "" }
        }, "Not set — tap to choose") { pickGender() }
        nav(R.id.rowAddress, R.id.subAddress, {
            prefs.userAddress.ifBlank { prefs.userName?.trim()?.takeIf { it.isNotEmpty() }?.let { "By your name ($it)" } ?: "" }
        }, "By your name") { pickAddress() }''')
rep('''    // ===== About you =====''', '''    // ===== About you =====

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
    }''')
save(p)
print("ok")
