# UI 重构 Stage 1

## 当前成果与边界

基线phase05-google-sheets@5f500e6641b0029a34b2d11e524ae352b0656051；任务分支ui-redesign@87021fb。正式app源代码、协议和业务行为保持基线，本阶段只新增独立debug原型模块，不将某套主题推广到正式页面。用户最新要求优先：专注显示起止时段和圆环，不使用主倒计时；今日安排可视化；全屏白名单和指定常用顺序；缺权解释由用户操作。

此前同一工作阶段已经编写的功能/UI候选完整保存在codex/phase06-focus-ui@c64923c，未安装、未合入本分支。不能把该候选视为选定风格或稳定性验收；功能修复将独立协调。没有丢弃或覆盖已有工作。

## 现状审计与功能映射

|现有入口|现状/问题|选定风格后的目标|Stage1状态|
|---|---|---|---|
|MainActivity/FocusHome|手动入口突出，倒计时和零散文字，白名单按钮逐项堆叠|自动任务优先的首页；独立时段专注页|三套模拟首页/专注页|
|OnlineActivity|连接、计划、统计、隐私、调整混在长表单|日程、统计、二级设置分离|原业务保留；原型示意导航|
|GoogleConnectActivity|独立默认主题、文件授权与检查|统一主题，保留用户授权与错误码|原业务保留|
|FocusAccessibilityService|原生View覆盖层；生命周期/安全要求独立|与专注页语义一致，但保留窗口安全和超时|本阶段不改|
|AppRules/CategoryPicker|类别开关、应用例外、人工修正|监管页继续沿用规则优先级|原型仅虚构网格；未迁移规则|
|UsageCollector/OnlineActivity|真实汇总与许可、预览/删除/保留期|统计页仅用真实可靠字段，未知留空|原型明确虚构，缺权/空态不显示假实测|
|CapabilityPanel/DebugReceiver|Lab测试及debug安全边界|二级诊断仍可达，release无receiver|保持原样|
|普通退出/应急/电话|10秒确认/立即解除/系统通话|全部保留，应急不依赖滚动或等待|原型交互均为本机模拟，不调用业务|

## 三套对照

所有方案共享PrototypeState、同一虚构日期/任务和九个场景。样例09:00–10:30，已进行35分钟，不表示真实计划。

|方案|视觉与布局|专注页|取舍|
|---|---|---|---|
|A 自然简约|绿灰/米白、22dp圆角、轻卡片、安静的文字层级|连续圆环、上下起止时间、宽白名单按钮|更适合长时间阅读；需要用户确认颜色喜好|
|B 黑白极简|中性色、3dp小圆角、分隔线和网格、等宽数字|细线圆环、清晰数字、低装饰|信息直接，视觉更硬朗|
|C Material兼容探索|紫色调、32dp圆角、色块层级、胶囊按钮/导航|分段粗圆环、强调主操作|更鲜明；不是完整Material3 Expressive组件移植|

现有Material3 1.3.2不升级。本轮C用现有稳定API表达形状/色彩层级；完整Expressive API、波形动画和工具链升级不纳入。未开启动态壁纸取色，以保证三套可对比。

提供浅/深色、正常/1.3/1.6倍字号开关，主要操作最小48dp，焦点页内容可滚动而应急条固定在底部；普通退出弹窗也有立即应急入口。时间轴有对应文字任务列表。实际渲染、对比度、TalkBack和小屏/大字号人工检查尚未执行，不能宣称视觉通过。SDK无模拟器/镜像，未使用computer-use；按任务书回退为独立测试APK，见ui-previews/README.md。

## 设计资源与许可核对（2026-11-28）

本阶段没有复制第三方图片、图标或应用整套源码，组件为项目自行编写并调用已有AndroidX。未引入新库、付费服务或遥测。下列源码及Apache-2.0许可证已阅读；只借鉴结构的项目不构成代码移植，若后续移植须保留原版权和许可证。

