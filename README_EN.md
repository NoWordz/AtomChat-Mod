[简体中文](README.md) | [English](README_EN.md)

<h1 align="center"><img width="256" height="256" alt="logo" src="https://github.com/user-attachments/assets/e449c62e-644b-4c19-9fbc-431d7a899781" /></h1>

<h1 align="center">AtomChat</h1>

<p align="center">
  <em>A phone-app style chat experience for Minecraft</em>
</p>

<p align="center">
  <img alt="MC" src="https://img.shields.io/badge/MC-1.21.1%20%7C%201.20.1-green">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-Fabric-orange">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-NeoForge-blue">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-Forge-red">
  <img alt="Side" src="https://img.shields.io/badge/Side-Client%20%7C%20Server-blue">
  <img alt="OS" src="https://img.shields.io/badge/OS-Windows%20x64-lightgrey">
  <img alt="Java" src="https://img.shields.io/badge/Java-17%2B-yellow">
  <img alt="Version" src="https://img.shields.io/github/v/release/E33EPUS/AtomChat?sort=semver">
  <img alt="License" src="https://img.shields.io/badge/License-Apache--2.0-brightgreen">
</p>

<p align="center">
  <a href="https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml"><img alt="Build" src="https://github.com/E33EPUS/AtomChat/actions/workflows/build.yml/badge.svg?branch=main"></a>
</p>

