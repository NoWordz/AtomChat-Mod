# Media Boundaries and Performance Audit Plan

> **For agentic workers:** Follow the Codex `codex-agent-orchestration` workflow. GPT-6.1-Sol (medium) authors production code and returns a unified diff; the main session applies it, runs tests/builds, measures, and deploys. Steps use checkbox syntax for tracking.

**Goal:** Align hosted and local image acceptance with the renderer's bounded decode budget, audit media and reaction performance, and measure only the confirmed default-path hotspots.

**Architecture:** Preserve original image and GIF bytes. Enforce a 16MiB source-byte cap and a 32,000,000-pixel cap before a local upload is published, and at the server storage boundary. Use a server-safe header parser rather than client/native image decoders. Keep performance optimizations conditional on measurements.

**Tech Stack:** Java 17, JUnit 5, Java ImageIO for existing client metadata paths, shared media payload helpers, Fabric 1.21.1 dedicated server for the representative runtime profile.

## Global Constraints

- Do not recompress or resize uploaded image bytes; GIFs remain animated and byte-preserved.
- Reject local files over 16MiB or 32,000,000 pixels before either hosted or external upload; show an explicit localized message and do not fall back to an image host for these global limits.
- Clamp existing server configurations that request more than 16MiB; retain the 2MiB default.
- Keep the 100MiB client disk-cache cap and current derived static-image WebP/PNG and GIF cache behavior unless measurements prove a problem.
- Audit the prior 64MiB setting and the four-per-player pending-upload bound statically after the new 16MiB ceiling is applied; report the effective memory bound and the absence/presence of a global in-flight budget.
- Measure the default 2MiB media route on a local dedicated server, and compare client `FrameProfile` with reaction support off/on. Keep multi-client reaction snapshot fanout as static complexity analysis unless a multi-client environment is explicitly available.
- Only implement performance changes for hotspots confirmed by the requested measurements. Preserve upload quality and data.

---

### Task 1: Add format-safe image bounds and test the limits

**Files:**
- Create: `shared/src/main/java/com/atom/chat/image/ImageBounds.java`
- Test: `shared/src/test/java/com/atom/chat/image/ImageBoundsTest.java`
- Review existing: `shared/src/main/java/com/atom/chat/image/PngDimensions.java`

**Interface:** `ImageBounds.read(byte[] bytes)` returns parsed width and height for the same PNG, JPEG, GIF, WebP, and BMP formats accepted by `MediaIds.extensionOf`, or no dimensions for malformed/unsupported bytes. It must not decode or allocate pixel buffers.

- [x] Add tests from hand-built valid headers at exactly 32,000,000 pixels, one pixel above that boundary, zero dimensions, truncated headers, and each supported format.
- [x] Run `cd platforms/1.21.1-fabric && ./gradlew test --tests com.atom.chat.image.ImageBoundsTest`; confirm the tests fail because the shared parser is absent.
- [x] Implement bounded header parsing with overflow-safe width × height checks and no client/native dependencies.
- [x] Re-run the focused parser test and verify the exact limit and one-over boundary cases.

### Task 2: Enforce the media byte/pixel budget at local and server boundaries

**Files:**
- Modify: `shared/src/main/java/com/atom/chat/config/ServerConfigValues.java`
- Modify: `shared/src/main/java/com/atom/chat/config/AtomChatServerConfig.java`
- Modify: `platforms/1.21.1-fabric/src/main/java/com/atom/chat/image/ImageUploadPipeline.java`
- Modify: `layers/mapping/official/src/main/java/com/atom/chat/image/ImageUploadPipeline.java` (the official-mapping twin used by NeoForge and Forge)
- Modify: three `MediaCompanionServer.java` loader copies
- Modify: three `MediaCompanionClient.java` loader copies
- Modify: locale keys in `platforms/1.21.1-fabric/src/main/resources/assets/atomchat/lang/{en_us,zh_cn}.json`, `platforms/1.21.1-neoforge/src/main/resources/assets/atomchat/lang/{en_us,zh_cn}.json`, and `platforms/1.20.1-forge/src/main/resources/assets/atomchat/lang/{en_us,zh_cn}.json`
- Verify: each loader's `ServerConfigScreen` inherits the 16,384KB ceiling from `ServerConfigValues.MAX_FILE_KB_LIMIT`
- Test: `platforms/1.21.1-fabric/src/test/java/com/atom/chat/image/ImageUploadPipelineTest.java`, the official-mapping `ImageUploadPipelineTest.java`, new `ImageBoundsTest`, and focused server config validation tests

