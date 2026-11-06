# muonium-research 完整消息记录

原始执行记录（含工具调用/结果）：C:\Users\NIUQU\.codex\sessions\2026\11\05\rollout-2026-11-05T14-06-23-01a1aa50-c80d-7af0-8ba1-829f98fe1848.jsonl

本文件不截断用户与助手文本；工具调用和结果保留在上述原始记录中。

## 1. user | 2026-11-05T06:06:27.075Z | 原始行 6

# AGENTS.md instructions

<INSTRUCTIONS>
## 人格 / persona
- **不说废话** - 简洁通俗中文短句，结构化输出
- **批判性思维** - 可批判决策、质疑方案、直接指错；模糊先追问（grill+ask），不确定给方案+取舍
- **不装全知** - 陌生领域先查；会出错：做完自查

---

## 铁律 / Hard Rules（无条件执行）

1. **先探后写** — 除非环境已打通/验证，否则调用非自写接口（宿主 API/第三方 SDK/CLI/HTTP）前需跑 3 行探针，确认真实字段/参数/返回结构，cite file:line 再写进交付物；禁凭印象拼 `res.xxx`。
2. **验证金字塔** — unit → suite → dev smoke → artifact smoke → acceptance，禁跳级；"能跑"≠完成，未跑回归不许报"修好了"；非平凡改动（多文件/新接口/插件/发布物）必 delegate `code-reviewer` 再审。
3. **根因门** — 症状≠根因；修不好回证据、禁叠补丁；同一问题连续 2 轮无进展 → 停手，先说明「卡在哪、试过什么、下一步换哪个信息源」。
4. **如实报告** — 结论先行、全证据、披露 workaround 与遗留，每条 cite file:line；禁给无适用范围的"修好了"。
5. **证据双轨** — 环境事实与代码事实分头收集；多文件扫荡/长日志交 subagent 只收结论

---

## 省 token / Token Economy

成本 ≈ 工具调用次数 × 上下文体积（输入占 ~96%），压输入才有用：
1. **大文件先定位再局部读** — >~500 行先 `Grep` 行号再 `Read` 片段；小文件/配置/需全貌时整读。
2. **长输出先落盘再取** — 构建/测试/全文搜索重定向到临时目录再 `grep`/`tail`；短错误直接看。
3. **宽搜交 subagent** — 仅当「中间产物 >> 结论」；需边看边改主意的探索自己做。
判据：省下的重发量 > 多跑一轮开销；拿不准直接做。

---

## 其余纪律 / Discipline

方案门/实施顺序/安全门/可观测回写/完成四合一/通用编码纪律 → skill `agent-discipline`（架构改动、安全敏感写入、发版或需完整交付标准时读）。

## 正本 / Canon（跨工具唯一）

- **记忆** `~/.agents/memory/`：开工先读索引 `MEMORY.md`，条目按需读；新记忆写正本 + 更新索引行。**宿主自带 memory 目录一律不用**（写进去 = 从正本消失，跨 harness 读不到）。
- **技能** `~/.agents/skills/`：要用时直接 `Read` 其 `SKILL.md`（比走 Skill 工具省）。
- 索引行 ≤600 字符（这是什么 + 现役状态 + 最要命的坑 + 指回正本）；工具拓扑见 `reference_agent-hub-topology.md`。

## 规矩 / Conventions

- commit message 英文、无 emoji（全中文输出规矩的唯一例外）
- 发版必附双语 CHANGELOG（中 + 英）
</INSTRUCTIONS>
<environment_context>
  <cwd>C:\Users\NIUQU\Documents\Codex\2026-11-05\muoniumplayer-render-audit</cwd>
  <shell>powershell</shell>
  <current_date>2026-11-05</current_date>
  <timezone>Asia/Shanghai</timezone>
  <filesystem><workspace_roots><root>C:\Users\NIUQU\Documents\Codex</root><root>C:\Users\NIUQU\Documents\Codex\2026-11-05\muoniumplayer-render-audit</root><root>C:\Users\NIUQU\.codex\visualizations\2026\11\05\01a1aa50-c80d-7af0-8ba1-829f98fe1848</root></workspace_roots><permission_profile type="disabled"><file_system type="unrestricted" /></permission_profile></filesystem>
</environment_context>

## 2. assistant | 2026-11-05T06:06:33.352Z | 原始行 14

我会先核对仓库版本、项目规范和记忆索引，再分开收集渲染生命周期与缓存路径证据。全程只读，不构建、不启动游戏。

