package io.lifeos.focuslab

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.util.Base64
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.json.JSONObject

object IntegrationHooks {
    private var initialized = false
    @Synchronized private fun initialize(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { system(context, intent.action) }
        }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_SCREEN_ON) }
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("UnspecifiedRegisterReceiverFlag") app.registerReceiver(receiver, filter)
        app.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { SyncWork.request(app) }
        })
        SyncWork.schedule(app)
        initialized = true
    }
    fun resume(context: Context) {
        initialize(context)
        PlanRuntime.reconcile(context, true)
        SyncWork.request(context, ChangeAccess.returned())
    }
    fun tick(context: Context) {
        initialize(context); PlanRuntime.tick(context)
        if (ChangeAccess.expired()) FocusAccessibilityService.live?.recheckForeground()
    }
    fun system(context: Context, action: String?) {
        initialize(context)
        PlanRuntime.reconcile(context, true)
        if (action != "PLAN_BOUNDARY" && action != "START" && action != "END") SyncWork.request(context)
    }
    fun stopping(context: Context, code: String, planned: Boolean) = PlanRuntime.stopping(context, code, planned)
    fun allows(context: Context, packageName: String) = ChangeAccess.allows(packageName)
    @Composable fun Status(activity: Activity, pulse: Int) {
        val plans = PlanStore(activity)
        val decision = ScheduleEngine.resolve(plans.load(), System.currentTimeMillis())
        decision.task?.let { Text(it.title.ifBlank { "计划专注" }) }
        Text(plans.prefs.getString("execution_status", "尚未连接在线计划").orEmpty())
        TextButton(onClick = { activity.startActivity(Intent(activity, OnlineActivity::class.java)) }) { Text("计划、统计与申请调整") }
    }
    fun debug(context: Context, intent: Intent): String? = when (intent.getStringExtra("operation")) {
        "google_connect" -> {
            context.startActivity(Intent(context, GoogleConnectActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "GOOGLE_CONNECT_OPENED"
        }
        "online_configure" -> {
            val raw = intent.getStringExtra("configuration") ?: error("Missing configuration")
            require(raw.length <= 4096)
            val json = JSONObject(String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8))
            SyncConfig(context).save(json.getString("endpoint"), json.getString("token"))
            SyncWork.request(context, true)
            "ONLINE_CONFIGURED"
        }
        "online_arm" -> {
            PlanRuntime.prepare(context)
            PlanStore(context).armed = intent.getBooleanExtra("armed", false)
            PlanRuntime.reconcile(context, true)
            "AUTO_EXECUTION_UPDATED"
        }
        "online_sync" -> { SyncWork.request(context, true); "SYNC_QUEUED" }
        "online_status" -> {
            val plans = PlanStore(context)
            val decision = ScheduleEngine.resolve(plans.load(), System.currentTimeMillis())
            JSONObject().put("revision", plans.load()?.revision ?: 0).put("logical_active", decision.task != null)
                .put("armed", plans.armed).put("planned_session", LabStore(context).session()?.planned == true)
                .put("phase", LabStore(context).phase().name).put("last_sync", plans.prefs.getLong("last_sync", 0))
                .put("sync_status", plans.prefs.getString("sync_status", ""))
                .put("execution_status", plans.prefs.getString("execution_status", ""))
                .put("service_connected", FocusAccessibilityService.live != null).put("usage_enabled", plans.prefs.getBoolean("usage_enabled", false))
                .put("google_check", plans.prefs.getString("google_check", ""))
                .put("usage_report_count", UsageCollector.reports(context).size).toString()
        }
        else -> null
    }
}
