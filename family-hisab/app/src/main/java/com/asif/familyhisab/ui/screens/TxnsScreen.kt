package com.asif.familyhisab.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.asif.familyhisab.data.AppData
import com.asif.familyhisab.data.Calc
import com.asif.familyhisab.data.Store
import com.asif.familyhisab.data.TxType
import com.asif.familyhisab.ui.Nav
import com.asif.familyhisab.ui.Routes
import com.asif.familyhisab.ui.components.Card
import com.asif.familyhisab.ui.components.EmptyState
import com.asif.familyhisab.ui.components.PeriodPicker
import com.asif.familyhisab.data.Period
import com.asif.familyhisab.ui.components.Pill
import com.asif.familyhisab.ui.components.RowSpaced
import com.asif.familyhisab.ui.components.StatTile
import com.asif.familyhisab.ui.components.TxRow
import com.asif.familyhisab.ui.theme.LocalExtra
import com.asif.familyhisab.util.LocalFmt
import java.time.LocalDate

@Composable
fun TxnsScreen(d: AppData, period: Period, setPeriod: (Period) -> Unit, nav: Nav, @Suppress("UNUSED_PARAMETER") store: Store) {
    val f = LocalFmt.current
    val x = LocalExtra.current
    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var memberFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var catFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val searching = query.isNotBlank()

    val base = remember(d.txns, period, searching) { if (searching) d.txns else Calc.inPeriod(d.txns, period) }
    val list = remember(base, query, typeFilter, memberFilter, catFilter, d.categories, d.members) {
        val q = query.trim().lowercase()
        base.filter { t ->
            (typeFilter == null || t.type.name == typeFilter) &&
                (memberFilter == null || t.memberId == memberFilter) &&
                (catFilter == null || t.categoryId == catFilter) &&
                (q.isEmpty() || t.note.lowercase().contains(q) ||
                    (d.category(t.categoryId)?.name?.lowercase()?.contains(q) == true) ||
                    (d.member(t.memberId)?.name?.lowercase()?.contains(q) == true) ||
                    t.items.any { it.name.lowercase().contains(q) } ||
                    (t.amount / 100).toString() == q)
        }.sortedWith(compareByDescending<com.asif.familyhisab.data.Txn> { it.day }.thenByDescending { it.createdAt })
    }
    val totals = Calc.totals(list)
    val catsInMonth = remember(base) { base.map { it.categoryId }.distinct() }
    val today = LocalDate.now()

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 130.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("লেনদেন", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                if (!searching) PeriodPicker(period, setPeriod, earliest = d.txns.minOfOrNull { it.day }?.let { LocalDate.ofEpochDay(it) })
            }
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text("খুঁজুন: নোট, আইটেম, সদস্য, টাকা…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (searching) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "মুছুন") } },
                singleLine = true, shape = RoundedCornerShape(18.dp), colors = fieldColors(),
            )
            if (searching) Text("সব মাসে খোঁজা হচ্ছে — ${f.num(list.size)}টি পাওয়া গেছে", style = MaterialTheme.typography.labelMedium, color = x.muted)
            Spacer(Modifier.height(6.dp))
            RowSpaced {
                StatTile("খরচ", f.money(totals.expense), "🧾", x.expense, Modifier.weight(1f))
                StatTile("আয়", f.money(totals.income), "💰", x.income, Modifier.weight(1f))
                StatTile("এন্ট্রি", f.num(list.size) + "টি", "📋", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Pill("সব", typeFilter == null, { typeFilter = null }) }
                item { Pill("খরচ", typeFilter == TxType.EXPENSE.name, { typeFilter = TxType.EXPENSE.name }, color = x.expense) }
                item { Pill("আয়", typeFilter == TxType.INCOME.name, { typeFilter = TxType.INCOME.name }, color = x.income) }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Pill("সবাই", memberFilter == null, { memberFilter = null }, emoji = "👥") }
                items(d.activeMembers, key = { it.id }) { m ->
                    Pill(m.name.split(" ").first(), memberFilter == m.id, { memberFilter = if (memberFilter == m.id) null else m.id }, emoji = m.emoji, color = Color(m.color))
                }
            }
            if (catsInMonth.size > 1) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(catsInMonth) { id ->
                        val c = d.category(id) ?: return@items
                        Pill(c.name, catFilter == id, { catFilter = if (catFilter == id) null else id }, emoji = c.emoji, color = Color(c.color))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (list.isEmpty()) item {
            Card { EmptyState("🔍", if (searching) "কিছু পাওয়া যায়নি" else "কোনো লেনদেন নেই", if (searching) "অন্য শব্দ দিয়ে খুঁজে দেখুন" else "+ বাটন চেপে নতুন এন্ট্রি দিন") }
        }

        list.groupBy { it.day }.forEach { (day, txs) ->
            item(key = "h$day") {
                val date = LocalDate.ofEpochDay(day)
                val spent = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.dayHeader(date, today), style = MaterialTheme.typography.titleSmall)
                        if (searching || date.year != today.year) Text(f.date(date), style = MaterialTheme.typography.labelSmall, color = x.muted)
                    }
                    if (spent > 0) Text("−" + f.money(spent), style = MaterialTheme.typography.labelLarge, color = x.expense)
                }
            }
            item(key = "b$day") {
                Card(padding = 6.dp) { txs.forEach { t -> TxRow(t, d, onClick = { nav.go(Routes.add(id = t.id)) }) } }
            }
        }
    }
}
