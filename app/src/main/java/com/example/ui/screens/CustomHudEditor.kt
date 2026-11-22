package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsMartialArts
import androidx.compose.material.icons.rounded.SportsMma
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.model.CustomHudLayout
import com.example.game.model.HudElementConfig
import com.example.game.model.HudElementGroup
import com.example.game.model.HudElementId
import com.example.ui.theme.CityBorder
import com.example.ui.theme.CityCard
import com.example.ui.theme.CityObsidian
import com.example.ui.theme.CitySlate
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.TacticalAccent
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

private enum class HudEditorLayerFilter(val label: String) {
    ON_FOOT("ON FOOT"),
    VEHICLE("VEHICLE"),
    ALL("ALL (22)")
}

/**
 * Full-Screen Individual Custom HUD Editor (22 Independent Widgets).
 * Every single button & indicator (Emote, Punch, Kick, Flip, Drop, Run, Jump, Use, Horn,
 * Steer L/R, Gas/Brake, Health, Stamina, FPS, Mini-Map, Time, Weather, etc.) can be moved,
 * resized (65%-145%), faded (30%-100%), or shown/hidden completely independently!
 */
@Composable
fun CustomHudEditorOverlay(
    initialLayout: CustomHudLayout,
    onSave: (CustomHudLayout) -> Unit,
    onCancel: () -> Unit
) {
    var draftLayout by remember(initialLayout) { mutableStateOf(initialLayout) }
    var layerFilter by remember { mutableStateOf(HudEditorLayerFilter.ON_FOOT) }
    var selectedId by remember { mutableStateOf(HudElementId.BTN_EMOTE) }
    var dockAtTop by remember { mutableStateOf(true) }

    BackHandler(onBack = onCancel)

    val visibleIds = remember(layerFilter) {
        when (layerFilter) {
            HudEditorLayerFilter.ON_FOOT -> HudElementId.entries.filter {
                it.group == HudElementGroup.COMMON || it.group == HudElementGroup.ON_FOOT
            }
            HudEditorLayerFilter.VEHICLE -> HudElementId.entries.filter {
                it.group == HudElementGroup.COMMON || it.group == HudElementGroup.VEHICLE
            }
            HudEditorLayerFilter.ALL -> HudElementId.entries
        }
    }

    val activeSelectedId = if (selectedId in visibleIds) selectedId else visibleIds.first()
    val selectedCfg = draftLayout.configFor(activeSelectedId)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A111C), Color(0xFF101A29), Color(0xFF080D14))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("custom_hud_editor")
    ) {
        // Alignment grid lines for crisp positioning
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cols = 12
            val rows = 6
            val gridColor = Color.White.copy(alpha = 0.06f)
            for (i in 1 until cols) {
                val x = size.width * (i.toFloat() / cols)
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            }
            for (j in 1 until rows) {
                val y = size.height * (j.toFloat() / rows)
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            drawLine(TacticalCyan.copy(alpha = 0.14f), Offset(size.width * 0.5f, 0f), Offset(size.width * 0.5f, size.height), strokeWidth = 1.2f)
            drawLine(TacticalCyan.copy(alpha = 0.14f), Offset(0f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), strokeWidth = 1.2f)
        }

        // Draggable stage matching GameplayScreen's exact coordinate container
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            val density = LocalDensity.current
            val stageW = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
            val stageH = with(density) { maxHeight.toPx().coerceAtLeast(1f) }

            visibleIds.forEach { elementId ->
                val cfg = draftLayout.configFor(elementId)
                val isSelected = activeSelectedId == elementId
                DraggableHudPreviewItem(
                    id = elementId,
                    config = cfg,
                    isSelected = isSelected,
                    stageWidthPx = stageW,
                    stageHeightPx = stageH,
                    onSelect = { selectedId = elementId },
                    onMove = { newX, newY ->
                        selectedId = elementId
                        draftLayout = draftLayout.updated(
                            elementId,
                            cfg.copy(
                                xNorm = newX.coerceIn(0.03f, 0.97f),
                                yNorm = newY.coerceIn(0.04f, 0.96f)
                            )
                        )
                    }
                )
            }
        }

        // Dockable Compact Inspector Panel (can flip Top <-> Bottom so it NEVER covers your target area!)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .align(if (dockAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CitySlate.copy(alpha = 0.95f))
                .border(1.dp, TacticalCyan.copy(alpha = 0.60f), RoundedCornerShape(8.dp))
                .padding(horizontal = 11.dp, vertical = 7.dp)
                .width(370.dp)
        ) {
            // Row 1: Layer Mode Tabs (ON FOOT / VEHICLE / ALL) + Dock Flip button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                HudEditorLayerFilter.entries.forEach { filter ->
                    val active = layerFilter == filter
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (active) TacticalCyan else CityCard)
                            .clickable {
                                layerFilter = filter
                                val firstInFilter = HudElementId.entries.first {
                                    when (filter) {
                                        HudEditorLayerFilter.ON_FOOT -> it.group == HudElementGroup.ON_FOOT
                                        HudEditorLayerFilter.VEHICLE -> it.group == HudElementGroup.VEHICLE
                                        HudEditorLayerFilter.ALL -> true
                                    }
                                }
                                selectedId = firstInFilter
                            }
                            .testTag("hud_filter_${filter.name.lowercase()}")
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) CityObsidian else TextPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CityCard)
                        .border(0.7.dp, CityBorder, RoundedCornerShape(4.dp))
                        .clickable { dockAtTop = !dockAtTop }
                        .testTag("btn_hud_dock_flip")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapVert,
                        contentDescription = "Move Toolbar Top or Bottom",
                        tint = TacticalCyan,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Row 2: Selected Item Name + Show/Hide Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "SELECTED: ${activeSelectedId.label.uppercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selectedCfg.visible) EmeraldPulse.copy(alpha = 0.20f) else CrimsonAlert.copy(alpha = 0.22f))
                        .border(
                            0.9.dp,
                            if (selectedCfg.visible) EmeraldPulse else CrimsonAlert,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable {
                            draftLayout = draftLayout.updated(
                                activeSelectedId,
                                selectedCfg.copy(visible = !selectedCfg.visible)
                            )
                        }
                        .padding(horizontal = 8.dp)
                        .testTag("btn_hud_toggle_visibility")
                ) {
                    Icon(
                        imageVector = if (selectedCfg.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = "Toggle Visibility",
                        tint = if (selectedCfg.visible) EmeraldPulse else CrimsonAlert,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (selectedCfg.visible) "SHOWN" else "HIDDEN",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selectedCfg.visible) EmeraldPulse else CrimsonAlert,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Row 3: Size & Opacity Sliders side-by-side
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SIZE ${(selectedCfg.scale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 8.sp
                    )
                    Slider(
                        value = selectedCfg.scale,
                        onValueChange = { nextScale ->
                            draftLayout = draftLayout.updated(
                                activeSelectedId,
                                selectedCfg.copy(scale = nextScale.coerceIn(0.65f, 1.45f))
                            )
                        },
                        valueRange = 0.65f..1.45f,
                        colors = SliderDefaults.colors(
                            thumbColor = TacticalCyan,
                            activeTrackColor = TacticalCyan,
                            inactiveTrackColor = CityBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .testTag("slider_hud_scale")
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OPACITY ${(selectedCfg.opacity * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 8.sp
                    )
                    Slider(
                        value = selectedCfg.opacity,
                        onValueChange = { nextAlpha ->
                            draftLayout = draftLayout.updated(
                                activeSelectedId,
                                selectedCfg.copy(opacity = nextAlpha.coerceIn(0.30f, 1.0f))
                            )
                        },
                        valueRange = 0.30f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = TacticalCyan,
                            activeTrackColor = TacticalCyan,
                            inactiveTrackColor = CityBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .testTag("slider_hud_opacity")
                    )
                }
            }

            // Row 4: RESET / CANCEL / SAVE
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                HudEditorToolbarBtn(
                    icon = Icons.Rounded.Refresh,
                    label = "RESET",
                    bg = CityCard,
                    border = CityBorder,
                    tint = TextPrimary,
                    testTag = "btn_hud_reset",
                    modifier = Modifier.weight(1f),
                    onClick = { draftLayout = CustomHudLayout.default() }
                )
                HudEditorToolbarBtn(
                    icon = Icons.Rounded.Close,
                    label = "CANCEL",
                    bg = CityCard,
                    border = CrimsonAlert.copy(alpha = 0.7f),
                    tint = CrimsonAlert,
                    testTag = "btn_hud_cancel",
                    modifier = Modifier.weight(1f),
                    onClick = onCancel
                )
                HudEditorToolbarBtn(
                    icon = Icons.Rounded.Check,
                    label = "SAVE",
                    bg = TacticalAccent,
                    border = Color.White.copy(alpha = 0.30f),
                    tint = TextPrimary,
                    testTag = "btn_hud_save",
                    modifier = Modifier.weight(1.2f),
                    onClick = { onSave(draftLayout) }
                )
            }
        }
    }
}

