package ly.payhub.merchant.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import ly.payhub.merchant.data.MerchantRepository.AuthState
import ly.payhub.merchant.ui.screen.HomeScreen
import ly.payhub.merchant.ui.screen.SplashScreen
import ly.payhub.merchant.ui.screen.account.ChangePasswordScreen
import ly.payhub.merchant.ui.screen.account.MfaSettingsScreen
import ly.payhub.merchant.ui.screen.auth.AcceptInviteScreen
import ly.payhub.merchant.ui.screen.auth.LoginScreen
import ly.payhub.merchant.ui.screen.auth.MfaScreen
import ly.payhub.merchant.ui.screen.org.OrgProfileScreen
import ly.payhub.merchant.ui.screen.payments.PaymentDetailScreen
import ly.payhub.merchant.ui.screen.paylinks.CreatePayLinkScreen
import ly.payhub.merchant.ui.screen.paylinks.PayLinkDetailScreen
import ly.payhub.merchant.ui.screen.settlements.SettlementDetailScreen
import ly.payhub.merchant.ui.screen.settlements.SettlementsScreen
import ly.payhub.merchant.ui.screen.submerchants.SubMerchantDetailScreen
import ly.payhub.merchant.ui.screen.submerchants.SubMerchantsScreen
import ly.payhub.merchant.ui.screen.submerchants.SubUsersScreen

/**
 * Single source of truth for the screen graph.
 *
 * Top-level zones:
 *  - `splash`  — shown until [authState] settles out of [AuthState.Bootstrapping].
 *  - the auth flow (`login` → `mfa` ↔ `accept-invite`).
 *  - `home`    — the bottom-nav shell; detail / settlements screens are pushed on top of it.
 *
 * A `LaunchedEffect(authState)` keeps the displayed zone in sync with the
 * repository's auth state — login success, token expiry, sign-out all route
 * here, not from inside a screen.
 *
 * **Deep links** are handled by [parseDeepLink]. Two intents matter:
 *  - `payhub://accept-invite?token=…&m=…&u=…&s=…` — the invite email link.
 *    Sends the user to [Routes.ACCEPT_INVITE] *before* login.
 *  - `payhub://pay-link/{id}` — emitted by push notifications. Routes to the
 *    pay-link detail; if the user is signed out, the link is parked and replayed
 *    after they complete login (the same way the SPA preserves a deep link
 *    through a forced sign-in).
 */
object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val MFA = "mfa/{challengeToken}"
    const val ACCEPT_INVITE = "accept-invite?token={token}&m={m}&u={u}&s={s}"
    const val HOME = "home"
    const val CREATE_PAY_LINK = "pay-links/create"
    const val PAY_LINK_DETAIL = "pay-links/{id}"
    const val PAYMENT_DETAIL = "payments/{id}"
    const val SETTLEMENTS = "settlements"
    const val SETTLEMENT_DETAIL = "settlements/{id}"
    const val CHANGE_PASSWORD = "account/change-password"
    const val MFA_SETTINGS = "account/mfa"
    const val ORG_PROFILE = "org"
    const val SUB_MERCHANTS = "sub-merchants"
    const val SUB_MERCHANT_DETAIL = "sub-merchants/{id}"
    const val SUB_USERS = "sub-merchants/{id}/users"

    fun mfa(challengeToken: String) = "mfa/${Uri.encode(challengeToken)}"
    fun payLinkDetail(id: String) = "pay-links/${Uri.encode(id)}"
    fun paymentDetail(id: String) = "payments/${Uri.encode(id)}"
    fun settlementDetail(id: String) = "settlements/${Uri.encode(id)}"
    fun subMerchantDetail(id: String) = "sub-merchants/${Uri.encode(id)}"
    fun subUsers(id: String) = "sub-merchants/${Uri.encode(id)}/users"
    fun acceptInvite(token: String, m: String?, u: String?, s: String?): String {
        val q = buildList {
            add("token=${Uri.encode(token)}")
            if (!m.isNullOrBlank()) add("m=${Uri.encode(m)}")
            if (!u.isNullOrBlank()) add("u=${Uri.encode(u)}")
            if (!s.isNullOrBlank()) add("s=${Uri.encode(s)}")
        }.joinToString("&")
        return "accept-invite?$q"
    }
}

/** Parsed deep-link the launcher received. */
private sealed interface DeepLink {
    data class Invite(val token: String, val m: String?, val u: String?, val s: String?) : DeepLink
    data class PayLink(val id: String) : DeepLink
}

