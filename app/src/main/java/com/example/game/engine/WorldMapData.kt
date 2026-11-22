package com.example.game.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 7 Seamless Biomes spanning the massive 50 km x 65 km GTA 6-Style Bharat Multi-Island Open World!
 * Non-square organic archipelago: huge Bharat Mainland + 6 large separate islands (3.5km - 14km each)
 * connected by multi-tier expressways, mountain passes, island ring roads, and overseas sea bridges.
 */
enum class WorldBiome(
    val displayName: String,
    val mapRegionLabel: String,
    val centerX: Float,
    val centerZ: Float
) {
    SAINI_METRO("Saini Metro City", "SAINI METRO", 0f, -60f),
    SNOW_PEAKS("Jammu & Himalayan Peaks", "JAMMU & HIMALAYAS", -1200f, -19500f),
    THAR_DESERT("Rajasthan Thar Desert", "THAR DESERT", -12800f, -2400f),
    VILLAGE_PLAINS("Saini Khet Plains", "SAINI PLAINS", 2200f, -6800f),
    JUNGLE_RIVER("Emerald Jungle & River", "EMERALD JUNGLE", 12200f, -3800f),
    KONKAN_COAST("Goa Palma Mega-Island", "GOA PALMA ISLAND", -12600f, 13800f),
    SAINI_KEYS("Saini Keys & Vice Islands", "SOUTHERN ISLANDS", 400f, 23800f)
}

enum class PoiCategory(val label: String) {
    LANDMARK("Landmark"),
    TOWN("Town / City"),
    MILITARY("Military"),
    GARAGE("Garage"),
    PETROL_PUMP("Petrol Pump"),
    DHABA("Highway Dhaba"),
    HELIPAD_DOCK("Air / Sea Port")
}

data class RegionalPoi(
    val id: String,
    val name: String,
    val x: Float,
    val z: Float,
    val biome: WorldBiome,
    val category: PoiCategory,
    val radius: Float = 80f
)

enum class RoadClass(
    val halfWidth: Float,
    val isPaved: Boolean,
    val isBridge: Boolean
) {
    RING_EXPRESSWAY(8.4f, true, false),
    HIGHWAY(7.8f, true, false),
    GHAT_MOUNTAIN(6.4f, true, false),
    SEA_BRIDGE(8.2f, true, true),
    DIRT_TRAIL(5.0f, false, false)
}

data class RoadSpline(
    val id: String,
    val name: String,
    val roadClass: RoadClass,
    val halfWidth: Float,
    val points: List<Pair<Float, Float>>,
    val closedLoop: Boolean = false
)

data class RoadSegFlat(
    val splineId: String,
    val roadClass: RoadClass,
    val halfWidth: Float,
    val ax: Float,
    val ay: Float,
    val az: Float,
    val bx: Float,
    val by: Float,
    val bz: Float,
    val length: Float,
    val isBridge: Boolean
) {
    val dx: Float get() = bx - ax
    val dz: Float get() = bz - az
}

data class BiomeWeights(
    val metro: Float = 0f,
    val snow: Float = 0f,
    val desert: Float = 0f,
    val plains: Float = 0f,
    val jungle: Float = 0f,
    val coast: Float = 0f,
    val keys: Float = 0f
)

data class IslandLabelSpec(
    val title: String,
    val x: Float,
    val z: Float
)

/**
 * Deterministic 50 km x 65 km Open-World Terrain, Multi-Island Archipelago, Unified-Elevation Road
 * Network, Road-Cleared Regional POIs, GPS Pathfinding, and 512x666 Satellite Map Generator.
 */
object WorldMapData {

    // Non-square 50 km (E-W) x 65 km (N-S) GTA 6-Scale Open World!
    const val WORLD_WIDTH = 50000f
    const val WORLD_HEIGHT = 65000f
    const val WORLD_HALF_X = 25000f
    const val WORLD_HALF_Z = 32500f
    const val PLAYABLE_BOUND_X = 24500f
    const val PLAYABLE_BOUND_Z = 32000f

    // Backward-compatible aliases (callers should prefer WORLD_HALF_X / WORLD_HALF_Z)
    const val WORLD_HALF_EXTENT = WORLD_HALF_Z
    const val WORLD_SIZE = WORLD_HEIGHT
    const val PLAYABLE_BOUND = PLAYABLE_BOUND_X
    const val WATER_LEVEL = -1.65f

    // Saini Metro Downtown footprint at (0, 0) preserved 100% flat & unobstructed
    const val METRO_MIN_X = -455f
    const val METRO_MAX_X = 455f
    const val METRO_MIN_Z = -425f
    const val METRO_MAX_Z = 408f

    // Satellite map exact 50:65 aspect ratio
    const val SAT_MAP_WIDTH = 512
    const val SAT_MAP_HEIGHT = 666

    // Sparse spatial road grid cell size (250m -> O(1) lookups across 50km x 65km with tiny memory!)
    private const val ROAD_GRID_CELL = 250f

    val islandMapLabels: List<IslandLabelSpec> = listOf(
        IslandLabelSpec("JAMMU & HIMALAYAS", -1000f, -21000f),
        IslandLabelSpec("THAR DESERT", -13200f, -2800f),
        IslandLabelSpec("SAINI METRO", 0f, -200f),
        IslandLabelSpec("SAINI KHET PLAINS", 2500f, -8500f),
        IslandLabelSpec("EMERALD JUNGLE", 12500f, -5500f),
        IslandLabelSpec("WESTERN FORT ISLAND", -19200f, 3800f),
        IslandLabelSpec("EASTERN LAGOON ISLAND", 18200f, 3400f),
        IslandLabelSpec("SAINI KEYS ISLAND", 600f, 5400f),
        IslandLabelSpec("GOA PALMA MEGA-ISLAND", -12600f, 13800f),
        IslandLabelSpec("EMERALD PORT ISLAND", 12400f, 14400f),
        IslandLabelSpec("VICE VOLCANO ISLAND", -400f, 24800f)
    )

