package io.lifeos.focuslab

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock

object ChangeAccess {
    private var until = 0L
    var returning = false
        private set
    fun allows(packageName: String) = packageName == "com.openai.chatgpt" && SystemClock.elapsedRealtime() < until
    fun requestText(context: Context): String {
        val plans = PlanStore(context)
        val current = ScheduleEngine.resolve(plans.load(), System.currentTimeMillis()).task
        return "请帮我调整LifeOS计划（当前版本${plans.load()?.revision ?: 0}）。" +
            (current?.let { "当前任务：${it.title.ifBlank { "专注任务" }}。" } ?: "当前没有专注任务。") +
            "我想申请推迟/延长/提前完成/处理临时事项，请先和我确认，再发布完整的新版本计划。"
    }
    fun copy(context: Context) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("LifeOS计划调整", requestText(context)))
    }
    fun open(activity: Activity): Boolean = try {
        val launch = activity.packageManager.getLaunchIntentForPackage("com.openai.chatgpt") ?: return false
        // Only the explicitly opened planning app gets a bounded exception. No browser-wide pass.
        until = SystemClock.elapsedRealtime() + 5 * 60_000
        returning = true
        PlanStore(activity).prefs.edit().putLong("change_revision", PlanStore(activity).load()?.revision ?: 0).apply()
        activity.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        FocusAccessibilityService.live?.clearOverlay("已打开计划调整入口，专注计时继续")
        true
    } catch (_: Exception) { until = 0; returning = false; false }
    fun returned(): Boolean = returning.also { returning = false; until = 0 }
    fun expired(): Boolean = (until > 0 && SystemClock.elapsedRealtime() >= until).also { if (it) until = 0 }
}
