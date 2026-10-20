# 0.3.2 图片预览、弹层裁切与过滤记忆

基线：main@2e6c3ac。用户确认上一轮已提交，本轮完成列表前五项；第六项仅提供功能建议。源码版本为 0.3.2，已按用户后续指令部署 CCB Fabric，未提交、推送、打 tag 或发布，未启动游戏。

## 实现与证据

- `ImagePreviewOverlay` 原先仅遮罩与按钮带透明度，图片首帧就是不透明；现在图片同步淡入，以现有指数收敛控制轻缩放与较快退出，结束后不再分配透明图层。
- 适配大小为最小倍率，滚轮放大到最多 8 倍；平移按图像与独立视口限位，左键拖动直接跟随指针，松手停止。底部关闭区不参与图片裁切，背景/关闭键/Esc 继续关闭；重新打开恢复初始视图，非法滚轮输入被忽略。
- 连续滚轮保留目标倍率累积，但每次从屏幕当前显示的 `zoom/pan` 重新计算锚点，避免前次缩放尚未落定时移动指针造成漂移。
- 三种弹层原先使用 `radius(12)` 遮罩，面板外框为 `panelRadius()`，且弹层无统一内缘裁切；现在 `PanelBackground.clipInterior` 按实际边框内缩和圆角裁切完整弹层，official/Fabric 副本均接线。
- 默认主题卡原先独立判断空主题/FROSTED，漏掉其他内置卡已有的“无匹配保存预设”条件；现在保存预设匹配时所有内置卡都不显示选中边框。
- `WorldFeedFilter` 不再以新屏幕实例字段初始化 ALL，而从 `AtomChatConfig.worldFeedFilter` 实时读取，切换立即保存；缺字段、空值或无效值回退 ALL，其他配置保留。消息存储、未读与私聊规则保持原语义，外观快照不包含过滤偏好。
- 三端 `ModalInput` 已接入预览的按下/拖拽/松手/滚轮；右键不启动拖动，事件不继续传给底层列表。

## 回归与审查

- 首轮三个真实像素回归 3/3 按预期失败：图片首帧 alpha=255、保存预设后默认卡多出 1273 个像素差异、弹层在边框采样点 alpha=200。日志 `.work/0.3.2-fabric-red.log`、`.work/0.3.2-neo-red.log`。
- 手势测试先以缺失的预览缩放/拖动接口编译失败；实现后覆盖指针锚定、最大/最小倍率、拖动、松手、关闭区、重新打开和无效输入。
- 独立强模型审查发现连续滚轮换锚点漂移、旧 `MessageListWiringTest` 依赖实例重置两项 P2；新增换锚点序列与既有列表测试在 `.work/0.3.2-review-red.log` 中 21 项/2 失败，修正后专项 60 项全部通过。
- 首轮完整测试另发现新主题视觉测试的动画开关污染旧菜单测试；补齐逐例临时配置与 provider/config 恢复，没有修改菜单生产代码。最终完整测试已覆盖相邻用例。
- 独立只读复查确认原两项 P2 消除，本次审查范围无阻断；审查者未重复运行构建。
- 三种弹层分别做 Skija CPU 光栅检查，覆盖方角、默认圆角和较大圆角、四角与外框留白、Canvas save/restore 平衡；预览图 `.work/0.3.2-preview/modal-{0,1,2}.png`，已查看取色器与裁剪图。裁剪图使用加载占位，不能替代真实图片裁剪实机验收。
- 过滤持久化 23 项用例覆盖新实例、磁盘 reload、切页、图标/消息可见性、非法值与旧配置；最终三端均通过。

## 最终命令与结果

命令在 Git Bash 运行；Forge 使用 JDK 17.0.18，另外两端使用 JDK 21.0.11。

```bash
./gradlew test build --offline --no-daemon -I ../../.work/layout-local-mirrors.gradle
```

| 目标 | 测试总数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| NeoForge 1.21.1 | 1355 | 0 | 0 | 2 |
| Fabric 1.21.1 | 1367 | 0 | 0 | 2 |
| Forge 1.20.1 | 1355 | 0 | 0 | 2 |

各端 2 项均为原有 golden/benchmark 跳过。日志 `.work/0.3.2-{target}-full.log`，统计 `.work/0.3.2-build-summary.json`。

