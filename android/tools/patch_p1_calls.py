import os
os.chdir(r"C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main")
J = "java/com/faiza/ai/"


def patch(p, pairs):
    s = open(p, encoding="utf-8").read()
    for a, b in pairs:
        assert s.count(a) == 1, p + " :: " + a[:80]
        s = s.replace(a, b)
    open(p, "w", encoding="utf-8").write(s)


patch(J + "ai/live/GeminiLiveSession.kt", [
 ("""    private fun startCallAudioRouting() {
        runCatching {
            previousAudioMode = audioManager.mode
            previousSpeakerOn = audioManager.isSpeakerphoneOn
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (forceSpeaker || !headsetConnected()) {
                if (Build.VERSION.SDK_INT >= 31) {
                    audioManager.availableCommunicationDevices
                        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                        ?.let { audioManager.setCommunicationDevice(it) }
                } else {
                    audioManager.isSpeakerphoneOn = true
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun stopCallAudioRouting() {
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) audioManager.clearCommunicationDevice()
            else audioManager.isSpeakerphoneOn = previousSpeakerOn
            audioManager.mode = previousAudioMode
        }
    }""", """    /** True while Faiza's voice and mic are routed to a Bluetooth headset. */
    private var routedBluetooth = false

    /**
     * Bluetooth headset connected (MAYA "Bluetooth" permission): talk through it, like a phone call.
     * Without one, audio stays exactly as it was (speaker + phone mic) - barge-in is tuned for that.
     */
    @Suppress("DEPRECATION")
    private fun startCallAudioRouting() {
        val headset = bluetoothHeadset() ?: return
        runCatching {
            previousAudioMode = audioManager.mode
            previousSpeakerOn = audioManager.isSpeakerphoneOn
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= 31) {
                audioManager.setCommunicationDevice(headset)
            } else {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
            }
            routedBluetooth = true
        }
    }

    @Suppress("DEPRECATION")
    private fun stopCallAudioRouting() {
        if (!routedBluetooth) return
        routedBluetooth = false
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                audioManager.clearCommunicationDevice()
            } else {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
            audioManager.mode = previousAudioMode
        }
    }

    private fun bluetoothHeadset(): AudioDeviceInfo? = runCatching {
        if (forceSpeaker) return@runCatching null
        if (Build.VERSION.SDK_INT >= 31) {
            audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
        } else {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        }
    }.getOrNull()"""),
])

patch("AndroidManifest.xml", [
 ("""    <uses-permission android:name="android.permission.READ_PHONE_STATE" />""",
  """    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <!-- Answer / reject / end calls by voice (MAYA "Answer & manage calls") -->
    <uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />
    <!-- Talk through a Bluetooth headset -->
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />"""),
])

patch(J + "util/PermissionHelper.kt", [
 ("""            // File assistant (STONIC video): find, read, organize and send files""",
  """            // Answer & manage calls (MAYA): "pick up", "cut the call", "end it"
            PermissionItem(
                id = "manage_calls",
                name = "Answer & manage calls",
                description = "Lets Faiza answer, reject or end calls when you ask - handy when your hands are busy.",
                icon = "📲",
                isGranted = {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) ==
                            PackageManager.PERMISSION_GRANTED
                },
                request = { activity ->
                    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ANSWER_PHONE_CALLS), REQUEST_CODE_PERMISSIONS)
                },
                isRequired = false
            ),

            // Bluetooth headset (MAYA)
            PermissionItem(
                id = "bluetooth",
                name = "Bluetooth",
                description = "So Faiza can talk and listen through your Bluetooth headset or earbuds.",
                icon = "🎧",
                isGranted = {
                    Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                            PackageManager.PERMISSION_GRANTED
                },
                request = { activity ->
                    if (Build.VERSION.SDK_INT >= 31) {
                        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_CODE_PERMISSIONS)
                    }
                },
                isRequired = false
            ),

            // File assistant (STONIC video): find, read, organize and send files"""),
])

patch(J + "ai/skills/ExtraSkills.kt", [
 ("""        Skill(
            "teach_command",""", """        Skill(
            "manage_call", "📲", "Answer & end calls", "\\"Pick up\\", \\"Cut the call\\"",
            "Control a phone call: answer a ringing call, reject it, end the current call, or switch the call to speaker.",
            listOf(ToolParam("action", ParamType.STRING, "What to do", enum = listOf("answer", "reject", "end", "speaker_on", "speaker_off")))
        ) { ctx, args -> manageCall(ctx, args.require("action")) },

        Skill(
            "teach_command","""),
 ("""    // ===== Phone settings =====""", """    // ===== Calls =====

    @android.annotation.SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun manageCall(ctx: Context, action: String): String {
        val granted = ctx.checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted && action in setOf("answer", "reject", "end")) {
            return "NEEDS PERMISSION: allow \\"Answer & manage calls\\" in Faiza AI → Settings → Phone access, then try again."
        }
        val tm = ctx.getSystemService(android.telecom.TelecomManager::class.java) ?: return "ERROR: no phone service"
        return when (action) {
            "answer" -> { tm.acceptRingingCall(); "OK: call answered" }
            "reject", "end" -> {
                if (Build.VERSION.SDK_INT < 28) return "ERROR: ending calls needs Android 9 or newer"
                if (tm.endCall()) "OK: call ${if (action == "reject") "rejected" else "ended"}" else "There's no call to end."
            }
            "speaker_on", "speaker_off" -> {
                val am = ctx.getSystemService(AudioManager::class.java) ?: return "ERROR: unavailable"
                am.isSpeakerphoneOn = action == "speaker_on"
                "OK: speaker ${if (action == "speaker_on") "on" else "off"}"
            }
            else -> "ERROR: unknown action $action"
        }
    }

    // ===== Phone settings ====="""),
])
print("calls + bluetooth done")
