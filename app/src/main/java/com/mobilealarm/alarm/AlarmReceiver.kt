package com.mobilealarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mobilealarm.MobileAlarmApp
import com.mobilealarm.domain.model.DayMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE_ALARM) return
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (alarmId <= 0L) return
        val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)

        AlarmService.start(context, alarmId)

        if (!snooze) {
            val pending = goAsync()
            val app = context.applicationContext as? MobileAlarmApp
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val alarm = app?.container?.alarmRepository?.get(alarmId)
                    if (alarm != null) {
                        if (alarm.dayMode == DayMode.ONCE) {
                            // 只响一次：触发后立即关闭，不再安排下一次。
                            app.container.alarmRepository.setEnabled(alarm.id, false)
                        } else {
                            app.container.alarmScheduler.scheduleNext(alarm)
                        }
                    }
                } finally {
                    pending.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_FIRE_ALARM = "com.mobilealarm.action.FIRE_ALARM"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SNOOZE = "snooze"
    }
}