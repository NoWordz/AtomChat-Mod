# AtomLib extraction and AtomChat integration verification

Date: 2026-10-30. Workspace: `D:/AtomLib` and `D:/AtomChat-Main`. Versions remain
AtomLib `0.1.0-local` and AtomChat `0.4.0`. No commit, push, tag, remote Maven,
store publication, deployment, or Minecraft/server launch was performed.

This is Hubble's implementation-time evidence. The later parent review found
open blockers; see [the current checkpoint](2026-10-30-atomlib-checkpoint.md).
Local checkpoint commits and cleanup supersede the historical no-commit note.
This report is not a final acceptance or release approval.

## Automated results

| Suite | Result |
|---|---|
| AtomLib root JUnit (`core`, `runtime`, `ui`, `devtools`) | 116 tests, 0 failures/errors/skips |
| Hidden GLFW GPU suite, three loaders | 33 tests total, 11 per target, 0 failures/errors/skips |
| AtomLib artifact verifier self-tests | 29 tests, 0 failures |
| Consumer verifier self-tests | 2 tests, 0 failures |
| AtomChat jar profile verifier self-tests | 16 tests, 0 failures |
| UI/example provenance guard | 3 tests, 0 failures |
| AtomChat Forge 1.20.1 / JDK17 | 1561 tests, 0 failures/errors, 4 existing skips |
| AtomChat Fabric 1.21.1 / JDK21 | 1573 tests, 0 failures/errors, 4 existing skips |
| AtomChat NeoForge 1.21.1 / JDK21 | 1561 tests, 0 failures/errors, 4 existing skips |
| Six independent Maven consumers (`examples/a`, `examples/b` x three loaders) | 6 PASS |

The hidden-GL run reported NVIDIA GeForce RTX 5060 Laptop GPU,
`OpenGL 3.3.0 NVIDIA 617.42`, using a hidden GLFW context. It is not a Minecraft
client launch.

## Commands actually used

```powershell
# AtomLib source tests (JDK17, host font supplied only as a test argument)
.\gradlew.bat :core:test :runtime:test :ui:test :devtools:test `
  -PatomlibTestFont=D:/AtomChat-Main/platforms/1.20.1-forge/src/main/resources/assets/atomchat/font/bundled.otf `
  --no-daemon --console=plain

# Each AtomLib target, from its own directory
.\gradlew.bat build publishToTestRepository verifyArtifact `
  -PatomlibTestFont=D:/AtomChat-Main/platforms/1.20.1-forge/src/main/resources/assets/atomchat/font/bundled.otf `
  --no-daemon --console=plain

# GPU bridge regression
.\gradlew.bat hiddenGlTest --no-daemon --console=plain

# Isolated consumers
python tools/run_packaging.py consumers --jobs 2

# Each AtomChat target, from its own directory
.\gradlew.bat clean test build -PatomlibMavenRepo=D:/AtomLib/build/test-maven --no-daemon --console=plain
python tools/verify_jars.py --profile atomlib-consumer platforms/<target>
```

## Final artifacts

The delivery directory is `build/atomlib-delivery-20261030-final` under AtomChat. Its
`SHA256SUMS.txt` is authoritative for the copied bytes.

| Target | AtomChat JAR SHA-256 | AtomLib JAR SHA-256 |
|---|---|---|
| Forge 1.20.1 | `ad6f16936c4945337ada37c8f1c45f1b4bee55f1d3247bb7ebe7c878eb237d17` | `b508d86014923a34ceaffe9003f5d7a1f5f361fde16d78c4166121ab5e92f074` |
| Fabric 1.21.1 | `9fb727d6b51efd58677ef9b12870d2f09cc9a47b7005a16075b67d862a9bf4ac` | `a05af72290134527dd1a360d7700d5a1c3867790f45cb5c7f9085769c1f617ce` |
| NeoForge 1.21.1 | `cdeeca18b80d96f0645951c6ccd952e3c7ff826a3f440e5ec26882851fbe85dc` | `d280f7679ee24ae7ea8714852c3f89257a24f7771e475da15b67598133dfae7e` |

Each AtomLib JAR contains AtomLib Java classes, Skija shared/types, Yoga API,
and both Windows x64/Windows ARM64 natives. The recursive artifact verifier
checked PE machine values, versions, nested registration, duplicate classes,
font absence, devtools absence, LWJGL-core absence and POM compile API.

## Implemented integration

- AC's previous `SkiaGraphics` constructor path became a compatibility facade
  over `MinecraftRenderBridge`; it no longer creates a DirectContext or wraps
  the framebuffer. Page close leaves the shared context alive.
- Host drawing and Skija painting are separate bridge phases. Raw context needs
  use the synchronous `withContext` lease; chat effects borrow the surface only
  inside the active paint callback.
- AC appearance/layout adjustment is rendered by the production
  `AtomLibAppearanceSection`, inside the existing chip and scroll shell. Width
  drag, resize, one-commit release, half-screen mode, AC confirmation modal and
  focus restoration are covered by real event tests. A reset-layout confirmation
  action is present with English/Chinese resources.
- AC `CropMath` now delegates to AtomLib's audited implementation.
- AtomLib includes four allowlisted line icons and three CC0 feedback sounds;
  no font, branding artwork, notification audio or Tuui code was copied.
- CI build/release paths now expect one AtomChat consumer JAR plus one pinned
  AtomLib platform JAR per target. The AtomLib input is downloaded over HTTPS,
  verified against the archive SHA-256 and each JAR's lock hash, and CI fails
  when the explicit URL/hash inputs are absent.

## Not covered / remaining acceptance

- No Minecraft client or dedicated-server process was started. Actual screen
  appearance, interaction, loader startup, missing-Lib messaging, and server
  non-initialization still require the user's in-game acceptance.
- Windows ARM64 was packaged and its PE architecture was checked, but no ARM64
  device ran it. Linux/macOS clients are outside this test build's scope.
- IME composition, full bidi/UAX #14/CJK line-breaking, Iris/OptiFine or other
  shader stacks, alternate GPU drivers, performance and long-session leak tests
  remain outside the automated proof.
- AC's HUD editor/shell and chat composer continue to use their existing host
  implementations. AtomLib exposes audited HUD, crop, virtual-list, text-field,
  overlay and optional-shell APIs and isolated examples; those complete
  replacements were not forced into AC's business pages in this pass.
- Existing independent reviews are reused only for their recorded foundation,
  UiView and render-bridge scopes. The new adapter/integration/CI/asset changes
  did not receive a new independent strong-model review. The checks above are
  self-verification, not independent review.
