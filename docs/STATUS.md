# AtomChat 当前状态

> 截至 2026-11-01。这里只维护当前快照，提交及推送事实以 Git 查询为准。

## 当前检查点：0.4.0 不变，AtomLib 独立仓库链路落地（2026-11-01）

- AC 版本仍为 `0.4.0`，本轮零功能改动；变化全部在构建链与字体资源。
- AtomLib 已拆到独立公开仓库 [NoWordz/AtomLib](https://github.com/NoWordz/AtomLib)，自有 CI 六 job 全绿（含 Xvfb 真实 GL 测试）；AC 的输入改为从 Lib release 固定包拉取，URL 与 zip sha256 落在 `versions/atomlib.json` 的 `input_archive`（单一事实源），`fetch_atomlib_input.py` 无参即取，build/release 三处 secret 硬门退役，旧 secret 已删除。
- 当前钉 `v0.1.0-beta-beta3`（zip sha256 `51a95d0e…`，commit `5a523636`），AC CI 三平台绿（run 41218706477）。
- 三平台 `bundled.otf/bundled-bold.otf` 已换成 Lib beta3 默认字体包（同源 Noto Sans SC 变量字体重新子集化，7671 码点；与旧文件共有 7549 码点零漂移，wght=500 instancing 亦零漂移）。`FontManager` 加载路径与 `makeClone(wght,500)` 未动；新增字形（Latin-1 补充区、颜文字区）只有增益。
- 顺带修复的 Lib 侧真雷：Yoga 绑定按 3.3.1 编译但 fabric/neoforge 内嵌 3.3.3（resize 即 `NoSuchMethodError`），已按目标编译桥修复；文本 advance 取整漂移已由 subpixel 修复归零。
- 新增 `SettingsHomeReplicationTest`（测量夹具，非 JUnit 门）：21 项检查 4 项失败，全部为已文档化的 1u 网格圆整差异，与 Lib 证据基线逐字节相同；证据见 Lib `docs/evidence/settings-home-replication/`。
- 仍未覆盖：ARM64 实机；AC CI 只跑 Linux；专服启动未验证；Lib 的 Maven host 选型（D）与双消费者共存（E）未启动。

## 上一检查点：0.4.0 保持原观感，AtomLib 拆出设计包（2026-10-31）

- AC 停在 `0.4.0`，外观页回到拆分前基线；1.0.0-beta 那版把 UI 换成 Lib 通用组件的试点已整体回退，相关类与测试删除。
- “长得像 AC”的部分已抽成 Lib 的可选设计包 `com.atom.lib.design.atomchat`（`UiTokens`、`SkiaDraw`、`UiCards`、`AppIcons`）。AC 只改 import，颜色/圆角/卡片透明度仍读 `AtomChatConfig`，通过 Lib 的 `DesignHost` 注入，取值与默认值和拆分前一致。
- Lib 的通用组件、浮层、壳层、滚动、交互、视图宿主、样式与 devtools 已整体删除；`ui` 只剩 Yoga 布局与设计包。
- Lib 版本改为 `0.1.0-beta`，补齐作者、Apache-2.0、description、logo 占位与仓库地址占位（`https://github.com/example/atomlib`，待替换）。
- 打包缺陷已修：Lib 的 jar 以前缺 `pack.mcmeta`，Forge 不挂载它的资源，反馈音效一直是哑的。
- CI：main 已全绿（run 41171032455）。修复了两处只在本机绿的问题——Linux 上 CJK 回退字体多折一行导致确认窗测试失败；`tools/test_verify_jars.py` 写死 `0.1.0-local`。AtomLib 输入改用 pre-release `atomlib-input-0.1.0-beta` 做固定包，secret 已配置。
- 仍未覆盖：远端 CI 只跑过 Linux；ARM64 未实机；专服启动未验证；Lib 尚未有独立仓库与独立 CI，输入包暂时挂在 AtomChat 仓库的 release 上。

## 当前交付：0.4.0 续开发已验收

- 2026-10-29 用户确认本轮验收通过，授权提交、推送 main 并更新项目状态
- 范围包含输入栏工具菜单与当前页搜索、主题与点击反馈、确认／取色弹窗族群、原版指令补全外观及兼容修正、Emoji 字形缓存、设置分组图标、默认关闭的实验性 SkSL 特效及 Iris／Bliss 世界黑屏修复
- 本轮验收包含最新光影黑屏修复；下文各阶段的“待复测／未提交推送”是当时快照，已由本条验收及后续 Git 事实取代
- 最终三端完整 test build 为 Forge 1551／Fabric 1563／NeoForge 1551，失败／错误 0，各 4 项既有跳过；三 Windows x64 实例安装包保持最新部署回执
- 版本保持 0.4.0，提交分支 main、推送目标 origin/main；提交号和远端同步以 Git 查询为准，不打 tag 或公开发布
- 验收不扩大为其他 GPU、ARM64／Linux 实机或首播无卡顿保证；冷编译限制保留，临时证据与部署备份保留

## 最新：Iris／Bliss 下实验特效世界黑屏修复

- 实际录像与光影源文件定位：Bliss final只写RGB，alpha并非透明coverage；原全屏特效当作premul图片处理，导致世界变黑、UI仍可见
- 三个产品文件限定修复：新增明确显示帧入口，按已合成RGB处理并输出alpha1；普通透明surface保留原premul规则，不改SkiaGraphics或光影配置
- rawGL旧版可复现alpha0全黑；新实际会话入口四模式／四时刻／四alpha通过，另两分辨率15个UI边界及屏幕边缘点一致。限定独审未发现确认P1/P2；Iris+Bliss游戏换包后已获用户验收
- 完整三端1551/1563/1551零失败／错误，各4既有skip；Windows x64三实例已备份部署（ac7b36a2d301a837/15fbd8bfe9a9885f/d86f3291082aed81），其他163/73/201文件hash／大小／mtime不变。未启停游戏／改配置玩家数据／提交推送发布，ARM/Linux未更新
- 新回执：[光影黑屏修复](verification/2026-10-29-experimental-iris.md)，对应packages／deployment JSON为最新；旧shader冷编译限制未宣称解决

## 最新：实验性 SkSL 特效（默认关闭）

- 前序 UI／图标已获用户验收；新增“实验性内容”折叠组位于趣味特效下，仅一个默认关闭开关，标题为遵循统一路径／线宽／尺寸的原子星芒图标
- 手动启用一次三秒全屏星云引力冲击波，之后当前可见聊天关键词触发水波、冰霜、黑洞；独立于旧 Emoji 开关并服从全局动画，重进与 HUD 编辑器返回不重播
- 独立初审三项P2（重装已关闭对象、预乘alpha、重复合成）已补红回归修复，限定复查通过，另120组原生像素检查通过；本轮不扩大为此前整个版本独审
- 三端完整 test build：1550/1562/1550，失败／错误0，各4旧跳过。真实GL两分辨率640帧通过，1080p串行预热p95约0.175～0.444ms；冷编译并发构建时曾156.887ms，串行新进程12.873ms，不当作游戏整帧或声称首播无卡顿
- 三Windows实例已备份部署（e6c96e68932803d8/ffc917f1b7c1613d/150593c92e116a92），其他163/73/201文件hash／大小／mtime不变；未启停游戏或改玩家数据／配置，未提交推送发布，ARM/Linux未更新
- 记录：[实验性特效](verification/2026-10-29-experimental-effects.md)，含真实截图、帧耗与产物／部署回执；游戏观感、首播和其他GPU仍待用户验收

## 最新：设置副标题逐项分配图标

- 修正atomchat命名空间被contains(chat)误归类为聊天气泡，26个实际副标题改为完整key映射，复用9个图标、新绘17个，只有气泡样式保留气泡
- 新路径沿用原DrawPrimitives居中／尺寸／线宽约束，深浅背景多尺寸真实渲染已查看；逐项语义表见 [图标记录](verification/2026-10-29-settings-icons.md)
- 完整测试1541/1553/1541，0失败／错误、各4跳过，三Windows实例已备份部署（6b9063457bc5faec/a93c7e2a3f1ebc72/4ef012bf9f34d529），其他163/73/201文件不变。未启停游戏／提交／推送／发布，该阶段图标已获用户验收

## 最新：确认窗跟随取色弹窗，卡片按压恢复，分组标题图标

- 按用户grill定案，固定深色取色模态材质通过同一个 `PickerModalPaint` 绘制，文字按钮保留且始终取消左／确认右（含窄窗），取消无强调色边框，确认危险红，标题红色三角感叹号，正文两行短句无句号
- 打开确认前清理底层pointer捕获，修正关窗后卡片一直按住、无法悬停回弹；设置页所有折叠标题复用已有图标并留出统一文字列
- 三端完整测试 1537/1549/1537，失败／错误0、各4跳过；独审：独立初审发现1项P2：窄窗正文孤字，已补真实中英文窄宽回归并修复；其他本轮指定范围未发现明确P1/P2。修复后的限定复查因服务429未完成，不能声称最终独审通过，审查不覆盖此前全部0.4.0改动
- Windows x64三个既定实例已备份部署（46562cb6289dd110/341a73ca7e9eb3b4/bb63475bd502efa5），其他163/73/201文件不变；未启停游戏或改个人数据，未提交推送发布。实机观感／回弹仍待用户确认，Emoji性能不在本轮验收范围
- 当前记录：[2026-10-29确认族群](verification/2026-10-29-confirmation-family.md)，下文菜单修正与更早候选为历史阶段

## 最新：紧凑菜单族群修正已部署

- 用户四图确认的参照是右键紧凑菜单，＋／补全已改为完整复用该族群的外框、高亮及回弹余量，选择器美术入口独立命名；补全与实际输入栏／＋菜单左侧对齐，含半屏。常用语的宽内容内边距保持。
- 新渲染回归先红后绿，覆盖窄宽、首末行、悬停进退及长候选限宽；独立审查两项P2已补失败回归并修复。三端完整测试 1530/1542/1530，失败／错误0、各4跳过。
- 三Windows x64已备份部署（0d401fed3b9c810c/a616d48af0634e8d/88b523679cb65550），详见 [当前证据](verification/2026-10-28-ui-corrections.md)。未启停游戏、提交推送发布；当前菜单实机观感／Emoji性能待用户复测。两项修正后的独立限定复查因429未完成，不称最终独审通过。

## 0.4.0：UI 续修已部署，待 CCB 实机复测

- 基线 `main@93f58a1`，版本保持 0.4.0，未提交／推送／发布。
- 初版工具页和确认窗口被拒收；已按最新决定改为原浮层内玩家列表、真实输入框借用搜索、主题确认弹窗，并补齐开关、hover、按压反馈。
- 搜索范围为当前页及当前筛选，包含折叠记录；恢复草稿和光标，阻断发送与拖图，隔离在途上传回写。
- 三类输入栏浮层统一实际间距、图标尺寸、连接尾迹和动画变换；补全沿用原版解析与候选应用，常用语编辑期间也可显示，保留原版格式及灰色尾缀。
- 最终完整 `test build`：Forge 1526／Fabric 1538／NeoForge 1526，失败与错误均 0，各 4 项跳过；Windows x64 新包已备份部署三实例，源码指纹及安装哈希见 [返工证据](verification/2026-10-28-ui-corrections.md)。ARM64／Linux 未更新。
- CCB 带状态 JFR 捕获到 Emoji 开关慢帧，独立动态缩放 GL 探针复现字形生成／提交开销；共享 Emoji 字形缓存已通过像素、容量与缩放回归。最终新包在 CCB 的开关、稳定帧率和观感仍待用户实测。
- 早期独立审查问题已修；本轮最新补全／缓存修改的独立复审因 429 未完成，不能当作审查通过。代理未启动或关闭游戏，未提交／推送／发布。

## 0.4.0：高清皮肤读回修复（10 月 25 日基线）

- 2026-10-25 快照：版本由 0.4.0-preview 收口为 0.4.0，基线 main@e8a6aca4f0d750daf285747b9f3ac47972a9a735。源码、测试、九包和三个 Windows x64 实例部署已完成；用户已要求提交、推送并清理临时文件，交付分支为 main、推送目标为 origin/main，实际同步事实以 Git 查询为准，不打 tag 或公开发布。
- 证据指向旧头像读取：固定分配 64x64 RGBA 客户端缓冲，却按 GL 实际纹理尺寸写回；实际实例有 1024x512 皮肤，脱敏 dbgeng 栈在 RtlFreeHeap 上层出现 Skija Bitmap installPixels/finalizer。字体、Skija 版本、驱动、Java 与启动器均未修改，也不声称全部原生崩溃已解决。
- 共享 SkinTextureReader 现按实际宽高分配，接受 64 整数倍且不超过 4096 的正方形或 legacy 2:1 纹理并归一为 64x64；读取期间隔离并恢复 PACK alignment/rowlength/skips、PBO 和纹理绑定。两份 AvatarRenderer 已统一接入，裁脸、帽层和颜色处理未变。
- 新增 Windows 隐藏 GLFW 真实读回回归，覆盖 64x64、64x32、128x128、1024x512 的脸/帽层/alpha、畸形几何、污染 PACK/PBO、纹理绑定与 GL error；Linux 无显示环境明确跳过。静态守卫扫描全仓，产品源码只有读取器直接调用 glGetTexImage。
- 旧接线变异已实际变红，随后按字节恢复，AvatarRenderer 源文件 SHA-256 前后均为 5447c4ec19915a821dbd4ab54d2ffc8899cd0d2ce2041eb8323811b24dacc2da。
- 三端完整 test build 使用 JDK 17/21 和离线参数：Forge 1481、Fabric 1493、NeoForge 1481 项，失败/错误 0，各 2 项既有 golden/benchmark 跳过；真实 GL 子套件在三端各 3 项均执行。Windows ARM64/Linux x64 以 build -x test 生成剩余六包。
- 九包通过 native 唯一性、PE/ELF 架构、Skija 0.143.18、FlatLaf/types 登记、版本、资源与重定位校验；同加载器三 OS 的 612–628 个应用 class/assets 条目逐字节一致，均无 JUnit、测试 agent 或本轮探针类。
- 静态门禁：seams 139/71、目标矩阵通过、孪生 75 对未归类/陈旧 0、日志 269 条违规 0、裸值 469/505、git diff --check 通过。
- 已备份并部署 Forge 1.20.1-main、Fabric 1.21.1-CCB、NeoForge Mechanomania-航空学：最终 SHA-256 分别 4ce2836e…、43ac102d…、8c6117db…；旧包分别 8765ebfc…、f1ebe063…、119d47f3…。其他顶层文件 157/73/201 个的哈希、大小、mtime 未变，各只有一个活动 AtomChat 元数据；未启动或关闭游戏。Forge 最终包重装前的 878a3787… 也单独备份。
- CHANGELOG 与 RELEASE_NOTES 的 v0.4.0 段已同步横幅卡片材质、50% 最低透明度、局部模糊和全局 zoom/slide 事实，并新增高清皮肤闪退修复，中英双语一致。
- 香草纪元：食旅纪行 的 1024x512 实机场景尚未复测；隐藏 GL 测试不等同于整局游戏验收。详见 [验证记录](verification/2026-10-25-0.4.0.md)、[包指纹](verification/2026-10-25-0.4.0-packages.json)、[部署记录](verification/2026-10-25-0.4.0-deployment.json)。
- 本轮 `.work/` 中的 1300 个临时文件（180233348 字节，含测试生成图、XML、过程日志和九包副本）已逐文件校验归档至 `D:/AtomChat-Backups/2026-10-25-0.4.0/closeout-20261025-160835/`；按用户要求在推送后移入回收站，执行结果见归档目录的 `recycle-receipt.json`。各平台 build 产物、安装包、部署备份和长期验证记录保留。

## 0.4.0-preview：横幅专项已验收

- 用户于 2026-10-23 确认本轮验收通过，要求提交、推送、更新状态并将临时文件移入回收站。版本保持 `0.4.0-preview`，交付分支 `main`，推送目标 `origin/main`；提交号和远端同步事实以 Git 查询为准，不打 tag 或公开发布。
- HUD、面板与编辑器横幅复用卡片族材质，继承卡片 RGB/透明度，底色最低 50%，局部背景模糊；不透明底色跳过无效滤镜。
- 入退场跟随全局 zoom/slide，旧横幅始终滑动让位；保留通知出生时间，兼容面板开关和 HUD 九宫格。修复重叠误触旧回复按钮、锚点跳位及 dismiss 后切 HUD 暂时消失。
- 三端完整 `test build`：Forge/NeoForge 各 1476 项，Fabric 1488 项，失败/错误均为 0，各有 2 项原有跳过。Forge 新 JVM 事件加载顺序回归及真实 RTX 5060 局部模糊探针通过；独立初审两项 P2 已补失败回归并修复，独立复审因 429 未完成。
- 三个 Windows x64 包已备份并部署到 `1.20.1-main`、`1.21.1-CCB`、`Mechanomania-航空学`；其他顶层 mods 文件分别为 157/73/200 个，哈希、大小和修改时间均未变。本轮未重建 ARM64/Linux 包。
- 用户确认崩溃来自旧版 0.3.2 报告：直接崩溃点为 JVM C1 编译线程，字体日志不能证明因果；没有修改当前字体代码或 Java 配置，也不声称修复了 JVM。
- 详见 [横幅验证与验收](verification/2026-10-23-notification-banners.md)、[包指纹](verification/2026-10-23-notification-banners-packages.json)、[安装记录](verification/2026-10-23-notification-banners-deployment.json)。本轮临时证据收工时归档到 `D:/AtomChat-Backups/2026-10-23-notification-banners/closeout-evidence/` 后移入回收站；部署备份与长期视觉证据保留。用户整体验收不扩大为多人专项、不同 GPU 整合包帧耗或 ARM64/Linux 实机验收。

## 历史基线：0.4.0-preview 实时模糊与四组目录

- HUD 与面板横幅保留原有不透明浮动通知材质、下落动画和共享队列；仅修复跳转图标的强调色对比色。
- 面板背景模糊改为每帧刷新；步进箭头 hover 使用全局渐变。
- `atomchat/` 顶层收为 `client/`、`cache/`、`server/`、`backups/`；客户端个人目录首次启动创建，旧目录自动校验迁移。
- Forge/NeoForge 各 1467 项、Fabric 1479 项测试，0 失败/错误，各 2 原有跳过；九包完成构建与一致性校验。
- 已备份并部署三端 Windows x64，其他 193/432/215 个文件未变。改动已提交到 `main`；未推送或公开发布，部署代理未启动或关闭游戏。
- 见 [本轮验证](verification/2026-10-23-0.4.0-preview.md)、[包指纹](verification/2026-10-23-0.4.0-preview-packages.json)、[安装记录](verification/2026-10-23-0.4.0-preview-deployment.json)。

## 0.3.3 兼容修复与个人数据迁移

- 已按 Forge 实测反馈修复 Quark 入场延迟显示、外部模组自己的物品分享被错误丢弃、FTB Teams 嵌套系统通知误识别；root/detail/chip 动画期间锁定指针，并在释放时纯取消按下态，作者信息使用折叠点阵标题。
- 用户批准个人数据根目录为与 config 同级的 `atomchat/`。常用语、签名、屏蔽名单拆至 `client-data.json`，素材/历史/预设/缓存/诊断图及旧 atomchat-data 迁入同根；校验备份后退休旧副本，冲突/失败提示、原文件保留、备份去重、拒绝链接/NTFS junction。服务端只分发明确的 server-emotes。
- 最终三端 `test build --offline --no-daemon`：Forge/NeoForge 各 1435 项，Fabric 1447 项，0 失败/错误，各 2 项原有跳过；Windows junction 隔离回归实际通过。独立审查发现 5 项 P2，已补回归并修正；后续独立复核因 429 重试耗尽未完成，不声称二次复核通过。
- 三加载器 × Windows x64 / Windows ARM64 / Linux x64 九包通过 native/版本/资源、测试代码未入包及同加载器应用 class/assets 一致性校验；构建临时目录已在收尾时移入回收站，见 [指纹](verification/2026-10-23-mod-compatibility-packages.json) 和 [验证记录](verification/2026-10-22-mod-compatibility.md)。
- 已依用户自动部署授权替换 `D:/Myworld/.minecraft/versions/1.20.1-main/mods/atomchat-Forge-1.20.1-0.3.3-windows-x64.jar`，SHA-256 `a3acee0b1a0d8f49166210b0675a95fd25188db0a35e482e3ecf43fbbc11555e`；旧包备份为 `D:/AtomChat-Backups/2026-10-23-forge-compat/20261023-004808-462887/atomchat-Forge-1.20.1-0.3.3-windows-x64.jar`（旧 SHA-256 `6213407c68082b065ee65818fff09fcc06c779fd074ebdbd137608ee7852d258`），其他 193 文件哈希/大小/修改时间不变，modId=atomchat 只有一份。
- 代理未启动游戏、未修改玩家图片或聊天记录；迁移在下次启动新版时发生。Quark/FTB 共装实机与 GPU 验收仍待用户复测。该轮记录形成时未提交、推送或发布，相关源码已随 0.4.0-preview 提交归档；2026-10-22 初始候选和 0.3.2 基线材料不代表当前续修已实机验收。

## Skija / Windows ARM64 迁移

- 用户授权尝试升级以支持 Windows ARM64；Skija 现钉 0.143.18（仓库根单一定义），types 0.2.0，模组版本仍为 0.3.2。已适配字体、PathBuilder、ColorType 与 GPU flush 接口。
- 三加载器 × Windows x64 / Windows ARM64 / Linux x64 九包均构建，文件名统一以系统与架构结尾；完整测试 NeoForge 1377 / Fabric 1389 / Forge 1377，均 0 失败、0 错误，各 2 原有跳过。
- 九包 native/登记/版本/资源与同加载器应用内容一致性通过，Windows/WSL Linux x64 native CPU 冒烟通过；独立审查四项 P2 已修复并复查通过。详见 [迁移验证](verification/2026-10-21-skija-windows-arm64.md) 与 [包指纹](verification/2026-10-21-skija-packages.json)。
- 候选包集中于 `.work/skija-upgrade/dist/`。用户已反馈所部署 Windows x64 候选渲染正常，并授权三加载器改动提交、推送。该反馈不代表 Windows ARM64 实机、Linux 新版 GPU、其他加载器运行或量化性能已逐项验收。
- 已按用户授权部署到 Windows `1.21.1-CCB` Fabric 实例：`atomchat-Fabric-1.21.1-0.3.2-windows-x64.jar`，SHA-256 `5bcd0e33cc13dd06e4ebd3c97114ff5b3a5e374d12aa39634b7f7a4d0c238974`。旧包已备份，其他 73 个文件哈希/大小/修改时间不变，只有一个 AtomChat；由用户启动并完成上述 x64 渲染验收。
- 本轮仅提交、推送源码，不打 tag 或发布；具体完成状态以 Git 查询为准。下文旧六包与旧 CCB 哈希是升级前已验收基线。商店正文与正式发布说明未同步此次候选。

## 项目与验收

- 仓库 `D:/AtomChat-Main`，分支 `main`，远端 `origin` 为 `NoWordz/AtomChat-Mod`；三端为 Fabric 1.21.1、NeoForge 1.21.1、Forge 1.20.1，源码版本为 0.4.0，下文旧版本记录为历史基线。
- 用户已确认图片预览/过滤、半屏与面板外关闭均验收通过，授权提交、推送和清理旧轮次材料；本次不打 tag 或发布。
- 图片预览支持平滑淡入、鼠标锚点滚轮缩放与左键拖动；三种弹层统一裁切，保存预设与默认主题单一选中，公屏系统/玩家过滤持久化。见 [预览与过滤验证](verification/2026-10-20-preview-and-filter.md)。
- 半屏模式位于外观→调整→布局，左、上、下贴边，顶底栏贴边并去掉手机外环。原宽度滑条分别保存手机/半屏宽度，切换及重载后恢复；内部卡片、气泡和主题保留。
- 左键点击面板外世界画面正常关闭并消费事件；拖动移出面板不关闭。伸出面板的原生命令补全框仍可点击，弹层打开时不激活底层补全。
- 半屏应用个人预设不覆盖手机宽度；手机应用预设保留原宽度行为。详情见 [半屏方案](superpowers/plans/2026-10-20-half-screen.md) 和 [半屏验证](verification/2026-10-21-half-screen.md)。

## 最终验证与部署

- 三端完整 `test build --offline --no-daemon`：NeoForge 1375 / Fabric 1387 / Forge 1375，均 0 失败、0 错误，各 2 项原有 golden/benchmark 跳过；独立审查发现的两项 P2 已补失败回归并修复，复查无阻断。
- Windows/Linux 六包已构建；对应 native、版本、资源及同加载器 class/assets 一致性通过。收工前再次核验包内容、CCB 安装包和旧包备份哈希；没有重复游戏测试或性能测量。
- seams 125/71、targets、孪生 70 对/未归类 0/陈旧 0、日志 262/违规 0、bare 471/505 及 `git diff --check` 通过。
- CCB 安装：`D:/Myworld/.minecraft/versions/1.21.1-CCB/mods/atomchat-Fabric-1.21.1-0.3.2.jar`，SHA-256 `a3c59c8baa0cac9c41a6c11ae24eddd82f4cce1020f66c79f39b21c71c01e244`。
- 原已验收包 `43ae7c6a…` 保留在 `D:/AtomChat-Backups/2026-10-21-half-screen-CCB/111741-540159/`；部署时其他 73 个文件未改动，代理未启动游戏，随后用户反馈验收通过。

## 仍有效的约束与待办

- shared 为公共逻辑；official/Fabric 同名源码按映射等价维护，修改公共面须跑 seams、targets 与孪生检查。
- 玩家档案以服务端认证身份授权，按世界保存；头像图片先持久化再发布元数据。保留 [同步约束](superpowers/plans/2026-10-11-profile-sync.md)、[同步验证](verification/2026-10-12-profile-sync.md) 和 API 探针。
- 消息反应的实际内容键机制与边界以 [反应验证](verification/2026-10-12-reactions.md) 为准；过期的指纹/回执实施方案已清除。
- 多人同步、重连、延迟队列及 GPU 量化性能尚无逐项实机证据；[媒体性能审计](superpowers/plans/2026-10-12-media-performance-audit.md) 中两项真实传输/剖析仍未完成，不将整体验收当作性能测量。
- 延期：反应悬停名单超过 6 人仍只显示剩余人数。WSLg 性能及鼠标锁定未定因，已解决的字体/音效不在本轮重开。

## 资料与清理

- 长期设计见 [design.md](design.md)，玩家/服主/开发者手册见 `docs/wiki/`，商店正文仍由现有工作流引用，保留。
- 已完成旧轮次方案、过期验证图、历史 STATUS、旧审计及散落构建日志按用户要求移入回收站；当前两轮 0.3.2 方案/验收记录、有效协议与未完成审计保留。
- 临时证据已按逐文件哈希归档到 `D:/AtomChat-Backups/2026-10-21-closeout/`，历史 `.work` 路径从归档查阅；候选六包、已安装包和旧包备份保留。
- 本轮 `.work/` 构建与部署临时目录、`logs/` 运行日志已移入回收站；长期验证文档、视觉证据、包指纹和部署记录保留在仓库。
