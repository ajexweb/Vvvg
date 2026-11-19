package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
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
import com.example.game.model.CharacterSkin
import com.example.game.model.LobbyModal
import com.example.game.model.PlayerProfileStats
import com.example.game.viewmodel.GameUiState
import com.example.ui.theme.CityBorder
import com.example.ui.theme.CityCard
import com.example.ui.theme.CityObsidian
import com.example.ui.theme.CitySlate
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.SunsetAmber
import com.example.ui.theme.SunsetGold
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LobbyScreen(
    uiState: GameUiState,
    onStartGame: () -> Unit,
    onOpenModal: (LobbyModal) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CityObsidian)
            .testTag("lobby_screen")
    ) {
        // Real-world city skyline backdrop
        Image(
            painter = painterResource(id = R.drawable.img_city_hero),
            contentDescription = "Saini City Lobby Backdrop",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            CityObsidian.copy(alpha = 0.88f),
                            CityObsidian.copy(alpha = 0.55f),
                            CityObsidian.copy(alpha = 0.82f)
                        )
                    )
                )
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            val isWide = maxWidth >= 680.dp

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header Bar
                LobbyTopBar(
                    stats = uiState.profileStats,
                    onProfileClick = { onOpenModal(LobbyModal.PROFILE) },
                    onSettingsClick = { onOpenModal(LobbyModal.SETTINGS) }
                )

                // Middle Content (Adaptive side-by-side on landscape/tablet, stacked on portrait)
                if (isWide) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActiveCharacterShowcaseCard(
                            skin = uiState.selectedSkin,
                            stats = uiState.profileStats,
                            modifier = Modifier
                                .weight(1f)
                                .widthIn(max = 380.dp),
                            onCustomizeClick = { onOpenModal(LobbyModal.SKINS) }
                        )
                        Spacer(modifier = Modifier.width(24.dp))
                        LobbyActionStack(
                            onStartGame = onStartGame,
                            onSkinClick = { onOpenModal(LobbyModal.SKINS) },
                            onSettingsClick = { onOpenModal(LobbyModal.SETTINGS) },
                            onCreditsClick = { onOpenModal(LobbyModal.CREDITS) },
                            modifier = Modifier
                                .weight(1f)
                                .widthIn(max = 360.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        ActiveCharacterShowcaseCard(
                            skin = uiState.selectedSkin,
                            stats = uiState.profileStats,
                            modifier = Modifier.widthIn(max = 460.dp),
                            onCustomizeClick = { onOpenModal(LobbyModal.SKINS) }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LobbyActionStack(
                            onStartGame = onStartGame,
                            onSkinClick = { onOpenModal(LobbyModal.SKINS) },
                            onSettingsClick = { onOpenModal(LobbyModal.SETTINGS) },
                            onCreditsClick = { onOpenModal(LobbyModal.CREDITS) },
                            modifier = Modifier.widthIn(max = 460.dp)
                        )
                    }
                }

                // Bottom Footer with prominent Ajay Saini (@ajayzsaini) Developer Handle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CitySlate.copy(alpha = 0.85f))
                            .border(1.dp, CityBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Explore,
                            contentDescription = null,
                            tint = EmeraldPulse,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${uiState.profileStats.zonesDiscoveredTotal}/5 Zones Explored",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(CityObsidian.copy(alpha = 0.85f))
                            .border(1.dp, SunsetGold.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                            .clickable { onOpenModal(LobbyModal.CREDITS) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("lobby_developer_badge")
                    ) {
                        Text(
                            text = "Developed by Ajay Saini",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Verified,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "@ajayzsaini",
                                style = MaterialTheme.typography.labelLarge,
                                color = SunsetGold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LobbyTopBar(
    stats: PlayerProfileStats,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "SAINI SIMULATOR",
                style = MaterialTheme.typography.headlineMedium,
                color = SunsetGold,
                modifier = Modifier.testTag("lobby_title")
            )
            Text(
                text = "Rank: ${stats.explorerRank}",
                style = MaterialTheme.typography.bodyMedium,
                color = ElectricCyan
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(CityCard.copy(alpha = 0.9f))
                    .border(1.dp, SunsetGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MonetizationOn,
                    contentDescription = "Coins",
                    tint = SunsetGold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stats.coins.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
            }

            LobbyIconButton(
                icon = Icons.Rounded.Person,
                contentDescription = "Player Profile",
                testTag = "btn_profile",
                onClick = onProfileClick
            )
            LobbyIconButton(
                icon = Icons.Rounded.Settings,
                contentDescription = "Settings",
                testTag = "btn_settings_top",
                onClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun LobbyIconButton(
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .background(CityCard.copy(alpha = 0.9f))
            .border(1.dp, CityBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = ElectricCyan,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ActiveCharacterShowcaseCard(
    skin: CharacterSkin,
    stats: PlayerProfileStats,
    modifier: Modifier = Modifier,
    onCustomizeClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CitySlate.copy(alpha = 0.88f))
            .border(1.dp, skin.previewAccent.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
            .clickable(onClick = onCustomizeClick)
            .padding(18.dp)
            .testTag("lobby_character_card")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Stylized skin preview badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(skin.previewAccent, CityCard)
                            )
                        )
                        .border(2.dp, skin.previewAccent, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = skin.name,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
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
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(skin.previewAccent.copy(alpha = 0.2f))
                    .border(1.dp, skin.previewAccent, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = skin.badgeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = skin.previewAccent
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "PERK: ${skin.perkDescription}",
            style = MaterialTheme.typography.bodyMedium,
            color = ElectricCyan,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatMiniChip(label = "BEST SCORE", value = "${stats.highScore}")
            StatMiniChip(label = "DISTANCE", value = "${stats.distanceMeters}m")
            StatMiniChip(label = "TAP TO CHANGE", value = "SKINS >")
        }
    }
}

@Composable
private fun StatMiniChip(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CityObsidian.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontSize = 10.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun LobbyActionStack(
    onStartGame: () -> Unit,
    onSkinClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCreditsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onStartGame,
            colors = ButtonDefaults.buttonColors(
                containerColor = SunsetGold,
                contentColor = CityObsidian
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .testTag("btn_start_game")
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "START EXPLORATION",
                style = MaterialTheme.typography.titleLarge,
                color = CityObsidian
            )
        }

        LobbySecondaryButton(
            icon = Icons.Rounded.Checkroom,
            label = "SKIN SELECTION",
            accent = ElectricCyan,
            testTag = "btn_skin_selection",
            onClick = onSkinClick
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LobbySecondaryButton(
                icon = Icons.Rounded.Settings,
                label = "SETTINGS",
                accent = SunsetAmber,
                testTag = "btn_settings",
                onClick = onSettingsClick,
                modifier = Modifier.weight(1f)
            )
            LobbySecondaryButton(
                icon = Icons.Rounded.Info,
                label = "CREDITS",
                accent = EmeraldPulse,
                testTag = "btn_credits",
                onClick = onCreditsClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LobbySecondaryButton(
    icon: ImageVector,
    label: String,
    accent: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, accent.copy(alpha = 0.65f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CitySlate.copy(alpha = 0.88f),
            contentColor = TextPrimary
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            maxLines = 1
        )
    }
}
