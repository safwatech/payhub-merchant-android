package ly.payhub.merchant.ui.screen.paylinks

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ly.payhub.merchant.ui.components.EmptyBox
import ly.payhub.merchant.ui.components.ErrorBox
import ly.payhub.merchant.ui.components.LoadingBox
import ly.payhub.merchant.ui.components.PayLinkRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayLinksScreen(
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PayLinksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Refresh when returning to the tab — the detail screen may have mutated links.
    LaunchedEffect(Unit) { viewModel.refresh() }

    // Infinite scroll.
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.items.size - 4 && state.nextCursor != null
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMore() }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (state.canCreate) {
                ExtendedFloatingActionButton(
                    onClick = onCreate,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("New link") },
                )
            }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            // Filter chip row (scrollable — six options won't fit on a phone).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PayLinkFilter.entries.forEach { f ->
                    FilterChip(
                        selected = state.filter == f,
                        onClick = { viewModel.setFilter(f) },
                        label = { Text(f.label) },
                    )
                }
            }

            when {
                state.loading -> LoadingBox(label = "Loading pay-links…")
                state.error != null && state.items.isEmpty() -> ErrorBox(state.error!!, onRetry = viewModel::refresh)
                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.items.isEmpty()) {
                        EmptyBox(
                            title = "No pay-links here",
                            body = when (state.filter) {
                                PayLinkFilter.All -> "Create a link and share it with a customer to get paid."
                                else -> "Nothing matches this filter right now."
                            },
                            actionLabel = if (state.canCreate && state.filter == PayLinkFilter.All) "Create a link" else null,
                            onAction = onCreate.takeIf { state.canCreate },
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 88.dp),
                        ) {
                            items(state.items, key = { it.id }) { link ->
                                PayLinkRow(link = link, onClick = { onOpen(link.id) })
                                HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
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