    // =========================================================================
    // 1. MULTI-TIER 50 KM x 65 KM CONNECTED ROAD & OVERSEAS SEA BRIDGE NETWORK
    //    Every highway junction shares exact node coordinates so the Junction
    //    Height Unification pass snaps intersecting roads flush (0.00m step)!
    // =========================================================================
    val roadSplines: List<RoadSpline> = listOf(
        // 1) Saini Metro Outer Ring Expressway (loops cleanly outside downtown at ~640m-760m)
        RoadSpline(
            id = "saini_ring",
            name = "Saini Ring Expressway",
            roadClass = RoadClass.RING_EXPRESSWAY,
            halfWidth = 8.4f,
            closedLoop = true,
            points = listOf(
                0f to -640f,
                420f to -635f,
                680f to -450f,
                700f to 0f,
                680f to 480f,
                420f to 660f,
                48f to 665f,
                -420f to 660f,
                -680f to 480f,
                -700f to 0f,
                -680f to -450f,
                -420f to -635f
            )
        ),

        // 2) Seamless Metro Downtown Connectors (link CityWorldData streets directly to Ring!)
        RoadSpline(
            id = "metro_spur_n",
            name = "North Metro Link",
            roadClass = RoadClass.RING_EXPRESSWAY,
            halfWidth = 7.4f,
            points = listOf(
                0f to -400f,
                0f to -495f,
                0f to -640f
            )
        ),
        RoadSpline(
            id = "metro_spur_w",
            name = "West Airport Expressway",
            roadClass = RoadClass.RING_EXPRESSWAY,
            halfWidth = 7.4f,
            points = listOf(
                -400f to 0f,
                -530f to 0f,
                -700f to 0f
            )
        ),
        RoadSpline(
            id = "metro_spur_e",
            name = "East Boulevard Link",
            roadClass = RoadClass.RING_EXPRESSWAY,
            halfWidth = 7.4f,
            points = listOf(
                400f to 0f,
                530f to 0f,
                700f to 0f
            )
        ),
        RoadSpline(
            id = "metro_spur_s",
            name = "South Harbour Link",
            roadClass = RoadClass.RING_EXPRESSWAY,
            halfWidth = 7.4f,
            points = listOf(
                48f to 398f,
                48f to 520f,
                48f to 665f
            )
        ),

        // 3) NH-01 Grand North Himalayan Highway (Saini Ring -> Khet Plains -> Jammu Foothills -> Rohtang -> Leh Summit)
        RoadSpline(
            id = "nh01_north_himalaya",
            name = "NH-01 Jammu-Himalaya Highway",
            roadClass = RoadClass.HIGHWAY,
            halfWidth = 7.8f,
            points = listOf(
                0f to -640f,
                -80f to -1450f,
                -220f to -2800f,
                -350f to -4600f,
                -150f to -6800f,
                320f to -9400f,
                -120f to -12200f,
                -750f to -15200f,
                -1200f to -18200f,
                -900f to -21200f,
                -450f to -24200f,
                -1200f to -26800f
            )
        ),

        // 4) Himalayan Scenic Ridge Ghat Loop (branches off NH-01 across snow passes & alpine lake)
        RoadSpline(
            id = "himalaya_ridge_ghat",
            name = "Kashmir Alpine Pass Road",
            roadClass = RoadClass.GHAT_MOUNTAIN,
            halfWidth = 6.2f,
            points = listOf(
                -750f to -15200f,
                -3800f to -16400f,
                -7200f to -18500f,
                -9400f to -21400f,
                -6800f to -24800f,
                -3600f to -26400f,
                -1200f to -26800f,
                2600f to -25200f,
                6200f to -22400f,
                7400f to -18200f,
                4600f to -14800f,
                -120f to -12200f
            )
        ),

        // 5) NH-02 Western Thar Desert Super-Expressway (Saini Ring -> Jaipur Gate -> Thar Dunes -> Jaisalmer -> Western Fort Sea Bridge)
        RoadSpline(
            id = "nh02_west_desert",
            name = "NH-02 Thar Desert Expressway",
            roadClass = RoadClass.HIGHWAY,
            halfWidth = 7.8f,
            points = listOf(
                -700f to 0f,
                -1800f to -120f,
                -3600f to -480f,
                -6200f to -1100f,
                -9200f to -1650f,
                -12400f to -1900f,
                -15200f to -1200f,
                -17200f to 200f
            )
        ),

        // 6) Thar Salt Flats & Oasis Scenic Circuit (North-West desert loop)
        RoadSpline(
            id = "thar_oasis_loop",
            name = "Rajputana Oasis Highway",
            roadClass = RoadClass.HIGHWAY,
            halfWidth = 6.8f,
            points = listOf(
                -6200f to -1100f,
                -7600f to -4600f,
                -10800f to -7800f,
                -14800f to -8200f,
                -17600f to -5400f,
                -15200f to -1200f
            )
        ),

        // 7) Desert-to-Himalaya Foothill Corridor (connects Thar Desert directly north to Jammu!)
        RoadSpline(
            id = "nw_foothill_corridor",
            name = "Punjab-Jammu Corridor",
            roadClass = RoadClass.HIGHWAY,
            halfWidth = 7.2f,
            points = listOf(
                -10800f to -7800f,
                -9200f to -11500f,
                -6800f to -14200f,
                -3800f to -16400f
            )
        ),

        // 8) NH-03 Eastern Jungle & Saini River Expressway (Saini Ring -> Green Valley -> River Bridge -> Jungle Ruins -> Eastern Coast)
        RoadSpline(
            id = "nh03_east_jungle",
            name = "NH-03 Emerald Jungle Highway",
            roadClass = RoadClass.HIGHWAY,
            halfWidth = 7.8f,
            points = listOf(
                700f to 0f,
                1900f to -200f,
                3900f to -700f,
                6400f to -1400f,
                9200f to -2100f,
                12000f to -1800f,
                14200f to -400f,
                15400f to 1400f
            )
        ),

        // 9) North-East Khet & Rainforest Loop (connects Plains, Jungle & Himalayan East Foothills!)
        RoadSpline(
            id = "ne_plains_jungle_loop",
            name = "Saini Valley & Jungle Loop",
            roadClass = RoadClass.HIGHWAY,
            halfWidth = 6.8f,
            points = listOf(
                -150f to -6800f,
                3200f to -7200f,
                6800f to -8400f,
                10400f to -9600f,
                13600f to -8200f,
                14800f to -4600f,
                12000f to -1800f
            )
        ),

        // 10) East Himalaya Link
        RoadSpline(
            id = "ne_himalaya_link",
            name = "Shivalik Foothill Highway",
            roadClass = RoadClass.GHAT_MOUNTAIN,
            halfWidth = 6.4f,
            points = listOf(
                6800f to -8400f,
                6200f to -11800f,
                4600f to -14800f
            )
        ),

        // 11) Mainland Southern Coastal Boulevard (SW Konkan Coast <-> Saini South <-> SE Bay Coast)
        RoadSpline(
            id = "mainland_coastal_hwy",
            name = "Konkan Grand Coastal Highway",
            roadClass = RoadClass.HIGHWAY,
            halfWidth = 7.4f,
            points = listOf(
                -12400f to -1900f,
                -11200f to 1600f,
                -8400f to 3600f,
                -4600f to 2600f,
                -1800f to 1200f,
                48f to 665f,
                2400f to 1350f,
                5600f to 2600f,
                9200f to 3200f,
                12200f to 2400f,
                14200f to -400f
            )
        ),

        // =====================================================================
        // OVERSEAS SEA BRIDGES + 6 MASSIVE ISLAND RING ROAD NETWORKS
        // =====================================================================

        // 12) Saini Overseas Sea Bridge (South Metro Ring -> Saini Keys Island -> Vice Volcano Mega-Island!)
        RoadSpline(
            id = "saini_keys_sea_bridge",
            name = "Saini Overseas Sea Bridge",
            roadClass = RoadClass.SEA_BRIDGE,
            halfWidth = 8.0f,
            points = listOf(
                48f to 665f,
                160f to 1400f,
                320f to 2500f,
                480f to 3600f,
                560f to 4300f
            )
        ),

        // 13) Saini Keys Island Ring Highway
        RoadSpline(
            id = "saini_keys_ring",
            name = "Saini Keys Island Parkway",
            roadClass = RoadClass.HIGHWAY,
            halfWidth = 7.0f,
            closedLoop = true,
            points = listOf(
                560f to 4300f,
                2200f to 4500f,
                3100f to 5400f,
                2200f to 6600f,
                400f to 6900f,
                -1300f to 6400f,
                -1900f to 5300f,
                -1000f to 4400f
            )
        ),

        // 14) Western Overseas Expressway Bridge (Konkan Mainland -> Goa Palma Mega-Island!)
        RoadSpline(
            id = "goa_palma_sea_bridge",
            name = "Goa Palma Grand Sea Bridge",
            roadClass = RoadClass.SEA_BRIDGE,
            halfWidth = 8.2f,
            points = listOf(
                -8400f to 3600f,
                -9400f to 5800f,
                -10600f to 8200f,
                -11400f to 9800f
            )
        ),

        // 15) Goa Palma Mega-Island Grand Ring Expressway (~13.5km x 9.5km island!)
        RoadSpline(
            id = "goa_palma_ring",
            name = "Goa Palma Island Expressway",
            roadClass = RoadClass.HIGHWAY,
            halfWidth = 7.6f,
            closedLoop = true,
            points = listOf(
                -11400f to 9800f,
                -8000f to 10800f,
                -6800f to 13600f,
                -8200f to 16600f,
                -11800f to 17600f,
                -16000f to 16800f,
                -18200f to 14000f,
                -17200f to 11000f,
                -14400f to 9800f
            )
        ),

        // 16) Goa Palma Cross-Island Boulevard (cuts straight through Palma Downtown & Airstrip)
        RoadSpline(
            id = "goa_palma_cross",
            name = "Palma Beach Boulevard",
            roadClass = RoadClass.HIGHWAY,
            halfWidth = 7.0f,
            points = listOf(
                -18200f to 14000f,
                -14800f to 13800f,
                -11600f to 13600f,
                -6800f to 13600f
            )
        ),

        // 17) Keys-to-Palma Turquoise Causeway (links Saini Keys Island directly to Goa Palma Mega-Island!)
        RoadSpline(
            id = "keys_palma_causeway",
            name = "Coral Bay Causeway",
            roadClass = RoadClass.SEA_BRIDGE,
            halfWidth = 7.4f,
            points = listOf(
                -1900f to 5300f,
                -4200f to 7600f,
                -6400f to 9400f,
                -8000f to 10800f
            )
        ),

        // 18) Eastern Overseas Sea Bridge (SE Coast -> Emerald Port Island!)
        RoadSpline(
            id = "emerald_port_sea_bridge",
            name = "Emerald Harbour Sea Bridge",
            roadClass = RoadClass.SEA_BRIDGE,
            halfWidth = 8.2f,
            points = listOf(
                9200f to 3200f,
                10400f to 6200f,
                11400f to 9200f,
                11800f to 10800f
            )
        ),

        // 19) Emerald Port Island Ring Expressway (~11.5km x 8.5km island!)
        RoadSpline(
            id = "emerald_port_ring",
            name = "Emerald Port Ring Road",
            roadClass = RoadClass.HIGHWAY,
            halfWidth = 7.6f,
            closedLoop = true,
            points = listOf(
                11800f to 10800f,
                15400f to 11600f,
                17200f to 14200f,
                15600f to 17200f,
                11800f to 17800f,
                8400f to 16400f,
                7600f to 13800f,
                9000f to 11400f
            )
        ),

        // 20) Keys-to-Emerald Sea Bridge (links Saini Keys Island to Emerald Port Island!)
        RoadSpline(
            id = "keys_emerald_bridge",
            name = "East Reef Overseas Bridge",
            roadClass = RoadClass.SEA_BRIDGE,
            halfWidth = 7.4f,
            points = listOf(
                3100f to 5400f,
                5600f to 8200f,
                7400f to 10000f,
                9000f to 11400f
            )
        ),

        // 21) Grand Southern Ocean Causeway (Saini Keys -> Far-South Vice Volcano Mega-Island!)
        RoadSpline(
            id = "vice_south_causeway",
            name = "Grand Southern Ocean Highway",
            roadClass = RoadClass.SEA_BRIDGE,
            halfWidth = 8.4f,
            points = listOf(
                400f to 6900f,
                200f to 11200f,
                -100f to 15800f,
                -300f to 20600f
            )
        ),

        // 22) Vice Volcano Mega-Island Grand Ring Road (~14km x 8.5km far-south island!)
        RoadSpline(
            id = "vice_volcano_ring",
            name = "Vice Volcano Coastal Highway",
            roadClass = RoadClass.HIGHWAY,
            halfWidth = 7.6f,
            closedLoop = true,
            points = listOf(
                -300f to 20600f,
                4200f to 21600f,
                6200f to 24600f,
                4400f to 27800f,
                -400f to 28600f,
                -5200f to 27600f,
                -6800f to 24400f,
                -4600f to 21400f
            )
        ),

        // 23) Palma-to-Vice West Ocean Bridge & Emerald-to-Vice East Ocean Bridge
        RoadSpline(
            id = "palma_vice_bridge",
            name = "West Atoll Sea Bridge",
            roadClass = RoadClass.SEA_BRIDGE,
            halfWidth = 7.4f,
            points = listOf(
                -8200f to 16600f,
                -6600f to 19000f,
                -4600f to 21400f
            )
        ),
        RoadSpline(
            id = "emerald_vice_bridge",
            name = "East Atoll Sea Bridge",
            roadClass = RoadClass.SEA_BRIDGE,
            halfWidth = 7.4f,
            points = listOf(
                8400f to 16400f,
                6400f to 19000f,
                4200f to 21600f
            )
        ),

        // 24) Western Naval Fortress Island Bridge & Ring Road
        RoadSpline(
            id = "west_fort_bridge",
            name = "Western Naval Causeway",
            roadClass = RoadClass.SEA_BRIDGE,
            halfWidth = 7.4f,
            points = listOf(
                -17200f to 200f,
                -18000f to 1200f,
                -18600f to 2000f
            )
        ),
        RoadSpline(
            id = "west_fort_ring",
            name = "Fortress Island Perimeter Road",
            roadClass = RoadClass.HIGHWAY,
            halfWidth = 6.8f,
            closedLoop = true,
            points = listOf(
                -18600f to 2000f,
                -16800f to 3200f,
                -17200f to 5400f,
                -19600f to 6000f,
                -21600f to 4400f,
                -21000f to 2400f
            )
        ),

        // 25) Eastern Lagoon Island Bridge & Ring Road
        RoadSpline(
            id = "east_lagoon_bridge",
            name = "Mangrove Lagoon Suspension Bridge",
            roadClass = RoadClass.SEA_BRIDGE,
            halfWidth = 7.4f,
            points = listOf(
                15400f to 1400f,
                16400f to 2000f,
                17200f to 2400f
            )
        ),
        RoadSpline(
            id = "east_lagoon_ring",
            name = "Lagoon Island Scenic Loop",
            roadClass = RoadClass.HIGHWAY,
            halfWidth = 6.8f,
            closedLoop = true,
            points = listOf(
                17200f to 2400f,
                19800f to 2200f,
                20800f to 4000f,
                19400f to 5800f,
                16800f to 5400f,
                16200f to 3600f
            )
        ),

        // 26) Countryside & Mountain Dirt Off-Road Trails (cleanly connected to highways)
        RoadSpline(
            id = "khet_village_trail",
            name = "Saini Khet Farm Trail",
            roadClass = RoadClass.DIRT_TRAIL,
            halfWidth = 5.0f,
            points = listOf(
                -350f to -4600f,
                1800f to -4400f,
                3800f to -3400f,
                5600f to -4800f,
                3200f to -7200f
            )
        ),
        RoadSpline(
            id = "vice_volcano_climb",
            name = "Caldera Summit Trail",
            roadClass = RoadClass.GHAT_MOUNTAIN,
            halfWidth = 5.8f,
            points = listOf(
                -300f to 20600f,
                1200f to 23400f,
                -400f to 25200f,
                -2200f to 26400f,
                -400f to 28600f
            )
        )
    )

