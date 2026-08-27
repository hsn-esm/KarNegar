package ir.karnegar.app

import ir.karnegar.app.calendar.Holidays
import ir.karnegar.app.calendar.JalaliDate
import ir.karnegar.app.calendar.PersianDate
import ir.karnegar.app.data.HolidaySyncState
import ir.karnegar.app.data.RemoteHoliday
import ir.karnegar.app.export.ReportData
import ir.karnegar.app.model.ShiftEntry
import ir.karnegar.app.model.ShiftType
import ir.karnegar.app.model.WorkCalculator
import ir.karnegar.app.model.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersianDateTest {

    @Test
    fun `نوروز ۱۴۰۴ برابر ۲۱ مارس ۲۰۲۵ است`() {
        val jdn = PersianDate.toJdn(1404, 1, 1)
        val g = PersianDate.jdnToGregorian(jdn)
        assertEquals(2025, g.gy)
        assertEquals(3, g.gm)
        assertEquals(21, g.gd)
    }

    @Test
    fun `رفت و برگشت تبدیل شمسی درست است`() {
        var date = JalaliDate(1400, 1, 1)
        repeat(2000) {
            val back = PersianDate.fromJdn(date.jdn)
            assertEquals(date, back)
            date = date.plusDays(1)
        }
    }

    @Test
    fun `طول ماه ها درست است`() {
        assertEquals(31, PersianDate.monthLength(1405, 1))
        assertEquals(30, PersianDate.monthLength(1405, 7))
        assertEquals(29, PersianDate.monthLength(1405, 12))
        assertEquals(30, PersianDate.monthLength(1403, 12)) // ۱۴۰۳ کبیسه است
    }

    @Test
    fun `سال کبیسه شمسی درست تشخیص داده می شود`() {
        assertTrue(PersianDate.isLeapYear(1403))
        assertFalse(PersianDate.isLeapYear(1404))
        assertFalse(PersianDate.isLeapYear(1405))
        assertTrue(PersianDate.isLeapYear(1408))
    }

    @Test
    fun `کلید تاریخ رفت و برگشت می شود`() {
        val d = JalaliDate(1405, 5, 12)
        assertEquals(14050512, d.key)
        assertEquals(d, JalaliDate.fromKey(14050512))
    }

    @Test
    fun `کلید خراب به بازه معتبر محدود می شود`() {
        val d = JalaliDate.fromKey(14051340) // ماه ۱۳ و روز ۴۰ وجود ندارد
        assertTrue(d.month in 1..12)
        assertTrue(d.day in 1..31)
    }

    @Test
    fun `روز هفته درست محاسبه می شود`() {
        // ۱ فروردین ۱۴۰۴ جمعه بود و ۳۱ مرداد ۱۴۰۵ شنبه است
        assertEquals(6, JalaliDate(1404, 1, 1).dayOfWeek)
        assertEquals(0, JalaliDate(1405, 5, 31).dayOfWeek)
        assertTrue(JalaliDate(1404, 1, 1).isFriday)
    }
}

class HijriConversionTest {

    @Test
    fun `رفت و برگشت تبدیل قمری درست است`() {
        var jdn = PersianDate.toJdn(1400, 1, 1)
        repeat(4000) {
            val h = PersianDate.jdnToHijri(jdn)
            assertTrue("ماه قمری بی‌اعتبار: ${h.month}", h.month in 1..12)
            assertTrue("روز قمری بی‌اعتبار: ${h.day}", h.day in 1..30)
            assertEquals(jdn, PersianDate.hijriToJdn(h.year, h.month, h.day))
            jdn += 1
        }
    }

    @Test
    fun `آخرین روز سال قمری صفر محرم خوانده نمی شود`() {
        // اگر تصحیح یک‌روزه در jdnToHijri نباشد، این روز «۰ محرم» سال بعد می‌شود
        val h = PersianDate.jdnToHijri(2461208L)
        assertTrue(h.day >= 1)
        assertEquals(12, h.month)
    }

    @Test
    fun `تقویم قمری ایران با تاریخ اعلام شده منطبق است`() {
        // نقطه‌ی سنجش رسمی: ۲۱ مرداد ۱۴۰۵ = ۱۲ اوت ۲۰۲۶ = ۲۸ صفر ۱۴۴۸
        val h = JalaliDate(1405, 5, 21).toHijri()
        assertEquals(1448, h.year)
        assertEquals(2, h.month)
        assertEquals(28, h.day)
    }

