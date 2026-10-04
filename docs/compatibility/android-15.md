# Android 15 (API 35)

Tested on redroid 15 `BP1A.250505.005.D1` ([testing-environment.md](../testing-environment.md)).
Final APK `6ec49dd5…` — results in the [matrix](compatibility-matrix.md); screenshots in
[evidence/android-15/](../evidence/android-15/).

## Platform changes that mattered

| Android 15 change | Effect on Fort Conquer | Handling |
|---|---|---|
| Edge-to-edge enforced for apps targeting API 35+: windows draw behind system bars and `layoutInDisplayCutoutMode` is treated as `ALWAYS` | Without a fix the 800×480 GL scene is stretched under the display cutout in landscape | [CutoutCompat](../../compat/src/main/java/io/github/vignettedias/fortconquer/compat/CutoutCompat.java) pads the content view by the cutout safe insets only (the original already hid the system bars with immersive mode) — see [rendering-andengine.md](../rendering-andengine.md#display-cutout-and-edge-to-edge) |
| `elegantTextHeight` default, 16 KB page-size readiness, private space, etc. | No effect: no native libraries, game text is in textures | none |
| Back: key-based BACK is delivered to `onKeyDown` **and** then to the `OnBackInvokedCallback` | Without care the original handler would run twice (pause, then quit, in one press) | [0008](../../patches/0008-back-key-exactly-once.patch): the callback skips when a real `KEYCODE_BACK` was just handled (`back invoked after KEYCODE_BACK; already handled` logged on every press) |

## Observations specific to this run

* Emulated tall cutout in landscape: the game starts at x = 96 px, the cutout strip is black;
  toggling the overlay recreates the activity (it is not in the original `configChanges`) and
  the game returns to the title screen with the save intact — the same as the original.
* The tutorial's first battle was lost because the tester was too slow placing troops by hand;
  RETRY led back to Select Troops and the second attempt (test driver) won.
* The Arena request failed with `SSLHandshakeException: Trust anchor for certification path not
  found` because the sandbox intercepts TLS. The game showed its own "Network error" dialog; the
  certificate check was **not** weakened to make it pass.
