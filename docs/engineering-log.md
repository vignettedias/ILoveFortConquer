# Engineering log

Chronological record of every issue investigated during the compatibility restoration.
Each entry records the evidence that was actually observed. Test devices are described in
[testing-environment.md](testing-environment.md) (redroid AOSP images running in a QEMU VM;
no Google Play services).

---

## 1. Test environment: Android userspace crash-loops (netd) — environment issue, not the game

```text
Issue:            Android 14 test image never finished booting; system_server restarted every ~15 s.
Affected version: test harness only (redroid 14 inside QEMU)
Observed:         sys.system_server.start_count kept increasing; init logged
                  "Service 'netd' exited with status 1" followed by system_server being killed.
Root cause:       netd could not create its NFLOG / NETLINK_NETFILTER / XFRM sockets and
                  iptables-restore could not initialise the 'mangle' table. Android's init.rc
                  empties /proc/sys/kernel/modprobe, so the generic Ubuntu kernel used for the VM
                  could not autoload netfilter modules (Docker hosts load them from the host side).
Fix:              VM initramfs preloads the kernel's netfilter / xfrm / sched modules before
                  switching to Android's /init. (Test harness only; nothing in the APK.)
Validation:       boot completes in ~90 s with sys.system_server.start_count = 1.
```

## 2. Baseline: original APK crashes ~1 s after launch on Android 14

```text
Issue:            Original 1.2.4 APK installs, then the process dies immediately after onCreate.
Affected version: Android 14 (API 34) observed; applies to every Android 9+ device because the
                  app targets API 30.
Observed:         FATAL EXCEPTION: pool-4-thread-1 (and pool-5-thread-1)
                  java.lang.NoClassDefFoundError: Failed resolution of:
                      Lorg/apache/http/impl/client/DefaultHttpClient;
                    at com.droidhen.fortconquer.kits.DiscountManager$NetworkService.run(DiscountManager.java:152)
                  Caused by: java.lang.ClassNotFoundException: org.apache.http.impl.client.DefaultHttpClient
Root cause:       Android platform change (Android 9 / API 28): the Apache HTTP client is no longer
                  on the boot class path for apps targeting API >= 28 unless they declare
                  <uses-library android:name="org.apache.http.legacy"/>. 1.2.4 was rebuilt with
                  targetSdkVersion 30 but kept the Apache-based DiscountManager / ArenaAgent code and
                  did not declare the library. GameActivity.onCreate() -> DiscountManager
                  .updateDiscountRate() starts the request on an executor thread; the Error is not
                  caught (only IOException/JSONException are), so the process is killed.
Relevant code:    com.droidhen.fortconquer.kits.DiscountManager$NetworkService.run()
                  com.droidhen.fortconquer.kits.ArenaAgent$NetworkService.run()
Control test:     an unmodified apktool round-trip of the APK (no patches, re-signed) crashes with
                  the identical stack trace -> the rebuild pipeline itself does not change behaviour.
Fix:              patches/0001-apache-http-legacy-library.patch declares the platform library
                  (android:required="false").
Validation:       Android 14: no crash in 90 s; DroidHen logo scene and start menu render.
                  The request now fails gracefully inside the game's own catch block:
                  java.io.IOException: Cleartext traffic not permitted: http://fortconquer.droidhen.com
```

## 3. Test environment: software rendering too slow for gameplay testing

```text
Issue:            With redroid "guest" GPU mode (ANGLE -> SwiftShader Vulkan inside the emulated
                  CPU) the game rendered correctly but at 0.16 fps (50 frames in 307 s).
Root cause:       No KVM in the build container: every guest instruction, including SwiftShader's
                  rasteriser, runs under QEMU TCG.
Fix (harness):    virtio-gpu-gl device + virglrenderer: guest Mesa (libGLESv1_CM_mesa /
                  virtio_gpu_dri) forwards GL to the host, where Mesa llvmpipe renders natively
                  on an Xvfb display. Measured 30.0 fps (127 frames / 4.2 s, compositor capped at 30).
Note:             Two different GLES implementations were therefore exercised: ANGLE/SwiftShader
                  (as on Pixel-class devices that ship ANGLE) and Mesa/virgl.
```

## 4. Test environment: Android 16 image needs an ashmem driver

```text
Issue:            Android 16 guest never finished booting; system_server crashed in a loop with
                  java.lang.RuntimeException: Failed to create ashmem: No such file or directory
                    at com.android.internal.os.ApplicationSharedMemory.nativeCreate
Root cause:       Android 15+/16 libcutils only uses memfd when the kernel implements the Android
                  Common Kernel "ashmem-memfd compat" ioctls; the generic Ubuntu 6.8 kernel used for
                  the VM does not, and /dev/ashmem was removed from mainline in 5.18.
Fix (harness):    built the open-source ashmem driver from redroid-modules, ported to Linux 6.8
                  (shrinker_alloc()/vm_flags_clear()/kprobe-based symbol lookup), loaded from the
                  VM initramfs. Not part of the APK. Real Android 15/16 devices ship this in their
                  kernels.
```

