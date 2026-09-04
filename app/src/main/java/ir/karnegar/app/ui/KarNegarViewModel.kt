package ir.karnegar.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.karnegar.app.calendar.JalaliDate
import ir.karnegar.app.data.EventRules
import ir.karnegar.app.data.EventsRepository
import ir.karnegar.app.data.EventsSyncState
import ir.karnegar.app.data.KarNegarStore
import ir.karnegar.app.model.MonthlySummary
import ir.karnegar.app.model.ShiftEntry
import ir.karnegar.app.model.ShiftType
import ir.karnegar.app.model.WorkCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class KarNegarState(
    val firstName: String = "",
    val lastName: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val shifts: Map<Int, ShiftEntry> = emptyMap(),
    val holidayOverrides: Map<Int, Boolean> = emptyMap(),
    /** قاعده‌های مناسبت از مخزن persian-calendar/events؛ خالی باشد، جدول درون‌برنامه‌ای مبناست */
    val eventRules: EventRules = EventRules.EMPTY,
    val sync: EventsSyncState = EventsSyncState(),
    val visibleYear: Int = JalaliDate.today().year,
    val visibleMonth: Int = JalaliDate.today().month,
    val summaryYear: Int = JalaliDate.today().year,
    val summaryMonth: Int = JalaliDate.today().month,
    val reportYear: Int = JalaliDate.today().year,
    val reportMonth: Int = JalaliDate.today().month
) {
    val fullName: String get() = "$firstName $lastName".trim()
    val isProfileComplete: Boolean get() = firstName.isNotBlank() && lastName.isNotBlank()
}

class KarNegarViewModel(app: Application) : AndroidViewModel(app) {

    private val store = KarNegarStore(app)

    private val _state = MutableStateFlow(
        KarNegarState(
            firstName = store.firstName,
            lastName = store.lastName,
            themeMode = runCatching { ThemeMode.valueOf(store.themeMode) }.getOrDefault(ThemeMode.SYSTEM),
            shifts = store.loadShifts(),
            holidayOverrides = store.loadHolidayOverrides(),
            eventRules = store.loadEventRules(),
            sync = store.loadSyncState()
        )
    )
    val state: StateFlow<KarNegarState> = _state.asStateFlow()

    init {
        // یک بار دریافت، برای همه‌ی سال‌ها: قاعده‌ها سال ندارند. پس فقط وقتی داده
        // نداریم یا بیش از سی روز گذشته است سراغ شبکه می‌رویم.
        if (_state.value.sync.isStale()) syncHolidays(silent = true)
    }

    // ---------- پروفایل ----------

    fun saveProfile(first: String, last: String) {
        store.firstName = first
        store.lastName = last
        _state.update { it.copy(firstName = first.trim(), lastName = last.trim()) }
    }

    // ---------- تم ----------

    fun setThemeMode(mode: ThemeMode) {
        store.themeMode = mode.name
        _state.update { it.copy(themeMode = mode) }
    }

    // ---------- ناوبری ماه ----------

    fun shiftVisibleMonth(delta: Int) = _state.update {
        val (y, m) = addMonths(it.visibleYear, it.visibleMonth, delta)
        it.copy(visibleYear = y, visibleMonth = m)
    }

    fun shiftSummaryMonth(delta: Int) = _state.update {
        val (y, m) = addMonths(it.summaryYear, it.summaryMonth, delta)
        it.copy(summaryYear = y, summaryMonth = m)
    }

    fun shiftReportMonth(delta: Int) = _state.update {
        val (y, m) = addMonths(it.reportYear, it.reportMonth, delta)
        it.copy(reportYear = y, reportMonth = m)
    }

    private fun addMonths(year: Int, month: Int, delta: Int): Pair<Int, Int> {
        var y = year
        var m = month + delta
        while (m > 12) { m -= 12; y += 1 }
        while (m < 1) { m += 12; y -= 1 }
        return y to m
    }

    // ---------- شیفت‌ها ----------

    fun upsertShift(entry: ShiftEntry) {
        // ذخیره‌سازی بیرون از update انجام می‌شود، چون لامبدای update ممکن است
        // در صورت تغییر همزمان وضعیت چند بار اجرا شود.
        val updated = _state.value.shifts.toMutableMap().apply { put(entry.dateKey, entry) }
        store.saveShifts(updated)
        _state.update { it.copy(shifts = updated) }
    }

