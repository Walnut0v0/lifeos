# 开发代理约束

先读 tasks/ACTIVE.md、docs/REQUIREMENTS.md、docs/ARCHITECTURE.md、docs/PRIVACY.md、docs/ROADMAP.md、tests/PHASE01_TEST_PLAN.md。隐私与安全规范优先，存在冲突时先采取最小权限和故障放行。

- 当前只做 Phase 01；独立 phase01-poc 分支，不提前开发云同步、完整分类、严格锁定。
- Kotlin/Compose 原生工程，最小依赖、可重现构建；英文代码命名，中文面向用户文档。
- 不得读取私人内容，不引入 INTERNET 或遥测；验证最终合并 Manifest 和 APK 权限。
- 仅用户选中的非必要应用作为测试目标，有限时长，停止入口可用；禁止阻止撤权/卸载/设置及必要系统功能。
- 所有秘密、真实日程、应用清单、使用记录、日志、签名文件、配置不得进入仓库；允许授权的应用元数据与详细记录另行发送至受控私有目的地，不在公开 GitHub 自动上传。
- 保持提交历史；不覆盖已有工作，不强推，不自动合并未验收分支；维护 ACTIVE.md、handoff.md。
- 明确区分构建、单元测试、模拟器和真机测试；不得伪造结果。记录技术决策、已知问题、复现和修复。
- 第一阶段输出 APK、哈希、操作说明与测试计划/报告；未确认 MagicOS 实际能力前不作保证。
