package com.mobilealarm.alarm

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mobilealarm.MobileAlarmApp
import com.mobilealarm.R
import com.mobilealarm.domain.model.Alarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 前台服务负责播放铃声与震动；通知本身带全屏 Intent。 */
class AlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var autoStopJob: Job? = null
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentId: Long = -1L

    override fun onCreate() {
        super.onCreate()
        AlarmNotifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
        if (id <= 0L) {
            stopSelf()
            return START_NOT_STICKY
        }
        currentId = id
        val placeholder = NotificationCompat.Builder(this, AlarmNotifications.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(getString(R.string.alarm_notification_title))
            .setContentText(getString(R.string.alarm_notification_loading))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(AlarmNotifications.contentIntent(this))
            .build()
        startForeground(AlarmNotifications.notificationId(id), placeholder)
        scope.launch { loadAndStart(id) }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission") // 通知权限在 AlarmNotifications.show 中检查，前台服务通知必须展示。
    private suspend fun loadAndStart(id: Long) {
        val container = (application as MobileAlarmApp).container
        val alarm = container.alarmRepository.get(id)
        if (alarm == null) {
            stopSelf()
            return
        }
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MobileAlarm:ringing")
            .apply { acquire(10 * 60 * 1000L) }
        if (AlarmNotifications.canPostNotifications(this)) {
            NotificationManagerCompat.from(this)
                .notify(AlarmNotifications.notificationId(id), AlarmNotifications.buildRinging(this, alarm))
        }
        startPlayback(alarm)
        if (alarm.vibrate) startVibration()
        autoStopJob?.cancel()
        autoStopJob = scope.launch {
            delay(AlarmNotifications.AUTO_STOP_MILLIS)
            stopSelf()
        }
    }

    private fun startPlayback(alarm: Alarm) {
        val candidates = buildList {
            alarm.ringtone?.let { runCatching { Uri.parse(it) }.getOrNull()?.let(::add) }
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let(::add)
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)?.let(::add)
        }.distinct()

        for (uri in candidates) {
            val created = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    setDataSource(this@AlarmService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            }.getOrNull()
            if (created != null) {
                player = created
                return
            }
        }
    }

    private fun startVibration() {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = vib
        val pattern = longArrayOf(0, 800, 500, 800, 500)
        runCatching {
            vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
    }

    private fun releasePlayback() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        runCatching { wakeLock?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        autoStopJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
        releasePlayback()
        if (currentId > 0) AlarmNotifications.cancel(this, currentId)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"

        fun start(context: Context, alarmId: Long) {
            val intent = Intent(context, AlarmService::class.java)
                .putExtra(EXTRA_ALARM_ID, alarmId)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmService::class.java))
        }
    }
}