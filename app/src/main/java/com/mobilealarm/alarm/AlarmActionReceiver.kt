package com.mobilealarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mobilealarm.MobileAlarmApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmNotifications.EXTRA_ALARM_ID, -1L)
        if (alarmId <= 0L) return
        when (intent.action) {
            ACTION_STOP -> {
                AlarmNotifications.cancel(context, alarmId)
                AlarmService.stop(context)
            }
            ACTION_SNOOZE -> {
                val pending = goAsync()
                val app = context.applicationContext as? MobileAlarmApp
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        app?.container?.alarmRepository?.get(alarmId)?.let { alarm ->
                            app.container.alarmScheduler.scheduleSnooze(alarm)
                        }
                    } finally {
                        AlarmNotifications.cancel(context, alarmId)
                        AlarmService.stop(context)
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.mobilealarm.action.STOP"
        const val ACTION_SNOOZE = "com.mobilealarm.action.SNOOZE"
    }
}