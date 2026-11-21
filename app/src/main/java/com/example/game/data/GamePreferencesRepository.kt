package com.example.game.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.game.model.ControlLayout
import com.example.game.model.GameSettings
import com.example.game.model.GameSkins
import com.example.game.model.GameStoreCatalog
import com.example.game.model.GraphicsQuality
import com.example.game.model.PlayerProfileStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sainiDataStore by preferencesDataStore(name = "saini_simulator_prefs")

data class PersistedGameData(
    val selectedSkinId: String = GameSkins.all.first().id,
    val settings: GameSettings = GameSettings(),
    val stats: PlayerProfileStats = PlayerProfileStats(),
    val ownedItemIds: Set<String> = GameStoreCatalog.defaultOwnedIds,
    val claimedMissionIds: Set<String> = emptySet(),
    val equippedGearIds: Set<String> = emptySet()
)

class GamePreferencesRepository(private val context: Context) {

    private object Keys {
        val SELECTED_SKIN = stringPreferencesKey("selected_skin_id")
        val MUSIC_VOL = floatPreferencesKey("music_volume")
        val SFX_VOL = floatPreferencesKey("sfx_volume")
        val IS_MUTED = booleanPreferencesKey("is_muted")
        val GRAPHICS = stringPreferencesKey("graphics_quality")
        val CONTROL_LAYOUT = stringPreferencesKey("control_layout")
        val CAMERA_SENS = floatPreferencesKey("camera_sensitivity")
        val TARGET_FPS = intPreferencesKey("target_fps")
        val STREAMING_RADIUS = floatPreferencesKey("streaming_radius")
        val TOTAL_SCORE = intPreferencesKey("total_score")
        val HIGH_SCORE = intPreferencesKey("high_score")
        val COINS = intPreferencesKey("coins")
        val DIAMONDS = intPreferencesKey("diamonds")
        val DISTANCE_METERS = intPreferencesKey("distance_meters")
        val ZONES_DISCOVERED = intPreferencesKey("zones_discovered")
        val OWNED_ITEMS = stringPreferencesKey("owned_items_csv")
        val CLAIMED_MISSIONS = stringPreferencesKey("claimed_missions_csv")
        val EQUIPPED_GEAR = stringPreferencesKey("equipped_gear_csv")
    }

    val gameDataFlow: Flow<PersistedGameData> = context.sainiDataStore.data.map { prefs ->
        val graphics = runCatching {
            GraphicsQuality.valueOf(prefs[Keys.GRAPHICS] ?: GraphicsQuality.HIGH.name)
        }.getOrDefault(GraphicsQuality.HIGH)

        val controlLayout = runCatching {
            ControlLayout.valueOf(prefs[Keys.CONTROL_LAYOUT] ?: ControlLayout.JOYSTICK.name)
        }.getOrDefault(ControlLayout.JOYSTICK)

        val ownedRaw = prefs[Keys.OWNED_ITEMS]
        val ownedSet = if (ownedRaw.isNullOrBlank()) {
            GameStoreCatalog.defaultOwnedIds
        } else {
            ownedRaw.split(",").filter { it.isNotBlank() }.toSet() + GameStoreCatalog.defaultOwnedIds
        }

        val claimedRaw = prefs[Keys.CLAIMED_MISSIONS]
        val claimedSet = if (claimedRaw.isNullOrBlank()) {
            emptySet()
        } else {
            claimedRaw.split(",").filter { it.isNotBlank() }.toSet()
        }

        val equippedRaw = prefs[Keys.EQUIPPED_GEAR]
        val equippedSet = if (equippedRaw.isNullOrBlank()) {
            emptySet()
        } else {
            equippedRaw.split(",").filter { it.isNotBlank() }.toSet()
        }

        PersistedGameData(
            selectedSkinId = prefs[Keys.SELECTED_SKIN] ?: GameSkins.all.first().id,
            settings = GameSettings(
                musicVolume = prefs[Keys.MUSIC_VOL] ?: 0.65f,
                sfxVolume = prefs[Keys.SFX_VOL] ?: 0.85f,
                isMuted = prefs[Keys.IS_MUTED] ?: false,
                graphicsQuality = graphics,
                controlLayout = controlLayout,
                cameraSensitivity = prefs[Keys.CAMERA_SENS] ?: 1.0f,
                targetFps = prefs[Keys.TARGET_FPS] ?: 120,
                streamingRadiusMeters = prefs[Keys.STREAMING_RADIUS] ?: 56f
            ),
            stats = PlayerProfileStats(
                totalScore = prefs[Keys.TOTAL_SCORE] ?: 0,
                highScore = prefs[Keys.HIGH_SCORE] ?: 0,
                coins = prefs[Keys.COINS] ?: 1200,
                diamonds = prefs[Keys.DIAMONDS] ?: 150,
                distanceMeters = prefs[Keys.DISTANCE_METERS] ?: 0,
                zonesDiscoveredTotal = prefs[Keys.ZONES_DISCOVERED] ?: 0
            ),
            ownedItemIds = ownedSet,
            claimedMissionIds = claimedSet,
            equippedGearIds = equippedSet
        )
    }