## 5. Baseline confirmed on Android 16

```text
Issue:            Same as entry 2 on Android 16 (API 36, BP2A.250605.031.A3).
Observed:         install Success (targetSdk=30); FATAL EXCEPTION: pool-6-thread-1
                  java.lang.NoClassDefFoundError: ... DefaultHttpClient
                    at com.droidhen.fortconquer.kits.DiscountManager$NetworkService.run(DiscountManager.java:152)
```

## 6. Raising targetSdkVersion 30 -> 36: required manifest changes

```text
Issue:            Modernise the target API (Android 16) without changing behaviour.
Root cause:       targetSdk >= 31 refuses components with intent filters but no android:exported;
                  targetSdk 36 on sw>=600dp displays ignores orientation requests unless the app is
                  a game; informational compileSdk/platformBuildVersion attributes still said 30.
Fix:              patches/0002-target-api-36-manifest.patch: apktool.yml targetSdkVersion 36,
                  versionName "1.2.4-modern"; GameActivity android:exported="true";
                  <application android:appCategory="game" android:enableOnBackInvokedCallback="true">;
                  compileSdkVersion/platformBuildVersion attributes 36 (resources are linked against
                  apktool 2.12.1's framework table, which contains the API 36 attributes).
Validation:       Android 14 and 16 install and launch; dumpsys package shows targetSdk=36.
```

## 7. Legacy AdMob SDK crashes the game when targeting API 34+

```text
Issue:            Diagnostic build = 0001 + 0002 only (targetSdk 36, ads untouched) crashes on start.
Affected version: any device running Android 14+ with an APK targeting API >= 34.
Observed:         FATAL EXCEPTION: main
                  java.lang.SecurityException: com.droidhen.fortconquer: One of RECEIVER_EXPORTED or
                  RECEIVER_NOT_EXPORTED should be specified when a receiver isn't being registered
                  exclusively for system broadcasts
                    at android.content.ContextWrapper.registerReceiver(ContextWrapper.java:778)
                    at com.google.android.gms.internal.ads.zzakk.zzal(Unknown Source:26)
                    at com.google.android.gms.ads.internal.zza.<init>(Unknown Source:45)
Root cause:       play-services-ads 15.0.1 (2018) registers a runtime receiver without the export
                  flag that Android 14 requires for apps targeting API 34+.
Fix:              patches/0004-disable-legacy-admob.patch: AdController.showAdInLayout() is a
                  no-op, so no AdView is ever constructed (all other AdController methods are
                  already null-safe). AD_ID permission removed. Rationale beyond the crash: the ad
                  unit belongs to DroidHen's AdMob account and must not serve from an unofficial,
                  re-signed build; the SDK version is long out of support and needs Play services.
Validation:       0001+0002+0004 diagnostic build launches without crash on Android 16.
```

## 8. Android 16 no longer delivers KEYCODE_BACK to the game

```text
Issue:            Back navigation semantics change for targetSdk 36.
Observed:         Diagnostic build without the compat hook (0001+0002+0004): pressing BACK on the
                  title screen sends the game to the background (topResumedActivity becomes
                  com.android.launcher3). The original consumes BACK on the title screen.
Root cause:       Android 16 behaviour change: for apps targeting API 36, onBackPressed() is not
                  called and KeyEvent.KEYCODE_BACK is not dispatched; back goes to
                  OnBackInvokedCallback / predictive back. The game handles back only in
                  GameActivity.onKeyDown -> SmartScene.onKeyDown (pause dialog in battle, previous
                  scene in menus, no-op on the title screen).
Fix:              compat BackCompat (API 33+) registers an OnBackInvokedCallback that calls the
                  original GameActivity.onKeyDown(KEYCODE_BACK, ACTION_DOWN); manifest opts in
                  with enableOnBackInvokedCallback="true" so back is never delivered twice.
                  Hooked from GameActivity.onCreate (patches/0003-compat-layer-hooks.patch).
Validation:       Android 14 and 16: BACK on title -> game stays resumed; BACK on STATUS -> title.
```

## 9. Edge-to-edge (Android 15+) would put the display cutout over the game

