# ACTIVE — Phase 01

状态：待开发。目标：交付可安装的 LifeOS Focus Lab v0.1.0-poc APK，用于荣耀 MagicOS 11 实机验证。

## Codex 执行顺序
1. 阅读 AGENTS.md、全部 docs/ 与 tests/PHASE01_TEST_PLAN.md；检查仓库和工具链，创建 phase01-poc 分支或继续已存在分支。
2. 初始化 android/ Kotlin + Compose 工程、Gradle wrapper，确定并记录 SDK/JDK/依赖版本。
3. 实现独立测试入口：设备和权限诊断、应用扫描选择、前台包名检测、黑名单拦截（HOME 优先、覆盖层独立验证）、手动计时、自动起止、后台及开机恢复、脱敏诊断与报告导出。
4. 全局停止与故障放行优先；严格遵循 PRIVACY.md。无网络权限，不做完整分类、同步或严格锁定。
5. 编写纯逻辑单元测试（时段、到期、异常状态、规则优先级、停止），运行构建与测试，检查合并 Manifest、APK 权限；配置 GitHub Actions 构建并上传测试 APK artifact（限制保留期）。
6. 交付 lifeos-focus-lab-v0.1.0.apk（可安装测试签名）、SHA256、构建/安装操作指南、权限设置步骤、测试报告模板。
7. 用户按 T01–T08 真机测试；开发者记录实际结果、修复并复测。未实测项不得标通过。
8. 更新 handoff.md、README.md 与本文件；验收前不合并正式功能，不推进 Phase 02。

## 完成定义
代码能构建、测试可执行、APK 可安装（安装待实机确认）、测试说明完整、权限安全审查完成。Phase 01 最终验收仍须用户真机验证，报告标记所有未测试/失败项；安全失败阻断。
