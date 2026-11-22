package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.engine.InteractiveLobbyCharacter3D
import com.example.game.engine.LobbyCameraPreset
import com.example.game.engine.LobbyPoseMode
import com.example.game.model.CharacterSkin
import com.example.game.model.CityZones
import com.example.game.model.GameEmotes
import com.example.game.model.GameVehicleWraps
import com.example.game.model.LobbyModal
import com.example.game.model.PlayerProfileStats
import com.example.game.model.SpawnDistricts
import com.example.game.viewmodel.GameUiState
import com.example.ui.components.TacticalAvatar
import com.example.ui.theme.CityCard
import com.example.ui.theme.CityObsidian
import com.example.ui.theme.CoinSilver
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DiamondBlue
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.TacticalAccent
import com.example.ui.theme.TacticalAccentDark
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalSilver
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private val LobbyGlass = Color(0xFF0E131C).copy(alpha = 0.80f)
private val LobbyHairline = Color.White.copy(alpha = 0.14f)

@Composable
fun LobbyScreen(
    uiState: GameUiState,
    onStartGame: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenModal: (LobbyModal) -> Unit,
    onCloseModal: () -> Unit,
    onOpenCustomHud: () -> Unit,
    onCycleSpawnDistrict: (Int) -> Unit,
    onToggleStartNight: () -> Unit,
    onToggleStartRain: () -> Unit,
    onSelectSkin: (CharacterSkin) -> Unit
) {
    var zoomNormalized by remember { mutableFloatStateOf(LobbyCameraPreset.FULL_BODY.zoomNormalized) }
    var yawDeg by remember { mutableFloatStateOf(12f) }
    var poseMode by remember { mutableStateOf(LobbyPoseMode.STAND) }

    val isCharacterInspectorOpen = uiState.activeModal == LobbyModal.CHARACTERS
    val cameraHorizontalShift = if (isCharacterInspectorOpen) 0.48f else 0f

    // Pause the background 3D lobby OpenGL thread whenever any fullscreen modal or HUD editor covers the lobby!
    val pauseLobbyGl = (uiState.activeModal != LobbyModal.NONE && !isCharacterInspectorOpen) ||
        uiState.isCustomHudEditorOpen ||
        (uiState.isPreferencesLoaded && !uiState.profileStats.hasSetUsername)

    LaunchedEffect(isCharacterInspectorOpen) {
        if (!isCharacterInspectorOpen && zoomNormalized > 0.45f) {
            zoomNormalized = LobbyCameraPreset.FULL_BODY.zoomNormalized
        }
    }

    val activeSpawn = remember(uiState.settings.selectedSpawnId) {
        SpawnDistricts.byId(uiState.settings.selectedSpawnId)
    }
    val activeWrap = remember(uiState.settings.selectedVehicleWrapId) {
        GameVehicleWraps.byId(uiState.settings.selectedVehicleWrapId)
    }
    val activeEmote = remember(uiState.settings.selectedEmoteId) {
        GameEmotes.byId(uiState.settings.selectedEmoteId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CityObsidian)
            .testTag("lobby_screen")
    ) {
        // 1. Live Interactive 3D Character Studio Viewport (auto-pauses when modals open to eliminate GPU lag!)
        InteractiveLobbyCharacter3D(
            zoomNormalized = zoomNormalized,
            poseMode = poseMode,
            yawDeg = yawDeg,
            horizontalShift = cameraHorizontalShift,
            isPaused = pauseLobbyGl,
            onYawChange = { yawDeg = it },
            onZoomChange = { zoomNormalized = it },
            modifier = Modifier.fillMaxSize(),
            testTag = "lobby_3d_character"
        )

        // Subtle side vignette so HUD controls pop crisply
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            CityObsidian.copy(alpha = 0.62f),
                            Color.Transparent,
                            Color.Transparent,
                            CityObsidian.copy(alpha = 0.64f)
                        )
                    )
                )
        )

        // 2. Rich Tactical Open-World Lobby HUD
        if (!isCharacterInspectorOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // TOP STRIP: Player Profile Pill (Left) + Currencies & Utility Icons (Right)
                LobbyTopBar(
                    stats = uiState.profileStats,
                    isMuted = uiState.settings.isMuted,
                    onToggleMute = onToggleMute,
                    onProfileClick = { onOpenModal(LobbyModal.PROFILE) },
                    onSettingsClick = { onOpenModal(LobbyModal.SETTINGS) },
                    onCreditsClick = { onOpenModal(LobbyModal.CREDITS) },
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // LEFT VERTICAL RAIL: Real Menu Actions + Active Loadout Summary
                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(top = 6.dp)
                ) {
                    LobbySideMenuTile(
                        icon = Icons.Rounded.Storefront,
                        label = "STORE",
                        accent = TacticalAccent,
                        testTag = "btn_lobby_store",
                        onClick = { onOpenModal(LobbyModal.STORE) }
                    )
                    LobbySideMenuTile(
                        icon = Icons.Rounded.Person,
                        label = "CHARACTER",
                        accent = TacticalCyan,
                        testTag = "btn_lobby_characters",
                        onClick = { onOpenModal(LobbyModal.CHARACTERS) }
                    )
                    LobbySideMenuTile(
                        icon = Icons.Rounded.Tune,
                        label = "HUD LAYOUT",
                        accent = EmeraldPulse,
                        testTag = "btn_lobby_hud",
                        onClick = onOpenCustomHud
                    )
                    LobbySideMenuTile(
                        icon = Icons.Rounded.Settings,
                        label = "SETTINGS",
                        accent = TacticalSilver,
                        testTag = "btn_lobby_settings_rail",
                        onClick = { onOpenModal(LobbyModal.SETTINGS) }
                    )
                }

                // BOTTOM-LEFT: Active Real Loadout Card (Vehicle Wrap + 3D Emote + Graphics)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .width(156.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(LobbyGlass)
                            .border(0.8.dp, LobbyHairline, RoundedCornerShape(5.dp))
                            .clickable { onOpenModal(LobbyModal.STORE) }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("lobby_loadout_card")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(activeWrap.swatchColor)
                                    .border(0.6.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                            )
                            Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(11.dp))
                            Text(
                                text = activeWrap.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Rounded.EmojiEmotions, contentDescription = null, tint = EmeraldPulse, modifier = Modifier.size(11.dp))
                            Text(
                                text = "Emote: ${activeEmote.shortLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = "${uiState.settings.graphicsQuality.label.uppercase()} • ${uiState.settings.targetFps} FPS • ${uiState.settings.renderResolution.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TacticalCyan,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "SAINI SIMULATOR • AJAY SAINI (@ajayzsaini)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary.copy(alpha = 0.78f),
                        fontSize = 8.sp,
                        modifier = Modifier
                            .clickable { onOpenModal(LobbyModal.CREDITS) }
                            .padding(horizontal = 2.dp, vertical = 1.dp)
                            .testTag("lobby_developer_badge")
                    )
                }

                // BOTTOM-CENTER: 3D Character Camera & Animation Pose Strip
                LobbyQuickCameraBar(
                    zoomNormalized = zoomNormalized,
                    poseMode = poseMode,
                    onSelectPreset = { preset ->
                        zoomNormalized = preset.zoomNormalized
                        if (preset == LobbyCameraPreset.FACE_ZOOM) yawDeg = 4f
                    },
                    onStepZoom = { delta ->
                        zoomNormalized = (zoomNormalized + delta).coerceIn(0f, 1f)
                    },
                    onCyclePose = {
                        val all = LobbyPoseMode.entries
                        poseMode = all[(poseMode.ordinal + 1) % all.size]
                    },
                    onResetView = {
                        zoomNormalized = LobbyCameraPreset.FULL_BODY.zoomNormalized
                        yawDeg = 12f
                        poseMode = LobbyPoseMode.STAND
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // RIGHT & BOTTOM-RIGHT: Real Deployment Selector (Spawn District + Time/Weather + START)
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    // Spawn Point Selector (choose any major Saini State district before deploying!)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .width(174.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(LobbyGlass)
                            .border(0.8.dp, LobbyHairline, RoundedCornerShape(5.dp))
                            .padding(horizontal = 7.dp, vertical = 5.dp)
                            .testTag("lobby_spawn_selector")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "DEPLOY ZONE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TacticalCyan,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.7.sp
                                )
                            }
                            Text(
                                text = activeSpawn.regionTag,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontSize = 7.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CityCard)
                                    .clickable { onCycleSpawnDistrict(-1) }
                                    .testTag("btn_spawn_prev")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous Spawn", tint = TextPrimary, modifier = Modifier.size(16.dp))
                            }

                            Text(
                                text = activeSpawn.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CityCard)
                                    .clickable { onCycleSpawnDistrict(1) }
                                    .testTag("btn_spawn_next")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next Spawn", tint = TextPrimary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Quick Starting Environment Toggles (DAY/NIGHT + CLEAR/RAIN)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.width(174.dp)
                    ) {
                        val isNight = uiState.settings.startNightMode
                        val isRain = uiState.settings.startRainMode
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isNight) TacticalCyan.copy(alpha = 0.22f) else LobbyGlass)
                                .border(0.8.dp, if (isNight) TacticalCyan else LobbyHairline, RoundedCornerShape(4.dp))
                                .clickable(onClick = onToggleStartNight)
                                .testTag("btn_lobby_time")
                        ) {
                            Icon(
                                imageVector = if (isNight) Icons.Rounded.NightsStay else Icons.Rounded.WbSunny,
                                contentDescription = "Start Time",
                                tint = if (isNight) TacticalCyan else TacticalSilver,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isNight) "NIGHT" else "DAY",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isNight) TacticalCyan else TextPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isRain) TacticalCyan.copy(alpha = 0.22f) else LobbyGlass)
                                .border(0.8.dp, if (isRain) TacticalCyan else LobbyHairline, RoundedCornerShape(4.dp))
                                .clickable(onClick = onToggleStartRain)
                                .testTag("btn_lobby_weather")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WaterDrop,
                                contentDescription = "Start Weather",
                                tint = if (isRain) TacticalCyan else TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isRain) "RAIN" else "CLEAR",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isRain) TacticalCyan else TextPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Primary Crimson START Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(174.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(TacticalAccent, TacticalAccentDark)
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(4.dp))
                            .clickable(onClick = onStartGame)
                            .testTag("btn_start_game")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "START GAME",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                letterSpacing = 1.4.sp
                            )
                        }
                    }
                }
            }
        } else {
            CharacterInspectorOverlay(
                uiState = uiState,
                zoomNormalized = zoomNormalized,
                poseMode = poseMode,
                onZoomChange = { zoomNormalized = it },
                onSelectPreset = { preset ->
                    zoomNormalized = preset.zoomNormalized
                    if (preset == LobbyCameraPreset.FACE_ZOOM) yawDeg = 4f
                },
                onSelectPose = { poseMode = it },
                onResetCamera = {
                    zoomNormalized = LobbyCameraPreset.FULL_BODY.zoomNormalized
                    yawDeg = 12f
                    poseMode = LobbyPoseMode.STAND
                },
                onSelectSkin = onSelectSkin,
                onOpenStore = { onOpenModal(LobbyModal.STORE) },
                onDismiss = onCloseModal
            )
        }

        // Compact Floating Lobby Toast
        AnimatedVisibility(
            visible = uiState.lobbyToast != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 46.dp)
        ) {
            uiState.lobbyToast?.let { toast ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CityObsidian.copy(alpha = 0.92f))
                        .border(0.8.dp, TacticalCyan, RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = toast,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LobbyTopBar(
    stats: PlayerProfileStats,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCreditsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Player Profile Pill (Tactical Avatar + Username + Discovered Zones)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(LobbyGlass)
                .border(0.8.dp, LobbyHairline, RoundedCornerShape(4.dp))
                .clickable(onClick = onProfileClick)
                .padding(start = 4.dp, end = 10.dp)
                .testTag("btn_profile")
        ) {
            TacticalAvatar(
                avatarId = stats.selectedAvatarId,
                size = 28.dp,
                cornerRadius = 3.dp,
                borderWidth = 1.dp
            )
            Spacer(modifier = Modifier.width(7.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = stats.playerName.ifBlank { "SAINI PLAYER" }.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.testTag("lobby_title")
                )
                Text(
                    text = "${stats.zonesDiscoveredTotal}/${CityZones.all.size} ZONES • PROFILE",
                    style = MaterialTheme.typography.labelSmall,
                    color = TacticalCyan,
                    fontSize = 8.sp
                )
            }
        }

        // Right: Currencies + Utility Icons
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CurrencyChip(
                icon = Icons.Rounded.MonetizationOn,
                value = stats.coins.toString(),
                tint = CoinSilver,
                contentDescription = "Coins"
            )
            CurrencyChip(
                icon = Icons.Rounded.Diamond,
                value = stats.diamonds.toString(),
                tint = DiamondBlue,
                contentDescription = "Diamonds"
            )
            TopIconSquareButton(
                icon = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                contentDescription = if (isMuted) "Unmute Sound" else "Mute Sound",
                testTag = "btn_lobby_mute",
                tint = if (isMuted) CrimsonAlert else TacticalSilver,
                onClick = onToggleMute
            )
            TopIconSquareButton(
                icon = Icons.Rounded.Settings,
                contentDescription = "Settings",
                testTag = "btn_settings_top",
                onClick = onSettingsClick
            )
            TopIconSquareButton(
                icon = Icons.Rounded.Info,
                contentDescription = "About",
                testTag = "btn_credits_top",
                onClick = onCreditsClick
            )
        }
    }
}

