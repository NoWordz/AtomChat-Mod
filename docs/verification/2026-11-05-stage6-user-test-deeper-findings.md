# Stage6 用户实测：长帧、资源抖动与发送前后差异

## 范围

本轮只保存证据与查源，不改生产代码/包/配置，不启动游戏。用户正在自行对照0.3.0-Preview。用户反馈：首开骤降后280–300，发送消息后>300；emoji展开掉帧；导航聊天300–350、个人300–330、设置导航370；字多设置250–300、关于200–250。Euler已有修复效果得到用户确认，不能回退。

## 环境证据

本轮有效录制是归档 logs/2026-11-05-6.log.gz，16:44:20启动、16:49:52结束；加载AC0.5.0与Lib0.3.1-shadowcache。已复制归档和解压原文至 D:/AtomChat-Main/build/deep-performance-20261105/，解压文本SHA256 4ba883375c9ea786ff009a4f71f13cf0f00ec115f376cbeba02a42c5727ceb5c，94份滚动profile解析为profile-reports.json。日志版本号与当前已替换磁盘包不可混为一谈，版本号本身不能进一步唯一证明同名JAR的每个字节。

latest.log在分析前已被16:54:50的新启动覆盖，5394字节，预览包报zip END header not found；这次预览启动失败不能作性能对照。未修改用户包。若用户自行完成后续启动，需另存新日志并绑定版本，不能沿用该失败结论描述未来状态。

## 一：首开有真实218ms长帧，字体同步冷加载是候选

归档原文1047–1066行：16:46:38 PANEL_OPEN，16:46:39在Render thread Loaded bundled font，首600帧panel max218.23ms、chat_interval max232.50ms；稳定panel p50为2.29ms。同步加载发生在FontManager.getDefaultTypeface:35/42，decodeTypeface:83，font:105/111，包含资源读取、Data拷贝、FontMgr解码、variable clone。

确定事实：首开发生大长帧；首次字体读取在交互绘制线程同步执行。尚未证明218ms全部由字体造成，首次桥接/着色器/字形上传也在同窗口。应在每一步加有界一次性stage计时，验证后在加载/资源重载阶段预热，不能简单把阻塞挪到别的交互时刻。

## 二：开合期间模糊目标尺寸变化导致真实资源重建

AtomChatScreen.java（Fabric net/minecraft/client/gui/screen）:1989把panelProgress送入PanelOpenMotion.blurCapture，结果尺寸传给PanelBlurRenderer.render。该类:83–88把变化尺寸转换像素；ensureTextures:279在宽高任意不同后:285 destroyTextures，:287计数一次，随后创建三套texture/FBO并glTexImage2D。

首窗口BLUR_RESOURCE=7；稳定窗口为0；第二次PANEL_OPEN在归档3570行，16:49:47窗口3600行BLUR_RESOURCE=21。计数单位为一次三目标重建，不是21个对象；滚动窗口也可能含关闭过程，不把21直接归给单一打开动作。此路径足以确认动画关联的分配抖动，但还缺每次重建的耗时与宽高序列。

优先验证：记录旧新尺寸/纹理generation/分配时间，在保持动态背景、采样UV、半径和裁剪不变前提下研究稳定容量/尺寸分桶。不能直接固定capture坐标造成采错背景，也不能只改Lib后误以为AC私有PanelBlurRenderer已复用。

## 三：首次emoji139ms长帧与同步字形栅格化同窗

归档1191行开始首次emoji桶，max139.08ms，之后固定样本980帧、p50约2.13ms。EmojiPanel.java:719–751绘制可见字形；EmojiGlyphCache.java:34–44 miss或更高scale会同步rasterize；:63–77创建CPU Surface、drawString和snapshot。打开时EmojiPanel.toggle:200–205还同步refreshEmotes，需分别测而非把139ms全归glyph。

验证：记录字体fallback、每帧glyph构建数/耗时、2x→4x→8x档位变化、目录扫描，区分首次与重复展开。可预算预热，必须覆盖下一次资源代与总字节预算，不采取缺图/闪烁/跳过emoji充当提速。

## 四：发送消息后变快，现有证据只支持部分解释

发送事件1142行16:46:57。之前16:46:55滚窗chat_interval p50已达3.33ms（约300），之后3.31ms、3.28ms，升温趋势在发送前就存在。空态确实通过MessageListView:320调用EmptyState:35/38的额外点阵；有消息则跳过，属真实分支差异。LIST跨窗口约0.27→0.13–0.18ms，但不是隔离A/B，不能把差额精确全归占位图。

重要纠错：单个218ms样本退出4096帧窗口，主要改变max与mean，不能解释p50整体提升，更不能解释Minecraft独立FPS显示。不能用“统计窗口假象”否认用户实测。缓存预热、空态装饰差异是候选，尚无证据说明消息有无是主要因果。

最小因果对照：同一热态、同世界镜头，空→有消息→再次空，记录page/empty/glyph/cache/CPU与GPU；若恢复空后不变慢，更支持预热；若恢复空可重复变慢，才优先查空态分支。用户本轮先做旧版对照，不再加新测试负担。

## 五：持续低帧目前仍缺GPU实测，观测链没有闭合

本轮BLUR CPU p50约0.14–0.24ms，不代表5个全分辨率pass的GPU耗时。PanelBlurRenderer:349刷新链仍有capture+5pass+合成。GpuTimerQuery.java:16–19明确未接入生产；全源码搜索只有类定义，无真实调用者。现有log不含GPU完成时间，不能宣称“已测GPU排除模糊”。

此外FrameProfile.java:168按surface分桶，而设置/关于/个人/导航都落OTHER；旧的OTHER滚动4096帧会混页，不能用某个OTHER p50直接命名“关于页实测”。需要pageKey、过渡phase与首开标志，复用现有FrameSegments而非重造诊断体系。

DOTS是drawEdge/halo等装饰真实墙钟段，跨页面约0.56–0.91ms，但已包含在DRAW，不能相加。About的静态halo收益不能扩写成全局所有edge与empty都已烘焙；目前小halo仍Picture回放。下一步要对真实生产drawEdge/blur进行受控GL对照，不重走把微基准当总FPS的路。

## 下一步与交接

1. 优先验证并消除首开冷加载和开合blur目标抖动；emoji首开单独计时。
2. 接好有界、跨帧非阻塞GPU计时与page/phase关联，再选择blur/合成优化；不用glFinish污染正常帧循环，避让其他mod占用的query。
3. 消息前后只做热态可逆对照，不能按操作顺序直接推因果。等用户旧版有效结果后，用同条件和准确包版本比，不更换其现场。

本轮没有修复声明、没有编译或回归（未改生产代码），没有部署。旁路代理只读报告在build/deep-performance-20261105/empty-vs-populated-code.md，主报告以上述统计与范围修正为准。
