# 2026-10-19 设置整理与聊天输入验证

状态：**delivered**。本轮最终实现随 `37589c7` 提交并推送，Windows 日常 NeoForge 与 WSL Prism Fabric 已部署；未启动游戏，真实 GPU、输入焦点、关闭动画和真实进退服仍待实机验收。

基线为 `main@f7b5377f1fd0b215569f9d1452a8fe0707c56b17`，版本保持 0.3.0。

## 完成范围

- **预设干净重写**：删除旧 `ThemePresetStore` 和命名/覆盖/重命名界面，新增 `AppearancePresetStore`。只提供保存当前、列举、应用、删除；目录改为 `atomchat/appearance-presets`，旧未发布目录不迁移、不删除。显式保存十二色、透明度、尺寸、UI/内容缩放、卡片浓度、圆角、模糊、外框、动画、切页方式和独立壁纸副本。使用稳定 UUID 与当前最小空闲正整数，删除不重排。应用前完整校验，配置写入失败回滚外观和壁纸。
- **预设条交互**：内置主题后接自动编号预设，末尾固定＋；悬停整张自定义卡时右上角显示×，点击删除，删除优先于应用，内置卡不可删；编号可复用，显示顺序始终追加到末尾。保留横向拖拽/滚轮和窄窗口滚动。
- **发送与草稿**：新增 `closeAfterSend=false`、`retainDraftOnClose=true`。公聊、私聊、命令两条成功发送链的最后统一调用关闭回调；空白、被拦截私聊、仅插入表情不关闭。草稿改由客户端会话级 `DraftSessionStore` 持有，JOIN/DISCONNECT 清零；公聊与每个私聊分开，非空初始输入优先，发送成功清空，关闭开关不破坏当前输入及框内切页草稿。
- **容器圆角**：发送按钮底色、hover、press 层改为实时 `UiTokens.radius(9)`；按负责人清单修复发送/图标按钮、表情/短语面板、颜色选择、图片预览/裁剪、通知按钮、未读 badge、历史/引用胶囊、滚动条/FAB、开关轨道、资料页横幅操作胶囊和头像编辑徽章、设置输入/按钮/色彩预览等容器。聊天气泡、头像、开关圆点、色彩圆点、表情图案和裁剪 mask 保持原语义。
- **设置整卡**：全部设置 tab 改为确认过的折叠组，默认展开；每组一张卡、内部细线分隔，组识别、measure、rowAdvance、rowRect、绘制与命中共用同一 membership。英文四 chip 改为 `Messages / History / Notify / Advanced` 并给 chip 文本增加宽度截断。

## 验证结果

### 红绿测试与审查修正

- 旧 `ThemePresetStoreTest` 删除，新 `AppearancePresetStoreTest` 先因类缺失红，复审修正后 10 项通过。
- `DraftStoreTest` 先因新 API 缺失红，复审修正后 13 项通过。
- `SendPipelineTest` 先因关闭回调和统一完成链缺失红，后 6 项通过。
- `DrawPrimitivesTest` 先因固定 `s(9)` 在圆角 0 仍切角红，修复后 9 项通过；真实 Skija 像素检查 0/默认圆角、hover、press 和 save/restore。
- `SettingsPresetStripTest` 覆盖预设条应用、× 删除优先、末尾＋、保存滚动、全部 tab 分组标题和合卡色点悬停；3 项通过。
- 独立强模型只读审查发现 7 项 P2、1 项 P3；负责人修正后新增/调整：壁纸备份失败保留原图、旧屏幕 generation 不重写新连接、图片头部与 3200 万像素预算、追加顺序持久化、合卡色点逐行 hover/press、资料页编辑按钮动态圆角和整卡悬停显示×。设置说明另改为单词边界换行，并修正逗号后的词距丢失；第二轮复审所遇 429 发生在最后交付前，未改变结论证据。

### 三端完整测试

从 `build/test-results/test/TEST-*.xml` 聚合：

| 目标 | tests | failures | errors | skipped | suites |
|---|---:|---:|---:|---:|---:|
| 1.21.1-neoforge | 1290 | 0 | 0 | 2 | 164 |
| 1.21.1-fabric | 1302 | 0 | 0 | 2 | 167 |
| 1.20.1-forge | 1290 | 0 | 0 | 2 | 164 |

三端 Windows `build` 均成功；Linux 使用 `build -x test -Patomchat.nativePlatform=linux-x64` 均成功。原始日志在 `.work/2026-10-19-settings/logs/`。

### 六包

