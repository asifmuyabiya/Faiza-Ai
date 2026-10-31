root = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main'
base = root + r'\java\com\faiza\ai'
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
rep('''    // ====== How she addresses the user ======''', '''    // ====== Avatar (living picture instead of the orb) ======
    var isAvatarMode: Boolean
        get() = prefs.getBoolean("avatar_mode", false)
        set(value) = prefs.edit().putBoolean("avatar_mode", value).apply()
    /** "lx,ly,rx,ry,mx,my" in 0..1 of the picture. */
    var avatarFace: String?
        get() = prefs.getString("avatar_face", null)
        set(value) = prefs.edit().putString("avatar_face", value).apply()

    // ====== How she addresses the user ======''')
save(p)

# ---- Floating bubble layout ----
p = root + r'\res\layout\floating_orb.xml'
load(p)
rep('''    <com.faiza.ai.ui.orb.OrbView
        android:id="@+id/orbArt"
        android:layout_width="76dp"
        android:layout_height="76dp" />''', '''    <com.faiza.ai.ui.orb.OrbView
        android:id="@+id/orbArt"
        android:layout_width="76dp"
        android:layout_height="76dp" />

    <com.faiza.ai.ui.orb.AvatarView
        android:id="@+id/avatarArt"
        android:layout_width="88dp"
        android:layout_height="88dp"
        android:visibility="gone" />''')
save(p)

# ---- Home hero layout ----
p = root + r'\res\layout\activity_dashboard.xml'
load(p)
rep('''                <com.faiza.ai.ui.orb.OrbView
                    android:id="@+id/orbHero"
                    android:layout_width="150dp"
                    android:layout_height="150dp"
                    android:layout_marginTop="12dp" />''', '''                <com.faiza.ai.ui.orb.OrbView
                    android:id="@+id/orbHero"
                    android:layout_width="150dp"
                    android:layout_height="150dp"
                    android:layout_marginTop="12dp" />

                <com.faiza.ai.ui.orb.AvatarView
                    android:id="@+id/avatarHero"
                    android:layout_width="170dp"
                    android:layout_height="170dp"
                    android:layout_marginTop="12dp"
                    android:visibility="gone" />''')
save(p)

# ---- Dashboard ----
p = base + r'\ui\DashboardActivity.kt'
load(p)
rep('''        orb.mode = if (isAiRunning) OrbMode.LISTENING else OrbMode.IDLE
    }''', '''        orb.mode = if (isAiRunning) OrbMode.LISTENING else OrbMode.IDLE
        // Avatar instead of the orb (living picture).
        val useAvatar = com.faiza.ai.ui.orb.AvatarStore.inUse(this)
        orb.visibility = if (useAvatar) android.view.View.GONE else android.view.View.VISIBLE
        binding.avatarHero.visibility = if (useAvatar) android.view.View.VISIBLE else android.view.View.GONE
        if (useAvatar) {
            binding.avatarHero.theme = OrbTheme.from(prefs.orbTheme)
            binding.avatarHero.reload()
            binding.avatarHero.mode = if (isAiRunning) OrbMode.LISTENING else OrbMode.IDLE
        }
    }''')
save(p)

# ---- Floating service ----
p = base + r'\service\FloatingOrbService.kt'
load(p)
rep('''    private lateinit var orbArt: OrbView''', '''    private lateinit var orbArt: OrbView
    private lateinit var avatarArt: com.faiza.ai.ui.orb.AvatarView''')
rep('''        orbArt = orbView.findViewById(R.id.orbArt)
''', '''        orbArt = orbView.findViewById(R.id.orbArt)
        avatarArt = orbView.findViewById(R.id.avatarArt)
        avatarArt.voiceLevel = { live?.voiceLevel() ?: 0f }
''')
rep('''        val px = (76 * resources.displayMetrics.density * prefs.orbSizePercent / 100f).toInt()
        orbArt.layoutParams = LinearLayout.LayoutParams(px, px)''', '''        val px = (76 * resources.displayMetrics.density * prefs.orbSizePercent / 100f).toInt()
        orbArt.layoutParams = LinearLayout.LayoutParams(px, px)
        // Living picture instead of the orb.
        val useAvatar = com.faiza.ai.ui.orb.AvatarStore.inUse(this)
        orbArt.visibility = if (useAvatar) View.GONE else View.VISIBLE
        avatarArt.visibility = if (useAvatar) View.VISIBLE else View.GONE
        if (useAvatar) {
            val apx = (px * 1.18f).toInt()
            avatarArt.layoutParams = LinearLayout.LayoutParams(apx, apx)
            avatarArt.theme = theme
            avatarArt.reload()
        }''')
rep('''        orbArt.setIconRes(iconRes)
        orbArt.mode = mode''', '''        orbArt.setIconRes(iconRes)
        orbArt.mode = mode
        if (::avatarArt.isInitialized) avatarArt.mode = mode''')
rep('''    /** Call speaker / soft-voice settings changed: apply them to the conversation now. */''', '''    /** The avatar picture / switch changed in Settings. */
    fun onAvatarChanged() {
        runCatching { applyOrbAppearance() }
    }

    /** Call speaker / soft-voice settings changed: apply them to the conversation now. */''')
save(p)

# ---- Manifest ----
p = root + r'\AndroidManifest.xml'
load(p)
rep('''        <activity android:name=".ui.SecretSettingsActivity"''', '''        <activity android:name=".ui.AvatarSetupActivity"
            android:theme="@style/Theme.FaizaAI" />
        <activity android:name=".ui.SecretSettingsActivity"''')
save(p)
print("ok")
