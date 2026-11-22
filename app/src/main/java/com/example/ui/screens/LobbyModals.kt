package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.engine.LobbyCameraPreset
import com.example.game.engine.LobbyPoseMode
import com.example.game.model.CharacterSkin
import com.example.game.model.CityZones
import com.example.game.model.ControlLayout
import com.example.game.model.DensityLevel
import com.example.game.model.GameAvatars
import com.example.game.model.GameEmotes
import com.example.game.model.GameSettings
import com.example.game.model.GameStoreCatalog
import com.example.game.model.GameVehicleWraps
import com.example.game.model.GraphicsQuality
import com.example.game.model.HudElementId
import com.example.game.model.PlayerProfileStats
import com.example.game.model.RenderResolution
import com.example.game.model.StoreCategory
import com.example.game.model.StoreItem
import com.example.game.model.VehicleControlMode
import com.example.game.viewmodel.GameUiState
import com.example.ui.components.TacticalAvatar
import com.example.ui.theme.CityBorder
import com.example.ui.theme.CityCard
import com.example.ui.theme.CityObsidian
import com.example.ui.theme.CitySlate
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
import kotlin.math.abs

private val ModalBackdrop = Color(0xFF06090E).copy(alpha = 0.86f)
private val ModalHairline = Color.White.copy(alpha = 0.12f)

@Composable
private fun CompactTacticalSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = TacticalCyan
) {
    var trackWidthPx by remember { mutableStateOf(1f) }
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    fun updateFromX(x: Float) {
        val nextFrac = (x / trackWidthPx).coerceIn(0f, 1f)
        onValueChange(valueRange.start + nextFrac * span)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(22.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(valueRange) {
                detectTapGestures { offset -> updateFromX(offset.x) }
            }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    updateFromX(change.position.x)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cy = size.height * 0.5f
            val trackH = 3.5.dp.toPx()
            val thumbR = 5.5.dp.toPx()
            val usableW = (size.width - thumbR * 2f).coerceAtLeast(1f)
            val thumbX = thumbR + usableW * fraction

            drawRoundRect(
                color = CityBorder,
                topLeft = Offset(thumbR, cy - trackH * 0.5f),
                size = Size(usableW, trackH),
                cornerRadius = CornerRadius(trackH * 0.5f, trackH * 0.5f)
            )
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(thumbR, cy - trackH * 0.5f),
                size = Size((usableW * fraction).coerceAtLeast(0f), trackH),
                cornerRadius = CornerRadius(trackH * 0.5f, trackH * 0.5f)
            )
            drawCircle(
                color = activeColor.copy(alpha = 0.28f),
                radius = thumbR + 3.dp.toPx(),
                center = Offset(thumbX, cy)
            )
            drawCircle(
                color = Color.White,
                radius = thumbR,
                center = Offset(thumbX, cy)
            )
            drawCircle(
                color = activeColor,
                radius = thumbR * 0.45f,
                center = Offset(thumbX, cy)
            )
        }
    }
}

@Composable
private fun CompactTogglePill(
    checked: Boolean,
    onToggle: () -> Unit,
    testTag: String? = null
) {
    val bg = if (checked) TacticalCyan else CityObsidian
    val borderCol = if (checked) TacticalCyan else CityBorder
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (checked) Arrangement.End else Arrangement.Start,
        modifier = Modifier
            .width(36.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(0.8.dp, borderCol, RoundedCornerShape(9.dp))
            .clickable(onClick = onToggle)
            .padding(2.dp)
            .let { if (testTag != null) it.testTag(testTag) else it }
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(if (checked) CityObsidian else TextSecondary)
        )
    }
}

@Composable
private fun CompactToggleSettingRow(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    testTag: String? = null,
    onToggle: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(CityCard.copy(alpha = 0.65f))
            .border(0.7.dp, if (checked) TacticalCyan.copy(alpha = 0.42f) else CityBorder, RoundedCornerShape(5.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .let { if (testTag != null) it.testTag(testTag) else it }
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 8.sp,
                    lineHeight = 10.sp
                )
            }
        }
        CompactTogglePill(checked = checked, onToggle = onToggle)
    }
}