- Linux 三端执行 `build -x test -Patomchat.nativePlatform=linux-x64 --offline --no-daemon`，同样带已有本地恢复 init；Forge 包含重混淆任务。
- 本机在线依赖解析曾连接超时/握手失败，首次离线也缺仓库元数据；沿用 `.work/layout-local-mirrors.gradle` 后 Windows 三端完成。
- NeoForge/Forge Linux 原生库版本范围缺离线 listing；另用 `.work/0.3.2-linux-local.gradle` 从已有缓存恢复临时标准 Maven 目录及版本元数据。JAR/POM 字节均与 Gradle 缓存 SHA-256 核对，版本仍为 0.116.8，没有改源码依赖或仓库配置。恢复后两端 Linux 完整构建通过，Fabric Linux 原命令通过。
- `tools/gen_seams.py --check`：125/71；`tools/verify_targets.py` 通过；`tools/twin_diff_report.py`：70 对、未归类 0、陈旧 0；`tools/verify_log_prefixes.py`：262 出口/0 违规；`tools/bare_scan.py`：471/505；`git diff --check` 通过。
- 六包逐一执行 `tools/verify_jars.py`、`tools/verify_chat_assets.py`，对应系统唯一 native、嵌套库登记、0.3.2 元数据、中英资源与反馈音一致性均通过。
- 包内预览、过滤与裁切接线已检查；同 loader 的 Windows/Linux 全部根目录 class 与 assets 逐项 SHA-256 一致（NeoForge 599 项、Fabric 584 项、Forge 587 项，含 Fabric 原版包名下的屏幕类）。报告 `.work/0.3.2-packages.json`。

| 目标 | 系统 | SHA-256 |
| --- | --- | --- |
| NeoForge 1.21.1 | Windows x64 | `f63208465b7619d80449bfacf83ac2f8011b301e726dbbe922e15b307c8a01f4` |
| NeoForge 1.21.1 | Linux x64 | `6a609feb68e2b6005d6736282dcb23b4b827d1c3905f7b08e63dbfb7a6e162e2` |
| Fabric 1.21.1 | Windows x64 | `43ae7c6a568f407ebeaa2ceeed0d6ed341bb739b871beaf1dfcf3407f95fe772` |
| Fabric 1.21.1 | Linux x64 | `9b15e9a9135a8b69b4d170cf491771db0af638a200beea9dc53c89216861538f` |
| Forge 1.20.1 | Windows x64 | `f547d27332d0760d630d09fae6f76c2b4f08a3f6f25fa5ced47cb56f0ad987d2` |
| Forge 1.20.1 | Linux x64 | `5c7181bd20407f03dac6df9e4f54bc97746d1b2c0fe3263b5f304a1855d4a2c6` |

## CCB 部署

- 用户明确指定 `D:/Myworld/.minecraft/versions/1.21.1-CCB/mods`。
- 部署前扫描 72 个 JAR 的 `fabric.mod.json`，没有 `id=atomchat` 的旧包，因此无需备份旧包；AtomSound 是其他模组，保持原样。
- 安装 `atomchat-Fabric-1.21.1-0.3.2.jar`；源文件、暂存副本与最终安装 SHA-256 均为 `43ae7c6a568f407ebeaa2ceeed0d6ed341bb739b871beaf1dfcf3407f95fe772`。
- 同目录暂存副本核验后重命名，安装后对照其他文件名称、大小与修改时间均未变化；部署记录 `.work/0.3.2-ccb-deployment.json`。
- 未启动客户端，重启游戏后生效，实机观感仍待用户验收。

## 实机验收范围

用户已反馈本轮验收通过，并明确要求本地提交。下述自动化范围与未测项目保留为验证边界。

自动化与 CPU 光栅不代表游戏 GPU 动效已验收。尚需在真实聊天图片上体验首次/重复打开、快速滚轮换锚点、放大拖动/限位、缩回居中、关闭；检查取色/头像/壁纸裁剪在方角与大圆角下的四角，保存预设单一选中，以及系统/玩家过滤关闭重开和切页后的保留。未测 GPU 帧时、触控板或 Linux 实机输入，不据包内一致性宣称这些场景已通过。
