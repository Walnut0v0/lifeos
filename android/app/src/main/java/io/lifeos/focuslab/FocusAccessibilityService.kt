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
    private var overlayRing: FocusRingView? = null
    private var exitButton: Button? = null
    private var exitCancelButton: Button? = null
    private var exitAt = 0L
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
                    pkg in store.blockedApps -> "TARGET"
                    AppCatalog(this).isHome(pkg) -> "HOME"
                    AppCatalog(this).protected(pkg) -> "PROTECTED"
                    else -> "OTHER"
                }
            } catch (_: Exception) { "UNRESOLVED" }
            store.log("WINDOW_${role}_${if (event.isFullScreen) "FULL" else "PARTIAL"}_${if (overlay != null) "COVERED" else "OPEN"}", SystemClock.uptimeMillis() - event.eventTime)
        }
        // Usage evidence is enabled only for a bounded Lab test or an opted-in focus session.
        // Full home events and all other protected/system windows still release immediately.
        val auxiliaryLauncherEvent = try {
            if (overlay != null && pkg != null && !event.isFullScreen && store.phase() == Phase.ACTIVE && AppCatalog(this).isHome(pkg)) {
                val decision = ForegroundProbe.decision(this, store.blockedApps)
                store.log(decision)
                decision == "ASSIST_TARGET_CONFIRMED"
            } else false
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
        IntegrationHooks.tick(this)
        val watch = IntegrationHooks.watchesForeground(this) || store.isWhitelistSession
        val observed = if (watch && unlocked()) ForegroundProbe.observeCurrent(this) else { ForegroundProbe.resetCurrent(); null }
        val phase = store.phase()
        if (store.isWhitelistSession && phase == Phase.ACTIVE && !usageGranted(this)) { store.stop("WHITELIST_PERMISSION_LOST"); return }
        if (phase != lastPhase) {
            store.log(if (phase == Phase.ACTIVE) "STATE_ACTIVE" else "STATE_INACTIVE")
            // A continuous usage candidate must agree with the last observed window package.
            if (phase == Phase.ACTIVE && unlocked()) {
                if (observed != null && observed == foreground) enforce(null)
                else if (SystemClock.elapsedRealtime() - foregroundAt < 5_000) enforce(null)
            }
            lastPhase = phase
        }
        // Unlock can happen within one active phase: use continuous usage evidence, not a stale window event.
        if (phase == Phase.ACTIVE && overlay == null && observed != null && observed == foreground && observed in store.blockedApps) enforce(null)
        if (phase != Phase.ACTIVE) clearOverlay("计时已结束或测试已停止")
        else if (!unlocked()) clearOverlay("手机已锁屏或屏幕已关闭，已让出画面")
        else if (overlay?.isAttachedToWindow == false) clearOverlay("系统已移除覆盖层窗口，计时仍在继续")
        overlayRing?.refresh()
        if (exitAt > 0) {
            val wait = (exitAt - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            exitButton?.text = if (wait > 0) "再等 ${(wait + 999) / 1000} 秒后可结束" else "确认结束专注"
        }
        if (!store.isWhitelistSession && overlay != null && SystemClock.elapsedRealtime() - overlayAt > 10_000) store.stop("OVERLAY_SAFETY_TIMEOUT")
    }
    private fun enforce(eventAt: Long?) {
        if (!store.isWhitelistSession && store.mode == "DETECT") { clearOverlay("当前模式只检测，不显示拦截画面"); return }
        val pkg = foreground
        if (pkg == null) { clearOverlay("收到无法识别所属应用的窗口事件，已让出画面"); return }
        if (!unlocked()) { clearOverlay("手机已锁屏或屏幕已关闭，已让出画面"); return }
        if (store.phase() != Phase.ACTIVE) { clearOverlay("计时已结束或测试已停止"); return }
        if (pkg !in store.blockedApps) {
            val reason = when {
                pkg == packageName -> "已返回 LifeOS 页面，覆盖层已撤下"
                AppCatalog(this).protected(pkg) -> "收到系统或受保护应用窗口事件，已让出画面；计时仍继续"
                else -> "收到未选中应用的窗口事件，已让出画面；计时仍继续"
            }
            clearOverlay(reason); return
        }
        if (!FocusPolicy.shouldBlock(true, true, AppCatalog(this).protected(pkg), unlocked())) { clearOverlay("当前窗口属于受保护应用，已让出画面"); return }
        if (store.isWhitelistSession && !WhitelistPolicy.blocks(true, pkg in store.managedApps, pkg in store.whitelist, false, unlocked())) { clearOverlay("此应用可正常使用"); return }
        val now = SystemClock.elapsedRealtime()
        if (overlay != null) return
        if (now - lastAction < 800) {
            val expected = pkg
            handler.postDelayed({ if (foreground == expected && store.phase() == Phase.ACTIVE) try { enforce(null) } catch (_: Exception) { store.stop("RETRY_FAULT") } }, 800 - (now - lastAction))
            return
        }
        while (recentActions.isNotEmpty() && now - recentActions.first() > 10_000) recentActions.removeFirst()
        if (recentActions.size >= 8) { store.stop("LOOP_GUARD_STOP"); return }
        recentActions.addLast(now); lastAction = now
        when (if (store.isWhitelistSession) "OVERLAY" else store.mode) {
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
    fun recheckForeground() { try { enforce(null) } catch (_: Exception) { store.stop("FOREGROUND_RECHECK_FAULT") } }
    private fun showOverlay() {
        if (overlay != null) return
        val whitelistMode = store.isWhitelistSession
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            val dp = resources.displayMetrics.density
            setPadding((22 * dp).toInt(), (20 * dp).toInt(), (22 * dp).toInt(), (20 * dp).toInt()); setBackgroundColor(if (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES) Color.rgb(23, 34, 29) else Color.rgb(245, 247, 243))
            if (whitelistMode) {
                addView(TextView(context).apply { text = "正在专注"; textSize = 22f; setTextColor(if (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES) Color.rgb(233, 236, 232) else Color.rgb(32, 39, 34)) })
                overlayRing = FocusRingView(context)
                addView(overlayRing, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (minOf(240, (resources.configuration.screenHeightDp - 300).coerceAtLeast(100)) * resources.displayMetrics.density).toInt()))
                addView(TextView(context).apply { text = "需要用到的应用，都在白名单里"; textSize = 14f; setTextColor(if (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES) Color.rgb(186, 195, 188) else Color.rgb(86, 99, 91)); gravity = Gravity.CENTER })
                addView(Button(context).apply { text = "打开白名单应用"; setOnClickListener {
                    try {
                        startActivity(android.content.Intent(this@FocusAccessibilityService, MainActivity::class.java).putExtra("open_allowed_apps", true).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        clearOverlay("已打开白名单入口")
                    } catch (_: Exception) { Toast.makeText(context, "请从通知或桌面打开 LifeOS", Toast.LENGTH_SHORT).show() }
                } })
                addView(Space(context), LinearLayout.LayoutParams(1, 0, 1f))
                addView(Button(context).apply { text = "返回计时主页"; setOnClickListener {
                    try { startActivity(android.content.Intent(this@FocusAccessibilityService, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); clearOverlay("已返回计时主页") }
                    catch (_: Exception) { Toast.makeText(context, "请从通知或桌面打开 LifeOS", Toast.LENGTH_SHORT).show() }
                } })
                exitButton = Button(context).apply { text = "申请结束专注"; setOnClickListener {
                    if (exitAt == 0L) { exitAt = SystemClock.elapsedRealtime() + 10_000; exitCancelButton?.visibility = View.VISIBLE }
                    else if (SystemClock.elapsedRealtime() >= exitAt) store.stop("WHITELIST_CONFIRMED_EXIT")
                } }
                addView(exitButton)
                exitCancelButton = Button(context).apply { text = "继续专注"; visibility = View.GONE; setOnClickListener { exitAt = 0; exitButton?.text = "申请结束专注"; visibility = View.GONE } }
                addView(exitCancelButton)
                addView(Button(context).apply { text = "遇到误拦截？应急解除"; setOnClickListener { store.stop("WHITELIST_EMERGENCY_STOP") } })
            } else {
                addView(TextView(context).apply { text = "专注拦截测试\n\n覆盖层最多显示 10 秒，随后自动停止本次测试。"; textSize = 22f; setTextColor(Color.BLACK) })
                addView(Button(context).apply { text = "返回桌面"; setOnClickListener { performGlobalAction(GLOBAL_ACTION_HOME); clearOverlay("已点击返回桌面，计时仍继续") } })
                addView(Button(context).apply { text = "停止所有测试并解除"; setOnClickListener { store.stop() } })
            }
        }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        val dark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val background = if (dark) Color.rgb(23, 34, 29) else Color.rgb(245, 247, 243)
        val ink = if (dark) Color.rgb(178, 208, 190) else Color.rgb(49, 92, 75)
        if (whitelistMode) for (i in 0 until layout.childCount) {
            (layout.getChildAt(i) as? Button)?.apply {
                isAllCaps = false; minHeight = (48 * resources.displayMetrics.density).toInt()
                backgroundTintList = android.content.res.ColorStateList.valueOf(if (dark) Color.rgb(48, 70, 58) else Color.rgb(225, 236, 227))
                setTextColor(if (text.toString().contains("应急")) if (dark) Color.rgb(255, 180, 171) else Color.rgb(160, 48, 40) else ink)
            }
        }
        val content: View = if (whitelistMode) {
            val emergency = layout.getChildAt(layout.childCount - 1)
            layout.removeView(emergency)
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setBackgroundColor(background)
                addView(ScrollView(context).apply { isFillViewport = true; addView(layout) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
                addView(emergency, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
        } else ScrollView(this).apply { isFillViewport = true; addView(layout) }
        getSystemService(WindowManager::class.java).addView(content, params)
        overlay = content; overlayAt = SystemClock.elapsedRealtime()
        // Assign an event source to OUR View to obtain its window metadata. Never read a node/source.
        @Suppress("DEPRECATION")
        val ownEvent = AccessibilityEvent.obtain()
        try { ownEvent.setSource(content); overlayWindowId = ownEvent.windowId }
        finally { @Suppress("DEPRECATION") ownEvent.recycle() }
        store.overlayStatus = "覆盖层已显示；若画面消失后仍显示此提示，需进一步检查系统窗口行为"
    }
    fun clearOverlay(reason: String = "测试已停止或配置已重置") {
        if (overlay == null) return
        overlay?.let { try { getSystemService(WindowManager::class.java).removeViewImmediate(it) } catch (_: Exception) { } }
        overlay = null
        overlayRing = null
        exitButton = null; exitCancelButton = null; exitAt = 0
        overlayWindowId = -1
        if (::store.isInitialized) {
            store.overlayStatus = reason
            store.log("OVERLAY_REMOVED")
        }
    }
    override fun onInterrupt() {
        // This callback interrupts feedback; it does not revoke the system permission.
        clearOverlay("系统打断了当前画面，等待下一次应用识别")
        foreground = null; ForegroundProbe.resetCurrent()
        if (::store.isInitialized) store.log("SERVICE_FEEDBACK_INTERRUPTED")
    }
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (::store.isInitialized) store.stop("SERVICE_UNBOUND")
        shutdown(); return super.onUnbind(intent)
    }
    private fun shutdown() { handler.removeCallbacksAndMessages(null); clearOverlay(); if (live === this) reference.clear(); foreground = null; ForegroundProbe.resetCurrent(); lastExternalPackage = "服务已断开" }
    override fun onDestroy() { shutdown(); super.onDestroy() }
}
