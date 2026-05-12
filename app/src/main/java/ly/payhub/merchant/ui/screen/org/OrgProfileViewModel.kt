package ly.payhub.merchant.ui.screen.org

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
import ly.payhub.merchant.data.RawMerchantApi
import ly.payhub.merchant.data.appError
import ly.payhub.merchant.util.isParentOwner
import javax.inject.Inject

/** The editable subset of an org profile, mirrored into mutable strings. `code`/`status`/`created_at` are read-only. */
data class OrgEdit(
    val name: String = "",
    val type: String = "company",
    val legalName: String = "",
    val taxNumber: String = "",
    val commercialRegisterNo: String = "",
    val billingEmail: String = "",
    val supportEmail: String = "",
    val phone: String = "",
    val website: String = "",
    val addressLine1: String = "",
    val addressLine2: String = "",
    val city: String = "",
    val country: String = "",
    val logoUrl: String = "",
)

data class OrgProfileUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: AppError? = null,
    val org: RawMerchantApi.OrgInfo? = null,
    val edit: OrgEdit = OrgEdit(),
    val canEdit: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class OrgProfileViewModel @Inject constructor(
    private val repo: MerchantRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OrgProfileUiState(canEdit = repo.currentMe?.isParentOwner() == true))
    val state: StateFlow<OrgProfileUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = it.org == null, error = null) }
        viewModelScope.launch {
            val r = repo.getOrg()
            r.fold(
                onSuccess = { o -> _state.update { it.copy(loading = false, org = o, edit = o.toEdit()) } },
                onFailure = { _state.update { it.copy(loading = false, error = r.appError()) } },
            )
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeError() = _state.update { it.copy(error = null) }

    fun onEdit(transform: (OrgEdit) -> OrgEdit) = _state.update { it.copy(edit = transform(it.edit)) }

    fun save() {
        val s = _state.value
        if (s.saving || !s.canEdit) return
        val org = s.org ?: return
        val patch = buildPatch(org, s.edit)
        if (patch == null) {
            // nothing changed
            _state.update { it.copy(message = MSG_SAVED) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val r = repo.updateOrg(patch)
            r.fold(
                onSuccess = { o -> _state.update { it.copy(saving = false, org = o, edit = o.toEdit(), message = MSG_SAVED) } },
                onFailure = { _state.update { it.copy(saving = false, error = r.appError()) } },
            )
        }
    }

    /** Build a patch from only the dirty fields. An emptied field whose original was non-null → `""` (server clears it). */
    private fun buildPatch(org: RawMerchantApi.OrgInfo, e: OrgEdit): RawMerchantApi.OrgPatch? {
        fun diff(current: String?, edited: String): String? {
            val orig = current.orEmpty()
            if (edited == orig) return null
            return edited // "" intentionally sent to clear; a value to set
        }
        val name = e.name.trim().takeIf { it != org.name && it.isNotBlank() }
        val type = e.type.takeIf { it != org.type }
        val legalName = diff(org.legalName, e.legalName.trim())
        val taxNumber = diff(org.taxNumber, e.taxNumber.trim())
        val commercialRegisterNo = diff(org.commercialRegisterNo, e.commercialRegisterNo.trim())
        val billingEmail = diff(org.billingEmail, e.billingEmail.trim())
        val supportEmail = diff(org.supportEmail, e.supportEmail.trim())
        val phone = diff(org.phone, e.phone.trim())
        val website = diff(org.website, e.website.trim())
        val addressLine1 = diff(org.addressLine1, e.addressLine1.trim())
        val addressLine2 = diff(org.addressLine2, e.addressLine2.trim())
        val city = diff(org.city, e.city.trim())
        val country = diff(org.country, e.country.trim().uppercase())
        val logoUrl = diff(org.logoUrl, e.logoUrl.trim())
        val anyDirty = listOf(
            name, type, legalName, taxNumber, commercialRegisterNo, billingEmail, supportEmail,
            phone, website, addressLine1, addressLine2, city, country, logoUrl,
        ).any { it != null }
        if (!anyDirty) return null
        return RawMerchantApi.OrgPatch(
            name = name, type = type, legalName = legalName, taxNumber = taxNumber,
            commercialRegisterNo = commercialRegisterNo, billingEmail = billingEmail, supportEmail = supportEmail,
            phone = phone, website = website, addressLine1 = addressLine1, addressLine2 = addressLine2,
            city = city, country = country, logoUrl = logoUrl,
        )
    }

    private fun RawMerchantApi.OrgInfo.toEdit() = OrgEdit(
        name = name, type = type, legalName = legalName.orEmpty(), taxNumber = taxNumber.orEmpty(),
        commercialRegisterNo = commercialRegisterNo.orEmpty(), billingEmail = billingEmail.orEmpty(),
        supportEmail = supportEmail.orEmpty(), phone = phone.orEmpty(), website = website.orEmpty(),
        addressLine1 = addressLine1.orEmpty(), addressLine2 = addressLine2.orEmpty(), city = city.orEmpty(),
        country = country.orEmpty(), logoUrl = logoUrl.orEmpty(),
    )

    companion object { const val MSG_SAVED = "Saved" }
}
