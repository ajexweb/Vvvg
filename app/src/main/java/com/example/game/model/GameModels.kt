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
    PROFILE,
    SETTINGS,
    CREDITS
}

enum class GraphicsQuality(val label: String, val fogEnd: Float, val propDensity: Int) {
    LOW("Low", 92f, 1),
    MEDIUM("Medium", 132f, 2),
    HIGH("High", 180f, 3)
}

enum class ControlLayout(val label: String) {
    JOYSTICK("Joystick"),
    DPAD("D-Pad")
}

enum class VehicleControlMode(val label: String) {
    BUTTONS("Buttons"),
    JOYSTICK("Joystick")
}

enum class DensityLevel(val label: String) {
    OFF("Off"),
    LOW("Low"),
    MEDIUM("Med"),
    HIGH("High")
}

enum class RenderResolution(val label: String, val scale: Float) {
    PERFORMANCE_75("75%", 0.75f),
    BALANCED_85("85%", 0.85f),
    NATIVE_100("100%", 1.0f)
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
    val agilityRating: Float = 0.84f,
    val staminaRating: Float = 0.88f,
    val drivingRating: Float = 0.86f
)

/**
 * Only the REAL rigged 3D Ajay Saini character model (zero fake unrendered outfits!).
 */
object GameSkins {
    val all: List<CharacterSkin> = listOf(
        CharacterSkin(
            id = "urban_explorer",
            name = "Ajay Saini",
            subtitle = "Rigged 3D Street Hoodie, Slim Denim & Low-Top Sneakers",
            perkDescription = "Full 6-Animation Rig (Stand, Run, Punch, Kick, Flip, Drop)",
            speedMultiplier = 1.0f,
            skinToneRgb = floatArrayOf(0.84f, 0.65f, 0.50f),
            hairColorRgb = floatArrayOf(0.11f, 0.09f, 0.08f),
            shirtColorRgb = floatArrayOf(0.95f, 0.95f, 0.96f),
            jacketColorRgb = floatArrayOf(0.14f, 0.16f, 0.20f),
            pantsColorRgb = floatArrayOf(0.14f, 0.16f, 0.20f),
            shoeColorRgb = floatArrayOf(0.94f, 0.94f, 0.96f),
            accentColorRgb = floatArrayOf(0.22f, 0.74f, 0.97f),
            previewAccent = Color(0xFF38BDF8),
            badgeLabel = "OFFICIAL 3D",
            agilityRating = 0.88f,
            staminaRating = 0.90f,
            drivingRating = 0.86f
        )
    )

    fun byId(id: String): CharacterSkin = all.firstOrNull { it.id == id } ?: all.first()
}

/**
 * Real working 3D Vehicle Paint Wraps applied directly to the player's driven Car / Bullet 350
 * inside OpenGLCityRenderer!
 */
data class VehiclePaintWrap(
    val id: String,
    val name: String,
    val description: String,
    val swatchColor: Color,
    val rgbOverride: FloatArray?, // null = keep factory original paint
    val priceCoins: Int,
    val rarity: ItemRarity
)

object GameVehicleWraps {
    val all: List<VehiclePaintWrap> = listOf(
        VehiclePaintWrap(
            id = "wrap_factory",
            name = "Factory Original",
            description = "Keeps each car & Bullet bike's original showroom paint.",
            swatchColor = Color(0xFF64748B),
            rgbOverride = null,
            priceCoins = 0,
            rarity = ItemRarity.COMMON
        ),
        VehiclePaintWrap(
            id = "wrap_stealth_carbon",
            name = "Midnight Carbon",
            description = "Applies sleek matte obsidian-carbon finish to your driven vehicle.",
            swatchColor = Color(0xFF1E242E),
            rgbOverride = floatArrayOf(0.11f, 0.13f, 0.17f),
            priceCoins = 250,
            rarity = ItemRarity.RARE
        ),
        VehiclePaintWrap(
            id = "wrap_crimson_blaze",
            name = "Crimson Blaze",
            description = "Applies glossy racing crimson finish to any car or bike you drive.",
            swatchColor = Color(0xFFD91E2A),
            rgbOverride = floatArrayOf(0.84f, 0.11f, 0.16f),
            priceCoins = 400,
            rarity = ItemRarity.RARE
        ),
        VehiclePaintWrap(
            id = "wrap_arctic_pearl",
            name = "Arctic Pearl",
            description = "Applies bright metallic pearl-white paint to your active vehicle.",
            swatchColor = Color(0xFFE8EEF5),
            rgbOverride = floatArrayOf(0.92f, 0.94f, 0.97f),
            priceCoins = 450,
            rarity = ItemRarity.EPIC
        ),
        VehiclePaintWrap(
            id = "wrap_emerald_gt",
            name = "Emerald GT",
            description = "Applies deep British racing emerald finish to your active vehicle.",
            swatchColor = Color(0xFF108E56),
            rgbOverride = floatArrayOf(0.07f, 0.56f, 0.34f),
            priceCoins = 550,
            rarity = ItemRarity.EPIC
        ),
        VehiclePaintWrap(
            id = "wrap_electric_cyan",
            name = "Electric Cyan",
            description = "Applies vibrant metallic neon-cyan finish to your driven vehicle.",
            swatchColor = Color(0xFF0EA5E9),
            rgbOverride = floatArrayOf(0.06f, 0.66f, 0.92f),
            priceCoins = 650,
            rarity = ItemRarity.LEGENDARY
        )
    )

