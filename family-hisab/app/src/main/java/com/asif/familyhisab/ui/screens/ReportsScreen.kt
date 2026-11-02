package com.asif.familyhisab.ui.screens

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asif.familyhisab.data.AppData
import com.asif.familyhisab.data.Calc
import com.asif.familyhisab.data.TxType
import com.asif.familyhisab.ui.Nav
import com.asif.familyhisab.ui.Routes
import com.asif.familyhisab.ui.components.BarChart
import com.asif.familyhisab.ui.components.Card
import com.asif.familyhisab.ui.components.Donut
import com.asif.familyhisab.ui.components.EmojiBadge
import com.asif.familyhisab.ui.components.GradientBox
import com.asif.familyhisab.ui.components.PeriodPicker
import com.asif.familyhisab.ui.components.period
import com.asif.familyhisab.data.Period
import com.asif.familyhisab.data.PKind
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.graphicsLayer
import java.time.LocalDate
import com.asif.familyhisab.ui.components.Pill
import com.asif.familyhisab.ui.components.ProgressBar
import com.asif.familyhisab.ui.components.TxRow
import com.asif.familyhisab.ui.components.c
import com.asif.familyhisab.ui.theme.LocalExtra
import com.asif.familyhisab.util.LocalFmt
import java.time.YearMonth

