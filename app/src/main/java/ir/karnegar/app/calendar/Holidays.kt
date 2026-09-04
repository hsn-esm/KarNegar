package ir.karnegar.app.calendar

import ir.karnegar.app.data.CalendarEvent
import ir.karnegar.app.data.EventCalendar
import ir.karnegar.app.data.EventRules

/**
 * تعطیلات رسمی و مناسبت‌های ایران با سه لایه‌ی اولویت:
 *
 *  ۱) تنظیم دستی کاربر (نگه‌داشتن انگشت روی روز) — همیشه برنده است
 *  ۲) قاعده‌های دریافتی از مخزن persian-calendar/events — مرجع اصلی
 *  ۳) جدول درون‌برنامه‌ای پایین — فقط پشتیبان، تا اپ بدون اینترنت هم کار کند
 *
 * لایه‌ی دوم قاعده‌محور است نه سال‌محور: مناسبت شمسی با ماه و روزِ شمسی و مناسبت
 * قمری با ماه و روزِ قمری تعریف شده، پس یک بار دریافت برای همه‌ی سال‌ها بس است.
 */
object Holidays {

    /** کلید: ماه*۱۰۰+روز در تقویم شمسی */
    private val SOLAR: Map<Int, String> = mapOf(
        101 to "جشن نوروز / سال نو",
        102 to "عیدنوروز",
        103 to "عیدنوروز",
        104 to "عیدنوروز",
        112 to "روز جمهوری اسلامی ایران",
        113 to "سیزده بدر",
        314 to "رحلت امام خمینی",
        315 to "قیام ۱۵ خرداد",
        1122 to "پیروزی انقلاب اسلامی",
        1229 to "روز ملی شدن صنعت نفت ایران"
    )

    /** کلید: ماه قمری*۱۰۰+روز قمری */
    private val LUNAR: Map<Int, String> = mapOf(
        109 to "تاسوعای حسینی",
        110 to "عاشورای حسینی",
        220 to "اربعین حسینی",
        228 to "رحلت رسول اکرم و شهادت امام حسن مجتبی",
        230 to "شهادت امام رضا",
        308 to "شهادت امام حسن عسکری",
        317 to "میلاد رسول اکرم و امام جعفر صادق",
        603 to "شهادت حضرت فاطمه زهرا",
        713 to "ولادت امام علی",
        727 to "مبعث رسول اکرم",
        815 to "ولادت حضرت قائم",
        921 to "شهادت امام علی",
        1001 to "عید سعید فطر",
        1002 to "تعطیل به مناسبت عید فطر",
        1025 to "شهادت امام جعفر صادق",
        1210 to "عید سعید قربان",
        1218 to "عید سعید غدیر خم"
    )

    // ---------- لایه‌ی مخزن ----------

    /**
     * همه‌ی مناسبت‌های یک روز از قاعده‌های مخزن — شمسی، قمری و میلادی با هم.
     *
     * برای هر تقویم، تاریخِ همان روز در آن تقویم حساب می‌شود و در نقشه‌ی قاعده‌ها
     * جست‌وجو می‌شود. مناسبت قمری روی تقویم قمری ایران سنجیده می‌شود
     * ([JalaliDate.toHijri]) نه قمری تابعیِ خام.
     */
    fun eventsOf(date: JalaliDate, rules: EventRules = EventRules.EMPTY): List<CalendarEvent> {
        if (rules.isEmpty) return emptyList()
        val result = mutableListOf<CalendarEvent>()
        rules.solar[date.month * 100 + date.day]?.let { result += it }
        val h = date.toHijri()
        rules.lunar[h.month * 100 + h.day]?.let { result += it }
        val g = PersianDate.jdnToGregorian(date.jdn)
        rules.gregorian[g.gm * 100 + g.gd]?.let { result += it }
        return result
    }

    /**
     * تعطیلی رسمی روز داده‌شده را برمی‌گرداند یا null.
     * اگر قاعده‌های مخزن موجود باشند، همان مبنا قرار می‌گیرند و جدول
     * درون‌برنامه‌ای فقط در حالت بی‌داده استفاده می‌شود.
     * جمعه‌ها جداگانه در [isDayOff] بررسی می‌شوند.
     */
    fun officialHolidayTitle(
        date: JalaliDate,
        rules: EventRules = EventRules.EMPTY
    ): String? {
        val events = eventsOf(date, rules)
        if (events.isNotEmpty()) {
            return events.firstOrNull { it.isHoliday }?.title
        }
        return builtInTitle(date)
    }

