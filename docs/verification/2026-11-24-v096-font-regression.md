# 0.9.6 Modern UI 聊天方框回归

2026-11-24。源版本 `2a2d8782` 部署后，用户在 Fabric CCB 反馈正文、玩家名和系统消息全部成为方框，标题和时间正常。用户截图与当次客户端日志保存在 `build/v096-font-tofu/`。这是新增字体兼容路径的回归，先前的自动化检查未覆盖外部字体系统替代原版 FontStorage 的场景。

## 根因与修正

CCB 安装的 Modern UI 3.11.1.9 用 StandardFontSet 的独立字体集合供字，先对 superclass FontStorage 调用空 provider 列表的 setFonts。直接读取其 JAR 字节码确认：公开 getGlyph 使用独立缓存，普通 outline glyph 的 bake 不调用原版 baker。

AC 新增的 NativeFontProvenance 根据空 superclass provider 图记录 removedDefault；NativeFontRenderer 又仅枚举该图，因此把本来可用的全部文字判成原版 MISSING。界面标题和时间仍使用 AC 原有绘制路径，与截图的差异吻合。客户端 capture 日志中的原始中文、字母和数字正常，不是聊天内容损坏。

Fabric 和 official 两个实际 NativeFontRenderer 实现现于选中字形为空或 MISSING 时核对公开 lookup；公开接口确实供字才退回已有 AC 字体，同时缓存相同的布局/绘制决定。不捕获外部无原版 upload recipe 的 glyph，不借用外部 atlas，不改变全局 PlainText/SkiaFontRenderer。公开结果仍为 MISSING 或 null 时保留原有资源删除/显式缺字路径。混淆候选回退 AC 时保留原生原字形，避免新增的 null 分支导致空指针。

## 可核对验证

- 新增正式套件用例 `NativeFontProviderTest.delegatedFontStorageWithEmptyVanillaProvidersKeepsChatTextLegible`。修复前实际进入 test，在第一个中文字「聊」（32842）的路由断言失败，1 项执行、1 项失败；红证据为 `build/v096-font-tofu/regression-red.log`。
- 修复后定向运行 NativeFontProviderTest、NativeFontProvenanceTest、NativeGlyphRasterStateTest、RichTextFormattingRenderTest、SkiaFontWidthIdentityTest，19 项全部通过，无失败/错误/跳过。日志 `regression-green.log`、XML `targeted-results/`。该定向命令为快速验证排除了 frontendContractSourceCheck，不能替代后续正式构建。
- 新用例检查默认/显式字体、中文/ASCII/数字、零/负 advance、真实 missing 与外部供字切换、混合 byWidth 混淆候选。真实 request/prepare/draw 的宽度及所有输出像素与独立 PlainText 参照相同；`delegated-text-after.png` 已人工查看，文字可读且非空。
- 既有 bitmap、TTF、unihex、独立图标、filter/options、资源删除及 native 字形回收测试保留，未降低原断言或修改参考/容差。
- 三端按实际前端 policy 做增量观察，均为 existing changes=0、diagnostics=0；不更新准入清单、author、场景或缓存策略。
- 独立审查已读取实际 Modern UI 字节码、两映射差异、红/绿日志、XML 和新 PNG；未发现本补丁的确定阻断。报告 `build/v096-font-tofu/independent-review.md`。

三端正式执行 strict `clean build frontendContractCheck`，Forge/NeoForge 同时执行 `verifyAtomLibInput --offline`；CCB 按既有 `tools/deploy_ccb.py` 构建与部署，三端 JAR/AtomLib 输入断言均通过。正式检查没有排除 frontendContractSourceCheck，沿用独立审定配置修订 `2a2d8782`，source=APPROVED_VALID、scenes=SCENARIOS_VALID，每端196正式场景。

| 目标 | tests（含跳过） | 实际通过 | 失败/错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| Fabric 1.21.1 | 2340 | 2320 | 0/0 | 20 |
| NeoForge 1.21.1 | 2320 | 2302 | 0/0 | 18 |
| Forge 1.20.1 | 2320 | 2302 | 0/0 | 18 |
| 合计 | 6980 | 6924 | 0/0 | 56 |

本轮 XML、日志与三份制品保存在 `build/v096-font-tofu/verification.json`、`results/`、`products/`。测试生成覆盖的30张跟踪 PNG 已另存并按运行前及 HEAD 一致的字节恢复，没有重录基线、改容差或增加前端准入豁免。

CCB 于13:31部署，Forge主实例与NeoForge航空学于13:34部署；原包、现有 AtomLib 和配置先备份再替换，逐实例读回校验通过，其余 mod/config 保持。部署正本 `D:/AtomChat-Backups/2026-11-24-v096-font-hotfix/`，原 v0.9.6 包已由本轮修复包替代，版本号仍为0.9.6。未启动游戏、推送或公开发布。JAR 构建戳仍为既有 git_commit=unknown 的本地回退，具体源码与制品对应依据本轮源码差异、完整构建和部署读回，不能冒充带准确 Git 戳的公开发行包。

## 结论范围

已复现并修正错误制造全量 MISSING 的路径。离屏用例模拟的是公开供字与 superclass 空图之间的真实契约，不能单独替代完整客户端验证。部署后用户于11月24日明确确认“实测没问题”，本次方框回归已获现场验收并关闭；没有由代理代启游戏。该反馈不扩写为每个未记录专项、特殊图标或游戏帧时都已独立测过。

本补丁恢复外部字体系统场景下普通文字的 AC 字体，不意味着能复制 Modern UI 独立字体集合里的服务器 PUA、称号图标或全部资源包字形。原版 provider 路径的资源包兼容保持原实现；外部系统私有字体的完整绘制仍是能力边界，不能将本次文字恢复当作其通过证据。
