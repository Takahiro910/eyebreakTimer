package io.github.takahiro910.eyebreak.core

import java.time.DayOfWeek
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Scheduler.nextTrigger の仕様(6.1)テスト。
 *
 * 基準日: 2026-07-06(月) 〜 2026-07-12(日)
 */
class SchedulerTest {

    private val weekdaySettings = Settings(
        enabled = true,
        startMinuteOfDay = 9 * 60, // 9:00
        endMinuteOfDay = 18 * 60, // 18:00
        activeDays = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        ),
    )

    // Mon=6, Tue=7, ..., Fri=10, Sat=11, Sun=12, next Mon=13
    private fun monday(hour: Int, minute: Int, second: Int = 0): LocalDateTime =
        LocalDateTime.of(2026, 7, 6, hour, minute, second)

    // --- 仕様 6.1 ---

    @Test
    fun `1 平日10時05分は10時20分を返す`() {
        val next = Scheduler.nextTrigger(monday(10, 5), weekdaySettings)
        assertEquals(monday(10, 20), next)
    }

    @Test
    fun `2 平日17時55分で終了18時なら翌有効日9時00分を返す`() {
        val next = Scheduler.nextTrigger(monday(17, 55), weekdaySettings)
        assertEquals(LocalDateTime.of(2026, 7, 7, 9, 0), next)
    }

    @Test
    fun `3 金曜18時30分は土日を飛ばして月曜9時00分を返す`() {
        val friday = LocalDateTime.of(2026, 7, 10, 18, 30)
        val next = Scheduler.nextTrigger(friday, weekdaySettings)
        assertEquals(LocalDateTime.of(2026, 7, 13, 9, 0), next)
    }

    @Test
    fun `4 ちょうど発火時刻の場合は次スロットを返す(二重発火防止)`() {
        val next = Scheduler.nextTrigger(monday(10, 20, 0), weekdaySettings)
        assertEquals(monday(10, 40), next)
    }

    @Test
    fun `5 曜日全OFF時はnullを返す`() {
        val next = Scheduler.nextTrigger(monday(10, 5), weekdaySettings.copy(activeDays = emptySet()))
        assertNull(next)
    }

    // --- 追加の境界・堅牢性テスト ---

    @Test
    fun `マスターOFF時はnullを返す`() {
        val next = Scheduler.nextTrigger(monday(10, 5), weekdaySettings.copy(enabled = false))
        assertNull(next)
    }

    @Test
    fun `時間帯前は当日の最初のスロット9時00分を返す`() {
        val next = Scheduler.nextTrigger(monday(6, 30), weekdaySettings)
        assertEquals(monday(9, 0), next)
    }

    @Test
    fun `終了18時のとき最終スロットは17時40分`() {
        val next = Scheduler.nextTrigger(monday(17, 39), weekdaySettings)
        assertEquals(monday(17, 40), next)
    }

    @Test
    fun `17時40分ちょうどなら翌有効日9時00分(18時00分スロットは存在しない)`() {
        val next = Scheduler.nextTrigger(monday(17, 40, 0), weekdaySettings)
        assertEquals(LocalDateTime.of(2026, 7, 7, 9, 0), next)
    }

    @Test
    fun `開始9時30分のとき最初のスロットは9時40分(20分の倍数へ切り上げ)`() {
        val settings = weekdaySettings.copy(startMinuteOfDay = 9 * 60 + 30)
        val next = Scheduler.nextTrigger(monday(9, 0), settings)
        assertEquals(monday(9, 40), next)
    }

    @Test
    fun `無効曜日(日曜)の昼は月曜9時00分を返す`() {
        val sunday = LocalDateTime.of(2026, 7, 12, 12, 0)
        val next = Scheduler.nextTrigger(sunday, weekdaySettings)
        assertEquals(LocalDateTime.of(2026, 7, 13, 9, 0), next)
    }

    @Test
    fun `全曜日有効なら土曜も発火する`() {
        val allDays = weekdaySettings.copy(activeDays = DayOfWeek.entries.toSet())
        val saturday = LocalDateTime.of(2026, 7, 11, 10, 5)
        val next = Scheduler.nextTrigger(saturday, allDays)
        assertEquals(LocalDateTime.of(2026, 7, 11, 10, 20), next)
    }

    @Test
    fun `秒単位の途中(10時19分59秒)は10時20分を返す`() {
        val next = Scheduler.nextTrigger(monday(10, 19, 59), weekdaySettings)
        assertEquals(monday(10, 20), next)
    }

    @Test
    fun `開始と終了が同じ(退化した時間帯)ならnullを返す`() {
        val settings = weekdaySettings.copy(startMinuteOfDay = 540, endMinuteOfDay = 540)
        assertNull(Scheduler.nextTrigger(monday(10, 5), settings))
    }

    @Test
    fun `終了24時00分まで有効なら23時40分が最終スロット`() {
        val settings = weekdaySettings.copy(startMinuteOfDay = 23 * 60, endMinuteOfDay = 24 * 60)
        val next = Scheduler.nextTrigger(monday(23, 41), settings)
        // 23:40の次は翌日(火曜)の23:00
        assertEquals(LocalDateTime.of(2026, 7, 7, 23, 0), next)
    }
}
