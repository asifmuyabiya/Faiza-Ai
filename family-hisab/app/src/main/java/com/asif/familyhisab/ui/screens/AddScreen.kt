package com.asif.familyhisab.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asif.familyhisab.data.AppData
import com.asif.familyhisab.data.BazarItem
import com.asif.familyhisab.data.Backup
import com.asif.familyhisab.data.Calc
import com.asif.familyhisab.data.Defaults
import com.asif.familyhisab.data.FAMILY_ID
import com.asif.familyhisab.data.PayMethod
import com.asif.familyhisab.data.Store
import com.asif.familyhisab.data.TxType
import com.asif.familyhisab.data.Txn
import com.asif.familyhisab.ui.Nav
import com.asif.familyhisab.ui.components.EmojiBadge
import com.asif.familyhisab.ui.components.MemberPick
import com.asif.familyhisab.ui.components.Pill
import com.asif.familyhisab.ui.components.TopBar
import com.asif.familyhisab.ui.theme.HeroColors
import com.asif.familyhisab.ui.theme.LocalExtra
import com.asif.familyhisab.util.LocalFmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private class ItemDraft(name: String = "", qty: String = "1", unit: String = "কেজি", price: String = "") {
    var name by mutableStateOf(name)
    var qty by mutableStateOf(qty)
    var unit by mutableStateOf(unit)
    var price by mutableStateOf(price)
    fun pricePoisha(): Long = Calc.evalExpr(price) ?: 0
    fun toItem() = BazarItem(name.trim(), qty.toDoubleOrNull() ?: 1.0, unit, pricePoisha())
}

