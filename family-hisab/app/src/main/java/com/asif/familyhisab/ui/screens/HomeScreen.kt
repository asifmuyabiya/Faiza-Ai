package com.asif.familyhisab.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asif.familyhisab.data.AppData
import com.asif.familyhisab.data.Calc
import com.asif.familyhisab.data.Period
import com.asif.familyhisab.data.Settings
import com.asif.familyhisab.data.Store
import com.asif.familyhisab.data.ThemeMode
import com.asif.familyhisab.ui.components.LegendRow
import com.asif.familyhisab.ui.components.PeriodPicker
import com.asif.familyhisab.ui.components.period
import com.asif.familyhisab.ui.components.prevLabel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import com.asif.familyhisab.data.TxType
import com.asif.familyhisab.ui.Nav
import com.asif.familyhisab.ui.Routes
import com.asif.familyhisab.ui.components.AnimatedMoney
import com.asif.familyhisab.ui.components.BarChart
import com.asif.familyhisab.ui.components.Card
import com.asif.familyhisab.ui.components.Donut
import com.asif.familyhisab.ui.components.EmojiBadge
import com.asif.familyhisab.ui.components.EmptyState
import com.asif.familyhisab.ui.components.GradientBox
import com.asif.familyhisab.ui.components.ProgressBar
import com.asif.familyhisab.ui.components.RowSpaced
import com.asif.familyhisab.ui.components.SectionTitle
import com.asif.familyhisab.ui.components.StatTile
import com.asif.familyhisab.ui.components.TxRow
import com.asif.familyhisab.ui.components.budgetColor
import com.asif.familyhisab.ui.components.c
import com.asif.familyhisab.ui.components.memberBrush
import com.asif.familyhisab.ui.theme.LocalExtra
import com.asif.familyhisab.util.Fmt
import com.asif.familyhisab.util.LocalFmt
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@Composable
fun HomeScreen(d: AppData, period: Period, setPeriod: (Period) -> Unit, nav: Nav, store: Store) {
    val f = LocalFmt.current
    val x = LocalExtra.current
    val today = LocalDate.now()
    val monthTx = remember(d.txns, period) { Calc.inPeriod(d.txns, period) }
    val totals = remember(monthTx) { Calc.totals(monthTx) }
    val prevPeriod = period.previous()
    val prev = remember(d.txns, period) { prevPeriod?.let { Calc.totals(Calc.inPeriod(d.txns, it)) } }
    // Budget is monthly, so the budget card always looks at the running month.
    val thisMonthSpent = remember(d.txns) { Calc.totals(Calc.inMonth(d.txns, YearMonth.now())).expense }
    val debtFlow = remember(d.debts, period) { Calc.debtFlow(d.debts, period) }
    val earliest = remember(d.txns) { d.txns.minOfOrNull { it.day }?.let { LocalDate.ofEpochDay(it) } }
    var catSel by remember(period) { mutableStateOf<Int?>(null) }
    val byMember = remember(monthTx) { Calc.expenseByMember(monthTx).toMap() }
    val byCat = remember(monthTx) { Calc.byCategory(monthTx) }
    val six = remember(d.txns, period) { Calc.lastMonths(d.txns, period.yearMonth, 6) }
    val quick = remember(d.txns) { Calc.quickPicks(d.txns) }
    val debt = remember(d.debts) { Calc.debtSummary(d.debts) }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 130.dp)) {
        // ---------- Greeting ----------
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge("👑", 0xFF6C5CE7, 46.dp, solid = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${Fmt.greeting(LocalTime.now().hour)} 👋", style = MaterialTheme.typography.bodySmall, color = x.muted)
                    Text(d.settings.ownerName, style = MaterialTheme.typography.titleLarge)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(f.weekday(today), style = MaterialTheme.typography.labelMedium, color = x.muted)
                    Text(f.date(today), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(8.dp))
                ThemeButton(d.settings, store)
            }
            Spacer(Modifier.height(10.dp))
        }

        // ---------- Hero ----------
        item {
            GradientBox(x.hero) {
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("পরিবারের মোট খরচ", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        PeriodPicker(period, setPeriod, onDark = true, earliest = earliest)
                    }
                    Spacer(Modifier.height(6.dp))
                    AnimatedMoney(totals.expense, MaterialTheme.typography.displayMedium, Color.White)
                    val ch = prev?.let { Calc.change(it.expense, totals.expense) }
                    if (ch != null && ch != 0) {
                        Text(
                            (if (ch > 0) "▲ " else "▼ ") + f.num(kotlin.math.abs(ch)) + "% ${prevLabel(period)} চেয়ে " + (if (ch > 0) "বেশি" else "কম"),
                            color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.14f)).padding(14.dp)) {
                        HeroStat("💵 আয়", f.money(totals.income), Modifier.weight(1f))
                        Box(Modifier.width(1.dp).height(38.dp).background(Color.White.copy(alpha = 0.25f)))
                        HeroStat("💰 অবশিষ্ট", f.money(totals.balance), Modifier.weight(1f).padding(start = 14.dp))
                    }
                    if (debtFlow != 0L) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.10f)).clickable { nav.go("debts") }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(
                                "🤝 ধার-বাকি মিলিয়ে হাতে আছে", color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f),
                            )
                            Text(f.money(totals.balance + debtFlow), color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    val budget = d.budget.monthlyTotal
                    if (budget > 0 && period.isMonth) {
                        Spacer(Modifier.height(14.dp))
                        val frac = Calc.percent(totals.expense, budget)
                        Row {
                            Text("বাজেট ${f.money(budget)}", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            Text("${f.num(Math.round(frac * 100))}%", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.height(6.dp))
                        ProgressBar(frac, if (frac >= 1f) Color(0xFFFFD1D6) else Color.White, track = Color.White.copy(alpha = 0.2f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---------- Quick stats ----------
        item {
            val perDay = Calc.perDay(d.txns, period, today)
            RowSpaced {
                StatTile("আজ", f.money(Calc.spentOn(d.txns, today)), "📅", x.expense, Modifier.weight(1f))
                StatTile("এই সপ্তাহ", f.money(Calc.spentThisWeek(d.txns, today)), "🗓️", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatTile("দিনপ্রতি", f.money(perDay), "🎯", x.income, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }

        // ---------- Members ----------
        item {
            SectionTitle("👨‍👩‍👦 সদস্যদের হিসাব", action = "সবাই", onAction = { nav.go("members") })
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 4.dp)) {
                items(d.activeMembers, key = { it.id }) { m ->
                    val spent = byMember[m.id] ?: 0
                    Box(
                        Modifier.width(150.dp).clip(RoundedCornerShape(24.dp)).background(memberBrush(m.color))
                            .clickable { nav.go("member/${m.id}") }.padding(16.dp),
                    ) {
                        Column {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                                Text(m.emoji, fontSize = 22.sp)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(m.name, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(m.relation, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            Text(f.money(spent), color = Color.White, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                            Text(
                                "মোটের ${f.num(Math.round(Calc.percent(spent, totals.expense) * 100))}%",
                                color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ---------- Quick entry ----------
        item {
            SectionTitle("⚡ দ্রুত এন্ট্রি")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val base = listOf(
                    Triple("bazar", "family", "🛒 বাজার"),
                    Triple("fish", "family", "🐟 মাছ-মাংস"),
                    Triple("veg", "family", "🥬 সবজি"),
                    Triple("bill", "family", "💡 বিল"),
                )
                items(quick.size) { i ->
                    val q = quick[i]
                    val cat = d.category(q.categoryId)
                    val m = d.member(q.memberId)
                    QuickChip("${cat?.emoji ?: "📦"} ${cat?.name ?: ""}", "${m?.name?.split(" ")?.first() ?: ""} · ${f.money(q.lastAmount)}", cat?.color ?: 0xFF6C5CE7) {
                        nav.go(Routes.add(type = q.type.name, member = q.memberId, cat = q.categoryId, amt = q.lastAmount))
                    }
                }
                if (quick.size < 4) items(base.size) { i ->
                    val (cat, mem, label) = base[i]
                    QuickChip(label, "পরিবার", d.category(cat)?.color ?: 0xFF6C5CE7) { nav.go(Routes.add(member = mem, cat = cat)) }
                }
                item { QuickChip("💰 আয়", "যোগ করুন", 0xFF00B894) { nav.go(Routes.add(type = TxType.INCOME.name, member = "asif")) } }
                item { QuickChip("📝 বাজার লিস্ট", "${f.num(d.shopping.count { !it.done })}টি বাকি", 0xFFE84393) { nav.go("shopping") } }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ---------- Category donut ----------
        item {
            Card {
                Text("🍩 কোথায় খরচ হলো", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(14.dp))
                if (byCat.isEmpty()) {
                    Text("এই সময়ে কোনো খরচ নেই", color = x.muted, style = MaterialTheme.typography.bodyMedium)
                } else {
                    val top = byCat.take(5)
                    val rest = byCat.drop(5).sumOf { it.second }
                    val slices = top.map { it.second to (d.category(it.first)?.color ?: 0xFFB2BEC3).c() } +
                        (if (rest > 0) listOf(rest to Color(0xFFB2BEC3)) else emptyList())
                    val names = top.map { d.category(it.first)?.let { c -> "${c.emoji} ${c.name}" } ?: it.first } + (if (rest > 0) listOf("অন্যান্য") else emptyList())
                    val sel = catSel?.takeIf { it < slices.size }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Donut(slices, Modifier.size(140.dp), 18.dp, selected = sel, onSelect = { catSel = it }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 30.dp)) {
                                if (sel == null) {
                                    Text("${f.num(byCat.size)}টি", style = MaterialTheme.typography.titleMedium)
                                    Text("খাত", style = MaterialTheme.typography.labelSmall, color = x.muted)
                                } else {
                                    Text(f.money(slices[sel].first), style = MaterialTheme.typography.titleSmall, color = slices[sel].second, maxLines = 1)
                                    Text("${f.num(Math.round(Calc.percent(slices[sel].first, totals.expense) * 100))}%", style = MaterialTheme.typography.labelMedium, color = x.muted)
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            slices.forEachIndexed { i, (v, c) ->
                                LegendRow(c, names[i], f.money(v), sel == i, sel != null && sel != i) { catSel = if (sel == i) null else i }
                            }
                        }
                    }
                    Text(
                        "রঙে বা নামে চাপ দিলে ওই খরচটা আলাদা করে দেখাবে", style = MaterialTheme.typography.labelSmall,
                        color = x.muted, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---------- 6 month chart ----------
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📊 ৬ মাসের চিত্র", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Dot(x.expense); Text(" খরচ  ", style = MaterialTheme.typography.labelSmall, color = x.muted)
                    Dot(x.income); Text(" আয়", style = MaterialTheme.typography.labelSmall, color = x.muted)
                }
                Spacer(Modifier.height(14.dp))
                BarChart(
                    labels = six.map { f.monthShort(it.first) },
                    a = six.map { it.second.expense }, colorA = x.expense,
                    b = six.map { it.second.income }, colorB = x.income,
                    highlight = 5,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---------- Budget + debt mini cards ----------
        item {
            RowSpaced {
                Card(Modifier.weight(1f), padding = 16.dp, onClick = { nav.go("debts") }) {
                    Text("🤝 বাকি-ধার", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("পাবো", style = MaterialTheme.typography.labelSmall, color = x.muted)
                    Text(f.money(debt.toGet), style = MaterialTheme.typography.titleMedium, color = x.income)
                    Text("দেবো", style = MaterialTheme.typography.labelSmall, color = x.muted)
                    Text(f.money(debt.toGive), style = MaterialTheme.typography.titleMedium, color = x.expense)
                    val late = Calc.overdue(d.debts, today).size
                    if (late > 0) Text("⚠️ ${f.num(late)}টির মেয়াদ পার", style = MaterialTheme.typography.labelSmall, color = x.expense)
                }
                Card(Modifier.weight(1f), padding = 16.dp, onClick = { nav.go("budget") }) {
                    Text("🎯 বাজেট", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    val b = d.budget.monthlyTotal
                    if (b <= 0) {
                        Text("মাসিক বাজেট ঠিক করুন, খরচ সীমার মধ্যে রাখুন", style = MaterialTheme.typography.bodySmall, color = x.muted)
                    } else {
                        val frac = Calc.percent(thisMonthSpent, b)
                        Text("এই মাসে বাকি আছে", style = MaterialTheme.typography.labelSmall, color = x.muted)
                        Text(f.money(b - thisMonthSpent), style = MaterialTheme.typography.titleMedium, color = if (b - thisMonthSpent < 0) x.expense else MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(10.dp))
                        ProgressBar(frac, budgetColor(frac, x.income, x.warn, x.expense))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---------- Backup reminder ----------
        val days = if (d.settings.lastBackupAt == 0L) -1 else ((System.currentTimeMillis() - d.settings.lastBackupAt) / 86_400_000L).toInt()
        if (d.txns.size >= 10 && (days < 0 || days >= 7)) item {
            Card(onClick = { nav.go("backup") }, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💾", fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ব্যাকআপ নিয়ে রাখুন", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (days < 0) "এখনো কোনো ব্যাকআপ নেওয়া হয়নি" else "শেষ ব্যাকআপ ${f.num(days)} দিন আগে",
                            style = MaterialTheme.typography.bodySmall, color = x.muted,
                        )
                    }
                    Text("এক্সপোর্ট ›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---------- Recent ----------
        item { SectionTitle("🕒 সাম্প্রতিক লেনদেন", action = "সব দেখুন", onAction = { nav.tab("txns") }) }
        val recent = monthTx.sortedWith(compareByDescending<com.asif.familyhisab.data.Txn> { it.day }.thenByDescending { it.createdAt }).take(6)
        if (recent.isEmpty()) item {
            Card { EmptyState("🧾", "এই সময়ে কোনো এন্ট্রি নেই", "নিচের + বাটন চাপুন, কয়েক সেকেন্ডেই প্রথম খরচ যোগ হয়ে যাবে") }
        } else item {
            Card(padding = 8.dp) {
                recent.forEach { t -> TxRow(t, d, onClick = { nav.go(Routes.add(id = t.id)) }, showDate = true) }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

@Composable
fun Legend(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun Dot(color: Color, size: Int = 9) = Box(Modifier.size(size.dp).clip(CircleShape).background(color))

@Composable
private fun QuickChip(title: String, sub: String, color: Long, onClick: () -> Unit) {
    val x = LocalExtra.current
    Column(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(Color(color).copy(alpha = if (x.dark) 0.20f else 0.12f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = x.muted, maxLines = 1)
    }
}

/** Sun / moon / auto button: tap to switch light → dark → auto. */
@Composable
private fun ThemeButton(s: Settings, store: Store) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val next = when (s.theme) { ThemeMode.LIGHT -> ThemeMode.DARK; ThemeMode.DARK -> ThemeMode.SYSTEM; ThemeMode.SYSTEM -> ThemeMode.LIGHT }
    IconButton(
        onClick = {
            store.saveSettings(s.copy(theme = next))
            android.widget.Toast.makeText(ctx, when (next) { ThemeMode.LIGHT -> "☀️ লাইট মোড"; ThemeMode.DARK -> "🌙 ডার্ক মোড"; ThemeMode.SYSTEM -> "📱 অটো (ফোনের মতো)" }, android.widget.Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Icon(
            when (s.theme) { ThemeMode.LIGHT -> Icons.Rounded.LightMode; ThemeMode.DARK -> Icons.Rounded.DarkMode; ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto },
            "থিম বদলান", tint = MaterialTheme.colorScheme.primary,
        )
    }
}
