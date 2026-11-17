# 常用语与消息模板逐页对照，主代理复审及测试部署

## 最新状态：主代理复审、展示原生图后已部署

用户随后明确要求“先审计，再把表情包、常用语和消息模板修正后的原生图给我看看，没问题就部署”。主代理按此最新条件授权完成复审，在本线程展示三类原生图及单编/字段/提取预览链接后执行部署。下文先前“未部署/待确认”是实施代理交付时的历史状态。

主代理独立阅读当前页面的标题区/列表操作、字段流排、编辑计数、预览状态与卡片高度、总草稿导航和点击路径，并打开资源根页、常用语列表/编辑、模板列表/字段/成功，以及280英文浅色列表/单编/未匹配和中文浅色歧义图；实际滚动段记录同步核对。相较认可草图的本轮偏差已修正，已读增量未发现新阻断。保留的响应式差异是六字段按宽度流排、结果字段纵向排列；窄屏标题和部分标签沿用既有省略，不声称逐像素一致。

本轮重新核对归档 XML 的134/129/129项、最终构建日志、产物SHA/CRC，并重新执行三端 `verify_jars.py --profile atomlib-consumer` 与 `git diff --check`，通过。业务测试复用实施代理12:37最终执行结果，没有冒称主代理重跑全部测试。没有修改生产代码或放宽基线。IME、实际动效和多人网络仍不是离屏图能够验收的项目。

部署批次 `20261117-124630`：Forge1.20.1-main、NeoForge航空学备份后同盘原子替换，Fabric1.21.1-CCB经模组元数据核对确无AtomChat，按本次部署授权补装。未改AtomLib、其他模组或配置，未启动/关闭/重启游戏，版本仍0.8.0。

独立进程回读：三端安装SHA与本页最终候选相同，CRC正常，均只有一个有效AtomChat；1625/744/817个其他mods/config文件的路径、大小、mtime、SHA不变。备份验证仅针对确有旧包的Forge/Neo，CCB原无旧包，回执中的规划备份路径不代表该文件存在。备份根目录 `D:/AtomChat-Backups/2026-11-17-v090-list-editor-review/20261117-124630/`；准备清单、执行和独立回读见 `build/v090-list-editor-review/deployment-{prepared,receipt,readback}.json`，部署脚本同目录。航空学仍保留0.3.1-shadowcache，旧库运行组合待实机；原90%兼容率、缺资料陌生离线皮肤、孪生/日志遗留限制不变。

2026-11-17。此候选接续资源页纠偏，但没有部署。新的顺序是：**主代理独审 → 展示真实原生图给用户 → 用户确认 → 才可部署**。主代理审查通过本身不构成自动部署授权。代码与产物现已冻结供复核。

## 先纠正事实

- 撤回“模板前两项红X消失”判断。主代理对原文件三个区域的红像素均计为82，本代理打开原图也看见三项；没有为此修改遮挡、Skija、图标 bounds、图层或绘制管线。
- 267张旧图确实没有常用语单条编辑页；模板只录了顶部，字段/样例/结果在截断外。原因是夹具没有进入该单编页，也没对模板页做滚动分段。旧截图不能支持“这些页均已视觉验收”的说法。
- close 代替删除、编辑文字被替换为图标、整行新增、固定两列字段和输入/结果混卡均是实际偏稿。已找到并复用 `SettingsGroupGlyphs.TRASH`，它也是草图生成源直接引用的图标，不需要新画。

## 逐页差异闭环

| 对照页 | 原偏差 | 此次落实 | 最新原生图，Fabric代表 |
|---|---|---|---|
| `03-phrases` 常用语列表 | 新增是大行；导航返回文字含糊；排序/删除图形偏稿 | 计数与蓝色小新增在卡片外标题区；列表合并卡；整行编辑；沿用设置实心三角排序与语义垃圾桶；底部“返回资源包”，不提交 | `details/zh_cn-420-dark-phrases.png`、`details/zh_cn-280-dark-phrases.png` |
| `05-phrase-edit` 单条常用语 | 无原生图；缺计数 | 正文输入、点击后填聊天栏说明、动态 `当前 / 200`、完成及主配置页保存说明；单编返回保草稿 | `segments/zh_cn-420-dark-phrase-edit.png`、`segments/en_us-280-light-phrase-edit.png` |
| `06-template-list` 模板列表 | 大行新增；编辑变铅笔、删除变close；无总数/启用数和字段摘要 | 标题区新增小按钮、总数/启用数；文字“编辑”、开关、红色垃圾桶；类型与现有格式字段摘要；仅根保存提交 | `details/zh_cn-420-dark-templates.png`、`details/en_us-280-light-templates.png` |
| `04-template` 编辑顶部与格式 | 格式框过高；顶部图无法看到后续 | 保留真实设置输入框与三种类型，模板格式/样例采用紧凑输入视口，完整值和已有编辑/内部滚动能力不丢失 | `segments/zh_cn-420-dark-template-top.png`、`segments/zh_cn-420-dark-template-format-fields.png` |
| 同页：字段 | 无理由最多两列；后续被推远 | 每个按钮以真实字体测宽+既有行操作内边距排列，放不下才换行；420中文首排至少3项的行为断言及280/420全部按钮不越界/不重叠；实际点击最后字段验证插入 `{target}` | `segments/zh_cn-420-dark-template-fields.png`、`segments/en_us-280-light-template-fields.png` |
| 同页：样例与结果 | 混一张卡；只有纯次级文本；无截到的图 | 两张独立卡；成功强调色标题、字段名/值层级与身份依据；未匹配标题+说明；歧义危险色标题+不采用结果的说明。结果来自实际现有解析器 | `segments/zh_cn-420-dark-template-sample-match.png`、`segments/en_us-280-light-template-sample-no-match.png`、`segments/zh_cn-280-light-template-sample-ambiguous.png` |

