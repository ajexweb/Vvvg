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
import com.example.game.model.DailyMission
import com.example.game.model.GameSettings
import com.example.game.model.GameSkins
import com.example.game.model.GameStoreCatalog
import com.example.game.model.GameTelemetry
import com.example.game.model.GraphicsQuality
import com.example.game.model.GroundSurface
import com.example.game.model.LobbyModal
import com.example.game.model.MissionMetric
import com.example.game.model.PlayerProfileStats
import com.example.game.model.StoreItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameUiState(
    val currentScreen: AppScreen = AppScreen.SPLASH,
    val activeModal: LobbyModal = LobbyModal.NONE,
    val splashProgress: Float = 0f,
    val splashStatus: String = "Loading Saini City Assets...",
    val selectedSkin: CharacterSkin = GameSkins.all.first(),
    val settings: GameSettings = GameSettings(),
    val profileStats: PlayerProfileStats = PlayerProfileStats(),
    val ownedItemIds: Set<String> = GameStoreCatalog.defaultOwnedIds,
    val claimedMissionIds: Set<String> = emptySet(),
    val equippedGearIds: Set<String> = emptySet(),
    val lobbyToast: String? = null,
    val telemetry: GameTelemetry = GameTelemetry(),
    val isPaused: Boolean = false,
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

    init {
        viewModelScope.launch {
            repo.gameDataFlow.collect { saved ->
                audio.applyAudioSettings(
                    musicVol = saved.settings.musicVolume,
                    sfxVol = saved.settings.sfxVolume,
                    muted = saved.settings.isMuted
                )
                _uiState.update { state ->
                    state.copy(
                        selectedSkin = GameSkins.byId(saved.selectedSkinId),
                        settings = saved.settings,
                        profileStats = saved.stats,
                        ownedItemIds = saved.ownedItemIds,
                        claimedMissionIds = saved.claimedMissionIds,
                        equippedGearIds = saved.equippedGearIds
                    )
                }
            }
        }
        runSplashSequence()
    }

    private fun runSplashSequence() {
        viewModelScope.launch {
            val stages = listOf(
                0.22f to "Loading Saini City Textures & Props...",
                0.48f to "Initializing 3D Character Rig & Vehicles...",
                0.76f to "Preparing Open-World Districts & Airport...",
                1.00f to "Ready to Deploy"
            )
            for ((target, label) in stages) {
                delay(240L)
                _uiState.update { it.copy(splashProgress = target, splashStatus = label) }
            }
            delay(260L)
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
        showLobbyToast("Equipped ${skin.name}")
    }

    fun purchaseOrEquipStoreItem(item: StoreItem) {
        val state = _uiState.value
        val owned = item.id in state.ownedItemIds
        if (owned) {
            if (item.linkedSkinId != null) {
                selectSkin(GameSkins.byId(item.linkedSkinId))
            } else {
                audio.playClick()
                viewModelScope.launch { repo.toggleEquippedGear(item.id) }
                val isCurrentlyEquipped = item.id in state.equippedGearIds
                showLobbyToast(if (isCurrentlyEquipped) "Unequipped ${item.name}" else "Equipped ${item.name}")
            }
            return
        }

        val stats = state.profileStats
        if (stats.coins < item.priceCoins || stats.diamonds < item.priceDiamonds) {
            audio.playClick()
            val needed = if (item.priceDiamonds > 0) "${item.priceDiamonds} Diamonds" else "${item.priceCoins} Coins"
            showLobbyToast("Not enough currency! Need $needed.")
            return
        }

        viewModelScope.launch {
            val bought = repo.purchaseItem(
                itemId = item.id,
                costCoins = item.priceCoins,
                costDiamonds = item.priceDiamonds,
                linkedSkinId = item.linkedSkinId
            )
            if (bought) {
                audio.playPickup()
                showLobbyToast("Purchased & Equipped ${item.name}!")
            }
        }
    }

    fun claimMissionReward(mission: DailyMission) {
        val state = _uiState.value
        if (mission.id in state.claimedMissionIds) return
        val progress = missionProgressValue(mission, state.profileStats)
        if (progress < mission.targetValue) {
            audio.playClick()
            showLobbyToast("Complete this mission in Saini City first!")
            return
        }
        viewModelScope.launch {
            repo.claimMission(mission.id, mission.rewardCoins, mission.rewardDiamonds)
            audio.playZoneDiscovered()
            showLobbyToast("Claimed +${mission.rewardCoins} Coins & +${mission.rewardDiamonds} Diamonds!")
        }
    }

    fun missionProgressValue(mission: DailyMission, stats: PlayerProfileStats): Int {
        return when (mission.metric) {
            MissionMetric.ZONES -> stats.zonesDiscoveredTotal
            MissionMetric.DISTANCE -> stats.distanceMeters
            MissionMetric.SCORE -> stats.totalScore
        }
    }

    fun hasClaimableMissions(state: GameUiState): Boolean {
        return com.example.game.model.GameMissions.all.any { m ->
            m.id !in state.claimedMissionIds && missionProgressValue(m, state.profileStats) >= m.targetValue
        }
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
            muted = updated.isMuted
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
        updateSettings { it.copy(graphicsQuality = quality) }
    }

    fun setControlLayout(layout: ControlLayout) {
        audio.playClick()
        updateSettings { it.copy(controlLayout = layout) }
    }

    fun setTargetFps(fps: Int) {
        audio.playClick()
        updateSettings { it.copy(targetFps = fps) }
    }

    fun setStreamingRadius(meters: Float) {
        audio.playClick()
        updateSettings { it.copy(streamingRadiusMeters = meters) }
    }

    fun startGame() {
        audio.playGameStartDeploy()
        audio.startGameplayAudio()
        lastSavedSessionScore = 0
        lastSavedDistance = 0f
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.GAMEPLAY,
                activeModal = LobbyModal.NONE,
                isPaused = false,
                telemetry = GameTelemetry(),
                bannerMessage = "Welcome to Saini City"
            )
        }
        clearBannerSoon()
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
        _uiState.update {
            it.copy(
                isPaused = false,
                telemetry = GameTelemetry(),
                bannerMessage = "Restarted at Saini Central Chauraha"
            )
        }
        clearBannerSoon()
    }

    fun returnToLobby() {
        audio.playClick()
        commitPendingStats()
        inputState.moveX = 0f
        inputState.moveY = 0f
        inputState.isSprinting = false
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.LOBBY,
                isPaused = false,
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
        showBanner("$npcName: \"Welcome to Saini City!\"")
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
        showBanner("Gold Coin Collected +100 PTS")
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
