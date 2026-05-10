package ly.payhub.merchant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import ly.payhub.merchant.data.MerchantRepository
import ly.payhub.merchant.ui.AppNavHost
import ly.payhub.merchant.ui.theme.PayHubMerchantTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Injected only so the repository (and its AuthState bootstrap) is alive as
    // early as possible — the splash screen waits on it.
    @Inject lateinit var repository: MerchantRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep the system splash up until the repository has resolved the
        // persisted token (→ AuthState leaves `Bootstrapping`). The in-app
        // SplashScreen composable covers the brief gap after that.
        splash.setKeepOnScreenCondition {
            repository.authState.value is MerchantRepository.AuthState.Bootstrapping
        }
        repository.bootstrap { /* AuthState drives both the splash and the nav */ }

        // The deep-link intent (payhub://accept-invite?…) is read by AppNavHost.
        val initialUri = intent?.data?.toString()

        setContent {
            PayHubMerchantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    val authState by repository.authState.collectAsStateWithLifecycle()
                    AppNavHost(
                        navController = navController,
                        authState = authState,
                        initialDeepLink = initialUri,
                    )
                }
            }
        }
    }
}
