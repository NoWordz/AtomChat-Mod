# 渲染锐度 + 性能/内存修复部署（2026-11-02）

- 构建提交：`2a25c5a3`（`git_dirty=false`，三端构建戳一致）
- 备份根：`D:/AtomChat-Backups/2026-11-02-render-quality/20261102-172814/`
- 台账：`verification/2026-11-02-render-quality-deployment.json`

## 产物

| 目标 | jar | bytes | sha256 |
|---|---|---|---|
| 1.20.1-forge | `atomchat-Forge-1.20.1-v0.4.0.jar` | 8471041 | `d1fd473e40cf…` |
| 1.21.1-fabric | `atomchat-Fabric-1.21.1-v0.4.0.jar` | 8492616 | `abe683d88f4a…` |
| 1.21.1-neoforge | `atomchat-NeoForge-1.21.1-v0.4.0.jar` | 8483328 | `4d1d54ddefdc…` |

## 替换与回滚点

| 实例 mods 目录 | 旧包 sha256（= 上一轮台账的 new） | 新包 sha256 | mods 可见文件数 |
|---|---|---|---|
| `1.20.1-main` | `6dfb1a82c2a8…` 8458279 | `d1fd473e40cf…` 8471041 | 164 → 164 |
| `1.21.1-CCB` | `89cbe654cd11…` 8479633 | `abe683d88f4a…` 8492616 | 76 → 76 |
| `Mechanomania-航空学` | `315df7a20b54…` 8470731 | `4d1d54ddefdc…` 8483328 | 206 → 206 |

三个实例各只有一份 atomchat jar；备份读回哈希与源逐一吻合；替换后读回哈希与新包逐一吻合。

**链完整性**：旧包哈希与 `2026-11-02-snap-device-grid-deployment.json` 记录的 `new_sha256` 完全一致，说明本次是从上一轮已确认的状态升级，中间没有未知替换。

回滚：把 `备份根/<loader>/atomchat-*.jar` 拷回对应 mods 目录覆盖即可。

## 字节码核对（Fabric jar）

- `com/atom/chat/render/ImageQuality.class`、`SkiaImages.class` 均在包内（三端各查）。
- `SkiaImages.drawRoundedImage` 的字节码含 `invokestatic ImageQuality.scaledForLogical`（两处包装各一次）。
- `MessageListView` 含 `heightList` / `rowTop` / `rowHeight` / `dividerTop` / `heightReaction` 字段。
- `ReactionClient` 含 `KEYS` / `KEY_MISSES` / `revision`。

## 未验证

- **实机观感**：锐度改动必须进游戏看。三个客户端需完全重启（旧进程里的 jar 不会重载）。
- 24 小时长滚动下的 `ImageQuality` 缓存内存曲线未实测（有 4M 像素上限兜底）。
- ARM64 与专服启动未验证；未发版（仍 `0.4.0`）。
