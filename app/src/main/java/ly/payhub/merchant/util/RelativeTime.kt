package ly.payhub.merchant.util

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Lightweight relative-time formatting over `java.time` — "expires in 2 days",
 * "shared 3 h ago". Available on API 24 fully (the desugaring isn't even needed
 * for `Instant`/`Duration`/`OffsetDateTime`, but `app/build.gradle.kts` keeps
 * minSdk 24 so this is safe regardless).
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

    /** "in 2 days" / "5 h ago" / "just now". Null timestamp → "—". */
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

    /** "10 May 2026, 14:32" in the device zone. */
    fun absolute(iso: String?): String {
        val instant = parse(iso) ?: return "—"
        return DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
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
