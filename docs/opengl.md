# OpenGL ES

| Item | Original (unchanged) |
|---|---|
| API | OpenGL ES **1.x** fixed-function (`GL10`/`GL11`), EGL10 via `EGLContext.getEGL()` |
| Surface | AndEngine's own copy of `GLSurfaceView` (Android 1.x code), `SimpleEGLConfigChooser` |
| Context lifetime | The GL thread and EGL context are **destroyed on every `onPause()`** and recreated on `onResume()`; AndEngine reloads all textures into the new context |
| Textures | PNG/JPG atlases decoded with `BitmapFactory`, uploaded with `GLUtils.texImage2D`; no compressed textures in use |
| VBOs | disabled (`EXTENSIONS_VERXTEXBUFFEROBJECTS = false` logged) |

## Driver coverage

| Environment | GLES 1.x implementation | Result |
|---|---|---|
| Test VM, `virgl` mode (all acceptance runs) | `RENDERER: virgl (LLVMPIPE (LLVM 20.1.2, 256 bits))`, `VERSION: OpenGL ES-CM 1.1 Mesa 24.0.8` | Correct rendering, 30 fps (compositor-capped) |
| Test VM, `guest` mode (early runs) | redroid's ANGLE → SwiftShader (software Vulkan inside the emulated CPU) | Correct rendering of logo and menus at 0.16 fps; too slow for gameplay |
| Vendor GPU drivers (Adreno, Mali, PowerVR, Xclipse) | — | **NOT TESTED** |

Notes:

* Some current devices run OpenGL ES through ANGLE on top of Vulkan instead of a native GLES
  driver; the ANGLE/SwiftShader run above is the closest test of that configuration that was
  possible here (menus only).
* The `E OpenGLRenderer/HWUI: Device claims wide gamut support…` and `MESA … fallback gralloc`
  lines in the logs come from the platform's UI renderer and the test VM's Mesa build, not from
  the game.
* Context loss is the normal path for this engine (see the lifecycle table in
  [lifecycle.md](lifecycle.md)): verified after Home/return and screen off/on on Android 14, 15
  and 16 (`AndEngine: onSurfaceCreated` logged again, textures intact, no black or corrupted
  sprites).
