package com.example.game.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.RarityCommon
import com.example.ui.theme.RarityEpic
import com.example.ui.theme.RarityLegendary
import com.example.ui.theme.RarityRare

enum class AppScreen {
    SPLASH,
    LOBBY,
    GAMEPLAY
}

enum class LobbyModal {
    NONE,
    CHARACTERS,
    STORE,
    MISSIONS,
    PROFILE,
    SETTINGS,
    CREDITS
}

enum class GraphicsQuality(val label: String, val fogEnd: Float, val propDensity: Int) {
    LOW("Low", 95f, 1),
    MEDIUM("Medium", 135f, 2),
    HIGH("High", 185f, 3)
}

enum class ControlLayout(val label: String) {
    JOYSTICK("Joystick"),
    DPAD("D-Pad")
}

data class CharacterSkin(
    val id: String,
    val name: String,
    val subtitle: String,
    val perkDescription: String,
    val speedMultiplier: Float,
    val skinToneRgb: FloatArray,
    val hairColorRgb: FloatArray,
    val shirtColorRgb: FloatArray,
    val jacketColorRgb: FloatArray,
    val pantsColorRgb: FloatArray,
    val shoeColorRgb: FloatArray,
    val accentColorRgb: FloatArray,
    val previewAccent: Color,
    val badgeLabel: String,
    val agilityRating: Float = 0.78f,
    val staminaRating: Float = 0.82f,
    val drivingRating: Float = 0.80f
)

object GameSkins {
    val all: List<CharacterSkin> = listOf(
        CharacterSkin(
            id = "urban_explorer",
            name = "Ajay Street Hoodie",
            subtitle = "Black Tactical Hoodie, Slim Denim & White Sneakers",
            perkDescription = "Balanced handling & steady stamina recovery",
            speedMultiplier = 1.0f,
            skinToneRgb = floatArrayOf(0.84f, 0.65f, 0.50f),
            hairColorRgb = floatArrayOf(0.11f, 0.09f, 0.08f),
            shirtColorRgb = floatArrayOf(0.95f, 0.95f, 0.96f),
            jacketColorRgb = floatArrayOf(0.14f, 0.16f, 0.20f),
            pantsColorRgb = floatArrayOf(0.14f, 0.16f, 0.20f),
            shoeColorRgb = floatArrayOf(0.94f, 0.94f, 0.96f),
            accentColorRgb = floatArrayOf(1.0f, 0.72f, 0.05f),
            previewAccent = Color(0xFFFFB800),
            badgeLabel = "DEFAULT",
            agilityRating = 0.78f,
            staminaRating = 0.82f,
            drivingRating = 0.80f
        ),
        CharacterSkin(
            id = "saini_streetwear",
            name = "Crimson Striker",
            subtitle = "Red Tactical Streetwear & Black Joggers",
            perkDescription = "+12% Sprint Agility across city streets",
            speedMultiplier = 1.12f,
            skinToneRgb = floatArrayOf(0.82f, 0.62f, 0.47f),
            hairColorRgb = floatArrayOf(0.08f, 0.07f, 0.07f),
            shirtColorRgb = floatArrayOf(0.16f, 0.16f, 0.18f),
            jacketColorRgb = floatArrayOf(0.76f, 0.13f, 0.18f),
            pantsColorRgb = floatArrayOf(0.13f, 0.14f, 0.17f),
            shoeColorRgb = floatArrayOf(0.90f, 0.18f, 0.22f),
            accentColorRgb = floatArrayOf(0.96f, 0.96f, 0.98f),
            previewAccent = Color(0xFFEF4444),
            badgeLabel = "RARE",
            agilityRating = 0.90f,
            staminaRating = 0.86f,
            drivingRating = 0.82f
        ),
        CharacterSkin(
            id = "metro_tactical",
            name = "Bullet Rider",
            subtitle = "Heavy Biker Leather Jacket & Riding Boots",
            perkDescription = "+10% Speed & Enhanced Vehicle Handling",
            speedMultiplier = 1.10f,
            skinToneRgb = floatArrayOf(0.86f, 0.67f, 0.53f),
            hairColorRgb = floatArrayOf(0.14f, 0.10f, 0.08f),
            shirtColorRgb = floatArrayOf(0.76f, 0.80f, 0.86f),
            jacketColorRgb = floatArrayOf(0.12f, 0.13f, 0.16f),
            pantsColorRgb = floatArrayOf(0.15f, 0.18f, 0.23f),
            shoeColorRgb = floatArrayOf(0.18f, 0.18f, 0.20f),
            accentColorRgb = floatArrayOf(0.88f, 0.90f, 0.94f),
            previewAccent = Color(0xFF64748B),
            badgeLabel = "EPIC",
            agilityRating = 0.86f,
            staminaRating = 0.88f,
            drivingRating = 0.96f
        ),
        CharacterSkin(
            id = "golden_legend",
            name = "Saini VIP Commander",
            subtitle = "Tailored Charcoal Suit & 24K Gold Trim",
            perkDescription = "+18% Exploration Speed & VIP Commander Aura",
            speedMultiplier = 1.18f,
            skinToneRgb = floatArrayOf(0.84f, 0.64f, 0.49f),
            hairColorRgb = floatArrayOf(0.09f, 0.08f, 0.08f),
            shirtColorRgb = floatArrayOf(0.98f, 0.96f, 0.92f),
            jacketColorRgb = floatArrayOf(0.16f, 0.17f, 0.22f),
            pantsColorRgb = floatArrayOf(0.13f, 0.14f, 0.18f),
            shoeColorRgb = floatArrayOf(0.26f, 0.16f, 0.10f),
            accentColorRgb = floatArrayOf(0.95f, 0.72f, 0.12f),
            previewAccent = Color(0xFF22C55E),
            badgeLabel = "LEGENDARY",
            agilityRating = 0.96f,
            staminaRating = 0.95f,
            drivingRating = 0.94f
        )
    )

