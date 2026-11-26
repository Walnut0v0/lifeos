# 下一阶段：日程在线联动与全天使用记录（优先级重排）

用户确认自动分类已有实现，允许正常 Bug 修复及小版本迭代；不要求先把拦截打磨至完美才做同步。仓库已推送代码与本地进度可能不同，开始时检查实际最新分支、保留已有分类与功能。此任务优先于 NEXT_V015.md 中的顺序；保留其中安全约束与回归用例。旧 Lab 保持离线测试基线，新增正式开发变体或独立模块明确区分权限；不要将测试 APK 的无 INTERNET 约束误用为正式在线版禁令。正式在线版仅添加必要联网权限并重新审计。

## 产品主目标
A. ChatGPT 制定/调整计划并发布；手机在线自动获取、缓存、执行。
B. 不需要点击开始：到点按绝对时间进入专注，锁屏不唤醒屏幕；解锁/应用切换/服务重连时重新计算当前有效时段，错过开始回调也正确进入。到期绝不继续错误限制。
C. 全天手机使用数据（包括非专注时段）自动汇总并回传，供 ChatGPT 晚间复盘和次日规划。
D. 计划变更入口打开 ChatGPT，允许规划调整场景临时使用必要的 ChatGPT/认证界面，不提供通用娱乐解锁；保留应急解除及系统控制权。先不接 OpenAI API，不读取 ChatGPT 会话。

## Stage A：最小自动执行链（最高优先级）
1. 定义 ScheduleSnapshot v1：schema_version、revision（单调递增）、generated_at、timezone、valid_until、tasks[]。每条含 task_id、title（可选）、start_at、end_at（带偏移 ISO 8601）、focus_mode（稳定 mode_id，free 表示放行）。半开区间 [start,end)，无任务即 free，空计划可明确发布取消。禁止重叠、负时长、未知 mode_id（提示且对应时段放行）、损坏版本；验证后原子替换完整快照。计划 horizon 至少今日和次日，跨日、跨时区及计划取消有测试；失效后放行。不采用“收到消息后开始 N 分钟”作为计划计时。
2. 先用模拟 JSON 测绝对时段引擎：任何 now → 唯一有效任务、剩余=end-now、下一边界。使用 AlarmManager 单次注册下一边界；闹钟迟到、解锁、服务连接、启动、计划同步、时间/时区变化时重算并重新注册；对接既有管控状态机。锁屏期间不显示拦截层、不强拉 Activity；逻辑上已进入专注。解锁时优先重新判定（可用时 USER_PRESENT/屏幕/窗口事件与服务重连）；首次应用访问前或最早可观测事件执行策略。开机首次解锁后恢复；强停/撤权不可绕过，明确异常。Doze 延迟不影响绝对时间正确性，禁止补跑已经结束的任务。
3. 数据层优先 Google Sheets：使用独立私有 Focus Exchange 表（现有 LifeOS Planning State 仍为规划主数据源，不直接开放全部个人资料给手机）。通过 ChatGPT 已连接工具发布快照、读取统计。手机单独 Google OAuth 授权，验证荣耀的网络、Google 服务/浏览器登录、scope、token refresh、用户选择指定文件的可行性；尽量 drive.file 或其他最小可用授权，不能假定已有文件自动可访问。若当前环境不能可靠连接，记录失败并选择受控 HTTPS 数据源/专用日历桥接，保留相同 SyncAdapter；禁止将私有 Sheet 发布到 Web、硬编码 token 或用公开 GitHub 当数据传输层。
4. 第一轮同步实验：用户登录、选择/配置指定私有表、读取一份完整快照，显示版本和最后同步时间；自动后台更新采用 WorkManager（周期最短约15分钟且非保证），打开 APP、解锁后的可用事件、联网恢复、用户返回 ChatGPT 后进行节流的即时同步；手动刷新仅作故障诊断。必要时后续评估私有推送服务；不得承诺后台实时。同步时间与专注生效时间分离：已缓存的未来计划到点本地执行，无须网络。检测冲突与版本倒退，失败保留尚未过期的旧计划，显示 stale 状态。
5. 真实闭环：ChatGPT 发布模拟/短时计划 → 手机无需手工开始自动执行 → ChatGPT 发布修订（改开始/结束、模式、取消、追加）→ 手机接受新版并重算 → 锁屏/断网/解锁验证。当前 ChatGPT 连接工具可访问 Sheets，仍需实际创建交换表及验证写入权限；不要在本轮擅自修改真实规划自动化或日程。用户确认真实启用后再接23:30自动化，计划发布和复盘可读数据均要验证。

