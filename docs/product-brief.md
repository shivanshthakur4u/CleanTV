# TV Home - product brief

## Confirmed requirements

- Build a public product inspired by CleanYourTV and ZeroPulse TV.
- Give the TV home screen a cleaner appearance and more advanced features.
- Provide a companion app for Android phones and iPhones.
- Let the companion app change the TV wallpaper and customize the TV experience.
- Support control on the home network and while the owner is away from home.
- Provide a public website through which users can find and install the product.
- Aim for broad TV compatibility rather than a launcher customized for one owner's TV.

## Confirmed release scope

The user selected all Android TV / Google TV brands for the first release, with expansion to other operating systems later.
This is an OS coverage target; individual TV model compatibility still needs device verification.
Android TV, Samsung Tizen, LG webOS, Roku OS, and Apple tvOS require different applications and installation processes.
Public release support must name the operating systems, versions, and capabilities actually verified.

## Proposed experience

The TV opens to an atmospheric background, a compact clock, favorite apps, and a clear focus indicator for the physical remote.
Additional controls appear on demand so the home screen remains uncluttered.
The phone provides wallpaper previews, photo uploads, app arrangement, saved scenes, and a TV selector.
Users explicitly pair each TV and can revoke a phone's access.

### Proposed first vertical slice

1. Install and open the TV app.
2. Pair the TV with the owner's phone using a short-lived code or QR code and confirmation on the TV.
3. Select a wallpaper or upload a photo from the phone.
4. Apply it to the TV and receive confirmation from the TV.
5. Change the background, scene, and favorite-app order without rebuilding the TV application.
6. Reconnect after restarting either app and retain the saved appearance.
7. Repeat the supported controls while the phone is away from the home network.
8. Revoke access from the TV or the owner's account.

## Capabilities to distinguish

| Capability | Product meaning |
| --- | --- |
| Launcher wallpaper | The background shown within our home screen. |
| Ambient display | A separate idle-screen experience, where the platform permits it. |
| App launch | Opening an installed application through a supported platform API. |
| Full remote control | Navigation, text input, volume, and power outside our application, subject to platform support. |
| Home replacement | Using our application when the physical Home button is pressed, subject to the OS and manufacturer. |
| Away-from-home control | Authenticated control through an online service while the TV is powered, connected, and reachable. |

These capabilities must not be advertised as equivalent or universally available.
Commands must report whether the TV acknowledged completion rather than displaying success when a command is merely queued.
Offline or sleeping TVs must show an accurate status.

## Connectivity requirements

Away-from-home control requires an internet-accessible service or another supported remote-access mechanism.
The TV should connect outward to the service so the user does not need router port forwarding.
The service must authenticate the owner, authorize access to each TV, expire pairing attempts, and support access revocation.
Network commands must use encrypted transport and validate all incoming data.
Wallpaper transfers must enforce image type, file size, decoded dimensions, and storage limits.
Sensitive commands must not remain queued indefinitely and execute unexpectedly after reconnection.

## Distribution requirements

The website must show installation choices based on the user's TV operating system.
Android / Google TV can use a signed APK and an installation guide, subject to the device's installation policies.
Other TV platforms need their own distribution paths and cannot install that APK.
Android phone and iPhone distribution also need platform-appropriate delivery.
Production signing identities, hosting, store accounts, and the public domain are not configured in this workspace.

## Verification constraints

Do not add test cases unless the user explicitly requests them.
Compiler checks, existing checks, and manual device verification can still be used.
Do not open a browser.
Do not claim device support, successful pairing, or working remote commands without verification.
There is currently no connected TV or phone identified for hardware verification.

## Reference sources

- [CleanYourTV source and feature documentation](https://github.com/Gathin23/cleanyourtv)
- [CleanYourTV theme and wallpaper documentation](https://github.com/Gathin23/cleanyourtv/blob/main/themes/README.md)
- [ZeroPulse TV 2.0 creator's description](https://www.reddit.com/user/Zeropulse00/comments/1xaxbyu/i_rebuilt_my_tcl_google_tv_home_screen_with/)
- [Android TV app setup](https://developer.android.com/training/tv/get-started/create)
- [Samsung TV application distribution](https://developer.samsung.com/tv-seller-office/guides/applications/distributing-application.html)
- [LG webOS app distribution](https://webostv.developer.lge.com/distribute/app-ecosystem)
- [Roku application distribution](https://developer.roku.com/dev/docs/channel-store)

## TL;DR

Build a polished TV experience with Android and iPhone control, including control away from home.
Target Android TV / Google TV first, then verify the complete pairing-to-wallpaper flow and individual TV models before public release.
