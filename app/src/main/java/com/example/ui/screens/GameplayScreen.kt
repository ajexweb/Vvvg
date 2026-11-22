package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsMartialArts
import androidx.compose.material.icons.rounded.SportsMma
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.game.engine.CityGLSurfaceView
import com.example.game.engine.CityWorldData
import com.example.game.engine.GameEngineListener
import com.example.game.engine.PlayerInputState
import com.example.game.engine.RoadClass
import com.example.game.engine.WorldMapData
import com.example.game.model.ActiveVehicleMode
import com.example.game.model.CityZones
import com.example.game.model.ControlLayout
import com.example.game.model.CustomHudLayout
import com.example.game.model.GameEmotes
import com.example.game.model.GameTelemetry
import com.example.game.model.GameVehicleWraps
import com.example.game.model.HudElementId
import com.example.game.model.SpawnDistricts
import com.example.game.model.VehicleControlMode
import com.example.game.model.WeatherState
import com.example.game.viewmodel.GameUiState
import kotlinx.coroutines.delay
import com.example.ui.theme.CityBorder
import com.example.ui.theme.CityCard
import com.example.ui.theme.CityObsidian
import com.example.ui.theme.CitySlate
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.TacticalAccent
import com.example.ui.theme.TacticalAccentDark
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalSilver
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Sleek Free Fire MAX Translucent Tactical HUD Colors (NO Yellow!)
private val HudDarkGlass = Color(0xFF0C1018).copy(alpha = 0.68f)
private val HudControlGlass = Color(0xFF0E131B).copy(alpha = 0.50f)
private val HudBorderSubtle = Color.White.copy(alpha = 0.22f)

