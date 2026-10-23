<p align="center">
<img width="400" height="250" alt="logo1" src="https://github.com/user-attachments/assets/354cc7eb-4c1c-49f0-9759-bbe18bf11a14" />
</p>

<h1 align="center">AtomChat</h1>

<p align="center">
  <em>A phone-app style chat experience for Minecraft, powered by Skija.</em>
</p>

## What it is

A client-side mod that replaces the vanilla chat screen with a vector-drawn phone panel: rounded message bubbles, real skin avatars, private chat sessions, quote replies, image and GIF messages. The interface is drawn entirely with [Skija](https://github.com/HumbleUI/skija) and does not use vanilla chat textures. Image messages follow the `[[CICode]]` protocol and interoperate with the E33Chat / ChatImage family. Installing the mod on the server is optional; it enables media hosting and synchronization of custom avatars, profile headers and signatures.

## Features

- **Phone panel** — frosted-glass or wallpaper background, three bottom tabs (chat / profile / settings), slide and zoom page transitions, switchable globally.
- **Bubbles and identity** — own messages right, others left; real player names with round skin avatars, quote capsules, @ highlights, flood grouping, history folding at 100 messages per batch.
- **Image messages** — upload via picker, drag-and-drop or Ctrl+V; GIFs loop inside the bubble; left-click for a fullscreen preview, right-click to save the original.
- **Private chat and notifications** — a foldable conversation list; per-session drafts and scroll positions; banners for mentions, quotes and DMs with click-to-jump; double-click an avatar to poke.
- **Appearance** — nine themes including two translucent ones, per-color adjustment with live preview, custom wallpaper / header, selectable AtomChat or E33Chat player-avatar style, content scale slider.
- **Feedback sounds** — button, send and panel-open feedback share a soft CC0 bubble source; notification and feedback sounds have separate switches and volumes.
- **Experimental chat effects** — hearts, birthday cakes and fireworks appear inside the open chat panel for matching Chinese or English phrases, controlled in Chat and notifications → Advanced.
- **Player profiles** — the server synchronizes signatures and cropped headers; hosted profile images are preserved per world, with later joins and reconnects supported.
- **Controls and settings** — world-chat view filters, cross-message drag selection and copy, block list; settings apply instantly and persist; chat history can be saved per server or world.

## Compatibility

| Target | Requires |
| --- | --- |
| Fabric 1.21.1 | Fabric Loader 0.16+, Fabric API |
| NeoForge 1.21.1 | NeoForge 21.1+ |
| Forge 1.20.1 | Forge 47+ |

Java 21 for the 1.21.1 targets, Java 17 for Forge 1.20.1. **Windows x64, Windows ARM64 and Linux x64 have separate JARs.** Choose exactly one matching your OS and Minecraft JVM architecture. Filenames follow `atomchat-<loader>-<mc>-<mod>-<os>-<arch>.jar`. Each JAR bundles Skija 0.143.18 with only its matching native. Windows ARM64 packaging is experimental pending testing on an ARM64 Windows machine. Linux needs EGL/OpenGL libraries (Ubuntu/Debian: `sudo apt install libegl1 libgl1`). Install the same variant on the server for media hosting and profile synchronization.

Item links using Minecraft's `SHOW_ITEM` hover data have inline icons and native tooltips in the AtomChat panel. Quark's original action-emote buttons and panel are retained on Forge/NeoForge; Fabric uses the common item-link support without a Quark dependency. Actual item sharing still requires the sharing mod's own client/server support.

Chat HUD decorations and complete chat-screen replacements need separate integration: Chat Heads and ChatAnimation do not decorate the AtomChat panel; Chat Patches can change message formatting; Emojiful can replace the chat screen and uses a separate emoji renderer. Co-installation does not imply that all of these features work inside AtomChat. The new item/Quark integration still requires in-game combination testing.

## Configuration

Client config lives at `config/atomchat/atomchat-client.json` and every option applies instantly. Common client keys: panelWidth, uiScale, contentScale, pageNavStyle (slide or zoom), avatarStyle (ATOMCHAT or E33CHAT), animationEnabled, blurEnabled, notificationSoundEnabled, notifyVolume, feedbackSoundEnabled, feedbackVolume, funEffectsEnabled and debug.

Personal and runtime data live beside `config` under `atomchat/`; only four top-level folders need remembering:

```text
atomchat/
├─ client/   your avatar, banner, wallpaper, emotes, presets, history and client-data.json
├─ cache/    images, downloaded packs and debug captures; safe to clear
├─ server/   hosted media, uploaded avatars and distributed emotes when hosting
└─ backups/  verified migration copies and conflicts
```

Client folders and `client-data.json` are created on first launch. Existing `config/atomchat` content, the former `atomchat-data/` directory and the earlier flat preview layout migrate automatically. If migration reports a warning, inspect the log and backups before sharing your pack. Exclude the entire `atomchat/` directory from modpack exports.

Server policy lives in config/atomchat/atomchat-server.json, including hostingEnabled, retentionDays and maxFileKb. Durable profile data lives under the world save's atomchat/profiles/; maxProfileTotalMb sets its image capacity (default 64 MiB per world, configured in the file). Temporary chat-media expiry does not delete the durable copy; external-host links can still expire. All settings fold groups start expanded.

## Known limitations

- Windows x64 rendering with Skija 0.143.18 passed user validation on Fabric 1.21.1. Windows ARM64 remains untested in-game, and Linux x64 GPU validation still needs renewal after the upgrade; earlier Linux/WSLg validation used Skija 0.116.8. macOS, Linux ARM64 and Android natives are not bundled.
- Without the server mod, images fall back to a third-party host (roughly 3-hour expiry); files above `maxFileKb` are not uploaded.
- Player identity parsing is best-effort — exotic nickname-plugin formats degrade to grey system messages.
- The dedicated-server hosting path has only been tested against singleplayer and LAN hosts.

## FAQ

**Is the server install required?** — No for your own use; it adds media hosting and profile synchronization for everyone, including LAN hosts.

**Do images go to the server or an image host?** — To the server when it runs AtomChat with `hostingEnabled=true`, otherwise to the fallback host. `Stored hosted media` in the client log means hosting was used.

**Where are emotes stored?** — Yours in `atomchat/client/emotes/`; server-pushed packs in a read-only "This server" section.

## Install

1. Download the jar for your loader from [Releases](https://github.com/NoWordz/AtomChat-Mod/releases) (Forge: the non-`-slim` jar).
2. Place it in `mods/`; add the same jar to the server's `mods/` for media hosting.
3. Open chat in game, or press `Y` to open the panel directly.

## Building from source

Single-branch multi-target layout: `shared/` holds mapping-neutral logic, `layers/mapping/official/` the official-mappings layer, and `platforms/<target>/` one Gradle project per target.

```bash
cd platforms/1.21.1-fabric && ./gradlew build    # JDK 21; other targets: change directory, Forge 1.20.1 uses JDK 17
```

See the [wiki](https://github.com/NoWordz/AtomChat-Mod/wiki) for details.

## License

[MIT License](LICENSE) Third-party components: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Copyright &copy; 2026 E33EPUS & NoWordz

HUD and panel banners use the same queue, card renderer and expiry. Opening or closing chat fades between hosts for 120 ms without restarting the six-second lifetime. Chat banner settings open a live layout editor with dragging, corner resizing and nine anchor snaps; HUD banners are display-only and hide in menus, F1 and F3. New configurations use compact messages, single-corner bubbles, anti-spam off and 100% interaction volume; existing choices are preserved. Composer drafts survive closing and reopening during the current connection.
