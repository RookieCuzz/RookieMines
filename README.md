# RookieMines（菜鸟矿洞）

面向 Paper 1.21.4、Java 21 的程序化矿洞插件。玩家从普通矿洞逐层深入，解锁电梯、领取阶段奖励，并在第 120 层取得“骷髅钥匙”后进入会话制骷髅洞。

> 当前版本：`1.0.0-SNAPSHOT`。这是可运行、可测试的开发版本，正式服部署前请先在测试服备份并验证配置。

## 玩法概览

| 楼层 | 区域 | 主要资源与敌人 | 地图方式 |
| --- | --- | --- | --- |
| 1–39 | 泥土矿区 | 铜、煤、紫水晶；史莱姆、僵尸 | 按天程序化生成 |
| 40–79 | 冰雪矿区 | 铁、青金石、钻石；流浪者、史莱姆 | 按天程序化生成 |
| 80–119 | 熔岩矿区 | 金、红石、钻石；岩浆怪、骷髅 | 按天程序化生成 |
| 120 | 终点奖励室 | 个人一次性“骷髅钥匙” | 代码构建的固定房间 |
| 121 | 骷髅洞大厅 | 通往骷髅洞的入口 | 代码构建的固定房间 |
| 122+ | 骷髅洞 | 钻石、金、绿宝石、远古残骸；高阶怪物 | 按会话程序化生成 |

普通矿层不是已有地图，也不依赖 `.schem` 或预生成世界文件。布局由“世界种子 + 游戏天数 + 楼层”确定：同一天重进会保留已经挖掉的方块和已出现的梯子，跨天则换种子重建。第 120 层和第 121 层也不读取外部地图，而是由代码生成固定房间。

核心机制包括：

- 连通洞穴、安全出生区、封闭外壳、暗层、主题水池、特殊房间、木桶、矿物和怪物；
- 挖石与击杀怪物触发梯子，最后一块计数石必定给出出口；
- 每 5 层记录个人电梯检查点，进度和已领取奖励持久化；
- 第 120 层奖励使用带插件标记的原版 `TRIAL_KEY`，只有持有者能进入骷髅洞大厅；
- 骷髅洞支持多层跳坑，最后一名玩家离开后结束本次会话并刷新；
- 楼层蓝图异步计算，主线程分批放置方块，降低一次性生成对服务器 Tick 的冲击。

完整规则见 [玩法说明](docs/GAMEPLAY.md)。

## 安装

要求：

- Paper `1.21.4`
- Java `21`
- 无前置插件

安装步骤：

1. 执行 `.\mvnw.cmd -B -ntp clean verify` 构建插件。
2. 将 `target/RookieMines-*.jar` 放入 Paper 的 `plugins/`。
3. 启动服务器。插件会创建独立虚空世界 `rookie_mines` 和数据目录 `plugins/RookieMines/`。
4. 按需修改生成后的 `plugins/RookieMines/config.yml`，然后重启服务器。

升级或改动世界生成参数前，请同时备份 `plugins/RookieMines/` 与 `rookie_mines/` 世界目录。

## 命令与权限

玩家命令使用权限 `rookiemines.use`，默认所有玩家拥有：

| 命令 | 用途 |
| --- | --- |
| `/rmine enter [floor]` | 进入第 1 层，或进入已解锁的普通矿洞检查点 |
| `/rmine leave` | 返回进入矿洞前的位置 |
| `/rmine status` | 查看当前矿层状态 |
| `/rmine elevator <floor>` | 前往已解锁的 5 层倍数检查点 |
| `/rmine skull` | 持有骷髅钥匙时进入第 121 层大厅 |

管理命令使用权限 `rookiemines.admin`，默认仅 OP 拥有：

| 命令 | 用途 |
| --- | --- |
| `/rmine admin day <数字|auto>` | 固定矿洞天数，或恢复跟随主世界时间 |
| `/rmine admin goto <floor>` | 忽略进度限制传送到指定楼层 |
| `/rmine admin regenerate <floor>` | 删除该层已保存状态并立即重建 |
| `/rmine admin force-ladder [floor]` | 强制为当前层或指定层创建出口 |
| `/rmine admin describe <floor>` | 查看指定楼层的主题、种子和生成参数 |

命令别名为 `/rookiemine`。右键点击矿层中生成的橡木活板门即可下楼。

## 配置

默认配置位于 `src/main/resources/config.yml`，主要包含：

- 独立世界名、世界种子、楼层间距和生成高度；
- 矿层尺寸、暗层概率、怪物密度、主题水池和特殊房间概率；
- 每 Tick 方块放置上限与梯子/跳坑概率；
- 仅供自动化测试使用的 `test-mode`。

公开服务器必须保持 `test-mode: false`。配置字段和开发约束见 [开发说明](docs/DEVELOPMENT.md)。

## 构建与测试

```powershell
.\mvnw.cmd -B -ntp clean verify
./scripts/run-e2e.ps1
```

第一条命令运行 9 个 JUnit 生成规则测试；第二条命令会校验并启动固定版本的 Paper `1.21.4-232`，连接 Mineflayer `4.38.0` 机器人，执行真实挖掘、下楼、跨天刷新、第 120 层领奖和骷髅洞会话重置测试。Node.js 端到端测试要求 Node `22+`。

## 项目结构

```text
RookieMines/
├─ .github/                 GitHub Actions 与 Issue 模板
├─ docs/                    玩法和开发文档
├─ mineflayer/              真实客户端端到端测试
├─ scripts/                 本地测试入口
├─ src/main/java/           Paper 插件源码
├─ src/main/resources/      plugin.yml 与默认配置
├─ src/test/java/           JUnit 生成规则测试
├─ pom.xml                  Maven 构建配置
└─ README.md
```

`target/`、`mineflayer/node_modules/`、Paper 下载缓存和端到端运行目录均为可再生成内容，不会提交到 Git。

## 来源说明

本项目根据 [Starfield-Pastoral](https://github.com/ChangQingElysium/Starfield-Pastoral) 所呈现的矿洞玩法思路重新设计为独立 Paper 插件，没有复制其源码、地图、材质或其他资产。若要直接复用上游内容，请自行核对上游许可证。

## 许可证

仓库当前没有附带开源许可证。添加明确许可证前，代码默认保留全部权利，不代表允许复制、修改、分发或商用。
