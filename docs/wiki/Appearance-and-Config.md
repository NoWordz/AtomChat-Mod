**What the settings page holds, how to change your avatar and wallpaper, and what the many keys in the config file mean.**

## The settings page

The third tab at the bottom, **Settings**, is a Windows 11 style 2×2 tile home: **Appearance / Chat / Privacy & Blocking / About**, each opening a subpage.

Every option **applies immediately and is written to disk at once — no game restart**. Colour options come with a preset palette, a `+` custom colour picker and a live preview swatch.

## Appearance

| Option | Notes |
|---|---|
| Theme preset | `Frosted` / `Modern`, one click each; a preset simply writes all the individual items below once, and you can still change them afterwards |
| Background blur | Gaussian blur behind the panel; **mutually exclusive with a custom wallpaper** |
| Panel opacity | 0–100%, applied to the base colour whether blur is on or off |
| Panel width | 400–600 (design pixels) |
| UI scale | x0.75 – x1.50, scaling the whole interface (layout and hit testing included) |
| Message entry animation | The QQ-style slide-in from the side plus fade |
| Animations | Turns off decorative motion (entry/exit, page push, nudge, scroll snapping); functional feedback such as hover highlights is **not** affected |
| Double-click avatar to nudge | On / off |
| All interface colours | Panel background / own bubble / other bubbles / bubble text / secondary capsule / primary and secondary text / card / outline / accent, each with a live preview |

> The corner style (large / medium / small) has **no setting of its own**: presets write it, and changing it by hand means editing `cornerStyle` in the config file.

### Custom avatar

`Appearance → Custom avatar` → pick an image → you land in the cropper:

- **drag** to pan, **scroll** to zoom around the frame, **double-click** to reset;
- the output is a 256-pixel square PNG in `.minecraft/config/atomchat/avatar/`;
- players on the same server who run AtomChat **receive your avatar automatically** (changing it broadcasts a refresh immediately). If they do not run it, or the server turned hosting off, it degrades silently to the skin-based avatar.

### Custom wallpaper

`Appearance → Custom wallpaper` → pick an image → the same cropper. The image is downsampled (long edge ≤ 1024 pixels) and stored in `.minecraft/config/atomchat/wallpaper/`; `png` / `jpg` / `jpeg` / `webp` are supported.

A wallpaper and background blur are **mutually exclusive**; with both configured the wallpaper wins, and panel opacity is used to darken it so the text stays readable.

## Chat

| Option | Default | Notes |
|---|---|---|
| Image messages | on | Off means no download and no cache, with a green `[图片]` placeholder |
| Timestamp dividers | every 5 minutes | The first message always shows one; `0` turns them off |
| Anti-spam merging | on | Consecutive identical messages collapse into one with a count |
| Compact grouping | off | Within five minutes, a sender's consecutive messages keep only the first one's avatar and name |
| Keep chat history | off | When on, stores per server / world in `.minecraft/config/atomchat/history/` (JSONL) and restores it on rejoining |
| History retention | 7 days | `0` means forever |
| Notifications (@ / quotes / private chat) | on | Banners and sounds toggle separately, with an overall volume |
| Teleport mode | `auto` | Cycles between `/tp`, `/tpa` and `auto`; `auto` probes the server's command tree |

## Privacy & Blocking

- **Blocked list:** a visual list (avatar + name + a one-click unblock);
- **Hide blocked players' messages:** on by default. Turned off, their public messages are visible again while private chat stays blocked;
- **Accept server-offered content:** on by default. Turned off, the client stops requesting emotes / phrases / server identity and clears what it has already loaded.

## About

Version, licence, repository links, third-party components (Skija / Skia / FlatLaf), and two actions:

- **Clear image cache** — clears only the image cache under `<game dir>/atomchat-data/`, leaving emotes, wallpaper and avatars alone;
- **Debug mode** — writes the avatar colour sampling into `.minecraft/config/atomchat/debug/` as PNGs, for chasing avatar colour problems.

## Config file and where data lands

Anything you can change in game can also be edited in the file, which is what you want for bulk edits or pre-seeding a pack. The main config is **`.minecraft/config/atomchat/atomchat-client.json`** (generated on first launch).

> Editing the file by hand needs a **game restart**, with one exception: `chatTemplates` / `whisperTemplates`, which are re-read every time the chat screen opens.

