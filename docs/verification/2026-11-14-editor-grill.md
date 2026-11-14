# 2026-11-14 编辑器与当前页签恢复：验证与部署

**状态：主代理独立复审通过；15:29–15:30三端已备份部署，独立另进程回读通过。** 用户已明确撤回全部SHA/trust/hook/readonly门控，仅保留普通功能与回归测试。

## 功能范围

- 面板四角/四边八向缩放，八个常显缩放点、整边可拖；顶部外侧中央独立四向移动胶囊，顶部空间不足时内移；固定唯一九宫格锚点，按HUD margin实时吸附。
- 真实内容仍可交互、外侧空白不关闭；footer为“拖动把手移动 · 拖动边框改尺寸”及还原/保存。还原进入本次编辑时几何，Esc取消，保存失败保留；内容缩放不被编辑器回写。
- 当前section+chip恢复明确65字段，含折叠/未滚到项；不删个人预设/壁纸/历史/屏蔽名单。确认捕获页签，取消不改，保存false/throw仅回滚当前tab。serverPacksEnabled未展示，按既定B边界不纳入。
- 用户A方案：成功恢复外观/调整后先follow新几何再finish，committed=true且不二次保存；旧cancel/removed不覆盖已保存默认，失败不结束编辑器。
- 普通warning/open与reset红图标/红确认/灰取消；教程openTutorial与普通编辑主操作保留强调色。
- 两个图标入口共用tight bounds归一化，256有界identity缓存按Path generation失效；已认可教程22步、HUD底栏/退场、阴影18/12、点阵alpha64/4x缓存、Toast和theme hover修复保留。手机HUD/新手机动画/旧PanelLayoutEditor不恢复。

## 门控已撤回并清理

用户原话：“那个门控是气话，不要继续做了，把前面要求的功能实现完就行”。已停止运行中的变异脚本；当时仅完成正常working/index检查，未进入生产源/测试/checker的篡改变异，不把已取消测试伪称验收完成。

- 精确备份于build/editor-grill-20261114/withdrawn-guard-backup，再移除本轮tools/verify_ui_contract.py、ui_contract_lock.json、本机trust、仓库/安装pre-commit，以及Gradle bootstrap/uiContractCheck/dependency和仅guard用attributes。
- 原hook在初始化前不存在，没有覆盖或移除其他hook；从未激活readonly，未更改原有属性。AGENTS/UI_CONTRACT改为普通防回归约定。
- guard-removal.json及guard-removed-verification.json记录移除和二次核实；真实index仍无暂存改动。Gradle/attributes的guard清理后与HEAD没有内容差异。
- source-freeze/artifact-manifest只是本次候选交付身份记录，不是锁、批准清单或自动更新门禁。旧门禁报告已标“需求撤回”，不继续建设或变异。

## 实际完整构建

三端串行执行，JAVA_HOME=C:/Program Files/Microsoft/jdk-21.0.8.9-hotspot（避免本机默认Java25与旧Gradle/Groovy不兼容）。命令均为：

```powershell
.\gradlew.bat test build --offline --no-daemon -PatomlibMavenRepo=D:/AtomChat-Main/build/atomlib-slim-20261107/atomlib-maven-v040-final
```

| 目标 | tests | failures | errors | skipped | 日志 |
|---|---:|---:|---:|---:|---|
| 1.21.1-fabric | 1894 | 0 | 0 | 7 | build/editor-grill-20261114/final-1.21.1-fabric.log |
| 1.20.1-forge | 1879 | 0 | 0 | 7 | build/editor-grill-20261114/final-1.20.1-forge.log |
| 1.21.1-neoforge | 1879 | 0 | 0 | 7 | build/editor-grill-20261114/final-1.21.1-neoforge.log |

原生UiStyleContractTest每端4项均执行、0失败/错误/跳过；其中5图标×3density×rest/hover/press=45样本/端（共135个实际raster样本），中心误差阈值保持1输出像素，未放宽容差。warning普通open/真实reset红色及mutable Path.swap缓存失效均在同套测试；教程强调色例外使用真实openTutorial。A组合回归验证560不覆盖480、只保存一次。

