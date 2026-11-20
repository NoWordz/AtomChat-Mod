**Install it, then actually use the panel once.** About three minutes end to end.

## 1. Pick the right jar

Download [AtomChat](https://github.com/NoWordz/AtomChat-Mod/releases/latest) and [AtomLib](https://github.com/NoWordz/AtomLib-Mod/releases/latest) for the same Minecraft version and loader.

| Your game | Download this file | You also need |
|---|---|---|
| Minecraft 1.21.1 + Fabric | `atomchat-Fabric-1.21.1-x.y.z.jar` | Fabric Loader 0.16.0+ with Fabric API, Java 21+ |
| Minecraft 1.21.1 + NeoForge | `atomchat-NeoForge-1.21.1-x.y.z.jar` | NeoForge 21.1+, Java 21+ |
| Minecraft 1.20.1 + Forge | `atomchat-Forge-1.20.1-x.y.z.jar` | Forge 47+, Java 17 |

Choose the installation JARs for your loader. The installation requirements for the current development version are in the [README](../../README.md).

## 2. Drop it into mods

Put both JARs in your client's `mods/` folder. For a modpack, use that instance's own folder.

To enable optional server media hosting, profile sync or shared stickers and quick phrases, put AtomChat into the server's `mods/` folder. Dedicated servers only need AtomChat. [Server Owner Guide](Server-Owner-Guide.md)

## 3. Open the panel

Once you are in a world:

- press the **chat key** (default `T` or `/`) — AtomChat takes over the vanilla chat screen and puts you straight into public chat input;
- or press **`Y`** (the default; change it under `Options → Controls → Key Binds → AtomChat`) — this opens the panel directly and restores the page you were on last.

The panel looks like this: the public channel and the system clock on top, the message list in the middle, the input bar at the bottom, and three tabs below that — **Chat / Profile / Settings**.

## 4. Try these five things first

1. **Send a message:** type, hit enter. The input grows once your text passes one line, and the up/down arrows still move the caret between lines.
2. **Send an image:** click the image button left of the input; or drag an image file straight into the game window; or copy an image and press `Ctrl+V`. All three upload first, then insert the image code into your draft, and you send it with enter once it looks right.
3. **Use an emote:** click the emote button. The panel has three tabs — `Emoji` / `Kaomoji` / `Stickers`. A click inserts; stickers upload first and insert into the draft, then the panel closes.
4. **Right-click a message:** copy it, or reply with a quote (the quote carries the original message's bubble). Image messages also get a "Save" entry that writes the original image to disk.
5. **Click an avatar:** a single click opens the player's profile page; a second click within 300 ms is a QQ-style "nudge"; right-clicking the avatar opens a menu — mention, private chat, teleport, block.

## 5. Make it look like yours

Open **Settings** at the bottom of the panel.

- Choose a theme, bubble shape or wallpaper in **Appearance & Interaction → Presets**. The custom wallpaper entry follows the wallpaper presets.
- Adjust panel and card blur and opacity under **Surface**. Open **Layout** for panel size, the shortcut icon and the notification banner editor.
- Under **Chat & Notifications → Messages → Chat HUD**, choose the message limit, timed dismissal or vanilla chat.

## Next

- Where images actually live, and why some of them go through an image host → [Media and Transfers](Media-and-Transfers.md)
- How to add or remove emotes, and where server-offered ones come from → [Emotes](Emotes.md)
- The panel will not open after installing / messages turned grey / a crash → [Troubleshooting](Troubleshooting.md)
