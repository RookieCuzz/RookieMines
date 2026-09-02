# 开发与运维说明

## 环境要求

| 用途 | 版本 |
| --- | --- |
| 插件运行 | Paper 1.21.4、Java 21 |
| Java 构建 | Java 21、Maven 3.9+，或仓库内 Maven Wrapper |
| Mineflayer E2E | Node.js 22+、PowerShell、Java 21 |

这是纯服务端 Paper 插件，玩家客户端不需要安装模组或资源包。

## 源码结构

```text
src/main/java/dev/rookiemines/
├─ RookieMinesPlugin.java       插件生命周期与依赖装配
├─ MineCommand.java             /rmine 命令与补全
├─ MineListener.java            挖掘、击杀、交互和离开事件
├─ mine/
│  ├─ FloorPlanGenerator.java   纯内存楼层蓝图生成器
│  ├─ FloorManager.java         生成任务、世界应用与玩法状态
│  ├─ GenerationRules.java      楼层主题、种子和梯子公式
│  └─ ...                       蓝图、描述符和数据模型
├─ storage/                     矿层状态与玩家进度 YAML
└─ world/                       独立虚空世界创建
```

生成流程分为三段：

1. 主线程创建楼层描述符并登记等待玩家；
2. 异步线程只计算不访问 Bukkit 世界的 `FloorPlan`；
3. 主线程异步预加载区块，再按 `generation.blocks-per-tick` 分批应用方块，最后生成容器、怪物并传送等待玩家。

涉及 Bukkit 世界、实体、背包或配置保存的逻辑必须继续留在主线程。

## 配置约束

`GeneratorSettings` 在启动时校验以下硬约束：

- `generation.min-size >= 24`
- `generation.max-size >= generation.min-size`
- `10 <= generation.height <= 48`
- `world.floor-spacing > generation.max-size + 8`

概率字段建议保持在 `0.0–1.0`。`generation.blocks-per-tick` 越大，矿层完成越快，但单 Tick 峰值也越高；代码最低按 500 处理。调整尺寸、高度或楼层间距后，旧世界坐标与已保存描述符可能不再匹配，正式服应使用新世界名或在完整备份后清理旧矿洞数据。

`forced-day: -1` 表示跟随 `world.source-day-world` 的完整游戏时间。`test-mode` 会开放机器可读测试响应和挖掘夹具，只能在隔离测试服启用。

## 数据与备份

运行后会产生三组相关数据：

- `plugins/RookieMines/config.yml`
- `plugins/RookieMines/floors.yml` 与 `players.yml`
- `rookie_mines/` 世界目录

备份和恢复时必须把插件数据目录与矿洞世界放在同一个时间点处理，否则保存的梯子、奖励箱坐标和世界方块可能不一致。插件目前没有运行时重载命令，修改配置后应完整重启 Paper。

## 单元测试

```powershell
.\mvnw.cmd -B -ntp clean verify
```

JUnit 测试覆盖主题边界、种子确定性、跨天变化、梯子概率、入口安全、外壳封闭、矿物主题、第 120/121 层固定结构和骷髅洞特殊房间。

## Mineflayer 端到端测试

```powershell
.\scripts\run-e2e.ps1
```

脚本会执行以下动作：

1. 干净构建插件；
2. 下载并 SHA-256 校验 Paper `1.21.4-232`；
3. 用 `npm ci` 安装锁定的 Mineflayer 依赖；
4. 在随机本地端口启动隔离、离线模式的 Paper；
5. 连接机器人，验证真实方块可见、实际挖掘与完整进度流程；
6. 正常退出机器人和服务器。

测试服的 `online-mode=false` 只用于本机自动化，绝不能照搬到公网服务器。当前启动参数针对 Windows/JDK 的选择器与本地域套接字兼容问题进行了处理，因此 GitHub E2E workflow 使用 `windows-latest`。

失败记录会复制到已忽略的 `e2e/artifacts/`，便于本地分析和 CI 上传；Paper 与依赖缓存位于 `e2e/cache/`、`e2e/runtime/`。

## 版本与发布检查

版本号以 `pom.xml` 的 `<version>` 为准，Maven 资源过滤会把它写入 `plugin.yml`。准备发布时：

1. 将 Maven 版本从 `SNAPSHOT` 改为正式版本；
2. 更新 `CHANGELOG.md`；
3. 执行 JUnit 与 Mineflayer E2E；
4. 检查 `target/RookieMines-*.jar` 内的 `plugin.yml` 和主类；
5. 为 JAR 计算 SHA-256，并创建与版本一致的 Git tag/Release。

## 当前限制

- `/rmine` 目前要求由在线玩家执行，控制台不能指定目标玩家；
- 进入矿洞前的返回位置只保存在内存中，服务器重启后 `/rmine leave` 会回到首个世界出生点；
- 第 120 层奖励只能领取一次，玩家丢失骷髅钥匙后暂时没有自助找回流程；
- 尚未提供旧版本数据迁移器、语言文件或第三方经济/物品插件桥接。
