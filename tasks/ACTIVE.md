# 当前任务 — 在线计划初版与项目迁移

按最新tasks/NEXT_ONLINE_PLAN.md执行，复用phase03分类和白名单，保留离线Lab。项目根目录已迁移到D:/document/10.others/lifeos。

- 已实现：ScheduleSnapshot v1验证、完整原子替换、revision防倒退/冲突、跨日绝对时间/下一边界、缓存执行、到期和未知模式放行、应急解除不重启本段。
- 已实现：online/lab独立权限变体、认证HTTPS SyncAdapter、WorkManager周期与前台/网络/解锁补同步、Android Keystore凭证、错误与缓存状态。
- 已实现：可选全天UsageStats元数据、重排去重/跨日切分/未知覆盖、应用与类别/小时汇总、可选详细区间分批上传、保留期/预览/本机及私有服务删除。
- 已实现：ChatGPT复制申请/打开入口、单应用最多5分钟临时通行、返回同步与等待新版本状态；不读取或发送聊天。
- 已实现：私有交换参考服务、phone/planner权限隔离、OpenAPI描述、Windows启停及ADB配置工具。
- 本轮边界：用户选择先开发和安装包，未连接手机；Google手机OAuth/选表、稳定HTTPS部署、ChatGPT账号端写入链路尚未配置。服务只做环回自检，已停止，无公网部署；未动真实规划或23:30自动化。
- 下一轮：完成实际私有HTTPS/授权写入者接入，再执行A1/A2两段计划、修订取消、锁屏/断网/重启；B1/B2/C1真机验收。统计实际管控时长、历史策略重放、跨设备/多模式及长期稳定性仍待完善。

详见docs/ONLINE_SETUP.md、tests/ONLINE_VERIFICATION.md。分支phase04-online-plan，main不自动合并。旧Phase01能力与安全回归仍有效，不伪称全部已通过。