- [x] Add tests that reject a local file above 16MiB and a file above 32,000,000 pixels; assert the external uploader is not called and a failure is surfaced.
- [x] Add server-boundary tests for the shared gate; verify `MediaCompanionServer` calls it before hashing/writing and valid under-limit PNG/JPEG/GIF/WebP/BMP headers remain accepted.
- [x] Run the focused tests and confirm invalid local files do not reach either uploader.
- [x] Clamp saved server settings and the UI's `maxFileKb` maximum to 16,384KB. Keep the 2,048KB default.
- [x] Preflight local file size and dimensions before server or external upload. A 16MiB/32MP violation returns a localized error and does not append a CICode.
- [x] Enforce the same byte and pixel budget at `MediaCompanionServer` storage so older or custom clients cannot publish a file current clients reject.
- [x] Keep ordinary server-config `too_large` fallback behavior for files within the global 16MiB/32MP display budget but above a server's lower configured limit.
- [x] Re-run focused tests and all three platform test suites.

### Task 3: Static performance review and default-path measurements

**Files and paths to review:**
- `shared/src/main/java/com/atom/chat/image/ImageLoader.java`
- `shared/src/main/java/com/atom/chat/image/ImageUploader.java`
- all three `MediaCompanionClient.java` and `MediaCompanionServer.java` copies
- `shared/src/main/java/com/atom/chat/net/CompanionMaintenance.java`
- `shared/src/main/java/com/atom/chat/ui/ReactionChips.java`
- `shared/src/main/java/com/atom/chat/reaction/ReactionFingerprint.java`
- all three `ReactionCompanionServer.java` copies
- `shared/src/main/java/com/atom/chat/diagnostics/FrameProfile.java`

- [x] Record existing evidence: client disk cache 100MiB/500 files/7 days; static images use a 768px processed cache at WebP quality 80 with PNG fallback; animated GIF bytes are cached raw; server media store defaults to 512MiB/7 days; uploads retain original bytes.
- [x] Review source and record exact bounds for active loads, queued image work, server per-player buffers, stale-upload sweeps, server-thread file operations, client/server chunk loops, reaction hashing, and all-player snapshot fanout.
- [ ] On a local Fabric 1.21.1 dedicated server and one client, run a representative valid 2MiB upload and read it back through the hosted-media route. Capture client `FrameProfile` with debug enabled and server `/debug start` reports around the transfer; record the exact profile, file size, chunk count, and client/server scenario.
- [ ] Repeat the client profile with reaction support disabled and enabled on a populated chat list; compare panel p50/p95/max and allocation values.
- [x] Keep 64MiB legacy-config and concurrency analysis static: verify clamping to16MiB and calculate the remaining four-buffer per-player bound. Do not run a 64MiB stress test.
- [x] Make no performance code changes without a repeatable measured hotspot; none was measured in this pass.

### Task 4: Cross-loader build and deployment

- [x] Run `./gradlew test` and `./gradlew build` in Fabric 1.21.1, NeoForge 1.21.1, and Forge 1.20.1.
- [x] Deploy all three jars using the repository's existing deployment script, preserving its backup and hash verification.
- [x] Report static findings, exact build commands, deployment targets/hashes, and runtime measurements not performed per the user's latest instruction.

### Static review findings

- Hosted media preserves original image and GIF bytes; the server store does not apply lossy recompression. The client derives bounded display caches separately: static images are scaled to 768px and cached as WebP quality 80 with PNG fallback; GIF data remains raw. Client cache limits are 100 MiB / 500 files / 7 days; server media store defaults to 512 MiB / 7 days.
- The source cap is now 16 MiB and 32,000,000 pixels. Existing 64 MiB server settings are normalized to 16 MiB. Four pending upload buffers per player therefore allow at most 64 MiB of raw pending buffers per player, with no global in-flight byte budget. Stale uploads are swept every five minutes with a five-minute age threshold.
- The server currently hashes, stores/prunes, reads complete media files, and sends 24 KiB chunks on the server task thread. A 2 MiB file is about 86 chunks. This remains a static risk; no runtime timing was collected.
- Client image loading uses a three-thread fixed pool with an unbounded executor queue and a pending-URL set without a global cap. Reaction snapshots fan out to each connected modded client, while reaction content keys require SHA-256 per eligible visible message row. Runtime frame measurements were not collected.
- The user requested no further testing before runtime profiling; these measurements remain explicitly unperformed.
