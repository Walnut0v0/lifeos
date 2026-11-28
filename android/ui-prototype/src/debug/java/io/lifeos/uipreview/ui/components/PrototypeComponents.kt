package io.lifeos.uipreview.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lifeos.uipreview.ui.screens.*

@Composable fun DemoClock(style: DesignStyle, state: PrototypeState) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.primaryContainer
    val size = if (style == DesignStyle.MONO) 220.dp else 248.dp
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).semantics { contentDescription = "演示专注时段09:00至10:30，进度约39%" }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                val stroke = Stroke(if (style == DesignStyle.MONO) 3.dp.toPx() else if (style == DesignStyle.MATERIAL) 14.dp.toPx() else 10.dp.toPx(), cap = StrokeCap.Round)
                if (style == DesignStyle.MATERIAL) {
                    repeat(24) { segment ->
                        drawArc(track, -90f + segment * 15f, 8f, false, style = stroke)
                        val filled = (state.progress * 360f - segment * 15f).coerceIn(0f, 8f)
                        if (filled > 0) drawArc(primary, -90f + segment * 15f, filled, false, style = stroke)
                    }
                } else {
                    drawArc(track, -90f, 360f, false, style = stroke)
                    drawArc(primary, -90f, state.progress * 360f, false, style = stroke)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("阅读与笔记", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("09:00", fontSize = 36.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = primary)
                Text("— 10:30", fontSize = 20.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable fun DemoTimeline(state: PrototypeState, style: DesignStyle) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("今日安排", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("${state.tasks.size} 段 · 演示", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(if (style == DesignStyle.MONO) 10.dp else 22.dp)) {
            drawRoundRect(colors.primaryContainer, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
            (if (state.tasks.isEmpty()) emptyList() else listOf(.375f to .4375f, .5833f to .625f)).forEach { (start, end) ->
                drawRect(colors.primary, Offset(start * size.width, 0f), Size((end - start) * size.width, size.height))
            }
            drawLine(colors.error, Offset(.3993f * size.width, -3.dp.toPx()), Offset(.3993f * size.width, size.height + 3.dp.toPx()), 2.dp.toPx())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("00", "06", "12", "18", "24").forEach { Text(it, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = colors.onSurfaceVariant) } }
        if (state.tasks.isEmpty()) Text("暂无日程。连接计划源后，这里会显示任务。", color = colors.onSurfaceVariant)
        state.tasks.forEachIndexed { index, task ->
            val current = state.active && index == 0
            val rounded = if (style == DesignStyle.MONO) 0.dp else if (style == DesignStyle.MATERIAL) 24.dp else 14.dp
            Row(Modifier.fillMaxWidth().background(if (current) colors.primaryContainer else colors.surface, RoundedCornerShape(rounded)).padding(vertical = 12.dp, horizontal = if (style == DesignStyle.MONO) 0.dp else 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(task.time.substringBefore('–'), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                Column(Modifier.weight(1f)) {
                    Text(task.title, fontWeight = FontWeight.Medium)
                    Text(task.time, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = colors.onSurfaceVariant)
                }
                Text(if (current) "进行中" else if (task.focus) "专注" else "自由", style = MaterialTheme.typography.labelSmall, color = colors.primary)
            }
            if (style == DesignStyle.MONO) HorizontalDivider()
        }
    }
}

@Composable fun DemoStatus(state: PrototypeState, action: () -> Unit) {
    val warning = state.scenario in setOf(DemoScenario.EXPIRED, DemoScenario.DISCONNECTED, DemoScenario.PERMISSION)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(state.status, style = MaterialTheme.typography.bodyMedium, color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        if (state.scenario == DemoScenario.PERMISSION) OutlinedButton(onClick = action) { Text("查看授权说明（演示）") }
        if (state.scenario == DemoScenario.SYNC_FAILED) Text("缓存有效至明日 00:00 · 最近同步 08:40（虚构）", style = MaterialTheme.typography.bodySmall)
    }
}