    @Test
    fun `تبدیل قمری ایران هم رفت و برگشت سالم است`() {
        // نباید ماه یا روز بی‌اعتبار تولید شود، وگرنه نام ماه‌ها اندیس‌شکن می‌شود
        var d = JalaliDate(1400, 1, 1)
        repeat(4000) {
            val h = d.toHijri()
            assertTrue("ماه قمری بی‌اعتبار: ${h.month}", h.month in 1..12)
            assertTrue("روز قمری بی‌اعتبار: ${h.day}", h.day in 1..30)
            d = d.plusDays(1)
        }
    }
}

class HolidaysTest {

    @Test
    fun `نوروز تعطیل رسمی است`() {
        assertEquals("جشن نوروز / سال نو", Holidays.officialHolidayTitle(JalaliDate(1405, 1, 1)))
        assertTrue(Holidays.isDayOff(JalaliDate(1405, 1, 1), emptyMap()))
    }

    @Test
    fun `جمعه همیشه تعطیل است`() {
        val friday = JalaliDate(1404, 1, 1) // جمعه
        assertTrue(Holidays.isDayOff(friday, emptyMap()))
        assertEquals("جشن نوروز / سال نو", Holidays.dayOffTitle(friday, emptyMap()))
    }

    @Test
    fun `تنظیم دستی بر لیست پیش فرض اولویت دارد`() {
        val nowruz = JalaliDate(1405, 1, 1)
        assertFalse(Holidays.isDayOff(nowruz, mapOf(nowruz.key to false)))
        val workday = JalaliDate(1405, 5, 31)
        assertTrue(Holidays.isDayOff(workday, mapOf(workday.key to true)))
    }

    @Test
    fun `عنوان مناسبت قمری برای هر روز سال ساخته می شود`() {
        // اگر تبدیل قمری ماه بی‌اعتبار بدهد، این حلقه با خطای اندیس می‌شکند
        var d = JalaliDate(1405, 1, 1)
        repeat(370) {
            Holidays.hijriLabel(d)
            Holidays.isDayOff(d, emptyMap())
            d = d.plusDays(1)
        }
    }

    @Test
    fun `برچسب قمری و میلادی روز جزئیات درست ساخته می شود`() {
        // نقطه‌ی سنجش همان روزی است که در HijriConversionTest لنگر شده
        val d = JalaliDate(1405, 5, 21)
        assertEquals("۲۸ صفر", Holidays.hijriLabel(d))
        assertEquals("۲۸ صفر ۱۴۴۸", Holidays.hijriFullLabel(d))
        // ارقام میلادی باید لاتین بمانند، حتی اگر locale پیش‌فرض فارسی باشد
        assertEquals("12 August 2026", Holidays.gregorianLabel(d))
    }

    @Test
    fun `سطر تاریخ های دیگر قمری را با سال و بعد میلادی می آورد`() {
        val label = Holidays.otherCalendarsLabel(JalaliDate(1405, 5, 21))
        // جداسازهای دوجهته دیده نمی‌شوند ولی در رشته هستند؛ برای مقایسه حذفشان می‌کنیم
        val plain = label.replace("⁦", "").replace("⁩", "")
        assertEquals("۲۸ صفر ۱۴۴۸ - 12 August 2026", plain)
        // بخش میلادی باید داخل جداساز باشد، وگرنه در رابط RTL سال جابه‌جا می‌شود
        assertTrue(label.contains("⁦" + "12 August 2026" + "⁩"))
    }

    @Test
    fun `برچسب میلادی برای هر روز سال بدون خطای اندیس ساخته می شود`() {
        var d = JalaliDate(1405, 1, 1)
        repeat(370) {
            Holidays.gregorianLabel(d)
            Holidays.otherCalendarsLabel(d)
            d = d.plusDays(1)
        }
    }

    // برای این آزمون‌ها به روزی نیاز داریم که جمعه نباشد، وگرنه تعطیلی جمعه
    // نتیجه را مخدوش می‌کند. تاریخ‌ها به‌جای حدس زدن، از تقویم پیدا می‌شوند.
    private fun firstNonFriday(predicate: (JalaliDate) -> Boolean): JalaliDate {
        var d = JalaliDate(1405, 1, 1)
        repeat(370) {
            if (!d.isFriday && predicate(d)) return d
            d = d.plusDays(1)
        }
        throw AssertionError("روز مناسبی برای آزمون پیدا نشد")
    }

