package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.engine.InteractiveLobbyCharacter3D
import com.example.game.engine.LobbyCameraPreset
import com.example.game.engine.LobbyPoseMode
import com.example.game.model.CharacterSkin
import com.example.game.model.CityZones
import com.example.game.model.LobbyModal
import com.example.game.model.PlayerProfileStats
import com.example.game.viewmodel.GameUiState
import com.example.ui.theme.CityBorder
import com.example.ui.theme.CityCard
import com.example.ui.theme.CityObsidian
import com.example.ui.theme.CitySlate
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DiamondBlue
import com.example.ui.theme.SunsetAmber
import com.example.ui.theme.SunsetGold
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LobbyScreen(
    uiState: GameUiState,
    hasClaimableMissions: Boolean,
    onStartGame: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenModal: (LobbyModal) -> Unit,
    onCloseModal: () -> Unit,
    onSelectSkin: (CharacterSkin) -> Unit
) {
    var zoomNormalized by remember { mutableFloatStateOf(LobbyCameraPreset.FULL_BODY.zoomNormalized) }
    var yawDeg by remember { mutableFloatStateOf(12f) }
    var poseMode by remember { mutableStateOf(LobbyPoseMode.IDLE) }

    val isCharacterInspectorOpen = uiState.activeModal == LobbyModal.CHARACTERS

    // Smoothly frame character in the left half when Character Inspector opens, and center when closed
    val cameraHorizontalShift = if (isCharacterInspectorOpen) 0.48f else 0f

    LaunchedEffect(isCharacterInspectorOpen) {
        if (!isCharacterInspectorOpen && zoomNormalized > 0.45f) {
            zoomNormalized = LobbyCameraPreset.FULL_BODY.zoomNormalized
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CityObsidian)
            .testTag("lobby_screen")
    ) {
        // 1. Live Interactive 3D Character Studio Viewport (360-deg Drag Spin + Pinch Zoom + Face Zoom!)
        InteractiveLobbyCharacter3D(
            zoomNormalized = zoomNormalized,
            poseMode = poseMode,
            yawDeg = yawDeg,
            horizontalShift = cameraHorizontalShift,
            onYawChange = { yawDeg = it },
            onZoomChange = { zoomNormalized = it },
            modifier = Modifier.fillMaxSize(),
            testTag = "lobby_3d_character"
        )

        // Subtle cinematic side vignette so HUD labels stay crisp without dimming the character
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

        // 2. Main Lobby HUD Overlay (hidden while Character Inspector takes over the right side)
        if (!isCharacterInspectorOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // TOP BAR: Player Banner (Left) + Currencies, Sound Toggle & Utility Icons (Right)
                LobbyTopBar(
                    stats = uiState.profileStats,
                    isMuted = uiState.settings.isMuted,
                    onToggleMute = onToggleMute,
                    onProfileClick = { onOpenModal(LobbyModal.PROFILE) },
                    onSettingsClick = { onOpenModal(LobbyModal.SETTINGS) },
                    onCreditsClick = { onOpenModal(LobbyModal.CREDITS) },
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // LEFT VERTICAL OFFICIAL GAME MENU RAIL (Free Fire / BGMI style)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(top = 14.dp)
                ) {
                    LobbySideMenuTile(
                        icon = Icons.Rounded.Storefront,
                        label = "STORE",
                        accent = SunsetGold,
                        testTag = "btn_lobby_store",
                        onClick = { onOpenModal(LobbyModal.STORE) }
                    )
                    LobbySideMenuTile(
                        icon = Icons.Rounded.Person,
                        label = "CHARACTER",
                        accent = Color.White,
                        testTag = "btn_lobby_characters",
                        onClick = { onOpenModal(LobbyModal.CHARACTERS) }
                    )
                    LobbySideMenuTile(
                        icon = Icons.Rounded.Assignment,
                        label = "MISSIONS",
                        accent = Color.White,
                        showBadgeDot = hasClaimableMissions,
                        testTag = "btn_lobby_missions",
                        onClick = { onOpenModal(LobbyModal.MISSIONS) }
                    )
                }

                // BOTTOM-LEFT: Active Outfit + Creator Credit (subtle & official)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CityObsidian.copy(alpha = 0.72f))
                            .border(1.dp, CityBorder.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                            .clickable { onOpenModal(LobbyModal.CHARACTERS) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = uiState.selectedSkin.name.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.selectedSkin.badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = SunsetGold,
                            fontSize = 10.sp
                        )
                    }

                    Text(
                        text = "SAINI SIMULATOR • BY AJAY SAINI (@ajayzsaini)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clickable { onOpenModal(LobbyModal.CREDITS) }
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                            .testTag("lobby_developer_badge")
                    )
                }

                // BOTTOM-CENTER: Quick 3D Camera Zoom & Face Zoom Bar right in Lobby!
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
                        poseMode = when (poseMode) {
                            LobbyPoseMode.IDLE -> LobbyPoseMode.WALK
                            LobbyPoseMode.WALK -> LobbyPoseMode.SPRINT
                            LobbyPoseMode.SPRINT -> LobbyPoseMode.IDLE
                        }
                    },
                    onResetView = {
                        zoomNormalized = LobbyCameraPreset.FULL_BODY.zoomNormalized
                        yawDeg = 12f
                        poseMode = LobbyPoseMode.IDLE
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // BOTTOM-RIGHT: Map/Mode Selector Card + Iconic Golden START Button
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    // Mode / Map Selection Banner
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .width(204.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CitySlate.copy(alpha = 0.88f))
                            .border(1.dp, CityBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Explore,
                            contentDescription = null,
                            tint = SunsetGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SAINI CITY",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "OPEN WORLD • ${uiState.profileStats.zonesDiscoveredTotal}/${CityZones.all.size} ZONES",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Iconic Free Fire / BGMI Bold Golden START Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(204.dp)
                            .height(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(SunsetGold, SunsetAmber)
                                )
                            )
                            .border(1.5.dp, Color(0xFFFFE082), RoundedCornerShape(8.dp))
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
                                tint = CityObsidian,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "START",
                                style = MaterialTheme.typography.headlineMedium,
                                color = CityObsidian,
                                fontSize = 24.sp,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }
            }
        } else {
            // 3. Dedicated Split-Screen 3D Character Inspector (Full Body / Upper / Face Zoom + Outfits + Stats)
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
                    poseMode = LobbyPoseMode.IDLE
                },
                onSelectSkin = onSelectSkin,
                onOpenStore = { onOpenModal(LobbyModal.STORE) },
                onDismiss = onCloseModal
            )
        }

        // Floating Lobby Toast Notification (Purchase / Equip / Claim Feedback)
        AnimatedVisibility(
            visible = uiState.lobbyToast != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 64.dp)
        ) {
            uiState.lobbyToast?.let { toast ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CityObsidian.copy(alpha = 0.92f))
                        .border(1.dp, SunsetGold, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = toast,
                        style = MaterialTheme.typography.labelMedium,
                        color = SunsetGold
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
        // Tactical Player Banner (Avatar + Name + Level + Rank)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(CitySlate.copy(alpha = 0.88f))
                .border(1.dp, CityBorder, RoundedCornerShape(8.dp))
                .clickable(onClick = onProfileClick)
                .padding(start = 6.dp, end = 14.dp, top = 5.dp, bottom = 5.dp)
                .testTag("btn_profile")
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_player_face),
                contentDescription = "Player Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.5.dp, SunsetGold, RoundedCornerShape(6.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "AJAY SAINI",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        modifier = Modifier.testTag("lobby_title")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SunsetGold)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "LV.${stats.level}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CityObsidian,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = stats.explorerRank,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        // Right: Gold Coins + Diamonds + Quick Sound Toggle + Settings + Credits
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CurrencyChip(
                icon = Icons.Rounded.MonetizationOn,
                value = stats.coins.toString(),
                tint = SunsetGold,
                contentDescription = "Gold Coins"
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
                tint = if (isMuted) CrimsonAlert else SunsetGold,
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
                contentDescription = "Credits",
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
            .height(38.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(CitySlate.copy(alpha = 0.88f))
            .border(1.dp, CityBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            fontSize = 15.sp
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
            .size(48.dp)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .background(CitySlate.copy(alpha = 0.88f))
            .border(1.dp, CityBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun LobbySideMenuTile(
    icon: ImageVector,
    label: String,
    accent: Color,
    showBadgeDot: Boolean = false,
    testTag: String,
    onClick: () -> Unit
) {
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .width(136.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CitySlate.copy(alpha = 0.84f))
                .border(1.dp, CityBorder.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp)
                .testTag(testTag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                fontSize = 14.sp
            )
        }
        if (showBadgeDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(CrimsonAlert)
            )
        }
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CityObsidian.copy(alpha = 0.78f))
            .border(1.dp, CityBorder.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
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

        // Step Zoom Out / In buttons
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CityCard)
                .clickable { onStepZoom(-0.22f) }
                .testTag("btn_zoom_out")
        ) {
            Icon(Icons.Rounded.Remove, contentDescription = "Zoom Out", tint = TextPrimary, modifier = Modifier.size(18.dp))
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CityCard)
                .clickable { onStepZoom(0.22f) }
                .testTag("btn_zoom_in")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Zoom In", tint = TextPrimary, modifier = Modifier.size(18.dp))
        }

        // Pose toggle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CityCard)
                .clickable(onClick = onCyclePose)
                .padding(horizontal = 10.dp)
                .testTag("btn_lobby_pose")
        ) {
            Text(
                text = poseMode.label,
                style = MaterialTheme.typography.labelSmall,
                color = SunsetGold,
                fontWeight = FontWeight.Bold
            )
        }

        // Reset camera angle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CityCard)
                .clickable(onClick = onResetView)
                .testTag("btn_reset_cam")
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Reset Camera", tint = TextSecondary, modifier = Modifier.size(17.dp))
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
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) SunsetGold else CityCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) CityObsidian else TextPrimary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) CityObsidian else TextPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}
