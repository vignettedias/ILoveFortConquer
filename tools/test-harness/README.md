# Test harness (not part of the APK)

Scripts used to run Android 14, 15 and 16 in a container without KVM and to drive Fort Conquer
for the acceptance tests. The setup, its limits and how the results were collected are described
in [docs/testing-environment.md](../../docs/testing-environment.md).

| File | Purpose |
|---|---|
| `build_images.sh` | Record of how the guest kernel, initramfs and the Android 14/15/16 disk images were built (redroid AOSP userspace on an Ubuntu 6.8 kernel) |
| `initramfs/init` | Loads binder + ashmem, preloads netfilter modules, mounts the Android root and `switch_root`s into Android `init` |
| `ashmem/ashmem-linux-6.8.patch` | Port of the redroid-modules ashmem driver to Linux 6.8 (needed by Android 15/16 on a mainline kernel) |
| `run_android.sh` | Boots an image in QEMU (TCG). `virgl` GPU mode renders GL on the host through virglrenderer/llvmpipe (~30 fps); `guest` uses SwiftShader in the guest (~0.2 fps) |
| `wait_boot.sh`, `con.py` | Wait for `sys.boot_completed` over the serial console |
| `pull_image.py` | Pulls a Docker Hub image as verified layer tarballs (no Docker daemon needed) |
| `fctouch.c`, `touch.py` | uinput multitouch device inside the guest + host client: low-latency taps and drags in landscape screen coordinates |
| `snap.sh` | Screenshot (PNG + JPEG preview) |
| `battle_smart.py` | Test driver that plays a battle: reads the in-game radar from screenshots and drags troop cards into the most threatened lane |

Environment variables: `FC_HARNESS` (work directory for kernel, images, sockets; default
`~/fc-harness`), `FC_SERIAL` (adb serial; default `127.0.0.1:6555`), `FC_TOUCH_ROT` (display
rotation while the game runs in landscape; default `1`), `FC_PANEL` (physical panel size, default
`720x1600`; scripts always use 1600×720 landscape coordinates and scale them).

Typical session:

```sh
tools/test-harness/run_android.sh ~/fc-harness/android16.img a16 720 1600 320 6555 virgl &
tools/test-harness/wait_boot.sh a16 1500
export ADB_LOCAL_TRANSPORT_MAX_PORT=5553     # adb would otherwise also probe the VM's console ports
adb connect 127.0.0.1:6555 && adb -s 127.0.0.1:6555 root
adb -s 127.0.0.1:6555 push ~/fc-harness/fctouch /data/local/tmp/
adb -s 127.0.0.1:6555 shell 'nohup /data/local/tmp/fctouch 720 1600 >/dev/null 2>&1 &'
# the container has no ueventd-managed /dev/input node for it: recreate it so InputReader sees it
adb -s 127.0.0.1:6555 shell 'n=$(basename $(dirname $(dirname $(grep -l fc-test /sys/class/input/event*/device/name)))); \
  maj_min=$(cat /sys/class/input/$n/dev); rm -f /dev/input/$n; \
  mknod -m 666 /dev/input/$n c ${maj_min%%:*} ${maj_min##*:}'
adb -s 127.0.0.1:6555 forward tcp:7070 tcp:7070
scripts/collect_logcat.sh dist/FortConquer-1.2.4-Modern-Android.apk out/a16 60 127.0.0.1:6555
python3 tools/test-harness/touch.py tap 800 540      # START on the title screen
```
