package com.mobilealarm.alarm

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.mobilealarm.MainActivity
import com.mobilealarm.R
import com.mobilealarm.domain.model.CommutePlan
import com.mobilealarm.domain.model.CommutePreview

object CommuteNotifications {
    const val CHANNEL = "commute_reminder"
    const val NOTIFICATION_ID = 31_001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL)
        if (existing == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "通勤出门提醒",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "按所选出行方式的耗时，在最迟出门时间前提醒"
                    enableVibration(true)
                },
            )
        }
    }

    @SuppressLint("MissingPermission") // canPostNotifications() 已做运行时检查。
    fun show(context: Context, plan: CommutePlan, preview: CommutePreview) {
        if (!AlarmNotifications.canPostNotifications(context)) return
        ensureChannel(context)
        val origin = plan.origin?.name ?: "出发地"
        val destination = plan.destination?.name ?: "目的地"
        val arrival = preview.arrival.let { "%02d:%02d".format(it.hour, it.minute) }
        val body = "$origin → $destination · ${plan.mode.label}约 ${preview.routeMinutes} 分钟\n" +
            "现在出发，$arrival 到达；最迟 ${preview.latestDepartureText} 出门。"

        val content = PendingIntent.getActivity(
            context,
            0x0CC0_0001,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismiss = PendingIntent.getBroadcast(
            context,
            0x0CC0_0002,
            Intent(context, CommuteReceiver::class.java).setAction(CommuteReceiver.ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("该出门了 · $arrival 到达")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSubText("通勤提醒 · 已计入${plan.mode.label}耗时和 ${plan.bufferMinutes} 分钟缓冲")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(content)
            .addAction(0, "知道了", dismiss)
            .build()
        androidx.core.app.NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        androidx.core.app.NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}