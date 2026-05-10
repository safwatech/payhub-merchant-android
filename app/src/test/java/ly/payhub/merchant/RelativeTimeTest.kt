package ly.payhub.merchant

import ly.payhub.merchant.util.RelativeTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RelativeTimeTest {

    private val now = Instant.parse("2026-05-10T12:00:00Z")

    @Test
    fun parses_offset_and_z_timestamps() {
        assertEquals(Instant.parse("2026-05-10T12:00:00Z"), RelativeTime.parse("2026-05-10T12:00:00Z"))
        assertEquals(Instant.parse("2026-05-10T12:00:00Z"), RelativeTime.parse("2026-05-10T14:00:00+02:00"))
        assertEquals(Instant.parse("2026-05-10T12:00:00Z"), RelativeTime.parse("2026-05-10T12:00:00"))
        assertNull(RelativeTime.parse("not-a-date"))
        assertNull(RelativeTime.parse(null))
    }

    @Test
    fun future_and_past_phrasing() {
        assertEquals("in 2 days", RelativeTime.until("2026-05-12T12:00:00Z", now))
        assertEquals("3 hours ago", RelativeTime.until("2026-05-10T09:00:00Z", now))
        assertEquals("just now", RelativeTime.until("2026-05-10T12:00:10Z", now))
        assertEquals("—", RelativeTime.until(null, now))
    }

    @Test
    fun short_form() {
        assertEquals("2d", RelativeTime.shortUntil("2026-05-12T12:00:00Z", now))
        assertEquals("5h", RelativeTime.shortUntil("2026-05-10T17:00:00Z", now))
        assertEquals("30m", RelativeTime.shortUntil("2026-05-10T12:30:00Z", now))
        assertEquals("now", RelativeTime.shortUntil("2026-05-10T12:00:30Z", now))
    }

    @Test
    fun is_past() {
        assertTrue(RelativeTime.isPast("2026-05-09T12:00:00Z", now))
        assertFalse(RelativeTime.isPast("2026-05-11T12:00:00Z", now))
        assertFalse(RelativeTime.isPast(null, now))
    }
}