@Composable
fun ReportsScreen(d: AppData, period: Period, setPeriod: (Period) -> Unit, nav: Nav) {
    val f = LocalFmt.current
    val x = LocalExtra.current
    val today = LocalDate.now()
    var catSel by remember(period) { mutableStateOf<Int?>(null) }

    val periodTx = remember(d.txns, period) { Calc.inPeriod(d.txns, period) }
    val prevPeriod = period.previous()
    val prevTx = remember(d.txns, period) { prevPeriod?.let { Calc.inPeriod(d.txns, it) } ?: emptyList() }
    val totals = Calc.totals(periodTx)
    val prevTotals = Calc.totals(prevTx)
    val byMember = remember(periodTx) { Calc.expenseByMember(periodTx) }
    val byCat = remember(periodTx) { Calc.byCategory(periodTx) }
    val prevByCat = remember(prevTx) { Calc.byCategory(prevTx).toMap() }
    val incomeByCat = remember(periodTx) { Calc.byCategory(periodTx, TxType.INCOME) }
    val itemStats = remember(periodTx) { Calc.itemStats(periodTx).take(8) }
    val biggest = remember(periodTx) { periodTx.filter { it.type == TxType.EXPENSE }.sortedByDescending { it.amount }.take(5) }
    val trend = remember(d.txns, period) { Calc.trend(d.txns, period) }
    val earliest = remember(d.txns) { d.txns.minOfOrNull { it.day }?.let { LocalDate.ofEpochDay(it) } }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("রিপোর্ট", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                PeriodPicker(period, setPeriod, earliest = earliest)
            }
            Spacer(Modifier.height(4.dp))
            // Quick switch: দৈনিক / সাপ্তাহিক / মাসিক / বার্ষিক — keeps the date you're looking at.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val anchor = if (period.contains(today.toEpochDay())) today else period.to
                Pill("দৈনিক", period.kind == PKind.DAY, { setPeriod(Period.day(anchor)) }, emoji = "📅")
                Pill("সাপ্তাহিক", period.kind == PKind.WEEK, { setPeriod(Period.week(anchor)) }, emoji = "🗓️")
                Pill("মাসিক", period.kind == PKind.MONTH, { setPeriod(Period.month(YearMonth.from(anchor))) }, emoji = "📆")
                Pill("বার্ষিক", period.kind == PKind.YEAR, { setPeriod(Period.year(anchor.year)) }, emoji = "🗂️")
            }
        }

        // Summary
        item {
            GradientBox(x.hero) {
                Column(Modifier.padding(20.dp)) {
                    Text(f.period(period, today) + if (period.kind == PKind.RANGE || period.kind == PKind.WEEK || period.kind == PKind.ALL) "  ·  ${f.date(period.from)} – ${f.date(period.to)}" else "", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        SumCol("মোট খরচ", f.money(totals.expense), Calc.change(prevTotals.expense, totals.expense), Modifier.weight(1f))
                        SumCol("মোট আয়", f.money(totals.income), Calc.change(prevTotals.income, totals.income), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.14f)).padding(12.dp)) {
                        Text("সঞ্চয় / অবশিষ্ট", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text(f.money(totals.balance), color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    if (totals.income > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "আয়ের ${f.num(Math.round(Calc.percent(totals.balance.coerceAtLeast(0), totals.income) * 100))}% জমেছে",
                            color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }

        // Trend chart
        item {
            Card {
                val monthly = trend.firstOrNull()?.monthly == true
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (monthly) "📊 মাসভিত্তিক" else "📈 দিনভিত্তিক", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Dot(x.expense); Text(" খরচ  ", style = MaterialTheme.typography.labelSmall, color = x.muted)
                    if (monthly) { Dot(x.income); Text(" আয়", style = MaterialTheme.typography.labelSmall, color = x.muted) }
                }
                Text(
                    "সর্বোচ্চ: ${f.money(trend.maxOfOrNull { it.expense } ?: 0)} · দিনপ্রতি গড়: ${f.money(Calc.perDay(d.txns, period, today))}",
                    style = MaterialTheme.typography.bodySmall, color = x.muted,
                )
                Spacer(Modifier.height(12.dp))
                val labels = trend.map { if (it.monthly) f.monthShort(YearMonth.from(it.label)) else f.num(it.label.dayOfMonth) }
                BarChart(
                    labels = labels, a = trend.map { it.expense }, colorA = x.expense,
                    // Daily view shows spending only — one salary day would otherwise flatten every other bar.
                    b = if (monthly) trend.map { it.income } else null, colorB = x.income,
                    modifier = Modifier.fillMaxWidth().height(170.dp),
                    showEvery = when { trend.size > 20 -> 5; trend.size > 8 -> 2; else -> 1 },
                    highlight = trend.indexOfFirst { if (it.monthly) YearMonth.from(it.label) == YearMonth.from(today) else it.label == today },
                )
            }
        }

        // Members
        item {
            Card {
                Text("👨‍👩‍👦 কার জন্য কত খরচ", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                if (byMember.isEmpty()) Text("কোনো খরচ নেই", color = x.muted)
                val max = byMember.maxOfOrNull { it.second } ?: 1
                byMember.forEach { (id, v) ->
                    val m = d.member(id)
                    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        EmojiBadge(m?.emoji ?: "🙂", m?.color ?: 0xFF6C5CE7, 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(m?.name ?: id, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(f.money(v), style = MaterialTheme.typography.titleSmall)
                            }
                            Spacer(Modifier.height(5.dp))
                            ProgressBar(Calc.percent(v, max), (m?.color ?: 0xFF6C5CE7).c(), height = 7.dp)
                            Text("মোটের ${f.num(Math.round(Calc.percent(v, totals.expense) * 100))}%", style = MaterialTheme.typography.labelSmall, color = x.muted)
                        }
                    }
                }
            }
        }

        // Categories
        item {
            Card {
                Text("🏷️ খাতভিত্তিক খরচ", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                if (byCat.isEmpty()) Text("কোনো খরচ নেই", color = x.muted) else {
                    val sel = catSel?.takeIf { it < byCat.size }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Donut(byCat.map { it.second to (d.category(it.first)?.color ?: 0xFFB2BEC3).c() }, Modifier.size(190.dp), 24.dp, selected = sel, onSelect = { catSel = it }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 44.dp)) {
                                if (sel == null) {
                                    Text("মোট", style = MaterialTheme.typography.labelSmall, color = x.muted)
                                    Text(f.money(totals.expense), style = MaterialTheme.typography.titleMedium)
                                } else {
                                    val c = d.category(byCat[sel].first)
                                    Text("${c?.emoji ?: ""} ${c?.name ?: ""}", style = MaterialTheme.typography.labelSmall, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(f.money(byCat[sel].second), style = MaterialTheme.typography.titleMedium, color = (c?.color ?: 0xFFB2BEC3).c())
                                    Text("${f.num(Math.round(Calc.percent(byCat[sel].second, totals.expense) * 100))}%", style = MaterialTheme.typography.labelSmall, color = x.muted)
                                }
                            }
                        }
                    }
                    Text("রঙে বা নিচের নামে চাপ দিন", style = MaterialTheme.typography.labelSmall, color = x.muted, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    byCat.forEachIndexed { i, (id, v) ->
                        val c = d.category(id)
                        val ch = Calc.change(prevByCat[id] ?: 0, v)
                        val col = (c?.color ?: 0xFFB2BEC3).c()
                        Row(
                            Modifier.clip(RoundedCornerShape(14.dp))
                                .background(if (sel == i) col.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable { catSel = if (sel == i) null else i }
                                .graphicsLayer { alpha = if (sel != null && sel != i) 0.4f else 1f }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            EmojiBadge(c?.emoji ?: "📦", c?.color ?: 0xFFB2BEC3, 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(c?.name ?: id, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (ch != null && ch != 0) Text(
                                        (if (ch > 0) "▲" else "▼") + f.num(kotlin.math.abs(ch)) + "% ",
                                        style = MaterialTheme.typography.labelSmall, color = if (ch > 0) x.expense else x.income,
                                    )
                                    Text(f.money(v), style = MaterialTheme.typography.titleSmall)
                                }
                                Spacer(Modifier.height(4.dp))
                                ProgressBar(Calc.percent(v, totals.expense), (c?.color ?: 0xFFB2BEC3).c(), height = 6.dp)
                            }
                        }
                    }
                }
            }
        }

        if (incomeByCat.isNotEmpty()) item {
            Card {
                Text("💰 আয়ের উৎস", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                incomeByCat.forEach { (id, v) ->
                    val c = d.category(id)
                    Legend((c?.color ?: 0xFF00B894).c(), "${c?.emoji ?: ""} ${c?.name ?: id}", f.money(v))
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        if (itemStats.isNotEmpty()) item {
            Card {
                Text("🛒 বাজারের আইটেম — সবচেয়ে বেশি খরচ", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                itemStats.forEachIndexed { i, s ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(f.num(i + 1) + ".", style = MaterialTheme.typography.labelLarge, color = x.muted, modifier = Modifier.width(26.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleSmall)
                            Text("${f.num(s.times)} বার কেনা · শেষ দাম ${f.money(s.lastUnitPrice)}/${s.unit}", style = MaterialTheme.typography.labelSmall, color = x.muted)
                        }
                        Text(f.money(s.totalSpent), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }

        if (biggest.isNotEmpty()) item {
            Card(padding = 8.dp) {
                Text("🔥 সবচেয়ে বড় খরচগুলো", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(10.dp))
                biggest.forEach { TxRow(it, d, onClick = { nav.go(Routes.add(id = it.id)) }, showDate = true) }
            }
        }
    }
}

@Composable
private fun SumCol(label: String, value: String, change: Int?, modifier: Modifier) {
    val f = LocalFmt.current
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
        Text(value, color = Color.White, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1)
        if (change != null && change != 0) Text(
            (if (change > 0) "▲ " else "▼ ") + f.num(kotlin.math.abs(change)) + "% আগের তুলনায়",
            color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall,
        )
    }
}
