package com.asif.familyhisab.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asif.familyhisab.data.Defaults
import com.asif.familyhisab.ui.theme.LocalExtra

/** A dialog with a scrollable form body and save/cancel buttons. */
@Composable
fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveText: String = "সেভ",
    extraButton: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) { content() } },
        confirmButton = { TextButton(onClick = onSave) { Text(saveText) } },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                extraButton?.invoke()
                TextButton(onClick = onDismiss) { Text("বাতিল") }
            }
        },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, number: Boolean = false, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value, { v -> onChange(if (number) v.filter { it.isDigit() || it == '.' || it == '+' } else v) },
        modifier.fillMaxWidth().padding(bottom = 10.dp),
        label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(14.dp),
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        colors = fieldColors(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmojiGrid(selected: String, onPick: (String) -> Unit) {
    Text("আইকন", style = MaterialTheme.typography.labelLarge, color = LocalExtra.current.muted)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Defaults.emojis.forEach { e ->
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (e == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { onPick(e) },
                contentAlignment = Alignment.Center,
            ) { Text(e, fontSize = 20.sp) }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorGrid(selected: Long, onPick: (Long) -> Unit) {
    Text("রং", style = MaterialTheme.typography.labelLarge, color = LocalExtra.current.muted)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Defaults.colors.forEach { c ->
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                    .then(if (c == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { onPick(c) },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}
