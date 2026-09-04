package ir.karnegar.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * دریافت مناسبت‌ها و تعطیلات از مخزن persian-calendar/events.
 *
 * داده‌ها از اینترنت دریافت و به ساختار مورد استفاده برنامه تبدیل می‌شوند.
 */
object EventsRepository {

    private const val TIMEOUT_MS = 15_000

    private val URLS = listOf(
        "https://raw.githubusercontent.com/persian-calendar/events/main/events.json",
        "https://raw.githubusercontent.com/persian-calendar/events/master/events.json"
    )

    sealed interface Result {
        data class Success(val rules: EventRules) : Result
        data class Failure(val message: String) : Result
    }

    /**
     * دریافت و پردازش داده‌های مناسبت‌ها.
     *
     * این تابع عملیات شبکه انجام می‌دهد و باید از Dispatchers.IO فراخوانی شود.
     */
    fun fetch(): Result {
        val errors = mutableListOf<String>()

        for (url in URLS) {
            val outcome = runCatching {
                parse(get(url))
            }

            outcome.onSuccess { rules ->
                if (rules.eventCount >= 50) {
                    return Result.Success(rules)
                }

                errors += "پاسخ ناقص بود (${rules.eventCount} مناسبت)"
            }

            outcome.onFailure { error ->
                errors += error.message ?: error.javaClass.simpleName
            }
        }

        return Result.Failure(
            errors
                .distinct()
                .joinToString(" / ")
                .ifBlank { "منبعی پاسخ نداد" }
        )
    }

    // ---------------------------------------------------------
    // شبکه
    // ---------------------------------------------------------

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection

        connection.apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true

            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "KarNegar/1.5.0 (Android)")
        }

        require(connection is HttpsURLConnection) {
            "اتصال ناامن رد شد"
        }

        try {
            val responseCode = connection.responseCode

            require(responseCode == HttpURLConnection.HTTP_OK) {
                "کد پاسخ سرور: $responseCode"
            }

            return connection.inputStream
                .bufferedReader()
                .use(BufferedReader::readText)

        } finally {
            connection.disconnect()
        }
    }

    // ---------------------------------------------------------
    // پارس JSON
    // ---------------------------------------------------------

    private fun parse(body: String): EventRules {
        val trimmed = body.trim()

        return when {
            trimmed.startsWith("{") -> {
                parseObject(JSONObject(trimmed))
            }

            trimmed.startsWith("[") -> {
                parseFlatArray(JSONArray(trimmed))
            }

            else -> {
                throw IllegalStateException("پاسخ دریافتی JSON معتبر نیست")
            }
        }
    }

    /**
     * ساختارهای مختلف JSON را پشتیبانی می‌کند.
     *
     * ساختار فعلی منبع:
     *
     * {
     *   "Source": {...},
     *   "#meta": [...],
     *   "data": [
     *      {
     *          "calendar": "Persian",
     *          "month": 1,
     *          "day": 1,
     *          ...
     *      }
     *   ]
     * }
     */
    private fun parseObject(root: JSONObject): EventRules {

        // -----------------------------------------------------
        // ساختار فعلی منبع:
        // root["data"] = JSONArray
        // -----------------------------------------------------
        root.optJSONArray("data")?.let { data ->
            return parseFlatArray(data)
        }

        // -----------------------------------------------------
        // پشتیبانی از ساختارهای قدیمی یا جایگزین
        // -----------------------------------------------------

        val solar = collect(
            root,
            SOLAR_KEYS,
            EventCalendar.SOLAR
        )

        val lunar = collect(
            root,
            LUNAR_KEYS,
            EventCalendar.LUNAR
        )

        val gregorian = collect(
            root,
            GREGORIAN_KEYS,
            EventCalendar.GREGORIAN
        )

        if (
            solar.isNotEmpty() ||
            lunar.isNotEmpty() ||
            gregorian.isNotEmpty()
        ) {
            return EventRules(
                solar = solar,
                lunar = lunar,
                gregorian = gregorian
            )
        }

        // -----------------------------------------------------
        // جستجو در ساختارهای تودرتو
        // -----------------------------------------------------

        val iterator = root.keys()

        while (iterator.hasNext()) {
            val key = iterator.next()

            root.optJSONArray(key)?.let { array ->
                val rules = parseFlatArray(array)

                if (!rules.isEmpty) {
                    return rules
                }
            }

            root.optJSONObject(key)?.let { nestedObject ->
                val rules = runCatching {
                    parseObject(nestedObject)
                }.getOrNull()

                if (rules != null && !rules.isEmpty) {
                    return rules
                }
            }
        }

        throw IllegalStateException(
            "دسته یا آرایه مناسبت‌ها در داده دریافتی پیدا نشد"
        )
    }

    // ---------------------------------------------------------
    // کلیدهای ساختارهای قدیمی
    // ---------------------------------------------------------

    private val SOLAR_KEYS = listOf(
        "Persian Calendar",
        "PersianCalendar",
        "persian",
        "solar",
        "Solar Calendar"
    )

    private val LUNAR_KEYS = listOf(
        "Hijri Calendar",
        "HijriCalendar",
        "hijri",
        "lunar",
        "Islamic Calendar"
    )

    private val GREGORIAN_KEYS = listOf(
        "Gregorian Calendar",
        "GregorianCalendar",
        "gregorian"
    )

    private val TITLE_KEYS = listOf(
        "title",
        "name",
        "event",
        "description"
    )

    private val HOLIDAY_KEYS = listOf(
        "holiday",
        "isHoliday",
        "is_holiday",
        "isOff"
    )

    // ---------------------------------------------------------
    // خواندن آرایه‌های دسته‌بندی‌شده
    // ---------------------------------------------------------

    private fun collect(
        root: JSONObject,
        keys: List<String>,
        calendar: EventCalendar
    ): Map<Int, List<CalendarEvent>> {

        val array = keys
            .firstNotNullOfOrNull { key ->
                root.optJSONArray(key)
            }
            ?: return emptyMap()

        val result =
            linkedMapOf<Int, MutableList<CalendarEvent>>()

        for (i in 0 until array.length()) {

            runCatching {

                val event =
                    array.optJSONObject(i)
                        ?: return@runCatching

                addTo(
                    target = result,
                    o = event,
                    calendar = calendar
                )
            }
        }

        return result
    }

    // ---------------------------------------------------------
    // ساختار آرایه‌ای
    // ---------------------------------------------------------

    /**
     * هر عنصر آرایه دارای فیلد calendar است.
     *
     * مثال:
     *
     * "calendar": "Persian"
     * "calendar": "Hijri"
     * "calendar": "Gregorian"
     */
    private fun parseFlatArray(
        array: JSONArray
    ): EventRules {

        val solar =
            linkedMapOf<Int, MutableList<CalendarEvent>>()

        val lunar =
            linkedMapOf<Int, MutableList<CalendarEvent>>()

        val gregorian =
            linkedMapOf<Int, MutableList<CalendarEvent>>()

        for (i in 0 until array.length()) {

            runCatching {

                val event =
                    array.optJSONObject(i)
                        ?: return@runCatching

                val rawCalendar =
                    event.optString("calendar", "")
                        .trim()
                        .lowercase()

                val calendar = when {

                    rawCalendar.contains("hijri") ||
                        rawCalendar.contains("lunar") ||
                        rawCalendar.contains("islamic") -> {

                        EventCalendar.LUNAR
                    }

                    rawCalendar.contains("gregorian") -> {

                        EventCalendar.GREGORIAN
                    }

                    rawCalendar.contains("persian") ||
                        rawCalendar.contains("solar") ||
                        rawCalendar.contains("jalali") -> {

                        EventCalendar.SOLAR
                    }

                    else -> {
                        return@runCatching
                    }
                }

                val target = when (calendar) {

                    EventCalendar.SOLAR -> solar

                    EventCalendar.LUNAR -> lunar

                    EventCalendar.GREGORIAN -> gregorian
                }

                addTo(
                    target = target,
                    o = event,
                    calendar = calendar
                )
            }
        }

        return EventRules(
            solar = solar,
            lunar = lunar,
            gregorian = gregorian
        )
    }

    // ---------------------------------------------------------
    // افزودن مناسبت
    // ---------------------------------------------------------

    /**
     * یک مناسبت JSON را به ساختار برنامه اضافه می‌کند.
     *
     * کلید ذخیره‌سازی:
     *
     * month * 100 + day
     *
     * مثال:
     *
     * 1/1  -> 101
     * 12/29 -> 1229
     */
    private fun addTo(
        target: MutableMap<Int, MutableList<CalendarEvent>>,
        o: JSONObject,
        calendar: EventCalendar
    ) {

        // -----------------------------------------------------
        // فقط مناسبت‌های ایران و ایران باستان
        // -----------------------------------------------------

        val type =
            o.optString("type", "")
                .trim()
                .lowercase()

        if (
            type.isNotBlank() &&
            !type.contains("iran")
        ) {
            return
        }

        // -----------------------------------------------------
        // ماه و روز
        // -----------------------------------------------------

        val month =
            intOf(
                o,
                listOf("month", "m")
            )
            ?: return

        val day =
            intOf(
                o,
                listOf("day", "d")
            )
            ?: return

        if (month !in 1..12) {
            return
        }

        if (day !in 1..31) {
            return
        }

        // -----------------------------------------------------
        // عنوان
        // -----------------------------------------------------

        val title =
            TITLE_KEYS
                .firstNotNullOfOrNull { key ->

                    o.optString(key, "")
                        .trim()
                        .takeIf { it.isNotBlank() }
                }
                ?: return

        // -----------------------------------------------------
        // وضعیت تعطیل بودن
        // -----------------------------------------------------

        val isHoliday =
            HOLIDAY_KEYS.any { key ->

                when {
                    o.optBoolean(key, false) -> true

                    o.optString(key, "")
                        .equals(
                            "true",
                            ignoreCase = true
                        ) -> true

                    else -> false
                }
            }

        // -----------------------------------------------------
        // ذخیره
        // -----------------------------------------------------

        val key =
            month * 100 + day

        target
            .getOrPut(key) {
                mutableListOf()
            }
            .add(
                CalendarEvent(
                    title = title,
                    isHoliday = isHoliday,
                    calendar = calendar
                )
            )
    }

    // ---------------------------------------------------------
    // تبدیل مقدار JSON به عدد
    // ---------------------------------------------------------

    private fun intOf(
        o: JSONObject,
        keys: List<String>
    ): Int? {

        for (key in keys) {

            if (!o.has(key)) {
                continue
            }

            val value = o.opt(key)

            when (value) {

                is Int -> {
                    return value
                }

                is Long -> {
                    return value.toInt()
                }

                is Double -> {
                    return value.toInt()
                }

                is String -> {
                    value
                        .trim()
                        .toIntOrNull()
                        ?.let {
                            return it
                        }
                }
            }
        }

        return null
    }
}