    // =========================================================================
    // 2. PRE-BAKED UNIFIED-ELEVATION ROAD SEGMENTS + SPARSE SPATIAL GRID
    // =========================================================================
    val allRoadSegments: List<RoadSegFlat>
    private val roadSpatialGrid: HashMap<Long, ArrayList<RoadSegFlat>>

    // 55+ Rich Regional POIs — automatically offset safely OFF all roads!
    val regionalPois: List<RegionalPoi>

    init {
        allRoadSegments = buildUnifiedRoadSegments()
        roadSpatialGrid = buildSparseRoadGrid(allRoadSegments)
        regionalPois = buildRoadClearedPois()
    }

    private fun gridKey(gx: Int, gz: Int): Long {
        return (gx.toLong() shl 32) or (gz.toLong() and 0xFFFFFFFFL)
    }

    // =========================================================================
    // 3. NON-SQUARE 50 KM x 65 KM MULTI-ISLAND ARCHIPELAGO COASTLINE & BIOMES
    // =========================================================================

    private fun radialIslandSignedDist(
        x: Float,
        z: Float,
        cx: Float,
        cz: Float,
        rx: Float,
        rz: Float,
        seed: Float
    ): Float {
        val dx = x - cx
        val dz = z - cz
        val nx = dx / rx
        val nz = dz / rz
        val ang = atan2(nz, nx)
        // Rich GTA-6 organic coastline bays, peninsulas & coves
        val warp = 1.0f +
            0.13f * sin(ang * 2f + seed) +
            0.09f * cos(ang * 3f - seed * 1.4f) +
            0.06f * sin(ang * 5f + seed * 0.7f) +
            0.035f * cos(ang * 8f - seed * 2.1f)
        val normR = sqrt(nx * nx + nz * nz) / warp
        val avgR = (rx + rz) * 0.5f
        return (1.0f - normR) * avgR
    }

    /**
     * Signed distance to nearest coastline in meters:
     *   > 0 = dry land (mainland Bharat OR one of the 6 large islands!)
     *   = 0 = shoreline
     *   < 0 = open ocean / turquoise strait
     */
    fun getCoastlineSignedDist(x: Float, z: Float): Float {
        // Saini Metro City + generous surrounding buffer is ALWAYS solid dry mainland!
        if (x in -1500f..1500f && z in -1500f..1050f) {
            return 1400f
        }

        // A) Huge Organic Bharat Mainland Continent (non-square multi-lobe landmass)
        val mainlandNorth = radialIslandSignedDist(x, z, -500f, -15500f, 16500f, 14500f, 0.7f)
        val mainlandCentral = radialIslandSignedDist(x, z, -400f, -4500f, 17800f, 9200f, 1.9f)
        val mainlandWest = radialIslandSignedDist(x, z, -11200f, -2600f, 10200f, 7600f, 3.1f)
        val mainlandEast = radialIslandSignedDist(x, z, 10400f, -3200f, 9600f, 8200f, 4.5f)
        val mainlandSWCoast = radialIslandSignedDist(x, z, -8200f, 1800f, 5200f, 3200f, 2.2f)
        val mainlandSECoast = radialIslandSignedDist(x, z, 8600f, 1600f, 5200f, 3200f, 5.1f)

        var best = max(
            max(mainlandNorth, mainlandCentral),
            max(max(mainlandWest, mainlandEast), max(mainlandSWCoast, mainlandSECoast))
        )

        // Carve natural turquoise South Metro Bay smoothly BELOW z = +1150m (well outside city!)
        val bayDx = (x - 400f) / 4800f
        val bayDz = (z - 2550f) / 1550f
        val bayR = sqrt(bayDx * bayDx + bayDz * bayDz)
        if (bayR < 1.15f) {
            val carve = (1.15f - bayR) * 1800f
            best -= carve
        }

        // B) 6 Massive Separate Islands (each 4km - 14km across with full roads, cities & landmarks!)
        // 1. Saini Keys & Luxury Resort Island (~6.5 km x 4.2 km)
        val islKeys = radialIslandSignedDist(x, z, 600f, 5550f, 3300f, 2050f, 1.2f)
        // 2. Goa Palma Mega-Island (~13.5 km x 9.5 km — SW GTA-6 coastal metropolis island!)
        val islPalma = radialIslandSignedDist(x, z, -12600f, 13800f, 6750f, 4800f, 2.6f)
        // 3. Emerald Port & Harbor Island (~11.5 km x 8.5 km — SE port & resort island!)
        val islEmerald = radialIslandSignedDist(x, z, 12400f, 14400f, 5800f, 4350f, 3.8f)
        // 4. Vice Volcano Mega-Island (~14.5 km x 9.2 km — Far-South tropical volcanic island!)
        val islVice = radialIslandSignedDist(x, z, -400f, 24800f, 7300f, 4650f, 5.4f)
        // 5. Western Naval Fortress Island (~6.2 km x 5.2 km)
        val islWestFort = radialIslandSignedDist(x, z, -19200f, 3900f, 3150f, 2650f, 0.4f)
        // 6. Eastern Mangrove Lagoon Island (~6.4 km x 5.2 km)
        val islEastLagoon = radialIslandSignedDist(x, z, 18400f, 3850f, 3250f, 2600f, 4.9f)

        best = max(best, islKeys)
        best = max(best, islPalma)
        best = max(best, islEmerald)
        best = max(best, islVice)
        best = max(best, islWestFort)
        best = max(best, islEastLagoon)

        // Subtle fine shoreline detail noise
        val detail = (fbm2D(x * 0.0014f, z * 0.0014f, 2) - 0.5f) * 260f
        return best + detail
    }

    /**
     * Saini River winding across East Plains & Emerald Jungle into the Eastern Bay.
     */
    fun getRiverCenterX(z: Float): Float {
        return 6200f +
            sin(z * 0.00042f) * 1900f +
            cos(z * 0.00019f + 0.8f) * 950f
    }

    fun getRiverHalfWidth(z: Float): Float {
        val widen = smoothstep(-15000f, 3000f, z)
        return 110f + widen * 140f
    }

    fun getRiverSignedDist(x: Float, z: Float): Float {
        if (z < -16000f || z > 4200f) return 9999f
        val rx = getRiverCenterX(z)
        return abs(x - rx) - getRiverHalfWidth(z)
    }

    fun getBiomeWeights(x: Float, z: Float): BiomeWeights {
        if (x in METRO_MIN_X..METRO_MAX_X && z in METRO_MIN_Z..METRO_MAX_Z) {
            return BiomeWeights(metro = 1f)
        }

        // Smooth metro edge fade (455m -> 760m)
        val dxM = max(0f, max(METRO_MIN_X - x, x - METRO_MAX_X))
        val dzM = max(0f, max(METRO_MIN_Z - z, z - METRO_MAX_Z))
        val distMetro = sqrt(dxM * dxM + dzM * dzM)
        val wMetro = (1f - smoothstep(0f, 320f, distMetro)) * 2.4f

        // South islands (z > 3200) -> Konkan Coast (SW Palma) vs Saini Keys / Tropical Islands
        val isSouthIslandBand = smoothstep(2600f, 4600f, z)
        val isPalmaWest = smoothstep(-3200f, -7200f, x) * isSouthIslandBand
        val wKeys = isSouthIslandBand * (1f - isPalmaWest * 0.85f) * 2.2f
        val wCoastIsland = isPalmaWest * 2.2f

        // Far North -> Jammu & Himalayas Snow Peaks (z < -10500)
        val wSnow = smoothstep(-9500f, -14500f, z) * 2.4f

        // West -> Thar Desert (x < -3800, mainland latitude)
        val desertLat = 1f - smoothstep(-12500f, -16500f, z) - smoothstep(2600f, 6800f, z)
        val wDesert = (smoothstep(-3200f, -7200f, x) * desertLat.coerceIn(0f, 1f)) * 2.2f

        // East -> Emerald Jungle (x > +3800, mainland latitude)
        val jungleLat = 1f - smoothstep(-13000f, -17000f, z) - smoothstep(4200f, 8200f, z)
        val wJungle = (smoothstep(3200f, 7000f, x) * jungleLat.coerceIn(0f, 1f)) * 2.1f

        // Mainland coastline sandy band
        val coastDist = getCoastlineSignedDist(x, z)
        val wCoastShore = (1f - smoothstep(120f, 780f, coastDist)) * (1f - wSnow * 0.45f).coerceIn(0f, 1f) * 1.15f

        // Central green Khet plains fill the remainder organically
        val wPlains = 0.85f * (1f - isSouthIslandBand * 0.85f)

        val total = (wMetro + wSnow + wDesert + wPlains + wJungle + wCoastIsland + wCoastShore + wKeys).coerceAtLeast(0.001f)
        return BiomeWeights(
            metro = wMetro / total,
            snow = wSnow / total,
            desert = wDesert / total,
            plains = wPlains / total,
            jungle = wJungle / total,
            coast = (wCoastIsland + wCoastShore) / total,
            keys = wKeys / total
        )
    }

    fun getBiomeAt(x: Float, z: Float): WorldBiome {
        val w = getBiomeWeights(x, z)
        var best = WorldBiome.VILLAGE_PLAINS
        var bestW = w.plains
        if (w.metro > bestW) { bestW = w.metro; best = WorldBiome.SAINI_METRO }
        if (w.snow > bestW) { bestW = w.snow; best = WorldBiome.SNOW_PEAKS }
        if (w.desert > bestW) { bestW = w.desert; best = WorldBiome.THAR_DESERT }
        if (w.jungle > bestW) { bestW = w.jungle; best = WorldBiome.JUNGLE_RIVER }
        if (w.coast > bestW) { bestW = w.coast; best = WorldBiome.KONKAN_COAST }
        if (w.keys > bestW) { best = WorldBiome.SAINI_KEYS }
        return best
    }

    // =========================================================================
    // 4. NATURAL TERRAIN HEIGHTFIELD + UNIFIED MULTI-ROAD JUNCTION BLENDING
    // =========================================================================

