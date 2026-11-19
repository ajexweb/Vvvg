package com.example.game.model

import androidx.compose.ui.graphics.Color

enum class AppScreen {
    SPLASH,
    LOBBY,
    GAMEPLAY
}

enum class LobbyModal {
    NONE,
    PROFILE,
    SETTINGS,
    SKINS,
    CREDITS
}

enum class GraphicsQuality(val label: String, val fogEnd: Float, val propDensity: Int) {
    LOW("Low", 95f, 1),
    MEDIUM("Medium", 135f, 2),
    HIGH("High", 185f, 3)
}

enum class ControlLayout(val label: String) {
    JOYSTICK("Virtual Joystick"),
    DPAD("Touch D-Pad")
}

data class CharacterSkin(
    val id: String,
    val name: String,
    val subtitle: String,
    val perkDescription: String,
    val speedMultiplier: Float,
    val jacketColorRgb: FloatArray,
    val pantsColorRgb: FloatArray,
    val accentColorRgb: FloatArray,
    val previewAccent: Color,
    val badgeLabel: String
)

object GameSkins {
    val all: List<CharacterSkin> = listOf(
        CharacterSkin(
            id = "urban_explorer",
            name = "Urban Explorer",
            subtitle = "City Denim & Gold Trim",
            perkDescription = "Balanced handling & steady stamina recovery",
            speedMultiplier = 1.0f,
            jacketColorRgb = floatArrayOf(0.14f, 0.38f, 0.65f),
            pantsColorRgb = floatArrayOf(0.24f, 0.22f, 0.20f),
            accentColorRgb = floatArrayOf(1.0f, 0.72f, 0.01f),
            previewAccent = Color(0xFFFFB703),
            badgeLabel = "DEFAULT"
        ),
        CharacterSkin(
            id = "saini_streetwear",
            name = "Saini Streetwear",
            subtitle = "Crimson Hoodie Edition",
            perkDescription = "+12% Sprint Agility across boulevards",
            speedMultiplier = 1.12f,
            jacketColorRgb = floatArrayOf(0.82f, 0.16f, 0.22f),
            pantsColorRgb = floatArrayOf(0.12f, 0.14f, 0.18f),
            accentColorRgb = floatArrayOf(0.95f, 0.95f, 0.98f),
            previewAccent = Color(0xFFEF4444),
            badgeLabel = "STREET"
        ),
        CharacterSkin(
            id = "metro_tactical",
            name = "Metro Tactical",
            subtitle = "Stealth Cyan Operative",
            perkDescription = "+10% Speed & Cyan Reflective Armor",
            speedMultiplier = 1.10f,
            jacketColorRgb = floatArrayOf(0.11f, 0.18f, 0.26f),
            pantsColorRgb = floatArrayOf(0.16f, 0.20f, 0.24f),
            accentColorRgb = floatArrayOf(0.0f, 0.90f, 1.0f),
            previewAccent = Color(0xFF00E5FF),
            badgeLabel = "TACTICAL"
        ),
        CharacterSkin(
            id = "golden_legend",
            name = "Golden Legend",
            subtitle = "Ajay Saini Signature VIP",
            perkDescription = "+18% Exploration Speed & Gold Finish",
            speedMultiplier = 1.18f,
            jacketColorRgb = floatArrayOf(0.92f, 0.68f, 0.08f),
            pantsColorRgb = floatArrayOf(0.10f, 0.10f, 0.14f),
            accentColorRgb = floatArrayOf(0.06f, 0.85f, 0.55f),
            previewAccent = Color(0xFF10B981),
            badgeLabel = "VIP"
        )
    )

    fun byId(id: String): CharacterSkin = all.firstOrNull { it.id == id } ?: all.first()
}

data class ExplorationZone(
    val id: String,
    val name: String,
    val description: String,
    val x: Float,
    val z: Float,
    val radius: Float,
    val bonusPoints: Int
)

object CityZones {
    val all: List<ExplorationZone> = listOf(
        ExplorationZone(
            id = "saini_plaza",
            name = "Saini Central Plaza",
            description = "Iconic monument fountain in the heart of Saini City",
            x = 0f,
            z = 0f,
            radius = 9f,
            bonusPoints = 150
        ),
        ExplorationZone(
            id = "skyline_district",
            name = "Skyline Financial District",
            description = "Reflective glass towers and high-rise avenues",
            x = 42f,
            z = -42f,
            radius = 10f,
            bonusPoints = 250
        ),
        ExplorationZone(
            id = "emerald_park",
            name = "Emerald City Park",
            description = "Lush green lawns, shaded trees, and quiet walkways",
            x = -42f,
            z = 42f,
            radius = 11f,
            bonusPoints = 250
        ),
        ExplorationZone(
            id = "sunset_boulevard",
            name = "Sunset Boulevard",
            description = "Wide streetlight avenue with cruising city traffic",
            x = 42f,
            z = 42f,
            radius = 10f,
            bonusPoints = 250
        ),
        ExplorationZone(
            id = "harbor_overlook",
            name = "North Metro Overlook",
            description = "Scenic urban corner with golden hour skyline views",
            x = -42f,
            z = -42f,
            radius = 10f,
            bonusPoints = 300
        )
    )
}

data class GameSettings(
    val musicVolume: Float = 0.65f,
    val sfxVolume: Float = 0.85f,
    val graphicsQuality: GraphicsQuality = GraphicsQuality.HIGH,
    val controlLayout: ControlLayout = ControlLayout.JOYSTICK,
    val cameraSensitivity: Float = 1.0f
)

data class PlayerProfileStats(
    val totalScore: Int = 0,
    val highScore: Int = 0,
    val coins: Int = 500,
    val distanceMeters: Int = 0,
    val zonesDiscoveredTotal: Int = 0
) {
    val explorerRank: String
        get() = when {
            highScore >= 2500 -> "Grand City Legend"
            highScore >= 1200 -> "Master Explorer"
            highScore >= 500 -> "Urban Pathfinder"
            else -> "City Rookie"
        }
}

data class GameTelemetry(
    val playerX: Float = 0f,
    val playerZ: Float = 0f,
    val playerYawRad: Float = 0f,
    val isMoving: Boolean = false,
    val isSprinting: Boolean = false,
    val stamina: Float = 100f,
    val health: Float = 100f,
    val collectedOrbIds: Set<Int> = emptySet(),
    val discoveredZoneIds: Set<String> = emptySet(),
    val nearbyInteractTarget: String? = null,
    val distanceThisSession: Float = 0f,
    val sessionScore: Int = 0
)
