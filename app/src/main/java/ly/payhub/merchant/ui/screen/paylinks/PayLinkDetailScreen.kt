package ly.payhub.merchant.ui.screen.paylinks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ly.payhub.merchant.R
import ly.payhub.merchant.ui.components.ErrorBox
import ly.payhub.merchant.ui.components.LoadingBox
import ly.payhub.merchant.ui.components.MetaBadge
import ly.payhub.merchant.ui.components.StatusPill
import ly.payhub.merchant.ui.theme.MonoStyle
import ly.payhub.merchant.util.Money
import ly.payhub.merchant.util.RelativeTime
import ly.payhub.merchant.util.copyToClipboard
import ly.payhub.merchant.util.pspLabel
import ly.payhub.merchant.util.shareText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayLinkDetailScreen(
    payLinkId: String,
    onBack: () -> Unit,
    onOpenOther: (String) -> Unit,
    viewModel: PayLinkDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showCancelDialog by remember { mutableStateOf(false) }
    var showExtendSheet by remember { mutableStateOf(false) }

    LaunchedEffect(payLinkId) { viewModel.start(payLinkId) }

    val shareChooser = stringResource(R.string.share_paylink_chooser)
    val openLabel = stringResource(R.string.action_open)
    val payLinkLabel = stringResource(R.string.pld_pay_link_label)
    val linkCopiedToast = stringResource(R.string.pld_link_copied)
    val clonedFmt = stringResource(R.string.pld_cloned_to)
    LaunchedEffect(Unit) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is DetailEffect.Toast -> snackbarHostState.showSnackbar(effect.message)
                is DetailEffect.Share -> {
                    context.shareText(effect.url, shareChooser)
                    viewModel.recordShared()
                }
                is DetailEffect.ClonedTo -> {
                    val res = snackbarHostState.showSnackbar(
                        message = clonedFmt.format(effect.newRef),
                        actionLabel = openLabel,
                    )
                    if (res == SnackbarResult.ActionPerformed) onOpenOther(effect.newLinkId)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pld_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (state.actionInProgress) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                state.loading -> LoadingBox(label = stringResource(R.string.loading))
                state.error != null && state.link == null -> ErrorBox(state.error!!, onRetry = viewModel::reload)
                state.link != null -> DetailContent(
                    state = state,
                    onShare = viewModel::shareLink,
                    onCopy = {
                        val showToast = context.copyToClipboard(payLinkLabel, state.link!!.url)
                        if (showToast) scope.launch { snackbarHostState.showSnackbar(linkCopiedToast) }
                    },
                    onExtend = { showExtendSheet = true },
                    onClone = viewModel::clone,
                    onCancel = { showCancelDialog = true },
                )
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(stringResource(R.string.pld_cancel_dialog_title)) },
            text = { Text(stringResource(R.string.pld_cancel_dialog_body)) },
            confirmButton = {
                TextButton(onClick = { showCancelDialog = false; viewModel.cancel() }) {
                    Text(stringResource(R.string.pld_cancel_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(R.string.pld_cancel_dialog_keep))
                }
            },
        )
    }

    if (showExtendSheet) {
        val sheetState = rememberModalBottomSheetState()
        ExtendSheet(
            sheetState = sheetState,
            onDismiss = { showExtendSheet = false },
            onExtend = { days ->
                showExtendSheet = false
                viewModel.extend(days)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtendSheet(
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    onExtend: (days: Int) -> Unit,
) {
    var customDays by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.pld_extend_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.pld_extend_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { onExtend(1) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pld_extend_1d))
            }
            OutlinedButton(onClick = { onExtend(7) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pld_extend_1w))
            }
            HorizontalDivider()
            OutlinedTextField(
                value = customDays,
                onValueChange = { customDays = it.filter(Char::isDigit).take(3) },
                label = { Text(stringResource(R.string.pld_extend_custom_label)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { customDays.toIntOrNull()?.let { if (it > 0) onExtend(it) } },
                enabled = (customDays.toIntOrNull() ?: 0) > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.pld_extend_submit)) }
        }
    }
}

@Composable
private fun DetailContent(
    state: PayLinkDetailUiState,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onExtend: () -> Unit,
    onClone: () -> Unit,
    onCancel: () -> Unit,
) {
    val link = state.link ?: return
    val active = link.status.equals("active", ignoreCase = true)
    val canAct = state.canWrite && !state.actionInProgress

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusPill(link.status)
            Text(Money.format(link.amountMinor, link.currency), style = MaterialTheme.typography.headlineSmall)
        }

        val dash = stringResource(R.string.common_dash)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(stringResource(R.string.pld_field_order_ref), link.merchantOrderRef, mono = true)
                if (!link.description.isNullOrBlank()) {
                    Field(stringResource(R.string.pld_field_description), link.description!!)
                }
                Field(stringResource(R.string.pld_field_link_url), link.url, mono = true)
                Field(
                    stringResource(R.string.pld_field_expires),
                    if (link.expiresAt == null) {
                        dash
                    } else {
                        "${RelativeTime.absolute(link.expiresAt)}  (${RelativeTime.until(link.expiresAt)})"
                    },
                )
                Field(
                    stringResource(R.string.pld_field_customer_attempts),
                    stringResource(R.string.pld_field_customer_attempts_value, link.attempts),
                )
                if (link.extendCount > 0) {
                    Field(
                        stringResource(R.string.pld_field_extended),
                        stringResource(
                            R.string.pld_field_extended_value,
                            link.extendCount,
                            RelativeTime.until(link.lastExtendedAt),
                        ),
                    )
                }
                if (link.resharedCount > 0) {
                    Field(
                        stringResource(R.string.pld_field_reshared),
                        stringResource(
                            R.string.pld_field_reshared_value,
                            link.resharedCount,
                            RelativeTime.until(link.lastSharedAt),
                        ),
                    )
                }
                if (link.cloneGeneration > 0) {
                    Field(stringResource(R.string.pld_field_clone_gen), "${link.cloneGeneration}")
                }
                if (!link.allowedPsps.isNullOrEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        link.allowedPsps!!.forEach { MetaBadge(pspLabel(it)) }
                    }
                }
                if (link.createdAt != null) {
                    Field(stringResource(R.string.pld_field_created), RelativeTime.absolute(link.createdAt))
                }
            }
        }

        if (!state.canWrite) {
            Text(
                stringResource(R.string.pld_read_only_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Actions.
        Button(onClick = onShare, enabled = canAct, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Share, contentDescription = null)
            Text("  " + stringResource(R.string.pld_action_reshare))
        }
        OutlinedButton(onClick = onCopy, enabled = !state.actionInProgress, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null)
            Text("  " + stringResource(R.string.pld_action_copy))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onExtend, enabled = canAct && active, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.MoreTime, contentDescription = null)
                Text(" " + stringResource(R.string.pld_action_extend))
            }
            OutlinedButton(onClick = onClone, enabled = canAct, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                Text(" " + stringResource(R.string.pld_action_clone))
            }
        }
        if (active) {
            OutlinedButton(
                onClick = onCancel,
                enabled = canAct,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("  " + stringResource(R.string.pld_action_cancel_link), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, mono: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (mono) {
            Text(value, style = MonoStyle)
        } else {
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
