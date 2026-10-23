# CleanTV / TV Home - continuation handoff

Prepared on 23 October 2026, Asia/Kolkata.
This document describes the actual implementation and verified state, rather than treating the developer preview as a finished public release.
No private credential values are included.

## 0. Update for 0.2 (23 October 2026)

This section supersedes older statements below where they conflict.

### Deploy order

1. Deploy the relay first by pushing `main`, because the 0.1 relay rejects 0.2 TV state replies with its strict schema.
2. Confirm `GET /health` returns 200 after the deployment.
3. Only then replace the two APKs on the download page or install them on TVs and phones.

No new database migration is required.

### What changed

- Relay: new settings fields, new remote keys, state validation that passes unknown TV fields through, and `DELETE /v1/account`.
- TV 0.2.0: tvOS-style home with app banners in a glass favourites shelf, an All apps grid, folders, hidden apps, and an adaptive background tinted by the focused app.
- TV 0.2.0: Liquid Glass and Frosted Glass, adjustable blur through `RenderEffect` on Android 12 or later, and a darker scrim on older TVs.
- TV 0.2.0: Dark Mode that is off, on, sunset to sunrise, or custom, with darker tiles and banners.
- TV 0.2.0: status pill styles (minimal, full, weather, hidden) that open Control Center.
- TV 0.2.0: Control Center with Screensaver, Wi-Fi, Bluetooth, Inputs, Settings, Dark Mode, Cast, Recent apps, and TV Home settings.
- TV 0.2.0: Recent Apps for apps opened from TV Home, with a live RAM meter, and Up to close an app through `killBackgroundProcesses`.
- TV 0.2.0: weather and sunrise or sunset from Open-Meteo, which needs no key, using a city chosen on the TV or phone.
- TV 0.2.0: remappable Menu and Info or Guide buttons, a first-run favourites seed, package-change refresh, and immediate relay reconnect when the network returns.
- TV 0.2.0: tile pictures and the wallpaper bitmap are released when another app is in front and redrawn on return.
- TV 0.2.0: release builds use R8 and resource shrinking; the release APK is about 285 KB.
- TV 0.2.0: publisher signing is read from `TVHOME_KEYSTORE`, `TVHOME_KEYSTORE_PASSWORD`, `TVHOME_KEY_ALIAS`, and `TVHOME_KEY_PASSWORD`; without them the development key is used.
- Phone 0.2.0: a Style tab for glass, blur, Dark Mode schedule, status pill, tiles, scenes, and dimming.
- Phone 0.2.0: Settings for weather, remote buttons, a 12-app favourites shelf, folders, hidden apps, and account deletion.
- Phone 0.2.0: Control Center, Recent apps, and All apps shortcuts on the Remote tab, plus forgot-password and a `reset-password` screen.
- Phone 0.2.0: options that need TV 0.2 are hidden when the paired TV still runs 0.1.

### Required Supabase dashboard step for password reset

Add `tvhome://reset-password` to Authentication, URL Configuration, Redirect URLs.
Without it, Supabase sends the reset link to the Site URL and the app cannot finish the reset.

### Not implemented, by design

- Continue Watching rows need the system TV provider's watch-next data, which third-party launchers cannot read on current Android TV builds.
- True sleep or power off needs system privileges, so Control Center starts the screensaver instead.
- Recent Apps lists apps opened from TV Home, because the system recents list is not available to third-party apps.

### Verification status for 0.2

Passed: relay TypeScript build, TV debug and release builds, TV lint with no errors, companion typecheck and lint, and iOS and Android JavaScript exports.
The TV release APK contains the production relay origin and is signed with the same development certificate as the 0.1 preview, so it installs as an update.
No TV, emulator, or Android TV system image was available, so the new interface has not been run on a screen.
Sign-in, pairing, wallpaper upload, reconnection, away-from-home control, and account deletion still need checks with a real TV and an authorized account.

## 1. Start here

| Item | Current value |
| --- | --- |
| Repository | https://github.com/shivanshthakur4u/CleanTV |
| Git remote | `git@github.com:shivanshthakur4u/CleanTV.git` |
| Working branch | `main` |
| Latest implementation commit before this handoff | `b6b1033` |
| Public backend and download website | https://clean-tv-chi.vercel.app/ |
| Database health endpoint | https://clean-tv-chi.vercel.app/health |
| Current display name | TV Home, with provisional application identifiers. |
| Existing local checkout | `/Users/saurabhsingh/Documents/Codex/2026-10-21/ca/outputs/tv-home` |

