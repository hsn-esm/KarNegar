package ir.karnegar.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.karnegar.app.calendar.Holidays
import ir.karnegar.app.calendar.JalaliDate
import ir.karnegar.app.calendar.PersianDate
import ir.karnegar.app.calendar.toPersianDigits
import ir.karnegar.app.model.ShiftEntry
import ir.karnegar.app.model.ShiftType
import ir.karnegar.app.model.formatClock
import ir.karnegar.app.model.formatDuration
import ir.karnegar.app.ui.KarNegarState
import ir.karnegar.app.ui.components.LabeledValue
import ir.karnegar.app.ui.components.MonthGrid
import ir.karnegar.app.ui.components.MonthNavigator
import ir.karnegar.app.ui.components.SectionCard
import ir.karnegar.app.ui.components.ShiftEditorSheet
import ir.karnegar.app.ui.components.ShiftLegend

/**
 * تب تقویم — شبکه‌ی ماه، ثبت شیفت با کلیک روی روز، و کارت وضعیت امروز.
 */
@Composable
fun CalendarScreen(
    state: KarNegarState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSaveShift: (ShiftEntry) -> Unit,
    onDeleteShift: (Int) -> Unit,
    onToggleHoliday: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // روز انتخاب‌شده به صورت کلید عددی نگه داشته می‌شود تا با rememberSaveable ذخیره شود
    var selectedKey by rememberSaveable { mutableStateOf<Int?>(null) }
    val selected = selectedKey?.let { JalaliDate.fromKey(it) }
    val today = JalaliDate.today()
    val monthEntries = state.shifts.values.filter {
        val d = JalaliDate.fromKey(it.dateKey)
        d.year == state.visibleYear && d.month == state.visibleMonth
    }
    val monthMinutes = monthEntries.sumOf { it.workedMinutes }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---------- کارت امروز ----------
        SectionCard {
            Row(Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Rounded.Today,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    // فقط روز و تاریخ شمسی؛ تاریخ قمری و میلادی داخل خانه‌های تقویم است
                    Text(today.longFormatted(), style = MaterialTheme.typography.titleMedium)
                    Holidays.dayOffTitle(today, state.holidayOverrides, state.eventRules)
                        ?.let { title ->
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                }
                TextButton(onClick = { selectedKey = today.key }) {
                    Text(if (state.shifts.containsKey(today.key)) "ویرایش امروز" else "ثبت امروز")
                }
            }
            val todayEntry = state.shifts[today.key]
            if (todayEntry != null) {
                HorizontalDivider()
                LabeledValue(
                    label = "شیفت ${todayEntry.type.title}",
                    value = "${formatClock(todayEntry.actualInMinutes)} – " +
                            "${formatClock(todayEntry.actualOutMinutes)}  •  " +
                            "${formatDuration(todayEntry.workedMinutes)} ساعت"
                )
            }
        }

        // ---------- تقویم ----------
        SectionCard {
            MonthNavigator(
                year = state.visibleYear,
                month = state.visibleMonth,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
                subtitle = "${monthEntries.size.toPersianDigits()} شیفت  •  " +
                        "${formatDuration(monthMinutes)} ساعت"
            )
            MonthGrid(
                year = state.visibleYear,
                month = state.visibleMonth,
                shifts = state.shifts,
                holidayOverrides = state.holidayOverrides,
                eventRules = state.eventRules,
                selectedKey = selected?.key,
                onDayClick = { selectedKey = it.key },
                onDayLongClick = { onToggleHoliday(it.key) }
            )
            HorizontalDivider()
            ShiftLegend()
            Text(
                text = if (state.sync.hasData)
                    "تعطیلات از تقویم رسمی آنلاین گرفته شده است. نگه‌داشتن روی هر روز، آن را تعطیل یا کاری می‌کند."
                else
                    "تقویم درون‌برنامه‌ای در حال استفاده است. نگه‌داشتن روی هر روز، آن را تعطیل یا کاری می‌کند.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---------- مناسبت‌های ماه ----------
        val holidays = buildList {
            for (day in 1..PersianDate.monthLength(state.visibleYear, state.visibleMonth)) {
                val d = JalaliDate(state.visibleYear, state.visibleMonth, day)
                val title = Holidays.occasionTitle(d, state.eventRules)
                if (title != null) add(Triple(d, title, Holidays.isDayOff(d, state.holidayOverrides, state.eventRules)))
            }
        }
        if (holidays.isNotEmpty()) {
            SectionCard(title = "تعطیلات و مناسبت‌های این ماه") {
                holidays.forEach { (d, title, isOff) ->
                    LabeledValue(
                        label = title,
                        value = "${d.day.toPersianDigits()} ${PersianDate.MONTH_NAMES[d.month - 1]}" +
                                if (isOff) "" else "  (غیرتعطیل)"
                    )
                }
            }
        }

        // ---------- شیفت‌های ثبت‌شده‌ی ماه ----------
        if (monthEntries.isNotEmpty()) {
            SectionCard(title = "شیفت‌های ثبت‌شده") {
                monthEntries.sortedBy { it.dateKey }.forEach { e ->
                    val d = JalaliDate.fromKey(e.dateKey)
                    LabeledValue(
                        label = "${d.day.toPersianDigits()} ${PersianDate.MONTH_NAMES[d.month - 1]} — ${e.type.title}",
                        value = formatDuration(e.workedMinutes)
                    )
                }
            }
        }
    }

    // ---------- برگه‌ی ثبت شیفت ----------
    selected?.let { date ->
        ShiftEditorSheet(
            date = date,
            existing = state.shifts[date.key],
            holidayOverrides = state.holidayOverrides,
            eventRules = state.eventRules,
            onDismiss = { selectedKey = null },
            onSave = { onSaveShift(it); selectedKey = null },
            onDelete = { onDeleteShift(date.key); selectedKey = null }
        )
    }
}
