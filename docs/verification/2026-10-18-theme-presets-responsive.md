# 2026-10-18 主题预设与窄窗口适配验证

基线：main@fe56f95；本轮候选未提交。版本保持0.3.0。

## 范围

- 个人外观预设包括独立壁纸副本；支持命名、应用、覆盖、重命名和删除，外观字段白名单。
- 窄设置行换行/控件下移、颜色网格和数字输入、顶栏文字安全区、输入按钮换行。
- 保留已授权s(2)细外框；未改变整体字号层级、美术、阴影或导航。

## 执行与审查

- 沿用Command Code GOAT / deepseek/deepseek-v4.1-flash-fast，ZCode独立会话，reasoning disabled。15分钟上限到达时尚未完成验证，由负责人接手；不能称DS完成交付。供应商配置哈希未变，执行会话已关闭。
- 独立强模型只读审查发现取消命中、补充码点粘贴、覆盖后缓存和清理、数字输入测量与绘制分歧；负责人修正后完成测试。缺壁纸及缓存manifest问题由负责人直接确认。
- 配置原子保存并反馈失败，预设应用失败回滚；WallpaperStore先stage再换文件；WallpaperImage让release失效旧异步解码。

## 实际命令与结果

- JDK17 Forge；JDK21 Fabric/NeoForge。各工程 `./gradlew build --offline --no-daemon`，然后 `./gradlew build -x test -Patomchat.nativePlatform=linux-x64 --offline --no-daemon`。
- Fabric首轮因候选代码的`host.client().mouseHandler`映射遗漏编译失败；转为`mouse`后Windows及Linux重建成功。其他目标源码在构建期间未变；初始661项输入哈希和Fabric修正后哈希见本轮证据。
- 定向红日志：lead-red.log 16项4失败；responsive-red.log 4项3失败；io-red.log证实配置写失败未回滚。修正后完整测试如下：

| 目标 | 测试数 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|
| 1.20.1-forge | 1278 | 0 | 0 | 2 |
| 1.21.1-fabric | 1290 | 0 | 0 | 2 |
| 1.21.1-neoforge | 1278 | 0 | 0 | 2 |

各2项跳过为既有golden与benchmark；未在Linux主机运行完整JUnit。

- `bare_scan.py`492/505、`gen_seams.py --check`123/71、`twin_diff_report.py`68对未归类0、`verify_targets.py`、`verify_log_prefixes.py`262出口0违规及`git diff --check`通过。
- `verify_jars.py --platform windows-x64/linux-x64`与`verify_chat_assets.py`通过；六包有新预设类和中英语言键，对应OS的全部class与assets/atomchat内容哈希一致。旧Preview构建产物11件移动至D:/AtomChat-Backups/2026-10-18-theme-presets/old-build-outputs，未删除。

## 安装包

| 包 | SHA-256 |
|---|---|
| atomchat-Forge-1.20.1-0.3.0.jar | `2fce09a709a3dc94f8751cc0b2931f1997ae708648d72f59fbef1c1f8b120c9c` |
| atomchat-Forge-1.20.1-linux-x64-0.3.0.jar | `e08998107d21cd0c61134b98e41a1f36a87ac75da570ef168ba1877b2ba9c30e` |
| atomchat-Fabric-1.21.1-0.3.0.jar | `d3eaa379e31d237d0f2c23f147c084d42ffa6c0b5113d874dd51d3e04230bc16` |
| atomchat-Fabric-1.21.1-linux-x64-0.3.0.jar | `9a6ef97cab2b8aa8aee8a630a11aaf902d1f3d6ef26ac8531f8bd8f9f9a21c7a` |
| atomchat-NeoForge-1.21.1-0.3.0.jar | `5cacf6abb2eff8bd12218cb14f2b1beab58070b353a9d6bbd2fed5eb6154d8a4` |
| atomchat-NeoForge-1.21.1-linux-x64-0.3.0.jar | `deb9bfd2e851a3fc2f39a591e753ce81dcb7a1ebdb13571315c57e98bc0989e4` |

## 部署

- windows：`D:\Myworld\.minecraft\versions\Mechanomania-航空学\mods\atomchat-NeoForge-1.21.1-0.3.0.jar`；新SHA-256 `5cacf6abb2eff8bd12218cb14f2b1beab58070b353a9d6bbd2fed5eb6154d8a4`。
  旧包备份：`D:\AtomChat-Backups\2026-10-18-theme-presets\NeoForge\atomchat-NeoForge-1.21.1-0.3.0.jar`；旧SHA-256 `2450b8ff1dd6b43f75ffa5cc3331ca9fc1f39ec4f0552e75bb74b6fe7028b82f`。
- linux：`/home/niuqu/.local/share/PrismLauncher/instances/1.21.1/minecraft/mods/atomchat-Fabric-1.21.1-linux-x64-0.3.0.jar`；新SHA-256 `9a6ef97cab2b8aa8aee8a630a11aaf902d1f3d6ef26ac8531f8bd8f9f9a21c7a`。
  旧包备份：`/home/niuqu/.local/share/PrismLauncher/instances/1.21.1/atomchat-backup/2026-10-18-theme-presets/atomchat-Fabric-1.21.1-linux-x64-0.3.0.jar`；旧SHA-256 `ddb29825abfd5624d1fa7b94ebfaba79730acce0531a44e3373d9d0ce5c5e9fc`。

## 尚未覆盖

- 未启动游戏，GPU画面、中文IME输入法候选窗、窗口真实拖动/缩放和壁纸切换观感仍需用户实机验收。
- CPU离屏测试证明测量/绘制可运行及画布状态平衡，不证明所有视口文字都可读；320×240且两个缩放均最大时物理空间不足，不能承诺完整聊天区同时可用。
- 未提交、推送、升版、打tag或发布正式版。本轮先交付候选包供验收。

原始日志、哈希清单、部署JSON：`.work/2026-10-18-theme-presets/`。
