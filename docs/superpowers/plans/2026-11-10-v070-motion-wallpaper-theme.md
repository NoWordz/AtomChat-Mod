# v0.7.0 动画、壁纸与主题实施计划

执行方式：用户明确指定一个子代理实施，主代理审查、集成与自动部署，不再另问执行方式。最初并行拆分已按用户纠正停止，所有剩余实现由唯一子代理接手，不另派审查代理。产品决策以 [设计规格](../../specs/2026-11-10-v0.7.0-motion-wallpaper-theme.md) 为准。

**目标：** 恢复可连续中断的真实页面出场，实现底栏形变，完善透明黑色主题、静态壁纸预设、边框粗细与软阴影。

**架构：** 保留现有 Skija 与手写布局。动画模型提供当前绘制与命中共享几何，退场层只负责呈现。主题、壁纸与边框数据独立；壁纸后台生成静态 CPU 图像，避免把生成成本放进切页帧。

**技术栈：** Java 17/21、Gradle 8.8（Forge/Fabric）与 9.2.1（NeoForge）、Skija 0.143.18、现有 AtomLib 0.4.0 编译输入。三端 Forge 1.20.1、Fabric/NeoForge 1.21.1。

## 全局约束

- 不迁移 Yoga，不引入上一轮被拒绝的 G2 改造或幽灵色块退场。
- 不恢复模糊降频、冻结背景或半分辨率方案。
- 不修改 AtomLib 输入锁，不改实例配置，不启动游戏，不提交、推送或发布。
- 测试先红后绿，三端构建串行；日志留在 `.work/v0.7.0/`。
- 不改动原有 `.workbuddy/memory/2026-11-08.md`。

## 分工与步骤

### 1. 动画模型与接线（唯一实现子代理 Kant）

文件：`shared/src/main/java/com/atom/chat/nav/`、必要的 `ui/` 与 `input/`；official/Fabric 的 `AtomChatScreen` 为该代理唯一写集。对应 `shared/src/test/java/com/atom/chat/nav/` 与动画测试。

- [x] 写连续中断、反向返回、退场不交互、变换后命中的失败用例。
- [x] 实现真实页面内容同时入退场，保留当前画面接续，生命周期有界。
- [x] 实现底栏形变和焦点／草稿规则，接全局关闭动画。
- [x] 接外观粗细接口与阴影 helper；检查 official/Fabric 孪生。
- [x] 生产绘制路径输出连续帧，主代理查看中断、形变与退出内容。

### 2. 配置、主题与静态壁纸（已转交 Kant）

文件：`shared/.../config/AtomChatConfig.java`、`theme/`、`wallpaper/`，official/Fabric `settings/` 与语言资源；不写 screen。

对外接口：`AtomChatConfig.effectivePanelOutlineWidth()` 返回 0–4 逻辑像素；实际绘制使用 `UiTokens.s(width)`。`WallpaperStore.current()/isSet()` 保持，预设后台生成后交现有图像路径。

- [x] 先红绿验证旧配置 true→2／false→0、新配置→3、边界钳制与预设迁移。
- [x] 用粗细滑条替代开关，完善保存、应用、默认值与白名单。
- [x] 实现蓝色强调不变的黑色透明默认配色，不重置历史用户自定义。
- [x] 壁纸选择独立于主题；仅新配置首次设置默认壁纸。
- [x] 用静态 SkSL 生成渐变、几何、流体、光带、层叠与亮款，提供加载／失败状态。
- [x] 实际编译 shader、输出预览、测试无每帧生成与缓存失效。

### 3. 阴影根因与性能（已转交 Kant）

文件：`shared/.../render/` 的阴影 helper、相应测试；screen 改动由 Kant 接线。

- [x] 定位回底部按钮硬边阴影的真实绘制／裁剪路径，先复现再修复。
- [x] 审计同类浮层阴影边界，新增手机外阴影与半屏可见边缘阴影。
- [x] 真实绘制路径输出软衰减与动画边界证据，检查分配／缓存成本。

### 4. 主代理集成、审查与部署

- [x] 核查工作区基线、配对库和三实例位置；保留上一轮回退记录。
- [x] 汇总接口，按代理定向测试→三端全量套件→开发绘制探针顺序验证。
- [x] 主代理审查实际 diff，处理确认的问题并回归；依用户“只用一个子代理”的最新限制，不另派 code-reviewer。
- [x] 版本改为 0.7.0；按 changelog-format 写中英文说明，不承诺未测 FPS。
- [x] 三端完整构建，运行目标、孪生、裸值与制品检查。
- [x] 检查目标进程、加载器与配对库 API；未运行目标才能替换。
- [x] 完整备份旧 AtomChat，安装新包，读回 SHA-256，确保每实例仅一个启用的 AtomChat，AtomLib 和其他模组保持哈希。
- [x] 写部署回执和当前状态，披露真实游戏首次／连续切换验收是否完成。

构建输入：`D:/AtomChat-Main/build/atomlib-slim-20261107/atomlib-maven-v040-final`。每个平台目录执行 `./gradlew build --offline --no-daemon --console=plain -PatomlibMavenRepo=<该路径>`；Forge 使用 JDK17 与既有证书检查参数，Fabric/NeoForge 使用 JDK21。NeoForge 开发资产下载若影响纯构建，应先核实任务依赖并记录处理。

部署目标：`D:/Myworld/.minecraft/versions/{1.20.1-main,1.21.1-CCB,Mechanomania-航空学}/mods`。当前均存在启用 AtomChat；本轮不自动启用任何 `.disabled` 文件。

## 最终结果（2026-11-11）

三端 v0.7.0 完整 build 与 1780/1765/1765 项测试通过（各7个既有skip）；空状态入退出与逆变换时钟回归已补，普通帧额外GL scope已移除。三实例安装哈希一致、旧包备份完成、AtomLib与其他模组未变。最终复核和部署事实见 docs/verification/2026-11-11-v070-review-and-deployment.md；真实游戏体验仍待用户验收，未启动游戏或提交发布。
