package io.lifeos.focuslab

import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Display helpers only. Never used to calculate enforcement deadlines. */
object FocusPresentation {
    fun time(epoch: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(Instant.ofEpochMilli(epoch))

    fun range(start: Long, end: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val crossDay = Instant.ofEpochMilli(start).atZone(zone).toLocalDate() != Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
        return "${time(start, zone)}–${if (crossDay) "次日 " else ""}${time(end, zone)}"
    }

    fun progress(duration: Long, remaining: Long): Float =
        if (duration <= 0) 0f else (1.0 - remaining.coerceIn(0, duration).toDouble() / duration).toFloat()

    fun priority(label: String, packageName: String): Int = when {
        label.trim() in setOf("相机", "Camera") -> 0
        packageName == "com.eg.android.AlipayGphone" || label.trim() == "支付宝" -> 1
        label.trim() in setOf("移动交通大学", "移动交大") -> 2
        label.trim() in setOf("哈罗", "哈啰", "哈啰出行", "哈罗出行") -> 3
        else -> 4
    }

    fun <T> ordered(items: List<T>, name: (T) -> String, pkg: (T) -> String, sortKey: ((String) -> String)? = null): List<T> {
        val collator = Collator.getInstance(Locale.CHINA).apply { strength = Collator.PRIMARY }
        val keys = if (sortKey == null) emptyMap() else items.associateWith { sortKey(name(it)) }
        return items.sortedWith { a, b ->
            val pinned = priority(name(a), pkg(a)).compareTo(priority(name(b), pkg(b)))
            if (pinned != 0) pinned else {
                val label = if (sortKey == null) collator.compare(name(a), name(b)) else keys.getValue(a).compareTo(keys.getValue(b))
                if (label != 0) label else pkg(a).compareTo(pkg(b))
            }
        }
    }

    fun today(tasks: List<PlannedTask>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<PlannedTask> {
        val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return tasks.filter { it.start < end && it.end > start }.sortedBy { it.start }
    }
}
