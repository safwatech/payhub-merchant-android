package ly.payhub.merchant

import ly.payhub.merchant.util.Money
import ly.payhub.merchant.util.humanizeRole
import ly.payhub.merchant.util.isWriteRole
import ly.payhub.merchant.util.pspLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {

    @Test
    fun lyd_renders_three_decimals() {
        assertEquals("4.500 LYD", Money.format(4500, "LYD"))
        assertEquals("1,234.567 LYD", Money.format(1_234_567, "LYD"))
        assertEquals("0.000 LYD", Money.format(0, "LYD"))
    }

    @Test
    fun usd_renders_two_decimals() {
        assertEquals("12.34 USD", Money.format(1234, "USD"))
    }

    @Test
    fun parse_major_to_minor_for_lyd() {
        assertEquals(4500L, Money.parseToMinor("4.5", "LYD"))
        assertEquals(4500L, Money.parseToMinor("4.500", "LYD"))
        assertEquals(25_000L, Money.parseToMinor("25", "LYD"))
        assertEquals(1_234_567L, Money.parseToMinor("1234.567", "LYD"))
    }

    @Test
    fun parse_rejects_garbage_and_negatives() {
        assertNull(Money.parseToMinor("abc", "LYD"))
        assertNull(Money.parseToMinor("", "LYD"))
        assertNull(Money.parseToMinor("-5", "LYD"))
    }

    @Test
    fun bare_format_has_no_currency_suffix() {
        assertEquals("4.500", Money.formatBare(4500, "LYD"))
    }

    @Test
    fun humanize_role() {
        assertEquals("Sub owner", humanizeRole("sub_owner"))
        assertEquals("Owner", humanizeRole("owner"))
    }

    @Test
    fun psp_labels() {
        assertEquals("Sadad", pspLabel("sadad"))
        assertEquals("Moamalat (Card)", pspLabel("moamalat"))
        assertEquals("Mobicash QR", pspLabel("mobicash"))
        assertEquals("T-Lync", pspLabel("tlync"))
    }

    @Test
    fun write_roles() {
        assertTrue(isWriteRole("owner"))
        assertTrue(isWriteRole("sub_operator"))
        assertFalse(isWriteRole("viewer"))
        assertFalse(isWriteRole("sub_viewer"))
        assertFalse(isWriteRole(""))
    }
}
