package ly.payhub.merchant.ui.screen.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ly.payhub.merchant.R

/**
 * The "Forgot password?" mini-flow — merchant code + username (+ optional shop
 * code). Reused from both the Login screen and the More screen. The server
 * responds the same whether or not the account exists (enumeration-safe), so the
 * caller just shows a generic "check your email" toast.
 */
@Composable
fun ForgotPasswordDialog(
    initialMerchantCode: String = "",
    initialUsername: String = "",
    initialSubCode: String = "",
    onDismiss: () -> Unit,
    onSubmit: (merchantCode: String, username: String, subCode: String?) -> Unit,
) {
    var merchantCode by remember { mutableStateOf(initialMerchantCode) }
    var username by remember { mutableStateOf(initialUsername) }
    var subCode by remember { mutableStateOf(initialSubCode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.forgot_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.forgot_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = merchantCode,
                    onValueChange = { merchantCode = it },
                    label = { Text(stringResource(R.string.login_merchant_code)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.login_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = subCode,
                    onValueChange = { subCode = it },
                    label = { Text(stringResource(R.string.login_shop_code)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(merchantCode.trim(), username.trim(), subCode.trim().ifBlank { null }) },
                enabled = merchantCode.isNotBlank() && username.isNotBlank(),
            ) { Text(stringResource(R.string.forgot_submit)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
