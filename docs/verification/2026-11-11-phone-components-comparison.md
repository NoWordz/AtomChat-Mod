# 手机体验第三方组件比较

结论：本轮不新增依赖。以下是接入前的能力评估，不是三端兼容或性能认证。首先把现有滚动、手势与动效契约补稳；需要成套控件时，再对 Modern UI 做单页隔离原型。

| 路线 | 能补什么 | 对当前工程的代价与边界 | 建议 |
| --- | --- | --- | --- |
| 现有 AtomLib + AtomChat | 保留 Skija 材质、弹簧、滚动、菜单和输入；补滑动返回等具体能力 | 无新增框架包体；仍需自行维护手势仲裁、速度连续性和无障碍行为 | 当前优先，按实测短板改进 |
| [Modern UI / MC](https://github.com/BloCamLimb/ModernUI-MC) | 面向 MC 的完整 UI 集成、文本与控件体系 | 框架级接入，须核对三个具体 MC/Loader 版本、渲染与字体接管边界；不能当几个独立 Java 按钮复制进来 | 最值得做独立原型的成套方案；不直接迁移 |
| [Yoga 纯 Java 移植](https://github.com/AppliedEnergistics/yoga) | Flexbox 布局、测量约束 | 只解决布局，不包含手机导航、手势、主题或业务状态；AtomLib 已有 Yoga 能力，先评估现有实现，避免重复引入 | 后续复杂响应布局再比较 |
| [libGDX Scene2D UI](https://libgdx.com/wiki/graphics/2d/scene2d/scene2d-ui) | 场景树、事件与布局、ScrollPane、文本框和通用控件 | 须桥接另一套 Stage/Actor、批绘和输入焦点到当前 MC/Skija；现有界面样式也需另做皮肤 | 对本项目成本偏高，暂不选 |
| [AndroidX DynamicAnimation](https://developer.android.com/jetpack/androidx/releases/dynamicanimation) | Android 平台弹簧和惯性 API | 这是 Android 平台组件，不是 MC 桌面控件；官方已标维护模式。可研究物理模型，不能直接作为手机化开关 | 仅算法参考，不加依赖 |

以上接入成本为基于当前双映射源码、Skija 绘制和输入路由的工程判断。未下载候选产物、未测试三端桥接、未量测新增包体，因此不提供虚构的 MB 或 FPS 数值。

下一次选型应以同一原型比较：一页可滚动列表、一层可打断详情页、滑动返回与纵向滚动冲突、弹窗焦点、中文长文本、GUI 缩放、资源重载。先测 CPU/GPU 帧耗与 native 资源生命周期，再决定是否承担迁移。

当前自身能力依据：`shared/src/main/java/com/atom/chat/ui/ScrollController.java:14`、`UiSpring.java`、`PressScale.java`，以及 `nav/InterruptiblePageMotion.java`。框架更换不会自动修复坐标、裁剪或重复动画；本轮先修已证实根因。
