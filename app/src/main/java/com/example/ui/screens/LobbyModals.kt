package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.engine.LobbyCameraPreset
import com.example.game.engine.LobbyPoseMode
import com.example.game.model.CharacterSkin
import com.example.game.model.CityZones
import com.example.game.model.ControlLayout
import com.example.game.model.DailyMission
import com.example.game.model.GameMissions
import com.example.game.model.GameSettings
import com.example.game.model.GameSkins
import com.example.game.model.GameStoreCatalog
import com.example.game.model.GraphicsQuality
import com.example.game.model.PlayerProfileStats
import com.example.game.model.StoreCategory
import com.example.game.model.StoreItem
import com.example.game.viewmodel.GameUiState
import com.example.ui.theme.CityBorder
import com.example.ui.theme.CityCard
import com.example.ui.theme.CityObsidian
import com.example.ui.theme.CitySlate
import com.example.ui.theme.DiamondBlue
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.SunsetAmber
import com.example.ui.theme.SunsetGold
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.abs

/**
 * Full-featured 3D Character Inspector Overlay (Free Fire / BGMI Vault & Character screen).
 * Left side remains open for live 360-deg drag rotation & pinch zoom on the 3D Avaturn model!
 * Right side provides Face Zoom / Upper / Full Body presets, Zoom Slider, Pose Preview, Stats & Outfits.
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("modal_characters")
    ) {
        // Top-Left Back Button + Interactive 3D Gesture Hint
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CitySlate.copy(alpha = 0.90f))
                    .border(1.dp, CityBorder, RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 14.dp)
                    .testTag("btn_back_from_characters")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to Lobby",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CHARACTERS",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CityObsidian.copy(alpha = 0.72f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "DRAG TO ROTATE 360° • PINCH TO ZOOM",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }

        // Bottom-Left: Camera Presets (Full Body / Upper / Face Zoom) + Pose Switcher over the 3D Stage
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .widthIn(max = 360.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CityObsidian.copy(alpha = 0.84f))
                .border(1.dp, CityBorder, RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            // Camera Framing Presets
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LobbyCameraPreset.entries.forEach { preset ->
                    val selected = abs(zoomNormalized - preset.zoomNormalized) < 0.16f
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) SunsetGold else CityCard)
                            .clickable { onSelectPreset(preset) }
                            .padding(horizontal = 10.dp)
                            .testTag("preset_${preset.name.lowercase()}")
                    ) {
                        if (preset == LobbyCameraPreset.FACE_ZOOM) {
                            Icon(
                                imageVector = Icons.Rounded.Face,
                                contentDescription = null,
                                tint = if (selected) CityObsidian else SunsetGold,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) CityObsidian else TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CityCard)
                        .clickable(onClick = onResetCamera)
                        .testTag("btn_char_reset_cam")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reset View", tint = TextSecondary, modifier = Modifier.size(17.dp))
                }
            }

            // Smooth Zoom Slider (0% Full Body -> 100% Face Close-Up)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "ZOOM ${(zoomNormalized * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = SunsetGold,
                    modifier = Modifier.width(72.dp)
                )
                Slider(
                    value = zoomNormalized,
                    onValueChange = onZoomChange,
                    colors = SliderDefaults.colors(
                        thumbColor = SunsetGold,
                        activeTrackColor = SunsetGold,
                        inactiveTrackColor = CityBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .testTag("slider_character_zoom")
                )
            }

            // Live Pose Switcher
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("POSE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(modifier = Modifier.width(4.dp))
                LobbyPoseMode.entries.forEach { mode ->
                    val active = poseMode == mode
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) TextPrimary else CityCard)
                            .clickable { onSelectPose(mode) }
                            .padding(horizontal = 12.dp)
                            .testTag("pose_${mode.name.lowercase()}")
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) CityObsidian else TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Right Side Tactical Drawer: Character Stats & Outfits
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .widthIn(min = 290.dp, max = 340.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CitySlate.copy(alpha = 0.94f))
                .border(1.dp, CityBorder, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = uiState.selectedSkin.name.uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = SunsetGold
                    )
                    Text(
                        text = uiState.selectedSkin.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Character Stat Bars
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CityCard)
                    .padding(10.dp)
            ) {
                CharacterStatRow("AGILITY", uiState.selectedSkin.agilityRating)
                CharacterStatRow("STAMINA", uiState.selectedSkin.staminaRating)
                CharacterStatRow("DRIVING", uiState.selectedSkin.drivingRating)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "OUTFIT PRESETS & PERKS",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                GameSkins.all.forEach { skin ->
                    val storeItem = GameStoreCatalog.items.firstOrNull { it.linkedSkinId == skin.id }
                    val isOwned = storeItem == null || storeItem.id in uiState.ownedItemIds
                    val isSelected = skin.id == uiState.selectedSkin.id

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) SunsetGold.copy(alpha = 0.14f) else CityCard)
                            .border(
                                BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) SunsetGold else CityBorder
                                ),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                if (isOwned) onSelectSkin(skin) else onOpenStore()
                            }
                            .padding(10.dp)
                            .testTag("skin_option_${skin.id}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(skin.previewAccent.copy(alpha = 0.22f))
                                .border(1.dp, skin.previewAccent, RoundedCornerShape(6.dp))
                        ) {
                            Icon(
                                imageVector = if (isOwned) Icons.Rounded.Person else Icons.Rounded.Lock,
                                contentDescription = skin.name,
                                tint = skin.previewAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = skin.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = skin.perkDescription,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Equipped",
                                tint = SunsetGold,
                                modifier = Modifier.size(20.dp)
                            )
                        } else if (!isOwned) {
                            Text(
                                text = "STORE",
                                style = MaterialTheme.typography.labelSmall,
                                color = SunsetGold,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SunsetGold, contentColor = CityObsidian),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_equip_return")
            ) {
                Text("EQUIP & BACK TO LOBBY", style = MaterialTheme.typography.labelLarge)
            }
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
            modifier = Modifier.width(64.dp),
            fontSize = 10.sp
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(CityObsidian)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0.1f, 1f))
                    .background(SunsetGold)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${(fraction * 100).toInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ModalContainer(
    title: String,
    testTag: String,
    maxWidthDp: Int = 560,
    headerExtra: (@Composable () -> Unit)? = null,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(CityObsidian.copy(alpha = 0.84f))
            .clickable(onClick = onDismiss)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = maxWidthDp.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CitySlate)
                .border(1.dp, CityBorder, RoundedCornerShape(14.dp))
                .clickable(enabled = false, onClick = {})
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = SunsetGold
                    )
                    headerExtra?.invoke()
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_close_modal")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = TextPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                content()
            }
        }
    }
}

/**
 * Official In-Game Store Modal (Outfits, Vehicle Finishes, Emotes, Tactical Gear).
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
        title = "GAME STORE",
        testTag = "modal_store",
        maxWidthDp = 680,
        headerExtra = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CityCard)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.MonetizationOn, contentDescription = null, tint = SunsetGold, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${uiState.profileStats.coins}", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CityCard)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.Diamond, contentDescription = null, tint = DiamondBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${uiState.profileStats.diamonds}", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                }
            }
        },
        onDismiss = onDismiss
    ) {
        // Category Filter Tabs
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            StoreCategory.entries.forEach { cat ->
                val active = selectedCategory == cat
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) SunsetGold else CityCard)
                        .border(1.dp, if (active) SunsetGold else CityBorder, RoundedCornerShape(6.dp))
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 14.dp)
                        .testTag("store_tab_${cat.name.lowercase()}")
                ) {
                    Text(
                        text = cat.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) CityObsidian else TextPrimary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2-Column Responsive Store Grid
        val rows = filteredItems.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { pair ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    pair.forEach { item ->
                        val owned = item.id in uiState.ownedItemIds
                        val equipped = if (item.linkedSkinId != null) {
                            uiState.selectedSkin.id == item.linkedSkinId
                        } else {
                            item.id in uiState.equippedGearIds
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
            .clip(RoundedCornerShape(10.dp))
            .background(CityCard)
            .border(1.dp, if (equipped) SunsetGold else CityBorder, RoundedCornerShape(10.dp))
            .padding(12.dp)
            .testTag("store_item_${item.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(item.rarity.color.copy(alpha = 0.2f))
                    .border(1.dp, item.rarity.color, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = item.rarity.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = item.rarity.color,
                    fontSize = 9.sp
                )
            }
            Text(
                text = item.category.label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = item.description,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            fontSize = 12.sp,
            minLines = 2
        )

        Spacer(modifier = Modifier.height(10.dp))

        val buttonBg = when {
            equipped -> EmeraldPulse.copy(alpha = 0.2f)
            owned -> CitySlate
            else -> SunsetGold
        }
        val buttonTextColor = when {
            equipped -> EmeraldPulse
            owned -> TextPrimary
            else -> CityObsidian
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(buttonBg)
                .border(1.dp, if (equipped) EmeraldPulse else if (owned) CityBorder else SunsetGold, RoundedCornerShape(6.dp))
                .clickable(onClick = onAction)
                .testTag("btn_store_action_${item.id}")
        ) {
            if (equipped) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = EmeraldPulse, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("EQUIPPED", style = MaterialTheme.typography.labelLarge, color = buttonTextColor, fontSize = 13.sp)
            } else if (owned) {
                Text("EQUIP", style = MaterialTheme.typography.labelLarge, color = buttonTextColor, fontSize = 13.sp)
            } else if (item.priceDiamonds > 0) {
                Icon(Icons.Rounded.Diamond, contentDescription = null, tint = CityObsidian, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("BUY ${item.priceDiamonds}", style = MaterialTheme.typography.labelLarge, color = buttonTextColor, fontSize = 13.sp)
            } else {
                Icon(Icons.Rounded.MonetizationOn, contentDescription = null, tint = CityObsidian, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("BUY ${item.priceCoins}", style = MaterialTheme.typography.labelLarge, color = buttonTextColor, fontSize = 13.sp)
            }
        }
    }
}

/**
 * Official Daily & Exploration Missions Modal with working Reward Claims.
 */