    fun getNaturalTerrainHeight(x: Float, z: Float): Float {
        // Saini Metro downtown is strictly flat y = 0.0f
        if (x in METRO_MIN_X..METRO_MAX_X && z in METRO_MIN_Z..METRO_MAX_Z) {
            return 0f
        }

        val dxM = max(0f, max(METRO_MIN_X - x, x - METRO_MAX_X))
        val dzM = max(0f, max(METRO_MIN_Z - z, z - METRO_MAX_Z))
        val distMetro = sqrt(dxM * dxM + dzM * dzM)
        val metroBlend = smoothstep(0f, 340f, distMetro)

        val coastDist = getCoastlineSignedDist(x, z)
        if (coastDist <= -120f) {
            val deepT = smoothstep(-120f, -1400f, coastDist)
            return WATER_LEVEL - 1.2f - deepT * 18.0f
        }

        val w = getBiomeWeights(x, z)
        val macro = fbm2D(x * 0.00024f, z * 0.00024f, 4)
        val medium = fbm2D(x * 0.0011f + 17.3f, z * 0.0011f - 9.1f, 3)
        val fine = fbm2D(x * 0.0042f - 5.4f, z * 0.0042f + 11.7f, 2)

        // Himalayan majestic mountain ranges (up to +240m in deep North)
        val ridge = 1f - abs(medium * 2f - 1f)
        val hSnow = 18f + macro * 135f + (ridge * ridge) * 105f + fine * 8f

        // Rolling Thar Desert dunes + sandstone mesas
        val duneWave = sin((x * 0.0048f + z * 0.0022f) + macro * 3.8f) * 0.5f + 0.5f
        val hDesert = 3.5f + macro * 22f + duneWave * 9.5f + fine * 1.8f

        // Gentle rolling green Saini Khet plains
        val hPlains = 1.4f + macro * 12f + medium * 5.2f + fine * 1.0f

        // Emerald Jungle hills & ridges
        val hJungle = 4.2f + macro * 34f + medium * 16f + fine * 2.5f

        // Coastal islands (Goa Palma hills + beaches)
        val hCoast = 2.0f + macro * 24f + medium * 10f + fine * 1.4f

        // Tropical Southern Islands (including Vice Volcano Peak at (-400, 24800)!)
        val vDx = x - (-400f)
        val vDz = z - 24800f
        val vDist = sqrt(vDx * vDx + vDz * vDz)
        val volcanoCone = (1f - smoothstep(450f, 3400f, vDist)) * 165f
        val craterDip = (1f - smoothstep(0f, 520f, vDist)) * 32f
        val hKeys = 2.2f + macro * 20f + medium * 9f + volcanoCone - craterDip

        var h = w.metro * 0f +
            w.snow * hSnow +
            w.desert * hDesert +
            w.plains * hPlains +
            w.jungle * hJungle +
            w.coast * hCoast +
            w.keys * hKeys

        // Smooth shoreline ramp from beach down into turquoise shallow water
        val shoreMask = smoothstep(-120f, 420f, coastDist)
        val underwaterY = WATER_LEVEL - 1.2f
        h = underwaterY + (h - underwaterY) * shoreMask

        // Carve winding Saini River channel smoothly
        val riverSd = getRiverSignedDist(x, z)
        if (riverSd < 180f) {
            val rT = smoothstep(-40f, 180f, riverSd)
            val riverBed = WATER_LEVEL - 2.6f
            h = riverBed + (h - riverBed) * rT
        }

        // Flatten around Saini Metro boundary seamlessly
        return h * metroBlend
    }

    /**
     * Evaluates ALL nearby road segments around (x, z) using smooth inverse-distance blending!
     * Fixes road-junction height steps AND guarantees zero dirt clipping on overlapping roads.
     */
    private data class RoadBlendResult(
        val nearestDist: Float,
        val nearestSeg: RoadSegFlat?,
        val blendedHeight: Float,
        val insideDeck: Boolean,
        val insideBridge: Boolean,
        val blendAlpha: Float
    )

    private fun evaluateRoadBlend(x: Float, z: Float, maxSearchRadius: Float): RoadBlendResult {
        val gx = floor((x + WORLD_HALF_X) / ROAD_GRID_CELL).toInt()
        val gz = floor((z + WORLD_HALF_Z) / ROAD_GRID_CELL).toInt()
        val cellSpan = if (maxSearchRadius > 45f) 1 else 1

        var bestDist = Float.MAX_VALUE
        var bestSeg: RoadSegFlat? = null
        var weightedY = 0f
        var totalWeight = 0f
        var insideDeck = false
        var insideBridge = false
        var bestDeckWeight = 0f

        for (dx in -cellSpan..cellSpan) {
            for (dz in -cellSpan..cellSpan) {
                val list = roadSpatialGrid[gridKey(gx + dx, gz + dz)] ?: continue
                for (i in 0 until list.size) {
                    val seg = list[i]
                    val abx = seg.bx - seg.ax
                    val abz = seg.bz - seg.az
                    val lenSq = abx * abx + abz * abz
                    if (lenSq < 0.0001f) continue
                    val t = (((x - seg.ax) * abx + (z - seg.az) * abz) / lenSq).coerceIn(0f, 1f)
                    val px = seg.ax + abx * t
                    val pz = seg.az + abz * t
                    val d = hypot(x - px, z - pz)

                    if (d < bestDist) {
                        bestDist = d
                        bestSeg = seg
                    }

                    val segY = seg.ay + (seg.by - seg.ay) * t
                    val shoulderMargin = seg.halfWidth + 2.6f
                    val outerInfluence = seg.halfWidth + maxSearchRadius

                    if (d <= shoulderMargin) {
                        insideDeck = true
                        if (seg.isBridge) insideBridge = true
                    }

                    if (d < outerInfluence) {
                        // Strong flat plateau weight inside road deck, smooth cosine falloff outside
                        val w = if (d <= seg.halfWidth) {
                            12.0f + (1f - d / seg.halfWidth.coerceAtLeast(1f)) * 4.0f
                        } else {
                            val u = ((d - seg.halfWidth) / maxSearchRadius).coerceIn(0f, 1f)
                            val s = 1f - u * u * (3f - 2f * u)
                            s * s * 6.0f
                        }
                        weightedY += segY * w
                        totalWeight += w
                        if (d <= outerInfluence) {
                            val localAlpha = if (d <= seg.halfWidth + 1.5f) {
                                1f
                            } else {
                                1f - smoothstep(seg.halfWidth + 1.5f, outerInfluence, d)
                            }
                            if (localAlpha > bestDeckWeight) bestDeckWeight = localAlpha
                        }
                    }
                }
            }
        }

        val blendedY = if (totalWeight > 0.0001f) weightedY / totalWeight else 0f
        return RoadBlendResult(
            nearestDist = bestDist,
            nearestSeg = bestSeg,
            blendedHeight = blendedY,
            insideDeck = insideDeck,
            insideBridge = insideBridge,
            blendAlpha = bestDeckWeight
        )
    }

    /**
     * Authoritative walkable/drivable elevation at (x, z) used by player feet, 4-wheel car physics,
     * bike physics, AI traffic, NPCs, buildings, trees, and grass.
     */
    fun getTerrainHeight(x: Float, z: Float): Float {
        if (x in METRO_MIN_X..METRO_MAX_X && z in METRO_MIN_Z..METRO_MAX_Z) {
            return 0f
        }
        val natH = getNaturalTerrainHeight(x, z)
        val rb = evaluateRoadBlend(x, z, 18f)
        if (rb.nearestSeg == null || rb.blendAlpha <= 0.001f) return natH

        // On an elevated sea/river bridge deck, return exact bridge deck surface (+0.08f)
        if (rb.insideBridge) {
            return rb.blendedHeight + 0.08f
        }
        // On paved ground road deck, return exact road asphalt surface (+0.08f)
        if (rb.insideDeck) {
            return rb.blendedHeight + 0.08f
        }
        // Smooth embankment blending into surrounding terrain
        return natH + ((rb.blendedHeight + 0.04f) - natH) * rb.blendAlpha
    }

    /**
     * Terrain mesh vertex height used ONLY when baking terrain chunks.
     * Deliberately recesses terrain cleanly (-0.34m) under ALL paved road decks & overlapping
     * junctions so dirt/sand/grass NEVER pokes through asphalt!
     */
    fun getTerrainHeightForMesh(x: Float, z: Float): Float {
        if (x in METRO_MIN_X..METRO_MAX_X && z in METRO_MIN_Z..METRO_MAX_Z) {
            return 0f
        }
        val natH = getNaturalTerrainHeight(x, z)
        val rb = evaluateRoadBlend(x, z, 22f)
        val seg = rb.nearestSeg ?: return natH
        if (rb.insideBridge && natH < WATER_LEVEL) {
            // Keep seafloor natural under elevated overseas sea bridges!
            return natH
        }
        if (rb.blendAlpha <= 0.001f) return natH

        // Under any road deck or junction overlap, push terrain vertex safely -0.34m below asphalt!
        val recess = if (rb.insideDeck) {
            -0.34f
        } else {
            val shoulderT = 1f - smoothstep(seg.halfWidth + 2.6f, seg.halfWidth + 10.0f, rb.nearestDist)
            -0.34f * shoulderT
        }
        val targetH = rb.blendedHeight + recess
        return natH + (targetH - natH) * rb.blendAlpha
    }

    fun getTerrainNormal(x: Float, z: Float, out: FloatArray) {
        val eps = 1.6f
        val hL = getTerrainHeight(x - eps, z)
        val hR = getTerrainHeight(x + eps, z)
        val hD = getTerrainHeight(x, z - eps)
        val hU = getTerrainHeight(x, z + eps)
        val nx = hL - hR
        val ny = 2f * eps
        val nz = hD - hU
        val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
        out[0] = nx / len
        out[1] = ny / len
        out[2] = nz / len
    }

    fun getTerrainNormal(x: Float, z: Float): Triple<Float, Float, Float> {
        val out = FloatArray(3)
        getTerrainNormal(x, z, out)
        return Triple(out[0], out[1], out[2])
    }

    fun getSainiMetroFlatWeight(x: Float, z: Float): Float {
        if (x in METRO_MIN_X..METRO_MAX_X && z in METRO_MIN_Z..METRO_MAX_Z) return 1f
        val dxM = max(0f, max(METRO_MIN_X - x, x - METRO_MAX_X))
        val dzM = max(0f, max(METRO_MIN_Z - z, z - METRO_MAX_Z))
        val d = sqrt(dxM * dxM + dzM * dzM)
        return 1f - smoothstep(0f, 180f, d)
    }

