# UI Stage1 验证（2026-11-28）

本报告记录14406d0的Stage1结果；用户随后已选择A并进入Stage2，正式更新包以UI_BACKGROUND_VERIFICATION.md为准。

## 构建和单元测试

- PASS：独立原型:ui-prototype:assembleDebug、testDebugUnitTest、lintDebug；2项模型测试通过，Lint 0错误/0警告。
- PASS：原app testLabDebugUnitTest/testOnlineDebugUnitTest，各25项通过；assembleLabDebug/assembleOnlineDebug、lintLabDebug/lintOnlineDebug、两份release主Manifest生成通过。Lab 0错误/43警告、online 0错误/84警告（保留基线提示）。
- PASS：独立模块tasks --all没有assembleRelease/packageRelease/compileReleaseKotlin等release任务；beforeVariants禁用release。原型源只在src/debug，正式app源相对5f500e6零改动。
- PASS：tools/audit_ui_prototype.py检查debug源码、依赖、最终合并Manifest/APK：无系统权限、联网、应用查询、服务、真实业务引用；独立applicationId。AndroidX内部签名权限不是系统敏感授权。
- PASS：tools/audit.py对两变体的源码、合并Manifest和APK审计；Lab无INTERNET，online保持原网络范围。tools/check_release_manifest.py两份release无debug receiver。
- PASS：独立APK apksigner verify --verbose，v2签名有效。
- Windows审计首次读取中文badging发生GBK解码错误，已明确使用UTF-8后重新通过；未忽略错误或修改审计要求。

复现：按现有tools/build.ps1设置本地JDK/Gradle后，在android运行Gradle任务。另运行python tools/audit_ui_prototype.py <prototype.apk> <aapt>，以及原有两变体审计。原型CI任务已加入工作流，云端结果独立记录，不用本地结果替代。

## 渲染和真机

- NOT RUN：实际Compose渲染图、正常/1.3/1.6字号的视觉验收、深浅色对比度、TalkBack、横屏/小屏和触控成功率。当前SDK无emulator/system-images，未使用computer-use。没有以代码或浏览器示意冒充Android截图；按任务书交付独立可运行预览APK。
- 原型只有虚构日期/任务、固定进度、应用名称示意，不访问真实计划/采集/同步/手机应用清单；底部导航的统计/监管仅示意，不代表Stage3完成。
- USB安装：首次被系统INSTALL_FAILED_ABORTED取消；用户明确要求重试后安装Success；am start -W返回Status: ok，独立预览进程存在。仅证明安装和启动，不证明布局/交互验收。没有覆盖原LifeOS。
- 原无障碍系统开关反复关闭的原因未确定；安装前一次只读诊断显示当时授权和服务均正常，不据此判定长期稳定。第一次ADB超时后重启daemon即恢复，不归因为手机USB未开启。
- 原Phase05锁屏/断网/重启、后台延迟、真实应用拦截、真实日汇总等仍未验收。本阶段未发布或修改真实计划、私有表和复盘自动化。

## 交付与下一阶段

独立包outputs/lifeos-ui-preview-stage1.apk，SHA256：ff0ba914bd0521044a02de27c3abb1e1cb98fee354826365a752aad9c601510b。APK不申请业务权限，支持A自然/B黑白/C Material兼容方案、九种模拟状态、深浅色与字号切换。用户选定后再全量改造；不合并main或phase05。

用户原先要求的功能/UI候选保存在codex/phase06-focus-ui，尚未安装/未并入原型分支。候选的构建结果另见该分支UI_BACKGROUND_VERIFICATION，不混为正式原型或真机验收。
