package ir.karnegar.app.data

import ir.karnegar.app.calendar.JalaliDate
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * گرفتن تعطیلات رسمی ایران از منابع عمومی آنلاین.
 *
 * چرا API و نه خودِ time.ir؟ صفحه‌ی time.ir یک صفحه‌ی HTML است که ساختارش هر چند وقت
 * عوض می‌شود و پارس‌کردنش در اپ شکننده است. منابعی که این‌جا استفاده می‌شوند خودشان
 * داده را از time.ir استخراج و به JSON تبدیل می‌کنند، پس دقت همان است ولی پایدارتر.
 *
 * سه منبع به ترتیب امتحان می‌شوند و اولین پاسخ سالم پذیرفته می‌شود؛ اگر همه شکست
 * بخورند، اپ سراغ جدول درون‌برنامه‌ای می‌رود و هیچ‌چیز از کار نمی‌افتد.
 *
 * از HttpURLConnection استاندارد استفاده می‌شود تا هیچ وابستگی شبکه‌ای به پروژه اضافه نشود.
 */
object HolidayApi {

    private const val TIMEOUT_MS = 12_000

    /** منابع به ترتیب اولویت؛ هر کدام یک سال شمسی کامل را برمی‌گرداند */
    private val SOURCES = listOf(
        Source("persiancalapi.ir") { year -> "https://persiancalapi.ir/jalali/$year" },
        Source("holidayapi.ir") { year -> "https://holidayapi.ir/jalali/$year" },
        Source("pholiday.ir") { year -> "https://pholiday.ir/api/year/$year" }
    )

    private class Source(val name: String, val url: (Int) -> String)

    /** نتیجه‌ی یک تلاش همگام‌سازی */
    sealed interface Result {
        data class Success(val source: String, val days: Map<Int, RemoteHoliday>) : Result
        data class Failure(val message: String) : Result
    }

    /**
     * تعطیلات یک سال شمسی را می‌گیرد. این متد شبکه را مسدود می‌کند،
     * پس باید از Dispatchers.IO صدا زده شود.
     */
    fun fetchYear(year: Int): Result {
        val errors = mutableListOf<String>()
        for (source in SOURCES) {
            val outcome = runCatching {
                val body = get(source.url(year))
                parse(body, year)
            }
            outcome.onSuccess { days ->
                // پاسخ باید حداقل چند ده روز داشته باشد، وگرنه احتمالاً ساختارش عوض شده
                if (days.size >= 20) return Result.Success(source.name, days)
                errors += "${source.name}: پاسخ ناقص بود"
            }.onFailure { e ->
                errors += "${source.name}: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        return Result.Failure(errors.joinToString(" / ").ifBlank { "منبعی پاسخ نداد" })
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
        // فقط HTTPS پذیرفته می‌شود تا داده در مسیر دست‌کاری نشود
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
     * ساختار پاسخ منابع مختلف یکسان نیست، پس هر دو شکل رایج پوشش داده می‌شود:
     * یک آرایه‌ی روزها، یا یک شیء که آرایه در یکی از کلیدهایش است.
     */
    private fun parse(body: String, year: Int): Map<Int, RemoteHoliday> {
        val trimmed = body.trim()
        val array = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                ARRAY_KEYS.firstNotNullOfOrNull { root.optJSONArray(it) }
                    ?: throw IllegalStateException("آرایه‌ی روزها پیدا نشد")
            }
            else -> throw IllegalStateException("پاسخ JSON نبود")
        }
        val result = linkedMapOf<Int, RemoteHoliday>()
        for (i in 0 until array.length()) {
            // خرابی یک روز نباید کل سال را بی‌اعتبار کند
            runCatching {
                val day = array.optJSONObject(i) ?: return@runCatching
                val key = dateKeyOf(day, year) ?: return@runCatching
                val title = titleOf(day) ?: return@runCatching
                val isOff = isOffOf(day)
                // اگر روزی چند مناسبت داشت، تعطیل‌بودن برنده است
                val existing = result[key]
                result[key] = when {
                    existing == null -> RemoteHoliday(title, isOff)
                    existing.isOff || isOff -> RemoteHoliday(
                        if (existing.isOff) existing.title else title, true
                    )
                    else -> existing
                }
            }
        }
        return result
    }

    private val ARRAY_KEYS = listOf("days", "data", "result", "items", "holidays", "events")
    private val TITLE_KEYS = listOf("event", "title", "description", "name", "occasion")
    private val OFF_KEYS = listOf("isHoliday", "is_holiday", "holiday", "isOff", "is_off")

    /** کلید عددی تاریخ شمسی (۱۴۰۵۰۵۱۲) از شکل‌های مختلف تاریخ در پاسخ */
    private fun dateKeyOf(day: JSONObject, fallbackYear: Int): Int? {
        // شکل اول: رشته‌ی "1405-5-12" یا "1405/05/12" در کلید date
        for (k in listOf("date", "jalali", "jdate", "shamsi")) {
            val raw = day.optString(k, "")
            if (raw.isNotBlank()) {
                val parts = raw.split('-', '/', '.').mapNotNull { it.trim().toIntOrNull() }
                if (parts.size == 3) return normalize(parts[0], parts[1], parts[2])
            }
        }
        // شکل دوم: اعداد جدا در کلیدهای year/month/day
        val m = firstInt(day, listOf("month", "jmonth", "m")) ?: return null
        val d = firstInt(day, listOf("day", "jday", "d")) ?: return null
        val y = firstInt(day, listOf("year", "jyear", "y")) ?: fallbackYear
        return normalize(y, m, d)
    }

    private fun normalize(y: Int, m: Int, d: Int): Int? {
        if (m !in 1..12 || d !in 1..31) return null
        if (y < 1300 || y > 1600) return null
        return JalaliDate(y, m, d).key
    }

    private fun firstInt(o: JSONObject, keys: List<String>): Int? =
        keys.firstNotNullOfOrNull { k -> if (o.has(k)) o.optInt(k, -1).takeIf { it >= 0 } else null }

    private fun titleOf(day: JSONObject): String? {
        for (k in TITLE_KEYS) {
            val v = day.optString(k, "").trim()
            if (v.isNotBlank()) return v
        }
        // بعضی منابع مناسبت‌ها را در آرایه‌ی تودرتو می‌گذارند
        val nested = day.optJSONArray("events") ?: return null
        val titles = (0 until nested.length()).mapNotNull { i ->
            nested.optJSONObject(i)?.let { titleOf(it) } ?: nested.optString(i).takeIf { it.isNotBlank() }
        }
        return titles.joinToString("، ").takeIf { it.isNotBlank() }
    }

    private fun isOffOf(day: JSONObject): Boolean {
        for (k in OFF_KEYS) if (day.has(k)) return day.optBoolean(k, false)
        val nested = day.optJSONArray("events")
        if (nested != null) {
            for (i in 0 until nested.length()) {
                val e = nested.optJSONObject(i) ?: continue
                if (OFF_KEYS.any { e.optBoolean(it, false) }) return true
            }
        }
        return false
    }
}
