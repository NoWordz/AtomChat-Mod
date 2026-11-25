# 0.9.7 画质阶段验证

本文记录画质进入三端集成前的验证。标志、设置文案和取色遮罩的独立对照图见 [界面验证](README.md)。后续三端构建、设置回放及最终画质参数见 [0.9.7 验证记录](../../verification/2026-11-24-v097-compatibility.md)。本阶段的原始运行日志和 JUnit XML 留在本地。

## 接线与定向测试

- `GraphicsQuality` 和 `AtomChatConfig` 已接真实迁移、保存及重启的路径。旧布尔键在读入时归一为档位。预设页签恢复极致，关于页已去掉旧开关。
- 双映射设置页复用既有卡条的按压、横拖和松开判定。关于页的第三方标志与画质标志共用按压和悬停绘制，性能标志使用最近邻采样。教程为画质提供独立步骤，复用预设页的绘制锚点。
- Fabric 与 JDK 21 的配置迁移回归先复现旧配置保存时缺少 `graphicsQuality` 键，修复后通过。 [配置迁移测试源码](../../../shared/src/test/java/com/atom/chat/config/GraphicsQualityMigrationTest.java)
- 2026-11-24，Fabric 的 `compileJava`、`compileTestJava` 通过。启用 `ATOMCHAT_HUD_MEDIA_BENCH=1` 后，HUD 用例执行了 CPU raster 和隐藏 OpenGL 场景。连同画质迁移和性能策略测试，共 4 项通过，0 失败、0 跳过。该阶段缺独立审定输入，严格前端契约检查返回 `MISSING_EVIDENCE`，定向复跑单独执行。 [性能策略测试源码](../../../shared/src/test/java/com/atom/chat/config/PerformanceModeTest.java) · [HUD 测试源码](../../../shared/src/test/java/com/atom/chat/render/ChatHudMediaCostTest.java)
