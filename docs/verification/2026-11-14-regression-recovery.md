# 回退事故恢复验证

## 恢复依据与范围

用户实际要求是撤回 DeepSeek 自己的改动，保留此前认可的实现。18:56 的 reset 回到了 11 月 11 日的检查点，连带丢失后续未提交实现；22:29 交付沿用这个旧基线，主代理没有逐项核对回退边界。这是交付流程错误，不能据 reflog 判断具体操作者或动机。

恢复依据三份：17:00 的源码 SHA-256 清单、stash `d9b0ba6b086988e7252ad27b2de3b322b5f46002`（含 untracked 父树）、17:00 原 Fabric 成品 `d3faa3124b2cc2acfcfc24efa07040de9a810f5f7cdbe24d89cee5b5b4af1fa5`。原包位于 `D:/AtomChat-Backups/2026-11-13-theme-strip-performance/20261113-183333/`，另复制到本次事故备份。

| 功能 | 当前恢复方式 |
| --- | --- |
| 教程外侧说明窗、面板内聚焦框、缓动回弹、不断开的轮廓连线 | 从已匹配 17:00 指纹的源码恢复，保留窄视口内置回退 |
| 线性 22 步、设置 9 页签用途、过滤真实聚焦、头像左右键、子页返回、点击白名单 | 恢复真实控件接线与内存沙盒，不保留旧 DemoPanels |
| 警告/普通确认窗、灰色取消、强调色普通确认、危险红色确认 | 恢复同一 ConfirmationSurface 与 Button，教程和 HUD 底栏实际调用它 |
| HUD 编辑器底栏及退场 | 恢复原确认窗材质、正常字号、按钮动效及退场等待 |
| 点阵 | 恢复 edge alpha 64、既有颜色来源、4x 有界大拖尾图缓存 |
| 主面板阴影 | 按旧成品复核恢复 sigma18/drop12/contact4/drop3、alpha120/68、3x patch；原像素差上限4未放宽 |
| 非默认预设描边 | 根据原成品恢复 8 个预设原值，默认仍为用户指定的 #14161B |
| 模糊/描边配置与短文案 | 恢复模糊默认200%/上限300%、描边4/6和短句；已有用户配置不强制覆盖 |
| Toast、主题预览 hover 卡顿 | 保留22:29换行/自适应和每条每帧一次命中修复，不使用旧 GPU 成绩冒充当前结果 |
| 手机 HUD、新手机开合动画、DeepSeek 旧 PanelLayoutEditor、4 pass 模糊 | 按用户后续决定排除，保持旧开合动画 |
| 真实面板编辑器 | 保留本次新编辑器并修接线，移除重复内容缩放滑条，独立滑条保留设置页 |

16 个关键恢复文件与 17:00 内容逐一相符，比较允许 LF/CRLF 换行差异，不把格式变化当功能变化。证据 `docs/evidence/2026-11-13-editor-incident/recovered-fingerprints.json`。

## 崩溃和编辑器根因

错误包SHA256为 `156b260b5aeab00dcd358a08ff62774d5ca15b1e8fc2ba27644eb39a6c6c0e29`，会话收尾时已移至本机归档的 `private-error-reports/error-report-2026-11-13-223908.zip`，位置见 `docs/evidence/2026-11-14-session-close/archive-index.json`，原始包不进入Git。22:39:03，Render thread 的 `Paint._nGetColor4f` 原生访问读取0x30；现场 Paint `_ptr=0`、`_cleanable=null`。业务链从 PageTransitionComposite.drawLayer 到 Screen。现役旧实现 close 后 reset 未重建 Paint，已恢复原有重建及幂等关闭，同时恢复 BottomChromeTransition 的同类修复。TransitionLifecycleTest 两项先红后绿；不能据此保证整合包从此不会出现其他原生故障。

编辑器原外侧点击 PASS 穿透到关闭处理，且输入错误地使用正文/底栏逆变换坐标。现已保活外侧空白，使用独立屏幕坐标；上边框整条移动，左右边框改宽，下边框改高，四角改宽高；内部保持真实交互。用户最终要求底栏仅还原/保存，已移除模式与折叠按钮；工具栏共享确认材质，仍可拖标题避让内容；移除重复缩放滑条和密集圆圈，保留移动时的视口锚点提示。

## 独立审查

复用唯一代理 Plato（01a1d562-f198-7972-9298-d966a15cf2a6）完成只读 code-reviewer 审查，未另派并行代理。抓到3项P2并修复：

1. render 每帧覆写真实设置的宽高 → render 不写配置，followPanel 接真实屏幕几何，手势期间不重建model
2. 教程能启动不可见编辑器 → dispatch 沙盒与三端直接入口同时拦截并提示
3. 大面板下工具栏遮挡控件 → 优先外置，标题可拖以避让内容；用户后续决定去掉模式和折叠，仅留还原/保存

代理再次只读确认三项静态关闭；简化补丁又发现标题首次按下导致位置归零，已补初始化与按下/渲染/释放无拖动测试，先红后修。代理未跑 Gradle、未做实机验收。主代理行为回归先红后绿，最初沙盒测试 fixture 返回空 sectionPage 引发NPE，修正 fixture 后确认为误放行红灯，再实施拦截。

## 验证与交付状态

恢复候选已完成三端完整 `test build`：Fabric1878/Forge1863/NeoForge1863，零失败各7跳过；557生产文件冻结。2026-11-14 12:28三端备份部署已完成，回执build/editor-incident-20261113/deployment-receipt.json；用户未认可旧边框交互，后续按grill新定案实施，不能标记编辑器已验收。日志和成品清单正本 `build/editor-incident-20261113/`。此前一轮三端完整构建通过，不能替代补修后的验证。一次 bareValueCheck 抓到新布局 s(420)，已改用既有 DIALOG_MAX_WIDTH，未放宽基线。

未启动 Minecraft、未提交/推送/发版。游戏窗口、真实手感、IME、GPU整帧成本仍需要实机确认。离屏截图和逻辑测试不冒充实机验收。

## 备份

本轮开始已保存 WIP patch、所有修改/未跟踪文件、stash tracked/untracked 两树、安装包、截图和错误包到 `D:/AtomChat-Backups/incident-20261113-224928/`。未执行整体 stash apply、reset 或清空工作区。

## 最终恢复清单审计

唯一代理对17:00的556个生产文件作换行归一比较：512项匹配、40项内容不同、4项缺失。不同/缺失全部有意图归因，未发现明确漏恢复旧功能，未发现未解释文件。此清单是源文件与已知用户意图的静态检查，不等于所有游戏行为已经实测，也不能凭哈希判定所有改动的作者。

详细清单 `build/editor-incident-20261113/recovery-audit.json`；限定排除项包括手机HUD/新开合与倾斜mask、DeepSeek旧编辑器/4-pass候选；合并项包括默认原色、面板位置、22:29 Toast/hover、浮窗阴影、当前编辑器及调试说明。

最终编辑器只绘制还原、保存两个按钮；上边框/侧边/底边/四角的任意位置直接操作，Esc取消。标题拖动不影响真实面板几何。边框非中点测试先红后绿；单跑渲染测试发现原测试依赖其他类安装Platform，补BeforeEach/AfterEach临时平台隔离后通过。
