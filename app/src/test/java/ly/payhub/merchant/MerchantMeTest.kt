package ly.payhub.merchant

import ly.payhub.Entitlements
import ly.payhub.MerchantMe
import ly.payhub.MerchantRefInfo
import ly.payhub.SubMerchantRef
import ly.payhub.merchant.util.canManageSubs
import ly.payhub.merchant.util.isParentOwner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantMeTest {

    private fun me(
        role: String = "owner",
        effectiveRole: String = role,
        sub: SubMerchantRef? = null,
        aggregator: Boolean = true,
    ) = MerchantMe(
        id = "u-1",
        username = "alice",
        role = role,
        effectiveRole = effectiveRole,
        fullName = "Alice",
        merchant = MerchantRefInfo(id = "m-1", code = "acme", name = "Acme", type = "company"),
        subMerchant = sub,
        entitlements = Entitlements(aggregator = aggregator),
    )

    @Test
    fun parent_owner_with_aggregator_can_manage_subs() {
        val m = me(role = "owner", aggregator = true)
        assertTrue(m.isParentOwner())
        assertTrue(m.canManageSubs())
    }

    @Test
    fun parent_owner_without_aggregator_cannot_manage_subs() {
        val m = me(role = "owner", aggregator = false)
        assertTrue(m.isParentOwner())
        assertFalse(m.canManageSubs())
    }

    @Test
    fun parent_non_owner_is_not_a_parent_owner() {
        assertFalse(me(role = "finance").isParentOwner())
        assertFalse(me(role = "viewer").isParentOwner())
    }

    @Test
    fun tier_degraded_owner_uses_effective_role() {
        // raw role OWNER but effective collapsed to viewer ⇒ no write affordances.
        assertFalse(me(role = "owner", effectiveRole = "viewer").isParentOwner())
    }

    @Test
    fun sub_user_is_never_a_parent_owner() {
        val sub = SubMerchantRef(id = "s-1", code = "east", codePrefix = "AE", name = "East")
        assertFalse(me(role = "sub_owner", sub = sub).isParentOwner())
        assertFalse(me(role = "sub_owner", sub = sub).canManageSubs())
    }
}
