package ly.payhub.merchant.ui.screen.submerchants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ly.payhub.MerchantApiKey
import ly.payhub.merchant.R
import ly.payhub.merchant.ui.components.EmptyBox
import ly.payhub.merchant.ui.components.ErrorBox
import ly.payhub.merchant.ui.components.LoadingBox
import ly.payhub.merchant.ui.components.MetaBadge
import ly.payhub.merchant.ui.components.StatusPill
import ly.payhub.merchant.ui.theme.MonoStyle
import ly.payhub.merchant.util.RelativeTime
import ly.payhub.merchant.util.copyToClipboard

/**
 * Sub-merchant API-key management. List + generate + revoke.
 *
 * Plaintext secrets are shown **exactly once** in the
 * [RevealSecretDialog] — pressed dismissed via "I've saved it", the secret
 * is cleared from the ViewModel state. The masked row stays in the list
 * (server stores only an argon2 hash).
 *
 * In-app key management is deliberately **sub-scoped only**; parent-merchant
 * API-key management remains web-portal-only — see `CLAUDE.md`'s native-apps
 * paragraph for the rationale.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubMerchantApiKeysScreen(
    subId: String,
    onBack: () -> Unit,
    viewModel: SubMerchantApiKeysViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.api_keys_title)) },
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
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.api_keys_generate))
            }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize()) {
            when {
                state.loading -> LoadingBox(modifier = Modifier.padding(inner), label = stringResource(R.string.loading))
                state.error != null && state.items.isEmpty() ->
                    ErrorBox(state.error!!, modifier = Modifier.padding(inner), onRetry = viewModel::refresh)
                state.items.isEmpty() -> EmptyBox(
                    title = stringResource(R.string.api_keys_empty),
                    actionLabel = stringResource(R.string.api_keys_generate),
                    onAction = { showCreate = true },
                    modifier = Modifier.padding(inner),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(inner),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(state.items, key = { it.id }) { key ->
                        ApiKeyRow(
                            key = key,
                            onRevoke = { viewModel.revoke(key.id) },
                        )
                        HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateApiKeyDialog(
            busy = state.busy,
            onDismiss = { showCreate = false },
            onSubmit = { scopes ->
                viewModel.createKey(scopes) { ok -> if (ok) showCreate = false }
            },
        )
    }

    val revealed = state.revealedSecret
    if (revealed != null) {
        RevealSecretDialog(secret = revealed, onDismiss = viewModel::dismissReveal)
    }
}

@Composable
private fun ApiKeyRow(
    key: MerchantApiKey,
    onRevoke: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = { StatusPill(key.status) },
        headlineContent = {
            Text(key.keyId, style = MonoStyle, maxLines = 1)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (key.scopes.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        key.scopes.take(3).forEach { MetaBadge(it) }
                        if (key.scopes.size > 3) MetaBadge("+${key.scopes.size - 3}")
                    }
                }
                Text(
                    stringResource(R.string.api_keys_created_fmt, RelativeTime.absolute(key.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            // Only an active key has a menu — a revoked row is read-only.
            if (key.status.equals("active", ignoreCase = true)) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.api_keys_revoke)) },
                            onClick = { menuOpen = false; onRevoke() },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun CreateApiKeyDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (scopes: List<String>) -> Unit,
) {
    // The sub-scoped key needs at least one scope on the wire — the server
    // rejects an empty list. Default to "payments:write" which covers the
    // pay-link / payment lifecycle a typical backend wires.
    var scopesText by remember { mutableStateOf("payments:write") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.api_keys_new_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.api_keys_new_blurb), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = scopesText,
                    onValueChange = { scopesText = it },
                    label = { Text(stringResource(R.string.api_keys_scopes)) },
                    supportingText = { Text(stringResource(R.string.api_keys_scopes_hint)) },
                    singleLine = true,
                    textStyle = MonoStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            val scopes = scopesText.split(",", " ").map(String::trim).filter(String::isNotBlank)
            TextButton(
                onClick = { onSubmit(scopes) },
                enabled = !busy && scopes.isNotEmpty(),
            ) { Text(stringResource(R.string.api_keys_generate)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun RevealSecretDialog(secret: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboardLabel = stringResource(R.string.api_keys_clipboard_label)
    AlertDialog(
        // No automatic dismiss — the user must explicitly confirm so we know
        // they've copied the value somewhere.
        onDismissRequest = { },
        title = { Text(stringResource(R.string.api_keys_secret_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.api_keys_secret_blurb),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                SelectionContainer {
                    Text(secret, style = MonoStyle, color = MaterialTheme.colorScheme.onSurface)
                }
                Button(
                    onClick = { context.copyToClipboard(clipboardLabel, secret) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.api_keys_copy_secret)) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.api_keys_saved_it)) }
        },
    )
}
