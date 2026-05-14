package ly.payhub.merchant.ui.screen.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import ly.payhub.merchant.BuildConfig
import ly.payhub.merchant.R
import ly.payhub.merchant.data.TokenStore
import javax.inject.Inject

/**
 * The "send anonymous crash reports" opt-in. Off by default; hidden entirely
 * in builds that don't carry a [BuildConfig.SENTRY_DSN] (vendor-managed). The
 * toggle's value drives [ly.payhub.merchant.ui.util.CrashReportingController]
 * through [TokenStore.crashReportingFlow], so flipping it here starts /
 * stops Sentry without a restart.
 */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val tokenStore: TokenStore,
) : ViewModel() {
    val enabled: StateFlow<Boolean> = tokenStore.crashReportingFlow
    fun setEnabled(value: Boolean) {
        tokenStore.crashReportingEnabled = value
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
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
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (BuildConfig.SENTRY_DSN.isBlank()) {
                Text(
                    stringResource(R.string.diagnostics_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = enabled, onCheckedChange = { viewModel.setEnabled(it) })
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.diagnostics_toggle), style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                stringResource(R.string.diagnostics_blurb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
