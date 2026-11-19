package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.game.model.CharacterSkin
import com.example.game.model.ControlLayout
import com.example.game.model.GameSettings
import com.example.game.model.GameSkins
import com.example.game.model.GraphicsQuality
import com.example.game.model.PlayerProfileStats
import com.example.ui.theme.CityBorder
import com.example.ui.theme.CityCard
import com.example.ui.theme.CityObsidian
import com.example.ui.theme.CitySlate
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.SunsetGold
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
private fun ModalContainer(
    title: String,
    testTag: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(CityObsidian.copy(alpha = 0.82f))
            .clickable(onClick = onDismiss)
            .padding(18.dp)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CitySlate)
                .border(1.2.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                .clickable(enabled = false, onClick = {})
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = SunsetGold
                )
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
            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                content()
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
                .clip(RoundedCornerShape(16.dp))
                .background(CityCard)
                .border(1.dp, CityBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(activeSkin.previewAccent)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = CityObsidian,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Explorer Saini",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = stats.explorerRank,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ElectricCyan
                )
                Text(
                    text = "Active Outfit: ${activeSkin.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SunsetGold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

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
                tint = ElectricCyan,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProfileStatTile(
                icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                label = "DISTANCE",
                value = "${stats.distanceMeters} m",
                tint = EmeraldPulse,
                modifier = Modifier.weight(1f)
            )
            ProfileStatTile(
                icon = Icons.Rounded.Explore,
                label = "ZONES FOUND",
                value = "${stats.zonesDiscoveredTotal} / 5",
                tint = SunsetGold,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "EARNED BADGES",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BadgePill("City Visitor", unlocked = true)
            BadgePill("Pathfinder", unlocked = stats.distanceMeters >= 150)
            BadgePill("Zone Master", unlocked = stats.zonesDiscoveredTotal >= 5)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = SunsetGold, contentColor = CityObsidian),
            shape = RoundedCornerShape(14.dp),
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
            .clip(RoundedCornerShape(14.dp))
            .background(CityCard)
            .border(1.dp, CityBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun BadgePill(title: String, unlocked: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (unlocked) EmeraldPulse.copy(alpha = 0.16f) else CityCard)
            .border(1.dp, if (unlocked) EmeraldPulse else CityBorder, RoundedCornerShape(50))
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
    onUpdateSettings: ((GameSettings) -> GameSettings) -> Unit,
    onSetGraphics: (GraphicsQuality) -> Unit,
    onSetControlLayout: (ControlLayout) -> Unit,
    onDismiss: () -> Unit
) {
    ModalContainer(
        title = "SETTINGS",
        testTag = "modal_settings",
        onDismiss = onDismiss
    ) {
        // Music Volume
        Text(
            text = "Lobby Music Volume (${(settings.musicVolume * 100).toInt()}%)",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Slider(
            value = settings.musicVolume,
            onValueChange = { v -> onUpdateSettings { it.copy(musicVolume = v) } },
            colors = SliderDefaults.colors(thumbColor = SunsetGold, activeTrackColor = SunsetGold)
        )

        // SFX Volume
        Text(
            text = "SFX & Footsteps Volume (${(settings.sfxVolume * 100).toInt()}%)",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Slider(
            value = settings.sfxVolume,
            onValueChange = { v -> onUpdateSettings { it.copy(sfxVolume = v) } },
            colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
        )

        // Camera Sensitivity
        Text(
            text = "Camera Sensitivity (${String.format("%.1fx", settings.cameraSensitivity)})",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Slider(
            value = settings.cameraSensitivity,
            valueRange = 0.5f..2.0f,
            onValueChange = { v -> onUpdateSettings { it.copy(cameraSensitivity = v) } },
            colors = SliderDefaults.colors(thumbColor = EmeraldPulse, activeTrackColor = EmeraldPulse)
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text("Graphics Quality", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
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
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) SunsetGold else CityCard)
                        .border(1.dp, if (selected) SunsetGold else CityBorder, RoundedCornerShape(12.dp))
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

        Spacer(modifier = Modifier.height(14.dp))
        Text("On-Screen Controls", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
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
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) ElectricCyan else CityCard)
                        .border(1.dp, if (selected) ElectricCyan else CityBorder, RoundedCornerShape(12.dp))
                        .clickable { onSetControlLayout(layout) }
                        .testTag("ctrl_${layout.name.lowercase()}")
                ) {
                    Text(
                        text = layout.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) CityObsidian else TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CityCard)
                .padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Keyboard,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Key Bindings: WASD / Arrows = Move • Space = Jump • Shift = Run • E = Interact • Esc = Pause",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = SunsetGold, contentColor = CityObsidian),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("SAVE & CLOSE", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SkinsModal(
    selectedSkin: CharacterSkin,
    onSelectSkin: (CharacterSkin) -> Unit,
    onDismiss: () -> Unit
) {
    ModalContainer(
        title = "SKIN SELECTION",
        testTag = "modal_skins",
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GameSkins.all.forEach { skin ->
                val isSelected = skin.id == selectedSkin.id
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) skin.previewAccent.copy(alpha = 0.14f) else CityCard)
                        .border(
                            BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) skin.previewAccent else CityBorder
                            ),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onSelectSkin(skin) }
                        .padding(14.dp)
                        .testTag("skin_option_${skin.id}")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(skin.previewAccent)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = skin.name,
                            tint = CityObsidian,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = skin.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = skin.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = skin.perkDescription,
                            style = MaterialTheme.typography.labelSmall,
                            color = skin.previewAccent
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Equipped",
                            tint = skin.previewAccent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = SunsetGold, contentColor = CityObsidian),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("EQUIP & RETURN", style = MaterialTheme.typography.labelLarge)
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
                    .size(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(colors = listOf(SunsetGold, ElectricCyan))
                    )
            ) {
                Text(
                    text = "AS",
                    style = MaterialTheme.typography.displayLarge,
                    color = CityObsidian
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "SAINI SIMULATOR",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                text = "3D Open-World City Exploration Game",
                style = MaterialTheme.typography.bodyMedium,
                color = ElectricCyan
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CityCard)
                    .border(1.dp, SunsetGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
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
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Instagram: @ajayzsaini",
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Powered by Native Kotlin, Jetpack Compose, OpenGL ES 2.0 3D Engine & Synthesized Audio.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SunsetGold, contentColor = CityObsidian),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("CLOSE", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
