**Sending images, receiving images, and the one thing people mix up most: where the file actually lives.**

## How to send

Three ways, all equivalent:

1. **Click the image button** — opens a file picker with thumbnail previews (starting in your system pictures folder);
2. **Drag an image file into the game window** — just drop it;
3. **Copy an image and press `Ctrl+V`**.

All three **upload first**: while that runs the input placeholder says "uploading image…", and on success a piece of image code (`[[CICode,...]]`) is inserted into your draft — you can still edit the text around it before sending. Nothing leaves until you press enter.

## Which route an image takes

This is the most common question, and the rule is a single line: **if the server runs AtomChat it goes through the server, otherwise through a third-party image host.**

| Route | When it is used | What happens |
|---|---|---|
| **Server-hosted** | The server runs AtomChat and `hostingEnabled=true` | The image is stored in the server's `atomchat/server/media/` (the file name is its content hash, so the same image is stored once), the message carries an `atomchat-media:<id>` short link, and other players fetch it from the server on demand — over the game connection only, **no HTTP port is opened** |
| **Third-party host** | The server does not run AtomChat / `hostingEnabled` is off / the file is over `maxFileKb` (2 MB by default) / the upload failed | The image goes to [uguu.se](https://uguu.se) and the message carries an ordinary https link — **which expires after roughly three hours** |

Falling back is designed behaviour, not a failure: without server hosting you can still send images, they just expire.

### Checking which route was used

Look for this line in the client log (`.minecraft/logs/latest.log`):

```
Stored hosted media <id> (<bytes> bytes)
```

**Present** = the server stored it. **Absent** = it went to the image host (a successful external upload writes no info line; only a failure logs `Failed to upload image`).

### Retention on the server side

Hosted media is kept for **7 days** by default (`retentionDays`; `0` means forever), and is trimmed to the size caps at server start, every five minutes, and after every upload: 512 MB for the media store, 64 MB for the avatar store. See the [Server Owner Guide](Server-Owner-Guide) for the keys.

## Images you receive

- A message containing image code renders as an image bubble at the original aspect ratio; **GIFs loop inside the bubble**, and transparent PNGs / GIFs show the panel behind them.
- **Right-click an image → Save** writes the original to disk (the save dialog lets you pick a folder and file name).
- Do not want them? Turn off `Settings → Chat → Image messages`. With it off the client **does not download or cache** anything and every image message shows a green `[图片]` placeholder (clicking Save still downloads on demand).

## Where the cache lives, and how to clear it

| Contents | Path |
|---|---|
| Downloaded / uploaded image cache | `<game dir>/atomchat/cache/images/` |
| Server-offered emotes, other players' avatars, … | `<game dir>/atomchat/cache/packs/`, `atomchat/server/avatars/` |
| Server-side hosted media | `<server game dir>/atomchat/server/media/`, `avatars/` |

The client cache has three automatic gates: **at most 500 files, at most 100 MB, and anything unused for 7 days is deleted** (reading a file refreshes its timestamp). To clear it now: `Settings → About → Clear image cache` — it clears only the image cache and leaves emotes, wallpaper and avatars alone.

## Troubleshooting

| Symptom | Look here first |
|---|---|
| An image stays on "loading" forever | Is the server running? Is the image server-hosted (an `atomchat-media:` short link) and already swept by the 7-day retention? |
| An old image is blank or broken | Image-host links expire after about three hours. Ask the owner to enable server hosting, or ask for a re-send |
| Clicking the image button does nothing / no window appears | See "the file picker will not open" in [Troubleshooting](Troubleshooting) |
| The image will not send and the log says `Failed to upload image` | The image host is unreachable (network / proxy). Files above `maxFileKb` are refused by the server and go straight to the host |