@Composable
private fun CompactPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = TacticalAccent,
    testTag: String? = null
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(accent, if (accent == TacticalAccent) TacticalAccentDark else accent)
                )
            )
            .border(0.8.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
            .let { if (testTag != null) it.testTag(testTag) else it }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp
        )
    }
}

/**
 * 3D Character & Animation Inspector Overlay — shows ONLY the real rigged 3D Ajay Saini character,
 * all 6 real GLB animations, and your real equipped Vehicle Paint & Emote!
 */
@Composable
fun CharacterInspectorOverlay(
    uiState: GameUiState,
    zoomNormalized: Float,
    poseMode: LobbyPoseMode,
    onZoomChange: (Float) -> Unit,
    onSelectPreset: (LobbyCameraPreset) -> Unit,
    onSelectPose: (LobbyPoseMode) -> Unit,
    onResetCamera: () -> Unit,
    onSelectSkin: (CharacterSkin) -> Unit,
    onOpenStore: () -> Unit,
    onDismiss: () -> Unit
) {
    val activeWrap = remember(uiState.settings.selectedVehicleWrapId) {
        GameVehicleWraps.byId(uiState.settings.selectedVehicleWrapId)
    }
    val activeEmote = remember(uiState.settings.selectedEmoteId) {
        GameEmotes.byId(uiState.settings.selectedEmoteId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("modal_characters")
    ) {
        // Top-Left Back Button + Gesture Hint
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CitySlate.copy(alpha = 0.90f))
                    .border(0.8.dp, ModalHairline, RoundedCornerShape(4.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 10.dp)
                    .testTag("btn_back_from_characters")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to Lobby",
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "CHARACTER",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontSize = 13.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(CityObsidian.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "DRAG 360° • PINCH ZOOM",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 9.sp
                )
            }
        }

        // Bottom-Left: Camera Presets + Zoom Slider
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .widthIn(max = 285.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CityObsidian.copy(alpha = 0.84f))
                .border(0.8.dp, ModalHairline, RoundedCornerShape(6.dp))
                .padding(7.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LobbyCameraPreset.entries.forEach { preset ->
                    val selected = abs(zoomNormalized - preset.zoomNormalized) < 0.16f
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (selected) TacticalCyan else CityCard)
                            .clickable { onSelectPreset(preset) }
                            .padding(horizontal = 7.dp)
                            .testTag("preset_${preset.name.lowercase()}")
                    ) {
                        if (preset == LobbyCameraPreset.FACE_ZOOM) {
                            Icon(
                                imageVector = Icons.Rounded.Face,
                                contentDescription = null,
                                tint = if (selected) CityObsidian else TacticalCyan,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) CityObsidian else TextPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CityCard)
                        .clickable(onClick = onResetCamera)
                        .testTag("btn_char_reset_cam")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reset View", tint = TextSecondary, modifier = Modifier.size(13.dp))
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "ZOOM ${(zoomNormalized * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = TacticalCyan,
                    fontSize = 9.sp,
                    modifier = Modifier.width(56.dp)
                )
                CompactTacticalSlider(
                    value = zoomNormalized,
                    onValueChange = onZoomChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("slider_character_zoom")
                )
            }
        }

        // Right Side Drawer: Real 3D Rig Profile + 6 Animation Previews + Active Store Loadout
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .widthIn(min = 250.dp, max = 286.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CitySlate.copy(alpha = 0.94f))
                .border(0.8.dp, ModalHairline, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Text(
                text = uiState.selectedSkin.name.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = TacticalCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = uiState.selectedSkin.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 9.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .background(CityCard)
                    .padding(7.dp)
            ) {
                CharacterStatRow("AGILITY", uiState.selectedSkin.agilityRating)
                CharacterStatRow("STAMINA", uiState.selectedSkin.staminaRating)
                CharacterStatRow("DRIVING", uiState.selectedSkin.drivingRating)
            }

            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = "PREVIEW 3D ANIMATIONS",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 9.sp,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(4.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                LobbyPoseMode.entries.chunked(2).forEach { pair ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        pair.forEach { mode ->
                            val active = poseMode == mode
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (active) TacticalCyan else CityCard)
                                    .border(0.8.dp, if (active) TacticalCyan else CityBorder, RoundedCornerShape(5.dp))
                                    .clickable { onSelectPose(mode) }
                                    .testTag("pose_${mode.name.lowercase()}")
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) CityObsidian else TextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Real Equipped Vehicle Paint & Emote Summary
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(CityCard)
                        .border(0.7.dp, CityBorder, RoundedCornerShape(5.dp))
                        .clickable(onClick = onOpenStore)
                        .padding(7.dp)
                ) {
                    Text(
                        text = "ACTIVE CUSTOMIZATION (TAP STORE)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TacticalCyan,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(activeWrap.swatchColor)
                                .border(0.7.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        )
                        Text(
                            text = "Vehicle Paint: ${activeWrap.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            fontSize = 10.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.EmojiEmotions, contentDescription = null, tint = EmeraldPulse, modifier = Modifier.size(12.dp))
                        Text(
                            text = "Equipped Emote: ${activeEmote.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            CompactPrimaryButton(
                text = "DONE",
                onClick = onDismiss,
                testTag = "btn_equip_return",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CharacterStatRow(label: String, fraction: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.width(52.dp),
            fontSize = 9.sp
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CityObsidian)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0.1f, 1f))
                    .background(TacticalCyan)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "${(fraction * 100).toInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun ModalContainer(
    title: String,
    testTag: String,
    maxWidthDp: Int = 520,
    scrollableBody: Boolean = true,
    headerExtra: (@Composable () -> Unit)? = null,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(ModalBackdrop)
            .clickable(onClick = onDismiss)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = maxWidthDp.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CitySlate)
                .border(0.8.dp, ModalHairline, RoundedCornerShape(8.dp))
                .clickable(enabled = false, onClick = {})
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TacticalAccent)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        letterSpacing = 1.0.sp
                    )
                    headerExtra?.invoke()
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CityCard)
                        .border(0.8.dp, CityBorder, RoundedCornerShape(4.dp))
                        .clickable(onClick = onDismiss)
                        .testTag("btn_close_modal")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.8.dp)
                    .background(ModalHairline)
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (scrollableBody) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    content()
                }
            } else {
                content()
            }
        }
    }
}

