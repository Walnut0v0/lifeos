# 当前任务 — A自然简约正式界面与后台修复

分支：ui-redesign，基于 phase05-google-sheets @ 5f500e6641b0029a34b2d11e524ae352b0656051。

执行入口：[UI_REDESIGN.md](UI_REDESIGN.md)。先读 AGENTS.md 和任务书列出的现有规范。
本轮优先完成：
- [x] 审计现有页面、业务状态与安全/隐私入口，建立功能映射。
- [x] 实际调研 Material 3、Now in Android、Compose Samples、Vico 和可访问的设计资源，记录版本、许可及复用选择。
- [x] 制作自然简约、黑白极简、Material 3 Expressive 三套首页与专注页 Compose 原型。
- [x] 提供实际渲染预览；无法渲染时提供独立测试 APK 与限制说明。
- [x] 建立 docs/UI_REDESIGN.md 与原型对照说明，记录测试及尚未验证的项目。
- [x] Stage1已提交14406d0，独立预览已安装；用户明确选择A自然简约，进入Stage2。

原功能接入任务及待验收项目保留于 [PHASE05_BASELINE.md](PHASE05_BASELINE.md)，不因本任务而视为完成。
保留原 lab/online 隔离、隐私与安全约束、所有现有功能；不修改真实数据，不使用 computer-use，不自动合并 main 或原功能分支。

Stage1已交付独立debug应用io.lifeos.uipreview：A/B/C共享虚构状态，支持深浅色、字体缩放、九种状态、时段圆环和全屏白名单演示。两项模型测试、Lint0错误0警告、独立权限/依赖审计和v2签名通过；原业务25项测试两变体仍通过，正式app源零改动。原型实际渲染图未获得，按任务书回退独立APK，已USB安装并启动，视觉/TalkBack/交互待用户。已请求风格选择。此前功能/UI候选保存在codex/phase06-focus-ui@c64923c，不丢弃、不并入原型，不覆盖安装原LifeOS。

## Stage2：用户已选A自然简约
2026-11-28用户在手机独立预览中选择A，开始整合正式主页、时段圆环、今日时间轴、全屏白名单及权限提示，同时保留候选后台连续判断修复。最终构建/审计/手机安装结果待本轮追加；不得把原型启动当成正式界面或后台验收。

正式本轮范围：ui/theme统一深浅色与系统栏；ui/components时段圆环/今日时间轴/全屏可搜索白名单；ui/screens/home真实任务与计时入口；权限前台弹窗、授权/断连区分；普通退出确认内和覆盖层固定应急入口。在线/离线隔离不变，无新增系统权限。后台仅对有效未解除计划保留内存单一前台候选；服务中断反馈不当作撤权，真正断连仍放行。最终APK结果见tests/UI_BACKGROUND_VERIFICATION.md。Stage3独立统计/监管/导航迁移尚未实现，不删除现有入口。

最终33号包：两变体各32项单测、构建/Lint/权限/release/签名通过，Lab47/online90警告；最终APK/哈希已生成。首次32号安装对照复现PLAN_PERMISSION_LOST短暂重连被误记解除，33号已拆分PlanStopPolicy并测试；当前因ADB设备列表为空等待重连，尚未安装33号，不伪造真机结果。
