package app.persora.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import app.persora.android.core.util.runCatchingSafe
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.persora.android.ui.theme.Bento
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun persoraFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Bento.fg, unfocusedBorderColor = Bento.borderStrong, focusedLabelColor = Bento.primary, unfocusedLabelColor = Bento.mutedFg,
    cursorColor = Bento.primary, focusedContainerColor = Bento.card, unfocusedContainerColor = Bento.card, focusedTextColor = Bento.fg, unfocusedTextColor = Bento.fg,
)

@Composable
fun TextInput(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, placeholder: String = "", required: Boolean = false,
    singleLine: Boolean = true, minLines: Int = 1, keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false, enabled: Boolean = true, supporting: String? = null, isError: Boolean = false,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(), enabled = enabled, isError = isError,
        label = { Text(if (required) "$label *" else label) }, placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder, color = Bento.subtleFg) }) else null,
        singleLine = singleLine && minLines <= 1, minLines = minLines, shape = RoundedCornerShape(12.dp), colors = persoraFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        supportingText = supporting?.let { { Text(it, color = if (isError) Bento.danger else Bento.subtleFg) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectInput(value: String, options: List<String>, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, required: Boolean = false, allowEmpty: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, label = { Text(if (required) "$label *" else label) }, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            trailingIcon = { Icon(Icons.Outlined.ExpandMore, null, tint = Bento.mutedFg) }, shape = RoundedCornerShape(12.dp), colors = persoraFieldColors(), singleLine = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Bento.card) {
            if (allowEmpty && !required) DropdownMenuItem(text = { Text("—", color = Bento.subtleFg) }, onClick = { onChange(""); expanded = false })
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false }) }
        }
    }
}

/** YYYY-MM-DD picker — same wire format as the web's <input type="date">. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateInput(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, required: Boolean = false, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, enabled = enabled, label = { Text(if (required) "$label *" else label) }, modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Icon(Icons.Outlined.CalendarMonth, null, tint = Bento.mutedFg) }, shape = RoundedCornerShape(12.dp), colors = persoraFieldColors(), singleLine = true,
            placeholder = { Text("Select a date", color = Bento.subtleFg) },
        )
        if (enabled) Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val initial = runCatchingSafe { LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(onDismissRequest = { open = false }, confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; open = false }) { Text("OK") }
        }, dismissButton = { Row { if (value.isNotBlank()) TextButton(onClick = { onChange(""); open = false }) { Text("Clear") }; TextButton(onClick = { open = false }) { Text("Cancel") } } }) { DatePicker(state = state) }
    }
}

/** HH:mm picker — same wire format as <input type="time">. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeInput(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, required: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, label = { Text(if (required) "$label *" else label) }, modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Icon(Icons.Outlined.Schedule, null, tint = Bento.mutedFg) }, shape = RoundedCornerShape(12.dp), colors = persoraFieldColors(), singleLine = true,
            placeholder = { Text("Select a time", color = Bento.subtleFg) },
        )
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val parsed = runCatchingSafe { LocalTime.parse(value) }.getOrNull() ?: LocalTime.of(8, 0)
        val state = rememberTimePickerState(initialHour = parsed.hour, initialMinute = parsed.minute, is24Hour = false)
        AlertDialog(onDismissRequest = { open = false }, confirmButton = { TextButton(onClick = { onChange(String.format("%02d:%02d", state.hour, state.minute)); open = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }, text = { TimePicker(state = state) }, containerColor = Bento.card)
    }
}

/** Combined date + time -> ISO-8601 instant (reminderAt). */
@Composable
fun DateTimeInput(valueIso: String, onChange: (String) -> Unit, label: String, required: Boolean = false) {
    val zoned = remember(valueIso) { app.persora.android.core.util.Dates.parseInstant(valueIso) }
    var date by remember(valueIso) { mutableStateOf(zoned?.toLocalDate()?.toString().orEmpty()) }
    var time by remember(valueIso) { mutableStateOf(zoned?.toLocalTime()?.let { String.format("%02d:%02d", it.hour, it.minute) }.orEmpty()) }
    fun emit() { if (date.isNotBlank() && time.isNotBlank()) runCatchingSafe { onChange(LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(java.time.ZoneId.systemDefault()).toInstant().toString()) } }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DateInput(date, { date = it; emit() }, "$label date", Modifier.weight(1f), required)
        TimeInput(time, { time = it; emit() }, "Time", Modifier.weight(1f), required)
    }
}


/** Stand-alone Material date picker (yyyy-MM-dd). Used by chip-style pickers in the task editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickDateDialog(value: String, onPick: (String) -> Unit, onDismiss: () -> Unit, allowClear: Boolean = true) {
    val initial = runCatchingSafe { LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton(onClick = { state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; onDismiss() }) { Text("OK") }
    }, dismissButton = { Row { if (allowClear && value.isNotBlank()) TextButton(onClick = { onPick(""); onDismiss() }) { Text("Clear") }; TextButton(onClick = onDismiss) { Text("Cancel") } } }) { DatePicker(state = state) }
}

/** Stand-alone Material time picker (HH:mm). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickTimeDialog(value: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val parsed = runCatchingSafe { LocalTime.parse(value) }.getOrNull() ?: LocalTime.of(8, 0)
    val state = rememberTimePickerState(initialHour = parsed.hour, initialMinute = parsed.minute, is24Hour = false)
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = { onPick(String.format("%02d:%02d", state.hour, state.minute)); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, text = { TimePicker(state = state) }, containerColor = Bento.card)
}

/** Google Tasks-style value chip: outlined when empty, tinted when it holds a value, optional clear "×". */
@Composable
fun ValueChip(label: String, icon: ImageVector, filled: Boolean, onClick: () -> Unit, onClear: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.clip(shape).background(if (filled) Bento.primarySoft else Color.Transparent).border(1.dp, if (filled) Bento.primary.copy(alpha = 0.6f) else Bento.borderStrong, shape).clickable(onClick = onClick).padding(start = 10.dp, end = if (onClear != null && filled) 6.dp else 10.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = if (filled) Bento.primary else Bento.mutedFg, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (filled) Bento.primary else Bento.fg, maxLines = 1)
        if (onClear != null && filled) Icon(Icons.Outlined.Close, "Clear", tint = Bento.primary, modifier = Modifier.size(16.dp).clip(CircleShape).clickable(onClick = onClear).padding(1.dp))
    }
}