## 3. assistant | 2026-11-05T06:06:52.377Z | 原始行 25

本次采用 `check` 与 `code-review` 技能做静态审计；用户已明确禁止执行验证命令，因此结论会限定为源码证据，运行时性能保持待验证。

## 4. assistant | 2026-11-05T06:08:25.278Z | 原始行 65

已确认两份仓库都是干净的 `main@c602189`，版本号为 `2.3.1`。目前最关键的区别是：播放器复用离屏渲染目标，但每次绘制都会清空重画；这与“整页渲染结果跨帧复用”是两回事。接下来核对背景模糊的分层缓存和动画失效路径。

## 5. assistant | 2026-11-05T06:11:09.189Z | 原始行 125

布局并非每帧整树重建：脏标记触发重建，开合动画期间还会延后补账。滚动也有等高行与签名快路径，但控件绘制层仍扫描子列表，不能称为完整的 O(可见行数) 虚拟列表。模糊采用四档缓存与重建预算，代价是允许借用旧画面或近似半径。

## 6. assistant | 2026-11-05T06:14:25.123Z | 原始行 170

已核对到明确的视觉取舍：开合动画会停用液态玻璃，静态背景模式冻结动态背景，封面阴影仍走 `saveLayer + blur`。这些能作为 AtomChat 的候选策略，但不能据此宣称同等视觉下更快；本次也不会引用源码注释中的历史毫秒数作为实测结果。

## 7. user | 2026-11-05T06:16:16.520Z | 原始行 198

