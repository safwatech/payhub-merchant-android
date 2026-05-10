package ly.payhub.merchant.ui.screen.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ly.payhub.merchant.data.MerchantRepository
import ly.payhub.merchant.data.asAppError
import javax.inject.Inject

data class MfaUiState(
    val code: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    /** Set on success — the screen pops; AuthState already flipped to Authenticated. */
    val done: Boolean = false,
)

@HiltViewModel
class MfaViewModel @Inject constructor(
    private val repo: MerchantRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MfaUiState())
    val state: StateFlow<MfaUiState> = _state.asStateFlow()

    fun onCode(v: String) {
        // Accept only digits, cap at 8 (TOTP is 6; allow recovery-style 8-digit codes).
        val filtered = v.filter(Char::isDigit).take(8)
        _state.update { it.copy(code = filtered, error = null) }
    }

    fun submit(challengeToken: String) {
        val s = _state.value
        if (s.submitting) return
        if (s.code.length < 6) {
            _state.update { it.copy(error = "Enter the 6-digit code from your authenticator.") }
            return
        }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            repo.loginMfa(challengeToken, s.code).fold(
                onSuccess = { lr ->
                    if (lr.requiresMfa) {
                        _state.update { it.copy(submitting = false, error = "That code didn't work — try again.", code = "") }
                    } else {
                        _state.update { it.copy(submitting = false, done = true) }
                    }
                },
                onFailure = { t ->
                    _state.update { it.copy(submitting = false, error = t.asAppError().message, code = "") }
                },
            )
        }
    }
}
