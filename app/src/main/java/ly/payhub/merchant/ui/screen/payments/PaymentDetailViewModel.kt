package ly.payhub.merchant.ui.screen.payments

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

data class PaymentDetailUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: AppError? = null,
    val payment: PaymentDetail? = null,
)

@HiltViewModel
class PaymentDetailViewModel @Inject constructor(
    private val repo: MerchantRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PaymentDetailUiState())
    val state: StateFlow<PaymentDetailUiState> = _state.asStateFlow()

    private var paymentId: String = ""

    fun start(id: String) {
        if (paymentId == id && _state.value.payment != null) return
        paymentId = id
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        _state.update {
            it.copy(
                loading = initial && it.payment == null,
                refreshing = !initial,
                error = null,
            )
        }
        viewModelScope.launch {
            val r = repo.getPayment(paymentId)
            r.fold(
                onSuccess = { p ->
                    _state.update { it.copy(loading = false, refreshing = false, payment = p) }
                },
                onFailure = {
                    _state.update {
                        it.copy(loading = false, refreshing = false, error = r.appError())
                    }
                },
            )
        }
    }
}
