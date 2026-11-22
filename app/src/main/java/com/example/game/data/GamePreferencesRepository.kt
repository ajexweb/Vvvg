package com.example.game.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.game.model.ControlLayout
import com.example.game.model.CustomHudLayout
import com.example.game.model.DensityLevel
import com.example.game.model.GameAvatars
import com.example.game.model.GameSettings
import com.example.game.model.GameSkins
import com.example.game.model.GameStoreCatalog
import com.example.game.model.GraphicsQuality
import com.example.game.model.PlayerProfileStats
import com.example.game.model.RenderResolution
import com.example.game.model.VehicleControlMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sainiDataStore by preferencesDataStore(name = "saini_simulator_prefs")

data class PersistedGameData(
    val selectedSkinId: String = GameSkins.all.first().id,
    val settings: GameSettings = GameSettings(),
    val stats: PlayerProfileStats = PlayerProfileStats(),
    val ownedItemIds: Set<String> = GameStoreCatalog.defaultOwnedIds
)

class GamePreferencesRepository(private val context: Context) {

    companion object {
        private const val FRESH_PROFILE_SCHEMA_VERSION = 3
    }

    private object Keys {
        val PROFILE_SCHEMA_VERSION = intPreferencesKey("profile_schema_version")
        val SELECTED_SKIN = stringPreferencesKey("selected_skin_id")
        val PLAYER_NAME = stringPreferencesKey("player_name")
        val HAS_SET_USERNAME = booleanPreferencesKey("has_set_username")
        val SELECTED_AVATAR = stringPreferencesKey("selected_avatar_id")
        val MUSIC_VOL = floatPreferencesKey("music_volume")
        val SFX_VOL = floatPreferencesKey("sfx_volume")
        val IS_MUTED = booleanPreferencesKey("is_muted")
        val AMBIENT_AUDIO = booleanPreferencesKey("ambient_audio_enabled")
        val GRAPHICS = stringPreferencesKey("graphics_quality")
        val RENDER_RES = stringPreferencesKey("render_resolution")
        val SHADOWS_ENABLED = booleanPreferencesKey("shadows_enabled")
        val GRASS_DENSITY = stringPreferencesKey("grass_density")
        val AI_TRAFFIC_DENSITY = stringPreferencesKey("ai_traffic_density")
        val NPC_DENSITY = stringPreferencesKey("npc_density")
        val DYNAMIC_DAY_NIGHT = booleanPreferencesKey("dynamic_day_night")
        val CONTROL_LAYOUT = stringPreferencesKey("control_layout")
        val VEHICLE_CONTROL_MODE = stringPreferencesKey("vehicle_control_mode")
        val CAMERA_SENS = floatPreferencesKey("camera_sensitivity")
        val INVERT_CAM_Y = booleanPreferencesKey("invert_camera_y")
        val AUTO_CENTER_VEH_CAM = booleanPreferencesKey("auto_center_vehicle_cam")
        val AUTO_SPRINT = booleanPreferencesKey("auto_sprint")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val HUD_OPACITY = floatPreferencesKey("hud_opacity")
        val MINIMAP_AUTO_ZOOM = booleanPreferencesKey("minimap_auto_zoom")
        val TARGET_FPS = intPreferencesKey("target_fps")
        val STREAMING_RADIUS = floatPreferencesKey("streaming_radius")
        val SELECTED_SPAWN = stringPreferencesKey("selected_spawn_id")
        val START_NIGHT = booleanPreferencesKey("start_night_mode")
        val START_RAIN = booleanPreferencesKey("start_rain_mode")
        val SELECTED_WRAP = stringPreferencesKey("selected_vehicle_wrap_id")
        val SELECTED_EMOTE = stringPreferencesKey("selected_emote_id")
        val CUSTOM_HUD_LAYOUT_V2 = stringPreferencesKey("custom_hud_layout_v2")
        val TOTAL_SCORE = intPreferencesKey("total_score")
        val HIGH_SCORE = intPreferencesKey("high_score")
        val COINS = intPreferencesKey("coins")
        val DIAMONDS = intPreferencesKey("diamonds")
        val DISTANCE_METERS = intPreferencesKey("distance_meters")
        val ZONES_DISCOVERED = intPreferencesKey("zones_discovered")
        val OWNED_ITEMS = stringPreferencesKey("owned_items_csv")
    }

