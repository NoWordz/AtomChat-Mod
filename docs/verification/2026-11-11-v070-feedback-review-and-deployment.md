# v0.7.0 实机反馈复核与部署

2026-11-11 17:17（Asia/Shanghai）已部署原三个实例。仅使用一个子代理实现和分阶段自审，主代理复核、完整构建、GPU 验证与部署。没有启动游戏、提交、推送、打标签或公开发布。

## 本轮修复

- 壁纸与主题共用卡片绘制：字号、字重、名称间距、圆点、选中描边、当前名称与横向交互一致。修正壁纸通用命中误走气泡动作。依据：`layers/mapping/official/src/main/java/com/atom/chat/settings/SettingsSectionPage.java:2215`、`:2393`，Fabric 同步。
- 新用户默认无壁纸，已有图片保留；“石墨”预设替换为“钴蓝叠面”，新旧 ID 独立，旧图不参与尺寸变化重生成。依据：`shared/src/main/java/com/atom/chat/wallpaper/WallpaperPresets.java:19`、`:110`。
- 底栏形变 420ms，收拢／展开 320ms；形变与展开有轻微弹性，消失方向单调收至零，透明度保持合法。反向从当前形状继续。依据：`shared/src/main/java/com/atom/chat/ui/BottomChromeMotion.java:7`、`:35`。
- 恢复会话列表、设置首页、个人页／详情、设置详情的错峰入口，使用导航与真实 paint 的同一时间戳；逐项记录已绘制位置并逆算命中，保留整体同时入退场与可打断。依据：`platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java:2600`、`shared/src/main/java/com/atom/chat/ui/StaggerHitMap.java:6`。
- 三端六份中英文语言资源补齐描边粗细键与壁纸状态，修正旧白色描边说明。语言审计剩余四个字面量候选均为系统属性、文件名或注释，非界面语言键。依据：`.work/v0.7.0-feedback/language-audit.json`、`FeedbackLanguageTest`。
- 默认背景 #202630、卡片 #343D4B，保持约 50% 透明混合；边框 #14161B，蓝色强调仍为 #4A90E2。已有配色保留，主动应用默认主题才采用新版配色。依据：`shared/src/main/java/com/atom/chat/config/AtomChatConfig.java:213`、`:220`、`:373`。
- 右键菜单恢复原版底色与阴影顺序，撤销裁掉内侧光梯度的改动；没有另设计高光。依据：`shared/src/main/java/com/atom/chat/ui/MenuPopup.java:90`。
- 恢复底栏点阵，按旧颜色／间距／半径／强度在 body 下方绘制，跟随真实底栏形状；聊天阴影在 body 前、导航阴影在 body 后。运动中直接执行原绘制命令，不按每帧尺寸创建缓存 Picture；淡出层包括完整点阵范围。依据：`shared/src/main/java/com/atom/chat/ui/BottomChromeTransition.java:49`、`:67`，`shared/src/main/java/com/atom/chat/render/DotTexture.java:121`，Fabric screen `:2619`。

## 额外审计与用户批准

- 发现重复描边：三端只保留最后一次绘制，保证覆盖层顺序；测试检查每帧唯一。依据：Fabric screen `:2653`，另两端同步。
- 发现退役石墨可能降至自选图 1024 解码上限：保留旧 marker 作为生成图来源，活动 presetId 仍为空，解码仍采用生成图 4096 预算；普通自选图维持 1024。成功替换／清除会清来源并使旧解码失效，失败替换保留旧来源。真实 800×1600 PNG 异步解码、重启、尺寸变化、所有 setter 与失败路径回归通过。依据：`shared/src/main/java/com/atom/chat/wallpaper/WallpaperStore.java:31`、`:72`、`WallpaperImage.java:83`。

## 验证

先复现回归，再运行针对性、完整套件、真实 GPU、成品检查；真实 Minecraft 验收未执行。