    @Test
    fun `داده آنلاین بر جدول درون برنامه ای اولویت دارد`() {
        // روزی که در جدول پیش‌فرض تعطیل نیست، با داده‌ی آنلاین تعطیل می‌شود
        val day = firstNonFriday { !Holidays.isDayOff(it, emptyMap()) }
        val remote = mapOf(day.key to RemoteHoliday("تعطیل رسمی اعلام‌شده", true))
        assertTrue(Holidays.isDayOff(day, emptyMap(), remote))
        assertEquals("تعطیل رسمی اعلام‌شده", Holidays.dayOffTitle(day, emptyMap(), remote))
    }

    @Test
    fun `داده آنلاین می تواند تعطیلی جدول پیش فرض را لغو کند`() {
        // اگر تقویم رسمی روزی را غیرتعطیل بداند، جدول درون‌برنامه‌ای نباید آن را تعطیل کند
        val day = firstNonFriday { Holidays.isDayOff(it, emptyMap()) }
        val remote = mapOf(day.key to RemoteHoliday("مناسبت غیرتعطیل", false))
        assertFalse(Holidays.isDayOff(day, emptyMap(), remote))
        // مناسبت باید همچنان در فهرست مناسبت‌های ماه دیده شود
        assertEquals("مناسبت غیرتعطیل", Holidays.occasionTitle(day, remote))
    }

    @Test
    fun `تنظیم دستی بر داده آنلاین هم اولویت دارد`() {
        val day = firstNonFriday { !Holidays.isDayOff(it, emptyMap()) }
        val remote = mapOf(day.key to RemoteHoliday("تعطیل رسمی", true))
        assertFalse(Holidays.isDayOff(day, mapOf(day.key to false), remote))
        assertTrue(Holidays.isDayOff(day, mapOf(day.key to true), emptyMap()))
    }
}

class HolidaySyncStateTest {

    @Test
    fun `وضعیت بدون داده کهنه شمرده می شود`() {
        assertTrue(HolidaySyncState().isStale(1405))
    }

    @Test
    fun `سال دریافت نشده کهنه شمرده می شود`() {
        val now = 1_700_000_000_000L
        val state = HolidaySyncState(lastSyncAt = now, syncedYears = setOf(1404), source = "x")
        assertTrue(state.isStale(1405, now))
        assertFalse(state.isStale(1404, now))
    }

    @Test
    fun `پس از سی روز کهنه می شود`() {
        val now = 1_700_000_000_000L
        val state = HolidaySyncState(lastSyncAt = now, syncedYears = setOf(1405), source = "x")
        assertFalse(state.isStale(1405, now + 29L * 24 * 60 * 60 * 1000))
        assertTrue(state.isStale(1405, now + 31L * 24 * 60 * 60 * 1000))
    }
}

class ShiftCalculationTest {

    @Test
    fun `مدت استاندارد شیفت ها درست است`() {
        assertEquals(7 * 60 + 30, ShiftType.MORNING.standardMinutes)   // ۷:۳۰
        assertEquals(6 * 60 + 30, ShiftType.AFTERNOON.standardMinutes) // ۶:۳۰
        assertEquals(10 * 60 + 30, ShiftType.EVENING.standardMinutes)  // ۱۰:۳۰
        assertEquals(14 * 60, ShiftType.HOLIDAY.standardMinutes)       // ۱۴:۰۰
        assertEquals(10 * 60, ShiftType.NIGHT.standardMinutes)         // ۱۰:۰۰
    }

    @Test
    fun `شیفت شب از نیمه شب عبور می کند`() {
        assertTrue(ShiftType.NIGHT.crossesMidnight)
        assertFalse(ShiftType.MORNING.crossesMidnight)
    }

    @Test
    fun `تعجیل در ورود و تاخیر در خروج درست محاسبه می شود`() {
        // ورود ۰۷:۱۵ یعنی ۱۵ دقیقه تعجیل، خروج ۱۵:۳۰ یعنی ۳۰ دقیقه تاخیر
        val e = ShiftEntry(14050512, ShiftType.MORNING, 7 * 60 + 15, 15 * 60 + 30)
        assertEquals(15, e.earlyInMinutes)
        assertEquals(0, e.lateInMinutes)
        assertEquals(0, e.earlyOutMinutes)
        assertEquals(30, e.lateOutMinutes)
        assertEquals(8 * 60 + 15, e.workedMinutes)
    }

