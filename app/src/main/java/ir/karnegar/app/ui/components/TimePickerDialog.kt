package ir.karnegar.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.karnegar.app.calendar.toPersianDigits
import ir.karnegar.app.model.formatClock

/**
 * انتخاب‌گر ساعت با دو ستون عددی — سبک‌تر و قابل‌کنترل‌تر از TimePicker پیش‌فرض،
 * و بدون وابستگی به لوکال دستگاه برای نمایش ارقام.
 *
 * @param initialMinutes دقیقه از نیمه‌شب. اگر بیش از ۱۴۴۰ باشد یعنی روز بعد.
 * @param allowNextDay اجازه‌ی انتخاب «روز بعد» برای شیفت شب
 */
@Composable
fun TimePickerDialog(
    title: String,
    initialMinutes: Int,
    allowNextDay: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var nextDay by remember { mutableIntStateOf(if (initialMinutes >= 24 * 60) 1 else 0) }
    val base = initialMinutes % (24 * 60)
    var hour by remember { mutableIntStateOf(base / 60) }
    var minute by remember { mutableIntStateOf(base % 60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumberSpinner(
                        value = hour,
                        range = 0..23,
                        label = "ساعت",
                        onValueChange = { hour = it }
                    )
                    Text(
                        ":",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                    NumberSpinner(
                        value = minute,
                        range = 0..59,
                        label = "دقیقه",
                        onValueChange = { minute = it }
                    )
                }
                if (allowNextDay) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SegmentedPair(
                            options = listOf("همان روز", "روز بعد"),
                            selectedIndex = nextDay,
                            onSelect = { nextDay = it }
                        )
                    }
                }
                Text(
                    text = "زمان انتخابی: " + formatClock(hour * 60 + minute) +
                            if (nextDay == 1) " (روز بعد)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(nextDay * 24 * 60 + hour * 60 + minute) }) {
                Text("تایید")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
private fun NumberSpinner(
    value: Int,
    range: IntRange,
    label: String,
    step: Int = 1,
    onValueChange: (Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // برچسب بالای ستون، تا پیش از لمس دکمه‌ها مشخص باشد کدام ستون چیست
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = {
            val next = value + step
            onValueChange(if (next > range.last) range.first else next)
        }) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "افزایش $label")
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "%02d".format(value).toPersianDigits(),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            )
        }
        IconButton(onClick = {
            val next = value - step
            // اگر از ابتدای بازه پایین‌تر رفت، به آخرین مقدار معتبر همان گام برمی‌گردیم
            val wrapped = range.last - (range.last - range.first) % step
            onValueChange(if (next < range.first) wrapped else next)
        }) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "کاهش $label")
        }
    }
}

@Composable
private fun SegmentedPair(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large
    ) {
        Row(Modifier.padding(3.dp)) {
            options.forEachIndexed { i, text ->
                val selected = i == selectedIndex
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable { onSelect(i) }
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                    )
                }
            }
        }
    }
}