private fun poishaToExpr(p: Long): String = if (p % 100 == 0L) (p / 100).toString() else "%.2f".format(p / 100.0)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(
    d: AppData, store: Store, nav: Nav,
    editId: String?, initType: String?, initMember: String?, initCat: String?, initAmt: Long?, fromShopping: Boolean,
) {
    val f = LocalFmt.current
    val x = LocalExtra.current
    val ctx = LocalContext.current
    val editing = remember(editId) { editId?.let { id -> d.txns.firstOrNull { it.id == id } } }

    var type by remember { mutableStateOf(editing?.type ?: initType?.let { runCatching { TxType.valueOf(it) }.getOrNull() } ?: TxType.EXPENSE) }
    var expr by remember { mutableStateOf(editing?.amount?.let(::poishaToExpr) ?: initAmt?.let(::poishaToExpr) ?: "") }
    var memberId by remember { mutableStateOf(editing?.memberId ?: initMember ?: FAMILY_ID) }
    var catId by remember { mutableStateOf(editing?.categoryId ?: initCat ?: if (fromShopping) "bazar" else "") }
    var day by remember { mutableStateOf(editing?.let { LocalDate.ofEpochDay(it.day) } ?: LocalDate.now()) }
    var note by remember { mutableStateOf(editing?.note ?: "") }
    var method by remember { mutableStateOf(editing?.method ?: PayMethod.CASH) }
    val items = remember {
        mutableStateListOf<ItemDraft>().apply {
            editing?.items?.forEach { add(ItemDraft(it.name, Backup.trimQty(it.qty), it.unit, poishaToExpr(it.price))) }
            if (fromShopping) store.pendingItems.forEach { add(ItemDraft(it.name, Backup.trimQty(it.qty), it.unit, "")) }
        }
    }
    var showItems by remember { mutableStateOf(items.isNotEmpty()) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val cats = d.categories.filter { it.type == type && !it.archived }
    LaunchedEffect(type) { if (cats.none { it.id == catId }) catId = cats.firstOrNull()?.id ?: "" }

    val itemsSum = items.sumOf { it.pricePoisha() }
    // When item prices change, the total follows them — but opening an old entry must not overwrite its saved amount.
    val openingSum = remember { itemsSum }
    LaunchedEffect(itemsSum) { if (itemsSum > 0 && itemsSum != openingSum) expr = poishaToExpr(itemsSum) }

    val amount = Calc.evalExpr(expr) ?: 0
    val accent by animateColorAsState(if (type == TxType.EXPENSE) x.expense else x.income, label = "a")

    fun save(again: Boolean) {
        if (amount <= 0) { Toast.makeText(ctx, "টাকার পরিমাণ লিখুন", Toast.LENGTH_SHORT).show(); return }
        if (catId.isEmpty()) { Toast.makeText(ctx, "একটা ক্যাটাগরি বাছুন", Toast.LENGTH_SHORT).show(); return }
        val t = Txn(
            id = editing?.id ?: com.asif.familyhisab.data.newId(),
            type = type, amount = amount, categoryId = catId, memberId = memberId, day = day.toEpochDay(),
            note = note.trim(), method = method,
            items = if (type == TxType.EXPENSE) items.filter { it.name.isNotBlank() }.map { it.toItem() } else emptyList(),
            createdAt = editing?.createdAt ?: System.currentTimeMillis(),
        )
        store.saveTxn(t)
        if (fromShopping) { store.clearDoneShop(); store.pendingItems = emptyList() }
        Toast.makeText(ctx, "✓ সেভ হয়েছে — ${f.money(amount)}", Toast.LENGTH_SHORT).show()
        if (again) { expr = ""; note = ""; items.clear(); showItems = false } else nav.back()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(
            if (editing != null) "এন্ট্রি এডিট" else if (type == TxType.EXPENSE) "নতুন খরচ" else "নতুন আয়",
            onBack = { nav.back() },
        ) {
            if (editing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "মুছুন", tint = x.expense) }
            else TextButton(onClick = { save(true) }) { Text("সেভ + আরেকটা") }
        }

        // Type switch
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
        ) {
            TypeTab("🧾 খরচ", type == TxType.EXPENSE, x.expense) { type = TxType.EXPENSE }
            TypeTab("💰 আয়", type == TxType.INCOME, x.income) { type = TxType.INCOME; showItems = false }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Label(if (type == TxType.EXPENSE) "কার জন্য খরচ?" else "কার আয়?")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(d.activeMembers, key = { it.id }) { m -> MemberPick(m, m.id == memberId) { memberId = m.id } }
            }

            Label("ক্যাটাগরি")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEach { c ->
                    val sel = c.id == catId
                    val col = Color(c.color)
                    Column(
                        Modifier.width(76.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (sel) col.copy(alpha = if (x.dark) 0.28f else 0.14f) else Color.Transparent)
                            .then(if (sel) Modifier.border(1.5.dp, col, RoundedCornerShape(18.dp)) else Modifier)
                            .clickable { catId = c.id }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EmojiBadge(c.emoji, c.color, 40.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(c.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
                Column(
                    Modifier.width(76.dp).clip(RoundedCornerShape(18.dp)).clickable { nav.go("categories") }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(50)).border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("নতুন", style = MaterialTheme.typography.labelSmall)
                }
            }

            Label("তারিখ")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val today = LocalDate.now()
                Pill("আজ", day == today, { day = today })
                Pill("গতকাল", day == today.minusDays(1), { day = today.minusDays(1) })
                Pill(
                    if (day != today && day != today.minusDays(1)) f.date(day) else "অন্য দিন",
                    day != today && day != today.minusDays(1), { pickDate = true }, emoji = "📅",
                )
            }

            Label("কীভাবে দিলেন")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PayMethod.entries.forEach { m -> Pill(m.label, method == m, { method = m }, emoji = m.emoji) }
            }

            Label("নোট (ঐচ্ছিক)")
            OutlinedTextField(
                note, { note = it }, Modifier.fillMaxWidth(),
                placeholder = { Text("যেমন: সাপ্তাহিক বাজার, দোকানের নাম…") },
                shape = RoundedCornerShape(16.dp), singleLine = true, colors = fieldColors(),
            )

            if (type == TxType.EXPENSE) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { showItems = !showItems; if (showItems && items.isEmpty()) items.add(ItemDraft()) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🛒", fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("বাজারের আইটেম ধরে হিসাব", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            if (items.isEmpty()) "চাল, ডাল, মাছ… আলাদা দাম লিখুন, মোট নিজে হবে" else "${f.num(items.size)}টি আইটেম · মোট ${f.money(itemsSum)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        )
                    }
                    Text(if (showItems) "▲" else "▼", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                AnimatedVisibility(showItems) { ItemsEditor(d, items) }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ---------- Amount + keypad ----------
        val imeOpen = WindowInsets.isImeVisible
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (expr.any { it == '+' || it == '-' }) f.digits(expr) + " =" else "টাকার পরিমাণ",
                        style = MaterialTheme.typography.labelMedium, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(f.money(amount), style = MaterialTheme.typography.displaySmall, color = accent, maxLines = 1)
                }
                if (expr.isNotEmpty()) IconButton(onClick = { expr = "" }) { Icon(Icons.Rounded.Close, "মুছুন", tint = x.muted) }
            }
            if (!imeOpen) {
                Spacer(Modifier.height(8.dp))
                Keypad(
                    f = { f.digits(it) },
                    onKey = { k ->
                        expr = when (k) {
                            "⌫" -> expr.dropLast(1)
                            "+", "-" -> if (expr.isEmpty() || expr.last() in "+-.") expr else expr + k
                            "." -> {
                                val lastNum = expr.takeLastWhile { it != '+' && it != '-' }
                                if (lastNum.contains('.')) expr else expr + (if (lastNum.isEmpty()) "0." else ".")
                            }
                            else -> {
                                val lastNum = expr.takeLastWhile { it != '+' && it != '-' }
                                val dot = lastNum.indexOf('.')
                                if (dot >= 0 && lastNum.length - dot > 2) expr
                                else if (lastNum.length >= 9) expr
                                else expr + k
                            }
                        }
                    },
                    onSave = { save(false) },
                    accent = accent,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                SaveButton("সেভ করুন", Modifier.fillMaxWidth().height(52.dp)) { save(false) }
            }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("ঠিক আছে") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("বাতিল") } },
        ) { DatePicker(state) }
    }

    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("এন্ট্রি মুছবেন?") },
            text = { Text("${f.money(editing.amount)} এর এই এন্ট্রিটা স্থায়ীভাবে মুছে যাবে।") },
            confirmButton = {
                TextButton(onClick = { store.deleteTxn(editing.id); confirmDelete = false; nav.back() }) { Text("মুছুন", color = x.expense) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("বাতিল") } },
        )
    }
}