The most important unfinished step is to configure the deployed relay origin in both native app builds, rebuild them, and verify real account-to-TV pairing and wallpaper control.
The backend is deployed and database-connected, but the supplied APKs were built before the production URL was available.
Do not tell the user that the current preview APKs already provide verified internet control.

On 23 October, read-only requests confirmed:

| Request | Result |
| --- | --- |
| `GET /` | HTTP 200, HTML landing/download page. |
| `GET /health` | HTTP 200, `{"status":"ok"}`. |
| `GET /v1/devices` without authorization | HTTP 401. |

These checks do not prove physical-TV pairing or remote control.

## 2. What the user wants

The user wants a polished TV home application inspired by CleanYourTV and ZeroPulse TV, with cleaner visuals and more advanced customization.
The companion must support Android phones and iPhones, including wallpaper changes and supported TV controls both at home and away from home.
The first release targets Android TV / Google TV across brands, followed by other TV operating systems later.
The user wants public website distribution and selected Supabase as the backend and Vercel as the host.

The user explicitly rejected asking ordinary customers to enter a server address.
Hosting addresses belong in publisher environment variables and app build configuration.
Customers should install, sign in, enter the TV pairing code, and approve pairing on the TV.
Do not restore the removed server-address settings or ask customers for hosting information.

Broad Android TV coverage is a target, not a verified compatibility claim.
Samsung Tizen, LG webOS, Roku, and Apple TV cannot install this Android TV APK.
Additional advanced features have not all been selected or implemented.
Do not invent commitments about universal remote control, schedules, widgets, video wallpapers, or other operating systems.

## 3. What exists

### Native TV application

- Kotlin Android TV launcher, package `app.tvhome.launcher`.
- Android 8 or newer, minimum SDK 26, compile/target SDK 36, JDK 17.
- Atmospheric scenes, clock, installed-app discovery, favorite apps, favorite ordering, room name, dimming, and 12/24-hour clock setting.
- Personal photo wallpaper and an optional ambient clock screensaver.
- Physical-remote focus navigation and a platform default-home picker where firmware supports it.
- User-enabled foreground companion-control service and an outbound OkHttp WebSocket connection.
- Encrypted device credentials using Android KeyStore, explicit pairing approval, reconnect backoff, command deadlines, and acknowledgments after application.
- The relay origin comes from `BuildConfig.RELAY_URL`, never from a customer input field.

The application does not disable the manufacturer's launcher automatically.
Phone navigation operates within TV Home and does not inject keys into arbitrary applications.
Launching another app is constrained by Android activity-launch rules and requires TV Home to be visible.
Universal power, volume, HDMI switching, system-wide typing, and waking a sleeping TV are not implemented.
Manufacturer-specific Home-button handling, reboot behavior, and foreground-service retention require physical-device verification.

### Phone companion

- Expo SDK 57, React 19.2.3, React Native 0.86.3, and Expo Router.
- Shared Android/iPhone source, package/bundle identifier `app.tvhome.companion`.
- Sign-up/sign-in through Supabase Auth, a TV selector, pairing flow, wallpaper/scene controls, favorite settings, and a limited remote.
- SecureStore-backed authentication sessions, with chunking for larger session values.
- Account-free design preview that is explicitly identified as a preview.
- Remote buttons disabled in design preview, rather than pretending a TV is connected.
- Photo selection/manipulation, with camera, microphone, and Android overlay permissions blocked.
- A standalone Android APK has been built; no signed iPhone IPA is available.

The website is a landing/download page, not a browser version of the companion.
Password recovery and account deletion flows are not implemented in this preview.

### Backend and website

- TypeScript, Fastify 5, PostgreSQL through `pg`, `ws`, `jose`, and Zod.
- Supabase Auth access tokens verified through the project's JWKS, with ES256/RS256, issuer, audience, and owner checks.
- Per-device random secrets, expiring pairing codes, explicit TV approval, access revocation, and owner-scoped device APIs.
- PostgreSQL-backed routing across different relay instances, rather than assuming the HTTP request and TV socket share one process.
- Server-only command storage, acknowledgments, deadlines, connection leases, shared rate limits, and PostgreSQL notifications.
- HTML download page and both Android APKs under `relay/public/downloads/`.
- A Vercel deployment with its project Root Directory set to `relay`.

