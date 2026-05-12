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
import ly.payhub.merchant.data.RawMerchantApi
import ly.payhub.merchant.data.appError
import javax.inject.Inject

data class SubMerchantDetailUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val deleting: Boolean = false,
    val error: AppError? = null,
    val sub: RawMerchantApi.SubMerchant? = null,
    val editName: String = "",
    val editActive: Boolean = true,
    val message: String? = null,
    val deleted: Boolean = false,
)

@HiltViewModel
class SubMerchantDetailViewModel @Inject constructor(
    private val repo: MerchantRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SubMerchantDetailUiState())
    val state: StateFlow<SubMerchantDetailUiState> = _state.asStateFlow()

    private var subId: String = ""

    fun start(id: String) {
        if (subId == id && _state.value.sub != null) return
        subId = id
        load()
    }

    fun load() {
        _state.update { it.copy(loading = it.sub == null, error = null) }
        viewModelScope.launch {
            val r = repo.getSubMerchant(subId)
            r.fold(
                onSuccess = { sm ->
                    _state.update {
                        it.copy(loading = false, sub = sm, editName = sm.name, editActive = sm.status == "active")
                    }
                },
                onFailure = { _state.update { it.copy(loading = false, error = r.appError()) } },
            )
        }
    }

    fun onName(v: String) = _state.update { it.copy(editName = v) }
    fun onActive(v: Boolean) = _state.update { it.copy(editActive = v) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeError() = _state.update { it.copy(error = null) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val sm = s.sub ?: return
        val name = s.editName.trim().takeIf { it != sm.name && it.isNotBlank() }
        val status = if (s.editActive == (sm.status == "active")) null else if (s.editActive) "active" else "disabled"
        if (name == null && status == null) {
            _state.update { it.copy(message = MSG_UPDATED) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val r = repo.updateSubMerchant(subId, RawMerchantApi.SubMerchantPatch(name = name, status = status))
            r.fold(
                onSuccess = { upd ->
                    _state.update {
                        it.copy(saving = false, sub = upd, editName = upd.name, editActive = upd.status == "active", message = MSG_UPDATED)
                    }
                },
                onFailure = { _state.update { it.copy(saving = false, error = r.appError()) } },
            )
        }
    }

    fun delete() {
        val s = _state.value
        if (s.deleting) return
        if ((s.sub?.paymentsCount ?: 0) > 0) return
        _state.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            val r = repo.deleteSubMerchant(subId)
            r.fold(
                onSuccess = { _state.update { it.copy(deleting = false, deleted = true) } },
                onFailure = { _state.update { it.copy(deleting = false, error = r.appError()) } },
            )
        }
    }

    companion object { const val MSG_UPDATED = "Updated" }
}