    val gameDataFlow: Flow<PersistedGameData> = context.sainiDataStore.data.map { prefs ->
        val graphics = runCatching {
            GraphicsQuality.valueOf(prefs[Keys.GRAPHICS] ?: GraphicsQuality.MEDIUM.name)
        }.getOrDefault(GraphicsQuality.MEDIUM)

        val renderRes = runCatching {
            RenderResolution.valueOf(prefs[Keys.RENDER_RES] ?: RenderResolution.BALANCED_85.name)
        }.getOrDefault(RenderResolution.BALANCED_85)

        val grassDensity = runCatching {
            DensityLevel.valueOf(prefs[Keys.GRASS_DENSITY] ?: DensityLevel.MEDIUM.name)
        }.getOrDefault(DensityLevel.MEDIUM)

        val trafficDensity = runCatching {
            DensityLevel.valueOf(prefs[Keys.AI_TRAFFIC_DENSITY] ?: DensityLevel.MEDIUM.name)
        }.getOrDefault(DensityLevel.MEDIUM)

        val npcDensity = runCatching {
            DensityLevel.valueOf(prefs[Keys.NPC_DENSITY] ?: DensityLevel.MEDIUM.name)
        }.getOrDefault(DensityLevel.MEDIUM)

        val controlLayout = runCatching {
            ControlLayout.valueOf(prefs[Keys.CONTROL_LAYOUT] ?: ControlLayout.JOYSTICK.name)
        }.getOrDefault(ControlLayout.JOYSTICK)

        val vehicleCtrlMode = runCatching {
            VehicleControlMode.valueOf(prefs[Keys.VEHICLE_CONTROL_MODE] ?: VehicleControlMode.BUTTONS.name)
        }.getOrDefault(VehicleControlMode.BUTTONS)

        val validCatalogIds = GameStoreCatalog.items.map { it.id }.toSet()
        val ownedRaw = prefs[Keys.OWNED_ITEMS]
        val ownedSet = if (ownedRaw.isNullOrBlank()) {
            GameStoreCatalog.defaultOwnedIds
        } else {
            (ownedRaw.split(",").filter { it in validCatalogIds }.toSet()) + GameStoreCatalog.defaultOwnedIds
        }

        val savedName = prefs[Keys.PLAYER_NAME]?.trim().orEmpty()
        val hasUsername = (prefs[Keys.HAS_SET_USERNAME] ?: false) && savedName.isNotEmpty()
        val savedAvatar = prefs[Keys.SELECTED_AVATAR] ?: GameAvatars.all.first().id

        PersistedGameData(
            selectedSkinId = GameSkins.all.first().id,
            settings = GameSettings(
                musicVolume = prefs[Keys.MUSIC_VOL] ?: 0.65f,
                sfxVolume = prefs[Keys.SFX_VOL] ?: 0.85f,
                isMuted = prefs[Keys.IS_MUTED] ?: false,
                ambientAudioEnabled = prefs[Keys.AMBIENT_AUDIO] ?: true,
                graphicsQuality = graphics,
                renderResolution = renderRes,
                shadowsEnabled = prefs[Keys.SHADOWS_ENABLED] ?: false,
                grassDensity = grassDensity,
                aiTrafficDensity = trafficDensity,
                npcDensity = npcDensity,
                dynamicDayNight = prefs[Keys.DYNAMIC_DAY_NIGHT] ?: true,
                controlLayout = controlLayout,
                vehicleControlMode = vehicleCtrlMode,
                cameraSensitivity = prefs[Keys.CAMERA_SENS] ?: 1.0f,
                invertCameraY = prefs[Keys.INVERT_CAM_Y] ?: false,
                autoCenterVehicleCam = prefs[Keys.AUTO_CENTER_VEH_CAM] ?: true,
                autoSprint = prefs[Keys.AUTO_SPRINT] ?: false,
                hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
                hudOpacity = prefs[Keys.HUD_OPACITY] ?: 0.92f,
                minimapAutoZoom = prefs[Keys.MINIMAP_AUTO_ZOOM] ?: true,
                targetFps = prefs[Keys.TARGET_FPS] ?: 60,
                streamingRadiusMeters = prefs[Keys.STREAMING_RADIUS] ?: 52f,
                selectedSpawnId = prefs[Keys.SELECTED_SPAWN] ?: "saini_plaza",
                startNightMode = prefs[Keys.START_NIGHT] ?: false,
                startRainMode = prefs[Keys.START_RAIN] ?: false,
                selectedVehicleWrapId = prefs[Keys.SELECTED_WRAP] ?: "wrap_factory",
                selectedEmoteId = prefs[Keys.SELECTED_EMOTE] ?: "emote_flip",
                customHudLayout = CustomHudLayout.deserialize(prefs[Keys.CUSTOM_HUD_LAYOUT_V2])
            ),
            stats = PlayerProfileStats(
                playerName = savedName,
                hasSetUsername = hasUsername,
                selectedAvatarId = savedAvatar,
                totalScore = prefs[Keys.TOTAL_SCORE] ?: 0,
                highScore = prefs[Keys.HIGH_SCORE] ?: 0,
                coins = prefs[Keys.COINS] ?: 0,
                diamonds = prefs[Keys.DIAMONDS] ?: 0,
                distanceMeters = prefs[Keys.DISTANCE_METERS] ?: 0,
                zonesDiscoveredTotal = prefs[Keys.ZONES_DISCOVERED] ?: 0
            ),
            ownedItemIds = ownedSet
        )
    }

