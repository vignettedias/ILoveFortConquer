# Testing environment

All runtime results in this repository come from the environment described here. Read this
before relying on a PASS: it is real Android system software, but it is **not a phone**.

## What was available

* A Linux x86_64 build container: 4 vCPUs, no `/dev/kvm`, no physical devices, no Android SDK
  emulator images (the Google Maven / `dl.google.com` hosts were not reachable).
* Outbound HTTPS only through a TLS-intercepting proxy (relevant for the network tests below).

## What was built

| Layer | Choice | Why |
|---|---|---|
| Hypervisor | QEMU 8.2 system emulation, TCG (`-accel tcg,thread=multi`), 4 vCPUs, 6 GiB | no KVM |
| Guest kernel | Ubuntu `6.8.0-146-generic` (stock), `binder_linux` module | redroid needs binder; GKI kernels are not bootable on QEMU's PC machine without extra work |
| ashmem | [redroid-modules](https://github.com/remote-android/redroid-modules) `ashmem`, ported to 6.8 ([patch](../tools/test-harness/ashmem/ashmem-linux-6.8.patch)) | Android 15/16 `system_server` aborts without it on a mainline kernel ([engineering log #4](engineering-log.md#4-test-environment-android-16-image-needs-an-ashmem-driver)) |
| Android userspace | [redroid](https://github.com/remote-android/redroid-doc) x86_64 images (AOSP, `userdebug`, test-keys) | the only Android 14/15/16 system images reachable from the container |
| GPU | `virtio-gpu-gl` + virglrenderer: guest Mesa → host Mesa llvmpipe on Xvfb | 30 fps; the alternative (SwiftShader inside the TCG guest) ran at 0.16 fps |
| Input | `fctouch`: a uinput multitouch screen inside the guest driven over TCP | `adb shell input` takes ~1 s per event under TCG, too slow for drag-and-drop gameplay |
| Display | 720×1600 portrait panel, 320 dpi (a common phone geometry); the game rotates it to 1600×720 landscape | |

| Image | `ro.build.fingerprint` | Docker digest |
|---|---|---|
| Android 14 (API 34) | `redroid/redroid_x86_64_only/redroid_x86_64_only:14/UD2A.240505.001.W1/eng.frank.20240527.155732:userdebug/test-keys` | `sha256:68ae34df…daab86` |
| Android 15 (API 35) | `redroid/redroid_x86_64_only/redroid_x86_64_only:15/BP1A.250505.005.D1/eng.root:userdebug/test-keys` | `sha256:58e548d7…31adf3` |
| Android 16 (API 36) | `redroid/redroid_x86_64_only/redroid_x86_64_only:16/BP2A.250605.031.A3/eng.root:userdebug/test-keys` | `sha256:18eaf705…d9e9f0` |
| Android 17 | — no image available to this environment — | **NOT TESTED** |

Scripts: [tools/test-harness/](../tools/test-harness/) (`build_images.sh` records how the images,
kernel and initramfs were made; `run_android.sh` boots one).

## How this differs from a phone

| Difference | Consequence for the results |
|---|---|
| No Google Play services, no Play Store | Billing can only be tested on its *unavailable* path; AdMob 15.0.1 could not have loaded ads anyway |
| x86_64 only, no ARM translation | Irrelevant for this APK: it contains no native code |
| Emulated CPU (TCG) | Timing-sensitive behaviour (frame pacing, audio latency) is not representative; functional behaviour is |
| GL via Mesa/virgl (and ANGLE/SwiftShader in guest mode) | Two real GLES implementations, but no vendor GPU driver (Adreno/Mali/PowerVR) was exercised |
| No audio output device in the VM | Audio *playback state* was verified (AudioTrack/MediaPlayer active, no errors), not audibility |
| redroid's SystemUI does not process edge-swipe gestures | **Gesture-navigation (predictive) back could not be exercised**; key-based back (3-button / hardware BACK) was |
| No real network: HTTPS leaves through a TLS-intercepting proxy whose CA the guest does not trust | The legacy DroidHen server could not be reached; every network request fails in the TLS handshake. The *failure handling* of the game was tested, not server compatibility |
| Large-screen and cutout geometry are emulated (`wm size`/`wm density`, `cmd overlay … cutout.emulation.tall`) | Real foldables/tablets were not tested |
| One device per Android version | No OEM skins (One UI, MIUI, ColorOS …) |

## Evidence

Every PASS in [compatibility/compatibility-matrix.md](compatibility/compatibility-matrix.md) is
backed by a screenshot and/or a logcat excerpt captured with
[`scripts/collect_logcat.sh`](../scripts/collect_logcat.sh), `tools/test-harness/snap.sh` and
`adb logcat`/`dumpsys`. A curated subset of screenshots is in [evidence/](evidence/). Raw logs were
kept out of Git because they contain the test device's identifiers and are several MB each.
