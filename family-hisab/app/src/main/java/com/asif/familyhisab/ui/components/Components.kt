package com.asif.familyhisab.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asif.familyhisab.data.AppData
import com.asif.familyhisab.data.Member
import com.asif.familyhisab.data.Poisha
import com.asif.familyhisab.data.TxType
import com.asif.familyhisab.data.Txn
import com.asif.familyhisab.ui.theme.LocalExtra
import com.asif.familyhisab.util.LocalFmt
import java.time.YearMonth

fun Long.c() = Color(this)

/** A soft rounded card — the basic building block of every screen. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val x = LocalExtra.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = x.card,
        border = BorderStroke(1.dp, x.cardBorder),
        shadowElevation = if (x.dark) 0.dp else 1.dp,
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) { Column(Modifier.padding(padding), content = content) }
}

@Composable
fun GradientBox(brush: Brush, modifier: Modifier = Modifier, radius: Dp = 28.dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(brush),
    ) {
        // Decorative glow circles for a premium feel.
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.10f), radius = size.minDimension * 0.55f, center = Offset(size.width * 0.95f, size.height * 0.05f))
            drawCircle(Color.White.copy(alpha = 0.07f), radius = size.minDimension * 0.35f, center = Offset(size.width * 0.05f, size.height * 1.0f))
        }
        content()
    }
}

fun memberBrush(color: Long): Brush {
    val c = Color(color)
    return Brush.linearGradient(listOf(c, lerp(c, Color(0xFF1B1A2E), 0.25f)))
}

fun lerp(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

/** Emoji inside a tinted circle — used for members and categories. */
@Composable
fun EmojiBadge(emoji: String, color: Long, size: Dp = 44.dp, solid: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (solid) memberBrush(color) else Brush.linearGradient(listOf(Color(color).copy(alpha = 0.18f), Color(color).copy(alpha = 0.10f)))),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size.value * 0.46f).sp) }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "ফিরে যান") }
        } else Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

/** ‹ নভেম্বর ২০২৬ › */
@Composable
fun MonthSwitcher(ym: YearMonth, onChange: (YearMonth) -> Unit, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val f = LocalFmt.current
    val fg = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    val bg = if (onDark) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onChange(ym.minusMonths(1)) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.ChevronLeft, "আগের মাস", tint = fg)
        }
        Text(f.month(ym), style = MaterialTheme.typography.labelLarge, color = fg, modifier = Modifier.padding(horizontal = 2.dp))
        IconButton(onClick = { onChange(ym.plusMonths(1)) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.ChevronRight, "পরের মাস", tint = fg)
        }
    }
}

/** A number that counts up when it changes. */
@Composable
fun AnimatedMoney(value: Poisha, style: androidx.compose.ui.text.TextStyle, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    val f = LocalFmt.current
    val anim = remember { Animatable(0f) }
    LaunchedEffect(value) { anim.animateTo(value.toFloat(), tween(700, easing = FastOutSlowInEasing)) }
    Text(f.money(Math.round(anim.value.toDouble() / 100) * 100), style = style, color = color, modifier = modifier, maxLines = 1)
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, track: Color = MaterialTheme.colorScheme.surfaceVariant, height: Dp = 8.dp) {
    val p by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(800), label = "p")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxWidth(p).height(height).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color))))
    }
}

fun budgetColor(fraction: Float, ok: Color, warn: Color, bad: Color) = when {
    fraction >= 1f -> bad
    fraction >= 0.8f -> warn
    else -> ok
}

/**
 * Donut chart with a centre label. Tap a colour (or pass [selected]) to highlight it: that slice grows
 * thicker, the rest fade. Tap it again or the middle to show all equally.
 */
@Composable
fun Donut(
    slices: List<Pair<Long, Color>>,
    modifier: Modifier = Modifier,
    thickness: Dp = 22.dp,
    selected: Int? = null,
    onSelect: ((Int?) -> Unit)? = null,
    center: @Composable () -> Unit = {},
) {
    val total = slices.sumOf { it.first }.toFloat()
    val sweep by animateFloatAsState(1f, tween(900), label = "d")
    val track = MaterialTheme.colorScheme.surfaceVariant
    // One animated "focus" value per slice: 1 = normal/selected, 0 = faded.
    val focus = slices.indices.map { i ->
        animateFloatAsState(if (selected == null || selected == i) 1f else 0f, tween(300), label = "f$i").value
    }
    val grow = slices.indices.map { i ->
        animateFloatAsState(if (selected == i) 1f else 0f, tween(300), label = "g$i").value
    }
    val tap = if (onSelect == null || total <= 0) Modifier else Modifier.pointerInput(slices, selected) {
        detectTapGestures { pos ->
            val cx = size.width / 2f
            val cy = size.height / 2f
            val dx = pos.x - cx
            val dy = pos.y - cy
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            val outer = size.width / 2f
            val inner = outer - thickness.toPx() * 2.2f
            if (dist < inner || dist > outer + 8) { onSelect(null); return@detectTapGestures }
            var ang = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
            if (ang < 0) ang += 360f
            var acc = 0f
            val hit = slices.indexOfFirst { (v, _) -> acc += 360f * v / total; ang <= acc }
            onSelect(if (hit == selected || hit < 0) null else hit)
        }
    }
    Box(modifier.then(tap), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val maxStroke = stroke * 1.55f
            val inset = maxStroke / 2
            val arcSize = Size(size.width - maxStroke, size.height - maxStroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (total > 0) {
                var start = -90f
                val gap = if (slices.size > 1) 2.5f else 0f
                slices.forEachIndexed { i, (v, c) ->
                    val s = 360f * v / total * sweep
                    if (s > gap) drawArc(
                        c, start + gap / 2, s - gap, false, Offset(inset, inset), arcSize,
                        alpha = 0.25f + 0.75f * focus[i],
                        style = Stroke(stroke * (1f + 0.55f * grow[i]), cap = StrokeCap.Butt),
                    )
                    start += s
                }
            }
        }
        center()
    }
}