<subagent_notification>
{"agent_path":"01a1aa52-1318-75f1-bf18-aa968468b341","status":{"completed":"已收敛为文字、图片各 4 条。均为本地源码证据；未写文件、构建、测试或启动游戏。运行时影响尚未验证。\n\n**文字**\n\n1. **当前是逐字形拼装，没有整段塑形。** 歌词换行调用 `fitWidth`；绘制逐 UTF-16 `char` 查 glyph，再按独立 advance 拼 TextBlob。**移植策略：**测量、换行、绘制共用塑形结果，以字素簇切分。**风险：**补充平面字符、组合字符、阿拉伯文及双向文本不能据现实现保证正确。  \n   证据：[LyricLine.java:181](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/screens/ncm/LyricLine.java:181)、[CFontRenderer.java:493](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/font/CFontRenderer.java:493)、[CFontRenderer.java:728](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/font/CFontRenderer.java:728)。\n\n2. **TextBlob 有明确限额和关闭路径，可保留。** 每渲染器最多 **256 条／16,384 个 UTF-16 单元**；仅缓存 ≤1,024 单元且无格式码的字符串。淘汰、替换、字体重建均关闭旧 blob；字宽表达到 **20,000 条**后整表清空。**移植策略：**保留所有权与双限额，未来缓存键增加塑形参数。**风险：**这些是每实例限额，不是全局内存预算。  \n   证据：[CFontRenderer.java:79](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/font/CFontRenderer.java:79)、[TextBlobCache.java:25](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/font/TextBlobCache.java:25)、[CFontRenderer.java:644](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/font/CFontRenderer.java:644)、[CFontRenderer.java:1033](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/font/CFontRenderer.java:1033)、[CFontRenderer.java:1059](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/font/CFontRenderer.java:1059)。\n\n3. **字体登记表没有容量上限，切换后旧 Typeface 仍被强引用。** `UiFontLibrary` 每次准备新句柄，再登记到身份映射；过期异步结果直接返回，没有显式关闭。`dispose` 关闭 Typeface，但不清字体字节表。**移植策略：**按稳定字体标识复用，明确活动引用与退役释放；丢弃 Prepared 时关闭资源。**风险：**反复切换字体会累计登记对象；过期结果的原生回收时机待验证。  \n   证据：[UiFontLibrary.java:244](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/management/UiFontLibrary.java:244)、[UiFontLibrary.java:295](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/management/UiFontLibrary.java:295)、[SkijaText.java:28](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/render/skija/SkijaText.java:28)、[SkijaText.java:101](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/render/skija/SkijaText.java:101)。\n\n4. **测量与换行存在字符归一化差异。** 绘制把 `（ ）・` 替换为 `( )·`；整串测宽只替换括号，换行直接量原字符。**移植策略：**抽成同一文字预处理入口。**风险：**这些字符在所选字体中字宽不同时，换行、裁切与实际绘制边界可能不一致，误差待测。  \n   证据：[CFontRenderer.java:736](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/font/CFontRenderer.java:736)、[CFontRenderer.java:1017](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/font/CFontRenderer.java:1017)、[CFontRenderer.java:1212](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/font/CFontRenderer.java:1212)。\n\n**图片**\n\n1. **动态封面已有可移植的有界生命周期。** 图片分支限制 **8 MiB、24 帧、最长边512**；视频分支下载限制 **12 MiB**，抽帧为 **256²、最多50帧**。安装时核对当前曲目；已安装最多保留 **2 首**，淘汰删除纹理并撤销尝试记录。**移植策略：**保留身份校验与主动淘汰。**风险：**图片先完整解码再缩小，压缩字节限额不等于解码峰值限额。  \n   证据：[MusicCoverService.java:58](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/ncm/music/MusicCoverService.java:58)、[MusicCoverService.java:524](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/ncm/music/MusicCoverService.java:524)、[MusicCoverService.java:787](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/ncm/music/MusicCoverService.java:787)、[VideoCoverFrames.java:76](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/ncm/music/VideoCoverFrames.java:76)。\n\n2. **静态封面缺少缓存限额，同键覆盖不释放旧纹理。** 播放准备、下一首预加载均会进入封面服务；服务仅检查“已安装”，没有处理中去重。`TextureManager` 的 `512` 是初始容量，`loadTexture` 直接 `put`。本地小封面也直接上传原图，没有本地缩尺寸。**移植策略：**按字节预算淘汰、合并同键请求、替换时释放旧对象，并在上传前限制像素数。**风险：**重复未完成请求可产生旧 GL 纹理遗失；长会话缓存持续增长。  \n   证据：[CloudMusic.java:2000](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/ncm/music/CloudMusic.java:2000)、[MusicCoverService.java:87](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/ncm/music/MusicCoverService.java:87)、[TextureManager.java:19](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/TextureManager.java:19)、[TextureManager.java:50](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/TextureManager.java:50)、[MusicCoverService.java:744](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/ncm/music/MusicCoverService.java:744)。\n\n3. **上传预算有界，积压容量无界。** `onLoop` 排空预算为 **4 ms／最多12项**，单项不可抢占；队列持续持有待上传图像。下载去重标记靠成功后的回调移除，上传异常路径不会执行该回调。**移植策略：**增加待上传字节上限、过期任务取消；终结回调放入 `finally` 并携带成功状态。**风险：**突发下载可积压内存；一次上传失败可能让该地址一直处于 pending。  \n   证据：[MuoniumPlayerEventHandler.java:46](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/MuoniumPlayerEventHandler.java:46)、[Textures.java:39](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/texture/Textures.java:39)、[TextureUploadQueue.java:21](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/texture/TextureUploadQueue.java:21)、[Textures.java:89](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/texture/Textures.java:89)、[Textures.java:141](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/texture/Textures.java:141)。\n\n4. **SkijaImages 借用纹理，缓存视图与尺寸表均无限额。** 分配登记真实尺寸；尺寸改变会关闭旧视图。删除失效只清 Image，不移除尺寸；整体 `clear` 才清两表。**移植策略：**保留借用所有权，将“尺寸变更失效”与“纹理删除遗忘”分开。**风险：**长期创建、删除不同纹理名会留下尺寸记录；同尺寸动画更新继续复用视图，其跨后端一致性仍需验收。  \n   证据：[SkijaImages.java:51](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/render/skija/SkijaImages.java:51)、[SkijaImages.java:97](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/render/skija/SkijaImages.java:97)、[DynamicTexture.java:105](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/texture/DynamicTexture.java:105)、[SkijaImages.java:159](D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/render/skija/SkijaImages.java:159)。"}}
</subagent_notification>

## 8. user | 2026-11-05T06:38:20.355Z | 原始行 199

<turn_aborted>
The user interrupted the previous turn on purpose. Any running unified exec processes may still be running in the background. If any tools/commands were aborted, they may have partially executed.
</turn_aborted>

## 9. user | 2026-11-05T06:38:35.963Z | 原始行 209

<external_codex_apps_open_page>{"page_id":null}</external_codex_apps_open_page>

## 10. user | 2026-11-05T06:38:35.985Z | 原始行 211

继续


## 11. assistant | 2026-11-05T06:38:42.015Z | 原始行 217

