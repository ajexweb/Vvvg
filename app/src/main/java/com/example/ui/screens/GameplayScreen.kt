package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.game.engine.CityGLSurfaceView
import com.example.game.engine.CityWorldData
import com.example.game.engine.GameEngineListener
import com.example.game.engine.PlayerInputState
import com.example.game.model.ActiveVehicleMode
import com.example.game.model.CityZones
import com.example.game.model.ControlLayout
import com.example.game.model.GameTelemetry
import com.example.game.model.WeatherState
import com.example.game.viewmodel.GameUiState
import com.example.ui.theme.CityBorder
import com.example.ui.theme.CityCard
import com.example.ui.theme.CityObsidian
import com.example.ui.theme.CitySlate
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.SunsetGold
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Free Fire / BGMI Clean Matte Translucent Control Colors (NO Neon Cyan!)
private val HudDarkGlass = Color(0xFF12161D).copy(alpha = 0.68f)
private val HudControlGlass = Color(0xFF14181F).copy(alpha = 0.54f)
private val HudBorderSubtle = Color.White.copy(alpha = 0.28f)

@Composable
fun GameplayScreen(
    uiState: GameUiState,
    inputState: PlayerInputState,
    engineListener: GameEngineListener,
    onTogglePause: () -> Unit,
    onToggleMute: () -> Unit,
    onRestart: (resetEngine: () -> Unit) -> Unit,
    onReturnToLobby: () -> Unit,
    onSetWaypoint: (Float, Float) -> Unit = { _, _ -> },
    onClearWaypoint: () -> Unit = {}
) {
    val context = LocalContext.current
    val glView = remember {
        CityGLSurfaceView(context, inputState, engineListener)
    }
    var isFullMapOpen by remember { mutableStateOf(false) }

    LaunchedEffect(
        uiState.selectedSkin,
        uiState.settings.graphicsQuality,
        uiState.settings.cameraSensitivity,
        uiState.settings.streamingRadiusMeters,
        uiState.settings.targetFps,
        uiState.isPaused,
        isFullMapOpen,
        uiState.waypointX,
        uiState.waypointZ
    ) {
        glView.cityRenderer.activeSkin = uiState.selectedSkin
        glView.cityRenderer.graphicsQuality = uiState.settings.graphicsQuality
        glView.cityRenderer.cameraSensitivity = uiState.settings.cameraSensitivity
        glView.cityRenderer.streamingRadiusMeters = uiState.settings.streamingRadiusMeters
        glView.cityRenderer.isPaused = uiState.isPaused || isFullMapOpen
        glView.cityRenderer.waypointActive = uiState.waypointX != null && uiState.waypointZ != null
        glView.cityRenderer.waypointX = uiState.waypointX ?: 0f
        glView.cityRenderer.waypointZ = uiState.waypointZ ?: 0f
        glView.applyTargetFrameRate(uiState.settings.targetFps)
    }

    DisposableEffect(glView) {
        glView.onResume()
        onDispose {
            inputState.holdGateOpen = false
            inputState.holdGateClose = false
            glView.onPause()
        }
    }

    BackHandler {
        if (isFullMapOpen) {
            isFullMapOpen = false
        } else {
            onTogglePause()
        }
    }

    val focusRequester = remember { FocusRequester() }
    var keyW by remember { mutableStateOf(false) }
    var keyS by remember { mutableStateOf(false) }
    var keyA by remember { mutableStateOf(false) }
    var keyD by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    fun syncKeyboardVector() {
        val x = (if (keyD) 1f else 0f) - (if (keyA) 1f else 0f)
        val y = (if (keyS) 1f else 0f) - (if (keyW) 1f else 0f)
        inputState.moveX = x
        inputState.moveY = y
    }

    var sprintToggled by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CityObsidian)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                val isDown = event.type == KeyEventType.KeyDown
                val isUp = event.type == KeyEventType.KeyUp
                if (!isDown && !isUp) return@onKeyEvent false
                when (event.key) {
                    Key.W, Key.DirectionUp -> { keyW = isDown; syncKeyboardVector(); true }
                    Key.S, Key.DirectionDown -> { keyS = isDown; syncKeyboardVector(); true }
                    Key.A, Key.DirectionLeft -> { keyA = isDown; syncKeyboardVector(); true }
                    Key.D, Key.DirectionRight -> { keyD = isDown; syncKeyboardVector(); true }
                    Key.ShiftLeft, Key.ShiftRight -> {
                        inputState.isSprinting = isDown || sprintToggled
                        true
                    }
                    Key.Spacebar -> {
                        if (isDown) inputState.jumpQueued = true
                        true
                    }
                    Key.E -> {
                        if (uiState.telemetry.nearbyGateName != null) {
                            inputState.holdGateOpen = isDown
                        } else if (isDown) {
                            inputState.interactQueued = true
                        }
                        true
                    }
                    Key.R -> {
                        inputState.holdGateClose = isDown
                        true
                    }
                    Key.H -> {
                        if (isDown) inputState.hornQueued = true
                        true
                    }
                    Key.Escape -> {
                        if (isDown) onTogglePause()
                        true
                    }
                    else -> false
                }
            }
            .testTag("gameplay_screen")
    ) {
        // 1. Native OpenGL ES 3D Viewport
        AndroidView(
            factory = { glView },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Smooth 360-deg Camera Orbit Touch-Drag Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        inputState.cameraYawDelta += dragAmount.x
                        inputState.cameraPitchDelta += dragAmount.y
                    }
                }
        )

        // 3. Clean Official Free Fire / BGMI-Style Gameplay HUD (NO debug clutter!)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Top-Left: Clean HP/Stamina + Compact Score/Zones + Compact Location/Weather toggles
            Column(
                modifier = Modifier.align(Alignment.TopStart),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HudStatusCard(telemetry = uiState.telemetry)
                    HudScoreBadge(
                        score = uiState.telemetry.sessionScore,
                        zonesCount = uiState.telemetry.discoveredZoneIds.size
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.telemetry.currentLocationLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(HudDarkGlass)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("hud_location_label")
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(HudDarkGlass)
                            .border(1.dp, HudBorderSubtle, RoundedCornerShape(6.dp))
                            .clickable { inputState.toggleDayNightQueued = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("btn_toggle_time")
                    ) {
                        Icon(
                            imageVector = if (uiState.telemetry.isNight) Icons.Rounded.NightsStay else Icons.Rounded.WbSunny,
                            contentDescription = "Toggle Day Night",
                            tint = if (uiState.telemetry.isNight) Color.White else SunsetGold,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = uiState.telemetry.timeOfDayLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            fontSize = 10.sp
                        )
                    }

                    val isRain = uiState.telemetry.weatherState == WeatherState.RAIN
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isRain) SunsetGold.copy(alpha = 0.25f) else HudDarkGlass)
                            .border(
                                1.dp,
                                if (isRain) SunsetGold else HudBorderSubtle,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { inputState.toggleWeatherQueued = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("btn_toggle_weather")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WaterDrop,
                            contentDescription = "Toggle Rain",
                            tint = if (isRain) SunsetGold else TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isRain) "RAIN" else "CLEAR",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isRain) SunsetGold else TextPrimary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Top-Right: Interactive Tap-to-Expand Tactical MiniMap + Pause Button
            Row(
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MiniMapRadar(
                        telemetry = uiState.telemetry,
                        streamingRadiusMeters = uiState.settings.streamingRadiusMeters,
                        waypointX = uiState.waypointX,
                        waypointZ = uiState.waypointZ,
                        onOpenFullMap = { isFullMapOpen = true }
                    )
                    if (uiState.waypointX != null && uiState.waypointZ != null) {
                        val wdx = uiState.waypointX - uiState.telemetry.playerX
                        val wdz = uiState.waypointZ - uiState.telemetry.playerZ
                        val distM = sqrt(wdx * wdx + wdz * wdz).roundToInt()
                        Text(
                            text = "PIN • ${distM}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = CityObsidian,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(SunsetGold)
                                .clickable { isFullMapOpen = true }
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                .testTag("hud_waypoint_distance")
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(HudDarkGlass)
                        .border(1.dp, HudBorderSubtle, RoundedCornerShape(8.dp))
                        .clickable(onClick = onTogglePause)
                        .testTag("btn_pause")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = "Pause Game",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Top-Center Clean Notification Banner
            AnimatedVisibility(
                visible = uiState.bannerMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
            ) {
                uiState.bannerMessage?.let { msg ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CityObsidian.copy(alpha = 0.86f))
                            .border(1.dp, SunsetGold.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Bottom-Left: Clean Free Fire / BGMI Virtual Joystick or D-Pad
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 6.dp)
            ) {
                if (uiState.settings.controlLayout == ControlLayout.JOYSTICK) {
                    VirtualJoystick(
                        onVectorChange = { x, y ->
                            inputState.moveX = x
                            inputState.moveY = y
                        }
                    )
                } else {
                    TouchDPad(
                        onVectorChange = { x, y ->
                            inputState.moveX = x
                            inputState.moveY = y
                        }
                    )
                }
            }

            // Bottom-Right: Clean Tactical Action Controls (Vehicle Speed, Horn, Run, Jump, Interact)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 6.dp)
            ) {
                if (uiState.telemetry.vehicleMode != ActiveVehicleMode.ON_FOOT) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(HudDarkGlass)
                            .border(1.dp, HudBorderSubtle, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("hud_vehicle_speed")
                    ) {
                        Icon(
                            imageVector = if (uiState.telemetry.vehicleMode == ActiveVehicleMode.CAR) Icons.Rounded.DirectionsCar else Icons.Rounded.TwoWheeler,
                            contentDescription = "Vehicle Mode",
                            tint = SunsetGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${uiState.telemetry.vehicleSpeedKmh} KM/H",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }
                }

                // Hand-Controlled Interactive Gate Controls (Stop / Leave-Anywhere!)
                val nearGateName = uiState.telemetry.nearbyGateName
                val inVehicle = uiState.telemetry.vehicleMode != ActiveVehicleMode.ON_FOOT
                if (!inVehicle && nearGateName != null) {
                    val pct = (uiState.telemetry.nearbyGateProgress * 100f).roundToInt().coerceIn(0, 100)
                    val stateLabel = when {
                        pct <= 2 -> "CLOSED"
                        pct >= 98 -> "OPEN"
                        else -> "$pct% OPEN"
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.testTag("hud_gate_controls")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(HudDarkGlass)
                                .border(1.dp, SunsetGold.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$nearGateName  •  $stateLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = SunsetGold,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GateHoldButton(
                                icon = Icons.Rounded.LockOpen,
                                label = "HOLD: OPEN GATE",
                                accent = EmeraldPulse,
                                testTag = "btn_hold_open_gate",
                                onHoldChange = { held -> inputState.holdGateOpen = held }
                            )
                            GateHoldButton(
                                icon = Icons.Rounded.Lock,
                                label = "HOLD: CLOSE GATE",
                                accent = SunsetGold,
                                testTag = "btn_hold_close_gate",
                                onHoldChange = { held -> inputState.holdGateClose = held }
                            )
                        }
                    }
                } else {
                    inputState.holdGateOpen = false
                    inputState.holdGateClose = false
                }

                uiState.telemetry.nearbyInteractTarget?.let { hint ->
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = CityObsidian,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SunsetGold)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (uiState.telemetry.vehicleMode == ActiveVehicleMode.CAR) {
                        TacticalActionButton(
                            icon = Icons.Rounded.Campaign,
                            label = "HORN",
                            highlighted = false,
                            sizeDp = 56,
                            testTag = "btn_horn",
                            onTap = { inputState.hornQueued = true }
                        )
                    }

                    TacticalActionButton(
                        icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                        label = when {
                            inVehicle && sprintToggled -> "TURBO"
                            inVehicle -> "BOOST"
                            sprintToggled -> "SPRINT"
                            else -> "RUN"
                        },
                        highlighted = sprintToggled,
                        sizeDp = 58,
                        testTag = "btn_run",
                        onTap = {
                            sprintToggled = !sprintToggled
                            inputState.isSprinting = sprintToggled
                        }
                    )

                    if (!inVehicle) {
                        TacticalActionButton(
                            icon = Icons.Rounded.ArrowUpward,
                            label = "JUMP",
                            highlighted = false,
                            sizeDp = 62,
                            testTag = "btn_jump",
                            onTap = { inputState.jumpQueued = true }
                        )
                    }

                    TacticalActionButton(
                        icon = Icons.Rounded.PanTool,
                        label = if (inVehicle) "EXIT" else "USE",
                        highlighted = uiState.telemetry.nearbyInteractTarget != null || inVehicle,
                        sizeDp = 68,
                        testTag = "btn_interact",
                        onTap = { inputState.interactQueued = true }
                    )
                }
            }
        }

        // 4. Fullscreen Interactive Tactical City Map Overlay (Tap MiniMap to explore, zoom, & pin route!)
        if (isFullMapOpen) {
            CityMapModal(
                telemetry = uiState.telemetry,
                streamingRadiusMeters = uiState.settings.streamingRadiusMeters,
                waypointX = uiState.waypointX,
                waypointZ = uiState.waypointZ,
                onSetWaypoint = onSetWaypoint,
                onClearWaypoint = onClearWaypoint,
                onDismiss = { isFullMapOpen = false }
            )
        }

        // 5. Pause Menu Overlay
        if (uiState.isPaused && !isFullMapOpen) {
            PauseMenuOverlay(
                score = uiState.telemetry.sessionScore,
                zonesCount = uiState.telemetry.discoveredZoneIds.size,
                isMuted = uiState.settings.isMuted,
                onToggleMute = onToggleMute,
                onResume = onTogglePause,
                onRestart = { onRestart { glView.cityRenderer.queueReset() } },
                onReturnToLobby = onReturnToLobby
            )
        }
    }
}

