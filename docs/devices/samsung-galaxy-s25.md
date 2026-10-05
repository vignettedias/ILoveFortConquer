# Samsung Galaxy S25

**Which APK:** the standard release, [`dist/FortConquer-1.2.4-Modern-Android.apk`](../../dist/)
(SHA-256 `6ec49dd5f3630f7e5ae29d09c858bc98f8be91b5aecf4d0e7d9abc1168ee2588`). There is no
separate Galaxy build and none is needed: the game has no native code (nothing CPU-specific for
the S25's Snapdragon 8 Elite), its graphics are density-independent textures, and the
device-relevant behaviour (cutout, back, orientation, Android 15/16 rules) is already handled in
the one APK.

## Install on the S25

1. **Uninstall the original Fort Conquer** if it is on the phone (different signing key; this
   deletes the original's local save).
2. **Auto Blocker** (Settings → Security and privacy → Auto Blocker) is on by default on Galaxy
   phones and blocks installing APKs from outside Galaxy Store / Play Store. Turn it off for the
   install; you can turn it back on afterwards — the installed game keeps working.
3. Copy the APK to the phone (USB, cloud, browser download), open it in **My Files**, allow
   "Install unknown apps" for My Files when asked, and install. With a computer instead:
   `adb install FortConquer-1.2.4-Modern-Android.apk` (needs USB debugging and Auto Blocker off).
4. Play Protect may warn about an unknown developer; check the SHA-256 above first.


## Real-device report

**2026-10-05 — reported by the repository owner:** the release APK (`6ec49dd5…`) was installed
on a Samsung Galaxy S25 and the game worked. The Android / One UI version and the steps exercised
were not reported, so this confirms installation and start-up on real S25 hardware; the
detailed results below come from the emulated S25 profile.

## Which Android your S25 runs

The S25 shipped with Android 15 (One UI 7) and received Android 16 (One UI 8). Samsung began
rolling out **One UI 9 / Android 17** to the S25 series at the end of September 2026
(South Korea first, then the US and other regions). Check Settings → About phone → Software
information → Android version.

| S25 software | What the preservation build was tested on |
|---|---|
| Android 15 (One UI 7) | Android 15 AOSP — acceptance test with no failures; a few steps partial or not run ([matrix](../compatibility/compatibility-matrix.md)) |
| Android 16 (One UI 8 / 8.5) | Android 16 AOSP — acceptance test with no failures (see matrix); plus the S25 display profile below |
| Android 17 (One UI 9) | **Not tested** — no Android 17 image was available to the test environment; reviewed against Google's published Android 17 changes, see below |

## S25 display profile test (Android 16)

No physical S25 was available. An Android 16 (AOSP) VM was booted at the S25's native panel
size, **1080 × 2340**, at 450 dpi (approximation of Samsung's default FHD+ density), with an
emulated punch-hole cutout (136 px deep; AOSP's emulation places the hole in a corner,
the S25's is top-centre — in landscape both inset the same screen edge). Final APK
`6ec49dd5…`. Screenshots: [evidence/galaxy-s25-profile/](../evidence/galaxy-s25-profile/).

| Check | Result |
|---|---|
| Install, launch, no crash (same process from install to the deliberate force-stop) | PASS |
| Game surface | PASS — 2204 × 1080: full screen minus the 136 px cutout strip; 800×480 scene stretched to it as in the original |
| Name dialog + keyboard | PASS |
| Tutorial battle with drag-and-drop, **stage 1 won** (12 kills, +114 coins, +1 crystal), stage 2 unlocked | PASS |
| Market purchase, Evolve | PASS |
| Coin store BUY → "Can't make purchases", nothing granted | PASS |
| BACK: Select Troops → Status → Title → no-op; battle → PAUSE → Select Troops (each press handled once) | PASS |
| Home / return and screen off / on during a battle (GL context recreated at 2204 × 1080, still paused) | PASS |
| Music player started; force-stop → progress kept | PASS |
| Memory (TOTAL PSS) | 75 MB |

## 120 Hz display

The S25 refreshes at up to 120 Hz; the game was written for 60 Hz phones. AndEngine renders
continuously, so on the S25 it may draw up to 120 frames per second. **Game speed does not depend
on the frame rate** — checked in the original bytecode:

* unit movement and all animations are AndEngine entity modifiers with durations in seconds
  (`MoveByModifier`, `ScaleModifier`, `DelayModifier`, …);
* card cooldowns are `ScaleAtModifier`s whose duration is the cooldown in seconds;
* the stage clock is a `java.util.Timer` at a fixed rate, and stone income uses
  `System.currentTimeMillis()`;
* the per-frame battle handler only checks lane state (collisions, win/loss), it does not advance
  anything by a fixed amount per frame.

Not tested on real 120 Hz hardware (the VM draws at 30 fps). Rendering at 120 Hz uses more battery
than at 60 Hz; One UI's game and display settings may offer a lower refresh rate if you prefer.

## Android 17 (One UI 9) review — not tested

Google's Android 17 behaviour changes were checked against this APK. The APK targets API 36, so
only the changes for **all apps** apply:

| Android 17 change (all apps) | Effect on Fort Conquer |
|---|---|
| Per-app memory limits based on device RAM | Game uses 45–75 MB; far below any limit on a 12 GB phone |
| Background audio hardening (audio calls outside valid lifecycle states fail silently) | Affects at most a music/sound call the game makes while in the background: it is ignored instead of played. Failing silently means no exception, so no crash path |
| IME visibility not restored after an unhandled configuration change | Only the name dialog uses the keyboard; rotation is handled in-process, so not affected |
| `usesCleartextTraffic` deprecation plan | Not used by the build |
| SMS OTP protection, cross-profile loopback, keystore limits, touchpad pointer capture, Bluetooth re-pairing | Not used by the game |

Changes that apply only to apps **targeting** Android 17 (API 37) — unmodifiable `static final`
fields, large-screen orientation opt-out removal, certificate transparency by default, BAL
hardening, and others — do not apply to an API 36 app. (The game's only reflection skips `final`
fields anyway.)

Expectation: it should run on One UI 9 as on Android 16, but this has **not been verified**. If you
are on One UI 9, a quick check is the [acceptance test](../acceptance-test.md) steps 1–5, 13–14
and 16–18.

## Things specific to Samsung that were not tested

* One UI's own behaviour (Game Booster / Game Launcher, per-app full-screen and camera-cutout
  settings, Samsung Keyboard, edge panels). A thin bar on the camera side in landscape is
  expected (the game is kept out of the cutout, as the original was); if the picture is boxed in
  more than that, check One UI's full-screen apps setting for Fort Conquer.
* Samsung's Adreno GPU driver (the VM used Mesa). The game uses OpenGL ES 1.1; how One UI's
  graphics stack runs it was not tested.
* Gesture navigation: One UI supports both 3-button and swipe navigation. Button navigation was
  tested; swipe-gesture back was not.

Sources for the S25 software status and Samsung settings:
[SamMobile — One UI 9.0 stable update for Galaxy S25](https://www.sammobile.com/news/one-ui-9-0-stable-update-galaxy-s25-finally-here/),
[GSMArena — S25 series One UI 9 in the US](https://www.gsmarena.com/samsung_galaxy_s25_series_one_ui_9_stable_update_usa-news-74890.php),
[Android Developers — Android 17 behaviour changes for all apps](https://developer.android.com/about/versions/17/behavior-changes-all),
[Android Developers — Android 17 behaviour changes for apps targeting 17](https://developer.android.com/about/versions/17/behavior-changes-17),
[9to5Google — Samsung Auto Blocker and sideloading](https://9to5google.com/2024/07/23/samsung-galaxy-sideloading-android-apps/),
[Samsung Mobile Press — Galaxy S25 specifications](https://www.samsungmobilepress.com/media-assets/galaxy-s25?tab=specs).
