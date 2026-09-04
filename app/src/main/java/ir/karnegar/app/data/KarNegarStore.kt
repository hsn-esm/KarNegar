package ir.karnegar.app.data

import android.content.Context
import android.content.SharedPreferences
import ir.karnegar.app.model.ShiftEntry
import ir.karnegar.app.model.ShiftType
import org.json.JSONArray
import org.json.JSONObject

/**
 * ذخیره‌سازی محلی روی SharedPreferences با سریال‌سازی JSON.
 * چیزی به بیرون از دستگاه فرستاده نمی‌شود — همه‌ی داده‌ها شخصی و آفلاین است.
 */
class KarNegarStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("karnegar_store", Context.MODE_PRIVATE)

    // ---------- پروفایل ----------

    var firstName: String
        get() = prefs.getString(KEY_FIRST_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FIRST_NAME, value.trim()).apply()

    var lastName: String
        get() = prefs.getString(KEY_LAST_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_NAME, value.trim()).apply()

    val fullName: String get() = "$firstName $lastName".trim()

    val isProfileComplete: Boolean get() = firstName.isNotBlank() && lastName.isNotBlank()

    // ---------- تم ----------

    /** SYSTEM / LIGHT / DARK */
    var themeMode: String
        get() = prefs.getString(KEY_THEME, "SYSTEM") ?: "SYSTEM"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    // ---------- شیفت‌ها ----------

    fun loadShifts(): Map<Int, ShiftEntry> {
        val raw = prefs.getString(KEY_SHIFTS, null) ?: return emptyMap()
        val result = linkedMapOf<Int, ShiftEntry>()
        val outcome = runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                // خرابی یک رکورد نباید بقیه‌ی شیفت‌ها را از بین ببرد
                runCatching {
                    val o = arr.getJSONObject(i)
                    val type = ShiftType.fromName(o.optString("type")) ?: return@runCatching
                    val entry = ShiftEntry(
                        dateKey = o.getInt("date"),
                        type = type,
                        actualInMinutes = o.getInt("in"),
                        actualOutMinutes = o.getInt("out"),
                        note = o.optString("note", "")
                    )
                    result[entry.dateKey] = entry
                }
            }
        }
        // اگر کل رشته خراب بود، نسخه‌ی خام را نگه می‌داریم تا با ذخیره‌ی بعدی از بین نرود
        if (outcome.isFailure) backupCorrupt(KEY_SHIFTS, raw)
        return result
    }

    fun saveShifts(shifts: Map<Int, ShiftEntry>) {
        val arr = JSONArray()
        for (e in shifts.values) {
            arr.put(
                JSONObject().apply {
                    put("date", e.dateKey)
                    put("type", e.type.name)
                    put("in", e.actualInMinutes)
                    put("out", e.actualOutMinutes)
                    put("note", e.note)
                }
            )
        }
        prefs.edit().putString(KEY_SHIFTS, arr.toString()).apply()
    }

    // ---------- تعطیلات دستی ----------

    fun loadHolidayOverrides(): Map<Int, Boolean> {
        val raw = prefs.getString(KEY_HOLIDAY_OVERRIDES, null) ?: return emptyMap()
        val result = linkedMapOf<Int, Boolean>()
        val outcome = runCatching {
            val o = JSONObject(raw)
            for (key in o.keys()) {
                runCatching { result[key.toInt()] = o.getBoolean(key) }
            }
        }
        if (outcome.isFailure) backupCorrupt(KEY_HOLIDAY_OVERRIDES, raw)
        return result
    }

    /** نگه‌داشتن نسخه‌ی خام داده‌ی خراب، تا در صورت نیاز قابل بازیابی دستی باشد */
    private fun backupCorrupt(key: String, raw: String) {
        prefs.edit().putString("${key}_corrupt_backup", raw).apply()
    }

    fun saveHolidayOverrides(overrides: Map<Int, Boolean>) {
        val o = JSONObject()
        for ((k, v) in overrides) o.put(k.toString(), v)
        prefs.edit().putString(KEY_HOLIDAY_OVERRIDES, o.toString()).apply()
    }

    // ---------- قاعده‌های مناسبت از مخزن persian-calendar/events ----------

    /**
     * قاعده‌های کش‌شده. یک بار از مخزن گرفته می‌شوند و بعد از آن اپ بی‌نیاز از
     * شبکه کار می‌کند؛ چیزی به بیرون فرستاده نمی‌شود، فقط خوانده می‌شود.
     *
     * ساختار ذخیره‌شده کوتاه نگه داشته شده چون در SharedPreferences می‌نشیند:
     * `{"s":{"101":[{"t":"…","h":true}]},"l":{…},"g":{…}}`
     */
    fun loadEventRules(): EventRules {
        val raw = prefs.getString(KEY_EVENT_RULES, null) ?: return EventRules.EMPTY
        val outcome = runCatching {
            val root = JSONObject(raw)
            EventRules(
                solar = readGroup(root, "s", EventCalendar.SOLAR),
                lunar = readGroup(root, "l", EventCalendar.LUNAR),
                gregorian = readGroup(root, "g", EventCalendar.GREGORIAN)
            )
        }
        if (outcome.isFailure) backupCorrupt(KEY_EVENT_RULES, raw)
        return outcome.getOrDefault(EventRules.EMPTY)
    }

    private fun readGroup(
        root: JSONObject,
        key: String,
        calendar: EventCalendar
    ): Map<Int, List<CalendarEvent>> {
        val group = root.optJSONObject(key) ?: return emptyMap()
        val result = linkedMapOf<Int, List<CalendarEvent>>()
        for (dayKey in group.keys()) {
            runCatching {
                val arr = group.getJSONArray(dayKey)
                val list = (0 until arr.length()).mapNotNull { i ->
                    val item = arr.optJSONObject(i) ?: return@mapNotNull null
                    CalendarEvent(
                        title = item.optString("t"),
                        isHoliday = item.optBoolean("h", false),
                        calendar = calendar
                    )
                }
                if (list.isNotEmpty()) result[dayKey.toInt()] = list
            }
        }
        return result
    }

    fun saveEventRules(rules: EventRules) {
        val root = JSONObject().apply {
            put("s", writeGroup(rules.solar))
            put("l", writeGroup(rules.lunar))
            put("g", writeGroup(rules.gregorian))
        }
        prefs.edit().putString(KEY_EVENT_RULES, root.toString()).apply()
    }

    private fun writeGroup(group: Map<Int, List<CalendarEvent>>): JSONObject =
        JSONObject().apply {
            for ((dayKey, events) in group) {
                val arr = JSONArray()
                for (e in events) {
                    arr.put(JSONObject().apply { put("t", e.title); put("h", e.isHoliday) })
                }
                put(dayKey.toString(), arr)
            }
        }

    fun loadSyncState(): EventsSyncState = EventsSyncState(
        lastSyncAt = prefs.getLong(KEY_LAST_SYNC, 0L),
        eventCount = prefs.getInt(KEY_EVENT_COUNT, 0)
    )

    fun saveSyncState(state: EventsSyncState) {
        prefs.edit()
            .putLong(KEY_LAST_SYNC, state.lastSyncAt)
            .putInt(KEY_EVENT_COUNT, state.eventCount)
            .apply()
    }

    /** پاک‌کردن کش تقویم؛ اپ به جدول درون‌برنامه‌ای برمی‌گردد */
    fun clearEventRules() {
        prefs.edit()
            .remove(KEY_EVENT_RULES)
            .remove(KEY_LAST_SYNC)
            .remove(KEY_EVENT_COUNT)
            // کلیدهای نسخه‌ی پیشین هم پاک می‌شوند تا در حافظه نمانند
            .remove("remote_holidays_json")
            .remove("holidays_synced_years")
            .remove("holidays_sync_source")
            .apply()
    }

    private companion object {
        const val KEY_FIRST_NAME = "first_name"
        const val KEY_LAST_NAME = "last_name"
        const val KEY_THEME = "theme_mode"
        const val KEY_SHIFTS = "shifts_json"
        const val KEY_HOLIDAY_OVERRIDES = "holiday_overrides_json"
        const val KEY_EVENT_RULES = "event_rules_json"
        const val KEY_LAST_SYNC = "events_last_sync"
        const val KEY_EVENT_COUNT = "events_count"
    }
}
