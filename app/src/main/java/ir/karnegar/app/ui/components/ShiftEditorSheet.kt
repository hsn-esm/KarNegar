package ir.karnegar.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.karnegar.app.calendar.Holidays
import ir.karnegar.app.calendar.JalaliDate
import ir.karnegar.app.data.RemoteHoliday
import ir.karnegar.app.model.ShiftEntry
import ir.karnegar.app.model.ShiftType
import ir.karnegar.app.model.formatClock
import ir.karnegar.app.model.formatDuration

/**
 * برگه‌ی ثبت/ویرایش شیفت یک روز.
 *
 * جریان کار: نوع شیفت انتخاب می‌شود -> ساعت ورود و خروج با پیش‌فرض همان شیفت پر می‌شود
 * -> کاربر در صورت لزوم دستکاری می‌کند -> تعجیل/تاخیر بلافاصله محاسبه و نشان داده می‌شود.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ShiftEditorSheet(
    date: JalaliDate,
    existing: ShiftEntry?,
    holidayOverrides: Map<Int, Boolean>,
    remoteHolidays: Map<Int, RemoteHoliday> = emptyMap(),
    onDismiss: () -> Unit,
    onSave: (ShiftEntry) -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var type by remember(date.key) { mutableStateOf(existing?.type ?: ShiftType.MORNING) }
    var inMinutes by remember(date.key) { mutableIntStateOf(existing?.actualInMinutes ?: ShiftType.MORNING.startMinutes) }
    var outMinutes by remember(date.key) { mutableIntStateOf(existing?.actualOutMinutes ?: ShiftType.MORNING.endMinutes) }
    var note by remember(date.key) { mutableStateOf(existing?.note ?: "") }
    var picker by remember { mutableStateOf<String?>(null) }

    /** وقتی نوع شیفت عوض می‌شود، ساعت‌ها روی مقدار استاندارد همان شیفت می‌روند */
    fun selectType(newType: ShiftType) {
        type = newType
        inMinutes = newType.startMinutes
        outMinutes = newType.endMinutes
    }

    val draft = ShiftEntry(date.key, type, inMinutes, outMinutes, note.trim())
    val holidayTitle = Holidays.dayOffTitle(date, holidayOverrides, remoteHolidays)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---------- عنوان و تاریخ‌های دیگر ----------
            // خانه‌های تقویم فقط عدد شمسی را دارند، پس قمری و بعد میلادی این‌جا
            // در یک سطر و با جداکننده نوشته می‌شوند؛ برچسب لازم نیست چون ترتیب
            // و شکل نوشته (نام ماه قمری در برابر ماه لاتین) خودش گویاست.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(date.longFormatted(), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = Holidays.otherCalendarsLabel(date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (holidayTitle != null) {
                    Text(
                        text = holidayTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            HorizontalDivider()

            // ---------- انتخاب نوع شیفت ----------
            // پنج گزینه باید در یک سطر جا شوند. با FilterChip نمی‌شد: پدینگ درونی
            // و آیکون پیشرو آن‌قدر پهنا می‌گیرد که «بعدازظهر» به سطر دوم می‌افتاد.
            // پس تراشه‌ی جمع‌وجور خودمان را می‌سازیم و هر پنج‌تا را weight(1f) می‌دهیم
            // تا پهنای صفحه را مساوی بین خود تقسیم کنند و هیچ‌وقت نشکنند.
            Text("نوع شیفت", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ShiftType.entries.forEach { t ->
                    ShiftTypeChip(
                        type = t,
                        selected = t == type,
                        onClick = { selectType(t) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                text = "ساعت استاندارد این شیفت: ${type.standardRange}  " +
                        "(${formatDuration(type.standardMinutes)} ساعت)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // ---------- ساعت ورود و خروج ----------
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeField(
                    label = "ورود",
                    minutes = inMinutes,
                    icon = { Icon(Icons.AutoMirrored.Rounded.Login, null, Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f),
                    onClick = { picker = "in" }
                )
                TimeField(
                    label = "خروج",
                    minutes = outMinutes,
                    icon = { Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f),
                    onClick = { picker = "out" }
                )
            }

            // ---------- محاسبه‌ی زنده ----------
            DeltaSummary(draft)

            // ---------- توضیح ----------
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("توضیح (اختیاری)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ---------- دکمه‌ها ----------
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onSave(draft) },
                    enabled = draft.isValid,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (existing == null) "ثبت شیفت" else "ذخیره تغییرات")
                }
                if (existing != null) {
                    OutlinedButton(onClick = onDelete) {
                        Icon(Icons.Rounded.Delete, contentDescription = "حذف شیفت")
                    }
                }
            }
            if (!draft.isValid) {
                Text(
                    "ساعت خروج باید بعد از ساعت ورود باشد. برای شیفت شب گزینه‌ی «روز بعد» را انتخاب کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // ---------- انتخاب‌گر ساعت ----------
    when (picker) {
        "in" -> TimePickerDialog(
            title = "ساعت ورود",
            initialMinutes = inMinutes,
            allowNextDay = false,
            onDismiss = { picker = null },
            onConfirm = { inMinutes = it; picker = null }
        )
        "out" -> TimePickerDialog(
            title = "ساعت خروج",
            initialMinutes = outMinutes,
            // فقط شیفت‌هایی که از نیمه‌شب می‌گذرند به گزینه‌ی «روز بعد» نیاز دارند
            allowNextDay = type.crossesMidnight,
            onDismiss = { picker = null },
            onConfirm = { outMinutes = it; picker = null }
        )
    }
}

/**
 * تراشه‌ی انتخاب نوع شیفت — جمع‌وجورتر از FilterChip.
 *
 * چرا دست‌ساز: FilterChip پدینگ افقی ثابت و آیکون پیشرو دارد و پنج‌تایشان در
 * پهنای گوشی جا نمی‌شد و به سطر دوم می‌افتاد. این‌جا نقطه‌ی رنگی بالای متن
 * می‌نشیند، پس تراشه باریک می‌شود و با weight(1f) پنج‌تا در یک سطر جا می‌گیرند.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ShiftTypeChip(
    type: ShiftType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = shiftColor(type)
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (selected) color.copy(alpha = 0.18f) else scheme.surface,
        contentColor = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
        border = BorderStroke(
            width = if (selected) 1.4.dp else 1.dp,
            color = if (selected) color else scheme.outlineVariant
        )
    ) {
        Column(
            Modifier.padding(vertical = 7.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Text(
                text = type.title,
                style = MaterialTheme.typography.labelSmall,
                // اندازه‌ی صریح، چون بلندترین برچسب («بعدازظهر») باید در یک‌پنجم
                // پهنای صفحه جا شود؛ به قلم پیش‌فرض تم تکیه نمی‌کنیم که اگر روزی
                // بزرگ‌تر شد، نوشته بریده نشود
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun TimeField(
    label: String,
    minutes: Int,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        icon()
        Column(
            Modifier.padding(start = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(
                text = formatClock(minutes) + if (minutes >= 24 * 60) " (فردا)" else "",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeltaSummary(draft: ShiftEntry) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.AccessTime, null, Modifier.size(18.dp), tint = scheme.primary)
            Text(
                "کارکرد این شیفت",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 6.dp)
            )
            Text(
                if (draft.isValid) "${formatDuration(draft.workedMinutes)} ساعت" else "—",
                style = MaterialTheme.typography.titleMedium
            )
        }

        val chips = buildList {
            if (draft.earlyInMinutes > 0)
                add("تعجیل در ورود: ${formatDuration(draft.earlyInMinutes)}" to scheme.tertiary)
            if (draft.lateInMinutes > 0)
                add("تاخیر در ورود: ${formatDuration(draft.lateInMinutes)}" to scheme.error)
            if (draft.earlyOutMinutes > 0)
                add("تعجیل در خروج: ${formatDuration(draft.earlyOutMinutes)}" to scheme.error)
            if (draft.lateOutMinutes > 0)
                add("تاخیر در خروج: ${formatDuration(draft.lateOutMinutes)}" to scheme.tertiary)
        }

        if (chips.isEmpty()) {
            Text(
                "ورود و خروج مطابق ساعت استاندارد شیفت است.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        } else {
            // این‌ها نشانگرند نه دکمه؛ پس از Surface غیرقابل‌کلیک استفاده می‌کنیم
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                chips.forEach { (text, color) ->
                    Surface(
                        color = color.copy(alpha = 0.12f),
                        contentColor = color,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
