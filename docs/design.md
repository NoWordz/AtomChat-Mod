# AtomChat Design

> 当前实现进度、版本、部署与待办见 [STATUS.md](STATUS.md)；本文件记录设计意图，个别数值（如面板默认尺寸）会随迭代变化。

## Goal

Build a high-quality, highly customizable phone-app-style chat UI for Minecraft, distinct from E33Chat. The vanilla chat box remains visible normally; pressing the chat key opens AtomChat and hides the vanilla chat HUD.

## Rendering engine

- **Skija (HumbleUI Skia bindings, 0.116.8)** — same engine used by the Tuui mod.
- Draw directly onto Minecraft's main framebuffer via `BackendRenderTarget.makeGL` + `Surface.wrapBackendRenderTarget`.
- Save/restore GL state around Skia drawing (`GlStateUtil`).
- Bundled native libraries (`skija-windows-x64`) are auto-extracted by Skija at runtime.

## Architecture (learned from Tuui)

| Concept | AtomChat class |
| --- | --- |
| Skia screen bridge | `SkiaGraphics`, `AtomChatScreen` |
| GL state guard | `GlStateUtil` |
| Components | `UiComponent`, `Box`, `Text`, `AbstractCanvas` |
| Fonts | `FontManager`, `SkiaFontRenderer` |
| Drawing helpers | `SkiaDraw` |
| Animation | `Animator`, `Easing` |
| Config | `AtomChatConfig` |

## UI layout

- Centered phone panel (default 420×780), rounded corners, dark translucent background with Skia ImageFilter blur.
- Header: world channel name + time.
- Scrollable message list with bubbles/avatars.
- Input bar: image / emoji buttons, two-line text area, send button.

## Interaction design

- Click avatar → insert `@名字 `.
- Double-click avatar → poke shake (600ms damped sine).
- Right-click message → copy / quote context menu.
- Quote target is shown above the input bar.

## MVP scope

1. Skija render bridge + phone panel.
2. Chat key interception + vanilla chat HUD hiding.
3. Message capture/send, bubbles, avatars, names, time.
4. @, poke, copy/quote, emoji panel.
5. JSON config.
