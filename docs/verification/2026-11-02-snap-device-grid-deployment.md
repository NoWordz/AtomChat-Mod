# 设备像素网格修复部署（2026-11-02，0.4.0 内）

## 换的是什么

提交 `d833327e`（已推 `origin/main`，CI run 41405223734 四 job 全绿）。三处改动：

1. **设备像素网格修复**（唯一影响渲染的改动）：`SkiaGraphics` 用 `canvas.scale(scale)` 映射逻辑坐标到设备像素，屏幕却另用 `beginFrame(uiDensity / guiScale)` 告诉 `UiPixelSnap` 密度，两者差一个 GUI 倍率 → 吸附网格变成 2–4 设备像素。现在密度只由 `SkiaGraphics` 在 `canvas.scale` 旁写入，且在模糊 pre-pass 之前。
2. **CI 修复**：`GnetumHudCompat` 从"official 层 + Fabric 孪生两份逐字相同"移到 `shared/` 单一副本。
3. **注释清理**：源码/测试里 142 处上游项目名引用与"taken verbatim"表述删除。

## 产物

| 目标 | 字节 | SHA256 | 构建戳 |
|---|---|---|---|
| Forge 1.20.1 | 8458279 | `6dfb1a82c2a83fc381f0cc8b75527db8a8251004e4790195daf3ad108946502a` | `d833327` / dirty=false |
| Fabric 1.21.1 | 8479633 | `89cbe654cd11cde110be1293f9bcfe3abb985836c3dd62f481d685231c8387de` | `d833327` / dirty=false |
| NeoForge 1.21.1 | 8470731 | `315df7a20b546747ac37f8c14fdafe8d240133c26be4416150d119573ca5b3b6` | `d833327` / dirty=false |

产物门：`tools/test_verify_jars.py` 16 项 OK；`tools/verify_jars.py --profile atomlib-consumer` 三端全部通过。字节码级复核（Fabric 包）：`SkiaGraphics.draw` 里存在 `UiPixelSnap.beginFrame(float)` 调用且位于 `canvas.scale` 之后、`vanillaPass` 之前；`AtomChatScreen` 的 `beginFrame` 引用数为 **0**、`snapToDevice` 引用数 6（几何吸附仍在）。

## 替换结果（备份 → 替换 → 读回哈希）

备份根：`D:\AtomChat-Backups\2026-11-02-snap-device-grid\20261102-153018\<loader>\`
记录：`docs/verification/2026-11-02-snap-device-grid-deployment.json`

| 实例 | 新包 SHA256 | 字节 | 被替换（上一轮） |
|---|---|---|---|
| 1.20.1-main（Forge） | `6dfb1a82c2a83fc3…` | 8458279 | `9747498fe6d0a125…` |
| 1.21.1-CCB（Fabric） | `89cbe654cd11cde1…` | 8479633 | `57ee79663744bee5…` |
| Mechanomania-航空学（NeoForge） | `315df7a20b546747…` | 8470731 | `3bcd1ac7fd32ee1e…` |

链条完整：被替换哈希 = `2026-11-02-shadow-rim-axis-deployment.json` 的新包，逐一吻合。备份读回哈希与替换前的安装包一致。三实例 mods 目录各 1 份 atomchat 包，文件数替换前后不变（164 / 76 / 206）。

## 两处口径说明

- `mods_dir_files` 记的是非隐藏项，与早前台账同口径；`1.20.1-main` 与 `Mechanomania-航空学` 下另有 `.connector/`（Sinytra Connector）隐藏目录，既有且未触碰。
- CCB 实例是 **76** 而非上轮记录的 74：`mcef-libraries/` 与 `mcef-cache/` 两个目录在 2026-11-02 13:51/13:52 由 mod 运行时生成，不是本次部署产生的。

## 回滚

把备份目录下的三个 jar 拷回各自 `mods/` 覆盖即可，哈希见台账 `old` 字段。上一轮原始包还在 `D:\AtomChat-Backups\2026-11-02-shadow-rim-axis\20261102-134853\`。

## 未验证

实机观感（边缘锐度是否确实变好）未验：本轮只能给出字节码级证据 + 网格数学，需要进游戏看。三实例均需重启客户端生效。
