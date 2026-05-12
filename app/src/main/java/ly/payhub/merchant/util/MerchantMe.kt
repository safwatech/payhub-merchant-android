package ly.payhub.merchant.util

import ly.payhub.MerchantMe

/**
 * Visibility helpers derived from `/merchant/auth/me`. The server still enforces
 * RBAC on every route — these only decide which affordances the app *shows*.
 *
 *  - [isParentOwner] — a parent-merchant user (not scoped to a sub) whose
 *    *effective* role is OWNER. The effective role is used (not the raw role)
 *    so a tier-degraded OWNER doesn't see write affordances they'll be 403'd on.
 *  - [canManageSubs] — [isParentOwner] **and** the install carries the
 *    `aggregator` entitlement (Pro+). Sub-merchant management is parent-OWNER-only.
 */
fun MerchantMe.isParentOwner(): Boolean =
    subMerchant == null && effectiveRole.equals("owner", ignoreCase = true)

fun MerchantMe.canManageSubs(): Boolean =
    isParentOwner() && entitlements.aggregator