| Path | Contents |
|---|---|
| `config/atomchat/atomchat-client.json` | The client's main config |
| `config/atomchat/avatar/` | Custom avatars (256px PNG) |
| `config/atomchat/emotes/` | Your own emotes (unlimited by default, `emoteMax` sets a cap) |
| `config/atomchat/server-emotes/` | **Server only:** the distribution source handed to clients |
| `config/atomchat/wallpaper/` | Custom wallpaper |
| `config/atomchat/history/` | Chat history (off by default, one file per server / world) |
| `config/atomchat/debug/` | Avatar sampling PNGs exported in debug mode |
| `<game dir>/atomchat-data/` | Image cache, server-offered packs, other players' avatar data (500 files / 100 MB / 7 days unused) |

The server's own config and data live under the server's game directory; see the [Server Owner Guide](Server-Owner-Guide).

## The full client config

| Key | Default | Notes |
|---|---|---|
| `panelWidth` / `panelHeight` | `440.0` / `780.0` | Panel size in design pixels, multiplied by UI scale internally |
| `panelOpacity` | `0.93` | Panel background opacity, 0–1 |
| `blurEnabled` | `true` | Background blur (mutually exclusive with a wallpaper) |
| `uiScale` | `1.0` | UI scale, x0.75–x1.50 |
| `animationEnabled` | `true` | Master switch for decorative motion |
| `messageEntryAnimation` | `true` | Message entry animation |
| `avatarPokeEnabled` | `true` | Double-click an avatar to nudge |
| `hideBlockedMessages` | `true` | Whether a blocked player's public messages are dropped outright |
| `cardTint` | `0.235` | Surface tint for cards and the panel frame; presets write this |
| `cornerStyle` | `"large"` | Corner style: `large` / `medium` / `small` |
| `themeName` | `""` | The preset applied last (`frosted` / `modern`) |
| `panelBgColor` | `0xEE16191F` | Panel background colour |
| `panelOutline` / `panelOutlineColor` | `true` / `0xFFFFFFFF` | The white phone-style outline and its colour |
| `ownBubbleColor` / `bubbleTextColor` | `0xFF1E90FF` / `0xFFFFFFFF` | Own bubble: fill / text |
| `otherBubbleColor` / `otherBubbleTextColor` | `0xFF2C3E50` / `0xFFFFFFFF` | Other players' bubbles: fill / text |
| `textPrimaryColor` / `textSecondaryColor` | `0xFFFFFFFF` / `0xDCAAAABA` | Primary / secondary text |
| `secondaryCapsuleBg` / `secondaryCapsuleText` | `0x962C3E50` / `0xDCAAAABA` | Shared by system lines, timestamps and quote capsules |
| `cardColor` | `0xFFFFFFFF` | Card surface colour |
| `accentColor` | `0xFF4A90E2` | Accent (send button, quote bar, scrollbar, …) |
| `imageMessagesEnabled` | `true` | Whether image messages are fetched and rendered |
| `timestampIntervalMinutes` | `5` | Timestamp divider interval in minutes; `0` turns them off |
| `antiSpamEnabled` | `true` | Merge consecutive identical messages with a count |
| `compactMessagesEnabled` | `false` | Compact grouping within five minutes per sender |
| `mentionBannerEnabled` / `mentionSoundEnabled` | `true` | Banner / sound when you are mentioned or quoted |
| `whisperBannerEnabled` / `whisperSoundEnabled` | `true` | Banner / sound on an incoming private message |
| `notifyVolume` | `1.0` | Notification volume, 0–1 |
| `mentionRequireAt` | `false` | `true` counts only an explicit `@name` as a mention; `false` also counts a bare name |
| `serverPacksEnabled` | `true` | Whether to accept server-offered emotes / phrases / identity |
| `emoteMax` | `0` | Cap for your own sticker folder, `0` = unlimited; takes effect when the chat screen reopens |
| `chatHistoryEnabled` / `historyRetentionDays` | `false` / `7` | Keep chat history / days to keep it (`0` = forever) |
| `teleportCommandMode` | `"auto"` | Teleport command mode: `auto` / `tp` / `tpa` |
| `quickPhrases` | `[]` | Quick phrases (up to 20, each ≤256 characters); a fresh config is seeded with `/atomchat gui` once, and deleting it sticks |
| `guiTipSeeded` | `false` | Whether that seed has run (internal marker) |
| `blockedPlayers` | `[]` | Blocked list (real player names) |
| `chatTemplates` / `whisperTemplates` | `[]` | Templates that recognise non-standard chat formats, with the placeholders `{name}` `{display_name}` `{prefix}` `{suffix}` `{sep}` `{content}`; reopen the chat screen after editing |
| `debug` | `false` | Debug output and avatar sampling PNGs |
