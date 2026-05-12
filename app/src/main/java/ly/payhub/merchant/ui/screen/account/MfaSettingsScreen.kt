package ly.payhub.merchant.ui.screen.account

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.launch
import ly.payhub.merchant.R
import ly.payhub.merchant.data.localizedMessage
import ly.payhub.merchant.ui.theme.MonoStyle
import ly.payhub.merchant.util.copyToClipboard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MfaSettingsScreen(
    onBack: () -> Unit,
    viewModel: MfaSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mfa_title_settings)) },
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
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }

            val enrol = state.enrol
            when {
                state.mfaEnabled -> {
                    Text(stringResource(R.string.mfa_status_on), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.mfa_disable),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = state.disablePassword,
                        onValueChange = viewModel::onDisablePassword,
                        label = { Text(stringResource(R.string.mfa_disable_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                        isError = state.error != null,
                    )
                    ErrorLine(state)
                    Button(
                        onClick = viewModel::disable,
                        enabled = !state.busy && state.disablePassword.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.mfa_disable))
                    }
                }

                enrol == null -> {
                    Text(stringResource(R.string.mfa_status_off), style = MaterialTheme.typography.titleMedium)
                    ErrorLine(state)
                    Button(
                        onClick = viewModel::startEnrol,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.mfa_enable))
                    }
                }

                else -> {
                    Text(
                        stringResource(R.string.mfa_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    QrImage(enrol.otpauthUri)
                    Text(stringResource(R.string.mfa_secret_label), style = MaterialTheme.typography.labelLarge)
                    SelectionContainer {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Text(enrol.secret, style = MonoStyle, modifier = Modifier.padding(14.dp).fillMaxWidth())
                        }
                    }
                    val copyLabel = stringResource(R.string.mfa_secret_label)
                    val copiedToast = stringResource(R.string.common_copied)
                    OutlinedButton(
                        onClick = {
                            val showToast = context.copyToClipboard(copyLabel, enrol.secret)
                            if (showToast) scope.launch { snackbarHostState.showSnackbar(copiedToast) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.common_copy)) }
                    OutlinedTextField(
                        value = state.code,
                        onValueChange = viewModel::onCode,
                        label = { Text(stringResource(R.string.mfa_code)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 6.sp, textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                        isError = state.error != null,
                    )
                    ErrorLine(state)
                    Button(
                        onClick = viewModel::confirmEnrol,
                        enabled = !state.busy && state.code.length >= 6,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.mfa_confirm))
                    }
                    OutlinedButton(onClick = viewModel::cancelEnrol, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorLine(state: MfaSettingsUiState) {
    val err = state.error ?: return
    Text(err.localizedMessage(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun QrImage(content: String, size: Int = 220) {
    val bitmap = remember(content) { qrBitmap(content) } ?: return
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size.dp),
        )
    }
}

/** Render an `otpauth://` URI to a black/white QR [Bitmap]. Null on encode failure. */
private fun qrBitmap(content: String, px: Int = 512): Bitmap? = runCatching {
    val matrix: BitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, px, px)
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val offset = y * w
        for (x in 0 until w) {
            pixels[offset + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(pixels, 0, w, 0, 0, w, h) }
}.getOrNull()
