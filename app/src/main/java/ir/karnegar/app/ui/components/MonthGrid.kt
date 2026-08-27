package ir.karnegar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.karnegar.app.calendar.Holidays
import ir.karnegar.app.calendar.JalaliDate
import ir.karnegar.app.calendar.PersianDate
import ir.karnegar.app.calendar.toPersianDigits
import ir.karnegar.app.data.RemoteHoliday
import ir.karnegar.app.model.ShiftEntry
import ir.karnegar.app.model.ShiftType
import ir.karnegar.app.ui.theme.ShiftPalette

/** رنگ هر شیفت، متناسب با تم روشن یا تاریک */
@Composable
fun shiftColor(type: ShiftType): Color {
    val dark = isSystemInDarkTheme()
    return when (type) {
        ShiftType.MORNING -> if (dark) ShiftPalette.darkMorning else ShiftPalette.lightMorning
        ShiftType.AFTERNOON -> if (dark) ShiftPalette.darkAfternoon else ShiftPalette.lightAfternoon
        ShiftType.EVENING -> if (dark) ShiftPalette.darkEvening else ShiftPalette.lightEvening
        ShiftType.HOLIDAY -> if (dark) ShiftPalette.darkHoliday else ShiftPalette.lightHoliday
        ShiftType.NIGHT -> if (dark) ShiftPalette.darkNight else ShiftPalette.lightNight
    }
}

/**
 * شبکه‌ی تقویم شمسی یک ماه.
 * هفته از شنبه شروع می‌شود؛ جمعه‌ها و تعطیلات رسمی با رنگ متفاوت مشخص‌اند
 * و روزهایی که شیفت ثبت شده یک نقطه‌ی رنگی زیر عدد دارند.
 */
@Composable
fun MonthGrid(
    year: Int,
    month: Int,
    shifts: Map<Int, ShiftEntry>,
    holidayOverrides: Map<Int, Boolean>,
    remoteHolidays: Map<Int, RemoteHoliday> = emptyMap(),
    selectedKey: Int?,
    onDayClick: (JalaliDate) -> Unit,
    onDayLongClick: (JalaliDate) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val daysInMonth = PersianDate.monthLength(year, month)
    val firstDay = JalaliDate(year, month, 1)
    val leading = firstDay.dayOfWeek // ۰ = شنبه
    val today = JalaliDate.today()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {

        // نام روزهای هفته
        Row(Modifier.fillMaxWidth()) {
            for (i in 0..6) {
                Text(
                    text = PersianDate.WEEK_DAY_SHORT[i],
                    style = MaterialTheme.typography.labelMedium,
                    color = if (i == 6) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        val totalCells = leading + daysInMonth
        val weeks = (totalCells + 6) / 7

        for (week in 0 until weeks) {
            Row(Modifier.fillMaxWidth()) {
                for (dow in 0..6) {
                    val cellIndex = week * 7 + dow
                    val dayNumber = cellIndex - leading + 1
                    Box(Modifier.weight(1f).padding(2.dp)) {
                        if (dayNumber in 1..daysInMonth) {
                            val date = JalaliDate(year, month, dayNumber)
                            DayCell(
                                date = date,
                                entry = shifts[date.key],
                                isDayOff = Holidays.isDayOff(date, holidayOverrides, remoteHolidays),
                                isOverridden = holidayOverrides.containsKey(date.key),
                                isToday = date.key == today.key,
                                isSelected = date.key == selectedKey,
                                onClick = { onDayClick(date) },
                                onLongClick = { onDayLongClick(date) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(
    date: JalaliDate,
    entry: ShiftEntry?,
    isDayOff: Boolean,
    isOverridden: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shiftAlpha = if (isSystemInDarkTheme()) 0.30f else 0.14f
    val bg = when {
        isSelected -> scheme.primary
        entry != null -> shiftColor(entry.type).copy(alpha = shiftAlpha)
        isDayOff -> scheme.errorContainer.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    val fg = when {
        isSelected -> scheme.onPrimary
        isDayOff -> scheme.error
        else -> scheme.onSurface
    }
    // خانه فقط عدد شمسی را نشان می‌دهد، ولی صفحه‌خوان تاریخ قمری و میلادی را هم
    // می‌خواند تا همان اطلاعاتی که در برگه‌ی جزئیات روز هست از این‌جا هم شنیده شود
    val description = buildString {
        append(date.longFormatted())
        append("، قمری ${Holidays.hijriFullLabel(date)}")
        append("، میلادی ${Holidays.gregorianLabel(date)}")
        if (isDayOff) append("، تعطیل")
        if (isOverridden) append("، دستی تنظیم شده")
        if (entry != null) append("، شیفت ${entry.type.title}")
    }

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .then(
                    if (isToday && !isSelected)
                        Modifier.border(1.4.dp, scheme.primary, RoundedCornerShape(12.dp))
                    else Modifier
                )
                .combinedClickable(
                    onClickLabel = "ثبت شیفت",
                    onLongClickLabel = "تغییر وضعیت تعطیلی",
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .semantics { contentDescription = description }
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = date.day.toPersianDigits(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = fg
            )
            Box(Modifier.padding(top = 3.dp).size(6.dp)) {
                if (entry != null) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) scheme.onPrimary else shiftColor(entry.type))
                    )
                }
            }
        }
        // نشانه‌ی روزهایی که وضعیت تعطیلی‌شان دستی تنظیم شده
        if (isOverridden) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) scheme.onPrimary else scheme.tertiary)
            )
        }
    }
}

/**
 * راهنمای رنگ شیفت‌ها — همیشه در یک سطر.
 *
 * پنج برچسب کوتاه است ولی «بعدازظهر» بلندترینشان است و در FlowRow به سطر دوم
 * می‌افتاد. این‌جا با Row و SpaceBetween در یک سطر می‌مانند و برای اطمینان
 * maxLines = 1 گذاشته شده تا در نمایشگرهای باریک هم سطر دوم نسازند.
 */
@Composable
fun ShiftLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (type in ShiftType.entries) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(shiftColor(type))
                )
                Text(
                    text = type.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 3.dp)
                )
            }
        }
    }
}
