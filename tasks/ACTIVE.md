# 当前任务 — LifeOS UI/UX 重构（Stage 1）

分支：ui-redesign，基于 phase05-google-sheets @ 5f500e6641b0029a34b2d11e524ae352b0656051。

执行入口：[UI_REDESIGN.md](UI_REDESIGN.md)。先读 AGENTS.md 和任务书列出的现有规范。
本轮优先完成：
- [ ] 审计现有页面、业务状态与安全/隐私入口，建立功能映射。
- [ ] 实际调研 Material 3、Now in Android、Compose Samples、Vico 和可访问的设计资源，记录版本、许可及复用选择。
- [ ] 制作自然简约、黑白极简、Material 3 Expressive 三套首页与专注页 Compose 原型。
- [ ] 提供实际渲染预览；无法渲染时提供独立测试 APK 与限制说明。
- [ ] 建立 docs/UI_REDESIGN.md 与原型对照说明，记录测试及尚未验证的项目。
- [ ] 提交 Stage 1 成果，等待用户选定风格；不提前全量改造。

原功能接入任务及待验收项目保留于 [PHASE05_BASELINE.md](PHASE05_BASELINE.md)，不因本任务而视为完成。
保留原 lab/online 隔离、隐私与安全约束、所有现有功能；不修改真实数据，不使用 computer-use，不自动合并 main 或原功能分支。
