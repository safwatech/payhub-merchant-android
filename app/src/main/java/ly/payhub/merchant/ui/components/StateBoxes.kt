package ly.payhub.merchant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ly.payhub.merchant.data.AppError

@Composable
fun LoadingBox(modifier: Modifier = Modifier, label: String? = null) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            if (label != null) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ErrorBox(error: AppError, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) =
    ErrorBox(message = error.message, isNetwork = error is AppError.Network, modifier = modifier, onRetry = onRetry)

@Composable
fun ErrorBox(
    message: String,
    modifier: Modifier = Modifier,
    isNetwork: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    CenteredMessage(
        modifier = modifier,
        icon = if (isNetwork) Icons.Rounded.CloudOff else Icons.Rounded.ErrorOutline,
        title = if (isNetwork) "You're offline" else "Something went wrong",
        body = message,
    ) {
        if (onRetry != null) Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
fun EmptyBox(
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    CenteredMessage(modifier = modifier, icon = icon, title = title, body = body) {
        if (actionLabel != null && onAction != null) OutlinedButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun CenteredMessage(
    modifier: Modifier,
    icon: ImageVector?,
    title: String,
    body: String?,
    trailing: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 48.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (!body.isNullOrBlank()) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            trailing()
        }
    }
}
