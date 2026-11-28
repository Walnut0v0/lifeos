package io.lifeos.focuslab

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun FocusHome(activity: Activity, store: LabStore, active: Boolean, pulse: Int, launcherRequest: Int, onLab: () -> Unit) {
    var apps by remember { mutableStateOf<List<LabApp>>(emptyList()) }
    var selected by remember { mutableStateOf(store.whitelist) }
    var editing by remember { mutableStateOf(false) }
    var launcher by remember { mutableStateOf(false) }
    val rules = remember(activity) { AppRules(activity) }
    var rulesRevision by remember { mutableIntStateOf(0) }
    var exitAt by remember { mutableLongStateOf(0) }
    var minutes by remember { mutableStateOf("25") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableIntStateOf(0) }
    var permissionRequest by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(launcherRequest) { if (launcherRequest > 0) launcher = true }
    LaunchedEffect(refreshing, active) {
        if (!active) exitAt = 0
        try {
            apps = withContext(Dispatchers.IO) { AppCatalog(activity).apps().also { rules.refresh(it) } }
            selected = store.whitelist; rulesRevision++
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message = "暂时无法读取应用列表，请刷新重试" }
    }
    PermissionPrompts(activity, pulse, permissionRequest)
    if (launcher) AllowedApps(activity, apps, selected) { launcher = false }
    val remaining = remember(pulse) { store.remainingMillis() }
    val session = remember(pulse) { store.session() }
    val exitWait = remember(pulse, exitAt) { (exitAt - SystemClock.elapsedRealtime()).coerceAtLeast(0) }
    if (active && exitAt > 0) AlertDialog(onDismissRequest = { exitAt = 0 }, title = { Text("结束这次专注？") },
        text = { Text(if (exitWait > 0) "请等 ${(exitWait + 999) / 1000} 秒再确认。这段专注仍在继续。" else "确认后，本段将解除。你也可以回去继续。") },
        confirmButton = { TextButton(enabled = exitWait == 0L, onClick = {
            if (exitAt > 0 && SystemClock.elapsedRealtime() >= exitAt) { store.stop("WHITELIST_CONFIRMED_EXIT"); exitAt = 0 }
        }) { Text("确认结束") } }, dismissButton = { TextButton(onClick = { exitAt = 0 }) { Text("继续专注") } })
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("LifeOS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = FocusInk)
                Text(if (active) "把注意力留给眼前" else "让今天的时间，看得见", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!active && !editing) TextButton(onClick = onLab) { Text("测试") }
            else if (active) SuggestionChip(onClick = {}, label = { Text("专注中") })
        }
        if (editing && !active) {
            Text("应用分类与白名单", style = MaterialTheme.typography.titleLarge)
            Text("单个应用选择优先于分类开关；系统应用保持放行。", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { refreshing++ }) { Text("刷新应用列表") }
            CategoryPicker(apps, rules, selected, rulesRevision) { selected = store.whitelist; rulesRevision++ }
            Button(onClick = { editing = false }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Text("完成 · 已选 ${selected.size} 个") }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (active && session != null) {
                    FocusClock(session, remaining)
                    Button(onClick = { launcher = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(18.dp)) { Text("打开白名单应用", style = MaterialTheme.typography.titleMedium) }
                    Text("应用用完后，可从通知回到这里。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IntegrationHooks.Status(activity, pulse)
                if (!active) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("开始一段专注", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text("临时专注，最长 30 分钟。在线计划到点自动进入。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(minutes, { minutes = it.take(3) }, label = { Text("时长（分钟）") }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(5, 15, 25, 30).forEach { value ->
                                FilterChip(selected = minutes == value.toString(), onClick = { minutes = value.toString() }, label = { Text("$value 分") })
                            } }
                            OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("分类与白名单 · ${selected.size} 个") }
                            Button(enabled = !loading, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), onClick = {
                                val value = minutes.toLongOrNull()
                                if (value == null || value !in 1..30) message = "请填 1–30 之间的分钟数"
                                else if (FocusAccessibilityService.live == null || !usageGranted(activity)) { permissionRequest++; message = "完成应用识别授权后，就可以开始。" }
                                else scope.launch {
                                    loading = true
                                    try {
                                        val prepared = withContext(Dispatchers.IO) {
                                            val catalog = AppCatalog(activity); val list = catalog.apps()
                                            list to list.filterNot { catalog.protected(it.packageName) }.map { it.packageName }.toSet()
                                        }
                                        store.startWhitelist(value * 60_000, prepared.second, prepared.first)
                                        editing = false; message = ""
                                    } catch (cancelled: CancellationException) { throw cancelled }
                                    catch (error: IllegalArgumentException) { message = error.message ?: "请完成权限设置"; permissionRequest++ }
                                    catch (_: Exception) { store.stop("WHITELIST_START_FAILED"); message = "暂时不能安全开始，请检查应用识别权限" }
                                    finally { loading = false }
                                }
                            }) { Text(if (loading) "正在准备…" else "开始专注") }
                        }
                    }
                    TextButton(onClick = { permissionRequest++ }, modifier = Modifier.fillMaxWidth()) { Text(FocusPermissions.accessibilityLabel(activity) + " · 检查权限", style = MaterialTheme.typography.labelMedium) }
                    Text("${BuildConfig.VERSION_NAME} · ${BuildConfig.FLAVOR}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }
            if (active) {
                HorizontalDivider(color = FocusMint)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        try { activity.startActivity(Intent(Intent.ACTION_DIAL)); FocusAccessibilityService.live?.clearOverlay("已打开必要通话功能") }
                        catch (_: Exception) { message = "请从系统桌面打开电话" }
                    }) { Text("电话") }
                    TextButton(onClick = { exitAt = SystemClock.elapsedRealtime() + 10_000 }) { Text("申请结束") }
                    TextButton(onClick = { store.stop("WHITELIST_EMERGENCY_STOP"); exitAt = 0 }) { Text("应急解除", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