@Composable
private fun HudStatusCard(telemetry: GameTelemetry) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(HudDarkGlass)
            .border(1.dp, HudBorderSubtle, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = "Health",
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(7.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .fillMaxWidth((telemetry.health / 100f).coerceIn(0f, 1f))
                        .background(Color(0xFFECEFF4))
                )
            }
        }
        Spacer(modifier = Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = "Stamina",
                tint = SunsetGold,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .fillMaxWidth((telemetry.stamina / 100f).coerceIn(0f, 1f))
                        .background(SunsetGold)
                )
            }
        }
    }
}

@Composable
private fun HudScoreBadge(score: Int, zonesCount: Int) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(HudDarkGlass)
            .border(1.dp, HudBorderSubtle, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = "Score",
                tint = SunsetGold,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$score PTS",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                fontSize = 13.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Explore,
                contentDescription = "Zones",
                tint = EmeraldPulse,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$zonesCount/${CityZones.all.size} ZONES",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun MiniMapRadar(
    telemetry: GameTelemetry,
    streamingRadiusMeters: Float,
    waypointX: Float?,
    waypointZ: Float?,
    onOpenFullMap: () -> Unit
) {
    val enterablePreview = remember { CityWorldData.createEnterableBuildings() }

    Box(
        modifier = Modifier
            .size(104.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF141921).copy(alpha = 0.88f))
            .border(1.2.dp, Color.White.copy(alpha = 0.42f), RoundedCornerShape(10.dp))
            .clickable(onClick = onOpenFullMap)
            .testTag("hud_minimap")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val half = size.width * 0.5f
            val radarRangeMeters = 135f
            val worldScale = half / radarRangeMeters
            val pWorldX = telemetry.playerX
            val pWorldZ = telemetry.playerZ
            val cullRange = radarRangeMeters + 60f

            fun mapX(wx: Float): Float = half + (wx - pWorldX) * worldScale
            fun mapZ(wz: Float): Float = half + (wz - pWorldZ) * worldScale

            // Emerald City Park (-96..-56, 56..96)
            if (kotlin.math.abs(-76f - pWorldX) < cullRange && kotlin.math.abs(76f - pWorldZ) < cullRange) {
                drawRect(
                    color = Color(0xFF1E5128).copy(alpha = 0.72f),
                    topLeft = Offset(mapX(-96f), mapZ(56f)),
                    size = Size(40f * worldScale, 40f * worldScale)
                )
            }

            // Ocean Bay & Pier (WATERFRONT_Z = 410f)
            val waterTop = mapZ(CityWorldData.WATERFRONT_Z)
            if (waterTop < size.height) {
                val clampedTop = waterTop.coerceAtLeast(0f)
                drawRect(
                    color = Color(0xFF1D4E78).copy(alpha = 0.80f),
                    topLeft = Offset(0f, clampedTop),
                    size = Size(size.width, (size.height - clampedTop).coerceAtLeast(0f))
                )
                drawRect(
                    color = Color(0xFF9A6B3F),
                    topLeft = Offset(mapX(-7f), waterTop),
                    size = Size(14f * worldScale, 36f * worldScale)
                )
            }

            // Narrow Colony Alleys ("Patli Galiyan") within radar range
            val alleyColor = Color(0xFF2D3542)
            for (al in CityWorldData.colonyAlleys) {
                if (kotlin.math.abs(al.x - pWorldX) > cullRange || kotlin.math.abs(al.z - pWorldZ) > cullRange) continue
                val aw = if (al.isVertical) al.width else al.length
                val ad = if (al.isVertical) al.length else al.width
                drawRect(
                    color = alleyColor,
                    topLeft = Offset(mapX(al.x - aw * 0.5f), mapZ(al.z - ad * 0.5f)),
                    size = Size(aw * worldScale, ad * worldScale)
                )
            }

            // Main Roads & Intersections within tactical radar range
            val roadColor = Color(0xFF3E4856)
            for (r in CityWorldData.roadSegments) {
                if (kotlin.math.abs(r.x - pWorldX) > cullRange + r.length * 0.5f || kotlin.math.abs(r.z - pWorldZ) > cullRange + r.length * 0.5f) continue
                val rw = if (r.isVertical) r.width else r.length
                val rd = if (r.isVertical) r.length else r.width
                drawRect(
                    color = roadColor,
                    topLeft = Offset(mapX(r.x - rw * 0.5f), mapZ(r.z - rd * 0.5f)),
                    size = Size(rw * worldScale, rd * worldScale)
                )
            }
            for (ix in CityWorldData.intersections) {
                if (kotlin.math.abs(ix.x - pWorldX) > cullRange || kotlin.math.abs(ix.z - pWorldZ) > cullRange) continue
                drawRect(
                    color = roadColor,
                    topLeft = Offset(mapX(ix.x - ix.sizeX * 0.5f), mapZ(ix.z - ix.sizeZ * 0.5f)),
                    size = Size(ix.sizeX * worldScale, ix.sizeZ * worldScale)
                )
            }

            // Buildings within tactical radar range
            val bColor = Color(0xFF5C6675)
            for (b in CityWorldData.buildings) {
                if (kotlin.math.abs(b.x - pWorldX) > cullRange || kotlin.math.abs(b.z - pWorldZ) > cullRange) continue
                drawRect(
                    color = bColor,
                    topLeft = Offset(mapX(b.x - b.width * 0.5f), mapZ(b.z - b.depth * 0.5f)),
                    size = Size(b.width * worldScale, b.depth * worldScale)
                )
            }

            // Enterable Buildings (highlighted gold footprint)
            for (eb in enterablePreview) {
                if (kotlin.math.abs(eb.centerX - pWorldX) > cullRange || kotlin.math.abs(eb.centerZ - pWorldZ) > cullRange) continue
                val left = mapX(eb.centerX - eb.halfW)
                val top = mapZ(eb.centerZ - eb.halfD)
                val sz = Size(eb.width * worldScale, eb.depth * worldScale)
                drawRect(color = SunsetGold.copy(alpha = 0.28f), topLeft = Offset(left, top), size = sz)
                drawRect(color = SunsetGold, topLeft = Offset(left, top), size = sz, style = Stroke(width = 1.3f))
            }

            // Player 360-degree Loaded Zone Ring (centered on player!)
            val zoneRadiusPx = streamingRadiusMeters * worldScale
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = zoneRadiusPx,
                center = Offset(half, half)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.50f),
                radius = zoneRadiusPx,
                center = Offset(half, half),
                style = Stroke(width = 1.1f)
            )

            // Exploration zones (clamped to radar edge if distant)
            for (z in CityZones.all) {
                val discovered = telemetry.discoveredZoneIds.contains(z.id)
                val dx = (z.x - pWorldX) * worldScale
                val dz = (z.z - pWorldZ) * worldScale
                val d = sqrt(dx * dx + dz * dz)
                val maxEdge = half - 5f
                val cx = if (d > maxEdge && d > 0.001f) half + (dx / d) * maxEdge else half + dx
                val cz = if (d > maxEdge && d > 0.001f) half + (dz / d) * maxEdge else half + dz
                drawCircle(
                    color = if (discovered) EmeraldPulse else SunsetGold,
                    radius = if (d > maxEdge) 2.2f else 3.2f,
                    center = Offset(cx, cz)
                )
            }

            // Active Waypoint Dashed Route Line + Pin Tick Mark on Live MiniMap!
            if (waypointX != null && waypointZ != null) {
                val dx = (waypointX - pWorldX) * worldScale
                val dz = (waypointZ - pWorldZ) * worldScale
                val d = sqrt(dx * dx + dz * dz)
                val maxEdge = half - 6f
                val wx = if (d > maxEdge && d > 0.001f) half + (dx / d) * maxEdge else half + dx
                val wz = if (d > maxEdge && d > 0.001f) half + (dz / d) * maxEdge else half + dz
                drawLine(
                    color = SunsetGold,
                    start = Offset(half, half),
                    end = Offset(wx, wz),
                    strokeWidth = 2.0f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))
                )
                drawCircle(color = SunsetGold, radius = 4.6f, center = Offset(wx, wz))
                drawCircle(color = Color.White, radius = 4.6f, center = Offset(wx, wz), style = Stroke(width = 1.1f))
            }

            // Player heading arrow (always centered)
            val deg = -(telemetry.playerYawRad * 180f / PI.toFloat()) + 180f
            rotate(degrees = deg, pivot = Offset(half, half)) {
                val arrow = Path().apply {
                    moveTo(half, half - 6.2f)
                    lineTo(half + 4.4f, half + 4.4f)
                    lineTo(half, half + 1.8f)
                    lineTo(half - 4.4f, half + 4.4f)
                    close()
                }
                drawPath(arrow, color = SunsetGold)
            }
        }
        Text(
            text = "N",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
        )
        Text(
            text = "MAP",
            style = MaterialTheme.typography.labelSmall,
            color = SunsetGold,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 4.dp, bottom = 2.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 3.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun VirtualJoystick(
    onVectorChange: (Float, Float) -> Unit
) {
    val radiusDp = 54.dp
    val maxDragPx = with(LocalDensity.current) { 36.dp.toPx() }
    var knobX by remember { mutableFloatStateOf(0f) }
    var knobY by remember { mutableFloatStateOf(0f) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(radiusDp * 2)
            .clip(CircleShape)
            .background(HudControlGlass)
            .border(1.4.dp, Color.White.copy(alpha = 0.34f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        knobX = 0f
                        knobY = 0f
                        onVectorChange(0f, 0f)
                    },
                    onDragCancel = {
                        knobX = 0f
                        knobY = 0f
                        onVectorChange(0f, 0f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val nx = knobX + dragAmount.x
                        val ny = knobY + dragAmount.y
                        val dist = sqrt(nx * nx + ny * ny)
                        if (dist > maxDragPx && dist > 0.001f) {
                            knobX = (nx / dist) * maxDragPx
                            knobY = (ny / dist) * maxDragPx
                        } else {
                            knobX = nx
                            knobY = ny
                        }
                        onVectorChange(knobX / maxDragPx, knobY / maxDragPx)
                    }
                )
            }
            .testTag("virtual_joystick")
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(knobX.roundToInt(), knobY.roundToInt()) }
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.78f))
                .border(2.dp, Color(0xFF1E242E).copy(alpha = 0.55f), CircleShape)
        )
    }
}

