# v0.3.0-alpha验证记录

日期：2026-11-26。范围：本地分类、类别白名单、计时主页及退出摩擦。真实应用清单、包名、选择和原始日志不进入仓库。

## 本地验证
JDK21、Gradle8.13、compile/target35。testDebugUnitTest、lintDebug、assembleDebug通过，13项单元测试0失败；Lint0错误、42警告（KTX/SharedPreferences等建议及缺应用图标，未隐藏警告）。
源码/合并Manifest/APK隐私权限审计通过，无INTERNET，内容/截图/手势能力关闭；APK签名验证通过。release合并Manifest不含DebugReceiver。git diff --check通过。

复核时修正普通退出确认按钮的初次重组时间缓存：计时计算依赖退出申请时刻，点击处理还校验单调时钟，避免刚打开确认框时出现瞬时可确认窗口。

## USB真机
HONOR MagicOS11，Android17/API37。v0.3.0-alpha覆盖安装成功，旧白名单存在、无障碍仍连接。
DUMP调用权限保护的debug-only自检返回CLASSIFICATION_PERSISTENCE_AND_PRECEDENCE_PASSED：人工分类重扫/重新创建规则实例后保留、类别允许生效、单应用关闭覆盖类别允许、单应用允许覆盖类别关闭、取消例外跟随类别、恢复自动分类。finally恢复原有五组私有规则字段，未保存真实包名或应用清单。
8秒白名单会话观察到ACTIVE，随后IDLE、remaining_ms=0、前台辅助关闭，原白名单计数不变。只打开LifeOS，不启动系统设置或其他应用。
旧v0.2自动短测受用户操作影响，只能视为观察，不代表稳定性通过。10秒退出的真实触控交互尚待用户观察，源码校验不能冒充真机触控测试。

## 待验收
分类/退出交互的人工观察；普通全屏20次成功率、长待机、长锁屏、重启、分屏、小窗、分身、所有T01–T12项目未全部验收。不宣称完全锁定系统，不宣称已接通在线同步。