    fun byId(id: String): CharacterSkin = all.firstOrNull { it.id == id } ?: all.first()
}

enum class StoreCategory(val label: String) {
    ALL("ALL"),
    OUTFITS("OUTFITS"),
    VEHICLES("VEHICLES"),
    EMOTES("EMOTES"),
    GEAR("GEAR")
}

enum class ItemRarity(val label: String, val color: Color) {
    COMMON("COMMON", RarityCommon),
    RARE("RARE", RarityRare),
    EPIC("EPIC", RarityEpic),
    LEGENDARY("LEGENDARY", RarityLegendary)
}

data class StoreItem(
    val id: String,
    val name: String,
    val category: StoreCategory,
    val description: String,
    val priceCoins: Int = 0,
    val priceDiamonds: Int = 0,
    val rarity: ItemRarity,
    val linkedSkinId: String? = null
)

object GameStoreCatalog {
    val defaultOwnedIds: Set<String> = setOf("outfit_urban_explorer", "emote_idle_wave", "vehicle_classic_bullet")

    val items: List<StoreItem> = listOf(
        StoreItem(
            id = "outfit_urban_explorer",
            name = "Ajay Street Hoodie",
            category = StoreCategory.OUTFITS,
            description = "Signature dark street hoodie with white low-top sneakers.",
            priceCoins = 0,
            rarity = ItemRarity.COMMON,
            linkedSkinId = "urban_explorer"
        ),
        StoreItem(
            id = "outfit_saini_streetwear",
            name = "Crimson Striker Bundle",
            category = StoreCategory.OUTFITS,
            description = "+12% sprint agility outfit for rapid urban runs.",
            priceCoins = 600,
            rarity = ItemRarity.RARE,
            linkedSkinId = "saini_streetwear"
        ),
        StoreItem(
            id = "outfit_metro_tactical",
            name = "Bullet Rider Pack",
            category = StoreCategory.OUTFITS,
            description = "Heavy biker leather gear with +10% speed & bike control.",
            priceCoins = 950,
            rarity = ItemRarity.EPIC,
            linkedSkinId = "metro_tactical"
        ),
        StoreItem(
            id = "outfit_golden_legend",
            name = "Saini VIP Commander",
            category = StoreCategory.OUTFITS,
            description = "Elite VIP suit with +18% exploration speed bonus.",
            priceDiamonds = 100,
            rarity = ItemRarity.LEGENDARY,
            linkedSkinId = "golden_legend"
        ),
        StoreItem(
            id = "vehicle_classic_bullet",
            name = "Royal Bullet 350 Stock",
            category = StoreCategory.VEHICLES,
            description = "Factory chrome & jet-black cruiser finish.",
            priceCoins = 0,
            rarity = ItemRarity.COMMON
        ),
        StoreItem(
            id = "vehicle_matte_stealth",
            name = "Bullet Matte Stealth Coat",
            category = StoreCategory.VEHICLES,
            description = "Tactical desert-black custom tank decal & twin exhaust.",
            priceCoins = 500,
            rarity = ItemRarity.RARE
        ),
        StoreItem(
            id = "vehicle_gold_sedan",
            name = "Executive Gold Sedan Wrap",
            category = StoreCategory.VEHICLES,
            description = "Metallic brushed-gold finish for Saini City sedans.",
            priceDiamonds = 60,
            rarity = ItemRarity.EPIC
        ),
        StoreItem(
            id = "emote_idle_wave",
            name = "Street Ready Stance",
            category = StoreCategory.EMOTES,
            description = "Signature relaxed gesture pose in the Lobby.",
            priceCoins = 0,
            rarity = ItemRarity.COMMON
        ),
        StoreItem(
            id = "emote_tactical_sprint",
            name = "Tactical Dash Showcase",
            category = StoreCategory.EMOTES,
            description = "Unlocks combat-ready athletic preview stance.",
            priceCoins = 350,
            rarity = ItemRarity.RARE
        ),
        StoreItem(
            id = "gear_stamina_pack",
            name = "Tactical Utility Pack",
            category = StoreCategory.GEAR,
            description = "Reinforced urban backpack for extended city exploration.",
            priceCoins = 450,
            rarity = ItemRarity.EPIC
        ),
        StoreItem(
            id = "gear_aviator_shades",
            name = "Commander Aviators",
            category = StoreCategory.GEAR,
            description = "Polarized gold-frame sunglasses for bright daytime runs.",
            priceDiamonds = 40,
            rarity = ItemRarity.LEGENDARY
        )
    )
}

