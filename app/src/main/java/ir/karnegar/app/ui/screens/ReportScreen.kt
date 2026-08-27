package ir.karnegar.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ir.karnegar.app.calendar.JalaliDate
import ir.karnegar.app.calendar.ltrIsolate
import ir.karnegar.app.calendar.toPersianDigits
import ir.karnegar.app.export.ReportData
import ir.karnegar.app.export.ReportExporter
import ir.karnegar.app.model.MonthlySummary
import ir.karnegar.app.model.ShiftType
import ir.karnegar.app.model.formatDuration
import ir.karnegar.app.ui.components.LabeledValue
import ir.karnegar.app.ui.components.MonthNavigator
import ir.karnegar.app.ui.components.SectionCard
import java.io.File
import java.util.Calendar

/**
 * تب گزارش‌گیری — سربرگ سه‌بخشی (نام / ماه / تاریخ خروجی) و خروجی PDF یا اکسل.
 */
@Composable
fun ReportScreen(
    fullName: String,
    summary: MonthlySummary,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lastFile by remember { mutableStateOf<Pair<File, ReportExporter.Format>?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun nowMinutes(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    fun buildData() = ReportData(
        fullName = fullName,
        summary = summary,
        generatedOn = JalaliDate.today(),
        generatedAtMinutes = nowMinutes()
    )

    /** ساخت فایل روی نخ پس‌زمینه تا رابط کاربری در ماه‌های پرشیفت قطع نشود */
    fun export(format: ReportExporter.Format) {
        if (busy) return
        busy = true
        val data = buildData()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ReportExporter.export(context, data, format) }
            }
            busy = false
            result
                .onSuccess { file ->
                    lastFile = file to format
                    ReportExporter.share(context, file, format)
                }
                .onFailure {
                    Toast.makeText(
                        context,
                        "خطا در ساخت فایل: ${it.message ?: "نامشخص"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---------- سربرگ سه‌بخشی ----------
        SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                // راست: نام و فامیل
                Column(Modifier.weight(1.4f)) {
                    Text(
                        "کارمند",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        fullName.ifBlank { "—" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // وسط: ماه انتخابی
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "ماه گزارش",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        summary.monthTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
                // چپ: تاریخ خروجی
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        "تاریخ خروجی",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        // سال/ماه/روز؛ جداساز دوجهته لازم است وگرنه در رابط RTL
                        // اسلش‌ها ترتیب پاره‌ها را برمی‌گردانند و ۳۱/۰۵/۱۴۰۵ می‌شود
                        JalaliDate.today().formatted().ltrIsolate(),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // ---------- انتخاب ماه ----------
        SectionCard(title = "انتخاب ماه") {
            MonthNavigator(
                year = summary.year,
                month = summary.month,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
                subtitle = "${summary.shiftCount.toPersianDigits()} شیفت  •  " +
                        "${formatDuration(summary.totalMinutes)} ساعت"
            )
        }

        // ---------- پیش‌نمایش محتوای گزارش ----------
        SectionCard(title = "محتوای گزارش") {
            LabeledValue("کل کارکرد", "${formatDuration(summary.totalMinutes)} ساعت", emphasize = true)
            HorizontalDivider()
            ShiftType.entries.forEach { type ->
                // فقط شیفت‌هایی که در گزارش می‌آیند نمایش داده می‌شوند
                if (summary.minutesOf(type) == 0 && summary.countOf(type) == 0) return@forEach
                LabeledValue(
                    "کارکرد ${type.title}",
                    "${formatDuration(summary.minutesOf(type))} ساعت"
                )
            }
            HorizontalDivider()
            LabeledValue("تعداد روزهای ثبت‌شده در گزارش", "${summary.entries.size.toPersianDigits()} روز")
        }

        // ---------- خروجی ----------
        SectionCard(title = "دریافت خروجی") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { export(ReportExporter.Format.PDF) },
                    enabled = !busy && summary.shiftCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(18.dp))
                    Text("خروجی PDF", Modifier.padding(start = 6.dp))
                }
                Button(
                    onClick = { export(ReportExporter.Format.XLSX) },
                    enabled = !busy && summary.shiftCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.TableChart, null, Modifier.size(18.dp))
                    Text("خروجی اکسل", Modifier.padding(start = 6.dp))
                }
            }

            if (busy) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        "در حال ساخت فایل…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            if (summary.shiftCount == 0) {
                Text(
                    "برای این ماه شیفتی ثبت نشده؛ ابتدا از تب تقویم شیفت ثبت کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            lastFile?.let { (file, format) ->
                HorizontalDivider()
                Text(
                    "آخرین فایل ساخته‌شده: ${file.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { ReportExporter.open(context, file, format) },
                        modifier = Modifier.weight(1f)
                    ) { Text("باز کردن") }
                    OutlinedButton(
                        onClick = { ReportExporter.share(context, file, format) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Share, null, Modifier.size(16.dp))
                        Text("اشتراک‌گذاری", Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}
