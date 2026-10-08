# Unlimited gems + coins cheat variant

**This is a cheat build, made at the repository owner's request.** It is *not* the preservation
build and does not follow the preservation rule that no currency is ever granted
([billing.md](../billing.md)). The preservation build in [`dist/`](../../dist/) is unchanged.

| | |
|---|---|
| File | [`dist/unlimited-gems-coins/FortConquer-1.2.4-Modern-Android-UnlimitedGemsCoins.apk`](../../dist/unlimited-gems-coins/) |
| SHA-256 | `eae99f872a6eb3443563350bb2f46e3098d9b13502b98bdcc128eaa6c067627b` |
| versionName / versionCode | `1.2.4-modern-unlimited-gems-coins` / 31 |
| Package | `com.droidhen.fortconquer` (same as the preservation build) |
| Signing certificate | same preservation key as `dist/` (SHA-256 `bbdd17d4…6e976634`) |

It replaces the earlier gems-only cheat APK (`…-UnlimitedGems.apk`, SHA-256 `0ee2dea0…`), which
is still in the Git history (commit `a4815ea`).

## What it changes

Everything from the preservation build, plus three patches in
[`patches/variants/unlimited-gems-coins/`](../../patches/variants/unlimited-gems-coins/):

| Patch | Change |
|---|---|
| `9001-unlimited-gems-and-coins.patch` | `Player.getCrystals()` / `getCoins()` always return the game's own caps — **99,999 gems** and **9,999,999 coins** — and `changeCrystals()` / `changeCoins()` always succeed and keep the stored balances there, so spending never reduces them. Saves are written through the game's own checksummed `setSafeInt`, so they stay valid |
| `9002-arena-offline.patch` | Arena requests go to `arena-disabled.invalid`, a reserved name that can never resolve, so Arena always ends in the game's own "Network error" dialog. This keeps cheated teams and results away from other players on the shared Arena server, in case it is still running |
| `9003-variant-version-name.patch` | versionName `1.2.4-modern-unlimited-gems-coins`, so the build is recognisable in Settings → Apps |

Not changed: the billing code (the coin store still shows "Can't make purchases"; no fake
purchases), the discount request, gameplay and balance otherwise.

## Install

Same package name and signing key as the preservation build and the earlier gems-only cheat build,
so it **installs as an update over either of them and keeps your progress** (verified for both).
On a Galaxy phone, Auto Blocker must be off for the install
([S25 notes](../devices/samsung-galaxy-s25.md)). It cannot be installed over the *original*
DroidHen APK (different key) — uninstall that first.

## Tested (Android 16, Galaxy S25 display profile)

Android 16 VM at 1080 × 2340 with a punch-hole cutout, as in the
[S25 notes](../devices/samsung-galaxy-s25.md). Screenshots:
[evidence/unlimited-gems-coins/](../evidence/unlimited-gems-coins/).

| Check | Result |
|---|---|
| Normal build → gems-only cheat → this build, each installed over the previous | PASS — same profile kept; 9,999,999 coins and 99,999 gems |
| Stage 1 lost once (+71 coins bonus), then won (+149 coins, +2 gems) | PASS — balances stay at the caps |
| Market card for 308 coins | PASS — card added (4 → 5 cards), coins still 9,999,999 |
| City Wall upgrade (1,000 coins) and Stope upgrade (10 gems) | PASS — both reach level 2, balances unchanged |
| Force-stop and relaunch | PASS — balances kept; save values 9,999,999 / 99,999 with matching checksums |
| Arena | PASS (offline by design) — `UnknownHostException: arena-disabled.invalid`, game shows "Network error", nothing sent to DroidHen's server |
| No crash (same process from install to the deliberate force-stop; crash log empty) | PASS |

The earlier gems-only build was also tested with gem purchases (market Refresh for 4 gems, an
8-card pack for 45 gems); this build uses the same gem change. Not tested: a real phone,
Android 14/15/17.

## Things to know

* **Going back to the normal build keeps the balances.** The save stores 9,999,999 coins and
  99,999 gems, so reinstalling the preservation build over this one (same key) shows them, and
  they are then spent normally — and Arena is online again in that build. (Verified with the
  gems-only build; coins are saved the same way.)
* This build is for personal, offline play. The preservation build's documentation, test
  results and release notes describe the normal APK, not this variant.

## Building it

```sh
./gradlew -Pfc.variant=unlimited-gems-coins clean assembleRelease
```

The variant builds in `build/fc-unlimited-gems-coins/` and publishes to
`dist/unlimited-gems-coins/` (APK, `.sha256`, `.metadata.txt`). Without `-Pfc.variant` the normal
preservation APK is built exactly as before (`6ec49dd5…`).
