package io.github.takahiro910.eyebreak.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * AlarmManagerへの登録(副作用側)。次回時刻の計算は [Scheduler](純粋関数)に委譲する。
 *
 * ワンショット連鎖方式: アラーム受信のたびに次の1回だけを setExactAndAllowWhileIdle で再登録する。
 */
object AlarmScheduler {

    const val ACTION_START = "io.github.takahiro910.eyebreak.action.START_BREAK"
    const val ACTION_END = "io.github.takahiro910.eyebreak.action.END_BREAK"

    /** 予定していた発火時刻(epoch millis)。早発火・時計補正時の二重発火ガードに使う。 */
    const val EXTRA_SCHEDULED_AT = "scheduled_at"

    /** 開始バイブの30秒後に終了バイブ。 */
    const val END_DELAY_MILLIS = 30_000L

    private const val REQUEST_START = 1
    private const val REQUEST_END = 2

    /**
     * 次回の開始アラームを登録する。次回が存在しない(OFF・曜日なし等)場合は登録を解除する。
     * @return 登録した次回発火時刻。未登録なら null。
     */
    fun scheduleNextStart(
        context: Context,
        settings: Settings,
        now: LocalDateTime = LocalDateTime.now(),
    ): LocalDateTime? {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val next = Scheduler.nextTrigger(now, settings)
        if (next == null) {
            alarmManager.cancel(startPendingIntent(context, 0L))
            return null
        }
        val triggerAtMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            startPendingIntent(context, triggerAtMillis),
        )
        return next
    }

    /** 終了バイブ用アラームを登録する(Handler/coroutine delayはプロセス死で消えるため不可)。 */
    fun scheduleEnd(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            endPendingIntent(context),
        )
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(startPendingIntent(context, 0L))
        alarmManager.cancel(endPendingIntent(context))
    }

    private fun startPendingIntent(context: Context, scheduledAtMillis: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_START,
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SCHEDULED_AT, scheduledAtMillis),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun endPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_END,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_END),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
