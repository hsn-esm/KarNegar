package ir.karnegar.app.model

import ir.karnegar.app.calendar.Holidays
import ir.karnegar.app.calendar.JalaliDate
import ir.karnegar.app.calendar.PersianDate
import ir.karnegar.app.calendar.toPersianDigits
import ir.karnegar.app.data.RemoteHoliday

/**
 * خلاصه‌ی کارکرد یک ماه شمسی — هم کل و هم جز به جز بر اساس نوع شیفت.
 */
data class MonthlySummary(
    val year: Int,
    val month: Int,
    val totalMinutes: Int,
    val perShiftMinutes: Map<ShiftType, Int>,
    val perShiftCount: Map<ShiftType, Int>,
    val shiftCount: Int,
    val standardMinutes: Int,
    val earlyInMinutes: Int,
    val lateInMinutes: Int,
    val earlyOutMinutes: Int,
    val lateOutMinutes: Int,
    /** کارکردی که در روزهای تعطیل انجام شده — جمعه‌ها و تعطیلات رسمی */
    val holidayWorkMinutes: Int = 0,
    /** کارکرد در پنجره‌ی ۲۳:۳۰ تا ۰۷:۳۰ بامداد */
    val nightWorkMinutes: Int = 0,
    val entries: List<ShiftEntry>
) {
    val monthTitle: String get() = "${PersianDate.MONTH_NAMES[month - 1]} ${year.toPersianDigits()}"

    /** اختلاف کارکرد واقعی با مجموع ساعت استاندارد شیفت‌های ثبت‌شده */
    val balanceMinutes: Int get() = totalMinutes - standardMinutes

    /** کارکرد معمولی — هر چه در پنجره‌ی شب نبوده */
    val dayWorkMinutes: Int get() = totalMinutes - nightWorkMinutes

    fun minutesOf(type: ShiftType): Int = perShiftMinutes[type] ?: 0
    fun countOf(type: ShiftType): Int = perShiftCount[type] ?: 0
}

object WorkCalculator {

    /**
     * جمع‌بندی کارکرد ماه. کارکرد بر مبنای «ساعات واقعی» محاسبه می‌شود،
     * یعنی اختلاف ورود و خروج ثبت‌شده — و تعجیل/تاخیر جداگانه گزارش می‌شود.
     *
     * شیفت شب به روزی نسبت داده می‌شود که در آن شروع شده است، ولی تعطیل‌کاری‌اش
     * روزبه‌روز سنجیده می‌شود: بخشِ پیش از نیمه‌شب با تعطیلیِ روز شیفت و بخشِ پس از
     * نیمه‌شب با تعطیلیِ روز بعد. پس شیفت شبِ پنج‌شنبه، هفت‌ساعت‌ونیمِ بامداد جمعه را
     * تعطیل‌کاری می‌آورد بدون آن‌که دو ساعت اولش تعطیل‌کاری شود.
     *
     * @param overrides تنظیم دستی تعطیلی از سوی کاربر
     * @param remoteHolidays تعطیلات رسمی دریافتی از اینترنت؛ خالی باشد، جدول درون‌برنامه‌ای مبناست
     */
    fun summarize(
        year: Int,
        month: Int,
        all: Collection<ShiftEntry>,
        overrides: Map<Int, Boolean> = emptyMap(),
        remoteHolidays: Map<Int, RemoteHoliday> = emptyMap()
    ): MonthlySummary {
        val monthEntries = all
            .filter { JalaliDate.fromKey(it.dateKey).let { d -> d.year == year && d.month == month } }
            .filter { it.isValid }
            .sortedBy { it.dateKey }

        val perMinutes = linkedMapOf<ShiftType, Int>()
        val perCount = linkedMapOf<ShiftType, Int>()
        var total = 0
        var standard = 0
        var earlyIn = 0
        var lateIn = 0
        var earlyOut = 0
        var lateOut = 0
        var holidayWork = 0
        var nightWork = 0

        for (e in monthEntries) {
            total += e.workedMinutes
            standard += e.type.standardMinutes
            perMinutes[e.type] = (perMinutes[e.type] ?: 0) + e.workedMinutes
            perCount[e.type] = (perCount[e.type] ?: 0) + 1
            earlyIn += e.earlyInMinutes
            lateIn += e.lateInMinutes
            earlyOut += e.earlyOutMinutes
            lateOut += e.lateOutMinutes

            val day = JalaliDate.fromKey(e.dateKey)
            holidayWork += e.holidayWorkMinutes(
                isShiftDayOff = Holidays.isDayOff(day, overrides, remoteHolidays),
                // روز بعد فقط برای شیفت‌هایی معنا دارد که از نیمه‌شب می‌گذرند؛
                // در بقیه minutesAfterMidnight صفر است و این مقدار بی‌اثر می‌ماند
                isNextDayOff = Holidays.isDayOff(day.plusDays(1), overrides, remoteHolidays)
            )
            nightWork += e.nightWorkMinutes
        }

        return MonthlySummary(
            year = year,
            month = month,
            totalMinutes = total,
            perShiftMinutes = perMinutes,
            perShiftCount = perCount,
            shiftCount = monthEntries.size,
            standardMinutes = standard,
            earlyInMinutes = earlyIn,
            lateInMinutes = lateIn,
            earlyOutMinutes = earlyOut,
            lateOutMinutes = lateOut,
            holidayWorkMinutes = holidayWork,
            nightWorkMinutes = nightWork,
            entries = monthEntries
        )
    }
}