enum class MissionMetric {
    ZONES,
    DISTANCE,
    SCORE
}

data class DailyMission(
    val id: String,
    val title: String,
    val subtitle: String,
    val targetValue: Int,
    val rewardCoins: Int,
    val rewardDiamonds: Int,
    val metric: MissionMetric
)

object GameMissions {
    val all: List<DailyMission> = listOf(
        DailyMission(
            id = "mission_first_steps",
            title = "City Patrol",
            subtitle = "Travel 120 meters across Saini City",
            targetValue = 120,
            rewardCoins = 250,
            rewardDiamonds = 15,
            metric = MissionMetric.DISTANCE
        ),
        DailyMission(
            id = "mission_zone_scout",
            title = "District Scout",
            subtitle = "Discover 2 landmarks in Saini City",
            targetValue = 2,
            rewardCoins = 400,
            rewardDiamonds = 25,
            metric = MissionMetric.ZONES
        ),
        DailyMission(
            id = "mission_coin_hunter",
            title = "Score Hunter",
            subtitle = "Reach 500 total exploration points",
            targetValue = 500,
            rewardCoins = 500,
            rewardDiamonds = 30,
            metric = MissionMetric.SCORE
        ),
        DailyMission(
            id = "mission_master_explorer",
            title = "Saini Grand Explorer",
            subtitle = "Discover 5 or more city zones",
            targetValue = 5,
            rewardCoins = 1000,
            rewardDiamonds = 75,
            metric = MissionMetric.ZONES
        )
    )
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
            name = "Saini Central Chauraha",
            description = "Main downtown crossroads & central hub of Saini City",
            x = 0f,
            z = 0f,
            radius = 14f,
            bonusPoints = 150
        ),
        ExplorationZone(
            id = "skyline_district",
            name = "Saini Grand Office Tower",
            description = "Enterable multi-floor glass corporate headquarters",
            x = 20f,
            z = -20f,
            radius = 14f,
            bonusPoints = 250
        ),
        ExplorationZone(
            id = "emerald_park",
            name = "Emerald City Park",
            description = "Expansive nature reserve with giant trees & walking paths",
            x = -76f,
            z = 76f,
            radius = 22f,
            bonusPoints = 300
        ),
        ExplorationZone(
            id = "sunset_boulevard",
            name = "Saini Commercial Plaza",
            description = "Bustling retail boulevard, showrooms & roadside shops",
            x = 76f,
            z = -22f,
            radius = 18f,
            bonusPoints = 250
        ),
        ExplorationZone(
            id = "north_financial",
            name = "North Tech & Skyline District",
            description = "High-rise glass towers & executive corporate avenues",
            x = 0f,
            z = -248f,
            radius = 26f,
            bonusPoints = 350
        ),
        ExplorationZone(
            id = "west_suburbs",
            name = "Saini Royal Villas & Suburbs",
            description = "Tree-lined residential neighborhood with luxury villas",
            x = -248f,
            z = 0f,
            radius = 26f,
            bonusPoints = 350
        ),
        ExplorationZone(
            id = "east_industrial",
            name = "East Industrial Factory Zone",
            description = "Heavy CNC manufacturing plants, warehouses & cargo depots",
            x = 224f,
            z = 140f,
            radius = 26f,
            bonusPoints = 350
        ),
        ExplorationZone(
            id = "harbor_boardwalk",
            name = "Saini Waterfront Pier & Marina",
            description = "Grand coastal promenade & wooden pier overlooking the ocean",
            x = 0f,
            z = 408f,
            radius = 24f,
            bonusPoints = 400
        ),
        ExplorationZone(
            id = "saini_airport",
            name = "Saini International Airport",
            description = "Full-length runway, glass ATC tower, hangars & jetliner",
            x = 340f,
            z = -248f,
            radius = 36f,
            bonusPoints = 450
        ),
        ExplorationZone(
            id = "saini_army_base",
            name = "Saini Military Airbase",
            description = "Fortified concrete bunkers, helipad & active military chopper",
            x = -340f,
            z = -248f,
            radius = 34f,
            bonusPoints = 450
        )
    )
}

