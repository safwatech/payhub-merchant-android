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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
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

    LaunchedEffect(Unit) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is DetailEffect.Toast -> snackbarHostState.showSnackbar(effect.message)
                is DetailEffect.Share -> {
                    context.shareText(effect.url, "Share payment link")
                    viewModel.recordShared()
                }
                is DetailEffect.ClonedTo -> {
                    val res = snackbarHostState.showSnackbar(
                        message = "Cloned to ${effect.newRef}",
                        actionLabel = "Open",
                    )
                    if (res == SnackbarResult.ActionPerformed) onOpenOther(effect.newLinkId)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pay-link") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (state.actionInProgress) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                state.loading -> LoadingBox(label = "Loading…")
                state.error != null && state.link == null -> ErrorBox(state.error!!, onRetry = viewModel::reload)
                state.link != null -> DetailContent(
                    state = state,
                    onShare = viewModel::shareLink,
                    onCopy = {
                        val showToast = context.copyToClipboard("PayHub pay-link", state.link!!.url)
                        if (showToast) scope.launch { snackbarHostState.showSnackbar("Link copied") }
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
            title = { Text("Cancel this link?") },
            text = { Text("Customers won't be able to pay with it anymore. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { showCancelDialog = false; viewModel.cancel() }) { Text("Cancel link") }
            },
            dismissButton = { TextButton(onClick = { showCancelDialog = false }) { Text("Keep it") } },
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
            Text("Extend expiry", style = MaterialTheme.typography.titleLarge)
            Text(
                "Push the link's expiry further out (capped by your install's maximum link lifetime).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { onExtend(1) }, modifier = Modifier.fillMaxWidth()) { Text("+ 1 day") }
            OutlinedButton(onClick = { onExtend(7) }, modifier = Modifier.fillMaxWidth()) { Text("+ 1 week") }
            HorizontalDivider()
            OutlinedTextField(
                value = customDays,
                onValueChange = { customDays = it.filter(Char::isDigit).take(3) },
                label = { Text("Custom — number of days") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { customDays.toIntOrNull()?.let { if (it > 0) onExtend(it) } },
                enabled = (customDays.toIntOrNull() ?: 0) > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Extend") }
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

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Order reference", link.merchantOrderRef, mono = true)
                if (!link.description.isNullOrBlank()) Field("Description", link.description!!)
                Field("Link URL", link.url, mono = true)
                Field(
                    "Expires",
                    if (link.expiresAt == null) "—"
                    else "${RelativeTime.absolute(link.expiresAt)}  (${RelativeTime.until(link.expiresAt)})",
                )
                Field("Customer attempts", "${link.attempts} / 5")
                if (link.extendCount > 0) Field("Extended", "${link.extendCount}× (last ${RelativeTime.until(link.lastExtendedAt)})")
                if (link.resharedCount > 0) Field("Re-shared", "${link.resharedCount}× (last ${RelativeTime.until(link.lastSharedAt)})")
                if (link.cloneGeneration > 0) Field("Clone generation", "${link.cloneGeneration}")
                if (!link.allowedPsps.isNullOrEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        link.allowedPsps!!.forEach { MetaBadge(pspLabel(it)) }
                    }
                }
                if (link.createdAt != null) Field("Created", RelativeTime.absolute(link.createdAt))
            }
        }

        if (!state.canWrite) {
            Text(
                "Your role can view links but not change them. Ask a shop owner to act on this one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Actions.
        Button(onClick = onShare, enabled = canAct, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Share, contentDescription = null); Text("  Re-share")
        }
        OutlinedButton(onClick = onCopy, enabled = !state.actionInProgress, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null); Text("  Copy link")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onExtend, enabled = canAct && active, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.MoreTime, contentDescription = null); Text(" Extend")
            }
            OutlinedButton(onClick = onClone, enabled = canAct, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null); Text(" Clone")
            }
        }
        if (active) {
            OutlinedButton(
                onClick = onCancel,
                enabled = canAct,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("  Cancel link", color = MaterialTheme.colorScheme.error)
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
