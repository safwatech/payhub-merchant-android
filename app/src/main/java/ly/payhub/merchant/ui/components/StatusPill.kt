package ly.payhub.merchant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ly.payhub.merchant.ui.theme.StatusColors
import ly.payhub.merchant.util.humanizeRole
import java.util.Locale

private data class PillStyle(val bg: Color, val fg: Color, val label: String)

private fun styleFor(status: String): PillStyle {
    val s = status.lowercase(Locale.ROOT)
    return when (s) {
        "succeeded", "paid", "captured" ->
            PillStyle(StatusColors.PositiveContainer, StatusColors.PositiveOnContainer, "Paid")
        "active", "pending", "processing", "requires_action", "inflight", "in_flight" ->
            PillStyle(StatusColors.PendingContainer, StatusColors.PendingOnContainer, humanizeRole(s))
        "refunded", "partially_refunded" ->
            PillStyle(StatusColors.PositiveContainer, StatusColors.PositiveOnContainer, humanizeRole(s))
        "expired" ->
            PillStyle(StatusColors.NeutralContainer, StatusColors.NeutralOnContainer, "Expired")
        "cancelled", "canceled", "failed", "declined", "voided" ->
            PillStyle(StatusColors.NegativeContainer, StatusColors.NegativeOnContainer, humanizeRole(s))
        else ->
            PillStyle(StatusColors.NeutralContainer, StatusColors.NeutralOnContainer, humanizeRole(s.ifBlank { "—" }))
    }
}

@Composable
fun StatusPill(status: String, modifier: Modifier = Modifier) {
    val st = styleFor(status)
    Text(
        text = st.label,
        color = st.fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(st.bg)
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