No Supabase service-role key is needed by either client or by this relay implementation.

## 4. Source map

| Path | Responsibility |
| --- | --- |
| `tv/app/src/main/java/app/tvhome/launcher/MainActivity.kt` | TV UI, focus, app lists, settings, pairing approval. |
| `tv/app/src/main/java/app/tvhome/launcher/RemoteService.kt` | Foreground service, HTTP/WebSocket connection, commands, acknowledgments, reconnects. |
| `tv/app/src/main/java/app/tvhome/launcher/RelayCredentials.kt` | Device secret storage and compiled relay origin. |
| `tv/app/src/main/java/app/tvhome/launcher/HomeStore.kt` | Persisted appearance, favorites, and wallpaper handling. |
| `tv/app/src/main/java/app/tvhome/launcher/AtmosphereView.kt` | TV background rendering. |
| `tv/app/src/main/java/app/tvhome/launcher/ClockDream.kt` | Ambient clock screensaver. |
| `tv/app/build.gradle.kts` | TV build target and relay-origin configuration. |
| `companion/src/lib/auth.ts` | Public environment configuration, Supabase client, secure sessions, authenticated HTTP helper. |
| `companion/src/lib/store.tsx` | Shared app state and device/control operations. |
| `companion/src/app/auth.tsx` | Account UI and design-preview entry. |
| `companion/src/app/pair.tsx` | Pairing UI. |
| `companion/src/app/(tabs)/` | Home, wallpaper, remote, and settings screens. |
| `companion/src/components/ui.tsx` | Shared visual components, colors, scenes, icons. |
| `companion/app.json` | Native app identifiers, plugins, appearance, permissions. |
| `companion/eas.json` | Expo EAS build profiles. |
| `relay/src/server.ts` | Fastify routes, authentication, validation, WebSocket handling, lifecycle, temporary diagnostics. |
| `relay/src/broker.ts` | Connection leases, command claims/results, notifications, cleanup. |
| `relay/src/database.ts` | Certificate-path normalization shared by runtime connections and migrations. |
| `relay/src/rate-store.ts` | Database-backed quota counters. |
| `relay/src/migrate.ts` | Locked, recorded migration runner. |
| `relay/migrations/` | Database schema migrations. |
| `relay/certs/prod-ca-2021.crt` | Public Supabase root CA, not a private key. |
| `relay/vercel.json` | Current supported Fastify deployment configuration. |
| `relay/public/` | Website and downloadable Android preview APKs. |
| `VERCEL.md` | Deployment/build configuration instructions. |
| `docs/protocol.md` | HTTP/socket protocol and command lifecycle. |
| `docs/verification.md` | Historical verification record and remaining checks. |
| `docs/dependency-notes.md` | Compatibility overrides and known advisory status at the previous review. |

Read `companion/AGENTS.md` before editing the mobile app.
Its Expo instructions require consulting matching SDK documentation and keeping generated native directories generated.

## 5. Environment and credentials

### Backend

The Vercel project needs these private server settings:

```dotenv
SUPABASE_URL=https://YOUR-PROJECT.supabase.co
DATABASE_URL=postgresql://YOUR-USER:YOUR-ENCODED-PASSWORD@YOUR-SESSION-POOLER:5432/postgres?sslmode=verify-full&sslrootcert=certs/prod-ca-2021.crt
```

Use the Supabase session pooler on port 5432 or a direct database connection.
Transaction pooling is unsuitable for the dedicated PostgreSQL LISTEN connection.
Keep certificate and hostname verification enabled.
The `PGSSLMODE=disable` line in the example file is for local PostgreSQL only and must not be copied into a production TLS setup.

The existing local `relay/.env` contains the actual private configuration and is ignored by Git.
No secret values are provided in this handoff or the repository.
If Claude runs on another machine, those local environment files will not be present after cloning.
Use the owner's private deployment configuration or obtain missing credentials through an appropriate private channel.
Do not ask the user to paste passwords or service-role keys into chat again.
Credentials were shared earlier in the conversation, so rotation remains a release requirement.

### Companion and TV builds

Set these publisher variables before rebuilding the phone app:

