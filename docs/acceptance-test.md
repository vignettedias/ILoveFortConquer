# Gameplay acceptance test (22 steps)

The test a preservation build has to pass on each Android version. It exercises everything a
player touches plus the platform behaviours that changed between Android 9 and 16. Results per
version are in [compatibility/compatibility-matrix.md](compatibility/compatibility-matrix.md);
the environment is described in [testing-environment.md](testing-environment.md).

Start from a device where Fort Conquer has never been installed (or uninstall it: the original
APK and the preservation build are signed with different keys).

| # | Step | Expected (original game behaviour) |
|---:|---|---|
| 1 | Install `dist/FortConquer-1.2.4-Modern-Android.apk` | Installs; no "built for an older version" warning |
| 2 | Launch, wait 60 s | DroidHen logo, then title screen (music + sound icons, START); no crash |
| 3 | START → tap the name panel, type a name, OK | Status screen; native "Please input your name" dialog with soft keyboard; name shown after OK |
| 4 | LOCAL START → select three troops | Select Troops screen; cards move into the team slots |
| 5 | Start the battle; follow the tutorial; drag troop cards onto the field | Tutorial overlays; troops spawn in the lane the card is dropped on (touch mapping correct) |
| 6 | Lose a battle | GAME OVER with consolation bonus; RETRY returns to Select Troops |
| 7 | Win stage 1 | VICTORY with kill/life/stage bonus; coins, crystals and XP credited; stage 2 unlocked |
| 8 | Market: buy a card | Coins deducted, card added |
| 9 | Evolve: combine three cards | Evolution animation, new card |
| 10 | Upgrade: City Wall / Stope | Upgrade dialog; upgrade with enough coins, or redirect to the coin store if not |
| 11 | Coin store: tap BUY | "Can't make purchases" dialog (billing unavailable); **no coins or crystals granted** |
| 12 | "Learn more" in that dialog | Opens the browser; with no browser installed the game stays open (no crash) |
| 13 | BACK on menu screens | Returns to the previous scene; on the title screen BACK does nothing and the game stays in front |
| 14 | BACK during a battle, then BACK again | First press opens PAUSE; second press quits to Select Troops (one action per press) |
| 15 | RESUME in the PAUSE dialog | Battle continues |
| 16 | Home during a battle, then return | Battle is still there (auto-paused), graphics intact |
| 17 | Screen off / on during a battle | Same as 16 |
| 18 | Force-stop, relaunch | Name, coins, crystals, stage, XP, cards unchanged |
| 19 | Install the same APK again over itself (update) | Progress unchanged |
| 20 | Arena (unlocked after stage 5) without a reachable server | Game's own "Network error" dialog; CANCEL returns to Status; energy not consumed; no crash |
| 21 | Device with a display cutout | Whole game visible, nothing under the cutout; touch still lands on the right buttons |
| 22 | Audio | Music on menus, effects in battle; toggles on the title screen work |

Arena needs five cleared stages. For step 20 the test devices reached it with a **test-only edit
of the local save** (`level_passed0` set to 5 in the app's shared preferences, done as root on a
userdebug image); the APK was not modified for this.
