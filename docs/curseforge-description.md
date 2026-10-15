<div style="text-align:center;">
  <h1>AtomChat</h1>
  <p align="center">
  <em>A phone-app style chat experience for Minecraft, powered by Skija.</em>
</p>
  <p align="center">
    <img alt="MC" src="https://img.shields.io/badge/MC-1.21.1%20%7C%201.20.1-green" />
    <img alt="Loaders" src="https://img.shields.io/badge/Loaders-Forge%20%7C%20Fabric%20%7C%20NeoForge-orange" />
    <img alt="Side" src="https://img.shields.io/badge/Side-Client%20required%20%7C%20Server%20optional-blue" />
    <img alt="OS" src="https://img.shields.io/badge/OS-Windows%20%2F%20Linux%20x64-lightgrey" />
    <img alt="Java" src="https://img.shields.io/badge/Java-17%2B%20%7C%2021%2B-yellow" />
    <img alt="License" src="https://img.shields.io/badge/License-MIT-brightgreen" />
  </p>
</div>

AtomChat replaces the vanilla chat screen with a phone-style panel for public chat, private conversations and rich media. Its interface is vector-drawn with Skija and does not depend on vanilla chat textures.

**Client required · Server optional.** A server install adds hosted media, shared reactions, profile sync and server-provided content. Windows x64 and Linux x64 use separate JARs. The Linux package passed a WSL Skija native-load and CPU-raster smoke test; full Minecraft UI/GPU verification is pending.

---

## 💬 Chat & Private Conversations

- Public and private chats share one panel, with rounded bubbles, real player names and skin avatars.
- Unread markers, per-conversation drafts and optional per-server or per-world history keep conversations organized.
- Quote replies, rich text, chat filters, blocking and a multi-line composer are built in.

## 🖼️ Media & Reactions

- Send images and looping GIFs; browse emoji, kaomoji and stickers; react to messages with standard emoji.
- Pick, paste or drag images into chat. Messages use the `[[CICode]]` protocol shared by the E33Chat / ChatImage family.
- Images use server hosting when enabled; otherwise AtomChat falls back to an external image host. Shared reactions require AtomChat on the server.

## 👤 Profiles & Personalization

- Customize your profile signature and banner. Saved custom avatars still appear in AtomChat style; E33Chat style uses player skins. On AtomChat servers, profile details sync to other AtomChat players, and profile images are stored per world.
- Choose a theme, adjust interface colors live and use a custom wallpaper.

## ✨ Effects & Sounds

- Notification banners highlight mentions, quotes and whispers.
- Optional phrase-triggered effects add hearts, birthday cakes or fireworks.
- Notification and interface sounds have separate controls.

---

## ✅ Compatibility

