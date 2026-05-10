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

data class AcceptInviteUiState(
    val password: String = "",
    val confirm: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class AcceptInviteViewModel @Inject constructor(
    private val repo: MerchantRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AcceptInviteUiState())
    val state: StateFlow<AcceptInviteUiState> = _state.asStateFlow()

    fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onConfirm(v: String) = _state.update { it.copy(confirm = v, error = null) }

    fun submit(token: String) {
        val s = _state.value
        if (s.submitting) return
        when {
            token.isBlank() -> { _state.update { it.copy(error = "This invitation link is incomplete.") }; return }
            s.password.length < 12 -> { _state.update { it.copy(error = "Password must be at least 12 characters.") }; return }
            s.password != s.confirm -> { _state.update { it.copy(error = "Passwords don't match.") }; return }
        }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            repo.acceptInvite(token, s.password).fold(
                onSuccess = { _state.update { it.copy(submitting = false, done = true) } },
                onFailure = { t -> _state.update { it.copy(submitting = false, error = t.asAppError().message) } },
            )
        }
    }
}
