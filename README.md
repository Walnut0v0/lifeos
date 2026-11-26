# LifeOS Focus

个人 Android 专注管理应用，与 LifeOS 日程规划联动，根据任务自动开始/结束专注，按模式限制应用，并提供可审查的执行记录。

当前阶段：**Phase 01 — MagicOS 11 真机兼容性验证**。目标设备荣耀手机，实际 Android API Level 由测试应用读取。先交付离线 LifeOS Focus Lab APK，再根据实测确定正式架构。

## 文档导航
- [完整需求](docs/REQUIREMENTS.md)
- [架构与技术方案](docs/ARCHITECTURE.md)
- [隐私与安全规范](docs/PRIVACY.md)
- [开发路线](docs/ROADMAP.md)
- [第一阶段测试方案](tests/PHASE01_TEST_PLAN.md)
- [第一阶段报告模板](tests/PHASE01_REPORT.md)
- [当前开发任务](tasks/ACTIVE.md)
- [开发代理规范](AGENTS.md)
- [交接记录](handoff.md)

当前仅初始化文档，尚无 Android 工程、APK 或真机测试结果。Codex 从 ACTIVE.md 开始，在 phase01-poc 分支开发。

允许用户授权后向受控私有目的地共享应用名称/包名清单、分类及详细使用记录；这些元数据可能暴露生活习惯，须可预览、可选择、可撤销同步。禁止读取或存储消息、屏幕文字、输入、通知正文等个人内容。公开仓库仅保存代码、通用文档、模拟数据和审查通过的脱敏报告。Phase 01 APK 完全离线。
