import os
main = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai'


def edit(p, a, b):
    p = os.path.join(main, *p.split('/'))
    s = open(p, encoding='utf-8').read()
    assert s.count(a) == 1, (p, a[:70], s.count(a))
    open(p, 'w', encoding='utf-8').write(s.replace(a, b))


p = 'ai/memory/BackupManager.kt'
edit(p, '''        val scheduledTasks: List<ScheduledTask>? = null,
        val watches: List<Watch>? = null
    )''', '''        val scheduledTasks: List<ScheduledTask>? = null,
        val watches: List<Watch>? = null,
        /** The money book (খাতা). */
        val khata: List<com.faiza.ai.ai.khata.Khata.Entry>? = null,
        /** Medicine courses (their reminders are in [scheduledTasks]). */
        val medicineJson: String? = null,
        val extras: Extras? = null
    )

    /** Settings added later: prayer, gaming, driving, calls. All optional so old backups still load. */
    data class Extras(
        val userAddress: String? = null,
        val busyReplyText: String? = null,
        val prayerMethod: String? = null,
        val prayerHanafi: Boolean? = null,
        val hijriAdjust: Int? = null,
        val azanReminders: Boolean? = null,
        val azanBeforeMin: Int? = null,
        val ramadanReminders: Boolean? = null,
        val prayerLat: Float? = null,
        val prayerLng: Float? = null,
        val gamingTalk: String? = null,
        val gamingJokes: Boolean? = null,
        val gamingAsk: Boolean? = null,
        val gamingAutoStart: Boolean? = null,
        val gamingEchoGuard: Boolean? = null,
        val homeAddress: String? = null,
        val workAddress: String? = null,
        val drivingSpeedLimit: Int? = null,
        val smsMoneyLog: Boolean? = null
    )''')
edit(p, '''            scheduledTasks = Routines.loadTasks { prefs.routinesJson },
            watches = Routines.loadWatches { prefs.watchesJson }
        )
    )''', '''            scheduledTasks = Routines.loadTasks { prefs.routinesJson },
            watches = Routines.loadWatches { prefs.watchesJson },
            khata = runCatching { com.faiza.ai.ai.khata.Khata.all(com.faiza.ai.FaizaApp.instance) }.getOrNull(),
            medicineJson = prefs.medicineJson,
            extras = Extras(
                userAddress = prefs.userAddress, busyReplyText = prefs.busyReplyText,
                prayerMethod = prefs.prayerMethod, prayerHanafi = prefs.prayerHanafi, hijriAdjust = prefs.hijriAdjust,
                azanReminders = prefs.azanReminders, azanBeforeMin = prefs.azanBeforeMin, ramadanReminders = prefs.ramadanReminders,
                prayerLat = prefs.prayerLat, prayerLng = prefs.prayerLng,
                gamingTalk = prefs.gamingTalk, gamingJokes = prefs.gamingJokes, gamingAsk = prefs.gamingAsk,
                gamingAutoStart = prefs.gamingAutoStart, gamingEchoGuard = prefs.gamingEchoGuard,
                homeAddress = prefs.homeAddress, workAddress = prefs.workAddress, drivingSpeedLimit = prefs.drivingSpeedLimit,
                smsMoneyLog = prefs.smsMoneyLog
            )
        )
    )''')
edit(p, '''            contacts = b.contacts?.filter { it != null && it.name != null && it.number != null && it.number.any(Char::isDigit) }
        )''', '''            contacts = b.contacts?.filter { it != null && it.name != null && it.number != null && it.number.any(Char::isDigit) },
            khata = b.khata?.filter { it != null && it.type != null && it.amount > 0 && it.id != null }
        )''')
edit(p, '''        // Restored alarms must be set again.''', '''        b.khata?.let { runCatching { com.faiza.ai.ai.khata.Khata.replaceAll(com.faiza.ai.FaizaApp.instance, it) } }
        b.medicineJson?.let { prefs.medicineJson = it }
        b.extras?.let { x ->
            x.userAddress?.let { prefs.userAddress = it }
            x.busyReplyText?.let { prefs.busyReplyText = it }
            x.prayerMethod?.let { prefs.prayerMethod = it }
            x.prayerHanafi?.let { prefs.prayerHanafi = it }
            x.hijriAdjust?.takeIf { it in -2..2 }?.let { prefs.hijriAdjust = it }
            x.azanReminders?.let { prefs.azanReminders = it }
            x.azanBeforeMin?.takeIf { it in 0..30 }?.let { prefs.azanBeforeMin = it }
            x.ramadanReminders?.let { prefs.ramadanReminders = it }
            x.prayerLat?.let { prefs.prayerLat = it }
            x.prayerLng?.let { prefs.prayerLng = it }
            x.gamingTalk?.let { prefs.gamingTalk = it }
            x.gamingJokes?.let { prefs.gamingJokes = it }
            x.gamingAsk?.let { prefs.gamingAsk = it }
            x.gamingAutoStart?.let { prefs.gamingAutoStart = it }
            x.gamingEchoGuard?.let { prefs.gamingEchoGuard = it }
            x.homeAddress?.let { prefs.homeAddress = it }
            x.workAddress?.let { prefs.workAddress = it }
            x.drivingSpeedLimit?.takeIf { it in 0..200 }?.let { prefs.drivingSpeedLimit = it }
            x.smsMoneyLog?.let { prefs.smsMoneyLog = it }
        }
        runCatching { com.faiza.ai.ai.prayer.PrayerCenter.schedule(com.faiza.ai.FaizaApp.instance) }
        // Restored alarms must be set again.''')

edit('ai/khata/Khata.kt', '''    fun add(ctx: Context, e: Entry) = save(ctx, all(ctx) + e)''', '''    fun add(ctx: Context, e: Entry) = save(ctx, all(ctx) + e)
    /** Restore from a backup. */
    fun replaceAll(ctx: Context, list: List<Entry>) = save(ctx, list)''')
print('ok')
