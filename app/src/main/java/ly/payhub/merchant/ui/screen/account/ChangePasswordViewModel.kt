package ly.payhub.merchant.ui.screen.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ly.payhub.merchant.R
import ly.payhub.merchant.data.AppError
import ly.payhub.merchant.data.MerchantRepository
import ly.payhub.merchant.data.appError
import javax.inject.Inject

data class ChangePasswordUiState(
    val current: String = "",
    val newPassword: String = "",
    val confirm: String = "",
    val code: String = "",
    /** The TOTP field is shown when the account has MFA on, or once the server demands it. */
    val codeRequired: Boolean = false,
    val submitting: Boolean = false,
    /** Localised error key, or null. */
    val errorRes: Int? = null,
    /** Server-supplied error message (already friendly), or null. */
    val error: String? = null,
    /** Set on success — the screen toasts + pops. */
    val done: Boolean = false,
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val repo: MerchantRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChangePasswordUiState(codeRequired = repo.currentMe?.mfaEnabled == true),
    )
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    private fun clearError(s: ChangePasswordUiState) = s.copy(errorRes = null, error = null)

    fun onCurrent(v: String) = _state.update { clearError(it).copy(current = v) }
    fun onNew(v: String) = _state.update { clearError(it).copy(newPassword = v) }
    fun onConfirm(v: String) = _state.update { clearError(it).copy(confirm = v) }
    fun onCode(v: String) = _state.update { clearError(it).copy(code = v.filter(Char::isDigit).take(8)) }

    fun submit() {
        val s = _state.value
        if (s.submitting) return
        when {
            s.newPassword.length < MIN_LEN -> {
                _state.update { it.copy(errorRes = R.string.changepw_too_short) }; return
            }
            s.newPassword != s.confirm -> {
                _state.update { it.copy(errorRes = R.string.changepw_mismatch) }; return
            }
        }
        _state.update { it.copy(submitting = true, errorRes = null, error = null) }
        viewModelScope.launch {
            val code = s.code.trim().ifBlank { null }
            val r = repo.changePassword(s.current, s.newPassword, code)
            r.fold(
                onSuccess = { _state.update { it.copy(submitting = false, done = true) } },
                onFailure = {
                    val err = r.appError()
                    if (err is AppError.MfaRequired) {
                        _state.update {
                            it.copy(submitting = false, codeRequired = true, error = err.message, code = "")
                        }
                    } else {
                        _state.update { it.copy(submitting = false, error = err?.message) }
                    }
                },
            )
        }
    }

    companion object {
        const val MIN_LEN = 12
    }
}
