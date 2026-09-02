# 参与贡献

感谢你改进 RookieMines。提交 Issue 或 Pull Request 前请先确认改动面向 Paper 1.21.4 与 Java 21，并且不包含未经许可复制的上游源码、地图、材质或其他资产。

## 开发流程

1. 从 `main` 创建用途明确的分支。
2. 保持提交范围小而清晰，并为生成规则变化补充 JUnit 测试。
3. 执行 `.\mvnw.cmd -B -ntp clean verify`。
4. 涉及 Bukkit 事件、世界方块、进度或命令行为时，再执行 `.\scripts\run-e2e.ps1`。
5. 在 Pull Request 中说明玩法影响、配置兼容性和测试结果。

不要提交 `target/`、Paper 运行目录、下载缓存、`node_modules/` 或测试失败日志。详细架构和测试说明见 `docs/DEVELOPMENT.md`。

## 报告问题

请至少提供 Paper 构建号、Java 版本、插件版本、复现步骤、相关配置和完整报错。若问题涉及生成错误，请同时提供楼层号、游戏天数或骷髅洞会话、世界种子，以及 `plugins/RookieMines/floors.yml` 的脱敏片段。
