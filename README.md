# TV Home

A native Android TV / Google TV launcher, an Android and iPhone companion, and an authenticated relay for control at home or away.
This is a developer preview, not a production release or a promise of compatibility with every TV model.
See `HANDOFF.md` for app installation and `VERCEL.md` for the prepared Vercel deployment.
See `CLAUDE-HANDOFF.md` for the current continuation handoff, live deployment status, remaining work, and user constraints.

## What is implemented

- Native Kotlin TV home screen with remote focus navigation and installed-app discovery.
- Four atmospheric scenes and personal photo wallpapers.
- Favorite apps, dock ordering, room names, dimming, and a 12/24-hour clock.
- An optional ambient clock screensaver.
- Android and iPhone companion source using Expo SDK 57 and Expo Router.
- Account sign-in and registration through a configured Supabase Auth project.
- Expiring pairing codes with explicit account approval on the TV.
- Multiple TVs per account and account access revocation.
- A relay that forwards commands over the TV's outbound WebSocket connection.
- Success responses only after the TV acknowledges the change.
- A download page with Android TV and Android phone preview APKs.
- An account-free design preview in the companion, clearly identified as a preview.

The launcher does not disable system packages or change the original launcher automatically.
The public name and application identifiers are provisional.

## Project structure

| Folder | Purpose |
| --- | --- |
| `tv/` | Native Kotlin launcher and companion connection service. |
| `companion/` | Expo application for Android phones and iPhones. |
| `relay/` | TypeScript service, PostgreSQL migration, and download page. |
| `docs/` | Product scope, protocol, and verification notes. |

## Try the TV preview

Use the `tv-home-preview.apk` delivered alongside this project.
It is signed with a development key and intended for evaluation.
Android TV / Google TV running Android 8 or newer is the implementation target.
No real TV model has been verified yet.

Install using your TV's supported APK installation method.
If you already have an authorized ADB connection to your own TV, this installs the application without clearing its data:

```sh
adb install -r tv-home-preview.apk
adb shell am start -n app.tvhome.launcher/.MainActivity
```

Choose favorite apps, switch scenes, and use the physical TV remote.
The TV works without a configured relay for these local features.
Phone control requires the service configuration below.
The Home button's behavior depends on the TV firmware and whether the TV permits choosing a default home app.
Use Settings > Default home screen to open the platform's picker where available.
Choose your original home app there to return to it.
If the firmware does not provide this picker, the preview can still be opened from the TV's app list.

## Try the companion's design preview

Requires Node.js 22 or newer, including the Node requirement of the Supabase client.
The companion includes an `.nvmrc` selecting Node 22.

```sh
cd companion
npm ci
npx expo start
```

Open the project in an SDK-compatible Expo Go installation on your phone or use a development build.
Choose **Explore the design**.
Scene, dimming, and favorite-app edits in this mode affect only the preview.
Remote buttons stay disabled until a real TV is paired and reachable.

## Configure real account and remote control

Use your Supabase project for authentication and enable email/password sign-in.
Use asymmetric JWT signing keys, ES256 or RS256, because the relay verifies the project's published JWKS.
Keep email confirmation enabled and configure production email delivery before public registration.
The app supports returning to sign-in after confirming the email.
Password recovery and account deletion flows are not included in this preview.
Your provided project's public authentication configuration has been verified and saved in the local companion environment.
The service-role key was not saved or placed in a phone application.
Local environment files are excluded from the source archive.
The initial migration was applied through Supabase's SQL editor, and the shared-routing migration has also been applied.
The initial four tables were checked through the public data API and deny anon access.
The relay's private PostgreSQL connection is configured locally and has been verified over TLS.

Provide a PostgreSQL database to the relay.
The Supabase project's PostgreSQL database can be used.
Use a server-side database connection role that owns the application tables or has the required access and can bypass their row-level security.
Never place that database connection string or a service-role key in the companion app.

```sh
cd relay
npm ci
cp -n .env.example .env
```

Edit `.env` with your database connection and Supabase project URL.
For a remote database, use authenticated TLS with a trusted certificate; do not use `PGSSLMODE=disable`.
The example's disabled database TLS is for a local PostgreSQL instance only.
For a Supabase connection, include `sslmode=verify-full&sslrootcert=certs/prod-ca-2021.crt` in the URI query.
The bundled public root certificate matches Supabase Studio's official download configuration and is included in the Docker image.
See `relay/certs/README.md` for certificate provenance and renewal details.

Run the migration before starting the service:

```sh
npm run migrate
npm run build
npm start
```