    fun deleteShift(dateKey: Int) {
        val updated = _state.value.shifts.toMutableMap().apply { remove(dateKey) }
        store.saveShifts(updated)
        _state.update { it.copy(shifts = updated) }
    }

    fun shiftOf(date: JalaliDate): ShiftEntry? = _state.value.shifts[date.key]

    // ---------- تعطیلات دستی ----------

    /** چرخه: خودکار -> تعطیل -> کاری -> خودکار */
    fun cycleHolidayOverride(dateKey: Int) {
        val updated = _state.value.holidayOverrides.toMutableMap()
        when (updated[dateKey]) {
            null -> updated[dateKey] = true
            true -> updated[dateKey] = false
            false -> updated.remove(dateKey)
        }
        store.saveHolidayOverrides(updated)
        _state.update { it.copy(holidayOverrides = updated) }
    }

    fun clearHolidayOverrides() {
        store.saveHolidayOverrides(emptyMap())
        _state.update { it.copy(holidayOverrides = emptyMap()) }
    }

    // ---------- دریافت تقویم از مخزن persian-calendar/events ----------

    /**
     * قاعده‌های مناسبت و تعطیلات را از مخزن می‌گیرد و روی دستگاه ذخیره می‌کند.
     * فقط خواندن است؛ هیچ داده‌ای از کاربر ارسال نمی‌شود.
     *
     * @param silent در دریافت خودکار، خطا در رابط کاربری نشان داده نمی‌شود
     */
    fun syncHolidays(silent: Boolean = false) {
        if (_state.value.sync.inProgress) return
        _state.update { it.copy(sync = it.sync.copy(inProgress = true, lastError = null)) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { EventsRepository.fetch() }
            when (result) {
                is EventsRepository.Result.Success -> {
                    val newSync = EventsSyncState(
                        lastSyncAt = System.currentTimeMillis(),
                        eventCount = result.rules.eventCount,
                        lastError = null,
                        inProgress = false
                    )
                    store.saveEventRules(result.rules)
                    store.saveSyncState(newSync)
                    _state.update { it.copy(eventRules = result.rules, sync = newSync) }
                }

                is EventsRepository.Result.Failure -> {
                    // شکست: کش قبلی دست‌نخورده می‌ماند و اپ با همان کار می‌کند
                    _state.update {
                        it.copy(
                            sync = it.sync.copy(
                                inProgress = false,
                                lastError = if (silent) null else result.message
                            )
                        )
                    }
                }
            }
        }
    }

    /** برگشت به جدول درون‌برنامه‌ای؛ کش تقویم پاک می‌شود */
    fun clearRemoteHolidays() {
        store.clearEventRules()
        _state.update {
            it.copy(eventRules = EventRules.EMPTY, sync = EventsSyncState())
        }
    }

    // ---------- جمع‌بندی ----------

    fun summaryFor(year: Int, month: Int): MonthlySummary {
        val s = _state.value
        // تعطیلات لازم‌اند تا تعطیل‌کاری محاسبه شود؛ همان سه لایه‌ی اولویتی که
        // تقویم هم استفاده می‌کند، وگرنه ممکن است تقویم روزی را تعطیل نشان دهد
        // ولی جمع‌بندی آن را کاری حساب کند
        return WorkCalculator.summarize(
            year = year,
            month = month,
            all = s.shifts.values,
            overrides = s.holidayOverrides,
            rules = s.eventRules
        )
    }

    /** آخرین ماه‌هایی که شیفتی در آن ثبت شده — برای پیشنهاد در گزارش‌گیری */
    fun monthsWithData(): List<Pair<Int, Int>> =
        _state.value.shifts.keys
            .map { JalaliDate.fromKey(it) }
            .map { it.year to it.month }
            .distinct()
            .sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })

    /** ساعت پیش‌فرض ورود/خروج برای شیفتی که کاربر انتخاب می‌کند */
    fun defaultTimesFor(type: ShiftType): Pair<Int, Int> = type.startMinutes to type.endMinutes
}
