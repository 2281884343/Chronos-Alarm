package com.mobilealarm.alarm

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mobilealarm.MainActivity
import com.mobilealarm.R
import com.mobilealarm.domain.model.Alarm

object AlarmNotifications {
    const val CHANNEL_ALARM = "alarm_ringing"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ALARM)
        if (existing != null) {
            // 铃声由 AlarmService 的 MediaPlayer 循环播放，通知渠道必须静音，避免双份声音。
            if (existing.sound != null) {
                existing.setSound(null, null)
                manager.createNotificationChannel(existing)
            }
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alarm_desc)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            enableVibration(false)
            // 铃声由 AlarmService 的 MediaPlayer 循环播放，通知渠道不发声。
            setSound(null, null)
            setBypassDnd(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun buildRinging(context: Context, alarm: Alarm): android.app.Notification {
        ensureChannel(context)
        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_ALARM_ID, alarm.id)
        }
        val fullScreen = PendingIntent.getActivity(
            context,
            requestCode(alarm.id, 0),
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getBroadcast(
            context,
            requestCode(alarm.id, 1),
            Intent(context, AlarmActionReceiver::class.java).apply {
                action = AlarmActionReceiver.ACTION_STOP
                putExtra(EXTRA_ALARM_ID, alarm.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getBroadcast(
            context,
            requestCode(alarm.id, 2),
            Intent(context, AlarmActionReceiver::class.java).apply {
                action = AlarmActionReceiver.ACTION_SNOOZE
                putExtra(EXTRA_ALARM_ID, alarm.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = alarm.label.ifBlank { context.getString(R.string.app_name) }
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        // 锁屏或熄屏时才直接拉起全屏响铃页；解锁状态保留系统横幅，
        // 这样用户正在看视频/打游戏时也能先看到通知并直接关闭。
        val showFullScreen = keyguard?.isKeyguardLocked == true || power?.isInteractive == false
        val autoStopAt = System.currentTimeMillis() + AUTO_STOP_MILLIS

        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.alarm_notification_text, alarm.timeText))
            .setSubText("响铃中 · 10 分钟后自动停止")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setWhen(autoStopAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(fullScreen)
            .apply { if (showFullScreen) setFullScreenIntent(fullScreen, true) }
            .setSound(null)
            .addAction(0, context.getString(R.string.action_snooze), snooze)
            .addAction(0, context.getString(R.string.action_dismiss), stop)
            .build()
    }

    @SuppressLint("MissingPermission") // canPostNotifications() 已做运行时检查。
    fun show(context: Context, alarm: Alarm) {
        if (!canPostNotifications(context)) return
        NotificationManagerCompat.from(context).notify(notificationId(alarm.id), buildRinging(context, alarm))
    }

    fun cancel(context: Context, alarmId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(alarmId))
    }

    fun canPostNotifications(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun notificationId(alarmId: Long): Int =
        20_000 + ((alarmId xor (alarmId ushr 32)).toInt() and 0x000F_FFFF)

    /** PendingIntent 的 requestCode 只取低 28 位，预留 3 个槽位给全屏页、关闭、稍后提醒。 */
    private fun requestCode(alarmId: Long, slot: Int): Int =
        (((alarmId xor (alarmId ushr 32)).toInt() and 0x0FFF_FFFF) * 8) + slot

    fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    const val EXTRA_ALARM_ID = "alarm_id"
    const val AUTO_STOP_MILLIS = 10 * 60 * 1000L
}