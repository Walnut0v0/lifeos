package io.lifeos.uipreview

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.lifeos.uipreview.ui.components.*
import io.lifeos.uipreview.ui.screens.*
import io.lifeos.uipreview.ui.theme.PrototypeTheme
import kotlinx.coroutines.delay
import androidx.core.view.WindowCompat

/** Isolated debug-only design playground. No production services, stores or permissions. */
class PrototypeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Gallery() }
    }
}

@Composable private fun Gallery() {
    var styleIndex by rememberSaveable { mutableIntStateOf(0) }
    var scenarioIndex by rememberSaveable { mutableIntStateOf(0) }
    var dark by rememberSaveable { mutableStateOf(false) }
    var fontIndex by rememberSaveable { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var exitAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val style = DesignStyle.entries[styleIndex]
    val state = PrototypeState(DemoScenario.entries[scenarioIndex])
    val density = LocalDensity.current
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? ComponentActivity
        activity?.let {
            WindowCompat.getInsetsController(it.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    LaunchedEffect(exitAt) { while (exitAt > 0) { now = SystemClock.elapsedRealtime(); delay(200) } }
    fun emergency() { exitAt = 0; apps = false; scenarioIndex = DemoScenario.FREE.ordinal; notice = "演示已立即解除。真实 LifeOS 没有变化。" }
    BackHandler(apps || exitAt > 0 || page != 0) { apps = false; exitAt = 0; page = 0 }
    PrototypeTheme(style, dark) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("LifeOS · 设计对照 / 全部为虚构数据", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DesignStyle.entries.forEachIndexed { index, design -> FilterChip(styleIndex == index, { styleIndex = index }, label = { Text(design.label) }) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            TextButton(onClick = { picker = true }) { Text("状态：${state.scenario.label} ▾") }
                            DropdownMenu(picker, { picker = false }) { DemoScenario.entries.forEachIndexed { index, scenario -> DropdownMenuItem(text = { Text(scenario.label) }, onClick = { scenarioIndex = index; picker = false; exitAt = 0 }) } }
                        }
                        TextButton(onClick = { dark = !dark }) { Text(if (dark) "深色" else "浅色") }
                        TextButton(onClick = { fontIndex = (fontIndex + 1) % 3 }) { Text(listOf("字号 1×", "1.3×", "1.6×")[fontIndex]) }
                    }
                }
                CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * listOf(1f, 1.3f, 1.6f)[fontIndex])) {
                    if (state.active) {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Text("正在专注", style = MaterialTheme.typography.headlineMedium, fontWeight = if (style == DesignStyle.MONO) FontWeight.Bold else FontWeight.Medium)
                            DemoStatus(state) { notice = "这是授权说明演示，不会打开系统设置。" }
                            DemoClock(style, state)
                            Button(onClick = { apps = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("打开白名单应用") }
                            Text("按计划继续，不需要反复看时间。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { notice = "正式版从这里申请调整计划；预览不会打开、填写或发送ChatGPT对话。" }) { Text("申请调整计划") }
                            OutlinedButton(onClick = { now = SystemClock.elapsedRealtime(); exitAt = now + 10000 }, modifier = Modifier.fillMaxWidth()) { Text("申请结束专注") }
                        }
                        // Emergency action remains outside scrolling content and the ordinary exit dialog.
                        SafetyBar(onPhone = { notice = "电话入口演示，不会拨号。" }, onEmergency = { emergency() })
                    } else {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("12 月 1 日 · 星期二", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(when (style) { DesignStyle.NATURE -> "今天，慢慢做好。"; DesignStyle.MONO -> "今日 / 计划"; DesignStyle.MATERIAL -> "给今天一点节奏" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { notice = "正式版这里进入连接、隐私与权限设置。" }) { Text("设置") }
                            }
                            if (page == 0 || page == 1) {
                                if (page == 0) Overview(style, state, onFocus = { scenarioIndex = DemoScenario.FOCUS.ordinal }, onNotice = { notice = it })
                                DemoTimeline(state, style)
                            } else if (page == 2) {
                                Text("今日使用 · 演示", style = MaterialTheme.typography.headlineSmall)
                                Text(if (state.hasStatistics) "屏幕交互 1 小时 36 分 · 覆盖不完整" else "暂无统计记录", style = MaterialTheme.typography.titleLarge)
                                Text("预览只展示虚构汇总；正式版未知指标留空，不把手机使用时长当作学习成果。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick = { notice = "隐私开关演示：不会启用或上传统计。" }) { Text("查看隐私选项") }
                            } else {
                                Text("监管与应用", style = MaterialTheme.typography.headlineSmall)
                                DemoStatus(state) { notice = "需要用户确认授权；预览不申请权限。" }
                                Button(onClick = { apps = true }) { Text("查看白名单应用（虚构）") }
                                Text("分类和单应用例外将在选定风格后迁移，原规则保持。")
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            listOf("首页", "日程", "统计", "监管").forEachIndexed { index, title -> NavigationBarItem(selected = page == index, onClick = { page = index }, icon = { Text(listOf("◷", "▤", "▥", "◇")[index]) }, label = { Text(title, maxLines = 1) }) }
                        }
                    }
                    if (apps) DemoAppPicker(onClose = { apps = false }, onEmergency = { emergency() }, onNotice = { notice = it })
                    if (exitAt > 0) AlertDialog(onDismissRequest = { exitAt = 0 }, title = { Text("结束这段专注？") }, text = { Text(if (now < exitAt) "普通结束还需 ${(exitAt - now + 999) / 1000} 秒；专注仍在继续。" else "可以确认结束，也可以继续。") }, confirmButton = {
                        TextButton(enabled = now >= exitAt, onClick = { if (SystemClock.elapsedRealtime() >= exitAt) { exitAt = 0; scenarioIndex = DemoScenario.FREE.ordinal } }) { Text("确认结束") }
                    }, dismissButton = { Column {
                        TextButton(onClick = { exitAt = 0 }) { Text("继续专注") }
                        TextButton(onClick = { emergency() }) { Text("应急立即解除", color = MaterialTheme.colorScheme.error) }
                    } })
                    if (notice != null) AlertDialog(onDismissRequest = { notice = null }, title = { Text("预览说明") }, text = { Text(notice.orEmpty()) }, confirmButton = { TextButton(onClick = { notice = null }) { Text("知道了") } }, dismissButton = { if (state.active) TextButton(onClick = { emergency() }) { Text("应急解除") } })
                }
            }
        }
    }
}

