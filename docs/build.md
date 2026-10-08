# Building

## Requirements

* JDK **17 or newer** (tested with JDK 21.0.11). No Android SDK, NDK or Android Studio is needed.
* `python3` (only for `scripts/verify_apk.sh` / `scripts/analyze_apk.sh`).
* Network access on the first build: Maven Central and GitHub release downloads. Everything
  is pinned and checked against SHA-256 values in
  [`gradle/verification-metadata.xml`](../gradle/verification-metadata.xml); the Gradle
  distribution itself is checked via `distributionSha256Sum`.
* Linux x86_64 (tested). macOS should work — apktool bundles a macOS `aapt2` and `extractTools`
  selects it — but was not tested. Windows: not supported by the scripts; use WSL.
* About 1 GB free disk space (`build/` ≈ 450 MB plus the Gradle cache ≈ 600 MB).

## Build

```sh
./gradlew clean assembleRelease
# or, including verification and checksum check:
scripts/build_release.sh
```

Outputs in `dist/`:

| File | Content |
|---|---|
| `FortConquer-1.2.4-Modern-Android.apk` | signed release APK |
| `FortConquer-1.2.4-Modern-Android.sha256` | `sha256sum` format |
| `FortConquer-1.2.4-Modern-Android.metadata.txt` | package/version/SDK levels, reference APK hash, source commit (`-dirty` if tracked files were modified), signing certificate digests, tool versions |

The first build signs with a locally generated key (see [signing.md](signing.md)).

## Pipeline

```
com.droidhen.fortconquer_v1.2.4-31_Android-4.1.apk   (repo root; SHA-256 checked: verifyReferenceApk)
  │ apktool 2.12.1 decode                              decodeReferenceApk  → build/fc/decoded
  │ patches/series applied strictly (no fuzz)          preparePatchedTree  → build/fc/patched
  │ compat/ (Java 8 → dx 16.0.1 → baksmali 2.5.2)      dexCompat, baksmaliCompat
  │ merge smali (duplicate classes = build failure)    stageApkTree        → build/fc/stage
  │ apktool build (smali + aapt2, API 36 framework)    buildUnsignedApk    → build/fc/apk/unsigned.apk
  │ zipalign (4-byte, 16 KiB for .so, fixed time)      alignApk            → build/fc/apk/aligned.apk
  │ apksigner 33.0.2 sign v1+v2+v3                     signReleaseApk      → build/fc/apk/signed.apk
  │ apksigner verify + alignment check                 verifyReleaseApk
  └ copy + checksum + metadata                         assembleRelease     → dist/
```

| Tool | Version | Source |
|---|---|---|
| Gradle | 8.14.3 | wrapper, SHA-256 pinned |
| apktool (incl. smali/baksmali + aapt2 binaries) | 2.12.1 | GitHub release `iBotPeaches/Apktool` |
| dx (DEX compiler for the compat layer) | 16.0.1 (`com.jakewharton.android.repackaged:dalvik-dx`) | Maven Central |
| baksmali | 2.5.2 (`org.smali:baksmali`) | Maven Central |
| Android API 36 class library (compile-only, for the compat layer) | `org.robolectric:android-all` `16-robolectric-13921718` | Maven Central |
| apksigner | 33.0.2 (AOSP build-tools, shipped inside uber-apk-signer 1.3.0) | GitHub release `patrickfav/uber-apk-signer` |

Why not the Android Gradle Plugin: the game must keep its original 2012/2020 bytecode exactly
(see [preservation-notes.md](preservation-notes.md)); AGP would recompile from decompiled Java.
Google's Maven repository was also not reachable from the build environment, so every tool comes
from Maven Central or a pinned GitHub release instead.

## Reproducibility

* Two `./gradlew clean assembleRelease` runs a minute apart produced byte-identical
  `aligned.apk` and signed APKs (SHA-256 `6ec49dd5…`) with the same key. apktool stamps ZIP
  entries with the current time; `ZipAlign` normalises them to 2008-01-01 00:00.
* RSA PKCS#1 v1.5 signatures are deterministic, so the signed APK is reproducible **for a given
  key**. A different key gives a different APK, by design.
* Inputs that affect the output: the reference APK, `patches/`, `compat/`, the pinned tools and
  the signing key. The JDK version affects only the compat-layer class files before dexing; JDK 21
  was used for the published build.

## Other tasks

| Task | Purpose |
|---|---|
| `./gradlew preparePatchedTree` | decode + apply patches only (inspect `build/fc/patched`) |
| `./gradlew extractTools` | unpack `apktool.jar`, `aapt2`, `apksigner.jar` into `build/tools` (used by the scripts) |
| `./gradlew verifyReleaseApk` | build and verify without publishing to `dist/` |

## Changing a patch

```sh
./gradlew preparePatchedTree
cp -a build/fc/patched /tmp/edit && $EDITOR /tmp/edit/smali/...   # make the change
scripts/make_patch.sh /tmp/edit patches/0009-my-change.patch        # diff against build/fc/patched
echo 0009-my-change.patch >> patches/series
./gradlew clean assembleRelease
```

Patches are applied strictly: a hunk that does not match exactly fails the build, so a patch can
never silently land in the wrong place.

## Variants

`-Pfc.variant=<name>` applies `patches/variants/<name>/series` after `patches/series`, builds in
`build/fc-<name>/` and publishes to `dist/<name>/`. The only variant is the
[unlimited-gems cheat build](variants/unlimited-gems.md):

```sh
./gradlew -Pfc.variant=unlimited-gems clean assembleRelease
```

Without the property the preservation build is produced unchanged. Variant patches are made the
same way as above (`scripts/make_patch.sh` diffs against `build/fc/patched`, i.e. the base series).