    fun byId(id: String): VehiclePaintWrap = all.firstOrNull { it.id == id } ?: all.first()
}

/**
 * Real working 3D Character Emotes backed by the real rigged GLB animation clips!
 * clipIndex maps directly to PlayerAnimClip ordinal:
 * 0=STAND, 1=RUN, 2=FLIP, 3=HAND_FIGHT, 4=LEG_FIGHT, 5=RELEASE
 */
data class GameEmote(
    val id: String,
    val name: String,
    val shortLabel: String,
    val description: String,
    val clipIndex: Int,
    val priceCoins: Int,
    val rarity: ItemRarity
)

object GameEmotes {
    val all: List<GameEmote> = listOf(
        GameEmote(
            id = "emote_flip",
            name = "Acrobat Backflip",
            shortLabel = "BACKFLIP",
            description = "Performs a full rigged 3D parkour backflip on command.",
            clipIndex = 2,
            priceCoins = 0,
            rarity = ItemRarity.COMMON
        ),
        GameEmote(
            id = "emote_shadow_box",
            name = "Shadow Boxing",
            shortLabel = "BOXING",
            description = "Unleashes a rapid 3D combat hand-fight combo emote.",
            clipIndex = 3,
            priceCoins = 0,
            rarity = ItemRarity.COMMON
        ),
        GameEmote(
            id = "emote_martial_kick",
            name = "Martial High Kick",
            shortLabel = "HIGH KICK",
            description = "Performs a sharp martial-arts spinning leg-fight showcase.",
            clipIndex = 4,
            priceCoins = 150,
            rarity = ItemRarity.RARE
        ),
        GameEmote(
            id = "emote_hero_drop",
            name = "Superhero Landing",
            shortLabel = "HERO DROP",
            description = "Crouches into a dramatic 3D superhero release pose.",
            clipIndex = 5,
            priceCoins = 200,
            rarity = ItemRarity.EPIC
        )
    )

    fun byId(id: String): GameEmote = all.firstOrNull { it.id == id } ?: all.first()
}

enum class StoreCategory(val label: String) {
    ALL("ALL"),
    VEHICLE_PAINTS("VEHICLE PAINTS"),
    EMOTES("3D EMOTES")
}

enum class ItemRarity(val label: String, val color: Color) {
    COMMON("STANDARD", RarityCommon),
    RARE("RARE", RarityRare),
    EPIC("EPIC", RarityEpic),
    LEGENDARY("PRO", RarityLegendary)
}

data class StoreItem(
    val id: String,
    val name: String,
    val category: StoreCategory,
    val description: String,
    val priceCoins: Int = 0,
    val priceDiamonds: Int = 0,
    val rarity: ItemRarity,
    val linkedWrapId: String? = null,
    val linkedEmoteId: String? = null,
    val swatchColor: Color? = null
)

/**
 * 100% REAL Working Store Catalog — ZERO fake unimplemented outfits, gear, or bundles!
 * Every single item either visibly paints your 3D driven vehicle or unlocks a real 3D Emote.
 */
