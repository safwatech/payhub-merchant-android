package ly.payhub.merchant.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import ly.payhub.merchant.data.MerchantRepository.AuthState
import ly.payhub.merchant.ui.screen.HomeScreen
import ly.payhub.merchant.ui.screen.SplashScreen
import ly.payhub.merchant.ui.screen.auth.AcceptInviteScreen
import ly.payhub.merchant.ui.screen.auth.LoginScreen
import ly.payhub.merchant.ui.screen.auth.MfaScreen
import ly.payhub.merchant.ui.screen.paylinks.CreatePayLinkScreen
import ly.payhub.merchant.ui.screen.paylinks.PayLinkDetailScreen

/**
 * Single source of truth for the screen graph.
 *
 * Three top-level zones:
 *  - `splash`  — shown until [authState] settles out of [AuthState.Bootstrapping].
 *  - the auth flow (`login` → `mfa` ↔ `accept-invite`).
 *  - `home`    — the bottom-nav shell; pay-link create/detail are pushed on top of it.
 *
 * A `LaunchedEffect(authState)` keeps the displayed zone in sync with the
 * repository's auth state — login success, token expiry, sign-out all route
 * here, not from inside a screen.
 */
object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val MFA = "mfa/{challengeToken}"
    const val ACCEPT_INVITE = "accept-invite?token={token}&m={m}&u={u}&s={s}"
    const val HOME = "home"
    const val CREATE_PAY_LINK = "pay-links/create"
    const val PAY_LINK_DETAIL = "pay-links/{id}"

    fun mfa(challengeToken: String) = "mfa/${Uri.encode(challengeToken)}"
    fun payLinkDetail(id: String) = "pay-links/${Uri.encode(id)}"
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

private data class InviteParams(val token: String, val m: String?, val u: String?, val s: String?)

private fun parseInviteDeepLink(uri: String?): InviteParams? {
    if (uri == null) return null
    val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return null
    if (parsed.scheme != "payhub" || parsed.host != "accept-invite") return null
    val token = parsed.getQueryParameter("token")?.takeIf { it.isNotBlank() } ?: return null
    return InviteParams(
        token = token,
        m = parsed.getQueryParameter("m"),
        u = parsed.getQueryParameter("u"),
        s = parsed.getQueryParameter("s"),
    )
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    authState: AuthState,
    initialDeepLink: String?,
) {
    val invite = parseInviteDeepLink(initialDeepLink)

    // Reactively route between the auth flow and the home shell whenever the
    // repository's auth state changes (and once the splash resolves). Each
    // transition clears the whole back stack so you can't "back" out of the
    // signed-in shell into the login screen (or vice-versa).
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
                    val dest = if (invite != null) {
                        Routes.acceptInvite(invite.token, invite.m, invite.u, invite.s)
                    } else {
                        Routes.LOGIN
                    }
                    navController.switchTo(dest)
                }
            }
            is AuthState.Authenticated -> {
                val current = navController.currentDestination?.route
                val inHomeZone = current?.startsWith("home") == true || current?.startsWith("pay-links/") == true
                if (!inHomeZone) navController.switchTo(Routes.HOME)
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
    }
}

private val AUTH_ROUTES = setOf(Routes.LOGIN, Routes.MFA, Routes.ACCEPT_INVITE)
