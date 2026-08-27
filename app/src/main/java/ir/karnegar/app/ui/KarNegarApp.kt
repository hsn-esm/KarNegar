package ir.karnegar.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
// آیکون‌های توپر و خط‌دار هم‌نام‌اند، پس با import ستاره‌ای وارد می‌شوند تا
// Kotlin بر پایه‌ی نوع گیرنده (Icons.Filled یا Icons.Outlined) تفکیک کند.
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ir.karnegar.app.ui.screens.CalendarScreen
import ir.karnegar.app.ui.screens.ReportScreen
import ir.karnegar.app.ui.screens.SettingsScreen
import ir.karnegar.app.ui.screens.SummaryScreen

/**
 * هر تب دو آیکون دارد: نسخه‌ی توپر برای حالت انتخاب‌شده و نسخه‌ی خط‌دار برای بقیه.
 * این همان الگوی Material 3 است و نوار پایین را تمیزتر و مدرن‌تر نشان می‌دهد.
 */
private data class Tab(val title: String, val selectedIcon: ImageVector, val icon: ImageVector)

private val TABS = listOf(
    Tab("تقویم", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    Tab("خلاصه کارکرد", Icons.Filled.Insights, Icons.Outlined.Insights),
    Tab("گزارش‌گیری", Icons.Filled.Article, Icons.Outlined.Article),
    Tab("تنظیمات", Icons.Filled.Tune, Icons.Outlined.Tune)
)

/**
 * پوسته‌ی اصلی اپ با نوار پایین چهار بخشی.
 */
@Composable
fun KarNegarApp(viewModel: KarNegarViewModel, state: KarNegarState) {
    // با rememberSaveable تب انتخابی پس از چرخش صفحه یا بازسازی Activity حفظ می‌شود
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(tonalElevation = 0.dp) {
                TABS.forEachIndexed { index, tab ->
                    val selected = tabIndex == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tabIndex = index },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.icon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (tabIndex) {
            0 -> CalendarScreen(
                state = state,
                onPreviousMonth = { viewModel.shiftVisibleMonth(-1) },
                onNextMonth = { viewModel.shiftVisibleMonth(1) },
                onSaveShift = viewModel::upsertShift,
                onDeleteShift = viewModel::deleteShift,
                onToggleHoliday = viewModel::cycleHolidayOverride,
                modifier = contentModifier
            )

            1 -> SummaryScreen(
                summary = viewModel.summaryFor(state.summaryYear, state.summaryMonth),
                onPreviousMonth = { viewModel.shiftSummaryMonth(-1) },
                onNextMonth = { viewModel.shiftSummaryMonth(1) },
                modifier = contentModifier
            )

            2 -> ReportScreen(
                fullName = state.fullName,
                summary = viewModel.summaryFor(state.reportYear, state.reportMonth),
                onPreviousMonth = { viewModel.shiftReportMonth(-1) },
                onNextMonth = { viewModel.shiftReportMonth(1) },
                modifier = contentModifier
            )

            else -> SettingsScreen(
                state = state,
                onSaveProfile = viewModel::saveProfile,
                onThemeChange = viewModel::setThemeMode,
                onClearHolidayOverrides = viewModel::clearHolidayOverrides,
                onSyncHolidays = { viewModel.syncHolidays() },
                onClearRemoteHolidays = viewModel::clearRemoteHolidays,
                modifier = contentModifier
            )
        }
    }
}