`relay/migrations/001_devices.sql` creates the device ownership, pairing-code, pairing-request, and migration-record tables.
It enables row-level security and removes client-role access so these records are not exposed through Supabase's public data API.
`relay/migrations/002_relay_routing.sql` adds protected connection leases, expiring commands, and rate counters for multiple relay instances.
The migration runner uses a database lock and records each migration so later runs do not apply it twice.
It does not change existing TV settings or application data.

Put the relay behind an HTTPS reverse proxy with WebSocket support.
Keep its default loopback binding when using a proxy on the same host.
Set `TRUST_PROXY=true` only when that trusted proxy is the only route to the service and overwrites incoming forwarding headers.
Limit request sizes, connection counts, and anonymous registration at the proxy.
The TV opens an outbound connection; no user router port forwarding is needed.
The included Dockerfile is an alternative packaging option and has not been built in this workspace.

Configure the companion:

```sh
cd companion
cp -n .env.example .env
```

Set `EXPO_PUBLIC_RELAY_URL` to the HTTPS relay origin.
Set `EXPO_PUBLIC_SUPABASE_URL` and `EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY` to the same Supabase project.
Only the publishable key belongs in the phone application.
Restart Expo after changing these variables.

The publisher sets the relay origin through build environment variables before distributing either app.
Ordinary users do not enter server addresses.
On the TV, choose **Connect phone** to display its pairing code.
In the phone app, sign in, choose **Add TV**, and enter the TV's ten-character pairing code.
Approve the displayed account email on the TV.
The code expires after five minutes and account approval expires after two minutes.

## Build and distribute

Build the TV application with JDK 17 and Android SDK 36:

```sh
cd tv
./gradlew :app:assembleDebug
```

Set the relay address through the build environment:

```sh
TVHOME_RELAY_URL=https://YOUR-RELAY-DOMAIN ./gradlew :app:assembleDebug
```

Use a permanent signing key, a final application identifier, and a signed release build before distributing a production TV app.
Keep that key private and backed up so future updates can use the same identity.
The generated Gradle wrapper is included.

The standalone Android phone preview is included as `tv-home-companion-preview.apk` alongside this project.
See `VERCEL.md` for local Android packaging after configuring your production origin.
For cloud companion builds, use the included EAS profiles after connecting your own Expo project and signing accounts:

```sh
cd companion
npx eas-cli@latest build --profile preview --platform android
npx eas-cli@latest build --profile production --platform ios
```

These commands upload source to Expo's build service and may require your Expo and Apple developer accounts.
They have not been run in this workspace.
For public iPhone distribution, use the appropriate Apple distribution channel rather than offering an Android APK to iPhone users.

The relay serves its download page at `/`.
Both evaluated Android APKs are included in `relay/public/downloads/` and labeled as development builds.
Replace them with configured production releases and update the page's wording only after release verification.

## Current limits

- Device compatibility is targeted by OS, with no real-TV compatibility matrix yet.
- The phone remote navigates TV Home; it does not inject keys into other applications.
- Launching apps from the phone requires TV Home to be visible because Android restricts background activity launches.
- Power, volume, system-wide keyboard input, and universal HDMI switching are not implemented.
- An offline or sleeping TV cannot be assumed to accept a command or wake itself.
- The foreground phone-control service is enabled by the user and can be paused in TV settings.
- Restart behavior and background service retention still need verification on manufacturer firmware.
- Profiles, scheduled wallpaper collections, video backgrounds, widgets, and additional TV operating systems are not implemented.
- Shared connection leases, expiring command delivery, replies, and rate limits use PostgreSQL across relay instances.
- The Vercel deployment is live at https://clean-tv-chi.vercel.app/ with verified homepage and database health; production TV socket behavior and capacity verification remain outstanding.
- Relay operators terminate TLS and can process wallpaper content; this preview does not implement end-to-end encryption.
- Existing preview APKs need rebuilding with the deployed relay origin; production signing and any custom domain setup remain separate release work.
- Upstream Expo build-tool advisories remain under review; see `docs/dependency-notes.md`.

## Checks

```sh
cd companion
npm run typecheck
npm run lint
npx expo export --platform ios --platform android
```

```sh
cd relay
npm run build
```

No test cases were added, following the user's instruction.
See `docs/verification.md` for checks actually performed and remaining verification.

## TL;DR

The first preview contains the TV launcher, both phone targets, and the pairing/remote-control service source.
Use the design preview immediately; configure hosting and accounts before real remote control, and verify real TV models before public release.
