package ly.payhub.merchant.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ly.payhub.merchant.ui.components.BrandLockup

/**
 * In-app splash, shown only for the brief window between Compose taking over
 * and [ly.payhub.merchant.data.MerchantRepository.authState] settling out of
 * `Bootstrapping`. The system splash window covers the cold-start before this.
 */
@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            BrandLockup(tagline = "Payments, in one place")
            CircularProgressIndicator()
        }
    }
}