| 包 | SHA-256 |
|---|---|
| NeoForge Windows | `b5d361eb8c8f52e781f6e05896fdaa3a80f28269423070a228a98fd529a0165b` |
| NeoForge Linux | `a9a6fe2709de1f78d958555fb8430ab28420b803d9f7bdc89baf583ee1dd4c79` |
| Fabric Windows | `1a59724539e994179c32329e30a1cd16e25006d9ba1bafa8b26a7d4689f3c8bd` |
| Fabric Linux | `882e018af7fb24e480e30c5eea265edcbf1b2d2867d7419d4eb373799027db6b` |
| Forge Windows | `bba032e001493291722b5713c5a16ce844744a17b0933474b486326899d06c07` |
| Forge Linux | `d921ff48caba29ec24dbd55c0e0f2302adbea146dc8e4d14071d51e927c04c05` |

校验确认每个包只含对应 OS 的 Skija native；版本为 0.3.0；中英语言资源、`AppearancePresetStore` 与 `DraftSessionStore` 均存在；同加载器 Windows/Linux 的 `com/atom/chat/**` 与 `assets/**` 内容无新增、缺失或变化（`osParity` 三项均为三个空数组）。详细路径、字节数和构建戳见 `.work/2026-10-19-settings/evidence/package-verification-final.json`。

### 静态守卫与输入稳定性

- `tools/gen_seams.py --check`：exit 0，甲 123 / 乙 71。
- `tools/verify_targets.py`：exit 0，六目标校验通过。
- `tools/twin_diff_report.py`：exit 0，孪生 68 对，未归类 0，陈旧 0。
- `tools/verify_log_prefixes.py`：exit 0，生产日志出口 262，违规 0。
- `tools/bare_scan.py`：exit 0，实测 477 / 基线 505。
- `git diff --check`：exit 0。
- 构建输入 664 文件聚合 SHA-256：`fe6c1c4fdc96431f7af16ae7ed06a0d958105edd3941dbb11b446ae99a27dac1`；三端 Windows/Linux 构建前后一致。核对包内 `DraftSessionStore.saveForScreen/generation`、`AppearancePresetStore$Preset.order()` 与无空格中文编号，确认最终包包含复审后代码。

### 日常实例部署

- Windows「Mechanomania-航空学」NeoForge：安装 `atomchat-NeoForge-1.21.1-0.3.0.jar`，SHA-256 `b5d361eb8c8f52e781f6e05896fdaa3a80f28269423070a228a98fd529a0165b`。旧包 SHA-256 `5cacf6abb2eff8bd12218cb14f2b1beab58070b353a9d6bbd2fed5eb6154d8a4` 已备份到 `D:\AtomChat-Backups\2026-10-19-settings-rework\NeoForge\`。
- WSL Prism「1.21.1」Fabric：安装 `atomchat-Fabric-1.21.1-linux-x64-0.3.0.jar`，SHA-256 `882e018af7fb24e480e30c5eea265edcbf1b2d2867d7419d4eb373799027db6b`。旧包 SHA-256 `9a6ef97cab2b8aa8aee8a630a11aaf902d1f3d6ef26ac8531f8bd8f9f9a21c7a` 已备份到实例 `atomchat-backup/2026-10-19-settings-rework/`。
- 部署后重新计算目标文件哈希并使用临时代名原子替换；两个 mods 目录均只有一个 AtomChat JAR，无残留 `.new`。部署时没有运行中的 Minecraft/Java 进程，未启动游戏。部署清单见 `.work/2026-10-19-settings/deployment.json`。

### 离屏截图

- `.work/2026-10-19-settings/evidence/png/settings-appearance-zh.png`
- `.work/2026-10-19-settings/evidence/png/settings-appearance-en.png`
- `.work/2026-10-19-settings/evidence/png/settings-chat-zh.png`
- `.work/2026-10-19-settings/evidence/png/settings-chat-en.png`
- `.work/2026-10-19-settings/evidence/png/send-radius-0.png`
- `.work/2026-10-19-settings/evidence/png/send-radius-0-hover.png`
- `.work/2026-10-19-settings/evidence/png/send-radius-0-press.png`
- `.work/2026-10-19-settings/evidence/png/send-radius-default.png`
- `.work/2026-10-19-settings/evidence/png/settings-presets-strip.png`

设置页截图使用与产品相同的离屏绘制代码和实际中英语言 JSON，仅在拍照测试中注入内存语言表；该临时钩子已在最终源码恢复，不进入产品。

## 已知限制

- 未启动游戏；GPU 实帧、真实 Skija native 运行、鼠标/键盘焦点、关闭动画和真实 JOIN/DISCONNECT 尚未验证。
- 现有 `SendPipelineTest` 继续不覆盖真实网络发送；本次验证了统一成功完成链与关闭回调，真实连接发送仍需实机。
- 离屏截图不能证明游戏内字体、缩放、窗口边缘裁切与输入法行为。
- 保持原版本/依赖/全局配置/部署实例不变；旧 `theme-presets` 目录未迁移也未删除。
