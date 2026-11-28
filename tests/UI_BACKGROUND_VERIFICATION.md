# v0.4.2 A自然简约与后台判断验证

用户2026-11-28在独立原型后明确选择A，正式本轮在ui-redesign整合；基线phase05-google-sheets不自动合并main。独立候选c64923c保留，Stage1原型记录见UI_REDESIGN_VERIFICATION.md。

## 构建与单元测试

PASS：testLabDebugUnitTest、testOnlineDebugUnitTest各32项、assembleLabDebug/assembleOnlineDebug、lintLabDebug/lintOnlineDebug；最终Lint 0错误，Lab47/online90警告。新增测试覆盖跨日显示/日筛选、固定应用优先与中文顺序、连续60秒前台证据、暂停/锁屏式间断/时间回退清除。它们不等同于MagicOS拦截率。

PASS：tools/audit.py的源码、合并Manifest和最终APK审计；无新增系统权限，Lab无INTERNET，online仍原必要网络权限。两份release主Manifest经tools/check_release_manifest.py确认无debug receiver；独立原型无release变体且未打包进正式应用。两份APK apksigner v2验证通过。

PASS：git diff --check。独立worktree候选构建/30项测试两变体/Lint另已通过，最终整合后重新全套检查。原型2项模型测试和零Lint警告保留。

## 设计/功能范围

实际主题集中ui/theme，组件ui/components，主页ui/screens/home；真实任务名、今日时间轴、时段+圆环、全屏可搜索白名单、指定置顶与拼音英文混排。相机用系统Intent解析的受保护系统应用快捷入口，无CAMERA权限；其余置顶只排序已选白名单，不扩大授权。

无障碍系统开关与服务连接分开显示；缺权只在Activity恢复前台后弹说明，由用户点选系统授权。稍后不连弹；慢连接恢复会撤掉旧提示。没有自动开系统权限。普通退出10秒，弹窗内/全屏网格/覆盖层固定底部均有无需等待的应急操作。

后台识别只为有效未解除在线计划或本次手动专注保留一个内存当前应用候选，与最近窗口包名一致才拦截。跨任务边界保留连续证据；缺权/锁屏/监测间断/回退等清除。onInterrupt只清反馈，不当成撤权；实际服务解绑仍放行。未知系统窗口始终让出，无永久保活保证。

## 真机与尚未验收

更新前只读诊断显示无障碍开关与服务均正常，当前配置保留本机；不把一次正常样本当成“反复关闭已根治”。手机安装和更新后的对照结果追加于下。没有修改真实计划、私有表、统计/上传许可或复盘自动化，没有读取屏幕/节点/全局logcat。

NOT RUN：真实界面截图/小屏字号/TalkBack/对比度人工验收、按钮触控与普通退出完整真机过程；静止目标到点实际拦截、长待机、清理、锁屏/断网/重启与20次成功率。不因单测或安装成功标为通过。系统关闭无障碍开关的根因仍未确定；改进属于诊断与故障处理，不承诺自动重新授权。

Stage3独立日程/统计/监管/设置页面及导航迁移尚未全量完成；原有入口仍在，Google协议及业务规则保持。

## 安装对照抓到的重连缺陷
首个整合包安装后，无障碍系统开关仍开启、服务已连接、Google连接保留，但当前逻辑时段存在而会话IDLE。固定诊断代码为PLAN_PERMISSION_LOST，执行状态为本段已解除：PlanRuntime在更新后服务尚未绑定时将短暂未就绪永久抑制，非系统开关关闭。已用PlanStopPolicy拆分暂时断连与用户主动结束，补2项回归测试；此修复的最终构建、安装和恢复结果在后面追加，旧30项/旧哈希不代表最终33号包。系统有时真正关闭无障碍的其他情形仍无证据，不能据此认定全部根因。

## 修复后最终构建（versionCode33）
最终两变体各32项测试通过，Lint均0错误；Lab47/online90警告。更新后的源码/合并Manifest/APK权限审计、两份release隔离、v2签名重新通过。Windows本次构建曾停在Gradle原生文件系统枚举，终止已确认的本任务Gradle客户端/守护进程并禁用文件监听后成功重建，没有修改全局Java或系统配置。

lifeos-lab-v0.4.2.apk SHA256 96e54ad26febc4bacb8f961e0fbd6165297488dfdcdd468146431cdefda6dffb
lifeos-online-v0.4.2.apk SHA256 1cb4add93040d6a7df0e6c0fc207fdca60e191bf3dd192638f0f69026008f514

## 当前安装状态
首次整合的32号包已安装并启动，虚构应用名置顶/拼音英文混排设备自检通过。修复PLAN_PERMISSION_LOST的最终33号包已通过32项测试两变体、最终审计和签名，但首次覆盖尝试返回no devices found，随后ADB列表为空；等待用户重连，当前不能声称33号包已安装或重连修复已真机通过。
