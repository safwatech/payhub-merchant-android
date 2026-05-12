package ly.payhub.merchant.ui.screen.more

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ly.payhub.merchant.BuildConfig
import ly.payhub.merchant.R
import ly.payhub.merchant.ui.components.BrandHeader
import ly.payhub.merchant.ui.components.MetaBadge
import ly.payhub.merchant.ui.screen.auth.ForgotPasswordDialog
import ly.payhub.merchant.util.humanizeRole
import ly.payhub.merchant.util.isParentOwner

@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    onOpenSettlements: () -> Unit = {},
    onOpenChangePassword: () -> Unit = {},
    onOpenMfaSettings: () -> Unit = {},
    onOpenOrgProfile: () -> Unit = {},
    onOpenSubMerchants: () -> Unit = {},
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showForgot by rememberSaveable { mutableStateOf(false) }
    // Set when the user toggles push ON and we still need permission; the launcher result resolves it.
    var pendingPushEnable by remember { mutableStateOf(false) }

    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (pendingPushEnable) {
            viewModel.setPushEnabled(enabled = true, permissionGranted = granted)
            pendingPushEnable = false
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { scope.launch { snackbarHostState.showSnackbar(it) }; viewModel.consumeMessage() }
    }

    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbarHostState) }) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val me = state.me
            BrandHeader(
                title = stringResource(R.string.more_title),
                subtitle = null,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            // Profile card.
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (me == null) {
                        Text(stringResource(R.string.more_loading_profile), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(me.fullName.ifBlank { me.username }, style = MaterialTheme.typography.titleMedium)
                        ProfileRow(stringResource(R.string.more_username), me.username)
                        ProfileRow(
                            stringResource(R.string.more_merchant),
                            stringResource(R.string.merchant_with_code, me.merchant.name, me.merchant.code),
                        )
                        me.subMerchant?.let {
                            ProfileRow(
                                stringResource(R.string.more_shop),
                                stringResource(R.string.merchant_with_code, it.name, it.code),
                            )
                        }
                        if (!me.email.isNullOrBlank()) ProfileRow(stringResource(R.string.more_email), me.email!!)
                        if (!me.mobile.isNullOrBlank()) ProfileRow(stringResource(R.string.more_mobile), me.mobile!!)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MetaBadge(humanizeRole(me.role))
                            if (me.effectiveRole != me.role) {
                                MetaBadge(stringResource(R.string.more_acting_as, humanizeRole(me.effectiveRole)), prominent = true)
                            }
                            if (me.mfaEnabled) MetaBadge(stringResource(R.string.more_2fa_on))
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        val aggregatorLabel = stringResource(
                            if (me.entitlements.aggregator) R.string.more_aggregator_on else R.string.more_aggregator_off,
                        )
                        val quotaValue = if (me.entitlements.payLinkQuota > 0) {
                            me.entitlements.payLinkQuota.toString()
                        } else {
                            stringResource(R.string.common_dash)
                        }
                        val quotaLabel = stringResource(R.string.more_pay_link_quota, quotaValue)
                        val routingLabel = if (me.entitlements.smartRouting) {
                            stringResource(R.string.more_dot) + stringResource(R.string.more_smart_routing_on)
                        } else {
                            ""
                        }
                        Text(
                            "$aggregatorLabel" + stringResource(R.string.more_dot) + "$quotaLabel$routingLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Push toggle.
            fun onPushToggle(wantOn: Boolean) {
                if (!wantOn) {
                    viewModel.setPushEnabled(enabled = false, permissionGranted = true)
                    return
                }
                // Turning ON: ensure POST_NOTIFICATIONS on API 33+.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        viewModel.setPushEnabled(enabled = true, permissionGranted = true)
                    } else {
                        pendingPushEnable = true
                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    viewModel.setPushEnabled(enabled = true, permissionGranted = true)
                }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_push_title)) },
                supportingContent = { Text(stringResource(R.string.more_push_hint)) },
                leadingContent = { Icon(Icons.Rounded.Notifications, contentDescription = null) },
                trailingContent = {
                    if (state.pushBusy) {
                        CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                    } else {
                        Switch(checked = state.pushEnabled, onCheckedChange = { onPushToggle(it) })
                    }
                },
            )

            // Settlements (read-only). Tucked here rather than the bottom nav —
            // reconciliation reports are checked far less often than pay-links/payments.
            ListItem(
                modifier = Modifier.clickableRow(onClick = onOpenSettlements),
                headlineContent = { Text(stringResource(R.string.more_settlements)) },
                supportingContent = { Text(stringResource(R.string.more_settlements_hint)) },
                leadingContent = { Icon(Icons.Rounded.FactCheck, contentDescription = null) },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )

            // ---- Security ----
            SectionHeader(stringResource(R.string.more_security_section))
            ListItem(
                modifier = Modifier.clickableRow(onClick = onOpenChangePassword),
                headlineContent = { Text(stringResource(R.string.more_change_pw)) },
                supportingContent = { Text(stringResource(R.string.more_change_pw_hint)) },
                leadingContent = { Icon(Icons.Rounded.Password, contentDescription = null) },
                trailingContent = { ChevronEnd() },
            )
            ListItem(
                modifier = Modifier.clickableRow(onClick = onOpenMfaSettings),
                headlineContent = { Text(stringResource(R.string.more_2fa)) },
                supportingContent = {
                    Text(stringResource(if (me?.mfaEnabled == true) R.string.more_2fa_on_hint else R.string.more_2fa_off_hint))
                },
                leadingContent = { Icon(Icons.Rounded.Security, contentDescription = null) },
                trailingContent = { ChevronEnd() },
            )
            // "Reset password" (the forgot-password mini-flow) — for when you've forgotten it.
            ListItem(
                modifier = Modifier.clickableRow { showForgot = true },
                headlineContent = { Text(stringResource(R.string.more_reset_pw)) },
                supportingContent = { Text(stringResource(R.string.more_reset_pw_hint)) },
                leadingContent = { Icon(Icons.Rounded.LockReset, contentDescription = null) },
            )

            // ---- Business (parent users only) ----
            if (me != null && me.subMerchant == null) {
                SectionHeader(stringResource(R.string.more_business_section))
                ListItem(
                    modifier = Modifier.clickableRow(onClick = onOpenOrgProfile),
                    headlineContent = { Text(stringResource(R.string.more_org_profile)) },
                    supportingContent = { Text(stringResource(R.string.more_org_profile_hint)) },
                    leadingContent = { Icon(Icons.Rounded.Business, contentDescription = null) },
                    trailingContent = { ChevronEnd() },
                )
                if (me.isParentOwner() && me.entitlements.aggregator) {
                    ListItem(
                        modifier = Modifier.clickableRow(onClick = onOpenSubMerchants),
                        headlineContent = { Text(stringResource(R.string.more_sub_merchants)) },
                        supportingContent = { Text(stringResource(R.string.more_sub_merchants_hint)) },
                        leadingContent = { Icon(Icons.Rounded.Storefront, contentDescription = null) },
                        trailingContent = { ChevronEnd() },
                    )
                }
                // TODO(payhub): SUB_OWNER cashier self-management screen
            }

            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            // Sign out.
            ListItem(
                modifier = Modifier.clickableRow(enabled = !state.signingOut) { viewModel.signOut() },
                headlineContent = {
                    Text(
                        stringResource(R.string.action_sign_out),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                leadingContent = {
                    if (state.signingOut) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
            )

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.more_version, BuildConfig.VERSION_NAME, state.serverUrl),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
    }

    if (showForgot) {
        val me = state.me
        ForgotPasswordDialog(
            initialMerchantCode = me?.merchant?.code.orEmpty(),
            initialUsername = me?.username.orEmpty(),
            initialSubCode = me?.subMerchant?.code.orEmpty(),
            onDismiss = { showForgot = false },
            onSubmit = { m, u, s -> viewModel.forgotPassword(m, u, s); showForgot = false },
        )
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
    )
}

@Composable
private fun ChevronEnd() {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun Modifier.clickableRow(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.clickable(enabled = enabled, onClick = onClick)