继续完成对照部分。Muonium 侧的 Surface、模糊缓存、布局与封面证据已收齐；我现在只读核对 AtomChat/AtomLib 的对应实现，避免把“可移植建议”写成未经证实的性能结论。

## 12. assistant | 2026-11-05T06:40:24.671Z | 原始行 333

对照已收敛到提交版本：AtomChat 与 AtomLib 都有未提交改动，我会以各自 `HEAD` 为准，并把本地 WIP 排除在结论外。这样不会把未落盘的 TextBlob/GL 快照优化误算成现役架构。

## 13. assistant | 2026-11-05T06:41:04.554Z | 原始行 342

## 审计底线

本次只读检查了 `main` 已提交代码，未构建、测试、启动游戏或修改文件。

- MuoniumPlayer：`main@c602189862d36a45965839009454172eafad60d6`，版本 `2.3.1`；五棵版本树版本号一致。[26.3/gradle.properties](/D:/MuoniumPlayer-multiversion/26.3/gradle.properties:33)、[1.20.1/gradle.properties](</D:/MuoniumPlayer-multiversion/1.20.1 recode/gradle.properties:21>)
- `D:/MuoniumPlayer-src` 是同一提交的浅克隆，`muonium-common` 与主审计仓逐文件一致。
- AtomChat：`main@e92fdaf9370a4ed53c8edb56117eb97cbb18b8e1`，版本 `0.5.0`，Skija `0.143.18`。[gradle.properties](/D:/AtomChat-Main/gradle.properties:17)
- AtomLib：`main@56f1b9574f28ebacf4f9951064cdb8e0d3841752`，版本 `0.3.0`。[gradle.properties](/D:/AtomLib/gradle.properties:3)
- 两个对照仓工作区都有未提交改动，包括 AtomChat 的 `SkiaGraphics`、`PanelBlurRenderer` 和 AtomLib 的 `SkijaRenderBridge`。以下对照仅使用各自 `HEAD`，不把本地 WIP 优化计入现役架构。
- 根规范声明的 `docs/实现约定-*` 在当前检出中不存在，只能以 [AGENTS.md](/D:/MuoniumPlayer-multiversion/AGENTS.md:28) 和实际代码为准。

## 已证事实

### 1. Surface、Canvas、Image 生命周期

Muonium 有两条版本差异明显的合成链：

- `1.20.1` 把 Minecraft 当前 FBO 包装成离屏 Skia Surface；尺寸变化才重建，每帧清空后重新画，结束时立即合成回原 FBO。[SkijaHost.java](</D:/MuoniumPlayer-multiversion/1.20.1 recode/src/main/java/com/muoniumplayer/render/skija/SkijaHost.java:293>)、[清屏](</D:/MuoniumPlayer-multiversion/1.20.1 recode/src/main/java/com/muoniumplayer/render/skija/SkijaHost.java:316>)、[帧末合成](</D:/MuoniumPlayer-multiversion/1.20.1 recode/src/main/java/com/muoniumplayer/render/skija/SkijaHost.java:458>)
- `26.3` 复用同一张离屏 Surface，但每显示帧只清一次，并把合成推迟到 `GlSurface.present()`。[SkijaHost.java](/D:/MuoniumPlayer-multiversion/26.3/src/main/java/com/muoniumplayer/render/skija/SkijaHost.java:366)、[compositeFrame](/D:/MuoniumPlayer-multiversion/26.3/src/main/java/com/muoniumplayer/render/skija/SkijaHost.java:621)、[GlSurfaceMixin.java](/D:/MuoniumPlayer-multiversion/26.3/src/main/java/com/muoniumplayer/mixin/GlSurfaceMixin.java:70)

这种复用只复用渲染目标，没有复用页面像素。合成时创建的是当前帧临时 `Image`，用 try-with-resources 立即关闭。[SkijaHost.java](/D:/MuoniumPlayer-multiversion/26.3/src/main/java/com/muoniumplayer/render/skija/SkijaHost.java:739) 主界面仍然每帧遍历组件重新绘制，布局树仅在脏标记触发时重建。[NCMScreen.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/screens/ncm/NCMScreen.java:253)、[AbstractWidget.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/ui/AbstractWidget.java:177)

