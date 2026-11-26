# v0.4.0-alpha验证

## 项目迁移
代码、.git全部历史、Android工程、原SDK/Gradle及缓存、工作记录和已有outputs已迁入D:/document/10.others/lifeos。旧work/lifeos与work/toolchain路径已不存在。SDK路径重写；tools/build.ps1从项目根定位所有本地工具。Android Studio JBR21仍是已安装的系统Java依赖，不移动系统应用。work与outputs均被Git忽略，迁移不上传工具、签名或私有记录。

## 自动验证
lab：22项单元测试0失败；Lint0错误/43警告；assemble及release Manifest生成成功。
online：22项单元测试0失败；Lint0错误/73警告；assemble及release Manifest生成成功。

两份APK签名验证通过且证书一致。源码、两变体合并Manifest及APK独立权限审计通过；Lab无INTERNET，online仅允许额外INTERNET/ACCESS_NETWORK_STATE/WAKE_LOCK。release均不含DebugReceiver。git diff --check通过。测试只用模拟数据，不保存真实应用清单或轨迹。

## 私有服务
3项Python集成测试：未认证拒绝、phone/planner权限隔离、计划递增/冲突/无效拒绝与取消；日统计幂等；分批区间收齐后原子替换、重传不累计旧区间、删除需认证。Windows启动/停止脚本已实际运行，环回未认证GET返回401、两种授权GET返回200和空模拟快照。服务已停止，没有开启公网Tunnel。

## 真机和真实在线闭环
NOT RUN。用户选择先完成开发和安装包，本轮不安装或操作手机。Google手机OAuth/交换表尚未接通；实际HTTPS/ChatGPT写入者尚未配置。不得把结构单测和环回服务当成A1/A2/B1/B2/C1通过。全天数据实际覆盖、锁屏/Doze/后台延迟、重启、耗电、分屏、ChatGPT跳转与5分钟通行均待真机验证。

## 已知边界
详细区间可分批上传；本机重新汇总当日/昨日，系统保留事件的实际范围可能不足。未知屏幕/锁状态单独计时；最晚RESUMED为前台估计，分屏不测量。计划匹配仅最近缓存版本+当前规则，实际有效管控时间为null。用户可独立停止采集/上传、预览、保留期及删本机/私有服务数据。没有改真实日程或自动化，没有读取消息/节点/屏幕。
