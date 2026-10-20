# 0.3.1 点阵与窄面板布局验证

## 本轮范围

- 空会话仍在原图标与文案后使用局部点阵，不再向列表外或底栏溢出。
- 个人资料的默认渐变头图使用低对比点阵；自定义照片保持原样。
- 表情与快捷短语浮层保留朝触发按钮渐隐的连接点阵。
- 消息正文、图片、引用、反应条和文本选择共用同一条自适应宽度规则；窄面板不再同时预留两侧头像列。
- 极窄消息的选择换行与绘制换行保持同一宽度。

## 验证

| 目标 | 测试 | 结果 |
|---|---:|---|
| NeoForge 1.21.1 | 1330 | 0 失败，0 错误，2 跳过 |
| Fabric 1.21.1 | 1342 | 0 失败，0 错误，2 跳过 |
| Forge 1.20.1 | 1330 | 0 失败，0 错误，2 跳过 |

`verify_targets.py`、孪生残差检查、`verify_jars.py --platform windows-x64` 和 `verify_chat_assets.py` 均通过。构建内的裸值检查通过。

离屏预览：

- `platforms/1.21.1-neoforge/build/preview/secondary-dots.png`
- `platforms/1.21.1-neoforge/build/preview/profile-banner-dots.png`
- `platforms/1.21.1-neoforge/build/preview/responsive-messages.png`

这些是 CPU 光栅预览，不等同于游戏内观感验收；本轮没有启动客户端。

## 部署

- 实例：`D:/Myworld/.minecraft/versions/1.21.1-CCB/mods`
- 新包：`atomchat-Fabric-1.21.1-0.3.1.jar`
- SHA-256：`74d6b1856e740537bef4cdc4551625432bbbe6bad6bdc9054508881b8788fb8a`
- 旧包备份：`D:/AtomChat-Backups/2026-10-20-dots-layout/CCB/atomchat-Fabric-1.21.1-0.3.1.jar`
- 旧包 SHA-256：`3aa2ad9daec911de67aafd14e0b78c9f110b6295f8b2ba0234e42987481302e8`
