package io.lifeos.focuslab

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun FocusHome(activity: Activity, store: LabStore, active: Boolean, pulse: Int, onLab: () -> Unit) {
    var apps by remember { mutableStateOf<List<LabApp>>(emptyList()) }
    var selected by remember { mutableStateOf(store.whitelist) }
    var editing by remember { mutableStateOf(false) }
    val rules = remember(activity) { AppRules(activity) }
    var rulesRevision by remember { mutableIntStateOf(0) }
    var exitAt by remember { mutableLongStateOf(0) }
    var minutes by remember { mutableStateOf("25") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(refreshing, active) {
        if (!active) exitAt = 0
        try { apps = withContext(Dispatchers.IO) { AppCatalog(activity).apps() }; rules.refresh(apps); selected = store.whitelist; rulesRevision++ }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message = "暂时无法读取应用列表，请刷新重试" }
    }
    val remaining = remember(pulse) { store.remainingMillis() }
    val exitWait = remember(pulse, exitAt) { (exitAt - SystemClock.elapsedRealtime()).coerceAtLeast(0) }
    if (active && exitAt > 0) AlertDialog(
        onDismissRequest = { exitAt = 0 },
        title = { Text("要结束这次专注吗？") },
        text = { Text(if (exitWait > 0) "先等 ${(exitWait + 999) / 1000} 秒，再决定是否结束。计时仍在继续。" else "如果只是想刷一会儿，也可以回去继续这段专注。") },
        confirmButton = { TextButton(enabled = exitWait == 0L, onClick = {
            if (exitAt > 0 && SystemClock.elapsedRealtime() >= exitAt) { store.stop("WHITELIST_CONFIRMED_EXIT"); exitAt = 0 }
        }) { Text("确认结束") } },
        dismissButton = { TextButton(onClick = { exitAt = 0 }) { Text("继续专注") } }
    )
    fun safety(action: String) {
        try { activity.startActivity(Intent(action)); FocusAccessibilityService.live?.clearOverlay("已打开必要系统功能") }
        catch (_: Exception) { message = "请从系统桌面打开此功能" }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("LifeOS", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (!active) TextButton(onClick = onLab) { Text("系统测试") }
            else Text("专注中", color = MaterialTheme.colorScheme.primary)
        }
        IntegrationHooks.Status(activity, pulse)
        if (active) {
            Text("留一点时间，做好眼前的事。", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(countdownText(remaining), fontSize = 64.sp, fontWeight = FontWeight.Light)
                Text("剩余专注时间", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            Text("白名单应用", style = MaterialTheme.typography.titleMedium)
            Text("用完后，可点通知返回这个计时页。", style = MaterialTheme.typography.bodySmall)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val allowed = apps.filter { it.packageName in selected }
                if (allowed.isEmpty()) Text("这次专注没有添加应用入口。必要通话仍可使用。")
                allowed.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { app ->
                            OutlinedButton(onClick = { if (!FocusActions.openAllowed(activity, app.packageName)) message = "暂时无法打开这个应用" }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(remember(app.packageName) { app.icon.toBitmap(80, 80).asImageBitmap() }, null, Modifier.size(40.dp))
                                    Spacer(Modifier.height(6.dp)); Text(app.name, maxLines = 2)
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { safety(Intent.ACTION_DIAL) }) { Text("电话") }
            }
            OutlinedButton(onClick = { exitAt = SystemClock.elapsedRealtime() + 10_000 }, modifier = Modifier.fillMaxWidth()) { Text("申请结束专注") }
            TextButton(onClick = { store.stop("WHITELIST_EMERGENCY_STOP"); exitAt = 0; message = "已应急结束" }, modifier = Modifier.fillMaxWidth()) { Text("遇到误拦截？应急解除") }
        } else if (editing) {
            Text("应用分类与白名单", style = MaterialTheme.typography.titleLarge)
            Text("单个应用的选择优先于整类开关。系统应用当前仍保护放行。", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { refreshing++ }) { Text("刷新应用列表") }
            CategoryPicker(apps, rules, selected, rulesRevision) { selected = store.whitelist; rulesRevision++ }
            Button(onClick = { editing = false }, modifier = Modifier.fillMaxWidth()) { Text("完成，已选 ${selected.size} 个") }
        } else {
            Text("给下一段时间，\n留一个清楚的边界。", fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium)
            Text("开始后进入计时页。白名单应用正常使用，其他第三方应用会显示计时画面。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(minutes, { minutes = it }, label = { Text("专注几分钟（1–30）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 15, 25, 30).forEach { value -> FilterChip(selected = minutes == value.toString(), onClick = { minutes = value.toString() }, label = { Text("$value 分") }) }
            }
            OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("应用分类与白名单 · 已选 ${selected.size} 个") }
            Text("应用识别：${if (FocusAccessibilityService.live != null) "已连接" else "需要开启"}  ·  前台识别：${if (usageGranted(activity)) "已允许" else "需要允许"}", style = MaterialTheme.typography.bodySmall)
            if (FocusAccessibilityService.live == null) TextButton(onClick = { safety(Settings.ACTION_ACCESSIBILITY_SETTINGS) }) { Text("开启应用识别服务") }
            if (!usageGranted(activity)) TextButton(onClick = { safety(Settings.ACTION_USAGE_ACCESS_SETTINGS) }) { Text("允许前台应用识别") }
            Spacer(Modifier.weight(1f))
            if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
            Text("开始后，仅在本次专注期间使用前台应用元数据判断白名单，不保存使用轨迹。随时可以应急结束。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("预览版暂不限制系统应用，电话和系统设置始终可用。", style = MaterialTheme.typography.bodySmall)
            Button(enabled = !loading, onClick = {
                val value = minutes.toLongOrNull()
                if (value == null || value !in 1..30) { message = "请填 1–30 之间的分钟数" }
                else scope.launch {
                    loading = true
                    try {
                        val prepared = withContext(Dispatchers.IO) {
                            val catalog = AppCatalog(activity)
                            val list = catalog.apps()
                            list to list.filterNot { catalog.protected(it.packageName) }.map { it.packageName }.toSet()
                        }
                        store.startWhitelist(value * 60_000, prepared.second, prepared.first)
                        editing = false; message = ""
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: IllegalArgumentException) { message = error.message ?: "请先完成权限设置" }
                    catch (_: Exception) { store.stop("WHITELIST_START_FAILED"); message = "无法安全开始，请检查应用识别权限后重试" }
                    finally { loading = false }
                }
            }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) { Text(if (loading) "正在准备…" else "开始专注") }
            Text("${BuildConfig.VERSION_NAME} · ${BuildConfig.FLAVOR} · 本地计划到点执行", style = MaterialTheme.typography.labelSmall)
        }
    }
}
