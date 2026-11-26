# LifeOS Focus Exchange — 接口规范 v1.0

状态：Google 原生工作簿已创建并读回验证，名称 LifeOS - Focus Exchange，位于与 Planning State 同一私有文件夹。仅初始化表头、Protocol、未发布日程和空模式。真实表 ID 通过用户私下配置/文件选择传给客户端，不进入仓库；本规范无真实用户数据。客户端未授权、未联网验证；晚间自动化尚未修改。

## 1. 通用约定
- 单用户、单主力手机、单串行计划发布者。工作簿文件级 OAuth 最小授权（优先验证 drive.file + 用户选择现有文件），不假设仅输入 ID 就会自动获授权。不允许公开分享、服务账号私钥入 APK、长期令牌入仓。Sheet 写入归属属于应用协议，非真正的 Google 权限隔离；若后续需要严格读写隔离，拆成独立文件或受控服务。认证及用户选择由 Android 开发端完成；ChatGPT 已用连接工具成功创建并写入、读回此表。
- 固定 sheet 名与第 1 行表头，数据从第 2 行开始。不要移动或改名。日期 YYYY-MM-DD，报告时区固定 IANA Asia/Singapore（与现有 Planning State 一致）；时间戳 RFC3339，明确偏移或 Z，比较绝对时刻；单位整数毫秒，布尔值 true/false（原生布尔）；数值 RAW 原生数值，字符串 RAW 原生文本，JSON 紧凑字符串，空值使用空单元或 JSON null，不把未知写成0。禁止 USER_ENTERED 解释公式。单 JSON 单元最多40000字符，不截断；超出需升级协议。
- JSON schema_version=1；未知可选字段可忽略，不支持的 schema 拒绝。ID 为稳定非空字符串，device_alias 为安装后随机生成的匿名代号，非硬件 ID。当前仅一个手机；重装后提示选择/关联，不自动把不同设备的统计相加。
- 写操作串行、失败重试带指数退避及抖动，429/5xx 按限流，认证错误停止重试并提示重新授权。按主键查找/更新，禁止盲目 append；批量读写，写后读回关键字段。Sheet 不具备数据库唯一约束或事务式 CAS，严格单写者；不要并发发布计划。手机写入不得触及 ChatGPT 所属字段。行数扩容须不改列结构。
 
## 2. 工作表与权限归属
| Tab | 写入方 | 主键/读取范围 |
|---|---|---|
| Protocol | 维护者 | A:C，仅文档 |
| Schedule | ChatGPT/授权规划发布者 | A2 完整快照 |
| App Catalog | 手机 | device_alias+app_package |
| Focus Modes | 手机 | A2 完整模式快照 |
| Usage Daily | 手机 | device_alias+date |
| Usage Timeline | 手机 | event_id |
| Sync State | 手机 | device_alias |
| Change Requests | 手机 A:G；ChatGPT H:K | request_id |
ChatGPT 可以阅读其被授权的数据、提出分类/白名单调整建议；用户在手机确认后由手机更新。安全保护规则永远由本地执行，不允许日程或远程数据关闭安全白名单、应急解除或系统控制。