@Composable
fun GameplayScreen(
    uiState: GameUiState,
    inputState: PlayerInputState,
    engineListener: GameEngineListener,
    onTogglePause: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenCustomHud: () -> Unit = {},
    onSetVehicleControlMode: (VehicleControlMode) -> Unit = {},
    onRestart: (resetEngine: () -> Unit) -> Unit,
    onReturnToLobby: () -> Unit,
    onSetWaypoint: (Float, Float) -> Unit = { _, _ -> },
    onClearWaypoint: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    fun triggerHaptic() {
        if (uiState.settings.hapticsEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Defer creating & attaching the OpenGL surface by 60ms so MatchLoadingOverlay paints on Frame 1 with ZERO hang!
    var mountGlView by remember { mutableStateOf(false) }
    var glViewRef by remember { mutableStateOf<CityGLSurfaceView?>(null) }
    var isFullMapOpen by remember { mutableStateOf(false) }
    var isEmotePickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(60L)
        mountGlView = true
    }

    val activeWrap = remember(uiState.settings.selectedVehicleWrapId) {
        GameVehicleWraps.byId(uiState.settings.selectedVehicleWrapId)
    }
    val activeSpawn = remember(uiState.settings.selectedSpawnId) {
        SpawnDistricts.byId(uiState.settings.selectedSpawnId)
    }
    val ownedEmotes = remember(uiState.ownedItemIds) {
        GameEmotes.all.filter { it.id in uiState.ownedItemIds }
    }

    LaunchedEffect(
        glViewRef,
        uiState.selectedSkin,
        uiState.settings,
        uiState.isPaused,
        uiState.isMatchLoading,
        uiState.isCustomHudEditorOpen,
        isFullMapOpen,
        uiState.waypointX,
        uiState.waypointZ
    ) {
        val gl = glViewRef ?: return@LaunchedEffect
        val r = gl.cityRenderer
        val s = uiState.settings
        r.activeSkin = uiState.selectedSkin
        r.graphicsQuality = s.graphicsQuality
        r.shadowsEnabled = s.shadowsEnabled
        r.grassDensity = s.grassDensity
        r.aiTrafficDensity = s.aiTrafficDensity
        r.npcDensity = s.npcDensity
        r.dynamicDayNight = s.dynamicDayNight
        r.invertCameraY = s.invertCameraY
        r.autoCenterVehicleCam = s.autoCenterVehicleCam
        r.autoSprint = s.autoSprint
        r.vehicleWrapRgb = activeWrap.rgbOverride
        r.cameraSensitivity = s.cameraSensitivity
        r.streamingRadiusMeters = s.streamingRadiusMeters
        r.spawnX = activeSpawn.x
        r.spawnZ = activeSpawn.z
        r.initialNightMode = s.startNightMode
        r.initialRainMode = s.startRainMode
        // Keep GL renderer active while match loading overlay is visible so incremental stages run smoothly!
        r.isPaused = !uiState.isMatchLoading && (uiState.isPaused || isFullMapOpen || uiState.isCustomHudEditorOpen)
        r.waypointActive = uiState.waypointX != null && uiState.waypointZ != null
        r.waypointX = uiState.waypointX ?: 0f
        r.waypointZ = uiState.waypointZ ?: 0f
        gl.applyTargetFrameRate(s.targetFps)
        gl.applyRenderResolutionScale(s.renderResolution.scale)
    }

    DisposableEffect(glViewRef) {
        glViewRef?.onResume()
        onDispose {
            inputState.holdGateOpen = false
            inputState.holdGateClose = false
            inputState.vehSteer = 0f
            inputState.vehThrottle = 0f
            glViewRef?.onPause()
        }
    }

    BackHandler {
        if (uiState.isMatchLoading) {
            // Ignore back press during pre-match world warm-up
        } else if (isFullMapOpen) {
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
                    Key.F -> {
                        if (isDown) inputState.handFightQueued = true
                        true
                    }
                    Key.K -> {
                        if (isDown) inputState.legFightQueued = true
                        true
                    }
                    Key.Q -> {
                        if (isDown) inputState.flipQueued = true
                        true
                    }
                    Key.C -> {
                        if (isDown) inputState.releaseQueued = true
                        true
                    }
                    Key.B -> {
                        if (isDown) {
                            val equipped = GameEmotes.byId(uiState.settings.selectedEmoteId)
                            inputState.emoteClipOrdinal = equipped.clipIndex
                            inputState.emoteQueued = true
                        }
                        true
                    }
                    Key.Escape -> {
                        if (isDown && !uiState.isMatchLoading) onTogglePause()
                        true
                    }
                    else -> false
                }
            }
            .testTag("gameplay_screen")
    ) {
        // 1. Native OpenGL ES 3D Viewport (mounted after Frame 1 so Loading Screen appears immediately!)
        if (mountGlView) {
            AndroidView(
                factory = { ctx ->
                    CityGLSurfaceView(ctx, inputState, engineListener).also { created ->
                        val r = created.cityRenderer
                        val s = uiState.settings
                        r.activeSkin = uiState.selectedSkin
                        r.graphicsQuality = s.graphicsQuality
                        r.shadowsEnabled = s.shadowsEnabled
                        r.grassDensity = s.grassDensity
                        r.aiTrafficDensity = s.aiTrafficDensity
                        r.npcDensity = s.npcDensity
                        r.dynamicDayNight = s.dynamicDayNight
                        r.invertCameraY = s.invertCameraY
                        r.autoCenterVehicleCam = s.autoCenterVehicleCam
                        r.autoSprint = s.autoSprint
                        r.vehicleWrapRgb = activeWrap.rgbOverride
                        r.cameraSensitivity = s.cameraSensitivity
                        r.streamingRadiusMeters = s.streamingRadiusMeters
                        r.spawnX = activeSpawn.x
                        r.spawnZ = activeSpawn.z
                        r.initialNightMode = s.startNightMode
                        r.initialRainMode = s.startRainMode
                        created.applyTargetFrameRate(s.targetFps)
                        created.applyRenderResolutionScale(s.renderResolution.scale)
                        glViewRef = created
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Smooth 360-deg Camera Orbit Touch-Drag Layer
        if (!uiState.isMatchLoading) {
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
        }

        // 3. Fully Individualized 22-Widget Customizable Gameplay HUD (mounts AFTER loading finishes!)
        if (!uiState.isMatchLoading) {
            val hudLayout = uiState.settings.customHudLayout
            val globalOpacity = uiState.settings.hudOpacity
            val inVehicle = uiState.telemetry.vehicleMode != ActiveVehicleMode.ON_FOOT
            val useVehicleButtons = inVehicle && uiState.settings.vehicleControlMode == VehicleControlMode.BUTTONS

            // Clear vehicle pedal/steer states whenever switching modes or exiting vehicle
            LaunchedEffect(useVehicleButtons) {
                if (!useVehicleButtons) {
                    inputState.vehSteer = 0f
                    inputState.vehThrottle = 0f
                }
                if (inVehicle) isEmotePickerOpen = false
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                val density = LocalDensity.current
                val stageW = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
                val stageH = with(density) { maxHeight.toPx().coerceAtLeast(1f) }

                // 3a. Individual Health Bar
                PositionedHudWidget(HudElementId.HEALTH_BAR, hudLayout, globalOpacity, stageW, stageH) {
                    HudSingleBarPill(
                        icon = Icons.Rounded.Favorite,
                        iconTint = Color.White,
                        barColor = Color(0xFFF1F5F9),
                        fraction = (uiState.telemetry.health / 100f).coerceIn(0f, 1f),
                        contentDesc = "Health"
                    )
                }

                // 3b. Individual Stamina Bar
                PositionedHudWidget(HudElementId.STAMINA_BAR, hudLayout, globalOpacity, stageW, stageH) {
                    HudSingleBarPill(
                        icon = Icons.Rounded.Bolt,
                        iconTint = TacticalCyan,
                        barColor = TacticalCyan,
                        fraction = (uiState.telemetry.stamina / 100f).coerceIn(0f, 1f),
                        contentDesc = "Stamina"
                    )
                }

                // 3c. Score & Discovered Zones Badge
                PositionedHudWidget(HudElementId.SCORE_BADGE, hudLayout, globalOpacity, stageW, stageH) {
                    HudScoreBadge(
                        score = uiState.telemetry.sessionScore,
                        zonesCount = uiState.telemetry.discoveredZoneIds.size
                    )
                }

                // 3d. Live FPS Counter (Toggleable & Movable independently!)
                PositionedHudWidget(HudElementId.FPS_COUNTER, hudLayout, globalOpacity, stageW, stageH) {
                    HudFpsBadge(fps = uiState.telemetry.currentFps)
                }

                // 3e. Current Location Label
                PositionedHudWidget(HudElementId.LOCATION_BAR, hudLayout, globalOpacity, stageW, stageH) {
                    Text(
                        text = uiState.telemetry.currentLocationLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(HudDarkGlass)
                            .border(0.7.dp, HudBorderSubtle, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.5.dp)
                            .testTag("hud_location_label")
                    )
                }

                // 3f. Individual Day/Night Button
                PositionedHudWidget(HudElementId.TIME_BTN, hudLayout, globalOpacity, stageW, stageH) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(HudDarkGlass)
                            .border(0.8.dp, HudBorderSubtle, RoundedCornerShape(4.dp))
                            .clickable { triggerHaptic(); inputState.toggleDayNightQueued = true }
                            .padding(horizontal = 6.dp, vertical = 2.5.dp)
                            .testTag("btn_toggle_time")
                    ) {
                        Icon(
                            imageVector = if (uiState.telemetry.isNight) Icons.Rounded.NightsStay else Icons.Rounded.WbSunny,
                            contentDescription = "Toggle Day Night",
                            tint = if (uiState.telemetry.isNight) TacticalCyan else TacticalSilver,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = uiState.telemetry.timeOfDayLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            fontSize = 9.sp
                        )
                    }
                }

                // 3g. Individual Weather Button
                PositionedHudWidget(HudElementId.WEATHER_BTN, hudLayout, globalOpacity, stageW, stageH) {
                    val isRain = uiState.telemetry.weatherState == WeatherState.RAIN
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isRain) TacticalCyan.copy(alpha = 0.22f) else HudDarkGlass)
                            .border(
                                0.8.dp,
                                if (isRain) TacticalCyan else HudBorderSubtle,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { triggerHaptic(); inputState.toggleWeatherQueued = true }
                            .padding(horizontal = 6.dp, vertical = 2.5.dp)
                            .testTag("btn_toggle_weather")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WaterDrop,
                            contentDescription = "Toggle Rain",
                            tint = if (isRain) TacticalCyan else TextSecondary,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = if (isRain) "RAIN" else "CLEAR",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isRain) TacticalCyan else TextPrimary,
                            fontSize = 9.sp
                        )
                    }
                }

                // 3h. Mini-Map Radar + Pin Distance
                PositionedHudWidget(HudElementId.MINIMAP, hudLayout, globalOpacity, stageW, stageH) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        MiniMapRadar(
                            telemetry = uiState.telemetry,
                            streamingRadiusMeters = uiState.settings.streamingRadiusMeters,
                            autoZoom = uiState.settings.minimapAutoZoom,
                            waypointX = uiState.waypointX,
                            waypointZ = uiState.waypointZ,
                            onOpenFullMap = { isFullMapOpen = true }
                        )
                        if (uiState.waypointX != null && uiState.waypointZ != null) {
                            val wdx = uiState.waypointX - uiState.telemetry.playerX
                            val wdz = uiState.waypointZ - uiState.telemetry.playerZ
                            val distM = sqrt(wdx * wdx + wdz * wdz).roundToInt()
                            val distStr = if (distM >= 1000) String.format("%.1f km", distM / 1000f) else "${distM}m"
                            Text(
                                text = "PIN • $distStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TacticalAccent)
                                    .clickable { isFullMapOpen = true }
                                    .padding(horizontal = 6.dp, vertical = 1.5.dp)
                                    .testTag("hud_waypoint_distance")
                            )
                        }
                    }
                }

                // 3i. Pause Button
                PositionedHudWidget(HudElementId.PAUSE_BTN, hudLayout, globalOpacity, stageW, stageH) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(HudDarkGlass)
                            .border(0.8.dp, HudBorderSubtle, RoundedCornerShape(5.dp))
                            .clickable(onClick = onTogglePause)
                            .testTag("btn_pause")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Pause,
                            contentDescription = "Pause Game",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Top-Center Notification Pill & Contextual Prompts (Interact / Iron Gate Hold)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 34.dp)
                ) {
                    // Sleek Water Entry Warning Pill (shown whenever player enters ocean/river water)
                    AnimatedVisibility(
                        visible = uiState.telemetry.waterWarning != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        uiState.telemetry.waterWarning?.let { warn ->
                            val isDeep = warn.contains("DEEP")
                            val borderTint = if (isDeep) CrimsonAlert else Color(0xFFF59E0B)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(CityObsidian.copy(alpha = 0.88f))
                                    .border(1.dp, borderTint, RoundedCornerShape(5.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .testTag("hud_water_warning")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WaterDrop,
                                    contentDescription = "Water Warning",
                                    tint = borderTint,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = warn,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = uiState.bannerMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        uiState.bannerMessage?.let { msg ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CityObsidian.copy(alpha = 0.86f))
                                    .border(0.8.dp, TacticalCyan.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    uiState.telemetry.nearbyInteractTarget?.let { hint ->
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CityObsidian.copy(alpha = 0.86f))
                                .border(0.9.dp, TacticalCyan, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    val nearGateName = uiState.telemetry.nearbyGateName
                    if (!inVehicle && nearGateName != null) {
                        val pct = (uiState.telemetry.nearbyGateProgress * 100f).roundToInt().coerceIn(0, 100)
                        val stateLabel = when {
                            pct <= 2 -> "CLOSED"
                            pct >= 98 -> "OPEN"
                            else -> "$pct%"
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.testTag("hud_gate_controls")
                        ) {
                            Text(
                                text = "$nearGateName • $stateLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = TacticalCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(HudDarkGlass)
                                    .border(0.8.dp, TacticalCyan.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                GateHoldButton(
                                    icon = Icons.Rounded.LockOpen,
                                    label = "HOLD: OPEN",
                                    accent = EmeraldPulse,
                                    testTag = "btn_hold_open_gate",
                                    onHoldChange = { held -> inputState.holdGateOpen = held }
                                )
                                GateHoldButton(
                                    icon = Icons.Rounded.Lock,
                                    label = "HOLD: CLOSE",
                                    accent = TacticalCyan,
                                    testTag = "btn_hold_close_gate",
                                    onHoldChange = { held -> inputState.holdGateClose = held }
                                )
                            }
                        }
                    } else {
                        inputState.holdGateOpen = false
                        inputState.holdGateClose = false
                    }
                }

                // 3j. Movement Control (On-Foot Joystick/D-Pad, OR Joystick when driving in Joystick mode)
                if (!useVehicleButtons) {
                    PositionedHudWidget(HudElementId.JOYSTICK, hudLayout, globalOpacity, stageW, stageH) {
                        if (uiState.settings.controlLayout == ControlLayout.JOYSTICK || inVehicle) {
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
                }

                // 3k. Dedicated In-Vehicle Button Controls (Steer Left/Right + Gas/Brake Pedals)
                if (useVehicleButtons) {
                    PositionedHudWidget(HudElementId.VEHICLE_STEER, hudLayout, globalOpacity, stageW, stageH) {
                        VehicleSteerButtons(
                            onSteerChange = { steer -> inputState.vehSteer = steer }
                        )
                    }
                    PositionedHudWidget(HudElementId.VEHICLE_PEDALS, hudLayout, globalOpacity, stageW, stageH) {
                        VehiclePedalButtons(
                            onThrottleChange = { throttle -> inputState.vehThrottle = throttle }
                        )
                    }
                }

                // 3l. Vehicle Speedometer + In-Vehicle Control Mode Switcher (BUTTONS <-> JOYSTICK right inside the vehicle!)
                if (inVehicle) {
                    PositionedHudWidget(HudElementId.SPEEDOMETER, hudLayout, globalOpacity, stageW, stageH) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(HudDarkGlass)
                                    .border(0.8.dp, HudBorderSubtle, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.5.dp)
                                    .testTag("hud_vehicle_speed")
                            ) {
                                Icon(
                                    imageVector = if (uiState.telemetry.vehicleMode == ActiveVehicleMode.CAR) Icons.Rounded.DirectionsCar else Icons.Rounded.TwoWheeler,
                                    contentDescription = "Vehicle Mode",
                                    tint = TacticalCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "${uiState.telemetry.vehicleSpeedKmh} KM/H",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }

                            // 1-Tap In-Vehicle Steering Control Switcher (BUTTONS / JOYSTICK)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TacticalCyan.copy(alpha = 0.20f))
                                    .border(0.8.dp, TacticalCyan.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                                    .clickable {
                                        triggerHaptic()
                                        val nextMode = if (uiState.settings.vehicleControlMode == VehicleControlMode.BUTTONS) {
                                            VehicleControlMode.JOYSTICK
                                        } else {
                                            VehicleControlMode.BUTTONS
                                        }
                                        onSetVehicleControlMode(nextMode)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 3.5.dp)
                                    .testTag("btn_vehicle_ctrl_switch")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Gamepad,
                                    contentDescription = "Switch Vehicle Controls",
                                    tint = TacticalCyan,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (useVehicleButtons) "BUTTONS" else "JOYSTICK",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TacticalCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }

                // 3m. Individual On-Foot Combat, Parkour & EMOTE Buttons (EVERY SINGLE BUTTON INDEPENDENTLY MOVABLE!)
                if (!inVehicle) {
                    PositionedHudWidget(HudElementId.BTN_EMOTE, hudLayout, globalOpacity, stageW, stageH) {
                        TacticalActionButton(
                            icon = Icons.Rounded.EmojiEmotions,
                            label = "EMOTE",
                            activeColor = if (isEmotePickerOpen) TacticalCyan else null,
                            sizeDp = 42,
                            testTag = "btn_emote",
                            onTap = {
                                triggerHaptic()
                                isEmotePickerOpen = !isEmotePickerOpen
                            }
                        )
                    }

                    PositionedHudWidget(HudElementId.BTN_PUNCH, hudLayout, globalOpacity, stageW, stageH) {
                        TacticalActionButton(
                            icon = Icons.Rounded.SportsMma,
                            label = "PUNCH",
                            activeColor = null,
                            sizeDp = 40,
                            testTag = "btn_hand_fight",
                            onTap = { triggerHaptic(); inputState.handFightQueued = true }
                        )
                    }

                    PositionedHudWidget(HudElementId.BTN_KICK, hudLayout, globalOpacity, stageW, stageH) {
                        TacticalActionButton(
                            icon = Icons.Rounded.SportsMartialArts,
                            label = "KICK",
                            activeColor = null,
                            sizeDp = 40,
                            testTag = "btn_leg_fight",
                            onTap = { triggerHaptic(); inputState.legFightQueued = true }
                        )
                    }

                    PositionedHudWidget(HudElementId.BTN_FLIP, hudLayout, globalOpacity, stageW, stageH) {
                        TacticalActionButton(
                            icon = Icons.Rounded.Autorenew,
                            label = "FLIP",
                            activeColor = null,
                            sizeDp = 40,
                            testTag = "btn_flip",
                            onTap = { triggerHaptic(); inputState.flipQueued = true }
                        )
                    }

                    PositionedHudWidget(HudElementId.BTN_DROP, hudLayout, globalOpacity, stageW, stageH) {
                        TacticalActionButton(
                            icon = Icons.Rounded.KeyboardDoubleArrowDown,
                            label = "DROP",
                            activeColor = null,
                            sizeDp = 40,
                            testTag = "btn_release",
                            onTap = { triggerHaptic(); inputState.releaseQueued = true }
                        )
                    }

                    PositionedHudWidget(HudElementId.BTN_JUMP, hudLayout, globalOpacity, stageW, stageH) {
                        TacticalActionButton(
                            icon = Icons.Rounded.ArrowUpward,
                            label = "JUMP",
                            activeColor = null,
                            sizeDp = 46,
                            testTag = "btn_jump",
                            onTap = { triggerHaptic(); inputState.jumpQueued = true }
                        )
                    }
                }

                // 3n. Individual Horn Button (when driving a car)
                if (uiState.telemetry.vehicleMode == ActiveVehicleMode.CAR) {
                    PositionedHudWidget(HudElementId.BTN_HORN, hudLayout, globalOpacity, stageW, stageH) {
                        TacticalActionButton(
                            icon = Icons.Rounded.Campaign,
                            label = "HORN",
                            activeColor = null,
                            sizeDp = 42,
                            testTag = "btn_horn",
                            onTap = { triggerHaptic(); inputState.hornQueued = true }
                        )
                    }
                }

                // 3o. Individual Run / Turbo Boost Button
                PositionedHudWidget(HudElementId.BTN_RUN, hudLayout, globalOpacity, stageW, stageH) {
                    TacticalActionButton(
                        icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                        label = when {
                            inVehicle && sprintToggled -> "TURBO"
                            inVehicle -> "BOOST"
                            sprintToggled -> "SPRINT"
                            else -> "RUN"
                        },
                        activeColor = if (sprintToggled) TacticalCyan else null,
                        sizeDp = 44,
                        testTag = "btn_run",
                        onTap = {
                            triggerHaptic()
                            sprintToggled = !sprintToggled
                            inputState.isSprinting = sprintToggled
                        }
                    )
                }

                // 3p. Individual Use / Exit Button
                PositionedHudWidget(HudElementId.BTN_USE, hudLayout, globalOpacity, stageW, stageH) {
                    TacticalActionButton(
                        icon = Icons.Rounded.PanTool,
                        label = if (inVehicle) "EXIT" else "USE",
                        activeColor = if (uiState.telemetry.nearbyInteractTarget != null || inVehicle) TacticalAccent else null,
                        sizeDp = 50,
                        testTag = "btn_interact",
                        onTap = { triggerHaptic(); inputState.interactQueued = true }
                    )
                }

                // Quick 3D Emote Selection Strip (pops up when tapping EMOTE!)
                AnimatedVisibility(
                    visible = isEmotePickerOpen && !inVehicle,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CityObsidian.copy(alpha = 0.90f))
                            .border(1.dp, TacticalCyan.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("emote_quick_wheel")
                    ) {
                        ownedEmotes.forEach { em ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(CityCard)
                                    .border(0.7.dp, TacticalCyan.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
                                    .clickable {
                                        triggerHaptic()
                                        inputState.emoteClipOrdinal = em.clipIndex
                                        inputState.emoteQueued = true
                                        isEmotePickerOpen = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                    .testTag("emote_trigger_${em.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EmojiEmotions,
                                    contentDescription = em.name,
                                    tint = TacticalCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = em.shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Fullscreen Interactive Tactical City Map
        if (isFullMapOpen && !uiState.isMatchLoading) {
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

        // 5. Compact Pause Menu Overlay
        if (uiState.isPaused && !isFullMapOpen && !uiState.isMatchLoading && !uiState.isCustomHudEditorOpen) {
            PauseMenuOverlay(
                score = uiState.telemetry.sessionScore,
                zonesCount = uiState.telemetry.discoveredZoneIds.size,
                isMuted = uiState.settings.isMuted,
                onToggleMute = onToggleMute,
                onOpenCustomHud = onOpenCustomHud,
                onResume = onTogglePause,
                onRestart = { onRestart { glViewRef?.cityRenderer?.queueReset() } },
                onReturnToLobby = onReturnToLobby
            )
        }

        // 6. Instant Pre-Match World Loading Overlay (Horizontal Line Progress Bar — stays up until 100% loaded!)
        AnimatedVisibility(
            visible = uiState.isMatchLoading,
            enter = fadeIn(animationSpec = tween(60)),
            exit = fadeOut(animationSpec = tween(280))
        ) {
            MatchLoadingOverlay(
                progress = uiState.matchLoadProgress,
                statusText = uiState.matchLoadStatus
            )
        }
    }
}

@Composable
private fun PositionedHudWidget(
    id: HudElementId,
    layout: CustomHudLayout,
    globalOpacity: Float,
    stageW: Float,
    stageH: Float,
    content: @Composable () -> Unit
) {
    val cfg = layout.configFor(id)
    if (!cfg.visible) return
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val posX = (cfg.xNorm * stageW - sizePx.width * 0.5f)
        .coerceIn(0f, (stageW - sizePx.width).coerceAtLeast(0f))
    val posY = (cfg.yNorm * stageH - sizePx.height * 0.5f)
        .coerceIn(0f, (stageH - sizePx.height).coerceAtLeast(0f))

    Box(
        modifier = Modifier
            .offset { IntOffset(posX.roundToInt(), posY.roundToInt()) }
            .onSizeChanged { sizePx = it }
            .scale(cfg.scale)
            .alpha((cfg.opacity * globalOpacity).coerceIn(0.25f, 1.0f))
    ) {
        content()
    }
}

@Composable
private fun HudSingleBarPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    barColor: Color,
    fraction: Float,
    contentDesc: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(HudDarkGlass)
            .border(0.8.dp, HudBorderSubtle, RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = iconTint,
            modifier = Modifier.size(11.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Box(
            modifier = Modifier
                .width(78.dp)
                .height(4.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun VehicleSteerButtons(
    onSteerChange: (Float) -> Unit
) {
    var leftHeld by remember { mutableStateOf(false) }
    var rightHeld by remember { mutableStateOf(false) }
    fun sync() {
        onSteerChange((if (rightHeld) 1f else 0f) - (if (leftHeld) 1f else 0f))
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag("hud_vehicle_steer_pad")
    ) {
        VehicleHoldKey(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            label = "LEFT",
            accent = TacticalCyan,
            widthDp = 56,
            heightDp = 54,
            testTag = "btn_veh_steer_left",
            onHoldChange = { leftHeld = it; sync() }
        )
        VehicleHoldKey(
            icon = Icons.AutoMirrored.Rounded.ArrowForward,
            label = "RIGHT",
            accent = TacticalCyan,
            widthDp = 56,
            heightDp = 54,
            testTag = "btn_veh_steer_right",
            onHoldChange = { rightHeld = it; sync() }
        )
    }
}

@Composable
private fun VehiclePedalButtons(
    onThrottleChange: (Float) -> Unit
) {
    var gasHeld by remember { mutableStateOf(false) }
    var brakeHeld by remember { mutableStateOf(false) }
    fun sync() {
        onThrottleChange((if (gasHeld) 1f else 0f) - (if (brakeHeld) 1f else 0f))
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.testTag("hud_vehicle_pedals_pad")
    ) {
        VehicleHoldKey(
            icon = Icons.Rounded.ArrowDownward,
            label = "BRAKE",
            accent = CrimsonAlert,
            widthDp = 54,
            heightDp = 54,
            testTag = "btn_veh_brake",
            onHoldChange = { brakeHeld = it; sync() }
        )
        VehicleHoldKey(
            icon = Icons.Rounded.ArrowUpward,
            label = "GAS",
            accent = EmeraldPulse,
            widthDp = 58,
            heightDp = 68,
            testTag = "btn_veh_gas",
            onHoldChange = { gasHeld = it; sync() }
        )
    }
}

@Composable
private fun VehicleHoldKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    widthDp: Int,
    heightDp: Int,
    testTag: String,
    onHoldChange: (Boolean) -> Unit
) {
    var held by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(widthDp.dp)
            .height(heightDp.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (held) accent.copy(alpha = 0.84f) else HudControlGlass)
            .border(1.2.dp, if (held) Color.White else accent.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        held = true
                        onHoldChange(true)
                        tryAwaitRelease()
                        held = false
                        onHoldChange(false)
                    }
                )
            }
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (held) CityObsidian else Color.White,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (held) CityObsidian else Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HudFpsBadge(fps: Int) {
    val tint = when {
        fps >= 45 -> EmeraldPulse
        fps >= 30 -> TacticalCyan
        else -> CrimsonAlert
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(HudDarkGlass)
            .border(0.8.dp, tint.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .testTag("hud_fps_counter")
    ) {
        Icon(
            imageVector = Icons.Rounded.Speed,
            contentDescription = "FPS",
            tint = tint,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = "$fps FPS",
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HudScoreBadge(score: Int, zonesCount: Int) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(HudDarkGlass)
            .border(0.8.dp, HudBorderSubtle, RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = "Score",
                tint = TacticalCyan,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "$score PTS",
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                fontSize = 10.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Explore,
                contentDescription = "Zones",
                tint = EmeraldPulse,
                modifier = Modifier.size(10.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "$zonesCount/${CityZones.all.size} ZONES",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 8.sp
            )
        }
    }
}

@Composable
private fun MiniMapRadar(
    telemetry: GameTelemetry,
    streamingRadiusMeters: Float,
    autoZoom: Boolean,
    waypointX: Float?,
    waypointZ: Float?,
    onOpenFullMap: () -> Unit
) {
    val satelliteImage = remember { WorldMapData.getOrCreateSatelliteBitmap().asImageBitmap() }
    val enterablePreview = remember { CityWorldData.createEnterableBuildings() }
    val pBucketX = (telemetry.playerX / 20f).toInt()
    val pBucketZ = (telemetry.playerZ / 20f).toInt()
    val miniGpsRoute = remember(waypointX, waypointZ, pBucketX, pBucketZ) {
        if (waypointX != null && waypointZ != null) {
            WorldMapData.computeRoadRoute(telemetry.playerX, telemetry.playerZ, waypointX, waypointZ)
        } else emptyList()
    }

    Box(
        modifier = Modifier
            .size(86.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF081B30))
            .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
            .clickable(onClick = onOpenFullMap)
            .testTag("hud_minimap")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val half = size.width * 0.5f
            val inMetro = kotlin.math.abs(telemetry.playerX) < 460f && telemetry.playerZ in -430f..420f
            val radarRangeMeters = if (!autoZoom) {
                165f
            } else {
                val speedBoost = (telemetry.vehicleSpeedKmh * 1.15f).coerceIn(0f, 95f)
                when {
                    telemetry.vehicleMode != ActiveVehicleMode.ON_FOOT && !inMetro -> 240f + speedBoost
                    telemetry.vehicleMode != ActiveVehicleMode.ON_FOOT -> 165f + speedBoost * 0.7f
                    !inMetro -> 215f
                    else -> 140f
                }
            }
            val worldScale = half / radarRangeMeters
            val pWorldX = telemetry.playerX
            val pWorldZ = telemetry.playerZ
            val cullRange = radarRangeMeters + 60f

            fun mapX(wx: Float): Float = half + (wx - pWorldX) * worldScale
            fun mapZ(wz: Float): Float = half + (wz - pWorldZ) * worldScale

            // 1. Live Multi-Biome 50km x 65km Satellite Terrain Map cropped seamlessly around player!
            val imgLeft = mapX(-WorldMapData.WORLD_HALF_X).roundToInt()
            val imgTop = mapZ(-WorldMapData.WORLD_HALF_Z).roundToInt()
            val imgW = (WorldMapData.WORLD_WIDTH * worldScale).roundToInt().coerceAtLeast(1)
            val imgH = (WorldMapData.WORLD_HEIGHT * worldScale).roundToInt().coerceAtLeast(1)
            drawImage(
                image = satelliteImage,
                dstOffset = IntOffset(imgLeft, imgTop),
                dstSize = IntSize(imgW, imgH)
            )

            // 2. Curved Regional Highways, Overseas Sea Bridge & Trails
            for (sp in WorldMapData.roadSplines) {
                val pts = sp.points
                if (pts.size < 2) continue
                val path = Path()
                var started = false
                for (i in pts.indices) {
                    val mx = mapX(pts[i].first)
                    val my = mapZ(pts[i].second)
                    if (!started) {
                        path.moveTo(mx, my)
                        started = true
                    } else {
                        path.lineTo(mx, my)
                    }
                }
                if (sp.closedLoop) path.close()
                val col = when (sp.roadClass) {
                    RoadClass.SEA_BRIDGE -> Color(0xFFE2F1F8)
                    RoadClass.DIRT_TRAIL -> Color(0xFFA08664).copy(alpha = 0.78f)
                    else -> Color(0xFFD5A96A)
                }
                val sw = when (sp.roadClass) {
                    RoadClass.SEA_BRIDGE -> 3.0f
                    RoadClass.DIRT_TRAIL -> 1.3f
                    else -> 2.4f
                }
                drawPath(path, color = col, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            // 3. Detailed Saini Metro Street Grid & Buildings when near downtown
            if (kotlin.math.abs(pWorldX) < 620f && pWorldZ in -580f..580f) {
                val roadColor = Color(0xFF475365)
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
                val bColor = Color(0xFF606B7A)
                for (b in CityWorldData.buildings) {
                    if (kotlin.math.abs(b.x - pWorldX) > cullRange || kotlin.math.abs(b.z - pWorldZ) > cullRange) continue
                    drawRect(
                        color = bColor,
                        topLeft = Offset(mapX(b.x - b.width * 0.5f), mapZ(b.z - b.depth * 0.5f)),
                        size = Size(b.width * worldScale, b.depth * worldScale)
                    )
                }
                for (eb in enterablePreview) {
                    if (kotlin.math.abs(eb.centerX - pWorldX) > cullRange || kotlin.math.abs(eb.centerZ - pWorldZ) > cullRange) continue
                    val left = mapX(eb.centerX - eb.halfW)
                    val top = mapZ(eb.centerZ - eb.halfD)
                    val sz = Size(eb.width * worldScale, eb.depth * worldScale)
                    drawRect(color = TacticalCyan.copy(alpha = 0.28f), topLeft = Offset(left, top), size = sz)
                    drawRect(color = TacticalCyan, topLeft = Offset(left, top), size = sz, style = Stroke(width = 1.0f))
                }
            }

            // 4. Streaming Zone Ring
            val zoneRadiusPx = streamingRadiusMeters * worldScale
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = zoneRadiusPx,
                center = Offset(half, half)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.36f),
                radius = zoneRadiusPx,
                center = Offset(half, half),
                style = Stroke(width = 0.9f)
            )

            // 5. Nearby Discovery Zones & Regional POI Markers
            for (z in CityZones.all) {
                val dx = (z.x - pWorldX) * worldScale
                val dz = (z.z - pWorldZ) * worldScale
                if (kotlin.math.abs(dx) > half + 8f || kotlin.math.abs(dz) > half + 8f) continue
                val discovered = telemetry.discoveredZoneIds.contains(z.id)
                drawCircle(
                    color = if (discovered) EmeraldPulse else TacticalCyan,
                    radius = 2.6f,
                    center = Offset(half + dx, half + dz)
                )
            }

            // 5b. Saved Parked Player Vehicle indicator on Mini-Map
            val savedCarX = telemetry.parkedCarX
            val savedCarZ = telemetry.parkedCarZ
            if (savedCarX != null && savedCarZ != null) {
                val cdx = (savedCarX - pWorldX) * worldScale
                val cdz = (savedCarZ - pWorldZ) * worldScale
                val d = sqrt(cdx * cdx + cdz * cdz)
                val maxEdge = half - 5f
                val cx = if (d > maxEdge && d > 0.001f) half + (cdx / d) * maxEdge else half + cdx
                val cz = if (d > maxEdge && d > 0.001f) half + (cdz / d) * maxEdge else half + cdz
                drawCircle(color = EmeraldPulse, radius = 3.2f, center = Offset(cx, cz))
                drawCircle(color = Color.White, radius = 3.2f, center = Offset(cx, cz), style = Stroke(width = 0.9f))
            }

            // 6. Active Curved GPS Route + Waypoint Edge Indicator
            if (waypointX != null && waypointZ != null) {
                if (miniGpsRoute.size >= 2) {
                    val gPath = Path().apply {
                        moveTo(mapX(miniGpsRoute[0].first), mapZ(miniGpsRoute[0].second))
                        for (i in 1 until miniGpsRoute.size) {
                            lineTo(mapX(miniGpsRoute[i].first), mapZ(miniGpsRoute[i].second))
                        }
                    }
                    drawPath(
                        path = gPath,
                        color = TacticalAccent,
                        style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
                val dx = (waypointX - pWorldX) * worldScale
                val dz = (waypointZ - pWorldZ) * worldScale
                val d = sqrt(dx * dx + dz * dz)
                val maxEdge = half - 5f
                val wx = if (d > maxEdge && d > 0.001f) half + (dx / d) * maxEdge else half + dx
                val wz = if (d > maxEdge && d > 0.001f) half + (dz / d) * maxEdge else half + dz
                drawCircle(color = TacticalAccent, radius = 4.0f, center = Offset(wx, wz))
                drawCircle(color = Color.White, radius = 4.0f, center = Offset(wx, wz), style = Stroke(width = 1.0f))
            }

            // 7. Player Heading Arrow
            val deg = -(telemetry.playerYawRad * 180f / PI.toFloat()) + 180f
            rotate(degrees = deg, pivot = Offset(half, half)) {
                val arrow = Path().apply {
                    moveTo(half, half - 5.4f)
                    lineTo(half + 3.8f, half + 3.8f)
                    lineTo(half, half + 1.5f)
                    lineTo(half - 3.8f, half + 3.8f)
                    close()
                }
                drawPath(arrow, color = TacticalCyan)
                drawPath(arrow, color = Color.White, style = Stroke(width = 0.8f))
            }
        }
        Text(
            text = "N",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 8.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 1.dp)
        )
        Text(
            text = "MAP",
            style = MaterialTheme.typography.labelSmall,
            color = TacticalCyan,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 3.dp, bottom = 2.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 3.dp, vertical = 0.5.dp)
        )
    }
}

@Composable
private fun VirtualJoystick(
    onVectorChange: (Float, Float) -> Unit
) {
    val radiusDp = 44.dp
    val maxDragPx = with(LocalDensity.current) { 28.dp.toPx() }
    var knobX by remember { mutableFloatStateOf(0f) }
    var knobY by remember { mutableFloatStateOf(0f) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(radiusDp * 2)
            .clip(CircleShape)
            .background(HudControlGlass)
            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
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
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.78f))
                .border(1.5.dp, Color(0xFF1E242E).copy(alpha = 0.55f), CircleShape)
        )
    }
}

@Composable
private fun TouchDPad(
    onVectorChange: (Float, Float) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.testTag("touch_dpad")
    ) {
        DPadKey(Icons.Rounded.ArrowUpward, "Move Forward") { down ->
            onVectorChange(0f, if (down) -1f else 0f)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            DPadKey(Icons.AutoMirrored.Rounded.ArrowBack, "Move Left") { down ->
                onVectorChange(if (down) -1f else 0f, 0f)
            }
            Spacer(modifier = Modifier.size(38.dp))
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
            .size(38.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(HudControlGlass)
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
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
        Icon(imageVector = icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TacticalActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    activeColor: Color?,
    sizeDp: Int,
    testTag: String,
    onTap: () -> Unit
) {
    val bg = activeColor?.copy(alpha = 0.86f) ?: HudControlGlass
    val border = if (activeColor != null) Color.White.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.30f)
    val contentTint = if (activeColor == TacticalCyan) CityObsidian else Color.White

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.1.dp, border, CircleShape)
            .clickable(onClick = onTap)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentTint,
            modifier = Modifier.size((sizeDp * 0.36f).dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentTint,
            fontSize = 8.sp,
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
    val border = if (isHeld) Color.White else accent.copy(alpha = 0.72f)
    val tint = if (isHeld) CityObsidian else Color.White

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(5.dp))
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
            .padding(horizontal = 8.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontSize = 9.sp,
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
    onOpenCustomHud: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onReturnToLobby: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(CityObsidian.copy(alpha = 0.84f))
            .padding(16.dp)
            .testTag("modal_pause")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 284.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CitySlate)
                .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                .padding(14.dp)
        ) {
            Text(
                text = "PAUSED",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontSize = 18.sp,
                letterSpacing = 1.4.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Score: $score PTS • Zones: $zonesCount/${CityZones.all.size}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            PauseActionRow(
                icon = Icons.Rounded.PlayArrow,
                label = "RESUME",
                filled = true,
                accent = TacticalAccent,
                testTag = "btn_resume",
                onClick = onResume
            )
            Spacer(modifier = Modifier.height(6.dp))
            PauseActionRow(
                icon = Icons.Rounded.Tune,
                label = "CUSTOM HUD",
                filled = false,
                accent = TacticalCyan,
                testTag = "btn_pause_custom_hud",
                onClick = onOpenCustomHud
            )
            Spacer(modifier = Modifier.height(6.dp))
            PauseActionRow(
                icon = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                label = if (isMuted) "SOUND: MUTED" else "SOUND: ON",
                filled = false,
                accent = if (isMuted) CrimsonAlert else TacticalCyan,
                testTag = "btn_pause_mute",
                onClick = onToggleMute
            )
            Spacer(modifier = Modifier.height(6.dp))
            PauseActionRow(
                icon = Icons.Rounded.Refresh,
                label = "RESTART",
                filled = false,
                accent = TacticalSilver,
                testTag = "btn_restart",
                onClick = onRestart
            )
            Spacer(modifier = Modifier.height(6.dp))
            PauseActionRow(
                icon = null,
                label = "EXIT TO LOBBY",
                filled = false,
                accent = CrimsonAlert,
                testTag = "btn_return_lobby",
                onClick = onReturnToLobby
            )
        }
    }
}

@Composable
private fun PauseActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    filled: Boolean,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (filled) {
                    Brush.horizontalGradient(listOf(accent, TacticalAccentDark))
                } else {
                    Brush.horizontalGradient(listOf(CityCard, CityCard))
                }
            )
            .border(0.8.dp, if (filled) Color.White.copy(alpha = 0.22f) else accent.copy(alpha = 0.55f), RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (filled) TextPrimary else accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) TextPrimary else if (accent == CrimsonAlert) CrimsonAlert else TextPrimary,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp
        )
    }
}

/**
 * Free Fire MAX-Style 3-Second Pre-Match World Loading Overlay.
 * Covers the OpenGL 3D viewport while shaders, textures, 3D grass, and all 6 character
 * animations load & warm up in the background, displaying a crisp horizontal line progress bar.
 */
@Composable
private fun MatchLoadingOverlay(
    progress: Float,
    statusText: String
) {
    val smoothProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 120),
        label = "match_load_line"
    )
    val tipText = when {
        smoothProgress < 0.34f -> "TIP: Approach ANY parked OR moving traffic car and press USE to drive across Mainland & all 6 Big Islands."
        smoothProgress < 0.68f -> "TIP: Tap the Mini-Map to explore the 50 km × 65 km world, place a GPS pin, or customize your HUD in Settings."
        else -> "TIP: Use PUNCH, KICK, FLIP & DROP buttons for real 3D combat and parkour moves."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CityObsidian)
            .pointerInput(Unit) {
                detectTapGestures { /* Consume all taps during pre-match loading */ }
            }
            .testTag("match_loading_overlay")
    ) {
        FullArtworkBlueGlowBackdrop(
            drawableRes = R.drawable.img_saini_splash_1,
            contentDescription = "Saini Simulator Match Loading"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Tactical Deployment Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CityObsidian.copy(alpha = 0.76f))
                        .border(0.8.dp, TacticalCyan.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 9.dp, vertical = 3.5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationCity,
                        contentDescription = null,
                        tint = TacticalCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "DEPLOYING • SAINI STATE",
                        style = MaterialTheme.typography.labelSmall,
                        color = TacticalSilver,
                        fontSize = 9.sp,
                        letterSpacing = 1.2.sp
                    )
                }

                Text(
                    text = "OFFLINE OPEN WORLD",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 9.sp,
                    letterSpacing = 1.0.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CityObsidian.copy(alpha = 0.72f))
                        .border(0.8.dp, HudBorderSubtle, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.5.dp)
                )
            }

            // Bottom Tip + Full-Width Sleek Horizontal Line Loading Bar (center left clean for hero + map artwork!)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
            ) {
                Text(
                    text = tipText,
                    style = MaterialTheme.typography.labelSmall,
                    color = TacticalSilver,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CityObsidian.copy(alpha = 0.72f))
                        .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )

                Spacer(modifier = Modifier.height(7.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        fontSize = 10.sp,
                        modifier = Modifier.testTag("match_load_status")
                    )
                    Text(
                        text = "${(smoothProgress * 100f).roundToInt().coerceIn(0, 100)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = TacticalCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.testTag("match_load_percent")
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Sleek Horizontal Line Progress Bar with glowing tip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CitySlate.copy(alpha = 0.90f))
                        .border(0.6.dp, CityBorder, RoundedCornerShape(2.dp))
                        .testTag("match_load_line_bar")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(smoothProgress.coerceIn(0.02f, 1f))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(TacticalCyan, TacticalAccent, Color.White)
                                )
                            )
                    )
                }
            }
        }
    }
}
