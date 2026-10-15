**Start from the symptom, then narrow it down with the log.** Everything here revolves around one file: `.minecraft/logs/latest.log` (on a server, `logs/latest.log`).

## The panel will not open / pressing T does nothing

1. **Is the jar the right one?** The Fabric build goes with Fabric Loader, the NeoForge build with NeoForge, the Forge build with 1.20.1 + Forge 47+; a jar for another loader will not load at all. Check the Java version too (21+ for Fabric / NeoForge, 17 for Forge 1.20.1).
2. **Is another screen in the way?** The `Y` key only brings the panel up when no other screen is open.
3. **Read the log:** search for `atomchat` and look for a load failure or a missing dependency (`fabric-api` is required on Fabric).
4. **Key conflict:** under `Options → Controls → Key Binds → AtomChat`, check that `Open AtomChat` (default `Y`) has not been taken by another mod; the vanilla chat keys (`T` / `/`) can also stop working when another mod rewrites them.

## The file picker will not open (image / avatar / wallpaper buttons do nothing)

This is a compatibility problem fixed in 0.2.9: some mods (in our testing, **TerraFirmaCraft / TFC**) initialise AWT very early, while Minecraft's client main class `Main` writes `java.awt.headless` to `true` — whoever touches AWT first locks that answer in. The result was AtomChat throwing `HeadlessException` when opening a file picker, with the log swallowing it, so the player saw a button that did nothing.

**Since 0.2.9** a mixin plugin claims AWT during the mixin preparation phase (before any mod is constructed and before `Main` runs). If it still cannot:

- the log gets an `ERROR` line naming the reason;
- the game shows a notice (`atomchat.picker.headless`).

Search the log for `HeadlessException`, `java.awt.headless` or `Image picker`. On 0.2.8 or earlier, **upgrading to 0.2.9 is the fix**.

## Messages render grey

Grey means the client cannot tell whether that line is a player message, so it plays safe and renders it as a system line (that is the **design**, not a mis-detection). It usually happens with nickname / title plugins that use a message format the client does not know.

The remedy is to describe the format by hand in `config/atomchat/atomchat-client.json` — `chatTemplates` for public chat and `whisperTemplates` for private chat, with the placeholders `{name}` `{display_name}` `{prefix}` `{suffix}` `{sep}` `{content}` (`{content}` must appear exactly once). **Reopen the chat screen** after editing; no game restart needed.

## Images

An image that will not show, an old image gone broken, a failed upload → see the troubleshooting table in [Media and Transfers](Media-and-Transfers).

## The panel goes fully black (old versions only)

Before 0.2.8, opening the panel on Forge 1.20.1 could turn the whole screen black (the game did not crash and typing still worked). The cause was the blur pre-pass writing raw GL state that disagreed with 1.20.1's cache-gated setters; **fixed in 0.2.8**. Since then, a failed blur pass falls back to an opaque solid panel and logs a throttled warning carrying the GL error code instead of going quietly black.

## Known unsupported

- **Linux x64:** use the `linux-x64` JAR and remove the Windows JAR. Ubuntu/Debian needs `libegl1 libgl1`; inspect the native-loading reason in the log if the panel stays disabled.
- **macOS / ARM64:** matching native libraries are not bundled, so the panel cannot start.
- **Quark:** the emote menu does not appear when Quark is installed; compatibility work is in progress.
- **Other MC versions / loaders:** currently Fabric and NeoForge 1.21.1, plus Forge 1.20.1.

## Emoji / kaomoji render as empty boxes on Linux (missing fonts)

On Linux the panel opens and text is fine, but the emoji panel's emoji and part of the kaomoji come out as a row of empty boxes. That is not a broken mod — **the system has no font that can draw those characters.**

The bundled `bundled.otf` is a **Noto Sans SC subset** (about 7,500 glyphs): it covers common Han and kana, but **no emoji, no halfwidth katakana, no Thai, no Hangul and only part of the symbol ranges**. Those gaps are filled from **system fonts**: Windows ships Segoe UI Emoji and YaHei, so it looks correct; a slim Linux (including the default WSL image) often has only DejaVu and Ubuntu, which do not cover them. Measured on such a system: 88 of the emoji panel's 122 emoji codepoints were boxes, and about 10 kaomoji symbols (`｡ ﾉ ￢ ゛ ゜ ー ㅂ ︵ ฅ ๑`) had no font at all.

Install three fonts (Ubuntu / Debian):

```
sudo apt install fonts-noto-cjk fonts-noto-color-emoji fonts-tlwg-garuda-ttf
fc-cache -f
```

- `fonts-noto-cjk` covers Han/kana — measured, kaomoji boxes drop from 10 to 2;
- `fonts-noto-color-emoji` covers color emoji — measured, emoji boxes drop from 88 to 0;
- `fonts-tlwg-garuda-ttf` covers Thai, including the remaining `ฅ` and `๑` characters. In this WSL offscreen check, missing codepoints across the 29 built-in kaomoji dropped to zero; the user also confirmed the missing glyphs were resolved in game.

All three are standard Ubuntu archive packages. The mod's glyph fallback picks them up automatically; without them the characters stay as boxes. **Exit the game completely and start it again** afterwards (returning to the main menu is not enough) so the new fonts are picked up.

