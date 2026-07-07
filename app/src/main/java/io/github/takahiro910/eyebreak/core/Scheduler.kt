package io.github.takahiro910.eyebreak.core

import java.time.LocalDateTime

/**
 * 次回発火時刻の計算。純粋関数のみ(Android非依存、ユニットテスト対象)。
 *
 * 発火スロットは毎時 00 / 20 / 40 分 = 「0:00からの20分の倍数」。
 */
object Scheduler {

    /** スロット間隔(分)。毎時 00/20/40 = 20分の倍数。 */
    const val SLOT_INTERVAL_MINUTES = 20

    /**
     * [now] より厳密に後の、次の発火時刻を返す。
     *
     * - 有効時間帯は [Settings.startMinuteOfDay] 以上 [Settings.endMinuteOfDay] 未満(終了は排他的)
     * - [now] がちょうどスロット時刻の場合は次のスロットを返す(二重発火防止)
     * - 無効(OFF)・有効曜日なし・有効なスロットが存在しない場合は null(アラーム未登録)
     */
    fun nextTrigger(now: LocalDateTime, settings: Settings): LocalDateTime? {
        if (!settings.enabled) return null
        if (settings.activeDays.isEmpty()) return null

        val start = settings.startMinuteOfDay
        val end = settings.endMinuteOfDay
        if (start >= end) return null

        // 時間帯開始以降で最初のスロット(20分の倍数へ切り上げ)
        val firstSlotMinute =
            (start + SLOT_INTERVAL_MINUTES - 1) / SLOT_INTERVAL_MINUTES * SLOT_INTERVAL_MINUTES

        // 今日から1週間ぶん探索すれば、有効曜日が1つでもあれば必ず見つかる
        for (dayOffset in 0L..7L) {
            val date = now.toLocalDate().plusDays(dayOffset)
            if (date.dayOfWeek !in settings.activeDays) continue

            var minute = firstSlotMinute
            while (minute < end) {
                val candidate = date.atStartOfDay().plusMinutes(minute.toLong())
                if (candidate.isAfter(now)) return candidate
                minute += SLOT_INTERVAL_MINUTES
            }
        }
        return null
    }
}
