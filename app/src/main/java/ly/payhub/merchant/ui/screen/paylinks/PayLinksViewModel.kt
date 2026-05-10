package ly.payhub.merchant.ui.screen.paylinks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ly.payhub.PayLink
import ly.payhub.merchant.data.AppError
import ly.payhub.merchant.data.MerchantRepository
import ly.payhub.merchant.data.appError
import ly.payhub.merchant.util.isWriteRole
import javax.inject.Inject

/** The filter chips at the top of the pay-links list. */
enum class PayLinkFilter(val label: String) {
    All("All"),
    NeedsFollowup("Needs follow-up"),
    Active("Active"),
    Paid("Paid"),
    Expired("Expired"),
    Cancelled("Cancelled"),
    ;

    /** Maps to `PayhubMerchantClient.payLinks.list(...)` arguments. */
    fun statusArg(): String? = when (this) {
        Active -> "active"
        Paid -> "paid"
        Expired -> "expired"
        Cancelled -> "cancelled"
        else -> null
    }

    fun bucketArg(): String? = if (this == NeedsFollowup) "needs_followup" else null
}

data class PayLinksUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: AppError? = null,
    val filter: PayLinkFilter = PayLinkFilter.All,
    val items: List<PayLink> = emptyList(),
    val nextCursor: String? = null,
    val loadingMore: Boolean = false,
    /** True for write roles (owner/developer/sub_owner/sub_operator). The FAB is hidden otherwise. */
    val canCreate: Boolean = false,
)

@HiltViewModel
class PayLinksViewModel @Inject constructor(
    private val repo: MerchantRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        PayLinksUiState(canCreate = isWriteRole(repo.currentMe?.effectiveRole ?: "")),
    )
    val state: StateFlow<PayLinksUiState> = _state.asStateFlow()

    init { load(reset = true) }

    fun setFilter(filter: PayLinkFilter) {
        if (_state.value.filter == filter) return
        _state.update { it.copy(filter = filter, items = emptyList(), nextCursor = null) }
        load(reset = true)
    }

    fun refresh() = load(reset = true)

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.nextCursor == null) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val r = repo.listPayLinks(status = s.filter.statusArg(), bucket = s.filter.bucketArg(), limit = PAGE_SIZE, cursor = s.nextCursor)
            r.fold(
                onSuccess = { list ->
                    _state.update { it.copy(items = it.items + list.items, nextCursor = list.nextCursor, loadingMore = false) }
                },
                onFailure = { _state.update { it.copy(loadingMore = false) } },
            )
        }
    }

    /** Called by the detail screen via a side channel when a link was changed — refreshes the list. */
    fun invalidate() = load(reset = true)

    private fun load(reset: Boolean) {
        val s = _state.value
        _state.update { it.copy(loading = reset && it.items.isEmpty(), refreshing = reset && it.items.isNotEmpty(), error = null) }
        // Keep canCreate fresh.
        _state.update { it.copy(canCreate = isWriteRole(repo.currentMe?.effectiveRole ?: "")) }
        viewModelScope.launch {
            val result = repo.listPayLinks(status = s.filter.statusArg(), bucket = s.filter.bucketArg(), limit = PAGE_SIZE)
            result.fold(
                onSuccess = { list ->
                    _state.update { it.copy(loading = false, refreshing = false, items = list.items, nextCursor = list.nextCursor) }
                },
                onFailure = {
                    _state.update { it.copy(loading = false, refreshing = false, error = result.appError()) }
                },
            )
        }
    }

    private companion object {
        const val PAGE_SIZE = 25
    }
}
