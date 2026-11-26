package io.lifeos.focuslab

import android.app.Activity
import android.content.Intent
import android.provider.Settings
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
    var search by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("25") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(refreshing, active) {
        try { apps = withContext(Dispatchers.IO) { AppCatalog(activity).apps() }; selected = store.whitelist }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message = "暂时无法读取应用列表，请刷新重试" }
    }
    val remaining = remember(pulse) { store.remainingMillis() }
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
                if (allowed.isEmpty()) Text("这次专注没有添加应用入口。电话和系统设置仍可使用。")
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
                TextButton(onClick = { safety(Settings.ACTION_SETTINGS) }) { Text("系统设置") }
            }
            OutlinedButton(onClick = { store.stop("WHITELIST_USER_STOP"); message = "本次专注已结束" }, modifier = Modifier.fillMaxWidth()) { Text("结束专注 / 应急解除") }
        } else if (editing) {
            Text("专注时，你需要哪些应用？", style = MaterialTheme.typography.titleLarge)
            Text("勾选后会出现在计时页。电话、设置等必要系统功能始终可用。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(search, { search = it }, label = { Text("搜索应用") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { refreshing++ }) { Text("刷新应用列表") }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                apps.filter { it.name.contains(search, true) || it.packageName.contains(search, true) }.forEach { app ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(app.packageName in selected, onCheckedChange = { checked ->
                            selected = if (checked) selected + app.packageName else selected - app.packageName
                            store.whitelist = selected
                        })
                        Image(remember(app.packageName) { app.icon.toBitmap(64, 64).asImageBitmap() }, null, Modifier.size(32.dp))
                        Text(app.name, Modifier.weight(1f).padding(start = 12.dp))
                    }
                }
            }
            Button(onClick = { editing = false }, modifier = Modifier.fillMaxWidth()) { Text("完成，已选 ${selected.size} 个") }
        } else {
            Text("给下一段时间，\n留一个清楚的边界。", fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium)
            Text("开始后进入计时页。白名单应用正常使用，其他第三方应用会显示计时画面。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(minutes, { minutes = it }, label = { Text("专注几分钟（1–30）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5, 15, 25, 30).forEach { value -> FilterChip(selected = minutes == value.toString(), onClick = { minutes = value.toString() }, label = { Text("$value 分") }) }
            }
            OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("选择白名单应用 · 已选 ${selected.size} 个") }
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
                        val managed = withContext(Dispatchers.IO) {
                            val catalog = AppCatalog(activity)
                            catalog.apps().filterNot { catalog.protected(it.packageName) }.map { it.packageName }.toSet()
                        }
                        store.startWhitelist(value * 60_000, managed)
                        editing = false; message = ""
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: IllegalArgumentException) { message = error.message ?: "请先完成权限设置" }
                    catch (_: Exception) { store.stop("WHITELIST_START_FAILED"); message = "无法安全开始，请检查应用识别权限后重试" }
                    finally { loading = false }
                }
            }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) { Text(if (loading) "正在准备…" else "开始专注") }
            Text("v0.2.0 · 白名单预览版 · 离线执行", style = MaterialTheme.typography.labelSmall)
        }
    }
}
