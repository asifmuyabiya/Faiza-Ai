import os
main = os.path.join(os.getcwd(), 'app', 'src', 'main')
def edit(p, a, b):
    p = os.path.join(main, *p.split('/')); s = open(p, encoding='utf-8').read()
    assert s.count(a) == 1, (p, a[:60], s.count(a)); open(p, 'w', encoding='utf-8').write(s.replace(a, b))
J = 'java/com/faiza/ai/'
edit(J + 'util/PrefsManager.kt', '''    // Prayer
''', '''    /** Log bKash / Nagad / Rocket transaction SMS into the money book. */
    var smsMoneyLog: Boolean
        get() = prefs.getBoolean("sms_money_log", false)
        set(value) = prefs.edit().putBoolean("sms_money_log", value).apply()

    // Prayer
''')
edit('AndroidManifest.xml', '''    <uses-permission android:name="android.permission.READ_SMS" />''', '''    <uses-permission android:name="android.permission.READ_SMS" />
    <uses-permission android:name="android.permission.RECEIVE_SMS" />''')
edit('AndroidManifest.xml', '''        <activity android:name=".ui.KhataActivity"''', '''        <receiver
            android:name=".ai.khata.MoneySmsReceiver"
            android:exported="true"
            android:permission="android.permission.BROADCAST_SMS">
            <intent-filter>
                <action android:name="android.provider.Telephony.SMS_RECEIVED" />
            </intent-filter>
        </receiver>
        <activity android:name=".ui.KhataActivity"''')
p = J + 'ui/KhataActivity.kt'
edit(p, '''        val entries = Khata.all(this)
        // Period chips.''', '''        // bKash / Nagad SMS → book.
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(8), dp(8)); setBackgroundResource(R.drawable.bg_input)
            addView(TextView(context).apply {
                text = "💸 bKash / Nagad / Rocket SMS থেকে অটো হিসাব\n(ফাইজা শুধু পড়ে, কখনো টাকা পাঠায় না)"
                textSize = 14f; setTextColor(getColor(R.color.text_primary)); setLineSpacing(0f, 1.25f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(androidx.appcompat.widget.SwitchCompat(context).apply {
                isChecked = prefs.smsMoneyLog
                setOnCheckedChangeListener { _, on ->
                    if (!on) { prefs.smsMoneyLog = false; return@setOnCheckedChangeListener }
                    val need = arrayOf(android.Manifest.permission.RECEIVE_SMS, android.Manifest.permission.READ_SMS)
                    if (need.all { checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) enableSms()
                    else smsPermission.launch(need)
                }
            })
        }, lp(top = 10))

        val entries = Khata.all(this)
        // Period chips.''')
edit(p, '''    private var which = "month"
''', '''    private var which = "month"
    private val prefs get() = com.faiza.ai.FaizaApp.instance.prefs

    private val smsPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) enableSms() else { toast("SMS পড়ার অনুমতি দরকার"); render() }
    }

    /** Turn on and bring in the last 30 days. */
    private fun enableSms() {
        prefs.smsMoneyLog = true
        Thread {
            val n = runCatching { com.faiza.ai.ai.khata.MoneySms.importRecent(this, 30) }.getOrDefault(0)
            runOnUiThread { toast("গত ৩০ দিনের $n টা লেনদেন খাতায় যোগ হয়েছে"); render() }
        }.start()
    }
''')
print('ok')