object GameStoreCatalog {
    val defaultOwnedIds: Set<String> = setOf("wrap_factory", "emote_flip", "emote_shadow_box")

    val items: List<StoreItem> = buildList {
        GameVehicleWraps.all.forEach { wrap ->
            add(
                StoreItem(
                    id = wrap.id,
                    name = wrap.name,
                    category = StoreCategory.VEHICLE_PAINTS,
                    description = wrap.description,
                    priceCoins = wrap.priceCoins,
                    rarity = wrap.rarity,
                    linkedWrapId = wrap.id,
                    swatchColor = wrap.swatchColor
                )
            )
        }
        GameEmotes.all.forEach { em ->
            add(
                StoreItem(
                    id = em.id,
                    name = em.name,
                    category = StoreCategory.EMOTES,
                    description = em.description,
                    priceCoins = em.priceCoins,
                    rarity = em.rarity,
                    linkedEmoteId = em.id
                )
            )
        }
    }
}

/**
 * Real Spawn Locations selectable directly from the Lobby!
 */
data class SpawnDistrict(
    val id: String,
    val name: String,
    val regionTag: String,
    val x: Float,
    val z: Float
)

object SpawnDistricts {
    val all: List<SpawnDistrict> = listOf(
        SpawnDistrict("saini_plaza", "Saini Central City", "METRO HUB", 0f, 8.2f),
        SpawnDistrict("saini_airport", "International Airport", "RUNWAY & ATC", 312f, -248f),
        SpawnDistrict("saini_army_base", "Saini Army Airbase", "MILITARY BASE", -312f, -248f),
        SpawnDistrict("saini_keys_island", "Saini Keys Resort", "ISLAND 1", 600f, 5200f),
        SpawnDistrict("goa_palma_island", "Goa Palma City", "MEGA-ISLAND", -12400f, 13600f),
        SpawnDistrict("emerald_port_island", "Emerald Port City", "HARBOR ISLAND", 12200f, 14200f),
        SpawnDistrict("vice_volcano_island", "Vice Volcano Isle", "SOUTH ISLAND", -300f, 20850f),
        SpawnDistrict("shimla_mall", "Jammu & Himalayas", "NORTH PEAKS", -350f, -15800f),
        SpawnDistrict("saini_garh_fort", "Thar Desert Fort", "RAJASTHAN", -12400f, -2400f),
        SpawnDistrict("jungle_temple", "Emerald Jungle", "EAST FOREST", 11800f, -3800f)
    )

    fun byId(id: String): SpawnDistrict = all.firstOrNull { it.id == id } ?: all.first()
}

data class GameAvatar(
    val id: String,
    val name: String,
    val primaryColor: Color,
    val accentColor: Color,
    val badgeBg: Color,
    val styleIndex: Int
)

object GameAvatars {
    val all: List<GameAvatar> = listOf(
        GameAvatar("avatar_viper", "Viper", Color(0xFF00E5FF), Color(0xFF0284C7), Color(0xFF0B1C2C), 0),
        GameAvatar("avatar_phantom", "Phantom", Color(0xFFFF4655), Color(0xFF991B1B), Color(0xFF240F17), 1),
        GameAvatar("avatar_apex", "Apex", Color(0xFF10B981), Color(0xFF047857), Color(0xFF0B231D), 2),
        GameAvatar("avatar_falcon", "Falcon", Color(0xFF60A5FA), Color(0xFF2563EB), Color(0xFF0F1B33), 3),
        GameAvatar("avatar_nova", "Nova", Color(0xFFA855F7), Color(0xFF6D28D9), Color(0xFF1E1133), 4),
        GameAvatar("avatar_ronin", "Ronin", Color(0xFFF43F5E), Color(0xFFBE123C), Color(0xFF290F19), 5),
        GameAvatar("avatar_titan", "Titan", Color(0xFF94A3B8), Color(0xFF475569), Color(0xFF141B24), 6),
        GameAvatar("avatar_cyber", "Cyber", Color(0xFF2DD4BF), Color(0xFF0D9488), Color(0xFF0B2424), 7)
    )

    fun byId(id: String): GameAvatar = all.firstOrNull { it.id == id } ?: all.first()
}

enum class HudElementGroup {
    COMMON,
    ON_FOOT,
    VEHICLE
}

