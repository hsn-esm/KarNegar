package ir.karnegar.app.calendar

/**
 * تبدیل تاریخ شمسی (هجری خورشیدی) و قمری (هجری قمری) بر پایه‌ی روز جولیَن (JDN).
 *
 * الگوریتم شمسی همان الگوریتم مرجع jalaali است که برای سال‌های ۱۱۷۸ تا ۱۶۳۳ شمسی
 * با تقویم رسمی ایران کاملاً منطبق است.
 */
object PersianDate {

    private val BREAKS = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
        1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    val MONTH_NAMES = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    /** نام روزهای هفته با شروع از شنبه */
    val WEEK_DAY_NAMES = arrayOf("شنبه", "یک‌شنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه")
    val WEEK_DAY_SHORT = arrayOf("ش", "ی", "د", "س", "چ", "پ", "ج")

    private class JalCal(val leap: Int, val gy: Int, val march: Int)

    private fun jalCal(jy: Int, withoutLeap: Boolean): JalCal {
        val bl = BREAKS.size
        val gy = jy + 621
        var leapJ = -14
        var jp = BREAKS[0]
        var jump = 0
        require(jy >= jp && jy < BREAKS[bl - 1]) { "سال شمسی نامعتبر: $jy" }
        for (i in 1 until bl) {
            val jm = BREAKS[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += (jump / 33) * 8 + (jump % 33) / 4
            jp = jm
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + (n % 33 + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1

        val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
        val march = 20 + leapJ - leapG

        var leap = -1
        if (!withoutLeap) {
            if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
            leap = ((n + 1) % 33 - 1) % 4
            if (leap == -1) leap = 4
        }
        return JalCal(leap, gy, march)
    }

    /** آیا سال شمسی کبیسه است؟ */
    fun isLeapYear(jy: Int): Boolean = jalCal(jy, false).leap == 0

    /** تعداد روزهای ماه شمسی */
    fun monthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        else -> if (isLeapYear(jy)) 30 else 29
    }

    // ---------- میلادی <-> JDN ----------

    fun gregorianToJdn(gy: Int, gm: Int, gd: Int): Long {
        var d = ((gy + (gm - 8) / 6 + 100100) * 1461L) / 4L +
                (153L * ((gm + 9) % 12) + 2L) / 5L + gd - 34840408L
        d -= ((((gy + 100100 + (gm - 8) / 6) / 100) * 3L) / 4L) - 752L
        return d
    }

    data class Gregorian(val gy: Int, val gm: Int, val gd: Int)

    /** نام ماه‌های میلادی — لاتین، همان‌طور که در تقویم‌های ایرانی هم نوشته می‌شود */
    val GREGORIAN_MONTH_NAMES = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    fun jdnToGregorian(jdn: Long): Gregorian {
        var j = 4L * jdn + 139361631L
        j += (((4L * jdn + 183187720L) / 146097L) * 3L) / 4L * 4L - 3908L
        val i = ((j % 1461L) / 4L) * 5L + 308L
        val gd = ((i % 153L) / 5L) + 1L
        val gm = ((i / 153L) % 12L) + 1L
        val gy = (j / 1461L) - 100100L + (8L - gm) / 6L
        return Gregorian(gy.toInt(), gm.toInt(), gd.toInt())
    }

    // ---------- شمسی <-> JDN ----------

    fun toJdn(jy: Int, jm: Int, jd: Int): Long {
        val r = jalCal(jy, true)
        return gregorianToJdn(r.gy, 3, r.march) + (jm - 1) * 31L - (jm / 7) * (jm - 7).toLong() + jd - 1L
    }

    fun fromJdn(jdn: Long): JalaliDate {
        val gy = jdnToGregorian(jdn).gy
        var jy = gy - 621
        val r = jalCal(jy, false)
        val jdn1f = gregorianToJdn(r.gy, 3, r.march)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) {
                return JalaliDate(jy, 1 + (k / 31).toInt(), ((k % 31) + 1).toInt())
            }
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        return JalaliDate(jy, 7 + (k / 30).toInt(), ((k % 30) + 1).toInt())
    }

    /**
     * ۰ = شنبه ... ۶ = جمعه
     *
     * مبنا: JDN ۲۴۵۱۵۴۵ برابر ۱ ژانویه ۲۰۰۰ و روز شنبه است، و ۲۴۵۱۵۴۵ % ۷ = ۵،
     * پس افزودن ۲ شنبه را به صفر می‌رساند.
     */
    fun dayOfWeek(jdn: Long): Int = (((jdn + 2L) % 7L) + 7L).toInt() % 7

    // ---------- قمری تقویمی (تابعی) ----------

    /** JDN اول محرم سال ۱ هجری قمری در تقویم تابعی مدنی */
    private const val HIJRI_EPOCH = 1948439L

    fun hijriToJdn(hy: Int, hm: Int, hd: Int): Long =
        hd + kotlin.math.ceil(29.5 * (hm - 1)).toLong() +
                (hy - 1) * 354L + ((3 + 11L * hy) / 30L) + HIJRI_EPOCH

    data class HijriDate(val year: Int, val month: Int, val day: Int)

    val HIJRI_MONTH_NAMES = arrayOf(
        "محرم", "صفر", "ربیع‌الاول", "ربیع‌الثانی", "جمادی‌الاول", "جمادی‌الثانی",
        "رجب", "شعبان", "رمضان", "شوال", "ذی‌القعده", "ذی‌الحجه"
    )

    /**
     * تبدیل قمری «تابعی» (محاسباتی). این تقویم مبنای ریاضی دارد و با تقویم رسمی ایران
     * که بر پایه‌ی رؤیت هلال بسته می‌شود یکی نیست — برای کار با تقویم ایران از
     * [jdnToIranHijri] استفاده شود، نه از این تابع.
     */
    fun jdnToHijri(jdn: Long): HijriDate {
        // ۱ محرم سال ۱ برابر HIJRI_EPOCH + 1 است، پس در برآورد سال باید یک روز عقب برویم؛
        // بدون این تصحیح، آخرین روز هر سال قمری به اشتباه «۰ محرم» سال بعد خوانده می‌شود.
        var hy = ((30L * (jdn - HIJRI_EPOCH - 1L) + 10646L) / 10631L).toInt()
        if (hy < 1) hy = 1
        var firstOfYear = hijriToJdn(hy, 1, 1)
        if (firstOfYear > jdn && hy > 1) {
            hy -= 1
            firstOfYear = hijriToJdn(hy, 1, 1)
        }
        // اگر برآورد سال کمتر از واقع بود، یک سال جلو می‌رویم
        while (hijriToJdn(hy + 1, 1, 1) <= jdn) {
            hy += 1
            firstOfYear = hijriToJdn(hy, 1, 1)
        }
        var hm = ((jdn - firstOfYear) / 29.5).toInt() + 1
        if (hm > 12) hm = 12
        if (hm < 1) hm = 1
        val hd = (jdn - hijriToJdn(hy, hm, 1) + 1L).toInt()
        return HijriDate(hy, hm, hd)
    }

    /**
     * فاصله‌ی تقویم قمری تابعی با تقویم رسمی ایران، بر حسب روز.
     *
     * تقویم قمری ایران بر پایه‌ی رؤیت هلال بسته می‌شود، ولی [jdnToHijri] یک تقویم
     * محاسباتی است و در این دوره یک روز عقب‌تر از تاریخ اعلام‌شده‌ی ایران می‌افتد.
     * نقطه‌ی سنجش: ۲۱ مرداد ۱۴۰۵ (۱۲ اوت ۲۰۲۶) در تقویم ایران ۲۸ صفر ۱۴۴۸ است،
     * در حالی که محاسبه‌ی تابعی ۲۷ صفر می‌دهد.
     */
    private const val IRAN_HIJRI_SHIFT = 1L

    /**
     * تاریخ قمری همان‌طور که در تقویم رسمی ایران نوشته می‌شود.
     *
     * این یک تقریب است، نه قطعیت: چون طول ماه‌های قمری با رؤیت هلال تعیین می‌شود،
     * ممکن است در بعضی ماه‌ها هنوز یک روز اختلاف بماند. مرجع دقیق تعطیلات،
     * داده‌ی آنلاین است و این تابع فقط حالت آفلاین را به واقعیت نزدیک می‌کند.
     */
    fun jdnToIranHijri(jdn: Long): HijriDate = jdnToHijri(jdn + IRAN_HIJRI_SHIFT)
}

/**
 * یک تاریخ شمسی. مقایسه‌پذیر و قابل تبدیل به عدد یکتا برای ذخیره‌سازی.
 */
data class JalaliDate(val year: Int, val month: Int, val day: Int) : Comparable<JalaliDate> {

