package ly.payhub.merchant.ui.screen.submerchants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ly.payhub.merchant.R
import ly.payhub.merchant.ui.components.ErrorBox
import ly.payhub.merchant.ui.components.LoadingBox
import ly.payhub.merchant.ui.components.StatusPill
import ly.payhub.merchant.ui.theme.MonoStyle
import ly.payhub.merchant.util.RelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubMerchantDetailScreen(
    subId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onOpenUsers: (String) -> Unit,
    viewModel: SubMerchantDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(subId) { viewModel.start(subId) }
    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sub_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        when {
            state.loading && state.sub == null -> LoadingBox(modifier = Modifier.padding(inner), label = stringResource(R.string.loading))
            state.error != null && state.sub == null -> ErrorBox(state.error!!, modifier = Modifier.padding(inner), onRetry = viewModel::load)
            state.sub != null -> {
                val sm = state.sub!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(sm.status)
                                Text(sm.name, style = MaterialTheme.typography.titleMedium)
                            }
                            DetailRow(stringResource(R.string.subs_code), sm.code, mono = true)
                            DetailRow(stringResource(R.string.subs_code_prefix), sm.codePrefix, mono = true)
                            DetailRow(stringResource(R.string.subs_payments_count, sm.paymentsCount), "")
                            if (!sm.externalRef.isNullOrBlank()) DetailRow("external_ref", sm.externalRef!!)
                            DetailRow(stringResource(R.string.pld_field_created), RelativeTime.absolute(sm.createdAt))
                        }
                    }

                    // Edit
                    Text(stringResource(R.string.sub_detail_edit), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(
                        value = state.editName,
                        onValueChange = viewModel::onName,
                        label = { Text(stringResource(R.string.subs_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.subs_status_active), modifier = Modifier.weight(1f))
                        Switch(checked = state.editActive, onCheckedChange = viewModel::onActive)
                    }
                    Button(onClick = viewModel::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                        if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.common_save))
                    }

                    HorizontalDivider()

                    // Manage users
                    ListItem(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenUsers(sm.id) },
                        leadingContent = { Icon(Icons.Rounded.Group, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.sub_detail_users)) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                    )

                    // TODO(payhub): sub-merchant API keys

                    HorizontalDivider()

                    // Delete — only when zero payments.
                    if (sm.paymentsCount == 0) {
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            enabled = !state.deleting,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.deleting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text(stringResource(R.string.sub_detail_delete))
                        }
                    } else {
                        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.sub_detail_delete))
                        }
                        Text(
                            stringResource(R.string.sub_detail_delete_blocked, sm.paymentsCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    state.error?.let { err ->
                        Text(err.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.sub_detail_delete)) },
            text = { Text(stringResource(R.string.sub_detail_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text(stringResource(R.string.sub_detail_delete))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (value.isNotBlank()) {
            Text(
                value,
                style = if (mono) MonoStyle else MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}