/**
 * EVERY single button & widget is its own individually customizable HUD element!
 * No grouped strips — move, resize, fade, or hide any single button independently.
 */
enum class HudElementId(val label: String, val group: HudElementGroup) {
    HEALTH_BAR("Health Bar", HudElementGroup.COMMON),
    STAMINA_BAR("Stamina Bar", HudElementGroup.COMMON),
    SCORE_BADGE("Score & Zones", HudElementGroup.COMMON),
    FPS_COUNTER("FPS Counter", HudElementGroup.COMMON),
    LOCATION_BAR("Location Tag", HudElementGroup.COMMON),
    TIME_BTN("Day / Night", HudElementGroup.COMMON),
    WEATHER_BTN("Weather", HudElementGroup.COMMON),
    MINIMAP("Mini-Map", HudElementGroup.COMMON),
    PAUSE_BTN("Pause Button", HudElementGroup.COMMON),
    JOYSTICK("Move Joystick", HudElementGroup.ON_FOOT),
    BTN_PUNCH("Punch Button", HudElementGroup.ON_FOOT),
    BTN_KICK("Kick Button", HudElementGroup.ON_FOOT),
    BTN_FLIP("Flip Button", HudElementGroup.ON_FOOT),
    BTN_DROP("Drop Button", HudElementGroup.ON_FOOT),
    BTN_EMOTE("Emote Button", HudElementGroup.ON_FOOT),
    BTN_RUN("Run / Boost", HudElementGroup.COMMON),
    BTN_JUMP("Jump Button", HudElementGroup.ON_FOOT),
    BTN_USE("Use / Exit", HudElementGroup.COMMON),
    BTN_HORN("Car Horn", HudElementGroup.VEHICLE),
    SPEEDOMETER("Speedometer", HudElementGroup.VEHICLE),
    VEHICLE_STEER("Steer L / R", HudElementGroup.VEHICLE),
    VEHICLE_PEDALS("Gas / Brake", HudElementGroup.VEHICLE)
}

data class HudElementConfig(
    val xNorm: Float,
    val yNorm: Float,
    val scale: Float = 1.0f,
    val visible: Boolean = true,
    val opacity: Float = 1.0f
)

