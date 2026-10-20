# 0.3.1 点阵材质与气泡预览实施方案

**目标：** 保留手机风小面板密度，用可见的三款气泡预览、连续贴边点阵和克制的栏体细节统一外观。

**已确认设计：** C 画法（原有渐隐点阵＋淡柔光），不留接缝；点阵常开，无开关；顶栏向下，输入栏和导航底栏向上；关于页 Logo 背后向四周渐隐；折叠标题文字后方静态向右拖尾。局部高光和内阴影只用于三种栏体。空会话页、普通卡片、弹窗不扩展。

**结构：** 使用现有 Skija 直画、主题 token、四角 RRect 气泡原语。共享 `DotTexture` 提供栏边、Logo、标题三种有限绘制入口；`ChromeMaterial` 负责栏体表面的高光/内阴影。装饰先画、内容后画，所有裁剪和 save/restore 成对，不修改布局或输入区域，不引入库或动画。

## 实施顺序

- [x] 先补失败测试：三卡直接选择且窄窗口可达、点阵开关消失、旧预设忽略旧点阵字段；真实光栅验证贴边/圆角/主题透明度/裁剪/静态右拖尾。
- [x] 在 `shared/src/main/java/com/atom/chat/render/` 实现 `DotTexture.drawEdge(Canvas, UiLayout.Rect, float, boolean, int)`、`drawHalo`、`drawTrail` 和 `ChromeMaterial.drawDetails`。上下面向使用同一边界几何，保留原点阵的点距与半径范围。移除原 `MessageEdgeDots`，点阵由栏体而非消息视口定位。
- [x] 修改两份 `SettingsSectionPage`：三款预览卡以实际 `ChatBubble` 原语绘制左右气泡、使用当前主题；窄宽度自动换行，点击指定款式而非轮换。关于页和所有可折叠标题接入静态点阵。两份 `SettingsActions` 使用 `bubble_style:CLASSIC/ROUND/CORNER` 直接保存。
- [x] 删除 `AtomChatConfig`、`AppearancePresetStore`、两份 `SettingsCatalog` 的点阵布尔字段及语言键。Gson 自然忽略旧配置字段，旧外观清单仍可加载，新的清单不再写点阵字段。
- [x] 接入共享 `ShellHeader`、`BottomTabBar` 和三个平台的 `AtomChatScreen`：阴影→点阵背景→内容→栏体→细节→控件，保证消息/卡片在装饰之上。标题拖尾止于折叠箭头之前，Logo 点阵限定在品牌区。
- [x] 定向测试后跑三端完整 test/build、seams、targets、twin、日志、裸值与 diff 门；生成真实 Skija 离屏对比，核对深浅主题和窄宽度；安排独立强模型审查并处理实际问题。更新现有 0.3.1 日志/状态/验证记录，不自动提交、部署、启动游戏或发布。

**验证边界：** CPU 光栅和构建可验证接线、形状、裁剪与交互；实际游戏 GPU 帧耗和最终观感仍需实机反馈，不能以单测通过代替。
