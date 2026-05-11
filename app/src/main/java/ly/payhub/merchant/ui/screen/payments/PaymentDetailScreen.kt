package ly.payhub.merchant.ui.screen.payments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import ly.payhub.merchant.R
import ly.payhub.merchant.data.RawMerchantApi
import ly.payhub.merchant.ui.components.ErrorBox
import ly.payhub.merchant.ui.components.LoadingBox
import ly.payhub.merchant.ui.components.MetaBadge
import ly.payhub.merchant.ui.components.StatusPill
import ly.payhub.merchant.ui.theme.MonoStyle
import ly.payhub.merchant.ui.theme.StatusColors
import ly.payhub.merchant.util.Money
import ly.payhub.merchant.util.RelativeTime
import ly.payhub.merchant.util.copyToClipboard
import ly.payhub.merchant.util.humanizeRole
import ly.payhub.merchant.util.pspLabel

/** The dedicated key inside `Payment.metadata` for the customer's MSISDN. */
private const val META_KEY_MSISDN = "customer_msisdn"

/** The dedicated key linking a payment back to the pay-link that minted it. */
private const val META_KEY_PAY_LINK_ID = "pay_link_id"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentDetailScreen(
    paymentId: String,
    onBack: () -> Unit,
    onOpenPayLink: (String) -> Unit,
    viewModel: PaymentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(paymentId) { viewModel.start(paymentId) }

    val copiedToast = stringResource(R.string.common_copied)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pay_detail_title)) },
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
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                state.loading -> LoadingBox(label = stringResource(R.string.loading))
                state.error != null && state.payment == null -> ErrorBox(state.error!!, onRetry = viewModel::refresh)
                state.payment != null -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    DetailBody(
                        payment = state.payment!!,
                        onCopy = { label, text ->
                            val showToast = context.copyToClipboard(label, text)
                            if (showToast) scope.launch { snackbarHostState.showSnackbar(copiedToast) }
                        },
                        onOpenPayLink = onOpenPayLink,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailBody(
    payment: RawMerchantApi.PaymentDetail,
    onCopy: (label: String, text: String) -> Unit,
    onOpenPayLink: (String) -> Unit,
) {
    val customerMsisdn = payment.metadata.stringOrNull(META_KEY_MSISDN)
    val payLinkId = payment.metadata.stringOrNull(META_KEY_PAY_LINK_ID)
    val extraMetadata = payment.metadata.filterKeys { it != META_KEY_MSISDN && it != META_KEY_PAY_LINK_ID }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Headline: status pill + amount.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusPill(payment.status)
            Text(
                Money.format(payment.amountMinor, payment.currency),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        // Summary card.
        val copyLabel = stringResource(R.string.pay_detail_copy_label)
        val copyDesc = stringResource(R.string.action_copy)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(
                    label = stringResource(R.string.pay_detail_field_order_ref),
                    value = payment.merchantOrderRef,
                    mono = true,
                    trailing = {
                        IconButton(onClick = { onCopy(copyLabel, payment.merchantOrderRef) }) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = copyDesc,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetaBadge(pspLabel(payment.pspCode))
                    if (!payment.pspRef.isNullOrBlank()) {
                        Text(
                            payment.pspRef!!,
                            style = MonoStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!customerMsisdn.isNullOrBlank()) {
                    Field(stringResource(R.string.pay_detail_field_customer_mobile), customerMsisdn)
                }
                Field(stringResource(R.string.pay_detail_field_created), RelativeTime.absolute(payment.createdAt))
                if (payment.updatedAt != payment.createdAt) {
                    Field(
                        stringResource(R.string.pay_detail_field_last_update),
                        stringResource(
                            R.string.pay_detail_field_last_update_value,
                            RelativeTime.absolute(payment.updatedAt),
                            RelativeTime.until(payment.updatedAt),
                        ),
                    )
                }
            }
        }

        // View pay-link, if this payment was minted by one.
        if (!payLinkId.isNullOrBlank()) {
            OutlinedButton(
                onClick = { onOpenPayLink(payLinkId) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Link, contentDescription = null)
                Text("  " + stringResource(R.string.pay_detail_view_pay_link))
            }
        }

        // Events timeline.
        Text(stringResource(R.string.pay_detail_events), style = MaterialTheme.typography.titleMedium)
        if (payment.events.isEmpty()) {
            Text(
                stringResource(R.string.pay_detail_no_events),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column {
                payment.events.forEachIndexed { idx, ev ->
                    EventRow(event = ev, isLast = idx == payment.events.lastIndex)
                }
            }
        }

        // Remaining metadata.
        if (extraMetadata.isNotEmpty()) {
            HorizontalDivider()
            Text(stringResource(R.string.pay_detail_metadata), style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                extraMetadata.forEach { (k, v) ->
                    val display = v.displayString()
                    if (display != null) Field(k, display)
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: RawMerchantApi.PaymentEvent, isLast: Boolean) {
    val dotColor = when (event.source.lowercase()) {
        "psp" -> StatusColors.Pending
        "admin" -> StatusColors.Negative
        else -> MaterialTheme.colorScheme.primary
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // Leading dot + thin connector
        Column(
            modifier = Modifier.width(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(48.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Column(
            modifier = Modifier.padding(start = 12.dp, bottom = 12.dp).weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(humanizeRole(event.eventType), fontWeight = FontWeight.SemiBold)
            val transition = if (!event.prevStatus.isNullOrBlank() && !event.newStatus.isNullOrBlank()) {
                "${event.prevStatus} → ${event.newStatus}"
            } else {
                event.newStatus ?: event.prevStatus
            }
            if (!transition.isNullOrBlank()) {
                Text(
                    transition,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaBadge(event.source)
                Text(
                    RelativeTime.until(event.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    mono: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (mono) {
                Text(value, style = MonoStyle)
            } else {
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (trailing != null) trailing()
    }
}

// -- JSON helpers -------------------------------------------------------------

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

/**
 * Compact display string for a metadata value. Returns null for explicit nulls
 * and unwieldy types — composite objects/arrays would clutter the screen and
 * are better surfaced via a dev tool than the merchant app.
 */
private fun JsonElement.displayString(): String? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> contentOrNull?.takeIf { it.isNotBlank() }
    is JsonObject -> null
    is JsonArray -> null
}

