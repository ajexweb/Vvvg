package com.example.game.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.game.audio.GameAudioEngine
import com.example.game.data.GamePreferencesRepository
import com.example.game.engine.GameEngineListener
import com.example.game.engine.PlayerInputState
import com.example.game.model.ActiveVehicleMode
import com.example.game.model.AppScreen
import com.example.game.model.CarCrashType
import com.example.game.model.CharacterSkin
import com.example.game.model.ControlLayout
import com.example.game.model.CustomHudLayout
import com.example.game.model.DensityLevel
import com.example.game.model.GameSettings
import com.example.game.model.GameSkins
import com.example.game.model.GameStoreCatalog
import com.example.game.model.GameTelemetry
import com.example.game.model.GraphicsQuality
import com.example.game.model.GroundSurface
import com.example.game.model.HudElementId
import com.example.game.model.LobbyModal
import com.example.game.model.PlayerProfileStats
import com.example.game.model.RenderResolution
import com.example.game.model.SpawnDistricts
import com.example.game.model.StoreItem
import com.example.game.model.VehicleControlMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameUiState(
    val currentScreen: AppScreen = AppScreen.SPLASH,
    val activeModal: LobbyModal = LobbyModal.NONE,
    val isPreferencesLoaded: Boolean = false,
    val isCustomHudEditorOpen: Boolean = false,
    val splashProgress: Float = 0f,
    val splashStatus: String = "Loading Saini State Assets...",
    val selectedSkin: CharacterSkin = GameSkins.all.first(),
    val settings: GameSettings = GameSettings(),
    val profileStats: PlayerProfileStats = PlayerProfileStats(),
    val ownedItemIds: Set<String> = GameStoreCatalog.defaultOwnedIds,
    val lobbyToast: String? = null,
    val telemetry: GameTelemetry = GameTelemetry(),
    val isPaused: Boolean = false,
    val isMatchLoading: Boolean = false,
    val matchLoadProgress: Float = 0f,
    val matchLoadStatus: String = "Initializing Saini State...",
    val bannerMessage: String? = null,
    val waypointX: Float? = null,
    val waypointZ: Float? = null
)

class GameViewModel(application: Application) : AndroidViewModel(application), GameEngineListener {

