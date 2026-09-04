package ir.karnegar.app.data

/**
 * مناسبت‌های تقویمی که از مخزن باز persian-calendar/events خوانده می‌شوند.
 *
 * تفاوت بنیادی با پیاده‌سازی پیشین: آن نسخه تعطیلات را «سالِ به سال» از چند
 * سرویس شخصی می‌گرفت و هر بار یکی از آن سرویس‌ها از کار می‌افتاد، به‌روزرسانی
 * هم می‌خوابید. مخزن persian-calendar/events به‌جای فهرستِ یک سال، «قاعده»ها را
 * می‌دهد: مناسبت‌های شمسی بر پایه‌ی ماه و روزِ شمسی، و مناسبت‌های قمری بر پایه‌ی
 * ماه و روزِ قمری. پس یک بار دریافت، برای همه‌ی سال‌ها کار می‌کند و اپ برای سالِ
 * بعد به شبکه نیازی ندارد.
 */

/** تقویمی که مناسبت بر پایه‌ی آن تعریف شده است */
enum class EventCalendar { SOLAR, LUNAR, GREGORIAN }

/**
 * یک مناسبت تقویمی.
 *
 * @param title عنوان مناسبت، همان‌طور که در تقویم رسمی نوشته می‌شود
 * @param isHoliday آیا این مناسبت تعطیل رسمی است (بسیاری از مناسبت‌ها تعطیل نیستند)
 */
data class CalendarEvent(
    val title: String,
    val isHoliday: Boolean,
    val calendar: EventCalendar
)

/**
 * قاعده‌های مناسبت، دسته‌بندی‌شده بر پایه‌ی تقویم.
 *
 * کلیدِ هر سه نقشه «ماه×۱۰۰ + روز» در تقویم خودش است؛ سال در کلید نیست چون این
 * مناسبت‌ها سالانه تکرار می‌شوند. مناسبت‌های یک روز می‌توانند چند تا باشند، پس
 * مقدار هر کلید فهرست است نه یک عنصر.
 */
data class EventRules(
    val solar: Map<Int, List<CalendarEvent>> = emptyMap(),
    val lunar: Map<Int, List<CalendarEvent>> = emptyMap(),
    val gregorian: Map<Int, List<CalendarEvent>> = emptyMap()
) {
    val isEmpty: Boolean get() = solar.isEmpty() && lunar.isEmpty() && gregorian.isEmpty()

    val eventCount: Int
        get() = solar.values.sumOf { it.size } +
                lunar.values.sumOf { it.size } +
                gregorian.values.sumOf { it.size }

    companion object {
        val EMPTY = EventRules()
    }
}

/**
 * وضعیت آخرین دریافت تقویم از مخزن.
 *
 * دیگر فهرستی از «سال‌های دریافت‌شده» نگه داشته نمی‌شود؛ قاعده‌ها سال ندارند و
 * همین‌که یک بار گرفته شوند برای همه‌ی سال‌ها معتبرند.
 */
data class EventsSyncState(
    /** میلی‌ثانیه از epoch؛ صفر یعنی هنوز دریافت نشده */
    val lastSyncAt: Long = 0L,
    /** تعداد مناسبت‌های ذخیره‌شده، برای نمایش در تنظیمات */
    val eventCount: Int = 0,
    val lastError: String? = null,
    val inProgress: Boolean = false
) {
    val hasData: Boolean get() = lastSyncAt > 0L && eventCount > 0

    /**
     * آیا وقتِ دریافت دوباره است؟ داده نداریم، یا بیش از سی روز از دریافت گذشته.
     *
     * مخزن هر چند وقت یک بار با ابلاغ‌های تازه‌ی تعطیلات به‌روز می‌شود، پس تازه‌سازی
     * ماهانه کافی است و اپ در بقیه‌ی روزها هیچ اتصالی برقرار نمی‌کند.
     */
    fun isStale(now: Long = System.currentTimeMillis()): Boolean =
        !hasData || now - lastSyncAt > STALE_AFTER_MS

    companion object {
        const val STALE_AFTER_MS = 30L * 24 * 60 * 60 * 1000
    }
}
