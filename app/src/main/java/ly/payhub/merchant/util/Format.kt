package ly.payhub.merchant.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Currency / amount formatting helpers.
 *
 * PayHub amounts are in *minor units*. For LYD that's the millidinar — three
 * decimal places (1 LYD = 1000 minor). Other ISO-4217 currencies use the
 * standard exponent.
 */
object Money {

    /** Minor-unit exponent per currency (how many decimal places). LYD = 3. */
    fun exponentFor(currency: String): Int = when (currency.uppercase(Locale.ROOT)) {
        "LYD", "TND", "BHD", "KWD", "OMR", "JOD" -> 3
        "JPY", "KRW", "VND", "CLP", "ISK" -> 0
        else -> 2
    }

    /** `4500` LYD → `"4.500 LYD"`. */
    fun format(minor: Long, currency: String = "LYD"): String {
        val exp = exponentFor(currency)
        val value = BigDecimal(minor).movePointLeft(exp).setScale(exp, RoundingMode.UNNECESSARY)
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = exp
            maximumFractionDigits = exp
        }
        return "${nf.format(value)} ${currency.uppercase(Locale.ROOT)}"
    }

    /** Just the number part, e.g. `"4.500"` — used to pre-fill amount fields. */
    fun formatBare(minor: Long, currency: String = "LYD"): String {
        val exp = exponentFor(currency)
        return BigDecimal(minor).movePointLeft(exp).setScale(exp, RoundingMode.UNNECESSARY).toPlainString()
    }

    /** Parse a user-typed major-unit string ("4.5", "4.500") into minor units; null if not a number. */
    fun parseToMinor(text: String, currency: String = "LYD"): Long? {
        val cleaned = text.trim().replace(",", "")
        if (cleaned.isEmpty()) return null
        val dec = cleaned.toBigDecimalOrNull() ?: return null
        if (dec.signum() < 0) return null
        return dec.movePointRight(exponentFor(currency)).setScale(0, RoundingMode.HALF_UP).toLong()
    }
}

/** Convenience: format an LYD minor amount. */
fun formatLyd(minor: Long): String = Money.format(minor, "LYD")

/** Title-case a snake/kebab token for display: `"sub_owner"` → `"Sub owner"`. */
fun humanizeRole(role: String): String =
    role.replace('_', ' ').replace('-', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }

/** PSP wire codes → friendly labels (mirrors the portal). */
fun pspLabel(code: String): String = when (code.lowercase(Locale.ROOT)) {
    "sadad" -> "Sadad"
    "moamalat" -> "Moamalat (Card)"
    "mobicash" -> "Mobicash QR"
    "tlync", "t-lync", "t_lync" -> "T-Lync"
    "adfali" -> "Adfali"
    else -> humanizeRole(code)
}

val KNOWN_PSPS: List<Pair<String, String>> = listOf(
    "sadad" to "Sadad",
    "moamalat" to "Moamalat (Card)",
    "mobicash" to "Mobicash QR",
    "tlync" to "T-Lync",
)

/** Roles that may perform pay-link write actions. Mirrors the backend RBAC matrices. */
fun isWriteRole(effectiveRole: String): Boolean = when (effectiveRole.lowercase(Locale.ROOT)) {
    "owner", "developer", "sub_owner", "sub_operator" -> true
    else -> false
}

/**
 * Friendly label for a sub-merchant role wire code. The "shop" framing matches
 * the portal's vocabulary (`sub_owner` → "Shop owner"). Unknown codes fall back
 * to [humanizeRole].
 */
fun humanizeSubRole(role: String): String = when (role.lowercase(Locale.ROOT)) {
    "sub_owner" -> "Shop owner"
    "sub_operator" -> "Shop operator"
    "sub_viewer" -> "Shop viewer"
    else -> humanizeRole(role)
}
