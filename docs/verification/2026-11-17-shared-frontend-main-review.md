# 共用设置前端候选：主代理集中复审

> 2026-11-17收口更新：用户明确已验收UI。主代理本次重新核对三项修正源码、三端方向绑定、行为测试与最终中英图，限定增量关闭下列三个P2；追加cue接线及测试观察器也已读取。最后音效批次完整结果为2098/2081/2081，跳12/10/10，0失败错误；本次复核归档XML及三包SHA/CRC，沿用该批全量执行，不冒称新跑或实机FPS通过。历史“待复审”保留作时间记录，最新交接见[0.9.1交接](../handoff/2026-11-17-v091-next.md)。

2026-11-17。本轮用户通知实施代理完成后，主代理独立读取实际共享实现、与实施前SettingsSectionPage对照、三端宿主接线、行为用例、探针记录及同候选图片/动态帧。没有部署或起停游戏。

## 首轮结果：不放行，三项要求修正

1. **P2，设置页非数字行回归**：`SettingsSectionPage.compactText` 提取成 `SettingsRowText.measure` 后，原SLIDER/COLOR拥挤布局额外的 `SETTINGS_VALUE_BAND` 丢失。窄版非历史数字滑条/颜色行高度因此缩小，超出用户授权的设置页视觉调整范围。已要求按本轮归档before恢复，补测真实测量值，不能匹配源码代替行为。
2. **P2，断词排版**：实际打开当前 `pairs/zh_cn-420-dark-history-hosting.png` 看到MiB拆成Mi及B孤行，`pages/en_us-280-light-template-top.png` 中characters/message等拆到词中。server普通说明使用按码点wrap。要求采用已有按词折行、测量/绘制同源；设置原非数字行为保持，不以缩字或改文案遮盖。
3. **P2，方向动效未统一**：当前设置tab点击调用 `composite.navigationDirection(chip < activeIndex)`，server没有相应信号；向左切换/内部返回仍默认forward。现有快速反转测试0→1→2并不反转。要求三端方向接线、真实左右反转与内部返回验证，仍使用既有composite。

三项均交原唯一实施代理增量修正，主代理不并行修改生产代码。此前261/253/253结果对应修正前候选，不能直接当作修后结果。

## 已复核且范围有限的事实

- 普通输入事件现在传惰性剪贴板supplier，只有粘贴才读；读取失败保留草稿。模拟2ms provider的耗时不是Windows系统剪贴板实测，也不能量化Minecraft FPS改善。
- 输入测量缓存与1至4行自适应实现、光标/选区/指针使用相同行advance；当前单行格式居中问题已在软件离屏图中改正。OS输入法/真实帧时间仍未测。
- 服务端bodyVariant、固定chrome绘制、固定区逆变换排除和arrival时间戳已接入三端真实宿主。动态PNG含冻结离场内容，并非仅重新播放入场。
- 已看中宽暗色设置/托管并排、资源根页、表情包管理、常用语列表/单编、模板顶部/字段/真实样例预览、窄浅英文及SLIDE多个中间帧、折叠并排帧。
- 折叠/press的稳定逻辑命中是原设置既有行为，不能仅因没有反向press缩放就当作本轮新bug扩改。字段点选在折叠期间的边界仍需针对性核实。
- 所有图片为软件离屏生产组件/合成链；不是Minecraft窗口、世界背景、raw-GL、GPU、OS IME的验收证据。

## 后续

仅复核上述修正及受影响测试/产物/新图，再向用户展示。用户此后明确要求部署，三端测试实例已安装并独立回读；这不宣布整轮前端或实机性能通过。

## 三项修正回执（待主代理复审，不构成复审通过）

2026-11-17，唯一接替实施者已按首轮意见完成增量修正并冻结候选：

1. 普通SLIDER/COLOR的额外`SETTINGS_VALUE_BAND`已恢复；`SettingsResponsiveLayoutTest`用240/280真实字体测量验证，修改前159.3→129.3红，修后绿。历史数字仍走已批准的紧凑框+框外单位路径，普通设置其余布局未扩改。
2. server `rowText` 统一经`SettingsRowText.measure(..., words=true)`使用既有`wrapWords`，`SettingsRowText.draw`消费同一测量行；设置普通行仍`words=false`。中英280/420实图中`MiB`、`1–65536 MiB`、`characters`、`message`均保持词完整，字号和文案未改。
3. `ServerConfigPage`已接方向回调：tab新旧索引比较、进入/编辑下钻forward、内部返回backward；三端宿主都绑定同一`composite::navigationDirection`。行为验证含0→2→0、2→1、内部编辑/返回；新增TAB FORWARD、TAB 2 TO 1和internal forward/backward的SLIDE/ZOOM原始帧及GIF。

修正后的最终完整build为Fabric 2097（skip12）、Forge 2080（skip10）、NeoForge 2080（skip10），失败/错误均0。最终产物SHA见[实施记录](2026-11-17-shared-settings-frontend-implementation.md)。一次Forge筛选/中间完整build因Minecraft Bootstrap测试隔离失败，已用测试夹具修正并全量重跑；另有按目录的JAR验证因历史0.8.1残留产生歧义，未清理旧产物，改对最终三包显式路径验证全过。

本条只证明实施者完成修正和验证，**主代理尚未复审通过**，用户实机观感/IME/GPU/FPS仍未验收。用户随后明确要求部署，三端测试实例已于 `20261117-160428` 完成安装和独立回读；部署不改变“尚未复审通过”的记录，也不能替代实机验收。

## 追加音效修正（不在首轮三项内，尚未复审）

用户随后追问服务端页点击音效是否属于前端。实施者确认原实现只覆盖 `SettingsListAction` 按钮，tab 切换、内部返回/“完成”和开关提交静音；已增量补齐这三处 `BUTTON` cue，并新增真实点击测试，断言tab仅在实际切换时发声、内部“完成”发声、两类开关发声且状态变化。设置文本/数字输入仍静音。

次要胶囊的配色派生经代码复核为 `cardCutout` 表面加 `onAccent(surface)` 波纹/文字对比色；选中为运行时强调色加 `onAccent`，悬停为 `cardHover` 淡洗。本轮未新增固定色值，也未改 press/ripple 逆变换。

追加后的完整build为Fabric 2098（skip12）、Forge 2081（skip10）、NeoForge 2081（skip10），0失败/错误；新包已按`20261117-163726`部署并独立回读。该增量等待主代理复审；实际音频仍未经游戏内试听，不能把软件 cue 接线测试当作声卡/音量验收。
