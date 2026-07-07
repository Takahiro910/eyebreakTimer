package io.github.takahiro910.eyebreak.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 再起動・アプリ更新・時刻/タイムゾーン変更後にアラームを再登録する。
 * (AlarmManagerの登録は再起動で消える。RTCアラームは時刻変更でずれる)
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        Reschedule.apply(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
