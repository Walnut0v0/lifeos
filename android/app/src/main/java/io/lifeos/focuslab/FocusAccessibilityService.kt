package io.lifeos.focuslab

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.*

class FocusAccessibilityService : AccessibilityService() {
    companion object {
        private var reference = java.lang.ref.WeakReference<FocusAccessibilityService>(null)
        val live: FocusAccessibilityService? get() = reference.get()
        var lastExternalPackage: String = "尚无事件"
            private set
        var eventCount = 0
            private set
    }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: LabStore
    private var foreground: String? = null
    private var foregroundAt = 0L
    private var overlay: View? = null
    private var overlayAt = 0L
    private var overlayWindowId = -1
    private var lastAction = 0L
    private var lastPhase = Phase.IDLE
    private val recentActions = ArrayDeque<Long>()
    private val tick = object : Runnable {
        override fun run() {
            try { reconcile() } catch (_: Exception) { store.stop("SERVICE_FAULT") }
            handler.postDelayed(this, 250)
        }
    }
    override fun onServiceConnected() {
        reference = java.lang.ref.WeakReference(this); store = LabStore(this)
        store.log("SERVICE_CONNECTED")
        try { store.reschedule() } catch (_: Exception) { store.stop("CONNECT_FAULT") }
        handler.post(tick)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::store.isInitialized || event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        // No text, source, className, nodes, screenshots or window hierarchy is accessed.
        val pkg = event.packageName?.toString()
        val ownWindowId = overlayWindowId
        // Compare window metadata with our own View; this does not retrieve screen content.
        // A vendor can attribute our overlay's event to a different package.
        if (overlay != null && ((ownWindowId >= 0 && event.windowId == ownWindowId) ||
                ((ownWindowId < 0 || event.windowId < 0) && pkg == packageName))) {
            store.log("OVERLAY_SELF_WINDOW_IGNORED")
            return
        }
        // Only fixed roles/flags during a test; never persist package names, class names or UI text.
        if (store.phase() == Phase.ACTIVE) {
            val role = try {
                when {
                    pkg == null -> "UNKNOWN"
                    pkg == packageName -> "LAB"
                    pkg in store.targets -> "TARGET"
                    AppCatalog(this).isHome(pkg) -> "HOME"
                    AppCatalog(this).protected(pkg) -> "PROTECTED"
                    else -> "OTHER"
                }
            } catch (_: Exception) { "UNRESOLVED" }
            store.log("WINDOW_${role}_${if (event.isFullScreen) "FULL" else "PARTIAL"}_${if (overlay != null) "COVERED" else "OPEN"}", SystemClock.uptimeMillis() - event.eventTime)
        }
        // Only an explicitly enabled, at-most-60-second test may consult recent usage metadata.
        // Full home events and all other protected/system windows still release immediately.
        val auxiliaryLauncherEvent = try {
            overlay != null && pkg != null && !event.isFullScreen && store.phase() == Phase.ACTIVE &&
                AppCatalog(this).isHome(pkg) && ForegroundProbe.targetStillResumed(this, store.targets)
        } catch (_: Exception) { false }
        if (auxiliaryLauncherEvent) {
            store.log("LAUNCHER_AUXILIARY_EVENT_TARGET_STILL_RESUMED")
            return
        }
        foreground = pkg
        foregroundAt = SystemClock.elapsedRealtime()
        if (pkg != null && pkg != packageName) { lastExternalPackage = pkg; eventCount++ }
        if (pkg == "com.android.settings") store.log("SETTINGS_WINDOW_OBSERVED")
        try { enforce(event.eventTime) } catch (_: Exception) { store.stop("EVENT_FAULT") }
    }
    private fun unlocked(): Boolean = !getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
        getSystemService(PowerManager::class.java).isInteractive
    fun reconcile() {
        val phase = store.phase()
        if (phase != lastPhase) {
            store.log(if (phase == Phase.ACTIVE) "STATE_ACTIVE" else "STATE_INACTIVE")
            // Use only a recent known foreground package at the start boundary; otherwise await an event.
            if (phase == Phase.ACTIVE && SystemClock.elapsedRealtime() - foregroundAt < 5_000) enforce(null)
            lastPhase = phase
        }
        if (phase != Phase.ACTIVE) clearOverlay("计时已结束或测试已停止")
        else if (!unlocked()) clearOverlay("手机已锁屏或屏幕已关闭，已让出画面")
        else if (overlay?.isAttachedToWindow == false) clearOverlay("系统已移除覆盖层窗口，计时仍在继续")
        if (overlay != null && SystemClock.elapsedRealtime() - overlayAt > 10_000) store.stop("OVERLAY_SAFETY_TIMEOUT")
    }
    private fun enforce(eventAt: Long?) {
        if (store.mode == "DETECT") { clearOverlay("当前模式只检测，不显示拦截画面"); return }
        val pkg = foreground
        if (pkg == null) { clearOverlay("收到无法识别所属应用的窗口事件，已让出画面"); return }
        if (!unlocked()) { clearOverlay("手机已锁屏或屏幕已关闭，已让出画面"); return }
        if (store.phase() != Phase.ACTIVE) { clearOverlay("计时已结束或测试已停止"); return }
        if (pkg !in store.targets) {
            val reason = when {
                pkg == packageName -> "已返回 LifeOS 页面，覆盖层已撤下"
                AppCatalog(this).protected(pkg) -> "收到系统或受保护应用窗口事件，已让出画面；计时仍继续"
                else -> "收到未选中应用的窗口事件，已让出画面；计时仍继续"
            }
            clearOverlay(reason); return
        }
        if (!FocusPolicy.shouldBlock(true, true, AppCatalog(this).protected(pkg), unlocked())) { clearOverlay("当前窗口属于受保护应用，已让出画面"); return }
        val now = SystemClock.elapsedRealtime()
        if (overlay != null || now - lastAction < 800) return
        while (recentActions.isNotEmpty() && now - recentActions.first() > 10_000) recentActions.removeFirst()
        if (recentActions.size >= 8) { store.stop("LOOP_GUARD_STOP"); return }
        recentActions.addLast(now); lastAction = now
        when (store.mode) {
            "HOME" -> {
                val success = performGlobalAction(GLOBAL_ACTION_HOME)
                store.log(if (success) "HOME_REQUEST_ACCEPTED" else "HOME_REQUEST_FAILED", eventAt?.let { SystemClock.uptimeMillis() - it })
                if (!success) store.stop("HOME_FAIL_OPEN")
            }
            "OVERLAY" -> { showOverlay(); store.log("OVERLAY_ATTACHED", eventAt?.let { SystemClock.uptimeMillis() - it }) }
            "DETECT" -> Unit
            else -> store.stop("MODE_INVALID")
        }
    }
    private fun showOverlay() {
        if (overlay != null) return
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(32, 64, 32, 64); setBackgroundColor(Color.rgb(241, 245, 249))
            addView(TextView(context).apply { text = "专注拦截测试\n\n覆盖层最多显示 10 秒，随后自动停止本次测试。"; textSize = 22f; setTextColor(Color.BLACK) })
            addView(Button(context).apply { text = "返回桌面"; setOnClickListener { performGlobalAction(GLOBAL_ACTION_HOME); clearOverlay("已点击返回桌面，计时仍继续") } })
            addView(Button(context).apply { text = "停止所有测试并解除"; setOnClickListener { store.stop() } })
        }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        getSystemService(WindowManager::class.java).addView(layout, params)
        overlay = layout; overlayAt = SystemClock.elapsedRealtime()
        // Assign an event source to OUR View to obtain its window metadata. Never read a node/source.
        @Suppress("DEPRECATION")
        val ownEvent = AccessibilityEvent.obtain()
        try { ownEvent.setSource(layout); overlayWindowId = ownEvent.windowId }
        finally { @Suppress("DEPRECATION") ownEvent.recycle() }
        store.overlayStatus = "覆盖层已显示；若画面消失后仍显示此提示，需进一步检查系统窗口行为"
    }
    fun clearOverlay(reason: String = "测试已停止或配置已重置") {
        if (overlay == null) return
        overlay?.let { try { getSystemService(WindowManager::class.java).removeViewImmediate(it) } catch (_: Exception) { } }
        overlay = null
        overlayWindowId = -1
        if (::store.isInitialized) {
            store.overlayStatus = reason
            store.log("OVERLAY_REMOVED")
        }
    }
    override fun onInterrupt() { if (::store.isInitialized) store.stop("SERVICE_INTERRUPTED") }
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (::store.isInitialized) store.stop("SERVICE_UNBOUND")
        shutdown(); return super.onUnbind(intent)
    }
    private fun shutdown() { handler.removeCallbacksAndMessages(null); clearOverlay(); if (live === this) reference.clear(); foreground = null; lastExternalPackage = "服务已断开" }
    override fun onDestroy() { shutdown(); super.onDestroy() }
}
