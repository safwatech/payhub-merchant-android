package ly.payhub.merchant.ui.screen.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ly.payhub.merchant.data.AppError
import ly.payhub.merchant.data.MerchantRepository
import ly.payhub.merchant.data.RawMerchantApi
import ly.payhub.merchant.data.appError
import javax.inject.Inject

/**
 * Filter chips at the top of the payments list. Each chip maps to a
 * `PaymentStatus` enum value the server understands (`pending` / `succeeded` /
 * `failed` / `cancelled` / `refunded`). `requires_action` is folded under
 * "Pending" since on mobile the distinction is noise — the shopkeeper just
 * wants to know whether they got paid.
 */
enum class PaymentStatusFilter(val labelRes: Int, val wire: String?) {
    All(ly.payhub.merchant.R.string.pay_filter_all, null),
    Pending(ly.payhub.merchant.R.string.pay_filter_pending, "pending"),
    RequiresAction(ly.payhub.merchant.R.string.pay_filter_requires_action, "requires_action"),
    Succeeded(ly.payhub.merchant.R.string.pay_filter_succeeded, "succeeded"),
    Failed(ly.payhub.merchant.R.string.pay_filter_failed, "failed"),
    Cancelled(ly.payhub.merchant.R.string.pay_filter_cancelled, "cancelled"),
    Refunded(ly.payhub.merchant.R.string.pay_filter_refunded, "refunded"),
}

data class PaymentsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: AppError? = null,
    val filter: PaymentStatusFilter = PaymentStatusFilter.All,
    val items: List<RawMerchantApi.PaymentRow> = emptyList(),
    val hasMore: Boolean = false,
)

@HiltViewModel
class PaymentsViewModel @Inject constructor(
    private val repo: MerchantRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PaymentsUiState())
    val state: StateFlow<PaymentsUiState> = _state.asStateFlow()

    init { load(reset = true) }

    fun setFilter(filter: PaymentStatusFilter) {
        if (_state.value.filter == filter) return
        _state.update { it.copy(filter = filter, items = emptyList(), hasMore = false) }
        load(reset = true)
    }

    fun refresh() = load(reset = true)

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || !s.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val r = repo.listPayments(status = s.filter.wire, limit = PAGE, offset = s.items.size)
            r.fold(
                onSuccess = { page ->
                    _state.update {
                        it.copy(
                            loadingMore = false,
                            items = it.items + page,
                            // Pagination is offset-based: a short page ⇒ end of list.
                            hasMore = page.size >= PAGE,
                        )
                    }
                },
                onFailure = { _state.update { it.copy(loadingMore = false) } },
            )
        }
    }

    private fun load(reset: Boolean) {
        _state.update {
            it.copy(
                loading = reset && it.items.isEmpty(),
                refreshing = reset && it.items.isNotEmpty(),
                error = null,
            )
        }
        val s = _state.value
        viewModelScope.launch {
            val result = repo.listPayments(status = s.filter.wire, limit = PAGE, offset = 0)
            result.fold(
                onSuccess = { page ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            items = page,
                            hasMore = page.size >= PAGE,
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(loading = false, refreshing = false, error = result.appError())
                    }
                },
            )
        }
    }

    private companion object {
        const val PAGE = 50
    }
}
