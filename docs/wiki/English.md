> English documentation for AtomChat, which turns the vanilla chat screen into a phone-app-style panel. Fabric / NeoForge 1.21.1 and Forge 1.20.1.

**Current version: v0.2.10 ・ [Download](https://github.com/E33EPUS/AtomChat/releases) ・ [Issues](https://github.com/E33EPUS/AtomChat/issues) ・ [中文文档](Home)**

## What do you want to do?

| I want to… | Read this |
|---|---|
| Install it and open the panel for the first time | [Getting Started](Getting-Started) |
| Send images / debug an image that will not show / find out where they are stored | [Media and Transfers](Media-and-Transfers) |
| Manage emotes, or hand an emote pack to everyone on my server | [Emotes](Emotes) |
| Change avatars, wallpaper, theme colours, or look up a config key | [Appearance and Config](Appearance-and-Config) |
| Run a server, host images, offer content, change server settings | [Server Owner Guide](Server-Owner-Guide) |
| The panel will not open, messages turned grey, something threw | [Troubleshooting](Troubleshooting) |
| Build from source, run tests, understand the code layout | [Developer and Build](Developer-and-Build) |

## Three things worth knowing first

1. **Client-side mod** — it works without anything on the server (images then go through a third-party image host). Install it on the server too and you unlock **server-hosted media** and **custom avatar sync**.
2. **Separate Windows x64 / Windows ARM64 / Linux x64 JARs** — install one matching your OS and Minecraft JVM architecture. Filenames end with the OS and architecture. Linux requires EGL/GL libraries. Windows x64 Fabric rendering with Skija 0.143.18 passed user validation; Windows ARM64 and upgraded Linux GPU rendering still need in-game checks. macOS, Linux ARM64 and Android natives are not bundled.
3. **The whole interface is drawn as vectors by Skija**, with no vanilla chat textures involved. Image messages use the E33Chat `[[CICode]]` protocol, so they interoperate with E33Chat and the ChatImage family.

## Key bindings

| Key | What it does |
|---|---|
| `T` / `/` (the vanilla chat keys) | Opens AtomChat, taking over the vanilla chat screen |
| `Y` (default; change it under Options → Controls → Key Binds → the `AtomChat` category) | Opens the panel directly, restoring the page you were on last |

## Other languages

The Chinese wiki starts at [首页](Home). The Chinese README is [README.md](https://github.com/E33EPUS/AtomChat/blob/main/README.md); the repository also carries [README_EN.md](https://github.com/E33EPUS/AtomChat/blob/main/README_EN.md) for the short version.