    /** عنوان مناسبت روز، تعطیل باشد یا نه — برای نمایش زیر تاریخ */
    fun occasionTitle(
        date: JalaliDate,
        rules: EventRules = EventRules.EMPTY
    ): String? {
        val events = eventsOf(date, rules)
        if (events.isEmpty()) return builtInTitle(date)
        // تعطیل‌ها اول می‌آیند تا در فهرست ماه، عنوانِ مهم‌تر دیده شود
        return (events.firstOrNull { it.isHoliday } ?: events.first()).title
    }

    /** جدول پشتیبان درون‌برنامه‌ای؛ وقتی قاعده‌ای از مخزن نداریم */
    private fun builtInTitle(date: JalaliDate): String? {
        SOLAR[date.month * 100 + date.day]?.let { return it }
        val h = date.toHijri()
        LUNAR[h.month * 100 + h.day]?.let { return it }
        // اگر ماه صفر ۲۹ روزه بود، شهادت امام رضا به روز آخر منتقل می‌شود
        if (h.month == 2 && h.day == 29 && LUNAR.containsKey(230)) {
            val next = date.plusDays(1).toHijri()
            if (next.month != 2) return LUNAR[230]
        }
        return null
    }

    /** تاریخ قمری همان روز بدون سال، مثل «۲۸ صفر» — برای ستون باریک گزارش‌ها */
    fun hijriLabel(date: JalaliDate): String {
        val h = date.toHijri()
        return "${h.day.toPersianDigits()} ${PersianDate.HIJRI_MONTH_NAMES[h.month - 1]}"
    }

    /** تاریخ قمری با سال، مثل «۹ ربیع‌الاول ۱۴۴۸» */
    fun hijriFullLabel(date: JalaliDate): String =
        "${hijriLabel(date)} ${date.toHijri().year.toPersianDigits()}"

    /**
     * تاریخ میلادی همان روز، مثل «22 August 2026».
     *
     * ارقام لاتین می‌مانند چون نام ماه هم لاتین است و فارسی‌کردنِ فقط عدد،
     * نوشته را دوزبانه و ناخوانا می‌کند — همان قراری که تقویم‌های ایرانی دارند.
     */
    fun gregorianLabel(date: JalaliDate): String {
        val g = PersianDate.jdnToGregorian(date.jdn)
        return "${g.gd} ${PersianDate.GREGORIAN_MONTH_NAMES[g.gm - 1]} ${g.gy}"
    }

    /**
     * سطر تاریخ‌های دیگر در برگه‌ی جزئیات روز:
     * «۹ ربیع‌الاول ۱۴۴۸ - 22 August 2026»
     *
     * بخش میلادی داخل جداساز دوجهته پیچیده می‌شود ([ltrIsolate])؛ بدون آن، چون کل
     * رابط RTL است، «22 August 2026» می‌شکند و سال جابه‌جا نشان داده می‌شود.
     */
    fun otherCalendarsLabel(date: JalaliDate): String =
        "${hijriFullLabel(date)} - ${gregorianLabel(date).ltrIsolate()}"

    /**
     * آیا این روز تعطیل است؟ جمعه‌ها همیشه تعطیل‌اند مگر کاربر خلافش را ثبت کرده باشد.
     * @param overrides نقشه‌ی کلیدِ تاریخ به وضعیت دستی کاربر (true = تعطیل، false = کاری)
     * @param rules قاعده‌های مخزن؛ خالی باشد، جدول درون‌برنامه‌ای مبنا می‌شود
     */
    fun isDayOff(
        date: JalaliDate,
        overrides: Map<Int, Boolean> = emptyMap(),
        rules: EventRules = EventRules.EMPTY
    ): Boolean {
        overrides[date.key]?.let { return it }
        return date.isFriday || officialHolidayTitle(date, rules) != null
    }

    /** عنوان قابل نمایش برای تعطیلی؛ اگر تعطیل نبود null */
    fun dayOffTitle(
        date: JalaliDate,
        overrides: Map<Int, Boolean> = emptyMap(),
        rules: EventRules = EventRules.EMPTY
    ): String? {
        val manual = overrides[date.key]
        if (manual == false) return null
        officialHolidayTitle(date, rules)?.let { return it }
        if (date.isFriday) return "جمعه"
        if (manual == true) return "تعطیل (دستی)"
        return null
    }

    /** برچسب تقویمِ مناسبت، برای نمایش در فهرست ماه */
    fun calendarLabel(calendar: EventCalendar): String = when (calendar) {
        EventCalendar.SOLAR -> "شمسی"
        EventCalendar.LUNAR -> "قمری"
        EventCalendar.GREGORIAN -> "میلادی"
    }
}
