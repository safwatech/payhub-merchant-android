# PayHub Merchant — Android app

The native Android app for **PayHub** merchants: sign in to your merchant
portal, watch your dashboard, and create / share / follow up on pay-links from
your phone. Built on the official [`ly.payhub:payhub-android`](https://github.com/safwatech/payhub-android)
SDK (the bearer-token `PayhubMerchantClient`).

> **This is an app, not one of the PayHub SDK mirrors.** It currently lives in
> the PayHub monorepo at `apps/merchant-android/` for convenience, but it does
> **not** participate in the SDK-mirror publish machinery (`sdks/<lang>/` →
> `github.com/safwatech/payhub-<lang>`). To ship it, move this directory to its
> own repository — e.g.:
>
> ```bash
> git subtree split --prefix=apps/merchant-android -b merchant-android
> # …then push that branch to safwatech/payhub-merchant-android
> ```
>
> …or just copy the directory into a fresh repo. The standalone repo's own CI
> (the workflow under `.github/workflows/ci.yml`) and Play Console wiring take
> over from there.

---

## Status

`0.4.0` — see `CHANGELOG.md`. Every screen is on the **1.2.0** SDK
(`ly.payhub:payhub-android:1.2.0`); the previous `RawMerchantApi` /
`BearerRetry` shims are gone — `auth`, `pay-links`, `payments`, `settlements`,
`devices`, `account`, `mfa`, `org`, `subMerchants` (incl. nested `users` /
`apiKeys`), and `reports.dashboard(groupBySub = true)` all live in the SDK.

**It has not been compiled in the environment it was authored in** (no JDK /
Android SDK there) — it relies on Android Studio / the standalone repo's CI to
build. The Gradle wrapper JAR is **not** committed (it can't be without running
`gradle wrapper`); run `gradle wrapper` once, or just open the project in
Android Studio, which generates it.

## What it covers

The 1.2.0 SDK covers every `/merchant/*` endpoint this app needs, so the
repository is a thin pass-through to `client.<namespace>.<method>(…)`.
Webhook / PSP-gateway / parent-merchant API-key management are deliberately
left to the web portal; in-app API-key management is **scoped to a
sub-merchant** (Sub-merchant detail → API keys).

## Architecture

- **Kotlin 2.0 + Jetpack Compose** (Material 3 / Material You), single
  `MainActivity`, **Navigation-Compose** for the screen graph
  (`ui/AppNavHost.kt`), **Hilt** for DI.
- `data/MerchantRepository` is the single seam over `PayhubMerchantClient`: it
  owns the client, rebuilds it when the base URL / tokens change, exposes
  `suspend` functions returning `Result<T>` (failures are an `AppErrorException`
  carrying a friendly `AppError`), and publishes a `StateFlow<AuthState>` that
  the nav host routes on.
- `data/TokenStore` — `EncryptedSharedPreferences`: the token pair, the base
  URL, and the push opt-in flag.
- ViewModels are `@HiltViewModel`, each exposing a single `StateFlow<…UiState>`;
  composables hold no business logic.
- **FCM** — Firebase BoM + `firebase-messaging`;
  `push/PayhubFirebaseMessagingService` re-registers the token on refresh and
  shows notifications on the "payments" channel. Degrades to nothing without a
  Firebase project / Google Play services.
- **Theme** — `ui/theme/` — a Material 3 colour scheme seeded on the PayHub
  amber, with dynamic colour honoured on API 31+.

## Running it (Android Studio)

1. **The SDK.** Until `ly.payhub:payhub-android:1.2.0` is on Maven Central via
   the `safwatech/payhub-android` mirror, build it locally so `mavenLocal()`
   (already in `settings.gradle.kts`) can resolve it:

   ```bash
   # from the monorepo root:
   (cd sdks/android && ./gradlew publishToMavenLocal)
   # or, from a standalone checkout of this app, clone the SDK at its tag:
   git clone --depth 1 -b v1.2.0 https://github.com/safwatech/payhub-android
   (cd payhub-android && ./gradlew publishToMavenLocal)
   ```

2. **Firebase (optional but recommended for push).** Create a Firebase project,
   add an Android app with package `ly.payhub.merchant` (and, if you want push
   in debug builds, `ly.payhub.merchant.debug`), download `google-services.json`
   and drop it at `app/google-services.json` (it's gitignored — see
   `app/google-services.json.example`). Without it the Google-services Gradle
   plugin is **not** applied and FCM is a runtime no-op; the rest of the app
   works fine.

3. Open `apps/merchant-android/` in Android Studio, let it sync, and run on a
   device/emulator. At the login screen enter your **server URL** (default
   `https://app.payhub.ly` — on-prem installs put their own URL here; under
   "Advanced"), **merchant code**, optional **shop code** (for sub-merchant
   logins), **username**, and **password**.

## Building from the CLI

```bash
gradle wrapper                 # one-time: generates gradle/wrapper/gradle-wrapper.jar
./gradlew ktlintCheck detekt :app:testDebugUnitTest :app:assembleDebug
```

`assembleRelease` needs a signing keystore — provide it via a gitignored
`keystore.properties` at the project root:

```properties
storeFile=/abs/path/to/release.keystore
storePassword=…
keyAlias=payhub-merchant
keyPassword=…
```

…or via the `ANDROID_KEYSTORE_PATH` / `ANDROID_KEYSTORE_PASSWORD` /
`ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD` environment variables (what the
`release` CI job uses). **This keystore is the app's own — it is not the SDK's
Maven GPG signing key**; keep them separate.

## Distribution

Internal testing via the Google Play Console: build an AAB
(`./gradlew :app:bundleRelease`), upload to an internal-testing track, add
testers. The `release` job in `.github/workflows/ci.yml` is wired (currently
`if: false`) for when the standalone repo has the signing secrets.

## Deep links

- `https://app.payhub.ly/m/accept-invite?token=…&m=<merchant>&u=<username>&s=<shop>`
  — an **App Link** (verified via `/.well-known/assetlinks.json`). Opens the
  accept-invitation screen on devices where the app is installed; falls back to
  the SPA otherwise. The legacy `payhub://accept-invite?…` custom-scheme
  intent filter is retained through 0.4.0 for in-flight emails; slated for
  removal in 0.5.0.
- `payhub://pay-link/<id>` — emitted by push notifications; `AppNavHost`
  routes it to the pay-link detail screen (or parks it through the auth flow
  if the user is signed out).