@Composable
private fun HudEditorToolbarBtn(
    icon: ImageVector,
    label: String,
    bg: Color,
    border: Color,
    tint: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .height(26.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(0.8.dp, border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
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
private fun DraggableHudPreviewItem(
    id: HudElementId,
    config: HudElementConfig,
    isSelected: Boolean,
    stageWidthPx: Float,
    stageHeightPx: Float,
    onSelect: () -> Unit,
    onMove: (Float, Float) -> Unit
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val posX = (config.xNorm * stageWidthPx - boxSize.width * 0.5f)
        .coerceIn(0f, (stageWidthPx - boxSize.width).coerceAtLeast(0f))
    val posY = (config.yNorm * stageHeightPx - boxSize.height * 0.5f)
        .coerceIn(0f, (stageHeightPx - boxSize.height).coerceAtLeast(0f))

    val outlineColor = when {
        isSelected -> TacticalCyan
        !config.visible -> CrimsonAlert.copy(alpha = 0.75f)
        else -> Color.White.copy(alpha = 0.38f)
    }

    val isCircleButton = id in setOf(
        HudElementId.JOYSTICK,
        HudElementId.BTN_PUNCH,
        HudElementId.BTN_KICK,
        HudElementId.BTN_FLIP,
        HudElementId.BTN_DROP,
        HudElementId.BTN_EMOTE,
        HudElementId.BTN_RUN,
        HudElementId.BTN_JUMP,
        HudElementId.BTN_USE,
        HudElementId.BTN_HORN
    )
    val itemShape = if (isCircleButton) CircleShape else RoundedCornerShape(6.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset { IntOffset(posX.roundToInt(), posY.roundToInt()) }
            .onSizeChanged { boxSize = it }
            .scale(config.scale)
            .alpha(if (config.visible) config.opacity else 0.28f)
            .clip(itemShape)
            .background(if (isSelected) TacticalCyan.copy(alpha = 0.22f) else CityObsidian.copy(alpha = 0.78f))
            .border(if (isSelected) 1.8.dp else 0.9.dp, outlineColor, itemShape)
            .clickable(onClick = onSelect)
            .pointerInput(id, config.xNorm, config.yNorm, stageWidthPx, stageHeightPx) {
                var curX = config.xNorm
                var curY = config.yNorm
                detectDragGestures(
                    onDragStart = { onSelect() }
                ) { change, dragAmount ->
                    change.consume()
                    curX = (curX + dragAmount.x / stageWidthPx).coerceIn(0.03f, 0.97f)
                    curY = (curY + dragAmount.y / stageHeightPx).coerceIn(0.04f, 0.96f)
                    onMove(curX, curY)
                }
            }
            .padding(horizontal = if (isCircleButton) 0.dp else 7.dp, vertical = if (isCircleButton) 0.dp else 4.dp)
            .testTag("hud_editor_item_${id.name.lowercase()}")
    ) {
        when (id) {
            HudElementId.HEALTH_BAR -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                Text("HEALTH", color = TextPrimary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            HudElementId.STAMINA_BAR -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(11.dp))
                Text("STAMINA", color = TacticalCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            HudElementId.SCORE_BADGE -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.Star, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(11.dp))
                Text("SCORE", color = TextPrimary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            HudElementId.FPS_COUNTER -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Icons.Rounded.Speed, contentDescription = null, tint = EmeraldPulse, modifier = Modifier.size(11.dp))
                Text("60 FPS", color = EmeraldPulse, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            HudElementId.LOCATION_BAR -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Icons.Rounded.Explore, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(11.dp))
                Text("SAINI CITY", color = TextPrimary, fontSize = 8.sp)
            }
            HudElementId.TIME_BTN -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                Text("DAY", color = TextPrimary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            HudElementId.WEATHER_BTN -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Icons.Rounded.WaterDrop, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(11.dp))
                Text("RAIN", color = TextPrimary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            HudElementId.MINIMAP -> Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Map, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(18.dp))
                    Text("MAP", color = TextPrimary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
            HudElementId.PAUSE_BTN -> Box(contentAlignment = Alignment.Center, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Rounded.Pause, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
            }
            HudElementId.JOYSTICK -> Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                Text("MOVE", color = TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            HudElementId.BTN_EMOTE -> HudCirclePreview(Icons.Rounded.EmojiEmotions, "EMOTE", 42, TacticalCyan)
            HudElementId.BTN_PUNCH -> HudCirclePreview(Icons.Rounded.SportsMma, "PUNCH", 40, TextPrimary)
            HudElementId.BTN_KICK -> HudCirclePreview(Icons.Rounded.SportsMartialArts, "KICK", 40, TextPrimary)
            HudElementId.BTN_FLIP -> HudCirclePreview(Icons.Rounded.Autorenew, "FLIP", 40, TextPrimary)
            HudElementId.BTN_DROP -> HudCirclePreview(Icons.Rounded.KeyboardDoubleArrowDown, "DROP", 40, TextPrimary)
            HudElementId.BTN_RUN -> HudCirclePreview(Icons.AutoMirrored.Rounded.DirectionsRun, "RUN", 44, TacticalCyan)
            HudElementId.BTN_JUMP -> HudCirclePreview(Icons.Rounded.ArrowUpward, "JUMP", 46, TextPrimary)
            HudElementId.BTN_USE -> HudCirclePreview(Icons.Rounded.PanTool, "USE", 50, TacticalAccent)
            HudElementId.BTN_HORN -> HudCirclePreview(Icons.Rounded.Campaign, "HORN", 42, TextPrimary)
            HudElementId.SPEEDOMETER -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(13.dp))
                Text("85 KM/H", color = TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            HudElementId.VEHICLE_STEER -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(18.dp))
                Text("STEER", color = TextPrimary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = TacticalCyan, modifier = Modifier.size(18.dp))
            }
            HudElementId.VEHICLE_PEDALS -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = null, tint = CrimsonAlert, modifier = Modifier.size(16.dp))
                    Text("BRAKE", color = CrimsonAlert, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = EmeraldPulse, modifier = Modifier.size(18.dp))
                    Text("GAS", color = EmeraldPulse, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HudCirclePreview(
    icon: ImageVector,
    label: String,
    sizeDp: Int,
    tint: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.size(sizeDp.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size((sizeDp * 0.36f).dp))
        Text(label, color = TextPrimary, fontSize = 7.sp, fontWeight = FontWeight.Bold)
    }
}
