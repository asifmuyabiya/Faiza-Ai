package com.faiza.ai.util

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Helper to check and request all permissions Faiza AI needs.
 * Guides user through each one with explanations.
 */
class PermissionHelper(private val context: Context) {

    companion object {
        const val REQUEST_CODE_PERMISSIONS = 1001
        const val REQUEST_CODE_OVERLAY = 1002
        const val REQUEST_CODE_BATTERY = 1003
    }

    /**
     * All permission items the app needs, in order
     */
    data class PermissionItem(
        val id: String,
        val name: String,
        val description: String,
        val icon: String,
        val isGranted: () -> Boolean,
        val request: (Activity) -> Unit,
        val isRequired: Boolean = true
    )

    fun getAllPermissions(): List<PermissionItem> {
        return listOf(
            // 1. Overlay (Floating Orb)
            PermissionItem(
                id = "overlay",
                name = "Display Over Other Apps",
                description = "Shows the floating orb on your screen so Faiza AI is always accessible.",
                icon = "🔮",
                isGranted = { Settings.canDrawOverlays(context) },
                request = { activity ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    activity.startActivityForResult(intent, REQUEST_CODE_OVERLAY)
                }
            ),

            // 2. Microphone (Voice Commands)
            PermissionItem(
                id = "microphone",
                name = "Microphone Access",
                description = "Lets Faiza AI hear your voice commands and wake word \"Hey Faiza\".",
                icon = "🎤",
                isGranted = {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                },
                request = { activity ->
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.RECORD_AUDIO),
                        REQUEST_CODE_PERMISSIONS
                    )
                }
            ),

            // 3. Phone (Make/Answer Calls)
            PermissionItem(
                id = "phone",
                name = "Phone Access",
                description = "Allows Faiza AI to make and answer calls for you when you ask.",
                icon = "📞",
                isGranted = {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                            PackageManager.PERMISSION_GRANTED
                },
                request = { activity ->
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(
                            Manifest.permission.CALL_PHONE,
                            Manifest.permission.READ_PHONE_STATE
                        ),
                        REQUEST_CODE_PERMISSIONS
                    )
                }
            ),

            // 4. Contacts
            PermissionItem(
                id = "contacts",
                name = "Contacts Access",
                description = "Lets Faiza AI find contacts when you say \"Call Mom\" or \"Message Ahmed\".",
                icon = "👥",
                isGranted = {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                            PackageManager.PERMISSION_GRANTED
                },
                request = { activity ->
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.READ_CONTACTS),
                        REQUEST_CODE_PERMISSIONS
                    )
                }
            ),

            // 5. SMS
            PermissionItem(
                id = "sms",
                name = "SMS Access",
                description = "Allows Faiza AI to send and read text messages for you.",
                icon = "💬",
                isGranted = {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                            PackageManager.PERMISSION_GRANTED
                },
                request = { activity ->
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(
                            Manifest.permission.SEND_SMS,
                            Manifest.permission.READ_SMS
                        ),
                        REQUEST_CODE_PERMISSIONS
                    )
                }
            ),

            // 6. Notifications
            PermissionItem(
                id = "notifications",
                name = "Notification Access",
                description = "Lets Faiza AI read and announce your notifications.",
                icon = "🔔",
                isGranted = {
                    NotificationManagerCompat.getEnabledListenerPackages(context)
                        .contains(context.packageName)
                },
                request = { activity ->
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    activity.startActivity(intent)
                }
            ),

            // 7. Accessibility Service
            PermissionItem(
                id = "accessibility",
                name = "Accessibility Service",
                description = "Core permission that lets Faiza AI control your phone — tap buttons, read screens, type text, and navigate apps.",
                icon = "♿",
                isGranted = { isAccessibilityServiceEnabled() },
                request = { activity ->
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    activity.startActivity(intent)
                }
            ),

            // 8. Battery Optimization
            PermissionItem(
                id = "battery",
                name = "Battery Optimization",
                description = "Prevents Android from killing Faiza AI in the background. Essential for \"Hey Faiza\" wake word.",
                icon = "🔋",
                isGranted = { isBatteryOptimized() },
                request = { activity ->
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    activity.startActivity(intent)
                },
                isRequired = true
            ),

            // 9. Camera (Optional - for Vision)
            PermissionItem(
                id = "camera",
                name = "Camera Access",
                description = "Lets Faiza AI use your camera for visual understanding. You can skip this.",
                icon = "📷",
                isGranted = {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                },
                request = { activity ->
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.CAMERA),
                        REQUEST_CODE_PERMISSIONS
                    )
                },
                isRequired = false
            ),

            // Default assistant (video 7:56): hold power / home to call Faiza
            PermissionItem(
                id = "assistant",
                name = "Default assistant",
                description = "Hold the power or home button to call Faiza, like Google Assistant. In the next " +
                        "screen choose \"Digital assistant app\" → Faiza AI.",
                icon = "🎙️",
                isGranted = { com.faiza.ai.assist.AssistLauncher.isDefaultAssistant(context) },
                request = { activity -> com.faiza.ai.assist.AssistLauncher.openAssistantSettings(activity) },
                isRequired = false
            ),

            // Answer & manage calls (MAYA): "pick up", "cut the call", "end it"
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

            // File assistant (STONIC video): find, read, organize and send files
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

            // Location (SOS map link)
            PermissionItem(
                id = "location",
                name = "Location",
                description = "Used by SOS (to send your location to your emergency contacts), the weather where you are, and finding places near you.",
                icon = "📍",
                isGranted = {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED
                },
                request = { activity ->
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        REQUEST_CODE_PERMISSIONS
                    )
                },
                isRequired = false
            ),

            // Call log (SOS: was the call answered?)
            PermissionItem(
                id = "call_log",
                name = "Call Log",
                description = "Lets SOS mode know if a call was answered, so it can retry or call the next person.",
                icon = "📒",
                isGranted = {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
                            PackageManager.PERMISSION_GRANTED
                },
                request = { activity ->
                    ActivityCompat.requestPermissions(
                        activity, arrayOf(Manifest.permission.READ_CALL_LOG), REQUEST_CODE_PERMISSIONS
                    )
                },
                isRequired = false
            ),

            // 10. Post Notifications (Android 13+)
            PermissionItem(
                id = "post_notifications",
                name = "Show Notifications",
                description = "Allows Faiza AI to show status notifications while running.",
                icon = "📣",
                isGranted = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    } else true
                },
                request = { activity ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ActivityCompat.requestPermissions(
                            activity,
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            REQUEST_CODE_PERMISSIONS
                        )
                    }
                }
            )
        )
    }

    /**
     * Check if accessibility service is enabled for our app
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName
        }
    }

    /**
     * Check if battery optimization is disabled (we want it disabled)
     */
    private fun isBatteryOptimized(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Get count of granted permissions
     */
    fun getGrantedCount(): Int {
        return getAllPermissions().count { it.isGranted() }
    }

    /**
     * Get total required permissions
     */
    fun getTotalRequired(): Int {
        return getAllPermissions().count { it.isRequired }
    }

    /**
     * Check if all required permissions are granted
     */
    fun areAllRequiredGranted(): Boolean {
        return getAllPermissions().filter { it.isRequired }.all { it.isGranted() }
    }

    /**
     * Get the next permission that needs to be granted
     */
    fun getNextRequired(): PermissionItem? {
        return getAllPermissions().firstOrNull { it.isRequired && !it.isGranted() }
    }
}
