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

/**
 * Filter chips on the settlement-detail screen. Wire values mirror the
 * server-side `SettlementRow.status` enum: `matched` / `mismatch` /
 * `missing_in_hub` / `missing_in_psp`.
 */
enum class SettlementRowFilter(val labelRes: Int, val wire: String?) {
    All(ly.payhub.merchant.R.string.sett_filter_all, null),
    Matched(ly.payhub.merchant.R.string.sett_filter_matched, "matched"),
    Mismatch(ly.payhub.merchant.R.string.sett_filter_mismatch, "mismatch"),
    MissingInHub(ly.payhub.merchant.R.string.sett_filter_missing_hub, "missing_in_hub"),
    MissingInPsp(ly.payhub.merchant.R.string.sett_filter_missing_psp, "missing_in_psp"),
}

data class SettlementDetailUiState(
    val loadingRows: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: AppError? = null,
    val filter: SettlementRowFilter = SettlementRowFilter.All,
    val rows: List<RawMerchantApi.SettlementRow> = emptyList(),
    val hasMore: Boolean = false,
    /** Lazy: only the rows endpoint is needed for the screen — the list
     *  endpoint already gave us the file's matched/mismatch counters. */
    val file: RawMerchantApi.SettlementFile? = null,
    val fileError: AppError? = null,
)

@HiltViewModel
class SettlementDetailViewModel @Inject constructor(
    private val repo: MerchantRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettlementDetailUiState())
    val state: StateFlow<SettlementDetailUiState> = _state.asStateFlow()

    private var fileId: String = ""

    fun start(id: String) {
        if (fileId == id && (_state.value.file != null || _state.value.rows.isNotEmpty())) return
        fileId = id
        loadFile()
        loadRows(initial = true)
    }

    fun setFilter(filter: SettlementRowFilter) {
        if (_state.value.filter == filter) return
        _state.update { it.copy(filter = filter, rows = emptyList(), hasMore = false) }
        loadRows(initial = true)
    }

    fun refresh() {
        loadFile()
        loadRows(initial = true)
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || !s.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val r = repo.listSettlementRows(
                fileId = fileId,
                statusFilter = s.filter.wire,
                limit = ROW_PAGE,
                offset = s.rows.size,
            )
            r.fold(
                onSuccess = { page ->
                    _state.update {
                        it.copy(
                            loadingMore = false,
                            rows = it.rows + page,
                            hasMore = page.size >= ROW_PAGE,
                        )
                    }
                },
                onFailure = { _state.update { it.copy(loadingMore = false) } },
            )
        }
    }

    private fun loadFile() {
        // Per-file counters by id — `/merchant/settlements/{id}` returns the
        // same model the list endpoint hands out, scoped to one file.
        _state.update { it.copy(fileError = null) }
        viewModelScope.launch {
            val r = repo.getSettlement(fileId)
            r.fold(
                onSuccess = { f -> _state.update { it.copy(file = f) } },
                onFailure = { _state.update { it.copy(fileError = r.appError()) } },
            )
        }
    }

    private fun loadRows(initial: Boolean) {
        _state.update {
            it.copy(
                loadingRows = initial && it.rows.isEmpty(),
                refreshing = initial && it.rows.isNotEmpty(),
                error = null,
            )
        }
        val s = _state.value
        viewModelScope.launch {
            val r = repo.listSettlementRows(
                fileId = fileId,
                statusFilter = s.filter.wire,
                limit = ROW_PAGE,
                offset = 0,
            )
            r.fold(
                onSuccess = { page ->
                    _state.update {
                        it.copy(
                            loadingRows = false,
                            refreshing = false,
                            rows = page,
                            hasMore = page.size >= ROW_PAGE,
                        )
                    }
                },
                onFailure = {
                    _state.update { it.copy(loadingRows = false, refreshing = false, error = r.appError()) }
                },
            )
        }
    }

    private companion object {
        const val ROW_PAGE = 100
    }
}
