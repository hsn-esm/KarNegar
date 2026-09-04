package ir.karnegar.app.model

import ir.karnegar.app.calendar.toPersianDigits

/**
 * انواع شیفت و ساعت استاندارد هر کدام (بر حسب دقیقه از نیمه‌شب).
 * شیفت شب از ۲۱:۳۰ تا ۰۷:۳۰ روز بعد است، پس پایانش از ۲۴ ساعت عبور می‌کند.
 */
enum class ShiftType(
    val title: String,
    val startMinutes: Int,
    val endMinutes: Int
) {
    MORNING("صبح", 7 * 60 + 30, 15 * 60),                   // ۰۷:۳۰ – ۱۵:۰۰
    AFTERNOON("بعدازظهر", 15 * 60, 21 * 60 + 30),           // ۱۵:۰۰ – ۲۱:۳۰
    EVENING("عصر", 7 * 60 + 30, 18 * 60),                   // ۰۷:۳۰ – ۱۸:۰۰
    HOLIDAY("تعطیلی", 7 * 60 + 30, 21 * 60 + 30),           // ۰۷:۳۰ – ۲۱:۳۰
    NIGHT("شب", 21 * 60 + 30, 24 * 60 + 7 * 60 + 30);       // ۲۱:۳۰ – ۰۷:۳۰ فردا

    /** مدت استاندارد شیفت بر حسب دقیقه */
    val standardMinutes: Int get() = endMinutes - startMinutes

    /** آیا شیفت به روز بعد می‌رسد؟ */
    val crossesMidnight: Boolean get() = endMinutes > 24 * 60

    val standardRange: String
        get() = "${formatClock(startMinutes)} – ${formatClock(endMinutes % (24 * 60))}"

    companion object {
        fun fromName(name: String?): ShiftType? = entries.firstOrNull { it.name == name }

        /**
         * پنجره‌ی «کارکرد شب»: ۲۳:۳۰ تا ۰۷:۳۰ بامداد روز بعد — هشت ساعت.
         *
         * این پنجره به نوع شیفت وابسته نیست، به ساعت کار وابسته است. شیفت شب از
         * ۲۱:۳۰ شروع می‌شود، پس دو ساعت اولش (۲۱:۳۰ تا ۲۳:۳۰) کارکرد معمولی است
         * و بقیه‌اش کارکرد شب. اگر شیفت دیگری هم تا بعد از ۲۳:۳۰ طول بکشد، همان
         * مقدار برایش کارکرد شب حساب می‌شود.
         *
         * اعداد بر حسب دقیقه از نیمه‌شب روزِ شروع شیفت‌اند، پس پایان پنجره از
         * ۱۴۴۰ عبور می‌کند — همان قراری که [ShiftEntry.actualOutMinutes] دارد.
         */
        const val NIGHT_WINDOW_START = 23 * 60 + 30           // ۲۳:۳۰
        const val NIGHT_WINDOW_END = 24 * 60 + 7 * 60 + 30    // ۰۷:۳۰ فردا
    }
}

/** قالب‌بندی دقیقه به HH:mm با ارقام فارسی */
fun formatClock(totalMinutes: Int): String {
    val m = ((totalMinutes % (24 * 60)) + 24 * 60) % (24 * 60)
    return "%02d:%02d".format(m / 60, m % 60).toPersianDigits()
}

/**
 * قالب‌بندی مدت زمان به صورت ساعت:دقیقه — می‌تواند از ۲۴ ساعت بیشتر باشد.
 * برای مقادیر منفی به جای علامت «-» از واژه‌ی «کمبود» استفاده می‌شود، چون
 * در چیدمان راست‌به‌چپ علامت منفی سمت اشتباه عدد نمایش داده می‌شود.
 */
fun formatDuration(totalMinutes: Int): String {
    val abs = kotlin.math.abs(totalMinutes)
    val value = "%d:%02d".format(abs / 60, abs % 60).toPersianDigits()
    return if (totalMinutes < 0) "کمبود $value" else value
}

/**
 * یک شیفت ثبت‌شده در یک روز مشخص.
 *
 * @param dateKey کلید تاریخ شمسی (سال*۱۰۰۰۰+ماه*۱۰۰+روز)
 * @param actualInMinutes ساعت ورود واقعی، دقیقه از نیمه‌شب همان روز
 * @param actualOutMinutes ساعت خروج واقعی؛ اگر شیفت به روز بعد برسد از ۱۴۴۰ بیشتر است
 */
