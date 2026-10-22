# Dependency notes

## Expo SDK compatibility

The companion uses Expo SDK 57, React 19.2.3, and the React Native version selected by that SDK.
React DOM, Reanimated, and Worklets are pinned to Expo's compatible versions so npm does not select incompatible optional peers.
React DOM is an Expo Router peer; this project does not provide a browser companion.
Expo Font is explicitly pinned to the SDK-compatible version because an unpinned peer selected SDK 58 and failed the SDK 57 Android autolinking configuration.
Expo System UI supplies the native dark appearance declared by the application configuration.

## Published security fixes

Expo Router 57 uses the CommonJS API of `query-string` 7.
Its older URI-decoder dependency is affected by [CVE-2026-45822](https://github.com/advisories/GHSA-vcc3-ghjq-m6fr).
The project overrides that dependency with `vendor/safe-decoder`, a CommonJS export adapter around the upstream patched `decode-uri-component` 0.5.0 package.
The adapter contains no decoding implementation of its own.
Keep it until a compatible Expo Router release includes the patched decoder, then remove the override and adapter together.

The Xcode project parser's UUID dependency is overridden to 11.1.1, which includes the published [UUID bounds-check fix](https://github.com/advisories/GHSA-w5hq-g745-h8pq).

## Remaining upstream advisories

The Expo build toolchain also includes `braces` and `node-forge` versions covered by published advisories.
The reviewed advisories list no patched upstream releases at the time of this preview.
See [braces advisory](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) and [node-forge advisory](https://github.com/advisories/GHSA-86w9-cpqp-85rv).
Do not interpret successful compilation as a security certification or expose the development server publicly.
Review these upstream dependencies and their actual use before a public release.
The relay has its own dependency tree and does not include Expo's build toolchain.

## TL;DR

Compatible published fixes are applied; unresolved upstream build-tool advisories remain documented for release review.
