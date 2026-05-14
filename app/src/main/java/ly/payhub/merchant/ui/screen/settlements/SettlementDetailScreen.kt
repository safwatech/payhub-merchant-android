package ly.payhub.merchant.ui.screen.settlements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import ly.payhub.merchant.R
import ly.payhub.*
import ly.payhub.merchant.ui.components.ErrorBox
import ly.payhub.merchant.ui.components.LoadingBox
import ly.payhub.merchant.ui.components.MetaBadge
import ly.payhub.merchant.ui.components.StatusPill
import ly.payhub.merchant.ui.theme.MonoStyle
import ly.payhub.merchant.util.Money
import ly.payhub.merchant.util.RelativeTime
import ly.payhub.merchant.util.pspLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementDetailScreen(
    fileId: String,
    onBack: () -> Unit,
    onOpenPayment: (String) -> Unit,
    viewModel: SettlementDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(fileId) { viewModel.start(fileId) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.rows.size - 4 && state.hasMore
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMore() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sett_detail_title)) },
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
        Column(Modifier.fillMaxSize().padding(inner)) {
            when {
                state.loadingRows && state.file == null -> LoadingBox(label = stringResource(R.string.loading))
                state.error != null && state.rows.isEmpty() && state.file == null ->
                    ErrorBox(state.error!!, onRetry = viewModel::refresh)
                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        item { HeaderCard(state.file) }
                        item { FilterRow(state.filter, viewModel::setFilter) }

                        if (state.rows.isEmpty() && !state.loadingRows) {
                            item {
                                Text(
                                    stringResource(R.string.sett_no_rows),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                )
                            }
                        }

                        items(state.rows, key = { it.id }) { row ->
                            SettlementRowItem(
                                row = row,
                                onOpenPayment = onOpenPayment,
                            )
                            HorizontalDivider(
                                Modifier.padding(start = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        if (state.loadingMore) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) { CircularProgressIndicator(Modifier.padding(8.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(file: SettlementFile?) {
    if (file == null) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(file.filename, style = MonoStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val dash = stringResource(R.string.common_dash)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MetaBadge(pspLabel(file.pspCode))
                Text(
                    stringResource(R.string.sett_uploaded_ago, RelativeTime.until(file.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (file.periodFrom != null || file.periodTo != null) {
                Text(
                    stringResource(R.string.sett_period, file.periodFrom ?: dash, file.periodTo ?: dash),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Counter(stringResource(R.string.sett_counter_total), file.rowCount)
                Counter(stringResource(R.string.sett_counter_matched), file.matchedCount)
                Counter(stringResource(R.string.sett_counter_mismatch), file.mismatchCount, prominent = file.mismatchCount > 0)
                Counter(stringResource(R.string.sett_counter_missing_hub), file.missingInHubCount, prominent = file.missingInHubCount > 0)
                Counter(stringResource(R.string.sett_counter_missing_psp), file.missingInPspCount, prominent = file.missingInPspCount > 0)
            }
        }
    }
}

@Composable
private fun Counter(label: String, value: Int, prominent: Boolean = false) {
    Column {
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (prominent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterRow(current: SettlementRowFilter, onPick: (SettlementRowFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettlementRowFilter.entries.forEach { f ->
            FilterChip(
                selected = current == f,
                onClick = { onPick(f) },
                label = { Text(stringResource(f.labelRes)) },
            )
        }
    }
}

@Composable
private fun SettlementRowItem(
    row: SettlementRow,
    onOpenPayment: (String) -> Unit,
) {
    val payClickable = row.paymentId != null
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (payClickable) it.clickable { onOpenPayment(row.paymentId!!) } else it },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = { StatusPill(row.status) },
        headlineContent = {
            Text(
                row.merchantOrderRef ?: row.pspRef ?: "—",
                style = MonoStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val amount = row.amountMinor
                val currency = row.currency
                if (amount != null && currency != null) {
                    Text(Money.format(amount, currency), style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!row.pspStatus.isNullOrBlank()) {
                        Text(
                            stringResource(R.string.sett_psp_status, row.pspStatus!!),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!row.pspRef.isNullOrBlank() && row.merchantOrderRef != null) {
                        Text(
                            row.pspRef!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // diff is only meaningful for mismatches — render the key→[hub,psp] pairs inline.
                if (row.diff.isNotEmpty()) {
                    DiffTable(row.diff)
                }
            }
        },
        trailingContent = {
            if (payClickable) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/**
 * `SettlementRow.diff` carries `{ field -> { "hub": …, "psp": … } }` pairs for
 * mismatched fields. Render compactly so the row stays a row, not a card.
 */
@Composable
private fun DiffTable(diff: JsonObject) {
    val dash = stringResource(R.string.common_dash)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
        diff.entries.take(MAX_DIFF_FIELDS).forEach { (field, payload) ->
            val hub = (payload as? JsonObject)?.scalar("hub") ?: dash
            val psp = (payload as? JsonObject)?.scalar("psp") ?: dash
            Text(
                stringResource(R.string.sett_diff_row, field, hub, psp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (diff.size > MAX_DIFF_FIELDS) {
            Text(
                stringResource(R.string.sett_diff_more, diff.size - MAX_DIFF_FIELDS),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val MAX_DIFF_FIELDS = 3

private fun JsonObject.scalar(key: String): String? = when (val v = this[key]) {
    null, is JsonNull -> null
    is JsonPrimitive -> v.contentOrNull
    is JsonObject, is JsonArray -> null
}