    fun getTerrainSurfaceColor(x: Float, z: Float, h: Float, normalY: Float, out: FloatArray) {
        val bw = getBiomeWeights(x, z)
        val coastDist = getCoastlineSignedDist(x, z)
        val slope = (1f - normalY).coerceIn(0f, 1f)

        // Lush GTA-6 rich multi-biome ground palette (lush green plains, alpine snow, warm sandstone desert, jungle loam, coastal sand)
        val snowBlend = smoothstep(52f, 95f, h)
        val sr = 0.56f + snowBlend * 0.36f
        val sg = 0.62f + snowBlend * 0.32f
        val sb = 0.66f + snowBlend * 0.30f

        var r = bw.metro * 0.28f + bw.snow * sr + bw.desert * 0.74f + bw.plains * 0.25f + bw.jungle * 0.14f + bw.coast * 0.30f + bw.keys * 0.23f
        var g = bw.metro * 0.44f + bw.snow * sg + bw.desert * 0.61f + bw.plains * 0.52f + bw.jungle * 0.41f + bw.coast * 0.54f + bw.keys * 0.52f
        var b = bw.metro * 0.24f + bw.snow * sb + bw.desert * 0.41f + bw.plains * 0.21f + bw.jungle * 0.17f + bw.coast * 0.26f + bw.keys * 0.25f

        // Golden beach sand near shorelines and shallow riverbanks
        if (coastDist < 190f && bw.snow < 0.45f) {
            val sandT = (1f - smoothstep(0f, 190f, coastDist)) * (1f - smoothstep(2.5f, 8.0f, h))
            r += (0.82f - r) * sandT
            g += (0.73f - g) * sandT
            b += (0.52f - b) * sandT
        }

        // Exposed rocky mountain cliffs on steep slopes
        val rockT = smoothstep(0.14f, 0.38f, slope)
        r += (0.44f - r) * rockT
        g += (0.42f - g) * rockT
        b += (0.40f - b) * rockT

        // Underwater seabed sand/dark rock tint
        if (h < WATER_LEVEL) {
            r *= 0.68f; g *= 0.72f; b *= 0.78f
        }

        out[0] = r.coerceIn(0.05f, 0.96f)
        out[1] = g.coerceIn(0.05f, 0.96f)
        out[2] = b.coerceIn(0.05f, 0.98f)
    }

    fun isOnBridgeDeck(x: Float, z: Float): Boolean {
        val rb = evaluateRoadBlend(x, z, 4f)
        return rb.insideBridge
    }

    fun isOnBridge(x: Float, z: Float): Boolean = isOnBridgeDeck(x, z)

    fun getWaterDepth(x: Float, z: Float): Float {
        if (isOnBridgeDeck(x, z)) return 0f
        val natH = getNaturalTerrainHeight(x, z)
        return (WATER_LEVEL - natH).coerceAtLeast(0f)
    }

    fun isUnderwater(x: Float, z: Float): Boolean = getWaterDepth(x, z) > 0.15f

    fun isWater(x: Float, z: Float): Boolean = isUnderwater(x, z)

    fun getOceanDistance(x: Float, z: Float): Float = getCoastlineSignedDist(x, z).coerceAtLeast(0f)

    fun isOnRegionalRoad(x: Float, z: Float, extraMargin: Float = 0.8f): Boolean {
        val segInfo = findNearestRoadSegment(x, z, 18f) ?: return false
        return segInfo.second <= segInfo.first.halfWidth + extraMargin
    }

    fun isOnRoad(x: Float, z: Float): Boolean = isOnRegionalRoad(x, z, 0.8f)

    fun isOnPavedRoad(x: Float, z: Float): Boolean {
        val segInfo = findNearestRoadSegment(x, z, 18f) ?: return false
        return segInfo.first.roadClass.isPaved && segInfo.second <= segInfo.first.halfWidth + 0.8f
    }

    fun getDistanceToRoad(x: Float, z: Float): Float {
        val segInfo = findNearestRoadSegment(x, z, 48f) ?: return 999f
        return (segInfo.second - segInfo.first.halfWidth).coerceAtLeast(0f)
    }

    fun findNearestRoadSegment(x: Float, z: Float, maxDist: Float = 260f): Pair<RoadSegFlat, Float>? {
        val gx = floor((x + WORLD_HALF_X) / ROAD_GRID_CELL).toInt()
        val gz = floor((z + WORLD_HALF_Z) / ROAD_GRID_CELL).toInt()
        val cellRadius = ((maxDist / ROAD_GRID_CELL).toInt() + 1).coerceAtMost(4)
        var bestSeg: RoadSegFlat? = null
        var bestDist = maxDist
        for (dx in -cellRadius..cellRadius) {
            for (dz in -cellRadius..cellRadius) {
                val list = roadSpatialGrid[gridKey(gx + dx, gz + dz)] ?: continue
                for (i in 0 until list.size) {
                    val seg = list[i]
                    val d = distPointToSeg2D(x, z, seg.ax, seg.az, seg.bx, seg.bz)
                    if (d < bestDist) {
                        bestDist = d
                        bestSeg = seg
                    }
                }
            }
        }
        return bestSeg?.let { it to bestDist }
    }

    /**
     * Fast O(1) spatial query returning all road segments overlapping a terrain chunk bounding box.
     */
    fun getRoadSegmentsInBounds(minX: Float, minZ: Float, maxX: Float, maxZ: Float): List<RoadSegFlat> {
        val gx0 = floor((minX + WORLD_HALF_X) / ROAD_GRID_CELL).toInt()
        val gx1 = floor((maxX + WORLD_HALF_X) / ROAD_GRID_CELL).toInt()
        val gz0 = floor((minZ + WORLD_HALF_Z) / ROAD_GRID_CELL).toInt()
        val gz1 = floor((maxZ + WORLD_HALF_Z) / ROAD_GRID_CELL).toInt()
        val out = ArrayList<RoadSegFlat>(24)
        val seen = HashSet<RoadSegFlat>(32)
        for (gx in gx0..gx1) {
            for (gz in gz0..gz1) {
                val list = roadSpatialGrid[gridKey(gx, gz)] ?: continue
                for (i in 0 until list.size) {
                    val seg = list[i]
                    if (seen.add(seg)) out.add(seg)
                }
            }
        }
        return out
    }

    fun getRegionLabel(x: Float, z: Float): String = getLocationLabel(x, z)

    fun getLocationLabel(x: Float, z: Float): String {
        if (x in METRO_MIN_X..METRO_MAX_X && z in METRO_MIN_Z..METRO_MAX_Z) {
            return when {
                z > CityWorldData.WATERFRONT_Z -> "Saini Waterfront Pier"
                x > 290f && z in -370f..-130f -> "Saini International Airport"
                x < -280f && z in -320f..-180f -> "Saini Army Base"
                x in -98f..-54f && z in 54f..98f -> "Emerald City Park"
                else -> "Saini Metro City"
            }
        }
        var nearestPoi: RegionalPoi? = null
        var nearestD = 1200f
        for (poi in regionalPois) {
            val d = hypot(x - poi.x, z - poi.z)
            if (d < nearestD) {
                nearestD = d
                nearestPoi = poi
            }
        }
        if (nearestPoi != null && nearestD < max(nearestPoi.radius * 4.5f, 450f)) {
            return nearestPoi.name
        }
        if (getWaterDepth(x, z) > 0.35f) {
            return "Arabian Sea & Straits"
        }
        return getBiomeAt(x, z).displayName
    }

    // =========================================================================
    // 5. UNIFIED-ELEVATION ROAD BUILDER (ELIMINATES ALL JUNCTION STEPS!)
    // =========================================================================

    private class SplineNode(
        val splineId: String,
        val roadClass: RoadClass,
        val halfWidth: Float,
        val x: Float,
        var y: Float,
        val z: Float,
        val isBridge: Boolean,
        val isPinnedMetro: Boolean
    )

    private fun buildUnifiedRoadSegments(): List<RoadSegFlat> {
        val splineChains = ArrayList<Pair<RoadSpline, ArrayList<SplineNode>>>(roadSplines.size)

        // Pass 1: Catmull-Rom smooth subdivision for every road spline (~48m segments for crisp curves & fast streaming)
        for (sp in roadSplines) {
            val ctrl = sp.points
            if (ctrl.size < 2) continue
            val nodes = ArrayList<SplineNode>(ctrl.size * 6)
            val segCount = if (sp.closedLoop) ctrl.size else ctrl.size - 1
            for (i in 0 until segCount) {
                val p0 = ctrl[(i - 1 + ctrl.size) % ctrl.size].takeIf { sp.closedLoop || i > 0 } ?: ctrl[0]
                val p1 = ctrl[i]
                val p2 = ctrl[(i + 1) % ctrl.size]
                val p3 = ctrl[(i + 2) % ctrl.size].takeIf { sp.closedLoop || i + 2 < ctrl.size } ?: ctrl[ctrl.size - 1]
                val chord = hypot(p2.first - p1.first, p2.second - p1.second)
                val subSteps = (chord / 52f).toInt().coerceIn(3, 36)

                for (s in 0 until subSteps) {
                    val t = s.toFloat() / subSteps.toFloat()
                    val (wx, wz) = catmullRom2D(p0, p1, p2, p3, t)
                    nodes.add(makeInitialSplineNode(sp, wx, wz))
                }
            }
            if (!sp.closedLoop) {
                val last = ctrl.last()
                nodes.add(makeInitialSplineNode(sp, last.first, last.second))
            }
            splineChains.add(sp to nodes)
        }

        // Pass 2: Bridge approach ramp smoothing — smoothly connect elevated sea/river bridges to shore ground roads!
        for ((sp, nodes) in splineChains) {
            if (nodes.size < 3) continue
            // Smooth elevations along each spline (6 passes of Laplacian smoothing keeping metro pins & bridge peaks)
            val copy = FloatArray(nodes.size)
            for (iter in 0 until 6) {
                for (i in nodes.indices) {
                    val n = nodes[i]
                    if (n.isPinnedMetro) {
                        copy[i] = 0f
                        continue
                    }
                    val prevIdx = if (i > 0) i - 1 else if (sp.closedLoop) nodes.lastIndex else 0
                    val nextIdx = if (i < nodes.lastIndex) i + 1 else if (sp.closedLoop) 0 else nodes.lastIndex
                    val avg = (nodes[prevIdx].y + n.y * 2f + nodes[nextIdx].y) * 0.25f
                    // Keep bridges above water level!
                    copy[i] = if (n.isBridge && getNaturalTerrainHeight(n.x, n.z) < WATER_LEVEL + 0.5f) {
                        max(avg, WATER_LEVEL + 3.4f)
                    } else {
                        avg
                    }
                }
                for (i in nodes.indices) nodes[i].y = copy[i]
            }
        }

        // Pass 3: CROSS-SPLINE JUNCTION HEIGHT UNIFICATION!
        // Any two road nodes from different splines within 38m snap to ONE exact unified elevation!
        val junctionNodes = ArrayList<SplineNode>()
        for ((_, nodes) in splineChains) {
            for (n in nodes) junctionNodes.add(n)
        }
        // Spatial bucket hash (cell = 40m) for fast O(N) cross-spline junction snapping
        val bucketCell = 40f
        val buckets = HashMap<Long, ArrayList<SplineNode>>(junctionNodes.size)
        for (n in junctionNodes) {
            val bx = floor((n.x + WORLD_HALF_X) / bucketCell).toInt()
            val bz = floor((n.z + WORLD_HALF_Z) / bucketCell).toInt()
            buckets.getOrPut(gridKey(bx, bz)) { ArrayList(4) }.add(n)
        }

        for (pass in 0 until 4) {
            for (n in junctionNodes) {
                if (n.isPinnedMetro) {
                    n.y = 0f
                    continue
                }
                val bx = floor((n.x + WORLD_HALF_X) / bucketCell).toInt()
                val bz = floor((n.z + WORLD_HALF_Z) / bucketCell).toInt()
                var sumY = n.y
                var count = 1f
                var hasPinned = false
                for (ox in -1..1) {
                    for (oz in -1..1) {
                        val bList = buckets[gridKey(bx + ox, bz + oz)] ?: continue
                        for (k in 0 until bList.size) {
                            val other = bList[k]
                            if (other.splineId == n.splineId) continue
                            val d = hypot(n.x - other.x, n.z - other.z)
                            if (d < 36f) {
                                if (other.isPinnedMetro) {
                                    hasPinned = true
                                }
                                val w = (1f - d / 36f) * 2.5f
                                sumY += other.y * w
                                count += w
                            }
                        }
                    }
                }
                if (hasPinned) {
                    n.y = 0f
                } else if (count > 1.01f) {
                    n.y = sumY / count
                }
            }
            // Re-smooth 2 passes along every spline so unified junctions ramp seamlessly into adjacent segments!
            for ((sp, nodes) in splineChains) {
                if (nodes.size < 3) continue
                val tmp = FloatArray(nodes.size)
                for (i in nodes.indices) {
                    val n = nodes[i]
                    if (n.isPinnedMetro) {
                        tmp[i] = 0f
                        continue
                    }
                    val prevIdx = if (i > 0) i - 1 else if (sp.closedLoop) nodes.lastIndex else 0
                    val nextIdx = if (i < nodes.lastIndex) i + 1 else if (sp.closedLoop) 0 else nodes.lastIndex
                    // Keep endpoint elevations locked during smoothing so junctions stay 100% flush!
                    val isEndpoint = !sp.closedLoop && (i == 0 || i == nodes.lastIndex)
                    tmp[i] = if (isEndpoint) n.y else (nodes[prevIdx].y * 0.22f + n.y * 0.56f + nodes[nextIdx].y * 0.22f)
                }
                for (i in nodes.indices) nodes[i].y = tmp[i]
            }
        }

        // Pass 4: Emit final flattened segments
        val out = ArrayList<RoadSegFlat>(4096)
        for ((sp, nodes) in splineChains) {
            val segCount = if (sp.closedLoop) nodes.size else nodes.size - 1
            for (i in 0 until segCount) {
                val a = nodes[i]
                val b = nodes[(i + 1) % nodes.size]
                val len = hypot(b.x - a.x, b.z - a.z)
                if (len < 0.5f) continue
                val isBridge = a.isBridge || b.isBridge
                out.add(
                    RoadSegFlat(
                        splineId = sp.id,
                        roadClass = sp.roadClass,
                        halfWidth = sp.halfWidth,
                        ax = a.x,
                        ay = a.y,
                        az = a.z,
                        bx = b.x,
                        by = b.y,
                        bz = b.z,
                        length = len,
                        isBridge = isBridge
                    )
                )
            }
        }
        return out
    }

