package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
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
import com.example.game.model.CityZones
import com.example.game.model.ControlLayout
import com.example.game.model.GameTelemetry
import com.example.game.viewmodel.GameUiState
import com.example.ui.theme.CityBorder
import com.example.ui.theme.CityCard
import com.example.ui.theme.CityObsidian
import com.example.ui.theme.CitySlate
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.SunsetGold
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun GameplayScreen(
    uiState: GameUiState,
    inputState: PlayerInputState,
    engineListener: GameEngineListener,
    onTogglePause: () -> Unit,
    onRestart: (resetEngine: () -> Unit) -> Unit,
    onReturnToLobby: () -> Unit
) {
    val context = LocalContext.current
    val glView = remember {
        CityGLSurfaceView(context, inputState, engineListener)
    }

    // Keep renderer synchronized with selected skin, quality, sensitivity, and pause state
    LaunchedEffect(
        uiState.selectedSkin,
        uiState.settings.graphicsQuality,
        uiState.settings.cameraSensitivity,
        uiState.isPaused
    ) {
        glView.cityRenderer.activeSkin = uiState.selectedSkin
        glView.cityRenderer.graphicsQuality = uiState.settings.graphicsQuality
        glView.cityRenderer.cameraSensitivity = uiState.settings.cameraSensitivity
        glView.cityRenderer.isPaused = uiState.isPaused
    }

    DisposableEffect(glView) {
        glView.onResume()
        onDispose {
            glView.onPause()
        }
    }

    BackHandler {
        onTogglePause()
    }

    // Hardware keyboard state tracking (WASD / Arrow keys / Space / Shift / E / Esc)
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
                        if (isDown) inputState.interactQueued = true
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
        // 1. Native OpenGL ES 2.0 3D City Viewport
        AndroidView(
            factory = { glView },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Camera Touch-Drag Layer (behind HUD & buttons)
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

        // 3. Gameplay HUD & Touch Controls Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Top-Left: Health, Stamina, Score & Discovery Counter
            Column(
                modifier = Modifier.align(Alignment.TopStart),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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

                Text(
                    text = "Saini Simulator • Ajay Saini (@ajayzsaini)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.80f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CityObsidian.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            // Top-Right: Live Mini-Map Radar + Pause Button
            Row(
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                MiniMapRadar(telemetry = uiState.telemetry)

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CityObsidian.copy(alpha = 0.82f))
                        .border(1.2.dp, SunsetGold.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                        .clickable(onClick = onTogglePause)
                        .testTag("btn_pause")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = "Pause Game",
                        tint = SunsetGold,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Top-Center Toast Banner (Zone Discovered / Pickup / Interaction)
            AnimatedVisibility(
                visible = uiState.bannerMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 66.dp)
            ) {
                uiState.bannerMessage?.let { msg ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(CityObsidian.copy(alpha = 0.88f))
                            .border(1.2.dp, SunsetGold, RoundedCornerShape(50))
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelLarge,
                            color = SunsetGold
                        )
                    }
                }
            }

            // Bottom-Left: Virtual Joystick or Touch D-Pad
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 8.dp)
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

            // Bottom-Right: Run (Sprint), Jump, and Interact Action Buttons
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 8.dp)
            ) {
                uiState.telemetry.nearbyInteractTarget?.let { hint ->
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = CityObsidian,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(SunsetGold)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Run / Sprint toggle button
                    ActionCircleButton(
                        icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                        label = if (sprintToggled) "SPRINT ON" else "RUN",
                        accent = if (sprintToggled) SunsetGold else ElectricCyan,
                        filled = sprintToggled,
                        sizeDp = 62,
                        testTag = "btn_run",
                        onTap = {
                            sprintToggled = !sprintToggled
                            inputState.isSprinting = sprintToggled
                        }
                    )

                    // Jump button
                    ActionCircleButton(
                        icon = Icons.Rounded.ArrowUpward,
                        label = "JUMP",
                        accent = ElectricCyan,
                        filled = false,
                        sizeDp = 66,
                        testTag = "btn_jump",
                        onTap = { inputState.jumpQueued = true }
                    )

                    // Interact button
                    ActionCircleButton(
                        icon = Icons.Rounded.PanTool,
                        label = "INTERACT",
                        accent = SunsetGold,
                        filled = true,
                        sizeDp = 74,
                        testTag = "btn_interact",
                        onTap = { inputState.interactQueued = true }
                    )
                }
            }
        }

        // 4. Pause Menu Overlay
        if (uiState.isPaused) {
            PauseMenuOverlay(
                score = uiState.telemetry.sessionScore,
                zonesCount = uiState.telemetry.discoveredZoneIds.size,
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
            .clip(RoundedCornerShape(14.dp))
            .background(CityObsidian.copy(alpha = 0.78f))
            .border(1.dp, CityBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = "Health",
                tint = CrimsonAlert,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(CityCard)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .fillMaxWidth((telemetry.health / 100f).coerceIn(0f, 1f))
                        .background(CrimsonAlert)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = "Stamina",
                tint = ElectricCyan,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(CityCard)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .fillMaxWidth((telemetry.stamina / 100f).coerceIn(0f, 1f))
                        .background(ElectricCyan)
                )
            }
        }
    }
}

