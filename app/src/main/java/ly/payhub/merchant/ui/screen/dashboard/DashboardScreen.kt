package ly.payhub.merchant.ui.screen.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ly.payhub.merchant.R
import ly.payhub.*
import ly.payhub.merchant.ui.components.BrandHeader
import ly.payhub.merchant.ui.components.CounterCard
import ly.payhub.merchant.ui.components.ErrorBox
import ly.payhub.merchant.ui.components.LoadingBox
import ly.payhub.merchant.ui.components.MetaBadge
import ly.payhub.merchant.ui.theme.StatusColors
import ly.payhub.merchant.util.Money
import ly.payhub.merchant.util.humanizeRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        // Header: merchant/shop name + role badge.
        val me = state.me
        if (me != null) {
            val title = me.subMerchant?.name ?: me.merchant.name
            val shopSuffix = me.subMerchant?.let { stringResource(R.string.shop_subtitle_suffix, it.code) } ?: ""
            val subtitle = me.merchant.code + shopSuffix
            BrandHeader(
                title = title,
                subtitle = subtitle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                trailing = { MetaBadge(humanizeRole(me.effectiveRole), prominent = true) },
            )
        }

        // Window selector chips.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DashWindow.entries.forEach { w ->
                FilterChip(
                    selected = state.window == w,
                    onClick = { viewModel.setWindow(w) },
                    label = { Text(stringResource(w.labelRes)) },
                )
            }
        }

        when {
            state.loading && state.data == null -> LoadingBox(label = stringResource(R.string.dash_loading))
            state.error != null && state.data == null -> ErrorBox(state.error!!, onRetry = viewModel::refresh)
            else -> PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                DashboardBody(state)
            }
        }
    }
}

@Composable
private fun DashboardBody(state: DashboardUiState) {
    // The dashboard endpoint reports volume in the install's settlement currency;
    // for the Libyan market that's LYD. (No per-currency split is exposed.)
    val currency = "LYD"
    val windowLabel = stringResource(state.window.labelRes)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.dash_last_window, windowLabel),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        // 2x2 counter grid.
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                CounterCard(
                    label = stringResource(R.string.dash_paid),
                    value = state.paidCount.toString(),
                    subline = Money.format(state.paidVolumeMinor, currency),
                    icon = Icons.Rounded.CheckCircle,
                    accent = StatusColors.Positive,
                    accentContainer = StatusColors.PositiveContainer,
                    modifier = Modifier.weight(1f),
                )
                CounterCard(
                    label = stringResource(R.string.dash_inflight),
                    value = state.inflight.toString(),
                    icon = Icons.Rounded.AccessTime,
                    accent = StatusColors.Pending,
                    accentContainer = StatusColors.PendingContainer,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                CounterCard(
                    label = stringResource(R.string.dash_active_links),
                    value = state.activePayLinks.toString(),
                    icon = Icons.Rounded.Link,
                    accent = MaterialTheme.colorScheme.primary,
                    accentContainer = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1f),
                )
                CounterCard(
                    label = stringResource(R.string.dash_needs_followup),
                    value = state.needsFollowup.toString(),
                    icon = Icons.Rounded.NotificationsActive,
                    accent = StatusColors.Negative,
                    accentContainer = StatusColors.NegativeContainer,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Parent users: per-shop breakdown.
        if (state.isParent) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.dash_by_shop), style = MaterialTheme.typography.titleMedium)
                }
            }
            val rows = state.subBreakdown
            when {
                rows != null && rows.isNotEmpty() -> items(rows) { row -> SubBreakdownCard(row, currency) }
                rows != null && rows.isEmpty() -> item {
                    Text(
                        stringResource(R.string.dash_no_shop_activity),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> item {
                    Text(
                        stringResource(R.string.dash_breakdown_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubBreakdownCard(row: SubBreakdownRow, currency: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    row.name.takeIf { it.isNotBlank() } ?: row.code.takeIf { it.isNotBlank() } ?: stringResource(R.string.status_shop),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (row.code.isNotBlank()) MetaBadge(row.code)
            }
            // SDK 1.2's `SubBreakdownRow` carries paid_count + paid_volume_minor;
            // inflight / active-links / needs-follow-up live on the parent
            // `MerchantDashboard` total, not the per-sub row.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat(stringResource(R.string.dash_stat_paid), "${row.paidCount}")
                Stat(stringResource(R.string.dash_stat_volume), Money.format(row.paidVolumeMinor, currency))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
