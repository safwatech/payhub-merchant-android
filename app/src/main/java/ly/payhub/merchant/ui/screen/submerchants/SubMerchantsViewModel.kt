package ly.payhub.merchant.ui.screen.submerchants

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
import ly.payhub.*
import ly.payhub.merchant.data.appError
import javax.inject.Inject

data class SubMerchantsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: AppError? = null,
    val items: List<SubMerchant> = emptyList(),
    val creating: Boolean = false,
    /** Server-supplied create error (friendly), or null. */
    val createError: String? = null,
    val message: String? = null,
)

@HiltViewModel
class SubMerchantsViewModel @Inject constructor(
    private val repo: MerchantRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SubMerchantsUiState())
    val state: StateFlow<SubMerchantsUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = true)

    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun clearCreateError() = _state.update { it.copy(createError = null) }

    private fun load(initial: Boolean) {
        _state.update {
            it.copy(
                loading = initial && it.items.isEmpty(),
                refreshing = initial && it.items.isNotEmpty(),
                error = null,
            )
        }
        viewModelScope.launch {
            val r = repo.listSubMerchants()
            r.fold(
                onSuccess = { list -> _state.update { it.copy(loading = false, refreshing = false, items = list) } },
                onFailure = { _state.update { it.copy(loading = false, refreshing = false, error = r.appError()) } },
            )
        }
    }

    fun create(code: String, codePrefix: String, name: String, active: Boolean, onCreated: () -> Unit) {
        if (_state.value.creating) return
        _state.update { it.copy(creating = true, createError = null) }
        viewModelScope.launch {
            val r = repo.createSubMerchant(
                SubMerchantCreate(
                    code = code.trim(),
                    codePrefix = codePrefix.trim().uppercase(),
                    name = name.trim(),
                    status = if (active) "active" else "disabled",
                ),
            )
            r.fold(
                onSuccess = { sm ->
                    _state.update { it.copy(creating = false, items = it.items + sm, message = MSG_CREATED) }
                    onCreated()
                },
                onFailure = { _state.update { it.copy(creating = false, createError = r.appError()?.message) } },
            )
        }
    }

    companion object { const val MSG_CREATED = "Sub-merchant created" }
}
