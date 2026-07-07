package io.github.takahiro910.eyebreak.core

import java.time.DayOfWeek

/**
 * リマインダー設定。純粋なデータクラス(Android非依存、ユニットテスト可能)。
 *
 * 時刻は「その日の0:00からの分数」で保持する(例: 9:00 = 540)。
 * [endMinuteOfDay] は排他的境界。end=1080(18:00)のとき、18:00ちょうどのスロットは発火しない。
 */
data class Settings(
    val enabled: Boolean = true,
    val startMinuteOfDay: Int = DEFAULT_START_MINUTE,
    val endMinuteOfDay: Int = DEFAULT_END_MINUTE,
    val activeDays: Set<DayOfWeek> = DEFAULT_ACTIVE_DAYS,
) {
    companion object {
        const val DEFAULT_START_MINUTE = 9 * 60 // 9:00
        const val DEFAULT_END_MINUTE = 18 * 60 // 18:00
        val DEFAULT_ACTIVE_DAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
    }
}
