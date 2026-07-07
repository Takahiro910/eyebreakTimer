package io.github.takahiro910.eyebreak.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import io.github.takahiro910.eyebreak.core.Prefs
import io.github.takahiro910.eyebreak.core.Reschedule
import io.github.takahiro910.eyebreak.core.Settings
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 文字盤スワイプでアクセスするTile。
 * - マスターON/OFFトグル(TileService内で完結: DataStore更新→アラーム再計算→Tile再描画)
 * - 次回発火時刻の表示(OFF時は「停止中」)
 */
class EyeBreakTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            serviceScope.launch {
                try {
                    // トグルボタンのタップ(LoadAction)で再リクエストされた場合はON/OFFを反転
                    if (requestParams.currentState.lastClickableId == ID_TOGGLE) {
                        Prefs.update(this@EyeBreakTileService) { it.copy(enabled = !it.enabled) }
                    }
                    // アラーム再計算(自分自身の再描画中なのでTile更新要求は不要)
                    val (settings, next) =
                        Reschedule.apply(this@EyeBreakTileService, requestTileUpdate = false)
                    completer.set(buildTile(settings, next, requestParams.deviceConfiguration))
                } catch (t: Throwable) {
                    completer.setException(t)
                }
            }
            "EyeBreakTileService.onTileRequest"
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(
                ResourceBuilders.Resources.Builder()
                    .setVersion(RESOURCES_VERSION)
                    .build(),
            )
            "EyeBreakTileService.onTileResourcesRequest"
        }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildTile(
        settings: Settings,
        next: LocalDateTime?,
        device: DeviceParameters,
    ): TileBuilders.Tile {
        val statusText = when {
            !settings.enabled -> "停止中"
            next == null -> "予定なし"
            else -> "次 " + next.format(TIME_FORMAT)
        }

        val toggleClickable = Clickable.Builder()
            .setId(ID_TOGGLE)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

        val titleLabel = Text.Builder(this, "EyeBreak")
            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
            .setColor(argb(COLOR_GRAY))
            .build()

        val statusContent = Text.Builder(this, statusText)
            .setTypography(Typography.TYPOGRAPHY_TITLE1)
            .setColor(argb(COLOR_WHITE))
            .build()

        val toggleChip = CompactChip.Builder(
            this,
            if (settings.enabled) "OFFにする" else "ONにする",
            toggleClickable,
            device,
        ).build()

        val layout = PrimaryLayout.Builder(device)
            .setResponsiveContentInsetEnabled(true)
            .setPrimaryLabelTextContent(titleLabel)
            .setContent(statusContent)
            .setPrimaryChipContent(toggleChip)
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MILLIS)
            .setTileTimeline(Timeline.fromLayoutElement(layout))
            .build()
    }

    companion object {
        private const val RESOURCES_VERSION = "1"
        private const val ID_TOGGLE = "toggle"
        private const val FRESHNESS_INTERVAL_MILLIS = 20 * 60 * 1000L
        private const val COLOR_WHITE = 0xFFFFFFFF.toInt()
        private const val COLOR_GRAY = 0xFF9E9E9E.toInt()
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm")
    }
}
