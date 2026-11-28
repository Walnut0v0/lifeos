package io.lifeos.focuslab

import android.Manifest
import android.app.*
import android.content.Intent
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    companion object { var experimentsRef = java.lang.ref.WeakReference<CapabilityExperiments>(null) }
    private lateinit var store: LabStore
    private lateinit var experiments: CapabilityExperiments
    private var exportText = ""
    private var launcherRequest by mutableIntStateOf(0)
    private fun consumeLauncherIntent(value: Intent?) {
        if (value?.getBooleanExtra("open_allowed_apps", false) == true) {
            value.removeExtra("open_allowed_apps"); launcherRequest++
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); consumeLauncherIntent(intent) }
    private val saveReport = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(exportText) } }
        catch (_: Exception) { store.log("EXPORT_FAILED") }
        exportText = ""
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); store = LabStore(this)
        consumeLauncherIntent(intent)
        experiments = CapabilityExperiments(this)
        experimentsRef = java.lang.ref.WeakReference(experiments)
        setContent { LifeOsTheme { Surface { AppRoot() } } }
    }
    override fun onPause() { if (::experiments.isInitialized) experiments.removeOverlay(); super.onPause() }
    override fun onDestroy() {
        if (::experiments.isInitialized) experiments.stop()
        if (experimentsRef.get() === experiments) experimentsRef.clear()
        super.onDestroy()
    }
    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) try { store.reschedule() } catch (_: Exception) { store.stop("RESUME_FAULT") }
        IntegrationHooks.resume(this)
    }
    private fun open(action: String, ownPackage: Boolean = false) {
        try { startActivity(Intent(action).apply { if (ownPackage) data = Uri.parse("package:$packageName") }) }
        catch (_: Exception) { store.log("SETTINGS_UNAVAILABLE") }
    }
    @Composable private fun AppRoot() {
        var lab by remember { mutableStateOf(false) }
        var pulse by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) { while (true) { delay(250); pulse++ } }
        val active = remember(pulse) { store.phase() == Phase.ACTIVE && store.isWhitelistSession }
        if (!lab || active) FocusHome(this, store, active, pulse, launcherRequest) { lab = true }
        else Column(Modifier.fillMaxSize()) {
            TextButton(onClick = { lab = false }) { Text("返回专注主页") }
            Box(Modifier.weight(1f)) { LabScreen() }
        }
    }
    @Composable private fun LabScreen() {
        var pulse by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) { while (true) { delay(500); pulse++ } }
        val phase = remember(pulse) { store.phase() }
        var tab by remember { mutableIntStateOf(0) }
        var message by remember { mutableStateOf("") }
        var mode by remember { mutableStateOf(store.mode) }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Text("LifeOS Focus Lab", style = MaterialTheme.typography.headlineSmall)
            Text("v0.1.4-poc · 离线系统验证 · ${when (phase) { Phase.ACTIVE -> "正在计时"; Phase.SCHEDULED -> "等待开始"; Phase.FAULT -> "状态异常，已解除"; Phase.IDLE -> "未开始或已结束" }}")
            Button(onClick = { store.stop(); message = "已停止所有测试" }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("停止所有测试 / 立即解除") }
            Row { listOf("诊断", "应用", "计时", "能力", "报告").forEachIndexed { i, title -> TextButton(onClick = { tab = i }, contentPadding = PaddingValues(8.dp)) { Text(title) } } }
            Text(message)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tab) {
                    0 -> {
                        Text("T02 / T06 / T07 / T08：权限与前台检测", style = MaterialTheme.typography.titleMedium)
                        Text("设备：${Build.MANUFACTURER} / Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\nMagicOS 版本请在系统设置中核对。")
                        Text("无障碍连接：${FocusAccessibilityService.live != null}\n精确闹钟：${store.exact()}\n通知：${getSystemService(NotificationManager::class.java).areNotificationsEnabled()}\n电池优化豁免：${getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)}")
                        Text("T02 最近外部窗口包名（仅内存）：\n${FocusAccessibilityService.lastExternalPackage}\n窗口事件累计：${FocusAccessibilityService.eventCount}（不是应用切换准确率）")
                        Text("覆盖层最近情况：${store.overlayStatus}")
                        Text("无障碍仅读取窗口事件包名，不读取文字、输入或消息。启用前请阅读系统授权说明。")
                        Button(onClick = { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) }) { Text("打开无障碍设置") }
                        Button(onClick = { if (Build.VERSION.SDK_INT >= 31) open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, true) }) { Text("精确闹钟设置（Android 12+）") }
                        Button(onClick = { if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) else open(Settings.ACTION_APP_NOTIFICATION_SETTINGS) }) { Text("请求通知权限") }
                        Button(onClick = { open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }) { Text("电池优化列表") }
                        Button(onClick = { open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true) }) { Text("应用详情 / 撤权 / 卸载") }
                        Text("荣耀后台测试：分别记录默认设置，以及手动允许自启动、关联启动、后台活动后的表现。系统菜单以实机为准。\n强行停止后需要手动打开本应用；手动计时重启后取消；绝对时段在首次解锁并收到系统广播/服务重连后重算。")
                    }
                    1 -> AppPicker { message = it }
                    2 -> {
                        Text("T03 / T04 / T05：独立拦截与调度", style = MaterialTheme.typography.titleMedium)
                        listOf("DETECT" to "仅检测，不拦截", "HOME" to "A：返回桌面", "OVERLAY" to "B：覆盖层（10 秒安全超时）").forEach { (key, title) ->
                            Row { RadioButton(selected = mode == key, onClick = { store.mode = key; mode = key }); Text(title, Modifier.padding(top = 12.dp)) }
                        }
                        Text("改变模式、目标会停止当前测试。仅检测可不选目标。拦截请先选一个非关键第三方应用。")
                        Text("覆盖层最近情况：${store.overlayStatus}")
                        var duration by remember { mutableStateOf("1") }
                        var wait by remember { mutableStateOf("2") }
                        var usageAssist by remember { mutableStateOf(store.usageAssistOptIn) }
                        Row {
                            Checkbox(usageAssist, onCheckedChange = { usageAssist = it; store.usageAssistOptIn = it })
                            Text("本次允许使用已授权的使用情况访问，辅助判断桌面过渡窗口（最多60秒，不保存轨迹）", Modifier.padding(top = 8.dp))
                        }
                        Text("增强识别：${if (SystemClock.elapsedRealtime() < ForegroundProbe.enabledUntilElapsed) "本次正在使用" else if (usageAssist) "已勾选，下一次手动开始时启用" else "未勾选"}")
                        OutlinedTextField(duration, { duration = it }, label = { Text("持续分钟：1–30") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(wait, { wait = it }, label = { Text("相对计划：几分钟后开始（1–1440）") }, modifier = Modifier.fillMaxWidth())
                        fun start(manual: Boolean, absolute: Long? = null) {
                            try {
                                val d = duration.toLongOrNull() ?: error("时长必须为整数")
                                require(d in 1..30) { "时长需为 1–30 分钟" }
                                require(!usageAssist || usageGranted(this@MainActivity)) { "辅助判断需要先在能力页授权使用情况访问" }
                                require(!usageAssist || manual) { "辅助判断仅用于本次手动短测，请使用立即开始" }
                                if (mode != "DETECT") {
                                    require(FocusAccessibilityService.live != null) { "请先启用无障碍服务" }
                                    require(store.targets.isNotEmpty()) { "请先选择测试目标" }
                                    require(store.targets.none { AppCatalog(this@MainActivity).protected(it) }) { "安全关键应用不可作为目标，请重新选择" }
                                }
                                val w = wait.toLongOrNull() ?: 0
                                require(manual || absolute != null || w in 1..1440) { "等待需为 1–1440 分钟" }
                                val at = if (manual) System.currentTimeMillis() else absolute ?: (System.currentTimeMillis() + w * 60_000)
                                require(at >= System.currentTimeMillis() - 1000 && at < System.currentTimeMillis() + 86_400_000) { "开始时间需在未来 24 小时内" }
                                store.start(at, d * 60_000, manual)
                                if (usageAssist && manual) ForegroundProbe.enableForTest(60)
                                store.log(if (usageAssist && manual) "USAGE_ASSIST_ENABLED" else "USAGE_ASSIST_DISABLED")
                                message = if (store.exact()) "测试已设置" else "已使用非精确闹钟：系统可能延迟，不能据此验收精确调度"
                            } catch (e: IllegalArgumentException) { message = e.message ?: "参数无效" }
                            catch (_: Exception) { store.stop("START_FAULT"); message = "无法安全开始，已解除测试" }
                        }
                        Button(onClick = { start(true) }) { Text("立即开始手动测试") }
                        Button(onClick = { start(false) }) { Text("设置相对计划") }
                        Button(onClick = {
                            val now = Calendar.getInstance()
                            DatePickerDialog(this@MainActivity, { _, y, m, d ->
                                TimePickerDialog(this@MainActivity, { _, h, min ->
                                    val c = Calendar.getInstance().apply { set(y, m, d, h, min, 0); set(Calendar.MILLISECOND, 0) }
                                    start(false, c.timeInMillis)
                                }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
                            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
                        }) { Text("选择绝对开始日期和时间") }
                        val s = remember(pulse) { store.session() }
                        if (s != null) {
                            val remaining = if (s.manual) s.elapsedEnd - SystemClock.elapsedRealtime() else s.end - System.currentTimeMillis()
                            Text("起止：${time(s.start)} → ${time(s.end)}\n剩余：${maxOf(0, remaining) / 1000} 秒\n手动计时使用单调时钟；计划采用绝对时间。")
                        }
                        Text("到点时只尝试处理最近 5 秒内已知的前台包名；未知时等待新窗口事件。HOME 接受请求不代表肉眼已看到桌面。分屏/小窗、Doze、强停、锁屏和重启效果均需实测。")
                    }
                    3 -> CapabilityPanel(this@MainActivity, store, experiments, pulse) { sample -> exportText = sample; saveReport.launch("lifeos-simulated-plan.json") }
                    4 -> {
                        Text("T01–T08：手工记录与脱敏导出", style = MaterialTheme.typography.titleMedium)
                        Text("仅在实际完成相应测试后记录结果。延迟、成功数、样本量及子场景请填写仓库测试报告。日志最多 100 条，不包含包名、应用名、屏幕内容或完整使用历史。")
                        listOf("T01", "T02", "T03-A", "T03-B", "T04", "T05", "T06", "T07", "T08", "T09", "T10", "T11", "T12").forEach { id ->
                            Text(id)
                            Row { listOf("PASS", "PARTIAL", "FAIL", "NOT RUN").forEach { status -> TextButton(onClick = { store.result(id, status); message = "$id 已记为 $status" }, contentPadding = PaddingValues(4.dp)) { Text(status) } } }
                        }
                        var preview by remember { mutableStateOf("") }
                        Button(onClick = { preview = store.report() }) { Text("生成 / 刷新脱敏预览") }
                        if (preview.isNotEmpty()) {
                            Text(preview)
                            Button(onClick = { exportText = preview; saveReport.launch("lifeos-lab-report.json") }) { Text("选择文件位置导出此预览") }
                        }
                        Button(onClick = { store.clearLogs(); preview = ""; message = "诊断日志已清除" }) { Text("清除诊断日志") }
                        Button(onClick = { store.stop(); store.targets = emptySet(); store.clearLogs(); getSharedPreferences("lab", MODE_PRIVATE).edit().clear().commit(); preview = ""; message = "本地数据已清除" }) { Text("停止并删除全部本地配置与结果") }
                    }
                }
            }
        }
    }
    @Composable private fun AppPicker(feedback: (String) -> Unit) {
        var apps by remember { mutableStateOf<List<LabApp>>(emptyList()) }
        var safe by remember { mutableStateOf<Set<String>>(emptySet()) }
        var selected by remember { mutableStateOf(store.targets) }
        var query by remember { mutableStateOf("") }
        var refresh by remember { mutableIntStateOf(0) }
        LaunchedEffect(refresh) {
            try {
                val result = withContext(Dispatchers.IO) {
                    val catalog = AppCatalog(this@MainActivity); val list = catalog.apps()
                    list to list.filter { !catalog.protected(it.packageName) }.map { it.packageName }.toSet()
                }
                apps = result.first; safe = result.second
                val valid = selected.intersect(safe)
                if (valid != selected) { store.targets = valid; selected = valid }
            } catch (cancelled: CancellationException) {
                // Leaving the application-list tab cancels its scan; this is not a device failure.
                throw cancelled
            } catch (_: Exception) { store.stop("SCAN_FAULT"); apps = emptyList(); safe = emptySet(); feedback("扫描失败，已停止测试") }
        }
        Text("T01：应用扫描与目标选择", style = MaterialTheme.typography.titleMedium)
        Text("系统应用、桌面、拨号器、输入法、设置和本应用受保护。仅勾选非关键第三方测试应用。分身和工作资料可能不可见。")
        OutlinedTextField(query, { query = it }, label = { Text("搜索应用名称或包名") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { refresh++ }) { Text("刷新应用列表（${apps.size}）") }
        Text("系统已声明分类：${apps.count { it.category >= 0 }}/${apps.size}；-1 表示未声明。此处不做自动分类。")
        apps.filter { it.name.contains(query, true) || it.packageName.contains(query, true) }.forEach { app ->
            Row(Modifier.fillMaxWidth()) {
                Checkbox(app.packageName in selected, enabled = app.packageName in safe, onCheckedChange = { checked ->
                    selected = if (checked) selected + app.packageName else selected - app.packageName
                    store.targets = selected
                })
                Image(remember(app.packageName, refresh) { app.icon.toBitmap(72, 72).asImageBitmap() }, contentDescription = null, Modifier.size(32.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.name); Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    Text("category=${app.category} · ${if (app.system) "系统" else "第三方"}${if (app.packageName !in safe) " · 受保护" else ""}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    private fun time(at: Long) = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(at))
}
