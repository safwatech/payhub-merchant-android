package ly.payhub.merchant.ui.screen.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import ly.payhub.MerchantMe
import ly.payhub.merchant.data.MerchantRepository
import ly.payhub.merchant.data.asAppError
import javax.inject.Inject
import kotlin.coroutines.resume

data class MoreUiState(
    val me: MerchantMe? = null,
    val pushEnabled: Boolean = false,
    val pushBusy: Boolean = false,
    val signingOut: Boolean = false,
    val serverUrl: String = "",
    val message: String? = null,
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val repo: MerchantRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        MoreUiState(me = repo.currentMe, pushEnabled = repo.pushEnabled, serverUrl = repo.baseUrl),
    )
    val state: StateFlow<MoreUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { repo.refreshMe().onSuccess { me -> _state.update { it.copy(me = me) } } }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /**
     * Flip push on/off. The screen is responsible for obtaining the
     * POST_NOTIFICATIONS runtime permission first (API 33+) — it passes the
     * granted result in [permissionGranted]. We fetch the current FCM token and
     * register/unregister it with the hub.
     */
    fun setPushEnabled(enabled: Boolean, permissionGranted: Boolean) {
        if (_state.value.pushBusy) return
        if (enabled && !permissionGranted) {
            _state.update { it.copy(message = "Notification permission is required to enable push.") }
            return
        }
        _state.update { it.copy(pushBusy = true) }
        viewModelScope.launch {
            val token = runCatching { fcmToken() }.getOrNull()
            if (token == null) {
                _state.update {
                    it.copy(pushBusy = false, message = "Push isn't available on this device (no Google Play services).")
                }
                return@launch
            }
            val result = if (enabled) repo.registerDevice(token) else repo.unregisterDevice(token)
            result.fold(
                onSuccess = {
                    repo.pushEnabled = enabled
                    _state.update {
                        it.copy(
                            pushBusy = false,
                            pushEnabled = enabled,
                            message = if (enabled) "Push notifications on" else "Push notifications off",
                        )
                    }
                },
                onFailure = { t ->
                    _state.update { it.copy(pushBusy = false, message = t.asAppError().message) }
                },
            )
        }
    }

    fun forgotPassword(merchantCode: String, username: String, subCode: String?) {
        viewModelScope.launch {
            repo.forgotPassword(merchantCode, username, subCode).fold(
                onSuccess = { _state.update { it.copy(message = "Reset instructions sent (if that account exists).") } },
                onFailure = { t -> _state.update { it.copy(message = t.asAppError().message) } },
            )
        }
    }

    fun signOut() {
        if (_state.value.signingOut) return
        _state.update { it.copy(signingOut = true) }
        viewModelScope.launch {
            // Best-effort: drop the FCM token registration first.
            runCatching { fcmToken() }.getOrNull()?.let { repo.unregisterDevice(it) }
            repo.pushEnabled = false
            repo.logout()
            // AuthState → Unauthenticated drives the nav back to login.
        }
    }

    private suspend fun fcmToken(): String = suspendCancellableCoroutine { cont ->
        val task: Task<String> = FirebaseMessaging.getInstance().token
        task.addOnSuccessListener { cont.resume(it) }
        task.addOnFailureListener { cont.cancel(it) }
    }
}