## 3. Schedule（A1 snapshot_json；A2 唯一执行源）
初始：
```json
{"schema_version":1,"revision":0,"state":"unpublished","generated_at":null,"timezone":"Asia/Singapore","valid_until":null,"tasks":[]}
```
rev0 仅初始化，绝不执行。正式发布示例（仅模拟，不是用户实际计划）：
```json
{"schema_version":1,"revision":1,"state":"published","generated_at":"2026-12-01T00:00:00+08:00","timezone":"Asia/Singapore","valid_until":"2026-12-03T00:00:00+08:00","tasks":[{"task_id":"demo-1","title":"示例学习","start_at":"2026-12-01T08:00:00+08:00","end_at":"2026-12-01T09:30:00+08:00","focus_mode":"study"},{"task_id":"demo-2","title":"休息","start_at":"2026-12-01T09:30:00+08:00","end_at":"2026-12-01T09:45:00+08:00","focus_mode":"free"}]}
```
- 必填 revision 正整数、安全整数，严格递增；state=published；generated_at、valid_until 有效且 generated_at<valid_until；timezone 固定上述 IANA；tasks 数组，task_id 快照内唯一，title 可省略或为非敏感文本，focus_mode 必填。起止均可解析，start<end<=valid_until，无重叠。任务按 start 排序；同一任务改期尽量保留 ID。更新完整快照包括今日剩余和次日已定计划，保留需要用于复盘的已结束条目；绝不回放过去任务。没有任务的间隙为 free。已确认取消全部计划时发布更高 revision、空 tasks，不用清空单元格代替取消。
- 发布者读取当前 A2、Focus Modes A2 和有效日程，构造下一 revision，校验 JSON（包括模式是否存在）；一次 RAW 写入 A2，读回比较完整对象。单一发布者串行化；相同 revision 不同内容作为冲突拒绝，不猜测优先级。计划修改/取消均走同一路径。
- 客户端读取 A2；验证后原子保存完整快照及 applied_revision，拒绝旧版本、同版本不同内容、非法 JSON、重叠或无效字段。未知模式时该任务放行并报错，不影响其余合法任务；未初始化、没有有效缓存、过期放行；网络故障仍用未过期缓存，明确 stale；严重故障及撤权明确提示。切勿因网络错误延长已结束限制。
- 每次 now 判定 start<=now<end，剩余 end-now；下一边界由起止时间决定。闹钟、解锁、重启首次解锁、服务重连、时钟/时区变化、更新计划都重算；锁屏不点亮屏幕，不强拉界面。Doze/后台延迟不改变绝对时间。计划有效期建议覆盖今天及明天。手动刷新用于诊断；正常后台 WorkManager 与前台/解锁节流拉取；实测同步延迟，不承诺实时。

## 4. Focus Modes（A1 modes_json；A2 完整快照）
初始：
```json
{"schema_version":1,"revision":0,"device_alias":null,"updated_at":null,"modes":[]}
```
非空示例：
```json
{"schema_version":1,"revision":1,"device_alias":"demo-device","updated_at":"2026-12-01T00:00:00Z","modes":[{"mode_id":"study","name":"学习","allowed_categories":["education"],"allow_packages":["example.calc"],"deny_packages":[]}]}
```
mode_id 稳定且唯一、name 为显示文字；三个数组均为字符串数组，未知字段可选。free 是隐式保留模式（无普通应用限制），不得被重新定义。优先级本地安全白名单 > 单应用规则（冲突禁止配置并提示） > 类别规则；自动分类人工修正优先。手机用户改配置后发布新 revision，ChatGPT 读取已存在 mode_id，不发明不存在模式；同步失败不能覆盖本地配置。客户端实际分类数据结构可适配映射，不为了同步重写已有分类。App Catalog.category_id 与 allowed_categories 使用一致稳定 ID。

## 5. App Catalog（A:G）
`device_alias, app_package, app_label, category_id, category_source, installed, updated_at`
键 device_alias+app_package；category_source=manual/system/rule/unknown（已有算法可映射到 rule）；installed 布尔。卸载标 false；重新扫描不覆盖人工分类。仅明确开启 catalog 同步才发送；不传图标二进制或应用内容。未知分类可用 unknown。ChatGPT 建议由用户确认后再调整。

## 6. Usage Daily（A:P）
`device_alias, date, timezone, coverage_start, coverage_end, screen_interactive_ms, unlocked_interactive_ms, unlock_count, usage_by_app_json, usage_by_category_json, hourly_usage_json, focus_summary_json, missing_intervals_json, quality, is_final, updated_at`
- 单设备+日键幂等 upsert；23:30 复盘读取当时覆盖数据，次日回补后 is_final=true（最终仍可能有缺失）；禁止把 is_final 解释为完整。coverage_start/end 为实际可观测范围，缺失用数组 [{start_at,end_at,reason}]，未知指标留空。quality=complete/partial/unavailable。
- usage_by_app_json 数组 [{app_package,app_label,category_id,foreground_ms}]；usage_by_category_json 数组 [{category_id,foreground_ms}]；hourly_usage_json 长度24数组（0–23时的屏幕交互毫秒或 null）；focus_summary_json 对象 {planned_ms,enforced_ms,restricted_foreground_ms,block_attempts,early_exits,bypass_ms}，未知项 null。可增加可选字段。
- 按 UsageStats 系统事件实际能力估计屏幕 interactive、解锁 interactive、解锁次数、前台应用区间。分屏/重叠应用各自前台时长可能重叠；总屏幕时长按区间并集，不相加应用时长；分类重叠同理提示。跨报告日切分；缺失、重复、乱序、重启和未闭合事件有质量标记。无活动不代表学习完成。使用统计全天独立于专注状态。