三端各7项既有跳过：alternate AtomLib jar未提供；About GPU benchmark、GoldenFrame模式、PageEntrance GPU benchmark、SkiaDraw微基准及两项Emoji性能基准未启用。它们不是UiStyle像素测试跳过，未删测试/加skip。完整JUnit XML及skip清单保存在results-*、final-builds.json。

## 其他实际检查

- `python tools/verify_targets.py`通过（final-check-targets.log）。
- `python tools/bare_scan.py`通过：433处 <= 441处；未改baseline（final-check-bare.log）。
- `python tools/verify_jars.py --profile atomlib-consumer <三个精确JAR>`通过（final-check-jars.log）。不使用含旧包的目录级断言。
- `python tools/verify_chat_assets.py <三个精确JAR>`通过（final-check-chat-assets.log）；此前一次无参数调用因缺精确路径报错，已按原CLI要求重新执行。
- `git diff --check`通过（final-check-diff.log）。
- 额外日志规范扫描：HEAD已有25处，本轮新增Prompt的一处已按现有AtomChatLog.message格式修正；之后仍25处旧问题，未扩范围改旧诊断出口。详见check-log-prefixes-after.log和log-prefix-baseline.json，不能宣称该额外全仓扫描全绿。
- 初次产物检查误把RootPageInput内的方法调用要求放在外层Screen class；读回真实class结构后修正检查器，三端均在实际AtomChatScreen$RootPageInput中找到requestTabReset/followPanel/finishAfterSettingsReset。未因此修改产品代码。

## 产物与源身份

562份生产源码/资源逐字节摘要已记录并二次回读，候选JAR均含本轮geometry/defaults/prompt/tight-bounds类与三端真实接线；类major Forge61/Fabric65/Neo65。无PhoneHud/PhoneOpenMotion/PhoneShell/PhonePanelInput、旧PanelLayoutEditor或TutorialDemoPanels。三端zh_cn/en_us各521条内容相等。

| 目标 | SHA-256 |
|---|---|
| 1.20.1-forge | `967f4d3239551f50d2d866b59ad4aa12d5ada266f372cf64fc526ad193b1e4e7` |
| 1.21.1-fabric | `87704c398cf5d3cfd7888658e21a383df26fbf75b5d2c71cea50db17ec0ee872` |
| 1.21.1-neoforge | `89deaccd1404a1f816964da0e79ebac80eab5d57f8e0cbe1abe972e6b5dacc95` |

精确路径、大小和class/locale摘要见build/editor-grill-20261114/artifact-manifest.json，源码身份见source-freeze.json，二次核实见candidate-identity-check.json。

## 原生离屏图检查

三端各3张，共9张，位于docs/evidence/2026-11-14-editor-grill/<target>/。已直接查看本轮Fabric真实设置页/警告窗及编辑器图：恢复窗显示被确认的“外观·显示”，红警告图标和红确认、灰取消；编辑图可见八点、外置胶囊、9固定锚点、真实内容及仅还原/保存的footer；恢复图标已在hover背景内居中。截图大小/时间/hash见locale-and-visual-evidence.json。

这属于原生离屏光栅与行为验证，不是Minecraft实机交互、IME、GUI缩放或GPU整帧性能验收；未自行启动游戏。

## 部署准备及曾经暂停点（历史阶段）

- 仅只读枚举安装目录：Forge其他217、CCB其他462、Neo其他239文件均取得hash/size/mtime；路径及当前旧AtomChat摘要见deployment-readiness.json与三个*-other-mods-preparation.json。首次枚举遇一次瞬时PermissionError，重做完整只读枚举后均可读；未改变属性或文件。
- 当前仍是旧安装包，实例目录未写入；本次没有新backup/deployment receipt，绝不把12:28旧receipt当新部署。
- 准备阶段发现CCB游戏进程pid32928正在运行（game-process-preparation.json）；本代理未启动或操作它。主代理放行后须再做fresh检测，目标运行时不替换包；届时重新读取完整其他文件基线再备份部署。
- **现在暂停在实际部署之前，交主代理独立code-review。** 放行后仍由同一代理备份部署三个明确实例、独立二次回读installed/backup/其他mods/source freeze，更新最终记录。用户已取消readonly，不再激活。

