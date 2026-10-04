# Known limitations

## Of the preservation build

| Limitation | Why | Workaround |
|---|---|---|
| Cannot be installed over the original game; uninstalling the original deletes its save | Different signing key (the original key is not available) — [signing.md](signing.md) | None without root. Start a new game |
| In-app purchases unavailable ("Can't make purchases") | AIDL billing retired; game delisted; key not the developer's — [billing.md](billing.md) | Currency is earned by playing |
| Online features (discounts, Arena PvP) depend on `fortconquer.droidhen.com` answering over **HTTPS** | Cleartext was not re-enabled — [networking.md](networking.md) | None if the server is gone or HTTP-only; the rest of the game is offline |
| No ads | Legacy AdMob crashes API 34+ targets and the ad unit is not ours | — |
| 800×480 scene stretched to the screen | Original engine behaviour | — |
| Configuration changes not listed in the original `configChanges` (e.g. switching gesture/3-button navigation, changing display size) restart the game at the title screen | Original behaviour, kept | Progress is saved; continue from Status |
| The published `dist/` APK's signing key was not retained | Generated in an ephemeral build environment | Rebuild with your own key for maintained installs |

## Of the testing

| Not covered | Consequence |
|---|---|
| Android 17 | No image available — **not tested** |
| Physical phones/tablets, OEM skins, vendor GPU drivers | Tested only on AOSP (redroid) in a VM with Mesa/virgl — [testing-environment.md](testing-environment.md) |
| Gesture-navigation (predictive) back | Test SystemUI did not deliver edge swipes; the code path is the callback path exercised on Android 14 |
| Audio audibility, audio focus with other apps | Only player state checked |
| Real Google Play environment | No Play services on the test images |
| Server compatibility | Sandbox TLS interception; the server's status is unknown |
| Older Android releases (minSdk 16 … 13) | Not tested; the compat layer is gated by API level |
| Upgrade with enough coins, RESUME on 14/15, in-place update on 14/15 | See the NOT TESTED / PARTIAL cells in the [matrix](compatibility/compatibility-matrix.md) |
