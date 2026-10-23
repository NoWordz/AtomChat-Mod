**What installing AtomChat on the server buys you, how to configure it, and when to worry about capacity.**

## Why install it on the server

Without it, the mod is purely client-side: images go through a third-party image host (expiring after about three hours) and everyone sees everyone else's vanilla skin.

With it, three things unlock:

| Capability | What it means |
|---|---|
| **Server-hosted media** | Images and GIFs players send are stored on your server and served to the others: content-addressed deduplication, on-demand chunked transfers, and **over the game connection only — no HTTP port is opened** |
| **Custom avatar sync** | Uploaded avatars are broadcast to other AtomChat clients on the server (changing yours refreshes theirs immediately) |
| **Server-offered content** | This server's emote pack, quick phrases, server icon and MOTD are handed to joining clients, which show a read-only "this server" section in the panel |

The three share two master switches, `hostingEnabled` and `packEnabled`, and can be turned on or off independently.

## Installing and configuring

Put the same jar into the server's `mods/` and restart. The first start generates **`config/atomchat/atomchat-server.json`** (on a single-player or LAN host, the same file under that world's instance directory).

### Editing settings in game: `/atomchat gui`

OP level 2 (**single-player needs no permission**; running it from the console tells you to do it in game, because a console has no screen to draw on). The screen is drawn by the **client of the player who ran the command**, so it works on a dedicated server, a hosted panel, single-player and LAN alike.

- It is split into "server hosting" and "server-offered content" sections, with units written into the labels (KB / MB / milliseconds / "0 = forever"), and an out-of-range value is caught by **naming the offending field**;
- after a successful save, **the person who changed it sees their own panel refresh immediately** (the client re-runs the handshake);
- other online players pick the change up **on their next join**;
- two admins editing at once do not overwrite each other: the server rejects stale edits by `configVersion`.

> A fresh client config is seeded with one quick phrase, `/atomchat gui`, so an owner who never reads the docs still finds this entry point (seeded once; deleting it sticks).

## The full server config

`config/atomchat/atomchat-server.json`:

| Key | Default | Notes |
|---|---|---|
| `hostingEnabled` | `true` | **Master switch:** whether this server hosts chat images / GIFs and player avatars. Off means clients fall back to the third-party image host and to skins |
| `maxFileKb` | `2048` | Per-file size cap for hosted media, in KB; larger files fall back to the image host |
| `maxTotalMb` | `512` | Media store capacity in MB; past it the oldest entries go first |
| `maxAvatarTotalMb` | `64` | Avatar store capacity in MB; oldest first as well |
| `retentionDays` | `7` | Days hosted media and avatars are kept; `0` means forever |
| `uploadCooldownMs` | `3000` | Minimum gap between two uploads from the same player, in milliseconds |
| `packEnabled` | `true` | **Master switch:** whether this server's emotes / phrases / identity are sent to clients |
| `packMaxFiles` | `32` | Cap on offered emote files |
| `packMaxMb` | `8` | Cap on the total size offered, in MB |
| `packName` | `""` | The name clients show; empty means the first line of the cleaned-up server MOTD |
| `phrases` | `[]` | Quick phrases offered to clients (up to 20, each ≤200 characters) |

Ranges are re-validated by the server when it saves (the emote pack cap, for instance, must be 1–64 MB).

## Preparing what you offer

| What | Where | Notes |
|---|---|---|
| Emote pack | the server's `atomchat/server/emotes/` | `png` / `jpg` / `jpeg` / `gif`; **deliberately separate** from a player's own `emotes/` so single-player and LAN hosts never push the owner's collection to guests; on a dedicated server the first pack build copies over (copy only, never delete) whatever the old `emotes/` folder held |
| Quick phrases | `phrases` in the config | ≤20 entries, each ≤200 characters |
| Server icon | `server-icon.png` in the server root | Read automatically |
| Server name | `packName`, or the first MOTD line when empty | Cleaned up before it is shown |

Transfer details: the client first works out which files it already has (per-file SHA-256), the server **sends only the difference**, in 24 KiB chunks, at most 4 chunks per player per tick, and the client verifies every file before swapping the whole directory in atomically. **Every failure writes a reason into the server log.** The client's hard ceiling is 200 files / 16 MB.

## Where data lives, and how it is cleaned

| Path (under the server's game directory) | Contents |
|---|---|
| `config/atomchat/atomchat-server.json` | The server config |
| `atomchat/server/emotes/` | The emote source handed to clients |
| `atomchat/server/media/` | Hosted chat media; **the file name is its content hash**, so duplicates collapse |
| `atomchat/server/avatars/` | Uploaded player avatars |

Cleaning runs **at server start, every five minutes, and after every upload**, trimming the oldest entries to `retentionDays` and the size caps; a player disconnecting drops their unfinished upload buffers. To reclaim space right away, deleting files under `media/` or `avatars/` is safe — clients re-upload on demand.

## Troubleshooting

| Symptom | Check first |
|---|---|
| Players' images still go to the image host | Is AtomChat actually loaded on the server (does `/atomchat gui` open)? Is `hostingEnabled` true? Does the log contain `Stored hosted media`? |
| The image reached the server but others cannot see it | Hosted media is cleaned after 7 days by default; check `retentionDays` and `maxTotalMb` |
| A player says there is no "this server" section | The server's `packEnabled`; the client's `Settings → Privacy & Blocking → Accept server-offered content`; and whether they joined **before** the change (have them rejoin) |
| The server log shows a transfer failure | Read the reason on that line: over `packMaxFiles` / `packMaxMb`, a single file too large, the 15-second manifest watchdog or the 30-second chunk watchdog |
| Turn hosting off entirely | `hostingEnabled=false` (clients fall back to the image host and to skins), or simply do not install the server-side jar |