```text
Issue:            With targetSdk >= 35 the window is forced to LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS.
Observed:         Android 16 + emulated "tall" cutout (cmd overlay
                  com.android.internal.display.cutout.emulation.tall), landscape:
                  - targetSdk 30 build: platform letterboxes the window (black bar on cutout side);
                  - targetSdk 36 without compat: game drawn across the full width, under the cutout;
                  - RC (compat): game inset by the cutout safe area (~96 px) - same geometry as the
                    original. dumpsys window: layoutInDisplayCutoutMode=always.
Fix:              compat CutoutCompat (API 28+): OnApplyWindowInsetsListener on android.R.id.content
                  pads by DisplayCutout safe insets (system-bar insets intentionally ignored, as the
                  original drew behind the immersive bars).
Validation:       docs/evidence/android-16/05-cutout-comparison.jpg; the whole Android 16 RC5
                  acceptance run was played with this cutout active (touch mapping verified).
```

## 10. Coin store "BUY" silently does nothing when billing is unavailable (original bug)

```text
Issue:            Tapping BUY in the coin store showed nothing at all.
Affected version: every Android version whenever billing is unavailable; in this build billing
                  is always unavailable (app delisted, AIDL billing retired, Play's service not
                  visible to targetSdk >= 30 apps without <queries>).
Root cause:       Game/engine bug. AndEngine processes touches on its update thread, so
                  CoinStoreScene -> GameActivity.requestPurchaseItem() ->
                  PurchaseManager.buyItemInMainThread() runs off the UI thread. With
                  supportPurchase == false it calls alertBillingNotSupport(), whose
                  AlertDialog.create() needs a Looper; the RuntimeException is swallowed by
                  buyItemInMainThread's catch (Exception) block.
Fix:              patches/0007-billing-unavailable-dialog-ui-thread.patch +
                  GameCompat.showDialogOnUiThread(): the original dialog is built unchanged but
                  created/shown on the UI thread.
Validation:       Android 16: BUY -> "Can't make purchases / The Market billing service is not
                  available at this time..." with OK / Learn more. "Learn more" opens the browser;
                  with the only browser disabled it logs ActivityNotFoundException via
                  GameCompat.startActivitySafely() and the game stays in the foreground.
```

## 11. Back pressed once in battle paused AND quit the battle (RC4 regression, fixed)

```text
Issue:            With RC4 a single BACK during a battle went straight to Select Troops; the
                  targetSdk 30 reference build shows the PAUSE dialog for the same input.
Observed:         Diagnostic build logging a stack trace in GameActivity.onKeyDown: on Android 16
                  with enableOnBackInvokedCallback="true", key-based back still delivers
                  KEYCODE_BACK ACTION_DOWN through ViewPostImeInputStage -> Activity.dispatchKeyEvent
                  -> GameActivity.onKeyDown, and then also invokes the OnBackInvokedCallback
                  ("back invoked" logged ~65 ms later). The original handler therefore ran twice:
                  RUNNING -> pause, then PAUSE -> back to unit selection (GameScene.onKeyDown).
Fix:              patches/0008-back-key-exactly-once.patch: GameActivity.onKeyDown first calls
                  GameCompat.onKeyDown(); BackCompat remembers a real BACK DOWN and the callback
                  only synthesizes KEYCODE_BACK when no key event reached the game (gesture back).
Validation:       Android 16 RC5: BACK while running -> PAUSE (callback logs "already handled");
                  BACK while paused -> Select Troops (original state machine); title -> no-op.
```

## 12. Large screens: appCategory="game" keeps the landscape lock (Android 16)

```text
Observed:         Display resized to 1200x1920 @200 dpi (sw960dp), user rotation locked to
                  portrait. Variant without android:appCategory: Android 16 ignores the landscape
                  request and runs the 800x480 game stretched into a portrait window. RC5 (with
                  appCategory="game"): request honoured, landscape letterboxed, normal rendering.
```

## 13. Lifecycle evidence (Android 16, RC5)

```text
- Home during battle -> GL thread exits, launcher resumed; return -> new EGL context (AndEngine
  logs EXTENSIONS again), all textures reloaded, battle state kept and auto-paused.
- Screen off (KEYCODE_SLEEP) / on (WAKEUP + dismiss-keyguard) during battle -> auto-paused,
  rendering intact, no crash.
- Configuration changes outside the original configChanges list (adding a touchscreen device,
  switching 3-button/gesture navigation overlays, display density) recreate GameActivity in the
  same process; the original onDestroy/onCreate path re-initialises cleanly and returns to the
  title screen. Saved progress is kept. (Original behaviour, kept unchanged.)
- adb install -r (in-place update, same key) and am force-stop + relaunch keep all progress
  (name, coins, crystals, stage, XP, cards) - read back from shared_prefs.
```

## 14. Baseline confirmed on Android 15

```text
Issue:            Same as entry 2 on Android 15 (API 35, BP1A.250505.005.D1).
Observed:         install Success; 4.5 s after am start: FATAL EXCEPTION: pool-6-thread-1 and
                  pool-5-thread-1 (two discount requests), NoClassDefFoundError: ... DefaultHttpClient
                  at DiscountManager$NetworkService.run(DiscountManager.java:152); the restarted
                  process dies identically. See docs/baseline-failures/android-15.md.
```

