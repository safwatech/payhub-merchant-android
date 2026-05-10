package ly.payhub.merchant.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ly.payhub.merchant.R

/**
 * The PayHub mark + a title/subtitle — used on the auth screens and as a
 * dashboard header. The mipmap foreground PNG doubles as the logo here.
 */
@Composable
fun BrandHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    markSize: Int = 40,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = "PayHub",
            modifier = Modifier.size(markSize.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
        }
    }
}

/** A bigger, centred lockup for the login / accept-invite screens. */
@Composable
fun BrandLockup(modifier: Modifier = Modifier, tagline: String? = null) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = "PayHub",
            modifier = Modifier.size(72.dp),
        )
        Text("PayHub Merchant", style = MaterialTheme.typography.headlineSmall)
        if (!tagline.isNullOrBlank()) {
            Text(
                tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 0.dp),
            )
        }
    }
}
