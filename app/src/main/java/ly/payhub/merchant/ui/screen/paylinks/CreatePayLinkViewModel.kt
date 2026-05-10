package ly.payhub.merchant.ui.screen.paylinks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ly.payhub.CreatePayLinkRequest
import ly.payhub.PayLink
import ly.payhub.merchant.data.MerchantRepository
import ly.payhub.merchant.data.asAppError
import ly.payhub.merchant.util.KNOWN_PSPS
import ly.payhub.merchant.util.Money
import java.util.Locale
import javax.inject.Inject
import kotlin.random.Random

data class CreatePayLinkUiState(
    val amount: String = "",
    val description: String = "",
    val customerPhone: String = "",
    val selectedPsps: Set<String> = emptySet(),
    val expiryDays: String = DEFAULT_EXPIRY_DAYS.toString(),
    val orderRef: String = "",
    val orderRefEdited: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    /** Set on success — the screen swaps to the "share" panel. */
    val created: PayLink? = null,
) {
    companion object { const val DEFAULT_EXPIRY_DAYS = 7 }
}

@HiltViewModel
class CreatePayLinkViewModel @Inject constructor(
    private val repo: MerchantRepository,
) : ViewModel() {

    val knownPsps: List<Pair<String, String>> = KNOWN_PSPS

    private val merchantSlug: String = run {
        val raw = repo.currentMe?.merchant?.code ?: "shop"
        raw.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }.take(12).ifBlank { "shop" }
    }

    private val _state = MutableStateFlow(CreatePayLinkUiState(orderRef = generateRef()))
    val state: StateFlow<CreatePayLinkUiState> = _state.asStateFlow()

    private fun generateRef(): String = "$merchantSlug-${Random.nextInt(1_000_000, 10_000_000)}"

    fun onAmount(v: String) = _state.update { it.copy(amount = v.filter { c -> c.isDigit() || c == '.' }, error = null) }
    fun onDescription(v: String) = _state.update { it.copy(description = v, error = null) }
    fun onCustomerPhone(v: String) = _state.update { it.copy(customerPhone = v, error = null) }
    fun onExpiryDays(v: String) = _state.update { it.copy(expiryDays = v.filter(Char::isDigit).take(3), error = null) }
    fun onOrderRef(v: String) = _state.update { it.copy(orderRef = v, orderRefEdited = true, error = null) }
    fun regenerateRef() = _state.update { it.copy(orderRef = generateRef(), orderRefEdited = false) }

    fun togglePsp(code: String) = _state.update {
        it.copy(selectedPsps = if (code in it.selectedPsps) it.selectedPsps - code else it.selectedPsps + code, error = null)
    }

    fun submit() {
        val s = _state.value
        if (s.submitting) return
        val amountMinor = Money.parseToMinor(s.amount, "LYD")
        when {
            amountMinor == null || amountMinor <= 0 -> {
                _state.update { it.copy(error = "Enter a valid amount.") }; return
            }
            s.orderRef.isBlank() -> { _state.update { it.copy(error = "Order reference can't be empty.") }; return }
        }
        val days = s.expiryDays.toIntOrNull()?.takeIf { it in 1..3650 } ?: CreatePayLinkUiState.DEFAULT_EXPIRY_DAYS
        val request = CreatePayLinkRequest(
            amountMinor = amountMinor!!,
            currency = "LYD",
            merchantOrderRef = s.orderRef.trim(),
            description = s.description.trim().ifBlank { null },
            allowedPsps = s.selectedPsps.toList().ifEmpty { null },
            customerMsisdnHint = s.customerPhone.trim().ifBlank { null },
            expiresInSeconds = days * 86_400,
            language = "both",
        )
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            repo.createPayLink(request).fold(
                onSuccess = { link -> _state.update { it.copy(submitting = false, created = link) } },
                onFailure = { t -> _state.update { it.copy(submitting = false, error = t.asAppError().message) } },
            )
        }
    }

    /** Called once the user has shared/copied the created link — records the share signal. */
    fun markShared() {
        val id = _state.value.created?.id ?: return
        viewModelScope.launch { repo.markPayLinkShared(id) }
    }
}
