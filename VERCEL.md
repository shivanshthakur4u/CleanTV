# Deploy TV Home on Vercel

The relay is prepared for Vercel with shared PostgreSQL routing, connection leases, command acknowledgments, and shared rate limits.
The exported server and cross-instance protocol were checked locally.
A public Vercel deployment, production account flow, and physical TV remain unverified.

## 1. Prepare the database

Both migrations have already been applied to the provided Supabase project.
For a fresh database, configure the private `relay/.env` and run this from `relay/`:

```sh
npm ci
npm run migrate
```

The runner applies `001_devices.sql` and `002_relay_routing.sql` once each.
The second migration adds server-only connection leases, expiring commands, and rate counters.
Use the Supabase session pooler on port 5432 or a direct connection.
Transaction pooling is unsuitable because socket instances use PostgreSQL LISTEN.

## 2. Configure Vercel

Import the project into Vercel and set its Root Directory to `relay` within this project.
If the repository contains this entire folder under another directory, use the full repository-relative path ending in `tv-home/relay`.
The included `relay/vercel.json` selects Fastify, its build command, Tokyo execution near the supplied database, certificate/static assets, and a 300-second invocation limit.
Keep Fluid compute enabled for WebSocket support.

Set these server environment variables before deployment:

| Variable | Value |
| --- | --- |
| `SUPABASE_URL` | Your Supabase project's HTTPS URL. |
| `DATABASE_URL` | The private PostgreSQL URI, including the encoded password and `sslmode=verify-full&sslrootcert=certs/prod-ca-2021.crt` query parameters. |

The local `relay/.env` contains these values and is excluded from source archives and Vercel uploads.
No Supabase service-role key is needed by the relay or either app.
Keep database credentials in server secrets and rotate credentials shared in chat before public launch.
Do not use disabled database TLS from the local-only example configuration.

Deploy and use the stable production HTTPS address.
Use a production URL that app users can reach without Vercel team authentication.
The app itself still requires Supabase authorization for device operations.
Check `/health` for `{"status":"ok"}` and check both APK download links.
APK files belong in `public/downloads/` so Vercel can serve them as static downloads.

## 3. Configure the apps once, as publisher

Set these values in `companion/.env` before building the phone app:

```dotenv
EXPO_PUBLIC_RELAY_URL=https://YOUR-PRODUCTION-PROJECT.vercel.app
EXPO_PUBLIC_SUPABASE_URL=https://YOUR-PROJECT.supabase.co
EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY=YOUR-PUBLISHABLE-KEY
```

The relay origin and publishable key are public client configuration.
Never put the database password or service-role key in an `EXPO_PUBLIC_` variable.
The supplied phone preview has no production relay origin baked in and currently provides the design preview.
Build the connected version after setting the real origin.
Editing Vercel server variables does not change an already-installed phone binary.

For the TV build, set `TVHOME_RELAY_URL` to the same HTTPS origin before running Gradle.
The TV build also accepts exported `EXPO_PUBLIC_RELAY_URL`, so it can use the same publisher configuration.
This command reads the phone environment file without executing it as shell code:

```sh
cd tv
node --env-file=../companion/.env -e "require('node:child_process').execFileSync('./gradlew', [':app:assembleDebug'], {stdio: 'inherit'})"
```

For local phone packaging with the Android SDK and JDK installed:

```sh
cd companion
npm ci
npx expo prebuild --platform android
cd android
./gradlew :app:assembleRelease
```

Alternatively use the included Expo EAS profiles after setting the three public variables in your EAS build environment.
Production signing keys and final application identifiers still need your setup before public release.
The provided APKs use development signing keys.

## 4. Verify the customer flow

Users install the configured apps, sign in on the phone, enter the code shown by the TV, and approve pairing on the TV.
Neither app asks users for a hosting address.
Verify a wallpaper change, revocation, TV reconnection, and phone control over mobile data on real hardware.
Vercel can end a socket at its invocation limit; the TV reconnects with backoff and renews its lease.
Expired or previously claimed commands are not replayed onto a new connection.
Capacity, database quotas, production email delivery, and remaining upstream mobile-tool advisories require release review.

## TL;DR

Deploy `relay/` with its two private server settings, then build both apps using the stable production origin from publisher environment variables.
Users only sign in and pair; no address entry is part of their setup.
