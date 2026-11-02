package com.asif.familyhisab.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asif.familyhisab.data.AppData
import com.asif.familyhisab.data.BazarItem
import com.asif.familyhisab.data.Budget
import com.asif.familyhisab.data.Calc
import com.asif.familyhisab.data.Category
import com.asif.familyhisab.data.Debt
import com.asif.familyhisab.data.DebtDir
import com.asif.familyhisab.data.DebtPayment
import com.asif.familyhisab.data.Defaults
import com.asif.familyhisab.data.FAMILY_ID
import com.asif.familyhisab.data.Goal
import com.asif.familyhisab.data.Store
import com.asif.familyhisab.data.TxType
import com.asif.familyhisab.ui.Nav
import com.asif.familyhisab.ui.Routes
import com.asif.familyhisab.ui.components.Card
import com.asif.familyhisab.ui.components.Donut
import com.asif.familyhisab.ui.components.EmojiBadge
import com.asif.familyhisab.ui.components.EmptyState
import com.asif.familyhisab.ui.components.GradientBox
import com.asif.familyhisab.ui.components.Pill
import com.asif.familyhisab.ui.components.ProgressBar
import com.asif.familyhisab.ui.components.RowSpaced
import com.asif.familyhisab.ui.components.TopBar
import com.asif.familyhisab.ui.components.budgetColor
import com.asif.familyhisab.ui.components.c
import com.asif.familyhisab.ui.components.memberBrush
import com.asif.familyhisab.ui.theme.LocalExtra
import com.asif.familyhisab.util.LocalFmt
import java.time.LocalDate
import java.time.YearMonth

private fun toExpr(p: Long): String = if (p <= 0) "" else if (p % 100 == 0L) (p / 100).toString() else "%.2f".format(p / 100.0)

// ======================= MORE =======================