    private fun makeInitialSplineNode(sp: RoadSpline, wx: Float, wz: Float): SplineNode {
        val inMetro = wx in METRO_MIN_X..METRO_MAX_X && wz in METRO_MIN_Z..METRO_MAX_Z
        val natY = getNaturalTerrainHeight(wx, wz)
        val overWater = natY < WATER_LEVEL + 0.6f
        val isBridge = sp.roadClass == RoadClass.SEA_BRIDGE || overWater
        val y = when {
            inMetro -> 0f
            overWater -> WATER_LEVEL + 4.4f
            sp.roadClass == RoadClass.SEA_BRIDGE -> max(natY, WATER_LEVEL + 4.4f)
            else -> natY
        }
        return SplineNode(
            splineId = sp.id,
            roadClass = sp.roadClass,
            halfWidth = sp.halfWidth,
            x = wx,
            y = y,
            z = wz,
            isBridge = isBridge,
            isPinnedMetro = inMetro
        )
    }

    private fun buildSparseRoadGrid(segments: List<RoadSegFlat>): HashMap<Long, ArrayList<RoadSegFlat>> {
        val map = HashMap<Long, ArrayList<RoadSegFlat>>(2048)
        for (seg in segments) {
            val pad = seg.halfWidth + 28f
            val minX = min(seg.ax, seg.bx) - pad
            val maxX = max(seg.ax, seg.bx) + pad
            val minZ = min(seg.az, seg.bz) - pad
            val maxZ = max(seg.az, seg.bz) + pad
            val gx0 = floor((minX + WORLD_HALF_X) / ROAD_GRID_CELL).toInt()
            val gx1 = floor((maxX + WORLD_HALF_X) / ROAD_GRID_CELL).toInt()
            val gz0 = floor((minZ + WORLD_HALF_Z) / ROAD_GRID_CELL).toInt()
            val gz1 = floor((maxZ + WORLD_HALF_Z) / ROAD_GRID_CELL).toInt()
            for (gx in gx0..gx1) {
                for (gz in gz0..gz1) {
                    map.getOrPut(gridKey(gx, gz)) { ArrayList(6) }.add(seg)
                }
            }
        }
        return map
    }

    // =========================================================================
    // 6. ROAD-CLEARED REGIONAL POIs (NO DHABA / HOTEL / GARAGE EVER ON A ROAD!)
    // =========================================================================

    private fun clearPoiOffRoad(rawX: Float, rawZ: Float, minClearance: Float = 38f): Pair<Float, Float> {
        var px = rawX
        var pz = rawZ
        for (attempt in 0 until 4) {
            val nearest = findNearestRoadSegment(px, pz, 95f) ?: break
            val seg = nearest.first
            val dist = nearest.second
            val required = seg.halfWidth + minClearance
            if (dist >= required) break
            // Project onto segment and push perpendicularly cleanly off the road shoulder!
            val abx = seg.bx - seg.ax
            val abz = seg.bz - seg.az
            val lenSq = (abx * abx + abz * abz).coerceAtLeast(0.001f)
            val t = (((px - seg.ax) * abx + (pz - seg.az) * abz) / lenSq).coerceIn(0f, 1f)
            val projX = seg.ax + abx * t
            val projZ = seg.az + abz * t
            var ox = px - projX
            var oz = pz - projZ
            val oLen = hypot(ox, oz)
            if (oLen < 0.1f) {
                // Perpendicular right-hand normal of segment
                val invLen = 1f / sqrt(lenSq)
                ox = -abz * invLen
                oz = abx * invLen
            } else {
                ox /= oLen
                oz /= oLen
            }
            px = projX + ox * (required + 6f)
            pz = projZ + oz * (required + 6f)
        }
        return px to pz
    }

