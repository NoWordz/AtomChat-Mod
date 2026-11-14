<p align="center">
<img width="400" height="250" alt="logo1" src="https://github.com/user-attachments/assets/354cc7eb-4c1c-49f0-9759-bbe18bf11a14" />
</p>

<h1 align="center">AtomChat</h1>

<p align="center">
  <em>A phone-app style chat experience for Minecraft, powered by Skija.</em>
</p>

<p align="center">
  <a href="#install">Install</a> ·
  <a href="#features">Features</a> ·
  <a href="#compatibility">Compatibility</a> ·
  <a href="https://github.com/NoWordz/AtomChat-Mod/wiki">Wiki</a> ·
  <a href="https://github.com/NoWordz/AtomChat-Mod/issues">Report an issue</a>
</p>

## What it is

A client-side mod that replaces the vanilla chat screen with a vector-drawn phone panel: rounded message bubbles, real skin avatars, private chat sessions, quote replies, image and GIF messages. The interface is drawn entirely with [Skija](https://github.com/HumbleUI/skija) and does not use vanilla chat textures. Image messages follow the `[[CICode]]` protocol and interoperate with the E33Chat / ChatImage family. Installing the mod on the server is optional; it enables media hosting and synchronization of custom avatars, profile headers and signatures.

## Features

- **Phone panel** — frosted-glass or wallpaper background, three bottom tabs (chat / profile / settings), interruptible slide and zoom transitions, including settings tabs. Animations can be disabled globally.
- **Layout editor** — move the panel, resize from any edge or corner, and snap to nine anchors. New installations and layout resets fit the reference proportions to the available viewport; saved layouts are preserved. Width and height use design pixels, with limits that adapt to the window and UI scale.
- **Bubbles and identity** — own messages right, others left; real player names with round skin avatars, quote capsules, @ highlights, flood grouping, history folding at 100 messages per batch.
- **Image messages** — upload via picker, drag-and-drop or Ctrl+V; GIFs loop inside the bubble; left-click for a fullscreen preview, right-click to save the original. Uploads stay with their original conversation; when a draft is full, results wait for space. Failures and queue limits have visible feedback.
- **Private chat and notifications** — a foldable conversation list; per-session drafts and scroll positions; banners for mentions, quotes and DMs with click-to-jump; double-click an avatar to poke.
- **Appearance** — frosted glass and multiple color presets, including translucent palettes; per-color adjustment with live preview, custom wallpaper / header, selectable AtomChat or E33Chat player-avatar style, separate panel and content scaling.
- **Feedback sounds** — button, send and panel-open feedback share a soft CC0 bubble source; notification and feedback sounds have separate switches and volumes.
- **Experimental chat effects** — hearts, birthday cakes and fireworks appear inside the open chat panel for matching Chinese or English phrases, controlled in Chat and notifications → Advanced.
- **Player profiles** — the server synchronizes signatures and cropped headers; hosted profile images are preserved per world, with later joins and reconnects supported.
- **Notifications** — HUD and in-panel banners share a queue and lifetime; opening chat transfers the banner without restarting it. The live notification editor supports dragging, corner resizing and nine anchors. HUD banners hide in menus and with F1 or F3.
- **Controls and settings** — world-chat view filters, cross-message drag selection and copy, block list; settings apply instantly and persist, with reset controls for the current settings tab. Chat history can be saved per server or world, and conversation drafts survive closing and reopening during the same connection.

## Install

1. Download the AtomChat consumer JAR and matching AtomLib JAR for your loader and Minecraft version from [Releases](https://github.com/NoWordz/AtomChat-Mod/releases).
2. Place both in the client's `mods/` folder. Fabric also requires Fabric API. Remove older copies of the same mod before starting.
3. Open chat in game, or press `Y` to open the panel directly.

For optional server media hosting and profile synchronization, add only AtomChat to the server's `mods/` folder. Dedicated servers do not need AtomLib.

## Compatibility

| Target | Requires |
| --- | --- |
| Fabric 1.21.1 | Fabric Loader 0.16+, Fabric API, matching AtomLib client JAR |
| NeoForge 1.21.1 | NeoForge 21.1+, matching AtomLib client JAR |
| Forge 1.20.1 | Forge 47+, matching AtomLib client JAR |

Java 21 for the 1.21.1 targets, Java 17 for Forge 1.20.1. AtomChat is now one architecture-independent consumer JAR per loader; UI natives, Skija and Yoga are owned by the matching AtomLib JAR. AtomLib's first release scope is **Windows x64 and Windows ARM64**, selected from the JVM architecture by the library. Linux/macOS clients are outside this new native support scope; existing legacy AtomChat artifacts remain in release history. Dedicated servers do not require AtomLib or initialize its natives; install AtomChat on the server for media hosting and profile synchronization.

Item links using Minecraft's `SHOW_ITEM` hover data have inline icons and native tooltips in the AtomChat panel. Quark's action-emote integration is available on Forge/NeoForge; Fabric uses the common item-link support without a Quark dependency. Actual item sharing still requires the sharing mod's own client/server support.

Chat HUD decorations and complete chat-screen replacements need separate integration: Chat Heads and ChatAnimation do not decorate the AtomChat panel; Chat Patches can change message formatting; Emojiful can replace the chat screen and uses a separate emoji renderer. Co-installation does not imply that all of these features work inside AtomChat. The new item/Quark integration still requires in-game combination testing.

## Configuration

Use the in-game settings for live changes. Client configuration lives at `config/atomchat/atomchat-client.json`.

| Settings | Common keys |
| --- | --- |
| Layout | `panelWidth`, `panelHeight`, `uiScale`, `contentScale` |
| Appearance and motion | `avatarStyle` (`ATOMCHAT` or `E33CHAT`), `pageNavStyle` (slide or zoom), `animationEnabled`, `blurEnabled` |
| Sound | `notificationSoundEnabled`, `notifyVolume`, `feedbackSoundEnabled`, `feedbackVolume` |
| Other | `funEffectsEnabled`, `debug` |

Panel dimensions are design pixels, rather than physical display pixels. The height limit follows the available window height and scale. Existing layout choices are kept; use the Appearance → Adjust reset to adopt the viewport-fitted defaults.

Personal and runtime data live beside `config` under `atomchat/`; only four top-level folders need remembering:

```text
atomchat/
├─ client/   your avatar, banner, wallpaper, emotes, presets, history and client-data.json
├─ cache/    images, downloaded packs and debug captures; safe to clear
├─ server/   hosted media, uploaded avatars and distributed emotes when hosting
└─ backups/  verified migration copies and conflicts
```

Client folders and `client-data.json` are created on first launch. Existing `config/atomchat` content, the former `atomchat-data/` directory and the earlier flat preview layout migrate automatically. If migration reports a warning, inspect the log and backups before sharing your pack. Exclude the entire `atomchat/` directory from modpack exports.

Server policy lives in `config/atomchat/atomchat-server.json`, including `hostingEnabled`, `retentionDays` and `maxFileKb`. Durable profile data lives under the world save's `atomchat/profiles/`; `maxProfileTotalMb` sets its image capacity (default 64 MiB per world, configured in the file). Temporary chat-media expiry does not delete the durable copy; external-host links can still expire.

## Known limitations

- The current AtomLib client native distribution targets Windows x64 and Windows ARM64. Windows x64 has user validation; Windows ARM64 remains untested in-game. Linux CI builds are not proof of Linux client support. Linux, macOS and Android are outside the current client distribution scope.
- Without server hosting, images use a third-party host whose availability and retention can change. Server-hosted files must fit the server's configured limit. Disconnecting cancels uploads; a cancelled upload is not retried on a third-party host.
- Player identity parsing is best-effort — exotic nickname-plugin formats degrade to grey system messages.
- Dedicated-server and modpack combinations need broader testing. Intermittent reports of blocked input, a stuck dusk theme and white or missing UI remain unresolved; bounded debug diagnostics are available for a natural reproduction.

## FAQ

**Is the server install required?** — No for your own use; it adds media hosting and profile synchronization for everyone, including LAN hosts.

**Do images go to the server or an image host?** — To the server when it runs AtomChat with `hostingEnabled=true`, otherwise to the fallback host. `Stored hosted media` in the client log means hosting was used.

**Where are emotes stored?** — Yours in `atomchat/client/emotes/`; server-pushed packs in a read-only "This server" section.

**Can I keep typing while an image uploads?** — Yes. The result returns to the conversation where the upload began. If its draft is full, free enough space and return to that conversation; switching servers or disconnecting cancels unfinished results.

## Building from source

Single-branch multi-target layout: `shared/` holds mapping-neutral logic, `layers/mapping/official/` the official-mappings layer, and `platforms/<target>/` one Gradle project per target. AtomChat no longer owns or embeds Skija/Yoga. Pass a pinned local AtomLib Maven input explicitly. If you have a local AtomLib build, point the property at it directly; otherwise fetch the pinned input from the public AtomLib release first (URL and SHA-256 come from `versions/atomlib.json`):

```bash
python tools/fetch_atomlib_input.py --output build/atomlib-input-path
ATOMLIB_MAVEN_REPO="$(cat build/atomlib-input-path)"
cd platforms/1.21.1-fabric
./gradlew build -PatomlibMavenRepo="$ATOMLIB_MAVEN_REPO" --no-daemon
# JDK 21; Forge 1.20.1 uses JDK 17, NeoForge 1.21.1 uses JDK 21
```

The build runs the default tests and produces the consumer JAR in `build/atomlib-consumer/libs/` under the selected platform. Optional GPU benchmarks require a graphics context and explicit opt-in; they are separate from the default test run. Windows users can use `gradlew.bat` with the same property.

See the [wiki](https://github.com/NoWordz/AtomChat-Mod/wiki) for player documentation, [project status](docs/STATUS.md) for development state, and [CHANGELOG](CHANGELOG.md) for version history.

## License

[MIT License](LICENSE) Third-party components: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). AtomChat's own source is MIT; AtomLib is a separate Apache-2.0 project with its own notices.

Copyright &copy; 2026 E33EPUS & NoWordz
