# 超预算图片预处理：离屏验证

图片降采样的缓存预算为 4,194,304 像素。原先单张派生图超预算时，仍先创建、再关闭并回退源图；重复绘制会重复分配和缩放。当前对无需中间减半且能准确推算最终尺寸的情况，在生成前判断预算；有中间减半的路径仍保留原有生成后检查。小于或等于预算的派生图仍按原逻辑缓存。

Windows x64、JDK 21.0.11、Skija 0.143.18 CPU raster；构造 3076×3076 不透明图片，以设备宽度 1025 调用，目标派生图为 2050×2050，超过预算。每版独立启动 JVM，预热 3 次、测 12 次；使用相同的本地 AtomChat NeoForge 候选，前后仅替换图片路径源码。开启 Skija native call 统计时，原实现热样本中位 87.213 ms，范围 82.600–93.061 ms；修改后中位约 0.001 ms，范围 0.000–0.001 ms。原实现 12 次调用合计 120 次 native call；修改后为 0 次。结果来自合成单图，不代表游戏 FPS、常见聊天图片或 GPU。

可重复探针：`tools/probes/OversizeImageBudgetProbe.java`；用 Java 21 的 source-file 模式运行，并将待测 AtomChat JAR、`skija-shared-0.143.18.jar`、`skija-windows-x64-0.143.18.jar`、`types-0.2.0.jar` 放入 classpath。需分别以改前和改后源码构建的 JAR 测量；探针不是游戏内性能测试。

回归测试先在旧实现上失败：单次方图超预算请求触发 11 次 native call，超过测试的 4 次上限。提前判断后转绿；新增回归覆盖方图、竖图，既有用例覆盖预算边界、缓存命中、跨来源淘汰、源图所有权及三个绘制入口。Forge 1.20.1、Fabric 1.21.1、NeoForge 1.21.1 在隔离源码副本中各执行 `test --tests com.atom.chat.render.ImageQualityTest` 通过，使用本地 AtomLib 0.2.0 候选；未启动游戏。
