# AtomChat

<em>A phone-app style chat panel for Minecraft's vanilla chat box.</em>

## What it is

A client-side mod: pressing your chat key replaces the vanilla chat screen with a phone panel vector-drawn by Skija — rounded bubbles, real skin avatars, whisper conversations, quote replies, pokes, image and GIF messages. Images keep the `[[CICode]]` protocol and interoperate with the E33Chat / ChatImage family. Installing it on the server is optional: it unlocks server-side media hosting and avatar sync; without it the mod is purely client-side.

## Features

- **Phone panel** — blurred or wallpaper background, three bottom tabs (Chat / Profile / Settings), slide or zoom page transitions, switchable globally.
- **Bubbles and identity** — yours right, others left, real player names and round skin avatars; quote pills, @ highlights, spam-merge counters, history folding (100 per batch).
- **Image messages** — upload by picker, drag-drop or Ctrl+V; GIFs loop in the bubble; left-click for the full-size preview (dimmed backdrop, fade-and-zoom); right-click saves the original.
- **Whispers and notifications** — per-conversation drafts and scroll positions over `/msg`; banners for mentions, quotes and whispers jump to the source message; double-click an avatar to poke.
- **Appearance** — nine themes (including translucent Dusk and Mint), every colour configurable with live previews, custom wallpaper / banner / avatar, content-scale slider.
- **Hygiene and settings** — public-feed view filter, drag-select copy across messages, block list; settings apply and persist instantly, chat history can save per server / world.

## Compatibility

| Target | Requires |
| --- | --- |
| Fabric 1.21.1 | Fabric Loader 0.16+, Fabric API |
| NeoForge 1.21.1 | NeoForge 21.1+ |
| Forge 1.20.1 | Forge 47+ |

Java 21 (both 1.21.1 targets) / 17 (Forge 1.20.1). **Windows x64 only**: the jar bundles just the Windows x64 Skija native. All three builds work client-side; put the jar on the server too so others receive your hosted images and avatar.

## Configuration

Client config lives in `config/atomchat/atomchat-client.json`; every option applies and persists instantly. Keys worth knowing: `panelWidth` / `uiScale` / `contentScale` / `pageNavStyle` (slide or zoom) / `animationEnabled` / `blurEnabled` / `hostingEnabled` / `retentionDays` / `maxFileKb` / `emoteMax` / `debug`. Local avatars, wallpaper and stickers live under `config/atomchat/`.

## Known limitations

- Windows x64 only; the panel cannot start on Linux or macOS.
- Without AtomChat on the server, images go to a third-party host (links expire in roughly 3 hours); files over `maxFileKb` are not uploaded.
- Player identity resolution is best effort: extreme nickname-plugin formats fall back to gray system text.
- The hosting path has only been exercised against integrated / LAN-host servers.

## FAQ

**Do I need it on the server?** — Not for your own view; install it there for media hosting and avatar sync.

**Images: host or server?** — Server when it runs AtomChat with `hostingEnabled=true`, otherwise the external host; `Stored hosted media` in the client log means hosting was used.

**Where do stickers live?** — Yours in `config/atomchat/emotes/`; server-offered ones in the read-only "This server" section.

## Install

1. Download the jar for your loader from [Releases](https://github.com/NoWordz/AtomChat-Mod/releases) (for Forge, use the non-`-slim` one).
2. Drop it into `mods/`; put the same jar into the server's `mods/` if you want hosting.
3. Press your chat key or `Y` in game.

## Building from source

Single-branch multi-target layout: `shared/` holds platform-free logic, `layers/mapping/official/` the official-mapping sources, `platforms/<target>/` one Gradle project per target.

```bash
cd platforms/1.21.1-fabric && ./gradlew build    # JDK 21; swap the directory for the other targets, JDK 17 for Forge 1.20.1
```

More in the [wiki](https://github.com/NoWordz/AtomChat-Mod/wiki).

## License

MIT, see [LICENSE](LICENSE). Third-party notices in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
