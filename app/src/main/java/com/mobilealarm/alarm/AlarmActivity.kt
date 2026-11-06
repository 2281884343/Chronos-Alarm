package com.mobilealarm.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilealarm.MobileAlarmApp
import com.mobilealarm.domain.model.Alarm
import com.mobilealarm.domain.model.AlarmPlan
import com.mobilealarm.domain.model.DayInfo
import com.mobilealarm.domain.rule.AlarmTimeCalculator
import com.mobilealarm.ui.theme.MobileAlarmTheme
import com.mobilealarm.ui.theme.Night
import com.mobilealarm.ui.theme.NightHairline
import com.mobilealarm.ui.theme.NightInk
import com.mobilealarm.ui.theme.NightInk2
import com.mobilealarm.ui.theme.Vermilion
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class AlarmActivity : ComponentActivity() {
    private val ringingAlarmId: Long by lazy {
        intent.getLongExtra(AlarmNotifications.EXTRA_ALARM_ID, -1L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 夜间纸面：状态栏与导航栏都用浅色图标。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguard.requestDismissKeyguard(this, null)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MobileAlarmTheme(darkTheme = true) {
                AlarmRingingScreen(
                    alarmId = ringingAlarmId,
                    onStop = {
                        sendAction(AlarmActionReceiver.ACTION_STOP)
                        finish()
                    },
                    onSnooze = {
                        sendAction(AlarmActionReceiver.ACTION_SNOOZE)
                        finish()
                    },
                )
            }
        }
    }

    private fun sendAction(action: String) {
        val intent = Intent(this, AlarmActionReceiver::class.java)
            .setAction(action)
            .putExtra(AlarmNotifications.EXTRA_ALARM_ID, ringingAlarmId)
        sendBroadcast(intent)
    }
}

@Composable
private fun AlarmRingingScreen(
    alarmId: Long,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
) {
    val context = LocalContext.current
    var alarm by remember { mutableStateOf<Alarm?>(null) }
    var plan by remember { mutableStateOf<AlarmPlan?>(null) }
    var dayInfo by remember { mutableStateOf<DayInfo?>(null) }
    var loading by remember { mutableStateOf(true) }

    // 响铃页只允许「关闭」或「稍后」，避免误按返回键把闹钟留在后台继续响。
    BackHandler(enabled = true) {}

    LaunchedEffect(alarmId) {
        val container = (context.applicationContext as MobileAlarmApp).container
        val loaded = container.alarmRepository.get(alarmId)
        alarm = loaded
        if (loaded != null) {
            val calendar = container.holidayRepository.currentCalendar()
            val weather = container.weatherRepository.current()
            val todayPlan = AlarmTimeCalculator.planFor(loaded, LocalDate.now(), calendar, weather)
            plan = todayPlan ?: AlarmTimeCalculator.nextPlan(loaded, LocalDateTime.now(), calendar, weather)
            dayInfo = calendar.info(LocalDate.now())
        }
        loading = false
    }

    val displayTime = plan?.adjusted?.format(DateTimeFormatter.ofPattern("HH:mm"))
        ?: alarm?.timeText
        ?: LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "天时 · 闹钟",
            color = NightInk2,
            fontSize = 11.sp,
            letterSpacing = 3.sp,
        )
        Text(
            text = dayInfo?.kindLabel ?: "该起床了",
            color = NightInk.copy(alpha = 0.55f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.weight(0.65f))

        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            SparseDial()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = displayTime,
                    color = NightInk,
                    fontSize = 82.sp,
                    lineHeight = 86.sp,
                    fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                    fontWeight = FontWeight.Light,
                )
                Text(
                    text = (dayInfo?.date ?: LocalDate.now())
                        .format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
                    color = NightInk2,
                    fontSize = 14.sp,
                )
                if (!alarm?.label.isNullOrBlank()) {
                    Text(
                        text = alarm?.label.orEmpty(),
                        color = NightInk,
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp, start = 40.dp, end = 40.dp),
                    )
                }
            }
        }

        Spacer(Modifier.weight(0.5f))

        if (loading) {
            CircularProgressIndicator(color = NightInk2, strokeWidth = 1.5.dp)
        } else {
            Text(
                text = plan?.let { ringDetail(it) } ?: "按设定时间响铃",
                color = NightInk2,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            plan?.reasons?.firstOrNull()?.let { reason ->
                Text(
                    text = reason,
                    color = NightInk.copy(alpha = 0.62f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp, start = 20.dp, end = 20.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NightInk.copy(alpha = 0.08f),
                    contentColor = NightInk,
                ),
                border = BorderStroke(1.dp, NightInk.copy(alpha = 0.42f)),
            ) {
                Text("关闭", fontSize = 17.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
            }
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = NightInk.copy(alpha = 0.68f),
                ),
                border = BorderStroke(1.dp, NightInk.copy(alpha = 0.20f)),
            ) {
                Text("稍后 ${alarm?.snoozeMinutes ?: 10} 分钟", fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SparseDial() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = size.minDimension / 2f - 2.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = NightHairline,
            radius = radius,
            style = Stroke(width = 1.dp.toPx()),
        )
        drawCircle(
            color = NightInk.copy(alpha = 0.05f),
            radius = radius * 0.78f,
            style = Stroke(width = 1.dp.toPx()),
        )
        for (i in 0 until 60) {
            if (i % 5 != 0 && i % 5 != 1) continue
            val angle = Math.toRadians(i * 6.0 - 90.0)
            val outer = radius - 1.dp.toPx()
            val inner = outer - if (i % 5 == 0) 9.dp.toPx() else 5.dp.toPx()
            drawLine(
                color = NightInk.copy(alpha = if (i % 5 == 0) 0.34f else 0.14f),
                start = Offset(
                    center.x + (cos(angle) * inner).toFloat(),
                    center.y + (sin(angle) * inner).toFloat(),
                ),
                end = Offset(
                    center.x + (cos(angle) * outer).toFloat(),
                    center.y + (sin(angle) * outer).toFloat(),
                ),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawCircle(
            color = Vermilion,
            radius = 3.dp.toPx(),
            center = Offset(center.x, center.y - radius),
        )
    }
}

private fun ringDetail(plan: AlarmPlan): String {
    val weather = plan.weather
    val temperature = when {
        weather?.tempMin != null && weather.tempMax != null ->
            "%.0f°~%.0f°".format(weather.tempMin, weather.tempMax)
        weather?.temperature != null -> "%.0f°".format(weather.temperature)
        else -> null
    }
    return buildString {
        if (weather != null) {
            append(weather.codeLabel)
            temperature?.let { append(" $it") }
        } else {
            append("天气预报未更新")
        }
        append(" · ")
        if (plan.offsetMinutes == 0) append("按基准时间") else append("已${plan.offsetLabel}")
    }
}