## 7. Usage Timeline（A:O，可选详细轨迹）
`event_id, device_alias, date, timezone, app_package, app_label, category_id, start_at, end_at, duration_ms, task_id, focus_mode, policy_result, quality, updated_at`
按 event_id 幂等 upsert；本地持久生成稳定 ID（重试不变，回补修正同一事件用同 ID）。跨日、计划边界拆分，start<end、duration_ms=end-start；未闭合区间仅本地暂存。task_id/focus_mode 无任务可空/free；policy_result=allowed/restricted/unknown（当时实际策略，非事后推测）；quality=complete/partial/unavailable。只记录元数据，无屏幕内容；未授权不上传。按批次查询已有键去重，先实现单客户端串行队列。详细数据和日汇总开关独立。

## 8. Sync State（A:O）
`device_alias, client_version, last_poll_at, last_success_at, applied_revision, active_task_id, enforcement_state, usage_permission, usage_sync_at, daily_sync_enabled, timeline_sync_enabled, catalog_sync_enabled, status, error_code, updated_at`
设备键 upsert；enforcement_state=active/free/degraded/unknown，usage_permission=granted/denied/unknown，status=ok/stale/auth_required/error/uninitialized；错误代码仅固定短码（如 INVALID_SNAPSHOT、UNKNOWN_MODE、EXPIRED、AUTH_REQUIRED、NETWORK），不包含原始响应或令牌。last_success_at 是已接受/核对快照时间，usage_sync_at 为成功上传时间。状态更新节流，离线读本地；不因 Sync State 写失败而撤销计划或阻止应急操作。

## 9. Change Requests（A:K，可选）
`request_id, device_alias, created_at, task_id, request_type, requested_change, base_revision, status, resolved_at, published_revision, response_note`
手机 A:G 按稳定 request_id 写入，H 空视为 pending；request_type=extend/shorten/postpone/complete/urgent/other，requested_change 为用户主动提交的最少文字（可为空），严禁自动收集聊天。ChatGPT H:K 写 accepted/rejected/cancelled；接受后发布并核对新版计划才填 published_revision，其他状态留空。请求不自动修改当前计划。手机可先直接打开 ChatGPT 并复制简短请求；从聊天返回触发同步。不要求 API/聊天读取/自动发送。

## 10. 保留与隐私
日汇总建议365日、详细轨迹30日、变更请求90日，均为建议默认值；用户可调整；初版清理须确认，删除前核对范围。不自动删未授权数据。开关关闭停止对应后续上传，不等同于删除既有云端记录。允许用户主动清除/导出。禁止消息正文、通知正文、屏幕文字、输入、截图、录屏、通讯录、定位、硬件标识、认证凭证；真实使用记录、表 ID/令牌不得进入 GitHub。OAuth token 用安全存储，HTTPS；不引入遥测 SDK。旧 Lab 仍保持完全离线。

## 11. 联调步骤
1. 用户私下输入/选择真实交换表；验证 OAuth、文件授权、网络（MagicOS）、读取 Protocol 与 Schedule/Focus Modes 初始值。不使用 public URL 访问绕过认证。
2. 手机按授权上传已有模式（不含私人内容），ChatGPT 读取 mode_id；使用模拟数据先发布测试快照，两段短时任务，锁屏跨起止、解锁、断网、重启、修改计划并核对 applied_revision。
3. 手机写日汇总测试记录，读取确认；测试可选明细、重复重试去重、权限失效和覆盖缺失。数据需标明测试，清理由用户确认。实际运行前启用真实数据授权。
4. 再接入 23:30 复盘：读取当天汇总及经授权的明细，结合 Planning State 制定次日计划，发布单一完整快照并确认；不根据手机静默判断任务完成。自动化配置须单独验证，当前尚未修改。

本规范是 NEXT_ONLINE_PLAN.md 中拟议数据结构的最终 v1.0 落地版本（Schedule A2 对应旧 PlanSnapshot；不新增 PlanHistory）。如有不一致以本规范为准。任何破坏性字段或结构变更先升级 schema 并同步 ChatGPT、Android、表格。