AtomChat/AtomLib 走另一套架构：AtomLib 直接借用宿主 framebuffer，维护一个与 GL 目标绑定的 Surface；目标身份、尺寸、附件变化才重建，每帧 `flushAndSubmit` 后结束，不做第二次全屏贴回。[SkijaRenderBridge.java，AtomLib HEAD](/D:/AtomLib/platforms/shared/src/main/java/com/atom/lib/client/render/SkijaRenderBridge.java:84)、[目标复用](</D:/AtomLib/platforms/shared/src/main/java/com/atom/lib/client/render/SkijaRenderBridge.java:152>)、[显式重建](/D:/AtomLib/platforms/shared/src/main/java/com/atom/lib/client/render/SkijaRenderBridge.java:155)

因此 Muonium 的全屏离屏链多了一次离屏绘制和一次到宿主 FBO 的合成；AtomChat 直接画进宿主目标。前者隔离性、stencil 和状态恢复更完整，后者的显示链更短。两者差异是否转化为 FPS 差值，本次没有运行时数据，不能下结论。

资源关闭方面，AtomLib 的 Surface、BackendRenderTarget、RenderSession 和 DirectContext 都有显式关闭路径，并绑定创建线程与 GL 上下文。[SkijaRenderBridge.java，AtomLib HEAD](/D:/AtomLib/platforms/shared/src/main/java/com/atom/lib/client/render/SkijaRenderBridge.java:186)、[close](</D:/AtomLib/platforms/shared/src/main/java/com/atom/lib/client/render/SkijaRenderBridge.java:198>)

Muonium 也定义了完整关闭入口，会清空图片包装、字体、模糊缓存和离屏目标，[SkijaHost.java](/D:/MuoniumPlayer-multiversion/26.3/src/main/java/com/muoniumplayer/render/skija/SkijaHost.java:1188)。但在审计仓全树静态搜索中没有找到 `SkijaHost.shutdown()` 的调用点。是否由未纳入本仓的加载器代码调用，仍是待验证项。

### 2. 分层缓存与模糊

Muonium 没有整页缓存，主要使用背景采集和模糊缓存：

- 默认最多以 30Hz 从画面源 blit 到缩小纹理，不开 CPU `readPixels`。[GlassBackdrop.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/render/skija/GlassBackdrop.java:332)、[采集间隔](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/render/skija/GlassBackdrop.java:180)
- 同时维护最多 4 个模糊半径槽，每个采集代最多重建 2 档；超预算时借用当前代最近档。[GlassBlurLevels.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/render/skija/GlassBlurLevels.java:31)、[预算](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/render/skija/GlassBlurLevels.java:37)
- 转场期间允许借用最多落后 8 个采集代的旧档，避免每两代重复做全屏高斯。[GlassBackdrop.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/render/skija/GlassBackdrop.java:569)、[旧档年龄上限](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/render/skija/GlassBackdrop.java:626)
- 模糊槽借用外部 GL 纹理，而不是长期持有 `makeImageSnapshot()`；源码明确这样避免跨帧快照的 copy-on-write 生命周期风险。[GlassBackdrop.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/render/skija/GlassBackdrop.java:119)
- 开合动画期间液态玻璃直接关闭，改画实色表面。[GlassLook.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/GlassLook.java:151)

AtomChat 的模糊走面板区域裸 GL：三张面板尺寸 FBO 常驻，按需重建，执行 5 次 Kawase pass，没有 CPU 回读。[PanelBlurRenderer.java，AtomChat HEAD](/D:/AtomChat-Main/platforms/1.20.1-forge/src/main/java/com/atom/chat/render/PanelBlurRenderer.java:48)、[纹理复用](</D:/AtomChat-Main/platforms/1.20.1-forge/src/main/java/com/atom/chat/render/PanelBlurRenderer.java:265>)、[pass 循环](</D:/AtomChat-Main/platforms/1.20.1-forge/src/main/java/com/atom/chat/render/PanelBlurRenderer.java:361>)

该 HEAD 的 `full` 常量固定为 `true`，每次真正调用模糊刷新都会执行整组 pass；源码注释描述的按需节流没有在该提交的循环里体现。[PanelBlurRenderer.java，AtomChat HEAD](/D:/AtomChat-Main/platforms/1.20.1-forge/src/main/java/com/atom/chat/render/PanelBlurRenderer.java:356)

AtomChat 的文字软阴影会把整块文字放进一个过滤图层，避免每个颜色片段各开一层，但一次阴影渲染仍有一次 `saveLayer`。[SkiaFontRenderer.java，AtomChat HEAD](/D:/AtomChat-Main/shared/src/main/java/com/atom/chat/render/SkiaFontRenderer.java:322)

