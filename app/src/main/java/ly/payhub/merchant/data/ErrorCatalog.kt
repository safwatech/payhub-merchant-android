package ly.payhub.merchant.data

import android.content.Context
import ly.payhub.MerchantValidationError
import ly.payhub.PayhubApiException
import ly.payhub.merchant.R

/**
 * Maps server error-envelope codes (e.g. `merchant.last_owner`) to localised
 * strings drawn from `res/values{,-ar}/strings.xml`. Falls back to the server's
 * English `message` for any code not in the table, and finally to a generic
 * error string when even that is blank.
 *
 * The catalogue stays small on purpose — the seed list of ~30 high-traffic
 * codes is documented in
 * `docs/superpowers/specs/2026-05-14-d6-d7-sdk-1.2-and-app-followups-design.md` §8.
 * Extending it: add a row here AND a matching `err_*` entry in **both**
 * `res/values/strings.xml` and `res/values-ar/strings.xml`.
 *
 * Two surfaces feed in:
 *  - `AppError.Validation(code, params, message)` — the typed path the
 *    repository emits when the SDK throws `MerchantValidationError`.
 *  - A bare `MerchantValidationError` / `PayhubApiException` — for callers
 *    that still hold the raw SDK exception.
 */
object ErrorCatalog {
    private val MAP: Map<String, Int> = mapOf(
        "merchant.invalid_credentials" to R.string.err_merchant_invalid_credentials,
        "merchant.locked_out" to R.string.err_merchant_locked_out,
        "merchant.last_owner" to R.string.err_merchant_last_owner,
        "merchant.disabled" to R.string.err_merchant_disabled,
        "mfa_required" to R.string.err_mfa_required_envelope,
        "hub.merchant.mfa_required" to R.string.err_mfa_required_envelope,
        "mfa.invalid_code" to R.string.err_mfa_invalid_code,
        "mfa.already_enabled" to R.string.err_mfa_already_enabled,
        "password.too_weak" to R.string.err_password_too_weak,
        "password.reuse" to R.string.err_password_reuse,
        "password.current_wrong" to R.string.err_password_current_wrong,
        "auth.session_expired" to R.string.err_auth_session_expired,
        "auth.bearer_expired" to R.string.err_auth_bearer_expired,
        "pay_link.quota_exceeded" to R.string.err_pay_link_quota_exceeded,
        "pay_link.expired" to R.string.err_pay_link_expired,
        "pay_link.cancelled" to R.string.err_pay_link_cancelled,
        "pay_link.already_paid" to R.string.err_pay_link_already_paid,
        "pay_link.max_retries" to R.string.err_pay_link_max_retries,
        "sub_merchant.code_taken" to R.string.err_sub_merchant_code_taken,
        "sub_merchant.has_payments" to R.string.err_sub_merchant_has_payments,
        "sub_merchant.disabled" to R.string.err_sub_merchant_disabled,
        "sub_merchant.aggregator_required" to R.string.err_sub_merchant_aggregator_required,
        "sub_user.last_owner" to R.string.err_sub_user_last_owner,
        "sub_user.username_taken" to R.string.err_sub_user_username_taken,
        "device.platform_keys_missing" to R.string.err_device_platform_keys_missing,
        "device.token_too_long" to R.string.err_device_token_too_long,
        "api_key.label_taken" to R.string.err_api_key_label_taken,
        "api_key.revoked" to R.string.err_api_key_revoked,
        "invite.expired" to R.string.err_invite_expired,
        "invite.consumed" to R.string.err_invite_consumed,
        "rate_limited" to R.string.err_rate_limited,
    )

    /** Resolve a typed `AppError.Validation` against the catalogue. */
    fun localize(error: AppError.Validation, ctx: Context): String =
        resolve(error.code, error.params, error.message, ctx)

    /** Resolve a bare SDK exception against the catalogue. */
    fun localize(error: PayhubApiException, ctx: Context): String {
        val params = (error as? MerchantValidationError)?.params.orEmpty()
        return resolve(error.code, params, error.message.orEmpty().substringBefore(" [request_id="), ctx)
    }

    private fun resolve(
        code: String,
        params: List<String>,
        fallbackMessage: String,
        ctx: Context,
    ): String {
        val resId = MAP[code]
            ?: return fallbackMessage.takeIf { it.isNotBlank() } ?: ctx.getString(R.string.err_generic)
        return if (params.isEmpty()) ctx.getString(resId)
        else ctx.getString(resId, *params.toTypedArray<Any>())
    }
}
