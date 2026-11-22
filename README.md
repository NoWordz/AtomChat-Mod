<p align="left"><strong>English</strong> · <a href="README.zh-CN.md">简体中文</a></p>

<p align="center"><img src="https://github.com/user-attachments/assets/354cc7eb-4c1c-49f0-9759-bbe18bf11a14" width="320" alt="AtomChat logo"></p>

<h1 align="center">AtomChat</h1>

<p align="center"><em>A phone-app style chat experience for Minecraft, powered by Skija.</em></p>

<p align="center">
  <a href="https://github.com/NoWordz/AtomChat-Mod/releases/latest"><strong>Download</strong></a> ·
  <a href="docs/wiki/Getting-Started.md">Getting started</a> ·
  <a href="docs/wiki/English.md">Documentation</a> ·
  <a href="RELEASE_NOTES.md">Release notes</a>
</p>

## Install

Install AtomChat and the matching [AtomLib](https://github.com/NoWordz/AtomLib-Mod/releases/latest) JAR in your client's `mods/` folder. Match both JARs to your Minecraft version and loader.

| Minecraft | Loader | Java |
| --- | --- | --- |
| 1.21.1 | Fabric Loader 0.16+ and Fabric API | 21 |
| 1.21.1 | NeoForge 21.1+ | 21 |
| 1.20.1 | Forge 47+ | 17 |

Press `T` to start typing or `Y` to open the panel. You can change the shortcut in Minecraft's key bindings.

Install AtomChat on the server to add media hosting, profile sync, shared stickers and quick phrases. Dedicated servers only need AtomChat. [Server owner guide](docs/wiki/Server-Owner-Guide.md)

## Features

- **Chat panel.** Message bubbles, player avatars, private conversations, quotes, mentions and notification banners.
- **Images and reactions.** Paste or drag in a picture, play GIFs, use stickers and add emoji reactions when the server also runs AtomChat.
- **Chat HUD.** See recent messages with avatars and image previews while playing. Keep up to ten messages, let them expire, or switch back to vanilla chat.
- **Appearance.** Mix theme, bubble and wallpaper presets. Use your own wallpaper, adjust opacity and blur, and edit individual colors.
- **Layout and motion.** Resize or move the panel, position notification banners, and adjust interface transitions and feedback.

## Compatibility

Pair the published AtomChat 0.9.2 package with AtomLib 0.6.0. Its bundled settings profile belongs to that library release. The AtomChat 0.9.3 development builds target AtomLib 0.7.0 on Windows. [AtomLib desktop support](https://github.com/NoWordz/AtomLib-Mod/releases/tag/v0.7.0)

Image messages use the `[[CICode]]` protocol used by E33Chat and ChatImage. Chat-screen replacements or unusual server chat formats may require extra configuration. [Troubleshooting](docs/wiki/Troubleshooting.md)

## Build from source

From the repository root, fetch the pinned AtomLib Maven input, then build the Fabric target with Java 21. Choose `platforms/1.21.1-neoforge` for NeoForge or `platforms/1.20.1-forge` with Java 17 for Forge.

```sh
python tools/fetch_atomlib_input.py --output build/atomlib-input-path
ATOMLIB_MAVEN_REPO="$(cat build/atomlib-input-path)"
cd platforms/1.21.1-fabric
./gradlew test build -PatomlibMavenRepo="$ATOMLIB_MAVEN_REPO" --no-daemon \
  -x frontendContractSourceCheck -x frontendContractCheck
```

This produces a local test build in `build/atomlib-consumer/libs/` under the selected target. On Windows, use `gradlew.bat` and pass the path written to `build/atomlib-input-path`. The distribution build also runs the frontend contract checks. [Project status](docs/STATUS.md) · [Changelog](CHANGELOG.md) · [Issues](https://github.com/NoWordz/AtomChat-Mod/issues)

## License

AtomChat is [MIT-licensed](LICENSE). [Third-party notices](THIRD_PARTY_NOTICES.md)