@Composable private fun Overview(style: DesignStyle, state: PrototypeState, onFocus: () -> Unit, onNotice: (String) -> Unit) {
    val content: @Composable ColumnScope.() -> Unit = {
        DemoStatus(state) { onNotice("真实授权由你确认；预览不打开系统设置。") }
        Text(if (state.scenario == DemoScenario.DISCONNECTED) "还没有同步的安排" else "下一段 · 阅读与笔记", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(if (state.tasks.isEmpty()) "— — : — —" else "09:00–10:30", fontSize = if (style == DesignStyle.MONO) 38.sp else 30.sp, fontFamily = FontFamily.Monospace, fontWeight = if (style == DesignStyle.MONO) FontWeight.Bold else FontWeight.Normal)
        Text("跟着已同步的计划，到点自动开始。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onFocus) { Text("看专注页") }
            TextButton(onClick = { onNotice("演示手动入口，不会开启真实计时。") }) { Text("临时专注") }
        }
    }
    if (style == DesignStyle.MONO) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { HorizontalDivider(); content(); HorizontalDivider() }
    else Card(colors = CardDefaults.cardColors(containerColor = if (style == DesignStyle.MATERIAL) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f))) {
        Column(Modifier.fillMaxWidth().padding(if (style == DesignStyle.MATERIAL) 24.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable private fun SafetyBar(onPhone: () -> Unit, onEmergency: () -> Unit) {
    HorizontalDivider()
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onPhone, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("必要通话") }
        TextButton(onClick = onEmergency, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("应急立即解除", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable private fun DemoAppPicker(onClose: () -> Unit, onEmergency: () -> Unit, onNotice: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("白名单应用", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f)); TextButton(onClick = onClose) { Text("返回") }
                }
                Text("全部为虚构入口，不读取手机应用清单。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(query, { query = it.take(80) }, singleLine = true, placeholder = { Text("搜索应用") }, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
                LazyVerticalGrid(GridCells.Adaptive(90.dp), Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(PrototypeState.allowedApps.filter { it.contains(query.trim(), true) }) { label ->
                        TextButton(onClick = { onNotice("这是“$label”的虚构入口，不会启动真实应用。") }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) { Text(label.take(1), modifier = Modifier.padding(18.dp), fontSize = 24.sp, fontWeight = FontWeight.Medium) }
                                Spacer(Modifier.height(8.dp)); Text(label, maxLines = 2)
                            }
                        }
                    }
                }
                TextButton(onClick = onEmergency, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("应急立即解除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
