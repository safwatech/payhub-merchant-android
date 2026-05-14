package ly.payhub.merchant.ui.screen.submerchants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import ly.payhub.merchant.util.copyToClipboard
import ly.payhub.merchant.util.humanizeSubRole
import ly.payhub.merchant.util.shareText

private val SUB_ROLES = listOf(
    "sub_owner" to R.string.role_sub_owner,
    "sub_operator" to R.string.role_sub_operator,
    "sub_viewer" to R.string.role_sub_viewer,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubUsersScreen(
    subId: String,
    onBack: () -> Unit,
    viewModel: SubUsersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showInvite by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<SubUser?>(null) }
    var mfaTarget by remember { mutableStateOf<SubUser?>(null) }

    LaunchedEffect(subId) { viewModel.start(subId) }
    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.subusers_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showInvite = true }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.subusers_invite))
            }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize()) {
            when {
                state.loading -> LoadingBox(modifier = Modifier.padding(inner), label = stringResource(R.string.loading))
                state.error != null && state.items.isEmpty() -> ErrorBox(state.error!!, modifier = Modifier.padding(inner), onRetry = viewModel::refresh)
                state.items.isEmpty() -> EmptyBox(
                    title = stringResource(R.string.subusers_empty),
                    actionLabel = stringResource(R.string.subusers_invite),
                    onAction = { showInvite = true },
                    modifier = Modifier.padding(inner),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(inner),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(state.items, key = { it.id }) { user ->
                        SubUserRow(
                            user = user,
                            onEdit = { editTarget = user },
                            onDisable = { viewModel.disable(user.id) },
                            onReissue = { viewModel.reissue(user.id) },
                            onClearMfa = { mfaTarget = user },
                        )
                        HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    if (showInvite) {
        InviteUserDialog(
            busy = state.busy,
            onDismiss = { showInvite = false },
            onSubmit = { u, n, e, m, p, role ->
                viewModel.invite(u, n, e, m, p, role) { success, _ -> if (success) showInvite = false }
            },
        )
    }
    editTarget?.let { target ->
        EditUserDialog(
            user = target,
            busy = state.busy,
            onDismiss = { editTarget = null },
            onSubmit = { role, status ->
                viewModel.update(target.id, role, status) { ok -> if (ok) editTarget = null }
            },
        )
    }
    mfaTarget?.let { target ->
        ClearMfaDialog(
            username = target.username,
            busy = state.busy,
            onDismiss = { mfaTarget = null },
            onSubmit = { code -> viewModel.clearMfa(target.id, code) { ok -> if (ok) mfaTarget = null } },
        )
    }
    state.invite?.let { invite ->
        InviteLinkDialog(invite = invite, onDismiss = viewModel::consumeInvite)
    }
}

@Composable
private fun SubUserRow(
    user: SubUser,
    onEdit: () -> Unit,
    onDisable: () -> Unit,
    onReissue: () -> Unit,
    onClearMfa: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = { StatusPill(user.status) },
        headlineContent = { Text(user.username, style = MonoStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (user.fullName.isNotBlank()) Text(user.fullName, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetaBadge(humanizeSubRole(user.role))
                    if (user.mfaEnabled) MetaBadge(stringResource(R.string.action_2fa), prominent = true)
                }
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.subusers_edit)) }, onClick = { menuOpen = false; onEdit() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.subusers_reissue)) }, onClick = { menuOpen = false; onReissue() })
                    if (user.mfaEnabled) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.subusers_clear_mfa)) }, onClick = { menuOpen = false; onClearMfa() })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.subusers_disable)) }, onClick = { menuOpen = false; onDisable() })
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleDropdown(value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = SUB_ROLES.firstOrNull { it.first == value }?.let { stringResource(it.second) } ?: humanizeSubRole(value)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.subusers_role)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SUB_ROLES.forEach { (code, res) ->
                DropdownMenuItem(text = { Text(stringResource(res)) }, onClick = { onChange(code); expanded = false })
            }
        }
    }
}

@Composable
private fun InviteUserDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (username: String, fullName: String, email: String, mobile: String, phone: String, role: String) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("sub_operator") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subusers_invite)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text(stringResource(R.string.subusers_username)) }, singleLine = true, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text(stringResource(R.string.subusers_full_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.subusers_email)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text(stringResource(R.string.subusers_mobile)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(stringResource(R.string.subusers_phone)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                RoleDropdown(value = role, onChange = { role = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(username, fullName, email, mobile, phone, role) },
                enabled = !busy && username.length >= 3 && fullName.isNotBlank(),
            ) { Text(stringResource(R.string.subusers_invite)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun EditUserDialog(
    user: SubUser,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (role: String?, status: String?) -> Unit,
) {
    var role by remember { mutableStateOf(user.role) }
    var active by remember { mutableStateOf(user.status == "active") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subusers_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RoleDropdown(value = role, onChange = { role = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.subs_status_active), modifier = Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newRole = role.takeIf { it != user.role }
                    val newStatus = if (active == (user.status == "active")) null else if (active) "active" else "disabled"
                    onSubmit(newRole, newStatus)
                },
                enabled = !busy,
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ClearMfaDialog(
    username: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (code: String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subusers_clear_mfa)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(username, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(8) },
                    label = { Text(stringResource(R.string.subusers_clear_mfa_code)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(code) }, enabled = !busy && code.length >= 6) {
                Text(stringResource(R.string.subusers_clear_mfa))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun InviteLinkDialog(invite: InviteResult, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // No snackbar host here; copy feedback on API <33 is left to the OS toast / no-op.
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subusers_invite_link_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (invite.sentToChannel != null) {
                        stringResource(R.string.subusers_invite_sent, invite.sentToChannel)
                    } else {
                        stringResource(R.string.subusers_invite_not_sent)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                SelectionContainer {
                    Text(invite.url, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            val label = stringResource(R.string.pld_pay_link_label)
            TextButton(onClick = {
                context.copyToClipboard(label, invite.url)
                onDismiss()
            }) { Text(stringResource(R.string.subusers_invite_copy)) }
        },
        dismissButton = {
            val chooser = stringResource(R.string.action_share)
            TextButton(onClick = {
                context.shareText(invite.url, chooser)
                onDismiss()
            }) { Text(stringResource(R.string.action_share)) }
        },
    )
}