    @Test
    fun `تاخیر در ورود و تعجیل در خروج درست محاسبه می شود`() {
        // ورود ۰۷:۴۵ یعنی ۱۵ دقیقه تاخیر، خروج ۱۴:۳۰ یعنی ۳۰ دقیقه تعجیل
        val e = ShiftEntry(14050513, ShiftType.MORNING, 7 * 60 + 45, 14 * 60 + 30)
        assertEquals(0, e.earlyInMinutes)
        assertEquals(15, e.lateInMinutes)
        assertEquals(30, e.earlyOutMinutes)
        assertEquals(0, e.lateOutMinutes)
        assertEquals(6 * 60 + 45, e.workedMinutes)
    }

    @Test
    fun `شیفت شب ده ساعت کارکرد دارد`() {
        val e = ShiftEntry(14050514, ShiftType.NIGHT, 21 * 60 + 30, 24 * 60 + 7 * 60 + 30)
        assertEquals(10 * 60, e.workedMinutes)
        assertEquals(0, e.earlyInMinutes)
        assertEquals(0, e.lateOutMinutes)
        assertTrue(e.isValid)
    }

    @Test
    fun `قالب بندی مدت زمان درست است`() {
        assertEquals("۱۷۷:۳۰", formatDuration(177 * 60 + 30))
        assertEquals("۳۷:۳۰", formatDuration(37 * 60 + 30))
        assertEquals("۶۰:۰۰", formatDuration(60 * 60))
        assertEquals("کمبود ۲:۱۵", formatDuration(-(2 * 60 + 15)))
    }
}

class MonthlySummaryTest {

    /** پنج شیفت استاندارد، یکی از هر نوع، در مرداد ۱۴۰۵ */
    private fun standardMonth(): List<ShiftEntry> = listOf(
        ShiftEntry(14050501, ShiftType.MORNING, 7 * 60 + 30, 15 * 60),
        ShiftEntry(14050502, ShiftType.AFTERNOON, 15 * 60, 21 * 60 + 30),
        ShiftEntry(14050503, ShiftType.EVENING, 7 * 60 + 30, 18 * 60),
        ShiftEntry(14050504, ShiftType.HOLIDAY, 7 * 60 + 30, 21 * 60 + 30),
        ShiftEntry(14050505, ShiftType.NIGHT, 21 * 60 + 30, 24 * 60 + 7 * 60 + 30)
    )

    @Test
    fun `جمع کل و جز به جز درست است`() {
        val s = WorkCalculator.summarize(1405, 5, standardMonth())
        assertEquals(5, s.shiftCount)
        assertEquals(7 * 60 + 30, s.minutesOf(ShiftType.MORNING))
        assertEquals(6 * 60 + 30, s.minutesOf(ShiftType.AFTERNOON))
        assertEquals(10 * 60 + 30, s.minutesOf(ShiftType.EVENING))
        assertEquals(14 * 60, s.minutesOf(ShiftType.HOLIDAY))
        assertEquals(10 * 60, s.minutesOf(ShiftType.NIGHT))
        assertEquals(48 * 60 + 30, s.totalMinutes)
        assertEquals(0, s.balanceMinutes) // همه استاندارد بودند
    }

    @Test
    fun `شیفت ماه های دیگر در جمع بندی نمی آید`() {
        val entries = standardMonth() + ShiftEntry(14050601, ShiftType.MORNING, 7 * 60 + 30, 15 * 60)
        val s = WorkCalculator.summarize(1405, 5, entries)
        assertEquals(5, s.shiftCount)
        val next = WorkCalculator.summarize(1405, 6, entries)
        assertEquals(1, next.shiftCount)
    }

    @Test
    fun `شیفت بی اعتبار نادیده گرفته می شود`() {
        val bad = ShiftEntry(14050510, ShiftType.MORNING, 15 * 60, 7 * 60) // خروج قبل از ورود
        val s = WorkCalculator.summarize(1405, 5, standardMonth() + bad)
        assertEquals(5, s.shiftCount)
    }

    @Test
    fun `مانده مثبت وقتی بیشتر کار شده باشد`() {
        val overtime = listOf(
            ShiftEntry(14050501, ShiftType.MORNING, 7 * 60, 16 * 60) // ۹ ساعت جای ۷:۳۰
        )
        val s = WorkCalculator.summarize(1405, 5, overtime)
        assertEquals(9 * 60, s.totalMinutes)
        assertEquals(90, s.balanceMinutes)
        assertEquals(30, s.earlyInMinutes)
        assertEquals(60, s.lateOutMinutes)
    }
}