(WSL users: this is not a WSL fault — any slim Linux may lack these fonts.)

## Very low frame rate under WSL / WSLg (OpenGL falling back to software rendering)

When the game runs at a very low frame rate inside WSL (WSLg), confirm the renderer in the log first: search for `OpenGL renderer`, or look at the `gpu` line of the `AtomChat environment summary` block.

- `llvmpipe` means **CPU software rendering**, a performance bottleneck to address first. This alone does not establish whether AtomChat adds any further overhead;
- a renderer such as `D3D12 (Intel(R) UHD Graphics)` or `D3D12 (NVIDIA GeForce ...)` indicates the corresponding hardware backend. Frame rate still needs to be measured.

To move WSLg off llvmpipe, set the environment variable `GALLIUM_DRIVER=d3d12` (Mesa's D3D12 backend) on **that instance** in the launcher. On a machine with several GPUs, pick the adapter with `MESA_D3D12_DEFAULT_ADAPTER_NAME` (for example `NVIDIA`, or the adapter's actual name). In Prism Launcher this goes under "Instance settings → Environment variables", one name and value per line — which avoids hand-editing `instance.cfg`:

- [Prism Launcher environment variables](https://prismlauncher.org/wiki/help-pages/environment-variables/)
- [WSLg GPU selection guide](https://github.com/microsoft/wslg/wiki/GPU-selection-in-WSLg)
- [Upstream WSLg report on llvmpipe / D3D12 selection](https://github.com/microsoft/wslg/issues/1332)

Note these variables only make sense for a WSL instance; do not set them on a normal Linux desktop. Whether they take effect is decided by **the renderer the game actually reports** — if it still says `llvmpipe`, it did not switch.

Mouse capture failures and delayed click sounds need separate verification; the renderer name alone does not establish their cause. Correct GPU selection first and test again, then compare with AtomChat disabled if either symptom persists. [WSLg has an upstream mouse capture report](https://github.com/microsoft/wslg/issues/240), but that report does not prove the same cause applies to this machine.


## Turning on debug, and which lines to send

**One line you always get** (no switch involved). Search for `AtomChat build`:

```
AtomChat build 0.2.11 | Forge 47.4.10 / minecraft 1.20.1 | atomchat-Forge-1.20.1-0.2.11.jar | sha256:de20503c1a2b | git 56122d9
```

Mod version, loader and its version, MC version, **the jar file that was actually loaded**, **the hash of that file's contents**, and the commit it was built from.
The last two are the point: players rename jars all the time, so when the file name disagrees with everything else, the version plus the content hash still pin down exactly which build it is.

**A full environment block** (`debug` needed). In game, `Settings → About`; or set `"debug": true` in `config/atomchat/atomchat-client.json` and restart. Search for `AtomChat environment summary`:

```
AtomChat environment summary (debug) — paste this whole block into a bug report
  build    0.2.11 | Forge 47.4.10 / minecraft 1.20.1 | git 56122d9
  artifact ...\mods\atomchat-Forge-1.20.1-0.2.11.jar | ... | loader reports 0.2.11
  runtime  Java 17.0.9 (Eclipse Adoptium) | Windows 11 10.0 | amd64
  laf      com.atom.chat.shaded.flatlaf.FlatLightLaf <- ...\mods\atomchat-....jar | ...
  skija    0.116.8 windows/x64 <- ...\skija-windows-x64-0.116.8.jar | natives loaded in 132 ms
  gpu      NVIDIA GeForce RTX 3060 | 4.6.0 NVIDIA 552.22 | NVIDIA Corporation
  switches blur=on (opacity 0.93) | wallpaper=none | imageMessages=on | animations=on | panel=440x780 @1.0x
```

It answers the things that get asked most and are hardest to work out from a log: **which jar the FlatLaf in use came from** (that is where the old registry collision happened), **whether the Skija native library actually loaded**, **the GPU and driver**, and the switches as they stood. The whole block is **one** log record with embedded line breaks, so copy it as a unit — other mods' lines cannot land in the middle of it.

With `debug` on, input is logged line by line too: which key went down, which character was typed, and which point on screen was clicked together with the panel coordinates that point maps to. The UI is drawn by Skia, so none of the usual vanilla-widget debugging applies, and "this button does nothing" is decided entirely by those lines — did the click arrive, and what was under it. Search for `click:`, `keyPressed:`, `charTyped:`.

## What to include in a bug report

1. the `AtomChat build` line above (or the whole environment block) — it carries the mod version, loader and MC version in one go, which beats typing them out;
2. whether this is **single-player / a LAN host / a dedicated server**, and whether the server also runs AtomChat;
3. reproduction steps — shorter is better: what you did → what you expected → what happened;
4. `.minecraft/logs/latest.log` (or a report from `crash-reports/`);
5. a screenshot or clip of the moment it went wrong (very useful for interface problems);
6. for anything image- or pack-related, paste the `Stored hosted media`, `Failed to upload image` and transfer-failure lines from the log.

Open an issue at [Issues](https://github.com/E33EPUS/AtomChat/issues).
