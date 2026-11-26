package io.lifeos.focuslab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class OnlineActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { Panel() } } }
    }
    override fun onResume() { super.onResume(); IntegrationHooks.resume(this@OnlineActivity) }
    @Composable private fun Panel() {
        val plans = remember { PlanStore(this@OnlineActivity) }
        var credentials by remember { mutableStateOf(SyncConfig(this@OnlineActivity).read()) }
        var endpoint by remember { mutableStateOf(credentials?.endpoint ?: "") }
        var token by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        var revision by remember { mutableIntStateOf(0) }
        var reports by remember { mutableStateOf(UsageCollector.reports(this@OnlineActivity)) }
        var preview by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) { while (true) { delay(1000); revision++ } }
        fun setting(key: String, checked: Boolean) {
            val editor = plans.prefs.edit().putBoolean(key, checked)
            if (!checked && key == "usage_enabled") editor.putBoolean("usage_upload", false).putBoolean("usage_details_upload", false)
            if (!checked && key in setOf("usage_details", "usage_upload")) editor.putBoolean("usage_details_upload", false)
            editor.commit(); revision++; SyncWork.schedule(this@OnlineActivity)
        }
        if (preview) AlertDialog(onDismissRequest = { preview = false }, title = { Text("上传内容预览（仅本机显示）") },
            text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text(reports.firstOrNull()?.let { report ->
                    val copy = org.json.JSONObject(report.toString())
                    if (!plans.prefs.getBoolean("usage_details_upload", false)) copy.remove("intervals")
                    copy.toString(2)
                } ?: "启用统计后才会产生数据。")
            } }, confirmButton = { TextButton(onClick = { preview = false }) { Text("关闭") } })
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = { finish() }) { Text("返回计时主页") }
            Text("在线计划", style = MaterialTheme.typography.headlineSmall)
            val plan = remember(revision) { plans.load() }
            Text("版本 ${plan?.revision ?: 0} · ${plans.prefs.getString("execution_status", "尚未取得计划")}")
            Text(plans.prefs.getString("sync_status", "尚未连接私有数据源").orEmpty())
            val requestedRevision = plans.prefs.getLong("change_revision", -1)
            if (requestedRevision >= 0) Text(if ((plan?.revision ?: 0) > requestedRevision) "已收到申请之后的新计划版本" else "已打开调整入口，等待ChatGPT或规划端发布新版本")
            val syncedAt = plans.prefs.getLong("last_sync", 0)
            if (syncedAt > 0) Text("最近同步：${time(syncedAt)}")
            plan?.let { value ->
                Text("有效至 ${time(value.validUntil)}；无任务、自由模式、未知模式或过期时放行。")
                value.tasks.take(20).forEach { Text("${time(it.start)}–${time(it.end)}  ${it.title.ifBlank { "专注任务" }} · ${when (it.mode) { "focus" -> "专注"; "free" -> "自由"; else -> "未知模式，放行" }}") }
            }
            Row {
                Checkbox(plans.armed, { checked -> scope.launch {
                    try { withContext(Dispatchers.IO) { PlanRuntime.prepare(this@OnlineActivity) }; plans.armed = checked; PlanRuntime.reconcile(this@OnlineActivity, true); revision++ }
                    catch (_: Exception) { message = "暂不能准备应用规则，自动执行未变更" }
                } })
                Text("按已缓存计划自动执行（无需手动开始）")
            }
            Text("到点按绝对时间执行；锁屏不点亮屏幕，解锁重新判断。关闭此开关会解除计划管控。应急结束后，本段结束前不会自动重启。")
            if (plans.prefs.getString("suppressed_task", "").orEmpty().isNotEmpty()) TextButton(onClick = {
                plans.prefs.edit().remove("suppressed_task").commit(); PlanRuntime.reconcile(this@OnlineActivity, true); revision++
            }) { Text("恢复本段计划执行") }
            OutlinedTextField(endpoint, { endpoint = it }, label = { Text("私有 HTTPS 数据源") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(token, { token = it }, label = { Text(if (credentials != null) "新访问凭证（留空保留）" else "手机访问凭证") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("凭证保存在系统密钥库保护的本机存储；不上传GitHub。Google手机授权尚未配置，本版先支持受控HTTPS交换服务。")
            Button(onClick = {
                try { SyncConfig(this@OnlineActivity).save(endpoint, token.ifEmpty { credentials?.token ?: "" }); credentials = SyncConfig(this@OnlineActivity).read(); token = ""; SyncWork.request(this@OnlineActivity, true); message = "已保存，正在同步" }
                catch (_: Exception) { message = "请检查HTTPS地址和访问凭证" }
            }) { Text("保存连接并同步") }
            TextButton(onClick = { SyncWork.request(this@OnlineActivity, true); message = "已请求更新，后台频率受系统限制" }) { Text("重新检查更新") }
            Text("后台约15分钟起检查，系统可能延迟；打开应用、解锁和网络恢复会尝试补同步。已缓存计划到点不依赖网络。")
            HorizontalDivider()
            Text("申请调整计划", style = MaterialTheme.typography.titleLarge)
            Text("先复制申请，再到ChatGPT说明变更。这里只打开应用，不读取、填写或发送聊天；返回后检查新版本。")
            TextButton(onClick = { ChangeAccess.copy(this@OnlineActivity); message = "申请已复制，可以粘贴到你的规划对话" }) { Text("复制计划调整申请") }
            Button(onClick = { if (!ChangeAccess.open(this@OnlineActivity)) message = "没有找到ChatGPT应用。请安装后使用；浏览器入口暂不提供专注内通行。" }) { Text("打开 ChatGPT 申请调整") }
            Text("从此按钮打开时，仅ChatGPT临时允许5分钟，计时仍继续；返回LifeOS后结束临时通行。是否能发布新计划取决于你在ChatGPT配置的授权工具。")
            HorizontalDivider()
            Text("全天手机使用统计", style = MaterialTheme.typography.titleLarge)
            val enabled = plans.prefs.getBoolean("usage_enabled", false)
            Row { Checkbox(enabled, { checked ->
                if (checked && !usageGranted(this@OnlineActivity)) message = "已有使用情况授权当前不可用，未开启采集"
                else { if (checked) plans.prefs.edit().putLong("usage_since", System.currentTimeMillis()).commit(); setting("usage_enabled", checked); SyncWork.request(this@OnlineActivity, true) }
            }); Text("从现在起统计全天必要元数据") }
            Text("包括应用前台估计时长、屏幕交互、可确认的解锁次数、类别和小时分布。不会读取消息、页面、屏幕或输入。初次状态未知会单独列出；前台时长不等于注意力。默认保留7天，回补最近两天。")
            Row { Checkbox(plans.prefs.getBoolean("usage_details", false), { setting("usage_details", it) }, enabled = enabled); Text("本机保存详细应用区间（可选）") }
            val destination = credentials?.endpoint ?: "尚未配置目的地"
            Text("上传目的地：$destination")
            Row { Checkbox(plans.prefs.getBoolean("usage_upload", false), { setting("usage_upload", it); if (it) SyncWork.request(this@OnlineActivity, true) }, enabled = enabled && credentials != null); Text("允许上传日汇总（含按应用时长）") }
            Row { Checkbox(plans.prefs.getBoolean("usage_details_upload", false), { setting("usage_details_upload", it) }, enabled = enabled && plans.prefs.getBoolean("usage_upload", false) && plans.prefs.getBoolean("usage_details", false)); Text("另外允许上传详细区间") }
            Row { listOf(1, 7, 30).forEach { days -> FilterChip(selected = plans.prefs.getInt("usage_retention", 7) == days, onClick = { plans.prefs.edit().putInt("usage_retention", days).apply(); revision++ }, label = { Text("保留${days}天") }) } }
            Text(plans.prefs.getString("usage_status", "尚未开启采集").orEmpty())
            Text(plans.prefs.getString("usage_upload_status", "尚未上传统计").orEmpty())
            TextButton(onClick = { scope.launch {
                withContext(Dispatchers.IO) { runCatching { UsageCollector.collect(this@OnlineActivity) } }
                reports = UsageCollector.reports(this@OnlineActivity); revision++
            } }) { Text("查看本机最新统计") }
            reports.take(3).forEach { report ->
                Text("${report.optString("date")}：屏幕交互 ${report.optLong("screen_interactive_ms") / 60000} 分；解锁交互 ${report.optLong("unlocked_interactive_ms") / 60000} 分；可靠解锁 ${report.optInt("unlock_count")} 次")
                Text("覆盖 ${report.optString("coverage_start")} 至 ${report.optString("coverage_end")}；屏幕状态未知 ${report.optLong("screen_state_unknown_ms") / 1000} 秒")
                val apps = report.optJSONObject("usage_by_app")
                apps?.keys()?.asSequence()?.map { it to apps.optLong(it) }?.sortedByDescending { it.second }?.take(8)?.forEach { (pkg, duration) ->
                    val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault("未安装/不可见应用")
                    Text("$label：${duration / 1000} 秒")
                }
            }
            TextButton(onClick = { reports = UsageCollector.reports(this@OnlineActivity); preview = true }) { Text("预览将上传的数据") }
            TextButton(onClick = { UsageCollector.clear(this@OnlineActivity); reports = emptyList(); revision++; SyncWork.schedule(this@OnlineActivity); message = "已关闭采集和上传，清除本机统计。云端已传数据需在私有服务单独删除。" }) { Text("停止并清除本机统计") }
            TextButton(onClick = { scope.launch {
                setting("usage_upload", false)
                val current = SyncConfig(this@OnlineActivity).read()
                val result = withContext(Dispatchers.IO) { runCatching { require(current != null); ExchangeClient(current).deleteUsage() } }
                message = if (result.isSuccess) "已删除此交换服务中的统计，上传已关闭" else "未能确认云端删除，请检查连接后重试"
            } }) { Text("删除私有交换服务中的统计") }
            TextButton(onClick = { plans.armed = false; PlanRuntime.reconcile(this@OnlineActivity, true); SyncConfig(this@OnlineActivity).clear(); credentials = null; token = ""; endpoint = ""; setting("usage_upload", false); message = "已断开同步，计划自动执行关闭" }) { Text("断开私有同步") }
            if (message.isNotEmpty()) Text(message)
        }
    }
    private fun time(value: Long): String = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))
}
