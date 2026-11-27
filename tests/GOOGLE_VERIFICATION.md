# Google授权与协议检查

日期：2026-11-27（Asia/Manila）。

- PASS：25项单元测试（两变体），包括未发布快照、RAW公式样式文本保持、40000字符上限、固定列契约。
- PASS：构建、Lint、源码/合并Manifest/APK权限审计；Lab仍无INTERNET，online无新敏感权限；release无debug receiver。
- PASS：v0.4.1 USB覆盖安装、Google账号授权、drive.file原生文件选择、交换表8表结构核对、未发布计划识别、模式写入/读回、Sync State更新。Google连接工具独立读回验证。
- NOT RUN：计划修订、后台周期延迟、锁屏/断网/重启和日汇总实测；两段执行与空计划取消的后续结果见下。
- NOT IMPLEMENTED：Sheets详细时间线（真实历史策略）、Catalog同步、经预览确认的Google行清理。相关开关不启用，不以估算代替真实策略。

公共记录只保留固定状态、通用模式ID和模拟测试。真实表ID、设备代号、应用名单、令牌及原始响应均不入仓。

## 两段模拟计划与取消（USB观测）
用户恢复无障碍后，ChatGPT连接工具在私有Schedule!A2发布revision1、两个各20秒的模拟任务，mode_id=focus；整份快照读回一致。手机自动取得revision1，在预定边界依次IDLE→ACTIVE→IDLE→ACTIVE→IDLE，planned_session与逻辑状态一致，未调用手动开始。观测间隔约2秒，不作为亚秒级延迟或实际拦截成功率证明。结束后发布revision2空快照并读回确认，再关闭armed；无真实计划被覆盖，全天统计未启用。Google授权和专注模式保留。锁屏/断网/重启、后台15分钟实际延迟、目标应用拦截及真实使用统计仍待后续验收。Google视觉渲染未检查（用户禁止computer-use）；仅通过API核对值与单元格格式，写入仅userEnteredValue。
