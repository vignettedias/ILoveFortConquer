# Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` / "App not installed as package conflicts with an existing package" | The original (or another preservation build) is installed and signed with a different key | Uninstall Fort Conquer first (this deletes its local save) |
| Samsung Galaxy: the install is blocked by Auto Blocker (APK from an unauthorised source) | One UI's Auto Blocker is on by default | Settings → Security and privacy → Auto Blocker → off, install, then turn it back on if you want ([S25 notes](devices/samsung-galaxy-s25.md)) |
| Play Protect warns or blocks the install | Unknown developer key | Review [signing.md](signing.md), compare the certificate SHA-256, then choose "Install anyway" if you trust the build |
| Game shows "Network error" in Arena | Server unreachable or not serving HTTPS | Expected when offline; CANCEL returns to Status without losing energy. See [networking.md](networking.md) |
| BUY shows "Can't make purchases" | Billing is unavailable for this build by design | — ([billing.md](billing.md)) |
| Game returns to the title screen after changing navigation mode, display size or a foldable posture | Configuration change outside the original `configChanges`; original behaviour | Tap START: progress is kept |
| BACK during the first (tutorial) battle or on GAME OVER does nothing | The original game ignores BACK there | Use the on-screen buttons |
| Black bars at the screen edge on a phone with a notch / punch-hole | The game is kept inside the cutout safe area; the original APK gets the same bar from the platform | — |
| Build fails at `verifyReferenceApk` | The APK in the repo root is not the expected file | Restore `com.droidhen.fortconquer_v1.2.4-31_Android-4.1.apk` (SHA-256 `933557dc…`) |
| Build fails with "hunk … does not apply" | A patch no longer matches the decoded tree (e.g. a modified reference APK or edited patch) | Regenerate the patch with `scripts/make_patch.sh` against `build/fc/patched` |
| Build fails with a dependency verification error | A downloaded tool does not match `gradle/verification-metadata.xml` | Do not override; check your network/proxy and retry. Update the metadata only for an intentional version change |
| `keytool failed` on the first build | No JDK `keytool` (JRE only) | Install a full JDK 17+ |
| `scripts/verify_apk.sh` reports misaligned entries | APK was modified after signing (e.g. re-zipped) | Use the APK from `dist/` as built |

Collecting logs for a bug report:

```sh
scripts/collect_logcat.sh dist/FortConquer-1.2.4-Modern-Android.apk out/ 90 <adb-serial>
adb logcat -s FCPreservation AndEngine AndroidRuntime
```

The `FCPreservation` tag logs the build description and Android version at start and every back
dispatch decision.
