# APK forensics — Fort Conquer 1.2.4 (versionCode 31)

All facts below were read from the binary APK (apktool 2.12.1 decode, JADX 1.5.3 on
`classes.dex` only, `keytool`, `apksigner`, a raw parse of the APK Signing Block, and on-device
`dumpsys package`). The APK filename was **not** treated as authoritative.

## 1. Identity

| Item | Value |
|---|---|
| File | `com.droidhen.fortconquer_v1.2.4-31_Android-4.1.apk` |
| SHA-256 | `933557dc1b5ba6c900b078689d269faf47c622fb3ea23acc3efa11b7c753cc92` |
| Size | 24,729,915 bytes, 652 ZIP entries (388 stored, 264 deflated) |
| Package | `com.droidhen.fortconquer` |
| versionName / versionCode | `1.2.4` / `31` |
| minSdkVersion | **16** (Android 4.1 — this is what "Android-4.1" in the filename refers to) |
| targetSdkVersion | **30** (Android 11) |
| compileSdkVersion | 30 (`platformBuildVersionName="11"`) |
| Built with | `Created-By: Android Gradle 3.6.0` (META-INF/MANIFEST.MF) |
| Native code | none (no `lib/` directory) — runs on every ABI |
| DEX | single `classes.dex`, format `035`, 5,280 classes, 41,495 method refs, 23,292 field refs |

### Signing

| Item | Value |
|---|---|
| Schemes present | v1 (JAR: `META-INF/HZSTUDIO.SF/.RSA`), v2 (`0x7109871a`), v3 (`0xf05368c0`) |
| Other signing-block entries | `0x2146444e` (Google Play metadata block), `0x42726577` (verity padding) |
| Certificate subject | `CN=hzstudio, OU=hzstudio, O=hzstudio, L=hz, ST=zj, C=86` |
| Validity | 2009-11-30 → 2037-04-17, serial `4b1356cb` |
| Key / algorithm | RSA 1024-bit, SHA1withRSA |
| Cert SHA-256 | `D3:5F:30:D8:00:48:C9:96:95:62:98:2F:3E:F5:52:05:E4:EB:F0:78:42:6D:89:12:02:D8:E9:3E:38:D7:87:22` |
| Cert SHA-1 | `0C:A3:00:39:71:93:11:5E:7C:D6:CA:8E:01:61:75:F8:90:B1:4E:ED` |

The certificate's L/ST fields (`hz`/`zj`, i.e. Hangzhou, Zhejiang) and the Play metadata block
are consistent with a Play-distributed DroidHen build, but the certificate itself does not name
DroidHen and this project cannot verify key ownership. The private key is of course not
available, so the preservation build is re-signed with a different key (see
[signing.md](signing.md)).

## 2. Manifest (decoded)

```xml
<uses-permission WRITE_EXTERNAL_STORAGE, ACCESS_NETWORK_STATE, INTERNET, WAKE_LOCK,
                 com.android.vending.BILLING, READ_PHONE_STATE, ACCESS_WIFI_STATE,
                 com.google.android.gms.permission.AD_ID,
                 com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE,
                 FOREGROUND_SERVICE/>
<supports-screens anyDensity normal large xlarge (smallScreens=false)/>
<application name="androidx.multidex.MultiDexApplication"
             appComponentFactory="androidx.core.app.CoreComponentFactory"
             icon="@drawable/icon" label="@string/app_name">
  <activity name="com.droidhen.fortconquer.GameActivity"            <!-- only game activity -->
            launchMode="singleTask" theme="@android:style/Theme.NoTitleBar.Fullscreen"
            configChanges="keyboard|keyboardHidden|orientation|screenLayout|uiMode|screenSize|smallestScreenSize">
    MAIN / LAUNCHER intent filter, no android:exported attribute
  </activity>
  PlayCoreMissingSplitsActivity, PlayCoreDialogWrapperActivity  (enabled="false")
  AssetPackExtractionService (enabled="false", exported="true")
  com.android.billingclient.api.ProxyBillingActivity  (Play Billing Library 3.0.0)
  com.google.android.gms.ads.AdActivity
  meta-data com.google.android.play.billingclient.version=3.0.0, com.google.android.gms.version
</application>
```

Findings:

* **No `android:screenOrientation`** — landscape is requested at runtime by AndEngine
  (`setRequestedOrientation(SCREEN_ORIENTATION_LANDSCAPE)` from `EngineOptions.ScreenOrientation.LANDSCAPE`).
* **No receivers, no providers, no network-security config, no backup rules, no `<queries>`,
  no `<uses-library>`, no `<uses-feature>`.** `allowBackup` is the platform default (true).
* The launcher activity has an intent filter but no `android:exported` → **cannot be installed
  with targetSdk ≥ 31** without adding it.
* `ReferrerReceiver` exists in the dex but is no longer a `BroadcastReceiver` and is not declared.

## 3. Code inventory (`classes.dex`)

