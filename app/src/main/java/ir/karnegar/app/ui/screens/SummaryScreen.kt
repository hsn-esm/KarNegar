package ir.karnegar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.karnegar.app.calendar.JalaliDate
import ir.karnegar.app.calendar.PersianDate
import ir.karnegar.app.calendar.toPersianDigits
import ir.karnegar.app.model.MonthlySummary
import ir.karnegar.app.model.ShiftType
import ir.karnegar.app.model.formatClock
import ir.karnegar.app.model.formatDuration
import ir.karnegar.app.ui.components.LabeledValue
import ir.karnegar.app.ui.components.MonthNavigator
import ir.karnegar.app.ui.components.SectionCard
import ir.karnegar.app.ui.components.shiftColor

/**
 * تب خلاصه کارکرد — کل و جز به جز، با امکان جابه‌جایی بین ماه‌ها.
 */
@Composable
fun SummaryScreen(
    summary: MonthlySummary,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionCard {
            MonthNavigator(
                year = summary.year,
                month = summary.month,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
                subtitle = "${summary.shiftCount.toPersianDigits()} شیفت ثبت‌شده"
            )
        }

        // ---------- کل کارکرد ----------
        SectionCard {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "کل کارکرد ${summary.monthTitle}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (summary.shiftCount == 0) "—" else formatDuration(summary.totalMinutes),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    if (summary.shiftCount == 0) "شیفتی ثبت نشده" else "ساعت",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---------- جز به جز ----------
        SectionCard(title = "کارکرد به تفکیک شیفت") {
            if (summary.shiftCount == 0) {
                Text(
                    "برای این ماه شیفتی ثبت نشده است.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                )
            } else {
                ShiftType.entries.forEach { type ->
                    val minutes = summary.minutesOf(type)
                    val count = summary.countOf(type)
                    // هم‌راستا با خروجی گزارش: شیفت‌های بدون کارکرد نمایش داده نمی‌شوند
                    if (minutes == 0 && count == 0) return@forEach
                    LabeledValue(
                        label = "کارکرد ${type.title}" +
                                if (count > 0) "  (${count.toPersianDigits()} شیفت)" else "",
                        value = "${formatDuration(minutes)} ساعت",
                        leading = {
                            Box(
                                Modifier.size(10.dp).clip(CircleShape).background(shiftColor(type))
                            )
                        }
                    )
                    // نوار نسبت — فقط برای شیفت‌هایی که کارکرد دارند
                    if (minutes > 0 && summary.totalMinutes > 0) {
                        val fraction = minutes.toFloat() / summary.totalMinutes
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(shiftColor(type))
                            )
                        }
                    }
                }
                HorizontalDivider()
                LabeledValue(
                    label = "کل کارکرد",
                    value = "${formatDuration(summary.totalMinutes)} ساعت",
                    emphasize = true
                )
            }
        }

        // ---------- تعجیل و تاخیر ----------
        if (summary.shiftCount > 0) {
            SectionCard(title = "تعجیل و تاخیر") {
                LabeledValue("جمع تعجیل در ورود", "${formatDuration(summary.earlyInMinutes)} ساعت")
                LabeledValue("جمع تاخیر در ورود", "${formatDuration(summary.lateInMinutes)} ساعت")
                LabeledValue("جمع تعجیل در خروج", "${formatDuration(summary.earlyOutMinutes)} ساعت")
                LabeledValue("جمع تاخیر در خروج", "${formatDuration(summary.lateOutMinutes)} ساعت")
                HorizontalDivider()
                LabeledValue(
                    "مجموع ساعت استاندارد شیفت‌ها",
                    "${formatDuration(summary.standardMinutes)} ساعت"
                )
                LabeledValue(
                    "مانده (واقعی منهای استاندارد)",
                    "${formatDuration(summary.balanceMinutes)} ساعت",
                    emphasize = true
                )
            }
        }

        // ---------- تعطیل‌کاری و کارکرد شب ----------
        // این‌ها برشی دیگر از همان کل کارکردند، نه چیزی افزون بر آن؛ پس جدا از
        // کارت «تفکیک شیفت» می‌آیند تا با هم جمع زده نشوند
        if (summary.shiftCount > 0) {
            SectionCard(title = "تعطیل‌کاری و کارکرد شب") {
                LabeledValue(
                    label = "تعطیل‌کاری",
                    value = "${formatDuration(summary.holidayWorkMinutes)} ساعت",
                    emphasize = true
                )
                Text(
                    "ساعاتی که در جمعه‌ها و تعطیلات رسمی کار شده. برای شیفت شب، هر " +
                            "طرف نیمه‌شب جدا سنجیده می‌شود.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                LabeledValue(
                    label = "کارکرد شب (۲۳:۳۰ تا ۰۷:۳۰)",
                    value = "${formatDuration(summary.nightWorkMinutes)} ساعت"
                )
                LabeledValue(
                    label = "کارکرد معمولی",
                    value = "${formatDuration(summary.dayWorkMinutes)} ساعت"
                )
            }
        }

        // ---------- ریز روزها ----------
        if (summary.entries.isNotEmpty()) {
            SectionCard(title = "ریز کارکرد روزانه") {
                summary.entries.forEach { e ->
                    val d = JalaliDate.fromKey(e.dateKey)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape).background(shiftColor(e.type))
                        )
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(
                                "${d.day.toPersianDigits()} ${PersianDate.MONTH_NAMES[d.month - 1]} — ${e.type.title}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${formatClock(e.actualInMinutes)} تا ${formatClock(e.actualOutMinutes)}" +
                                        if (e.actualOutMinutes >= 24 * 60) " (فردا)" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            formatDuration(e.workedMinutes),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}
