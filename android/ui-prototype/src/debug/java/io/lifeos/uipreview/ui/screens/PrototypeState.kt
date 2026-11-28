package io.lifeos.uipreview.ui.screens

enum class DesignStyle(val label: String) { NATURE("A 自然简约"), MONO("B 黑白极简"), MATERIAL("C Material") }
enum class DemoScenario(val label: String) {
    IDLE("空闲"), WAITING("等待自动开始"), FOCUS("自动专注中"), FREE("自由时段"),
    EXPIRED("计划过期"), DISCONNECTED("未连接"), SYNC_FAILED("同步失败"), PERMISSION("权限缺失"), EMPTY_STATS("空统计")
}
data class DemoTask(val time: String, val title: String, val focus: Boolean)
data class PrototypeState(val scenario: DemoScenario) {
    val active get() = scenario == DemoScenario.FOCUS
    val status get() = when (scenario) {
        DemoScenario.IDLE -> "自动执行未启用"
        DemoScenario.WAITING -> "自动执行已开启 · 等待 09:00"
        DemoScenario.FOCUS -> "自动计划正在执行 · 识别已连接"
        DemoScenario.FREE -> "当前自由时段 · 下一段 14:00"
        DemoScenario.EXPIRED -> "缓存计划已过期 · 当前放行"
        DemoScenario.DISCONNECTED -> "未连接计划源 · 当前放行"
        DemoScenario.SYNC_FAILED -> "同步失败 · 沿用未过期缓存"
        DemoScenario.PERMISSION -> "应用识别未授权 · 当前放行"
        DemoScenario.EMPTY_STATS -> "统计尚未开启 · 暂无记录"
    }
    val tasks get() = if (scenario == DemoScenario.DISCONNECTED) emptyList() else listOf(
        DemoTask("09:00–10:30", "阅读与笔记", true),
        DemoTask("10:30–11:00", "走动休息", false),
        DemoTask("14:00–15:00", "整理课程资料", true)
    )
    val hasStatistics get() = scenario !in setOf(DemoScenario.EMPTY_STATS, DemoScenario.PERMISSION, DemoScenario.DISCONNECTED)
    val progress get() = if (active) 35f / 90f else 0f
    companion object {
        val allowedApps = listOf("相机", "支付宝", "移动交通大学", "哈啰", "笔记", "词典", "地图", "日历")
    }
}
