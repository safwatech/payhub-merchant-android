# Changelog — PayHub Merchant (Android)

All notable changes to the native Android merchant app. Versioning is
independent of the PayHub server and SDK.

## [0.1.0] — 2026-05-10

Initial scaffold ("D6" of the mobile-uplift plan). Built on
`ly.payhub:payhub-android` **1.1.0** (the bearer-token `PayhubMerchantClient`).

### Added
- **Auth**: server-URL-aware login (on-prem installs), optional shop code for
  sub-merchant logins, TOTP MFA challenge, forgot-password, and the
  `payhub://accept-invite` deep-link flow.
- **Dashboard**: 24h / 3d / 7d KPI cards (paid count + volume, in-flight, active
  links, needs-follow-up), pull-to-refresh, role badge, and a per-shop breakdown
  for parent merchants (via a raw `GET /merchant/dashboard?group_by=sub` until
  the SDK exposes it).
- **Pay-links**: filterable list (All / Needs follow-up / Active / Paid /
  Expired / Cancelled) with infinite scroll and pull-to-refresh; create form
  (amount, description, customer phone, PSP allow-list, expiry, auto-generated
  order ref) with a share/copy success panel; detail screen with re-share,
  extend, clone, and cancel — write actions gated on the user's effective role.
- **More**: profile + entitlements card; push-notifications toggle (FCM token
  registered via a raw `POST /merchant/devices` — runtime
  `POST_NOTIFICATIONS` permission on Android 13+); request-password-reset;
  sign-out; app-version + server footer.
- **Push**: `PayhubFirebaseMessagingService` — token refresh re-registration and
  a "payments" notification channel; degrades to a no-op without a Firebase
  project / Google Play services.
- Material 3 theme seeded on the PayHub amber, with Material You dynamic colour
  on Android 12+.

### Known limitations / TODO(payhub) — pending SDK 1.2
- No merchant-payments list, change-password, MFA management, settlements, or
  sub-merchant management — the 1.1.0 SDK doesn't cover them.
- `/merchant/devices` and the dashboard sub-breakdown are raw authenticated HTTP
  calls in `data/RawMerchantApi.kt`; fold them into the SDK when 1.2 ships.
- Could not be compiled in the authoring environment (no Android toolchain) —
  relies on Android Studio / CI.
