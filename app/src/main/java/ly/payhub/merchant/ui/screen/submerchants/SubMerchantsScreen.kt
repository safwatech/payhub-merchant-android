package ly.payhub.merchant.ui.screen.submerchants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ly.payhub.merchant.R
import ly.payhub.*
import ly.payhub.merchant.ui.components.EmptyBox
import ly.payhub.merchant.ui.components.ErrorBox
import ly.payhub.merchant.ui.components.LoadingBox
import ly.payhub.merchant.ui.components.MetaBadge
import ly.payhub.merchant.ui.components.StatusPill
import ly.payhub.merchant.ui.theme.MonoStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubMerchantsScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: SubMerchantsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreate by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.subs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.subs_add))
            }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize()) {
            when {
                state.loading -> LoadingBox(modifier = Modifier.padding(inner), label = stringResource(R.string.loading))
                state.error != null && state.items.isEmpty() -> ErrorBox(state.error!!, modifier = Modifier.padding(inner), onRetry = viewModel::refresh)
                state.items.isEmpty() -> EmptyBox(
                    title = stringResource(R.string.subs_empty),
                    actionLabel = stringResource(R.string.subs_add),
                    onAction = { showCreate = true },
                    modifier = Modifier.padding(inner),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(inner),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(state.items, key = { it.id }) { sm ->
                        SubMerchantRow(sm) { onOpen(sm.id) }
                        HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateSubMerchantDialog(
            creating = state.creating,
            errorMessage = state.createError,
            onClearError = viewModel::clearCreateError,
            onDismiss = { showCreate = false; viewModel.clearCreateError() },
            onSubmit = { code, prefix, name, active ->
                viewModel.create(code, prefix, name, active) { showCreate = false }
            },
        )
    }
}

@Composable
private fun SubMerchantRow(sm: SubMerchant, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = { StatusPill(sm.status) },
        headlineContent = { Text(sm.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${sm.code}", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MetaBadge(sm.codePrefix)
                Text(
                    stringResource(R.string.subs_payments_count, sm.paymentsCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun CreateSubMerchantDialog(
    creating: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (code: String, prefix: String, name: String, active: Boolean) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subs_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.lowercase(); onClearError() },
                    label = { Text(stringResource(R.string.subs_code)) },
                    supportingText = { Text(stringResource(R.string.subs_code_hint)) },
                    singleLine = true,
                    textStyle = MonoStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it.uppercase().take(6); onClearError() },
                    label = { Text(stringResource(R.string.subs_code_prefix)) },
                    supportingText = { Text(stringResource(R.string.subs_prefix_hint)) },
                    singleLine = true,
                    textStyle = MonoStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; onClearError() },
                    label = { Text(stringResource(R.string.subs_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.subs_status_active), modifier = Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
                if (errorMessage != null) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(code, prefix, name, active) },
                enabled = !creating && code.isNotBlank() && prefix.length in 2..6 && name.isNotBlank(),
            ) { Text(stringResource(R.string.subs_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
