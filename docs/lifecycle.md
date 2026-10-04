# Lifecycle, threads and input

## Activity

`GameActivity` is the only game activity: `launchMode="singleTask"`, theme
`Theme.NoTitleBar.Fullscreen`, `configChanges="keyboard|keyboardHidden|orientation|screenLayout|
uiMode|screenSize|smallestScreenSize"` (unchanged). AndEngine requests landscape at runtime.

| Event | Original engine behaviour (kept) | Observed on Android 14 / 15 / 16 |
|---|---|---|
| `onPause` (Home, Recents, screen off, another activity) | GL thread stopped, EGL context destroyed; a running battle is switched to PAUSE by the game | PASS — battle shows PAUSE on return |
| `onResume` | new EGL context, all textures reloaded (`AndEngine: onSurfaceCreated`) | PASS — no missing or corrupt textures |
| Screen off → on (+ keyguard) | as pause/resume | PASS |
| Rotation (device rotated, rotation lock) | `orientation|screenSize` handled in-process; AndEngine keeps landscape | PASS on 16 (user rotation 0 and 3) |
| Configuration change **not** in `configChanges` (density, cutout overlay, navigation-mode overlay, new input device) | activity destroyed and recreated in the same process; `onDestroy` clears static state via reflection; game restarts at the title screen | PASS — same as the original, save kept |
| Force-stop / process death | progress is persisted in SharedPreferences | PASS — name, coins, crystals, stage, XP and cards restored |
| In-place update (same signing key) | — | PASS on 16 |

The recreation-to-title on configuration changes is original behaviour and was deliberately not
"fixed": adding more `configChanges` flags would change how the 2012 engine reacts to
density/layout changes it was never written for.

## Threads

* UI thread: activity callbacks, dialogs.
* AndEngine `UpdateThread`: scene logic **and touch events** (AndEngine queues `MotionEvent`s and
  processes them on the update thread).
* `GLThread`: rendering.
* Executors: network requests.

Consequence found during testing: game code reached from a touch (e.g. the coin store's BUY) runs
on the update thread. Building a dialog there throws because that thread has no `Looper`; the
original swallowed the exception. Fixed for the billing dialog
([0007](../patches/0007-billing-unavailable-dialog-ui-thread.patch)). The player-name dialog, the
only other native dialog that was exercised, already worked; in-game dialogs such as PAUSE, GAME
OVER or the Arena "Network error" are drawn by the engine and are not affected.

## Back navigation

The original implements back only in `GameActivity.onKeyDown(KEYCODE_BACK)` →
`SmartScene.onKeyDown()`: title — consumed, nothing happens; menu scenes — previous scene;
battle — PAUSE, and from PAUSE back to Select Troops; in the stage-1 tutorial battle and on the
GAME OVER dialog it is ignored (observed). The platform never finishes the activity on BACK.

Back delivery for an app that targets API 36 and sets `android:enableOnBackInvokedCallback="true"`
(required to opt into predictive back) differs between releases. Observed:

| Android | Key-based BACK (nav-bar button / `input keyevent 4`) | Gesture back |
|---|---|---|
| 14 | only the `OnBackInvokedCallback` is invoked; `onKeyDown` is **not** called | NOT TESTED |
| 15, 16 | `KEYCODE_BACK` ACTION_DOWN delivered to `onKeyDown`, **then** the callback is invoked (~65 ms later) | NOT TESTED (documented to invoke only the callback) |

`BackCompat` + [0008](../patches/0008-back-key-exactly-once.patch) make the original handler run
exactly once in every case:

1. `GameActivity.onKeyDown` first calls `GameCompat.onKeyDown()`, which records a real
   `KEYCODE_BACK` ACTION_DOWN (repeat 0).
2. The `OnBackInvokedCallback` (registered at `PRIORITY_DEFAULT` in `onCreate`):
   * if a real BACK was recorded within the last 10 s → clear it and do nothing
     (log: `back invoked after KEYCODE_BACK; already handled`);
   * otherwise → synthesise `KEYCODE_BACK` ACTION_DOWN and call `GameActivity.onKeyDown`
     (log: `back invoked -> GameActivity.onKeyDown(KEYCODE_BACK)`).

Verified: Android 14 — synthesised path on every press; Android 15/16 — "already handled" on every
press; scene transitions identical to the original targetSdk-30 build on all three.

Because the game consumes BACK on the title screen, the system's predictive "back to home"
animation never runs — the same as the original, which never exits on BACK. Players leave with
Home or Recents.

## Input

* Touch: `View.OnTouchListener` on AndEngine's render view; coordinates converted to the 800×480
  camera by the resolution policy. Taps and drags were exercised; multi-finger input was not.
* Text input: the player-name dialog is a native `AlertDialog` with an `EditText`; the soft
  keyboard (extracted full-screen UI in landscape) works on 14/15/16.
* Hardware keys: BACK is the only key whose handling was examined and tested.