Muonium 的磨砂背景在预糊不可用时退回 `saveLayer + blur`；封面阴影同样每帧开一个模糊图层。[SkijaEffects.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/render/skija/SkijaEffects.java:311)、[bloom](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/render/skija/SkijaEffects.java:474)、[MusicLyricsPanel.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/screens/ncm/MusicLyricsPanel.java:2038)

本次没有发现普通 NCM 卡片使用独立的投影纹理或跨帧阴影缓存；可见的明显阴影集中在封面 bloom、文字和玻璃退化路径。

### 3. 文字塑形、TextBlob 与字体缓存

Muonium 当前不是整段复杂文字塑形：

- 绘制逐 UTF-16 `char` 解析字形、回退字体和 advance，再自行累加位置。[CFontRenderer.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/font/CFontRenderer.java:493)、[逐字符构建](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/font/CFontRenderer.java:722)
- TextBlob LRU 上限为 256 条、16,384 个 UTF-16 单元，淘汰时显式关闭原生对象。[CFontRenderer.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/font/CFontRenderer.java:79)、[TextBlobCache.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/font/TextBlobCache.java:25)
- 测宽缓存达到 20,000 条后整表清空。[CFontRenderer.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/font/CFontRenderer.java:984)
- 歌词换行调用同一个逐字宽度实现，[LyricLine.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/screens/ncm/LyricLine.java:176)
- 绘制会把全角括号和 `・` 替换成半角/中点，而换行循环直接检查原字符；若字体对这些字符宽度不同，换行边界可能与实际绘制不一致。[绘制替换](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/font/CFontRenderer.java:736)、[换行入口](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/font/CFontRenderer.java:1206)
- 字体 Typeface 使用 `IdentityHashMap` 长期缓存；关闭入口关闭 Typeface，但不清除字体源字节表。[SkijaText.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/render/skija/SkijaText.java:27)、[dispose](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/render/skija/SkijaText.java:101)

AtomChat HEAD 的现役文字路径仍由 `RichTextRenderer` 调用 `SkiaFontRenderer` 测宽和逐 run 绘制。[RichTextRenderer.java，AtomChat HEAD](/D:/AtomChat-Main/layers/mapping/official/src/main/java/com/atom/chat/render/RichTextRenderer.java:32)、[绘制 run](</D:/AtomChat-Main/layers/mapping/official/src/main/java/com/atom/chat/render/RichTextRenderer.java:156>)

AtomChat 已有 4,096 条字符串宽度 LRU，减少重复塑形。[SkiaFontRenderer.java，AtomChat HEAD](/D:/AtomChat-Main/shared/src/main/java/com/atom/chat/render/SkiaFontRenderer.java:85)

AtomLib 则提供了更完整的缓存基础：

- `TextService` 缓存整个 `TextLayout`，键包含文字、约束和字体 generation；换字体时清空塑形缓存。[TextService.java](/D:/AtomLib/runtime/src/main/java/com/atom/lib/text/TextService.java:46)、[字体换代](/D:/AtomLib/runtime/src/main/java/com/atom/lib/text/TextService.java:212)
- 按字素簇切分，缓存实际 `TextLine`，并同时限制条目数和字符数。[TextService.java](/D:/AtomLib/runtime/src/main/java/com/atom/lib/text/TextService.java:81)、[shape cache](/D:/AtomLib/runtime/src/main/java/com/atom/lib/text/TextService.java:219)
- 绘制直接重用 `TextLine`，不再每帧重建文字布局，[TextService.java](/D:/AtomLib/runtime/src/main/java/com/atom/lib/text/TextService.java:65)
- 关闭时逐个关闭 text line、shaper 和字体资源。[TextService.java](/D:/AtomLib/runtime/src/main/java/com/atom/lib/text/TextService.java:240)

这是本次审计中最明确的可移植能力。AtomChat HEAD 虽已依赖 AtomLib，却没有在实际 screen 路径中发现 `TextService` 使用，仍走旧的 `SkiaFontRenderer` 路径。

### 4. 布局与滚动重算

Muonium 的页面布局有脏标记，不是每帧全量重建。开合动画期间会保留脏标记，等动画和静置宽限期结束再统一重建。[NCMScreen.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/screens/ncm/NCMScreen.java:253)

滚动容器有两种路径：

