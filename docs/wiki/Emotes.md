**Two sources, three tabs, one read-only dividing line.**

## What the panel looks like

Click the emote button above the input bar. The panel has three tabs (with a sliding indicator and a push animation):

| Tab | Contents |
|---|---|
| `Emoji` | built-in emoji |
| `Kaomoji` | built-in kaomoji |
| `Stickers` | **your own emotes** (a local folder) plus a read-only **"this server" section** for what the server offers |

A click inserts. For a sticker it uploads first, inserts into the draft, and closes the panel — one at a time.

## Your own emote folder

- **Folder:** `.minecraft/config/atomchat/emotes/`
- **Formats:** `png` / `jpg` / `jpeg` / `gif`
- **Count:** unlimited by default; to set a cap, put a positive integer in `emoteMax` in `atomchat-client.json` — it takes effect when the chat screen reopens (past the cap the grid scrolls; since 0.2.9 it is no longer pinned to two rows)
- **Order:** by file name — rename a file to move it
- **Adding:** the `+` tile at the end of the grid, then pick a local image
- **Removing:** hover a thumbnail and click the `×` in its top-right corner

> A GIF emote shows **only its first frame inside the grid** (that keeps scrolling and redraws smooth); once sent, it loops normally in the bubble.

## Server-offered emotes (the "this server" section)

On joining, a server running AtomChat hands its emote pack to the client. Those land in a **separate read-only section** at the bottom of the stickers tab, with the server icon, its name and a sync status:

- **A click uses them** just like a local emote;
- **they cannot be deleted and are counted separately from your own** (the client shows at most 32 of them);
- the transfer is **per-file SHA-256 verified and incremental**: delete one offered emote and your next join fetches only that one file, never the whole pack again;
- do not want them? Turn off `Settings → Privacy & Blocking → Accept server-offered content`. With it off the client stops requesting anything from the server and clears what it has already loaded (it is on by default).

## For server owners: handing emotes to everyone

1. Put the emote files you want to offer in the **server's** `config/atomchat/server-emotes/`;
2. make sure `packEnabled=true` in the server config (it is the default);
3. players sync them **on their next join** (no server restart needed).

That folder is **deliberately** separate from a player's own `config/atomchat/emotes/` — otherwise the host of a single-player or LAN world would push their private collection to every guest. On a dedicated server, the first pack build **copies over** (copy only, never delete, so a rollback stays safe) whatever the old `emotes/` folder held; single-player and LAN hosts **do not** migrate and no longer offer anything by default.

The relevant limits (all in `config/atomchat/atomchat-server.json`, also editable in game with `/atomchat gui`):

| Key | Default | Meaning |
|---|---|---|
| `packEnabled` | `true` | Whether this server's emotes / phrases / identity are sent to clients |
| `packMaxFiles` | `32` | Cap on offered emote files |
| `packMaxMb` | `8` | Cap on the total size offered, in MB |
| `packName` | `""` | The name clients show; empty means the first line of the cleaned-up server MOTD |

The client has a hard gate of its own: a pack above **200 files / 16 MB** is refused outright, so a hostile server cannot flood a client. The full picture is in the [Server Owner Guide](Server-Owner-Guide).

## Troubleshooting

| Symptom | Cause / what to do |
|---|---|
| A file is in the folder but not in the panel | Is the format one of `png / jpg / jpeg / gif`? Did you set an `emoteMax` cap in `atomchat-client.json` (the grid scrolls — try scrolling down)? |
| The tiles are in the wrong order | They sort by file name; rename the file |
| The owner added emotes but players do not see them | Is `packEnabled` true on the server? Has the player turned off "Accept server-offered content"? Players sync **on join**, so have them rejoin |
| The transfer failed | The server log names the reason (over `packMaxFiles` / `packMaxMb`, a single file too large, a manifest timeout, …) |
