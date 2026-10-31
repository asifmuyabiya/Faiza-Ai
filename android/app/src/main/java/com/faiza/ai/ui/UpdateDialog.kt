package com.faiza.ai.ui

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.faiza.ai.R
import com.faiza.ai.util.UpdateChecker

/** "Version X is available" with release notes and a download button (video 7:15). */
object UpdateDialog {
    fun show(activity: AppCompatActivity, info: UpdateChecker.UpdateInfo) {
        val required = UpdateChecker.isRequired(UpdateChecker.currentVersionCode(activity), info)
        val notes = info.notes.ifBlank { "Improvements and fixes." }
        AlertDialog.Builder(activity, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Faiza ${info.versionName} is available")
            .setMessage(
                (if (required) "This update is required to keep using Faiza.\n\n" else "") +
                        "$notes\n\nAfter the download, open the file to install it. Your settings and memories stay."
            )
            .setPositiveButton("Download") { _, _ ->
                runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url))) }
            }
            .setNegativeButton("Later", null)
            .show()
    }
}