- 等高固定行会只更新视口附近子项，并让离开视口的行执行 `onViewportExit()`。[ScrollPanel.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/ui/container/ScrollPanel.java:566)
- 启用布局签名的容器，签名不变可跳过重排；注释说明只有主页网格显式承担失效通知。[ScrollPanel.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/ui/container/ScrollPanel.java:229)
- 未启用快路径的非等高列表仍每帧计算内容高度并重排。[ScrollPanel.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/ui/container/ScrollPanel.java:244)
- 绘制阶段仍遍历完整 child 列表，再通过 `shouldRenderChildren()` 剔除视口外项，所以是绘制裁剪和位置虚拟化，不是零扫描虚拟列表。[AbstractWidget.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/ui/AbstractWidget.java:212)、[ScrollPanel.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/ui/container/ScrollPanel.java:824)
- 主页卡片按批加入，并在批次加入后显式使布局签名失效。[HomePanel.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/screens/ncm/panels/HomePanel.java:944)

AtomLib 的 Yoga 适配器在布局树、viewport 和固有高度模式都未变化时直接复用上一份 snapshot；测量回调和 revision 不变时也保留回调，避免新 lambda 引用造成无意义失效。[YogaLayoutEngine.java](/D:/AtomLib/ui/src/main/java/com/atom/lib/layout/internal/YogaLayoutEngine.java:59)、[快路径](/D:/AtomLib/ui/src/main/java/com/atom/lib/layout/internal/YogaLayoutEngine.java:76)

缩放后的绘制几何也按缩放值复用，避免每个控件重复复制整页 box Map。[PresentationFrame.java](/D:/AtomLib/ui/src/main/java/com/atom/lib/layout/PresentationFrame.java:65)

Muonium 的强项是页面级业务脏标记、等高行虚拟化和动画期间延后重建；AtomLib 的强项是通用布局引擎和稳定 revision 的快路径。两者处在不同层，不能简单说其中一个整体更快。

### 5. 封面纹理上传与缩放

Muonium 对动态封面设置了相对完整的边界：

- 解码文件最大 8 MiB，最长边 512，最多 24 帧；视频下载最大 12 MiB。[MusicCoverService.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/ncm/music/MusicCoverService.java:58)
- 已安装动态封面只保留当前和上一首，淘汰时删除纹理。[MusicCoverService.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/ncm/music/MusicCoverService.java:67)
- 安装前核对曲目身份，换歌后丢弃已解出的纹理。[MusicCoverService.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/ncm/music/MusicCoverService.java:524)
- 本地音频的小封面直接按解码后的原图上传，没有在上传前缩到显示尺寸。[MusicCoverService.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/ncm/music/MusicCoverService.java:736)

静态封面缓存本身没有条目或字节上限：`TextureManager` 的 `512` 只是初始 `ConcurrentHashMap` 容量，`loadTexture` 直接覆盖写入。[TextureManager.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/TextureManager.java:19)、[写入](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/TextureManager.java:50)

上传侧有每 tick 4 ms、最多 12 项的预算，但队列本身没有积压字节上限。[Textures.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/texture/Textures.java:39)、[TextureUploadQueue.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/texture/TextureUploadQueue.java:21)

Skija 图片包装缓存按纹理 id 和尺寸借纹理，没有条目预算；纹理删除会关闭对应 Skia Image，但尺寸表要整体清空才移除。[SkijaImages.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/render/skija/SkijaImages.java:50)、[invalidate](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/render/skija/SkijaImages.java:158)

AtomLib 的图片基础层更适合移植：

- `ImageStore` 默认最多 128 个条目，替换和淘汰时释放旧原生 Image，并用 generation ticket 拒绝过期异步结果。[ImageStore.java](/D:/AtomLib/runtime/src/main/java/com/atom/lib/image/ImageStore.java:17)、[接受结果](/D:/AtomLib/runtime/src/main/java/com/atom/lib/image/ImageStore.java:39)
- 显示时可直接计算目标尺寸，也可走降采样版本。[ImageStore.java](/D:/AtomLib/runtime/src/main/java/com/atom/lib/image/ImageStore.java:71)
- `ImageDownsampler` 同时限制像素预算和条目预算，逐步减半缩放，避免单次大倍率线性采样。[ImageDownsampler.java](/D:/AtomLib/runtime/src/main/java/com/atom/lib/image/ImageDownsampler.java:21)、[预算淘汰](/D:/AtomLib/runtime/src/main/java/com/atom/lib/image/ImageDownsampler.java:62)

