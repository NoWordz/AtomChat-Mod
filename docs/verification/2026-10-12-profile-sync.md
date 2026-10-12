# Profile Sync 验证记录（2026-10-12）

## 结果与范围

- Profile Sync 已部署到 Fabric 1.21.1 1.21.1-CCB、NeoForge 1.21.1 Mechanomania-航空学、Forge 1.20.1 1.20.1-main。
- 三端完整测试：Fabric 1204、NeoForge 1192、Forge 1192；各端 0 failures、0 errors、2 skipped。三端 assemble、架构与目标守卫、jar 检查均通过。
- 部署包装器曾在最终标记生成前打印缺失文件提示；随后独立 manifest 哈希检查退出码为 0，三端目标均与构建产物 SHA-256 一致：

| 平台 | 部署文件 | SHA-256 |
|---|---|---|
| Fabric 1.21.1 | atomchat-Fabric-1.21.1-0.3.0-Preview.jar | A62C0CE47F2F4CC20FF6E064F6BD7298D74DE21D2051B3E11273F930FF3BAC99 |
| NeoForge 1.21.1 | atomchat-NeoForge-1.21.1-0.3.0.jar | 8C7CA1F097C7787C5CAF63419DD8E9B9E345F677F44F62E00AC60D57C7D00B0E |
| Forge 1.20.1 | atomchat-Forge-1.20.1-0.3.0.jar | 215397D6222422C666204E1F049243DEFBF6B5809B06667878EC0AB08E8513CE |

## 复审与剩余风险

- 独立复审最终未发现阻断问题。身份授权、过期响应、清空意图持久化、旧服务端兼容及平台包方向等发现已修正后完成全量回归；根代理另复核了存储恢复和额度重载修正。
- 后续存储恢复与热重载修正经定向回归通过：启动时回收模组已知格式的未完成事务文件，活跃仓库采用重新加载后的容量；未识别的手动恢复文件不会删除。
- 未启动客户端；真实多人资料同步仍待用户验收。代码审查、自动化及部署结果不代表此项已通过。

## 旧 jar 备份留存

- 三阶段共 9 份旧 jar 已保留到 D:\AtomChat-Backups\2026-10-12-closeout，并逐份核对 SHA-256；目录内 backup-manifest.json 包含路径、字节数和完整哈希。下列为资料同步部署前的三个备份。
- Fabric 1.21.1-CCB：E72C194F829883AEFA78EB2E722E3BAD585738980A9071384317061AFDAEB3F6
- NeoForge Mechanomania-航空学：1C6E8BD4B080A15F9CF423AFBB93FACE18B305BC698233932CF540346C715D9A
- Forge 1.20.1-main：69DDE4B18C6CAEA35BD5DCF92E80913D665039246B82CEDD1B7AC4522C6E2E91
