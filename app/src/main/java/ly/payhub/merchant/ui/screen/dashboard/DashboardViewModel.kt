package ly.payhub.merchant.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ly.payhub.MerchantDashboard
import ly.payhub.MerchantMe
import ly.payhub.merchant.data.AppError
import ly.payhub.merchant.data.MerchantRepository
import ly.payhub.merchant.data.RawMerchantApi
import ly.payhub.merchant.data.appError
import javax.inject.Inject

/**
 * Selectable dashboard windows. The API caps `window_hours` at 168 (7 days).
 * Labels are resolved via [labelRes] so the chips translate; long-form labels
 * (Last 24 h / Last 7 d) are formatted on the screen.
 */
enum class DashWindow(val hours: Int, val labelRes: Int) {
    Day(24, ly.payhub.merchant.R.string.dash_window_24h),
    ThreeDays(72, ly.payhub.merchant.R.string.dash_window_3d),
    Week(168, ly.payhub.merchant.R.string.dash_window_7d),
}

data class DashboardUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: AppError? = null,
    val window: DashWindow = DashWindow.Day,
    val me: MerchantMe? = null,
    val data: MerchantDashboard? = null,
    /** Per-shop breakdown (parent users only) — raw HTTP; null if unavailable. */
    val subBreakdown: List<RawMerchantApi.SubBreakdownRow>? = null,
    val subBreakdownUnavailable: Boolean = false,
) {
    val paidCount: Int get() = data?.paymentsByStatus?.firstOrNull { it.status.equals("succeeded", true) }?.count ?: 0
    val paidVolumeMinor: Long get() = data?.paymentsByStatus?.firstOrNull { it.status.equals("succeeded", true) }?.volumeMinor ?: 0L
    val inflight: Int get() = data?.inflight ?: 0
    val activePayLinks: Int get() = data?.activePayLinks ?: 0
    val needsFollowup: Int get() = data?.needsFollowup ?: 0
    val isParent: Boolean get() = me?.subMerchant == null
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: MerchantRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState(me = repo.currentMe))
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun setWindow(window: DashWindow) {
        if (_state.value.window == window) return
        _state.update { it.copy(window = window) }
        load(initial = false)
    }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
        viewModelScope.launch {
            // Keep `me` fresh — role/entitlements may have changed server-side.
            repo.refreshMe().onSuccess { me -> _state.update { it.copy(me = me) } }

            val window = _state.value.window
            val dashResult = repo.dashboard(window.hours)
            dashResult.fold(
                onSuccess = { d -> _state.update { it.copy(data = d) } },
                onFailure = {
                    _state.update { it.copy(loading = false, refreshing = false, error = dashResult.appError()) }
                    return@launch
                },
            )

            // Parent users also get a per-shop breakdown (raw HTTP — not in SDK 1.1.0).
            if (_state.value.isParent) {
                repo.dashboardBySub(window.hours).fold(
                    onSuccess = { resp ->
                        _state.update { it.copy(subBreakdown = resp.subBreakdown, subBreakdownUnavailable = false) }
                    },
                    onFailure = {
                        // Not fatal — just hide the section / show a hint.
                        _state.update { it.copy(subBreakdown = null, subBreakdownUnavailable = true) }
                    },
                )
            } else {
                _state.update { it.copy(subBreakdown = null, subBreakdownUnavailable = false) }
            }

            _state.update { it.copy(loading = false, refreshing = false) }
        }
    }
}