private fun parseDeepLink(uri: String?): DeepLink? {
    if (uri == null) return null
    val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return null
    if (parsed.scheme != "payhub") return null
    return when (parsed.host) {
        "accept-invite" -> {
            val token = parsed.getQueryParameter("token")?.takeIf { it.isNotBlank() } ?: return null
            DeepLink.Invite(
                token = token,
                m = parsed.getQueryParameter("m"),
                u = parsed.getQueryParameter("u"),
                s = parsed.getQueryParameter("s"),
            )
        }
        "pay-link" -> {
            // `payhub://pay-link/{id}` — the path's first segment is the id.
            val id = parsed.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
            DeepLink.PayLink(id)
        }
        else -> null
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    authState: AuthState,
    initialDeepLink: String?,
) {
    // Parse once — the launcher Intent never changes mid-composition.
    val deepLink = remember(initialDeepLink) { parseDeepLink(initialDeepLink) }

    // A PayLink deep-link that arrived while signed out is held here so it can
    // be replayed after the auth flow finishes. Wiped after one use to prevent
    // re-routing on every recomposition.
    var pendingPayLinkAfterAuth by rememberSaveable {
        mutableStateOf((deepLink as? DeepLink.PayLink)?.id)
    }

    fun NavHostController.switchTo(route: String) {
        navigate(route) {
            popUpTo(graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    LaunchedEffect(authState) {
        when (authState) {
            AuthState.Bootstrapping -> Unit
            AuthState.Unauthenticated -> {
                val current = navController.currentDestination?.route
                if (current !in AUTH_ROUTES) {
                    val dest = when (deepLink) {
                        is DeepLink.Invite -> Routes.acceptInvite(deepLink.token, deepLink.m, deepLink.u, deepLink.s)
                        else -> Routes.LOGIN
                    }
                    navController.switchTo(dest)
                }
            }
            is AuthState.Authenticated -> {
                val current = navController.currentDestination?.route
                val inHomeZone = current?.startsWith("home") == true ||
                    current?.startsWith("pay-links/") == true ||
                    current?.startsWith("payments/") == true ||
                    current?.startsWith("settlements") == true ||
                    current?.startsWith("account/") == true ||
                    current == "org" ||
                    current?.startsWith("sub-merchants") == true
                if (!inHomeZone) navController.switchTo(Routes.HOME)

                // Replay a parked pay-link deep-link, then forget it.
                val target = pendingPayLinkAfterAuth
                if (target != null) {
                    pendingPayLinkAfterAuth = null
                    navController.navigate(Routes.payLinkDetail(target)) { launchSingleTop = true }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) { SplashScreen() }

        composable(Routes.LOGIN) {
            LoginScreen(
                onRequiresMfa = { challenge -> navController.navigate(Routes.mfa(challenge)) },
                // success is handled by the authState effect above
            )
        }

        composable(
            Routes.MFA,
            arguments = listOf(navArgument("challengeToken") { type = NavType.StringType }),
        ) { backStack ->
            val challenge = backStack.arguments?.getString("challengeToken").orEmpty()
            MfaScreen(challengeToken = challenge, onBack = { navController.popBackStack() })
        }

        composable(
            Routes.ACCEPT_INVITE,
            arguments = listOf(
                navArgument("token") { type = NavType.StringType; defaultValue = "" },
                navArgument("m") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("u") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("s") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStack ->
            AcceptInviteScreen(
                token = backStack.arguments?.getString("token").orEmpty(),
                merchantCode = backStack.arguments?.getString("m"),
                username = backStack.arguments?.getString("u"),
                subCode = backStack.arguments?.getString("s"),
                onDone = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onCreatePayLink = { navController.navigate(Routes.CREATE_PAY_LINK) },
                onOpenPayLink = { id -> navController.navigate(Routes.payLinkDetail(id)) },
                onOpenPayment = { id -> navController.navigate(Routes.paymentDetail(id)) },
                onOpenSettlements = { navController.navigate(Routes.SETTLEMENTS) },
                onOpenChangePassword = { navController.navigate(Routes.CHANGE_PASSWORD) },
                onOpenMfaSettings = { navController.navigate(Routes.MFA_SETTINGS) },
                onOpenOrgProfile = { navController.navigate(Routes.ORG_PROFILE) },
                onOpenSubMerchants = { navController.navigate(Routes.SUB_MERCHANTS) },
            )
        }

        composable(Routes.CREATE_PAY_LINK) {
            CreatePayLinkScreen(
                onClose = { navController.popBackStack() },
                onCreated = { navController.popBackStack() },
            )
        }

        composable(
            Routes.PAY_LINK_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { backStack ->
            PayLinkDetailScreen(
                payLinkId = backStack.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenOther = { id ->
                    navController.navigate(Routes.payLinkDetail(id)) { launchSingleTop = true }
                },
            )
        }

        composable(
            Routes.PAYMENT_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { backStack ->
            PaymentDetailScreen(
                paymentId = backStack.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenPayLink = { id ->
                    navController.navigate(Routes.payLinkDetail(id)) { launchSingleTop = true }
                },
            )
        }

        composable(Routes.SETTLEMENTS) {
            SettlementsScreen(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(Routes.settlementDetail(id)) },
            )
        }

        composable(
            Routes.SETTLEMENT_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { backStack ->
            SettlementDetailScreen(
                fileId = backStack.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenPayment = { id ->
                    navController.navigate(Routes.paymentDetail(id)) { launchSingleTop = true }
                },
            )
        }

        // ---- account / org / sub-merchant management ----

        composable(Routes.CHANGE_PASSWORD) {
            ChangePasswordScreen(
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }

        composable(Routes.MFA_SETTINGS) {
            MfaSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ORG_PROFILE) {
            OrgProfileScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SUB_MERCHANTS) {
            SubMerchantsScreen(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(Routes.subMerchantDetail(id)) },
            )
        }

        composable(
            Routes.SUB_MERCHANT_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { backStack ->
            SubMerchantDetailScreen(
                subId = backStack.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() },
                onOpenUsers = { id -> navController.navigate(Routes.subUsers(id)) },
            )
        }

        composable(
            Routes.SUB_USERS,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { backStack ->
            SubUsersScreen(
                subId = backStack.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private val AUTH_ROUTES = setOf(Routes.LOGIN, Routes.MFA, Routes.ACCEPT_INVITE)