@Composable
private fun CurrencyChip(
    icon: ImageVector,
    value: String,
    tint: Color,
    contentDescription: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(LobbyGlass)
            .border(0.8.dp, LobbyHairline, RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun TopIconSquareButton(
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    tint: Color = TextPrimary,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(LobbyGlass)
            .border(0.8.dp, LobbyHairline, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun LobbySideMenuTile(
    icon: ImageVector,
    label: String,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(106.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(LobbyGlass)
            .border(0.8.dp, LobbyHairline, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(accent)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TextPrimary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun LobbyQuickCameraBar(
    zoomNormalized: Float,
    poseMode: LobbyPoseMode,
    onSelectPreset: (LobbyCameraPreset) -> Unit,
    onStepZoom: (Float) -> Unit,
    onCyclePose: () -> Unit,
    onResetView: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(LobbyGlass)
            .border(0.8.dp, LobbyHairline, RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 3.dp)
            .testTag("lobby_camera_bar")
    ) {
        val isFaceZoom = zoomNormalized >= 0.78f
        val isFullBody = zoomNormalized <= 0.28f

        QuickCamChip(
            icon = Icons.Rounded.CenterFocusStrong,
            label = "FULL",
            active = isFullBody,
            testTag = "btn_zoom_full",
            onClick = { onSelectPreset(LobbyCameraPreset.FULL_BODY) }
        )
        QuickCamChip(
            icon = Icons.Rounded.Face,
            label = "FACE",
            active = isFaceZoom,
            testTag = "btn_zoom_face",
            onClick = { onSelectPreset(LobbyCameraPreset.FACE_ZOOM) }
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CityCard)
                .clickable { onStepZoom(-0.22f) }
                .testTag("btn_zoom_out")
        ) {
            Icon(Icons.Rounded.Remove, contentDescription = "Zoom Out", tint = TextPrimary, modifier = Modifier.size(14.dp))
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CityCard)
                .clickable { onStepZoom(0.22f) }
                .testTag("btn_zoom_in")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Zoom In", tint = TextPrimary, modifier = Modifier.size(14.dp))
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CityCard)
                .clickable(onClick = onCyclePose)
                .padding(horizontal = 7.dp)
                .testTag("btn_lobby_pose")
        ) {
            Text(
                text = poseMode.label,
                style = MaterialTheme.typography.labelSmall,
                color = TacticalCyan,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CityCard)
                .clickable(onClick = onResetView)
                .testTag("btn_reset_cam")
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Reset Camera", tint = TextSecondary, modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
private fun QuickCamChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) TacticalCyan else CityCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) CityObsidian else TextPrimary,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) CityObsidian else TextPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
