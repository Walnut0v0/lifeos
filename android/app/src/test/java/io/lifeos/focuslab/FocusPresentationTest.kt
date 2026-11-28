package io.lifeos.focuslab

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class FocusPresentationTest {
    private val zone = ZoneId.of("Asia/Manila")
    private fun at(value: String) = Instant.parse(value).toEpochMilli()
    @Test fun overnightRangeAndProgressDoNotExtendSession() {
        assertEquals("23:50–次日 00:10", FocusPresentation.range(at("2026-11-28T15:50:00Z"), at("2026-11-28T16:10:00Z"), zone))
        assertEquals(0f, FocusPresentation.progress(100, 120), .0001f)
        assertEquals(.5f, FocusPresentation.progress(100, 50), .0001f)
        assertEquals(1f, FocusPresentation.progress(100, -10), .0001f)
    }
    @Test fun pinnedAppsAndChinesePinyinOrdering() {
        val names = listOf("微信", "百度", "支付宝", "移动交通大学", "哈啰", "相机", "知乎", "爱读书")
        assertEquals(listOf("相机", "支付宝", "移动交通大学", "哈啰", "爱读书", "百度", "微信", "知乎"), FocusPresentation.ordered(names, { it }, { it }))
    }
    @Test fun todayIncludesCrossingTasksButExcludesAdjacentDays() {
        val start = at("2026-11-27T16:00:00Z")
        val tasks = listOf(
            PlannedTask("before", "", start - 1000, start, "focus"),
            PlannedTask("cross", "", start - 1000, start + 1000, "focus"),
            PlannedTask("after", "", start + 86400000, start + 86401000, "focus")
        )
        assertEquals(listOf("cross"), FocusPresentation.today(tasks, start + 5000, zone).map { it.id })
    }
}
