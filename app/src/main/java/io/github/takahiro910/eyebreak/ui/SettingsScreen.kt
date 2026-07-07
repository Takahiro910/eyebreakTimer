package io.github.takahiro910.eyebreak.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleButton
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import io.github.takahiro910.eyebreak.BuildConfig
import io.github.takahiro910.eyebreak.core.AlarmReceiver
import io.github.takahiro910.eyebreak.core.AlarmScheduler
import io.github.takahiro910.eyebreak.core.Prefs
import io.github.takahiro910.eyebreak.core.Reschedule
import io.github.takahiro910.eyebreak.core.Scheduler
import io.github.takahiro910.eyebreak.core.Settings
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DAY_LABELS = mapOf(
    DayOfWeek.MONDAY to "月",
    DayOfWeek.TUESDAY to "火",
    DayOfWeek.WEDNESDAY to "水",
    DayOfWeek.THURSDAY to "木",
    DayOfWeek.FRIDAY to "金",
    DayOfWeek.SATURDAY to "土",
    DayOfWeek.SUNDAY to "日",
)

private val NEXT_FORMAT = DateTimeFormatter.ofPattern("M/d(E) H:mm", Locale.JAPANESE)

private fun formatTime(minuteOfDay: Int): String =
    "%d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsState = remember { Prefs.settingsFlow(context) }.collectAsState(initial = null)
    val settings = settingsState.value

    // 「次回」表示を追従させるための時計tick(画面表示中のみ)
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            tick++
        }
    }

    fun change(transform: (Settings) -> Settings) {
        scope.launch {
            Prefs.update(context, transform)
            Reschedule.apply(context)
        }
    }

    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        if (settings == null) return@Scaffold
        val next = remember(settings, tick) { Scheduler.nextTrigger(LocalDateTime.now(), settings) }

        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            // マスターON/OFF
            item {
                ToggleChip(
                    checked = settings.enabled,
                    onCheckedChange = { on -> change { it.copy(enabled = on) } },
                    label = { Text("リマインダー") },
                    secondaryLabel = { Text(if (settings.enabled) "ON" else "OFF") },
                    toggleControl = { Switch(checked = settings.enabled) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 次回発火時刻
            item {
                Text(
                    text = when {
                        !settings.enabled -> "停止中"
                        next == null -> "予定なし"
                        else -> "次回 " + next.format(NEXT_FORMAT)
                    },
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 有効時間帯(30分刻み)
            item { SectionLabel("時間帯") }
            item {
                TimeStepper(
                    label = "開始",
                    minuteOfDay = settings.startMinuteOfDay,
                    onMinus = {
                        change { it.copy(startMinuteOfDay = (it.startMinuteOfDay - 30).coerceAtLeast(0)) }
                    },
                    onPlus = {
                        change {
                            it.copy(
                                startMinuteOfDay = (it.startMinuteOfDay + 30)
                                    .coerceAtMost(it.endMinuteOfDay - 30),
                            )
                        }
                    },
                )
            }
            item {
                TimeStepper(
                    label = "終了",
                    minuteOfDay = settings.endMinuteOfDay,
                    onMinus = {
                        change {
                            it.copy(
                                endMinuteOfDay = (it.endMinuteOfDay - 30)
                                    .coerceAtLeast(it.startMinuteOfDay + 30),
                            )
                        }
                    },
                    onPlus = {
                        change { it.copy(endMinuteOfDay = (it.endMinuteOfDay + 30).coerceAtMost(24 * 60)) }
                    },
                )
            }

            // 有効曜日
            item { SectionLabel("曜日") }
            item {
                DayRow(
                    days = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
                    activeDays = settings.activeDays,
                    onToggle = { day, on ->
                        change { it.copy(activeDays = if (on) it.activeDays + day else it.activeDays - day) }
                    },
                )
            }
            item {
                DayRow(
                    days = listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                    activeDays = settings.activeDays,
                    onToggle = { day, on ->
                        change { it.copy(activeDays = if (on) it.activeDays + day else it.activeDays - day) }
                    },
                )
            }

            // デバッグ用: 発火を待たずにテスト(debugビルドのみ)
            if (BuildConfig.DEBUG) {
                item {
                    Chip(
                        onClick = {
                            context.sendBroadcast(
                                Intent(context, AlarmReceiver::class.java)
                                    .setAction(AlarmScheduler.ACTION_START),
                            )
                        },
                        label = { Text("今すぐ発火 (テスト)") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption1,
        color = MaterialTheme.colors.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TimeStepper(
    label: String,
    minuteOfDay: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption2,
            modifier = Modifier.width(36.dp),
        )
        StepButton("−", onMinus)
        Text(
            text = formatTime(minuteOfDay),
            style = MaterialTheme.typography.button,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp),
        )
        StepButton("＋", onPlus)
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.secondaryButtonColors(),
        modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
    ) {
        Text(text)
    }
}

@Composable
private fun DayRow(
    days: List<DayOfWeek>,
    activeDays: Set<DayOfWeek>,
    onToggle: (DayOfWeek, Boolean) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth(),
    ) {
        days.forEach { day ->
            ToggleButton(
                checked = day in activeDays,
                onCheckedChange = { on -> onToggle(day, on) },
                modifier = Modifier.size(36.dp),
            ) {
                Text(DAY_LABELS.getValue(day), fontSize = 13.sp)
            }
        }
    }
}
