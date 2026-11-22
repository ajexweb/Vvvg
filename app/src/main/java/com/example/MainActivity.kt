package com.example

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.game.model.AppScreen
import com.example.game.model.LobbyModal
import com.example.game.viewmodel.GameViewModel
import com.example.ui.components.UsernameOnboardingOverlay
import com.example.ui.screens.CreditsModal
import com.example.ui.screens.CustomHudEditorOverlay
import com.example.ui.screens.GameplayScreen
import com.example.ui.screens.LobbyScreen
import com.example.ui.screens.ProfileModal
import com.example.ui.screens.SettingsModal
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StoreModal
import com.example.ui.theme.SainiSimulatorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        applyImmersiveFullscreen()
        setContent {
            SainiSimulatorTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(uiState.settings.targetFps) {
                    applyPreferredRefreshRate(uiState.settings.targetFps)
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Crossfade(
                        targetState = uiState.currentScreen,
                        label = "screen_transition"
                    ) { screen ->
                        when (screen) {
                            AppScreen.SPLASH -> {
                                SplashScreen(
                                    progress = uiState.splashProgress,
                                    statusText = uiState.splashStatus
                                )
                            }

                            AppScreen.LOBBY -> {
                                LobbyScreen(
                                    uiState = uiState,
                                    onStartGame = viewModel::startGame,
                                    onToggleMute = viewModel::toggleMute,
                                    onOpenModal = viewModel::openModal,
                                    onCloseModal = viewModel::closeModal,
                                    onOpenCustomHud = viewModel::openCustomHudEditor,
                                    onCycleSpawnDistrict = viewModel::cycleSpawnDistrict,
                                    onToggleStartNight = {
                                        viewModel.updateSettings { it.copy(startNightMode = !it.startNightMode) }
                                    },
                                    onToggleStartRain = {
                                        viewModel.updateSettings { it.copy(startRainMode = !it.startRainMode) }
                                    },
                                    onSelectSkin = viewModel::selectSkin
                                )
                            }

                            AppScreen.GAMEPLAY -> {
                                GameplayScreen(
                                    uiState = uiState,
                                    inputState = viewModel.inputState,
                                    engineListener = viewModel,
                                    onTogglePause = viewModel::togglePause,
                                    onToggleMute = viewModel::toggleMute,
                                    onOpenCustomHud = viewModel::openCustomHudEditor,
                                    onSetVehicleControlMode = viewModel::setVehicleControlMode,
                                    onRestart = viewModel::restartSession,
                                    onReturnToLobby = viewModel::returnToLobby,
                                    onSetWaypoint = viewModel::setMapWaypoint,
                                    onClearWaypoint = viewModel::clearMapWaypoint
                                )
                            }
                        }
                    }

                    // Official Game Lobby Modals
                    if (uiState.currentScreen == AppScreen.LOBBY && !uiState.isCustomHudEditorOpen) {
                        when (uiState.activeModal) {
                            LobbyModal.NONE, LobbyModal.CHARACTERS -> Unit
                            LobbyModal.STORE -> StoreModal(
                                uiState = uiState,
                                onPurchaseOrEquip = viewModel::purchaseOrEquipStoreItem,
                                onDismiss = viewModel::closeModal
                            )
                            LobbyModal.PROFILE -> ProfileModal(
                                stats = uiState.profileStats,
                                activeSkin = uiState.selectedSkin,
                                onSaveProfile = viewModel::savePlayerProfile,
                                onDismiss = viewModel::closeModal
                            )
                            LobbyModal.SETTINGS -> SettingsModal(
                                settings = uiState.settings,
                                onToggleMute = viewModel::toggleMute,
                                onUpdateSettings = viewModel::updateSettings,
                                onSetGraphics = viewModel::setGraphicsQuality,
                                onSetControlLayout = viewModel::setControlLayout,
                                onSetVehicleControlMode = viewModel::setVehicleControlMode,
                                onSetTargetFps = viewModel::setTargetFps,
                                onSetStreamingRadius = viewModel::setStreamingRadius,
                                onToggleFpsCounter = viewModel::toggleFpsCounter,
                                onOpenCustomHud = viewModel::openCustomHudEditor,
                                onDismiss = viewModel::closeModal
                            )
                            LobbyModal.CREDITS -> CreditsModal(
                                onDismiss = viewModel::closeModal
                            )
                        }
                    }

                    // First-Time Launch Username & Tactical Avatar Onboarding Prompt
                    if (uiState.currentScreen == AppScreen.LOBBY &&
                        uiState.isPreferencesLoaded &&
                        !uiState.profileStats.hasSetUsername
                    ) {
                        UsernameOnboardingOverlay(
                            initialAvatarId = uiState.profileStats.selectedAvatarId,
                            onComplete = viewModel::savePlayerProfile
                        )
                    }

                    // Full-Screen Drag-and-Drop Custom HUD Editor (accessible from Settings & Pause Menu)
                    if (uiState.isCustomHudEditorOpen) {
                        CustomHudEditorOverlay(
                            initialLayout = uiState.settings.customHudLayout,
                            onSave = viewModel::saveCustomHudLayout,
                            onCancel = viewModel::closeCustomHudEditor
                        )
                    }
                }
            }
        }
    }

    private fun applyPreferredRefreshRate(targetFps: Int) {
        val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        } ?: return

        val modes = currentDisplay.supportedModes
        if (modes.isEmpty()) return

        val bestMode = modes
            .filter { it.refreshRate <= targetFps + 2f }
            .maxByOrNull { it.refreshRate }
            ?: modes.maxByOrNull { it.refreshRate }
            ?: return

        val params = window.attributes
        if (params.preferredDisplayModeId != bestMode.modeId) {
            params.preferredDisplayModeId = bestMode.modeId
            window.attributes = params
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveFullscreen()
    }

    private fun applyImmersiveFullscreen() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveFullscreen()
        viewModel.onAppResume()
    }
}
