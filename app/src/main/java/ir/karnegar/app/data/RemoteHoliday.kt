package ir.karnegar.app.data

/**
 * یک مناسبت تقویمی که از منبع آنلاین گرفته شده است.
 *
 * @param title عنوان مناسبت، همان‌طور که در تقویم رسمی نوشته می‌شود
 * @param isOff آیا این روز تعطیل رسمی است (بعضی مناسبت‌ها تعطیل نیستند)
 */
data class RemoteHoliday(val title: String, val isOff: Boolean)

/** وضعیت آخرین همگام‌سازی تقویم، برای نمایش در تنظیمات */
data class HolidaySyncState(
    /** میلی‌ثانیه از epoch؛ صفر یعنی هنوز همگام نشده */
    val lastSyncAt: Long = 0L,
    /** سال‌های شمسی‌ای که داده‌شان گرفته شده */
    val syncedYears: Set<Int> = emptySet(),
    /** نام منبعی که پاسخ داد، برای شفافیت با کاربر */
    val source: String = "",
    /** پیام خطای آخرین تلاش ناموفق، یا null */
    val lastError: String? = null,
    val inProgress: Boolean = false
) {
    val hasData: Boolean get() = lastSyncAt > 0L && syncedYears.isNotEmpty()

    /** اگر بیش از ۳۰ روز از آخرین همگام‌سازی گذشته باشد یا سال جاری را نداشته باشیم */
    fun isStale(currentYear: Int, now: Long = System.currentTimeMillis()): Boolean =
        !hasData || currentYear !in syncedYears || now - lastSyncAt > STALE_AFTER_MS

    companion object {
        const val STALE_AFTER_MS = 30L * 24 * 60 * 60 * 1000
    }
}
