# AtomChat UI 防回归约定

用户已于2026-11-14撤回SHA锁、trust、hook和只读门禁需求。保留普通功能与回归约定，详见[docs/UI_CONTRACT.md](docs/UI_CONTRACT.md)。

- 新UI复用现有组件；警告红图标/红确认/灰取消，教程及普通编辑主操作保留强调色例外
- 同类SVG复用token尺寸和tight bounds居中；头栏复用返回/过滤的布局、hover/press/ripple
- 不将局部撤销扩成全仓回退；保留已认可教程/HUD/阴影/点阵并记录版本依据
- 保留普通行为回归与原生像素测试；交付如实报告实际执行、失败、跳过和实机限制
- 不再建立SHA保护清单、独立信任副本、提交锁定钩子或Windows只读锁；不为此继续做变异验证
