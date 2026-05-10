package ly.payhub.merchant.ui.screen.paylinks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ly.payhub.PayLink
import ly.payhub.merchant.data.AppError
import ly.payhub.merchant.data.MerchantRepository
import ly.payhub.merchant.data.appError
import ly.payhub.merchant.data.asAppError
import ly.payhub.merchant.util.isWriteRole
import javax.inject.Inject

data class PayLinkDetailUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val link: PayLink? = null,
    val actionInProgress: Boolean = false,
    val canWrite: Boolean = false,
)

/** One-shot UI effects (snackbars, navigation) the screen consumes. */
sealed interface DetailEffect {
    data class Toast(val message: String) : DetailEffect
    data class ClonedTo(val newLinkId: String, val newRef: String) : DetailEffect
    data class Share(val url: String) : DetailEffect
}

@HiltViewModel
class PayLinkDetailViewModel @Inject constructor(
    private val repo: MerchantRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PayLinkDetailUiState(canWrite = isWriteRole(repo.currentMe?.effectiveRole ?: "")))
    val state: StateFlow<PayLinkDetailUiState> = _state.asStateFlow()

    private val effects = Channel<DetailEffect>(Channel.BUFFERED)
    val effectFlow = effects.consumeAsFlow()

    private var linkId: String = ""

    fun start(id: String) {
        if (linkId == id && _state.value.link != null) return
        linkId = id
        load()
    }

    fun reload() = load()

    private fun load() {
        _state.update { it.copy(loading = it.link == null, error = null, canWrite = isWriteRole(repo.currentMe?.effectiveRole ?: "")) }
        viewModelScope.launch {
            val r = repo.getPayLink(linkId)
            r.fold(
                onSuccess = { link -> _state.update { it.copy(loading = false, link = link) } },
                onFailure = { _state.update { it.copy(loading = false, error = r.appError()) } },
            )
        }
    }

    private fun runAction(block: suspend () -> Result<PayLink>) {
        if (_state.value.actionInProgress) return
        _state.update { it.copy(actionInProgress = true) }
        viewModelScope.launch {
            block().fold(
                onSuccess = { link -> _state.update { it.copy(actionInProgress = false, link = link) } },
                onFailure = { t ->
                    _state.update { it.copy(actionInProgress = false) }
                    effects.send(DetailEffect.Toast(t.asAppError().message))
                },
            )
        }
    }

    fun shareLink() {
        val link = _state.value.link ?: return
        viewModelScope.launch { effects.send(DetailEffect.Share(link.url)) }
    }

    /** Call after the share sheet / copy completes — records the reshare signal. */
    fun recordShared() = runAction { repo.markPayLinkShared(linkId) }

    fun extend(days: Int) {
        if (days <= 0) return
        runAction { repo.extendPayLink(linkId, days * 86_400) }
    }

    fun cancel() = runAction { repo.cancelPayLink(linkId) }

    fun clone() {
        if (_state.value.actionInProgress) return
        _state.update { it.copy(actionInProgress = true) }
        viewModelScope.launch {
            repo.clonePayLink(linkId).fold(
                onSuccess = { fresh ->
                    _state.update { it.copy(actionInProgress = false) }
                    effects.send(DetailEffect.ClonedTo(fresh.id, fresh.merchantOrderRef))
                },
                onFailure = { t ->
                    _state.update { it.copy(actionInProgress = false) }
                    effects.send(DetailEffect.Toast(t.asAppError().message))
                },
            )
        }
    }
}
