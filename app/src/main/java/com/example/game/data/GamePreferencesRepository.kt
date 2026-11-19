package com.example.game.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.game.model.ControlLayout
import com.example.game.model.GameSettings
import com.example.game.model.GameSkins
import com.example.game.model.GraphicsQuality
import com.example.game.model.PlayerProfileStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sainiDataStore by preferencesDataStore(name = "saini_simulator_prefs")

data class PersistedGameData(
    val selectedSkinId: String = GameSkins.all.first().id,
    val settings: GameSettings = GameSettings(),
    val stats: PlayerProfileStats = PlayerProfileStats()
)

class GamePreferencesRepository(private val context: Context) {

    private object Keys {
        val SELECTED_SKIN = stringPreferencesKey("selected_skin_id")
        val MUSIC_VOL = floatPreferencesKey("music_volume")
        val SFX_VOL = floatPreferencesKey("sfx_volume")
        val GRAPHICS = stringPreferencesKey("graphics_quality")
        val CONTROL_LAYOUT = stringPreferencesKey("control_layout")
        val CAMERA_SENS = floatPreferencesKey("camera_sensitivity")
        val TOTAL_SCORE = intPreferencesKey("total_score")
        val HIGH_SCORE = intPreferencesKey("high_score")
        val COINS = intPreferencesKey("coins")
        val DISTANCE_METERS = intPreferencesKey("distance_meters")
        val ZONES_DISCOVERED = intPreferencesKey("zones_discovered")
    }

    val gameDataFlow: Flow<PersistedGameData> = context.sainiDataStore.data.map { prefs ->
        val graphics = runCatching {
            GraphicsQuality.valueOf(prefs[Keys.GRAPHICS] ?: GraphicsQuality.HIGH.name)
        }.getOrDefault(GraphicsQuality.HIGH)

        val controlLayout = runCatching {
            ControlLayout.valueOf(prefs[Keys.CONTROL_LAYOUT] ?: ControlLayout.JOYSTICK.name)
        }.getOrDefault(ControlLayout.JOYSTICK)

        PersistedGameData(
            selectedSkinId = prefs[Keys.SELECTED_SKIN] ?: GameSkins.all.first().id,
            settings = GameSettings(
                musicVolume = prefs[Keys.MUSIC_VOL] ?: 0.65f,
                sfxVolume = prefs[Keys.SFX_VOL] ?: 0.85f,
                graphicsQuality = graphics,
                controlLayout = controlLayout,
                cameraSensitivity = prefs[Keys.CAMERA_SENS] ?: 1.0f
            ),
            stats = PlayerProfileStats(
                totalScore = prefs[Keys.TOTAL_SCORE] ?: 0,
                highScore = prefs[Keys.HIGH_SCORE] ?: 0,
                coins = prefs[Keys.COINS] ?: 500,
                distanceMeters = prefs[Keys.DISTANCE_METERS] ?: 0,
                zonesDiscoveredTotal = prefs[Keys.ZONES_DISCOVERED] ?: 0
            )
        )
    }

    suspend fun saveSelectedSkin(skinId: String) {
        context.sainiDataStore.edit { it[Keys.SELECTED_SKIN] = skinId }
    }

    suspend fun saveSettings(settings: GameSettings) {
        context.sainiDataStore.edit { prefs ->
            prefs[Keys.MUSIC_VOL] = settings.musicVolume
            prefs[Keys.SFX_VOL] = settings.sfxVolume
            prefs[Keys.GRAPHICS] = settings.graphicsQuality.name
            prefs[Keys.CONTROL_LAYOUT] = settings.controlLayout.name
            prefs[Keys.CAMERA_SENS] = settings.cameraSensitivity
        }
    }

    suspend fun recordSessionRewards(
        sessionScore: Int,
        coinsEarned: Int,
        distanceMeters: Int,
        zonesCount: Int
    ) {
        context.sainiDataStore.edit { prefs ->
            val prevTotal = prefs[Keys.TOTAL_SCORE] ?: 0
            val prevHigh = prefs[Keys.HIGH_SCORE] ?: 0
            val prevCoins = prefs[Keys.COINS] ?: 500
            val prevDist = prefs[Keys.DISTANCE_METERS] ?: 0
            val prevZones = prefs[Keys.ZONES_DISCOVERED] ?: 0

            prefs[Keys.TOTAL_SCORE] = prevTotal + sessionScore
            if (sessionScore > prevHigh) {
                prefs[Keys.HIGH_SCORE] = sessionScore
            }
            prefs[Keys.COINS] = prevCoins + coinsEarned
            prefs[Keys.DISTANCE_METERS] = prevDist + distanceMeters
            if (zonesCount > prevZones) {
                prefs[Keys.ZONES_DISCOVERED] = zonesCount
            }
        }
    }
}
