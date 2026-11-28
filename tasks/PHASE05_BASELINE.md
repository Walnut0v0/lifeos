# 当前任务 — Google Focus Exchange 实机接入

按docs/FOCUS_EXCHANGE_PROTOCOL.md复用已有分类与白名单。用户明确禁止computer-use，电脑网页配置由用户本人完成；开发命令和授权ADB诊断继续可用。Google Cloud使用Workspace标准额度，不开通试用/账单、不绑卡。

- 已实现并实测：原生Google drive.file文件选择授权；私有表8表契约校验；Schedule未发布识别；手机模式focus写入/读回；Sync State更新。
- 已实现待实测：Google日汇总RAW幂等写入，未知指标留空，Asia/Singapore报告日。采集/上传默认关闭。
- 未实现/禁用：Sheets详细轨迹（真实历史策略）、Catalog同步、表内历史清理。
- 本轮：用户已恢复无障碍，已用两段20秒模拟计划验证自动进入/退出，随后以revision2空计划取消并关闭自动执行；Google连接保留。
- 25项逻辑测试两变体通过，构建/Lint/独立权限审计通过，release无debug receiver。
- 真实表ID、账号、设备代号、令牌和白名单内容不入GitHub。Google授权状态可在本应用debug诊断读取，不读屏幕/节点或全局logcat。

旧任务及能力边界参见NEXT_ONLINE_PLAN.md，main不自动合并；分支phase05-google-sheets。详情tests/GOOGLE_VERIFICATION.md、docs/GOOGLE_CONNECTION.md。
