M = r"C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main"
J = M + r"\java\com\faiza\ai"


def patch(p, pairs):
    s = open(p, encoding="utf-8").read()
    for old, new in pairs:
        assert s.count(old) == 1, p + " NOT UNIQUE/FOUND: " + repr(old[:90])
        s = s.replace(old, new, 1)
    open(p, "w", encoding="utf-8").write(s)


patch(J + r"\ai\FaizaAgent.kt", [
    ("""        skills += com.faiza.ai.ai.routines.RoutineCenter.skills().filter { it.id !in disabled }""",
     """        skills += com.faiza.ai.ai.routines.RoutineCenter.skills().filter { it.id !in disabled }
        skills += com.faiza.ai.ai.files.FileSkills.skills().filter { it.id !in disabled }
        skills += com.faiza.ai.ai.skills.ExtraSkills.skills().filter { it.id !in disabled }"""),
    ("""            - TIME-BASED: "remind me at 8\"""", """            - FILES: "find / where is my … file" → find_files; read or summarize → read_file; "organize my Downloads" →
              organize_folder (tell the plan, then confirmed=true); "make a CSV / note of …" → create_file; "send this
              file to X" → share_file (WhatsApp needs a yes first). Number 0 = the file you just created.
            - SETTINGS: brightness, Do Not Disturb, Wi-Fi, Bluetooth, airplane mode… → phone_settings.
              "Turn everything off and lock the phone" → switch them off one by one, then phone_control lock_screen.
            - "When I say X, do Y" → teach_command. YouTube title/description/SEO → youtube_video_details first.
            - TIME-BASED: "remind me at 8\""""),
])

patch(J + r"\ui\SkillsActivity.kt", [
    ("""(BuiltInSkills.all + com.faiza.ai.ai.web.WebSkills.skills() + com.faiza.ai.ai.routines.RoutineCenter.skills())""",
     """(BuiltInSkills.all + com.faiza.ai.ai.web.WebSkills.skills() + com.faiza.ai.ai.routines.RoutineCenter.skills() +
                com.faiza.ai.ai.files.FileSkills.skills() + com.faiza.ai.ai.skills.ExtraSkills.skills())"""),
])

# Share any file the user picks (FileProvider) — external storage root.
open(M + r"\res\xml\file_paths.xml", "w", encoding="utf-8").write("""<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-files-path name="pictures" path="Pictures/" />
    <external-path name="storage" path="." />
</paths>
""")

patch(M + r"\AndroidManifest.xml", [
    ("""    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="29" />""", """    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="29" />
    <!-- File assistant: find, read, organize and send any file (sideloaded app, user grants it). -->
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
        tools:ignore="ScopedStorage" />
    <!-- Phone settings: brightness / auto-rotate, and Do Not Disturb -->
    <uses-permission android:name="android.permission.WRITE_SETTINGS"
        tools:ignore="ProtectedPermissions" />
    <uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />"""),
    ("""        android:usesCleartextTraffic="true\"""", """        android:usesCleartextTraffic="true"
        android:requestLegacyExternalStorage="true\""""),
])

patch(J + r"\util\PermissionHelper.kt", [
    ("""            // Location (SOS map link)""", """            // File assistant (STONIC video): find, read, organize and send files
            PermissionItem(
                id = "files",
                name = "All files access",
                description = "Lets Faiza find, read, organize and send your files (PDFs, photos, documents). " +
                        "Nothing leaves the phone unless you ask her to send it.",
                icon = "🗂️",
                isGranted = { com.faiza.ai.ai.files.FileSkills.hasAccess() },
                request = { activity ->
                    if (Build.VERSION.SDK_INT >= 30) {
                        runCatching {
                            activity.startActivity(
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${activity.packageName}"))
                            )
                        }.onFailure { activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                    } else {
                        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_CODE_PERMISSIONS)
                    }
                },
                isRequired = false
            ),

            // Phone settings: brightness / auto-rotate
            PermissionItem(
                id = "write_settings",
                name = "Change system settings",
                description = "Lets Faiza set the screen brightness and auto-rotate when you ask.",
                icon = "⚙️",
                isGranted = { Settings.System.canWrite(context) },
                request = { activity ->
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${activity.packageName}")))
                },
                isRequired = false
            ),

            // Do Not Disturb
            PermissionItem(
                id = "dnd",
                name = "Do Not Disturb access",
                description = "Lets Faiza turn Do Not Disturb or silent mode on and off when you ask.",
                icon = "🌙",
                isGranted = {
                    context.getSystemService(android.app.NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
                },
                request = { activity -> activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                isRequired = false
            ),

            // Location (SOS map link)"""),
])
print("stonic features wired")
