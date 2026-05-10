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

data class LoginUiState(
    val serverUrl: String = "",
    val merchantCode: String = "",
    val subCode: String = "",
    val username: String = "",
    val password: String = "",
    val advancedExpanded: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    /** One-shot: set when the server demands MFA; the screen navigates and clears it. */
    val mfaChallengeToken: String? = null,
    /** One-shot: a transient "check your email" style message from the forgot flow. */
    val info: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repo: MerchantRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        LoginUiState(
            serverUrl = repo.baseUrl,
            // If the user is "unauthenticated" because their session expired, the
            // last merchant code might be worth pre-filling — but we don't persist
            // it for privacy. Start blank.
        ),
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onServerUrl(v: String) = _state.update { it.copy(serverUrl = v, error = null) }
    fun onMerchantCode(v: String) = _state.update { it.copy(merchantCode = v, error = null) }
    fun onSubCode(v: String) = _state.update { it.copy(subCode = v, error = null) }
    fun onUsername(v: String) = _state.update { it.copy(username = v, error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }
    fun toggleAdvanced() = _state.update { it.copy(advancedExpanded = !it.advancedExpanded) }
    fun consumeMfaChallenge() = _state.update { it.copy(mfaChallengeToken = null) }
    fun consumeInfo() = _state.update { it.copy(info = null) }

    fun submit() {
        val s = _state.value
        if (s.submitting) return
        if (s.merchantCode.isBlank() || s.username.isBlank() || s.password.isEmpty()) {
            _state.update { it.copy(error = "Merchant code, username and password are required.") }
            return
        }
        // Persist any base-URL change before authenticating.
        if (s.serverUrl.isNotBlank() && s.serverUrl.trim().trimEnd('/') != repo.baseUrl) {
            repo.setBaseUrl(s.serverUrl)
        }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = repo.login(
                merchantCode = s.merchantCode,
                username = s.username,
                password = s.password,
                subCode = s.subCode,
            )
            result.fold(
                onSuccess = { lr ->
                    if (lr.requiresMfa) {
                        _state.update {
                            it.copy(submitting = false, mfaChallengeToken = lr.challengeToken ?: "")
                        }
                    } else {
                        // AuthState flips → AppNavHost routes to Home. Clear the password.
                        _state.update { it.copy(submitting = false, password = "") }
                    }
                },
                onFailure = { t ->
                    _state.update { it.copy(submitting = false, error = t.asAppError().message) }
                },
            )
        }
    }

    fun forgotPassword(merchantCode: String, username: String, subCode: String?) {
        viewModelScope.launch {
            repo.forgotPassword(merchantCode, username, subCode).fold(
                onSuccess = {
                    _state.update {
                        it.copy(info = "If that account exists, we've sent reset instructions to its email and phone.")
                    }
                },
                onFailure = { t -> _state.update { it.copy(error = t.asAppError().message) } },
            )
        }
    }
}
