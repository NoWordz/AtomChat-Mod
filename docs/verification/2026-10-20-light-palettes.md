# 0.3.1 浅色主题对方气泡配色

基线：main@f502343。本轮只修改四个浅色内置预设的对方气泡底色，沿用此前已验收的界面与布局。用户明确选择“中等明度的有色底＋深色文字”。源截图与观察在 `docs/design-references/0.3.1-light-palettes/`。

| 主题 | 原底色 | 新底色 | 方向 | 原有文字色 | 文字对比度 |
| --- | --- | --- | --- | --- | --- |
| 极简 | `#F1F2F6` | `#B9CDE6` | 雾蓝 | `#1A1D21` | 10.42:1 |
| 青柠 | `#EFF5E3` | `#C3D38D` | 柔橄榄绿 | `#1F2937` | 9.10:1 |
| 典雅 | `#EFE8DC` | `#D0B294` | 陶米棕 | `#3D3929` | 5.77:1 |
| 活力 | `#FBE3EB` | `#DEA6BE` | 豆沙粉 | `#3D2C35` | 6.40:1 |

对比度由标准 sRGB 线性化相对亮度及 `(L亮+0.05)/(L暗+0.05)` 计算，只用于检查正文可读性，不用数值代替配色观感判断。

## 范围与使用

- 唯一生产改动在共享 `ThemeService`，三端与主题缩略图读取同一配色；原强调色、自己气泡、文字、头像、几何和材质不变。
- 内置预设按点击时一次性应用，不会自动改写既有用户配置或个人保存的外观快照；重新点选对应内置主题即可使用新颜色。
- 延续本轮 CCB 部署目标，不自动提交、推送或发布。
- 本轮没有实现更多气泡、新点阵位置或布局重排。讨论的超椭圆/带尾巴/消息分组、空会话或资料头图点阵和按场景调整布局均是后续建议，尚未进入实施范围。
- 前轮已确定的界面功能已完成；项目仍未做 GPU 量化剖析及多人专项实机覆盖。WSL 性能/准星锁定、完整反应参与者名单等是此前独立延期事项。

## 验证

使用实际 `MessageListView`、顶栏和输入栏生成四主题调整前后对照，包含自己与对方、短消息与显式多行，面板宽 360。预览是 Skija CPU 图，不代表游戏 GPU 或用户观感验收。

本轮执行三端 `./gradlew.bat test --tests '*ThemeServiceTest' --tests '*OffscreenRenderTest.renderLightThemePaletteComparison' build --no-daemon`，三端均通过（各 19 项定向测试）。原有活力主题色值断言已按本次明确的配色调整同步。预览样本精简为两个完整可见消息后，又在 NeoForge 单独运行该预览用例，1 项通过。未重跑前轮完整测试集。


- seams 124/71、targets、twin 69 对/0 未归类/0 陈旧、日志 262/0、bare 480/505、diff-check 均通过。
- Linux 分别执行 NeoForge `jar`、Fabric `remapJar`、Forge `build -x test`，均传入 `-Patomchat.nativePlatform=linux-x64`；六包的目标 native、版本、素材及同 loader 两 OS 应用内容一致性校验通过。
- [实际聊天配色对比](assets/2026-10-20-light-theme-palettes.png)已人工查看；上排为原配色，下排为新配色。

| 目标 | 平台 | SHA-256 |
| --- | --- | --- |
| 1.21.1-neoforge | windows-x64 | `b222594456d16af43450adc23427190917b2b030c4392172855b9fbb296da0a6` |
| 1.21.1-neoforge | linux-x64 | `a29e945fec02ca8cbc53cdd7f891ad8a4c29c7cd4ad90f5d9717afc72f50e41b` |
| 1.21.1-fabric | windows-x64 | `3aa2ad9daec911de67aafd14e0b78c9f110b6295f8b2ba0234e42987481302e8` |
| 1.21.1-fabric | linux-x64 | `60d14709f4604bb3fd411397c41bd571958418d4d477c7b132bb8cd94eae39f2` |
| 1.20.1-forge | windows-x64 | `190ae367c30c633f5a492a5468a606a61cff588b736317951b9bff4f927bd508` |
| 1.20.1-forge | linux-x64 | `0b58da50cfd853380fdb18d203044668ef7c72c2bc5244eb778018af5b94e006` |

## 部署

- 仅更新用户指定 CCB Fabric 实例：`D:\Myworld\.minecraft\versions\1.21.1-CCB\mods\atomchat-Fabric-1.21.1-0.3.1.jar`。
- 新 SHA-256：`3aa2ad9daec911de67aafd14e0b78c9f110b6295f8b2ba0234e42987481302e8`。
- 旧 SHA-256：`180032a5075c8b31ffce3306db8253f089723b334505e46a689b562f4a42fb89`。
- 备份：`D:\AtomChat-Backups\2026-10-20-ccb-light-palettes-777d5944\atomchat-Fabric-1.21.1-0.3.1.jar`，附 `deployment.json`。
- 备份、候选及安装读回哈希通过；未改配置与其他实例，未启动客户端，未提交推送发布。重启后重新点选对应内置主题应用新配色。
