# 系统架构与技术方案

LifeOS 规划端 → 版本化计划接口 → 本地计划存储 → 调度与状态机 → 策略引擎 → Android 管控适配器 → 本地诊断/执行记录 → 授权同步/复盘。

## 技术选型
Kotlin、Jetpack Compose、Gradle Kotlin DSL、Android SDK，Android Studio/ADB 调试，VS Code/Codex 开发，GitHub Actions 构建。SDK 版本根据可用稳定工具链和实机 API 选择并锁定。第一阶段使用最少依赖和 DataStore 或 SharedPreferences 保存必要状态；后续 Room 保存计划和记录，WorkManager 用于非精确定时同步，网络模块独立。
 
## 模块职责
- device/permissions：设备、权限、连接状态、自启动/后台设置操作说明。
- apps：PackageManager 启动入口查询、应用元数据与后续分类库。
- focus：纯 Kotlin 状态机（Idle、Scheduled、Active、Bypass、Fault）；手动会话使用 elapsedRealtime 单调时钟；计划使用绝对时间及明确时区。重启后单调时钟不可沿用，手动会话只在可安全确认原截止时间时恢复，否则停止。过期和异常配置放行。
- scheduler：AlarmManager 精确闹钟（授权时），开始/结束均注册，时间改变、开机、解锁、重连重新计算。Doze 配额和厂商限制可能造成延迟；每次事件检查时间，禁止延迟回调造成超期管控。WorkManager 不承担精确边界。
- policy：安全白名单、单应用覆盖、分类策略；Phase 01 仅明确选择的非关键测试目标黑名单。
- enforcement：AccessibilityService，仅必要窗口事件包名；GLOBAL_ACTION_HOME 与无障碍覆盖层独立实验，处理自身窗口、重复事件、防循环。到点时尝试利用当前已知前台包名，信息不足不得误拦截；必要时测试 UsageStats 补充方案并说明独立授权与局限。
- diagnostics：本地结构化事件、时间误差、服务连接状态、测试结果；默认不持久化完整应用打开历史。可选详细记录仅在正式功能获得同意后开启。
- sync（后期）：可替换数据源接口；OAuth/HTTPS；校验 schema、版本、时间和冲突，离线缓存、失败重试、导出预览。不得将个人数据写入公共仓库。

## 接口示例（模拟数据）
```json
{"schema_version":1,"schedule_version":12,"timezone":"Asia/Shanghai","tasks":[{"task_id":"T001","title":"示例学习任务","start_at":"2026-12-01T10:00:00+08:00","end_at":"2026-12-01T11:30:00+08:00","focus_mode":"study"}]}
```
实际字段、同步源和冲突策略在 Phase 05 确认。任务标题也可能含个人信息，按数据最小化原则处理。

## 工程目录
android/（app、Gradle wrapper、构建配置）；docs/；tests/；tasks/ACTIVE.md；README.md；AGENTS.md；handoff.md。依赖版本锁定，测试 APK 使用独立测试签名；正式密钥不进仓库。
 
## 参考文档
- https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- https://developer.android.com/training/package-visibility
- https://developer.android.com/develop/background-work/services/alarms/schedule
- https://developer.android.com/reference/android/os/SystemClock
- https://developer.android.com/topic/security/data

## Phase 01 能力探索
以 tests/PHASE01_CAPABILITY_MATRIX.md 为完整验证矩阵。权限诊断区分公开 API 检测与厂商手动确认；UsageStats 可选补充前台事件与使用时长，前台服务、悬浮窗、屏幕固定独立验证，依据结果选择最少必要权限。荣耀卡片锁定和后台管理通过手动系统配置验证。高级受管设备机制仅调查，另行授权专用设备实验；普通应用不承诺禁止用户强停、撤权或卸载。

## 在线同步方向（待后续实现）
用户在ChatGPT调整日程 → 授权工具/自定义GPT Action → 私有版本化计划数据源 → 手机自动同步并校验 → 本地执行。现有私有Sheets可作为候选数据源，部署和连接方式后续验证。当前普通聊天能否写入该目的地必须实测，不能假设任意会话自动可读。同步服务采用认证、最小数据范围、可撤销授权和最近同步状态；手机号、消息内容和屏幕信息不参与此流程。当前API与Action文档依据：[开发quickstart](https://developers.openai.com/api/docs/quickstart)、[GPT Actions](https://developers.openai.com/api/docs/actions/introduction)。当前仅记录技术方向，未部署服务或创建凭证。