所有表中路径均位于 `build/v090-list-editor-review/previews/1.21.1-fabric/`。Forge、Neo 同名文件位于各自平台目录。不是另做静态成功字样或浏览器皮肤。

布局保持草图语义，但不是把600宽草图强行缩图：按钮在实际280/420面板里按真实文字折行。字段采用已有契约的“玩家账号/装饰名/接收者账号”等明确名称，增加现有解析器已支持的 target 插入按钮；六字段与草图示意五字段数量不同，属于现有模型信息完整性，不改变解析语义或凭昵称确认UUID。预览字段当前按实际宽度纵向列出，未机械照搬宽草图的双列坐标；身份说明与字段分开，完整保留。不声称原生PNG与草图逐像素相同。

单编计数遵守现有200 UTF-16单位限制，没有改变线上约束。排序、垃圾桶和编辑按钮继续采用既有 token/图标/hover/PressScale/ripple；未改全局确认窗、AtomLib、协议、权限、事务、模板识别或在线同步逻辑。所有配置与资源仍共享同一份总草稿。

## 原生图与滚动证据

- 每端137张，共411张。每端56张新 `segments/` 覆盖中英文、280/420、深浅主题的7个状态：单编、模板顶部、format/字段、字段、样例成功、未匹配、歧义。
- 分段全部采用实际 `UiLayout.ofDetail(...,width,820)` 和 `width+20 × 840` 栅格面板，经页面真实 ScrollController 滚动后正常绘制，保留真实裁剪、底栏与滚动条。没有把编辑器面板加高，也没有把裁剪外的内容拼进同一张图。
- 每张滚动分段旁有 `.png.scroll.txt`，记录面板大小、目标行、实际scrollY和视口。420格式段实际scrollY335；280英文浅色歧义段实际scrollY862.5（最终以同目录记录为准）。
- 每端另有24张根分类及57张详情/列表/hover图；之前资源根页仍包含有名称的真实曼波图片。旧 `resources-full` 加高图未延续为本轮证据。
- 已直接打开三端代表图核对列表、正文/计数、格式/字段、成功/未匹配/歧义和深浅窄宽。静态图不证明IME、完整按压/入场/水波、真实窗口焦点或联机时序。

## 验证与源码对应

最终三端相关 `test ... build`：Fabric **134**，Forge **129**，NeoForge **129**；全部0失败、0错误、0跳过。没有把此批称为重跑全仓2000多项。沿用相同离线 AtomLib0.4.0输入、Forge JDK17证书探测例外、Fabric/Neo JDK21、Neo `-x downloadAssets`，不改依赖/版号。

覆盖 `com.atom.chat.config.*`、TemplateParserTest、ServerTemplateRecognitionTest、FormTextTest、SettingsGroupIconsTest、SettingsResponsiveLayoutTest、Confirmation*、QuickPhrase*。新增列表内容结构红灯后修正；真实解析器校验 MATCH/NO_MATCH/AMBIGUOUS；分段正scrollY；字段流排不重叠；点击真实字段命中；既有根页导入/预览/移除仅根保存提交、排序边界和Undo隔离继续通过。

`build/v090-list-editor-review/` 中保留：

- `*-final.log`、`*-tests.zip`、`results.json`、`layout-red.log`、迭代日志。
- `artifacts/` 最终三包及CRC/AtomLib消费者产物断言 `jars.log`，均通过。
- `previews/` 原生图及滚动记录；`changed-files.txt` 此批文件范围。
- `bare.log`：441基线/412实测，通过，没抬基线；`whitespace.log`：diff check通过。
- `pre-test-tracked-pngs.zip`：158张原跟踪PNG全部仍与本次测试前WIP字节一致，未重录基线。

最终候选SHA256：Fabric `80b855080c75ac92e6274c4778a1541a80e67283ef5e66d6a67f34f065fe1eca`；Forge `519aff0a925cde63154ea1f7234bc0cca63f95e2c1a45b154595472ed5e1d3b4`；Neo `bc0735b6183c404a9c65adedbbd021eb119b498428b6b07bcae3ee7b196bc20d`。测试/构建/收集后运行代码未再变动。

## 部署状态与尚未验收

本代理未部署。只读检查发现 Forge、Neo 当前AC JAR仍与00:02旧回执一致；**CCB当前没有匹配 `atomchat-*.jar` 的顶层文件**，故不能沿用此前“三实例全部旧包不变”的结论。原只读脚本因恰好一个文件的假设失败，随后仅记录所见状态于 `installed-state.json`，没有补装、重命名、删除或修复实例，不能据此推断是谁或何时改变了CCB。该发现不是代码/构建失败。

待主代理集中独审、用户看图确认，才进入部署。没有新代理、启动游戏、提交/推送/发版。实机IME/动效/联机/Neo旧Lib组合、孪生台账33+8与日志31遗留、90%自动兼容率及陌生离线缺资料身份/皮肤问题均保留，未以本轮视觉工作冒称解决。
