# Billing

## Original implementation

* `PurchaseManager` → `IabHelper` (Google's 2013 sample helper) binds
  `com.android.vending.billing.InAppBillingService.BIND` — **AIDL In-app Billing v3**.
* Consumable SKUs `fortconquer1..6` (coins / crystals), `discount_fortconquer1..6`,
  `gift_fortconquer1..3`; developer RSA public key embedded for signature checks; purchases
  de-duplicated by order ID in `databases/record.db`.
* Play Billing Library 3.0.0 is packaged (and declared in the manifest) but never called.

## Why purchases cannot work

* Google Play retired the AIDL billing API; current Play Store versions reject it.
* An app targeting API 30+ cannot even see `com.android.vending`'s billing service without a
  `<queries>` entry (the original already targets 30 and has none), so the bind fails.
* Fort Conquer is no longer listed on Google Play; its products are not purchasable from any
  current listing, and the preservation build's signing key is not the developer's.
* The test devices have no Play Store at all.

## What the preservation build does

* **Nothing is unlocked, granted or emulated.** No purchase flow, verification or currency
  grant was modified; no fake responses exist.
* The original "billing unavailable" path is kept: BUY shows Android's
  "Can't make purchases — The Market billing service is not available at this time…" dialog with
  OK / Learn more, and the balance does not change.
* Fixed an original bug that hid that dialog: the coin store calls the purchase code from
  AndEngine's update thread, where `AlertDialog.create()` throws; the exception was swallowed and
  BUY did nothing visible. The original dialog is now built as before and shown on the UI thread
  ([0007](../patches/0007-billing-unavailable-dialog-ui-thread.patch), `GameCompat.showDialogOnUiThread`).
* "Learn more" opens the original help URL in a browser; with no browser it no longer crashes
  (`GameCompat.startActivitySafely`).

Verified on Android 14, 15 and 16 (coin store BUY → dialog, coins/crystals unchanged).

Migrating to Play Billing Library 7/8 was deliberately **not** done: it would need the product
IDs to be live on a Play listing owned by the developer, i.e. it can only be done by DroidHen.
In-game currency is still earned normally through play (battle rewards, Arena when online).
