# Changelog — PayHub Merchant (Android)

All notable changes to the native Android merchant app. Versioning is
independent of the PayHub server and SDK.

## [0.2.0] — 2026-05-11

Phone-first surface for the most-asked-for mobile queries (payments + settlements),
plus a full Arabic translation and a working pay-link push deep-link.

### Added
- **Payments** — new 3rd bottom-nav tab. List with status filters (All /
  Pending / Awaiting / Paid / Failed / Cancelled / Refunded), infinite scroll
  and pull-to-refresh, backed by `GET /merchant/payments`. Tapping a row opens
  a **payment detail** screen with the amount + status pill, copyable order
  ref, PSP + reference, customer mobile (when present in `metadata.customer_msisdn`),
  full `payment_events` timeline rendered as a coloured-dot timeline, the
  remaining `metadata` key/value list, and a "View pay-link" button that
  deep-jumps back to the pay-link detail when `metadata.pay_link_id` is set.
- **Settlements** — entry under More → "Settlements". List of settlement files
  (filename, PSP, matched/total + mismatch badges) and a **settlement-detail**
  screen with the per-file counter strip (Total / Matched / Mismatch /
  Missing-in-hub / Missing-in-PSP — coloured when non-zero), filter chips per
  reconciliation status, a paginated row list, an inline diff table for
  mismatched rows, and a tap-through to a row's payment detail when a
  `payment_id` is present.
- **Pay-link push deep-link** — `payhub://pay-link/{id}` now routes correctly.
  Manifest declares the intent-filter, `AppNavHost` parses the URI and pushes
  the link's detail screen on top of `home`; a deep-link that arrives while
  signed-out is parked in saveable state and replayed after the auth flow
  completes (mirrors how the SPA preserves deep-links through a forced login).
- **Full Arabic localisation** — every user-facing string moved to
  `values/strings.xml` (~170 entries) with a matching `values-ar/strings.xml`
  pulled from the SPA's `web/src/i18n/locales/ar.ts` where the concept maps,
  so the portal, PWA and native app share one vocabulary. `AppError` gets a
  `@Composable localizedMessage()` extension; `StatusPill` resolves its labels
  via `stringResource`. `RelativeTime` gains a pair of `@Composable` helpers
  (`rememberRelativeUntil` / `rememberAbsoluteTime`) backed by
  `android.text.format.DateUtils` for locale-aware date formatting.

### Changed
- **`MerchantRepository`** gains `listPayments` / `getPayment` /
  `listSettlements` / `getSettlement` / `listSettlementRows` (raw HTTP via
  `RawMerchantApi` until SDK 1.2 ships). `dashboardBySub`, `registerDevice`,
  `unregisterDevice` reuse a new `withAccess` helper for token + auth-loss
  propagation, replacing per-method boilerplate.
- **`RawMerchantApi`** gets a generic `getJson<T>` helper that all 4 new
  endpoints share; per-page caps mirror the server (`payments` 200,
  `settlements` 200, `settlement rows` 1000).
- **`HomeScreen`** bottom nav grows from 3 → 4 tabs (Dashboard / Pay-links /
  Payments / More). `MoreScreen` adds a "Settlements" list entry.
- **Pay-link / payment status chips** ("Paid", "Expired") are now localised
  via `R.string.status_*`.

### Tests
- `RawMerchantApiPaymentsTest` — MockWebServer-backed coverage of the four
  new raw endpoints plus device registration. Asserts request shape (path +
  query + bearer header) and JSON → Kotlin model round-trips against the
  Pydantic response shapes in `app/api/merchant/payments.py` /
  `app/api/merchant/settlements.py`.

### Known limitations
- The merchant-payments list / detail and settlements endpoints are still
  raw-HTTP — fold them into the SDK when 1.2 ships.
- Number formatting stays Latin-digits + ISO currency code in both locales
  (matches the SPA); the `LYD` suffix is not yet swapped for `د.ل` in AR.
- Relative-time strings inside non-Composable utility callers stay English;
  the locale-aware `@Composable` helpers exist for callers that opt in.

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