@Composable
private fun RowScope.TypeTab(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) color else Color.Transparent, label = "t")
    Box(
        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(bg).clickable(onClick = onClick).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = LocalExtra.current.muted, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemsEditor(d: AppData, items: MutableList<ItemDraft>) {
    val f = LocalFmt.current
    val x = LocalExtra.current
    val stats = remember(d.txns) { Calc.itemStats(d.txns) }
    Column(Modifier.padding(top = 10.dp)) {
        items.forEachIndexed { i, it ->
            Column(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.num(i + 1) + ".", style = MaterialTheme.typography.labelLarge, color = x.muted, modifier = Modifier.width(24.dp))
                    OutlinedTextField(
                        it.name, { v -> it.name = v }, Modifier.weight(1f), singleLine = true,
                        placeholder = { Text("আইটেমের নাম") }, shape = RoundedCornerShape(12.dp), colors = fieldColors(),
                    )
                    IconButton(onClick = { items.removeAt(i) }) { Icon(Icons.Rounded.Close, "বাদ", tint = x.muted) }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.width(16.dp))
                    OutlinedTextField(
                        it.qty, { v -> it.qty = v.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(0.8f), singleLine = true,
                        label = { Text("পরিমাণ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp), colors = fieldColors(),
                    )
                    UnitPicker(it.unit) { u -> it.unit = u }
                    OutlinedTextField(
                        it.price, { v -> it.price = v.filter { c -> c.isDigit() || c == '.' || c == '+' } }, Modifier.weight(1f), singleLine = true,
                        label = { Text("দাম ৳") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp), colors = fieldColors(),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .clickable { items.add(ItemDraft()) }.padding(12.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
            Text(" আরেকটা আইটেম", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        val have = items.map { it.name.trim().lowercase() }.toSet()
        val sugg = stats.filter { it.name.lowercase() !in have }.take(12)
        if (sugg.isNotEmpty()) {
            Text("আগে কেনা — এক ট্যাপে যোগ করুন", style = MaterialTheme.typography.labelMedium, color = x.muted, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sugg.forEach { s ->
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                val blank = items.indexOfFirst { it.name.isBlank() && it.price.isBlank() }
                                val draft = ItemDraft(s.name, "1", s.unit, poishaToExpr(s.lastUnitPrice))
                                if (blank >= 0) items[blank] = draft else items.add(draft)
                            }.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text("${s.name} · ${f.money(s.lastUnitPrice)}/${s.unit}", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

@Composable
private fun UnitPicker(unit: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { open = true }.padding(horizontal = 12.dp, vertical = 14.dp),
        ) { Text("$unit ▾", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        DropdownMenu(open, { open = false }) {
            Defaults.units.forEach { u -> DropdownMenuItem(text = { Text(u) }, onClick = { onPick(u); open = false }) }
        }
    }
}

@Composable
private fun Keypad(f: (String) -> String, onKey: (String) -> Unit, onSave: () -> Unit, accent: Color) {
    val rows = listOf(
        listOf("7", "8", "9", "⌫"),
        listOf("4", "5", "6", "+"),
        listOf("1", "2", "3", "-"),
        listOf(".", "0", "00", "✓"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.forEach { k ->
                    val isOp = k in listOf("⌫", "+", "-")
                    val mod = Modifier.weight(1f).height(52.dp)
                    if (k == "✓") {
                        SaveButton(null, mod, onSave)
                    } else {
                        Box(
                            mod.clip(RoundedCornerShape(16.dp))
                                .background(if (isOp) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onKey(if (k == "00") "0" else k); if (k == "00") onKey("0") },
                            contentAlignment = Alignment.Center,
                        ) {
                            when (k) {
                                "⌫" -> Icon(Icons.Rounded.Backspace, "মুছুন", tint = accent)
                                "-" -> Text("−", fontSize = 24.sp, color = accent, fontWeight = FontWeight.SemiBold)
                                "+" -> Text("+", fontSize = 24.sp, color = accent, fontWeight = FontWeight.SemiBold)
                                else -> Text(f(k), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SaveButton(text: String?, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(HeroColors)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Check, "সেভ", tint = Color.White)
            if (text != null) { Spacer(Modifier.width(6.dp)); Text(text, color = Color.White, style = MaterialTheme.typography.titleSmall) }
        }
    }
}