    /** یک بار محاسبه و نگه‌داشته می‌شود؛ در شبکه‌ی تقویم صدها بار خوانده می‌شود */
    val jdn: Long by lazy(LazyThreadSafetyMode.NONE) { PersianDate.toJdn(year, month, day) }

    /** ۰ = شنبه ... ۶ = جمعه */
    val dayOfWeek: Int get() = PersianDate.dayOfWeek(jdn)

    val isFriday: Boolean get() = dayOfWeek == 6

    /** کلید یکتای عددی برای ذخیره در حافظه، مثال: ۱۴۰۵۰۵۱۲ */
    val key: Int get() = year * 10000 + month * 100 + day

    fun plusDays(days: Int): JalaliDate = PersianDate.fromJdn(jdn + days)

    /** تاریخ قمری مطابق تقویم رسمی ایران (نه تقویم تابعی خام) */
    fun toHijri(): PersianDate.HijriDate = PersianDate.jdnToIranHijri(jdn)

    fun formatted(): String = "%04d/%02d/%02d".format(year, month, day).toPersianDigits()

    fun longFormatted(): String =
        "${PersianDate.WEEK_DAY_NAMES[dayOfWeek]} ${day.toString().toPersianDigits()} " +
                "${PersianDate.MONTH_NAMES[month - 1]} ${year.toString().toPersianDigits()}"

