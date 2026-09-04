package ir.karnegar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.karnegar.app.ui.KarNegarApp
import ir.karnegar.app.ui.KarNegarViewModel
import ir.karnegar.app.ui.screens.OnboardingScreen
import ir.karnegar.app.ui.theme.KarNegarTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: KarNegarViewModel = viewModel()
            val state by vm.state.collectAsState()

            KarNegarTheme(themeMode = state.themeMode) {
                // کل رابط کاربری راست‌به‌چپ است
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize()) {
                        if (state.isProfileComplete) {
                            KarNegarApp(viewModel = vm, state = state)
                        } else {
                            OnboardingScreen(
                                initialFirst = state.firstName,
                                initialLast = state.lastName,
                                onSubmit = vm::saveProfile
                            )
                        }
                    }
                }
            }
        }
    }
}
