package ir.karnegar.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * دریافت مناسبت‌ها و تعطیلات از مخزن باز persian-calendar/events.
 *
 * چرا این مخزن؟ سرویس‌های تعطیلاتِ شخصی (persiancalapi، holidayapi و مانندشان) هر
 * چند وقت از دسترس خارج می‌شوند و همین باعث شد به‌روزرسانی تقویم در نسخه‌ی پیشین
 * کار نکند. مخزن persian-calendar داده‌ی پشتِ اپ «تقویم فارسی» است، روی
 * raw.githubusercontent.com میزبانی می‌شود و ساختارش سال‌ها ثابت مانده.
 *
 * تفاوت مهمِ داده: این مخزن «قاعده» می‌دهد نه فهرستِ یک سال. مناسبت شمسی با ماه و
 * روزِ شمسی و مناسبت قمری با ماه و روزِ قمری تعریف شده است، پس یک بار دریافت برای
 * همه‌ی سال‌ها بس است و اپ برای سال بعد هم به شبکه نیاز ندارد.
 *
 * فقط خواندنی و یک‌طرفه: هیچ داده‌ای از کاربر — نام، شیفت، کارکرد — ارسال نمی‌شود.
 */
object EventsRepository {

    private const val TIMEOUT_MS = 15_000

    /**
     * نشانی‌ها به ترتیب امتحان می‌شوند و اولین پاسخِ سالم پذیرفته می‌شود.
     * شاخه‌ی پیش‌فرض مخزن ممکن است main یا master باشد، و نام فایل هم در
     * بازآرایی‌های مخزن جابه‌جا شده است، پس همه‌ی حالت‌های رایج پوشش داده می‌شود.
     */
    private val URLS = listOf(
        "https://raw.githubusercontent.com/persian-calendar/events/main/events.json",
        "https://raw.githubusercontent.com/persian-calendar/events/master/events.json",
        "https://raw.githubusercontent.com/persian-calendar/events/main/json/events.json",
        "https://raw.githubusercontent.com/persian-calendar/events/main/output/events.json"
    )

    sealed interface Result {
        data class Success(val rules: EventRules) : Result
        data class Failure(val message: String) : Result
    }

    /** شبکه را مسدود می‌کند؛ باید از Dispatchers.IO صدا زده شود. */
    fun fetch(): Result {
        val errors = mutableListOf<String>()
        for (url in URLS) {
            val outcome = runCatching { parse(get(url)) }
            outcome.onSuccess { rules ->
                // داده‌ی سالم صدها مناسبت دارد؛ کمتر از این یعنی ساختار عوض شده
                if (rules.eventCount >= 50) return Result.Success(rules)
                errors += "پاسخ ناقص بود (${rules.eventCount} مناسبت)"
            }.onFailure { e ->
                errors += e.message ?: e.javaClass.simpleName
            }
        }
        return Result.Failure(errors.distinct().joinToString(" / ").ifBlank { "منبعی پاسخ نداد" })
    }

