package com.asif.familyhisab.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asif.familyhisab.data.AppData
import com.asif.familyhisab.data.Calc
import com.asif.familyhisab.data.Member
import com.asif.familyhisab.data.Store
import com.asif.familyhisab.data.TxType
import com.asif.familyhisab.ui.Nav
import com.asif.familyhisab.ui.Routes
import com.asif.familyhisab.ui.components.BarChart
import com.asif.familyhisab.ui.components.Card
import com.asif.familyhisab.ui.components.Donut
import com.asif.familyhisab.ui.components.EmojiBadge
import com.asif.familyhisab.ui.components.EmptyState
import com.asif.familyhisab.ui.components.GradientBox
import com.asif.familyhisab.ui.components.PeriodPicker
import com.asif.familyhisab.ui.components.LegendRow
import com.asif.familyhisab.ui.components.period
import com.asif.familyhisab.ui.components.prevLabel
import com.asif.familyhisab.data.Period
import com.asif.familyhisab.ui.components.ProgressBar
import com.asif.familyhisab.ui.components.TopBar
import com.asif.familyhisab.ui.components.TxRow
import com.asif.familyhisab.ui.components.budgetColor
import com.asif.familyhisab.ui.components.c
import com.asif.familyhisab.ui.components.memberBrush
import com.asif.familyhisab.ui.theme.LocalExtra
import com.asif.familyhisab.util.LocalFmt
import java.time.YearMonth