@Composable
fun MissionsModal(
    uiState: GameUiState,
    progressProvider: (DailyMission) -> Int,
    onClaimMission: (DailyMission) -> Unit,
    onDismiss: () -> Unit
) {
    ModalContainer(
        title = "MISSIONS & REWARDS",
        testTag = "modal_missions",
        maxWidthDp = 620,
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GameMissions.all.forEach { mission ->
                val current = progressProvider(mission).coerceAtMost(mission.targetValue)
                val completed = current >= mission.targetValue
                val claimed = mission.id in uiState.claimedMissionIds
                val fraction = (current.toFloat() / mission.targetValue.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CityCard)
                        .border(1.dp, if (completed && !claimed) SunsetGold else CityBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                        .testTag("mission_${mission.id}")
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mission.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = mission.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CityObsidian)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction)
                                        .background(if (completed) EmeraldPulse else SunsetGold)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$current/${mission.targetValue}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "+${mission.rewardCoins} COINS",
                                style = MaterialTheme.typography.labelSmall,
                                color = SunsetGold
                            )
                            Text(
                                text = "+${mission.rewardDiamonds} DIAMONDS",
                                style = MaterialTheme.typography.labelSmall,
                                color = DiamondBlue
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(96.dp)
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    claimed -> CitySlate
                                    completed -> SunsetGold
                                    else -> CityObsidian.copy(alpha = 0.6f)
                                }
                            )
                            .border(
                                1.dp,
                                when {
                                    claimed -> EmeraldPulse
                                    completed -> SunsetGold
                                    else -> CityBorder
                                },
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = !claimed) { onClaimMission(mission) }
                            .testTag("btn_claim_${mission.id}")
                    ) {
                        Text(
                            text = when {
                                claimed -> "CLAIMED"
                                completed -> "CLAIM"
                                else -> "IN PROG"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = when {
                                claimed -> EmeraldPulse
                                completed -> CityObsidian
                                else -> TextSecondary
                            },
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileModal(
    stats: PlayerProfileStats,
    activeSkin: CharacterSkin,
    onDismiss: () -> Unit
) {
    ModalContainer(
        title = "PLAYER PROFILE",
        testTag = "modal_profile",
        onDismiss = onDismiss
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CityCard)
                .border(1.dp, CityBorder, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_player_face),
                contentDescription = "Player Profile Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.5.dp, SunsetGold, RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "AJAY SAINI  •  LV.${stats.level}",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = stats.explorerRank,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SunsetGold,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Active Outfit: ${activeSkin.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProfileStatTile(
                icon = Icons.Rounded.Star,
                label = "TOTAL SCORE",
                value = "${stats.totalScore}",
                tint = SunsetGold,
                modifier = Modifier.weight(1f)
            )
            ProfileStatTile(
                icon = Icons.Rounded.EmojiEvents,
                label = "HIGH SCORE",
                value = "${stats.highScore}",
                tint = TextPrimary,
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
                tint = SunsetGold,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "EARNED BADGES",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BadgePill("City Visitor", unlocked = true)
            BadgePill("Pathfinder", unlocked = stats.distanceMeters >= 150)
            BadgePill("Zone Master", unlocked = stats.zonesDiscoveredTotal >= CityZones.all.size)
        }

        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = SunsetGold, contentColor = CityObsidian),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("CLOSE PROFILE", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ProfileStatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CityCard)
            .border(1.dp, CityBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontSize = 18.sp)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 9.sp)
    }
}

