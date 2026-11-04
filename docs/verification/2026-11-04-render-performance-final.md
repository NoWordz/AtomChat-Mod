# Render performance final candidate: AtomChat

Date: 2026-11-04

AtomChat final source: `1d9411b7c7c9141af41d88fb182201b576bfa602`. AtomLib final candidate: `fd65d69e6830969a053912f94f9b03a7bd17ab43`. This is a new final-candidate record and does not replace `2026-11-04-render-performance-followup.md`.

No product code was changed in this verification pass. The AtomLib lock was changed to `0.3.0` only inside the AtomChat isolated source snapshot so that all three consumer builds compile against the final AtomLib candidate. No Minecraft process was started and nothing was deployed, published, committed, or pushed.

## Isolated inputs and combination

- AtomChat source archive SHA-256: `047fb06500dc062854d8a7d97cd2aab31f42b107c549efa0a604cd1bb59d56c0`
- AtomLib source archive SHA-256: `623b9c0f17e45df4d03794daebc1422709d541ccd5b22c73b314c4f647e362e4`
- Combination record: `D:/AtomChat-Main/build/render-perf-20261104/final-20261104-02/combination-input.json`
- AtomLib Maven input: `D:/AtomLib/build/render-perf-20261104/final-20261104-02/maven`
- Candidate coordinates and hashes:

| Target | Coordinate | SHA-256 |
| --- | --- | --- |
| Forge 1.20.1 | `com.atom.lib:atomlib-1.20.1-forge:0.3.0` | `ea618c3537d22b5eca81a3c85cb984e5a4f259e91c938a4425a243a9ae7b3774` |
| Fabric 1.21.1 | `com.atom.lib:atomlib-1.21.1-fabric:0.3.0` | `a1da0ff062a9b87a9eed8882ea9d1fdf5ce241336eaf1a806c04e7656b9308bc` |
| NeoForge 1.21.1 | `com.atom.lib:atomlib-1.21.1-neoforge:0.3.0` | `59fa6ccc7c2b13255b2e60eabc4dbe41da541d14e6044a76e12b8930852683d9` |

## Build results

The Fabric source snapshot was fresh and used:

```powershell
java.exe -Dfile.encoding=UTF-8 `
  -classpath <platform>\gradle\wrapper\gradle-wrapper.jar `
  org.gradle.wrapper.GradleWrapperMain `
  build --offline --no-daemon --console=plain `
  -PatomlibMavenRepo=D:/AtomLib/build/render-perf-20261104/final-20261104-02/maven
```

Forge used the retry command reproduced below; the first attempt was interrupted during tests and is retained as evidence:

```powershell
java.exe -Dfile.encoding=UTF-8 `
  -classpath <platform>\gradle\wrapper\gradle-wrapper.jar `
  org.gradle.wrapper.GradleWrapperMain `
  clean build --offline --no-daemon --console=plain `
  -PatomlibMavenRepo=D:/AtomLib/build/render-perf-20261104/final-20261104-02/maven
```

NeoForge used the Fabric command with `-x downloadAssets`. Its fresh `clean` deleted the pre-copied `minecraft_assets.properties`. After `clean`, the file was copied again from the earlier isolated build and the non-clean `build` passed. The asset file SHA-256 was `015d0bc702eff9f9e7bc7d1a514c8c554118407635c5d1c56470d62a38f04560`.

| Target | Result | Tests | Failures | Errors | Skipped | Build log |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Forge 1.20.1 | PASS | 1,620 | 0 | 0 | 5 | `logs/forge-build-retry.log` |
| Fabric 1.21.1 | PASS | 1,635 | 0 | 0 | 5 | `logs/fabric-build.log` |
| NeoForge 1.21.1 | PASS | 1,620 | 0 | 0 | 5 | `logs/neoforge-build-retry.log` |

