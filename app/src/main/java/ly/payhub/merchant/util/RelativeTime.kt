package ly.payhub.merchant.util

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import ly.payhub.merchant.R

/**
 * Relative-time helpers.
 *
 * Two layers:
 *
 *  - The plain Java methods here ([until], [absolute], [shortUntil]) produce
 *    English fallbacks suitable for logs / non-UI surfaces and for the JVM
 *    unit tests (which can't call Android framework classes like
 *    `android.text.format.DateUtils`).
 *  - The `@Composable` helpers below ([rememberRelativeUntil],
 *    [rememberAbsoluteTime]) delegate to `DateUtils`, which is locale-aware,
 *    so an AR system locale renders "قبل ٥ د" / a Gregorian date in the
 *    AR-LY conventions without per-call effort.
 *
 * Prefer the Composable helpers from Compose code; the plain methods stay for
 * tests and non-Compose call sites.
 */
object RelativeTime {

    /** Parse an ISO-8601 timestamp the API emits (with or without offset). Null on garbage. */
    fun parse(iso: String?): Instant? {
        if (iso.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(iso).toInstant() }
            .recoverCatching { Instant.parse(iso) }
            .recoverCatching {
                // Bare "2026-05-10T12:34:56" — assume UTC.
                java.time.LocalDateTime.parse(iso).atZone(ZoneId.of("UTC")).toInstant()
            }
            .getOrNull()
    }

    /** "in 2 days" / "5 hours ago" / "just now". Null timestamp → "—". */
    fun until(iso: String?, now: Instant = Instant.now()): String {
        val target = parse(iso) ?: return "—"
        val d = Duration.between(now, target)
        val future = !d.isNegative
        val secs = abs(d.seconds)
        val phrase = humanizeSeconds(secs)
        return when {
            secs < 45 -> "just now"
            future -> "in $phrase"
            else -> "$phrase ago"
        }
    }

    /** Short form for badges: "2d", "5h", "30m", "now". */
    fun shortUntil(iso: String?, now: Instant = Instant.now()): String {
        val target = parse(iso) ?: return "—"
        val secs = abs(Duration.between(now, target).seconds)
        return when {
            secs < 60 -> "now"
            secs < 3600 -> "${secs / 60}m"
            secs < 86_400 -> "${secs / 3600}h"
            else -> "${secs / 86_400}d"
        }
    }

    fun isPast(iso: String?, now: Instant = Instant.now()): Boolean {
        val target = parse(iso) ?: return false
        return target.isBefore(now)
    }

    /** "10 May 2026, 14:32" in the device zone, en-US numerals. */
    fun absolute(iso: String?): String {
        val instant = parse(iso) ?: return "—"
        return DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.US)
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }

    private fun humanizeSeconds(secs: Long): String = when {
        secs < 90 -> "1 minute"
        secs < 3600 -> "${secs / 60} minutes"
        secs < 5400 -> "1 hour"
        secs < 86_400 -> "${secs / 3600} hours"
        secs < 129_600 -> "1 day"
        secs < 2_592_000 -> "${secs / 86_400} days"
        secs < 5_184_000 -> "1 month"
        else -> "${secs / 2_592_000} months"
    }
}

/**
 * Locale-aware relative time — "5 min ago" in EN, "قبل ٥ د" in AR (system locale).
 * Returns [R.string.common_dash] for an unparseable timestamp.
 */
@Composable
fun rememberRelativeUntil(iso: String?): String {
    val target = RelativeTime.parse(iso) ?: return stringResource(R.string.common_dash)
    return DateUtils.getRelativeTimeSpanString(
        target.toEpochMilli(),
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

/** Locale-aware "10 May 2026, 14:32" — picks the device locale + calendar conventions. */
@Composable
fun rememberAbsoluteTime(iso: String?): String {
    val target = RelativeTime.parse(iso) ?: return stringResource(R.string.common_dash)
    val ctx = LocalContext.current
    return DateUtils.formatDateTime(
        ctx,
        target.toEpochMilli(),
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR,
    )
}
