**How to build the source, what the tests cover, how the repository is laid out, and how a release goes out.**

## Repository layout: one branch, many targets

There is exactly one source branch, **`main`**, and a release builds one jar per target from it. Splitting by Minecraft version or loader was tried and dropped: with per-version branches, keeping the non-primary branches current depends on someone remembering to write a catch-up commit, nothing enforces the timing, and older versions lag behind for months.

```
gradle.properties              # repository-wide identity: mod_version / mod_id / mod_group_id / license / authors
gradle/atomchat-layers.gradle  # reads that identity, wires shared/ and this target's layers into its source sets
versions/
  targets.json                 # which targets exist (every MC version needs all three loaders, or an explicit opt-out)
  layers.json                  # which layers exist: the axes since / loader / mappings
  mapping-aliases.json         # the few classes whose package path differs between the two mapping families
  third-party-apis.json        # third-party APIs the shared layer is allowed to use
shared/                        # shared by every target
layers/
  mapping/official/            # shared by the official-mappings family (NeoForge + Forge)
  version/  loader/            # version and loader layers (empty today - see "why there is no version layer yet")
platforms/
  1.21.1-fabric/  1.21.1-neoforge/  1.20.1-forge/   # each target's own code, build script, metadata and resources
tools/verify_targets.py        # the matrix and identity guard (local and CI run the same file)
docs/                          # store copy, plus the sources of this wiki
```

**There is no root `build.gradle`:** each target's project directory brings its own build (fabric-loom for Fabric, ModDevGradle for NeoForge, ForgeGradle for Forge), with its own Gradle and Java version. That is a hard constraint — ForgeGradle 6 does not run on Gradle 9, and 1.20.1 needs Java 17 — so the versions are not forced together.

Current size of each project:

| Project | main | test | Contents |
|---|---:|---:|---|
| `shared/` | 103 | 57 | Shared by all three targets |
| `layers/mapping/official/` | 37 | 13 | Shared by NeoForge + Forge |
| `platforms/1.21.1-fabric/` | 60 | 16 | Including 48 Yarn-named twin copies |
| `platforms/1.21.1-neoforge/` | 23 | 3 | |
| `platforms/1.20.1-forge/` | 23 | 3 | |

## Building and testing

Run these inside the **target's own project directory**, not the repository root:

```bash
cd platforms/1.21.1-neoforge
./gradlew build      # build + tests; output under platforms/1.21.1-neoforge/build/libs/
./gradlew runClient  # dev client (the Fabric and Forge targets work the same way)
./gradlew atomchatLayers   # print which layers this target mounts and which source dirs that pulls in
```

- The Fabric and NeoForge targets use **JDK 21**; Forge 1.20.1 uses **JDK 17**;
- the Forge target additionally produces a `-slim` jar (no bundled dependencies, development only) — **release the one without `-slim`**;
- the version number lives in exactly one place, the repository root `gradle.properties`; no platform defines it (a platform that cannot read it fails outright rather than falling back to a default).

The tests are **JUnit 5** and cover logic and native CPU rasterization without starting Minecraft. Tests use the native matching the host JVM, including when cross-building another variant. Run `./gradlew test build --no-daemon` for Windows x64; use `./gradlew build -x test -Patomchat.nativePlatform=windows-arm64 --no-daemon` or `-Patomchat.nativePlatform=linux-x64` for the other packages after the shared tests pass. Outputs are `build/libs/`, `build/windows-arm64/libs/` and `build/linux-x64/libs/`. Release filenames end in `-windows-x64`, `-windows-arm64` or `-linux-x64`; store labels use the same suffix. Install only one variant. Skija Java/native versions are pinned together at 0.143.18; HumbleUI types is 0.2.0. Packaging Windows ARM64 on x64 does not verify ARM64 runtime behavior.

## The layering rules

Layers are declared in `versions/layers.json`, where **a layer is a predicate over targets** on three axes:

| Axis | Meaning | Must not appear inside the layer |
|---|---|---|
| `since` | Minecraft version at or above it | loader- or mapping-specific constructs |
| `loader` | only targets on this loader | version- or mapping-specific constructs |
| `mappings` | only targets using this mapping family | version- or loader-specific constructs |

The layer's directory is **derived from its declared axes** (`layers/mapping/official/`), so there is no second place that can contradict it. Mounting is the declaration: a target that mounts a layer it cannot satisfy fails at **configuration time**.

### Why there is no version layer or loader layer yet

A layer pays off when several targets share one cell. Today's cells:

| Cell | Members |
|---|---|
| 1.21.1 + official | NeoForge (the only one) |
| 1.21.1 + yarn | Fabric (the only one) |
| 1.20.1 + official | Forge (the only one) |

With one member per cell, moving code into a version or loader layer shares nothing and only adds a directory level. The layers will appear on their own once a second target lands in an occupied cell (1.20.1-fabric, say).

### The mapping seam (kept on purpose)

Fabric uses Yarn; the other two use official names. A piece of logic that lives in the mapping layer therefore has two spellings: the one in `layers/mapping/official/` (shared by both official targets) and a twin copy at the same path on the Fabric side.

The cost is worth stating plainly: **a change to one of those files has to be made twice.** The guard can prove a twin exists, that paths line up, and that it is not byte-identical (in which case it belonged in `shared/`), but it **cannot prove the two copies behave the same** — that is the inherent risk of copying by hand, covered by tests and review.

