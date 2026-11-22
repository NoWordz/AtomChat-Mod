# AtomChat 0.9.4 清晰度、动效与兼容复测

## 渲染改动

- 常用线框 SVG 恢复连续线宽，使用同一原生抗锯齿描边画笔和实际曲线边界居中。设置首页和菜单接回旧隐私盾牌与旧屏蔽图形。圆角上限由滑杆、配置和外观预设共用。以现行 25 px 作对照，另拍 30 px 样本。
- 折叠标题及未选中页签使用聊天胶囊的文字软影。同一页签复用字体、颜色和圆角参数，静止时省去无位移的画布状态保存。常规文字直接提交缓存字形，文字软影沿用原有独立的过滤层。
- 性能模式中的页签、轮滚、游标和跳转反馈按即时状态绘制，拖动保留输入跟随，媒体继续播放。无独立卡片模糊时，当前页直接画到画布。受影响测试同时验证画布身份、正文和头栏调用次数、旧过渡退役以及标签折叠和点击的布局。

## MuoniumPlayer 对照

已核对 [2.6.0 固定提交](https://github.com/zihe114514/MuoniumPlayer-multiversion/commit/eb541684e9d681d8145f5f9d681cf31980646dc7) 的七个 Minecraft 版本目录、共享生产源码、内嵌 SkSL、世界歌词 GLSL 和同 SHA 的 14 项构建结果。本机 CCB 中的 2.5.0 另做字节码核对。

| 路径 | 上游行为与本轮 AtomChat 对照 |
| --- | --- |
| 帧缓冲 | 1.20.1 与 1.21.1 复用设备分辨率离屏层并即时复合。更新树中复合挂在 blit 或 present。AtomChat 已在主帧缓冲直画，测试保留原渲染桥和捕获边界。 [1.21.1 即时合成](https://github.com/zihe114514/MuoniumPlayer-multiversion/blob/eb541684e9d681d8145f5f9d681cf31980646dc7/1.21.1/src/main/java/com/muoniumplayer/render/skija/SkijaHost.java#L457-L506) |
| 背景 | 各边半分辨率，实时档最低采集间隔 8 ms，普通档 33 ms。以采集代次和模糊档位复用。AtomChat 按现行双 200% 模糊实跑，保留运动中背景刷新。 [采集入口](https://github.com/zihe114514/MuoniumPlayer-multiversion/blob/eb541684e9d681d8145f5f9d681cf31980646dc7/muonium-common/src/main/java/com/muoniumplayer/render/skija/GlassBackdrop.java#L337-L425) |
| 转场和文本 | 普通转场只遍历足够可见的页面，播放器与灵动岛的专用快照只存在于新版。不同列表用区间或二分定位可见条目，按回退字体复用文字。AtomChat 分测热切、静止和 220 条滚动，性能模式省去整页 Picture 录制。 [转场](https://github.com/zihe114514/MuoniumPlayer-multiversion/blob/eb541684e9d681d8145f5f9d681cf31980646dc7/muonium-common/src/main/java/com/muoniumplayer/core/screens/ncm/NCMScreen.java#L762-L853) · [列表](https://github.com/zihe114514/MuoniumPlayer-multiversion/blob/eb541684e9d681d8145f5f9d681cf31980646dc7/muonium-common/src/main/java/com/muoniumplayer/core/rendering/ui/container/ScrollPanel.java#L680-L816) |
| 媒体 | 后台取封面、解码和预模糊，按帧限上传并借用 GL 纹理。AtomChat 用固定 192×120 图片和原图像素对照。 [封面](https://github.com/zihe114514/MuoniumPlayer-multiversion/blob/eb541684e9d681d8145f5f9d681cf31980646dc7/muonium-common/src/main/java/com/muoniumplayer/core/ncm/music/MusicCoverService.java#L680-L765) · [上传](https://github.com/zihe114514/MuoniumPlayer-multiversion/blob/eb541684e9d681d8145f5f9d681cf31980646dc7/muonium-common/src/main/java/com/muoniumplayer/core/rendering/texture/Textures.java#L21-L45) |

## 契约和 Yoga

- 0.9.2 公版配公开 0.7.0 时，三端可在同一设置入口复现机制指纹不匹配。AtomLib 0.7.1 采用语义兼容版本，保留完整构建指纹和独立宿主注册表，显式支持两份已验证公版旧指纹。AtomChat 0.9.4 三端使用同一个已验 0.7 系列范围。包身份、内容和清单照常检查，明确的不兼容状态使用普通设置并提示一次。
- Forge 实机曾在加载历史设置时失败，包内有正确 DLL。Forge 的 union 文件系统映射和 JarJar 模块可见性造成原物理路径回退失效。AtomLib 改从自身模组资源读取构建索引指向的嵌套 DLL，继续使用原有验证、提取和显式宿主路径优先规则。原包与修后包均由隔离客户端复核。 [AtomLib 构建与测试记录](D:/AtomChat-Backups/2026-11-21-v094-followup-ccb/evidence/atomlib-verification.md)

## 最终客户端和帧时

Forge 1.20.1 47.4.1、NeoForge 1.21.1 21.1.235 及 Fabric 1.21.1 CCB 均使用实际最终 JAR 和 AtomLib 0.7.1。Forge 确认继续加载器的非致命提示后，通过游戏聊天入口打开面板和历史页。NeoForge 0.9.4 与原版 0.9.2 均重复进入历史设置。 [客户端记录](D:/AtomChat-Backups/2026-11-21-v094-followup-ccb/evidence/real-client-smoke.json) · [Forge 原图](D:/AtomChat-Backups/2026-11-21-v094-followup-ccb/evidence/forge-094-history.png) · [NeoForge 0.9.4 原图](D:/AtomChat-Backups/2026-11-21-v094-followup-ccb/evidence/neo-094-history.png) · [NeoForge 0.9.2 原图](D:/AtomChat-Backups/2026-11-21-v094-followup-ccb/evidence/neo-092-history.png)

Fabric 使用完整 CCB 模组组、RTX 5060 Laptop、1600×900、1.25× 界面密度、Bliss、面板和卡片双 200% 模糊。两边使用同一份最终 AtomLib 0.7.1、同一世界、视角、渲染分辨率、配置和探针。新包先测，旧包随后测。共 29,810 个采样帧。另取 30 px 样本对照圆角，主要帧时场景使用 25 px。 [帧时与原始输入](D:/AtomChat-Backups/2026-11-21-v094-followup-ccb/evidence/frame-times.json)

| 场景 | 改前 p50 / p95 | 改后 p50 / p95 |
| --- | ---: | ---: |
| 关面板 | 2.50 / 3.82 ms | 2.52 / 4.15 ms |
| 文字 | 2.89 / 4.71 ms | 3.03 / 5.20 ms |
| 图片 | 2.72 / 4.34 ms | 2.80 / 4.59 ms |
| 220 条历史滚动 | 2.75 / 4.27 ms | 3.04 / 4.51 ms |
| 设置稳态 | 3.28 / 4.96 ms | 3.87 / 6.01 ms |
| 标准模式切页签 | 4.29 / 6.66 ms | 5.08 / 7.57 ms |
| 性能模式切页签 | 2.83 / 4.45 ms | 2.91 / 4.61 ms |

性能模式切页 p99 从 6.15 降到 5.48 ms，面板胶囊的第一帧偏离目标次数从 10/10 到 0/10，两边 220 条历史都处理 10 次滚动事件。固定源图内部的 18,042 个 RGB 像素一致。 [聊天 1:1 对照](D:/AtomChat-Backups/2026-11-21-v094-followup-ccb/evidence/1x-chat.png) · [设置 1:1 对照](D:/AtomChat-Backups/2026-11-21-v094-followup-ccb/evidence/1x-settings.png) · [圆角 1:1 对照](D:/AtomChat-Backups/2026-11-21-v094-followup-ccb/evidence/1x-radius.png) · [隐私图标 1:1 对照](D:/AtomChat-Backups/2026-11-21-v094-followup-ccb/evidence/1x-home.png)

另有 24,861 帧开启 CPU/GPU 分段诊断的正序记录，使用 0.7.1 第一次产物。设置页 GPU 区间 p50 由 0.266 到 0.585 ms，性能模式页签的 Java 分配由 36.7 到 34.3 KB/帧。该组末段关面板 p50 已漂移约 1.09 ms，因此整帧结论使用上面的最终反序组。设置文字软影以独立过滤层合成。现有数据未单独归因每一层影子的成本，其他页面和不同设备亦有各自负载。

三端执行 `test build verifyAtomLibInput`，共 6,723 项含 56 跳过，零失败。产物检查、目标表及资料清单核对通过。开发包跳过 `frontendContractSourceCheck` 和 `frontendContractCheck`，正式设置设计及像素参考仍待审定。本轮保留历史参考 PNG 的原始字节。 [构建记录](D:/AtomChat-Backups/2026-11-21-v094-followup-ccb/evidence/artifacts.json)

20:25:46 备份并换入主 1.21.1-CCB。活动包分别为 AtomChat `ffb7f18bca82` 和 AtomLib `f36d559646fd`，两份活动包及四份备份经独立回读。 [部署回执](D:/AtomChat-Backups/2026-11-21-v094-followup-ccb/20261121-202546-968412/deployment.json) · [读回记录](D:/AtomChat-Backups/2026-11-21-v094-followup-ccb/evidence/deployment-readback.json)

## HUD 与设置渲染复测

- CCB 的实际配置为面板模糊关闭、卡片模糊 99.879%，性能模式关闭。Fabric 的 shader 在资源重载后可以处于未初始化状态。HUD 横幅先问 `isAvailable()`，使本应负责延迟加载的 `renderMasked()` 永远收不到首个请求。固定场景中主动清除 shader 后，旧包 100% 卡片模糊仍然 `available=false`，修复包的 0% 对照保持 false，100% 横幅首个阶段变为 true，原图确认横幅内的纹理扩散。HUD 继续在 Skia 绘制之前用 GL 合成模糊，维持 Gnetum 的逐帧主帧缓冲路径。
- 25 到 30 已经保存进配置。原 AtomLib 的渲染 token 将倍率封顶在 1.25，导致区间没有形变。0.7.1 候选将 AtomChat 的倍率上限设为 1.5，同包 25 和 30 的面板转角在实机原图可区分。接近胶囊的小控件依然受短边限制。面板描边恢复未量化的逻辑宽度，保留 Skija 覆盖抗锯齿。折叠的小箭头同样开启覆盖抗锯齿。
- 未选中页签和折叠标题原本各自用 `saveLayer` 做胶囊同款投影。折叠标题新增图标时，把两套轮廓放进同一个过滤层。静止的阴影可以按设备密度、相位、字形和主题复用。字与图标的前景仍由原生 Skija 绘制。移动或矩阵不匹配时用原过滤路径。
- 同一候选包、同一进程 ABBA 消融中，稳态设置的 p50 比持续实时过滤分别低 0.650、0.349 ms，p95 分别低 1.026、0.268 ms，关闭阴影的另一个阶段 p50 为 3.302 ms。标准热切的两对 p50 为 −0.344 和 +1.508 ms，不能从这两对稳定归因切页收益。整包反向对照的关面板 p50 相差 0.004 ms，稳态设置 p50 3.945 → 3.552 ms、标准热切 5.116 → 4.758 ms、性能模式热切 3.002 → 2.916 ms。稳态设置 p99 6.995 → 7.605 ms，尾部仍需参照原始分布。两个整包同时换入新几何和 AtomLib 候选，单项因果用同包消融。相同图片消息的 18,042 个 RGB 像素逐个一致。原始帧时、截图、构建及夹具见 [本轮证据](D:/AtomChat-Backups/2026-11-21-v094-render-recovery/evidence/frame-times.json)。
- MuoniumPlayer 的半分辨率采集会按“采集代次 + 模糊档位”复用结果，普通更新最短间隔 33 ms，实时档 8 ms。AtomChat 卡片底已使用半分辨率，这轮将复用用在静态标题阴影。上游普通转场按页面可见区域绘制，特殊动画另用快照。AtomChat 标准转场仍要复合两页的皮肤和遮罩。 [上游背景](https://github.com/zihe114514/MuoniumPlayer-multiversion/blob/eb541684e9d681d8145f5f9d681cf31980646dc7/muonium-common/src/main/java/com/muoniumplayer/render/skija/GlassBackdrop.java#L337-L425) · [上游转场](https://github.com/zihe114514/MuoniumPlayer-multiversion/blob/eb541684e9d681d8145f5f9d681cf31980646dc7/muonium-common/src/main/java/com/muoniumplayer/core/screens/ncm/NCMScreen.java#L762-L853)
- Apple 的 iPhone 15 机械图 Detail D 用多点坐标给出四角轮廓，可看出它比单一四分之一圆弧更缓地进入直边，图纸未标明数学连续阶数。显示规格确认可视区四角为圆角。Apple 软件设计将连续曲率和内外轮廓的配合分开讨论。AtomChat 较厚的描边会让内外轮廓间距在转角更显眼，因此机身用了变曲率也不能直接推导当前描边的观感。 [机械图第 2 页](https://developer.apple.com/download/files/accessories/dimensional-drawings/iphone-15.pdf#page=2) · [显示规格](https://support.apple.com/en-us/111831) · [Apple 连续与同心圆角说明](https://developer.apple.com/videos/play/wwdc2023/10076/)
- 三端与本轮 AtomLib 0.7.1 组合经实际 Minecraft 客户端验证。NeoForge 额外加载 Gnetum 3.3.6，`atomchat:notifications` 保持 `OFF` 缓存设置，在横幅阶段提交 24 次模糊，GL 目标均为主帧缓冲。当前 0.9.4 和已发布 0.9.2 均能在 NeoForge 进入面板与历史页。Forge 首次用了还未完成 `reobfJarJar` 的中间包而触发 refmap 错误，取构建结束后的正式字节完成复验。
- AtomChat 三端共 6,729 项，56 跳过，零失败。AtomLib 401 项零失败。平台包、输入 SHA、ZIP CRC、消费包及 19 项验包工具测试通过。三端开发构建沿用本地未发布的 0.7.1 输入，跳过两项正式前端契约任务。候选产物通过 [摘要清单](D:/AtomChat-Backups/2026-11-21-v094-render-recovery/evidence/artifacts.json) 交接，CCB 需要成对更新 AtomChat 与 AtomLib。

AtomChat 每个目标分别执行：

```powershell
.\gradlew.bat --offline --no-daemon --console=plain "-PatomlibMavenRepo=<0.7.1候选本地Maven>" test build verifyAtomLibInput -x frontendContractSourceCheck -x frontendContractCheck
```

隔离 CCB 与 NeoForge 的探针和启动脚本、原尺寸截图、逐帧数据、Gradle 日志与打包清单收于本轮证据目录。

21:29:52 已备份并成对部署 CCB，AtomChat `dd4340cce007` 与 AtomLib `7347d9099f6d`。读回两个活动包和四份备份，751 份其它模组与配置经核对，部署过程未启动主客户端。 [部署回执](D:/AtomChat-Backups/2026-11-21-v094-render-recovery/20261121-212952-762913/deployment.json)
