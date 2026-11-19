package com.example.game.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.game.audio.GameAudioEngine
import com.example.game.data.GamePreferencesRepository
import com.example.game.engine.GameEngineListener
import com.example.game.engine.PlayerInputState
import com.example.game.model.AppScreen
import com.example.game.model.CharacterSkin
import com.example.game.model.ControlLayout
import com.example.game.model.GameSettings
import com.example.game.model.GameSkins
import com.example.game.model.GameTelemetry
import com.example.game.model.GraphicsQuality
import com.example.game.model.LobbyModal
import com.example.game.model.PlayerProfileStats
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
    val telemetry: GameTelemetry = GameTelemetry(),
    val isPaused: Boolean = false,
    val bannerMessage: String? = null
)

class GameViewModel(application: Application) : AndroidViewModel(application), GameEngineListener {

    private val repo = GamePreferencesRepository(application.applicationContext)
    val audio = GameAudioEngine()
    val inputState = PlayerInputState()

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var lastSavedSessionScore = 0
    private var lastSavedDistance = 0f

    init {
        viewModelScope.launch {
            repo.gameDataFlow.collect { saved ->
                audio.musicVolume = saved.settings.musicVolume
                audio.sfxVolume = saved.settings.sfxVolume
                _uiState.update { state ->
                    state.copy(
                        selectedSkin = GameSkins.byId(saved.selectedSkinId),
                        settings = saved.settings,
                        profileStats = saved.stats
                    )
                }
            }
        }
        runSplashSequence()
    }

    private fun runSplashSequence() {
        viewModelScope.launch {
            val stages = listOf(
                0.18f to "Loading Real-World City Textures...",
                0.42f to "Building Skyscrapers & Boulevards...",
                0.68f to "Initializing 3D OpenGL ES Engine...",
                0.88f to "Spawning Traffic & Exploration Zones...",
                1.00f to "Welcome to Saini Simulator!"
            )
            for ((target, label) in stages) {
                delay(320L)
                _uiState.update { it.copy(splashProgress = target, splashStatus = label) }
            }
            delay(380L)
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
        audio.playClick()
        _uiState.update { it.copy(selectedSkin = skin) }
        viewModelScope.launch { repo.saveSelectedSkin(skin.id) }
    }

    fun updateSettings(transform: (GameSettings) -> GameSettings) {
        val updated = transform(_uiState.value.settings)
        audio.musicVolume = updated.musicVolume
        audio.sfxVolume = updated.sfxVolume
        _uiState.update { it.copy(settings = updated) }
        viewModelScope.launch { repo.saveSettings(updated) }
    }

    fun setGraphicsQuality(quality: GraphicsQuality) {
        audio.playClick()
        updateSettings { it.copy(graphicsQuality = quality) }
    }

    fun setControlLayout(layout: ControlLayout) {
        audio.playClick()
        updateSettings { it.copy(controlLayout = layout) }
    }

    fun startGame() {
        audio.playClick()
        audio.stopLobbyMusic()
        lastSavedSessionScore = 0
        lastSavedDistance = 0f
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.GAMEPLAY,
                activeModal = LobbyModal.NONE,
                isPaused = false,
                telemetry = GameTelemetry(),
                bannerMessage = "Explore Saini City & discover all 5 Landmark Zones!"
            )
        }
        clearBannerSoon()
    }

    fun togglePause() {
        audio.playClick()
        _uiState.update { it.copy(isPaused = !it.isPaused) }
    }

    fun setPaused(paused: Boolean) {
        _uiState.update { it.copy(isPaused = paused) }
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
                bannerMessage = "Exploration restarted at Saini Central Plaza"
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
                bannerMessage = null
            )
        }
        audio.startLobbyMusic()
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
        if (_uiState.value.currentScreen == AppScreen.GAMEPLAY) {
            _uiState.update { it.copy(isPaused = true) }
        }
    }

    fun onAppResume() {
        if (_uiState.value.currentScreen == AppScreen.LOBBY) {
            audio.startLobbyMusic()
        }
    }

    // --- GameEngineListener callbacks (called from GL thread) ---

    override fun onTelemetryUpdate(telemetry: GameTelemetry) {
        _uiState.update { it.copy(telemetry = telemetry) }
    }

    override fun onFootstep(isSprint: Boolean) {
        audio.playFootstep(isSprint)
    }

    override fun onJumpPerformed() {
        audio.playJump()
    }

    override fun onOrbCollected(orbId: Int, newScore: Int) {
        audio.playPickup()
        showBanner("Energy Star Collected! +100 PTS")
    }

    override fun onZoneDiscovered(zoneId: String, zoneName: String, bonusPoints: Int) {
        audio.playZoneDiscovered()
        showBanner("Zone Discovered: $zoneName (+$bonusPoints PTS)")
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
            delay(2800L)
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
