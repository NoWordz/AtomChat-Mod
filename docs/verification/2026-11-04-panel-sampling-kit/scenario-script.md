# 面板掉帧实机采样脚本（用户自行执行）

**状态：等待采样。** 本包只提供量具与复现步骤，未取得实机证据，不声明根因或性能改善。
游戏由用户启动、操作、关闭；agent 不启动游戏、不改实例文件、不部署。

## 最快路径（推荐，零交互）

诊断包和 debug 已经装好，不需要再替换任何文件。用 `auto-record.ps1`：**不用按回车、不用掐时间**。
它自己等游戏启动 → 自动开始录制（持续写盘）→ 游戏退出或满 3 分钟自动保存 → 打印文件路径。

```powershell
powershell -ExecutionPolicy Bypass -File "D:\AtomChat-Main\docs\verification\2026-11-04-panel-sampling-kit\auto-record.ps1"
```

顺序：先跑这行 → 再启动游戏 → 进世界开面板、切页十几次、开关十几次、开着待一分钟 → 关不关游戏都行。
脚本会打印 `.jfr` 路径，把那个文件和 `latest.log` 发我。

`one-click.ps1` 是交互版（需要按两次回车，且必须在游戏运行期间按），容易踩点不准，不再推荐。
下面各节是手动分步版和原理说明，赶时间可以不用看。

## 1. 构件与环境

| 项 | 值 |
| --- | --- |
| AtomChat Fabric 候选 | `build/panel-perf-20261104/artifacts/atomchat-Fabric-1.21.1-v0.5.0.jar` |
| 配套 AtomLib | `build/panel-perf-20261104/artifacts/atomlib-Fabric-1.21.1-v0.3.0.jar` |
| 完整哈希 | `build/panel-perf-20261104/delivery-manifest.json` |
| 实机现有 AtomChat | `2a038ec074b147b66f450079be960e5fc4c1e9cb5b3e743ac3e275e408cb657b` |
| 实机现有 AtomLib | `0d949b7949d4a221e47068719ea8c8e3b36193b56db2c78689efd76c63aed5f2` |

**AtomLib 无需更换**：实机现有 JAR 与本次配套 AtomLib 是同一个文件（哈希相同）。
只需替换 AtomChat。替换前备份原 JAR 并记下哈希；回退时把备份放回原名即可。
不要同时保留两个 AtomChat 或两个 AtomLib JAR。

固定并记录：世界/位置/视角、分辨率、GUI 缩放、壁纸、模糊开关、动画开关、
帧率上限、VSync、渲染距离、光影包（**关闭 Iris 光影**）、Java、GPU/驱动、
加载器与整合包、后台负载。等区块与纹理加载稳定后再开始计时。

`config/atomchat/atomchat-client.json` 设 `"debug": true` 并重启客户端；
否则自定义事件不会产生。日志里确认 `AtomChat build … | sha256:…` 身份行。

## 2. 启动与附加 JFR

`default` / `profile` 默认配置**不启用** `com.atomchat.*` 自定义事件；必须使用
`atomchat-panel.jfc`（已按零阈值显式启用）。命令已在测试 JVM 实测通过，见
`jfr-probe-verification.txt`（`RESULT=PASS`）。

```powershell
cd D:\AtomChat-Main\docs\verification\2026-11-04-panel-sampling-kit
.\capture.ps1 -Action list                       # 找客户端 PID
.\capture.ps1 -Action start -GamePid <PID>      # 输出待保存的 .jfr 路径
# 按第 3 节执行场景
.\capture.ps1 -Action stop -GamePid <PID> -Recording '<上一步输出的路径>'
.\capture.ps1 -Action print -Recording '<路径>' | Select-Object -First 40
```

`jfc` 同时启用执行栈、文件 I/O、Socket、分配与 GC 事件，用于把 `CONFIG_RELOAD`
与真实文件读取栈、切页动作与 GC/分配尖峰对齐。

## 3. 场景（每段标注开始/结束时间）

每段至少 20–30 秒稳定帧；切页实验要记录**每次动作时间**及动作后至少数百毫秒的逐帧，
不是只看 600 帧滚动窗口。用外部帧时间工具另存完整游戏帧时间；面板帧间隔不是游戏 FPS。

| # | 场景 | 操作与标注 |
| --- | --- | --- |
| A | 关面板基线 | 关闭面板，静置。记开始/结束时间。 |
| B | 聊天页空闲 | 打开面板停在聊天页，无新消息。记开始/结束。 |
| C | 常用页空闲 | 切到你最常用的页面，静置。记开始/结束。 |
| D | 固定两页交替 | 两页之间固定节奏交替切换 15–20 次；**逐次记录动作时刻**。 |
| E | 连续打开关闭 | 固定节奏开关面板 10–15 次；逐次记录时刻。 |

每场景同时保存：亮/暗背景截图各一张（确认观感未变）、外部帧时间曲线。

## 4. 回传清单

- `.jfr` 文件、`logs/latest.log`、实验时间表（场景/动作时刻）、外部帧时间文件
- 两个实际运行 JAR 的文件名与 SHA-256、环境记录（第 1 节）
- 截图（可不含聊天正文）

**隐私**：日志可能含聊天正文、玩家名、服务器地址、本地路径。分享前自行检查并删减。
不需要提供原始聊天正文。

## 5. 本包尚未覆盖

- AtomLib 桥接内部阶段（`GlStateScope.capture` / 上下文准备 / `flushAndSubmit` / scope 恢复）
  仍是 `BRIDGE_ENTER` / `BRIDGE_EXIT` 两个合并桶，**未分解**；不要相减或据此归因。
- GPU timer query 环形队列只有纯逻辑实现与单测，**未接入渲染路径**，本包不产出 GPU 耗时。
- 屏幕打开触发、构造、init、首帧只有 `CONFIG_RELOAD` 已接入真实生产调用点；
  其余阶段只有事件 API，未接线。
- 完整游戏帧时间需要外部工具；JFR 里的是渲染线程墙钟与面板帧间隔，不是游戏整帧。