data class CustomHudLayout(
    val elements: Map<HudElementId, HudElementConfig> = defaultElements()
) {
    fun configFor(id: HudElementId): HudElementConfig =
        elements[id] ?: defaultElements().getValue(id)

    fun updated(id: HudElementId, cfg: HudElementConfig): CustomHudLayout =
        copy(elements = elements + (id to cfg))

    fun serialize(): String = HudElementId.entries.joinToString(";") { id ->
        val c = configFor(id)
        "${id.name},${"%.3f".format(java.util.Locale.US, c.xNorm)},${"%.3f".format(java.util.Locale.US, c.yNorm)},${"%.2f".format(java.util.Locale.US, c.scale)},${if (c.visible) 1 else 0},${"%.2f".format(java.util.Locale.US, c.opacity)}"
    }

    companion object {
        fun defaultElements(): Map<HudElementId, HudElementConfig> = mapOf(
            // Top-Left Status Cluster (individually movable!)
            HudElementId.HEALTH_BAR to HudElementConfig(0.09f, 0.065f, 1.0f, true, 1.0f),
            HudElementId.STAMINA_BAR to HudElementConfig(0.09f, 0.125f, 1.0f, true, 1.0f),
            HudElementId.SCORE_BADGE to HudElementConfig(0.24f, 0.085f, 1.0f, true, 1.0f),
            HudElementId.FPS_COUNTER to HudElementConfig(0.37f, 0.075f, 1.0f, true, 1.0f),
            HudElementId.LOCATION_BAR to HudElementConfig(0.11f, 0.195f, 1.0f, true, 1.0f),
            HudElementId.TIME_BTN to HudElementConfig(0.27f, 0.195f, 1.0f, true, 1.0f),
            HudElementId.WEATHER_BTN to HudElementConfig(0.37f, 0.195f, 1.0f, true, 1.0f),
            // Top-Right Radar & Pause
            HudElementId.MINIMAP to HudElementConfig(0.84f, 0.18f, 1.0f, true, 1.0f),
            HudElementId.PAUSE_BTN to HudElementConfig(0.955f, 0.08f, 1.0f, true, 1.0f),
            // Bottom-Left Movement & Vehicle Steer
            HudElementId.JOYSTICK to HudElementConfig(0.12f, 0.77f, 1.0f, true, 1.0f),
            HudElementId.VEHICLE_STEER to HudElementConfig(0.14f, 0.80f, 1.0f, true, 1.0f),
            // Bottom-Right Individual Combat, Emote & Action Buttons
            HudElementId.BTN_EMOTE to HudElementConfig(0.68f, 0.56f, 1.0f, true, 1.0f),
            HudElementId.BTN_PUNCH to HudElementConfig(0.755f, 0.56f, 1.0f, true, 1.0f),
            HudElementId.BTN_KICK to HudElementConfig(0.83f, 0.56f, 1.0f, true, 1.0f),
            HudElementId.BTN_FLIP to HudElementConfig(0.905f, 0.56f, 1.0f, true, 1.0f),
            HudElementId.BTN_DROP to HudElementConfig(0.955f, 0.68f, 1.0f, true, 1.0f),
            HudElementId.BTN_RUN to HudElementConfig(0.74f, 0.82f, 1.0f, true, 1.0f),
            HudElementId.BTN_JUMP to HudElementConfig(0.83f, 0.76f, 1.0f, true, 1.0f),
            HudElementId.BTN_USE to HudElementConfig(0.92f, 0.84f, 1.0f, true, 1.0f),
            // Vehicle-Specific Individual Widgets
            HudElementId.SPEEDOMETER to HudElementConfig(0.50f, 0.88f, 1.0f, true, 1.0f),
            HudElementId.BTN_HORN to HudElementConfig(0.68f, 0.82f, 1.0f, true, 1.0f),
            HudElementId.VEHICLE_PEDALS to HudElementConfig(0.84f, 0.80f, 1.0f, true, 1.0f)
        )

        fun default(): CustomHudLayout = CustomHudLayout(defaultElements())

        fun deserialize(raw: String?): CustomHudLayout {
            if (raw.isNullOrBlank()) return default()
            val map = defaultElements().toMutableMap()
            raw.split(";").forEach { entry ->
                val parts = entry.split(",")
                if (parts.size >= 5) {
                    val id = runCatching { HudElementId.valueOf(parts[0]) }.getOrNull()
                    val x = parts[1].toFloatOrNull()
                    val y = parts[2].toFloatOrNull()
                    val s = parts[3].toFloatOrNull()
                    val v = parts[4] == "1"
                    val o = if (parts.size >= 6) (parts[5].toFloatOrNull() ?: 1.0f) else 1.0f
                    if (id != null && x != null && y != null && s != null) {
                        map[id] = HudElementConfig(
                            xNorm = x.coerceIn(0.03f, 0.97f),
                            yNorm = y.coerceIn(0.04f, 0.96f),
                            scale = s.coerceIn(0.65f, 1.45f),
                            visible = v,
                            opacity = o.coerceIn(0.30f, 1.0f)
                        )
                    }
                }
            }
            return CustomHudLayout(map)
        }
    }
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
        ),
        ExplorationZone(
            id = "shimla_mall",
            name = "Jammu & Shimla Mall",
            description = "Alpine mountain town & snow pass on NH-01 North Highway",
            x = -350f,
            z = -15800f,
            radius = 160f,
            bonusPoints = 500
        ),
        ExplorationZone(
            id = "snow_peak_viewpoint",
            name = "Himalayan Snow Summit",
            description = "High-altitude Kashmir & Ladakh glacier summit overlook",
            x = -1200f,
            z = -24500f,
            radius = 180f,
            bonusPoints = 600
        ),
        ExplorationZone(
            id = "saini_garh_fort",
            name = "Saini Garh Desert Fort",
            description = "Majestic sandstone fort overlooking the vast Thar Desert",
            x = -12400f,
            z = -2400f,
            radius = 170f,
            bonusPoints = 600
        ),
        ExplorationZone(
            id = "gram_chaupal",
            name = "Saini Gram & Khet Plains",
            description = "Expansive countryside village, green meadows & farm trails",
            x = 2200f,
            z = -6800f,
            radius = 160f,
            bonusPoints = 450
        ),
        ExplorationZone(
            id = "jungle_temple",
            name = "Emerald Jungle Sanctuary",
            description = "Deep tropical river forest & ancient stone temple ruins",
            x = 11800f,
            z = -3800f,
            radius = 170f,
            bonusPoints = 600
        ),
        ExplorationZone(
            id = "saini_keys_island",
            name = "Saini Keys Resort Island",
            description = "First overseas paradise island linked by Saini Sea Bridge",
            x = 600f,
            z = 5400f,
            radius = 180f,
            bonusPoints = 550
        ),
        ExplorationZone(
            id = "goa_palma_island",
            name = "Goa Palma Mega-Island",
            description = "13.5 km southwestern beach metropolis, marinas & coastal ring",
            x = -12600f,
            z = 13800f,
            radius = 220f,
            bonusPoints = 650
        ),
        ExplorationZone(
            id = "emerald_port_island",
            name = "Emerald Port Mega-Island",
            description = "11.5 km southeastern cargo port, bay skyline & resort highway",
            x = 12400f,
            z = 14400f,
            radius = 220f,
            bonusPoints = 650
        ),
        ExplorationZone(
            id = "vice_volcano_island",
            name = "Vice Volcano Mega-Island",
            description = "14.5 km far-south tropical caldera island & coastal speedway",
            x = -400f,
            z = 24800f,
            radius = 240f,
            bonusPoints = 750
        ),
        ExplorationZone(
            id = "western_fort_island",
            name = "Western Naval Fortress Island",
            description = "Fortified western ocean stronghold connected via Naval Causeway",
            x = -19200f,
            z = 3800f,
            radius = 180f,
            bonusPoints = 600
        ),
        ExplorationZone(
            id = "eastern_lagoon_island",
            name = "Eastern Mangrove Lagoon Island",
            description = "Lush eastern tropical bay island linked by Suspension Bridge",
            x = 18200f,
            z = 3400f,
            radius = 180f,
            bonusPoints = 600
        )
    )
}