## 15. Release APK was not reproducible (ZIP timestamps)

```text
Issue:            Rebuilding unchanged sources gave a different APK SHA-256 each time.
Observed:         Two unsigned APKs from consecutive builds: identical entry order, sizes and
                  CRC-32s; 649 of the 652 entries differed in their DOS time/date fields
                  (e.g. resources.arsc 20:09:42 vs 20:10:34), nothing else differed.
Root cause:       apktool writes the wall-clock time into every local and central header.
Fix:              ZipAlign normalises time/date to 2008-01-01 00:00 while aligning (commit
                  "build: byte-identical release APKs across rebuilds"); release metadata marks
                  "-dirty" source trees.
Validation:       two clean builds one minute apart -> aligned.apk and the signed APK identical
                  (6ec49dd5...ee2588). The final APK's 652 entries match RC5's CRC-32 and sizes
                  exactly, so RC5 test results carry over to it.
```

## 16. Android 14 routes key-based BACK only to the callback

```text
Observed:         Final APK on Android 14 (UD2A.240505.001.W1), enableOnBackInvokedCallback=true:
                  every BACK key press logged "back invoked -> GameActivity.onKeyDown(KEYCODE_BACK)"
                  - GameActivity.onKeyDown was NOT called by the framework. On Android 15 and 16 the
                  same press reaches onKeyDown first and the callback logs "already handled".
Consequence:      Both delivery orders occur in the field; BackCompat + 0008 handle both. Scene
                  transitions on 14 (Coin Store -> Select Troops -> Status -> Title -> no-op;
                  battle -> PAUSE -> Select Troops) match the original.
```

## 17. Network requests fail in the test environment (TLS interception) - not a game defect

```text
Observed:         Android 14/15/16: Arena and discount requests to
                  https://fortconquer.droidhen.com fail with
                  javax.net.ssl.SSLHandshakeException: Trust anchor for certification path not found
                  at ArenaAgent$NetworkService.run(ArenaAgent.java:259).
Root cause:       The build sandbox sends outbound TLS through an intercepting proxy whose CA is not
                  trusted by the Android guests. Certificate validation works as designed.
Decision:         No trust-store change, no custom TrustManager, no cleartext fallback. The server's
                  real status remains unknown (documented in networking.md).
Validation:       game shows its own "Network error" (CANCEL / RETRY); CANCEL -> Status, Arena
                  energy 40/40, process alive.
```

## 18. Final acceptance runs

```text
Android 14 (final APK): install, launch, name/IME, stage 1 lost then won (11 kills, +157 coins,
  +2 crystals), stage 2 unlocked, market, evolve, coin store "Can't make purchases", Learn more ->
  browser, back navigation, battle PAUSE, Home/return and screen off/on with GL re-creation,
  cutout, force-stop persistence, Arena offline. No crash (PID unchanged until force-stop).
Android 15 (final APK): same set (12 kills, +117 coins, +1 crystal). No crash.
Android 16 (final APK, smoke): install, launch, audio state (MediaPlayer started 44.1 kHz),
  status, battle render, 5 BACK presses each handled once, cutout, relaunch. Full run: RC5.
Not exercised: gesture back (SystemUI), Android 17 (no image), real Play billing, live server.
Details and every PARTIAL / NOT TESTED cell: docs/compatibility/compatibility-matrix.md.
```

## 19. Samsung Galaxy S25 display profile (Android 16) and Android 17 review

```text
Request:          provide the APK for a Samsung Galaxy S25.
Finding:          the universal APK is the right file (no native code, density-independent
                  assets). No device-specific build was made.
Test:             Android 16 VM booted at 1080x2340 @450 dpi with an emulated punch-hole cutout
                  (136 px); final APK 6ec49dd5...ee2588. GL surface 2204x1080; name/IME, stage 1
                  won by drag-and-drop (12 kills, +114 coins, +1 crystal), market, evolve, coin
                  store dialog, BACK in menus/battle (handled once), Home and screen off/on with
                  GL re-creation, force-stop persistence: all PASS. TOTAL PSS 75 MB.
120 Hz:           not testable here; bytecode review shows game speed is time-based (entity
                  modifiers in seconds, Timer-based stage clock, currentTimeMillis income, card
                  cooldowns as ScaleAtModifier durations).
Android 17:       One UI 9 (Android 17) is rolling out to the S25 since late September 2026. Still no
                  Android 17 image reachable (redroid has none; dl.google.com blocked). Reviewed
                  Google's all-apps behaviour changes: no blocking issue found. NOT TESTED.
Docs:             docs/devices/samsung-galaxy-s25.md, docs/compatibility/android-17.md.
```
