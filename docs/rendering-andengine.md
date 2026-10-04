# Rendering and AndEngine

## What the game runs on

* **AndEngine GLES1 branch** (anddev.org, 2012) — 520 classes in `org.anddev.andengine`, the same
  519 classes as the `bin/andengine.jar` left in the APK by DroidHen's build (built 2012-11-28)
  plus a generated `BuildConfig`. DroidHen's `com.droidhen.andplugin` adds TexturePacker `.plist`
  sprite sheets and Flash motion data (`FlashMotionCache`, Java-serialized `AllMotions.oos`).
* `GameActivity` → `DroidhenGameActivity` → `BaseGameActivity`.
* Camera: `SmoothCamera` **800×480**, `FillResolutionPolicy` (stretched to fill the view, no
  letterboxing by the engine), `ScreenOrientation.LANDSCAPE` (set at runtime with
  `setRequestedOrientation`), VBO extension disabled.
* Threads: UI thread, AndEngine `UpdateThread` (game logic **and touch processing**), `GLThread`
  (AndEngine's private copy of the Android 1.x `GLSurfaceView`), executor threads for network.

## Preservation decisions

* **No engine code was changed.** The original `classes.dex` is decoded to smali and reassembled;
  the only bytecode edits are the eight small hooks listed in
  [architecture.md](architecture.md#patch-series). No class of AndEngine or `andplugin` is
  touched.
* **Not upgraded** to AndEngine GLES2 or anything newer: different rendering pipeline, texture
  handling and timing — a rewrite, not a preservation.
* The 800×480 stretch-to-fill behaviour is kept. On 20:9 phones this stretches the scene
  horizontally exactly as the original did on any non-5:3 screen.
* Every file under `assets/` (all game graphics, motion data, sound and fonts) is byte-identical
  to the original and stored the same way (`.png`, `.jpg`, `.ogg` uncompressed, as `apktool.yml`'s
  `doNotCompress` list requires). Only Android resources are recompiled by aapt2 — see
  [preservation-notes.md](preservation-notes.md).

## Display cutout and edge-to-edge

Apps targeting API 35+ are drawn edge-to-edge and their window ignores the cutout mode they ask
for (`layoutInDisplayCutoutMode` is effectively `ALWAYS`). The original, targeting 30, got a
letterbox from the platform on the cutout side. Without handling, the preservation build's GL
view would extend under the cutout and the stretched 800×480 scene would lose its left edge to it.

`CutoutCompat` (API 28+) installs an `OnApplyWindowInsetsListener` on `android.R.id.content` and
pads it by `DisplayCutout.getSafeInset*()` only. System-bar insets are deliberately ignored: the
original hides the bars (immersive sticky) and draws behind them. Insets are returned unconsumed.
Effect: the GL surface is exactly the safe area, AndEngine's resolution policy fills it, and
touch coordinates stay consistent because AndEngine maps touches relative to its own view.

Observed with an emulated "tall" cutout in landscape: game starts at x = 96 px on Android 14,
15 and 16, identical to the original's geometry on Android 16
([comparison](evidence/android-16/05-cutout-comparison.jpg)). The whole Android 16
acceptance run (battles with drag-and-drop, menus, market, coin store, Arena) was played with the
emulated cutout active — every RC5 screenshot carries the 96 px inset strip — so touch mapping in
the inset view is verified. On Android 14 and 15 gameplay ran without a cutout and the cutout
check covered rendering only.

## Large screens

Android 16 ignores `setRequestedOrientation` on displays ≥ 600 dp for apps targeting API 36,
unless the app is a game. `android:appCategory="game"` keeps the original landscape lock
([evidence](evidence/android-16/06-large-screen.jpg)). The game is still 800×480 stretched to
the window, as before.

See also [opengl.md](opengl.md) and [lifecycle.md](lifecycle.md).
