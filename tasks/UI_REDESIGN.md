# LifeOS Focus UI/UX 重构任务书

状态：已授权制定任务，待 Codex 执行；首先完成设计调研和三套可运行原型，用户确认风格后继续全量重构。
基线：phase05-google-sheets @ 5f500e6641b0029a34b2d11e524ae352b0656051（0.4.1-alpha）
独立分支：ui-redesign。不得自动合并 main 或改写原功能分支。

## 1. 目标与范围

保留 LifeOS 作为自动日程执行、专注监管、使用统计工具的定位。利用成熟开源 Compose 组件和设计资源提高视觉完成度、信息层级、交互一致性；保持现有 Kotlin/Jetpack Compose/Material 3 技术栈、lab/online 构建变体和功能行为。优先做可验证的 UI 重构，避免无必要的架构迁移。自动计划为主，手动专注为补充。

先阅读 AGENTS.md、原 Google 接入任务（tasks/PHASE05_BASELINE.md）、REQUIREMENTS、ARCHITECTURE、PRIVACY、ROADMAP、FOCUS_EXCHANGE_PROTOCOL、最新 handoff、tests/GOOGLE_VERIFICATION.md。核对本地和远端分支差异，保留已有未提交工作。用户明确禁止 computer-use，电脑网页由用户操作；遵守原授权范围，不擅自操作手机或真实 Google 数据。

## 2. 设计调研和直接复用

必须实际阅读可访问的设计资源、源代码与许可证，建立复用清单（项目、URL、固定 commit/tag、许可、适用组件、采纳或舍弃理由、变更及必要署名）。优先原生 Material3 组件及稳定依赖，禁止盲目升级或整套搬运其他应用。

