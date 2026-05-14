# Changelog — PayHub Merchant (Android)

All notable changes to the native Android merchant app. Versioning is
independent of the PayHub server and SDK.

## [0.4.0] — 2026-05-14

The 1.2.0 SDK uplift — every `/merchant/*` endpoint now rides the SDK — plus
four feature follow-ups that round out the on-device parent-OWNER toolkit.

### Added
- **App Links for the invite URL.** `https://app.payhub.ly/m/accept-invite?…`
  now opens the app directly on devices where it's installed (verified via
  `<intent-filter android:autoVerify="true">` against the server-side
  `/.well-known/assetlinks.json`). The legacy `payhub://accept-invite` intent
  filter is kept through this release for in-flight emails — slated for
  removal in 0.5.0.
- **Account → Diagnostics** screen with a *Send anonymous crash reports*
  toggle (default **off**). Sentry / GlitchTip init is now runtime-gated on
  both a baked-in DSN **and** the toggle; turning the toggle off `Sentry.close`s
  the SDK so subsequent crashes are not captured. The Diagnostics row is
  hidden in builds without a DSN.
- **Sub-merchant detail → Cashiers tab** (invite / disable / clear-MFA) and
  **API keys tab** (generate / revoke). Plaintext API-key secrets are
  surfaced **once** at create time in a copy-or-it-is-gone modal — the server
  stores only an argon2 hash.
- **Localised server-error envelope.** The new `ErrorCatalog` resolves ~30
  high-traffic codes (`merchant.last_owner`, `pay_link.quota_exceeded`,
  `sub_merchant.code_taken`, `mfa.invalid_code`, …) to translated strings in
  `values{,-ar}/strings.xml`. Unknown codes fall back to the server's
  English message.

### Changed
- **Upgrades to `ly.payhub:payhub-android` 1.2.0.** The in-app
  `RawMerchantApi` and `BearerRetry` shims are deleted — every endpoint now
  goes through the SDK, including `payments` / `settlements` / `devices` /
  `account` / `mfa` / `org` / `subMerchants` (incl. nested `users` and
  `apiKeys`) and `reports.dashboard(groupBySub = true)`. SDK-level
  transparent 401 → refresh → retry covers what `BearerRetry` used to do.
- **Refresh-token-at-rest** is now AES/GCM-encrypted with an
  AndroidKeyStore key bound to biometric / device-credential auth — but
  **only when the app-lock toggle is on**. Toggling app lock on triggers a
  one-shot rewrap; toggling off transparently unwraps. The 30-second
  user-auth validity window lets the unlock prompt grant the SDK's first
  refresh without re-prompting; a stale window forces the next refresh to
  re-prompt. A `KeyPermanentlyInvalidatedException` (biometric enrolment
  changed) ⇒ session loss.
- `AppError` gains a `Validation(code, params, message)` case — server
  validation envelopes now reach the UI with their stable code, ready for
  catalogue lookup.

### Security
- Refresh tokens at rest are no longer in plain EncryptedSharedPreferences
  when app lock is on; an OS-enforced biometric gate is now required to
  unwrap them.

### Tests
- `ErrorCatalogTest` — catalogue hits resolve, unknowns fall back, param
  interpolation works.
- `RefreshTokenVaultTest` — round-trip on/off, rewrap migration,
  `KeyPermanentlyInvalidatedException` fallthrough (Robolectric Keystore).
- `CrashReportingControllerTest` — Sentry init / close on the toggle's
  edges, DSN-missing short-circuit.

### Known limitations
- Still not compiled in the authoring environment — relies on Android
  Studio / CI.
- Webhook / PSP-gateway / parent-merchant API-key management remains
  web-portal-only.

## [0.3.0] — 2026-05-12

The rest of the merchant surface a shopkeeper or parent owner needs from a phone
— in-app account / 2FA / organisation / sub-merchant management — plus three
pieces of "ship-ready" hardening.