    override fun compareTo(other: JalaliDate): Int = key.compareTo(other.key)

    companion object {
        /**
         * ساخت تاریخ از کلید عددی. اگر داده‌ی ذخیره‌شده خراب باشد،
         * مقادیر به بازه‌ی معتبر محدود می‌شوند تا از خطای اندیس در نام ماه‌ها جلوگیری شود.
         */
        fun fromKey(key: Int): JalaliDate {
            val y = (key / 10000).coerceIn(1, 3000)
            val m = ((key / 100) % 100).coerceIn(1, 12)
            val d = (key % 100).coerceIn(1, PersianDate.monthLength(y, m))
            return JalaliDate(y, m, d)
        }

        fun today(): JalaliDate {
            val cal = java.util.Calendar.getInstance()
            return PersianDate.fromJdn(
                PersianDate.gregorianToJdn(
                    cal.get(java.util.Calendar.YEAR),
                    cal.get(java.util.Calendar.MONTH) + 1,
                    cal.get(java.util.Calendar.DAY_OF_MONTH)
                )
            )
        }
    }
}

private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

/** تبدیل ارقام لاتین به فارسی */
fun String.toPersianDigits(): String = buildString(length) {
    for (c in this@toPersianDigits) {
        append(if (c in '0'..'9') PERSIAN_DIGITS[c - '0'] else c)
    }
}

fun Int.toPersianDigits(): String = toString().toPersianDigits()

/**
 * متن را داخل جداساز دوجهته‌ی چپ‌به‌راست می‌پیچد (U+2066 … U+2069).
 *
 * چرا لازم است: کل رابط کارنگار RTL است و در متن راست‌به‌چپ، الگوریتم دوجهته‌ی
 * یونیکد رشته‌ای مثل «۱۴۰۵/۰۵/۳۱» را از هم می‌پاشد و «۳۱/۰۵/۱۴۰۵» نشان می‌دهد،
 * چون اسلش کاراکتر بی‌جهت است و ترتیب پاره‌های عددی را تعیین نمی‌کند. همین برای
 * تاریخ میلادی («2026 22 August») هم پیش می‌آید. جداساز، متن را یک تکه‌ی
 * چپ‌به‌راست می‌کند و خودش هیچ عرضی ندارد و دیده نمی‌شود.
 *
 * فقط برای نمایش در رابط استفاده شود، نه برای متنی که در PDF یا اکسل نوشته
 * می‌شود؛ در فایل خروجی این کاراکترها بی‌فایده‌اند و ممکن است در برخی
 * برنامه‌ها به‌جای نادیده‌گرفته‌شدن، مربع خالی نشان داده شوند.
 */
fun String.ltrIsolate(): String = "⁦$this⁩"
