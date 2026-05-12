package ly.payhub.merchant.ui.screen.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ly.payhub.merchant.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val successMsg = stringResource(R.string.changepw_success)

    LaunchedEffect(state.done) {
        if (state.done) {
            snackbarHostState.showSnackbar(successMsg)
            delay(400)
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.changepw_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
            OutlinedTextField(
                value = state.current,
                onValueChange = viewModel::onCurrent,
                label = { Text(stringResource(R.string.changepw_current)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                isError = state.error != null,
            )
            OutlinedTextField(
                value = state.newPassword,
                onValueChange = viewModel::onNew,
                label = { Text(stringResource(R.string.changepw_new)) },
                supportingText = { Text(stringResource(R.string.changepw_too_short)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                isError = state.errorRes == R.string.changepw_too_short,
            )
            OutlinedTextField(
                value = state.confirm,
                onValueChange = viewModel::onConfirm,
                label = { Text(stringResource(R.string.changepw_confirm)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (state.codeRequired) ImeAction.Next else ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
                isError = state.errorRes == R.string.changepw_mismatch,
            )
            if (state.codeRequired) {
                OutlinedTextField(
                    value = state.code,
                    onValueChange = viewModel::onCode,
                    label = { Text(stringResource(R.string.changepw_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val errMsg = state.errorRes?.let { stringResource(it) } ?: state.error
            if (errMsg != null) {
                Text(errMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = viewModel::submit,
                enabled = !state.submitting && state.current.isNotBlank() && state.newPassword.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.changepw_submit))
            }
        }
    }
}
