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

/** A freshly-minted (or re-issued) invite link, shown in a dialog after the action succeeds. */
data class InviteResult(
    val url: String,
    val sentToChannel: String?,
)

data class SubUsersUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val busy: Boolean = false,
    val error: AppError? = null,
    val items: List<SubUser> = emptyList(),
    /** Set when an invite/re-issue succeeds — the screen pops a copy-the-link dialog. */
    val invite: InviteResult? = null,
    /** Server-supplied message for the last write action (already friendly), shown via snackbar. */
    val message: String? = null,
)

@HiltViewModel
class SubUsersViewModel @Inject constructor(
    private val repo: MerchantRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SubUsersUiState())
    val state: StateFlow<SubUsersUiState> = _state.asStateFlow()

    private var subId: String = ""

    fun start(id: String) {
        if (subId == id && _state.value.items.isNotEmpty()) return
        subId = id
        load(initial = true)
    }

    fun refresh() = load(initial = true)

    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeInvite() = _state.update { it.copy(invite = null) }
    fun consumeError() = _state.update { it.copy(error = null) }

    private fun load(initial: Boolean) {
        _state.update {
            it.copy(
                loading = initial && it.items.isEmpty(),
                refreshing = initial && it.items.isNotEmpty(),
                error = null,
            )
        }
        viewModelScope.launch {
            val r = repo.listSubUsers(subId)
            r.fold(
                onSuccess = { list -> _state.update { it.copy(loading = false, refreshing = false, items = list) } },
                onFailure = { _state.update { it.copy(loading = false, refreshing = false, error = r.appError()) } },
            )
        }
    }

    fun invite(
        username: String,
        fullName: String,
        email: String,
        mobile: String,
        phone: String,
        role: String,
        onResult: (success: Boolean, message: String?) -> Unit,
    ) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = repo.createSubUser(
                subId,
                SubUserCreate(
                    username = username.trim(),
                    fullName = fullName.trim(),
                    email = email.trim().ifBlank { null },
                    mobile = mobile.trim().ifBlank { null },
                    phone = phone.trim().ifBlank { null },
                    role = role,
                ),
            )
            r.fold(
                onSuccess = { created ->
                    _state.update {
                        it.copy(
                            busy = false,
                            items = it.items + created.asSubUser(),
                            invite = InviteResult(created.inviteUrl, created.inviteSentToChannel),
                            message = MSG_INVITED,
                        )
                    }
                    onResult(true, null)
                },
                onFailure = {
                    _state.update { it.copy(busy = false) }
                    onResult(false, r.appError()?.message)
                },
            )
        }
    }

    fun update(uid: String, role: String?, status: String?, onDone: (Boolean) -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = repo.updateSubUser(subId, uid, SubUserPatch(role = role, status = status))
            r.fold(
                onSuccess = { upd ->
                    _state.update {
                        it.copy(
                            busy = false,
                            items = it.items.map { row -> if (row.id == upd.id) upd else row },
                            message = MSG_UPDATED,
                        )
                    }
                    onDone(true)
                },
                onFailure = { _state.update { it.copy(busy = false, message = r.appError()?.message) }; onDone(false) },
            )
        }
    }

    fun disable(uid: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = repo.disableSubUser(subId, uid)
            r.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            busy = false,
                            items = it.items.map { row -> if (row.id == uid) row.copy(status = "disabled") else row },
                            message = MSG_DISABLED,
                        )
                    }
                },
                onFailure = { _state.update { it.copy(busy = false, message = r.appError()?.message) } },
            )
        }
    }

    fun reissue(uid: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = repo.reissueSubUserInvite(subId, uid)
            r.fold(
                onSuccess = { ri ->
                    _state.update {
                        it.copy(busy = false, invite = InviteResult(ri.inviteUrl, ri.sentToChannel), message = MSG_REISSUED)
                    }
                },
                onFailure = { _state.update { it.copy(busy = false, message = r.appError()?.message) } },
            )
        }
    }

    fun clearMfa(uid: String, code: String, onDone: (Boolean) -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = repo.clearSubUserMfa(subId, uid, code.trim())
            r.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            busy = false,
                            items = it.items.map { row -> if (row.id == uid) row.copy(mfaEnabled = false) else row },
                            message = MSG_MFA_CLEARED,
                        )
                    }
                    onDone(true)
                },
                onFailure = { _state.update { it.copy(busy = false, message = r.appError()?.message) }; onDone(false) },
            )
        }
    }

    companion object {
        const val MSG_INVITED = "Invited"
        const val MSG_UPDATED = "Updated"
        const val MSG_DISABLED = "Disabled"
        const val MSG_REISSUED = "Invite re-issued"
        const val MSG_MFA_CLEARED = "Two-factor cleared"
    }
}