|资源/固定版本|实际检查与决定|许可/署名|
|---|---|---|
|[Material 3 Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)，已安装material3:1.3.2；Compose UI/Foundation1.7.8|采用主题角色、Typography、Shapes、Card、NavigationBar、Chip、Dialog、Canvas基础组件；m3.material.io两个页面仅返回requires JavaScript，改读官方Compose说明和版本记录|AndroidX Apache-2.0，已有依赖，无额外源码复制|
|[Now in Android Theme.kt](https://github.com/android/nowinandroid/blob/2982e63cb691ad49cd009aab43dc206fee6699c2/core/designsystem/src/main/kotlin/com/google/samples/apps/nowinandroid/core/designsystem/theme/Theme.kt)|读浅/深色角色与Theme参数组织；采纳集中theme层和状态传参，舍弃完整架构、动态取色与渐变|Apache-2.0；The Android Open Source Project；LICENSE已读，无复制|
|[JetLaggedScreen.kt](https://github.com/android/compose-samples/blob/c7a01f04dcd2010935a1e11bec3da5c59f55f6a2/JetLagged/app/src/main/java/com/example/jetlagged/JetLaggedScreen.kt)|实际为睡眠/健康数据界面、睡眠图卡与Flow布局；参考时间分布与文字配合，不搬心率/健康指标和动态图案|Compose Samples Apache-2.0；The Android Open Source Project，无复制|
|[ReplyApp.kt](https://github.com/android/compose-samples/blob/c7a01f04dcd2010935a1e11bec3da5c59f55f6a2/Reply/app/src/main/java/com/example/reply/ui/ReplyApp.kt)|读NavigationWrapper/屏宽适配/显式UI state；参考分离导航与状态，暂不增加Navigation/Foldable库|同上，LICENSE已读，无复制|
|[Jetcaster](https://github.com/android/compose-samples/blob/c7a01f04dcd2010935a1e11bec3da5c59f55f6a2/Jetcaster/README.md)|核验README为播客应用；媒体播放/图片与本次计划工具无直接复用必要，不采用其依赖|同一仓库Apache-2.0，无复制|
|[Vico CartesianChartHost](https://github.com/patrykandpatrick/vico/blob/be3d37186f03dca342796762ae73b308f5c4ccf5/vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/cartesian/CartesianChartHost.kt)|读模型producer、占位、滚动/缩放及差值动画接口和libs.versions.toml；当前HEAD依赖栈显著新于基线，兼容性未证明。简单圆环/时间轴无需引入；真实历史趋势阶段再锁定兼容版本验证|Apache-2.0；Patryk Goworowski/Patrick Michalik；LICENSE已读，无复制/依赖|
|[Dribbble Digital Wellbeing](https://dribbble.com/search/digital-wellbeing)|公开列表可读，包含计时/统计仪表主题；只作领域参考，没有取得素材再分发许可，不复制设计图|各作者权利保留，无素材使用|
|[Figma Community](https://www.figma.com/community/search?query=focus%20timer)、[Mobbin](https://mobbin.com/browse/android/apps)|Figma工具无法访问；Mobbin跳转discover后仅返回极少导航，无法核验具体设计。未登录、付费或绕过限制|未获具体素材授权，不使用|

## 工程与导航

新增独立应用android/ui-prototype，applicationId=io.lifeos.uipreview，不依赖:app；源码仅src/debug，release variant禁用。ui/theme集中颜色/形状，ui/components负责圆环/任务/状态，ui/screens持有纯虚构模型，PrototypeActivity是原型导航和模拟动作宿主。没有同步、统计、无障碍服务、应用扫描或账户读取调用。

正式版目标：主页 → 日程/统计/监管；主页当前专注 → 独立专注页 → 全屏白名单；连接/隐私/权限/系统测试 → 二级设置。本轮导航按钮只切换示意内容，不代表相关正式页面已重构。

## 用户选择与下一步

完成Stage1后等待A/B/C选择或修订方向，再实施Stage2/3。最新用户起止时段/圆环优先于原任务书倒计时描述。无障碍开关反复关闭的根因未确定；后台与覆盖层候选修复不冒充真机通过。保留所有Phase05待验收项目，不修改真实表或自动化。验证命令和结果见tests/UI_REDESIGN_VERIFICATION.md。