/**
 * تعطیل‌کاری و تفکیک کارکرد شب.
 *
 * روزها به‌جای حدس‌زدن، از خود تقویم پیدا می‌شوند؛ اگر تبدیل تاریخ یا جدول
 * تعطیلات عوض شود این آزمون‌ها همچنان همان وضعیت را می‌سنجند نه یک تاریخ ثابت.
 */
class HolidayWorkTest {

    private val night = ShiftType.NIGHT
    private val nightIn = 21 * 60 + 30
    private val nightOut = 24 * 60 + 7 * 60 + 30

    /** اولین روز مرداد ۱۴۰۵ که خودش و روز بعدش شرط داده‌شده را داشته باشند */
    private fun findDay(predicate: (JalaliDate, JalaliDate) -> Boolean): JalaliDate {
        var d = JalaliDate(1405, 1, 1)
        repeat(370) {
            if (predicate(d, d.plusDays(1))) return d
            d = d.plusDays(1)
        }
        throw AssertionError("روز مناسبی برای آزمون پیدا نشد")
    }

    private fun summarize(day: JalaliDate, overrides: Map<Int, Boolean> = emptyMap()) =
        WorkCalculator.summarize(
            day.year, day.month,
            listOf(ShiftEntry(day.key, night, nightIn, nightOut)),
            overrides
        )

    @Test
    fun `شیفت شب روز کاری که فردایش تعطیل است فقط بخش بامداد تعطیل کاری دارد`() {
        // مثال کاربر: پنج‌شنبه شب ۲۱:۳۰ تا ۰۷:۳۰ جمعه → ۷:۳۰ ساعت تعطیل‌کاری
        val day = findDay { d, next -> !Holidays.isDayOff(d) && Holidays.isDayOff(next) }
        val s = summarize(day)
        assertEquals(7 * 60 + 30, s.holidayWorkMinutes)
    }

    @Test
    fun `شیفت شب روز تعطیل که فردایش کاری است فقط بخش پیش از نیمه شب را می آورد`() {
        // مثال کاربر: جمعه شب ۲۱:۳۰ تا نیمه‌شب → ۲:۳۰ ساعت تعطیل‌کاری
        val day = findDay { d, next -> Holidays.isDayOff(d) && !Holidays.isDayOff(next) }
        val s = summarize(day)
        assertEquals(2 * 60 + 30, s.holidayWorkMinutes)
    }

    @Test
    fun `شیفت شب بین دو روز تعطیل تمام ده ساعت تعطیل کاری است`() {
        // مثال کاربر: جمعه شب که شنبه‌اش هم تعطیل رسمی است
        val day = JalaliDate(1405, 5, 10)
        val overrides = mapOf(day.key to true, day.plusDays(1).key to true)
        val s = summarize(day, overrides)
        assertEquals(10 * 60, s.holidayWorkMinutes)
        assertEquals(s.totalMinutes, s.holidayWorkMinutes)
    }

    @Test
    fun `شیفت شب بین دو روز کاری تعطیل کاری ندارد`() {
        val day = JalaliDate(1405, 5, 10)
        val overrides = mapOf(day.key to false, day.plusDays(1).key to false)
        assertEquals(0, summarize(day, overrides).holidayWorkMinutes)
    }

    @Test
    fun `شیفت روزانه در روز تعطیل تمام کارکردش تعطیل کاری است`() {
        val day = findDay { d, _ -> Holidays.isDayOff(d) }
        val entry = ShiftEntry(day.key, ShiftType.MORNING, 7 * 60 + 30, 15 * 60)
        val s = WorkCalculator.summarize(day.year, day.month, listOf(entry))
        assertEquals(7 * 60 + 30, s.holidayWorkMinutes)
        // شیفتی که از نیمه‌شب نمی‌گذرد، تعطیلیِ فردا نباید رویش اثر بگذارد
        assertEquals(0, entry.minutesAfterMidnight)
    }

    @Test
    fun `کارکرد شیفت شب دو ساعت معمولی و هشت ساعت شب است`() {
        // ۲۱:۳۰ تا ۲۳:۳۰ معمولی، ۲۳:۳۰ تا ۰۷:۳۰ کارکرد شب
        val e = ShiftEntry(14050510, night, nightIn, nightOut)
        assertEquals(8 * 60, e.nightWorkMinutes)
        assertEquals(2 * 60, e.dayWorkMinutes)
        assertEquals(e.workedMinutes, e.nightWorkMinutes + e.dayWorkMinutes)
    }