## 数据交换表（拟定，不含任何真实 ID）
- PlanSnapshot：一行保存当前完整 JSON、revision、published_at，整体发布；可选 PlanHistory 做版本审计。优先单个 JSON 单元避免半更新；控制大小，必要时换分块加提交标记，客户端验证完整性。
- UsageDaily：date、timezone、device_alias（随机本地代号，可选）、coverage_start/end、screen_interactive_ms、unlocked_interactive_ms（可测时）、unlock_count、usage_by_app、focus_summary、missing_intervals、updated_at；使用稳定 date+device_alias 幂等更新，时间单位 ms。
- UsageIntervals（可选授权详细记录）：event_id、date、app_package、app_label（可选）、category、start_at、end_at、duration_ms、quality；分批传输，去重与断点，避免无界增长。应用清单及分类另设 Catalog（可选同步），不包含消息内容。
- ChangeRequests（可选）：request_id、timestamp、task_id、request_type、requested_change、status；计划发布仍由 ChatGPT/用户确认。先支持应用打开 ChatGPT 和复制最小请求文本，若有授权写入接口才同步请求。
- 私有表地址和凭证运行时配置，不写仓库。所有数据只经用户明确授权、可选择导出、清除；不得发布真实数据。

## Stage B：全天使用监测（与 A 同等产品核心，可独立并行）
- 使用 UsageStatsManager.queryEvents 的 ACTIVITY_RESUMED/PAUSED、SCREEN_INTERACTIVE/NON_INTERACTIVE、KEYGUARD_SHOWN/HIDDEN（实际可用性验证）等必要元数据，授权后全天回补，而非持续录屏/读屏。系统事件保留时间有限，按可用窗口周期增量拉取，重新启动/解锁/联网后回补；必要时无障碍只作为管控辅助，不作为唯一统计源。不得读取 event text/source/节点/页面或通知。
- 指标定义：屏幕交互时长（屏幕 interactive 区间）、解锁交互时长（交互且无锁屏，若可靠）、解锁次数（按可靠状态边沿）、各应用前台时长及起止区间、分类时长、各小时使用分布、专注计划/实际有效管控区间、专注中非允许应用使用、拦截和退出次数。屏幕交互不等于实际注意力，应用前台不等于正在主动使用；分屏重叠不重复累加为总手机时间。区分系统/桌面、未知、数据缺失，不凭空补足。跨午夜切分，处理事件乱序、重复、未闭合区间、权限失效、重启、时区变化。
- 本地保留必要元数据/聚合；可选同步应用清单、分类、详细历史（用户已允许这种数据类型，但需明确目的地和开关）；默认先同步日汇总，详细记录可开启，支持预览/删除/保留期；消息、屏幕、输入、通知正文永不采集。私有授权同步 HTTPS，安全凭证存储，禁止遥测。
- 当天统计可见，按小时、应用、类别、计划时段分析。晚间复盘读取当天截至当时的统计，明确覆盖范围及缺失；第二天补齐昨日终值。不得将屏幕熄灭或无手机活动当学习完成。
- 验证人工使用两个应用、锁屏/解锁、跨日、后台清理、撤权恢复和去重；与荣耀健康使用手机统计对照，记录差异口径；本地/云端总量一致。测试数据模拟，真实详细日志不进 repo。

## Stage C：计划变更入口
- 首页按钮“申请调整计划”显示当前任务与建议变更类型（推迟/延长/提前完成/临时事项），用户可复制生成的简短请求，尝试 Intent 打开已安装 ChatGPT；用户配置本人私有对话 URL 可尝试 ACTION_VIEW，未安装/链接不支持则浏览器或启动 App。不得使用公开分享链接暴露私密会话，不承诺自动填充/发送或精确定位原聊天；不要读取聊天或自动点击。
- ChatGPT 在聊天中确认调整并通过授权工具发布新版；客户端返回时立即触发同步，显示 pending/已更新及版本。在专注内允许明确的规划入口使用 ChatGPT；必要系统功能及应急退出永远可用。暂不引入 OpenAI API，不使用 APK 内 API key。
- 如果当前授权连接无法自动发布，先验证私有表人工写入 + 手机自动拉取，随后补齐 ChatGPT 写入链路；不能以手动 JSON 导入冒充在线联动。

## 验收门槛（允许正常小版本修复）
A1：两段真实在线计划下发，无点击开始；锁屏跨开始/结束、解锁时按绝对时间、错过整个时段放行；后台/重启后有效快照恢复；断网按缓存执行。
A2：ChatGPT/测试写入者修改计划，手机自动检测新 revision 并原子应用；App 前台/返回时同步、后台实际延迟分别量化；无效快照、旧版本、授权撤销、断网、过期均可观测。
B1：全天使用数据不依赖专注开始；受控测试与人工计时/系统统计对照；记录覆盖范围与误差，去重、跨日、分屏处理。
B2：私有云端日统计回传可由 ChatGPT 读取用于复盘；详细使用记录可选同步；无私人内容、无真实数据入仓。
C1：App 能打开 ChatGPT，计划申请可复制；更新计划后返回触发同步，当前时段正确重算。
所有阶段保留原系统能力回归、安全检查和故障放行；修复 Bug 可随时发布小版本，记录 commit、APK、测试结果。优先端到端可用，不要求每个边缘拦截场景在在线功能开发前完全通过。尚未确认的 Google 认证/网络、Android 后台时效、ChatGPT 深链、设备状态事件等如实实测。

参考 Android 官方：
https://developer.android.com/develop/background-work/services/alarms
https://developer.android.com/reference/android/app/usage/UsageStatsManager
https://developer.android.com/reference/android/app/usage/UsageEvents.Event
