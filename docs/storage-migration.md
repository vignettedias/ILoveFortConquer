# Storage

**Result: no migration is needed, and none was performed.** Every live code path stores data in
app-private internal storage, which scoped storage (Android 10+) does not affect.

| Data | Location | API | Survives update / force-stop |
|---|---|---|---|
| Progress, coins, crystals, cards, settings, player name, Arena record, discount cache | `shared_prefs/com.droidhen.fortconquer_preferences.xml` | `PreferenceManager.getDefaultSharedPreferences` | Yes — verified on Android 14, 15, 16 (force-stop) and 16 (in-place update) |
| Framework key/values | `shared_prefs/default.xml` | `PreferencesHelper` | not exercised (created only by framework paths that did not run) |
| Processed purchase order IDs | `databases/record.db` | `SQLiteOpenHelper` | not created: no purchase can complete (see [billing.md](billing.md)) |
| Regenerated motion cache | `files/AllMotions.oos` | `openFileOutput` | only written if the bundled asset cannot be read; not observed |

Integrity values: coins, crystals, XP, levels and Arena counters are stored together with an MD5
"check" entry (`total_coin` + `total_coin_md5`, …). The preservation build does not change this
format or the code that reads it (not tested: transplanting a save between the two builds). The
original APK and the preservation build are signed with different keys and cannot be installed
over each other, so moving a save would need root access to copy the shared-preferences file. See [known-limitations.md](known-limitations.md).

External storage:

* `Player.readFileFromSD()` reads `/mnt/sdcard/infinitewar.txt`; its only caller,
  `Player.recoverRecord()`, is never invoked (dead code since at least 1.2.4).
* AndEngine's external-storage texture sources and `FileUtils` helpers are not used by the game.
* `WRITE_EXTERNAL_STORAGE` was therefore removed ([0005](../patches/0005-remove-unused-permissions.patch)).
  On Android 11+ it would grant nothing to an API 30+ target anyway.

Backup: `allowBackup` is left at the platform default (true), as in the original.
