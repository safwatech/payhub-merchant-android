package ly.payhub.merchant.ui.screen.settlements

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

data class SettlementsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: AppError? = null,
    val items: List<RawMerchantApi.SettlementFile> = emptyList(),
    val hasMore: Boolean = false,
)

@HiltViewModel
class SettlementsViewModel @Inject constructor(
    private val repo: MerchantRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettlementsUiState())
    val state: StateFlow<SettlementsUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = true)

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || !s.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val r = repo.listSettlements(limit = PAGE, offset = s.items.size)
            r.fold(
                onSuccess = { page ->
                    _state.update {
                        it.copy(
                            loadingMore = false,
                            items = it.items + page,
                            hasMore = page.size >= PAGE,
                        )
                    }
                },
                onFailure = { _state.update { it.copy(loadingMore = false) } },
            )
        }
    }

    private fun load(initial: Boolean) {
        _state.update {
            it.copy(
                loading = initial && it.items.isEmpty(),
                refreshing = initial && it.items.isNotEmpty(),
                error = null,
            )
        }
        viewModelScope.launch {
            val r = repo.listSettlements(limit = PAGE, offset = 0)
            r.fold(
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
                        it.copy(loading = false, refreshing = false, error = r.appError())
                    }
                },
            )
        }
    }

    private companion object {
        const val PAGE = 50
    }
}
