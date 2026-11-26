# LifeOS Focus

个人 Android 专注管理应用，与 LifeOS 日程规划联动，根据任务自动开始/结束专注，按模式限制应用，并提供可审查的执行记录。

当前阶段：**Phase 01 — MagicOS 11 真机兼容性验证**。目标设备荣耀手机，实际 Android API Level 由测试应用读取。先交付离线 LifeOS Focus Lab APK，再根据实测确定正式架构。

当前测试补丁v0.1.1：针对B覆盖层1–2秒消失补充自身窗口识别及中文撤除原因；[本轮简单复测步骤](docs/OVERLAY_RETEST.md)。尚未确认MagicOS根因已修复。正式在线计划自动同步目标已记录为R15，当前包继续离线验证。

最新v0.1.3已进行USB实机短测：增加默认关闭的短时前台辅助识别，用户从桌面打开目标后确认B保持至约10秒测试上限；[USB诊断方式](docs/USB_DIAGNOSTICS.md)、[实机短测记录](tests/USB_DEVICE_VERIFICATION.md)。完整Phase01验收仍未完成。

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

已实现 `android/` Kotlin/Compose 测试工程、Gradle wrapper、核心单元测试、权限审计脚本和 GitHub Actions。在 `phase01-poc` 分支交付离线测试 APK。操作见 [安装与 T01–T12 指南](docs/INSTALL_AND_TEST.md)，本地验证见 [构建验证记录](tests/BUILD_VERIFICATION.md)。MagicOS 真机测试尚未执行，Phase 01 尚未最终验收，不合并 main。

允许用户授权后向受控私有目的地共享应用名称/包名清单、分类及详细使用记录；这些元数据可能暴露生活习惯，须可预览、可选择、可撤销同步。禁止读取或存储消息、屏幕文字、输入、通知正文等个人内容。公开仓库仅保存代码、通用文档、模拟数据和审查通过的脱敏报告。Phase 01 APK 完全离线。
