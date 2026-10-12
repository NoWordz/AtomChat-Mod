# Reaction Polish Implementation Plan

> **For agentic workers:** Follow the Codex `codex-agent-orchestration` workflow. GPT-6.1-Sol (medium) authors production code and returns a unified diff; the main session applies it, runs tests/builds, and deploys. Steps use checkbox syntax for tracking.

**Goal:** Correct the five-kind message reaction limit, stale reaction revisions, missing hover/sound feedback, and make reaction motion more legible.

**Architecture:** Keep reaction state and wire limits in shared code. Put the tooltip overlay predicate beside `ReactionChips` so all loader screens use one tested rule. Reuse the existing `UiSpring.messageEase()` curve and tune its shared token, accepting the corresponding change to ordinary message entrance motion.

**Tech Stack:** Java 17, JUnit 5, Skija, Fabric 1.21.1, NeoForge 1.21.1, Forge 1.20.1.

## Global Constraints

- One message may have at most five distinct reaction emoji kinds; multiple people using the same emoji share one kind.
- Chip enter/exit duration is 220ms; the glyph starts 60ms after the chip.
- Raise the shared message-ease overshoot from about 5% to about 9%; this also changes ordinary message entrances.
- Play the existing `FeedbackCue.BUTTON` for selecting React in the context menu and for toggling a reaction chip; selecting an emoji already plays it.
- The hover tooltip keeps the current first-six-names plus `+N` summary and remains suppressed while another overlay owns the pointer.
- Reaction state revisions must remain increasing for a key after the server evicts and later recreates it, including a no-op removal on the recreated key.
- Keep all three platform screens behaviorally aligned. Do not commit or publish.

---

### Task 1: Cap distinct reaction kinds and prevent stale snapshots

**Files:**
- Modify: `shared/src/main/java/com/atom/chat/reaction/ReactionWire.java`
- Modify: `shared/src/main/java/com/atom/chat/reaction/ReactionLedger.java`
- Test: `shared/src/test/java/com/atom/chat/reaction/ReactionLedgerTest.java`

**Behavior to protect:** Five distinct emoji kinds can be added to one message; a sixth is rejected. If an old key is evicted and recreated, the new snapshot revision exceeds the old one even when the recreated action removes a reaction that is no longer in the server ledger.

- [x] Add a ledger test that adds five different allowed emoji kinds to one key, expects an empty result for each, and expects `per_message_limit` for a sixth distinct kind.
- [x] Add a ledger test that records a key's revision, creates enough other keys to evict it, sends an inactive action for the evicted key, and asserts the recreated snapshot revision is greater than the recorded revision.
- [x] Run `cd platforms/1.21.1-fabric && ./gradlew test --tests com.atom.chat.reaction.ReactionLedgerTest`; confirm the new tests fail against the current 20-kind cap and per-message revision reset.
- [x] Change `ReactionWire.MAX_KINDS` to 5 and give each newly created ledger key a server-session monotonic revision before any action can return a snapshot; advance it for every accepted state change.
- [x] Re-run the focused ledger test, then all three platform test suites after the remaining reaction tasks are applied.

### Task 2: Restore tooltip hover and button feedback on all loaders

**Files:**
- Modify: `shared/src/main/java/com/atom/chat/ui/ReactionChips.java`
- Test: `shared/src/test/java/com/atom/chat/ui/ReactionChipsTest.java`
- Modify: `platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java`
- Modify: `platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java`
- Modify: `platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java`

**Behavior to protect:** The tooltip is suppressed if any overlay is open and is active with all overlays closed; its existing 90ms fade and six-name summary remain. React-menu selection and chip toggles each play one button cue.

- [x] Add a pure predicate test with literal cases: all overlay flags false returns false; each overlay flag true individually returns true.
- [x] Run the focused test and confirm the missing helper/contract is exposed before implementation.
- [x] Add one shared tooltip suppression predicate and call it from all three screen render paths; pass the overlay-open state, not the current all-overlays-closed expression.
- [x] Add `FeedbackSoundController.play(FeedbackCue.BUTTON)` after opening the reaction picker and when `messageListView.clickReaction(...)` returns true, in all three screen copies.
- [ ] Run the focused tooltip test; visually verify hover names fade in/out and overlays suppress the tooltip. Verify menu selection and chip toggles each play one button cue.

### Task 3: Retune reaction entrance and exit motion

**Files:**
- Modify: `shared/src/main/java/com/atom/chat/ui/UiMotion.java`
- Modify: `shared/src/main/java/com/atom/chat/ui/UiSpring.java`
- Test: `shared/src/test/java/com/atom/chat/ui/ReactionChipAnimTest.java`
- Test: `shared/src/test/java/com/atom/chat/ui/SpringAnimTest.java`

- [x] Update the chip animation test timeline to 220ms enter/exit and 60ms symbol delay; confirm it fails with the current 160ms/40ms values.
- [x] Update the shared ease test to require a peak between 8.5% and 9.5%, while keeping the curve start at 0 and end at exactly 1; confirm it fails against the current curve.
- [x] Set `REACTION_CHIP_MS=220`, `REACTION_SYMBOL_DELAY_MS=60`, and `MESSAGE_EASE_C1=1.6`; update comments to state that message entrances and reaction chips share the curve.
- [ ] Run the focused animation tests. In the client, verify the slower chip and glyph entry and the stronger overshoot on both reaction chips and ordinary message entrances.

### Task 4: Three-loader validation and delivery

**Files:**
- Verify all modified shared code is consumed by all three platform builds.

- [x] Run `./gradlew test` and `./gradlew build` from each of `platforms/1.21.1-fabric`, `platforms/1.21.1-neoforge`, and `platforms/1.20.1-forge`.
- [x] Review the final diff for parity across the three screen copies and ensure no unrelated changes entered the patch.
- [x] Deploy the three built jars using the existing deployment script after all tests and builds pass; verify the script's recorded SHA-256 hashes.

**In-game checks:** Hover animation, button sounds, and the revised spring were not manually verified; the user requested no further testing after automated checks had completed.
