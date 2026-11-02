package com.asif.familyhisab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.asif.familyhisab.data.PKind
import com.asif.familyhisab.data.Period
import com.asif.familyhisab.ui.theme.LocalExtra
import com.asif.familyhisab.util.Fmt
import com.asif.familyhisab.util.LocalFmt
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** Human label for a period: আজ, গতকাল, এই সপ্তাহ, গত ৭ দিন, নভেম্বর ২০২৬, ২০২৬ সাল, ২ অক্টো – ৯ নভে… */
fun Fmt.period(p: Period, today: LocalDate = LocalDate.now()): String {
    fun short(d: LocalDate) = digits(d.dayOfMonth.toString()) + " " + Fmt.MONTHS_SHORT[d.monthValue - 1] +
        if (d.year != today.year) " " + digits((d.year % 100).toString().padStart(2, '0')) else ""
    return when (p.kind) {
        PKind.DAY -> when (p.from) { today -> "আজ"; today.minusDays(1) -> "গতকাল"; else -> date(p.from) }
        PKind.WEEK -> if (p == Period.week(today)) "এই সপ্তাহ" else if (p == Period.week(today).shift(-1)) "গত সপ্তাহ" else "${short(p.from)} – ${short(p.to)}"
        PKind.MONTH -> month(YearMonth.from(p.from))
        PKind.YEAR -> digits(p.from.year.toString()) + " সাল"
        PKind.ALL -> "সব সময়"
        PKind.RANGE -> if (p.to == today) "গত ${digits(p.days.toString())} দিন" else "${short(p.from)} – ${short(p.to)}"
    }
}

/** "আগের চেয়ে" wording for comparisons. */
fun prevLabel(p: Period): String = when (p.kind) {
    PKind.DAY -> "আগের দিনের"
    PKind.WEEK -> "আগের সপ্তাহের"
    PKind.MONTH -> "গত মাসের"
    PKind.YEAR -> "গত বছরের"
    else -> "আগের সময়ের"
}

/**
 * ‹ নভেম্বর ২০২৬ ▾ › — arrows step back/forward; tapping the middle opens all choices:
 * today, yesterday, this week, last 7 days, this/last month, last 3 months, this year, all time, or any dates.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PeriodPicker(p: Period, onChange: (Period) -> Unit, modifier: Modifier = Modifier, onDark: Boolean = false, earliest: LocalDate? = null) {
    val f = LocalFmt.current
    val fg = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    val bg = if (onDark) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
    var open by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    Row(modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (p.kind != PKind.ALL) IconButton(onClick = { onChange(p.shift(-1)) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.ChevronLeft, "আগে", tint = fg)
        }
        Row(
            Modifier.clip(RoundedCornerShape(50)).clickable { open = true }.padding(start = 6.dp, end = 2.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(f.period(p, today), style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
            Icon(Icons.Rounded.ArrowDropDown, null, tint = fg.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        }
        if (p.kind != PKind.ALL) IconButton(onClick = { onChange(p.shift(1)) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.ChevronRight, "পরে", tint = fg)
        }
    }

    if (open) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                Text("📅 সময় বাছুন", style = MaterialTheme.typography.titleLarge)
                Text("এখন: " + f.period(p, today) + if (p.days > 1) " (${f.date(p.from)} – ${f.date(p.to)})" else "",
                    style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
                val groups = listOf(
                    "দিন" to listOf("আজ" to Period.day(today), "গতকাল" to Period.day(today.minusDays(1))),
                    "সপ্তাহ" to listOf("এই সপ্তাহ" to Period.week(today), "গত সপ্তাহ" to Period.week(today).shift(-1), "গত ৭ দিন" to Period.lastDays(7, today)),
                    "মাস" to listOf(
                        "এই মাস" to Period.thisMonth(), "গত মাস" to Period.thisMonth().shift(-1),
                        "গত ৩০ দিন" to Period.lastDays(30, today), "গত ৩ মাস" to Period.range(YearMonth.now().minusMonths(2).atDay(1), today),
                    ),
                    "বছর" to listOf("এই বছর" to Period.year(today.year), "গত বছর" to Period.year(today.year - 1), "সব সময়" to Period.all(earliest, today)),
                )
                groups.forEach { (title, items) ->
                    Text(title, style = MaterialTheme.typography.labelLarge, color = LocalExtra.current.muted, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.forEach { (label, value) ->
                            Pill(f.digits(label), value == p, { onChange(value); open = false })
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { custom = true }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.DateRange, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("  নিজের মতো তারিখ বাছুন (থেকে – পর্যন্ত)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }

    if (custom) {
        fun ms(d: LocalDate) = d.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        fun day(m: Long) = Instant.ofEpochMilli(m).atZone(ZoneOffset.UTC).toLocalDate()
        val state = rememberDateRangePickerState(initialSelectedStartDateMillis = ms(p.from), initialSelectedEndDateMillis = ms(p.to))
        DatePickerDialog(
            onDismissRequest = { custom = false },
            confirmButton = {
                TextButton(onClick = {
                    val a = state.selectedStartDateMillis
                    if (a != null) {
                        val b = state.selectedEndDateMillis ?: a
                        onChange(Period.range(day(a), day(b)))
                        open = false
                    }
                    custom = false
                }) { Text("ঠিক আছে") }
            },
            dismissButton = { TextButton(onClick = { custom = false }) { Text("বাতিল") } },
        ) {
            DateRangePicker(
                state, Modifier.height(520.dp),
                title = { Text("শুরু ও শেষের তারিখ চাপুন", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
            )
        }
    }
}
