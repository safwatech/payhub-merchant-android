package ly.payhub.merchant.ui.screen.payments

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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ly.payhub.merchant.R
import ly.payhub.*
import ly.payhub.merchant.ui.components.EmptyBox
import ly.payhub.merchant.ui.components.ErrorBox
import ly.payhub.merchant.ui.components.LoadingBox
import ly.payhub.merchant.ui.components.MetaBadge
import ly.payhub.merchant.ui.components.StatusPill
import ly.payhub.merchant.ui.theme.MonoStyle
import ly.payhub.merchant.util.Money
import ly.payhub.merchant.util.RelativeTime
import ly.payhub.merchant.util.pspLabel

/**
 * The third bottom-nav tab: every payment the signed-in user can see (their
 * own sub's if they're sub-scoped, the whole merchant if they're a parent).
 * Pure read; mutations live admin-side. Tapping a row pushes detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaymentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Returning to the tab (e.g. from the detail screen) refreshes — a status
    // can have flipped via webhook between glances.
    LaunchedEffect(Unit) { viewModel.refresh() }

    // Infinite scroll: load the next page when the user is 4 rows from the end.
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.items.size - 4 && state.hasMore
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMore() }

    Scaffold(modifier = modifier) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PaymentStatusFilter.entries.forEach { f ->
                    FilterChip(
                        selected = state.filter == f,
                        onClick = { viewModel.setFilter(f) },
                        label = { Text(stringResource(f.labelRes)) },
                    )
                }
            }

            when {
                state.loading -> LoadingBox(label = stringResource(R.string.pay_loading))
                state.error != null && state.items.isEmpty() -> ErrorBox(state.error!!, onRetry = viewModel::refresh)
                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.items.isEmpty()) {
                        EmptyBox(
                            title = stringResource(R.string.pay_empty_title),
                            body = when (state.filter) {
                                PaymentStatusFilter.All -> stringResource(R.string.pay_empty_all_body)
                                else -> stringResource(R.string.pay_empty_filter_body)
                            },
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            items(state.items, key = { it.id }) { row ->
                                PaymentRowItem(row = row, onClick = { onOpen(row.id) })
                                HorizontalDivider(
                                    Modifier.padding(start = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                            if (state.loadingMore) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(Modifier.padding(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentRowItem(row: PaymentRow, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = { StatusPill(row.status) },
        headlineContent = {
            Text(row.merchantOrderRef, style = MonoStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    Money.format(row.amountMinor, row.currency),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MetaBadge(pspLabel(row.pspCode))
                    Text(
                        RelativeTime.until(row.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
