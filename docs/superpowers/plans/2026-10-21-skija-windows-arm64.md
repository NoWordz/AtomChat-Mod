# Skija 0.143.18 与 Windows ARM64 迁移

目标：在模组版本保持 0.3.2 的前提下，试迁移到官方 Skija 0.143.18，提供 Windows x64、Windows ARM64、Linux x64 三种独立发行包。

结构：保留 Skija 直画、现有 shared/layers 与三加载器架构。Java API 与 native 使用同一精确版本；每包只登记一个对应平台 native。文件名统一为 `atomchat-<loader>-<mc>-<mod>-<os>-<arch>.jar`。测试使用构建主机的 native，而不是目标包的 native。

依据：官方 0.143.16 首次加入 Windows ARM64；0.143.18 为当前最新正式发布，含 Path.fillPath 修复及原生构建更新。没有官方“最稳定”评级，本轮以构建、既有回归和原生光栅验证评估适用性。

- [x] 依赖与 API：根 gradle.properties 单一定义 Skija 0.143.18，三平台 build.gradle 升级依赖及 HumbleUI types；按新版官方源码适配字体、路径和 GPU surface/flush 接口。新版编译先暴露接口断裂；保留既有行为测试，并为新版字体 null 返回补失败回归。
- [x] 打包：扩展 gradle/atomchat-native-platform.gradle；更新三加载器文件名、主机测试依赖。tools/test_verify_jars.py 先失败再修 tools/verify_jars.py；验证精确平台名、唯一 native、内嵌实际版本、Windows PE 和 Linux ELF 架构、Fabric/JarJar 登记。
- [x] 集成：更新 build.yml/release.yml 的构建、校验、取件和上传数量为每加载器三包/共九包；只修改配置，不触发发布。README/design/第三方说明与相关 Wiki 同步依赖和实验支持范围。
- [x] 验证交付：三端完整测试、九包构建及实际内容/一致性检查、四类守卫变异、既有 seams/targets/twin/log/bare 检查与独立代码复审均通过；docs/verification 与 docs/STATUS.md 已写入实际结果。Windows ARM64 与升级后 x64 游戏 GPU 验收待用户实机进行。

边界：不启动游戏，不部署现有实例，不提交/推送/tag/发布。没有 Windows ARM64 实机，不将打包成功描述成运行验收；升级后的 Windows/Linux GPU 效果也需重新实机确认。原有已验收包保留。
