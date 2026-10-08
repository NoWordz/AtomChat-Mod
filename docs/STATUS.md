# AtomChat 项目状态

> 交接文档：给下一个会话（或未来的自己）快速定位「现在在哪、下一步做什么」。
> 更新于 **v0.3.0 发版后**（commit `07e0a05`，tag `v0.3.0`）。

## 当前位置

- **版本**：`0.3.0`（`gradle.properties:17`）；tag `v0.3.0` 已打，**未 push 远端**（用户明说不推）。
- **构建**：`bash /d/Claude_ds/_atomchat_build_all.sh` → 三平台 BUILD SUCCESSFUL + 守卫 + `_parity_check.py` PASS。
- **部署**：五实例已铺 0.3.0（见下），md5 已配对。
- **许可**：Apache-2.0（根 `LICENSE`、三平台 `src/main/resources/LICENSE`、`fabric.mod.json`、`gradle.properties` 的 `mod_license` 四处一致，版权行 `E33EPUS`）。

| 平台 | 实例目录 | 部署 jar md5 |
|---|---|---|
| Fabric 1.21.1 | `1.21.1-CCB` | `861edecc` |
| NeoForge 1.21.1 | `Mechanomania-航空学` | `07d66c4c` |
| Forge 1.20.1 | `1.20.1-main` / `Go Fishing` / `元素觉醒1.4.6` | `3dae9ffb` |

上表是**0.3.0 之后四功能轮（plan `2026-10-08-atomchat-history-toast-poke-hudnotify.md`）的当前构建**：

- **T1 历史折叠** — `db87113`。内存仍 500 条，列表初始只展示最近 100，顶部「加载更早消息」胶囊每点一次揭 100 并做锚点补偿；公屏与每个私聊各自独立、关面板即忘。
- **T2 操作反馈 Toast** — `112295d`。清缓存/取消壁纸/清历史/复制/存图/头像头图移除 都有浮动反馈（成功=主题 accent、失败=共享危险红），底层 API 改为结果感知（剪贴板返回 bool、`clearDiskCache` 返回删除数）。
- **T3 戳一戳** — `ca82d39`。双击头像=变调音效+面板内 Toast+摇头像；定位改发送者 UUID；**拒自己**；服务端权威限流的远程真戳（不弹横幅，未协商通道则静默退回本地）。
- **T4 通知横幅搬屏幕顶（Skija 同源 HUD）** — **未开始**。需新增三端常驻 Skija HUD owner 并抽 `NotificationQueue`；这条是 0.2.4 事故（saveLayer 泄漏甩飞 UI）的旧路，用户已授权重开但要求逐条核对 `SkiaGraphics.draw` 的 saveBase 护栏。

版本号保持 `0.3.0` 不动。三端测试 669/657/657 全绿 + 守卫 PASS。

> 前一轮（引用胶囊取色 + 胶囊族软阴影 + `[图片]` 占位绿）已随 commit `d8fc806` 入库并验收。

实例都在 `D:\Myworld\.minecraft\versions\<实例>\mods\`；配置文件在 `<实例>\config\atomchat\atomchat-client.json`。

## 代码结构

```
D:\AtomChat-Main\
  shared/                        平台无关逻辑（config / ui tokens / theme / banner / wallpaper / 字体…）
  layers/mapping/official/       官方映射层的公共源码（与 fabric 树行级等价，仅映射名不同）
  platforms/
    1.21.1-fabric/               Fabric 1.21.1（屏幕类在 net.minecraft.client.gui.screen.AtomChatScreen）
    1.21.1-neoforge/             NeoForge 1.21.1
    1.20.1-forge/                Forge 1.20.1（Java 17；另两平台要 Java 21）
```

**孪生规则**：`layers/mapping/official/` 与 `platforms/1.21.1-fabric` 下的同名文件必须行级等价（只允许映射名差异）；改一处必须同步另一处，否则 `_parity_check.py` 会红。

## 这一版（0.3.0）做完的事

十二个验收轮次累计，详见 `CHANGELOG.md` 的 v0.3.0 段。要点：

- 动效整体换成指数趋近（帧率无关、起步快、落定稳）：页面切换、开屏、关屏；缩放为两阶段串行。
- 开屏动画跟随导航风格（zoom 0.94→1 / slide 横入）；关屏用短时长（τ40ms）+ 提前收尾。
- 个人档案页按 QQ 主页重做：自定义头图（带裁剪）、头像跨压、统计瓦片、信息大卡、身份卡。
- 「整卡」语言统一（关于页第三方 / mod 信息 / 档案信息区 / 身份卡）：发丝分割线 + 整卡缩放 + 逐行高亮。
- 双轴缩放：整机 `uiScale` + 内容 `contentScale`（手机框尺寸不变、内部随其缩放）。
- 悬停统一为强调色染色；卡片边框回发丝感；描边完全按所选颜色绘制（不再自动换灰）。
- 许可转 Apache-2.0。

## 已知限制 / 未做

1. **只支持 Windows x64**（Skia 原生库只打包 Windows）。
2. **未实机验证到「零问题」**：0.3.0 的观感（关屏手感、整卡、开屏 zoom）仍需用户真机验收。
3. **性能只有静态审计**：这一版做了 8 处低风险热路径优化（字体测量 / 阴影滤镜缓存等），但没有 in-game profiler 数据，帧率对比未测。
4. 半径迁移对「跑过 round3 那个未发布中间构建」的开发期配置会二次换算（删 `config/atomchat/atomchat-client.json` 即可，不影响正式发布版本）。
5. 历史 CHANGELOG 段（v0.1.8–v0.2.15）中英条目数不完全对等 —— 按规范历史段只统一骨架、不补译，有意保留。

## 下一会话的入手点

- **待用户真机验收后的反馈轮**：用户明说「下个会话还会继续优化」，预期是新一轮「投诉 → 审计 → grill → 工作流 → 构建部署」循环。
- **动工前先读**：`~/.agents/memory/project_atomchat-mod.md` 的 6e-15 / 6e-16 / 6e-17 三节（最近三轮的根因与教训）+ 本文件。
- **UI 改动纪律**（血泪教训，务必遵守）：
  - UI 动工前先在仓库里找现成同类控件照抄，不要自发明设计（已被推翻多次）。
  - 几何只在 `UiLayout` 算、间距只从 `UiTokens` 取；改 token 前 grep 全部调用点。
  - 设计敏感的页面先出 HTML 草稿（本地 http.server + ZCode IAB 预览）再实现。
  - 同 worktree 并发跑 gradle 会互相破坏，最终必须主代理串行跑一次全量构建。

## 工作流 / 发版注意

- 版本号 / 发版 / commit / push 都是**用户明示才动**；「修完」不等于「发版」。
- **子代理会把评审临时产物（`.review-*.diff`）留在工作区**，`git add -A` 前务必检查；`.review-*` 已加进 `.gitignore`。
- 工作流若因额度停摆（code 1005 / 1310），从 GUI 切模型会生成接力 run，无需手动 Resume；已完成的步骤走缓存。
- 发版必附双语 CHANGELOG（`CHANGELOG.md` 长账 + `RELEASE_NOTES.md` 浓缩），格式见 skill `changelog-format`；改完跑 `python /d/Claude_ds/_parity_check.py CHANGELOG.md`（**要传文件名**）。
