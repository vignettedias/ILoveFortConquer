# Android 14 (API 34)

Tested on redroid 14 `UD2A.240505.001.W1` ([testing-environment.md](../testing-environment.md)).
Final APK `6ec49dd5…` — results in the [matrix](compatibility-matrix.md); screenshots in
[evidence/android-14/](../evidence/android-14/).

## Platform changes that mattered

| Android 14 change | Effect on Fort Conquer | Handling |
|---|---|---|
| Apps targeting API 34+ must pass `RECEIVER_EXPORTED`/`RECEIVER_NOT_EXPORTED` to `registerReceiver()` for non-system broadcasts | play-services-ads 15.0.1 registers a receiver without the flag → `SecurityException` on start ([log #7](../engineering-log.md#7-legacy-admob-sdk-crashes-the-game-when-targeting-api-34)) | AdMob banner never created ([0004](../../patches/0004-disable-legacy-admob.patch)); see [ads-and-third-party-sdks.md](../ads-and-third-party-sdks.md) |
| Minimum installable `targetSdkVersion` 23 | original targets 30, preservation build 36 — both install | none needed |
| Back: with `android:enableOnBackInvokedCallback="true"`, key-based BACK is routed to `OnBackInvokedCallback` | Observed: `KEYCODE_BACK` was **not** delivered to `GameActivity.onKeyDown`; the compat callback synthesised it once per press (`back invoked -> GameActivity.onKeyDown(KEYCODE_BACK)`) | [BackCompat](../../compat/src/main/java/io/github/vignettedias/fortconquer/compat/BackCompat.java); see [lifecycle.md](../lifecycle.md#back-navigation) |
| Apache HTTP client not on the class path (since API 28) | Baseline crash | [0001](../../patches/0001-apache-http-legacy-library.patch) |

## Observations specific to this run

* Display cutout (emulated "tall"): Android 14 does not force edge-to-edge, the platform itself
  keeps the window out of the cutout (`mAppBounds` left = 96) and the compat inset listener has
  nothing to add. Visually identical to Android 15/16 with the compat layer.
* The first stage-1 attempt by the test driver was lost (GAME OVER, consolation bonus
  59 coins + 1 crystal); RETRY → second attempt won. This is the game's normal difficulty, not a
  defect.
