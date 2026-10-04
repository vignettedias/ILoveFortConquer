# Ads and third-party SDKs

| SDK (version) | Used by the game? | Status in the preservation build |
|---|---|---|
| Google Mobile Ads / play-services-ads **15.0.1** (2018) | `AdController.showAdInLayout(Activity, RelativeLayout)` adds an AdMob banner view (only if the player never bought coins) | **Disabled** — `AdController.showAdInLayout()` returns immediately ([0004](../patches/0004-disable-legacy-admob.patch)); `AD_ID` permission removed |
| play-services-base/-basement/-gass/-identifier 15.0.1 | transitively, by the ads SDK | inert (never initialised) |
| Play Billing Library 3.0.0 | no (game uses AIDL billing) | left in place, inert |
| Play Core (assets/missing-splits) | no | left in place; its components are `enabled="false"` in the original manifest |
| Install Referrer | no | `BIND_GET_INSTALL_REFERRER_SERVICE` permission removed ([0005](../patches/0005-remove-unused-permissions.patch)); classes inert |
| AndroidX 1.0.0 (multidex, core, appcompat, …) | only `MultiDexApplication` / `CoreComponentFactory` from the manifest | unchanged |
| Tapjoy, UMeng | `TapjoyHelper`, `UMengHelper` are already empty stubs in 1.2.4 | unchanged |

## Why AdMob was disabled rather than updated

1. **It crashes the game** when the app targets API 34+: the 2018 SDK calls `registerReceiver()`
   without `RECEIVER_EXPORTED`/`RECEIVER_NOT_EXPORTED` and Android throws `SecurityException`
   ([engineering log #7](engineering-log.md#7-legacy-admob-sdk-crashes-the-game-when-targeting-api-34)).
2. **The ad unit is not ours.** `ca-app-pub-6247246961848012/3820129282` belongs to DroidHen's
   AdMob account. Serving it from an unofficial, re-signed build would be ad traffic the account
   holder never authorised.
3. Updating to a current Mobile Ads SDK means new code, Play services dependencies, consent
   (UMP) flows and policy obligations — and still the developer's ad unit. The project rules also
   exclude adding new advertising.

Effect for the player: no banner is added. The game itself is drawn by AndEngine's GL view, which
the banner was laid over; its size and input handling do not depend on the banner (all
gameplay screens were exercised without it).

Verified: no `admob`/ads shared-preferences file appears after a clean install and play session
on Android 14, 15 and 16.

The SDK classes remain in `classes.dex` (removing them would mean rewriting the multidex/D8
output and is unnecessary because nothing reaches them).
