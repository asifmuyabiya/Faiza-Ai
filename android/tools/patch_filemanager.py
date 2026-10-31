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


p = base + r'\ai\files\FileManager.kt'
load(p)
rep('''        if (!f.exists() || FileOps.isProtected(rel(f.parentFile ?: f)) && rel(f.parentFile ?: f) != "DCIM/Camera") return false''',
    '''        if (!f.exists()) return false
        val parent = rel(f.parentFile ?: f).lowercase()
        if (parent.startsWith("android/data") || parent.startsWith("android/obb")) return false''')
rep('''    @Suppress("unused")
    private fun isInstalled(ctx: Context, pkg: String) =
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false) && PackageManager.GET_META_DATA >= 0
''', '')
rep('''import android.content.pm.PackageManager
''', '')
rep('''    private fun folder(name: String?): File {''', '''    internal fun folder(name: String?): File {''')
save(p)

p = base + r'\ai\files\FileSkills.kt'
load(p)
rep('''    private fun fileByNumber(n: Int): File =''', '''    internal fun fileByNumber(n: Int): File =''')
rep('''                ToolParam("folder", ParamType.STRING, "Which folder", enum = listOf("downloads", "documents")),''',
    '''                ToolParam("folder", ParamType.STRING, "downloads, documents, bluetooth, telegram, or a path like Documents/Office"),''')
rep('''        val dir = File(root, if (folderName == "documents") "Documents" else "Download")''',
    '''        val dir = FileManager.folder(folderName)''')
save(p)

p = base + r'\ai\FaizaAgent.kt'
load(p)
rep('''        skills += com.faiza.ai.ai.skills.TaskPlan.skills()''', '''        skills += com.faiza.ai.ai.skills.TaskPlan.skills()
        skills += com.faiza.ai.ai.files.FileManager.skills().filter { it.id !in disabled }''')
rep('''            - HELPERS (voice conversation):''', '''            - FILES & APPS: "জায়গা কিসে খাচ্ছে / storage" → storage_report. "Organise <folder>" → organize_folder (any
              folder; plan first, then confirmed=true; undo_organize puts back). Duplicates → find_duplicates then
              bin_duplicates. Find a file → find_files, then move_file / rename_file / share_file / bin_files by its
              number. "Delete" always means Faiza Bin (restorable) — restore_from_bin brings files back. Apps:
              list_apps, app_settings (then read_screen + tap to clear cache, change permissions…), uninstall_app.
              Ask once before binning or uninstalling; never touch Android/data or system folders.
            - HELPERS (voice conversation):''')
save(p)

p = base + r'\ai\skills\TaskLanes.kt'
load(p)
rep('''        "share_file", "run_macro", "phone_settings", "take_photo", "web_search", "set_alarm", "set_timer", "open_whatsapp_chat"''',
    '''        "share_file", "run_macro", "phone_settings", "take_photo", "web_search", "set_alarm", "set_timer", "open_whatsapp_chat",
        "app_settings", "uninstall_app"''')
rep('''        "youtube_channel_stats", "youtube_video_details", "find_files", "organize_folder", "create_file"''',
    '''        "youtube_channel_stats", "youtube_video_details", "find_files", "organize_folder", "create_file",
        "storage_report", "find_duplicates", "bin_duplicates", "move_file", "rename_file", "bin_files", "restore_from_bin"''')
save(p)

p = root + r'\AndroidManifest.xml'
load(p)
rep('''    <uses-permission android:name="android.permission.READ_CALL_LOG" />''', '''    <uses-permission android:name="android.permission.READ_CALL_LOG" />
    <!-- "Uninstall <app>": opens Android's own uninstall confirmation -->
    <uses-permission android:name="android.permission.REQUEST_DELETE_PACKAGES" />''')
save(p)
print("ok")
