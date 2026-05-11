package ly.payhub.merchant.ui.screen.paylinks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ly.payhub.merchant.R
import ly.payhub.merchant.ui.theme.MonoStyle
import ly.payhub.merchant.util.Money
import ly.payhub.merchant.util.copyToClipboard
import ly.payhub.merchant.util.shareText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePayLinkScreen(
    onClose: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreatePayLinkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.created == null) {
                            stringResource(R.string.plc_title)
                        } else {
                            stringResource(R.string.plc_success_title)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (state.created != null) onCreated() else onClose() }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val created = state.created
            if (created == null) {
                FormBody(state, viewModel)
            } else {
                // Success panel: the link + share/copy actions.
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Text(stringResource(R.string.plc_success_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    "${Money.format(created.amountMinor, created.currency)} · ${created.merchantOrderRef}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Text(
                        created.url,
                        style = MonoStyle,
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    )
                }
                val shareChooser = stringResource(R.string.share_paylink_chooser)
                val payLinkLabel = stringResource(R.string.pld_pay_link_label)
                val copiedToast = stringResource(R.string.pld_link_copied)
                Button(
                    onClick = {
                        context.shareText(created.url, shareChooser)
                        viewModel.markShared()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null)
                    Text("  " + stringResource(R.string.action_share))
                }
                OutlinedButton(
                    onClick = {
                        val showToast = context.copyToClipboard(payLinkLabel, created.url)
                        viewModel.markShared()
                        if (showToast) scope.launch { snackbarHostState.showSnackbar(copiedToast) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Text("  " + stringResource(R.string.action_copy_link))
                }
                OutlinedButton(onClick = onCreated, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_done))
                }
            }
        }
    }
}

@Composable
private fun FormBody(state: CreatePayLinkUiState, viewModel: CreatePayLinkViewModel) {
    val hasError = state.errorRes != null || state.error != null
    OutlinedTextField(
        value = state.amount,
        onValueChange = viewModel::onAmount,
        label = { Text(stringResource(R.string.plc_amount_lyd)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
        isError = hasError,
    )

    OutlinedTextField(
        value = state.description,
        onValueChange = viewModel::onDescription,
        label = { Text(stringResource(R.string.plc_description)) },
        placeholder = { Text(stringResource(R.string.plc_description_placeholder)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = state.customerPhone,
        onValueChange = viewModel::onCustomerPhone,
        label = { Text(stringResource(R.string.plc_customer_phone)) },
        supportingText = { Text(stringResource(R.string.plc_customer_phone_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )

    Text(stringResource(R.string.plc_methods), style = MaterialTheme.typography.titleSmall)
    Text(
        stringResource(R.string.plc_methods_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        // Two columns of chips — wrap manually since FlowRow isn't in this BOM by default everywhere.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            viewModel.knownPsps.filterIndexed { i, _ -> i % 2 == 0 }.forEach { (code, label) ->
                PspChip(label, code in state.selectedPsps) { viewModel.togglePsp(code) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            viewModel.knownPsps.filterIndexed { i, _ -> i % 2 == 1 }.forEach { (code, label) ->
                PspChip(label, code in state.selectedPsps) { viewModel.togglePsp(code) }
            }
        }
    }

    OutlinedTextField(
        value = state.expiryDays,
        onValueChange = viewModel::onExpiryDays,
        label = { Text(stringResource(R.string.plc_expires_days)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = state.orderRef,
        onValueChange = viewModel::onOrderRef,
        label = { Text(stringResource(R.string.plc_order_ref)) },
        textStyle = MonoStyle,
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = viewModel::regenerateRef) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.plc_order_ref_regenerate))
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )

    val errMsg = state.errorRes?.let { stringResource(it) } ?: state.error
    if (errMsg != null) {
        Text(errMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }

    Button(
        onClick = viewModel::submit,
        enabled = !state.submitting,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Text(stringResource(R.string.plc_submit))
    }
}

@Composable
private fun PspChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
    )
}