    private fun buildRoadClearedPois(): List<RegionalPoi> {
        val raw = listOf(
            // Immediate Ring & Outskirts (near Saini Metro — cleanly off-road!)
            RegionalPoi("ring_garage", "Ring Road Auto Works", 360f, -710f, WorldBiome.VILLAGE_PLAINS, PoiCategory.GARAGE, 65f),
            RegionalPoi("nh01_petrol", "NH-01 Bharat Petrol", -540f, -690f, WorldBiome.VILLAGE_PLAINS, PoiCategory.PETROL_PUMP, 65f),
            RegionalPoi("metro_south_hotel", "Harbour View Grand Hotel", 165f, 745f, WorldBiome.SAINI_METRO, PoiCategory.DHABA, 70f),
            RegionalPoi("west_outskirt_dhaba", "Jaipur Highway Dhaba", -980f, -125f, WorldBiome.VILLAGE_PLAINS, PoiCategory.DHABA, 65f),
            RegionalPoi("east_outskirt_dhaba", "Emerald Valley Dhaba", 1120f, -210f, WorldBiome.VILLAGE_PLAINS, PoiCategory.DHABA, 65f),

            // Saini Khet Plains & Central Villages
            RegionalPoi("saini_khet_village", "Saini Khet Gaon", 1850f, -4320f, WorldBiome.VILLAGE_PLAINS, PoiCategory.TOWN, 130f),
            RegionalPoi("wind_farm_ridge", "Bharat Wind Farm", 3900f, -3300f, WorldBiome.VILLAGE_PLAINS, PoiCategory.LANDMARK, 140f),
            RegionalPoi("plains_dhaba", "Punjabi Tadka Highway Dhaba", -260f, -4680f, WorldBiome.VILLAGE_PLAINS, PoiCategory.DHABA, 75f),
            RegionalPoi("canal_petrol", "Kisan Highway Fuel Stop", 3320f, -7320f, WorldBiome.VILLAGE_PLAINS, PoiCategory.PETROL_PUMP, 70f),

            // Jammu & Himalayan Snow Peaks (Far North)
            RegionalPoi("jammu_gateway", "Jammu Foothill Town", 420f, -9480f, WorldBiome.SNOW_PEAKS, PoiCategory.TOWN, 160f),
            RegionalPoi("kashmir_valley", "Kashmir Pine Valley", -280f, -12280f, WorldBiome.SNOW_PEAKS, PoiCategory.TOWN, 180f),
            RegionalPoi("rohtang_pass", "Rohtang Snow Pass", -880f, -15280f, WorldBiome.SNOW_PEAKS, PoiCategory.LANDMARK, 150f),
            RegionalPoi("himalaya_dhaba", "Himalayan Chai & Dhaba", -1320f, -18280f, WorldBiome.SNOW_PEAKS, PoiCategory.DHABA, 80f),
            RegionalPoi("leh_summit", "Leh Mountain Airfield", -1320f, -26700f, WorldBiome.SNOW_PEAKS, PoiCategory.HELIPAD_DOCK, 180f),
            RegionalPoi("alpine_ski_resort", "Gulmarg Snow Resort", -7050f, -18620f, WorldBiome.SNOW_PEAKS, PoiCategory.LANDMARK, 170f),
            RegionalPoi("shivalik_outpost", "Shivalik Army Outpost", 4720f, -14880f, WorldBiome.SNOW_PEAKS, PoiCategory.MILITARY, 150f),
            RegionalPoi("kailash_monastery", "Cloud Peak Monastery", 6320f, -22480f, WorldBiome.SNOW_PEAKS, PoiCategory.LANDMARK, 160f),

            // Rajasthan Thar Desert (West)
            RegionalPoi("jaipur_gate", "Rajputana Fort Town", -6320f, -1220f, WorldBiome.THAR_DESERT, PoiCategory.TOWN, 170f),
            RegionalPoi("thar_oasis", "Surya Desert Oasis", -12520f, -2040f, WorldBiome.THAR_DESERT, PoiCategory.LANDMARK, 160f),
            RegionalPoi("desert_dhaba", "Marwar Royal Dhaba & Hotel", -3720f, -590f, WorldBiome.THAR_DESERT, PoiCategory.DHABA, 80f),
            RegionalPoi("salt_flat_speedway", "Rann Salt Flat Speedway", -14920f, -8340f, WorldBiome.THAR_DESERT, PoiCategory.LANDMARK, 200f),
            RegionalPoi("jaisalmer_citadel", "Golden Sand Citadel", -17480f, -5300f, WorldBiome.THAR_DESERT, PoiCategory.TOWN, 180f),
            RegionalPoi("thar_airbase", "Thar Desert Airbase", -10940f, -7920f, WorldBiome.THAR_DESERT, PoiCategory.MILITARY, 180f),
            RegionalPoi("desert_garage", "Dune 4x4 Custom Garage", -9320f, -1780f, WorldBiome.THAR_DESERT, PoiCategory.GARAGE, 80f),

            // Emerald Jungle & Saini River (East)
            RegionalPoi("saini_river_town", "Saini River Crossing", 6520f, -1540f, WorldBiome.JUNGLE_RIVER, PoiCategory.TOWN, 150f),
            RegionalPoi("emerald_temple", "Ancient Jungle Temple", 12140f, -1940f, WorldBiome.JUNGLE_RIVER, PoiCategory.LANDMARK, 160f),
            RegionalPoi("waterfall_lodge", "Cascading Falls Lodge", 13720f, -8320f, WorldBiome.JUNGLE_RIVER, PoiCategory.DHABA, 120f),
            RegionalPoi("rainforest_camp", "Canopy Safari Camp", 14920f, -4720f, WorldBiome.JUNGLE_RIVER, PoiCategory.LANDMARK, 140f),
            RegionalPoi("jungle_petrol", "Jungle Highway Fuel Hub", 9320f, -2220f, WorldBiome.JUNGLE_RIVER, PoiCategory.PETROL_PUMP, 75f),

            // Island 1: Saini Keys Island (Immediate South across Sea Bridge)
            RegionalPoi("keys_resort", "Saini Keys Grand Resort", 2320f, 4620f, WorldBiome.SAINI_KEYS, PoiCategory.TOWN, 160f),
            RegionalPoi("keys_lighthouse", "Coral Point Lighthouse", 3220f, 5520f, WorldBiome.SAINI_KEYS, PoiCategory.LANDMARK, 120f),
            RegionalPoi("keys_marina", "Keys Yacht Marina", -1120f, 4520f, WorldBiome.SAINI_KEYS, PoiCategory.HELIPAD_DOCK, 140f),
            RegionalPoi("keys_garage", "Island Customs Garage", 520f, 6780f, WorldBiome.SAINI_KEYS, PoiCategory.GARAGE, 80f),

            // Island 2: Goa Palma Mega-Island (SW GTA-6 Tropical City Island!)
            RegionalPoi("palma_downtown", "Goa Palma City Center", -11720f, 13480f, WorldBiome.KONKAN_COAST, PoiCategory.TOWN, 260f),
            RegionalPoi("palma_beach", "Palma Golden Strip", -6920f, 13480f, WorldBiome.KONKAN_COAST, PoiCategory.LANDMARK, 190f),
            RegionalPoi("palma_airport", "Goa Palma Int'l Airport", -15900f, 16660f, WorldBiome.KONKAN_COAST, PoiCategory.HELIPAD_DOCK, 240f),
            RegionalPoi("palma_circuit", "Leonida Grand Race Circuit", -14920f, 13920f, WorldBiome.KONKAN_COAST, PoiCategory.LANDMARK, 220f),
            RegionalPoi("palma_resort", "Sunset Palm Hotel", -18080f, 13880f, WorldBiome.KONKAN_COAST, PoiCategory.DHABA, 130f),
            RegionalPoi("palma_garage", "Palma Supercars Garage", -8120f, 10940f, WorldBiome.KONKAN_COAST, PoiCategory.GARAGE, 90f),

            // Island 3: Emerald Port Island (SE Deep-Sea Port & Harbor Island)
            RegionalPoi("emerald_port_city", "Emerald Harbour City", 11920f, 10940f, WorldBiome.SAINI_KEYS, PoiCategory.TOWN, 220f),
            RegionalPoi("emerald_docks", "Mega Container Port", 15520f, 17080f, WorldBiome.SAINI_KEYS, PoiCategory.HELIPAD_DOCK, 220f),
            RegionalPoi("emerald_bay_resort", "Turquoise Lagoon Resort", 17080f, 14080f, WorldBiome.SAINI_KEYS, PoiCategory.LANDMARK, 170f),
            RegionalPoi("emerald_petrol", "Port Highway Fuel Station", 8520f, 16280f, WorldBiome.SAINI_KEYS, PoiCategory.PETROL_PUMP, 85f),

            // Island 4: Vice Volcano Mega-Island (Far South!)
            RegionalPoi("vice_city_north", "Vice Bay Waterfront", -420f, 20740f, WorldBiome.SAINI_KEYS, PoiCategory.TOWN, 220f),
            RegionalPoi("vice_volcano_peak", "Mount Agni Volcano Peak", -420f, 25080f, WorldBiome.SAINI_KEYS, PoiCategory.LANDMARK, 240f),
            RegionalPoi("vice_airstrip", "Vice Southern Airstrip", 4320f, 27680f, WorldBiome.SAINI_KEYS, PoiCategory.HELIPAD_DOCK, 220f),
            RegionalPoi("vice_beach_hotel", "South Reef Luxury Hotel", -5080f, 27480f, WorldBiome.SAINI_KEYS, PoiCategory.DHABA, 140f),

            // Island 5: Western Naval Fortress Island
            RegionalPoi("west_naval_base", "INS Bharat Naval Base", -19480f, 5880f, WorldBiome.KONKAN_COAST, PoiCategory.MILITARY, 200f),
            RegionalPoi("west_fort_lighthouse", "Sindhudurg Sea Fort", -21480f, 4280f, WorldBiome.KONKAN_COAST, PoiCategory.LANDMARK, 160f),

            // Island 6: Eastern Mangrove Lagoon Island
            RegionalPoi("east_lagoon_village", "Mangrove Cove Village", 19680f, 2320f, WorldBiome.JUNGLE_RIVER, PoiCategory.TOWN, 160f),
            RegionalPoi("east_hidden_ruins", "Sunken Temple Lagoon", 19280f, 5680f, WorldBiome.JUNGLE_RIVER, PoiCategory.LANDMARK, 150f)
        )

        return raw.map { poi ->
            val (cx, cz) = clearPoiOffRoad(poi.x, poi.z, minClearance = 38f)
            poi.copy(x = cx, z = cz)
        }
    }

    // =========================================================================
    // 7. ROAD-GRAPH GPS PATHFINDING (SUPPORTS ALL MAINLAND & 6 BIG ISLANDS!)
    // =========================================================================

    private data class GpsNode(val x: Float, val z: Float)
    private data class GpsEdge(val to: Int, val cost: Float)

    private var gpsNodes: List<GpsNode> = emptyList()
    private var gpsAdj: Array<ArrayList<GpsEdge>> = emptyArray()

    private fun ensureGpsGraph() {
        if (gpsNodes.isNotEmpty()) return
        val nodes = ArrayList<GpsNode>(512)
        val adj = ArrayList<ArrayList<GpsEdge>>(512)

        fun addOrMergeNode(x: Float, z: Float): Int {
            for (i in nodes.indices) {
                if (hypot(nodes[i].x - x, nodes[i].z - z) < 120f) return i
            }
            nodes.add(GpsNode(x, z))
            adj.add(ArrayList(4))
            return nodes.lastIndex
        }

        fun connect(a: Int, b: Int, mult: Float) {
            if (a == b) return
            val d = hypot(nodes[a].x - nodes[b].x, nodes[a].z - nodes[b].z) * mult
            adj[a].add(GpsEdge(b, d))
            adj[b].add(GpsEdge(a, d))
        }

        for (sp in roadSplines) {
            val pts = sp.points
            if (pts.size < 2) continue
            val mult = when (sp.roadClass) {
                RoadClass.RING_EXPRESSWAY -> 0.90f
                RoadClass.HIGHWAY -> 1.0f
                RoadClass.SEA_BRIDGE -> 1.05f
                RoadClass.GHAT_MOUNTAIN -> 1.25f
                RoadClass.DIRT_TRAIL -> 1.45f
            }
            val ids = IntArray(pts.size) { i -> addOrMergeNode(pts[i].first, pts[i].second) }
            for (i in 0 until ids.size - 1) connect(ids[i], ids[i + 1], mult)
            if (sp.closedLoop && ids.size >= 3) connect(ids.last(), ids.first(), mult)
        }

        gpsNodes = nodes
        gpsAdj = adj.toTypedArray()
    }

    fun computeRoadRoute(startX: Float, startZ: Float, endX: Float, endZ: Float): List<Pair<Float, Float>> {
        ensureGpsGraph()
        if (gpsNodes.isEmpty()) return listOf(startX to startZ, endX to endZ)
        val directDist = hypot(endX - startX, endZ - startZ)
        if (directDist < 520f) return listOf(startX to startZ, endX to endZ)

        var startIdx = 0
        var endIdx = 0
        var bestS = Float.MAX_VALUE
        var bestE = Float.MAX_VALUE
        for (i in gpsNodes.indices) {
            val ds = hypot(gpsNodes[i].x - startX, gpsNodes[i].z - startZ)
            if (ds < bestS) { bestS = ds; startIdx = i }
            val de = hypot(gpsNodes[i].x - endX, gpsNodes[i].z - endZ)
            if (de < bestE) { bestE = de; endIdx = i }
        }
        if (startIdx == endIdx) return listOf(startX to startZ, endX to endZ)

        val n = gpsNodes.size
        val dist = FloatArray(n) { Float.MAX_VALUE }
        val prev = IntArray(n) { -1 }
        val visited = BooleanArray(n)
        dist[startIdx] = 0f

        for (step in 0 until n) {
            var u = -1
            var bestD = Float.MAX_VALUE
            for (i in 0 until n) {
                if (!visited[i] && dist[i] < bestD) {
                    bestD = dist[i]
                    u = i
                }
            }
            if (u == -1 || u == endIdx) break
            visited[u] = true
            for (edge in gpsAdj[u]) {
                val alt = dist[u] + edge.cost
                if (alt < dist[edge.to]) {
                    dist[edge.to] = alt
                    prev[edge.to] = u
                }
            }
        }

        if (prev[endIdx] == -1) return listOf(startX to startZ, endX to endZ)
        val rev = ArrayList<Pair<Float, Float>>(32)
        rev.add(endX to endZ)
        var cur = endIdx
        while (cur != -1) {
            rev.add(gpsNodes[cur].x to gpsNodes[cur].z)
            cur = prev[cur]
        }
        rev.add(startX to startZ)
        rev.reverse()
        return rev
    }

