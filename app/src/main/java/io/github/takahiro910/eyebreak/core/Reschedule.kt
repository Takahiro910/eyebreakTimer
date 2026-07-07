package io.github.takahiro910.eyebreak.core

import android.content.Context
import androidx.wear.tiles.TileService
import io.github.takahiro910.eyebreak.tile.EyeBreakTileService
import java.time.LocalDateTime

/**
 * 「設定読み出し → アラーム再登録 → Tile更新要求」の共通処理。
 * Activity / Tile / 各Receiver から呼ばれる。
 */
object Reschedule {

    suspend fun apply(
        context: Context,
        requestTileUpdate: Boolean = true,
        now: LocalDateTime = LocalDateTime.now(),
    ): Pair<Settings, LocalDateTime?> {
        val settings = Prefs.read(context)
        val next = AlarmScheduler.scheduleNextStart(context, settings, now)
        if (!settings.enabled) {
            // OFF中は一切発火しない: 保留中の終了バイブも含めて解除
            AlarmScheduler.cancelAll(context)
        }
        if (requestTileUpdate) {
            TileService.getUpdater(context).requestUpdate(EyeBreakTileService::class.java)
        }
        return settings to next
    }
}
