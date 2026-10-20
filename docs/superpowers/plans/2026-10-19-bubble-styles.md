# 0.3.1 气泡样式与底部网点实施方案

> 按本会话用户“写个方案然后开工”的授权直接实施；负责人完成实施与验证，最后安排独立强模型审查。沿用当前仓库，不启用额外协作工作流。

**目标：** 保留手机风小面板的布局密度，提供经典、大圆角、单角尖角三款本地统一气泡，并在消息区底部增加随主题协调的渐隐网点。

**架构：** 配置及外观预设保存本地气泡样式和网点开关。新增共享的气泡几何/绘制与底部网点绘制类，两个 MessageListView 映射副本只接入绘制。测量、命中、滚动、字号、昵称区与消息间距保持原值。

**技术：** Java、Skija 0.116.8、现有 JUnit 离屏光栅、三端 Gradle。优先借鉴 Tuui 的 CanvasExtensionKt 四角 RRect、图片同形裁切及 save/restore 用法；网点规则参考本机 chatcore 的 ChatBoxWidget，不复制第三方源码或引入 ModernUI。

## 约束与设计

- 三种本地样式：CLASSIC（默认，半径 s(12)）、ROUND（半径最多 s(22)，同时不超过短边一半）、CORNER（ROUND 基础上头像侧上角为 0，自己右上、他人左上）。短文本与多行采用同一规则，无外伸尾巴。
- 所有玩家统一使用本地选择，不同步网络。文本、图片及图片占位一致接线；系统消息、引用胶囊、反应贴纸保持自身形状。
- 保留气泡尺寸与内边距；为避免超大圆角裁住多行或图片，实际光栅核对短文本、长文本、多行、图片与两侧镜像。
- 点阵仅在消息视口底部约 s(32) 深区域作为背景绘制，使用主题次要文字色，朝上透明、点径递减。参考原图底部九排网点，把原 alpha 48..160 的强度收敛为约四分之一作为小面板首轮候选；最终观感待用户实际比较。
- 点阵默认开启，外观页可关闭，随个人外观预设保存；它不增加布局槽位，不跟内容滚动，不遮挡或参与命中。裁切严格限制在消息视口及已有面板裁切内。
- 旧配置/旧预设缺字段时回到经典与开启网点；未知气泡枚举安全回到经典，避免整个配置丢失。
- 源码版本升为 0.3.1。构建本地三端 Windows/Linux 六包，不发布、不推送；部署不属于本轮已明确目标。

## 文件责任

- `shared/src/main/java/com/atom/chat/config/AtomChatConfig.java`：气泡枚举、默认值与反序列化归一化。
- `shared/src/main/java/com/atom/chat/render/ChatBubble.java`：单一 RRect 几何及文字底板/图片裁切。
- `shared/src/main/java/com/atom/chat/render/MessageEdgeDots.java`：受限网点几何与背景绘制，尺寸不变复用缓存、原生资源及时释放。
- `shared/src/main/java/com/atom/chat/ui/UiTokens.java`：新增具名气泡/网点参数。
- `shared/src/main/java/com/atom/chat/theme/AppearancePresetStore.java`：扩展快照及旧版兼容读取。
- 两份 `settings/SettingsCatalog.java` / `SettingsActions.java` / `SettingsSectionPage.java`：外观设置入口、循环样式选择与描述。
- 两份 `page/MessageListView.java`：背景点阵与三种玩家消息绘制接入。
- 三端中英语言文件、`gradle.properties`、版本日志与当前状态：同步用户可见说明及验证事实。

## 实施步骤

- [x] 1. 先加真实光栅与持久化行为测试：经典/圆润/尖角区别、镜像、图片裁切、画布恢复、网点渐隐/裁切/主题色、预设保存恢复及旧字段兼容。运行定向测试，记录缺失功能导致的失败。
- [x] 2. 用 Tuui 的四角 RRect 方式实现共享气泡绘制；实现受限且缓存的网点；复跑光栅测试，输出相同大小的三款气泡与点阵开关对比。
- [x] 3. 配置、外观预设、设置操作与中英语言接线，回归旧配置、旧预设、实际点击、保存恢复。
- [x] 4. 两份 MessageListView 接线，测试高度、消息命中和滚动不随样式/网点改变，检查实际离屏消息页。
- [x] 5. 更新 0.3.1 版本与中英日志；运行三端测试、seams、targets、孪生、日志出口、裸值与 diff 闸门。每个平台用自己的 Gradle wrapper，`--no-daemon`，定向测试先于完整测试。
- [x] 6. 构建三端 Windows/Linux 六包并核对包内版本、native 和同 loader 两 OS 应用资源一致性；独立强模型审查整批差异并修正发现，按实际改动追加必要验证。
- [x] 7. 更新 STATUS 与 verification 记录，交付预览和本地产物；明确离屏检查范围及尚未做的游戏内视觉验收。

## 测试命令

在 `platforms/1.21.1-neoforge` 先运行 `./gradlew.bat test --tests '*ChatBubbleTest' --tests '*MessageEdgeDotsTest' --tests '*AppearancePresetStoreTest' --no-daemon`。所有实现完成后，依次在三个平台运行 `./gradlew.bat test build --no-daemon`。包的构建任务按现有 `atomchat-native-platform.gradle` 的双 OS 约定执行。

仓库根运行 `python tools/gen_seams.py --check`、`python tools/verify_targets.py`、`python tools/twin_diff_report.py`、`python tools/verify_log_prefixes.py`、`python tools/bare_scan.py` 和 `git diff --check`。新共享接缝先运行 `python tools/gen_seams.py` 更新生成物。

验收依据为真实生产绘制路径的像素和交互结果；不以常量相等或源码字符串命中替代行为测试。视觉通过仍由用户在实际小面板中确认。

## 完成记录

2026-10-20 本地候选完成，实际命令、三端统计、独立审查、变异验证、预览与六包哈希见 `docs/verification/2026-10-20-bubble-styles.md`；游戏内观感待用户验收。
