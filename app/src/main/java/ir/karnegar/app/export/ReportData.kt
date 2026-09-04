package ir.karnegar.app.export

import ir.karnegar.app.calendar.JalaliDate
import ir.karnegar.app.calendar.PersianDate
import ir.karnegar.app.calendar.toPersianDigits
import ir.karnegar.app.model.MonthlySummary
import ir.karnegar.app.model.ShiftType
import ir.karnegar.app.model.formatClock
import ir.karnegar.app.model.formatDuration

/**
 * ساختار داده‌ی گزارش — مشترک بین خروجی PDF و اکسل تا هر دو دقیقاً یک محتوا بدهند.
 */
data class ReportData(
    val fullName: String,
    val summary: MonthlySummary,
    val generatedOn: JalaliDate,
    val generatedAtMinutes: Int
) {
    val monthTitle: String get() = summary.monthTitle
    val generatedLabel: String get() = "${generatedOn.formatted()} - ${formatClock(generatedAtMinutes)}"

    val detailHeader: List<String> = listOf(
        "تاریخ", "روز هفته", "نوع شیفت",
        "ورود", "خروج", "کارکرد", "تعجیل ورود", "تاخیر ورود", "تعجیل خروج", "تاخیر خروج", "توضیح"
    )

    /** سطرهای جز به جز روزهای ثبت‌شده */
    fun detailRows(): List<List<String>> = summary.entries.map { e ->
        val date = JalaliDate.fromKey(e.dateKey)
        listOf(
            date.formatted(),
            PersianDate.WEEK_DAY_NAMES[date.dayOfWeek],
            e.type.title,
            formatClock(e.actualInMinutes),
            formatClock(e.actualOutMinutes) +
                    if (e.actualOutMinutes >= 24 * 60) " (فردا)" else "",
            formatDuration(e.workedMinutes),
            if (e.earlyInMinutes > 0) formatDuration(e.earlyInMinutes) else "-",
            if (e.lateInMinutes > 0) formatDuration(e.lateInMinutes) else "-",
            if (e.earlyOutMinutes > 0) formatDuration(e.earlyOutMinutes) else "-",
            if (e.lateOutMinutes > 0) formatDuration(e.lateOutMinutes) else "-",
            e.note.ifBlank { "-" }
        )
    }

    /** خطوط جمع‌بندی: عنوان و مقدار */
    fun summaryLines(): List<Pair<String, String>> {
        val lines = mutableListOf<Pair<String, String>>()
        lines += "کل کارکرد" to "${formatDuration(summary.totalMinutes)} ساعت"
        for (type in ShiftType.entries) {
            // شیفت شب این‌جا نمی‌آید: عنوانش «کارکرد شب» می‌شد و با سطر واقعیِ
            // کارکرد شب (۲۳:۳۰ تا ۰۷:۳۰) اشتباه گرفته می‌شد. مدت شیفت شب ده ساعت
            // است ولی کارکرد شبِ آن هشت ساعت — دو عدد متفاوت با یک عنوان.
            if (type == ShiftType.NIGHT) continue
            val minutes = summary.minutesOf(type)
            if (minutes == 0 && summary.countOf(type) == 0) continue
            lines += "کارکرد ${type.title}" to
                    "${formatDuration(minutes)} ساعت (${summary.countOf(type).toPersianDigits()} شیفت)"
        }
        lines += "تعداد کل شیفت‌ها" to "${summary.shiftCount.toPersianDigits()} شیفت"
        // برشی دیگر از همان کل کارکرد، نه افزون بر آن
        lines += "تعطیل‌کاری" to "${formatDuration(summary.holidayWorkMinutes)} ساعت"
        // تنها سطر کارکرد شب در گزارش، بر پایه‌ی پنجره‌ی زمانی نه نوع شیفت
        lines += "کارکرد شب (۲۳:۳۰ تا ۰۷:۳۰)" to "${formatDuration(summary.nightWorkMinutes)} ساعت"
        lines += "کارکرد معمولی" to "${formatDuration(summary.dayWorkMinutes)} ساعت"
        lines += "مجموع ساعت استاندارد" to "${formatDuration(summary.standardMinutes)} ساعت"
        lines += "مانده (واقعی منهای استاندارد)" to "${formatDuration(summary.balanceMinutes)} ساعت"
        lines += "جمع تعجیل در ورود" to formatDuration(summary.earlyInMinutes)
        lines += "جمع تاخیر در ورود" to formatDuration(summary.lateInMinutes)
        lines += "جمع تعجیل در خروج" to formatDuration(summary.earlyOutMinutes)
        lines += "جمع تاخیر در خروج" to formatDuration(summary.lateOutMinutes)
        return lines
    }

    /** نام فایل پیشنهادی بدون پسوند */
    fun fileBaseName(): String =
        "KarNegar_${summary.year}_${"%02d".format(summary.month)}"
}
