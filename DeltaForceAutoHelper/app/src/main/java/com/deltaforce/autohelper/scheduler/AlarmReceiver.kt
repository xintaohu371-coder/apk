package com.deltaforce.autohelper.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.deltaforce.autohelper.model.ConfigRepository
import com.deltaforce.autohelper.service.AutomationForegroundService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ConfigRepository.appendLog(context, "定时闹钟触发，唤醒执行任务")
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "DeltaAutoHelper:WakeLock"
        )
        wl.acquire(15_000L)
        AutomationForegroundService.start(context, fromSchedule = true)
    }
}
