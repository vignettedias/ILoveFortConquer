# Unlimited-gems cheat variant

**This is a cheat build, made at the repository owner's request.** It is *not* the preservation
build and does not follow the preservation rule that no currency is ever granted
([billing.md](../billing.md)). The preservation build in [`dist/`](../../dist/) is unchanged.

| | |
|---|---|
| File | [`dist/unlimited-gems/FortConquer-1.2.4-Modern-Android-UnlimitedGems.apk`](../../dist/unlimited-gems/) |
| SHA-256 | `0ee2dea04cfc7c0d4934a0221c20505582a34271d208ab7d9384b226ab0f2fa7` |
| versionName / versionCode | `1.2.4-modern-unlimited-gems` / 31 |
| Package | `com.droidhen.fortconquer` (same as the preservation build) |
| Signing certificate | same preservation key as `dist/` (SHA-256 `bbdd17d4…6e976634`) |

## What it changes

Everything from the preservation build, plus three patches in
[`patches/variants/unlimited-gems/`](../../patches/variants/unlimited-gems/):

| Patch | Change |
|---|---|
| `9001-unlimited-crystals.patch` | `Player.getCrystals()` always returns 99,999 (the game's own cap), and `Player.changeCrystals()` always succeeds and keeps the stored balance at 99,999, so spending gems never reduces them. The save is written through the game's own checksummed `setSafeInt`, so it stays valid |
| `9002-arena-offline.patch` | Arena requests go to `arena-disabled.invalid`, a reserved name that can never resolve, so Arena always ends in the game's own "Network error" dialog. This keeps cheated teams and results away from other players on the shared Arena server, in case it is still running |
| `9003-variant-version-name.patch` | versionName `1.2.4-modern-unlimited-gems`, so the build is recognisable in Settings → Apps |

Not changed: coins, the billing code (the coin store still shows "Can't make purchases"; no fake
purchases), the discount request, gameplay and balance otherwise.

## Install

It uses the same package name and signing key as the preservation build, so it installs **as an
update over the preservation build and keeps your progress** (verified). No uninstall needed.
On a Galaxy phone, Auto Blocker must be off for the install
([S25 notes](../devices/samsung-galaxy-s25.md)). It cannot be installed over the *original*
DroidHen APK (different key) — uninstall that first.

## Tested (Android 16, Galaxy S25 display profile)

Android 16 VM at 1080 × 2340 with a punch-hole cutout, as in the
[S25 notes](../devices/samsung-galaxy-s25.md). Screenshots:
[evidence/unlimited-gems/](../evidence/unlimited-gems/).

| Check | Result |
|---|---|
| Installed over the preservation build | PASS — same profile kept, gems show 99999 |
| Fireball in battle (4 gems) | PASS — can be dragged onto a lane (it is only allowed when affordable); gems stay 99999 |
| Stage 1 won in the cheat build | PASS — rewards credited, gems still 99999 |
| Market **Refresh** (4 gems) | PASS — new cards, gems still 99999 |
| Market **Card Pack ×8** (45 gems) | PASS — 8 cards added (card count 3 → 11), gems still 99999 |
| Force-stop and relaunch | PASS — 99999; the save stays valid (`TOTAL_CRYSTAL` = 99999 with matching checksum) |
| Arena | PASS (offline by design) — `UnknownHostException: arena-disabled.invalid`, game shows "Network error", nothing sent to DroidHen's server |

Not tested: Super Evolve (blocked by the tutorial at the point it was tried; it uses the same
`changeCrystals()` call as the market), a real phone, Android 14/15/17.

## Things to know

* **Going back to the normal build keeps the gems.** The save stores 99999 gems, so reinstalling
  the preservation build over this one (same key, verified) shows 99999 gems, which then get spent
  normally — and Arena is online again in that build.
* Coins are not changed; only gems (crystals).
* This build is for personal, offline play. The preservation build's documentation, test
  results and release notes describe the normal APK, not this variant.

## Building it

```sh
./gradlew -Pfc.variant=unlimited-gems clean assembleRelease
```

The variant builds in `build/fc-unlimited-gems/` and publishes to `dist/unlimited-gems/` (APK,
`.sha256`, `.metadata.txt`). Without `-Pfc.variant` the normal preservation APK is built exactly
as before (verified byte-identical: `6ec49dd5…`).