@Composable
private fun TouchDPad(
    onVectorChange: (Float, Float) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.testTag("touch_dpad")
    ) {
        DPadKey(Icons.Rounded.ArrowUpward, "Move Forward") { down ->
            onVectorChange(0f, if (down) -1f else 0f)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DPadKey(Icons.AutoMirrored.Rounded.ArrowBack, "Move Left") { down ->
                onVectorChange(if (down) -1f else 0f, 0f)
            }
            Spacer(modifier = Modifier.size(48.dp))
            DPadKey(Icons.AutoMirrored.Rounded.ArrowForward, "Move Right") { down ->
                onVectorChange(if (down) 1f else 0f, 0f)
            }
        }
        DPadKey(Icons.Rounded.ArrowDownward, "Move Backward") { down ->
            onVectorChange(0f, if (down) 1f else 0f)
        }
    }
}

@Composable
private fun DPadKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onHoldChange: (Boolean) -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(HudControlGlass)
            .border(1.2.dp, Color.White.copy(alpha = 0.36f), RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onHoldChange(true)
                        tryAwaitRelease()
                        onHoldChange(false)
                    }
                )
            }
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = Color.White)
    }
}

@Composable
private fun TacticalActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    highlighted: Boolean,
    sizeDp: Int,
    testTag: String,
    onTap: () -> Unit
) {
    val bg = if (highlighted) SunsetGold.copy(alpha = 0.92f) else HudControlGlass
    val border = if (highlighted) Color(0xFFFFE082) else Color.White.copy(alpha = 0.38f)
    val contentTint = if (highlighted) CityObsidian else Color.White

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(sizeDp.dp)
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, border, CircleShape)
            .clickable(onClick = onTap)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentTint,
            modifier = Modifier.size(21.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentTint,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun GateHoldButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    testTag: String,
    onHoldChange: (Boolean) -> Unit
) {
    var isHeld by remember { mutableStateOf(false) }
    val bg = if (isHeld) accent.copy(alpha = 0.90f) else HudDarkGlass
    val border = if (isHeld) Color.White else accent.copy(alpha = 0.78f)
    val tint = if (isHeld) CityObsidian else Color.White

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.4.dp, border, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHeld = true
                        onHoldChange(true)
                        tryAwaitRelease()
                        isHeld = false
                        onHoldChange(false)
                    }
                )
            }
            .padding(horizontal = 10.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PauseMenuOverlay(
    score: Int,
    zonesCount: Int,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onReturnToLobby: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(CityObsidian.copy(alpha = 0.84f))
            .padding(24.dp)
            .testTag("modal_pause")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CitySlate)
                .border(1.dp, CityBorder, RoundedCornerShape(14.dp))
                .padding(20.dp)
        ) {
            Text(
                text = "PAUSED",
                style = MaterialTheme.typography.headlineMedium,
                color = SunsetGold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Score: $score PTS  •  Zones: $zonesCount/${CityZones.all.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(containerColor = SunsetGold, contentColor = CityObsidian),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_resume")
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("RESUME", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onToggleMute,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (isMuted) CrimsonAlert else SunsetGold.copy(alpha = 0.7f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_pause_mute")
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                    contentDescription = if (isMuted) "Unmute Sound" else "Mute Sound",
                    tint = if (isMuted) CrimsonAlert else SunsetGold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isMuted) "SOUND: MUTED" else "SOUND: ON",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isMuted) CrimsonAlert else TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onRestart,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CityBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_restart")
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = TextPrimary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("RESTART", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onReturnToLobby,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CrimsonAlert),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_return_lobby")
            ) {
                Text("EXIT TO LOBBY", style = MaterialTheme.typography.labelLarge, color = CrimsonAlert)
            }
        }
    }
}
