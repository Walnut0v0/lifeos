package io.lifeos.focuslab

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*

/** Visible, non-sticky, bounded FGS capability experiment; never used for permanent keepalive. */
class ShortTestService : Service() {
    companion object { var running = false; private set }
    private val handler = Handler(Looper.getMainLooper())
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("fgs", "60秒前台服务实验", NotificationManager.IMPORTANCE_LOW))
            val stop = PendingIntent.getBroadcast(this, 3, Intent(this, ScheduleReceiver::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(this, "fgs").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("60秒前台服务测试").setContentText("自动结束，可立即停止")
                .addAction(Notification.Action.Builder(null, "停止所有测试", stop).build()).build()
            if (Build.VERSION.SDK_INT >= 34) startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
            else startForeground(2, n)
            running = true; LabStore(this).log("FGS_STARTED")
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ stopSelf() }, 60_000)
        } catch (_: Exception) { LabStore(this).log("FGS_UNAVAILABLE"); stopSelf() }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int) { stopSelf() }
    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null); running = false
        stopForeground(STOP_FOREGROUND_REMOVE); LabStore(this).log("FGS_STOPPED"); super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}
