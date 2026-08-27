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

    // ---------- تعطیلات دریافتی از اینترنت ----------

    /**
     * تعطیلات کش‌شده. یک بار از اینترنت گرفته می‌شوند و بعد از آن اپ بی‌نیاز از
     * شبکه کار می‌کند؛ چیزی به بیرون فرستاده نمی‌شود، فقط خوانده می‌شود.
     */
    fun loadRemoteHolidays(): Map<Int, RemoteHoliday> {
        val raw = prefs.getString(KEY_REMOTE_HOLIDAYS, null) ?: return emptyMap()
        val result = linkedMapOf<Int, RemoteHoliday>()
        val outcome = runCatching {
            val o = JSONObject(raw)
            for (key in o.keys()) {
                runCatching {
                    val item = o.getJSONObject(key)
                    result[key.toInt()] = RemoteHoliday(
                        title = item.optString("t"),
                        isOff = item.optBoolean("o", true)
                    )
                }
            }
        }
        if (outcome.isFailure) backupCorrupt(KEY_REMOTE_HOLIDAYS, raw)
        return result
    }

    fun saveRemoteHolidays(days: Map<Int, RemoteHoliday>) {
        val o = JSONObject()
        for ((k, v) in days) {
            o.put(k.toString(), JSONObject().apply { put("t", v.title); put("o", v.isOff) })
        }
        prefs.edit().putString(KEY_REMOTE_HOLIDAYS, o.toString()).apply()
    }

    fun loadSyncState(): HolidaySyncState {
        val years = prefs.getString(KEY_SYNCED_YEARS, "")
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            ?: emptySet()
        return HolidaySyncState(
            lastSyncAt = prefs.getLong(KEY_LAST_SYNC, 0L),
            syncedYears = years,
            source = prefs.getString(KEY_SYNC_SOURCE, "") ?: ""
        )
    }

    fun saveSyncState(state: HolidaySyncState) {
        prefs.edit()
            .putLong(KEY_LAST_SYNC, state.lastSyncAt)
            .putString(KEY_SYNCED_YEARS, state.syncedYears.joinToString(","))
            .putString(KEY_SYNC_SOURCE, state.source)
            .apply()
    }

    /** پاک‌کردن کش تقویم؛ اپ به جدول درون‌برنامه‌ای برمی‌گردد */
    fun clearRemoteHolidays() {
        prefs.edit()
            .remove(KEY_REMOTE_HOLIDAYS)
            .remove(KEY_LAST_SYNC)
            .remove(KEY_SYNCED_YEARS)
            .remove(KEY_SYNC_SOURCE)
            .apply()
    }

    private companion object {
        const val KEY_FIRST_NAME = "first_name"
        const val KEY_LAST_NAME = "last_name"
        const val KEY_THEME = "theme_mode"
        const val KEY_SHIFTS = "shifts_json"
        const val KEY_HOLIDAY_OVERRIDES = "holiday_overrides_json"
        const val KEY_REMOTE_HOLIDAYS = "remote_holidays_json"
        const val KEY_LAST_SYNC = "holidays_last_sync"
        const val KEY_SYNCED_YEARS = "holidays_synced_years"
        const val KEY_SYNC_SOURCE = "holidays_sync_source"
    }
}
