# Android 16 (API 36) — primary target

Tested on redroid 16 `BP2A.250605.031.A3` ([testing-environment.md](../testing-environment.md)).
The full acceptance run used release candidate RC5 (same contents as the final APK, see the
[matrix](compatibility-matrix.md)); the final APK `6ec49dd5…` was then smoke-tested.
Screenshots: [evidence/android-16/](../evidence/android-16/).

## Platform changes that mattered

| Android 16 change (for apps targeting API 36) | Effect on Fort Conquer | Handling |
|---|---|---|
| Predictive back: `onBackPressed()` is no longer called and `KEYCODE_BACK` is not dispatched for back *gestures*; without an app callback the system default sends the task to the background | Observed with a diagnostic build: BACK on the title screen left the game; the original consumes it. All in-game back handling (pause in battle, previous scene in menus) lives in `GameActivity.onKeyDown` → `SmartScene.onKeyDown` | [BackCompat](../../compat/src/main/java/io/github/vignettedias/fortconquer/compat/BackCompat.java) registers an `OnBackInvokedCallback` that feeds the original handler ([log #8](../engineering-log.md#8-android-16-no-longer-delivers-keycode_back-to-the-game)) |
| …but key-based back (3-button nav bar, hardware key) is still delivered as `KEYCODE_BACK` **and** invokes the callback | RC4 paused *and* quit the battle on a single press | [0008](../../patches/0008-back-key-exactly-once.patch): callback skips when the key already reached the game ([log #11](../engineering-log.md#11-back-pressed-once-in-battle-paused-and-quit-the-battle-rc4-regression-fixed)) |
| Orientation / aspect-ratio / resizability requests ignored on displays ≥ 600 dp, unless the app declares itself a game | 800×480 landscape game stretched into a portrait window on a tablet-sized display (diagnostic build) | `android:appCategory="game"` ([0002](../../patches/0002-target-api-36-manifest.patch)); landscape kept ([log #12](../engineering-log.md#12-large-screens-appcategorygame-keeps-the-landscape-lock-android-16), [evidence](../evidence/android-16/06-large-screen.jpg)) |
| Edge-to-edge opt-out (`windowOptOutEdgeToEdgeEnforcement`) no longer honoured | Same as Android 15: the cutout must be handled by the app | [CutoutCompat](../../compat/src/main/java/io/github/vignettedias/fortconquer/compat/CutoutCompat.java) ([log #9](../engineering-log.md#9-edge-to-edge-android-15-would-put-the-display-cutout-over-the-game), [evidence](../evidence/android-16/05-cutout-comparison.jpg)) |
| Runtime receivers need an export flag (from Android 14, API 34 targets) | Legacy AdMob crashed at start | AdMob banner disabled ([0004](../../patches/0004-disable-legacy-admob.patch)) |
| Components with intent filters need `android:exported` (from API 31 targets) | Install would be refused | `android:exported="true"` on the launcher activity ([0002](../../patches/0002-target-api-36-manifest.patch)) |

## Results worth calling out

* **Back** behaves like the original targetSdk-30 game for every key press that was tested
  (title: no-op; menus: previous scene; battle: PAUSE; PAUSE: quit to Select Troops). In the
  stage-1 tutorial battle and on the GAME OVER dialog the original `GameScene.onKeyDown`
  deliberately ignores BACK; the preservation build delivers the key once and the game ignores it,
  as before.
* **Gesture back was not tested**: the redroid SystemUI in this environment does not turn edge
  swipes into back gestures. The callback path it would use is the same one Android 14 used for
  every key press (where the key event is not delivered), which was tested.
* **Cutout**: the emulated "tall" cutout was active for the entire RC5 run, so all gameplay
  (including drag-and-drop in battle) was exercised in the inset view.
* **Large screens**: with the display set to 1200×1920 @ 200 dpi (sw960dp) and user rotation
  locked to portrait, the game stays landscape and letterboxed.
* **Configuration changes** that are not in the original `configChanges` list (cutout overlay,
  navigation-mode overlay, density change, a new input device) recreate the activity in the same
  process; the game returns to the title screen with its save intact. The original APK does the
  same; this was left unchanged.
