package ly.payhub.merchant.ui.screen.account

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
import ly.payhub.merchant.data.asAppError
import javax.inject.Inject

data class MfaSettingsUiState(
    val mfaEnabled: Boolean = false,
    /** Non-null once `enrol` has run and before `confirm` lands — carries the secret + otpauth URI. */
    val enrol: RawMerchantApi.MfaEnrol? = null,
    val code: String = "",
    val disablePassword: String = "",
    val busy: Boolean = false,
    val error: AppError? = null,
    val message: String? = null,
)

@HiltViewModel
class MfaSettingsViewModel @Inject constructor(
    private val repo: MerchantRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MfaSettingsUiState(mfaEnabled = repo.currentMe?.mfaEnabled == true))
    val state: StateFlow<MfaSettingsUiState> = _state.asStateFlow()

    init {
        // Pick up any drift in mfaEnabled (e.g. just enabled it on another device).
        viewModelScope.launch { repo.refreshMe().onSuccess { me -> _state.update { it.copy(mfaEnabled = me.mfaEnabled) } } }
    }

    fun onCode(v: String) = _state.update { it.copy(code = v.filter(Char::isDigit).take(8), error = null) }
    fun onDisablePassword(v: String) = _state.update { it.copy(disablePassword = v, error = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeError() = _state.update { it.copy(error = null) }

    fun startEnrol() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val r = repo.mfaEnrol()
            r.fold(
                onSuccess = { e -> _state.update { it.copy(busy = false, enrol = e, code = "") } },
                onFailure = { t -> _state.update { it.copy(busy = false, error = t.asAppError()) } },
            )
        }
    }

    fun confirmEnrol() {
        val s = _state.value
        if (s.busy || s.code.length < 6) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val r = repo.mfaConfirm(s.code.trim())
            r.fold(
                onSuccess = {
                    _state.update {
                        it.copy(busy = false, mfaEnabled = true, enrol = null, code = "", message = MSG_ENABLED)
                    }
                },
                onFailure = { t -> _state.update { it.copy(busy = false, error = t.asAppError()) } },
            )
        }
    }

    fun cancelEnrol() = _state.update { it.copy(enrol = null, code = "", error = null) }

    fun disable() {
        val s = _state.value
        if (s.busy || s.disablePassword.isBlank()) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val r = repo.mfaDisable(s.disablePassword)
            r.fold(
                onSuccess = {
                    _state.update {
                        it.copy(busy = false, mfaEnabled = false, disablePassword = "", message = MSG_DISABLED)
                    }
                },
                onFailure = { t -> _state.update { it.copy(busy = false, error = t.asAppError()) } },
            )
        }
    }

    companion object {
        const val MSG_ENABLED = "Two-factor enabled"
        const val MSG_DISABLED = "Two-factor disabled"
    }
}
