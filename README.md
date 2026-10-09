<img width="2560" height="1600" alt="logo1" src="https://github.com/user-attachments/assets/354cc7eb-4c1c-49f0-9759-bbe18bf11a14" />
<h1 align="center"AtomChat></h1>

<p align="center">
  <em>A phone-app style chat experience for Minecraft, powered by Skija.</em>
</p>

## What it is

A client-side mod that replaces the vanilla chat screen with a vector-drawn phone panel: rounded message bubbles, real skin avatars, private chat sessions, quote replies, image and GIF messages. The interface is drawn entirely with [Skija](https://github.com/HumbleUI/skija) and does not use vanilla chat textures. Image messages follow the `[[CICode]]` protocol and interoperate with the E33Chat / ChatImage family. Installing the mod on the server is optional; it enables server-side media hosting and custom avatar sync.

## Features

- **Phone panel** — frosted-glass or wallpaper background, three bottom tabs (chat / profile / settings), slide and zoom page transitions, switchable globally.
- **Bubbles and identity** — own messages right, others left; real player names with round skin avatars, quote capsules, @ highlights, flood grouping, history folding at 100 messages per batch.
- **Image messages** — upload via picker, drag-and-drop or Ctrl+V; GIFs loop inside the bubble; left-click for a fullscreen preview, right-click to save the original.
- **Private chat and notifications** — per-session drafts and scroll positions; banners for mentions, quotes and DMs with click-to-jump; double-click an avatar to poke.
- **Appearance** — nine themes including two translucent ones, per-color adjustment with live preview, custom wallpaper / header / avatar, content scale slider.
- **Controls and settings** — world-chat view filters, cross-message drag selection and copy, block list; settings apply instantly and persist; chat history can be saved per server or world.

## Compatibility

| Target | Requires |
| --- | --- |
| Fabric 1.21.1 | Fabric Loader 0.16+, Fabric API |
| NeoForge 1.21.1 | NeoForge 21.1+ |
| Forge 1.20.1 | Forge 47+ |

Java 21 for the 1.21.1 targets, Java 17 for Forge 1.20.1. **Windows x64 only** — the jar bundles just the Windows x64 Skija native. All three targets work client-side; install the same jar on the server for media hosting and avatar sync.

## Configuration

Client config lives at `config/atomchat/atomchat-client.json` and every option applies instantly. Common keys: `panelWidth`, `uiScale`, `contentScale`, `pageNavStyle` (slide or zoom), `animationEnabled`, `blurEnabled`, `hostingEnabled`, `retentionDays`, `maxFileKb`, `emoteMax`, `debug`. Local avatars, wallpapers and emotes are stored in the same folder.

## Known limitations

- Windows x64 only; the panel cannot start on Linux or macOS.
- Without the server mod, images fall back to a third-party host (roughly 3-hour expiry); files above `maxFileKb` are not uploaded.
- Player identity parsing is best-effort — exotic nickname-plugin formats degrade to grey system messages.
- The dedicated-server hosting path has only been tested against singleplayer and LAN hosts.

## FAQ

**Is the server install required?** — No for your own use; it adds media hosting and avatar sync for everyone.

**Do images go to the server or an image host?** — To the server when it runs AtomChat with `hostingEnabled=true`, otherwise to the fallback host. `Stored hosted media` in the client log means hosting was used.

**Where are emotes stored?** — Yours in `config/atomchat/emotes/`; server-pushed packs in a read-only "This server" section.

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
