# 主题预设与窄窗口适配实施计划

**目标：** 保存、命名、应用、覆盖、重命名、删除个人外观预设，包含独立壁纸；修复小窗口和现有内容放大设置下的文字/控件重叠及不可达问题。

**架构：** 沿用现有 Skija 设置页、输入路由与 shared 纯 Java 文件存储；预设仅存外观白名单。官方映射层和 Fabric 镜像等价。单写执行者，负责人设计及验收。

**约束：** 不调整整体 UI 风格、字号层级或阴影；保留已完成的细外框未提交差异。不添加 ARM64。版本保持 0.3.0。不启动游戏；构建部署沿用已授权实例，正式发布需完成候选包验收。用户本轮先做两项，不提前发版。

## 1. 主题预设（独立可验收单元）

- shared 新增 `theme/ThemePresetStore.java`（必要时拆独立快照类型），存储位置 `config/atomchat/theme-presets/`。只从已知字段保存外观，使用随机 ID，显示名称不能参与路径拼接。
- 允许字段：ThemeService 的十二颜色与 panelOpacity/blurEnabled/panelOutline/cardTint；cornerRadius、panelWidth/panelHeight、uiScale/contentScale、animationEnabled/pageNavStyle、themeName。不包含头像、隐私、声音、服务器或聊天数据。
- 壁纸保存文件副本，不依赖外部来源路径。无壁纸预设显式清除当前壁纸。验证元数据和全部必需文件后才应用；保存/覆盖先完整写临时文件，提交后再更新索引；损坏文件不破坏当前设置。相同显示名称不混淆身份。
- 设置页管理入口在 APPEARANCE 的 theme 分组，沿用渲染和 InputHandler；支持中文命名、空名/长度验证、取消、返回、确认删除/覆盖；名字编辑不触发聊天输入、数值编辑或页面导航。不引入新 UI 框架。
- 两份 SettingsSectionPage 及必要输入接线同步；六份中英语言资源同步。
- TDD：临时目录测试保存→改变全部外观及壁纸→应用完整恢复；原图删除仍能恢复；无壁纸清除；覆盖/改名/删除/重启加载；无关配置保持；坏 JSON/缺壁纸/路径穿越/非法数值/过大文件拒绝且当前状态不变。UI 点击与输入至少一条真实集成回归。

## 2. 窄窗口/内容放大（独立可验收单元）

- 已定位 SettingsSectionPage.rowHeight 仅给说明换行；drawAction/drawSwitch 标题 truncate，右侧动作/开关会挤掉说明。使用同一份计算结果决定行高和绘制位置；宽度不足时控制移至独立行，标题和说明实际换行，不压缩用户所选字号。
- 检查 SLIDER/COLOR 的标题和值、分组标签、预设管理入口和页顶标签；修改必须由已证实的重叠或裁切驱动，不做观感重设计。
- 检查 ScreenMetrics 面板在极小窗口/最大缩放下的宽高是否正且落于视口；修复须同步绘制和点击坐标，不整体缩回内容字号。
- 回归覆盖 320×240、640×480、854×480、1280×720、1920×1080，uiScale/contentScale 边界，中英长标签；断言绘制/测量/命中一致，内容可滚达，画布保存/恢复平衡，离屏多帧绘制正常。不存在的缺陷不新增修补。

## 3. 验证与交付

- 先定向失败测试再实施；负责人检查真实 diff 和红绿日志；较大变更安排一次独立强模型审查。
- 静态检查：git diff --check、bare_scan（不抬基线）、gen_seams --check、twin_diff_report、verify_targets、verify_log_prefixes。
- 三端完整测试与 Windows/Linux 六包构建；verify_jars、verify_chat_assets；对应系统产物代码与资源一致。
- 部署 Windows Mechanomania-航空学 NeoForge 和 WSL Prism 1.21.1 Fabric，旧包先备份并核验哈希，不启动游戏。
- 更新 docs/STATUS.md 与精简验证记录，游戏内小窗口/大字号、中文命名和壁纸切换由用户验收，未做不得宣称通过。

## 当前进度

- [x] 确认范围及实现位置。
- [x] 预设实现及定向红绿测试。
- [x] 小窗口/大字号适配及定向红绿测试。
- [x] 独立审查、完整验证、构建、部署。
- [ ] 用户实机验收；通过后继续0.3.0正式版收尾。