```dotenv
EXPO_PUBLIC_RELAY_URL=https://clean-tv-chi.vercel.app
EXPO_PUBLIC_SUPABASE_URL=https://YOUR-PROJECT.supabase.co
EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY=YOUR-PUBLISHABLE-KEY
```

The current local `companion/.env` has the Supabase URL and publishable key, but `EXPO_PUBLIC_RELAY_URL` is blank as of this handoff.
The existing distributed previews therefore do not contain the production relay origin.
The three `EXPO_PUBLIC_` values are client configuration; never put database passwords or service-role keys there.
Changing a Vercel server variable does not change an already-built APK or IPA.
Expo public variables must be supplied in the local or EAS build environment and embedded in a new build.

TV relay-origin precedence is:

1. Gradle property `relayUrl`.
2. Environment variable `TVHOME_RELAY_URL`.
3. Environment variable `EXPO_PUBLIC_RELAY_URL`.
4. Blank origin, which leaves online control unavailable.

The TV requires an HTTPS origin without a page path, credentials, query string, or fragment.

## 6. Important deployment fixes already made

### Rejected Fastify function override

Commit `66a4bb7` fixed the Vercel CLI 64.7.0 error:

```text
[unused_function] The pattern "src/server.ts" defined in functions doesn't match any Serverless Functions inside the api directory.
```

The failure was reproduced with the same CLI's build detector.
Removing the `functions` override preserved Fastify detection and allowed the actual Fastify builder to pass.
Do not restore that override or move the existing backend into an `api/` wrapper merely to silence the error.
The current `vercel.json` selects Fastify, `npm run build`, and region `hnd1`.
The server exports `config = { maxDuration: 300 }`.
An explicit module-relative certificate read keeps the public CA in the traced function bundle.

### Database 503 despite environment variables being configured

Commit `18dcbef` added safe temporary logging after the website returned `{"error":"Database is temporarily unavailable."}`.
The user supplied the runtime entry showing `code: "ENOENT"`, with both TLS mode and CA file configured.
The supplied database connection and required routing tables worked from the developer machine.
Starting the backend from a different working directory reproduced the exact HTTP 503 and `ENOENT` failure.

Commit `b6b1033` fixed certificate resolution through `databaseConnectionString()`.
Relative `sslrootcert` paths now resolve from the relay application directory instead of `process.cwd()`.
The normalized connection string is used by the HTTP pool, the broker's notification listener, and the migration runner.
Absolute certificate paths remain absolute, and passwords and TLS verification are preserved.

After the fix, the changed-directory HTTP reproduction returned 200 for the homepage and health endpoint.
The PostgreSQL connection's TLS authorization and the separate notification listener were verified.
Vercel's actual Fastify builder included the new resolver and CA.
The production homepage and health endpoint then returned 200 and have been rechecked for this handoff.

### Temporary diagnostics must remain until authorized for removal

- `relay/package.json` prints `[DEBUG-vercel-entry]` during the build.
- `relay/src/server.ts` logs `[DEBUG-db-connect]` on cleanup failure, using sanitized error codes and configuration-presence flags.

The user has not yet explicitly confirmed that the fix is working for them and authorized removing these logs.
Do not remove them merely because automated checks passed.
Ask the user to confirm and authorize removal, then remove only the temporary instrumentation and its obsolete documentation references.
Never replace safe logging with raw database URLs or credential-bearing error dumps.

## 7. Database and protocol

Both migrations have already been applied to the supplied Supabase project.
Do not manually rerun the initial SQL file against the existing database.

| Migration | Tables |
| --- | --- |
| `001_devices.sql` | `devices`, `pairing_codes`, `pairing_requests`, `schema_migrations`. |
| `002_relay_routing.sql` | `relay_connections`, `relay_commands`, `relay_rate_limits`. |

The tables are server-only, with RLS and client-role access restrictions defined by the migrations.
Authentication through Supabase does not grant the phone direct table access.
The phone calls the owner-authorized relay API instead.
The migration runner takes an advisory lock and records each applied migration.
For a new database or a future migration, configure `relay/.env` and run `npm run migrate` from `relay/`.
Schema changes require a migration file and execution instructions.

The phone uses `Authorization: Bearer <Supabase access token>`.
The TV uses `Authorization: Device <random secret>`.
Do not move credentials into query strings.

