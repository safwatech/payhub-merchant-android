package ly.payhub.merchant.ui.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import ly.payhub.merchant.R
import ly.payhub.merchant.ui.screen.dashboard.DashboardScreen
import ly.payhub.merchant.ui.screen.more.MoreScreen
import ly.payhub.merchant.ui.screen.payments.PaymentsScreen
import ly.payhub.merchant.ui.screen.paylinks.PayLinksScreen

private enum class HomeTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Dashboard(R.string.nav_dashboard, Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    PayLinks(R.string.nav_paylinks, Icons.Rounded.ReceiptLong, Icons.Outlined.ReceiptLong),
    Payments(R.string.nav_payments, Icons.Rounded.Payments, Icons.Outlined.Payments),
    More(R.string.nav_more, Icons.Rounded.MoreHoriz, Icons.Outlined.MoreHoriz),
}

/**
 * The signed-in shell: a Material 3 bottom [NavigationBar] over four tabs.
 * Stack-routed detail screens (pay-link create/detail, payment detail,
 * settlements) are pushed *above* this by [ly.payhub.merchant.ui.AppNavHost],
 * hence the callbacks here just bubble up.
 */
@Composable
fun HomeScreen(
    onCreatePayLink: () -> Unit,
    onOpenPayLink: (String) -> Unit,
    onOpenPayment: (String) -> Unit,
    onOpenSettlements: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onOpenMfaSettings: () -> Unit,
    onOpenOrgProfile: () -> Unit,
    onOpenSubMerchants: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.Dashboard) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { entry ->
                    val label = stringResource(entry.labelRes)
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = {
                            Icon(
                                if (tab == entry) entry.selectedIcon else entry.unselectedIcon,
                                contentDescription = label,
                            )
                        },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { inner ->
        val content = Modifier.padding(inner)
        when (tab) {
            HomeTab.Dashboard -> DashboardScreen(modifier = content)
            HomeTab.PayLinks -> PayLinksScreen(
                modifier = content,
                onCreate = onCreatePayLink,
                onOpen = onOpenPayLink,
            )
            HomeTab.Payments -> PaymentsScreen(
                modifier = content,
                onOpen = onOpenPayment,
            )
            HomeTab.More -> MoreScreen(
                modifier = content,
                onOpenSettlements = onOpenSettlements,
                onOpenChangePassword = onOpenChangePassword,
                onOpenMfaSettings = onOpenMfaSettings,
                onOpenOrgProfile = onOpenOrgProfile,
                onOpenSubMerchants = onOpenSubMerchants,
            )
        }
    }
}

