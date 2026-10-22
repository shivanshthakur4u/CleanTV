# TV Home preview handoff

## Files

- `tv-home-preview.apk`: Android TV / Google TV launcher preview.
- `tv-home-companion-preview.apk`: standalone Android phone preview.
- `tv-home-source.zip`: the TV app, Android/iPhone companion source, relay, SQL migrations, website, and deployment instructions.

The APKs use development signing keys and have not been verified on physical devices.
They have no public relay origin baked in, so the TV's local features and the phone's design preview are available immediately.
Online control requires deployment and a build using the publisher's production relay origin.
No signed iPhone package is included; iPhone signing and distribution need your Apple setup.

## Host and build

Follow `VERCEL.md` to deploy the relay with the included Vercel configuration.
Supabase holds account data and server-only routing tables so multiple relay instances can coordinate.
The private database configuration is in the local `relay/.env`, outside the source archive.
Rotate shared credentials before public launch.
Set the service origin once through publisher build environment variables, then rebuild both apps.
Ordinary users do not enter server addresses.

## Customer setup after publishing configured apps

1. Install the TV app and phone companion.
2. Sign in or create an account on the phone and confirm its email if prompted.
3. Select **Connect phone** on the TV and **Add TV** on the phone.
4. Enter the TV's pairing code on the phone and approve the account on the TV.
5. Choose a wallpaper or scene and verify that the TV updates.

The remote operates TV Home; universal power, volume, and control inside other apps are not implemented.
Physical device behavior and control over mobile data still need verification.

## TL;DR

The two Android preview APKs and Vercel deployment source are included.
Publish a configured and signed build after deploying the backend and verifying real hardware.