    suspend fun ensureFreshProfileDefaults() {
        context.sainiDataStore.edit { prefs ->
            val currentSchema = prefs[Keys.PROFILE_SCHEMA_VERSION] ?: 1
            if (currentSchema < FRESH_PROFILE_SCHEMA_VERSION) {
                if (currentSchema < 2) {
                    prefs[Keys.TOTAL_SCORE] = 0
                    prefs[Keys.HIGH_SCORE] = 0
                    prefs[Keys.COINS] = 0
                    prefs[Keys.DIAMONDS] = 0
                    prefs[Keys.DISTANCE_METERS] = 0
                    prefs[Keys.ZONES_DISCOVERED] = 0
                }
                // Upgrade to lag-free balanced performance defaults & clean real-only catalog
                prefs[Keys.GRAPHICS] = GraphicsQuality.MEDIUM.name
                prefs[Keys.RENDER_RES] = RenderResolution.BALANCED_85.name
                prefs[Keys.SHADOWS_ENABLED] = false
                prefs[Keys.GRASS_DENSITY] = DensityLevel.MEDIUM.name
                prefs[Keys.STREAMING_RADIUS] = 52f
                prefs[Keys.TARGET_FPS] = 60
                prefs[Keys.VEHICLE_CONTROL_MODE] = VehicleControlMode.BUTTONS.name
                prefs[Keys.SELECTED_SKIN] = GameSkins.all.first().id
                prefs[Keys.SELECTED_WRAP] = "wrap_factory"
                prefs[Keys.SELECTED_EMOTE] = "emote_flip"
                val validIds = GameStoreCatalog.items.map { it.id }.toSet()
                val existingOwned = prefs[Keys.OWNED_ITEMS]?.split(",")?.filter { it in validIds }?.toSet() ?: emptySet()
                prefs[Keys.OWNED_ITEMS] = (existingOwned + GameStoreCatalog.defaultOwnedIds).joinToString(",")
                prefs[Keys.PROFILE_SCHEMA_VERSION] = FRESH_PROFILE_SCHEMA_VERSION
            }
        }
    }

    suspend fun savePlayerProfile(playerName: String, avatarId: String) {
        val clean = playerName.trim().take(16)
        if (clean.isEmpty()) return
        context.sainiDataStore.edit { prefs ->
            prefs[Keys.PLAYER_NAME] = clean
            prefs[Keys.HAS_SET_USERNAME] = true
            prefs[Keys.SELECTED_AVATAR] = avatarId
        }
    }