    // ---------- شبکه ----------

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "KarNegar/1.0 (Android)")
        }
        // فقط HTTPS؛ روی HTTP داده در مسیر قابل دست‌کاری است
        require(connection is HttpsURLConnection) { "اتصال ناامن رد شد" }
        try {
            val code = connection.responseCode
            require(code == HttpURLConnection.HTTP_OK) { "کد پاسخ $code" }
            return connection.inputStream.bufferedReader().use(BufferedReader::readText)
        } finally {
            connection.disconnect()
        }
    }

    // ---------- پارس ----------

    /**
     * نام کلیدهای دسته‌ها در فایل مخزن. هم شکلِ فاصله‌دار و هم بدون‌فاصله پوشش
     * داده می‌شود، چون در نسخه‌های مختلف مخزن هر دو دیده شده است.
     */
    private val SOLAR_KEYS = listOf(
        "Persian Calendar", "PersianCalendar", "persian", "solar", "Solar Calendar"
    )
    private val LUNAR_KEYS = listOf(
        "Hijri Calendar", "HijriCalendar", "hijri", "lunar", "Islamic Calendar"
    )
    private val GREGORIAN_KEYS = listOf(
        "Gregorian Calendar", "GregorianCalendar", "gregorian"
    )

    private val TITLE_KEYS = listOf("title", "name", "event", "description")
    private val HOLIDAY_KEYS = listOf("holiday", "isHoliday", "is_holiday", "isOff")

    /**
     * ساختار فایل می‌تواند یکی از این دو باشد:
     *
     *  ۱) شیئی با کلیدِ هر تقویم که مقدارش آرایه‌ی مناسبت‌هاست (شکل رایج مخزن)
     *  ۲) یک آرایه‌ی یکدست که نوعِ تقویمِ هر مناسبت در خودِ عنصر آمده است
     *
     * هر دو پوشش داده می‌شود تا بازآراییِ مخزن به‌روزرسانی را از کار نیندازد —
     * همان چیزی که در پیاده‌سازی پیشین اتفاق افتاد.
     */
    private fun parse(body: String): EventRules {
        val trimmed = body.trim()
        return when {
            trimmed.startsWith("{") -> parseObject(JSONObject(trimmed))
            trimmed.startsWith("[") -> parseFlatArray(JSONArray(trimmed))
            else -> throw IllegalStateException("پاسخ JSON نبود")
        }
    }

    private fun parseObject(root: JSONObject): EventRules {
        val solar = collect(root, SOLAR_KEYS, EventCalendar.SOLAR)
        val lunar = collect(root, LUNAR_KEYS, EventCalendar.LUNAR)
        val gregorian = collect(root, GREGORIAN_KEYS, EventCalendar.GREGORIAN)
        if (solar.isEmpty() && lunar.isEmpty() && gregorian.isEmpty()) {
            // شاید ساختار تودرتو باشد و آرایه‌ها یک لایه پایین‌تر بیایند
            val nested = root.keys().asSequence()
                .mapNotNull { root.optJSONObject(it) }
                .map { parseObject(it) }
                .firstOrNull { !it.isEmpty }
            if (nested != null) return nested
            throw IllegalStateException("دسته‌ی مناسبت‌ها پیدا نشد")
        }
        return EventRules(solar, lunar, gregorian)
    }

    private fun collect(
        root: JSONObject,
        keys: List<String>,
        calendar: EventCalendar
    ): Map<Int, List<CalendarEvent>> {
        val array = keys.firstNotNullOfOrNull { root.optJSONArray(it) } ?: return emptyMap()
        val result = linkedMapOf<Int, MutableList<CalendarEvent>>()
        for (i in 0 until array.length()) {
            // خرابی یک مناسبت نباید کل دسته را بی‌اعتبار کند
            runCatching {
                val o = array.optJSONObject(i) ?: return@runCatching
                addTo(result, o, calendar)
            }
        }
        return result
    }

    private fun parseFlatArray(array: JSONArray): EventRules {
        val solar = linkedMapOf<Int, MutableList<CalendarEvent>>()
        val lunar = linkedMapOf<Int, MutableList<CalendarEvent>>()
        val gregorian = linkedMapOf<Int, MutableList<CalendarEvent>>()
        for (i in 0 until array.length()) {
            runCatching {
                val o = array.optJSONObject(i) ?: return@runCatching
                val raw = listOf("calendar", "calendarType", "type")
                    .firstNotNullOfOrNull { o.optString(it, "").takeIf { s -> s.isNotBlank() } }
                    ?.lowercase()
                    ?: return@runCatching
                val calendar = when {
                    raw.contains("hijri") || raw.contains("lunar") || raw.contains("islamic") ->
                        EventCalendar.LUNAR
                    raw.contains("gregorian") -> EventCalendar.GREGORIAN
                    raw.contains("persian") || raw.contains("solar") || raw.contains("jalali") ->
                        EventCalendar.SOLAR
                    else -> return@runCatching
                }
                val target = when (calendar) {
                    EventCalendar.SOLAR -> solar
                    EventCalendar.LUNAR -> lunar
                    EventCalendar.GREGORIAN -> gregorian
                }
                addTo(target, o, calendar)
            }
        }
        return EventRules(solar, lunar, gregorian)
    }

    /** یک عنصر JSON را به نقشه‌ی «ماه×۱۰۰+روز» می‌افزاید */
    private fun addTo(
        target: MutableMap<Int, MutableList<CalendarEvent>>,
        o: JSONObject,
        calendar: EventCalendar
    ) {
        val month = intOf(o, listOf("month", "m")) ?: return
        val day = intOf(o, listOf("day", "d")) ?: return
        if (month !in 1..12 || day !in 1..31) return
        val title = TITLE_KEYS.firstNotNullOfOrNull {
            o.optString(it, "").trim().takeIf { s -> s.isNotBlank() }
        } ?: return
        // در این مخزن مناسبت‌های غیرایرانی هم هست (مثل مناسبت‌های افغانستان)؛
        // آن‌ها نباید در تقویم کاری یک کارمند ایرانی تعطیلی بسازند.
        val type = o.optString("type", "").lowercase()
        if (type.isNotBlank() && !type.contains("iran") && !type.contains("ancientiran")) return
        val isHoliday = HOLIDAY_KEYS.any { o.optBoolean(it, false) }
        val key = month * 100 + day
        target.getOrPut(key) { mutableListOf() }.add(CalendarEvent(title, isHoliday, calendar))
    }

    private fun intOf(o: JSONObject, keys: List<String>): Int? =
        keys.firstNotNullOfOrNull { k ->
            if (!o.has(k)) null
            else o.optInt(k, -1).takeIf { it >= 0 } ?: o.optString(k, "").trim().toIntOrNull()
        }
}