/**
 * 100% REAL Working In-Game Store:
 * - Real 3D Vehicle Paint Wraps (visibly re-colors any Car or Bullet 350 you drive!)
 * - Real 3D Character Emotes (plays actual rigged GLB animations via the HUD Emote button!)
 * ZERO fake bundles or unimplemented outfits.
 */
@Composable
fun StoreModal(
    uiState: GameUiState,
    onPurchaseOrEquip: (StoreItem) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(StoreCategory.ALL) }
    val filteredItems = remember(selectedCategory) {
        if (selectedCategory == StoreCategory.ALL) {
            GameStoreCatalog.items
        } else {
            GameStoreCatalog.items.filter { it.category == selectedCategory }
        }
    }

    ModalContainer(
        title = "STORE",
        testTag = "modal_store",
        maxWidthDp = 620,
        headerExtra = {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CityCard)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Rounded.MonetizationOn, contentDescription = null, tint = CoinSilver, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("${uiState.profileStats.coins}", style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontSize = 11.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CityCard)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Rounded.Diamond, contentDescription = null, tint = DiamondBlue, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("${uiState.profileStats.diamonds}", style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontSize = 11.sp)
                }
            }
        },
        onDismiss = onDismiss
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            StoreCategory.entries.forEach { cat ->
                val active = selectedCategory == cat
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (active) TacticalAccent else CityCard)
                        .border(0.8.dp, if (active) TacticalAccent else CityBorder, RoundedCornerShape(4.dp))
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 10.dp)
                        .testTag("store_tab_${cat.name.lowercase()}")
                ) {
                    Text(
                        text = cat.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val rows = filteredItems.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            rows.forEach { pair ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    pair.forEach { item ->
                        val owned = item.id in uiState.ownedItemIds
                        val equipped = when {
                            item.linkedWrapId != null -> uiState.settings.selectedVehicleWrapId == item.linkedWrapId
                            item.linkedEmoteId != null -> uiState.settings.selectedEmoteId == item.linkedEmoteId
                            else -> false
                        }

                        StoreItemCard(
                            item = item,
                            owned = owned,
                            equipped = equipped,
                            onAction = { onPurchaseOrEquip(item) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreItemCard(
    item: StoreItem,
    owned: Boolean,
    equipped: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CityCard)
            .border(0.8.dp, if (equipped) TacticalCyan else CityBorder, RoundedCornerShape(6.dp))
            .padding(8.dp)
            .testTag("store_item_${item.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (item.swatchColor != null) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(item.swatchColor)
                            .border(0.9.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = if (item.linkedEmoteId != null) Icons.Rounded.EmojiEmotions else Icons.Rounded.DirectionsCar,
                        contentDescription = null,
                        tint = TacticalCyan,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(item.rarity.color.copy(alpha = 0.18f))
                        .border(0.6.dp, item.rarity.color, RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = item.rarity.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = item.rarity.color,
                        fontSize = 8.sp
                    )
                }
            }
            Text(
                text = item.category.label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 8.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = item.description,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            minLines = 2
        )

        Spacer(modifier = Modifier.height(6.dp))

        val buttonBg = when {
            equipped -> EmeraldPulse.copy(alpha = 0.18f)
            owned -> CitySlate
            else -> TacticalAccent
        }
        val buttonTextColor = if (equipped) EmeraldPulse else TextPrimary

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(buttonBg)
                .border(0.8.dp, if (equipped) EmeraldPulse else if (owned) CityBorder else TacticalAccent, RoundedCornerShape(4.dp))
                .clickable(onClick = onAction)
                .testTag("btn_store_action_${item.id}")
        ) {
            if (equipped) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = EmeraldPulse, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("EQUIPPED", style = MaterialTheme.typography.labelSmall, color = buttonTextColor, fontSize = 10.sp)
            } else if (owned) {
                Text("EQUIP", style = MaterialTheme.typography.labelSmall, color = buttonTextColor, fontSize = 10.sp)
            } else if (item.priceDiamonds > 0) {
                Icon(Icons.Rounded.Diamond, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("BUY ${item.priceDiamonds}", style = MaterialTheme.typography.labelSmall, color = buttonTextColor, fontSize = 10.sp)
            } else {
                Icon(Icons.Rounded.MonetizationOn, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("BUY ${item.priceCoins}", style = MaterialTheme.typography.labelSmall, color = buttonTextColor, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun ProfileModal(
    stats: PlayerProfileStats,
    activeSkin: CharacterSkin,
    onSaveProfile: (name: String, avatarId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var editName by remember(stats.playerName) { mutableStateOf(stats.playerName.ifBlank { "SAINI PLAYER" }) }
    var editAvatarId by remember(stats.selectedAvatarId) { mutableStateOf(stats.selectedAvatarId) }
    val cleanName = editName.trim()
    val hasChanges = (cleanName != stats.playerName || editAvatarId != stats.selectedAvatarId) && cleanName.length >= 2

    ModalContainer(
        title = "PROFILE",
        testTag = "modal_profile",
        maxWidthDp = 500,
        onDismiss = onDismiss
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(CityCard)
                .border(0.8.dp, CityBorder, RoundedCornerShape(6.dp))
                .padding(8.dp)
        ) {
            TacticalAvatar(
                avatarId = editAvatarId,
                size = 48.dp,
                cornerRadius = 5.dp,
                borderWidth = 1.2.dp,
                selected = true
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "USERNAME",
                    style = MaterialTheme.typography.labelSmall,
                    color = TacticalCyan,
                    fontSize = 8.sp,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CityObsidian)
                        .border(0.8.dp, if (cleanName.length >= 2) TacticalCyan.copy(alpha = 0.7f) else CityBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp)
                ) {
                    BasicTextField(
                        value = editName,
                        onValueChange = { next -> if (next.length <= 16) editName = next.replace("\n", "") },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        cursorBrush = SolidColor(TacticalCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_profile_username")
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Character: ${activeSkin.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 9.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "CHOOSE AVATAR",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontSize = 9.sp,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            GameAvatars.all.forEach { av ->
                val selected = av.id == editAvatarId
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) TacticalCyan.copy(alpha = 0.16f) else CityCard)
                        .border(0.8.dp, if (selected) TacticalCyan else CityBorder, RoundedCornerShape(4.dp))
                        .clickable { editAvatarId = av.id }
                        .padding(vertical = 4.dp)
                        .testTag("profile_avatar_${av.id}")
                ) {
                    TacticalAvatar(
                        avatarId = av.id,
                        size = 28.dp,
                        cornerRadius = 4.dp,
                        selected = selected
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = av.name.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) TacticalCyan else TextSecondary,
                        fontSize = 7.sp,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ProfileStatTile(
                icon = Icons.Rounded.Star,
                label = "SCORE",
                value = "${stats.totalScore}",
                tint = TacticalCyan,
                modifier = Modifier.weight(1f)
            )
            ProfileStatTile(
                icon = Icons.Rounded.EmojiEvents,
                label = "BEST",
                value = "${stats.highScore}",
                tint = TacticalSilver,
                modifier = Modifier.weight(1f)
            )
            ProfileStatTile(
                icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                label = "DISTANCE",
                value = "${stats.distanceMeters}m",
                tint = EmeraldPulse,
                modifier = Modifier.weight(1f)
            )
            ProfileStatTile(
                icon = Icons.Rounded.Explore,
                label = "ZONES",
                value = "${stats.zonesDiscoveredTotal}/${CityZones.all.size}",
                tint = TacticalAccent,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        CompactPrimaryButton(
            text = if (hasChanges) "SAVE PROFILE" else "CLOSE",
            onClick = {
                if (hasChanges) {
                    onSaveProfile(cleanName, editAvatarId)
                }
                onDismiss()
            },
            testTag = "btn_profile_save_close",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ProfileStatTile(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CityCard)
            .border(0.8.dp, CityBorder, RoundedCornerShape(6.dp))
            .padding(vertical = 7.dp, horizontal = 4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontSize = 14.sp)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 8.sp)
    }
}

private enum class SettingsTab(val label: String, val icon: ImageVector) {
    GRAPHICS("GRAPHICS", Icons.Rounded.Tune),
    WORLD("WORLD", Icons.Rounded.Public),
    CONTROLS("CONTROLS", Icons.Rounded.Gamepad),
    AUDIO("AUDIO", Icons.Rounded.VolumeUp)
}

/**
 * Full Detailed Real Working Settings Modal — every single toggle, slider & selector
 * directly drives OpenGLCityRenderer, RealisticGrassSystem, GameAudioEngine, or GameplayScreen!
 */
@Composable
fun SettingsModal(
    settings: GameSettings,
    onToggleMute: () -> Unit,
    onUpdateSettings: ((GameSettings) -> GameSettings) -> Unit,
    onSetGraphics: (GraphicsQuality) -> Unit,
    onSetControlLayout: (ControlLayout) -> Unit,
    onSetVehicleControlMode: (VehicleControlMode) -> Unit,
    onSetTargetFps: (Int) -> Unit,
    onSetStreamingRadius: (Float) -> Unit,
    onToggleFpsCounter: (Boolean) -> Unit,
    onOpenCustomHud: () -> Unit,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableStateOf(SettingsTab.GRAPHICS) }
    val showFps = settings.customHudLayout.configFor(HudElementId.FPS_COUNTER).visible

    ModalContainer(
        title = "SETTINGS",
        testTag = "modal_settings",
        maxWidthDp = 610,
        scrollableBody = false,
        onDismiss = onDismiss
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(238.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left Vertical Tab Rail
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .width(108.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CityObsidian.copy(alpha = 0.65f))
                    .border(0.8.dp, ModalHairline, RoundedCornerShape(6.dp))
                    .padding(5.dp)
            ) {
                SettingsTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (selected) CityCard else Color.Transparent)
                            .clickable { activeTab = tab }
                            .padding(horizontal = 6.dp)
                            .testTag("settings_tab_${tab.name.lowercase()}")
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (selected) TacticalAccent else Color.Transparent)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) TacticalCyan else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) TextPrimary else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                CompactPrimaryButton(
                    text = "SAVE",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Right Scrollable Settings Content Pane
            Column(
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CityObsidian.copy(alpha = 0.45f))
                    .border(0.8.dp, ModalHairline, RoundedCornerShape(6.dp))
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (activeTab) {
                    SettingsTab.GRAPHICS -> {
                        CompactSegmentedSettingRow(
                            label = "QUALITY",
                            options = GraphicsQuality.entries.map { q ->
                                Triple(q.label.uppercase(), settings.graphicsQuality == q, "gfx_${q.name.lowercase()}")
                            },
                            onSelectIndex = { idx -> onSetGraphics(GraphicsQuality.entries[idx]) }
                        )

                        CompactSegmentedSettingRow(
                            label = "RESOLUTION",
                            options = RenderResolution.entries.map { res ->
                                Triple(res.label, settings.renderResolution == res, "res_${res.name.lowercase()}")
                            },
                            onSelectIndex = { idx ->
                                onUpdateSettings { it.copy(renderResolution = RenderResolution.entries[idx]) }
                            }
                        )

                        val fpsOptions = listOf(30, 60, 90, 120)
                        CompactSegmentedSettingRow(
                            label = "TARGET FPS",
                            options = fpsOptions.map { fps ->
                                Triple("$fps", settings.targetFps == fps, "fps_$fps")
                            },
                            onSelectIndex = { idx -> onSetTargetFps(fpsOptions[idx]) }
                        )

                        CompactToggleSettingRow(
                            label = "REAL-TIME SHADOWS",
                            subtitle = "Disable on mid/low phones for +15 FPS smoother gameplay",
                            checked = settings.shadowsEnabled,
                            testTag = "toggle_shadows",
                            onToggle = { onUpdateSettings { it.copy(shadowsEnabled = !it.shadowsEnabled) } }
                        )

                        CompactToggleSettingRow(
                            label = "SHOW LIVE FPS COUNTER",
                            subtitle = "Displays real-time frame rate on screen (movable in Custom HUD)",
                            checked = showFps,
                            testTag = "toggle_fps_counter",
                            onToggle = { onToggleFpsCounter(!showFps) }
                        )
                    }

                    SettingsTab.WORLD -> {
                        CompactSegmentedSettingRow(
                            label = "GRASS DENSITY",
                            options = DensityLevel.entries.map { d ->
                                Triple(d.label.uppercase(), settings.grassDensity == d, "grass_${d.name.lowercase()}")
                            },
                            onSelectIndex = { idx ->
                                onUpdateSettings { it.copy(grassDensity = DensityLevel.entries[idx]) }
                            }
                        )

                        CompactSegmentedSettingRow(
                            label = "AI TRAFFIC",
                            options = DensityLevel.entries.map { d ->
                                Triple(d.label.uppercase(), settings.aiTrafficDensity == d, "traffic_${d.name.lowercase()}")
                            },
                            onSelectIndex = { idx ->
                                onUpdateSettings { it.copy(aiTrafficDensity = DensityLevel.entries[idx]) }
                            }
                        )

                        CompactSegmentedSettingRow(
                            label = "PEDESTRIANS",
                            options = DensityLevel.entries.map { d ->
                                Triple(d.label.uppercase(), settings.npcDensity == d, "npc_${d.name.lowercase()}")
                            },
                            onSelectIndex = { idx ->
                                onUpdateSettings { it.copy(npcDensity = DensityLevel.entries[idx]) }
                            }
                        )

                        val radii = listOf(36f to "36m", 52f to "52m", 72f to "72m")
                        CompactSegmentedSettingRow(
                            label = "DRAW RADIUS",
                            options = radii.map { (rad, lbl) ->
                                Triple(lbl, abs(settings.streamingRadiusMeters - rad) < 4f, "zone_${rad.toInt()}")
                            },
                            onSelectIndex = { idx -> onSetStreamingRadius(radii[idx].first) }
                        )

                        CompactToggleSettingRow(
                            label = "DYNAMIC DAY / NIGHT",
                            subtitle = "Automatically cycles sunlight & city night lights over time",
                            checked = settings.dynamicDayNight,
                            testTag = "toggle_day_night",
                            onToggle = { onUpdateSettings { it.copy(dynamicDayNight = !it.dynamicDayNight) } }
                        )
                    }

                    SettingsTab.CONTROLS -> {
                        CompactSegmentedSettingRow(
                            label = "ON-FOOT PAD",
                            options = ControlLayout.entries.map { layout ->
                                Triple(layout.label.uppercase(), settings.controlLayout == layout, "ctrl_${layout.name.lowercase()}")
                            },
                            onSelectIndex = { idx -> onSetControlLayout(ControlLayout.entries[idx]) }
                        )

                        CompactSegmentedSettingRow(
                            label = "VEHICLE DRIVE",
                            options = VehicleControlMode.entries.map { mode ->
                                Triple(mode.label.uppercase(), settings.vehicleControlMode == mode, "veh_ctrl_${mode.name.lowercase()}")
                            },
                            onSelectIndex = { idx -> onSetVehicleControlMode(VehicleControlMode.entries[idx]) }
                        )

                        // Individual Custom HUD Editor Launcher (all 22 buttons!)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(5.dp))
                                .background(TacticalCyan.copy(alpha = 0.14f))
                                .border(0.9.dp, TacticalCyan.copy(alpha = 0.65f), RoundedCornerShape(5.dp))
                                .clickable { onOpenCustomHud() }
                                .padding(horizontal = 9.dp, vertical = 7.dp)
                                .testTag("btn_open_custom_hud")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = "Custom HUD",
                                    tint = TacticalCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Column {
                                    Text(
                                        text = "CUSTOM HUD EDITOR (22 WIDGETS)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Move, resize, fade or hide every single button individually",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        fontSize = 8.sp
                                    )
                                }
                            }
                            Text(
                                text = "EDIT",
                                style = MaterialTheme.typography.labelSmall,
                                color = CityObsidian,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(TacticalCyan)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        CompactSliderSettingRow(
                            label = "CAMERA SENSITIVITY",
                            valueText = String.format("%.1fx", settings.cameraSensitivity),
                            value = settings.cameraSensitivity,
                            valueRange = 0.5f..2.0f,
                            onValueChange = { v -> onUpdateSettings { it.copy(cameraSensitivity = v) } }
                        )

                        CompactSliderSettingRow(
                            label = "GLOBAL HUD OPACITY",
                            valueText = "${(settings.hudOpacity * 100).toInt()}%",
                            value = settings.hudOpacity,
                            valueRange = 0.40f..1.0f,
                            onValueChange = { v -> onUpdateSettings { it.copy(hudOpacity = v) } }
                        )

                        CompactToggleSettingRow(
                            label = "AUTO-CENTER VEHICLE CAMERA",
                            subtitle = "Smoothly aligns camera behind car/bike while driving",
                            checked = settings.autoCenterVehicleCam,
                            testTag = "toggle_auto_center_cam",
                            onToggle = { onUpdateSettings { it.copy(autoCenterVehicleCam = !it.autoCenterVehicleCam) } }
                        )

                        CompactToggleSettingRow(
                            label = "AUTO-SPRINT ON FULL TILT",
                            subtitle = "Automatically sprints when pushing joystick all the way forward",
                            checked = settings.autoSprint,
                            testTag = "toggle_auto_sprint",
                            onToggle = { onUpdateSettings { it.copy(autoSprint = !it.autoSprint) } }
                        )

                        CompactToggleSettingRow(
                            label = "INVERT CAMERA Y-AXIS",
                            subtitle = "Flips vertical look direction when dragging screen",
                            checked = settings.invertCameraY,
                            testTag = "toggle_invert_y",
                            onToggle = { onUpdateSettings { it.copy(invertCameraY = !it.invertCameraY) } }
                        )

                        CompactToggleSettingRow(
                            label = "MINIMAP AUTO-ZOOM",
                            subtitle = "Automatically zooms radar out at high vehicle speeds",
                            checked = settings.minimapAutoZoom,
                            testTag = "toggle_minimap_zoom",
                            onToggle = { onUpdateSettings { it.copy(minimapAutoZoom = !it.minimapAutoZoom) } }
                        )

                        CompactToggleSettingRow(
                            label = "HAPTIC FEEDBACK",
                            subtitle = "Tactile vibration response on combat, emote & vehicle buttons",
                            checked = settings.hapticsEnabled,
                            testTag = "toggle_haptics",
                            onToggle = { onUpdateSettings { it.copy(hapticsEnabled = !it.hapticsEnabled) } }
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(5.dp))
                                .background(CityCard)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Keyboard, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "WASD: Move • Space: Jump • Shift: Run • E: Drive/Use • B: Emote • H: Horn",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontSize = 8.sp
                            )
                        }
                    }

                    SettingsTab.AUDIO -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(CityCard)
                                .border(0.8.dp, if (settings.isMuted) CityBorder else TacticalCyan.copy(alpha = 0.55f), RoundedCornerShape(5.dp))
                                .clickable(onClick = onToggleMute)
                                .padding(horizontal = 9.dp)
                                .testTag("settings_mute_toggle"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (settings.isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                    contentDescription = "Master Sound",
                                    tint = if (settings.isMuted) CrimsonAlert else TacticalCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = if (settings.isMuted) "MASTER SOUND: OFF" else "MASTER SOUND: ON",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary,
                                    fontSize = 11.sp
                                )
                            }
                            CompactTogglePill(checked = !settings.isMuted, onToggle = onToggleMute)
                        }

                        CompactToggleSettingRow(
                            label = "WORLD AMBIENT AUDIO",
                            subtitle = "City wind, ocean surf, forest birds, helicopter & rain ambience",
                            checked = settings.ambientAudioEnabled,
                            testTag = "toggle_ambient_audio",
                            onToggle = { onUpdateSettings { it.copy(ambientAudioEnabled = !it.ambientAudioEnabled) } }
                        )

                        CompactSliderSettingRow(
                            label = "MUSIC & AMBIENCE",
                            valueText = "${(settings.musicVolume * 100).toInt()}%",
                            value = settings.musicVolume,
                            onValueChange = { v -> onUpdateSettings { it.copy(musicVolume = v) } }
                        )

                        CompactSliderSettingRow(
                            label = "SFX & ENGINE",
                            valueText = "${(settings.sfxVolume * 100).toInt()}%",
                            value = settings.sfxVolume,
                            onValueChange = { v -> onUpdateSettings { it.copy(sfxVolume = v) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSegmentedSettingRow(
    label: String,
    options: List<Triple<String, Boolean, String>>,
    onSelectIndex: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(CityCard.copy(alpha = 0.65f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(88.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            options.forEachIndexed { idx, (text, selected, tag) ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(25.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) TacticalCyan else CityObsidian.copy(alpha = 0.72f))
                        .border(0.8.dp, if (selected) TacticalCyan else CityBorder, RoundedCornerShape(4.dp))
                        .clickable { onSelectIndex(idx) }
                        .testTag(tag)
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) CityObsidian else TextPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactSliderSettingRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(CityCard.copy(alpha = 0.65f))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall,
                color = TacticalCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        CompactTacticalSlider(
            value = value,
            valueRange = valueRange,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CreditsModal(onDismiss: () -> Unit) {
    ModalContainer(
        title = "ABOUT SAINI SIMULATOR",
        testTag = "modal_credits",
        maxWidthDp = 440,
        onDismiss = onDismiss
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CityCard)
                    .border(0.8.dp, TacticalCyan.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = "SAINI SIMULATOR",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Bharat 4.8km Open-World 3D Sandbox",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
                Text(
                    text = "v3.5",
                    style = MaterialTheme.typography.labelSmall,
                    color = CityObsidian,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(TacticalCyan)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CityCard)
                    .border(0.8.dp, CityBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    text = "CREATOR & LEAD DEVELOPER",
                    style = MaterialTheme.typography.labelSmall,
                    color = TacticalCyan,
                    fontSize = 8.sp,
                    letterSpacing = 0.9.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ajay Saini",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Instagram: @ajayzsaini",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Text(
                text = "Built on a native OpenGL ES 3D engine featuring rigged 6-clip character animation, chunk-streamed multi-biome terrain, drivable cars & Bullet 350 bikes, custom 22-widget HUD, and real-time audio.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 9.sp,
                textAlign = TextAlign.Start
            )

            CompactPrimaryButton(
                text = "CLOSE",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
