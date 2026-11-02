package com.asif.familyhisab.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.asif.familyhisab.data.AppData
import com.asif.familyhisab.data.Backup
import com.asif.familyhisab.data.Calc
import com.asif.familyhisab.data.Store
import com.asif.familyhisab.data.ThemeMode
import com.asif.familyhisab.ui.Nav
import com.asif.familyhisab.ui.components.Card
import com.asif.familyhisab.ui.components.GradientBox
import com.asif.familyhisab.ui.components.Pill
import com.asif.familyhisab.ui.components.TopBar
import com.asif.familyhisab.ui.components.memberBrush
import com.asif.familyhisab.ui.theme.LocalExtra
import com.asif.familyhisab.util.LocalFmt
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

private fun writeUri(ctx: Context, uri: Uri, text: String): Boolean = runCatching {
    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    true
}.getOrDefault(false)

private fun share(ctx: Context, name: String, text: String, mime: String) {
    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, name).apply { writeText(text) }
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "শেয়ার করুন"))
}

@Composable
fun BackupScreen(d: AppData, store: Store, nav: Nav) {
    val f = LocalFmt.current
    val x = LocalExtra.current
    val ctx = LocalContext.current
    val today = LocalDate.now()
    var pendingCsv by remember { mutableStateOf("") }
    var imported by remember { mutableStateOf<AppData?>(null) }

    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            if (writeUri(ctx, uri, Backup.toJson(d))) { store.markBackedUp(); Toast.makeText(ctx, "✓ ব্যাকআপ সেভ হয়েছে", Toast.LENGTH_SHORT).show() }
            else Toast.makeText(ctx, "সেভ করা যায়নি", Toast.LENGTH_SHORT).show()
        }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) Toast.makeText(ctx, if (writeUri(ctx, uri, pendingCsv)) "✓ Excel ফাইল সেভ হয়েছে" else "সেভ করা যায়নি", Toast.LENGTH_SHORT).show()
    }
    val openJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            val data = text?.let { runCatching { Backup.fromJson(it) }.getOrNull() }
            if (data == null) Toast.makeText(ctx, "এটা সঠিক ব্যাকআপ ফাইল নয়", Toast.LENGTH_LONG).show() else imported = data
        }
    }

    val monthTx = Calc.inMonth(d.txns, YearMonth.now())
    val lastDays = if (d.settings.lastBackupAt == 0L) -1 else ((System.currentTimeMillis() - d.settings.lastBackupAt) / 86_400_000L).toInt()

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TopBar("এক্সপোর্ট / ইমপোর্ট", onBack = { nav.back() }) }
        item {
            GradientBox(x.hero) {
                Column(Modifier.padding(20.dp)) {
                    Text("💾 আপনার সব হিসাব", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${f.num(d.txns.size)}টি এন্ট্রি · ${f.num(d.members.size)} সদস্য · ${f.num(d.debts.size)}টি বাকি-ধার",
                        color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (lastDays < 0) "এখনো কোনো ব্যাকআপ নেওয়া হয়নি" else if (lastDays == 0) "আজকে ব্যাকআপ নেওয়া হয়েছে ✓" else "শেষ ব্যাকআপ ${f.num(lastDays)} দিন আগে",
                        color = Color.White, style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        item {
            Card(padding = 14.dp) {
                Text(
                    "ℹ️ সব ডাটা শুধু এই ফোনে থাকে — কোনো সার্ভার বা ইন্টারনেট লাগে না। অ্যাপ আনইনস্টল করলে বা ফোন বদলালে ডাটা হারাতে পারে, তাই মাঝে মাঝে ব্যাকআপ নিয়ে Google Drive বা WhatsApp-এ রেখে দিন।",
                    style = MaterialTheme.typography.bodySmall, color = x.muted,
                )
            }
        }
        item { Text("📤 এক্সপোর্ট", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
        item {
            ActionCard("🗂️", "পুরো ব্যাকআপ সেভ করুন", "JSON ফাইল — পরে ইমপোর্ট করে সব ফিরিয়ে আনা যাবে", 0xFF6C5CE7) {
                saveJson.launch("hisab-backup-$today.json")
            }
        }
        item {
            ActionCard("📲", "ব্যাকআপ শেয়ার করুন", "Drive, WhatsApp, Email-এ পাঠিয়ে রাখুন", 0xFF00B894) {
                share(ctx, "hisab-backup-$today.json", Backup.toJson(d), "application/json"); store.markBackedUp()
            }
        }
        item {
            ActionCard("📊", "এই মাসের হিসাব — Excel (CSV)", "${f.month(YearMonth.now())} · ${f.num(monthTx.size)}টি এন্ট্রি", 0xFF0984E3) {
                pendingCsv = Backup.toCsv(d, monthTx); saveCsv.launch("hisab-${YearMonth.now()}.csv")
            }
        }
        item {
            ActionCard("📑", "সব হিসাব — Excel (CSV)", "শুরু থেকে আজ পর্যন্ত সব এন্ট্রি", 0xFFF39C12) {
                pendingCsv = Backup.toCsv(d, d.txns); saveCsv.launch("hisab-all-$today.csv")
            }
        }
        item {
            ActionCard("💬", "এই মাসের হিসাব শেয়ার", "CSV ফাইল WhatsApp / Email-এ পাঠান", 0xFFE84393) {
                share(ctx, "hisab-${YearMonth.now()}.csv", Backup.toCsv(d, monthTx), "text/csv")
            }
        }
        item { Text("📥 ইমপোর্ট", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
        item {
            ActionCard("♻️", "ব্যাকআপ থেকে ফিরিয়ে আনুন", "আগে সেভ করা .json ফাইল বেছে নিন", 0xFFE17055) {
                openJson.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
            }
        }
    }

    imported?.let { inc ->
        AlertDialog(
            onDismissRequest = { imported = null },
            title = { Text("ব্যাকআপ পাওয়া গেছে ✓") },
            text = {
                Text(
                    "এই ফাইলে ${f.num(inc.txns.size)}টি এন্ট্রি, ${f.num(inc.members.size)} সদস্য ও ${f.num(inc.debts.size)}টি বাকি-ধার আছে।\n\n" +
                        "• মিলিয়ে যোগ: এখনকার ডাটা থাকবে, নতুনগুলো যোগ হবে।\n• সব বদলে দিন: এখনকার ডাটা মুছে ফাইলের ডাটা বসবে।",
                )
            },
            confirmButton = {
                TextButton(onClick = { store.mergeIn(inc); imported = null; Toast.makeText(ctx, "✓ মিলিয়ে যোগ হয়েছে", Toast.LENGTH_SHORT).show() }) { Text("মিলিয়ে যোগ") }
            },
            dismissButton = {
                TextButton(onClick = { store.replaceAll(inc); imported = null; Toast.makeText(ctx, "✓ ব্যাকআপ থেকে ফিরিয়ে আনা হয়েছে", Toast.LENGTH_SHORT).show() }) {
                    Text("সব বদলে দিন", color = x.expense)
                }
            },
        )
    }
}

@Composable
private fun ActionCard(emoji: String, title: String, sub: String, color: Long, onClick: () -> Unit) {
    Card(padding = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(memberBrush(color)), contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = LocalExtra.current.muted)
        }
    }
}

@Composable
fun SettingsScreen(d: AppData, store: Store, nav: Nav) {
    val f = LocalFmt.current
    val x = LocalExtra.current
    val ctx = LocalContext.current
    val s = d.settings
    var name by remember { mutableStateOf(s.ownerName) }
    var confirmReset by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TopBar("সেটিংস", onBack = { nav.back() }) }
        item {
            Card {
                Text("👤 আপনার নাম (হোমে দেখাবে)", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Field(name, { name = it; if (it.isNotBlank()) store.saveSettings(s.copy(ownerName = it.trim())) }, "নাম")
            }
        }
        item {
            Card {
                Text("🎨 থিম", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("অটো", s.theme == ThemeMode.SYSTEM, { store.saveSettings(s.copy(theme = ThemeMode.SYSTEM)) }, emoji = "📱")
                    Pill("লাইট", s.theme == ThemeMode.LIGHT, { store.saveSettings(s.copy(theme = ThemeMode.LIGHT)) }, emoji = "☀️")
                    Pill("ডার্ক", s.theme == ThemeMode.DARK, { store.saveSettings(s.copy(theme = ThemeMode.DARK)) }, emoji = "🌙")
                }
            }
        }
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🔢 বাংলা সংখ্যা", style = MaterialTheme.typography.titleSmall)
                        Text(if (s.banglaDigits) "উদাহরণ: ৳১২,৫০০" else "Example: ৳12,500", style = MaterialTheme.typography.bodySmall, color = x.muted)
                    }
                    Switch(s.banglaDigits, { store.saveSettings(s.copy(banglaDigits = it)) })
                }
            }
        }
        item {
            Card(onClick = { nav.go("categories") }) { Text("🏷️ ক্যাটাগরি সাজান ›", style = MaterialTheme.typography.titleSmall) }
        }
        item {
            Card(onClick = { nav.go("members") }) { Text("👨‍👩‍👦 সদস্য সাজান ›", style = MaterialTheme.typography.titleSmall) }
        }
        item {
            Card {
                Text("ℹ️ অ্যাপ সম্পর্কে", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "পরিবারের হিসাব · ভার্সন ১.০\nলগইন নেই, সার্ভার নেই, ইন্টারনেট লাগে না — সব হিসাব শুধু আপনার ফোনে।\nমোট ${f.num(d.txns.size)}টি এন্ট্রি সংরক্ষিত।",
                    style = MaterialTheme.typography.bodySmall, color = x.muted,
                )
            }
        }
        item {
            Card(onClick = { confirmReset = true }) {
                Text("🗑️ সব ডাটা মুছে ফেলুন", style = MaterialTheme.typography.titleSmall, color = x.expense)
                Text("আগে অবশ্যই ব্যাকআপ নিন", style = MaterialTheme.typography.bodySmall, color = x.muted)
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("সত্যিই সব মুছবেন?") },
            text = { Text("সব এন্ট্রি, বাকি-ধার, বাজেট, লক্ষ্য মুছে যাবে। এটা আর ফেরানো যাবে না (ব্যাকআপ না থাকলে)।") },
            confirmButton = {
                TextButton(onClick = { store.resetAll(); confirmReset = false; Toast.makeText(ctx, "সব মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show() }) {
                    Text("হ্যাঁ, মুছুন", color = x.expense)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("না") } },
        )
    }
}
