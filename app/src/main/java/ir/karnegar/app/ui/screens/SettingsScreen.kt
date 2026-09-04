package ir.karnegar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ir.karnegar.app.BuildConfig
import ir.karnegar.app.calendar.PersianDate
import ir.karnegar.app.calendar.ltrIsolate
import ir.karnegar.app.calendar.toPersianDigits
import ir.karnegar.app.ui.KarNegarState
import ir.karnegar.app.ui.ThemeMode
import ir.karnegar.app.ui.components.LabeledValue
import ir.karnegar.app.ui.components.SectionCard

/**
 * تب تنظیمات — نام کاربر، انتخاب تم، مدیریت تعطیلات دستی، و اطلاعات نسخه.
 */
@Composable
fun SettingsScreen(
    state: KarNegarState,
    onSaveProfile: (String, String) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onClearHolidayOverrides: () -> Unit,
    onSyncHolidays: () -> Unit = {},
    onClearRemoteHolidays: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---------- پروفایل ----------
        SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp).size(26.dp)
                    )
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        state.fullName.ifBlank { "بدون نام" },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "کارمند",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { editing = true }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "ویرایش نام")
                }
            }
        }

        // ---------- تم ----------
        SectionCard(title = "نمایش") {
            ThemeOption(
                title = "تم روشن",
                icon = Icons.Rounded.LightMode,
                selected = state.themeMode == ThemeMode.LIGHT,
                onClick = { onThemeChange(ThemeMode.LIGHT) }
            )
            ThemeOption(
                title = "تم تاریک",
                icon = Icons.Rounded.DarkMode,
                selected = state.themeMode == ThemeMode.DARK,
                onClick = { onThemeChange(ThemeMode.DARK) }
            )
            ThemeOption(
                title = "هماهنگ با سیستم",
                icon = Icons.Rounded.Brightness6,
                selected = state.themeMode == ThemeMode.SYSTEM,
                onClick = { onThemeChange(ThemeMode.SYSTEM) }
            )
        }

        // ---------- تقویم و تعطیلات ----------
        SectionCard(title = "تقویم و تعطیلات") {
            val sync = state.sync
            LabeledValue(
                "منبع تعطیلات",
                if (sync.hasData) "Persian Calendar"
                else "تقویم درون‌برنامه‌ای"
            )
            LabeledValue(
                "آخرین به‌روزرسانی",
                if (sync.lastSyncAt > 0L) formatSyncTime(sync.lastSyncAt) else "هنوز انجام نشده"
            )
            if (sync.eventCount > 0) {
                LabeledValue(
                    "مناسبت‌های دریافت‌شده",
                    "${sync.eventCount.toPersianDigits()} مورد"
                )
            }
            if (sync.inProgress) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        "در حال به‌روزرسانی تقویم…",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            if (sync.lastError != null) {
                Text(
                    "به‌روزرسانی ناموفق بود؛ تقویم درون‌برنامه‌ای استفاده می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row {
                TextButton(onClick = onSyncHolidays, enabled = !sync.inProgress) {
                    Text("به‌روزرسانی تقویم")
                }
                if (sync.hasData) {
                    TextButton(onClick = onClearRemoteHolidays, enabled = !sync.inProgress) {
                        Text("پاک کردن کش")
                    }
                }
            }
            Text(
                "مناسبت‌ها و تعطیلات رسمیِ شمسی و قمری از مخزن Persian Calendar " +
                        "خوانده می‌شود. این داده «قاعده» است نه فهرست یک سال، پس یک بار دریافت برای " +
                        "همه‌ی سال‌ها کافی است و اپ بدون اینترنت هم درست کار می‌کند. فقط خوانده " +
                        "می‌شود؛ نام، شیفت‌ها و کارکرد شما هیچ‌وقت ارسال نمی‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---------- داده‌ها ----------
        SectionCard(title = "داده‌ها") {
            LabeledValue("تعداد شیفت‌های ثبت‌شده", "${state.shifts.size.toPersianDigits()} شیفت")
            LabeledValue(
                "روزهای تعطیل دستی",
                "${state.holidayOverrides.size.toPersianDigits()} روز"
            )
            if (state.holidayOverrides.isNotEmpty()) {
                TextButton(onClick = onClearHolidayOverrides) {
                    Text("بازگرداندن تعطیلات به حالت پیش‌فرض")
                }
            }
            Text(
                "برای تعطیل یا کاری کردن دستی یک روز، در تب تقویم روی آن روز نگه دارید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---------- درباره ----------
        SectionCard(title = "درباره برنامه") {
            LabeledValue("نام برنامه", "کارنگار (KarNegar)")
            LabeledValue("نسخه", BuildConfig.VERSION_NAME.toPersianDigits())
            LabeledValue("شماره ساخت", BuildConfig.VERSION_CODE.toPersianDigits())
            HorizontalDivider()
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.Info,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "کارنگار یک ابزار شخصی است. تمام داده‌ها فقط روی همین دستگاه ذخیره می‌شود، " +
                            "هیچ حساب کاربری وجود ندارد و هیچ‌کس دیگری به کارکرد شما دسترسی ندارد. " +
                            "تنها استفاده از اینترنت، خواندن تاریخ تعطیلات رسمی از تقویم آنلاین است " +
                            "— یک‌طرفه و فقط خواندنی.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

    // ---------- ویرایش نام ----------
    if (editing) {
        var first by remember { mutableStateOf(state.firstName) }
        var last by remember { mutableStateOf(state.lastName) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("ویرایش نام") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = first,
                        onValueChange = { first = it },
                        label = { Text("نام") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = last,
                        onValueChange = { last = it },
                        label = { Text("نام خانوادگی") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onSaveProfile(first.trim(), last.trim()); editing = false },
                    enabled = first.isNotBlank() && last.isNotBlank()
                ) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("انصراف") }
            }
        )
    }
}

/**
 * تبدیل زمان میلی‌ثانیه‌ای به تاریخ شمسی و ساعت — مثال: «۱۴۰۵/۰۵/۳۱ — ۱۴:۰۵».
 * از تقویم دستگاه برای اجزای میلادی استفاده می‌کند و سپس به شمسی تبدیل می‌شود.
 */
private fun formatSyncTime(millis: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val jalali = PersianDate.fromJdn(
        PersianDate.gregorianToJdn(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    )
    val clock = "%02d:%02d".format(
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE)
    )
    // همان دلیل ReportScreen: در رابط RTL، اسلش و دونقطه ترتیب پاره‌های عددی را
    // برمی‌گردانند، پس هر دو تکه جداگانه در جداساز چپ‌به‌راست پیچیده می‌شوند
    return "${jalali.formatted().ltrIsolate()} — ${clock.toPersianDigits().ltrIsolate()}"
}

@Composable
private fun ThemeOption(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // کلیک روی کل سطر کار می‌کند، پس خود دکمه رویداد مستقل ندارد
        RadioButton(selected = selected, onClick = null)
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(start = 10.dp)
        )
    }
}
