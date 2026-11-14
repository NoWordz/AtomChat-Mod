# 0.7.0 体验修订验证 · 2026-11-13

## 已实现范围

- 三条输入说明与教程文案缩为短句，中文不再用句号、分号串成长说明
- 教程共22步，设置9个页签各讲用途和一次关键操作，仍按页面线性走完
- 搜索与过滤分开，过滤步骤聚焦真实漏斗，并把示例消息接入真实过滤器
- 头像右键菜单与左键主页分开；仅当前任务鼠标按键/区域可用，进入其他页后只允许返回，Esc亦可返回
- 聚焦描边增加1逻辑px；轮廓移动有连续弹簧；连线两端分别求交实际圆角轮廓
- 手机开合约240ms，保留反向时的位置和速度，轻微倾斜后归位；模糊mask、绘制、外部点击共享角度
- Y等实际键位有深色实底，键位与底色一起淡出
- 新配置模糊200%/上限300%、描边4px/上限6px，已有保存值保留；旧版仅描边开关继续迁移为2/0px
- 取消等次要按钮统一为灰色#4B4F58；警告窗、教程、HUD底栏共用入口；普通确认跟accent，危险操作仍红色
- 默认描边提亮至#68778A，部分预设描边同步提亮；顶部/底栏边缘点阵alpha最高40→64，仍跟随次要文字颜色，不增加光晕

## 性能范围与证据

上游研究固定至 [MuoniumPlayer 9b4cc7e](https://github.com/zihe114514/MuoniumPlayer-multiversion/tree/9b4cc7e7bd143315da716075a6d024a0d4c0c81b)（2026-11-12，2.5.2）
`RowGlowSprite`/`LyricGlyphAtlas` 的启示是有界静态烘焙、活动内容失效和资源主动释放，不照搬材质或整个渲染架构

大面积拖尾点阵现与About halo共用4倍CPU烘焙图，8条/32MiB预算，超限回退；小图/边缘仍保留精确Picture路径
`DotTexturePerformanceProbeTest`：RTX5060 Laptop，1400×700，16帧预热/60采样，GL_TIMESTAMP与glFinish分别记录
在320×215拖尾场景，直接逐点绘制CPU p50 0.5384ms→缓存0.0036ms，完成墙钟0.8155ms→0.0353ms
GPU像素最大通道差：边缘0、短拖尾0、大拖尾7、halo6，保留原8/255门槛；稳态120帧未重建缓存
这些是受控局部成本，不是整帧FPS保证；真实游戏中边缘Picture仍是后续热点
完整样本见 `build/v071-ui-refinement/dot-final-measurements.log`

## 质量取舍与撤回的候选

- 主阴影加深后九宫格与完整高斯偏差5–7/255，增加采样未改善；维持现役阴影与原≤4门槛，未放宽质量检查
- field点阵提高alpha1.6与native1x烘焙在GL对拍误差达到16/255，已撤回；只提高chrome边缘点阵，field维持原alpha与4x采样
- 为区分CPU/GPU尝试查询Surface/Context导致独立测试原生退出，该路径已全部撤回，最终代码无此查询与GPU资源所有权新增
- 一次同目标Gradle输出出现class缺失；强制重编后恢复，后续同目标任务串行

## 已执行证据

- 行为红绿：开合时间、头像左键误入、真实示例过滤、聚焦移动与圆形连线、灰色按钮实际像素
- `render-acceptance.log`：70项通过，无跳过，包含真实GL手机合成、正常字号中英说明卡与点阵GPU对拍
- 24帧双方向固定进度GL对拍一致；另记录开/关各240ms的逐16ms采样，见 `docs/evidence/2026-11-13-ui-refinement/1.21.1-fabric/phone-motion-timeline.csv`
- 三端rounded blur shader实际编译与倾斜mask内外点检查通过，日志 `glass-fabric.log`/`glass-forge.log`/`tilted-glass.log`
- 最终一次性总审查发现1项P2：图片预览真实入口未解锁下一步，已复现并修正；另补设置概览子页返回白名单
- 审查者未运行测试；最终修正由主agent行为回归和三端构建验证，未声称反复独审全绿

## 设计复核

DeepSeek Flash已直接读取本轮离屏截图：灰色次按钮、短文案、外侧说明卡与两端圆角连线未见明确静态问题
提出的两项修订已落实：删掉遗漏教程中文句号，并提亮默认描边至#68778A（估计设计值）
静态复核不代表动画手感通过

## 交付检查

最终三端完整 `test build` 均通过，日志 `build/v071-ui-refinement/*-delivery-verified.log`

| 目标 | 测试 | 失败/错误 | 跳过 |
| --- | ---: | ---: | ---: |
| Fabric 1.21.1 | 1871 | 0/0 | 10 |
| Forge 1.20.1 | 1856 | 0/0 | 10 |
| NeoForge 1.21.1 | 1856 | 0/0 | 10 |

跳过项含显式GPU/性能探针，后续独立运行已通过：70项渲染验收与最终12项smoke均无跳过
最终smoke覆盖图片真实入口完成判定、返回目的地、灰色次按钮、正常字号与GL手机合成
三端jar CRC/类版本/必要类/旧Demo排除、`verify_jars --profile atomlib-consumer`、targets与bare检查通过
裸值433≤441，未放宽基线；孪生报告仍有此前已存在的未归类块，未宣称全仓孪生门通过

**部署时间**：2026-11-13T17:00:25.303690+08:00
三端明确测试实例均已备份替换，仅改AtomChat；游戏进程检查为空，AtomLib保持原包
217/462/239个其他mods文件hash/size/mtime均不变，独立回读安装包/旧包备份/源码冻结均一致
备份：`D:\AtomChat-Backups\2026-11-13-ui-refinement\20261113-170025`
持久摘要：`docs/evidence/2026-11-13-ui-refinement/delivery-summary.json`
完整回执：`build/v071-ui-refinement/deployment-receipt.json`、`independent-deployment.json`
HEAD仍为513f1942，本轮未提交、推送或发布
已有保存的模糊/描边/颜色保留；重选默认或预设后应用新的配色
未启动Minecraft，实机键盘/IME、真实世界背景下观感与FPS仍待用户验收
极窄窗口的教程卡仍回退视口内滚动，保持正常字号
