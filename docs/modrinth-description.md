![AtomChat](https://cdn.modrinth.com/data/cached_images/489114fbc1ad9034d754b51f698975b71cebbc48.png)

# AtomChat

_A phone-app style chat experience for Minecraft, powered by Skia._


AtomChat turns the vanilla chat screen into a standalone "phone app" style chat panel: rounded bubbles, real player names with round skin avatars, emoji / kaomoji / stickers, image and animated GIF messages, server-side media hosting, copy and quote, multi-line input and QQ-style motion.

Every interface is vector-drawn with [Skija](https://github.com/HumbleUI/skija) and does not depend on vanilla chat textures. Image messages keep the `[[CICode]]` protocol of [E33Chat](https://github.com/E33EPUS/E33Chat), so they interoperate with the E33Chat / ChatImage family — this project is a clean rewrite in its spirit, not a fork of it.

The mod is **client-side only**: it works without a server install (images go to a third-party host). Installing it on the server as well unlocks **server-side media hosting** and **custom avatar sync**.

**Windows x64 only for now** — the JAR bundles only the Windows x64 Skija native, so the chat panel cannot start on Linux or macOS.

## Features

*   📱 **Phone panel** — the vanilla chat HUD hides while chat is open and AtomChat takes over: blurred background (wallpaper optional), three bottom tabs (Chat / Profile / Settings), page-level push transitions
*   💬 **Message bubbles** — yours on the right, everyone else's on the left, with real player names and round skin avatars (online / offline skins resolve with a fallback); quote pills, time dividers, system-message capsules
*   🖼️ **Image & GIF messages** — renders `[[CICode]]` natively at the original aspect ratio; GIFs loop inside the bubble; transparent images blend with the panel; right-click saves the original
*   📤 **Sending local images** — pick with the image button, drag a file into the window, or paste with Ctrl+V; the upload inserts itself into the draft
*   📦 **Server-side media hosting** — with AtomChat on the server, images / GIFs are stored there and handed out to other players: content-addressed dedup, on-demand chunked delivery, game connection only, **no HTTP port opened**; if the server lacks AtomChat, hosting is off, the file is too large or the upload fails, the client falls back to the external host
*   🎁 **Server packs** — joining a server syncs its stickers, quick phrases, server icon and MOTD; the sticker panel gains a read-only "This server" section and the phrases panel a read-only group; transfer is sha256-verified per file and only sends the delta; Settings → Privacy can turn it off
*   😀 **Emoji / kaomoji / stickers** — three tabs with a sliding indicator and full-width push transition; your own stickers live locally, unlimited by default with a scrolling grid (GIFs show their first frame in the grid and animate once sent)
*   ⚡ **Phrases & input** — the lightning button opens your quick phrases (tap to insert, never sends straight away, up to 20 at 256 characters each); multi-line input, @ mentions, IMBlocker command-state bridging, WATUT "typing" indicator
*   🔗 **Rich text & context menus** — names and bodies carry colors, underlines, click and hover events: `/tell`, coordinates, FTB accept / deny and external links are all clickable, bare URLs become links; the menu offers copy / quote / save / @ / whisper / teleport / block
*   👥 **Whispers & conversation list** — ordered public → online → recent offline with real IDs, skin avatars, presence dots and unread badges; whispers run over `/msg` with per-conversation drafts; a separate profile page shows player details
*   🔔 **Notifications** — being @mentioned, quoted or whispered pops a banner at the top of the panel (click to jump to the message) with an original synthesized cue; switches and volume live in Settings → Chat → Notifications
*   🎨 **Appearance** — one-tap theme presets (frosted / modern) plus every interface colour exposed with live previews; background blur, panel width 400–600, UI scale x0.75–x1.50, corner style, message entry animation; a custom wallpaper is downscaled to a 1024 px longest side
*   🧹 **Chat hygiene** — public feed filter (all / system only / players only); drag across several messages and copy them all with Ctrl+C; repeated messages merge with a counter; same-sender runs pack tightly; a visual block list
*   ⚙️ **Settings & persistence** — a Windows-11 style 2×2 tile home (Appearance / Chat / Privacy & blocking / About); every option applies and persists instantly, no restart; chat history can optionally be saved per server / world and restored on rejoin

## How to use

1.  Open chat and the phone panel is there: the public feed and system clock on top, the message list in the middle, the composer at the bottom
2.  Type and hit Enter; the composer grows when the text wraps, and Up/Down still move the caret between lines
3.  Send an image with the image button, by dragging a file into the window, or with Ctrl+V
4.  The emoji button opens the panel with `emoji` / `kaomoji` / `stickers` tabs
5.  Right-click a message to copy or quote it; click an avatar for the profile, double-click to poke, right-click for the menu (@ mention / whisper / teleport / block)

## Which route do images take?

A server running AtomChat **prefers** to host your images: the message carries an `atomchat-media` short link and other players fetch it from the server. Any of these falls back to the third-party host [uguu.se](https://uguu.se) (links expire in roughly 3 hours): the server has no AtomChat, `hostingEnabled` is off, the file exceeds `maxFileKb`, or the upload fails.

To see which route was used, check the client log: a successful host shows `Stored hosted media`. Hosted media is kept for 7 days by default (`retentionDays`, `0` = forever) and trimmed oldest-first against the size caps.

## Installation

| Requirement | Version |
| --- | --- |
| Minecraft | 1.21.1 (Fabric / NeoForge builds) or 1.20.1 (Forge build) |
| Fabric build | Fabric Loader 0.16.0+ and Fabric API, Java 21+ |
| NeoForge build | NeoForge 21.1+, Java 21+ |
| Forge build | Forge 47+ (1.20.1), Java 17 |
| OS | Windows x64 (macOS / Linux not usable yet) |

Download the JAR for your platform and drop it into `.minecraft/mods/`. To host images / GIFs and sync avatars on your server, put the same JAR into the server's `mods/` too. Then launch the game and press the chat key (default `T` / `/`); `Y` (default, rebindable under Controls → `AtomChat`) opens the panel directly and restores the page you were last on.

## Server config (optional, for hosts)

The server writes `config/atomchat/atomchat-server.json` on first launch, and it can also be edited in game with `/atomchat gui` (permission level 2; single-player is let through; a console has no drawable screen and says so). The person who saves sees the change immediately, everyone else on the next join.

**Media hosting** (`hostingEnabled`, on by default): `maxFileKb` 2048 KB per file, `maxTotalMb` 512 MB media store, `maxAvatarTotalMb` 64 MB avatar store, `retentionDays` 7 (`0` = forever), `uploadCooldownMs` 3000 ms between uploads by one player.

**Server packs** (`packEnabled`, on by default): `packMaxFiles` 32 sticker files offered, `packMaxMb` 8 MB total, `packName` the name shown in the client panel (blank = the cleaned first MOTD line), `phrases` the quick phrases handed out (at most 20, each at most 200 characters).

Stickers to hand out go into the server's `config/atomchat/server-emotes/` — deliberately separate from the player's own `config/atomchat/emotes/`, so a single-player or LAN host does not push its own collection to guests. A dedicated server copies the old folder's stickers over once when it first builds a pack (copy only, never delete). Clients enforce a hard cap of 200 files / 16 MB per pack.

## Compatibility

| Mod / plugin | Status |
| --- | --- |
| Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.20.1 | ✅ Supported (one shared implementation) |
| Server install | ✅ Optional — enables media hosting and avatar sync |
| `[[CICode]]` image protocol | ✅ Interoperable with the E33Chat / ChatImage family |
| ChatHeads / chat-animation style mods | ✅ Compatible |
| Nickname / display-name plugins | 🟡 Best effort; extreme unknown formats fall back to gray text |
| EasyBot | 🟡 Best effort (QQ-style message parsing); unparsable lines fall back to gray text |
| Quark | 🚫 No emoji menu; compatibility work in progress |
| Other loaders / versions | ❌ Fabric / NeoForge 1.21.1 and Forge 1.20.1 only |

## Known limitations

1.  **Windows x64 only**: only the Windows x64 Skija native ships, so Linux and macOS cannot run the panel
2.  **No hosting beyond your server**: without AtomChat on the server, images go to uguu.se (about 3 hours); files over `maxFileKb` (2 MB by default) are not uploaded to the server either, and the client will not recompress them
3.  **Player identity resolution is best effort**: anything unrecognized is conservatively shown as a gray system message
4.  **Dedicated-server end-to-end testing is incomplete**: the hosting path has only been exercised against an integrated (single-player / LAN host) server so far
5.  **Smaller feature set than E33Chat**: no whisper sidebar or search yet (the format templates `chatTemplates` / `whisperTemplates` work if hand-edited)

## Privacy & data

> **Warning**
> Images you send are uploaded to a third-party host (uguu.se by default) or, when the server hosts media, stored on that server's disk — and other players can save or forward them. Your messages can also be saved locally by players who enabled chat history. **Do not send anything sensitive or private.**

The mod sends no telemetry and no personal data; uploads only happen when you pick, paste or drag an image in. Hosting transfers bytes over the game connection only — it opens no HTTP port. Config, avatars, wallpaper and stickers stay in `.minecraft/config/atomchat/` and are never synced to anyone automatically.

## FAQ

**Do I need it on the server?** No — it is a client-side mod. A server install additionally enables media hosting and avatar sync.

**Where are stickers stored?** Your own live in `.minecraft/config/atomchat/emotes/` (unlimited by default, png / jpg / jpeg / gif; `emoteMax` in `atomchat-client.json` sets a cap); stickers offered by the server appear in the read-only "This server" section and are counted separately.

**Why is a message shown in gray?** When the client cannot be sure a line is player chat it conservatively falls back to gray system text — common with nickname plugins using unrecognized formats.

**Can I put it in a modpack?** Yes. The code is Apache-2.0 and needs no extra permission; if you redistribute the JAR, keep the third-party notices from the GitHub repository.

## Links

*   Source & issues — https://github.com/E33EPUS/AtomChat
*   Full documentation (config reference, host manual) — https://github.com/E33EPUS/AtomChat/wiki
