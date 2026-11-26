package io.lifeos.focuslab

import android.app.*
import android.app.admin.DevicePolicyManager
import android.app.usage.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.OffsetDateTime

class CapabilityExperiments(private val activity: Activity) {
    var stopVersion = 0
        private set
    private val handler = Handler(Looper.getMainLooper())
    private var view: android.view.View? = null
    fun removeOverlay() {
        view?.let { try { activity.getSystemService(WindowManager::class.java).removeViewImmediate(it) } catch (_: Exception) { } }
        view = null
    }
    fun stop() {
        stopVersion++
        handler.removeCallbacksAndMessages(null); removeOverlay()
        try { activity.stopLockTask() } catch (_: Exception) { }
        activity.stopService(Intent(activity, ShortTestService::class.java))
    }
    fun ordinaryOverlay() {
        check(Settings.canDrawOverlays(activity))
        removeOverlay()
        val label = TextView(activity).apply { text = "普通悬浮窗实验 · 10秒自动移除"; setTextColor(Color.BLACK); setBackgroundColor(Color.YELLOW); setPadding(16, 12, 16, 12) }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; params.y = 120
        activity.getSystemService(WindowManager::class.java).addView(label, params); view = label
        handler.postDelayed({ removeOverlay() }, 10_000)
    }
    fun pin() {
        check(!activity.getSystemService(DevicePolicyManager::class.java).isLockTaskPermitted(activity.packageName)) { "已是受管白名单，拒绝启动高级锁定实验" }
        handler.postDelayed({ try { activity.stopLockTask() } catch (_: Exception) { } }, 60_000)
        activity.startLockTask() // Normal system confirmation; no Device Owner or DPC configuration.
    }
}

fun usageGranted(context: android.content.Context): Boolean = context.getSystemService(AppOpsManager::class.java)
    .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

private fun usageSample(activity: Activity): String {
    check(usageGranted(activity)) { "尚未授权使用情况访问" }
    val manager = activity.getSystemService(UsageStatsManager::class.java)
    val end = System.currentTimeMillis(); val begin = end - 60_000
    val stream = manager.queryEvents(begin, end)
    val event = UsageEvents.Event(); val lines = mutableListOf<String>()
    while (stream.hasNextEvent()) {
        stream.getNextEvent(event)
        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED || event.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
            // Only package/time/type metadata; no class names or content, never persisted or exported.
            if (lines.size < 40) lines.add("${event.timeStamp - begin}ms ${event.packageName} type=${event.eventType}")
        }
    }
    val aggregate = manager.queryAndAggregateUsageStats(begin, end)
    return "最近60秒事件样本（最多40条，仅内存）：\n${lines.joinToString("\n")}\n聚合时长（系统分桶可能超出查询边界，不等于精确60秒）：\n" +
        aggregate.values.take(20).joinToString("\n") { "${it.packageName}: ${it.totalTimeInForeground}ms" }
}

object SamplePlans {
    const val JSON = """{"schema_version":1,"tasks":[{"start_at":"2026-12-01T23:59:00+08:00","end_at":"2026-12-02T00:01:00+08:00"},{"start_at":"2026-12-02T00:01:00+08:00","end_at":"2026-12-02T00:02:00+08:00"}]}"""
    fun inspect(raw: String): String {
        require(raw.length <= 4096)
        val root = JSONObject(raw); require(root.getInt("schema_version") == 1)
        val tasks = root.getJSONArray("tasks"); require(tasks.length() in 1..10)
        var previous = Long.MIN_VALUE
        val result = mutableListOf<String>()
        for (i in 0 until tasks.length()) {
            val task = tasks.getJSONObject(i)
            val start = OffsetDateTime.parse(task.getString("start_at")).toInstant().toEpochMilli()
            val end = OffsetDateTime.parse(task.getString("end_at")).toInstant().toEpochMilli()
            require(end > start && end - start <= FocusPolicy.MAX_DURATION && start >= previous)
            previous = end
            result.add("模拟任务${i + 1}：跨时区绝对时间 $start → $end；开始前=${FocusPolicy.phase(Session(start,end),start-1,0,0)}；开始=${FocusPolicy.phase(Session(start,end),start,0,0)}；结束=${FocusPolicy.phase(Session(start,end),end,0,0)}")
        }
        return result.joinToString("\n") + "\n仅验证模拟 JSON、连续边界与跨日，不注册真实计划。"
    }
}

