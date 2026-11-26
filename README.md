# LifeOS Focus

个人 Android 专注管理应用，与 LifeOS 日程规划联动，根据任务自动开始/结束专注，按模式限制应用，并提供可审查的执行记录。

当前预览版 **v0.3.0-alpha**：专注计时主页、白名单快捷启动、本地自动分类、手动改分类、类别允许与单应用例外。用户已明确授权继续这些功能，保留 Phase01 尚未完成的真机验收记录，不自动合并 main。

使用见[分类和专注操作说明](docs/CATEGORIES_AND_FOCUS.md)，验证见[本轮验证记录](tests/CATEGORIES_VERIFICATION.md)。专注页不提供系统设置快捷入口；普通退出等待10秒后确认，应急解除保留。当前仍为离线预览版，在线 ChatGPT 计划同步、多模式和长期稳定性验收尚未完成。

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

第一阶段扩展测试：[完整授权与系统能力矩阵](tests/PHASE01_CAPABILITY_MATRIX.md)。

已实现 `android/` Kotlin/Compose 测试工程、Gradle wrapper、核心单元测试、权限审计脚本和 GitHub Actions。在 `phase01-poc` 分支交付离线测试 APK。操作见 [安装与 T01–T12 指南](docs/INSTALL_AND_TEST.md)，本地验证见 [构建验证记录](tests/BUILD_VERIFICATION.md)。已有USB短测，完整MagicOS验收尚未完成，不合并main。

允许用户授权后向受控私有目的地共享应用名称/包名清单、分类及详细使用记录；这些元数据可能暴露生活习惯，须可预览、可选择、可撤销同步。禁止读取或存储消息、屏幕文字、输入、通知正文等个人内容。公开仓库仅保存代码、通用文档、模拟数据和审查通过的脱敏报告。Phase 01 APK 完全离线。
