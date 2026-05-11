package ly.payhub.merchant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ly.payhub.merchant.R
import ly.payhub.merchant.ui.theme.StatusColors
import ly.payhub.merchant.util.humanizeRole
import java.util.Locale

/**
 * Mappings between server status codes and pill colours / display strings.
 * Display labels for the common "paid" / "expired" cases come from the string
 * catalogue so they translate; everything else falls back to a humanised
 * version of the wire code (`requires_action` → "Requires action").
 */
private data class PillStyle(val bg: Color, val fg: Color)

@Composable
private fun pillFor(status: String): Pair<PillStyle, String> {
    val s = status.lowercase(Locale.ROOT)
    return when (s) {
        "succeeded", "paid", "captured" ->
            PillStyle(StatusColors.PositiveContainer, StatusColors.PositiveOnContainer) to
                stringResource(R.string.status_paid)
        "active", "pending", "processing", "requires_action", "inflight", "in_flight" ->
            PillStyle(StatusColors.PendingContainer, StatusColors.PendingOnContainer) to humanizeRole(s)
        "refunded", "partially_refunded" ->
            PillStyle(StatusColors.PositiveContainer, StatusColors.PositiveOnContainer) to humanizeRole(s)
        "expired" ->
            PillStyle(StatusColors.NeutralContainer, StatusColors.NeutralOnContainer) to
                stringResource(R.string.status_expired)
        "cancelled", "canceled", "failed", "declined", "voided" ->
            PillStyle(StatusColors.NegativeContainer, StatusColors.NegativeOnContainer) to humanizeRole(s)
        else ->
            PillStyle(StatusColors.NeutralContainer, StatusColors.NeutralOnContainer) to
                humanizeRole(s.ifBlank { stringResource(R.string.common_dash) })
    }
}

@Composable
fun StatusPill(status: String, modifier: Modifier = Modifier) {
    val (style, label) = pillFor(status)
    Text(
        text = label,
        color = style.fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(style.bg)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** A neutral outlined chip used for role / shop / count badges. */
@Composable
fun MetaBadge(text: String, modifier: Modifier = Modifier, prominent: Boolean = false) {
    val bg = if (prominent) StatusColors.PendingContainer else StatusColors.NeutralContainer
    val fg = if (prominent) StatusColors.PendingOnContainer else StatusColors.NeutralOnContainer
    Text(
        text = text,
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