    fun computePolylineLengthMeters(pts: List<Pair<Float, Float>>): Float {
        var total = 0f
        for (i in 0 until pts.size - 1) {
            total += hypot(pts[i + 1].first - pts[i].first, pts[i + 1].second - pts[i].second)
        }
        return total
    }

    // =========================================================================
    // 8. CRISP 512x666 GTA-6 STYLE SATELLITE MAP BITMAP (50 KM x 65 KM)
    // =========================================================================
    @Volatile
    private var cachedSatelliteBitmap: Bitmap? = null

    fun getOrCreateSatelliteBitmap(): Bitmap {
        cachedSatelliteBitmap?.let { return it }
        synchronized(this) {
            cachedSatelliteBitmap?.let { return it }
            val bmp = generateSatelliteBitmap()
            cachedSatelliteBitmap = bmp
            return bmp
        }
    }

    private fun generateSatelliteBitmap(): Bitmap {
        val w = SAT_MAP_WIDTH
        val h = SAT_MAP_HEIGHT
        val pixels = IntArray(w * h)
        val stepX = WORLD_WIDTH / w.toFloat()
        val stepZ = WORLD_HEIGHT / h.toFloat()

        for (py in 0 until h) {
            val wz = -WORLD_HALF_Z + (py + 0.5f) * stepZ
            val rowOff = py * w
            for (px in 0 until w) {
                val wx = -WORLD_HALF_X + (px + 0.5f) * stepX
                val coastDist = getCoastlineSignedDist(wx, wz)
                val riverSd = getRiverSignedDist(wx, wz)
                val elev = getNaturalTerrainHeight(wx, wz)

                val col = if (elev < WATER_LEVEL || coastDist < 0f) {
                    // Rich GTA-6 turquoise coastal reefs -> cobalt blue -> deep ocean bathymetry!
                    val depth = max(WATER_LEVEL - elev, (-coastDist) * 0.008f)
                    when {
                        depth < 0.7f -> Color.rgb(42, 168, 184)   // Crystal turquoise shoal
                        depth < 2.2f -> Color.rgb(24, 118, 158)   // Reef blue-teal
                        depth < 6.0f -> Color.rgb(15, 76, 126)    // Shelf ocean blue
                        else -> Color.rgb(10, 44, 86)             // Deep Arabian Sea
                    }
                } else {
                    val bw = getBiomeWeights(wx, wz)
                    // Hillshade relief from NW sunlight
                    val eE = getNaturalTerrainHeight(wx + 95f, wz)
                    val eS = getNaturalTerrainHeight(wx, wz + 95f)
                    val shade = (1.0f + ((elev - eE) * 0.015f + (elev - eS) * 0.015f)).coerceIn(0.68f, 1.32f)

                    // Smooth multi-biome palette (lush greens, crisp alpine snow, warm sand, dark jungle, metro slate)
                    val rSnow = if (elev > 95f) 236f else 178f
                    val gSnow = if (elev > 95f) 244f else 192f
                    val bSnow = if (elev > 95f) 252f else 206f

                    var r = bw.metro * 72f + bw.snow * rSnow + bw.desert * 196f + bw.plains * 78f + bw.jungle * 36f + bw.coast * 92f + bw.keys * 64f
                    var g = bw.metro * 82f + bw.snow * gSnow + bw.desert * 168f + bw.plains * 134f + bw.jungle * 102f + bw.coast * 144f + bw.keys * 138f
                    var b = bw.metro * 96f + bw.snow * bSnow + bw.desert * 118f + bw.plains * 66f + bw.jungle * 52f + bw.coast * 86f + bw.keys * 80f

                    // Golden sandy beaches along shorelines
                    if (coastDist < 380f && bw.snow < 0.45f) {
                        val beachT = (1f - coastDist / 380f).coerceIn(0f, 1f)
                        r = r + (214f - r) * beachT * 0.72f
                        g = g + (192f - g) * beachT * 0.72f
                        b = b + (146f - b) * beachT * 0.72f
                    }
                    // Turquoise river band
                    if (riverSd < 45f) {
                        r = 26f; g = 132f; b = 158f
                    }

                    val rr = (r * shade).toInt().coerceIn(0, 255)
                    val gg = (g * shade).toInt().coerceIn(0, 255)
                    val bb = (b * shade).toInt().coerceIn(0, 255)
                    Color.rgb(rr, gg, bb)
                }
                pixels[rowOff + px] = col
            }
        }

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)

        // Bake crisp highways & sea bridges directly into the satellite overview for instant clarity!
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (sp in roadSplines) {
            if (sp.points.size < 2) continue
            val path = Path()
            for (i in sp.points.indices) {
                val mx = ((sp.points[i].first + WORLD_HALF_X) / WORLD_WIDTH) * w
                val my = ((sp.points[i].second + WORLD_HALF_Z) / WORLD_HEIGHT) * h
                if (i == 0) path.moveTo(mx, my) else path.lineTo(mx, my)
            }
            if (sp.closedLoop) path.close()
            paint.color = when (sp.roadClass) {
                RoadClass.SEA_BRIDGE -> Color.rgb(230, 240, 248)
                RoadClass.DIRT_TRAIL -> Color.rgb(168, 146, 114)
                else -> Color.rgb(218, 198, 158)
            }
            paint.strokeWidth = when (sp.roadClass) {
                RoadClass.SEA_BRIDGE -> 2.0f
                RoadClass.DIRT_TRAIL -> 1.0f
                else -> 1.6f
            }
            canvas.drawPath(path, paint)
        }
        return bmp
    }

    // =========================================================================
    // 9. MATH & NOISE HELPERS
    // =========================================================================

    private fun catmullRom2D(
        p0: Pair<Float, Float>,
        p1: Pair<Float, Float>,
        p2: Pair<Float, Float>,
        p3: Pair<Float, Float>,
        t: Float
    ): Pair<Float, Float> {
        val t2 = t * t
        val t3 = t2 * t
        val x = 0.5f * (
            (2f * p1.first) +
                (-p0.first + p2.first) * t +
                (2f * p0.first - 5f * p1.first + 4f * p2.first - p3.first) * t2 +
                (-p0.first + 3f * p1.first - 3f * p2.first + p3.first) * t3
            )
        val z = 0.5f * (
            (2f * p1.second) +
                (-p0.second + p2.second) * t +
                (2f * p0.second - 5f * p1.second + 4f * p2.second - p3.second) * t2 +
                (-p0.second + 3f * p1.second - 3f * p2.second + p3.second) * t3
            )
        return x to z
    }

    private fun distPointToSeg2D(px: Float, pz: Float, ax: Float, az: Float, bx: Float, bz: Float): Float {
        val abx = bx - ax
        val abz = bz - az
        val lenSq = abx * abx + abz * abz
        if (lenSq < 0.0001f) return hypot(px - ax, pz - az)
        val t = (((px - ax) * abx + (pz - az) * abz) / lenSq).coerceIn(0f, 1f)
        return hypot(px - (ax + abx * t), pz - (az + abz * t))
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (abs(edge1 - edge0) < 0.0001f) return if (x >= edge1) 1f else 0f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun hash2(ix: Int, iz: Int): Float {
        var h = ix * 374761393 + iz * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 65535f
    }

    private fun valueNoise2D(x: Float, z: Float): Float {
        val ix = floor(x).toInt()
        val iz = floor(z).toInt()
        val fx = x - ix
        val fz = z - iz
        val u = fx * fx * (3f - 2f * fx)
        val v = fz * fz * (3f - 2f * fz)
        val a = hash2(ix, iz)
        val b = hash2(ix + 1, iz)
        val c = hash2(ix, iz + 1)
        val d = hash2(ix + 1, iz + 1)
        return (a + (b - a) * u) + ((c + (d - c) * u) - (a + (b - a) * u)) * v
    }

    fun fbm2D(x: Float, z: Float, octaves: Int): Float {
        var amp = 0.5f
        var freq = 1f
        var sum = 0f
        var norm = 0f
        for (i in 0 until octaves) {
            sum += valueNoise2D(x * freq, z * freq) * amp
            norm += amp
            freq *= 2.04f
            amp *= 0.5f
        }
        return if (norm > 0f) sum / norm else 0f
    }
}

/**
 * Unified Biome & Surface Query Facade used by RealisticGrassSystem and environment systems.
 * Delegates directly to the 50km x 65km multi-island WorldMapData + downtown CityWorldData.
 */
object BiomeManager {
    fun isOnRoad(x: Float, z: Float): Boolean {
        if (x in WorldMapData.METRO_MIN_X..WorldMapData.METRO_MAX_X &&
            z in WorldMapData.METRO_MIN_Z..WorldMapData.METRO_MAX_Z
        ) {
            if (CityWorldData.getGrassZoneType(x, z) == GrassZoneType.NONE) {
                if (!CityWorldData.isInsideAirportZone(x, z) && !CityWorldData.isInsideArmyBaseZone(x, z)) {
                    for (rc in CityWorldData.roadCoords) {
                        if (abs(x - rc) < 7.45f || abs(z - rc) < 7.45f) return true
                    }
                }
            }
        }
        return WorldMapData.isOnRegionalRoad(x, z, extraMargin = 0.85f)
    }

    fun isInsideBuildingOrWater(x: Float, z: Float): Boolean {
        if (WorldMapData.getWaterDepth(x, z) > 0.12f) return true
        if (WorldTerrainChunkManager.isRegionalBlocked(x, z, 0.8f)) return true
        if (x in WorldMapData.METRO_MIN_X..WorldMapData.METRO_MAX_X &&
            z in WorldMapData.METRO_MIN_Z..WorldMapData.METRO_MAX_Z
        ) {
            if (CityWorldData.getGrassZoneType(x, z) == GrassZoneType.NONE) return true
        }
        return false
    }

    fun isBeachSurfZone(x: Float, z: Float): Boolean {
        val coast = WorldMapData.getCoastlineSignedDist(x, z)
        if (coast > -14f) return true
        val river = WorldMapData.getRiverSignedDist(x, z)
        return river < 6f
    }

    fun getTerrainNormal(x: Float, z: Float, out: FloatArray): FloatArray {
        WorldMapData.getTerrainNormal(x, z, out)
        return out
    }

    fun getTerrainHeight(x: Float, z: Float): Float = WorldMapData.getTerrainHeight(x, z)

    fun getRoadShoulderOffset(x: Float, z: Float): Float {
        val hit = WorldMapData.findNearestRoadSegment(x, z, maxDist = 26f) ?: return 999f
        val (seg, dist) = hit
        val edgeDist = dist - seg.halfWidth
        return if (edgeDist > 0f) edgeDist else -1f
    }

    fun getBiomeWeights(x: Float, z: Float): BiomeWeights = WorldMapData.getBiomeWeights(x, z)
}

