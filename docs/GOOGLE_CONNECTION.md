# Google 交换表连接

手机端使用Google Identity授权与原生文件选择器，只申请drive.file。用户自行配置Google Cloud项目、启用Sheets/Drive/Picker API、测试用户与Android OAuth客户端；不需要开通试用或绑定账单。应用按已安装包的软件包名和签名识别，客户端ID、账号、令牌、真实工作簿ID不写仓库。

打开「连接Google私有交换表」，阅读用途后选择账号与LifeOS - Focus Exchange。连接会核对8表表头，再将现有focus模式和白名单元数据写入手机拥有的Focus Modes；不会更改Schedule或Planning State。后台重新取得用户已授权的访问令牌；需要再次同意时只提示，不在后台弹授权。

当前支持：Schedule!A2读取和未发布识别、模式发布/读回校验、Sync State状态行、Usage Daily保守日汇总。行写入RAW、按主键查找及读回；单JSON单元40000字符上限。报告日按协议Asia/Singapore。未确认的指标留空，详细轨迹/应用目录上传和Google表内自动删除尚未启用。云端模式不覆盖本机人工规则，未知模式继续放行。

2026-11-27真机已验证授权、选表、协议读取、模式写入读回及Sync State更新。计划仍revision0/unpublished；未自动开启专注或全天采集。无障碍已由用户恢复；两段模拟计划自动进入/退出与空计划取消已完成。锁屏/断网/后台等仍待后续测试。

Google OAuth客户端与APK签名绑定。请使用本地outputs内同签名的安装包；CI临时签名未在个人Google项目注册，CI包不能直接替代当前已授权的手机安装包。