@Composable fun CapabilityPanel(activity: Activity, store: LabStore, experiments: CapabilityExperiments, pulse: Int, export: (String) -> Unit) {
    var output by remember { mutableStateOf("") }
    var usageRequested by remember { mutableIntStateOf(0) }
    var sample by remember { mutableStateOf("") }
    var delayEnd by remember { mutableLongStateOf(0) }
    var observedStop by remember { mutableIntStateOf(experiments.stopVersion) }
    LaunchedEffect(pulse) {
        if (observedStop != experiments.stopVersion) {
            observedStop = experiments.stopVersion; delayEnd = 0; output = ""
        }
        if (delayEnd > 0 && SystemClock.elapsedRealtime() >= delayEnd) delayEnd = 0
    }
    val now = remember(pulse) { SystemClock.elapsedRealtime() }
    LaunchedEffect(usageRequested) {
        if (usageRequested > 0) {
            output = "正在读取本地元数据…"
            output = try { withContext(Dispatchers.IO) { usageSample(activity) } } catch (_: Exception) { "无权限或系统未返回数据；已放行" }
            delay(60_000); output = "使用统计内存样本已到期清除"
        }
    }
    fun open(action: String, own: Boolean = false) {
        experiments.removeOverlay()
        try { activity.startActivity(Intent(action).apply { if (own) data = Uri.parse("package:${activity.packageName}") }) }
        catch (_: Exception) { output = "系统未提供该设置入口，请手动打开" }
    }
    Text("T09–T12：候选能力实验", style = MaterialTheme.typography.titleMedium)
    Text("检查时间：${java.util.Date()}\n使用情况访问（AppOps）：${usageGranted(activity)}\n普通悬浮窗（公开API）：${Settings.canDrawOverlays(activity)}\n前台服务运行：${ShortTestService.running}\n屏幕固定状态：${activity.getSystemService(ActivityManager::class.java).lockTaskModeState}\n本应用Device Owner：${activity.getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(activity.packageName)}\n设备支持管理功能：${activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_DEVICE_ADMIN)}")
    Text("厂商自启动、关联启动、后台活动、卡片加锁、后台弹出：需手动确认。不能通过公共 API 可靠判断。")
    listOf("荣耀自启动", "荣耀关联启动", "荣耀后台活动", "多任务卡片加锁", "厂商后台弹出").forEach { key ->
        Row { Text(key, Modifier.weight(1f)); TextButton(onClick = { store.result(key, "USER_CONFIRMED_ON"); output = "$key：已记下手动确认开启" }) { Text("开") }; TextButton(onClick = { store.result(key, "USER_CONFIRMED_OFF"); output = "$key：已记下手动确认关闭" }) { Text("关") }; TextButton(onClick = { store.result(key, "UNKNOWN"); output = "$key：未知" }) { Text("未知") } }
    }
    Button(onClick = { open(Settings.ACTION_USAGE_ACCESS_SETTINGS) }) { Text("独立授权使用情况访问（可撤销）") }
    Button(onClick = { usageRequested++ }) { Text("仅本次读取最近60秒使用元数据") }
    Button(onClick = { output = "" }) { Text("立即清除内存样本") }
    Button(onClick = { open(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true) }) { Text("独立授权普通悬浮窗（可撤销）") }
    Button(onClick = { try { experiments.ordinaryOverlay(); output = "10秒普通悬浮窗，仅Lab前台；切后台立即移除" } catch (_: Exception) { output = "无法显示：请先授权，未启用任何拦截" } }) { Text("普通悬浮窗对比：10秒") }
    Button(onClick = { try { activity.startForegroundService(Intent(activity, ShortTestService::class.java)); output = "请求60秒前台服务；观察通知及后台表现" } catch (_: Exception) { output = "系统拒绝前台服务启动" } }) { Text("从可见界面启动60秒前台服务") }
    Text("前台服务使用 shortService（Android14+），最多60秒，不用于永久保活。通知可立即停止；系统任务管理器亦可停止。")
    Button(onClick = { try { experiments.pin(); output = "请按系统提示确认屏幕固定；最多60秒，顶部停止按钮可解除" } catch (_: Exception) { output = "固定不可用或设备已有受管配置；未更改管理角色" } }) { Text("短时屏幕固定：系统确认，60秒解除") }
    Text("仅固定 Lab，不是多应用白名单。先阅读系统退出提示：三键/手势退出方式以手机说明为准。企业 Lock Task 的 Home/Overview/通知配置需要 DPC/受管白名单；本 APK 不配置 Device Owner、不申请管理角色。")
    Button(onClick = { delayEnd = SystemClock.elapsedRealtime() + 60_000; output = "独立60秒退出摩擦演示已开始（不限制其他应用）" }) { Text("开始60秒可逆退出确认演示") }
    if (delayEnd > now) {
        Text("模拟会话剩余 ${(delayEnd - now) / 1000}秒，普通退出需等待5秒确认；应急解除立即有效。")
        var confirmAt by remember { mutableLongStateOf(0) }
        TextButton(onClick = { confirmAt = SystemClock.elapsedRealtime() + 5_000 }) { Text("申请普通退出") }
        if (confirmAt > 0) TextButton(enabled = now >= confirmAt, onClick = { delayEnd = 0; output = "演示已退出" }) { Text(if (now >= confirmAt) "确认退出" else "等待确认") }
        TextButton(onClick = { delayEnd = 0; store.stop(); output = "立即应急解除" }) { Text("应急立即退出") }
    }
    Text("设置识别仅记录 SETTINGS_WINDOW_OBSERVED 代码，无法区分设置子页面时全部放行。多任务与Home包名可能相同，不封锁桌面/SystemUI。请按测试矩阵手动测手势/三键、划卡、一键清理、分屏/小窗、强停、撤权和卸载。")
    Text("T11：依次对照默认、基础权限、基础+荣耀手动设置三组。每组后台5min、锁屏30min、重启/首次解锁；完整配置追加2h/夜间、省电/低电量/飞行模式。用计时页未来时段及日志核对广播/状态/动作，不把最近任务卡片存在当作保活成功。")
    OutlinedTextField(sample, { if (it.length <= 4096) sample = it }, label = { Text("T12 模拟 JSON（最多4KB）") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = { sample = SamplePlans.JSON }) { Text("载入跨日连续时段模拟数据") }
    Button(onClick = { output = try { SamplePlans.inspect(sample) } catch (_: Exception) { "模拟JSON无效：须含显式时区、无重叠、单段≤30min、1–10段" } }) { Text("导入文本并验证模拟时段") }
    Button(onClick = { try { SamplePlans.inspect(sample); export(sample) } catch (_: Exception) { output = "先载入有效模拟数据" } }) { Text("导出模拟 JSON") }
    Text("$output\n这些样本只在此页内存显示，离开页面即丢弃；不会写入诊断导出。")
}
