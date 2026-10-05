# Compatibility matrix

Only results that were actually observed are listed. **PASS** = behaved like the original game
on a supported device; **PARTIAL** = only part of the step could be exercised (the note says
which part); **FAIL** = broken; **NOT TESTED** = not run, no claim either way.

Environment: redroid AOSP images in a QEMU VM, no Google Play services — see
[testing-environment.md](../testing-environment.md) for what that does and does not prove.
Steps are defined in [acceptance-test.md](../acceptance-test.md).

Artifacts under test:

* **Original** — `com.droidhen.fortconquer_v1.2.4-31_Android-4.1.apk`, SHA-256 `933557dc…7c753cc92`
* **Preservation build** — `dist/FortConquer-1.2.4-Modern-Android.apk`, SHA-256
  `6ec49dd5f3630f7e5ae29d09c858bc98f8be91b5aecf4d0e7d9abc1168ee2588`. Android 14 and 15 were
  tested with exactly this file. The full Android 16 run used release candidate RC5, which has
  byte-identical contents (all 652 ZIP entries have the same CRC-32 and size; only the ZIP
  timestamps differ, normalised afterwards for reproducibility); the final file was then
  smoke-tested on Android 16 (row "final APK smoke test").

## Original APK (baseline)

| | Android 14 | Android 15 | Android 16 | Android 17 |
|---|---|---|---|---|
| Install | PASS | PASS | PASS | NOT TESTED |
| Launch | **FAIL** — `NoClassDefFoundError: DefaultHttpClient`, process killed | **FAIL** (same) | **FAIL** (same) | NOT TESTED |

Details: [baseline-failures/](../baseline-failures/).

## Preservation build — acceptance test

| # | Step | Android 14 | Android 15 | Android 16 | Android 17 |
|---:|---|---|---|---|---|
| 1 | Clean install | PASS | PASS | PASS | NOT TESTED |
| 2 | Launch, logo, title | PASS | PASS | PASS | NOT TESTED |
| 3 | Name dialog + soft keyboard | PASS | PASS | PASS | NOT TESTED |
| 4 | Select troops | PASS | PASS | PASS | NOT TESTED |
| 5 | Battle, tutorial, drag-and-drop deploy | PASS | PASS | PASS | NOT TESTED |
| 6 | Defeat → RETRY | PASS | PASS | PASS | NOT TESTED |
| 7 | Stage 1 victory, rewards, stage 2 unlocked | PASS (11 kills, +157 coins, +2 crystals) | PASS (12 kills, +117 coins, +1 crystal) | PASS (14 kills, +128 coins, +1 crystal) | NOT TESTED |
| 8 | Market: buy card | PASS | PASS | PASS | NOT TESTED |
| 9 | Evolve | PASS | PASS | PASS | NOT TESTED |
| 10 | Upgrade | NOT TESTED | NOT TESTED | PARTIAL — upgrade dialog and the not-enough-coins redirect to the coin store; no upgrade completed | NOT TESTED |
| 11 | Coin store BUY → billing unavailable, nothing granted | PASS | PASS | PASS | NOT TESTED |
| 12 | "Learn more" | PARTIAL — opens browser; no-browser case not run | PARTIAL — opens browser; no-browser case not run | PASS — browser, and no crash with the browser disabled | NOT TESTED |
| 13 | BACK in menus; title consumes BACK | PASS | PASS | PASS | NOT TESTED |
| 14 | BACK in battle → PAUSE; again → Select Troops | PASS | PASS | PASS | NOT TESTED |
| 15 | RESUME from PAUSE | NOT TESTED | NOT TESTED | PASS | NOT TESTED |
| 16 | Home → return (GL context recreated) | PASS | PASS | PASS | NOT TESTED |
| 17 | Screen off / on | PASS | PASS | PASS | NOT TESTED |
| 18 | Force-stop → progress kept | PASS | PASS | PASS | NOT TESTED |
| 19 | Update install → progress kept | NOT TESTED | NOT TESTED | PASS | NOT TESTED |
| 20 | Arena without server → "Network error", CANCEL, no energy lost | PASS | PASS | PASS | NOT TESTED |
| 21 | Display cutout (emulated "tall", landscape) | PASS — rendering (game starts at x=96) | PASS — rendering (game starts at x=96) | PASS — same geometry as the original; the entire RC5 run, incl. all battles and drags, was played with the cutout active | NOT TESTED |
| 22 | Audio | NOT TESTED | NOT TESTED | PARTIAL — title music `MediaPlayer` in state `started` (44.1 kHz stereo) and the effects `SoundPool` created, per `dumpsys audio`; not listened to; toggles not exercised | NOT TESTED |

## Preservation build — platform behaviour

| Item | Android 14 | Android 15 | Android 16 | Android 17 |
|---|---|---|---|---|
| No crash: the game process stayed alive from launch until the deliberate force-stop (same PID throughout; no `FATAL EXCEPTION` in any captured log) | PASS | PASS | PASS (final-APK smoke session) | NOT TESTED |
| No AdMob code path / no `admob` preferences on a clean install | PASS | PASS | PASS | NOT TESTED |
| Key-based BACK handled exactly once | PASS (delivered only to the back callback; bridge synthesises the key) | PASS (key delivered to `onKeyDown` *and* callback; callback skips) | PASS (as on 15) | NOT TESTED |
| Gesture-navigation / predictive BACK | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| Landscape kept with user rotation locked to portrait (phone) | NOT TESTED | NOT TESTED | PASS | NOT TESTED |
| Large screen (sw960dp) keeps landscape | NOT TESTED | NOT TESTED | PASS (`appCategory="game"`) | NOT TESTED |
| Config change outside `configChanges` (e.g. cutout overlay) → activity recreated, back to title, save kept (original behaviour) | PASS | PASS | PASS | NOT TESTED |
| Memory (TOTAL PSS, `dumpsys meminfo`) | 54 MB after ~25 min of play | 50 MB after relaunch | 46–57 MB during the RC5 run; 45 MB after relaunch (final APK) | NOT TESTED |
| Legacy DroidHen server reachable | NOT TESTED — sandbox egress is TLS-intercepted | NOT TESTED | NOT TESTED | NOT TESTED |
| Real Google Play purchase | NOT TESTED — no Play services; the game is delisted | NOT TESTED | NOT TESTED | NOT TESTED |
| Final APK smoke test (exact `6ec49dd5…` file) | covered by the run above | covered by the run above | PASS — install, launch, title, audio state, status, battle render, BACK handled once (5 presses), cutout, relaunch; see [android-16.md](android-16.md) | NOT TESTED |

## Device profile: Samsung Galaxy S25 (emulated display, Android 16)

1080×2340 panel at 450 dpi with an emulated punch-hole cutout, final APK: install, launch, name
dialog, stage 1 won with drag-and-drop, market, evolve, coin store dialog, BACK in menus and
battle, Home / screen off-on, force-stop persistence — all PASS. Real S25 hardware, One UI,
120 Hz and Android 17 (One UI 9): NOT TESTED. Details: [devices/samsung-galaxy-s25.md](../devices/samsung-galaxy-s25.md).

Per-version notes: [Android 14](android-14.md) · [Android 15](android-15.md) ·
[Android 16](android-16.md) · [Android 17](android-17.md). Screenshots: [evidence/](../evidence/).
