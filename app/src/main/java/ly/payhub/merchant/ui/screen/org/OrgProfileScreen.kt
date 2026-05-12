package ly.payhub.merchant.ui.screen.org

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ly.payhub.merchant.R
import ly.payhub.merchant.ui.components.ErrorBox
import ly.payhub.merchant.ui.components.LoadingBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrgProfileScreen(
    onBack: () -> Unit,
    viewModel: OrgProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.org_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        when {
            state.loading && state.org == null -> LoadingBox(modifier = Modifier.padding(inner), label = stringResource(R.string.loading))
            state.error != null && state.org == null -> ErrorBox(state.error!!, modifier = Modifier.padding(inner), onRetry = viewModel::load)
            state.org != null -> OrgForm(state, viewModel, Modifier.padding(inner))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrgForm(state: OrgProfileUiState, viewModel: OrgProfileViewModel, modifier: Modifier) {
    val org = state.org ?: return
    val e = state.edit
    val readOnly = !state.canEdit
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (readOnly) {
            Text(
                stringResource(R.string.org_read_only),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionHeader(stringResource(R.string.org_section_identity))
        ReadRow(stringResource(R.string.org_code), org.code)
        ReadRow(stringResource(R.string.org_status), org.status)
        Field(stringResource(R.string.org_name), e.name, readOnly) { v -> viewModel.onEdit { it.copy(name = v) } }
        TypeField(e.type, readOnly) { v -> viewModel.onEdit { it.copy(type = v) } }
        Field(stringResource(R.string.org_legal_name), e.legalName, readOnly) { v -> viewModel.onEdit { it.copy(legalName = v) } }
        Field(stringResource(R.string.org_tax_number), e.taxNumber, readOnly) { v -> viewModel.onEdit { it.copy(taxNumber = v) } }
        Field(stringResource(R.string.org_commercial_register_no), e.commercialRegisterNo, readOnly) { v ->
            viewModel.onEdit { it.copy(commercialRegisterNo = v) }
        }

        HorizontalDivider()
        SectionHeader(stringResource(R.string.org_section_contact))
        Field(
            stringResource(R.string.org_billing_email), e.billingEmail, readOnly,
            keyboardType = KeyboardType.Email,
            hint = if (e.billingEmail.isNotBlank() && !e.billingEmail.contains('@')) stringResource(R.string.org_err_email) else null,
        ) { v -> viewModel.onEdit { it.copy(billingEmail = v) } }
        Field(
            stringResource(R.string.org_support_email), e.supportEmail, readOnly,
            keyboardType = KeyboardType.Email,
            hint = if (e.supportEmail.isNotBlank() && !e.supportEmail.contains('@')) stringResource(R.string.org_err_email) else null,
        ) { v -> viewModel.onEdit { it.copy(supportEmail = v) } }
        Field(stringResource(R.string.org_phone), e.phone, readOnly, keyboardType = KeyboardType.Phone) { v ->
            viewModel.onEdit { it.copy(phone = v) }
        }
        Field(
            stringResource(R.string.org_website), e.website, readOnly,
            keyboardType = KeyboardType.Uri,
            hint = if (e.website.isNotBlank() && !e.website.startsWith("https://")) stringResource(R.string.org_err_https) else null,
        ) { v -> viewModel.onEdit { it.copy(website = v) } }
        Field(
            stringResource(R.string.org_logo_url), e.logoUrl, readOnly,
            keyboardType = KeyboardType.Uri,
            hint = if (e.logoUrl.isNotBlank() && !e.logoUrl.startsWith("https://")) stringResource(R.string.org_err_https) else null,
        ) { v -> viewModel.onEdit { it.copy(logoUrl = v) } }

        HorizontalDivider()
        SectionHeader(stringResource(R.string.org_section_address))
        Field(stringResource(R.string.org_address_line_1), e.addressLine1, readOnly) { v -> viewModel.onEdit { it.copy(addressLine1 = v) } }
        Field(stringResource(R.string.org_address_line_2), e.addressLine2, readOnly) { v -> viewModel.onEdit { it.copy(addressLine2 = v) } }
        Field(stringResource(R.string.org_city), e.city, readOnly) { v -> viewModel.onEdit { it.copy(city = v) } }
        Field(
            stringResource(R.string.org_country), e.country, readOnly,
            hint = if (e.country.isNotBlank() && !Regex("^[A-Za-z]{2}$").matches(e.country)) stringResource(R.string.org_err_country) else null,
        ) { v -> viewModel.onEdit { it.copy(country = v.uppercase()) } }

        if (!readOnly) {
            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.org_save))
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ReadRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value.ifBlank { stringResource(R.string.common_dash) },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    readOnly: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    hint: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        readOnly = readOnly,
        enabled = !readOnly,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        supportingText = if (hint != null) {
            { Text(hint, color = MaterialTheme.colorScheme.error) }
        } else {
            null
        },
        isError = hint != null,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeField(value: String, readOnly: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("person" to stringResource(R.string.org_type_person), "company" to stringResource(R.string.org_type_company))
    val label = options.firstOrNull { it.first == value }?.second ?: value
    if (readOnly) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            label = { Text(stringResource(R.string.org_type)) },
            readOnly = true,
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.org_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (code, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onChange(code); expanded = false })
            }
        }
    }
}
