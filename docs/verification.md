# Preview verification

Recorded during developer-preview preparation and handoff.
This records checks performed on the developer preview; it does not certify public release readiness.

| Check | Result |
| --- | --- |
| Native TV `assembleDebug` | Passed with JDK 17 and Android SDK 36. |
| Native TV `lintDebug` | Passed; nonfatal dependency-version, localization, and resource warnings remain. |
| Android phone `assembleRelease` | Passed with all four configured ABIs; uses a development signing key. |
| APK signing verification | Both delivered Android APKs passed APK Signature Scheme v2 verification. |
| Android phone APK alignment | Passed the 16 KB page-alignment check. |
| Android phone permissions | Final APK excludes camera, microphone, and system-overlay permissions. |
| Companion TypeScript | Passed. |
| Companion ESLint | Passed. |
| Android JavaScript/Hermes export | Passed. |
| iPhone JavaScript/Hermes export | Passed. |
| Expo SDK dependency compatibility | Passed after pinning compatible optional peers. |
| Clean mobile dependency installation | Passed in a separate temporary directory and the final source directory using npm-generated lock data. |
| iPhone interface | Inspected in Expo Go on a dedicated iPhone simulator. |
| Scene selection | Selected Quiet dusk in the companion's design preview and verified its selected state. |
| Customer connection setup | Address inputs removed from both apps; publisher build environment variables supply the relay origin. |
| Relay TypeScript build | Passed. |
| Download-page HTTP response | HTTP 200 on the loopback verification service. |
| APK downloads | Both final APKs returned HTTP 200; downloaded bytes have the same SHA-256 as the delivered files. |
| Unauthenticated device API | HTTP 401. |
| Migration SQL | Applied in a temporary PGlite PostgreSQL engine. |
| Migration row-level security flags | Enabled on devices, pairing_codes, and pairing_requests. |
| Relay production dependency audit | No reported advisories at verification time. |
| Provided Supabase project | Public Auth settings and JWKS endpoints returned HTTP 200; ES256 keys, email signup, and email confirmation were verified. |
| Hosted database migration | Project owner reported running the provided SQL file successfully. |
| Hosted table access restrictions | Public data API recognizes devices, pairing_codes, pairing_requests, and schema_migrations; anon requests receive HTTP 401 with PostgreSQL permission-denied code 42501. |
| Authenticated PostgreSQL connection | Passed using the provided Supabase session pooler, certificate and hostname verification, and TLS 1.3. |
| Hosted migration record | Confirmed `001_devices.sql` is recorded in schema_migrations. |
| Hosted row-level security and grants | All four tables have row-level security enabled; anon and authenticated roles have no SELECT grant on them. |
| Hosted migration runner | Reported that the database is up to date; no new schema migration was applied. |
| Relay health with live database | HTTP 200 with status ok using the configured Supabase connection; unauthenticated device requests still return HTTP 401. |
| Routing migration | Applied `002_relay_routing.sql` to the supplied Supabase project. |
| Independent-process command routing | A simulated TV connected to one relay acknowledged a command submitted through an independent process. |
| Shared pairing approval | Simulated TV approval updated protected pairing and ownership records. |
| Command expiry and timeout | An expired command was not delivered; an unacknowledged command returned status 504. |
| Shared quotas | Independent database clients incremented the same counter from 1 to 2. |
| Cross-instance revocation | Another relay disconnected the temporary TV with HTTP 200; its socket closed with code 4001. |
| Bodyless phone requests | A bodyless DELETE reached authentication and returned HTTP 401. |
| Vercel-mode export | The exported HTTP server returned status ok from its local health endpoint. |
| Vercel configuration | Matched published configuration property rules. |
| Vercel CLI 64.7.0 build failure | Reproduced the exact `unused_function` error with the CLI's build detector; removing the Fastify function override cleared it. |
| Vercel Fastify function build | The CLI's actual Fastify builder passed with the corrected configuration, a 300-second duration, the database CA, and both APK download assets. |
| Socket failure handling | Invalid device credentials returned HTTP 401; a simulated database outage returned HTTP 503. |
| Certificate lookup from another working directory | Reproduced HTTP 503 with `ENOENT` before the fix; after resolving the root certificate from the application directory, the homepage and health endpoint returned HTTP 200. |
| Database TLS after certificate-path fix | Verified the TLS connection and the separate PostgreSQL notification listener from another working directory with certificate validation enabled. |

The final mobile dependency audit reported 18 high-severity entries arising from the two remaining upstream `braces` and `node-forge` advisories and their dependency chains.
The URI decoder and UUID advisories were removed by the documented compatibility updates.

The PostgreSQL engine check verified migration syntax, the migration record, and the row-level security flags.
The subsequent hosted checks verified the Supabase project's table grants, row-level security flags, migration record, and authenticated PostgreSQL connection.
An initial local HTTP check used an unconfigured account-service origin.
A later check used the real Supabase configuration and confirmed database-backed relay health without authorizing a real account.
The later Supabase checks read public project configuration and checked table access restrictions after the project owner reported applying the migration.
They did not create an account or inspect user records.
The later protocol simulation used disposable device and pairing records and removed them afterwards.
It did not create a Supabase Auth account or use a physical TV.

## Outstanding verification

- Real Supabase sign-up, confirmation, sign-in, token refresh, and ownership authorization.
- Pairing a real TV and phone, including approval, expiry, cancellation, and revocation.
- Photo upload and rendering on real TV hardware.
- Control with the phone away from the home network.
- Launcher focus, Home-button behavior, HDMI shortcuts, reboot behavior, and background service retention across TV manufacturers.
- Production iPhone build and permanent release signing identities.
- A public Vercel deployment, installed apps configured with its production origin, and saved account sessions.
- Production HTTPS/WebSocket hosting, deployment-time database connections, capacity, and operational configuration.
- Remaining upstream dependency advisories documented in `dependency-notes.md`.

No test cases were added.
No browser was opened.
The exported screenshot shows the companion's design preview in Expo Go, including its development overlay; it does not show a connected TV or a standalone production iPhone build.

## TL;DR

The code compiles, both Android preview APKs are packaged, and the companion interface has been inspected.
The relay connects to Supabase over verified TLS and its local health endpoint passes.
Real account, TV, and internet-control verification must happen before public release.