| Package | Classes | Role |
|---|---:|---|
| `com.droidhen.fortconquer` | 310 | Game: activity, scenes, units, stages, sounds, kits, layers |
| `com.droidhen.framework` | 34 | DroidHen framework: `PurchaseManager`, `IabHelper` (AIDL billing v3), `DataProvider` (SQLite purchase ledger), `PreferencesHelper`, `DeviceHelper`, `CrashDumpUploader`, `net.*` |
| `com.droidhen.andplugin` | 24 | DroidHen AndEngine extensions: `.plist` sprite sheets (`PlistCache`, `PlistTexture*`), Flash motion data (`FlashMotionCache`) |
| `org.anddev.andengine` | 520 | **AndEngine GLES1 branch** (2012). Same 519 classes as the stray `bin/andengine.jar` (built 2012-11-28) + a generated `BuildConfig` |
| `com.google.android.gms.*` | ~2,170 | play-services-ads/-lite/-base/-basement/-gass/-identifier **15.0.1** (2018) |
| `com.google.android.play.core` | 423 | Play Core (unused by game code) |
| `com.android.billingclient` | 79 | Play Billing Library **3.0.0** (unused by game code) |
| `com.android.installreferrer` | 12 | Install Referrer (unused by game code) |
| `androidx.*`, `android.support.*` | ~1,300 | AndroidX 1.0.0 family (appcompat, core, fragment, lifecycle 2.0.0, multidex, browser …) |
| `com.android.vending.billing` | 1 | `IInAppBillingService` AIDL stub |

References *from game/engine code* (`com.droidhen.*`, `org.anddev.*`) to SDKs: only
`com.google.android.gms.ads` (3 classes: `AdController`, `GameActivity`, its runnables) and
`com.android.vending.billing` (2 classes). Billing Library 3, Install Referrer, Play Core and
AndroidX are packaged but never called by the game; AndroidX is used only via the manifest
(`MultiDexApplication`, `CoreComponentFactory`).

### Entry points and engine

* `GameActivity` → `DroidhenGameActivity` → `org.anddev.andengine.ui.activity.BaseGameActivity`.
* AndEngine **GLES 1.x** renderer through its *own copy* of `GLSurfaceView`
  (`org.anddev.andengine.opengl.view.GLSurfaceView`, Android-1.x era), EGL10 via `EGLContext.getEGL()`,
  `SimpleEGLConfigChooser`. The GL thread is torn down on every pause and recreated on resume.
* Engine options: camera `SmoothCamera` 800×480, `FillResolutionPolicy` (stretch to fill),
  `ScreenOrientation.LANDSCAPE`, sound + music enabled, touch handled on the update thread,
  VBO extension disabled.
* Threads: main/UI, AndEngine `UpdateThread`, `GLThread`, executor threads for network.
* Scenes: `LogoScene`, `StartMenuScene`, `StatusScene`, `UnitSelectScene`, `UnitEvolveScene`,
  `UpgradeScene`, `ShopScene`, `CoinStoreScene`, `GameScene` (+ `GamePauseDlg`,
  `StageCompleteDlg`), `ArenaScene` (+ `ArenaCompleteDlg`).

### Persistence (all app-private internal storage)

| Store | API | Content |
|---|---|---|
| `shared_prefs/com.droidhen.fortconquer_preferences.xml` | `PreferenceManager.getDefaultSharedPreferences` (`CCPrefs`, `ReferrerReceiver`, AndEngine `SimplePreferences`) | progress, coins/crystals ("safe ints" with MD5 check values), cards, settings, player name, discount cache |
| `shared_prefs/default.xml` | `PreferencesHelper` | framework key/values (Base64 "secure strings") |
| `databases/record.db` | `DataProvider` (SQLiteOpenHelper) | processed purchase order IDs (dedupe) |
| `files/AllMotions.oos` | `openFileOutput` | regenerated motion cache, only if the bundled asset cannot be read |

External storage: `Player.readFileFromSD()` reads `/mnt/sdcard/infinitewar.txt`, but its only
caller `Player.recoverRecord()` is never invoked (dead code). AndEngine's external-storage texture
sources are not used. → **No external-storage access in any live code path.**

### Networking

* `DiscountManager` (started from `GameActivity.onCreate`) and `ArenaAgent` (online "Arena" PvP
  matchmaking) use **Apache HttpClient** (`DefaultHttpClient`, `HttpGet`) against the cleartext
  endpoint `http://fortconquer.droidhen.com/FortConquer/game/Main.php` (functions
  `discountSell`, `getTapjoyData`, `matchBattlePlayer`), on single-thread executors, 5–8 s timeouts.
* `DiscountManager` may additionally download a notice bitmap from a server-supplied URL.
* `CrashDumpUploader` / `FileUploadRequest` (HttpURLConnection) exist but are never called.
* The Apache classes are **not bundled** and the manifest lacks `<uses-library
  android:name="org.apache.http.legacy">` — see [baseline-failures](baseline-failures/).

### Billing