## 可移植到 AtomChat

1. **优先接入 AtomLib `TextService` 和 Yoga 快路径。** 当前文字测量与布局的部分成果已经在 AtomLib 里，移植成本主要在 RichText 分段和 caret 映射。缓存键必须包含字体 generation、字号、样式、可用宽度、换行策略和语言方向。
2. **移植“按显示尺寸降采样”和像素预算。** AtomLib 的 `ImageDownsampler` 可直接处理头像、表情、图片消息和小封面。Muonium 的动态封面预算是很好的需求参考，但其“先完整解码再缩小”的峰值内存模型不适合直接照搬。
3. **可借鉴 Muonium 的分代模糊缓存。** AtomChat 已使用面板尺寸 FBO，适合加入“按采集代数失效、最多 N 档 sigma、每代重建预算、旧档年龄上限”。这能改善频繁切换半径时的互相顶替。
4. **可借鉴 Muonium 的滚动虚拟化。** 固定高度消息行、搜索行和设置列表可以只更新视口附近项；非等高消息仍计算一次布局签名，并在消息内容、贴图达到、字体换代、输入栏高度变化时显式失效。
5. **保留 AtomLib 的借用所有权。** 缓存 GL 纹理的 Skia 视图可以跨帧存在，但只能借用纹理；GL 上下文重建、纹理 id 重用和尺寸变化必须显式关闭或失效。AtomLib 的 Surface 重建规则比持有快照更安全。[SkijaRenderBridge.java，AtomLib HEAD](/D:/AtomLib/platforms/shared/src/main/java/com/atom/lib/client/render/SkijaRenderBridge.java:120)

## 不建议直接移植的部分

- **整页 Skia 离屏缓存**没有现成实现。Muonium 复用 Surface，但每帧清空重画；AtomChat `HEAD` 也没有生产路径 `PictureRecorder/drawPicture`。若要新增，必须定义消息、动画、悬停、头像异步、未读徽标、输入框高度、主题和字体变化的失效键，否则会留下旧消息、旧贴图或错误滚动状态。
- **全屏离屏合成**是 Muonium 为了隔离和合成层采用的方案，会增加一次全屏绘制与贴回。AtomChat 当前直接画宿主 FBO，移植前应以面板区域 A/B 测量证明收益。
- **Muonium 的借用旧模糊档**会用最多落后 8 个采集代的背景。对聊天界面，人物头像、文字层或快速开合时可能出现可察觉残影，需要截图或录屏验收。
- **Muonium 的逐字符文字路径**不适合作为 AtomChat 新架构。它没有复杂文字塑形，补充平面字符、组合字符、阿拉伯文和双向文本的正确性不能据此保证。[CFontRenderer.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/font/CFontRenderer.java:493)
- **跨 GL 上下文缓存 Skia Surface/Image/FBO**风险高。Muonium 在切换或关闭时有显式失效，但静态搜索没有找到 `SkijaHost.shutdown()` 调用点；AtomChat 必须依赖 AtomLib 的显式 invalidation，而不是 GC 回收。

## 待验证候选

以下是有代码路径支撑、但尚未被运行时或失败样本确认的事项：

- Muonium 静态封面缓存长期增长；本地封面未按显示尺寸缩小；上传队列积压无字节上限。
- 下载上传任务若上传阶段抛异常，`PENDING_DOWNLOADS.remove()` 在成功回调之后执行，异常时可能不会移除该地址，导致该封面后续被当作“下载中”跳过。[Textures.java](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/texture/Textures.java:89)、[finally](/D:/MuoniumPlayer-multiversion/muonium-common/src/main/java/com/muoniumplayer/core/rendering/texture/Textures.java:141)
- `SkijaImages.invalidate()` 删除 Image 视图，但没有删除 `SIZES` 中的尺寸记录；长期创建和删除不同纹理 id 时会留下元数据。
- 模糊旧档借用、全屏采集频率、面板 Kawase 五遍、封面 bloom `saveLayer` 各自的真实帧耗和视觉影响，均未进行游戏内测量。
- AtomChat/AtomLib 的未提交 WIP 中包含额外 TextBlob、GL 快照和模糊对象复用改动；本次未把它们当作现役结论。

本次没有依据源码注释里的历史毫秒数或 AtomLib 未跟踪的性能文档宣称 FPS 改善；性能收益仍需在相同窗口、缩放、光影和内容密度下实测。
