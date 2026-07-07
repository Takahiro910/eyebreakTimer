package io.github.takahiro910.eyebreak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.MaterialTheme
import io.github.takahiro910.eyebreak.core.Reschedule
import io.github.takahiro910.eyebreak.ui.SettingsScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 起動のたびに現在の設定でアラームを登録し直す(初回インストール後の登録もここで行われる)
        lifecycleScope.launch {
            Reschedule.apply(this@MainActivity)
        }

        setContent {
            MaterialTheme {
                SettingsScreen()
            }
        }
    }
}