@Composable
private fun HudScoreBadge(score: Int, zonesCount: Int) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CityObsidian.copy(alpha = 0.78f))
            .border(1.dp, SunsetGold.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = "Score",
                tint = SunsetGold,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$score PTS",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                fontSize = 12.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Explore,
                contentDescription = "Zones",
                tint = EmeraldPulse,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$zonesCount/5 ZONES",
                style = MaterialTheme.typography.labelSmall,
                color = EmeraldPulse
            )
        }
    }
}

@Composable
private fun MiniMapRadar(telemetry: GameTelemetry) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(94.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CityObsidian.copy(alpha = 0.82f))
                .border(1.2.dp, ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                .testTag("hud_minimap")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val half = size.width * 0.5f
                val worldScale = half / CityWorldData.CITY_HALF_EXTENT

                // Roads on radar
                val roadColor = Color(0xFF334155)
                for (r in CityWorldData.roads) {
                    val left = half + (r.x - r.width * 0.5f) * worldScale
                    val top = half + (r.z - r.depth * 0.5f) * worldScale
                    drawRect(
                        color = roadColor,
                        topLeft = Offset(left, top),
                        size = Size(r.width * worldScale, r.depth * worldScale)
                    )
                }

                // Skyscrapers on radar
                val bColor = Color(0xFF475569)
                for (b in CityWorldData.buildings) {
                    val left = half + (b.x - b.width * 0.5f) * worldScale
                    val top = half + (b.z - b.depth * 0.5f) * worldScale
                    drawRect(
                        color = bColor,
                        topLeft = Offset(left, top),
                        size = Size(b.width * worldScale, b.depth * worldScale)
                    )
                }

                // Exploration zones
                for (z in CityZones.all) {
                    val discovered = telemetry.discoveredZoneIds.contains(z.id)
                    drawCircle(
                        color = if (discovered) EmeraldPulse else SunsetGold,
                        radius = 4.5f,
                        center = Offset(half + z.x * worldScale, half + z.z * worldScale)
                    )
                }

                // Player arrow
                val px = half + telemetry.playerX * worldScale
                val pz = half + telemetry.playerZ * worldScale
                val deg = -(telemetry.playerYawRad * 180f / PI.toFloat()) + 180f
                rotate(degrees = deg, pivot = Offset(px, pz)) {
                    val arrow = Path().apply {
                        moveTo(px, pz - 7f)
                        lineTo(px + 5f, pz + 5f)
                        lineTo(px, pz + 2f)
                        lineTo(px - 5f, pz + 5f)
                        close()
                    }
                    drawPath(arrow, color = ElectricCyan)
                }
            }
            Text(
                text = "N",
                style = MaterialTheme.typography.labelSmall,
                color = ElectricCyan,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun VirtualJoystick(
    onVectorChange: (Float, Float) -> Unit
) {
    val radiusDp = 56.dp
    val maxDragPx = with(LocalDensity.current) { 38.dp.toPx() }
    var knobX by remember { mutableFloatStateOf(0f) }
    var knobY by remember { mutableFloatStateOf(0f) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(radiusDp * 2)
            .clip(CircleShape)
            .background(CityObsidian.copy(alpha = 0.45f))
            .border(1.8.dp, ElectricCyan.copy(alpha = 0.55f), CircleShape)
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
                .size(46.dp)
                .clip(CircleShape)
                .background(ElectricCyan.copy(alpha = 0.85f))
                .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
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
            .clip(RoundedCornerShape(12.dp))
            .background(CityObsidian.copy(alpha = 0.65f))
            .border(1.dp, ElectricCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
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
        Icon(imageVector = icon, contentDescription = description, tint = ElectricCyan)
    }
}

@Composable
private fun ActionCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    filled: Boolean,
    sizeDp: Int,
    testTag: String,
    onTap: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(sizeDp.dp)
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(if (filled) accent else CityObsidian.copy(alpha = 0.72f))
            .border(1.8.dp, accent, CircleShape)
            .clickable(onClick = onTap)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (filled) CityObsidian else accent,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (filled) CityObsidian else TextPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PauseMenuOverlay(
    score: Int,
    zonesCount: Int,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onReturnToLobby: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(CityObsidian.copy(alpha = 0.85f))
            .padding(24.dp)
            .testTag("modal_pause")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CitySlate)
                .border(1.5.dp, SunsetGold.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .padding(22.dp)
        ) {
            Text(
                text = "GAME PAUSED",
                style = MaterialTheme.typography.headlineMedium,
                color = SunsetGold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Score: $score PTS • Zones: $zonesCount/5",
                style = MaterialTheme.typography.bodyMedium,
                color = ElectricCyan
            )
            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(containerColor = SunsetGold, contentColor = CityObsidian),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_resume")
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("RESUME", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onRestart,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, ElectricCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_restart")
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = ElectricCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("RESTART EXPLORATION", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onReturnToLobby,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, CrimsonAlert),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_return_lobby")
            ) {
                Text("RETURN TO LOBBY", style = MaterialTheme.typography.labelLarge, color = CrimsonAlert)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Saini Simulator • Ajay Saini (@ajayzsaini)",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}