> **v0.3.0 is out** · [Download the JAR](https://github.com/E33EPUS/AtomChat/releases) · [Wiki](https://github.com/E33EPUS/AtomChat/wiki)

> [!WARNING]
> **Windows x64 only for now.** The JAR bundles only the Windows x64 Skija native, so the chat panel cannot start on Linux or macOS. See [Known limitations](#known-limitations).

## What it is

AtomChat turns the vanilla chat screen into a standalone "phone app" style chat panel: rounded bubbles, real player names with round skin avatars, emoji / kaomoji / stickers, image and animated GIF messages, server-side media hosting, copy and quote, multi-line input and QQ-style motion.

Every interface is vector-drawn with [Skija](https://github.com/HumbleUI/skija) and does not depend on vanilla chat textures. It is a clean rewrite in the spirit of [E33Chat](https://github.com/E33EPUS/E33Chat), **not a fork of it**; image messages keep its `[[CICode]]` protocol, so they interoperate with the E33Chat / ChatImage family.

The mod is **client-side only**: it works without a server install (images go to a third-party host). Installing it on the server as well unlocks **server-side media hosting** and **custom avatar sync**.

## Installation

| Requirement | Version |
|---|---|
| Minecraft | 1.21.1 (Fabric / NeoForge builds) or 1.20.1 (Forge build) |
| Fabric build | Fabric Loader 0.16.0+ and Fabric API |
| NeoForge build | NeoForge 21.1+ |
| Forge build | Forge 47+ (1.20.1) |
| Java | 21+ for Fabric / NeoForge; 17 for the Forge 1.20.1 build |
| OS | Windows x64 (macOS / Linux not usable yet) |

1. Download the JAR for your platform from [Releases](https://github.com/E33EPUS/AtomChat/releases) (`atomchat-Fabric-1.21.1-*.jar` / `atomchat-NeoForge-1.21.1-*.jar` / `atomchat-Forge-1.20.1-*.jar`), or build it yourself as described in [Development](#development)
2. Drop it into `.minecraft/mods/`; to host images / GIFs and sync avatars on your server, put the same JAR into the server's `mods/` too
3. Launch the game and press the chat key (default `T` / `/`) to open AtomChat; `Y` (default, rebindable under Controls → `AtomChat`) opens the panel directly and restores the page you were last on

## Quick start

1. Opening chat shows the phone panel: the "public feed" plus the system clock on top, the message list in the middle, the composer at the bottom
2. Type and hit Enter; the composer grows when the text wraps, and Up/Down still move the caret between lines
3. Three ways to send an image: the **image button**, **dragging a file into the window**, or **Ctrl+V** after copying one; the upload inserts itself into the draft
4. The **emoji button** opens the panel with `emoji` / `kaomoji` / `stickers` tabs; when the server offers content, the stickers tab also shows a read-only "This server" section
5. Right-click any message to **copy** or **quote reply**; click an avatar for the player profile, double-click to poke, right-click for the menu (@ mention / whisper / teleport / block)

## Features

- 📱 **Phone panel** — the vanilla chat HUD hides while the chat is open and AtomChat takes over: blurred background (wallpaper optional), three bottom tabs (Chat / Profile / Settings), page-level push transitions
- 💬 **Message bubbles** — yours on the right, everyone else's on the left, with real player names and round skin avatars (online / offline skins resolve with a fallback); quote pills, time dividers, system-message capsules
- 🖼️ **Image & GIF messages** — renders `[[CICode]]` natively at the original aspect ratio; GIFs loop inside the bubble; transparent images blend with the panel instead of showing a grey plate; right-click saves the original; outside the panel the vanilla HUD only shows a green `[Image]` or blue `[Quote]`
- 📤 **Sending local images** — the file picker opens in Details view with inline thumbnails; drag & drop and Ctrl+V both work; an uploaded image becomes a CICode automatically
- 📦 **Server-side media hosting** — with AtomChat on the server, images / GIFs are stored on the server and handed out to other players instead of going to a third-party host: content-addressed dedup, on-demand chunked delivery, game connection only, **no HTTP port opened**; when the server lacks AtomChat, hosting is off, the file is too large or the upload fails, the client falls back to the external host (the same `hostingEnabled` master switch also governs avatar sync)
- 🎁 **Server packs** — joining a server syncs its stickers, quick phrases, `server-icon.png` and MOTD; the sticker panel gains a read-only "This server" section and the phrases panel a read-only "This server" group; transfer is sha256-verified per file and only sends the delta (delete one sticker and the next join fetches just that one); Settings → Privacy can turn the whole thing off
- 😀 **Emoji / kaomoji / stickers** — three tabs with a sliding indicator and full-width push transition; your own stickers live locally, unlimited by default (`emoteMax` in `atomchat-client.json` sets a cap) with a scrolling grid, `+` to add and a `×` on hover to delete (GIFs show their first frame in the grid and animate once sent)
- ⚡ **Phrases & input** — the lightning button opens your quick phrases (tap to insert, never sends straight away, up to 20 at 256 characters each); multi-line input, @ mentions, IMBlocker command-state bridging, WATUT "typing" indicator; one phrase ships by default (`/atomchat gui`) so hosts can find the server config without reading docs
- 🔗 **Rich text & context menus** — names and bodies carry colors, underlines, click and hover events: `/tell`, coordinates, FTB accept / deny and external links are all clickable, bare URLs become links; the context menu offers copy / quote / save / @ / whisper / teleport / block
- 👥 **Whispers & conversation list** — the list is ordered public → online → recent offline with real IDs, skin avatars, presence dots and unread badges; whispers run over `/msg` with per-conversation drafts and scroll positions; a separate profile page shows player details
- 🔔 **Notifications** — being @mentioned, quoted or whispered pops a banner at the top of the panel (click to jump to the message and highlight it) with an original synthesized cue; switches and volume live in Settings → Chat → Notifications
- 🎨 **Appearance** — nine theme presets (frosted + eight palettes, including the translucent "Dusk" and "Mint") in one tap plus every interface colour exposed (panel / bubbles / text / cards / outline / accent) with live previews; background blur, panel width 400–600, UI scale x0.75–x1.50, content scale x0.80–x1.50, corner radius, navigation animation style (slide / zoom) and message entry animation; a custom wallpaper is downscaled to a 1024 px longest side
- 🧹 **Chat hygiene** — public feed filter (all / system only / players only, view-only); drag across several messages and copy them all with Ctrl+C; repeated messages merge with a counter; same-sender runs within five minutes pack tightly; timed dividers; a visual block list
- ⚙️ **Settings & persistence** — a Windows-11 style 2×2 tile home (Appearance / Chat / Privacy & blocking / About); every option applies and persists instantly, no restart; chat history can optionally be saved per server / world and restored on rejoin

## Which route do images take?

A server running AtomChat **prefers** to host your images: the message carries an `atomchat-media:<id>` short link and other players fetch it from the server. Any of these falls back to the third-party host [uguu.se](https://uguu.se) (links expire in roughly 3 hours): the server has no AtomChat, `hostingEnabled` is off, the file exceeds `maxFileKb`, or the upload fails.

To see which route was used, check the client log: a successful host shows `Stored hosted media`.

Hosted media is kept for 7 days by default (`retentionDays`, `0` = forever) and trimmed oldest-first against the size caps; the exact numbers are in the [Wiki host manual](https://github.com/E33EPUS/AtomChat/wiki).

## Compatibility

| Item | Status |
|---|---|
| Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1 | ✅ Supported (one shared implementation across all three) |
| Server install | ✅ Optional — enables media hosting and avatar sync; without it the mod is purely client-side |
| `[[CICode]]` image protocol | ✅ Interoperable with the E33Chat / ChatImage family |
| ChatHeads / chat-animation style mods | ✅ Compatible |
| Nickname / display-name plugins | 🟡 Best effort (whisper clicks / Tab names / decorated names); extreme unknown formats fall back to gray text |
| EasyBot | 🟡 Best effort (QQ-style message parsing); unparsable lines fall back to gray text |
| Quark | 🚫 No emoji menu; compatibility work in progress |
| Other loaders / versions | ❌ Fabric / NeoForge 1.21.1 and Forge 1.20.1 only |

## Known limitations

1. **Windows x64 only**: only the Windows x64 Skija native ships, so Linux and macOS cannot run the panel
2. **No hosting beyond your server**: without AtomChat on the server, images go to the third-party host uguu.se (about 3 hours); files over `maxFileKb` (2 MB by default) are not uploaded to the server either, and the client will not recompress them
3. **Player identity resolution is best effort**: tell-click structure capture, an offline "seen" cache and a multi-level `ownDisplayName` fallback; anything unrecognized is conservatively shown as a gray system message
4. **Dedicated-server end-to-end testing is incomplete**: the hosting path has only been exercised against an integrated (single-player / LAN host) server so far; a dedicated server (especially Linux, where no native ships) and two clients pulling from each other are untested
5. **Smaller feature set than E33Chat**: no whisper sidebar or search yet (the server-side format templates `chatTemplates` / `whisperTemplates` work if hand-edited)

## Privacy & data

> [!WARNING]
> Images you send are uploaded to a third-party host (uguu.se by default) or, when the server hosts media, stored on that server's disk — and other players can save or forward them. Your messages can also be saved locally by players who enabled chat history. **Do not send anything sensitive or private.**

- The mod sends no telemetry and no personal data; uploads only happen when you pick, paste or drag an image in
- Hosting transfers bytes over the game connection only — it opens no HTTP port and exposes nothing to players who are not connected
- Config, avatars, wallpaper and stickers stay in `.minecraft/config/atomchat/` and are never synced to anyone automatically
- The full data map and retention rules live in the [Wiki](https://github.com/E33EPUS/AtomChat/wiki)

## FAQ

**Do I need to install it on the server?** No — AtomChat is a client-side mod. Installing it server-side as well enables server-side media hosting and custom avatar sync.

**Do images go to the image host or to the server?** With AtomChat on the server and `hostingEnabled=true` they go to the server; otherwise they fall back to uguu.se. See [above](#which-route-do-images-take) for how to tell.

**Where are stickers stored?** Your own live in `.minecraft/config/atomchat/emotes/` (unlimited by default, png / jpg / jpeg / gif; set `emoteMax` in `atomchat-client.json` to cap it — takes effect when the chat screen reopens); stickers offered by the server appear in the read-only "This server" section and are counted separately. Hosts put the stickers they want to hand out in the server's `config/atomchat/server-emotes/`.

**Why is a message shown in gray?** When the client cannot be sure a line is player chat it conservatively falls back to gray system text — common with nickname plugins using unrecognized formats.

**Can I put it in a modpack?** Yes. The code is Apache-2.0 and needs no extra permission; if you redistribute the JAR, keep the third-party notices in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## More documentation

| Where | What |
|---|---|
| [Wiki](https://github.com/E33EPUS/AtomChat/wiki) | Quick start, images & transfer routes, stickers, appearance with the full config reference, the **host manual**, troubleshooting, developer notes |
| [Issues](https://github.com/E33EPUS/AtomChat/issues) | Bug reports and suggestions (platform comments work too) |
| [Releases](https://github.com/E33EPUS/AtomChat/releases) | JARs for all three platforms plus per-version release notes |
| [CHANGELOG](https://github.com/E33EPUS/AtomChat/blob/main/CHANGELOG.md) | Complete change history (bilingual) |

## Development

```bash
./gradlew.bat build   # build and run the tests
./gradlew.bat test    # tests only
```

Artifacts land in `build/libs/`: the Fabric and NeoForge builds need JDK 21, the Forge 1.20.1 build needs JDK 17; Forge also emits a `-slim` jar without bundled dependencies — **use the non-slim one for releases**.

The source lives on **a single branch**: code every target can use sits in `shared/`, code split by version / loader / mapping family sits in `layers/`, and each target's own code plus build files sit in `platforms/<target>/`. Which targets are supported is declared in `versions/targets.json`; one release produces a jar per target, and adding a target does not touch the build scripts. Module layout, packages and testing are described on the [Wiki developer page](https://github.com/E33EPUS/AtomChat/wiki).

## License

[AtomChat's own code](LICENSE) is Apache-2.0. The distributed JAR bundles Skija (Java bindings), HumbleUI types, FlatLaf and the Skia native library, each under its own license — full texts in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Copyright © 2026 E33EPUS
