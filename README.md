# Phacteur for Android

Native Kotlin and Jetpack Compose client for `phacteur.app`. The app uses the
existing Phacteur data and session model; it does not contain demo mailbox data
or a second source of truth.

This public repository contains the complete Android client. The Phacteur web
application, mail ingestion workers, database schema, and server deployment
configuration remain in the separate backend repository and are not part of
this source tree.

## Release status

[Download the latest Android release](https://github.com/wasd-c/phacteur-android/releases/latest).

The downloadable APK is a debug preview for sideload testing on Android 9 and
newer. It is debug-signed and is not a Play Store or production build. Version
1.1.1 (build 6) uses the current Phacteur logo, provider-aware account settings,
mail actions and expandable sender details. It retains native mailbox groups,
the mail calendar and swipe refresh. Opt-in Firebase notifications require
the matching server routes, mobile delivery migration, and server Firebase
configuration to be deployed. Background email checks remain available without
Firebase; Android schedules these roughly every 15 minutes and may delay them
further to save battery. This workspace build retains the existing debug-preview
signing identity. A later production-signed build can require reinstalling the app.

## Included

- a mailbox-first Compose UI with Phacteur’s web colors and current logo,
  compact message rows, and phone/tablet split views;
- searchable mailbox and group selection, using the same accounts and groups
  as the website, groups before individual mailboxes and a direct group-creation action;
- provider-aware account names and readable connection states, native sender
  identity editing, Gmail reception renewal and manual external-IMAP fetch;
- local copy-code, sign-in-link and meeting actions, with expiry checks and
  HTTPS destinations opening in the browser without Phacteur credentials;
- expandable sender details and declared mail-header domains, with public
  encryption keys abbreviated before opening their full copyable value;
- duplicate counts matching the website, with read/archive/delete actions
  applied to every copy included in the selected mailbox scope;
- native group creation, editing and deletion, including mailbox order, colors,
  inactive mailboxes and a deletion confirmation that preserves messages;
- conversations scoped to the same mailboxes/groups, with refresh of their
  server definitions and a retry action when conversation messages cannot load;
- a native month calendar and daily agenda for deliveries, travel, reservations
  and tickets detected by the server, with access to the exact source email;
- server-side Important/Newsletters categories combined with inbox, unread,
  read, archive, and trash filters; search and pagination retain the selected scope;
- pull-to-refresh on mail, conversations, groups and calendar, including empty
  lists and retryable errors, while retaining existing data during refresh;
- HTML email rendering in mail and conversations, with readable text previews,
  a plain-text fallback, and links opening externally; scripts, local file access,
  and remote resources stay blocked in the email viewer;
- dashboard, contacts, account settings, and composition with the current
  mailbox’s sending identity selected by default;
- browser sign-in bridge with a five-minute, one-use PKCE grant and encrypted
  `user-session` cookie storage;
- native Credential Manager passkey sign-in and passkey enrollment, with a
  browser sign-in action after provider rejection (cancellation remains silent);
- privacy-safe FCM data notifications and a WorkManager catch-up path;
- a persistent counter of newly arrived unread messages since the last app
  opening, cleared on foreground entry and updated on read/archive/delete;
- exact notification deep links that reload a user-owned email by ID;
- Android Keystore-backed encryption for the session, passkey challenge
  cookies, PKCE verifier, and Firebase Installation ID.

Provider setup, private relays and 2FA management open their web screens;
Drive and API-key management remain on the website. The browser has a separate cookie jar and may ask
the user to sign in again. Attachments are listed but are not downloaded by
this first native release.

The launcher decides whether a notification badge shows a number or a dot and
which color it uses. Old messages have no captured authentication headers or
sender encryption keys; the app labels missing values rather than inferring them.
Header domains are declarations, not a claim of verified sender identity.

## Build

Requirements: Android Studio with Android SDK 36.1 and JDK 17 or newer.

```bash
./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
Override the server for a non-production build with:

```bash
./gradlew :app:assembleDebug -PPHACTEUR_BASE_URL=https://example.test/
```

The URL must use HTTPS; cleartext traffic is disabled for every variant. The
default is `https://phacteur.app/`.

## Firebase setup

Firebase client support uses Firebase Installation IDs (`onRegistered`,
`FirebaseMessaging.register()` and HTTP v1 `message.fid`). Delivery also requires
the matching server-side registration and independent durable mobile delivery
integration. The implementation is in the separate Phacteur backend repository;
building this APK does not deploy its routes or database migration.

1. Add an Android application with package `app.phacteur.android` to the
   Phacteur Firebase project.
2. Download its `google-services.json` to `app/google-services.json`.
   This file is intentionally ignored by Git. The Google Services Gradle plugin
   is applied only when that file exists, so contributors can build a debug UI
   without production credentials. Release builds fail closed when it is
   missing.
3. On the server, configure `FIREBASE_PROJECT_ID` and
   `ANDROID_APP_PACKAGE_NAME=app.phacteur.android`. Put the service-account JSON
   outside both repositories and inject it through the backend deployment's
   secret-management mechanism.
4. Deploy the backend push-registration and sending integration, including its
   database migration. Its Firebase configuration is independent of browser
   Web Push/VAPID. Follow the backend's `docs/android-fcm.md` for the opt-in
   deployment overlay, restrictive server-key permissions and device checks.

Notifications are offered once after sign-in and can also be enabled in
Settings. Android's app and channel permissions must allow them; Settings links
to the system notification controls when blocked. Without Firebase, the same
WorkManager catch-up runs independently. Its cursor advances only after Android
accepts the notification, so denied permission does not discard pending mail.

The Firebase client expects only `new_email`, the email ID, and the optional
thread ID, without sender, subject, or body. An authenticated metadata request
confirms that the current session owns the unread message before showing it.
Session generations reject delayed work and notification clicks after logout
or a change of account. FCM and periodic checks share a deduplication ledger;
an out-of-order push does not advance the periodic scan cursor. The first catch-up run
records a high-water mark instead of replaying old unread messages. Logging out,
session expiry, or disabling notifications unregisters the installation and
clears the local cursor. Firebase-enabled builds also request removal of the
server registration when one exists.

## Browser sign-in

Production must include this exact callback:

```env
MOBILE_AUTH_REDIRECT_URIS="phacteur://auth/callback"
```

The browser completes the site's sign-in flow,
then asks for explicit approval before returning a one-use code to the app.
The app verifies `state` and exchanges the code with its PKCE verifier.

## Backend compatibility

The client expects the production Phacteur API contract, including the
following mobile endpoints:

- `POST /api/mobile-auth/authorize` and `POST /api/mobile-auth/exchange`;
- `PUT` and `DELETE /api/mobile/push-registrations` for Firebase-enabled builds;
- `GET /api/mobile/email-notifications` and `GET /api/mobile/emails/{id}`;
- `GET /api/mobile/email-notifications/{id}` for privacy-safe push ownership checks;
- `GET /api/mailbox-groups` and `GET /api/email-accounts` for identity navigation;
- `POST /api/mailbox-groups`, `PATCH` and `DELETE /api/mailbox-groups/{id}`;
- `GET /api/calendar` with ISO `start`/`end` boundaries for the displayed month;
- `GET /api/threads` with `scope=conversations` and optional `accountIds`;
- `GET /api/emails` with `accountIds`, `inbox`, `category`, `status`, `search`,
  and pagination parameters, plus the existing thread, contact, dashboard, send,
  and passkey endpoints used by the web application.

Refreshing reloads stored messages and the current mailbox/group definitions,
matching the web client; provider ingestion continues on the server. Empty groups
stay empty, and never fall back to the unified inbox. Native group changes refresh
the same server definitions and invalidate mail/conversation caches. The calendar
currently aggregates all user mailboxes, matching its server contract.

Authentication uses the backend's HTTP-only `user-session` cookie. The app does
not embed an API key, OAuth client secret, Firebase service account, or signing
key. A source build without server compatibility can compile and show the login
screen, but cannot provide a functional mailbox session.

Before distributing an APK, check the actual deployed server: `/mobile-auth`
with valid PKCE parameters must show sign-in/consent, and unauthenticated
`/api/mobile/email-notifications` must return JSON 401 instead of HTML 404.
A checked-in Android client or `assetlinks.json` alone does not implement these
server routes. Passkey verification must also explicitly accept the configured
Android certificate origins, in addition to the website origin.

## Passkey association

Credential Manager requires the installed APK's signing identity to be linked
to `phacteur.app`. The Android manifest loads the website statement from
`https://phacteur.app/.well-known/assetlinks.json`. The website must return that
file directly with HTTP 200 and `Content-Type: application/json`; redirects and
HTML error pages fail verification.

The Digital Asset Links payload for the current debug-preview signing
certificate is checked in at [`.well-known/assetlinks.json`](.well-known/assetlinks.json).
Configure the backend with the matching values:

```env
ANDROID_APP_PACKAGE_NAME="app.phacteur.android"
ANDROID_APP_SHA256_CERT_FINGERPRINTS="17:25:C1:99:60:70:E0:C9:32:31:88:24:A7:E9:81:61:AD:AA:C5:FA:20:A2:67:07:B2:9E:AE:91:3F:F3:EA:62"
WEBAUTHN_ANDROID_ORIGINS="android:apk-key-hash:FyXBmWBw4MkyMYgkp-mBYa2qxfogomcHsp6ukT_z6mI"
```

The server derives and cross-checks the origins, and publishes
`/.well-known/assetlinks.json`. Deploy the checked-in payload through the web
application if the backend does not generate it. A missing or inconsistent
configuration fails closed. Verify the deployed file before testing a passkey:

```bash
curl --fail --show-error https://phacteur.app/.well-known/assetlinks.json
```

Use `./gradlew signingReport` for local signing diagnostics. Debug and release
certificates have different fingerprints. Replace the checked-in preview
fingerprint and backend values when a production signing identity or Play App
Signing certificate is introduced.

Bitwarden also verifies the `delegate_permission/common.handle_all_urls`
relation through Google's Digital Asset Links service. Verify both relations
against the actual installed APK certificate; a different local debug key needs
its own explicit association. Version and build number appear on the login and
Settings screens to distinguish APK updates.

## End-to-end release checks

- Browser login returns to the app and survives an app restart.
- Existing passkey login and new Android passkey enrollment both succeed.
- A real inbound email produces one generic notification while the app is
  backgrounded and while it is killed.
- Tapping that notification opens the exact email, including when it is outside
  the first mailbox page.
- Disabling notifications and logging out remove the server registration.
- Switch between all mail, an individual mailbox, and a multi-mailbox group;
  search/category/status filters and pagination stay within that selection.
- Pull down on populated and empty mailboxes: the spinner runs until the request
  finishes, existing mail survives network errors, and retry recovers.
- Switch scopes while a slow request is pending; the previous response must not
  replace the newly selected mailbox.
- Send, reply, read/unread, archive, thread star/archive, search, pagination,
  and tablet split views work against production-like data.

The repository build verifies compilation, lint, mailbox query/scope handling,
compose identity selection, the PKCE protocol, and passkey provider
rejection/cancellation handling. Compose interaction tests for mailbox selection
and pull-to-refresh run with `./gradlew :app:connectedDebugAndroidTest` when an
Android device or emulator is attached. Firebase delivery requires the external
project configuration and server integration. Passkey sign-in and actual email
notifications still need verification on a signed physical-device build; periodic
email checks do not require Firebase credentials.
