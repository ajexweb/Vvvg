package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.game.model.AppScreen
import com.example.game.model.LobbyModal
import com.example.game.viewmodel.GameViewModel
import com.example.ui.screens.CreditsModal
import com.example.ui.screens.GameplayScreen
import com.example.ui.screens.LobbyScreen
import com.example.ui.screens.ProfileModal
import com.example.ui.screens.SettingsModal
import com.example.ui.screens.SkinsModal
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.SainiSimulatorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SainiSimulatorTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                                    onOpenModal = viewModel::openModal
                                )
                            }

                            AppScreen.GAMEPLAY -> {
                                GameplayScreen(
                                    uiState = uiState,
                                    inputState = viewModel.inputState,
                                    engineListener = viewModel,
                                    onTogglePause = viewModel::togglePause,
                                    onRestart = viewModel::restartSession,
                                    onReturnToLobby = viewModel::returnToLobby
                                )
                            }
                        }
                    }

                    // Lobby Modal Overlays
                    if (uiState.currentScreen == AppScreen.LOBBY) {
                        when (uiState.activeModal) {
                            LobbyModal.NONE -> Unit
                            LobbyModal.PROFILE -> ProfileModal(
                                stats = uiState.profileStats,
                                activeSkin = uiState.selectedSkin,
                                onDismiss = viewModel::closeModal
                            )
                            LobbyModal.SETTINGS -> SettingsModal(
                                settings = uiState.settings,
                                onUpdateSettings = viewModel::updateSettings,
                                onSetGraphics = viewModel::setGraphicsQuality,
                                onSetControlLayout = viewModel::setControlLayout,
                                onDismiss = viewModel::closeModal
                            )
                            LobbyModal.SKINS -> SkinsModal(
                                selectedSkin = uiState.selectedSkin,
                                onSelectSkin = viewModel::selectSkin,
                                onDismiss = viewModel::closeModal
                            )
                            LobbyModal.CREDITS -> CreditsModal(
                                onDismiss = viewModel::closeModal
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppPause()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResume()
    }
}
