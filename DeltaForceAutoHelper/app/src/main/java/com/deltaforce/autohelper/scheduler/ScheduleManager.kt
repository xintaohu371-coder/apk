package com.deltaforce.autohelper.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.deltaforce.autohelper.model.ConfigRepository
import com.deltaforce.autohelper.model.ScheduleMode
import java.util.Calendar

object ScheduleManager {
    private const val REQUEST_CODE = 3001

    fun scheduleNext(context: Context) {
        val config = ConfigRepository.load(context)
        if (!config.scheduleEnabled) {
            cancel(context)
            return
        }

        val triggerAtMillis = when (config.scheduleMode) {
            ScheduleMode.INTERVAL -> {
                System.currentTimeMillis() + config.intervalMinutes * 60_000L
            }
            ScheduleMode.DAILY_FIXED -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, config.dailyHour)
                    set(Calendar.MINUTE, config.dailyMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis() + 5_000L) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                cal.timeInMillis
            }
        }

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
        ConfigRepository.setNextRunMillis(context, triggerAtMillis)
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(buildPendingIntent(context))
        ConfigRepository.setNextRunMillis(context, 0L)
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
