# LifeOS Focus

个人 Android 专注管理应用，与 LifeOS 日程规划联动，根据任务自动开始/结束专注，按模式限制应用，并提供可审查的执行记录。

当前开发版 **v0.4.1-alpha**：保留计时主页、分类及白名单；新增独立online变体、受认证HTTPS同步、完整计划缓存与绝对时间调度、可选全天统计及ChatGPT计划调整入口。lab变体保持无INTERNET。

项目已迁到 `D:/document/10.others/lifeos`，包括Git历史、工具链、缓存和交付物。[项目布局](docs/PROJECT_LAYOUT.md)、[在线版操作及接入](docs/ONLINE_SETUP.md)、[本轮验证](tests/ONLINE_VERIFICATION.md)。Google drive.file授权与私有交换表已完成真机联通：模式/同步状态写入读回、两段20秒计划自动执行与空计划取消通过。仍需锁屏/断网/后台和统计验收；main不自动合并。[Google接入说明](docs/GOOGLE_CONNECTION.md)。

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

## Focus Exchange v1.0 接口已建立
以 [正式接口规范](docs/FOCUS_EXCHANGE_PROTOCOL.md) 为准：私有工作簿已创建和验证，Schedule!A2 为唯一版本化日程源，手机拥有模式配置及使用记录。用户私下选择/配置真实表，不在仓库提交表 ID。优先完成 Android OAuth + 读取未发布快照 + 上传模式 + 两段测试日程的端到端联调，再做真实复盘；原拟议结构如有差异以正式协议为准。
