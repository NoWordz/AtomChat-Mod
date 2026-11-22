# AtomChat 0.9.5 圆角、抗锯齿与 HUD 计划

以 20:01 部署的 0.9.5 为对照。按原来保存的圆角数值提高连续曲率角的圆润度，将次级胶囊背景最低不透明度从 85% 调到 70%，核实残余锯齿、三端 Yoga 崩溃风险，以及「心自安然」的聊天 HUD 闪回。

## 1. 连续圆角

- [x] 用 `CornerGeometry` 的现有曲线做对照。在小、中、高圆角数值，以及矮栏、普通卡片两种尺寸下，比较延伸长度和转角 45° 切入量。输出同数值的原尺寸和放大图，以普通圆弧提供圆润度参照。
- [x] 比较延伸长度映射与适度调整曲线控制点。保持原延伸长度和栏位，将每轴 45° 切入比例从 0.2063 调到 0.224，增加约 8.6%。直边和新轮廓的端点一致，曲角落在原轮廓范围内。Skija 矢量、局部覆盖及三端模糊着色器使用同一曲线。 [原尺寸几何对照](D:/AtomChat-Backups/2026-11-22-v095-followup/corner-proposals-1x.png)
- [x] 用真实渲染路径复核上下栏、胶囊及常见卡片。检查当前滑块数值对应的形状，没有轮廓截断或命中偏移。

## 2. 次级胶囊

- [x] 在 `AtomChatConfig.effectiveSecondaryCapsuleBg` 把背景最低不透明度调为 70%，对应 8 位 alpha 179。原阈值是 85%，对应 217。沿用配置 RGB 和用户设置的更高不透明度。
- [x] 用原生 HUD 像素和设置值同时验证低于及高于阈值的配置，并确认面板系统消息、时间分隔、引用与聊天 HUD 读取同一方法。 [像素回归](D:/AtomChat-Backups/2026-11-22-v095-followup/capsule-green.xml)

## 3. 局部抗锯齿

- [x] 用隐藏 OpenGL 对比 16 类边缘、4 档倍率和 8 个像素相位。连续圆角的直接局部填充误差已低，原生裁切与未投影的图片回放误差较高。 [基线原尺寸](D:/AtomChat-Backups/2026-11-22-v095-followup/aa-edge-comparison-1x.png)
- [x] 给滚动玩家菜单接入局部分组裁切，圆形图片使用单次着色器覆盖。候选原生连续裁切与局部裁切的覆盖误差为 19.34 和 0.89，圆形图片为 2.78 和 1.07。每轮 48 个图形的同步 GPU 探针中，局部裁切 p50 增加约 0.46 ms，图片增加约 0.05–0.08 ms。细小角和未投影回放仍有残差。发送和设置图标的覆盖统计与基线相同。 [原尺寸](D:/AtomChat-Backups/2026-11-22-v095-followup/aa-clip-image-1x.png) · [边缘数据](D:/AtomChat-Backups/2026-11-22-v095-followup/aa-edge-results.json) · [GPU 探针](D:/AtomChat-Backups/2026-11-22-v095-followup/aa-local-cost.log)

## 4. 三端 Yoga 报错

- [x] 附件对应 Forge 上的 AtomChat 0.9.1 和 AtomLib 0.6.0。绘制设置时找不到 `lwjgl_yoga.dll`。
- [x] 三端按活动 JAR 字节复制到离线实例。在真实加载器进入本地世界后，从渲染线程各调用两次生产 `SettingsYogaLayout.layout`，三个目标均通过，实际加载的 Windows x64 DLL 与配套 AtomLib 0.7.1 的内嵌文件一致。 [逐端核验](D:/AtomChat-Backups/2026-11-22-v095-followup/yoga/verification.json)

## 5. 「心自安然」聊天 HUD

- [x] 只读核实实例安装和配置。NeoForge 的 AtomChat 指纹为 `52053a367825`，HUD 已启用，Gnetum 为 3.3.6。
- [x] 用原版本和 20:01 版在隔离实例复现。默认聊天层进 Gnetum 第 3 个缓存 pass，AtomChat 拒绝非主帧缓冲，原版聊天绘出。开关面板临时进入主帧缓冲的直绘阶段。仅把 `minecraft:chat` 改到 uncached 后，AtomChat 稳态绘制恢复。 [调用顺序](D:/AtomChat-Backups/2026-11-22-v095-followup/hud/HUD-TRACE.md) · [逐帧对照](D:/AtomChat-Backups/2026-11-22-v095-followup/hud/trial-frame-counts.json)
- [x] 将 NeoForge 聊天层放在 Gnetum 的主帧缓冲通道。隔离测试通过了 Gnetum 开启、关闭、未安装与原版输入，并对最终 JAR 重跑 Gnetum 开启。HUD 开关与面板切换期间没有同时画出两套聊天。 [最终包逐帧验证](D:/AtomChat-Backups/2026-11-22-v095-followup/hud/candidate-hud-verification.json)

## 交付

- [x] 汇总五项结论和对应证据。只合入验证支持的改动，集中审查真实入口和三端兼容。
- [x] 运行 Fabric、Forge、NeoForge 的构建、普通回归、正式回放与受影响的原生渲染检查。按已授权三端目标备份、安装并读回；「心自安然」的诊断使用副本或只读原实例资料。记录精确产物和仍需原场景验收的项目。


## 交付记录

- 连续圆角的 45° 切入加深约 8.6%，沿用原延伸长度和栏位。次级胶囊背景最低不透明度降至 70%，更高自定义值按原值使用。 [设置页原尺寸对照](D:/AtomChat-Backups/2026-11-22-v095-followup/settings-history-1x.png)
- 滚动玩家菜单及图片边缘改为局部覆盖。GPU 测得连续裁切与圆形图片的候选边缘误差分别为 0.89 与 1.07，图标覆盖保持一致。最低测试半径和未投影回放仍有覆盖残差。 [量测与参考](D:/AtomChat-Backups/2026-11-22-v095-followup/aa-edge-results.json)
- 旧 Forge 包的 Yoga DLL 报错在目前三端未复现。三端的实装 20:01 包、原生库及真实加载器已分别核对。NeoForge 隔离复现了 Gnetum 缓存帧缓冲导致的原版 HUD 回退。修复把聊天层放回主帧缓冲，开关面板及原版输入验证通过。心自安然的原服务器未连接。 [三端 Yoga](D:/AtomChat-Backups/2026-11-22-v095-followup/yoga/verification.json) · [HUD 逐帧记录](D:/AtomChat-Backups/2026-11-22-v095-followup/hud/trial-frame-counts.json)
- 三端正式源码与各 196 个场景通过。全量 6,786 项中 54 项跳过、零失败。审定 `71c3158238af9aae39fcedd1c078cdfab2394812`。 [构建核验](D:/AtomChat-Backups/2026-11-22-v095-followup/verification.json)
- 20:48 部署到 Fabric CCB `89b413d7dd8c`、Forge 1.20.1-main `62b4c8010ed8` 和 NeoForge 航空学 `afd095b5415a`。旧包和配置已备份，新包逐端读回。 [部署回执](D:/AtomChat-Backups/2026-11-22-v095-followup/deployment-readback.json)
