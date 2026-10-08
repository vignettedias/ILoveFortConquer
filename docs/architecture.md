# Architecture

The preservation build is the **original APK, decoded and reassembled**, with a small, reviewable
set of changes on top. There is no decompiled-Java source tree: the game's bytecode is kept as it
was compiled in 2020 (AGP 3.6, D8), and every change is a patch you can read in a few lines.

## Repository layout

| Path | What it is |
|---|---|
| `com.droidhen.fortconquer_v1.2.4-31_Android-4.1.apk` | The unmodified reference APK (input; SHA-256 checked by the build) |
| `patches/` + `patches/series` | Ordered unified diffs against the apktool decode (smali + manifest + `apktool.yml`) |
| `compat/` | Java compatibility layer (`io.github.vignettedias.fortconquer.compat`), compiled against the API 36 class library, targeting Java 8 bytecode |
| `buildSrc/` | Build helpers: strict patch applier, portable zipalign, ZIP/alignment checks |
| `build.gradle.kts`, `settings.gradle.kts`, `gradle/` | Pipeline, pinned versions, dependency checksums, wrapper |
| `scripts/` | `build_release.sh`, `verify_apk.sh`, `analyze_apk.sh`, `hash_artifact.sh`, `collect_logcat.sh`, `make_patch.sh` |
| `tools/test-harness/` | How the Android 14/15/16 test VMs were built and driven (not part of the APK) |
| `docs/` | Documentation, evidence screenshots |
| `dist/` | Release APK, checksum, metadata |

## Build pipeline

See [build.md](build.md#pipeline). In short: verify reference → apktool decode → apply patches
strictly → compile compat layer to DEX → disassemble to smali → merge (duplicate classes fail
the build) → apktool build → align → sign → verify → publish.

The merged `classes.dex` stays a single DEX (41,519 method references, under the 65,536 limit;
the original has 41,495).

## Patch series

| Patch | Files | Change | Why |
|---|---|---|---|
| [0001](../patches/0001-apache-http-legacy-library.patch) | manifest | `<uses-library org.apache.http.legacy required=false>` | Start-up crash on Android 9+ ([baseline](baseline-failures/android-14.md)) |
| [0002](../patches/0002-target-api-36-manifest.patch) | manifest, `apktool.yml` | targetSdk 30 → 36, versionName `1.2.4-modern`, `exported="true"`, `appCategory="game"`, `enableOnBackInvokedCallback="true"`, compileSdk attributes 36 | Modern target API; required attributes; large-screen landscape; predictive-back opt-in |
| [0003](../patches/0003-compat-layer-hooks.patch) | `GameActivity`, `PurchaseManager$5` | call `GameCompat.onActivityCreated()` in `onCreate`, after AndEngine has set its render view as content view; "Learn more" uses `GameCompat.startActivitySafely()` | Installs cutout + back handling; no crash without a browser |
| [0004](../patches/0004-disable-legacy-admob.patch) | `AdController`, manifest | `showAdInLayout()` returns immediately; `AD_ID` permission removed | 2018 AdMob SDK crashes API 34+ targets; ad unit is the developer's ([details](ads-and-third-party-sdks.md)) |
| [0005](../patches/0005-remove-unused-permissions.patch) | manifest | remove `WRITE_EXTERNAL_STORAGE`, `READ_PHONE_STATE`, `BIND_GET_INSTALL_REFERRER_SERVICE`, `FOREGROUND_SERVICE` | Unused by any live code path / grant nothing on API 29+ (least privilege) |
| [0006](../patches/0006-legacy-endpoint-https.patch) | `DiscountManager(+$NetworkService)`, `ArenaAgent(+$NetworkService)` | `http://` → `https://` for `fortconquer.droidhen.com` | Cleartext is blocked by default; no global cleartext opt-in ([networking.md](networking.md)) |
| [0007](../patches/0007-billing-unavailable-dialog-ui-thread.patch) | `PurchaseManager` | `alertBillingNotSupport()` shows its dialog through `GameCompat.showDialogOnUiThread()` | Original bug: dialog built on the engine thread, exception swallowed, BUY did nothing |
| [0008](../patches/0008-back-key-exactly-once.patch) | `GameActivity` | first instruction of `onKeyDown` reports the key to `GameCompat.onKeyDown()` | Lets the back callback skip presses already delivered as `KEYCODE_BACK` |

Total: 10 distinct files touched; 25 lines added (including comments), 42 removed (mostly the AdMob
banner body).

## Compatibility layer

`compat/src/main/java/io/github/vignettedias/fortconquer/compat/`:

| Class | API level | Role |
|---|---|---|
| `GameCompat` | 16+ (verifies on every runtime) | Entry points called from the patched bytecode; version checks; no newer framework types in its signatures |
| `CutoutCompat` | 28+ | Pads the content view by `DisplayCutout` safe insets ([rendering-andengine.md](rendering-andengine.md#display-cutout-and-edge-to-edge)) |
| `BackCompat` | 33+ | `OnBackInvokedCallback` → original `onKeyDown(KEYCODE_BACK)`, exactly once ([lifecycle.md](lifecycle.md#back-navigation)) |
| `PreservationInfo` | — | Log tag `FCPreservation` and the "unofficial preservation build" description logged at start |

Newer framework types are confined to classes that are only loaded behind `Build.VERSION.SDK_INT`
checks, so the build still verifies and runs its original paths on old releases down to
`minSdkVersion` 16 (old releases were not tested).

## What is deliberately unchanged

Game logic, balance, timings, assets, AndEngine, the renderer, the 800×480 stretch, sounds,
save format, the billing implementation, the network protocol, the package name, versionCode,
`configChanges`, launch mode and theme. See [preservation-notes.md](preservation-notes.md).

## Variants

`patches/variants/<name>/` holds opt-in patch series applied on top of the preservation series
(`-Pfc.variant=<name>`, see [build.md](build.md#variants)). The only one is the
[unlimited gems + coins cheat build](variants/unlimited-gems-coins.md) — a separate APK in
`dist/unlimited-gems-coins/`; everything above describes the preservation build.