### Added
- **Change password** — More → Security → Change password. Old / new (≥12-char
  client check) / confirm; a TOTP-code field appears when 2FA is on (or on
  `hub.merchant.mfa_required`). A 401 from a wrong old-password / wrong code is
  surfaced inline — it is **not** treated as a session expiry, so you stay
  signed in.
- **Two-factor management** — More → Security → Two-factor. Enable → setup key
  (selectable) + a client-rendered QR (`com.google.zxing:core`) → 6-digit
  confirm; Disable → password. The More-screen 2FA badge updates via `refreshMe()`.
- **Organisation profile** — More → Business → Organisation profile (parent
  users). `code` / `status` / `created_at` read-only; `name`, `type`,
  `legal_name`, `tax_number`, `commercial_register_no`, `billing_email`,
  `support_email`, `phone`, `website`, address fields, `logo_url` editable for a
  parent **OWNER** (client validation mirrors the server; PATCH sends only dirty
  keys; an empty string clears).
- **Sub-merchant & sub-user management** — More → Business → Sub-merchants
  (parent **OWNER** with the aggregator entitlement). Create / edit / list
  sub-merchants (delete refused while `payments_count > 0` — disable instead);
  per sub: invite a sub-user (copyable invite link + the channel it was sent
  on), edit role / status, disable, reissue invite, clear MFA (with the acting
  owner's own TOTP). Last-`SUB_OWNER` and self-modify guards surface the server's
  messages.
- **Biometric / device-credential app lock** — More → Security → App lock.
  When on, the app re-prompts for Face ID / fingerprint / device PIN on cold
  start and after >2 min in the background (`androidx.biometric`,
  `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`). The toggle is disabled until a screen
  lock is set on the device. Off by default; the choice is cleared on sign-out.
- **Crash / error reporting** → GlitchTip (Sentry protocol). Off unless a DSN is
  built in (`-Ppayhub.sentryDsn=…` / `PAYHUB_SENTRY_DSN`); no PII, crashes/errors
  only, release-tagged `payhub-merchant-android@<version>`.

### Changed
- **Raw API calls now ride a transparent 401 → refresh → retry.** The `payhub-android`
  SDK already did this for the endpoints it covers; the raw-HTTP shim (payments
  / settlements / devices and all the screens above) didn't, so a ~30-min access-token
  expiry mid-session logged you out. Extracted `BearerRetry`; `MerchantRepository.withAccess`
  delegates to it (refresh de-duped behind a mutex). A 401 that survives the retry,
  or a dead refresh token, still drops the session.
- **`RawMerchantApi`** gains the `/merchant/auth/{change-password,mfa/*}`,
  `/merchant/org`, and `/merchant/sub-merchants[/…/users]` endpoints + their
  `@Serializable` models, and `httpError` is now envelope-aware (surfaces the
  server's `error.message` for 400 / 409 / 422, falling back to FastAPI's
  `{"detail": …}`). New `AppError.MfaRequired`.
- **`MainActivity`** is now a `FragmentActivity` (so `BiometricPrompt` can host
  its dialog). **`MoreScreen`** grows "Security" + (parent-only) "Business" groups.
- New deps: `androidx.biometric`, `androidx.lifecycle:lifecycle-process`,
  `io.sentry:sentry-android`, `com.google.zxing:core`.

### Tests
- `RawMerchantApi{Auth,Org,SubMerchants}Test`, `MerchantMeTest` — the new
  endpoints' request shapes / decodes and the `isParentOwner` / `canManageSubs`
  visibility matrix.
- `BearerRetryTest` — the 401-refresh-retry policy incl. the concurrent-401
  de-dup; `AppLockControllerTest` — the cold-start / background-timeout re-lock logic.

### Known limitations
- The account / org / sub-merchant endpoints stay raw-HTTP — fold into SDK 1.2.
- `SUB_OWNER` cashier self-management and in-app sub-merchant API-key management
  are still `// TODO(payhub)` (web portal only for now).
- Crash reporting has no user-facing opt-out yet (the build-time DSN gates it).
- Still not compiled in the authoring environment — relies on Android Studio / CI.

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
