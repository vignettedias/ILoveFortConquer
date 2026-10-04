# Preservation notes

## Principles applied

1. **Change the minimum that makes it run.** Each change answers an observed failure or a hard
   platform requirement of targeting API 36. Nothing was refactored or "improved".
2. **Keep the original bytecode.** The game is rebuilt from apktool's smali disassembly, not from
   decompiled Java. Decompilers do not round-trip (synthetic accessors, switch maps, generics,
   obfuscated names), and two classes must keep their exact `serialVersionUID`
   (`FlashMotionCache$MotionInfo` `0x30ec5fb14c5b6aab`, `$KeyFrameInfo` `0x0fdf30eb971eb338`)
   or the Java-serialized unit animations in `assets/gfx/xml/AllMotions.oos` stop loading.
3. **Keep the game's assets byte-identical.** All 167 files under `assets/` (texture atlases,
   backgrounds, `.plist` frame data, `AllMotions.oos`, music and effects, fonts) have the same
   bytes and the same storage method as in the original (`doNotCompress` is honoured). Android
   resources are recompiled by aapt2: `resources.arsc` and the 174 compiled XML files are
   re-encoded, the redundant `-v4` qualifier disappears from directory names (minSdk is 16), five
   AndroidX nine-patch PNGs are re-encoded and one redundant `layout-v16` variant is folded away.
   243 of the 248 resource bitmaps (including the launcher icon) are byte-identical. Measured
   by comparing CRC-32 and size of every ZIP entry against the original.
4. **Keep identity**: package `com.droidhen.fortconquer`, versionCode 31, label "Fort Conquer",
   icon. versionName becomes `1.2.4-modern` so the build is recognisable in Settings.
5. **Be honest about provenance.** The APK logs
   `Fort Conquer 1.2.4 - unofficial modern Android preservation build (not a DroidHen release)`
   at start, the signing certificate says "Unofficial preservation build", and the docs never
   present it as an official update.

## Preserved as-is (on purpose)

| Aspect | Note |
|---|---|
| Gameplay, balance, stage data, unit stats, prices, timers | untouched (no game-logic smali edited) |
| Rendering | AndEngine GLES1, 800×480 camera stretched to the screen, same frame logic |
| Audio | same `SoundPool`/`MediaPlayer` usage |
| Input | AndEngine touch on the update thread; BACK semantics identical (see [lifecycle.md](lifecycle.md)) |
| Save format | SharedPreferences keys and MD5 check values unchanged |
| Tutorial flow, texts, translations (ko / in textures) | unchanged |
| Online protocol | same requests; only the URL scheme is `https` |
| Billing code | unchanged apart from showing its own error dialog on the right thread |
| Recreation to the title screen on unhandled configuration changes | original behaviour |

## Changed (and why)

See the patch table in [architecture.md](architecture.md#patch-series). Player-visible effects:

* The game starts (instead of crashing) on Android 9 and later.
* No AdMob banner.
* On devices with a display cutout, the game sits inside the safe area (the original was
  letterboxed by the platform on that side — the same geometry).
* BACK works with the Android 13+ back system, including predictive-back devices.
* BUY in the coin store now shows "Can't make purchases" instead of doing nothing.
* "Learn more" can no longer crash the game on devices without a browser.

## Provenance of the input

* File: `com.droidhen.fortconquer_v1.2.4-31_Android-4.1.apk`, SHA-256
  `933557dc1b5ba6c900b078689d269faf47c622fb3ea23acc3efa11b7c753cc92`, committed by the
  repository owner. It carries a valid v1/v2/v3 signature by `CN=hzstudio` and a Google Play
  metadata block, consistent with a Play-distributed build. Who distributed this particular copy
  cannot be established from the file.
* Rights: Fort Conquer and its assets belong to their owners (DroidHen / the `hzstudio` signer).
  This repository contains no new game content; it documents and applies compatibility changes.
