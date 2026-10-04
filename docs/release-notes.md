# Release notes

## Fort Conquer 1.2.4-modern — unofficial preservation build

Based on the original Fort Conquer 1.2.4 (versionCode 31) APK, SHA-256 `933557dc…7c753cc92`.
**Not an official DroidHen release.** Signed with a preservation key: uninstall the original first.

Artifact: `dist/FortConquer-1.2.4-Modern-Android.apk`, SHA-256
`6ec49dd5f3630f7e5ae29d09c858bc98f8be91b5aecf4d0e7d9abc1168ee2588`, certificate SHA-256
`bbdd17d42f7ee3818081e91ccb815789fdc4fcbf38e47f6789a7afba6e976634`.

### Fixed

* Crash a few seconds after every launch on Android 9 and later (`NoClassDefFoundError:
  org.apache.http.impl.client.DefaultHttpClient`).
* Coin store BUY silently did nothing when billing was unavailable (original bug; the dialog was
  built on the engine thread).
* "Learn more" in that dialog could crash the game on devices without a browser.

### Changed

* targetSdkVersion 30 → 36 (Android 16); minSdkVersion unchanged (16).
* Back navigation bridged to the Android 13+ back system: with target API 36 the original key
  handler is no longer called for back gestures (Android 16), and on Android 15/16 a key press
  arrives twice; the game's own BACK handling now runs exactly once per press.
* Game kept inside the display-cutout safe area under Android 15+ edge-to-edge.
* Landscape kept on large screens on Android 16 (`appCategory="game"`).
* AdMob banner removed: the bundled 2018 SDK crashes apps targeting API 34+, and the ad unit
  belongs to the original developer.
* Online endpoint uses HTTPS; cleartext remains disabled.
* Permissions reduced to: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `WAKE_LOCK`,
  `com.android.vending.BILLING`.
* versionName `1.2.4-modern` (versionCode stays 31).

### Unchanged

Gameplay, balance, graphics, sound, save format, package name, versionCode.

### Tested

Android 14, 15 and 16 (AOSP in a VM) — see the [compatibility matrix](compatibility/compatibility-matrix.md).
Android 17: not tested.
