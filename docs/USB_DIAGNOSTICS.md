# 通过USB读取测试诊断

用户已授权连接个人测试手机进行LifeOS诊断。仅通过ADB/run-as读取本应用debug数据，在内存中筛选固定字段；不读取屏幕/节点、其他应用文件、全局logcat或聊天内容，不保存原始XML，不在仓库放真实诊断文件。不新增网络权限或开放远程接口。

```powershell
py -3 tools/read_device_diagnostics.py --adb <ADB完整路径>
py -3 tools/read_device_diagnostics.py --adb <ADB完整路径> --watch 60
```

输出会话起止、模式、测试目标数量、固定覆盖层说明及脱敏事件；不输出目标包名、设备标识或未知字段。用户只需复现B模式，开发者读取输出即可。无需反复手动导出或转述。

v0.1.2新增测试中窗口角色（LAB/TARGET/HOME/PROTECTED/OTHER/UNKNOWN）、全屏标志、当时是否有覆盖层、事件到处理的时间差，不记录包名、页面类名、原始窗口ID或内容。它用于区分过渡事件与实际退出，不能凭单个HOME事件判断用户已经回桌面。安全窗口继续放行。

同时修复应用列表页面离开时的协程取消被误判为SCAN_FAULT并停止会话的问题。真正扫描异常继续停止测试。此次读取已确认目标环境为Android17/API37、MagicOS11；初始日志可见覆盖层自身事件已正确忽略，但随后提前撤除，完整根因待新日志确认。

## v0.1.3 短时辅助识别与ADB复测

已从真实事件确认：部分HOME非全屏事件并不代表目标Activity已经退出。新增默认关闭的“本次允许使用已授权的使用情况访问”选项；只用于手动测试，最长60秒。仅当最近5秒UsageEvents证据表明选中目标仍RESUMED且未PAUSED时，才忽略HOME非全屏事件；HOME全屏、其他系统/保护窗口、暂停、缺权或不确定状态继续放行。只比较包名/事件类型，原始记录在内存使用后丢弃，不保存轨迹。

debug变体额外提供DebugReceiver，要求调用方持有平台DUMP权限，仅用于已授权ADB短测。release合并Manifest已验证不含该receiver；APK自身没有申请DUMP权限。测试只使用用户原来选中的非关键目标，最长60秒，可停止，不授予系统权限。

```powershell
adb shell am broadcast --user current --receiver-foreground -a io.lifeos.focuslab.DEBUG -n io.lifeos.focuslab/.DebugReceiver --es operation start --ei seconds 30 --es mode OVERLAY --ez usage_assist true
adb shell am broadcast --user current --receiver-foreground -a io.lifeos.focuslab.DEBUG -n io.lifeos.focuslab/.DebugReceiver --es operation stop
```

荣耀实机需要上述前台广播和明确action，普通无action广播未返回有效结果。snapshot操作可返回脱敏报告，但一般使用read_device_diagnostics.py即可。不是互联网远程控制接口，关闭USB调试即可撤销电脑调试访问。