private data class Tile(val emoji: String, val title: String, val sub: String, val color: Long, val route: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreScreen(d: AppData, nav: Nav) {
    val f = LocalFmt.current
    val debt = Calc.debtSummary(d.debts)
    val tiles = listOf(
        Tile("👨‍👩‍👦", "সদস্য", "${f.num(d.activeMembers.size)} জন", 0xFF6C5CE7, "members"),
        Tile("🎯", "বাজেট", if (d.budget.monthlyTotal > 0) f.money(d.budget.monthlyTotal) else "সেট করুন", 0xFF0984E3, "budget"),
        Tile("🤝", "বাকি-ধার", "পাবো ${f.money(debt.toGet)}", 0xFFE17055, "debts"),
        Tile("📝", "বাজার লিস্ট", "${f.num(d.shopping.count { !it.done })}টি কেনা বাকি", 0xFF00B894, "shopping"),
        Tile("🏦", "সঞ্চয় লক্ষ্য", "${f.num(d.goals.size)}টি লক্ষ্য", 0xFFE84393, "goals"),
        Tile("🏷️", "ক্যাটাগরি", "${f.num(d.categories.count { !it.archived })}টি খাত", 0xFFF39C12, "categories"),
        Tile("💾", "এক্সপোর্ট / ইমপোর্ট", "ব্যাকআপ ও Excel", 0xFF00CEC9, "backup"),
        Tile("⚙️", "সেটিংস", "থিম, সংখ্যা, নাম", 0xFF636E72, "settings"),
    )
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 130.dp)) {
        item {
            Text("আরও", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 6.dp))
            Spacer(Modifier.height(6.dp))
            GradientBox(com.asif.familyhisab.ui.theme.LocalExtra.current.hero) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🏡", fontSize = 40.sp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("পরিবারের হিসাব", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "মোট ${f.num(d.txns.size)}টি এন্ট্রি · সব ডাটা শুধু আপনার ফোনে",
                            color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        item {
            FlowRow(maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tiles.forEach { t ->
                    Card(Modifier.weight(1f), padding = 16.dp, onClick = { nav.go(t.route) }) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(memberBrush(t.color)),
                            contentAlignment = Alignment.Center,
                        ) { Text(t.emoji, fontSize = 24.sp) }
                        Spacer(Modifier.height(12.dp))
                        Text(t.title, style = MaterialTheme.typography.titleSmall)
                        Text(t.sub, style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ======================= BUDGET =======================

@Composable
fun BudgetScreen(d: AppData, store: Store, nav: Nav) {
    val f = LocalFmt.current
    val x = LocalExtra.current
    val ctx = LocalContext.current
    val monthTx = remember(d.txns) { Calc.inMonth(d.txns, YearMonth.now()) }
    val spent = Calc.totals(monthTx).expense
    val byMember = remember(monthTx) { Calc.expenseByMember(monthTx).toMap() }
    val byCat = remember(monthTx) { Calc.byCategory(monthTx).toMap() }

    var total by remember { mutableStateOf(toExpr(d.budget.monthlyTotal)) }
    val memberVals = remember { mutableStateMapOf<String, String>().apply { d.budget.perMember.forEach { (k, v) -> put(k, toExpr(v)) } } }
    val catVals = remember { mutableStateMapOf<String, String>().apply { d.budget.perCategory.forEach { (k, v) -> put(k, toExpr(v)) } } }

    fun save() {
        store.saveBudget(Budget(
            monthlyTotal = Calc.evalExpr(total) ?: 0,
            perMember = memberVals.mapValues { Calc.evalExpr(it.value) ?: 0 }.filterValues { it > 0 },
            perCategory = catVals.mapValues { Calc.evalExpr(it.value) ?: 0 }.filterValues { it > 0 },
        ))
        Toast.makeText(ctx, "✓ বাজেট সেভ হয়েছে", Toast.LENGTH_SHORT).show()
        nav.back()
    }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TopBar("মাসিক বাজেট", onBack = { nav.back() }) { TextButton(onClick = ::save) { Text("সেভ") } } }
        item {
            GradientBox(x.hero) {
                Column(Modifier.padding(20.dp)) {
                    Text("এই মাসে খরচ হয়েছে", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                    Text(f.money(spent), color = Color.White, style = MaterialTheme.typography.displaySmall)
                    val b = Calc.evalExpr(total) ?: 0
                    if (b > 0) {
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(Calc.percent(spent, b), Color.White, track = Color.White.copy(alpha = 0.2f))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (spent <= b) "আরও ${f.money(b - spent)} খরচ করা যাবে" else "বাজেটের চেয়ে ${f.money(spent - b)} বেশি খরচ!",
                            color = Color.White, style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
        item {
            Card {
                Text("💰 পুরো পরিবারের মাসিক বাজেট", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Field(total, { total = it }, "টাকা ৳", number = true)
            }
        }
        item {
            Card {
                Text("👨‍👩‍👦 সদস্য অনুযায়ী (ঐচ্ছিক)", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                d.activeMembers.forEach { m ->
                    BudgetLine(m.emoji, m.name, m.color, memberVals[m.id] ?: "", { memberVals[m.id] = it }, byMember[m.id] ?: 0)
                }
            }
        }
        item {
            Card {
                Text("🏷️ খাত অনুযায়ী (ঐচ্ছিক)", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                d.categories.filter { it.type == TxType.EXPENSE && !it.archived }.forEach { c ->
                    BudgetLine(c.emoji, c.name, c.color, catVals[c.id] ?: "", { catVals[c.id] = it }, byCat[c.id] ?: 0)
                }
            }
        }
    }
}

@Composable
private fun BudgetLine(emoji: String, name: String, color: Long, value: String, onChange: (String) -> Unit, spent: Long) {
    val f = LocalFmt.current
    val x = LocalExtra.current
    val b = Calc.evalExpr(value) ?: 0
    Column(Modifier.padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(emoji, color, 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text("খরচ ${f.money(spent)}", style = MaterialTheme.typography.labelSmall, color = x.muted)
            }
            OutlinedTextField(
                value, { v -> onChange(v.filter { it.isDigit() || it == '.' }) }, Modifier.width(120.dp),
                placeholder = { Text("৳ সীমা") }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            )
        }
        if (b > 0) {
            Spacer(Modifier.height(6.dp))
            val frac = Calc.percent(spent, b)
            ProgressBar(frac, budgetColor(frac, x.income, x.warn, x.expense), height = 6.dp)
        }
    }
}

// ======================= DEBTS =======================

@Composable
fun DebtsScreen(d: AppData, store: Store, nav: Nav) {
    val f = LocalFmt.current
    val x = LocalExtra.current
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var payFor by remember { mutableStateOf<Debt?>(null) }
    var editDebt by remember { mutableStateOf<Debt?>(null) }
    val sum = Calc.debtSummary(d.debts)
    val list = d.debts.filter {
        when (tab) {
            0 -> it.dir == DebtDir.GAVE && !it.settled
            1 -> it.dir == DebtDir.TOOK && !it.settled
            else -> it.settled
        }
    }.sortedByDescending { it.day }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TopBar("বাকি-ধার", onBack = { nav.back() }) { TextButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, null); Text(" নতুন") } } }
        item {
            RowSpaced {
                GradientBox(Brush.linearGradient(listOf(Color(0xFF00B894), Color(0xFF00A383))), Modifier.weight(1f), radius = 24.dp) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📥 পাবো", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
                        Text(f.money(sum.toGet), color = Color.White, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                        Text("অন্যরা আমাকে দেবে", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                    }
                }
                GradientBox(Brush.linearGradient(listOf(Color(0xFFFF7675), Color(0xFFE84393))), Modifier.weight(1f), radius = 24.dp) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📤 দেবো", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
                        Text(f.money(sum.toGive), color = Color.White, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                        Text("আমি অন্যকে দেবো", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item {
            Card(padding = 14.dp) {
                Text("💡 ধার-বাকির নিয়ম", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "ধার নেওয়া টাকা আয় নয়, ধার শোধ করাও খরচ নয় — তাই এগুলো আয়-খরচের হিসাবে যোগ হয় না। " +
                        "ধারের টাকা দিয়ে যা কিনবেন (বাজার, বিল) শুধু সেটাই খরচ হিসেবে লিখবেন। " +
                        "হোমে \"ধার-বাকি মিলিয়ে হাতে আছে\" লাইনে দেখবেন ধার নেওয়া/শোধ করা মিলিয়ে হাতে আসলে কত টাকা আছে।",
                    style = MaterialTheme.typography.bodySmall, color = x.muted,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("পাবো", tab == 0, { tab = 0 }, color = x.income)
                Pill("দেবো", tab == 1, { tab = 1 }, color = x.expense)
                Pill("শোধ হয়েছে", tab == 2, { tab = 2 })
            }
        }
        if (list.isEmpty()) item { Card { EmptyState("🤝", "এখানে কিছু নেই", "কাউকে টাকা দিলে বা কারো কাছ থেকে নিলে এখানে লিখে রাখুন") } }
        items(list, key = { it.id }) { debt ->
            val col = if (debt.dir == DebtDir.GAVE) x.income else x.expense
            Card(padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(col.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Text(debt.person.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = col)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(debt.person, style = MaterialTheme.typography.titleMedium)
                        Text(
                            f.date(LocalDate.ofEpochDay(debt.day)) + (if (debt.note.isNotBlank()) " · ${debt.note}" else ""),
                            style = MaterialTheme.typography.labelSmall, color = x.muted, maxLines = 1,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(f.money(debt.remaining), style = MaterialTheme.typography.titleMedium, color = col)
                        Text("মোট ${f.money(debt.amount)}", style = MaterialTheme.typography.labelSmall, color = x.muted)
                    }
                }
                debt.dueDay?.takeIf { !debt.settled }?.let { due ->
                    val left = due - LocalDate.now().toEpochDay()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "⏰ শোধের তারিখ ${f.date(LocalDate.ofEpochDay(due))} · " + when {
                            left < 0 -> "মেয়াদ ${f.num(-left)} দিন পার হয়ে গেছে"
                            left == 0L -> "আজই শেষ দিন"
                            else -> "আর ${f.num(left)} দিন বাকি"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (left <= 0) x.expense else if (left <= 3) x.warn else x.muted,
                    )
                }
                if (debt.paid > 0) {
                    Spacer(Modifier.height(10.dp))
                    ProgressBar(Calc.percent(debt.paid, debt.amount), col, height = 6.dp)
                    Text("${f.money(debt.paid)} শোধ হয়েছে (${f.num(debt.payments.size)} কিস্তি)", style = MaterialTheme.typography.labelSmall, color = x.muted)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!debt.settled) TextButton(onClick = { payFor = debt }) { Text(if (debt.dir == DebtDir.GAVE) "💵 ফেরত পেলাম" else "💵 শোধ করলাম") }
                    if (!debt.settled) TextButton(onClick = { store.saveDebt(debt.copy(payments = debt.payments + DebtPayment(debt.remaining, LocalDate.now().toEpochDay(), "পুরো শোধ"))) }) { Text("✓ সব শোধ") }
                    Spacer(Modifier.weight(1f))
                    if (debt.phone.isNotBlank()) IconButton(onClick = {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${debt.phone}"))) }
                    }) { Icon(Icons.Rounded.Call, "কল", tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = { editDebt = debt }) { Icon(Icons.Rounded.Edit, "এডিট", tint = x.muted) }
                }
            }
        }
    }

    if (adding || editDebt != null) {
        val ex = editDebt
        var person by remember(ex) { mutableStateOf(ex?.person ?: "") }
        var phone by remember(ex) { mutableStateOf(ex?.phone ?: "") }
        var amount by remember(ex) { mutableStateOf(toExpr(ex?.amount ?: 0)) }
        var note by remember(ex) { mutableStateOf(ex?.note ?: "") }
        var dir by remember(ex) { mutableStateOf(ex?.dir ?: if (tab == 1) DebtDir.TOOK else DebtDir.GAVE) }
        var given by remember(ex) { mutableStateOf(ex?.day ?: LocalDate.now().toEpochDay()) }
        var due by remember(ex) { mutableStateOf(ex?.dueDay) }
        var pick by remember(ex) { mutableStateOf<String?>(null) }
        FormDialog(
            title = if (ex == null) "নতুন বাকি / ধার" else "এডিট",
            onDismiss = { adding = false; editDebt = null },
            onSave = {
                val a = Calc.evalExpr(amount) ?: 0
                if (person.isBlank() || a <= 0) { Toast.makeText(ctx, "নাম ও টাকা লিখুন", Toast.LENGTH_SHORT).show(); return@FormDialog }
                store.saveDebt((ex ?: Debt(person = person, dir = dir, amount = a, day = LocalDate.now().toEpochDay()))
                    .copy(person = person.trim(), phone = phone.trim(), amount = a, note = note.trim(), dir = dir, day = given, dueDay = due))
                adding = false; editDebt = null
            },
            extraButton = if (ex != null) ({
                TextButton(onClick = { store.deleteDebt(ex.id); editDebt = null }) { Text("মুছুন", color = x.expense) }
            }) else null,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                Pill("📥 আমি দিলাম (পাবো)", dir == DebtDir.GAVE, { dir = DebtDir.GAVE }, color = x.income)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                Pill("📤 আমি নিলাম (দেবো)", dir == DebtDir.TOOK, { dir = DebtDir.TOOK }, color = x.expense)
            }
            Field(person, { person = it }, "কার সাথে (নাম / দোকান)")
            Field(phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, "মোবাইল নম্বর (ঐচ্ছিক)")
            Field(amount, { amount = it }, "টাকা ৳", number = true)
            Field(note, { note = it }, "নোট (ঐচ্ছিক)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("📅 " + f.date(LocalDate.ofEpochDay(given)), false, { pick = "given" })
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(due?.let { "⏰ শোধ: " + f.date(LocalDate.ofEpochDay(it)) } ?: "⏰ শোধের তারিখ (ঐচ্ছিক)", due != null, { pick = "due" })
                if (due != null) TextButton(onClick = { due = null }) { Text("বাদ") }
            }
        }
        pick?.let { which ->
            DayPickerDialog(if (which == "due") due ?: (LocalDate.now().toEpochDay() + 30) else given, onDismiss = { pick = null }) { picked ->
                if (which == "due") due = picked else given = picked
                pick = null
            }
        }
    }

    payFor?.let { debt ->
        var amt by remember(debt) { mutableStateOf("") }
        FormDialog(
            title = "${debt.person} — কিস্তি",
            onDismiss = { payFor = null },
            onSave = {
                val a = (Calc.evalExpr(amt) ?: 0).coerceAtMost(debt.remaining)
                if (a > 0) store.saveDebt(debt.copy(payments = debt.payments + DebtPayment(a, LocalDate.now().toEpochDay())))
                payFor = null
            },
        ) {
            Text("বাকি আছে ${f.money(debt.remaining)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp))
            Field(amt, { amt = it }, "কত টাকা ৳", number = true)
        }
    }
}

// ======================= SHOPPING LIST =======================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShoppingScreen(d: AppData, store: Store, nav: Nav) {
    val f = LocalFmt.current
    val x = LocalExtra.current
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    val pending = d.shopping.filter { !it.done }
    val done = d.shopping.filter { it.done }
    val have = d.shopping.map { it.name.lowercase() }.toSet()
    val sugg = remember(d.txns) { Calc.itemStats(d.txns).map { it.name } }.filter { it.lowercase() !in have }.take(14)

    fun add() {
        if (name.isNotBlank()) { store.addShop(name.trim(), qty.trim()); name = ""; qty = "" }
    }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { TopBar("বাজার লিস্ট", onBack = { nav.back() }) }
        item {
            Card(padding = 14.dp) {
                Text("কী কী কিনতে হবে লিখে রাখুন। কেনা হলে টিক দিন, তারপর এক ট্যাপে খরচে যোগ করুন।", style = MaterialTheme.typography.bodySmall, color = x.muted)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, Modifier.weight(1f), placeholder = { Text("আইটেম") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
                    OutlinedTextField(qty, { qty = it }, Modifier.width(90.dp), placeholder = { Text("পরিমাণ") }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = fieldColors())
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(com.asif.familyhisab.ui.theme.HeroColors)).clickable(onClick = ::add),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Add, "যোগ", tint = Color.White) }
                }
                if (sugg.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        sugg.forEach { s ->
                            Text(
                                "+ $s", style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { store.addShop(s, "") }.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
        if (d.shopping.isEmpty()) item { Card { EmptyState("📝", "লিস্ট খালি", "বাজারে যাওয়ার আগে কী কী লাগবে লিখে ফেলুন") } }
        if (pending.isNotEmpty()) item { Text("🛍️ কিনতে হবে (${f.num(pending.size)})", style = MaterialTheme.typography.titleSmall) }
        items(pending, key = { it.id }) { ShopRow(it.name, it.qty, false, { store.toggleShop(it.id) }, { store.deleteShop(it.id) }) }
        if (done.isNotEmpty()) {
            item { Text("✅ কেনা হয়েছে (${f.num(done.size)})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
            items(done, key = { it.id }) { ShopRow(it.name, it.qty, true, { store.toggleShop(it.id) }, { store.deleteShop(it.id) }) }
            item {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(com.asif.familyhisab.ui.theme.HeroColors))
                        .clickable {
                            store.pendingItems = done.map { s ->
                                val q = s.qty.takeWhile { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 1.0
                                val unit = Defaults.units.firstOrNull { s.qty.contains(it) } ?: "কেজি"
                                BazarItem(s.name, q, unit, 0)
                            }
                            nav.go(Routes.add(member = FAMILY_ID, cat = "bazar", items = true))
                        },
                    contentAlignment = Alignment.Center,
                ) { Text("🧾 কেনা আইটেমগুলো খরচে যোগ করুন", color = Color.White, style = MaterialTheme.typography.titleSmall) }
                TextButton(onClick = { store.clearDoneShop() }, modifier = Modifier.fillMaxWidth()) { Text("কেনাগুলো লিস্ট থেকে মুছুন", color = x.muted) }
            }
        }
    }
}

@Composable
private fun ShopRow(name: String, qty: String, done: Boolean, onToggle: () -> Unit, onDelete: () -> Unit) {
    val x = LocalExtra.current
    Card(padding = 6.dp, onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggle) {
                Icon(
                    if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                    tint = if (done) x.income else MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                textDecoration = if (done) TextDecoration.LineThrough else null, color = if (done) x.muted else MaterialTheme.colorScheme.onSurface,
            )
            if (qty.isNotBlank()) Text(qty, style = MaterialTheme.typography.labelMedium, color = x.muted)
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "মুছুন", tint = x.muted) }
        }
    }
}

// ======================= GOALS =======================

@Composable
fun GoalsScreen(d: AppData, store: Store, nav: Nav) {
    val f = LocalFmt.current
    val x = LocalExtra.current
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<Goal?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deposit by remember { mutableStateOf<Goal?>(null) }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TopBar("সঞ্চয় লক্ষ্য", onBack = { nav.back() }) { TextButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, null); Text(" নতুন") } } }
        if (d.goals.isEmpty()) item { Card { EmptyState("🏦", "কোনো লক্ষ্য নেই", "যেমন: ঈদের কেনাকাটা, Ibad-এর স্কুল ফি, হজ্জ, নতুন ফ্রিজ…") } }
        items(d.goals, key = { it.id }) { g ->
            val frac = Calc.percent(g.saved, g.target).coerceAtMost(1f)
            Card(padding = 16.dp, onClick = { editing = g }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Donut(listOf(g.saved.coerceAtMost(g.target) to g.color.c(), (g.target - g.saved).coerceAtLeast(0) to Color.Transparent), Modifier.size(78.dp), 9.dp) {
                        Text(g.emoji, fontSize = 26.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.name, style = MaterialTheme.typography.titleMedium)
                        Text("${f.money(g.saved)} / ${f.money(g.target)}", style = MaterialTheme.typography.bodyMedium, color = x.muted)
                        Text(
                            if (g.saved >= g.target) "🎉 লক্ষ্য পূরণ হয়েছে!" else "আরও ${f.money(g.target - g.saved)} লাগবে · ${f.num(Math.round(frac * 100))}%",
                            style = MaterialTheme.typography.labelMedium, color = g.color.c(),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = { deposit = g }) { Text("➕ জমা / তোলা") }
                }
            }
        }
    }

    if (adding || editing != null) {
        val ex = editing
        var name by remember(ex) { mutableStateOf(ex?.name ?: "") }
        var target by remember(ex) { mutableStateOf(toExpr(ex?.target ?: 0)) }
        var emoji by remember(ex) { mutableStateOf(ex?.emoji ?: "🎯") }
        var color by remember(ex) { mutableStateOf(ex?.color ?: 0xFF00B894) }
        FormDialog(
            title = if (ex == null) "নতুন লক্ষ্য" else "লক্ষ্য এডিট",
            onDismiss = { adding = false; editing = null },
            onSave = {
                val t = Calc.evalExpr(target) ?: 0
                if (name.isBlank() || t <= 0) { Toast.makeText(ctx, "নাম ও টাকা লিখুন", Toast.LENGTH_SHORT).show(); return@FormDialog }
                store.saveGoal((ex ?: Goal(name = name, target = t)).copy(name = name.trim(), target = t, emoji = emoji, color = color))
                adding = false; editing = null
            },
            extraButton = if (ex != null) ({ TextButton(onClick = { store.deleteGoal(ex.id); editing = null }) { Text("মুছুন", color = x.expense) } }) else null,
        ) {
            Field(name, { name = it }, "লক্ষ্যের নাম")
            Field(target, { target = it }, "কত টাকা দরকার ৳", number = true)
            EmojiGrid(emoji) { emoji = it }
            ColorGrid(color) { color = it }
        }
    }

    deposit?.let { g ->
        var amt by remember(g) { mutableStateOf("") }
        var withdraw by remember(g) { mutableStateOf(false) }
        FormDialog(
            title = "${g.emoji} ${g.name}",
            onDismiss = { deposit = null },
            onSave = {
                val a = Calc.evalExpr(amt) ?: 0
                if (a > 0) store.saveGoal(g.copy(saved = (g.saved + if (withdraw) -a else a).coerceAtLeast(0)))
                deposit = null
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                Pill("জমা", !withdraw, { withdraw = false }, color = x.income)
                Pill("তোলা", withdraw, { withdraw = true }, color = x.expense)
            }
            Field(amt, { amt = it }, "টাকা ৳", number = true)
        }
    }
}

// ======================= CATEGORIES =======================

@Composable
fun CategoriesScreen(d: AppData, store: Store, nav: Nav) {
    val x = LocalExtra.current
    val ctx = LocalContext.current
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var adding by remember { mutableStateOf(false) }
    val list = d.categories.filter { it.type == type }.sortedBy { it.archived }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { TopBar("ক্যাটাগরি", onBack = { nav.back() }) { TextButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, null); Text(" নতুন") } } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                Pill("খরচের খাত", type == TxType.EXPENSE, { type = TxType.EXPENSE }, color = x.expense)
                Pill("আয়ের খাত", type == TxType.INCOME, { type = TxType.INCOME }, color = x.income)
            }
        }
        items(list, key = { it.id }) { c ->
            Card(padding = 12.dp, onClick = { editing = c }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiBadge(c.emoji, c.color, 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name + if (c.archived) " (লুকানো)" else "", style = MaterialTheme.typography.titleSmall, color = if (c.archived) x.muted else MaterialTheme.colorScheme.onSurface)
                        if (c.isBazar) Text("বাজারের খাত", style = MaterialTheme.typography.labelSmall, color = x.muted)
                    }
                    Icon(Icons.Rounded.Edit, null, tint = x.muted)
                }
            }
        }
    }

    if (adding || editing != null) {
        val ex = editing
        var name by remember(ex) { mutableStateOf(ex?.name ?: "") }
        var emoji by remember(ex) { mutableStateOf(ex?.emoji ?: "📦") }
        var color by remember(ex) { mutableStateOf(ex?.color ?: 0xFF6C5CE7) }
        var bazar by remember(ex) { mutableStateOf(ex?.isBazar ?: false) }
        FormDialog(
            title = if (ex == null) "নতুন ক্যাটাগরি" else "ক্যাটাগরি এডিট",
            onDismiss = { adding = false; editing = null },
            onSave = {
                if (name.isBlank()) { Toast.makeText(ctx, "নাম লিখুন", Toast.LENGTH_SHORT).show(); return@FormDialog }
                store.saveCategory((ex ?: Category(name = name, emoji = emoji, color = color, type = type)).copy(name = name.trim(), emoji = emoji, color = color, isBazar = bazar))
                adding = false; editing = null
            },
            extraButton = if (ex != null) ({
                TextButton(onClick = { store.saveCategory(ex.copy(archived = !ex.archived)); editing = null }) {
                    Text(if (ex.archived) "ফিরিয়ে আনুন" else "লুকান", color = x.expense)
                }
            }) else null,
        ) {
            Field(name, { name = it }, "নাম")
            if (type == TxType.EXPENSE) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                Text("বাজারের খাত (আইটেম ধরে হিসাব)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(bazar, { bazar = it })
            }
            EmojiGrid(emoji) { emoji = it }
            ColorGrid(color) { color = it }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DayPickerDialog(initialDay: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initialDay * 86_400_000L)
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPick(it / 86_400_000L) } ?: onDismiss() }) { Text("ঠিক আছে") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } },
    ) { androidx.compose.material3.DatePicker(state) }
}