data class ShiftEntry(
    val dateKey: Int,
    val type: ShiftType,
    val actualInMinutes: Int,
    val actualOutMinutes: Int,
    val note: String = ""
) {
    /** ساعات کارکرد واقعی بر حسب دقیقه */
    val workedMinutes: Int get() = actualOutMinutes - actualInMinutes

    /** اختلاف ورود: منفی = تعجیل در ورود، مثبت = تاخیر در ورود */
    val inDelta: Int get() = actualInMinutes - type.startMinutes

    /** اختلاف خروج: منفی = تعجیل در خروج، مثبت = تاخیر در خروج */
    val outDelta: Int get() = actualOutMinutes - type.endMinutes

    val earlyInMinutes: Int get() = if (inDelta < 0) -inDelta else 0
    val lateInMinutes: Int get() = if (inDelta > 0) inDelta else 0
    val earlyOutMinutes: Int get() = if (outDelta < 0) -outDelta else 0
    val lateOutMinutes: Int get() = if (outDelta > 0) outDelta else 0

    /** آیا این ثبت منطقی است؟ خروج باید بعد از ورود باشد */
    val isValid: Boolean get() = workedMinutes > 0

    /**
     * اشتراک بازه‌ی کار با یک بازه‌ی دلخواه، بر حسب دقیقه.
     * مبنای همه‌ی تفکیک‌های زیر همین یک تابع است تا حساب دو بار جای مختلف نوشته نشود.
     */
    private fun overlap(from: Int, to: Int): Int =
        (minOf(actualOutMinutes, to) - maxOf(actualInMinutes, from)).coerceAtLeast(0)

    /** بخشی از کار که پیش از نیمه‌شب انجام شده — یعنی در روز خودِ شیفت */
    val minutesBeforeMidnight: Int get() = overlap(0, 24 * 60)

    /** بخشی از کار که پس از نیمه‌شب انجام شده — یعنی در روز بعد */
    val minutesAfterMidnight: Int get() = overlap(24 * 60, Int.MAX_VALUE / 2)

    /**
     * کارکرد شب: اشتراک کار با پنجره‌ی ۲۳:۳۰ تا ۰۷:۳۰ بامداد.
     *
     * برای شیفت شب استاندارد ۸ ساعت می‌شود و دو ساعت اول شیفت (۲۱:۳۰ تا ۲۳:۳۰)
     * بیرون این پنجره می‌ماند و کارکرد معمولی است.
     */
    val nightWorkMinutes: Int
        get() = overlap(ShiftType.NIGHT_WINDOW_START, ShiftType.NIGHT_WINDOW_END)

    /** کارکرد معمولی — هر چه کارکرد شب نباشد */
    val dayWorkMinutes: Int get() = workedMinutes - nightWorkMinutes

    /**
     * تعطیل‌کاری این شیفت بر حسب دقیقه.
     *
     * قاعده روی «روز تقویمی» کار می‌کند، نه روی نوع شیفت: هر دقیقه‌ای که در یک روز
     * تعطیل گذشته باشد تعطیل‌کاری است. برای شیفتی که از نیمه‌شب می‌گذرد، دو طرف
     * جداگانه سنجیده می‌شوند:
     *
     *  - پنج‌شنبه شب ۲۱:۳۰ تا ۰۷:۳۰ جمعه → فقط بخش بعد از نیمه‌شب (۷:۳۰) تعطیل‌کاری است
     *  - جمعه شب ۲۱:۳۰ تا ۰۷:۳۰ شنبه‌ی کاری → فقط بخش پیش از نیمه‌شب (۲:۳۰)
     *  - جمعه شب که شنبه‌اش هم تعطیل رسمی است → هر دو بخش، یعنی کل ۱۰ ساعت
     *
     * @param isShiftDayOff آیا روزِ شروع شیفت تعطیل است؟
     * @param isNextDayOff آیا روز بعد تعطیل است؟ برای شیفت‌هایی که از نیمه‌شب نمی‌گذرند بی‌اثر است.
     */
    fun holidayWorkMinutes(isShiftDayOff: Boolean, isNextDayOff: Boolean): Int {
        var minutes = 0
        if (isShiftDayOff) minutes += minutesBeforeMidnight
        if (isNextDayOff) minutes += minutesAfterMidnight
        return minutes
    }
}
