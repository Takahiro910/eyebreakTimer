package io.github.takahiro910.eyebreak.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * アラーム受信。振動を実行し、開始アラームなら「終了アラーム(+30秒)」と
 * 「次回の開始アラーム」を再登録する(ワンショット連鎖)。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_START -> {
                // 1) まず即振動(開始の合図)
                Vibration.start(context)

                // 2) 30秒後の終了バイブを登録
                AlarmScheduler.scheduleEnd(
                    context,
                    System.currentTimeMillis() + AlarmScheduler.END_DELAY_MILLIS,
                )

                // 3) 次回の開始アラームを再登録(DataStore読み出しがあるので goAsync)
                //    予定時刻より早く処理された場合でも同一スロットを再登録しないよう、
                //    基準時刻は max(現在時刻, 予定時刻) とする
                val scheduledAt = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0L)
                val baseMillis = max(System.currentTimeMillis(), scheduledAt)
                val base = LocalDateTime.ofInstant(Instant.ofEpochMilli(baseMillis), ZoneId.systemDefault())

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        Reschedule.apply(context, now = base)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            AlarmScheduler.ACTION_END -> {
                Vibration.end(context)
            }
        }
    }
}
