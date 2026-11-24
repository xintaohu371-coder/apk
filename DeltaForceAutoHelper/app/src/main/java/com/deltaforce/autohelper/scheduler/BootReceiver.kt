package com.deltaforce.autohelper.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.deltaforce.autohelper.model.ConfigRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val config = ConfigRepository.load(context)
            if (config.scheduleEnabled) {
                ScheduleManager.scheduleNext(context)
                ConfigRepository.appendLog(context, "设备重启后已自动恢复定时计划")
            }
        }
    }
}
