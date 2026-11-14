# AtomChat 当前状态

## 2026-11-14 23:19 0.7.1 验收通过，已推送，CI 全绿

- 用户已明确验收通过，授权提交、推送、临时文件移入回收站及 README/项目状态更新。上述验收不等于已复现并解决所有历史多人服偶发问题。
- README 已整理安装入口、功能与通知说明、布局和配置表格，移除过时的主题数量、固定第三方保留时长和冲突的平台支持描述。
- 沿用21:56交付前的集中独审和测试；本轮先完成文档收口，随后修正CI暴露的确认框换行缺陷。最终本地Fabric14项专项+build、Forge/Neo各1967项全量+build通过（各9跳过），未把此前结果冒充新跑。
- 两批共93个临时文件（4,932,323字节）先归档校验再移入Windows回收站；最终JAR、AtomLib构建输入、截图及长期验证记录保留。测试重新生成的15张历史PNG已另行归档，并恢复提交中的原始证据字节。无关UI调研文档保留未提交。
- `b0514f60`（0.7.1主体及README）、`4a3395f1`（确认框修复）已推送 `origin/main`（`NoWordz/AtomChat-Mod`），此前已验收的 `20df2ab8` 一并推送。未打标签、创建Release或发布商店。
- [最终CI](https://github.com/NoWordz/AtomChat-Mod/actions/runs/43608399703)全部通过：矩阵与三端测试/构建、干净构建戳和产物内容检查均成功，三份AC+AtomLib配对产物已上传。这里只证明CI产物与默认测试，不扩大为所有整合包实机验收。
- 确认框修复包已备份同步三个测试实例并独立回读，未改AtomLib或配置。记录见[CI修复部署](evidence/2026-11-14-v071-audit-fixes/verification/ci-fix-deployment.json)与[回读](evidence/2026-11-14-v071-audit-fixes/verification/ci-fix-readback.json)。

### CI 跟进

- `b0514f60` 已推送，连同此前已验收的 `20df2ab8`；[首次CI](https://github.com/NoWordz/AtomChat-Mod/actions/runs/43606084185)矩阵通过，三端各4项确认框渲染测试失败，已进入实际测试，与上一轮软件源DNS失败不同。
- 根因是 `ConfirmationText.wrap` 为平衡单字中文末行而修改渲染器返回的不可变列表；Linux字体度量触发该分支。现在只复制为可变列表再调整，仍保留共享缓存的只读契约。
- 新增按实际字宽构造的回归，在Windows也先得到相同 `UnsupportedOperationException`，再修复通过；同时验证缓存内容未变，不跳过原失败用例。局部自审确认其他换行消费者只读，不扩大改渲染器缓存契约。
- Forge首次单独选择UI测试未初始化原版注册表；补选另一个初始化用例仍未改变实际执行顺序，因此改跑完整套件，通过后才推送。两次局部夹具失败没有当作产品修复失败或测试通过。
- [本地增量/全量范围](evidence/2026-11-14-v071-audit-fixes/verification/ci-fix-local-tests.json)、[首次失败回执](evidence/2026-11-14-v071-audit-fixes/verification/ci-first-run.json)、[最终成功回执](evidence/2026-11-14-v071-audit-fixes/verification/ci-final-run.json)、[首批回收](evidence/2026-11-14-v071-audit-fixes/verification/cleanup.json)、[CI临时文件回收](evidence/2026-11-14-v071-audit-fixes/verification/ci-cleanup.json)。

## 2026-11-14 21:56 审计建议落实并部署

- 已落实上传连接/原草稿隔离、取消与队列/分片限额、失败与容量反馈，主题暖绘制不查磁盘、未变配置不重写、后台有界历史和图片缓存维护。
- 集中独审3项P2均修正；随后只复核受影响边界，最后补齐文件检查前固定epoch、客户端线程原子准入，限定范围无未解决P1/P2。
- 三端完整test/build为Fabric1978/Forge1963/Neo1963，0失败各9skip（7旧+2显式测量）；之后每端63项上传/历史专项+build通过且无skip。CPU/GPU显式测量通过，数据未支持额外改动画算法/画质。
- 21:56仅更新三个测试实例AtomChat，旧包备份 `D:/AtomChat-Backups/2026-11-14-v0.7.1-audit-fixes/20261114-215645-954/`，独立回读通过。AtomLib/配置未动，未启动游戏/提交发布。
- 输入失效、薄暮锁定与白屏未实机定位；已补有界debug诊断，不能把本次确定修复称为四症状共同根因。真实HTTP取消、MC整帧FPS与慢盘单文件删除延迟未验。
- [最终验证与部署](verification/2026-11-14-v071-audit-fixes.md)、[首轮审计](audits/2026-11-14-v071-performance-multiplayer.md)、[独审及限定复核](audits/2026-11-14-v071-fixes-review.md)。两个代理均已关闭。

## 2026-11-14 18:53 0.7.1 复测续作

- 描边改为柔和反光，默认黑边不再大幅提亮；原色、宽度与轮廓保持。原生默认/深/浅主题预览已检查。
- 高度上限已按窗口/界面缩放动态计算，非固定834；1080p以上设计像素上限可能相同，物理高度仍随窗口变化。新增36组缩放组合核对通过。
- 未发现现役G2曲率实现；普通RRect、模糊圆角距离场和旧cornerStyle配置迁移保留。标题点阵已走共享缓存，真实页稳定30帧无需重建；小标题Picture缓存与大面积图片缓存不混称。
- 三端串行完整test/build：Fabric1908/Forge1893/Neo1893，0失败/错误，各7旧skip；targets、JAR、资源、bare433、diff通过。前轮孪生历史台账未全绿状态保持披露。
- 已仅更新三实例AtomChat并独立回读，备份 `D:/AtomChat-Backups/2026-11-14-v0.7.1-followup/20261114-185304-507/`；未启动游戏/提交/发布。
- 用户指定gpt-6-astra high只读审计已完成：优先修上传连接/草稿生命周期及静默失败，再处理渲染热路径同步I/O；动画/模糊先测量。输入失效、薄暮锁定、白屏未定位，专项生产修复暂不实施。[审计报告](audits/2026-11-14-v071-performance-multiplayer.md)
- 详见[续作验证](verification/2026-11-14-v071-ui-regressions.md#1853-复测续作描边高度圆角与标题点阵)。

## 2026-11-14 0.7.1 UI 回归修复

- 菜单族恢复原面内明暗与柔影；设置内部 tab 接入全局可打断动画；浮窗按钮补反馈音；描边渐变、列表右影裁剪、中点文案、作者/UUID 悬停及 UUID 复制按压已实现。
- 默认面板按实际参考比例适配视口，宽高显示设计像素 px；旧布局保留，新安装/恢复调整默认才应用；宽高自动状态独立，独审发现的半屏预设高度覆盖 P2 已修复并有限复核。
- 三端串行 test build：Fabric1905/Forge1890/Neo1890，0失败/错误，各7旧skip；成品、资源、targets、bare、diff通过。额外孪生台账扫描仍有16未归类/1陈旧，未冒充全绿。
- 17:32已仅替换三实例AtomChat，旧包备份 `D:/AtomChat-Backups/2026-11-14-v0.7.1/20261114-173241-155/`，独立回读通过。未启动游戏/提交/推送/发布；实机观感及听感待复测。
- 用户第7项性能专项、第8项多人偶发无法输入/图片、薄暮锁定、白屏/不渲染明确延期，只记录。
- 详见 [0.7.1验证与部署](verification/2026-11-14-v071-ui-regressions.md)、[grill方案](superpowers/plans/2026-11-14-v071-ui-regressions.md)。

## 2026-11-14 会话收尾

- 用户已自行游戏测试，要求提交与临时文件移入回收站；15:34新报告排查停止，没有据此修改代码或补装依赖，未标记解决
- 关键审查/构建/部署记录转存 `docs/evidence/2026-11-14-editor-grill/verification/`，临时目录与私有错误包归档见 `docs/evidence/2026-11-14-session-close/archive-index.json`；仅本轮临时产物移入Windows回收站
- 生产代码与15:12–15:19通过三端测试构建的候选一致，复用已有结果；本轮targets/bare再次通过，实机验收由用户进行

## 2026-11-14 grill功能（独立复审通过，三端已备份部署）

- 现役约定：八向边框缩放、顶部独立MOVE胶囊与固定九锚点；当前页签65字段恢复；调整恢复成功按用户A方案结束编辑器；警告红图标/红确认/灰取消，教程例外强调色；图标tight bounds及Path generation缓存。
- 用户已撤回SHA/trust/hook/readonly门控。仅本轮新增实施已精确备份清除，普通回归与像素检查保留。
- 本轮三端test build全通过：Fabric1894/Forge1879/Neo1879，0失败/错误，各7既有skip；每端45个原生图标样本通过。主代理已独立复审放行，本次15:29新备份部署完成、另进程回读通过；其他217/434/239项和562源不变。CCB此前28项外部目录减少单独留档，未由本代理删除。旧12:28receipt不作新部署证据。
- 新receipt与独立回读在build/editor-grill-20261114，备份D:\AtomChat-Backups\2026-11-14-editor-grill\20261114-152929-912953；完整记录docs/verification/2026-11-14-editor-grill.md。未启动游戏/生产改动/提交发布，实机待验。


## 0.7.0 回退基线后的面板修订（2026-11-13 22:29）

- 前轮 DeepSeek / HUD 手机与新动画改动已由用户回退。本轮基于干净 `513f1942`；默认边框保留原色 #14161B、默认 4px/上限6，未恢复已删 HUD 图标或改旧动画。
- 已修 Toast 多行自适应、共用浮层软阴影、关于页调试入口说明；新增真实面板编辑器（外观 → 调整），手柄移动/改宽高、独立内容缩放、九宫格吸附与保存/取消。
- 主题预览内滚轮卡顿根因是 hover 命中循环反复触发预设磁盘路径检查，已收敛；CPU 离屏对照 64.638→5.102ms，非游戏 FPS / GPU 成绩。
- Terra medium 实施，429 退出后主代理完成集中复审和坐标/输入修正；三端1828/1813/1813零失败，各7跳过；备份换包与独立回读通过，其他mod不变。
- 未启动游戏、未提交发布；实机仍待验收。入口：[验证记录](verification/2026-11-13-panel-followup.md)。旧章节保留为历史，勿视为当前候选身份。


## v0.7.0 当前收口状态（2026-11-11 20:03 实机反馈后）

- **未通过实机验收**：用户反馈效果不好；错误包确认页面过渡调用中的原生崩溃，现场Paint指针为0。当前版本只保存为已知问题检查点，后续先查资源生命周期，再处理观感。
- 关闭后返回同一Screen的复用风险有源码依据，通知HUD编辑器返回路径是候选复现；此次具体操作链尚未证实，不标记为已修复。
- [错误包分析与接续任务](handoff/2026-11-11-v070-error-analysis.md)记录脱敏证据、性能长尾、输入/教程/动画未验收项；旧离屏测试结果不能替代本次实机反馈。
- 代码版本仍为0.7.0，推送目标origin/main；正式发布留待后续验收。桌面原始错误包与三个实例的部署备份保留。
- 收口时临时工作目录、测试日志和Python缓存移入Windows回收站；先归档证据，构建输入与最终产物不清理。归档及回收清单见交接文档的“收口与证据保留”。


## v0.7.0 教程与渲染反馈（2026-11-11 19:45，已部署待实机验收）

- 修正背景/描边圆角细缝，3逻辑px宽度已量测；聊天内两行/表情变化不再叠加导航弹簧，页面切换轻弹性保留。
- 默认配色采用已确认CCB快照（面板约70%/卡片约60%），新圆角25、描边3；已有配置不覆盖。
- 新老用户首次面板二选一教程，独立本地记录；16步目标批注、内存演示真实控件、外观自主搭配可跳过、关于页从头重播。
- 最终三端build1812/1797/1797，均0失败/错误、各7既有skip；中英文GL176帧各通过，点阵/中断/压力/产物烟测通过。源码838份冻结哈希一致。
- 已备份替换原三实例，AtomLib和其他mod不变；备份 `D:/AtomChat-Backups/2026-11-11-v070-20261111-194553/`。未启动游戏或提交发布。
- 完整证据：[教程、渲染与部署](verification/2026-11-11-onboarding-render-and-deployment.md)；[第三方组件比较](verification/2026-11-11-phone-components-comparison.md)。冷启动与真实游戏性能限制详见记录，不能将稳定GL数据当作游戏FPS。


## v0.7.0 实机反馈修复（2026-11-11 17:17，已重新部署）

- 壁纸完整复用主题卡片规范，新默认无壁纸；石墨预设替换为钴蓝叠面，旧图保留高清且不自动重绘。
- 底栏放缓并加入轻微弹性，恢复全页错峰及逐项绘制命中；恢复右键菜单高光、底栏点阵与原遮挡层次，运动点阵不创建缓存 Picture。
- 默认冷灰黑／石板灰透明层次、#14161B 边框；补齐三端中英语言键，面板描边只保留最后一次。
- 三端完整 build：Fabric 1795、Forge 1780、NeoForge 1780，失败／错误均0，各7个既有skip。真实GPU点阵、错峰中断及压力检查、成品断言通过；孪生原有14残差无新增。
- 原三实例已备份部署，安装哈希一致、AtomLib与其他文件不变。备份 `D:/AtomChat-Backups/2026-11-11-v070-20261111-171732/`；回执 `.work/v0.7.0-feedback/deployment-receipt.json`。
- 未启动游戏或公开发布。已有配置需主动应用默认主题才能查看新版配色，壁纸独立选择。真实游戏FPS和操作手感待用户验收。完整证据见 [反馈复核与部署](verification/2026-11-11-v070-feedback-review-and-deployment.md)。

## v0.7.0 动画、壁纸与主题专项（2026-11-11，已部署待实机验收）

- 真实页面同时入退场、连续中断，绘制与命中使用同一帧几何；输入框与底栏形变，具体设置页收拢，设置首页保留底栏。空状态图标、文字、点阵随页过渡，含旧实现复现红测与真实生产绘制绿测。
- 默认黑色 50% 不透明底保留蓝色强调；七色静态 SkSL 壁纸独立选择，后台合并生成、稳定物理尺寸适配，新预设生成／解码上限 4096；边框 0–4 逻辑像素，新默认 3，旧配置保留粗细或 0。
- 修复回底按钮、灰化会话卡片硬裁阴影，增加手机及半屏边缘软阴影并接预热；普通帧不再新增 GL 状态借用，快照只在中断溢出时按实际密度压平。
- 最终完整 build：Fabric 1780、Forge 1765、NeoForge 1765 项测试，失败／错误均 0，各 7 个既有 skip；隐藏 GL、制品断言通过，孪生既有 14 个残差无新增。
- 已备份部署三实例 v0.7.0，AtomLib 和其他文件哈希不变。备份 `D:/AtomChat-Backups/2026-11-11-v070-20261111-122827/`；回执 `.work/v0.7.0/deployment-receipt.json`。
- 未启动游戏、提交、推送或公开发布；真实整合包 FPS、连点手感、亮暗壁纸观感待用户验收。完整范围、预算降级与证据见 [复核与部署](verification/2026-11-11-v070-review-and-deployment.md)。

## PBO 冷尖峰与 About 静态层（2026-11-05，已部署待统一验收）

- 个人页首次皮肤读回改为有界 PBO + fence 异步轮询；8 个/96MB、2 秒过期、零等待读取，PENDING 不再写永久失败。隐藏 GL 正常/HD/满载/遗弃队列与状态恢复 8 项通过。
- About hero 大面积点阵改为 4x 静态 Image，8 条有界 LRU；真实隐藏 GL 同场景 p50 `0.910 -> 0.072ms`，面板样背景最大差 1/255。小区域仍走逐字节一致路径。
- 真实 GL A/B 同时确认阴影缓存稳定 key=1，但 `0.096ms` 缓存与 `0.097ms` MaskFilter 基本持平；保留 stage5 修复，不把 CPU 光栅收益误报成 GPU/FPS 收益。
- 三端最终 `build` 全绿；候选包含新 PBO 与 HaloKey class，已备份部署 AtomChat，配对 AtomLib 不变。回执 `build/muonium-followthrough-20261105/deployment-receipt.json`。未启动游戏、提交或发布。
- 完整会话、七项判定、GPU A/B 与仍未测的 AC 背景 blur GPU 成本见 [Muonium 对照落地](verification/2026-11-05-muonium-followthrough.md)。

## GPU 阴影与关于页续修（2026-11-05，已部署待实机验收）

- 修复关于页“清理图片缓存”每帧重新扫缓存目录；图片缓存大小改为 1 秒展示缓存，并由加载器写入/清理立即刷新。
- 设置页无参数翻译按资源代缓存，资源重载清空；离屏同页分配下降约 30–44%。
- AtomLib 卡片阴影改为按尺寸/圆角/blur/颜色预烘焙后贴图；60 个阴影离屏 p50 从 1.154ms 降到 0.138ms，最大像素差 1/255，入场阴影尾影回归通过。
- 干净三端源码全量测试：Forge 1654、Fabric 1669、NeoForge 1654，失败/错误均 0，各 6 个既有 skip；目标检查和三端产物断言通过。
- 已备份部署三端 AtomChat 与配对 AtomLib 0.3.1-shadowcache，回执 build/stage5-ac-20261105/deployment-receipt.json。部署前目标实例均未运行；未启动游戏、提交、推送或发布。
- 当前日志、MuoniumPlayer 对照与剩余 GPU 风险见 [GPU 阴影与关于页性能续修](verification/2026-11-05-gpu-shadow-and-about-performance.md)。

## 设置页文字密集页续修（2026-11-05，已部署待实机验收）

- 抓到设置页 `rowRect`/`rowExpansion` 的重复前缀扫描：80 行旧路径每帧做 `3,240` 次文字块测量，现由线性行几何降为 `80`；同帧 `CompactText` 也按行复用。
- `wrap`/`wrapWords` 增加有界 LRU；超过塑形缓存上限的文本不再创建每帧无法回收的原生 `TextBlob`。
- 三端隔离源码全量测试：Forge `1653`、Fabric `1668`、NeoForge `1653`，失败/错误均 `0`，各 `6` 个既有 skip；产物断言和目标检查通过。
- 已备份并部署三端新包，回执 `build/stage3-ac-20261105/deployment-receipt.json`。目标实例当时均未运行；检查中存在的另一个无关 MC 实例未被替换。未启动目标游戏、未提交、未推送、未发布。
- 用户日志、MuoniumPlayer 对照、离线基准和剩余 GPU 风险见 [设置页与文字密集页性能审计](verification/2026-11-05-settings-text-performance.md)。

## 面板切页性能续修（2026-11-05，已部署待实机验收）

- 修复 AC 重复文字塑形/二次测量、设置页同帧重复构建行模型、头像名每帧正则；保留页面入场和各卡片 alpha 图层动画。
- 三端隔离源码构建与全量测试通过：Forge 1651、Fabric 1666、NeoForge 1651，失败/错误均为 0，各 6 个既定诊断 skip；产物级检查通过。
- 已备份并部署三端 `atomchat-*-v0.5.0.jar` 新包；回执 `build/stage2-ac-20261105/deployment-receipt.json`。未启动游戏、未提交、未推送、未发布。
- 旧 JFR、分段分析与隐藏 GL A/B 证据见 [切页性能审计](verification/2026-11-05-panel-transition-performance.md)。部署后 CCB 实时窗口随切页从 panel p50/p95 `2.14/3.47ms` 到 `2.49/5.43ms`，聊天帧间隔 p50/p95 保持 `3.32/4.73ms`；max `44.20ms` 冷长尾仍在。首次 PROFILE 皮肤读回和外部 EMF/Sodium/Iris 成本仍是已知边界。

## AtomChat 0.5.0 三实例候选（2026-11-03）

`3af3412b` 已推送，0.5.0 三端已替换到 Forge 1.20.1-main、Fabric 1.21.1-CCB、NeoForge Mechanomania-航空学，继续配对各端 AtomLib 0.3.0；部署前 0.4.0 旧包与哈希记录在 `D:/AtomChat-Backups/2026-11-03-atomchat-050/20261103-172920-374156/deployment.json`。部署包来自提交前的隔离源码构建，构建戳不等同于干净提交构建。

三端离线全量测试分别 1610、1625、1610 项，各跳过 4 项，制品检查通过；未启动游戏。推送后云构建的 Linux 系统使接口预检测试先命中 Windows 平台守卫，已将 API 测试与平台测试隔离，并在 Windows JVM 模拟 Linux 属性复现和修复；Forge 云端另遇 ForgeGradle 插件解析失败。修复后的远端构建结论以 Actions 为准。

## AtomLib 0.3.0 三实例配对候选（2026-11-03）

本地 AtomChat 0.4.0 候选与 AtomLib 0.3.0 已分别部署到 Forge 1.20.1-main、Fabric 1.21.1-CCB 和 NeoForge Mechanomania-航空学。旧包及部署哈希见 `D:/AtomChat-Backups/2026-11-03-atomlib-030/20261103-170157-794/deployment.json`；Forge 之前没有可见的 AtomLib JAR，本次补装。

AtomChat 仍以公开 beta3 坐标及哈希编译，三端测试额外加载相应的本地 AtomLib 0.3.0 JAR 通过关键 API 预检；Forge、Fabric、NeoForge 全量测试分别为 1610、1625、1610，各 4 项跳过，零失败/错误；三端制品检查通过。实际游戏启动、5× 缩放、图标和 FPS 尚未验证；以上是当时的候选状态；提交与推送情况以 Git 为准。

## AtomLib 能力诊断与图片预算修复（2026-11-03，本地候选）

- 客户端仍接受任意 AtomLib 版本号；启动时额外检查一组直接使用的核心 API，缺失时给出类和成员诊断。这不是所有 API 或行为的完整兼容证明，真实客户端与专服尚未启动验证。下方 11-02 条目中“未增加完整 API 能力预检”是历史检查点；本轮增加的是限定范围预检。
- 图片缓存修复了超预算淘汰返回已关闭图像；在可准确预知最终尺寸的情况下，提前跳过无法缓存的派生图。离屏证据、可重复探针和测量范围见 [图片预算记录](verification/2026-11-03-oversized-image-budget.md)。
- 此前本地 AtomLib 0.2.0 隔离配对的三端定向测试、构建和制品检查通过；当时未改公开 beta3 编译锁、未部署或提交。后续 0.3.0 部署见本文件顶部；实际游戏、5× 界面观感和 FPS 待验收。

> 以下保留 2026-11-02 及更早检查点；提交及推送事实以 Git 查询为准。

## AtomLib 运行时版本约束调整（2026-11-02，本地候选）

- 根据用户要求，三端运行时不再按 AtomLib 版本号精确匹配：Forge 客户端依赖使用空范围，NeoForge 客户端依赖使用 `*`，Fabric `suggests` 使用 `*`，客户端启动检查仍要求已安装 AtomLib 和受支持的 Windows JVM 架构。编译输入仍固定 `0.1.0-beta` 及 SHA；接受版本号不代表任意版本的 API 都兼容，也未增加完整 API 能力预检。
- 先在旧实现上复现不同版本被拒绝，再更新代码；新检查覆盖缺失、空版本、多个不同版本与不受支持架构。三端完整构建通过：Forge 1596、Fabric 1611、NeoForge 1596 项，各 0 失败/错误、4 skip；`tools/test_verify_jars.py` 17 项通过，三端消费者制品检查及 `tools/verify_targets.py` 通过。
- 三端旧版 AtomLib beta3 与本地 `0.2.0` 的字节码接口比对：Forge 与 NeoForge 各 68 个公开类及 884 项公开/受保护成员与类层级标记，Fabric 69 个公开类及 889 项，均未发现缺失或标记差异；这不是 MC 内运行或行为兼容验证。NeoForge 本地候选 Jar 与 AtomLib 0.2.0 已按用户要求复制到桌面并校验 SHA-256，未启动游戏或部署实例。AtomChat 修改当前位于工作区，Git 状态以实际查询为准。
- 2026-11-07 更新：编译输入锁由 `0.1.0-beta` 同步为 `0.4.0`，`versions/atomlib.json` 的三端 SHA-256 与 `input_archive` 指向新的 `atomlib-input-0.4.0.zip`（挂在 AtomLib v0.4.0 Release）。运行时版本约束保持任意版本（`compatibility_range = "*"`、Forge 空范围、Fabric `suggests: "*"`），启动检查仍只校验已安装与 API 符号。三端在不跳过 `verifyAtomLibInput` 的情况下构建通过。

## 转场动画：单时间戳架构 + 六轮打磨（2026-11-01 ~ 11-02，0.4.0 内，**已提交已推送**）

- 版本仍是 `0.4.0`；本轮只动观感与动效，零功能改动。提交 `222c7483..59c966a7` 已推到 `origin/main`，三端 jar 各实例已替换。
- **架构反转**：删掉 `PageTransitionDirector`（两页同时绘制 + 交叉淡入），改为单时间戳语义——每帧只画到达页，一个 `UiStagger.Entrance` 驱动整页包络与逐项入场。中断不再是特例，只是用同一组数字重新打戳。
  - `PageTransitionComposite` 拥有唯一的到达戳；根页是卡片列表，用 `Host.arrivalStaggersSurfaces()` 跳过整页包络，改跑自己的逐项错峰（一帧只有一个动效）。
  - 顶栏是 chrome，最后绘制且**不做变换**——这是"切页时上栏会动"的根因修复。
- **样式区分**：`PageNavAxis` 是配置→运动轴的唯一解析点（SLIDE=水平、ZOOM=垂直）；五个站点与底栏都读它，不再各自 `if (ZOOM)`。条目级也吃样式分量：SLIDE 纯水平漂移 24px，ZOOM 上浮 18px + 0.97→1 缩放。
- **六轮的具体修复**（每轮都有 `docs/verification/` 记录与逐帧实测数字）：
  1. 入场修复：顶栏固定、slide 幅度 28px、透明度延迟 26ms 淡入。
  2. 逐项错峰：可见槽位索引，滚动不回放进场；折叠与入场共用一层。
  3. 中断打磨：打断即重打戳，无退出相位。
  4. 样式轮：页面进度改 `easeOutQuad`（原 cubic 起步速度为 0，读作"没曲线、生硬"）；半屏宽度加 `halfScreenWidthCustomized` 标记，不再读手机宽度的残留值。
  5. 阴影/贴合/轴向轮：入场 `saveLayer` 是硬裁切，补齐阴影外扩量（`CardShadowReach`，实测尾迹 13px、pad s(12)）；`AvatarRing` 让描边同心跨在位图边缘（上一轮为清晰度加的 `halfDevicePx` 圆心偏移是缝隙元凶）；`UiStagger.Axis` 删掉 SLIDE 的垂直腿。
- **清晰度与玻璃**：`UiPixelSnap` 把面板边、卡片边、头像描边对齐设备像素网格；面板磨砂加 `glassMaxLuma` luma 上限（只压 RGB，不碰 alpha），亮背景下白字仍可读。
- 验证：Fabric **1601** / Forge **1586** / NeoForge **1586**，0 失败，各 4 skip。孪生守卫未归类块 10（全部既存于 `SkiaGraphics` / `NotificationHudOverlay`）、陈旧台账 0；裸值棘轮收紧到 441/441。
- 部署：三实例已换 `2026-11-02-shadow-rim-axis` 那版（Forge `9747498f…`、Fabric `57ee7966…`、NeoForge `3bcd1ac7…`），备份在 `D:/AtomChat-Backups/2026-11-02-shadow-rim-axis/20261102-134853/`。
- **用户已实机验收通过**；仍未做：ARM64 与专服启动验证，未发版。

## 渲染锐度定因 + 性能/内存审计（2026-11-02，0.4.0 内，**已提交已推送；实例仍跑 `2a25c5a3`**）

- 提交 `74033973..b611a70e` 已推 `origin/main`，三实例已换 `2a25c5a3` 新包。详细证据：`verification/2026-11-02-render-quality-and-caching.md`（定因 + 改法）与 `verification/2026-11-02-render-quality-deployment.{md,json}`（部署）。给接手 agent 的坑位清单与优先级：[HANDOFF-render-sharpness-and-panel-perf.md](HANDOFF-render-sharpness-and-panel-perf.md)。
- **部署口径**：`7f9aa424`（图片预处理门槛 2×→3×）、`97d57817`（边缘探针）、`b611a70e`（交接文档）只提交未部署，三实例仍是 2× 门槛那版。
- **"糊"的定因是三把离屏探针给出的，不是猜的**：
  - **文字已在物理底线**。会话真实密度 1.4815、`FONT_BODY` 25px → 37 设备像素，10%→90% 过渡宽度 2.112px，而 4× 超采样理想值是 2.086px（差 1.2%）。开 hinting + 关亚像素定位只把它压到 2.085px（0.03px，不可见），却让字形误差从 2.10 翻到 4.86 —— **字体设置一项都不改**。
  - **色彩空间/亚像素 AA 无收益**。legacy（无色彩空间）surface 与 sRGB 给出同一组像素；开亚像素 AA 反而更糊（2.095→2.281px）。
  - **图片降采样是真凶**。缓存解码到 256–1024px，实际画在 40–90 设备像素，GPU 的 2×2 双线性只取 4/9–4/64 个纹素。128→40 的误差：直绘 23.96，缩到 2× 目标再交给 GPU **3.06**。机制是 **GPU 双线性在恰好 2 倍时精确**，其他比例只是近似 —— 所以换更"好"的采样模式没用，必须在 CPU 端先落到刚好 2×。
- **锐度改动**：新增 `ImageQuality`（MITCHELL 反复对半到 >4× 之后，再一次精确落到 2×目标；派生副本弱引用键控源图、4M 像素上限、淘汰即 close）与 `SkiaImages`（唯一入口），**14 个绘制点全部迁移**；三加载器的资源重载都调 `release()`。守卫 `ImageQualityTest.everyImageDrawSiteGoesThroughTheDecimatingWrappers` 扫全树，新增裸调用点即失败。
- **性能改动**（真机基线 panel p50=4.6ms / 95–190KB 每帧）：消息列表每帧把折叠窗口测两遍（测量一遍、绘制找首行一遍）→ 内容高度按「不可变快照实例+宽度+折叠点+布局位+表情版本」缓存，测量顺带记录每行偏移供绘制跳读；表情 chip 的 SHA-256 按 message id 缓存；时钟 formatter 静态化；换行 key 补上字号（原先换字号复用旧换行是正确性 bug）；`ChatStore`/`PrivateChatStore` 发布不可变快照不再每帧复制。**A/B 实测 500 条消息中段滚动：p50 2.191→1.313ms（-40%），每帧分配 200.7→33.1KB（-84%）**。
- **内存改动**：回退字体缓存（2048 项）、表情图缓存（256 项，淘汰即 close）、皮肤三表（各 256 项，淘汰即 close）全部封顶；`ImageUploader` 改进程级单例（原先每次开屏/改窗口大小泄漏一个 HttpClient + 线程池）；屏幕 `removed()` 释放裁剪器位图与表情网格。
- **顺带修三个既存测试缺陷**：`CompatibilityRegressionSourceTest`、`NavInterruptWiringSourceTest`、`InputWiringSourceTest` 直接拿 CRLF 工作副本匹配含 `\n` 的多行片段（`git stash` 验证过 HEAD 上同样失败），改为读后归一化行尾。
- 验证：三端全量 test 绿 —— Fabric **1610** / NeoForge **1596** / Forge **1596**，0 失败，各 4 skip；`verify_targets.py` 通过；`test_verify_jars.py` 16 项 OK；三端 `verify_jars.py --profile atomlib-consumer` 全通过；三端 `clean build`（含 check）成功，构建戳 `git_commit=2a25c5a`、`git_dirty=false`。
- 部署：Forge `d1fd473e…` / Fabric `abe683d88f4a…` / NeoForge `4d1d54ddefdc…`；旧包哈希与上一轮台账 `new_sha256` 逐一吻合（链完整性），备份根 `D:/AtomChat-Backups/2026-11-02-render-quality/20261102-172814/`，mods 可见文件数 164/76/206 不变、各一份包。
- **未验证**：实机观感（三客户端须完全重启）；`ImageQuality` 长期内存曲线；ARM64 与专服。**未发版**，仍 `0.4.0`。
- **仍未做的结构性优化**：静止面板依然整窗重绘（无 dirty-region），缓存整幅结果图是最大剩余收益但失效条件复杂（主题/缩放/滚动/悬停/动画），本轮未动。AtomLib 侧的 `UiTokens.hairline()/cardFill()` 每次重算亮度（3×`Math.pow`）与 `SkiaDraw.BLUR_FILTERS` 清空不 close，需在 AtomLib 仓库处理。

## 合规清理 + CI 修复 + 设备像素网格修复（2026-11-02，0.4.0 内，本轮）

- **CI 红在矩阵校验**（run 41397257133，23s 即挂）：`GnetumHudCompat` 在 `layers/mapping/official` 与 `platforms/1.21.1-fabric` 各存一份且逐字相同 —— 它纯反射、零 Minecraft 名，映射中立，本就该进 `shared/`。已移到 `shared/src/main/java/com/atom/chat/notification/GnetumHudCompat.java`，两份副本删除，`NotificationRenderWiringTest` 的守卫改为断言唯一共享副本；`tools/gen_seams.py` 重新生成 `shared/PLATFORM-SEAMS.md`。`python3 tools/verify_targets.py` 现在通过。
- **Dango / DangoMusic 字样清理**（代码注释 + 本轮提交）：源码与测试里的 142 处引用全部改写成描述行为本身（"单时间戳语义"/"逐项错峰"/"玻璃亮度钳制"/"弹簧调参 k=380 d=24"），不再提参考实现的名字、文件名或行号；`UiStagger` 类注释里"taken verbatim"的断言已删除。`docs/STATUS.md` 的分类标题同步改词。
- **设备像素网格修复**（本次唯一的渲染行为改动）：`SkiaGraphics` 用 `canvas.scale(density, density)` 把逻辑坐标映射到设备像素，但屏幕另用 `beginFrame(uiDensity / guiScale)` 告诉 `UiPixelSnap` 密度 —— 两者差一个 GUI 倍率，于是 `snapToDevice` 实际对齐到 **2–4 设备像素**的粗网格（1080p/scale2 → 每 2px，4K/scale4 → 每 4px），正是"图形分辨率不高"的直接来源。现在密度只由 `SkiaGraphics` 在 `canvas.scale` 旁写入（且在模糊 pre-pass 之前，保证截图矩形同网格），三个屏幕的重复写入删除；`NativeBorrowContractTest.theCanvasScaleIsTheOneTheSnapperIsTold` 锁死这条唯一所有权。
- 验证：三端全量 test 绿 —— Fabric **1602** / Forge **1587** / NeoForge **1587**，0 失败，各 4 skip（较上轮 +1 为本轮新增守卫）。`verify_targets.py` 通过；裸值棘轮 441/441；孪生未归类块 10（与改前相同，全部既存于 `SkiaGraphics` / `NotificationHudOverlay`）。
- 部署：三实例已换 `d833327e` 构建（Forge `6dfb1a82…` 8458279 / Fabric `89cbe654…` 8479633 / NeoForge `315df7a2…` 8470731），构建戳 `git_commit=d833327`、`git_dirty=false`。备份根 `D:/AtomChat-Backups/2026-11-02-snap-device-grid/20261102-153018/`，读回哈希与源逐一吻合，mods 目录各 1 份包、文件数不变。记录：`verification/2026-11-02-snap-device-grid-deployment.{md,json}`。
- 未验证：实机观感（边缘锐度是否真的变好）需要进游戏看；三个客户端都要重启才生效。三枚**已推送**的提交信息仍含 "Dango"（`3b47f632`、`59c966a7`、`c8f6912d`），改写要 force-push 覆盖已发布历史，未擅自执行 —— 要不要改由用户拍板。
- **合规提示（需人判断）**：删注释只是不再自述来源，如果当时的"verbatim 采纳"确有其事，代码本身仍可能构成 GPL-3.0 衍生作品，注释清理不能替代许可处理。落地算法本身是通用弹簧/缓动数学（阻尼谐振子、easeOutQuad + 单时间戳 + 每项延迟），未见可直接指认的复制痕迹；如需彻底澄清建议独立复核 `UiStagger` / `SpringAnim` / `PageTransitionComposite` 三处的实现来源。

## Gnetum 兼容：HUD 横幅闪烁元凶定位 + uncached 路线（2026-11-01 晚，0.4.0 内，未提交）

- 元凶（用户实测：禁用即不闪；CCB fabric 实例同代码不闪）= **Gnetum**（心自安然 mods 里的 gnetum-3.3.6.jar，NeoForge 1.21.1 专用）。源码（tag 3.3.6）+ vineflower 反编译实例 jar 双重核实机制：`GameRendererMixin.gnetum$wrapGuiRender` 劫持 `Gui.render`，`GuiMixin.gnetum$render` 取消原版 `GuiLayerManager.render`，把 HUD 层按 pass 分帧画进**它自己的帧缓冲**（默认 3 pass，一层一 pass，`FramebufferManager.blit()` 事后合成到主帧缓冲）。`registerAboveAll` 的 `atomchat:notifications` 排在 saving_indicator 之后 → `SuggestedPass` 归入 pass 3 → 每 cycle 只渲染 1 帧。
- 闪烁机制（split-brain）：cached pass 里 Gnetum FBO 是绑定目标，但横幅两半去往不同目标——raw GL 磨砂底板画进**当前绑定**（Gnetum cache FBO，且从主帧缓冲采样时下层 HUD 不在里面），Skia 卡片被 AtomLib 桥（backend target 钉死 `getMainRenderTarget()`，`MinecraftRenderBridge.target()`）flush 进**主帧缓冲**——只在 pass 3 帧落地、下一帧被世界清屏抹掉，同时 Gnetum 又把不含卡片的 cache（含残底板）合成其上 → 横幅区按 pass 节奏交替。
- 修复（方向 a，走 Gnetum 自己的 per-element 开关）：新增 `GnetumHudCompat`（official 层 + Fabric 孪生各一份，纯反射零编译依赖）：每帧把 `me.decce.gnetum.Gnetum.getCacheSetting("atomchat:notifications").enabled.value` 钉成 OFF——等价于 Gnetum 配置界面里给该元素选"关"。uncached 层被 Gnetum 逐帧画在 `FramebufferManager.unbind()` 之后的主帧缓冲上（cache 合成完、世界+HUD 齐备、`Gnetum.rendering=false` 无 blend 强改）——正是无 Gnetum 时的原始时序，raw GL pre-pass 与 Skia 全部落回主帧缓冲。每帧重钉防配置重载复活；反射失败 fail-open 回原行为。
- 面板路径零改动；无 Gnetum 环境零改动（`Platform.isModLoaded` 早退）。Fabric 侧保持镜像但为 no-op 探针（Gnetum 3.3.6 无 Fabric 版；4.x fabric 若出现且内部结构变了也只会静默退回）。
- 验证：`NotificationRenderWiringTest` 新增 `gnetumCompatPinsTheHudLayerToItsUncachedPathInBothTwins`（双孪生接线 + 禁编译期依赖 + 层 id 与注册一致）。三端全量 BUILD SUCCESSFUL：NeoForge 1563 tests 0 失败、Fabric 0 失败、Forge 0 失败（wiring xml 7 tests 0 failures）。
- 部署：仅重建 neoforge jar（`04900d177303e739…`），心自安然 + Mechanomania-航空学 两实例替换并 sha256 一致；上一版（`82b72614377d95e3`）备份于 `D:/AtomChat-Backups/2026-11-01-banner-flicker-deploy/*-pregnetum-compat*.jar`。CCB / 1.20.1-main 未动。
- 未做：游戏内复测（心自安然装回 Gnetum 看横幅不闪；Mechanomania 回归无 Gnetum 场景）。取舍备注：装 Gnetum 时本层被钉在 uncached（每帧全绘，无分帧缓存收益），这是正确性优先的有意选择；用户在 Gnetum 配置里手动改回"开"会被下一帧重新钉回"关"。

## TrChat 服务器插件系统消息误判修复（2026-11-01 下午，0.4.0 内，未提交）

- 用户转述（TrChat 在别人服，本机无日志）：插件系统消息被识别成玩家消息，且"一部分文本在玩家名位置、一部分在气泡里"。TrChat v2 仓库核实事实：玩家聊天经 TrChat 全部以系统聊天包下发（Paper `sendMessage`→SystemChat），扁平化形状 Global `[服务器名] 名字: 消息` / Normal `[世界名] 名字: 消息` / 私聊收方 `[名字 ➥ 你] 消息`——这就是系统消息启发式（`ChatPipeline.tryParsePlayerLine`→`MessagePresentation.parseGeneric`）存在的理由。
- 病根：`parseGeneric` 的 displayLabel 取名字前**全部**文本，任何"散文+玩家名+分隔符"的插件句（如"[公会] 你邀请了 Steve: 点击接受"）都会把散文推进玩家名位置；旧守卫（名字前无 `>»|：`、whitespace-gap）挡不住。
- 修复：`MessagePresentation.hasProseBeforeName` 散文守卫——名字前只允许装饰（括号组/§对/符号），括号外出现 CJK/假名/谚文/全角标点（0xFF01-0xFF60，排除全角（）0xFF08/09——那是装饰括号）或 ≥2 个英文词 → 整条保持系统消息；单 tag（"VIP"）仍是装饰，TrChat/原版形状全部保持可解析。包级 `MessageHandlerMixin.handleSystemMessage` 与 HUD 行级 `ChatHudMixin` 兜底共用此解析器，一处收紧两处生效。
- 验证：`MessagePresentationTest` 新增 5 项（TrChat 形状保持 + 中英散文拒绝 + 单 tag/全角括号装饰）；三端全量随二轮横幅构建一并通过（1563/1563/1557，0 失败）。TrChat 修复随二轮部署进同批 jar。
- 已知边界：`名字: 通知` 形状（"Steve: 你的订单已完成"）与真聊天在纯文本层不可区分，按设计仍进气泡；如反馈方给出确切原文可继续收守卫。

## HUD 横幅三轮：背景换面板式 raw GL 预画（2026-11-01 傍晚，0.4.0 内，未提交）

- 用户实测（心自安然，不开光影）：区域级 alpha 归一版把 HUD 横幅整块变纯黑矩形（黑块形状=归一 scissor 区域本身），面板内横幅正常；日志无 GL error。结论：**对主帧缓冲 alpha 的任何写入都会被显示链路（读者未定位，与光影无关）当成黑色**——Skia backdrop filter 路线在 HUD 彻底放弃，按用户方向改用面板式 raw GL。
- 修复：HUD 横幅磨砂背景改为 pre-pass 调 `PanelBlurRenderer.render`（复用现成 kawase+圆角 mask shader，面板场景长期实机验证）：official/Fabric 两个 `NotificationHudOverlay` 在 `gui.flush()/draw()` 后、Skia pass 前用 `NotificationBanner.hudPlacement` 的同一矩形 + `hudBackdropAlpha()`（HUD host fade）画圆角磨砂底板；`drawBanner` 增加 `backdropPreDrawn` 参数跳过 Skia `drawNotificationBackdrop`（面板路径保留——那里 backdrop 是 Skia 本帧自画的合法 premul）。全程**不读、不写主帧缓冲 alpha**，光影免疫。
- 降级路径：shader 不可用/触发熔断时 `isAvailable()=false` → 回落 Skia backdrop（无光影下本就正常）。
- 删除：`HudBackdropAlpha` 类与 `BannerBackdropAlphaStabilityTest` 整体移除（区域版 jar 备份 `*-region-alpha-backup.jar`）。
- 验证：`NotificationRenderWiringTest` 重写为双孪生接线断言（HUD overlay 必须走 PanelBlurRenderer pre-pass、矩形来自共享 hudPlacement、禁再出现 backdrop filter/归一；两份 banner 必须有跳过参数）。三端全量 BUILD SUCCESSFUL：Fabric 1563/0/4、NeoForge 1563/0/4、Forge 1557/0/4。
- 部署：三平台重建，四实例 sha256 校验一致：心自安然+Mechanomania（neoforge `82b72614377d95e3`）、CCB（fabric `c0fbc473f72d3a75`）、1.20.1-main（forge `61e6208cc2138f5b`）。上一版备份 `*-region-alpha-backup.jar`。
- 未做：游戏内复测（横幅不黑、不闪、磨砂观感）；黑块显示读者的根因未定位（已记录为"勿写主帧缓冲 alpha"约束）。

## HUD 横幅修复二轮：全视口 alpha 归一改区域级（2026-11-01 下午，0.4.0 内，未提交）

- 上轮全视口 alpha 归一（`normalizeToOpaque()` 无参版）实机（心自安然，Iris 1.8.14-beta.1 + DH 3.3.3）复测出新 bug：横幅从面板切到 HUD 后**整屏黑**、横幅退场即恢复。新旧 jar 字节级 diff 证明唯一代码差异就是该归一 → 元凶锁定为"向整个视口写 alpha=1"这一新增面。
- Iris 反编译排除了若干假设：final pass 直接写 vanilla main FB（`FinalPassRenderer` 的 `colorHolder` 挂 `main.getColorTextureId()`），colortex0 是独立纹理、无别名，Iris/DH/原版链路均不把 main FB 当采样器读（`RenderTargets` 无 main 别名、无 TAA history 回读 main）→ 黑屏读者在被审计链之外（DH、其它 mod 或驱动/交换链行为），未能静态定位，但"区域级 alpha 写入安全"有旧包实证：修复前横幅 premul 绘制每帧都往横幅矩形写 alpha，从未黑屏。
- 修复：`HudBackdropAlpha.normalizeToOpaque(int x,int y,int w,int h)` 改区域级——glScissor 到横幅栈矩形 + UiTokens.s(56) 余量（覆盖 blur 3σ 起始 18 逻辑px + SLIDE 动效 24 + 余量），矩形由 `NotificationBanner.hudPlacement`（本次从 `renderInHud` 提取的共享布局函数）给出，GL y 从底边换算；空矩形 no-op。official 层与 Fabric 孪生 `NotificationHudOverlay` 都在 Skia pass 前计算设备矩形调用；javadoc 记录区域约束是承载性的。
- 期间测试探针踩坑：区域外探针断言读的是 RGBA 的 R 通道而非 alpha（白色条纹上 R=255），与"区域外被写坏"无关——产品代码自始稳定；已改读 offset+3 的 alpha 字节。
- 验证：`BannerBackdropAlphaStabilityTest` 重写（区域级调用 + 区域外 alpha 保持原始值/交替值的正反断言）；`NotificationRenderWiringTest` 断言更新（区域来自共享 `hudPlacement`、clear 必须在 scissor 开启下执行、无参重载已删除、`NotificationHudLayout.scaled` 每 Banner 文件仅一处）。三端全量 BUILD SUCCESSFUL：NeoForge 1563/0/4、Fabric 1563/0/4、Forge 1557/0/4（forge 存量 flake 本轮未触发）。
- 部署：三平台重建（neoforge `jar` / forge `jarJar` / fabric `remapJar`，注意 fabric 的 libs 产物只有 remapJar 才刷新）。四实例已替换并 sha256 校验一致：心自安然 + Mechanomania-航空学（neoforge `ba19df3d51ddc2f0`）、1.21.1-CCB（fabric `0eefdc81d7df1807`）、1.20.1-main（forge `800dc1193ffb262c`）。上一版（全视口 alpha）jar 备份于 `D:/AtomChat-Backups/2026-11-01-banner-flicker-deploy/*-fullviewport-alpha-backup.jar`，可用于实机 A/B 复现黑屏。
- 未做：游戏内复测（横幅不黑屏且不闪）；全视口元凶的下游读者（谁在读 main FB alpha）未定位；未提交/推送/发布。

## HUD 横幅光影闪烁修复（2026-11-01，0.4.0 内，未提交/未部署）

- 用户实测（NeoForge 1.21.1 + Iris 1.8.12 + Bliss 2.1.2）：HUD 通知横幅开光影闪烁，面板不闪。
- 根因：横幅的 frosted 背景（`UiCards.drawNotificationBackdrop`）是 Skia backdrop filter，回读主帧缓冲按 premul 解释；Bliss final pass 只写 RGB（`dimensions/final.fsh:118`），帧缓冲 alpha 逐帧逐材质未定义 → 模糊输出随垃圾 alpha 波动。面板背景走 `PanelBlurRenderer`（alpha 来自圆角 mask），面板内横幅读 Skia 本帧自画内容，均不受影响。
- 修复：新增 `shared/.../render/HudBackdropAlpha.normalizeToOpaque()`（colorMask 仅开 A + clear 归一 alpha 为 1，RGB 不动，GL 状态全还原）；`NotificationHudOverlay.render` 在 `gui.flush()` 之后调用。vanilla 内容 premul 本来合法，无光影时无观感变化。
- 验证：真 GL 探针（hidden GLFW 镜像 AtomLib 桥序列）证明同 RGB 不同 alpha 输出 181/236、alpha 逐帧翻转 face alpha 198↔255；回归 `BannerBackdropAlphaStabilityTest` 无修复失败（182 vs 236）、有修复通过；wiring 断言入 `NotificationRenderWiringTest`（同时钉 official 层与 Fabric 孪生两份 HUD 入口）。三端全量终跑：NeoForge 1557/0 失败/4 skip，Fabric 1563/0/4，Forge 1557/0/4。期间 Forge `PanelWidthGestureTest` 曾一次性 4 项失败，stash 后基线复跑同样失败（存量 flake，与本轮无关），终跑通过。
- 独立 code review（限定只读）：发现 P0——Fabric 平台有自己的 Yarn 命名 `NotificationHudOverlay` 孪生、不挂 official 层，修复漏接；已补孪生调用 + 双孪生接线断言。P1——`HudBackdropAlpha` colorMask 未保存恢复（硬编码全 true）；已改为 `glGetBooleanv(GL_COLOR_WRITEMASK)` 查询后还原，javadoc 同步。P2 已修：glfwInit 改 assume、测试 config 反射 finally 还原、scissor-on 分支纳入覆盖、测试序列用 `resetGLAll` 对齐产品桥。
- jar 命名改为 `atomchat-平台-mc版本-v模组版本`（如 `atomchat-NeoForge-1.21.1-v0.4.0.jar`），去掉系统后缀；三端产物名已验证。storeVersion 字符串未动。
- 未做：游戏内复测（需用户开同一光影实机确认横幅不再闪）；未提交/推送/发布。

## 本地审计修复与原方案核对（2026-11-01）

- 修正 `SettingsHomeReplicationTest` 的像素差值：N32 通道先转无符号 8 位再相减；新增黑白全差异与 127/128 相邻灰阶回归，旧代码两项均失败，修复后连同复刻／步进控件共 7 项通过。
- 修正 README 的源码构建路径：读取下载器输出的绝对 Maven 目录后再进入平台目录，不再手写错误相对路径；同一取值方式下 `help verifyAtomLibInput` 通过。
- 使用现有 beta3 锁重跑复刻：21 项测量仍有 4 项已记录的 1u 圆整差异；非文字差异率仍为 0.7643%，全图差异像素由旧量具的 7851 修正为 7838。PNG 未变，修正数据另存 Lib `docs/evidence/settings-home-replication/unsigned-channel-fix/results.json`，历史数据保留。
- [最初完整方案](superpowers/plans/2026-10-29-atomlib-extraction-and-yoga.md) 已标注历史范围：当前 Lib 是基础栈及可选设计包，通用组件／滚动／模态／焦点／壳不在当前交付内；静态离屏复刻不代表“只写结构、数据和动作即可制作完整 UI”的原始易用性目标已完成。
- 此处是本地源码／验证检查点，AC 仍为 `0.4.0`，输入仍钉公开 beta3；Lib 同轮修复详情见其状态文档，未推送、发布或变更配套包。

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