    suspend fun saveSelectedSkin(skinId: String) {
        context.sainiDataStore.edit { it[Keys.SELECTED_SKIN] = skinId }
    }

    suspend fun saveSettings(settings: GameSettings) {
        context.sainiDataStore.edit { prefs ->
            prefs[Keys.MUSIC_VOL] = settings.musicVolume
            prefs[Keys.SFX_VOL] = settings.sfxVolume
            prefs[Keys.IS_MUTED] = settings.isMuted
            prefs[Keys.GRAPHICS] = settings.graphicsQuality.name
            prefs[Keys.CONTROL_LAYOUT] = settings.controlLayout.name
            prefs[Keys.CAMERA_SENS] = settings.cameraSensitivity
            prefs[Keys.TARGET_FPS] = settings.targetFps
            prefs[Keys.STREAMING_RADIUS] = settings.streamingRadiusMeters
        }
    }

    suspend fun purchaseItem(
        itemId: String,
        costCoins: Int,
        costDiamonds: Int,
        linkedSkinId: String?
    ): Boolean {
        var success = false
        context.sainiDataStore.edit { prefs ->
            val curCoins = prefs[Keys.COINS] ?: 1200
            val curDiamonds = prefs[Keys.DIAMONDS] ?: 150
            val owned = (prefs[Keys.OWNED_ITEMS]?.split(",")?.filter { it.isNotBlank() }?.toSet()
                ?: emptySet()) + GameStoreCatalog.defaultOwnedIds

            if (itemId in owned) {
                success = true
                return@edit
            }
            if (curCoins >= costCoins && curDiamonds >= costDiamonds) {
                prefs[Keys.COINS] = curCoins - costCoins
                prefs[Keys.DIAMONDS] = curDiamonds - costDiamonds
                prefs[Keys.OWNED_ITEMS] = (owned + itemId).joinToString(",")
                if (linkedSkinId != null) {
                    prefs[Keys.SELECTED_SKIN] = linkedSkinId
                } else {
                    val eq = prefs[Keys.EQUIPPED_GEAR]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                    prefs[Keys.EQUIPPED_GEAR] = (eq + itemId).joinToString(",")
                }
                success = true
            }
        }
        return success
    }

    suspend fun toggleEquippedGear(itemId: String) {
        context.sainiDataStore.edit { prefs ->
            val eq = prefs[Keys.EQUIPPED_GEAR]?.split(",")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
            if (itemId in eq) eq.remove(itemId) else eq.add(itemId)
            prefs[Keys.EQUIPPED_GEAR] = eq.joinToString(",")
        }
    }

    suspend fun claimMission(missionId: String, rewardCoins: Int, rewardDiamonds: Int) {
        context.sainiDataStore.edit { prefs ->
            val claimed = prefs[Keys.CLAIMED_MISSIONS]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            if (missionId !in claimed) {
                val curCoins = prefs[Keys.COINS] ?: 1200
                val curDiamonds = prefs[Keys.DIAMONDS] ?: 150
                prefs[Keys.COINS] = curCoins + rewardCoins
                prefs[Keys.DIAMONDS] = curDiamonds + rewardDiamonds
                prefs[Keys.CLAIMED_MISSIONS] = (claimed + missionId).joinToString(",")
            }
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
            val prevCoins = prefs[Keys.COINS] ?: 1200
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
