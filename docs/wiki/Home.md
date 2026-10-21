> 原子聊天（AtomChat）的中文文档。它把原版聊天屏改造成「手机 App」风格的独立聊天面板，支持 Fabric / NeoForge 1.21.1 与 Forge 1.20.1。

**当前版本：v0.2.10 ・ [下载](https://github.com/E33EPUS/AtomChat/releases) ・ [问题反馈](https://github.com/E33EPUS/AtomChat/issues)**

## 你想做什么？

| 我想…… | 看这一页 |
|---|---|
| 装上它，第一次打开面板 | [快速上手](快速上手) |
| 发图片 / 收到图片不显示 / 搞清图到底存在哪 | [图片消息与传输路线](图片消息与传输路线) |
| 管理表情包，或让服务器给玩家下发表情 | [表情包](表情包) |
| 换头像、换壁纸、调主题配色、查配置项 | [外观与配置](外观与配置) |
| 开服 / 托管图片 / 下发内容 / 改服务端配置 | [服主手册](服主手册) |
| 面板打不开、消息变灰、报错 | [常见问题排查](常见问题排查) |
| 编译源码、跑测试、了解代码结构 | [开发者与构建](开发者与构建) |

## 三条先知道

1. **纯客户端模组** —— 服务端不装也能用（图片走第三方图床）；服务端也装一份，才会解锁**服务端媒体托管**与**自定义头像同步**。
2. **Windows x64 / Windows ARM64 / Linux x64 分包** —— 按系统和 Minecraft JVM 架构选择一个 JAR；文件名统一以系统与架构结尾。Linux 需要 EGL/GL 库。Skija 0.143.18 的 Windows x64 Fabric 渲染已获用户验收；Windows ARM64 与新版 Linux GPU 仍待实机验证。macOS、Linux ARM64 与 Android 暂未打包。
3. **界面全部由 Skija 矢量绘制**，不依赖原版聊天纹理；图片消息沿用 E33Chat 的 `[[CICode]]` 协议，可与 E33Chat / ChatImage 系模组互通。

## 快捷键

| 按键 | 作用 |
|---|---|
| `T` / `/`（原版聊天键） | 打开 AtomChat（接管原版聊天界面） |
| `Y`（默认，可在 选项 → 控制 → 按键绑定 → `AtomChat` 分类里改） | 直接打开面板，恢复上次所在的页面 |

## 其他语言

**英文 Wiki：[English](English)** —— 与这里逐页对应（Getting Started / Media and Transfers / Emotes / Appearance and Config / Server Owner Guide / Troubleshooting / Developer and Build）。仓库里的英文简介是 [README_EN.md](https://github.com/E33EPUS/AtomChat/blob/main/README_EN.md)。