Pairing codes contain ten hexadecimal characters and expire after five minutes.
Approval requests expire after two minutes and require explicit acceptance on the TV.
The phone polls pairing status and can cancel a pending request.
Ownership checks apply to device listing, state, command, and deletion operations.

Connection leases last forty seconds and are renewed by a fifteen-second heartbeat.
Commands are bound to one connection generation, claimed once, and given a fourteen-second delivery deadline.
The HTTP request waits up to roughly fifteen seconds for a reply.
The phone uses a twenty-two-second HTTP timeout.
The broker limits active reply-bearing commands per device and stores results in PostgreSQL so different instances can coordinate.
Expired or claimed commands must not be replayed onto a new connection.
PostgreSQL notification payloads contain device IDs rather than wallpaper contents.
Revocation must close the TV connection even when another instance handles the revocation request.

Supported command types are `getState`, `settings`, `wallpaper`, `launchApp`, and `navigate`.
Both relay and TV validate incoming data, and the TV acknowledges after applying a command.
Preserve size, image-decoding, command-expiry, and ownership protections when extending functionality.
See `docs/protocol.md` and the implementation for exact payloads and limits.

## 8. Builds and checks

### Toolchain

Use Node.js 22 or newer as required by the current project.
The companion includes `.nvmrc` with Node 22, and previous successful checks also used the available Homebrew Node runtime.
Use JDK 17 and Android SDK 36 for the TV build.
The TV Gradle wrapper is included and selects Gradle 8.14.3.

Previously successful locations on this machine were:

```sh
export PATH=/opt/homebrew/bin:$PATH
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.20/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
```

Verify these paths before using them on another machine.
An older shell configuration pointed `ANDROID_HOME` at a nonexistent SDK, so do not assume the inherited value is valid.

### Relay

From `relay/`:

```sh
npm ci
npm run build
```

For local operation with a configured private `.env`:

```sh
npm start
```

Vercel uses Fastify detection and the exported HTTP server; do not add a second unconditional listening process for hosted execution.
The Vercel CLI was installed during diagnosis, but its local account was logged out.
Do not assume this machine can read private Vercel runtime logs without the owner's access.

### Android TV

From `tv/`:

```sh
TVHOME_RELAY_URL=https://clean-tv-chi.vercel.app ./gradlew :app:assembleDebug :app:lintDebug
```

The debug APK is produced at `tv/app/build/outputs/apk/debug/app-debug.apk`.
Use a final application ID and permanent signing identity for public release rather than distributing development signing indefinitely.

### Companion

Set the three publisher environment variables first.
From `companion/`:

```sh
npm ci
npm run typecheck
npm run lint
npx expo export --platform ios --platform android
```

For a local Android APK with the SDK/JDK available:

```sh
npx expo prebuild --platform android
cd android
./gradlew :app:assembleRelease
```

The generated APK is under `companion/android/app/build/outputs/apk/release/`.
An `assembleRelease` task name does not prove production signing; the delivered phone preview used a development key.
Configure and verify release signing before public distribution.
Generated `companion/android/` and `companion/ios/` directories are ignored and must not be edited manually or committed.
Make native configuration changes in `app.json` or appropriate config plugins, then regenerate with Expo.

The included EAS preview profile builds an internally distributed Android APK.
EAS and public iPhone distribution still require the owner's Expo/Apple setup and appropriate signing.
There is no signed iPhone IPA in the handoff.

### Verified versus unverified

Previous checks passed for TV compilation/lint, companion lint/types, Android/iOS JavaScript exports, standalone Android phone compilation, APK signatures, and Android phone 16 KB alignment.
The phone interface was inspected in Expo Go on a dedicated iPhone simulator, not as a signed production iPhone application.
Independent-process protocol simulations verified command replies, pairing approval, expiry, timeout, shared quotas, and cross-instance revocation using disposable records.
Those simulations did not create a Supabase Auth account or use a physical TV.
The live deployment's successful health check is not evidence that full account sign-in, hardware pairing, or away-from-home control has been verified.

## 9. Artifacts and known release limits

The repository includes:

- `relay/public/downloads/tv-home-preview.apk`, approximately 4.2 MiB.
- `relay/public/downloads/tv-home-companion-preview.apk`, approximately 94 MiB.

