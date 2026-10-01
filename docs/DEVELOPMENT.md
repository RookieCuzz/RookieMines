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
├─ MineListener.java            玩法、菜单、登录和位置校正事件
├─ ElevatorMenu.java            玩家专属电梯 GUI、楼层槽位与防误点逻辑
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
3. 蓝图进入全局写入队列；主线程一次只为一个楼层预加载区块，并按 `generation.blocks-per-tick` 分批应用方块，最后生成容器、怪物并传送等待玩家。

蓝图计算可以并行，但世界写入只有一个全局槽位，因此多个玩家同时请求不同楼层时不会把每 Tick 写入预算按楼层数叠加。每次从异步规划、区块加载或分批写入返回主线程时，管理器都会重新核对楼层描述符。若游戏日或骷髅洞会话在生成过程中变化，旧任务会释放区块票据、保留玩家队列，并用新种子重新开始，不会提交过期蓝图。

涉及 Bukkit 世界、实体、背包或配置保存的逻辑必须继续留在主线程。

电梯菜单使用玩家专属的自定义 `InventoryHolder`，不从窗口标题、显示名或 Lore 反推楼层。菜单打开期间会取消整个库存视图的点击和拖拽；只有顶层有效按钮的普通左键会被接受。传送延迟到下一 Tick 执行，并再次核对玩家在线状态、权限、当前位置和菜单会话，避免重复点击、关闭窗口或切换界面后触发陈旧操作。目标楼层等待状态按玩家唯一保存，采用最后一次有效请求覆盖旧请求。

## 配置约束

`GeneratorSettings` 在启动时校验以下硬约束：

- `generation.min-size >= 24`
- `generation.max-size >= generation.min-size`
- `10 <= generation.height <= 48`
- `0 <= generation.floor-variation <= 4`
- `0 <= generation.ceiling-variation <= 6`
- `generation.height >= generation.floor-variation + 7`
- `0.0 <= generation.wall-roughness <= 1.0`
- `0.0 <= generation.formation-density <= 1.0`
- `world.floor-spacing > generation.max-size + 8`

暗层、池塘、特殊房间、墙面粗糙度等概率字段应保持在 `0.0–1.0`。`formation-density` 表示开放地面中尝试生成岩柱的目标占比，并额外限制为每层最多 140 处。`earth-cobweb-density` 和 `skull-cobweb-density` 表示开放洞穴面积对应的目标蜘蛛网密度，分别限制为每层最多 12 和 48 块；设为 `0` 可关闭对应主题。冰雪、熔岩、终点奖励室和大厅不会生成蜘蛛网。`generation.blocks-per-tick` 越大，矿层完成越快，但单 Tick 峰值也越高；代码最低按 500 处理。调整尺寸、高度或楼层间距后，旧世界坐标与已保存描述符可能不再匹配，正式服应使用新世界名或在完整备份后清理旧矿洞数据。

自然洞穴默认使用 2 格地面起伏、3 格顶面起伏、`0.42` 墙面粗糙度和 `0.018` 岩柱密度。相邻可走地面由生成器强制限制为最多一格高差，空气净空至少三格；这些是可玩性约束，不提供配置开关。四个洞穴塑形值都设为 `0` 可得到平地、平顶、无壁龛和无岩柱的轮廓，但不会恢复旧版种子。

生成规则版本与所有 `GeneratorSettings` 会共同参与种子计算。升级规则或修改任一生成配置后，插件会在启动时删除不匹配的楼层状态；楼层下次被请求时自动重建。携带旧楼层标记登录的玩家会先转移到非矿洞世界的安全出生点，生成完成后再送回。插件没有运行时重载，修改配置后仍须完整重启 Paper。

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

JUnit 测试覆盖主题边界、种子确定性、跨天变化、梯子概率、入口安全、外壳封闭、矿物主题、地面与顶面起伏、坡度连通性、墙面凹口、岩柱、蜘蛛网主题与三格净空、第 120/121 层固定结构、高起伏地形中特殊房间的步行可达性，以及电梯目的地布局和每玩家唯一等待目标。

## Mineflayer 端到端测试

```powershell
.\scripts\run-e2e.ps1
```

脚本会执行以下动作：

1. 干净构建插件；
2. 下载并 SHA-256 校验 Paper `1.21.4-232`；
3. 用 `npm ci` 安装锁定的 Mineflayer 依赖；
4. 在随机本地端口启动隔离、离线模式的 Paper；
5. 连接机器人，验证真实方块可见、实际挖掘、电梯 GUI 与完整进度流程；
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