    suspend fun saveSelectedSkin(skinId: String) {
        context.sainiDataStore.edit { it[Keys.SELECTED_SKIN] = skinId }
    }

    suspend fun saveSettings(settings: GameSettings) {
        context.sainiDataStore.edit { prefs ->
            prefs[Keys.MUSIC_VOL] = settings.musicVolume
            prefs[Keys.SFX_VOL] = settings.sfxVolume
            prefs[Keys.IS_MUTED] = settings.isMuted
            prefs[Keys.AMBIENT_AUDIO] = settings.ambientAudioEnabled
            prefs[Keys.GRAPHICS] = settings.graphicsQuality.name
            prefs[Keys.RENDER_RES] = settings.renderResolution.name
            prefs[Keys.SHADOWS_ENABLED] = settings.shadowsEnabled
            prefs[Keys.GRASS_DENSITY] = settings.grassDensity.name
            prefs[Keys.AI_TRAFFIC_DENSITY] = settings.aiTrafficDensity.name
            prefs[Keys.NPC_DENSITY] = settings.npcDensity.name
            prefs[Keys.DYNAMIC_DAY_NIGHT] = settings.dynamicDayNight
            prefs[Keys.CONTROL_LAYOUT] = settings.controlLayout.name
            prefs[Keys.VEHICLE_CONTROL_MODE] = settings.vehicleControlMode.name
            prefs[Keys.CAMERA_SENS] = settings.cameraSensitivity
            prefs[Keys.INVERT_CAM_Y] = settings.invertCameraY
            prefs[Keys.AUTO_CENTER_VEH_CAM] = settings.autoCenterVehicleCam
            prefs[Keys.AUTO_SPRINT] = settings.autoSprint
            prefs[Keys.HAPTICS_ENABLED] = settings.hapticsEnabled
            prefs[Keys.HUD_OPACITY] = settings.hudOpacity
            prefs[Keys.MINIMAP_AUTO_ZOOM] = settings.minimapAutoZoom
            prefs[Keys.TARGET_FPS] = settings.targetFps
            prefs[Keys.STREAMING_RADIUS] = settings.streamingRadiusMeters
            prefs[Keys.SELECTED_SPAWN] = settings.selectedSpawnId
            prefs[Keys.START_NIGHT] = settings.startNightMode
            prefs[Keys.START_RAIN] = settings.startRainMode
            prefs[Keys.SELECTED_WRAP] = settings.selectedVehicleWrapId
            prefs[Keys.SELECTED_EMOTE] = settings.selectedEmoteId
            prefs[Keys.CUSTOM_HUD_LAYOUT_V2] = settings.customHudLayout.serialize()
        }
    }

    suspend fun purchaseItem(
        itemId: String,
        costCoins: Int,
        costDiamonds: Int,
        linkedWrapId: String?,
        linkedEmoteId: String?
    ): Boolean {
        var success = false
        context.sainiDataStore.edit { prefs ->
            val curCoins = prefs[Keys.COINS] ?: 0
            val curDiamonds = prefs[Keys.DIAMONDS] ?: 0
            val owned = (prefs[Keys.OWNED_ITEMS]?.split(",")?.filter { it.isNotBlank() }?.toSet()
                ?: emptySet()) + GameStoreCatalog.defaultOwnedIds

            if (itemId in owned) {
                if (linkedWrapId != null) prefs[Keys.SELECTED_WRAP] = linkedWrapId
                if (linkedEmoteId != null) prefs[Keys.SELECTED_EMOTE] = linkedEmoteId
                success = true
                return@edit
            }
            if (curCoins >= costCoins && curDiamonds >= costDiamonds) {
                prefs[Keys.COINS] = curCoins - costCoins
                prefs[Keys.DIAMONDS] = curDiamonds - costDiamonds
                prefs[Keys.OWNED_ITEMS] = (owned + itemId).joinToString(",")
                if (linkedWrapId != null) prefs[Keys.SELECTED_WRAP] = linkedWrapId
                if (linkedEmoteId != null) prefs[Keys.SELECTED_EMOTE] = linkedEmoteId
                success = true
            }
        }
        return success
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
            val prevCoins = prefs[Keys.COINS] ?: 0
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
