# Baseline failure — original APK on Android 15 (API 35)

Device: redroid 15 `BP1A.250505.005.D1` in the test VM ([testing-environment.md](../testing-environment.md)).
APK: original 1.2.4 (SHA-256 `933557dc…7c753cc92`).

| Step | Result |
|---|---|
| `adb install` | **Success** |
| Launch | `am start` 20:14:18.2, first FATAL 20:14:22.7 (4.5 s); the process is started again and dies the same way |
| Status | **FAIL — crash loop, game unplayable** |

```text
10-04 20:14:22.673  2618  2683 E AndroidRuntime: FATAL EXCEPTION: pool-6-thread-1
10-04 20:14:22.673  2618  2683 E AndroidRuntime: Process: com.droidhen.fortconquer, PID: 2618
10-04 20:14:22.673  2618  2683 E AndroidRuntime: java.lang.NoClassDefFoundError: Failed resolution of: Lorg/apache/http/impl/client/DefaultHttpClient;
10-04 20:14:22.673  2618  2683 E AndroidRuntime:   at com.droidhen.fortconquer.kits.DiscountManager$NetworkService.run(DiscountManager.java:152)
...
10-04 20:14:24.918  2692  2739 E AndroidRuntime: FATAL EXCEPTION: pool-5-thread-1
10-04 20:14:24.918  2692  2739 E AndroidRuntime: Process: com.droidhen.fortconquer, PID: 2692
10-04 20:14:24.918  2692  2739 E AndroidRuntime: java.lang.NoClassDefFoundError: Failed resolution of: Lorg/apache/http/impl/client/DefaultHttpClient;
```

Two executor threads of the same process (the discount request is issued twice during start-up)
die with the same error; the restarted process (PID 2692) fails identically.

Root cause and fix: identical to [Android 14](android-14.md).

Android 15's edge-to-edge enforcement does **not** apply to the original APK (it targets API 30),
so it is not a baseline failure; it becomes relevant once the target API is raised — see
[compatibility/android-15.md](../compatibility/android-15.md).