/**
 * Full real working Settings synchronized live with OpenGLCityRenderer, GameAudioEngine, and GameplayScreen.
 * Tuned out-of-the-box for lag-free 60 FPS mobile play!
 */
data class GameSettings(
    val musicVolume: Float = 0.65f,
    val sfxVolume: Float = 0.85f,
    val isMuted: Boolean = false,
    val ambientAudioEnabled: Boolean = true,
    val graphicsQuality: GraphicsQuality = GraphicsQuality.MEDIUM,
    val renderResolution: RenderResolution = RenderResolution.BALANCED_85,
    val shadowsEnabled: Boolean = false,
    val grassDensity: DensityLevel = DensityLevel.MEDIUM,
    val aiTrafficDensity: DensityLevel = DensityLevel.MEDIUM,
    val npcDensity: DensityLevel = DensityLevel.MEDIUM,
    val dynamicDayNight: Boolean = true,
    val controlLayout: ControlLayout = ControlLayout.JOYSTICK,
    val vehicleControlMode: VehicleControlMode = VehicleControlMode.BUTTONS,
    val cameraSensitivity: Float = 1.0f,
    val invertCameraY: Boolean = false,
    val autoCenterVehicleCam: Boolean = true,
    val autoSprint: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val hudOpacity: Float = 0.92f,
    val minimapAutoZoom: Boolean = true,
    val targetFps: Int = 60,
    val streamingRadiusMeters: Float = 52f,
    val selectedSpawnId: String = "saini_plaza",
    val startNightMode: Boolean = false,
    val startRainMode: Boolean = false,
    val selectedVehicleWrapId: String = "wrap_factory",
    val selectedEmoteId: String = "emote_flip",
    val customHudLayout: CustomHudLayout = CustomHudLayout.default()
)

data class PlayerProfileStats(
    val playerName: String = "",
    val hasSetUsername: Boolean = false,
    val selectedAvatarId: String = "avatar_viper",
    val totalScore: Int = 0,
    val highScore: Int = 0,
    val coins: Int = 0,
    val diamonds: Int = 0,
    val distanceMeters: Int = 0,
    val zonesDiscoveredTotal: Int = 0
)

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
    val nearbyGateProgress: Float = 0f,
    val isInWater: Boolean = false,
    val waterWarning: String? = null,
    val parkedCarX: Float? = null,
    val parkedCarZ: Float? = null
)