/** A legend line that can be tapped to highlight its donut slice. */
@Composable
fun LegendRow(color: Color, label: String, value: String, selected: Boolean, dimmed: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) color.copy(alpha = 0.16f) else Color.Transparent, label = "lb")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .graphicsLayer { alpha = if (dimmed) 0.4f else 1f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(if (selected) 12.dp else 9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            label, style = if (selected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(value, style = if (selected) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.labelMedium)
    }
}

/** Vertical bar chart. Each bar can have two series (e.g. expense + income). */
@Composable
fun BarChart(
    labels: List<String>,
    a: List<Long>,
    colorA: Color,
    modifier: Modifier = Modifier,
    b: List<Long>? = null,
    colorB: Color = Color.Gray,
    highlight: Int = -1,
    showEvery: Int = 1,
) {
    val max = (a + (b ?: emptyList())).maxOrNull()?.coerceAtLeast(1) ?: 1
    val grow by animateFloatAsState(1f, tween(900), label = "g")
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val n = a.size.coerceAtLeast(1)
            val slot = size.width / n
            val series = if (b != null) 2 else 1
            val barW = (slot * (if (series == 2) 0.32f else 0.55f)).coerceAtMost(26.dp.toPx())
            for (g in 1..3) {
                val y = size.height * g / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            for (i in 0 until n) {
                val cx = slot * i + slot / 2
                val dim = highlight >= 0 && i != highlight
                fun bar(v: Long, col: Color, x: Float) {
                    val h = size.height * (v.toFloat() / max) * grow
                    if (h <= 0f) return
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(col, col.copy(alpha = 0.55f)), startY = size.height - h, endY = size.height),
                        topLeft = Offset(x, size.height - h), size = Size(barW, h),
                        cornerRadius = CornerRadius(barW / 2.4f), alpha = if (dim) 0.45f else 1f,
                    )
                }
                if (series == 2) {
                    bar(a[i], colorA, cx - barW - 2f)
                    bar(b!![i], colorB, cx + 2f)
                } else bar(a[i], colorA, cx - barW / 2)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            labels.forEachIndexed { i, l ->
                Text(
                    if (i % showEvery == 0 || i == highlight) l else "", modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = if (i == highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Visible,
                )
            }
        }
    }
}

/** One transaction line: badge, category, member, amount. */
@Composable
fun TxRow(t: Txn, d: AppData, onClick: () -> Unit, showDate: Boolean = false) {
    val f = LocalFmt.current
    val x = LocalExtra.current
    val cat = d.category(t.categoryId)
    val m = d.member(t.memberId)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiBadge(cat?.emoji ?: "📦", cat?.color ?: 0xFFB2BEC3, 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (t.note.isNotBlank()) t.note else cat?.name ?: "—",
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (m != null) {
                    Text(m.emoji, fontSize = 11.sp)
                    Spacer(Modifier.width(3.dp))
                }
                val sub = buildList {
                    add(m?.name ?: "পরিবার")
                    if (t.note.isNotBlank() && cat != null) add(cat.name)
                    if (t.items.isNotEmpty()) add("${f.num(t.items.size)}টি আইটেম")
                    if (showDate) add(f.date(java.time.LocalDate.ofEpochDay(t.day)))
                }.joinToString(" · ")
                Text(sub, style = MaterialTheme.typography.bodySmall, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (t.type == TxType.EXPENSE) "−" else "+") + f.money(t.amount),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (t.type == TxType.EXPENSE) x.expense else x.income,
            )
            Text(t.method.emoji + " " + t.method.label, style = MaterialTheme.typography.labelSmall, color = x.muted)
        }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, subtitle: String = "", modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 38.sp) }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted, textAlign = TextAlign.Center)
        }
    }
}

/** Selectable pill chip with optional emoji. */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, emoji: String? = null, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    val bg = if (selected) color else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (emoji != null) { Text(emoji, fontSize = 15.sp); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/** Avatar used for "কার জন্য" picking. */
@Composable
fun MemberPick(m: Member, selected: Boolean, onClick: () -> Unit) {
    val c = Color(m.color)
    Column(
        Modifier.width(72.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(54.dp)
                .then(if (selected) Modifier.border(3.dp, c, CircleShape) else Modifier)
                .padding(if (selected) 4.dp else 0.dp),
            contentAlignment = Alignment.Center,
        ) { EmojiBadge(m.emoji, m.color, if (selected) 46.dp else 52.dp, solid = selected) }
        Spacer(Modifier.height(4.dp))
        Text(
            if (m.name.length <= 7 || m.relation.isBlank()) m.name.split(" ").first() else m.relation,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) c else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun StatTile(label: String, value: String, emoji: String, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 15.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = LocalExtra.current.muted, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

val ScreenPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 120.dp)

@Composable
fun Gap(h: Dp = 12.dp) = Spacer(Modifier.height(h))

@Composable
fun RowSpaced(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