All three runs reported `verifyAtomLibInput` as passed. Fabric completed in 2m36s, Forge in 1m54s, and NeoForge in 1m41s. The five skips on each target are the existing API alternate-JAR, golden-frame, Skia microbenchmark, and two emoji-performance assumptions. The full XML reports were archived before the later targeted measurement replaced the active test result directory.

Final consumer JARs, all copied and re-hashed under `D:/AtomChat-Main/build/render-perf-20261104/final-20261104-02/artifacts/`:

| Target | JAR | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| Forge 1.20.1 | `atomchat-Forge-1.20.1-v0.5.0.jar` | 8,482,346 | `c005506ff7961a8f441f4e78e67cc141b94b3ef446ce7153505200d4507aadd9` |
| Fabric 1.21.1 | `atomchat-Fabric-1.21.1-v0.5.0.jar` | 8,504,189 | `36fad4621ce9711d2b857b476237196ce83fdab9d4fe5e44eed9c94b84a892b1` |
| NeoForge 1.21.1 | `atomchat-NeoForge-1.21.1-v0.5.0.jar` | 8,494,724 | `2d0e3bbd50e9f006a115082604ac5a9509f5eb1699e27cdfb9bc96c40dd3bd17` |

## Artifact and dependency checks

Command:

```powershell
python tools/verify_jars.py --platform windows-x64 --profile atomlib-consumer <platform>
```

The first invocation omitted the `platforms/` path component and correctly rejected the missing directory. The corrected run passed all seven checks for each target:

- exactly one consumer JAR;
- architecture-independent consumer name;
- no duplicated AtomLib UI Java/native payload;
- private relocated FlatLaf retained;
- every nested JAR registered;
- client-scoped AtomLib dependency present;
- compiled AtomLib input metadata agrees with the `0.3.0` lock.

Evidence: `logs/artifact-check-retry.log`. The erroneous first call is preserved in `logs/artifact-check.log`.

`python tools/verify_targets.py` in the isolated patched checkout passed all six target-matrix entries; see `logs/verify-targets.log`. The facade wiring probe also passed official and Fabric paths through the actual `NotificationHudOverlay.render` and `SkiaGraphics` entry points against test-only Minecraft/GL stand-ins; see `logs/profile-wiring-final.log`.

## Offline instrumentation A/B

The existing `DotOfflinePerfTest` was copied into the isolated Fabric test source set and run twice: profiling disabled, then enabled. Each scene used 3 rounds, 20 warmups per scene round, and 100 recorded frames per round. Each CSV therefore contains 4,500 samples. Both JVM runs used JDK 21.0.11 with Gradle `--offline --no-daemon`.

At density 1.5:

| Scene | Off p50 / p95 ms | On p50 / p95 ms | p50 change |
| --- | ---: | ---: | ---: |
| edge-small | 0.5280 / 0.7950 | 0.4960 / 0.5765 | -6.07% |
| edge-large | 1.8058 / 2.1638 | 1.7307 / 2.0389 | -4.16% |
| halo | 4.6876 / 5.2919 | 4.5657 / 5.1104 | -2.60% |
| trail | 4.3330 / 4.9128 | 4.2536 / 4.8231 | -1.83% |

Across nonblank scenes and all densities, p50 changes ranged from -6.07% to +1.13%. The blank scene measured near zero at sub-microsecond scale and is not a useful percentage comparison. The disabled and enabled groups ran sequentially in separate JVMs, not randomized or interleaved; thermal, JIT, and scheduler effects remain. This run does not establish that instrumentation improves or costs frame time, and it is not a game FPS result.

Evidence:

- `D:/AtomChat-Main/build/render-perf-20261104/final-20261104-02/offline-ab/dot-profile-off.csv`, SHA-256 `c10c5b7763963735b852820c83f4be2c9f7ddd5ca014b6ba6f0dd1c6277a0377`
- `D:/AtomChat-Main/build/render-perf-20261104/final-20261104-02/offline-ab/dot-profile-on.csv`, SHA-256 `178701b9c06d9084705881fc4429cc6055c303a55d60ca04e599c2ca78c7bcbd`
- `logs/dot-profile-off.log` and `logs/dot-profile-on-rerun.log`
- `offline-ab/summary.json`, `offline-ab/summary.csv`, and `offline-ab/environment.json`

