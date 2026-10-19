# 2026-10-19 圆角尾巴与晚安星光验证

状态：**ready_for_review**。版本保持 0.3.0；未启动游戏。Windows 日常 NeoForge 与 WSL Prism Fabric 已更新，实机观感与交互仍待用户验收。

基线为 `main@f7b5377f1fd0b215569f9d1452a8fe0707c56b17`。

## 完成范围

- **设置滚动尾部**：详情页 list 底边改用 `LIST_PAD_X`，滚动内容新增 `ROW_CLIP_INSET` 尾部安全区；滚动到底时最后卡片到面板外缘为 23 布局单位，与左右一致。设置首页与个人资料页同样补尾部余量，消息列表不改贴底语义。
- **合卡 hover**：整卡 PressScale 的位移预算取卡片宽度和高度中较大者，高卡不再按宽卡的比例获得过大的纵向放大，底部允许完整显示 spring 回弹。
- **圆角审计**：开关把手、音量/滑条把手及其强调描边、菜单行 hover 底色、输入选区底色统一走 `UiTokens.radius` / `handleRadius`；圆角 0 为方角，默认仍为圆。头像、气泡、色彩圆点、表情图案保留原形状。
- **趣味特效**：可见文案删除“实验性”；新增 `STAR` 类型，匹配独立“晚安 / good night / goodnight”短句，排除命令、代码、链接和引用正文。星星最多 12 颗，沿内容区两侧上浮 3 秒，旧三种特效仍为 28 颗且未改默认强度或总开关。

## 审计与修正

- GPT-6.1 Sol 只读审计确认截图间距根因是滚动尾部无安全区，并发现滑条滑块、滑条描边、菜单 hover 和输入选区四处残留固定圆角。
- 审计建议中的“特效强度默认 50%”未采用：用户未要求降低旧特效强度，且会扩大配置与迁移面。最终只新增一种固定低粒子数的“晚安星光”。
- “晚安”中文规则去掉了不必要的前置边界，避免误伤“祝你晚安”；末尾边界仍排除“晚安模式”。
- 测试过程中暴露 `SettingsActionsTest` 依赖其他测试先安装 Platform 的顺序问题；已在 official/Fabric 两份测试中显式安装临时 Platform，避免全量测试顺序漂移。

## 验证

### 三端完整测试

| 目标 | tests | failures | errors | skipped | suites |
|---|---:|---:|---:|---:|---:|
| 1.21.1-neoforge | 1294 | 0 | 0 | 2 | 164 |
| 1.21.1-fabric | 1306 | 0 | 0 | 2 | 167 |
| 1.20.1-forge | 1294 | 0 | 0 | 2 | 164 |

### 静态守卫

- `tools/gen_seams.py --check`：exit 0，甲 123 / 乙 71。
- `tools/verify_targets.py`：exit 0。
- `tools/twin_diff_report.py`：exit 0，孪生 68 对，未归类 0，陈旧 0。
- `tools/verify_log_prefixes.py`：exit 0，生产日志出口 262，违规 0。
- `tools/bare_scan.py`：exit 0，实测 475 / 基线 505。
- `git diff --check`：exit 0。
- 666 个构建输入聚合 SHA-256：`e88d76c613bc3a452c1ad3604f256d2bbbb6e8170fcabdbd5d4147cffda61863`；构建前后一致。

### 六包

| 包 | SHA-256 |
|---|---|
| NeoForge Windows | `9d23ff6ac3f8b78441770eea34c5b14b628845a097e77aa6f0cb333a17d9cbd2` |
| NeoForge Linux | `59c625ea93317a8d4061e4e876e0bb97abe59d182963732cbf4263abebe1e1b2` |
| Fabric Windows | `1f2e3f5ab8ae72b69a6842075474c2d157f0855bcc13fd4645ea9c3b545713f5` |
| Fabric Linux | `97f766a38e7502dd140f222d8012db85bc21cb9e4e29b044bec1a4f55fd221fe` |
| Forge Windows | `632547bfbd9671f611680ebd80c7cf0d216bf1757afbfbd4ed33fa3c27076527` |
| Forge Linux | `f6e48c237b9c1b4b7b2da801da09ba446705956d3278c94558b901776d35e86d` |

每包仅含对应 OS 的 Skija native，版本 0.3.0；同加载器 Windows/Linux 的 `com/atom/chat/**` 与 `assets/**` 无新增、缺失或变化。证据在 `.work/2026-10-19-polish/evidence/package-verification.json`。

### 部署

- Windows「Mechanomania-航空学」NeoForge：安装 `9d23ff6a…`；旧包 `e2d9e0c7…` 备份至 `D:\AtomChat-Backups\2026-10-19-polish-final\NeoForge\`。
- WSL Prism「1.21.1」Fabric：安装 `97f766a3…`；旧包 `c0ce99c6…` 备份至实例 `atomchat-backup/2026-10-19-polish-final/`。
- 两处 mods 目录均只有一个 AtomChat JAR；部署清单在 `.work/2026-10-19-polish/deployment.json`。

## 实机待验收

- 设置页滚动到底、窄窗口和大内容缩放下，最后卡片与左右间距是否一致，hover/按压释放是否完整。
- 圆角 0、默认和最大半径下，开关与滑条填充/描边的形状是否统一；滑条两端拖动是否仍准确。
- “晚安”特效在公聊、私聊和不同 UI 缩放下是否安静、不遮输入；关闭特效/动画后不出现。

未启动游戏；GPU 实帧、输入法和真实多人环境不能由本轮 JUnit/离屏检查替代。