`PurchaseManager` → `IabHelper` (Google's 2013 sample helper) binding
`com.android.vending.billing.InAppBillingService.BIND` (AIDL In-app Billing **v3**), RSA public
key embedded, request code 9956, consumable SKUs `fortconquer1..6`, `discount_fortconquer1..6`,
`gift_fortconquer1..3`. Purchases are de-duplicated by order ID in `record.db`. Failure path:
`supportPurchase=false` → "Can't make purchases" dialog with a "Learn more" link to
`http://market.android.com/support/bin/answer.py?answer=1050566…`. See [billing.md](billing.md).

### Ads / analytics / other SDK wrappers

* `AdController`: AdMob banner, unit `ca-app-pub-6247246961848012/3820129282`, created only when
  the player never bought coins.
* `TapjoyHelper`, `UMengHelper`: **already empty stubs** in 1.2.4 (no Tapjoy/UMeng SDK in the dex).
* `GameActivity.openMoreGameScreen/openRateScreen/showInterstitial`: empty.

### Reflection / non-SDK interfaces

Only on the app's own classes: `GameActivity.onDestroy()` → `cleanStatic()`/`cleanChildren()` null
out **non-final** static/instance fields via reflection. No hidden-API access was found in game
or engine code.

### Deprecated / legacy APIs used by game + engine

`View.setSystemUiVisibility` (immersive sticky), `Theme.NoTitleBar.Fullscreen`/`FLAG_FULLSCREEN`,
`onKeyDown(KEYCODE_BACK)` back handling, `Display.getMetrics`, `TelephonyManager.getDeviceId`
(guarded), `Settings.System "android_id"`, `SoundPool(int,int,int)`, `PowerManager.newWakeLock`
(`SCREEN_BRIGHT|ON_AFTER_RELEASE`), Apache HttpClient, AIDL billing v3. Details and the decision
for each are in [deprecated-api-audit.md](deprecated-api-audit.md).

## 4. Assets and resources

| Area | Files | Bytes | Notes |
|---|---:|---:|---|
| `assets/gfx/**` | 138 | 20.4 MB | 62 PNG atlases + 62 matching `.plist` frame descriptors (cocos2d/TexturePacker XML plist), 13 JPG backgrounds |
| `assets/gfx/xml/AllMotions.oos` | 1 | 281,523 | **Java-serialized** `HashMap<String, FlashMotionCache$MotionInfo>` (unit animation key frames) |
| `assets/mfx` | 27 | 1,347,535 | OGG Vorbis music and sound effects |
| `assets/font` | 2 | 716,916 | `ArnoPro-Bold.otf`, `CONSTAN.TTF` |
| `res/` | 541 | 3.0 MB | mostly AndroidX/Play resources; game uses `drawable/icon`, strings, a few layouts |
| `ext/img`, `uml/`, `bin/` (`andengine.jar`, `jarlist.cache`) | 19 | ~1.1 MB | build leftovers from the AndEngine project, never loaded |

Important asset directories: `gfx/bg` (stage backgrounds), `gfx/cover` (title), `gfx/common`,
`gfx/game_common`, `gfx/store`, `gfx/unit/{biped,quad,dragon}`, `gfx/unit_select`,
`gfx/unit_evolve`.

**Localisation**: game text is largely baked into textures. `GameActivity.initLocale()` selects
Korean (`*_kr`) or Indonesian (`*_yn`) texture variants for `Locale.KOREA` / `in_ID`; everything
else uses English. `res/values-*` provides 85 qualifier folders, almost all from libraries; the
game's own strings are translated for `in` and `ko`.

**Preservation-critical detail**: `FlashMotionCache$MotionInfo` and `$KeyFrameInfo` declare
explicit `serialVersionUID`s (`0x30ec5fb14c5b6aab`, `0x0fdf30eb971eb338`). Recompiling these
classes from decompiled Java would risk breaking deserialisation of every unit animation; the
preservation build therefore keeps the original bytecode (smali) unchanged.

**Unusual resource handling**: `apktool.yml` records `forcedPackageId: 127` and a
`doNotCompress` list (`arsc`, `jpg`, `ogg`, `png`, AndroidX version files); the rebuild honours it,
so media stay stored (uncompressed) as in the original.

## 5. Technology stack summary

| Layer | Original technology |
|---|---|
| Language | Java (D8-dexed, AGP 3.6.0) |
| Engine | AndEngine GLES1 (anddev.org, 2012) + DroidHen `andplugin` |
| Graphics | OpenGL ES 1.x, EGL10, engine-private `GLSurfaceView` copy |
| Audio | `SoundPool` (effects) + `MediaPlayer` (music) via AndEngine audio managers and `SoundPlayer` |
| Input | `View.OnTouchListener` on the GL view, processed on AndEngine's update thread |
| Persistence | SharedPreferences, SQLite, internal files |
| Network | Apache HttpClient (platform legacy), cleartext HTTP, JSON |
| Billing | AIDL In-app Billing v3 (`IabHelper`); Billing Library 3.0.0 packaged but unused |
| Ads | play-services-ads 15.0.1 |
| Support libs | AndroidX 1.0.0 (multidex, core) |