The first enabled attempt was UP-TO-DATE and produced no CSV; its log is preserved as `logs/dot-profile-on.log`. The rerun used `--rerun-tasks` and exited 0. Earlier evidence under `offline-20261104-01` was not modified.

## Manual in-game sampling

This section prepares the user-run sample. The agent did not start Minecraft, install files, or copy the candidates into any instance.

1. Choose one loader/Minecraft target and prepare an isolated test instance. Record the world name, server or seed, player position, view direction, render distance, GUI scale, display resolution, VSync and FPS cap, Java runtime, GPU/driver, loader/Minecraft version, and relevant rendering mods.
2. Install exactly one AtomChat JAR and the matching AtomLib JAR from the tables above. Verify both file SHA-256 values before launch. Keep the two hashes and their file names with the sample. Avoid mixing another AtomLib version in the client.
3. In `config/atomchat/atomchat-client.json`, set `"debug": true` and restart the client. Confirm the log contains an `AtomChat build … | sha256:…` identity line and an `AtomChat environment summary (debug)` block.
4. Use a stable world and camera for all runs. Keep chunk/render distance and the panel size/scale fixed. Allow chunks and textures to settle before measurement.
5. Collect separate 20–30 second samples for: closed panel; panel open on Chat with no incoming messages; panel open with a stable text-message list; panel open with an item-link message after the icon is already warm; panel open with blur off; then the matching setup with blur on. Keep menus, windows, server changes, and background downloads out of timed intervals.
6. For a useful panel segment report, keep the panel open and no nested popup active for at least 600 panel frames after debug is enabled. The existing report interval is 600 frames. Record external frame-time/FPS evidence separately; do not derive game FPS from the panel report.
7. Stop the client normally. Save `logs/latest.log`, or the launcher's equivalent current log, together with screenshots or external FPS/frametime captures for each scenario.

Extract the last summary block with PowerShell:

```powershell
$instance = 'D:\path\to\instance'
$logPath = Join-Path $instance 'logs\latest.log'
$lines = Get-Content -LiteralPath $logPath
$hit = $lines | Select-String -Pattern 'AtomChat render profile \(debug\)' | Select-Object -Last 1
if (-not $hit) { throw 'No AtomChat render profile block in this log' }
$start = $hit.LineNumber - 1
$end = [Math]::Min($lines.Count - 1, $start + 24)
$lines[$start..$end] | Set-Content -LiteralPath 'render-profile-latest.txt' -Encoding utf8

$lines | Select-String -Pattern 'AtomChat build |AtomChat environment summary \(debug\)' -Context 0,12 |
  Set-Content -LiteralPath 'atomchat-environment.txt' -Encoding utf8
```

The render block starts with `AtomChat render profile (debug)` and contains `panel`, `decode`, `layout`, per-surface render/interval rows, then `segments wall-clock (nested; do not sum)` with `PRE_UI`, `BLUR`, `ICONS`, `DRAW`, `DOTS`, `LIST` and resource counts. `BLUR`/`ICONS` are nested under `PRE_UI`; `DOTS`/`LIST` are nested work inside `DRAW`. Never add the nested rows. `ICON_COLD` and resource counters are event counts, not bytes.

The logs may contain chat text, player names, server addresses, and personal paths. Review and redact those fields before sharing.

## Limits

The combination builds prove compilation, test execution, packaging, dependency checks, and final artifact identity for this machine and these inputs. They do not prove that Minecraft loads the pair, that the UI remains visually correct, or that frame time improves. Real model/ItemStack rendering, GPU timing, shader and modpack interactions, long-running resource behavior, ARM64 hardware, and user-visible performance still require a user-authorized game run. No synthetic timing in this document is a game-performance result.