| Mod / protocol | Compatibility |
| --- | --- |
| `[[CICode]]` — [E33Chat](https://www.curseforge.com/minecraft/mc-mods/e33chat) / [ChatImage](https://www.curseforge.com/minecraft/mc-mods/chatimage) | Image-message protocol interoperability |
| [ChatHeads](https://www.curseforge.com/minecraft/mc-mods/chat-heads) | Compatibility with Hud |
| [Chat Animation](https://www.curseforge.com/minecraft/mc-mods/chatanimation) | Compatibility with Hud |
| Nickname / display-name plugins | Best effort; unrecognized formats may appear as system messages |
| [EasyBot-MCDR](https://mcdreforged.com/en/plugin/easybot_mcdr/introduction) | Common QQ relay formats are recognized |
| [Quark](https://www.curseforge.com/minecraft/mc-mods/quark) | Known issue: the emote menu may not appear |

---

## ⚙️ Configuration & Storage

Expand for the client and server configuration paths and data locations.

### 📁 Client Configuration & Local Content

| Path | Contents |
| --- | --- |
| `config/atomchat/atomchat-client.json` | Client settings; created on first launch. |
| `config/atomchat/avatar/avatar.*` | Custom profile avatar (`png`, `jpg`, `jpeg` or `webp`). |
| `config/atomchat/banner.*` | Custom profile banner (`png`, `jpg`, `jpeg` or `webp`); stored directly in `config/atomchat/`. |
| `config/atomchat/debug/` | Avatar sampling images written in debug mode. |
| `config/atomchat/emotes/` | Your own stickers (`png`, `jpg`, `jpeg` or `gif`); unlimited by default, with an optional `emoteMax` cap. |
| `config/atomchat/history/` | Optional JSONL chat history, saved per server or world; off by default. |
| `config/atomchat/wallpaper/wallpaper.*` | Custom panel wallpaper (`png`, `jpg`, `jpeg` or `webp`). |

### 📦 Client Runtime Data

| Path | Contents |
| --- | --- |
| `atomchat-data/image-cache/` | Chat image cache, capped at 500 files / 100 MB; files unused for 7 days are removed. View or clear it under Settings → About. |
| `atomchat-data/packs/<server-key>/` | Sticker packs downloaded from servers. |
| `atomchat-data/avatars/` | Player avatars received from servers. |

### 🖥️ Server Configuration & Data

| Path (relative to the server game directory) | Contents |
| --- | --- |
| `config/atomchat/atomchat-server.json` | Server hosting, capacity and server-pack settings. |
| `config/atomchat/server-emotes/` | Sticker source offered to players; separate from a player's own `emotes/` folder. |
| `atomchat-data/media/` | Chat images and GIFs hosted by the server. |
| `atomchat-data/avatars/` | Player avatars uploaded to the server. |
| `<world save>/atomchat/profiles/documents/` | Synced profile records, one per player UUID. |
| `<world save>/atomchat/profiles/images/` | World-scoped copies of synced profile images. |

---

## 🖥️ For Server

A server install enables hosted media and profiles, shared message reactions, and server-provided stickers and quick phrases.

<div class="spoiler" data-title="Hosting, profiles & server packs">

### ⚙️ Configuration

The server creates `config/atomchat/atomchat-server.json` on first launch. Edit it directly or use `/atomchat gui` in-game (permission level 2).

### 🗂️ Media & Profile Hosting

- `hostingEnabled` is on by default.
- Defaults: 2 MB per file, 512 MB media storage, and 7-day media retention. Set `retentionDays` to `0` to keep hosted media indefinitely.
- Custom profile images have a separate 64 MB per-world limit and are not removed by temporary media cleanup.
- Hosted files travel over the Minecraft connection; AtomChat opens no HTTP port.

### 🎁 Server Packs

- `packEnabled` is on by default.
- The server can offer up to 32 sticker files / 8 MB and up to 20 quick phrases of 200 characters each.
- Put server stickers in `config/atomchat/server-emotes/`. The server icon and MOTD can provide the server identity shown in the client.

</div>

---

## ❓ FAQ

<div class="spoiler" data-title="Frequently asked questions">

- **Is the server mod required?** No. The client mod works on its own. A server install enables hosted media, profile sync, shared reactions and server packs.
- **Where do images go?** With server hosting enabled, images are stored on that server and kept according to its retention settings. Otherwise uploads use uguu.se; those links expire after roughly three hours. Images upload only when you choose, paste or drag a file, but recipients may save or forward them. Chat history may also be saved locally by players who enable it. Do not send sensitive content.
- **Can I include AtomChat in a modpack?** Yes, under the MIT license. Keep the [third-party notices](https://github.com/NoWordz/AtomChat-Mod/blob/main/THIRD_PARTY_NOTICES.md) with the distributed files.

</div>

## 📱 Feedback

- Issues and suggestions: [Issues](https://github.com/NoWordz/AtomChat-Mod/issues)
- Full documentation: [Wiki](https://github.com/NoWordz/AtomChat-Mod/wiki)