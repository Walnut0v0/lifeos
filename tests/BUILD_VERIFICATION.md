# v0.1.0-poc 构建与静态验证

日期：2026-11-26。基线：phase01-poc / 5bb1df7（保留扩展要求）。这是电脑端验证，不能代替安装和 MagicOS 实测。

|检查|结果|证据与边界|
|---|---|---|
|Kotlin / APK构建|PASS|Gradle testDebugUnitTest lintDebug assembleDebug；生成debug测试签名APK|
|纯逻辑单元测试|PASS|4个测试方法，0失败/0错误：时段起止、跨日绝对时间、到期、手动单调时钟/改时钟/重启、无效/超时配置、安全规则优先、无会话放行|
|Android Lint|PASS（存在警告）|0错误；保留中文实验界面本地化、KTX风格、SharedPreferences同步写入等警告；同步保存会话是为避免开始限制前状态未落盘|
|源码隐私审计|PASS|tools/audit.py：权限白名单、窗口事件范围、无内容/手势/截图能力、禁止接口扫描|
|合并Manifest及最终APK权限|PASS|无INTERNET；6项平台权限及AndroidX生成的签名级内部接收权限|
|APK内无障碍配置|PASS|aapt dump xmltree：typeWindowStateChanged，三项能力均false|
|APK签名|PASS|apksigner verify --verbose：APK Signature Scheme v2通过，1个签名者；测试签名非正式发布密钥|
|Gradle分发完整性|PASS|下载的8.13分发与官方SHA256一致，wrapper属性锁定该SHA256|
|手机安装/模拟器运行|NOT RUN|ADB设备列表为空，未启动模拟器|
|MagicOS T01–T12|NOT RUN|等待用户按指南和完整矩阵实测；无障碍、闹钟和后台行为均未宣称通过|
|GitHub Actions|已配置|分支推送/PR触发构建、测试、Lint、权限审计和APK/哈希artifact；以Actions实际运行结果为准|

## 交付包

文件名：`lifeos-focus-lab-v0.1.0.apk`

SHA256：`5c6dc6cef26beba7ba49a146e54701da8be13ea496390942784d4feec49ff453`

这是本次本地包的哈希，CI重新构建的包及签名可能不同，应使用随同该包提供的SHA256SUMS校验。

## 权限及必要性

- `RECEIVE_BOOT_COMPLETED`：解锁后重算已保存的有效计划，不在锁屏前执行管控。
- `SCHEDULE_EXACT_ALARM`：用户独立授权的自动起止实验，无权限时非精确降级。
- `POST_NOTIFICATIONS`：测试通知和额外停止入口，拒绝不妨碍Lab停止按钮。
- `PACKAGE_USAGE_STATS`：用户在系统Usage Access页面单独授权后，主动读取短时元数据；不自动采样、不导出轨迹。其AppOps授权方式见[官方说明](https://developer.android.com/reference/android/app/usage/UsageStatsManager)。Manifest的ProtectedPermissions Lint仅对此项定点说明，未关闭其他权限检查。
- `SYSTEM_ALERT_WINDOW`：独立普通悬浮窗对比；默认未授权，基础拦截不依赖该权限。
- `FOREGROUND_SERVICE`：用户在可见界面主动启动60秒shortService实验，不尝试永久保活。
- `io.lifeos.focuslab.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`：AndroidX生成的内部签名级广播权限。

不申请全应用查询、通知读取、通讯录/通话记录/定位、存储读取、设备管理、读屏、截图、麦克风或相机权限。

## 必须保留的局限

首次阶段的覆盖层仅10秒，FGS/屏幕固定各60秒；到点只尝试处理最近5秒内已知前台包名。普通悬浮窗只验证Lab前台创建/移除，不遮挡其他应用。无法仅凭包名可靠区分设置子页或Home/Overview时全部放行。厂商选项必须手工确认。模拟连续JSON仅验证边界与往返，后续产品才实现多段真实调度。不得把这些实验入口当成厂商兼容性已验证结论。

本阶段仍等待真机报告，未合并main。