无commit、push、发布或外部消息。原WIP和D:/AtomChat-Backups/incident-20261113-224928均保留。


## 本次实际部署完成（2026-11-14，复审放行后）

主代理报告parent-review.md直接复核核心实现/调用者/tests/native图，并独立回读562源/三JAR/JUnit计数相符，无新actionable finding；随后明确允许部署。

新备份：`D:\AtomChat-Backups\2026-11-14-editor-grill\20261114-152929-912953`。实际部署前和每目标换包前Get-CimInstance均未发现Minecraft，逐目标检测到替换均<90秒；准备阶段CCB曾运行不是实际部署时状态。只替换AtomChat，其他mods不写入；未启动游戏、未改生产源、未提交/推送/发布，门控和readonly不再建立。

- Forge / 1.20.1-main：安装 `967f4d3239551f50d2d866b59ad4aa12d5ada266f372cf64fc526ad193b1e4e7`；其他217项hash/size/mtime不变。
- Fabric / 1.21.1-CCB：安装 `87704c398cf5d3cfd7888658e21a383df26fbf75b5d2c71cea50db17ec0ee872`；其他434项hash/size/mtime不变。
- NeoForge / Mechanomania-航空学：安装 `89deaccd1404a1f816964da0e79ebac80eab5d57f8e0cbe1abe972e6b5dacc95`；其他239项hash/size/mtime不变。

CCB目录在准备快照之后少了28项（旧备份/旧版本条目，包含旧AtomLib条目），清单见other-mods-baseline-delta.json，非本代理删除。第一次部署尝试在任何替换前停止（deployment-attempt-1-stopped.json）；停止备份保留。第二次重新读取当前完整文件集建立217/434/239新基线，全部旧AtomChat先备份后再逐目标替换；不拿462旧数量作本次不变结论。

独立另一个Python进程未导入部署helper，重新读取installed、candidate、旧backup、全部其他mods和562源：全相符。backup还与先前独立部署准备观察的旧AtomChat SHA一致。结果independent-deployment-readback.json；完整结构化receipt为deployment-receipt.json，备份目录另存同份receipt。简明回执build/editor-grill-20261114/deployment-summary.md。

普通离屏/native及构建结论范围不变，尚未实机验收。主代理已再独立逐文件核验安装包、候选、旧包备份、其他217/434/239文件及562源全部相符，git diff --check再次通过；最终核验记录见parent-review.md。

## 会话收尾与归档

用户已自行进入游戏测试，并要求结束本次排查、提交和清理临时文件。15:34错误报告的排查到此停止，没有因此修改生产代码或安装依赖，也不把它标为已解决。

关键验证、源与产物摘要、独立审查和部署回执现保存在 `docs/evidence/2026-11-14-editor-grill/verification/`。本报告中的 `build/editor-*` 等路径是执行时位置，完整原始日志与临时探针已收进本机归档 `D:/AtomChat-Backups/2026-11-14-session-close/20261114-154456/session-temporary-evidence.zip`，成员保留原相对路径；归档校验通过后，4203份本轮临时文件通过Windows回收站API清理，没有永久删除。索引见 `docs/evidence/2026-11-14-session-close/archive-index.json`。

原始错误压缩包单独保存在上述备份的 `private-error-reports/`，不进入Git。源码、测试、离线依赖、最终JAR和各次部署旧包备份保留。收尾复用15:12–15:19三端测试构建及原生像素结果，已核对相关生产源码没有变化；本轮另跑targets和bare检查均通过，未重新运行完整构建或游戏。
