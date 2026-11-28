package io.lifeos.focuslab

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import java.time.Instant
import java.time.ZoneId

val FocusInk = Color(0xFF234B46)
val FocusMint = Color(0xFFDBEBE4)

@Composable fun LifeOsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = FocusInk, onPrimary = Color.White, primaryContainer = FocusMint,
        background = Color(0xFFF5F7F2), surface = Color(0xFFF5F7F2),
        onSurface = Color(0xFF213B36), onSurfaceVariant = Color(0xFF596C66)
    ), content = content)
}

@Composable fun FocusClock(session: Session, remaining: Long) {
    val progress = FocusPresentation.progress(session.duration, remaining)
    val range = FocusPresentation.range(session.start, session.end)
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(254.dp).semantics { contentDescription = "专注时段 $range，已进行 ${(progress * 100).toInt()}%" }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                val stroke = Stroke(10.dp.toPx(), cap = StrokeCap.Round)
                drawArc(FocusMint, -90f, 360f, false, style = stroke)
                drawArc(FocusInk, -90f, progress * 360f, false, style = stroke)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("这一段 · 专注", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Text(range, fontSize = if (range.length > 12) 21.sp else 28.sp, fontWeight = FontWeight.SemiBold, color = FocusInk)
                Spacer(Modifier.height(8.dp))
                Text("按自己的节奏，继续就好", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable fun TodaySchedule(tasks: List<PlannedTask>, now: Long, valid: Boolean) {
    val today = FocusPresentation.today(tasks, now)
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color.White), elevation = CardDefaults.elevatedCardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("今日安排", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${day.monthValue} 月 ${day.dayOfMonth} 日", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(Modifier.fillMaxWidth().height(24.dp).semantics { contentDescription = "今日时间轴，${today.size} 段安排" }) {
                drawRoundRect(FocusMint.copy(alpha = .5f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
                today.forEach { task ->
                    val left = (task.start.coerceAtLeast(dayStart) - dayStart).toFloat() / (dayEnd - dayStart) * size.width
                    val right = (task.end.coerceAtMost(dayEnd) - dayStart).toFloat() / (dayEnd - dayStart) * size.width
                    drawRect(if (!valid) Color.Gray else if (task.mode == "focus") FocusInk else Color(0xFFCB9C61), Offset(left, 0f), androidx.compose.ui.geometry.Size((right - left).coerceAtLeast(1f), size.height))
                }
                val x = ((now - dayStart).toFloat() / (dayEnd - dayStart)).coerceIn(0f, 1f) * size.width
                drawLine(Color(0xFFBD674B), Offset(x, -3.dp.toPx()), Offset(x, size.height + 3.dp.toPx()), 2.dp.toPx())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("00:00", "06:00", "12:00", "18:00", "24:00").forEach { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            if (!valid) Text("缓存计划已过期，仅供查看，当前不会执行。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (today.isEmpty()) Text("今天还没有同步的安排。可到计划页检查更新。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Column(Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                today.forEach { task ->
                    val current = valid && now >= task.start && now < task.end
                    Row(Modifier.fillMaxWidth().background(if (current) FocusMint else Color(0xFFF6F7F3), RoundedCornerShape(12.dp)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.width(3.dp).height(36.dp).background(if (task.mode == "focus") FocusInk else Color(0xFFCB9C61), RoundedCornerShape(2.dp)))
                        Column(Modifier.weight(1f)) {
                            Text(task.title.ifBlank { if (task.mode == "focus") "专注" else "自由安排" }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(FocusPresentation.range(task.start, task.end), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (current) "进行中" else if (task.end <= now) "已结束" else if (task.mode == "focus") "专注" else if (task.mode == "free") "自由" else "未支持", style = MaterialTheme.typography.labelSmall, color = FocusInk)
                    }
                }
            }
        }
    }
}

@Composable fun AllowedApps(activity: Activity, apps: List<LabApp>, selected: Set<String>, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val camera = remember(apps) { FocusActions.cameraPackage(activity) }
    val ordered = remember(apps, selected, camera) {
        AppDisplayOrder.ordered(apps.filter { it.packageName in selected || it.packageName == camera }, { it.name }, { it.packageName })
    }
    val filtered = remember(ordered, query) { ordered.filter { it.name.contains(query.trim(), ignoreCase = true) } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F7F2)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("可用应用", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("${ordered.size} 个入口 · 返回即可继续专注", style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = onClose) { Text("返回") }
                }
                OutlinedTextField(query, { query = it.take(80) }, placeholder = { Text("搜索应用名称") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
                Text("常用优先，其余按名称拼音排序", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                if (filtered.isEmpty()) Text(if (query.isBlank()) "还没有可用应用，请在专注结束后添加白名单。" else "没有找到这个应用。", modifier = Modifier.padding(vertical = 24.dp))
                LazyVerticalGrid(columns = GridCells.Adaptive(88.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Column(Modifier.fillMaxWidth().clickable {
                            if (FocusActions.openAllowed(activity, app.packageName)) onClose() else error = "暂时无法打开，请重试"
                        }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                                Image(remember(app.packageName) { app.icon.toBitmap(120, 120).asImageBitmap() }, contentDescription = "打开${app.name}", modifier = Modifier.padding(12.dp).size(48.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(app.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.heightIn(min = 34.dp))
                        }
                    }
                }
                TextButton(onClick = { LabStore(activity).stop("WHITELIST_EMERGENCY_STOP"); onClose() }, modifier = Modifier.fillMaxWidth()) { Text("遇到误拦截？应急解除") }
            }
        }
    }
}
