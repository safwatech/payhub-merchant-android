package ly.payhub.merchant.ui.screen.submerchants

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ly.payhub.MerchantApiKey
import ly.payhub.MerchantApiKeyWithSecret
import ly.payhub.merchant.data.AppError
import ly.payhub.merchant.data.MerchantRepository
import ly.payhub.merchant.data.appError
import javax.inject.Inject

data class SubMerchantApiKeysUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val busy: Boolean = false,
    val error: AppError? = null,
    val items: List<MerchantApiKey> = emptyList(),
    /**
     * The plaintext secret freshly minted by a `create` call. The screen pops
     * a copy-or-lose-it modal while this is non-null, then calls
     * [dismissReveal] — we hold it only in this in-memory state, never
     * persist, never log. After dismiss, the only retained surface is the
     * masked [MerchantApiKey] row appended to [items].
     */
    val revealedSecret: String? = null,
    val revealedKey: MerchantApiKey? = null,
    val message: String? = null,
)

/**
 * Backs the per-sub API-key tab. Scopes by `subMerchantId` (lifted from
 * `SavedStateHandle` so the standard `hiltViewModel()` factory works without
 * an `AssistedInject` factory). Calls `client.subMerchants.apiKeys.{list,
 * create, revoke}` via the repository.
 *
 * Parent-merchant API-key management is deliberately web-portal-only (see
 * `CLAUDE.md`'s native-apps note); this screen is sub-scoped.
 */
@HiltViewModel
class SubMerchantApiKeysViewModel @Inject constructor(
    private val repo: MerchantRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val subId: String = savedStateHandle.get<String>("id").orEmpty()

    private val _state = MutableStateFlow(SubMerchantApiKeysUiState())
    val state: StateFlow<SubMerchantApiKeysUiState> = _state.asStateFlow()

    init { refresh() }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeError() = _state.update { it.copy(error = null) }

    fun refresh() {
        _state.update {
            it.copy(
                loading = it.items.isEmpty(),
                refreshing = it.items.isNotEmpty(),
                error = null,
            )
        }
        viewModelScope.launch {
            val r = repo.listSubMerchantApiKeys(subId)
            r.fold(
                onSuccess = { keys ->
                    _state.update { it.copy(loading = false, refreshing = false, items = keys) }
                },
                onFailure = {
                    _state.update { it.copy(loading = false, refreshing = false, error = r.appError()) }
                },
            )
        }
    }

    /**
     * Mint a new key. The wire response carries the plaintext secret **once**
     * — it lives only in [revealedSecret] until the user dismisses the modal.
     */
    fun createKey(scopes: List<String>, onDone: (Boolean) -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val r = repo.createSubMerchantApiKey(subId, scopes)
            r.fold(
                onSuccess = { withSecret ->
                    val masked = withSecret.asMasked()
                    _state.update {
                        it.copy(
                            busy = false,
                            items = it.items + masked,
                            revealedSecret = withSecret.fullKeyMaterial(),
                            revealedKey = masked,
                            message = MSG_CREATED,
                        )
                    }
                    onDone(true)
                },
                onFailure = {
                    _state.update { it.copy(busy = false, error = r.appError()) }
                    onDone(false)
                },
            )
        }
    }

    fun dismissReveal() = _state.update { it.copy(revealedSecret = null, revealedKey = null) }

    fun revoke(keyId: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val r = repo.revokeSubMerchantApiKey(subId, keyId)
            r.fold(
                onSuccess = { updated ->
                    _state.update {
                        it.copy(
                            busy = false,
                            items = it.items.map { row -> if (row.id == updated.id) updated else row },
                            message = MSG_REVOKED,
                        )
                    }
                },
                onFailure = { _state.update { it.copy(busy = false, error = r.appError()) } },
            )
        }
    }

    companion object {
        const val MSG_CREATED = "API key created"
        const val MSG_REVOKED = "API key revoked"
    }
}

// ----- helpers ---------------------------------------------------------------

/**
 * Project the wide [MerchantApiKeyWithSecret] (carries the plaintext secret)
 * down to the masked [MerchantApiKey] for the list row.
 */
private fun MerchantApiKeyWithSecret.asMasked(): MerchantApiKey = MerchantApiKey(
    id = id,
    keyId = keyId,
    scopes = scopes,
    status = status,
    allowedIps = allowedIps,
    rateLimitTier = rateLimitTier,
    lastUsedAt = lastUsedAt,
    createdAt = createdAt,
    subMerchantId = subMerchantId,
)

/**
 * The wire format for the plaintext secret is `phk_<key_id>.<secret>`. The
 * SDK only carries the right-hand side in `.secret`; the operator wants the
 * full token to paste into a backend config.
 */
internal fun MerchantApiKeyWithSecret.fullKeyMaterial(): String = "phk_${keyId}.${secret}"