    @Test
    fun `شیفت روزانه کارکرد شب ندارد`() {
        val e = ShiftEntry(14050510, ShiftType.MORNING, 7 * 60 + 30, 15 * 60)
        assertEquals(0, e.nightWorkMinutes)
        assertEquals(7 * 60 + 30, e.dayWorkMinutes)
    }

    @Test
    fun `شیفت بعدازظهری که تا بعد از نیمه شب کشیده شود کارکرد شب می گیرد`() {
        // ۱۵:۰۰ تا ۰۱:۰۰ بامداد → از ۲۳:۳۰ به بعد یک ساعت‌ونیم کارکرد شب
        val e = ShiftEntry(14050510, ShiftType.AFTERNOON, 15 * 60, 24 * 60 + 60)
        assertEquals(90, e.nightWorkMinutes)
        assertEquals(60, e.minutesAfterMidnight)
        assertEquals(9 * 60, e.minutesBeforeMidnight)
    }

    @Test
    fun `جمع تعطیل کاری و کارکرد شب در کل ماه انباشته می شود`() {
        val day = JalaliDate(1405, 5, 10)
        val next = day.plusDays(2)
        val entries = listOf(
            ShiftEntry(day.key, night, nightIn, nightOut),
            ShiftEntry(next.key, night, nightIn, nightOut)
        )
        val overrides = mapOf(
            day.key to true, day.plusDays(1).key to true,
            next.key to true, next.plusDays(1).key to true
        )
        val s = WorkCalculator.summarize(1405, 5, entries, overrides)
        assertEquals(20 * 60, s.holidayWorkMinutes)
        assertEquals(16 * 60, s.nightWorkMinutes)
        assertEquals(4 * 60, s.dayWorkMinutes)
    }
}

/**
 * ساختار جدول گزارش.
 *
 * مهم‌ترین چیزی که این‌جا پاس داشته می‌شود، برابری تعداد سرستون‌ها با تعداد
 * خانه‌های هر سطر است؛ اگر ستونی حذف یا اضافه شود و یکی از دو طرف جا بماند،
 * جدول PDF و اکسل بی‌سر و ته می‌شوند و خطایی هم پرتاب نمی‌شود.
 */
class ReportStructureTest {

    private fun sampleData(): ReportData {
        val entries = listOf(
            ShiftEntry(14050501, ShiftType.MORNING, 7 * 60 + 30, 15 * 60),
            ShiftEntry(14050505, ShiftType.NIGHT, 21 * 60 + 30, 24 * 60 + 7 * 60 + 30)
        )
        return ReportData(
            fullName = "حسن اسماعیلی",
            summary = WorkCalculator.summarize(1405, 5, entries),
            generatedOn = JalaliDate(1405, 6, 2),
            generatedAtMinutes = 22 * 60 + 56
        )
    }

    @Test
    fun `تعداد خانه های هر سطر با سرستون ها برابر است`() {
        val data = sampleData()
        assertEquals(11, data.detailHeader.size)
        for (row in data.detailRows()) {
            assertEquals(data.detailHeader.size, row.size)
        }
    }

    @Test
    fun `ستون تاریخ قمری در گزارش نیست`() {
        val data = sampleData()
        assertFalse(data.detailHeader.any { it.contains("قمری") })
        // نام ماه‌های قمری هم نباید در هیچ خانه‌ای پیدا شود
        val cells = data.detailRows().flatten()
        for (name in PersianDate.HIJRI_MONTH_NAMES) {
            assertFalse("نام ماه قمری «$name» در گزارش مانده", cells.any { it.contains(name) })
        }
    }

    @Test
    fun `ترتیب سرستون ها همان ترتیب خانه های سطر است`() {
        val data = sampleData()
        assertEquals(listOf("تاریخ", "روز هفته", "نوع شیفت"), data.detailHeader.take(3))
        val first = data.detailRows().first()
        val day = JalaliDate(1405, 5, 1)
        assertEquals(day.formatted(), first[0])
        assertEquals(PersianDate.WEEK_DAY_NAMES[day.dayOfWeek], first[1])
        assertEquals(ShiftType.MORNING.title, first[2])
    }
}