- Material Design 3: https://m3.material.io/ — Color Scheme、Typography、Shapes、Cards、Navigation Bar、Top App Bar、Chips、Dialog、Bottom Sheet、Progress；评估 Material 3 Expressive 兼容性。
- Now in Android: https://github.com/android/nowinandroid — 主题、design system、导航、状态管理、Compose Preview、可复用组件。
- Compose Samples: https://github.com/android/compose-samples — JetLagged 统计/时间分布；Jetcaster、Reply 页面布局与导航；先核验示例的实际内容再选用。
- Vico: https://github.com/patrykandpatrick/vico — 评估 Compose 图表及版本兼容性；优先用于真实时间序列/柱状统计；简单计时环和排行条优先原生 Canvas/Progress。
- Figma Community (https://www.figma.com/community/)、Dribbble (https://dribbble.com/)、Mobbin (https://mobbin.com/)：搜索 Focus Timer、Digital Wellbeing、Screen Time、Daily Planner、Time Tracker、App Blocker、Productivity Dashboard。尊重素材授权；未授权资源只作设计参考。无法访问的资源如实记录，不绕过登录或付费限制。

可直接移植授权兼容的代码或组件，保留版权声明；不复制未获许可的商业图片、图标或设计资源。不引入付费订阅、试用或不必要网络服务。

## 3. 设计原型：先完成这一阶段

用相同的虚构演示数据制作三套有明显差异的 Compose 首页与专注页（每套同时覆盖空闲、自动专注中状态）：
A 自然简约：低饱和绿灰、米白、克制的卡片；参考主色 #315C4B、深色 #203B30、次色 #789487、背景 #F5F7F3、卡片 #FFFFFF、正文 #202722、次级文字 #707A73。
B 黑白极简：中性色、清晰网格、数字排版为中心，少装饰。
C Material 3 Expressive：原生 Android 的形状、层级、配色与适度动效，兼容性优先。

共享原型状态模型，使用 @Preview 或 debug-only 可运行入口；原型不得调用真实计划、监管、同步或统计服务，不得混入正式数据或 release。为每套给出浅色/深色渲染、正常字号/大字号检查，说明取舍。优先使用工程支持的 Compose Preview/本地模拟器/截图测试；不能渲染则明确限制，构建独立测试 APK 供用户手动安装。不得以代码冒充实际截图，不使用 computer-use；不采集真实使用记录或上传真实屏幕截图。公开仓库仅存模拟数据的预览。

**风格确认门槛：**完成三套原型、对照预览与设计说明后停止全量页面改造，待用户选择或修订方向；可并行进行不影响业务的公共组件基础工作。不得未经确认就把某一套风格推广至整个应用。

## 4. 工程设计

现有依赖基线：Material3 1.3.2、Compose UI/Foundation 1.7.8、activity-compose 1.10.1、Java 17、minSdk 26/targetSdk 35。实际开发前重新核对。先评估现有依赖，必要时锁定新增版本并验证两变体；禁止只为视觉效果升级整套 Gradle/Kotlin/Compose 工具链。

在 android/app/src/main/java/io/lifeos/focuslab/ 下按实际工程建立 ui/theme、ui/components、ui/navigation、ui/screens（home/focus/schedule/statistics/settings 等）；online 专属功能维持 flavor 隔离，必要时通过现有 IntegrationHooks 或明确的 UI state/action 接口传递。不得让 lab 直接依赖 online 实现。

统一 MainActivity、OnlineActivity、GoogleConnectActivity 及其他实际 UI 入口的主题；检查无障碍服务覆盖层等非 Compose UI 的视觉一致性，同时完整保留其生命周期、超时、放行、权限和安全行为。集中维护色彩、字体、形状、间距、图标和状态语义，支持浅/深色、系统状态栏/导航栏、字体缩放、触控面积（至少 48dp）、对比度和 TalkBack；计时数字使用等宽数字特性或适当字体。避免无意义渐变、重阴影、全页面卡片堆叠。组件包括 FocusTimer、TaskCard、ScheduleTimeline、StatCard、StatusIndicator、AppIconItem、SettingsItem、EmptyState 等。尽量使用明确的 UI 状态与回调，避免重组重复触发 I/O 或改变计时精度。

## 5. 页面与信息架构（确认风格后实施）

底部导航：首页、日程、统计、监管；专注中使用独立低干扰界面，并维持必要安全入口。连接、隐私、开发诊断移入二级设置；lab 不显示不可用 online 功能，必要入口仍可达。

- 首页：当前日期、任务、实际执行状态、剩余时间、下一任务、今日已有统计、同步状态、手动专注入口。准确区分未授权、未连接、同步失败、计划未发布/过期、未启用自动执行、自由时段、正常专注与异常放行。
- 专注页：大字号倒计时、可选环形进度、真实任务名称和起止、白名单应用图标入口、监管状态、可用的计划调整入口、必要通话与应急解除。维持普通退出10秒等待、计时持续、临时通行限制、故障放行；应急解除不被弹窗、滚动或视觉弱化妨碍。当前 API 无可靠字段时显示未知/不适用，不捏造计划名或进度。
- 日程页：按时间排列任务、当前时刻/任务高亮、专注/自由模式、过期/未知模式语义、真实版本、最近同步、主动同步和申请调整入口。保留当前缓存与计划执行机制。
- 统计页：根据 UsageCollector 的实际报告展示屏幕交互时长、解锁后交互、可靠解锁次数、应用排行、可用分类/小时分布及历史趋势。估计值、未知状态、覆盖区间、采集/上传状态明确标注；保留隐私开关、预览、删除、保留期、上传目的地。没有可靠数据的专注完成率/中断等指标不伪造；历史策略未适配的详细轨迹及 Catalog 同步保持原禁用状态。
- 监管页：实际自动监管状态、分类、白名单、搜索、单应用例外、权限状态和规则；保留保护系统应用及用户最终控制权。
- 二级设置：Google Sheets/私有同步、自动执行、隐私/统计、保留与清除、权限、系统测试与关于。保持已有授权确认、撤销、数据预览和 debug/release 边界。

## 6. 实施阶段

Stage 1（当前）：源码与功能入口审计、复用调研、三套原型、截图/可运行预览、取舍报告；提交后等待风格确认。
Stage 2（确认后）：统一主题、公共组件、导航、首页/专注页；测试白名单、手动/自动开始、正常结束、应急解除、返回与恢复前台。
Stage 3：日程、统计、监管和二级设置逐页迁移；不删除已有入口；提交旧/新功能映射。
Stage 4：深浅色/字体缩放/小屏/状态恢复优化；功能回归、构建、Lint、权限审计、签名核验；提供 lab 与 online 测试 APK、哈希和安装说明。

## 7. 约束与验收

- Google Sheets 协议、真实数据结构、认证凭证、自动执行/绝对时间/锁屏重算、后台同步、分类/白名单优先级、隐私采集与上传策略保持不变。无新增系统权限、遥测或网络调用。
- 不妨碍用户撤权、强停、卸载、必要通话/系统功能；保留所有应急、故障放行机制。不要将 UI 改造扩展成更强拦截机制。
- lab 无 INTERNET；online 仅必要网络权限；release 无 debug receiver。所有秘密、真实日程、真实应用清单、日志、账号、表 ID、签名不得进入公开仓库。
- 基线已有 25 项逻辑测试两变体通过，按实际工程运行现有测试并记录新结果；增加 UI state/导航/安全入口等必要测试。至少验证 lab/online 的 assemble、unit test、lint，按现有审计脚本审计最终 Manifest/APK；记录具体命令、结果、警告。
- 用模拟数据验证空闲、自动等待、专注中、自由时段、过期计划、未连接/同步失败、空统计及权限撤销等状态；区分模拟、单元测试、构建、真机结果。原 Phase05 尚待锁屏/断网/重启、后台延迟、实际拦截、真实日汇总等验收，不因 UI 重构而标记通过。
- 用户仅授权任务与 UI 重构；不发布真实计划、不修改私有 Google Sheets、不变更复盘自动化、不操作真实账户或手机，实机测试须遵循现有授权与用户确认。
- 保留历史提交，不强推，不自动合并 main 或 phase05；功能开发分支有后续更新时先报告差异，再协调合入。

## 8. 交付记录

维护 docs/UI_REDESIGN.md：现状与问题、原型和预览、资源与许可证、设计规范、组件结构、导航图、旧/新功能映射、依赖与测试结果、已知问题。预览素材可置于 docs/ui-previews/，只用模拟数据。测试报告置于 tests/UI_REDESIGN_VERIFICATION.md；逐阶段更新 tasks/ACTIVE.md 与 handoff.md，提交独立 commit。

本任务文档不等同于已实现或已验收；首次执行只完成 Stage 1 及安全的公共组件准备。
