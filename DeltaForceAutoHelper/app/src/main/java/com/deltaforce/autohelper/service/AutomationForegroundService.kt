package com.deltaforce.autohelper.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.deltaforce.autohelper.model.ConfigRepository
import com.deltaforce.autohelper.scheduler.ScheduleManager

class AutomationForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            DeltaAccessibilityService.instance?.stopAutomation("通知栏手动停止")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("准备执行特勤处自动化..."))

        val accessibility = DeltaAccessibilityService.instance
        if (accessibility == null) {
            ConfigRepository.appendLog(this, "触发失败：无障碍服务未开启")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val rescheduled = intent?.getBooleanExtra(EXTRA_FROM_SCHEDULE, false) == true
        accessibility.startAutomation(
            onStatusUpdate = { status ->
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(status))
            },
            onFinished = {
                if (rescheduled) {
                    ScheduleManager.scheduleNext(this)
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        )
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "特勤处自动化任务",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        val stopIntent = Intent(this, AutomationForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("三角洲特勤处助手")
            .setContentText(content)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止任务", stopPending)
            .build()
    }

    companion object {
        const val ACTION_START = "com.deltaforce.autohelper.ACTION_START"
        const val ACTION_STOP = "com.deltaforce.autohelper.ACTION_STOP"
        const val EXTRA_FROM_SCHEDULE = "extra_from_schedule"
        private const val CHANNEL_ID = "delta_auto_channel"
        private const val NOTIFICATION_ID = 2001

        fun start(context: Context, fromSchedule: Boolean) {
            val intent = Intent(context, AutomationForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FROM_SCHEDULE, fromSchedule)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AutomationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
