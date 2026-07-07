package io.github.takahiro910.eyebreak.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "eyebreak_settings")

/** DataStore (Preferences) ラッパー。設定の読み書きを担当。 */
object Prefs {

    private val KEY_ENABLED = booleanPreferencesKey("enabled")
    private val KEY_START_MINUTE = intPreferencesKey("start_minute_of_day")
    private val KEY_END_MINUTE = intPreferencesKey("end_minute_of_day")
    private val KEY_ACTIVE_DAYS = stringSetPreferencesKey("active_days")

    fun settingsFlow(context: Context): Flow<Settings> =
        context.dataStore.data.map { it.toSettings() }

    suspend fun read(context: Context): Settings =
        settingsFlow(context).first()

    /** 設定を変換して保存し、保存後の設定を返す。 */
    suspend fun update(context: Context, transform: (Settings) -> Settings): Settings {
        val newPrefs = context.dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs[KEY_ENABLED] = updated.enabled
            prefs[KEY_START_MINUTE] = updated.startMinuteOfDay
            prefs[KEY_END_MINUTE] = updated.endMinuteOfDay
            prefs[KEY_ACTIVE_DAYS] = updated.activeDays.map { it.name }.toSet()
        }
        return newPrefs.toSettings()
    }

    private fun Preferences.toSettings(): Settings = Settings(
        enabled = this[KEY_ENABLED] ?: true,
        startMinuteOfDay = this[KEY_START_MINUTE] ?: Settings.DEFAULT_START_MINUTE,
        endMinuteOfDay = this[KEY_END_MINUTE] ?: Settings.DEFAULT_END_MINUTE,
        activeDays = this[KEY_ACTIVE_DAYS]
            ?.mapNotNull { name -> runCatching { DayOfWeek.valueOf(name) }.getOrNull() }
            ?.toSet()
            ?: Settings.DEFAULT_ACTIVE_DAYS,
    )
}