    private val repo = GamePreferencesRepository(application.applicationContext)
    val audio = GameAudioEngine(application.applicationContext)
    val inputState = PlayerInputState()

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var lastSavedSessionScore = 0
    private var lastSavedDistance = 0f
    @Volatile private var worldEngineReady = false
    @Volatile private var latestEngineProgress = 0f
    @Volatile private var latestEngineStageLabel: String? = null
    private var matchLoadJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            repo.ensureFreshProfileDefaults()
            repo.gameDataFlow.collect { saved ->
                audio.applyAudioSettings(
                    musicVol = saved.settings.musicVolume,
                    sfxVol = saved.settings.sfxVolume,
                    muted = saved.settings.isMuted,
                    ambientEnabled = saved.settings.ambientAudioEnabled
                )
                _uiState.update { state ->
                    state.copy(
                        isPreferencesLoaded = true,
                        selectedSkin = GameSkins.byId(saved.selectedSkinId),
                        settings = saved.settings,
                        profileStats = saved.stats,
                        ownedItemIds = saved.ownedItemIds
                    )
                }
            }
        }
        runSplashSequence()
    }

    private fun runSplashSequence() {
        viewModelScope.launch {
            val stages = listOf(
                0.24f to "Loading Saini State Textures & Props...",
                0.52f to "Initializing 3D Character Rig & Vehicles...",
                0.80f to "Preparing 4.8km Open-World Biomes...",
                1.00f to "Ready to Deploy"
            )
            for ((target, label) in stages) {
                delay(220L)
                _uiState.update { it.copy(splashProgress = target, splashStatus = label) }
            }
            delay(220L)
            _uiState.update { it.copy(currentScreen = AppScreen.LOBBY) }
            audio.startLobbyMusic()
        }
    }

    fun openModal(modal: LobbyModal) {
        audio.playClick()
        _uiState.update { it.copy(activeModal = modal) }
    }

    fun closeModal() {
        audio.playClick()
        _uiState.update { it.copy(activeModal = LobbyModal.NONE) }
    }

    fun selectSkin(skin: CharacterSkin) {
        audio.playSkinEquip()
        _uiState.update { it.copy(selectedSkin = skin) }
        viewModelScope.launch { repo.saveSelectedSkin(skin.id) }
    }

    fun purchaseOrEquipStoreItem(item: StoreItem) {
        val state = _uiState.value
        val owned = item.id in state.ownedItemIds
        if (owned) {
            audio.playSkinEquip()
            updateSettings { s ->
                s.copy(
                    selectedVehicleWrapId = item.linkedWrapId ?: s.selectedVehicleWrapId,
                    selectedEmoteId = item.linkedEmoteId ?: s.selectedEmoteId
                )
            }
            showLobbyToast("Equipped ${item.name}")
            return
        }

        val stats = state.profileStats
        if (stats.coins < item.priceCoins || stats.diamonds < item.priceDiamonds) {
            audio.playClick()
            val needed = if (item.priceDiamonds > 0) "${item.priceDiamonds} Diamonds" else "${item.priceCoins} Coins"
            showLobbyToast("Need $needed — explore zones & collect coins!")
            return
        }

        viewModelScope.launch {
            val bought = repo.purchaseItem(
                itemId = item.id,
                costCoins = item.priceCoins,
                costDiamonds = item.priceDiamonds,
                linkedWrapId = item.linkedWrapId,
                linkedEmoteId = item.linkedEmoteId
            )
            if (bought) {
                audio.playPickup()
                showLobbyToast("Unlocked & Equipped ${item.name}!")
            }
        }
    }

    fun savePlayerProfile(name: String, avatarId: String) {
        val clean = name.trim().take(16)
        if (clean.length < 2) return
        audio.playClick()
        _uiState.update { state ->
            state.copy(
                profileStats = state.profileStats.copy(
                    playerName = clean,
                    hasSetUsername = true,
                    selectedAvatarId = avatarId
                )
            )
        }
        viewModelScope.launch {
            repo.savePlayerProfile(clean, avatarId)
        }
        showLobbyToast("Profile updated: $clean")
    }

    fun openCustomHudEditor() {
        audio.playClick()
        _uiState.update { it.copy(isCustomHudEditorOpen = true) }
    }

    fun closeCustomHudEditor() {
        audio.playClick()
        _uiState.update { it.copy(isCustomHudEditorOpen = false) }
    }

    fun saveCustomHudLayout(layout: CustomHudLayout) {
        audio.playClick()
        updateSettings { it.copy(customHudLayout = layout) }
        _uiState.update { it.copy(isCustomHudEditorOpen = false) }
        showLobbyToast("Custom HUD Layout Saved!")
    }

    private fun showLobbyToast(message: String) {
        _uiState.update { it.copy(lobbyToast = message) }
        viewModelScope.launch {
            delay(2400L)
            _uiState.update { if (it.lobbyToast == message) it.copy(lobbyToast = null) else it }
        }
    }

    fun updateSettings(transform: (GameSettings) -> GameSettings) {
        val updated = transform(_uiState.value.settings)
        audio.applyAudioSettings(
            musicVol = updated.musicVolume,
            sfxVol = updated.sfxVolume,
            muted = updated.isMuted,
            ambientEnabled = updated.ambientAudioEnabled
        )
        _uiState.update { it.copy(settings = updated) }
        viewModelScope.launch { repo.saveSettings(updated) }
    }

    fun toggleMute() {
        val nextMuted = !_uiState.value.settings.isMuted
        updateSettings { it.copy(isMuted = nextMuted) }
        if (!nextMuted) audio.playClick()
    }

    fun setGraphicsQuality(quality: GraphicsQuality) {
        audio.playClick()
        // Smart preset sync: auto-tunes shadows/grass/resolution for genuine instant FPS improvement!
        updateSettings { cur ->
            when (quality) {
                GraphicsQuality.LOW -> cur.copy(
                    graphicsQuality = quality,
                    renderResolution = RenderResolution.PERFORMANCE_75,
                    shadowsEnabled = false,
                    grassDensity = DensityLevel.LOW,
                    streamingRadiusMeters = 36f
                )
                GraphicsQuality.MEDIUM -> cur.copy(
                    graphicsQuality = quality,
                    renderResolution = RenderResolution.BALANCED_85,
                    shadowsEnabled = false,
                    grassDensity = DensityLevel.MEDIUM,
                    streamingRadiusMeters = 52f
                )
                GraphicsQuality.HIGH -> cur.copy(
                    graphicsQuality = quality,
                    renderResolution = RenderResolution.NATIVE_100,
                    shadowsEnabled = true,
                    grassDensity = DensityLevel.HIGH,
                    streamingRadiusMeters = 72f
                )
            }
        }
    }

    fun setControlLayout(layout: ControlLayout) {
        audio.playClick()
        updateSettings { it.copy(controlLayout = layout) }
    }

    fun setVehicleControlMode(mode: VehicleControlMode) {
        audio.playClick()
        updateSettings { it.copy(vehicleControlMode = mode) }
    }

    fun setTargetFps(fps: Int) {
        audio.playClick()
        updateSettings { it.copy(targetFps = fps) }
    }

    fun setStreamingRadius(meters: Float) {
        audio.playClick()
        updateSettings { it.copy(streamingRadiusMeters = meters) }
    }

    fun toggleFpsCounter(show: Boolean) {
        audio.playClick()
        updateSettings { cur ->
            val fpsCfg = cur.customHudLayout.configFor(HudElementId.FPS_COUNTER)
            cur.copy(customHudLayout = cur.customHudLayout.updated(HudElementId.FPS_COUNTER, fpsCfg.copy(visible = show)))
        }
    }

    fun cycleSpawnDistrict(delta: Int) {
        audio.playClick()
        val all = SpawnDistricts.all
        val curIdx = all.indexOfFirst { it.id == _uiState.value.settings.selectedSpawnId }.coerceAtLeast(0)
        val next = all[(curIdx + delta + all.size) % all.size]
        updateSettings { it.copy(selectedSpawnId = next.id) }
    }

    fun startGame() {
        audio.playGameStartDeploy()
        audio.stopLobbyMusic()
        lastSavedSessionScore = 0
        lastSavedDistance = 0f
        worldEngineReady = false
        latestEngineProgress = 0f
        latestEngineStageLabel = null
        inputState.resetAll()
        val spawn = SpawnDistricts.byId(_uiState.value.settings.selectedSpawnId)
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.GAMEPLAY,
                activeModal = LobbyModal.NONE,
                isPaused = false,
                isMatchLoading = true,
                matchLoadProgress = 0.02f,
                matchLoadStatus = "Deploying to ${spawn.name}...",
                telemetry = GameTelemetry(playerX = spawn.x, playerZ = spawn.z),
                bannerMessage = null
            )
        }
        runMatchLoadingSequence(spawn.name)
    }

    private fun runMatchLoadingSequence(spawnName: String) {
        matchLoadJob?.cancel()
        matchLoadJob = viewModelScope.launch {
            val minDurationMs = 2200L
            val safetyTimeoutMs = 45000L
            val startMs = System.currentTimeMillis()
            var smoothedProgress = 0.02f
            while (true) {
                val elapsed = System.currentTimeMillis() - startMs
                val timeFrac = (elapsed.toFloat() / minDurationMs.toFloat()).coerceIn(0f, 1f)
                val engineFrac = latestEngineProgress.coerceIn(0f, 1f)

                val targetProgress = if (worldEngineReady) {
                    maxOf(timeFrac, 0.98f)
                } else {
                    maxOf(engineFrac * 0.96f, minOf(timeFrac * 0.14f, 0.14f)).coerceAtMost(0.96f)
                }
                smoothedProgress = (smoothedProgress + (targetProgress - smoothedProgress) * 0.24f)
                    .coerceIn(smoothedProgress, if (worldEngineReady) 1.0f else 0.96f)

                val fallbackLabel = when {
                    smoothedProgress < 0.25f -> "Initializing 3D World Engine..."
                    smoothedProgress < 0.50f -> "Streaming City & Vehicle Assets..."
                    smoothedProgress < 0.72f -> "Loading Character Animations & Map..."
                    smoothedProgress < 0.95f -> "Pre-baking 3D Terrain & Foliage..."
                    else -> if (worldEngineReady) "Entering $spawnName..." else "Warming Up Smooth GPU Pipeline..."
                }
                val label = latestEngineStageLabel ?: fallbackLabel
                _uiState.update {
                    it.copy(
                        matchLoadProgress = smoothedProgress,
                        matchLoadStatus = label
                    )
                }
                if (elapsed >= minDurationMs && (worldEngineReady || elapsed >= safetyTimeoutMs)) {
                    break
                }
                delay(36L)
            }
            _uiState.update {
                it.copy(
                    matchLoadProgress = 1.0f,
                    matchLoadStatus = "Entering $spawnName..."
                )
            }
            delay(140L)
            audio.startGameplayAudio()
            _uiState.update {
                it.copy(
                    isMatchLoading = false,
                    bannerMessage = "Deployed: $spawnName"
                )
            }
            clearBannerSoon()
        }
    }

    fun togglePause() {
        audio.playClick()
        val nextPaused = !_uiState.value.isPaused
        _uiState.update { it.copy(isPaused = nextPaused) }
        if (nextPaused) audio.pauseGameplayLoops() else audio.resumeGameplayLoops()
    }

    fun restartSession(resetEngine: () -> Unit) {
        audio.playClick()
        commitPendingStats()
        lastSavedSessionScore = 0
        lastSavedDistance = 0f
        resetEngine()
        val spawn = SpawnDistricts.byId(_uiState.value.settings.selectedSpawnId)
        _uiState.update {
            it.copy(
                isPaused = false,
                telemetry = GameTelemetry(playerX = spawn.x, playerZ = spawn.z),
                bannerMessage = "Respawned at ${spawn.name}"
            )
        }
        clearBannerSoon()
    }

    fun returnToLobby() {
        audio.playClick()
        matchLoadJob?.cancel()
        commitPendingStats()
        inputState.resetAll()
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.LOBBY,
                isPaused = false,
                isMatchLoading = false,
                activeModal = LobbyModal.NONE,
                bannerMessage = null,
                waypointX = null,
                waypointZ = null
            )
        }
        audio.startLobbyMusic()
    }

    fun setMapWaypoint(worldX: Float, worldZ: Float) {
        audio.playClick()
        _uiState.update { it.copy(waypointX = worldX, waypointZ = worldZ) }
    }

    fun clearMapWaypoint() {
        audio.playClick()
        _uiState.update { it.copy(waypointX = null, waypointZ = null) }
    }

    override fun onWaypointReached() {
        audio.playZoneDiscovered()
        _uiState.update {
            it.copy(
                waypointX = null,
                waypointZ = null,
                bannerMessage = "Destination Reached!"
            )
        }
        clearBannerSoon()
    }

    private fun commitPendingStats() {
        val t = _uiState.value.telemetry
        val deltaScore = (t.sessionScore - lastSavedSessionScore).coerceAtLeast(0)
        val deltaDist = (t.distanceThisSession - lastSavedDistance).toInt().coerceAtLeast(0)
        val coinsEarned = deltaScore / 2
        if (deltaScore > 0 || deltaDist > 0 || t.discoveredZoneIds.isNotEmpty()) {
            lastSavedSessionScore = t.sessionScore
            lastSavedDistance = t.distanceThisSession
            viewModelScope.launch {
                repo.recordSessionRewards(
                    sessionScore = deltaScore,
                    coinsEarned = coinsEarned,
                    distanceMeters = deltaDist,
                    zonesCount = t.discoveredZoneIds.size
                )
            }
        }
    }

    fun onAppPause() {
        audio.stopLobbyMusic()
        audio.pauseGameplayLoops()
        if (_uiState.value.currentScreen == AppScreen.GAMEPLAY) {
            _uiState.update { it.copy(isPaused = true) }
        }
    }

    fun onAppResume() {
        if (_uiState.value.currentScreen == AppScreen.LOBBY) {
            audio.startLobbyMusic()
        } else if (_uiState.value.currentScreen == AppScreen.GAMEPLAY && !_uiState.value.isPaused) {
            audio.resumeGameplayLoops()
        }
    }

    // --- GameEngineListener callbacks (from GL thread) ---

    override fun onWorldLoadProgress(progress: Float, stageLabel: String) {
        latestEngineProgress = progress.coerceIn(0f, 1f)
        latestEngineStageLabel = stageLabel
    }

    override fun onWorldReady() {
        latestEngineProgress = 1f
        worldEngineReady = true
    }

    override fun onTelemetryUpdate(telemetry: GameTelemetry) {
        _uiState.update { it.copy(telemetry = telemetry) }
    }

    override fun onFootstep(isSprint: Boolean, surface: GroundSurface) {
        audio.playFootstep(isSprint, surface)
    }

    override fun onJumpPerformed() {
        audio.playJump()
    }

    override fun onPlayerLand(heavy: Boolean) {
        audio.playPlayerLand(heavy)
    }

    override fun onPlayerWallVault() {
        audio.playWallVault()
    }

    override fun onCombatAction(isKick: Boolean) {
        if (isKick) audio.playWallVault() else audio.playJump()
    }

    override fun onDoorToggled(buildingName: String, opening: Boolean) {
        audio.playDoorSound(opening)
        showBanner(if (opening) "$buildingName Opened" else "$buildingName Closed")
    }

    override fun onIronGateToggled(gateName: String, opening: Boolean) {
        audio.playIronGate(opening)
        showBanner(if (opening) "$gateName Opened" else "$gateName Closed")
    }

    override fun onIronGateMoving() {
        audio.playIronGate(opening = true)
    }

    override fun onIronGateSlammed(gateName: String) {
        audio.playIronGate(opening = false)
        showBanner("$gateName Closed")
    }

    override fun onCarEntered() {
        audio.playCarEnterSequence()
    }

    override fun onCarExited() {
        audio.playCarExitSequence()
    }

    override fun onCarAccelerateRev() {
        audio.playCarAccelerateRev()
    }

    override fun onCarEmergencyBrakeSkid() {
        audio.playCarEmergencyBrakeSkid()
    }

    override fun onCarCrash(type: CarCrashType) {
        audio.playCarCrash(type)
    }

    override fun onCarHorn() {
        audio.playCarHorn()
    }

    override fun onJetFlyby(distanceMeters: Float) {
        audio.playJetFlyby(distanceMeters)
    }

    override fun onNpcGreeting(npcName: String) {
        audio.playClick()
        showBanner("$npcName: \"Welcome to Saini State!\"")
    }

    override fun onAmbientAudioUpdate(
        vehicleMode: ActiveVehicleMode,
        vehicleSpeedRatio: Float,
        heliDistMeters: Float,
        heliTakeoffFactor: Float,
        oceanDistMeters: Float,
        rainIntensity: Float,
        nightFactor: Float,
        isIndoors: Boolean,
        nearTreesFactor: Float
    ) {
        audio.updateLiveAudio(
            vehicleMode = vehicleMode,
            vehicleSpeedRatio = vehicleSpeedRatio,
            heliDistMeters = heliDistMeters,
            heliTakeoffFactor = heliTakeoffFactor,
            oceanDistMeters = oceanDistMeters,
            rainIntensity = rainIntensity,
            nightFactor = nightFactor,
            isIndoors = isIndoors,
            nearTreesFactor = nearTreesFactor
        )
    }

    override fun onOrbCollected(orbId: Int, newScore: Int) {
        audio.playPickup()
        showBanner("Coin Collected +100 PTS")
    }

    override fun onZoneDiscovered(zoneId: String, zoneName: String, bonusPoints: Int) {
        audio.playZoneDiscovered()
        showBanner("Discovered: $zoneName (+$bonusPoints PTS)")
    }

    override fun onInteractMessage(message: String) {
        audio.playClick()
        showBanner(message)
    }

    private fun showBanner(msg: String) {
        _uiState.update { it.copy(bannerMessage = msg) }
        clearBannerSoon()
    }

    private fun clearBannerSoon() {
        val snapshot = _uiState.value.bannerMessage
        viewModelScope.launch {
            delay(2600L)
            _uiState.update { state ->
                if (state.bannerMessage == snapshot) state.copy(bannerMessage = null) else state
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audio.release()
    }
}