| 平台 | 测试数（含跳过） | 失败／错误 | 既有跳过 | 完整 build |
| --- | ---: | ---: | ---: | --- |
| Fabric 1.21.1 | 1795 | 0／0 | 7 | 通过 |
| Forge 1.20.1 | 1780 | 0／0 | 7 | 通过 |
| NeoForge 1.21.1 | 1780 | 0／0 | 7 | 通过 |

- 最终完整计数与日志：`.work/v0.7.0-feedback/build-results.json`、`build-*.log`；针对性 28 项和追加 20 项结果各有独立 XML 保存。
- 点阵与菜单原版 PNG、聊天和导航点阵真实卡片／气泡遮挡 PNG：主代理独立比对文件字节完全一致；图片保存在 `docs/evidence/2026-11-11-v070-user-regressions/`。卡片排版与钴蓝壁纸已目视检查，图片为生产离屏绘制。
- `PageGpuSmoke.log`：真实设置页启用错峰，SLIDE／ZOOM 中断 body 像素差均为 0，逐帧 reset 与新建 GPU Surface。
- `DenseNavigationGpuProbe.log`：120 个稳定复杂帧零压平，30 次密集中断；1.5 密度压平像素差至多 1/255。该压力场景 CPU 提交 p95 约 5.91ms，不能代表游戏 FPS。
- `com.atom.chat.render.ChromeGpuSmoke.log`：聊天／导航点阵与旧版真实内容遮挡在 GPU 上逐像素一致；80 个运动 Surface、8 次反向首帧逐像素一致，点阵 Picture 缓存无增长。
- 点阵裁剪探针区分有限／无限层和缓存／直画；此前 1/255 来自阴影细节层的分数边界舍入，参考保留无限点阵层，仅对齐非点阵阴影层边界后，全图仍以零容差比较。没有放宽像素容差。
- 822 个生产／测试 Java 与资源 JSON 在完整构建后全部匹配冻结哈希；AtomLib 锁未变。成品 CRC、新类／接线、两种语言与源文件一致、元数据、依赖及嵌套包检查通过。
- `verify_targets`、seams、`git diff --check` 通过；裸值 433≤441，未放宽基线。孪生检查仍有原有 14 个未归类残差，与本轮基线逐项一致，无新增，不能称全仓孪生全绿。

## 部署与回退

2026-11-11 17:17 部署完成，仍为 v0.7.0。部署前目标游戏进程为 0。每实例只替换 AtomChat，备份旧包后安装，并验证候选、安装包与备份 SHA-256；AtomLib 及其余 181／102／224 个文件哈希不变。

| 实例 | 最终 AtomChat SHA-256 |
| --- | --- |
| 1.20.1-main | `a5556630e450ab1c8d6f9569cb3cf045d53dc56bfdfe52c3b0c81aa80db76fe6` |
| 1.21.1-CCB | `c8f4c80bcd41efb7a07b48a5a7bdd9da60a955949c358475bab56b0dce069588` |
| Mechanomania-航空学 | `5d15f6e7325fb18bfad718d1ca579b8d44a8b9323b5fc70196cdfcbb6a43ab1e` |

备份：`D:/AtomChat-Backups/2026-11-11-v070-20261111-171732/`，含三个旧 JAR 和独立回执副本。本轮安装正本回执：`.work/v0.7.0-feedback/deployment-receipt.json`；上一轮目录和回执保留。

## 实机验收与限制

没有启动 Minecraft，实际整合包 FPS、首次开屏与连点手感、多语言长文本观感仍待用户验收。GPU 冷启动包括字体和管线成本，不用冷峰值推断游戏帧率。

已有用户需重新应用“默认”主题查看新配色；壁纸独立选择，已有石墨不自动换成钴蓝。普通自选图片仍按此前 1024 长边解码；生成图最高 4096／16M 像素；极端页面快照超过 8192／16M 预算会结束当次过渡，规则未扩大。