Three classes need package-private access inside vanilla's own packages, so their Fabric paths differ from the other family's; that difference is written down in `versions/mapping-aliases.json` and the twin check follows the alias.

## The guards

`tools/verify_targets.py` runs locally and in CI from the same file. It covers thirteen things, among them:

- **the matrix is a rule:** every Minecraft version that appears needs all three of Fabric / NeoForge / Forge — either build it, or declare `buildable: false` and say why;
- **one identity:** no platform may define `mod_version` or its siblings locally;
- **project directories and matrix entries must exist for each other:** a directory outside the matrix is source nothing ever compiles;
- **predicates match mounts:** mounting a layer you do not satisfy is red;
- **twin copies:** every file in the mapping layer needs a counterpart in each non-official target being built (found by alias); missing, or byte-identical, is red;
- **declaration matches implementation:** the `java` / `gradle` in `targets.json` must agree with the project's toolchain and wrapper;
- **access-widening parity, resource paths, dist isolation, alias integrity, the seam list's freshness, and what the shared layer may depend on** — plus a guard that scans `shared/` for syntax the lowest target's Java level cannot compile (Java 17 still treats pattern switches as a preview feature).

## CI and releasing

| Workflow | Trigger | What it does |
|---|---|---|
| `build.yml` | push to `main` / pull requests (ignoring `**.md` and `docs/**`) | reads the matrix → each target installs its JDK and runs `./gradlew build` (tests included) |
| `release.yml` | a `v*` tag | **`vX.Y.Z`:** build all three loader targets for Windows x64, Windows ARM64 and Linux x64 → check the repository version matches the tag → create a Release with nine JARs → publish to the stores; **`vX.Y.Z-rc1`:** a prerelease only, **nothing goes to the stores** |

Release checklist:

1. bump `mod_version` in the root `gradle.properties`;
2. write a `## vX.Y.Z` section in `RELEASE_NOTES.md` (the release body comes from it; a tagged suffix falls back to the base version's section);
3. commit and push `main`;
4. tag `vX.Y.Z`, push the tag, and watch the three jobs in `release.yml`.

To rehearse the flow without touching the stores, push a suffixed tag (`vX.Y.Z-rc1`); the store job is skipped entirely.

> ⚠️ **A rehearsal only covers the first three stages.** A `-rc` tag makes the `if` skip the publish job before its `matrix` is even evaluated. 0.2.10 hit exactly that on its real release: the publish job read `needs.targets.outputs.list` while declaring only `needs: build`, so GitHub dropped the whole job — the run went red, the log stayed empty, and the jobs list was one short. Fixing the workflow file is not enough: **the tag has to move with it** (`git tag -f` and `git push --force origin vX.Y.Z`), because a tag push runs the workflow file as it exists in that tag's tree.

### Store publishing and project copy

| Store | How a release goes out | How the project copy is changed |
|---|---|---|
| CurseForge | the `:curseforge` leg of `release.yml` (curseforgegradle; it runs when `CURSEFORGE_TOKEN` exists) | **Pasted by hand**: the official Upload API has exactly four endpoints (`upload-file`, `update-file`, maven, localization) and none for a project description ([docs](https://support.curseforge.com/support/solutions/articles/9000197321-curseforge-api)). The source of truth is `docs/curseforge-description.md` |
| Modrinth | the `:modrinth` leg of `release.yml` (minotaur); needs `modrinth_project_id` in `gradle.properties` plus the `MODRINTH_TOKEN` secret, and is SKIPPED without either | `python3 tools/store_sync.py` (a **dry run** unless you pass `--apply`) → `PATCH /v2/project/{id}`, **changing only the `body` field**; the `同步商店文案` workflow does the same from the Actions tab |

`tools/test_store_sync.py` runs every branch of that script against a local fake Modrinth (when it sends requests, how many, with what, when it sends nothing, and what it exits with on failure). Whether a real token works is the one thing that cannot be checked locally, so everything else is checked.

## Documentation and translation

- User docs: `README.md` (Chinese) / `README_EN.md` (English), **section-for-section mirrors** — change one and change the other;
- store copy: `docs/mcmod-description.md` (Chinese, mcmod's `[h1=]` syntax), `docs/curseforge-description.md` (English), `docs/modrinth-description.md` (English, sharing the body with CurseForge minus the badge line); all three are **sourced from the README**, so feature changes update them together;
- this wiki: the sources live in `docs/wiki/` and take effect once pushed (**the source is the only truth — edit it and push, do not edit on the web**);
- in-game translation: `platforms/*/src/main/resources/assets/atomchat/lang/{zh_cn,en_us}.json`; new strings go into both.

## Technical debt (written down, not acted on yet)

| Item | Today | Intent |
|---|---|---|
| The mapping seam | 48 files carry two spellings, so changes land twice | Either accept it (as now), or migrate Fabric to official mappings and delete `layers/mapping/official/` |
| `AtomChatScreen` (3337 lines) | Its Neo↔Forge difference is version API (`mouseScrolled` 4 vs 3 arguments, a `renderBackground` override, `moveCursorTo(x,false)`), and the version axis has one member per cell | Collecting it means a platform-side abstract base class plus a version layer (mcphone's `PhoneScreenBase` shape) — pure structural investment, no sharing today |
| Avatar / Media payload families | Their codecs hold no format rules (plain UUID / int / bool / byte arrays), so the byte layer collected nothing from them | Their duplication is the record field list; that waits for moving those payloads' content logic into the shared layer, whose blockers (sending, registration, byte format) are now gone |
