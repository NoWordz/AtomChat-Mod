# Dango 采纳四单元构建与三实例部署（2026-11-01，0.4.0 内，未提交）

## 范围与产物
- 本轮 jar 包含 Dango 采纳的已验证改动：单元 A（`SpringAnim.tick` 自计时弹簧 + 停摆重置）、单元 B（面板模糊 rounded shader 的 maxLuma 亮度钳制 + `glassMaxLuma` 开关）、单元 C（设置开关 knob 换弹簧，k=380/d=24）。单元 A/B/C 三端全量 test 均绿（NeoForge 1581 / Fabric 1596 / Forge 1581，0 失败，4 skip）。
- 三包构建（Windows x64）：NeoForge `jar`、Fabric `remapJar`（libs 产物只有 remapJar 才刷新）、Forge `jarJar`；产物位于各平台 `build/atomlib-consumer/libs/atomchat-<Loader>-<mc>-v0.4.0.jar`。
- 产物门：`python tools/verify_jars.py --platform windows-x64 --profile atomlib-consumer`（三平台全部通过：恰好一个 consumer jar、架构中立命名、无重复 UI/native payload、重定位 FlatLaf、嵌套 jar 全登记、AtomLib 客户端依赖与锁一致）。
- 内容级检查（三包逐项）：`atomchat_panel_blur.fsh`/`.json` 内含 `u_MaxLuma` 且钳制行在位；`atomchat_kawase_blur.fsh` 无 `u_MaxLuma`（Kawase 管线零污染）；`ToggleSwitch.class` 引用 `SpringAnim`、含 `knobSpringForTest`、不再引用 `Animator`；无 `com/atom/chat/testing/` 或 agent class；`0.4.0` 版本串在位。

## 部署（备份 → 替换 → 读回哈希核验）
- 备份根：`D:\AtomChat-Backups\2026-11-01-dango-adoption\<timestamp>\`（旧包原文件名保留，`removed-wrong-loader\` 孤儿隔离，哈希见 deployment.json）。
- deployment.json：`docs/verification/2026-11-01-dango-adoption-deployment.json`（新包/旧包 SHA256、字节数、mods 目录文件数、其他文件未动断言齐全）。

| 实例 | 安装包 | 新包 SHA256 |
|---|---|---|
| 1.20.1-main（Forge） | `atomchat-Forge-1.20.1-v0.4.0.jar` | `ed62fc2108c85d82a2b8f2fbdc16374819567accd3d8beec875b4891758886bd` |
| 1.21.1-CCB（Fabric） | `atomchat-Fabric-1.21.1-v0.4.0.jar` | `5792c50222013b72bb0e85936705b06839e39f2a1dff17d575b81d2b9dcf9da0` |
| Mechanomania-航空学（NeoForge） | `atomchat-NeoForge-1.21.1-v0.4.0.jar` | `b31d29cf66ad3566b092cbc047ca98e9eda90ef614616843fa559746823946ef` |

- 被替换旧包（与 STATUS.md 上一轮部署记录哈希一致，链条完整）：Forge `61e6208cc2138f5b…`、Fabric `c0fbc473f72d3a75…`、NeoForge `04900d177303e739…`。
- **孤儿清理**：`1.20.1-main` 与 `Mechanomania` 各混入 2 个错误 loader 的 AtomChat 包（此前轮次误拷；Fabric 实例干净）。按"目录里只应各有一份 AtomChat"移入备份目录 `removed-wrong-loader\` 留档（记录哈希），未删除、未动其他 mods 文件（脚本内 before/after 清单断言）。
- 三实例读回核验：安装包 SHA256 与构建产物一致、每目录恰 1 份 AtomChat（脚本二次独立校验全 PASS）。

## 未验证项（归用户）
- 游戏内观感：maxLuma 亮背景（雪原/沙漠白天）白字可读性、开关 knob 的 ~8% 过冲手感——需实机确认；`glassMaxLuma` 关闭即回旧渲染。
- GPU 帧耗与 Iris/光影组合下 rounded shader 表现未复测（shader 改动不触 alpha 通道，探针已证 alpha 恒等）。
- 未启动/关闭任何游戏实例；未 commit/push/tag/发布；版本号未动（0.4.0）。
