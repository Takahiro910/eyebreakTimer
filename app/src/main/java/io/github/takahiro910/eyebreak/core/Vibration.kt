package io.github.takahiro910.eyebreak.core

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager

/**
 * バイブレーション実行。開始と終了でパターンを明確に区別する。
 * - 開始: 長め2回(400ms - 休止200ms - 400ms)=「6m先を見ろ」
 * - 終了: 短め1回(150ms)=「画面に戻ってよし」
 */
object Vibration {

    private val START_TIMINGS = longArrayOf(0, 400, 200, 400)
    private const val END_DURATION_MS = 150L

    fun start(context: Context) {
        vibrate(context, VibrationEffect.createWaveform(START_TIMINGS, -1))
    }

    fun end(context: Context) {
        vibrate(context, VibrationEffect.createOneShot(END_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun vibrate(context: Context, effect: VibrationEffect) {
        val vibrator =
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        // アラーム用途として振動させる(スリープ中・画面OFF中でも確実に)
        val attributes = VibrationAttributes.Builder()
            .setUsage(VibrationAttributes.USAGE_ALARM)
            .build()
        vibrator.vibrate(effect, attributes)
    }
}