@Composable
private fun BadgePill(title: String, unlocked: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (unlocked) EmeraldPulse.copy(alpha = 0.16f) else CityCard)
            .border(1.dp, if (unlocked) EmeraldPulse else CityBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Verified,
            contentDescription = null,
            tint = if (unlocked) EmeraldPulse else TextSecondary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = if (unlocked) TextPrimary else TextSecondary
        )
    }
}

@Composable
fun SettingsModal(
    settings: GameSettings,
    onToggleMute: () -> Unit,
    onUpdateSettings: ((GameSettings) -> GameSettings) -> Unit,
    onSetGraphics: (GraphicsQuality) -> Unit,
    onSetControlLayout: (ControlLayout) -> Unit,
    onSetTargetFps: (Int) -> Unit,
    onSetStreamingRadius: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalContainer(
        title = "SETTINGS",
        testTag = "modal_settings",
        onDismiss = onDismiss
    ) {
        // Master Sound / Mute Toggle Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CityCard)
                .border(1.dp, if (settings.isMuted) CityBorder else SunsetGold.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                .clickable(onClick = onToggleMute)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("settings_mute_toggle"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (settings.isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                    contentDescription = "Master Sound",
                    tint = if (settings.isMuted) TextSecondary else SunsetGold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (settings.isMuted) "Master Sound: MUTED" else "Master Sound: ON",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Toggle all SFX, engine audio & city ambience",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
            Switch(
                checked = !settings.isMuted,
                onCheckedChange = { onToggleMute() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CityObsidian,
                    checkedTrackColor = SunsetGold,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = CityObsidian
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Lobby & Ambience Volume (${(settings.musicVolume * 100).toInt()}%)",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Slider(
            value = settings.musicVolume,
            onValueChange = { v -> onUpdateSettings { it.copy(musicVolume = v) } },
            colors = SliderDefaults.colors(thumbColor = SunsetGold, activeTrackColor = SunsetGold, inactiveTrackColor = CityBorder)
        )

        Text(
            text = "SFX & Engine Audio (${(settings.sfxVolume * 100).toInt()}%)",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Slider(
            value = settings.sfxVolume,
            onValueChange = { v -> onUpdateSettings { it.copy(sfxVolume = v) } },
            colors = SliderDefaults.colors(thumbColor = SunsetGold, activeTrackColor = SunsetGold, inactiveTrackColor = CityBorder)
        )

        Text(
            text = "Camera Sensitivity (${String.format("%.1fx", settings.cameraSensitivity)})",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Slider(
            value = settings.cameraSensitivity,
            valueRange = 0.5f..2.0f,
            onValueChange = { v -> onUpdateSettings { it.copy(cameraSensitivity = v) } },
            colors = SliderDefaults.colors(thumbColor = SunsetGold, activeTrackColor = SunsetGold, inactiveTrackColor = CityBorder)
        )

        Spacer(modifier = Modifier.height(6.dp))
        Text("Frame Rate", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(60, 90, 120).forEach { fps ->
                val selected = settings.targetFps == fps
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) SunsetGold else CityCard)
                        .border(1.dp, if (selected) SunsetGold else CityBorder, RoundedCornerShape(8.dp))
                        .clickable { onSetTargetFps(fps) }
                        .testTag("fps_$fps")
                ) {
                    Text(
                        text = "$fps FPS",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) CityObsidian else TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Zone Asset Load Radius (${settings.streamingRadiusMeters.toInt()}m)",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(40f to "40m", 56f to "56m", 76f to "76m").forEach { (rad, label) ->
                val selected = abs(settings.streamingRadiusMeters - rad) < 3f
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) SunsetGold else CityCard)
                        .border(1.dp, if (selected) SunsetGold else CityBorder, RoundedCornerShape(8.dp))
                        .clickable { onSetStreamingRadius(rad) }
                        .testTag("zone_${rad.toInt()}")
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) CityObsidian else TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text("Graphics Quality", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GraphicsQuality.entries.forEach { quality ->
                val selected = settings.graphicsQuality == quality
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) SunsetGold else CityCard)
                        .border(1.dp, if (selected) SunsetGold else CityBorder, RoundedCornerShape(8.dp))
                        .clickable { onSetGraphics(quality) }
                        .testTag("gfx_${quality.name.lowercase()}")
                ) {
                    Text(
                        text = quality.label.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) CityObsidian else TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text("Control Style", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ControlLayout.entries.forEach { layout ->
                val selected = settings.controlLayout == layout
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) SunsetGold else CityCard)
                        .border(1.dp, if (selected) SunsetGold else CityBorder, RoundedCornerShape(8.dp))
                        .clickable { onSetControlLayout(layout) }
                        .testTag("ctrl_${layout.name.lowercase()}")
                ) {
                    Text(
                        text = layout.label.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) CityObsidian else TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(CityCard)
                .padding(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Keyboard,
                contentDescription = null,
                tint = SunsetGold,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Keyboard: WASD = Move • Space = Jump • Shift = Run • E = Interact / Drive • H = Horn",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = SunsetGold, contentColor = CityObsidian),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("SAVE & CLOSE", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun CreditsModal(onDismiss: () -> Unit) {
    ModalContainer(
        title = "CREDITS",
        testTag = "modal_credits",
        onDismiss = onDismiss
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(colors = listOf(SunsetGold, SunsetAmber)))
            ) {
                Text(
                    text = "AS",
                    style = MaterialTheme.typography.displayLarge,
                    color = CityObsidian
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "SAINI SIMULATOR",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                text = "3D Open-World City Exploration Game",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CityCard)
                    .border(1.dp, SunsetGold.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "OFFICIAL DEVELOPER & CREATOR",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ajay Saini",
                    style = MaterialTheme.typography.headlineMedium,
                    color = SunsetGold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Instagram: @ajayzsaini",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Native Android OpenGL ES 3D Engine with Rigged Character Skinning, Vehicles, Enterable Buildings & 360° Zone Streaming.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SunsetGold, contentColor = CityObsidian),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("CLOSE", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