Both APKs are development-signed previews with the earlier build configuration.
Rebuild them with the production relay origin before treating them as connected previews.
Verify signing and installation/update behavior before replacing public download files.
GitHub accepted the phone APK but warned that it exceeds the recommended 50 MB file size.
Use an appropriate release-artifact hosting strategy if repeated APK commits become a maintenance problem.

Additional local handoff outputs live in the parent `outputs/` directory, including APK copies, a screenshot, a SHA-256 manifest, and `tv-home-source.zip`.
That ZIP predates the later Vercel and certificate-path fixes and must not be used as the current implementation.
Use the latest Git checkout as the source of truth, and regenerate release archives/manifests after future packaging changes.
The parent `supabase-setup.sql` is the original initial setup, not a replacement for the full migration directory.

The previous companion audit reported eighteen high-severity dependency-chain entries associated with upstream `braces` and `node-forge` advisories.
That is a historical audit result, not a current vulnerability census.
Recheck before public release and preserve the SDK-compatible dependency strategy.
The relay production dependency audit previously reported no advisories.

The companion intentionally includes `expo-font` pinned to SDK 57 compatibility.
An unpinned optional peer previously selected SDK 58 and broke Android autolinking.
Keep `vendor/safe-decoder` and its package override together until the upstream Expo Router dependency is updated compatibly.
The adapter uses the patched upstream decoder rather than a custom URI-decoding implementation.
See `docs/dependency-notes.md` before changing these overrides.

## 10. Recommended next work

1. Read this handoff, `README.md`, `VERCEL.md`, `docs/protocol.md`, and `companion/AGENTS.md`, then inspect the current Git status before editing.
2. Confirm whether the user sees the working production website and authorizes temporary-log removal.
3. Configure `EXPO_PUBLIC_RELAY_URL` and the TV build origin with the known production URL, then build new connected Android previews.
4. Verify Supabase sign-up, email confirmation, sign-in, saved sessions, token refresh, and ownership rejection using an authorized account.
5. Verify a real TV and phone can pair with explicit TV approval, persist state, change scenes, upload a wallpaper, and revoke access.
6. Verify reconnect, expiry, cancellation, an offline TV, and the same supported controls while the phone uses mobile data.
7. Verify focus, Home-button selection, service retention, reboot behavior, app-launch restrictions, and photo rendering across a named set of TV models.
8. Finish production signing and supported distribution, including the iPhone build, and review account recovery/deletion and dependency advisories before public launch.

This order is a recommendation for completing the confirmed core flow, not authorization to invent additional product features.
A production domain or new customer address prompt is not needed to begin because a stable Vercel URL already exists.
Do not claim universal television compatibility or advertise hardware capabilities that have not been verified.

## 11. User working instructions to carry forward

- Do not open or automate a browser; use files, CLI, HTTP requests, and available approved non-browser tools where appropriate.
- Do not write test cases unless the user explicitly requests them; use compiler/lint checks and targeted runtime verification.
- For a reported bug, first reproduce the user-visible failure as closely as possible, inspect callers, and fix the shared cause.
- Add temporary, credential-safe diagnostic logs while debugging, ask the user to reproduce, and offer either pasting the logs or confirming that the code is fixed.
- Keep those diagnostics until the user confirms the fix and authorizes their removal.
- Always include an option to explain a clarification question in simple words.
- Do not assume unknown requirements; inspect available evidence or ask for clarification.
- Use Graphify only if an existing `graphify-out` directory is present, and do not generate or update its graph.
- Do not manually edit generated files or `CHANGELOG.md`.
- Keep each full sentence on its own line in long Markdown documents.
- Use plain hyphens instead of em dashes, and do not add an agent co-author to commits.
- Add a TL;DR at the end of plans and code-change explanations.
- Prefer quality, simplicity, robustness, and maintainability over minimizing development effort.
- Do not expose credentials, disable TLS verification, restore customer server-address inputs, or repeat the previously declined Supabase-integration suggestion.
- Do not create or message a separate agent/thread without the user's authorization.

## TL;DR

The backend is live and healthy, and the Vercel build and certificate-path bugs are fixed.
The native app source and two Android previews exist, but the previews need the production relay URL baked into new builds.
Continue with real account, pairing, wallpaper, reconnection, and mobile-data verification, then production signing and iPhone distribution.
Temporary logs must stay until the user authorizes removal, secrets stay outside Git, and physical-TV compatibility remains unverified.