@Composable
fun MemberScreen(d: AppData, id: String, period: Period, setPeriod: (Period) -> Unit, nav: Nav, store: Store) {
    val f = LocalFmt.current
    val x = LocalExtra.current
    val m = d.member(id) ?: return
    var editing by remember { mutableStateOf(false) }
    val mine = remember(d.txns, id) { d.txns.filter { it.memberId == id } }
    val monthTx = remember(mine, period) { Calc.inPeriod(mine, period) }
    var catSel by remember(period) { mutableStateOf<Int?>(null) }
    val totals = Calc.totals(monthTx)
    val familyTotal = remember(d.txns, period) { Calc.totals(Calc.inPeriod(d.txns, period)).expense }
    val prev = remember(mine, period) { period.previous()?.let { Calc.totals(Calc.inPeriod(mine, it)).expense } ?: 0L }
    val byCat = remember(monthTx) { Calc.byCategory(monthTx) }
    val six = remember(mine, period) { Calc.lastMonths(mine, period.yearMonth, 6) }
    val allTime = remember(mine) { Calc.totals(mine).expense }
    val budget = d.budget.perMember[id] ?: 0

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TopBar(m.name, onBack = { nav.back() }) {
                IconButton(onClick = { editing = true }) { Icon(Icons.Rounded.Edit, "এডিট") }
            }
        }
        item {
            GradientBox(memberBrush(m.color)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                            Text(m.emoji, fontSize = 30.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, color = Color.White, style = MaterialTheme.typography.titleLarge)
                            Text(m.relation, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    PeriodPicker(period, setPeriod, onDark = true, earliest = mine.minOfOrNull { it.day }?.let { java.time.LocalDate.ofEpochDay(it) })
                    Spacer(Modifier.height(10.dp))
                    Text(if (m.isFamily) "যৌথ খরচ" else "${m.name.split(" ").first()}-এর জন্য খরচ", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                    Text(f.money(totals.expense), color = Color.White, style = MaterialTheme.typography.displaySmall)
                    Calc.change(prev, totals.expense)?.takeIf { it != 0 }?.let { ch ->
                        Text((if (ch > 0) "▲ " else "▼ ") + f.num(kotlin.math.abs(ch)) + "% " + prevLabel(period) + " তুলনায়", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.15f)).padding(12.dp)) {
                        MiniStat("পরিবারের", "${f.num(Math.round(Calc.percent(totals.expense, familyTotal) * 100))}%", Modifier.weight(1f))
                        MiniStat("এন্ট্রি", f.num(monthTx.size) + "টি", Modifier.weight(1f))
                        if (totals.income > 0) MiniStat("আয়", f.money(totals.income), Modifier.weight(1f))
                        else MiniStat("সব সময়", f.money(allTime), Modifier.weight(1f))
                    }
                }
            }
        }
        if (budget > 0 && period.isMonth) item {
            Card {
                val frac = Calc.percent(totals.expense, budget)
                Row {
                    Text("🎯 মাসিক বাজেট", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("${f.money(totals.expense)} / ${f.money(budget)}", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(8.dp))
                ProgressBar(frac, budgetColor(frac, x.income, x.warn, x.expense))
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(m.color).copy(alpha = 0.14f))
                    .padding(4.dp),
            ) {
                TextButton(onClick = { nav.go(Routes.add(member = id)) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Add, null, tint = Color(m.color)); Text(" খরচ যোগ", color = Color(m.color))
                }
                TextButton(onClick = { nav.go(Routes.add(member = id, type = TxType.INCOME.name)) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Add, null, tint = x.income); Text(" আয় যোগ", color = x.income)
                }
            }
        }
        if (byCat.isNotEmpty()) item {
            Card {
                Text("🍩 খাতভিত্তিক", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                val sel = catSel?.takeIf { it < byCat.size }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Donut(byCat.map { it.second to (d.category(it.first)?.color ?: 0xFFB2BEC3).c() }, Modifier.size(128.dp), 16.dp, selected = sel, onSelect = { catSel = it }) {
                        if (sel != null) Text(
                            "${f.num(Math.round(Calc.percent(byCat[sel].second, totals.expense) * 100))}%",
                            style = MaterialTheme.typography.titleSmall, color = (d.category(byCat[sel].first)?.color ?: 0xFFB2BEC3).c(),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        byCat.forEachIndexed { i, (cid, v) ->
                            val c = d.category(cid)
                            LegendRow((c?.color ?: 0xFFB2BEC3).c(), "${c?.emoji ?: ""} ${c?.name ?: cid}", f.money(v), sel == i, sel != null && sel != i) {
                                catSel = if (sel == i) null else i
                            }
                        }
                    }
                }
            }
        }
        item {
            Card {
                Text("📊 ৬ মাসের খরচ", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                BarChart(six.map { f.monthShort(it.first) }, six.map { it.second.expense }, Color(m.color), Modifier.fillMaxWidth().height(140.dp), highlight = 5)
            }
        }
        item { Text("🧾 লেনদেন — " + LocalFmt.current.period(period), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
        if (monthTx.isEmpty()) item { Card { EmptyState(m.emoji, "এই সময়ে কোনো এন্ট্রি নেই") } }
        else item {
            Card(padding = 6.dp) {
                monthTx.sortedWith(compareByDescending<com.asif.familyhisab.data.Txn> { it.day }.thenByDescending { it.createdAt }).forEach {
                    TxRow(it, d, onClick = { nav.go(Routes.add(id = it.id)) }, showDate = true)
                }
            }
        }
    }

    if (editing) MemberEditor(m, store, onDone = { editing = false; if (it) nav.back() })
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

@Composable
fun MemberEditor(existing: Member?, store: Store, onDone: (archived: Boolean) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var relation by remember { mutableStateOf(existing?.relation ?: "") }
    var emoji by remember { mutableStateOf(existing?.emoji ?: "🙂") }
    var color by remember { mutableStateOf(existing?.color ?: 0xFF6C5CE7) }
    FormDialog(
        title = if (existing == null) "নতুন সদস্য" else "সদস্য এডিট",
        onDismiss = { onDone(false) },
        onSave = {
            if (name.isBlank()) { Toast.makeText(ctx, "নাম লিখুন", Toast.LENGTH_SHORT).show(); return@FormDialog }
            store.saveMember((existing ?: Member(name = name)).copy(name = name.trim(), relation = relation.trim(), emoji = emoji, color = color))
            onDone(false)
        },
        extraButton = if (existing != null && !existing.isFamily) ({
            TextButton(onClick = { store.saveMember(existing.copy(archived = !existing.archived)); onDone(!existing.archived) }) {
                Text(if (existing.archived) "ফিরিয়ে আনুন" else "লুকান", color = LocalExtra.current.expense)
            }
        }) else null,
    ) {
        Field(name, { name = it }, "নাম")
        Field(relation, { relation = it }, "সম্পর্ক (যেমন: ছেলে, আম্মু)")
        EmojiGrid(emoji) { emoji = it }
        ColorGrid(color) { color = it }
    }
}

@Composable
fun MembersScreen(d: AppData, nav: Nav, store: Store) {
    val f = LocalFmt.current
    val x = LocalExtra.current
    var adding by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Member?>(null) }
    val month = YearMonth.now()
    val monthTx = remember(d.txns) { Calc.inMonth(d.txns, month) }
    val byMember = remember(monthTx) { Calc.expenseByMember(monthTx).toMap() }
    val allByMember = remember(d.txns) { Calc.expenseByMember(d.txns).toMap() }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TopBar("পরিবারের সদস্য", onBack = { nav.back() }) {
                TextButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, null); Text(" নতুন") }
            }
        }
        items(d.members.sortedWith(compareBy({ it.archived }, { it.order })), key = { it.id }) { m ->
            Card(onClick = { if (m.archived) edit = m else nav.go("member/${m.id}") }, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiBadge(m.emoji, m.color, 52.dp, solid = true)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name + if (m.archived) " (লুকানো)" else "", style = MaterialTheme.typography.titleMedium)
                        Text(m.relation, style = MaterialTheme.typography.bodySmall, color = x.muted)
                        Text("সব মিলিয়ে ${f.money(allByMember[m.id] ?: 0)}", style = MaterialTheme.typography.labelSmall, color = x.muted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("এই মাসে", style = MaterialTheme.typography.labelSmall, color = x.muted)
                        Text(f.money(byMember[m.id] ?: 0), style = MaterialTheme.typography.titleMedium, color = Color(m.color))
                    }
                    IconButton(onClick = { edit = m }) { Icon(Icons.Rounded.Edit, "এডিট", tint = x.muted) }
                }
            }
        }
    }
    if (adding) MemberEditor(null, store) { adding = false }
    edit?.let { m -> MemberEditor(m, store) { edit = null } }
}