data class GameSettings(
    val musicVolume: Float = 0.65f,
    val sfxVolume: Float = 0.85f,
    val isMuted: Boolean = false,
    val graphicsQuality: GraphicsQuality = GraphicsQuality.HIGH,
    val controlLayout: ControlLayout = ControlLayout.JOYSTICK,
    val cameraSensitivity: Float = 1.0f,
    val targetFps: Int = 120,
    val streamingRadiusMeters: Float = 62f
)

data class PlayerProfileStats(
    val totalScore: Int = 0,
    val highScore: Int = 0,
    val coins: Int = 1200,
    val diamonds: Int = 150,
    val distanceMeters: Int = 0,
    val zonesDiscoveredTotal: Int = 0
) {
    val level: Int
        get() = (1 + (totalScore / 300) + zonesDiscoveredTotal).coerceAtLeast(1)

    val explorerRank: String
        get() = when {
            highScore >= 2500 -> "Heroic Legend"
            highScore >= 1200 -> "Diamond Explorer"
            highScore >= 500 -> "Gold Pathfinder"
            else -> "Silver Rookie"
        }
}

enum class WeatherState(val label: String) {
    CLEAR("Clear"),
    RAIN("Rain")
}

enum class ActiveVehicleMode {
    ON_FOOT,
    CAR,
    BULLET_BIKE
}

enum class GroundSurface {
    ASPHALT,
    DIRT_GRAVEL,
    GRASS,
    INDOOR_TILE
}

enum class CarCrashType {
    LIGHT_BUMP,
    HEAVY_IMPACT,
    HIGH_SPEED_BRAKE_CRASH
}

data class GameTelemetry(
    val playerX: Float = 0f,
    val playerY: Float = 0f,
    val playerZ: Float = 8.2f,
    val playerYawRad: Float = 0f,
    val cameraYawRad: Float = 0f,
    val isMoving: Boolean = false,
    val isSprinting: Boolean = false,
    val stamina: Float = 100f,
    val health: Float = 100f,
    val collectedOrbIds: Set<Int> = emptySet(),
    val discoveredZoneIds: Set<String> = emptySet(),
    val nearbyInteractTarget: String? = null,
    val distanceThisSession: Float = 0f,
    val sessionScore: Int = 0,
    val currentFps: Int = 60,
    val activeChunksCount: Int = 0,
    val totalChunksCount: Int = 1600,
    val visibleObjectsCount: Int = 0,
    val culledObjectsCount: Int = 0,
    val currentLocationLabel: String = "Saini City • Street Level",
    val weatherState: WeatherState = WeatherState.CLEAR,
    val isNight: Boolean = false,
    val timeOfDayLabel: String = "Day",
    val vehicleMode: ActiveVehicleMode = ActiveVehicleMode.ON_FOOT,
    val vehicleSpeedKmh: Int = 0,
    val nearbyGateName: String? = null,
    val nearbyGateProgress: Float = 0f
)
