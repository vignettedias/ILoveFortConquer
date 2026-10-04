# Deprecated and legacy API audit

Scope: code that actually runs — the game (`com.droidhen.fortconquer`), the DroidHen framework
(`com.droidhen.framework`, `com.droidhen.andplugin`) and AndEngine (`org.anddev.andengine`).
Bundled SDKs the game never calls (Billing Library 3, Play Core, Install Referrer, most of
AndroidX) are listed in [ads-and-third-party-sdks.md](ads-and-third-party-sdks.md).
Inventory produced with [`scripts/analyze_apk.sh`](../scripts/analyze_apk.sh) and manual reading of
the smali.

Decision rule: change only what is broken or unsafe on API 36. A deprecated API that still works
stays, because changing it would change the 2012 engine's behaviour for no player-visible gain.

| API / behaviour | Where | Status for a targetSdk-36 app on Android 14–16 | Decision |
|---|---|---|---|
| Apache HttpClient (`org.apache.http.*`) | `DiscountManager`, `ArenaAgent` | Not on the class path for apps targeting API 28+ → `NoClassDefFoundError` (the baseline crash) | **Fixed**: `<uses-library org.apache.http.legacy required=false>` ([0001](../patches/0001-apache-http-legacy-library.patch)). Rewriting to `HttpURLConnection` was rejected: larger change, same result |
| Cleartext HTTP | same | Blocked by default for API 28+ targets (already the case for the original, which targets 30) | **Changed** the endpoint to `https://` ([0006](../patches/0006-legacy-endpoint-https.patch)); cleartext stays disabled. See [networking.md](networking.md) |
| `onKeyDown(KEYCODE_BACK)` as the only back handler | `GameActivity` → `SmartScene` subclasses | Not called for back gestures; key back routed differently per release (see [lifecycle.md](lifecycle.md#back-navigation)) | **Bridged** with `OnBackInvokedCallback` ([BackCompat](../compat/src/main/java/io/github/vignettedias/fortconquer/compat/BackCompat.java), [0003](../patches/0003-compat-layer-hooks.patch), [0008](../patches/0008-back-key-exactly-once.patch)) |
| Full-screen window without cutout handling | `GameActivity` | API 35+ targets are forced edge-to-edge; the GL view would extend under the cutout | **Fixed**: safe-area padding ([CutoutCompat](../compat/src/main/java/io/github/vignettedias/fortconquer/compat/CutoutCompat.java)) |
| `android:exported` missing on the launcher activity | manifest | Install refused for API 31+ targets | **Fixed** ([0002](../patches/0002-target-api-36-manifest.patch)) |
| Off-UI-thread `AlertDialog.create()` | `PurchaseManager.alertBillingNotSupport()` (called from AndEngine's update thread) | Throws (no Looper) on every Android version; exception swallowed → BUY silently does nothing | **Fixed** (original bug): dialog shown on the UI thread ([0007](../patches/0007-billing-unavailable-dialog-ui-thread.patch)) |
| `startActivity()` for "Learn more" without a handler check | `PurchaseManager` | `ActivityNotFoundException` kills the game on devices without a browser | **Guarded** (`GameCompat.startActivitySafely`, [0003](../patches/0003-compat-layer-hooks.patch)) |
| play-services-ads 15.0.1 `registerReceiver` without export flag | `AdController.showAdInLayout` | `SecurityException` at start for API 34+ targets | **Disabled** ([0004](../patches/0004-disable-legacy-admob.patch)) |
| `TelephonyManager.getDeviceId()` | `DeviceHelper.getDeviceId()` (sent with Arena requests) | Throws `SecurityException` for third-party apps targeting API 29+, with or without `READ_PHONE_STATE` | **Kept**: the call is wrapped in a catch-all in the original code. `READ_PHONE_STATE` removed because it can no longer grant anything ([0005](../patches/0005-remove-unused-permissions.patch)) |
| `Settings.System.getString("android_id")` | `AppContext`, `DeviceHelper` | Moved to `Settings.Secure` in API 17; the platform still forwards the read (per-app value since Android 8) | Kept |
| `View.setSystemUiVisibility()` (immersive sticky) | `GameActivity` | Deprecated in API 30, still functional | Kept — the game is full-screen on 14/15/16 in every screenshot |
| `Theme.NoTitleBar.Fullscreen` / `FLAG_FULLSCREEN` | manifest, `GameActivity` | Deprecated in API 30, still functional | Kept |
| `Display.getMetrics()` | `GameActivity` | Deprecated in API 30, returns the app window's metrics | Kept (AndEngine's `FillResolutionPolicy` scales to the view size anyway) |
| `SoundPool(int, int, int)` | AndEngine `SoundManager` | Deprecated in API 21; logs `Use of stream types is deprecated` | Kept — sound effects player created and functional |
| `PowerManager.newWakeLock(SCREEN_BRIGHT \| ON_AFTER_RELEASE)` | AndEngine `BaseGameActivity` | Deprecated flags, still honoured; needs `WAKE_LOCK` | Kept (`WAKE_LOCK` kept) |
| `AsyncTask` | AndEngine `ActivityUtils` (progress-dialog helpers) | Deprecated in API 30 | Kept; not on any path the game uses |
| `Environment.getExternalStorage*` | `Player.readFileFromSD()` (dead code), AndEngine `FileUtils` (unused) | Scoped storage would deny it | Kept; never executed. `WRITE_EXTERNAL_STORAGE` removed — see [storage-migration.md](storage-migration.md) |
| AIDL In-app Billing v3 (`IInAppBillingService`) | `IabHelper` | Retired by Google Play; the Play service is also invisible to API 30+ targets without `<queries>` | Kept as is: binding fails → original "billing unavailable" path. See [billing.md](billing.md) |
| Java serialization of motion data (`AllMotions.oos`) | `FlashMotionCache` | Works; depends on unchanged `serialVersionUID`s | Kept — original bytecode is preserved exactly ([preservation-notes.md](preservation-notes.md)) |
| Reflection on the game's own fields | `GameActivity.cleanStatic()` / `cleanChildren()` | Skips `final` fields (`Modifier.isFinal` check), so unaffected by restrictions on modifying `static final` fields | Kept |
| `setRequestedOrientation(LANDSCAPE)` at runtime | AndEngine `BaseGameActivity` | Ignored on ≥ 600 dp displays for API 36 targets unless the app is a game | `android:appCategory="game"` ([0002](../patches/0002-target-api-36-manifest.patch)) |

No non-SDK (hidden) API use was found in game or engine code.
