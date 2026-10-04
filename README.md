# Fort Conquer 1.2.4 — modern Android preservation build

**Unofficial.** This repository makes the 2012-era Android game *Fort Conquer* (DroidHen) run on
current Android again, while keeping the original game as it was. It is **not** a DroidHen
release, is not affiliated with or endorsed by DroidHen, and is not signed with DroidHen's key.

| | Original | Preservation build |
|---|---|---|
| File | `com.droidhen.fortconquer_v1.2.4-31_Android-4.1.apk` (in this repo) | [`dist/FortConquer-1.2.4-Modern-Android.apk`](dist/) |
| SHA-256 | `933557dc1b5ba6c900b078689d269faf47c622fb3ea23acc3efa11b7c753cc92` | `6ec49dd5f3630f7e5ae29d09c858bc98f8be91b5aecf4d0e7d9abc1168ee2588` |
| Package / versionCode | `com.droidhen.fortconquer` / 31 | same |
| versionName | 1.2.4 | 1.2.4-modern |
| min / target SDK | 16 / 30 | 16 / **36** |
| Signer | `CN=hzstudio` (original publisher) | `CN=Fort Conquer Local Preservation Build, O=Unofficial preservation build` — cert SHA-256 `bbdd17d4…6e976634` |
| Android 14 / 15 / 16 | **crashes** seconds after launch | runs — no failures in the acceptance test; a few steps only partially exercised or not run ([matrix](docs/compatibility/compatibility-matrix.md)) |
| Android 17 | not tested | not tested |

## Install

1. **Uninstall the original Fort Conquer** if it is installed. The preservation build is signed
   with a different key, so Android will not install it as an update. Uninstalling deletes the
   original's local save.
2. Check the file: `sha256sum FortConquer-1.2.4-Modern-Android.apk` must print the full SHA-256 above
   (`6ec49dd5…ee2588`), or run `scripts/verify_apk.sh`.
3. Install it (`adb install FortConquer-1.2.4-Modern-Android.apk`, or open it on the device and
   allow installing from that source).

## What changed

Eight small patches on top of the original bytecode plus a small compatibility layer (4 Java classes, ~230 lines with comments)
([architecture](docs/architecture.md)):

* **Start-up crash fixed** — the game used the Apache HTTP client, which Android 9+ removes from
  apps that target API 28+ ([baseline failures](docs/baseline-failures/)).
* **targetSdk 36** with the required manifest changes; landscape kept on large screens.
* **Back button / predictive back** bridged into the game's own handler, exactly once per press
  ([lifecycle](docs/lifecycle.md#back-navigation)).
* **Display cutout** handled under Android 15+ edge-to-edge.
* **AdMob removed** — the 2018 SDK crashes on API 34+ and the ad unit is DroidHen's
  ([ads](docs/ads-and-third-party-sdks.md)).
* **HTTPS** for the old game server; cleartext stays disabled ([networking](docs/networking.md)).
* **Two original bugs fixed**: the coin store's BUY did nothing when billing was unavailable, and
  "Learn more" could crash without a browser.
* Fewer permissions (no storage, no phone state).

Gameplay, balance, graphics, sound, engine and save format are untouched
([preservation notes](docs/preservation-notes.md)).

## What does not work

* **In-app purchases** — the game is delisted and used Google's retired AIDL billing; the build
  shows the original "Can't make purchases" message and grants nothing ([billing](docs/billing.md)).
* **Online features** (Arena PvP, discounts) only work if DroidHen's server still answers over
  HTTPS; this could not be checked. Everything else is offline.
* More in [known limitations](docs/known-limitations.md).

## How it was tested

Android 14, 15 and 16 AOSP (redroid) images in a QEMU VM with virgl graphics — no KVM, no
Google Play services, no physical devices ([testing environment](docs/testing-environment.md)).
A 22-step [acceptance test](docs/acceptance-test.md) covering first launch, tutorial, winning and
losing battles, market, evolve, coin store, back navigation, pause/resume, screen off, force-stop,
Arena offline and a display cutout; screenshots in [docs/evidence](docs/evidence/). The original
APK was run on the same images to record the [baseline failures](docs/baseline-failures/).

## Build it yourself

```sh
./gradlew clean assembleRelease     # JDK 17+, no Android SDK needed
scripts/verify_apk.sh               # checks manifest, alignment, signatures
```

Builds are reproducible for a given signing key. Without configuration the build creates its own
local key; see [build](docs/build.md) and [signing](docs/signing.md).

## Repository map

`patches/` (the changes) · `compat/` (compatibility layer) · `buildSrc/` + `build.gradle.kts`
(pipeline) · `scripts/` · `tools/test-harness/` · `docs/` · `dist/` (release).
Full index: [docs/README.md](docs/README.md). Work log: [docs/engineering-log.md](docs/engineering-log.md).

## Rights

Fort Conquer, its code, graphics, music and name belong to their respective owners. This
repository adds compatibility patches, build tooling and documentation for preservation of a
game that no longer runs on current devices.
