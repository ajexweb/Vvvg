package com.example.game.engine

import com.example.game.model.GroundSurface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class BuildingStyle {
    GLASS_SKYSCRAPER,
    APARTMENT_WINDOWS,
    RED_BRICK_HOUSE,
    FACTORY_INDUSTRIAL
}

enum class TreeKind {
    BROAD,
    PALM,
    PINE
}

enum class FurnitureKind {
    KING_BED,
    MODERN_SOFA,
    WOODEN_WARDROBE,
    REFRIGERATOR,
    TV_UNIT,
    KITCHEN_COUNTER,
    OFFICE_DESK_PC,
    FACTORY_CNC,
    CARGO_CRATE,
    OIL_DRUM
}

data class InteriorFurnitureItem(
    val kind: FurnitureKind,
    val localX: Float,
    val localZ: Float,
    val yawDeg: Float,
    val floorIdx: Int,
    val halfW: Float,
    val halfD: Float,
    val yOffset: Float = 0f,
    val propSize: Float = 1.1f
)

data class StaticPropObstacle(
    val x: Float,
    val z: Float,
    val halfW: Float,
    val halfD: Float,
    val height: Float = 1.6f,
    val isOilDrum: Boolean = false,
    val yawDeg: Float = 0f,
    val size: Float = 1.1f,
    val yFloor: Float = 0.04f
)

data class BuildingBlock(
    val x: Float,
    val z: Float,
    val width: Float,
    val depth: Float,
    val height: Float,
    val tintR: Float,
    val tintG: Float,
    val tintB: Float,
    val hasSetbackCrown: Boolean = true,
    val useGlassFacade: Boolean = false,
    val style: BuildingStyle = if (useGlassFacade) BuildingStyle.GLASS_SKYSCRAPER else BuildingStyle.APARTMENT_WINDOWS,
    val hasCompoundWall: Boolean = false,
    val variantSeed: Int = 0
)

enum class EnterableCategory {
    OFFICE_TOWER,
    MODERN_APARTMENT,
    RESIDENTIAL_HOUSE,
    LUXURY_VILLA,
    COMMERCIAL_SHOWROOM,
    INDUSTRIAL_FACTORY
}

enum class GateMechanism {
    HOUSE_SINGLE_HINGE,
    VILLA_DOUBLE_HINGE,
    APARTMENT_SLIDING,
    FACTORY_ROLLUP_SHUTTER
}

/**
 * Hand-controlled interactive gate supporting ALL 4 realistic mechanisms and
 * stop/leave-anywhere persistence (openProgress 0.0 = closed, 1.0 = fully open).
 */
data class InteractiveGate(
    val id: String,
    val name: String,
    val mechanism: GateMechanism,
    val x: Float,
    val y: Float,
    val z: Float,
    val width: Float,
    val height: Float,
    val facingDirZ: Float = 1f,
    var openProgress: Float = 0f,
    var isSolidThreshold: Float = 0.45f
) {
    fun distanceTo(px: Float, pz: Float): Float {
        val dx = px - x
        val dz = pz - z
        return sqrt(dx * dx + dz * dz)
    }

    val isBlocking: Boolean get() = openProgress < isSolidThreshold
}

data class EnterableBuilding(
    val id: String,
    val name: String,
    val centerX: Float,
    val centerZ: Float,
    val width: Float = 14f,
    val depth: Float = 14f,
    val floorHeight: Float = 4.2f,
    val accentR: Float,
    val accentG: Float,
    val accentB: Float,
    val isLuxuryMarble: Boolean = false,
    val category: EnterableCategory = EnterableCategory.OFFICE_TOWER,
    val gateMechanism: GateMechanism = GateMechanism.APARTMENT_SLIDING,
    val forcedFrontDirSign: Float = 0f,
    var doorOpenProgress: Float = 0f,
    var doorTargetOpen: Boolean = false
) {
    val halfW: Float get() = width * 0.5f
    val halfD: Float get() = depth * 0.5f
    val wallThick: Float get() = 0.55f

    // Realistic proportional doorway half-widths (no giant house gates!)
    val doorHalfWidth: Float
        get() = when (category) {
            EnterableCategory.INDUSTRIAL_FACTORY -> 2.40f
            EnterableCategory.COMMERCIAL_SHOWROOM -> 2.15f
            EnterableCategory.OFFICE_TOWER -> 1.65f
            EnterableCategory.LUXURY_VILLA -> 1.60f
            EnterableCategory.MODERN_APARTMENT -> 1.45f
            EnterableCategory.RESIDENTIAL_HOUSE -> 1.00f
        }

    val frontDirSign: Float
        get() = if (forcedFrontDirSign != 0f) forcedFrontDirSign else if (centerZ < 0f) 1f else -1f

    val frontZ: Float get() = centerZ + frontDirSign * halfD
    val backZ: Float get() = centerZ - frontDirSign * halfD

    // Realistic proportional gate heights
    val gateHeight: Float
        get() = when (category) {
            EnterableCategory.INDUSTRIAL_FACTORY -> 3.45f
            EnterableCategory.COMMERCIAL_SHOWROOM -> 3.05f
            EnterableCategory.OFFICE_TOWER -> 2.65f
            EnterableCategory.MODERN_APARTMENT -> 2.55f
            EnterableCategory.LUXURY_VILLA -> 2.50f
            EnterableCategory.RESIDENTIAL_HOUSE -> 2.30f
        }

    val gate: InteractiveGate = InteractiveGate(
        id = "gate_$id",
        name = "$name Gate",
        mechanism = gateMechanism,
        x = centerX,
        y = 0.14f,
        z = frontZ - frontDirSign * (wallThick * 0.5f),
        width = doorHalfWidth * 2f,
        height = gateHeight,
        facingDirZ = frontDirSign,
        openProgress = 0f
    )

    val stairMinX: Float get() = centerX + halfW - 3.0f
    val stairMaxX: Float get() = centerX + halfW - 0.65f
    val stairStartZ: Float get() = centerZ + frontDirSign * 2.8f
    val stairEndZ: Float get() = centerZ - frontDirSign * 4.4f

    val hasSecondFloor: Boolean
        get() = category != EnterableCategory.INDUSTRIAL_FACTORY && category != EnterableCategory.COMMERCIAL_SHOWROOM

    // Single source of truth for BOTH rendering AND solid physics colliders!
    // Every single furniture item sits flush against its inner wall (0.03m clearance) with distinct Floor 1 vs Floor 2 rooms.
    val furnitureItems: List<InteriorFurnitureItem> = buildFurnitureLayout()

    private fun buildFurnitureLayout(): List<InteriorFurnitureItem> {
        val f = frontDirSign
        val innerW = halfW - wallThick // 6.45m for 14m wide, 7.45m for 16m wide
        val innerD = halfD - wallThick // 6.45m for 14m deep, 6.95m for 15m deep
        val faceInYaw = if (f > 0f) 0f else 180f     // Back flush against Back wall (-f * innerD), faces front
        val faceBackYaw = if (f > 0f) 180f else 0f   // Back flush against Front wall (+f * innerD), faces back
        val wallGap = 0.03f

        fun backZ(itemHalfD: Float) = -f * (innerD - itemHalfD - wallGap)
        fun frontZ(itemHalfD: Float) = f * (innerD - itemHalfD - wallGap)
        fun leftX(itemHalfD: Float) = -(innerW - itemHalfD - wallGap)
        fun rightX(itemHalfD: Float) = (innerW - itemHalfD - wallGap)

        return when (category) {
            EnterableCategory.RESIDENTIAL_HOUSE -> listOf(
                // --- GROUND FLOOR: Living Room (front-left) + Modular Kitchen (back) ---
                // TV Unit flush against front-left inner wall
                InteriorFurnitureItem(FurnitureKind.TV_UNIT, -3.95f, frontZ(0.24f), faceBackYaw, 1, 1.02f, 0.24f),
                // Modern Sofa flush against interior room partition wall (localZ = f*1.65f) facing TV!
                InteriorFurnitureItem(FurnitureKind.MODERN_SOFA, -3.95f, f * 2.36f, faceInYaw, 1, 1.12f, 0.46f),
                // Wooden Wardrobe flush against back-left wall
                InteriorFurnitureItem(FurnitureKind.WOODEN_WARDROBE, -5.20f, backZ(0.31f), faceInYaw, 1, 0.82f, 0.31f),
                // Modular Kitchen Counter + Upper Cabinets flush against back wall
                InteriorFurnitureItem(FurnitureKind.KITCHEN_COUNTER, -0.45f, backZ(0.35f), faceInYaw, 1, 1.35f, 0.35f),
                // Stainless Double-Door Refrigerator right beside kitchen counter, flush against back wall
                InteriorFurnitureItem(FurnitureKind.REFRIGERATOR, 1.48f, backZ(0.38f), faceInYaw, 1, 0.42f, 0.38f),

                // --- 2ND FLOOR: Master Bedroom + Private Home Study (distinct layout!) ---
                // King Bed headboard flush against back wall
                InteriorFurnitureItem(FurnitureKind.KING_BED, -4.65f, backZ(1.075f), faceInYaw, 2, 1.00f, 1.075f),
                // Wardrobe flush against back wall next to bed
                InteriorFurnitureItem(FurnitureKind.WOODEN_WARDROBE, -1.60f, backZ(0.31f), faceInYaw, 2, 0.82f, 0.31f),
                // Study Office Desk & PC flush against front-left wall
                InteriorFurnitureItem(FurnitureKind.OFFICE_DESK_PC, -4.65f, frontZ(0.38f), faceBackYaw, 2, 0.76f, 0.38f),
                // Bedroom TV Unit flush against front wall facing the bed
                InteriorFurnitureItem(FurnitureKind.TV_UNIT, -1.45f, frontZ(0.24f), faceBackYaw, 2, 1.02f, 0.24f)
            )

            EnterableCategory.LUXURY_VILLA -> listOf(
                // --- GROUND FLOOR: Grand Marble Living Lounge + Chef's Kitchen ---
                // Sofa flush against Left wall facing center hall
                InteriorFurnitureItem(FurnitureKind.MODERN_SOFA, leftX(0.46f), f * 2.55f, 90f, 1, 0.46f, 1.12f),
                // Wide LED TV Unit flush against Front-Left wall
                InteriorFurnitureItem(FurnitureKind.TV_UNIT, -4.05f, frontZ(0.24f), faceBackYaw, 1, 1.02f, 0.24f),
                // Chef's Kitchen Counter flush against Back-Left wall
                InteriorFurnitureItem(FurnitureKind.KITCHEN_COUNTER, -3.90f, backZ(0.35f), faceInYaw, 1, 1.35f, 0.35f),
                // Stainless Refrigerator beside counter, flush against Back wall
                InteriorFurnitureItem(FurnitureKind.REFRIGERATOR, -1.96f, backZ(0.38f), faceInYaw, 1, 0.42f, 0.38f),
                // Second Lounge Sofa flush against Back-Center wall
                InteriorFurnitureItem(FurnitureKind.MODERN_SOFA, 0.65f, backZ(0.46f), faceInYaw, 1, 1.12f, 0.46f),

                // --- 2ND FLOOR: Royal Master Suite + Executive Study ---
                // King Bed centered against Back wall
                InteriorFurnitureItem(FurnitureKind.KING_BED, -3.25f, backZ(1.075f), faceInYaw, 2, 1.00f, 1.075f),
                // Walk-in Wardrobe #1 flush against Left wall
                InteriorFurnitureItem(FurnitureKind.WOODEN_WARDROBE, leftX(0.31f), -f * 4.35f, 90f, 2, 0.31f, 0.82f),
                // Walk-in Wardrobe #2 flush against Back wall right of bed
                InteriorFurnitureItem(FurnitureKind.WOODEN_WARDROBE, -0.45f, backZ(0.31f), faceInYaw, 2, 0.82f, 0.31f),
                // Executive PC Desk flush against Left wall near front window
                InteriorFurnitureItem(FurnitureKind.OFFICE_DESK_PC, leftX(0.38f), f * 3.55f, 90f, 2, 0.38f, 0.76f),
                // Master Lounge Sofa flush against Front wall
                InteriorFurnitureItem(FurnitureKind.MODERN_SOFA, -2.55f, frontZ(0.46f), faceBackYaw, 2, 1.12f, 0.46f)
            )

            EnterableCategory.MODERN_APARTMENT -> listOf(
                // --- GROUND FLOOR: Concierge Lobby + Compact Studio Kitchen ---
                InteriorFurnitureItem(FurnitureKind.MODERN_SOFA, leftX(0.46f), f * 2.85f, 90f, 1, 0.46f, 1.12f),
                InteriorFurnitureItem(FurnitureKind.TV_UNIT, -4.05f, frontZ(0.24f), faceBackYaw, 1, 1.02f, 0.24f),
                InteriorFurnitureItem(FurnitureKind.KITCHEN_COUNTER, -4.05f, backZ(0.35f), faceInYaw, 1, 1.35f, 0.35f),
                InteriorFurnitureItem(FurnitureKind.REFRIGERATOR, -2.12f, backZ(0.38f), faceInYaw, 1, 0.42f, 0.38f),
                InteriorFurnitureItem(FurnitureKind.OFFICE_DESK_PC, 0.55f, backZ(0.38f), faceInYaw, 1, 0.76f, 0.38f),

                // --- 2ND FLOOR: Modern 1BHK Apartment Residence ---
                InteriorFurnitureItem(FurnitureKind.KING_BED, -4.65f, backZ(1.075f), faceInYaw, 2, 1.00f, 1.075f),
                InteriorFurnitureItem(FurnitureKind.WOODEN_WARDROBE, -1.65f, backZ(0.31f), faceInYaw, 2, 0.82f, 0.31f),
                InteriorFurnitureItem(FurnitureKind.MODERN_SOFA, leftX(0.46f), f * 2.40f, 90f, 2, 0.46f, 1.12f),
                InteriorFurnitureItem(FurnitureKind.TV_UNIT, -3.95f, frontZ(0.24f), faceBackYaw, 2, 1.02f, 0.24f)
            )

            EnterableCategory.OFFICE_TOWER -> listOf(
                // --- GROUND FLOOR: Corporate Reception & Visitor Lounge ---
                InteriorFurnitureItem(FurnitureKind.OFFICE_DESK_PC, -3.85f, f * 2.85f, faceInYaw, 1, 0.76f, 0.38f),
                InteriorFurnitureItem(FurnitureKind.MODERN_SOFA, leftX(0.46f), -f * 2.20f, 90f, 1, 0.46f, 1.12f),
                InteriorFurnitureItem(FurnitureKind.TV_UNIT, -4.30f, backZ(0.24f), faceInYaw, 1, 1.02f, 0.24f),
                InteriorFurnitureItem(FurnitureKind.OFFICE_DESK_PC, -1.25f, backZ(0.38f), faceInYaw, 1, 0.76f, 0.38f),
                InteriorFurnitureItem(FurnitureKind.OFFICE_DESK_PC, 0.95f, backZ(0.38f), faceInYaw, 1, 0.76f, 0.38f),

                // --- 2ND FLOOR: Executive Engineering Studio (clean aligned workstations) ---
                InteriorFurnitureItem(FurnitureKind.OFFICE_DESK_PC, -4.85f, backZ(0.38f), faceInYaw, 2, 0.76f, 0.38f),
                InteriorFurnitureItem(FurnitureKind.OFFICE_DESK_PC, -2.50f, backZ(0.38f), faceInYaw, 2, 0.76f, 0.38f),
                InteriorFurnitureItem(FurnitureKind.OFFICE_DESK_PC, -0.15f, backZ(0.38f), faceInYaw, 2, 0.76f, 0.38f),
                InteriorFurnitureItem(FurnitureKind.OFFICE_DESK_PC, -4.60f, frontZ(0.38f), faceBackYaw, 2, 0.76f, 0.38f),
                InteriorFurnitureItem(FurnitureKind.MODERN_SOFA, -1.40f, frontZ(0.46f), faceBackYaw, 2, 1.12f, 0.46f)
            )

            EnterableCategory.COMMERCIAL_SHOWROOM -> listOf(
                // Billing Counters flush against Back-Left wall
                InteriorFurnitureItem(FurnitureKind.OFFICE_DESK_PC, -4.65f, backZ(0.38f), faceInYaw, 1, 0.76f, 0.38f),
                InteriorFurnitureItem(FurnitureKind.OFFICE_DESK_PC, -2.30f, backZ(0.38f), faceInYaw, 1, 0.76f, 0.38f),
                // Display TV flush against Left wall + Display Sofa facing it
                InteriorFurnitureItem(FurnitureKind.TV_UNIT, leftX(0.24f), f * 2.15f, 90f, 1, 0.24f, 1.02f),
                InteriorFurnitureItem(FurnitureKind.MODERN_SOFA, -4.20f, -f * 0.95f, faceInYaw, 1, 1.12f, 0.46f),
                // Stacked Cargo Crates flush against Back-Right corner
                InteriorFurnitureItem(FurnitureKind.CARGO_CRATE, rightX(0.55f) - 1.15f, backZ(0.55f), 4f, 1, 0.55f, 0.55f),
                InteriorFurnitureItem(FurnitureKind.CARGO_CRATE, rightX(0.55f), backZ(0.55f), -3f, 1, 0.55f, 0.55f),
                InteriorFurnitureItem(FurnitureKind.CARGO_CRATE, rightX(0.55f) - 0.58f, backZ(0.55f), 10f, 1, 0.52f, 0.52f, yOffset = 1.10f),
                InteriorFurnitureItem(FurnitureKind.CARGO_CRATE, rightX(0.55f), f * 1.40f, 0f, 1, 0.55f, 0.55f)
            )

            EnterableCategory.INDUSTRIAL_FACTORY -> listOf(
                // Heavy CNC Machines flush along Back & Left walls
                InteriorFurnitureItem(FurnitureKind.FACTORY_CNC, -5.15f, backZ(0.90f), faceInYaw, 1, 1.25f, 0.90f),
                InteriorFurnitureItem(FurnitureKind.FACTORY_CNC, -1.15f, backZ(0.90f), faceInYaw, 1, 1.25f, 0.90f),
                InteriorFurnitureItem(FurnitureKind.FACTORY_CNC, leftX(0.90f), f * 1.35f, 90f, 1, 0.90f, 1.25f),
                // Stacked Wooden Cargo Crates flush in Back-Right corner
                InteriorFurnitureItem(FurnitureKind.CARGO_CRATE, rightX(0.55f) - 1.15f, backZ(0.55f), 0f, 1, 0.55f, 0.55f),
                InteriorFurnitureItem(FurnitureKind.CARGO_CRATE, rightX(0.55f), backZ(0.55f), 5f, 1, 0.55f, 0.55f),
                InteriorFurnitureItem(FurnitureKind.CARGO_CRATE, rightX(0.55f) - 0.58f, backZ(0.55f), -6f, 1, 0.52f, 0.52f, yOffset = 1.10f),
                // Steel Oil Drums flush along Right wall
                InteriorFurnitureItem(FurnitureKind.OIL_DRUM, rightX(0.32f), f * 0.65f, 0f, 1, 0.32f, 0.32f),
                InteriorFurnitureItem(FurnitureKind.OIL_DRUM, rightX(0.32f), f * 1.38f, 35f, 1, 0.32f, 0.32f),
                InteriorFurnitureItem(FurnitureKind.OIL_DRUM, rightX(0.32f) - 0.72f, f * 0.65f, 70f, 1, 0.32f, 0.32f),
                InteriorFurnitureItem(FurnitureKind.OIL_DRUM, rightX(0.32f) - 0.72f, f * 1.38f, 115f, 1, 0.32f, 0.32f)
            )
        }
    }

    fun isInsideFootprint(x: Float, z: Float, margin: Float = 0f): Boolean {
        return abs(x - centerX) <= (halfW - margin) && abs(z - centerZ) <= (halfD - margin)
    }

    fun distanceToDoor(x: Float, z: Float): Float = gate.distanceTo(x, z)

    fun isOnStairBounds(x: Float, z: Float): Boolean {
        if (!hasSecondFloor) return false
        val minZ = minOf(stairStartZ, stairEndZ)
        val maxZ = maxOf(stairStartZ, stairEndZ)
        return x in stairMinX..stairMaxX && z in minZ..maxZ
    }

    fun stairProgressAt(z: Float): Float {
        return ((z - stairStartZ) / (stairEndZ - stairStartZ)).coerceIn(0f, 1f)
    }
}

data class RoadSegment(
    val x: Float,
    val z: Float,
    val length: Float,
    val width: Float,
    val isVertical: Boolean,
    val trimStart: Boolean = true,
    val trimEnd: Boolean = true
)

data class RoadIntersection(
    val x: Float,
    val z: Float,
    val sizeX: Float,
    val sizeZ: Float
)

data class TreeProp(
    val x: Float,
    val z: Float,
    val scale: Float,
    val seed: Int,
    val kind: TreeKind = TreeKind.BROAD
)

data class StreetLightProp(
    val x: Float,
    val z: Float,
    val dirX: Float,
    val dirZ: Float
)

data class TrafficSignalProp(
    val x: Float,
    val z: Float,
    val armDirX: Float,
    val armDirZ: Float,
    val axisXSignal: Boolean
)

data class BenchProp(
    val x: Float,
    val z: Float,
    val yawDeg: Float
)

data class KioskProp(
    val x: Float,
    val z: Float,
    val r: Float,
    val g: Float,
    val b: Float
)

data class ParkedCar(
    var x: Float,
    var z: Float,
    var yawDeg: Float,
    val r: Float,
    val g: Float,
    val b: Float,
    var wheelAngleDeg: Float = 0f
)

data class RoadsideShopProp(
    val id: String,
    val name: String,
    val x: Float,
    val z: Float,
    val width: Float = 6.2f,
    val depth: Float = 5.0f,
    val height: Float = 3.8f,
    val facingDirZ: Float = 1f,
    val accentR: Float,
    val accentG: Float,
    val accentB: Float,
    var shutterOpenProgress: Float = 0f,
    var shutterTargetOpen: Boolean = false
) {
    val frontZ: Float get() = z + facingDirZ * (depth * 0.5f)
    val gate: InteractiveGate = InteractiveGate(
        id = "gate_$id",
        name = "$name Shutter",
        mechanism = GateMechanism.FACTORY_ROLLUP_SHUTTER,
        x = x,
        y = 0.14f,
        z = frontZ,
        width = width - 0.52f,
        height = height - 0.65f,
        facingDirZ = facingDirZ,
        openProgress = 0f
    )
    fun distanceToFront(px: Float, pz: Float): Float = gate.distanceTo(px, pz)
    fun isInsideShop(px: Float, pz: Float): Boolean {
        return abs(px - x) <= (width * 0.5f - 0.25f) && abs(pz - z) <= (depth * 0.5f - 0.25f)
    }
}

data class PortContainerProp(
    val x: Float,
    val y: Float,
    val z: Float,
    val length: Float,
    val width: Float,
    val height: Float,
    val yawDeg: Float,
    val r: Float,
    val g: Float,
    val b: Float
)

data class CarAgent(
    var x: Float,
    var z: Float,
    val axisX: Boolean,
    val baseSpeed: Float,
    var currentSpeed: Float,
    val minBound: Float,
    val maxBound: Float,
    var dir: Float,
    var wheelAngleDeg: Float,
    val r: Float,
    val g: Float,
    val b: Float
)

data class NpcAgent(
    val id: Int,
    val isWoman: Boolean,
    val name: String,
    var x: Float,
    var z: Float,
    var yawRad: Float,
    val axisX: Boolean,
    val speed: Float,
    val minBound: Float,
    val maxBound: Float,
    var dir: Float,
    var walkPhase: Float,
    var pauseTimer: Float,
    val skinR: Float, val skinG: Float, val skinB: Float,
    val hairR: Float, val hairG: Float, val hairB: Float,
    val topR: Float, val topG: Float, val topB: Float,
    val bottomR: Float, val bottomG: Float, val bottomB: Float,
    val accentR: Float, val accentG: Float, val accentB: Float
)

data class CollectibleItem(
    val id: Int,
    val x: Float,
    val y: Float,
    val z: Float
)

data class BulletBikeProp(
    var x: Float,
    var z: Float,
    var yawDeg: Float,
    val label: String = "Royal Enfield Bullet 350",
    var wheelAngleDeg: Float = 0f
)

data class GrassTuftProp(
    val x: Float,
    val z: Float,
    val scale: Float,
    val yawDeg: Float,
    val seed: Int
)

data class ArmyBunkerProp(
    val x: Float,
    val z: Float,
    val width: Float,
    val depth: Float,
    val height: Float
)

data class BarbedFenceSegment(
    val x: Float,
    val z: Float,
    val length: Float,
    val isVertical: Boolean
)

data class HelipadProp(
    val x: Float,
    val z: Float,
    val size: Float,
    val hasHelicopter: Boolean
)

data class ArmyVehicleProp(
    val x: Float,
    val z: Float,
    val yawDeg: Float,
    val isTruck: Boolean
)

data class SkyCloudProp(
    var x: Float,
    val y: Float,
    var z: Float,
    val scaleX: Float,
    val scaleY: Float,
    val speedX: Float,
    val puffSeed: Int
)

data class FlyingBirdAgent(
    val centerX: Float,
    val centerZ: Float,
    val orbitRadius: Float,
    val altitude: Float,
    val angularSpeed: Float,
    var angle: Float,
    var wingPhase: Float,
    val scale: Float
)

data class ColonySurfacePad(
    val x: Float,
    val z: Float,
    val width: Float,
    val depth: Float,
    val isPaverCourtyard: Boolean = false
)

data class ColonyAlleySegment(
    val x: Float,
    val z: Float,
    val length: Float,
    val width: Float = 4.4f,
    val isVertical: Boolean
)

/**
 * Spatial 25m x 25m World Chunk (40x40 = 1,600 chunks) powering both 360-degree zone streaming
 * and ultra-fast O(1) spatial physics collision across the 10x expanded 1km x 1km metropolis!
 */
class WorldChunk(
    val cx: Int,
    val cz: Int,
    val centerX: Float,
    val centerZ: Float
) {
    val buildings = ArrayList<BuildingBlock>()
    val enterables = ArrayList<EnterableBuilding>()
    val staticObstacles = ArrayList<StaticPropObstacle>()
    val trees = ArrayList<TreeProp>()
    val streetLights = ArrayList<StreetLightProp>()
    val trafficSignals = ArrayList<TrafficSignalProp>()
    val benches = ArrayList<BenchProp>()
    val kiosks = ArrayList<KioskProp>()
    val parkedCars = ArrayList<ParkedCar>()
    val bulletBikes = ArrayList<BulletBikeProp>()
    val grassTufts = ArrayList<GrassTuftProp>()
    val armyBunkers = ArrayList<ArmyBunkerProp>()
    val barbedFences = ArrayList<BarbedFenceSegment>()
    val helipads = ArrayList<HelipadProp>()
    val armyVehicles = ArrayList<ArmyVehicleProp>()
    val roadSegments = ArrayList<RoadSegment>()
    val intersections = ArrayList<RoadIntersection>()
    val colonyPads = ArrayList<ColonySurfacePad>()
    val colonyAlleys = ArrayList<ColonyAlleySegment>()
    val collectibles = ArrayList<CollectibleItem>()
    val roadsideShops = ArrayList<RoadsideShopProp>()
    val portContainers = ArrayList<PortContainerProp>()
}

data class ResolvedPlayerPosition(
    val x: Float,
    val z: Float,
    val supportFloorY: Float,
    val insideBuilding: EnterableBuilding?,
    val floorNumber: Int
)

object CityWorldData {
    // 10x EXPANDED 1km x 1km Open-World Metropolis (-480..+480), Waterfront at Z = 410m!
    const val CITY_HALF_EXTENT = 480f
    const val WATERFRONT_Z = 410f
    const val CHUNK_SIZE = 25f
    const val GRID_MIN = -500f
    const val GRID_DIM = 40 // 40 x 40 = 1,600 spatial chunks

    val roadCoords = floatArrayOf(-412f, -328f, -248f, -172f, -104f, -48f, 0f, 48f, 104f, 172f, 248f, 328f, 412f)

    // Saini International Airport constants (East District)
    const val AIRPORT_RUNWAY_X = 355f
    const val AIRPORT_RUNWAY_Z = -250f
    const val AIRPORT_RUNWAY_WIDTH = 22f
    const val AIRPORT_RUNWAY_LENGTH = 176f
    const val AIRPORT_TOWER_X = 320f
    const val AIRPORT_TOWER_Z = -252f
    const val AIRPORT_HANGAR_X = 320f
    const val AIRPORT_HANGAR_Z = -196f
    const val AIRPORT_JET_X = 355f
    const val AIRPORT_JET_Z = -298f

    // Saini Military Airbase constants (West District)
    const val ARMY_BASE_CENTER_X = -348f
    const val ARMY_BASE_CENTER_Z = -248f

    private fun isInsideAirportZone(x: Float, z: Float, pad: Float = 0f): Boolean {
        return x in (268f - pad)..(442f + pad) && z in (-356f - pad)..(-142f + pad)
    }

    private fun isInsideArmyBaseZone(x: Float, z: Float, pad: Float = 0f): Boolean {
        return x in (-432f - pad)..(-268f + pad) && z in (-312f - pad)..(-186f + pad)
    }

    private fun isInsideEmeraldPark(x: Float, z: Float): Boolean {
        return x in -96f..-56f && z in 56f..96f
    }

    // Static snapshots of enterables & shops used to validate prop clearance during world build
    private val enterableFootprints: List<EnterableBuilding> = createEnterableBuildings()
    private val shopFootprints: List<RoadsideShopProp> = createRoadsideShops()

    val roadSegments: List<RoadSegment> = buildCleanRoadSegments()
    val intersections: List<RoadIntersection> = buildRoadIntersections()
    private val colonyLayout: Pair<List<ColonySurfacePad>, List<ColonyAlleySegment>> = buildColonyPadsAndAlleys()
    val colonyPads: List<ColonySurfacePad> = colonyLayout.first
    val colonyAlleys: List<ColonyAlleySegment> = colonyLayout.second
    val buildings: List<BuildingBlock> = buildSurroundingSkyscrapers()
    val exteriorStaticProps: List<StaticPropObstacle> = buildExteriorFactoryProps()
    val armyBunkers: List<ArmyBunkerProp> = buildArmyBunkers()
    val barbedFences: List<BarbedFenceSegment> = buildArmyBarbedFences()
    val helipads: List<HelipadProp> = buildHelipads()
    val armyVehicles: List<ArmyVehicleProp> = buildArmyVehicles()
    val portContainers: List<PortContainerProp> = buildPortContainers()
    val parkedCars: List<ParkedCar> = buildParkedCars()
    val bulletBikes: List<BulletBikeProp> = buildBulletBikes()
    val trees: List<TreeProp> = buildCityTrees()
    val streetLights: List<StreetLightProp> = buildStreetLights()
    val trafficSignals: List<TrafficSignalProp> = buildTrafficSignals()
    val benches: List<BenchProp> = buildParkAndStreetBenches()
    val kiosks: List<KioskProp> = buildCityKiosks()
    val grassTufts: List<GrassTuftProp> = buildGrassTufts()

    fun createRoadsideShops(): MutableList<RoadsideShopProp> = mutableListOf(
        // Downtown Central Market Shops
        RoadsideShopProp("shop_saini_mart", "Saini Super Mart", 16.5f, 36.5f, facingDirZ = -1f, accentR = 0.86f, accentG = 0.62f, accentB = 0.14f),
        RoadsideShopProp("shop_ajay_motors", "Ajay Auto Accessories", -16.5f, 36.5f, facingDirZ = -1f, accentR = 0.78f, accentG = 0.44f, accentB = 0.18f),
        RoadsideShopProp("shop_chai_bistro", "Saini Chai & Bakery", 35.5f, 14.5f, facingDirZ = -1f, accentR = 0.80f, accentG = 0.30f, accentB = 0.24f),
        RoadsideShopProp("shop_tech_hub", "Saini Mobile & PC", -35.5f, -35.5f, facingDirZ = 1f, accentR = 0.24f, accentG = 0.56f, accentB = 0.78f),
        // North Skyline Market
        RoadsideShopProp("shop_north_cafe", "Skyline Espresso Bar", 18.0f, -124.0f, facingDirZ = 1f, accentR = 0.72f, accentG = 0.48f, accentB = 0.24f),
        // West Suburban Corner Store
        RoadsideShopProp("shop_west_grocer", "Royal Villa Mart", -126.0f, 16.0f, facingDirZ = -1f, accentR = 0.28f, accentG = 0.64f, accentB = 0.40f),
        // East Industrial Hardware
        RoadsideShopProp("shop_east_tools", "Saini Steel & Tools", 126.0f, 66.0f, facingDirZ = -1f, accentR = 0.68f, accentG = 0.52f, accentB = 0.22f),
        // South Waterfront Harbor Shop
        RoadsideShopProp("shop_harbor_gear", "Harbor Marine Supply", 32.0f, 386.0f, facingDirZ = 1f, accentR = 0.20f, accentG = 0.62f, accentB = 0.46f)
    )

    fun createParkedCars(): MutableList<ParkedCar> = buildParkedCars().map { it.copy() }.toMutableList()
    fun createBulletBikes(): MutableList<BulletBikeProp> = buildBulletBikes().map { it.copy() }.toMutableList()

    val collectibles: List<CollectibleItem> = listOf(
        // Central Downtown
        CollectibleItem(0, 0f, 1.15f, 12.5f),
        CollectibleItem(1, 0f, 1.15f, -12.5f),
        CollectibleItem(2, 18f, 1.15f, -9.0f),
        CollectibleItem(3, -18f, 1.15f, -9.0f),
        CollectibleItem(4, 18f, 1.25f, -18f),
        CollectibleItem(5, 17f, 5.45f, -19.5f),
        CollectibleItem(6, -18f, 1.25f, -18f),
        CollectibleItem(7, -17f, 5.45f, -19.5f),
        CollectibleItem(8, 18f, 1.25f, 18f),
        CollectibleItem(9, 17f, 5.45f, 19.5f),
        CollectibleItem(10, -18f, 1.25f, 18f),
        CollectibleItem(11, -17f, 5.45f, 19.5f),
        CollectibleItem(12, 74f, 1.25f, -20f),
        CollectibleItem(13, 24f, 1.25f, 74f),
        // Emerald Park
        CollectibleItem(14, -76f, 1.15f, 76f),
        CollectibleItem(15, -66f, 1.15f, 66f),
        // North Tech District
        CollectibleItem(16, 0f, 1.15f, -172f),
        CollectibleItem(17, 22f, 1.25f, -222f),
        CollectibleItem(18, -22f, 1.25f, -222f),
        CollectibleItem(19, 0f, 1.15f, -328f),
        // West Royal Villas
        CollectibleItem(20, -172f, 1.15f, 0f),
        CollectibleItem(21, -220f, 1.25f, -22f),
        CollectibleItem(22, -248f, 1.15f, 48f),
        // East Industrial Zone
        CollectibleItem(23, 172f, 1.15f, 104f),
        CollectibleItem(24, 212f, 1.25f, 134f),
        CollectibleItem(25, 248f, 1.15f, 248f),
        // Waterfront & Pier
        CollectibleItem(26, 0f, 1.15f, 404f),
        CollectibleItem(27, 0f, 1.55f, 434f),
        CollectibleItem(28, -104f, 1.15f, 404f),
        CollectibleItem(29, 104f, 1.15f, 404f),
        // Airport & Army Base
        CollectibleItem(30, 310f, 1.15f, -250f),
        CollectibleItem(31, 355f, 1.15f, -210f),
        CollectibleItem(32, -312f, 1.15f, -248f),
        CollectibleItem(33, -348f, 1.15f, -238f)
    )

    fun createEnterableBuildings(): MutableList<EnterableBuilding> = mutableListOf(
        // 1. Central Corporate Glass Office Tower
        EnterableBuilding(
            id = "saini_grand_tower",
            name = "Saini Grand Office Tower",
            centerX = 18f, centerZ = -18f,
            accentR = 0.78f, accentG = 0.64f, accentB = 0.30f,
            isLuxuryMarble = true,
            category = EnterableCategory.OFFICE_TOWER,
            gateMechanism = GateMechanism.APARTMENT_SLIDING,
            forcedFrontDirSign = 1f
        ),
        // 2. Central Luxury Villa
        EnterableBuilding(
            id = "ajay_studio_hq",
            name = "Saini Luxury Villa",
            centerX = -18f, centerZ = -18f,
            accentR = 0.70f, accentG = 0.56f, accentB = 0.36f,
            isLuxuryMarble = true,
            category = EnterableCategory.LUXURY_VILLA,
            gateMechanism = GateMechanism.VILLA_DOUBLE_HINGE,
            forcedFrontDirSign = 1f
        ),
        // 3. Central Modern Family Residence (realistic single metal door!)
        EnterableBuilding(
            id = "metro_luxe_cafe",
            name = "Saini Family Residence",
            centerX = 18f, centerZ = 18f,
            accentR = 0.74f, accentG = 0.44f, accentB = 0.30f,
            isLuxuryMarble = false,
            category = EnterableCategory.RESIDENTIAL_HOUSE,
            gateMechanism = GateMechanism.HOUSE_SINGLE_HINGE,
            forcedFrontDirSign = -1f
        ),
        // 4. Emerald Park Modern Apartments
        EnterableBuilding(
            id = "emerald_pavilion",
            name = "Emerald Park Apartments",
            centerX = -18f, centerZ = 18f,
            accentR = 0.42f, accentG = 0.54f, accentB = 0.48f,
            isLuxuryMarble = false,
            category = EnterableCategory.MODERN_APARTMENT,
            gateMechanism = GateMechanism.APARTMENT_SLIDING,
            forcedFrontDirSign = -1f
        ),
        // 5. Commercial Mega Showroom
        EnterableBuilding(
            id = "downtown_mall",
            name = "Saini Mega Showroom",
            centerX = 74f, centerZ = -20f,
            accentR = 0.72f, accentG = 0.56f, accentB = 0.32f,
            isLuxuryMarble = true,
            category = EnterableCategory.COMMERCIAL_SHOWROOM,
            gateMechanism = GateMechanism.FACTORY_ROLLUP_SHUTTER,
            forcedFrontDirSign = 1f
        ),
        // 6. Downtown Precision CNC Factory
        EnterableBuilding(
            id = "pier_clubhouse",
            name = "Saini Precision Factory",
            centerX = 24f, centerZ = 74f,
            width = 16f, depth = 15f, floorHeight = 5.4f,
            accentR = 0.52f, accentG = 0.58f, accentB = 0.64f,
            isLuxuryMarble = false,
            category = EnterableCategory.INDUSTRIAL_FACTORY,
            gateMechanism = GateMechanism.FACTORY_ROLLUP_SHUTTER,
            forcedFrontDirSign = -1f
        ),
        // 7. North Skyline Tech Plaza (in expanded North District!)
        EnterableBuilding(
            id = "north_tech_plaza",
            name = "North Tech HQ Tower",
            centerX = 22f, centerZ = -222f,
            accentR = 0.46f, accentG = 0.60f, accentB = 0.74f,
            isLuxuryMarble = true,
            category = EnterableCategory.OFFICE_TOWER,
            gateMechanism = GateMechanism.APARTMENT_SLIDING,
            forcedFrontDirSign = -1f
        ),
        // 8. North Skyline Luxury Apartments
        EnterableBuilding(
            id = "north_skyline_apt",
            name = "Skyline Heights Suites",
            centerX = -22f, centerZ = -222f,
            accentR = 0.58f, accentG = 0.54f, accentB = 0.48f,
            isLuxuryMarble = false,
            category = EnterableCategory.MODERN_APARTMENT,
            gateMechanism = GateMechanism.APARTMENT_SLIDING,
            forcedFrontDirSign = -1f
        ),
        // 9. West Suburbs Royal Estate Villa
        EnterableBuilding(
            id = "west_royal_villa",
            name = "Royal Estate Villa",
            centerX = -220f, centerZ = -22f,
            accentR = 0.76f, accentG = 0.62f, accentB = 0.40f,
            isLuxuryMarble = true,
            category = EnterableCategory.LUXURY_VILLA,
            gateMechanism = GateMechanism.VILLA_DOUBLE_HINGE,
            forcedFrontDirSign = 1f
        ),
        // 10. East Industrial Mega Logistics Plant
        EnterableBuilding(
            id = "east_industrial_plant",
            name = "Saini Logistics Plant",
            centerX = 212f, centerZ = 134f,
            width = 16f, depth = 15f, floorHeight = 5.4f,
            accentR = 0.50f, accentG = 0.56f, accentB = 0.62f,
            isLuxuryMarble = false,
            category = EnterableCategory.INDUSTRIAL_FACTORY,
            gateMechanism = GateMechanism.FACTORY_ROLLUP_SHUTTER,
            forcedFrontDirSign = -1f
        )
    )

    fun createCars(): MutableList<CarAgent> = mutableListOf(
        // Central North-South & East-West Boulevards
        CarAgent(-2.7f, -210f, axisX = false, baseSpeed = 12.5f, currentSpeed = 12.5f, minBound = -400f, maxBound = 395f, dir = 1f, wheelAngleDeg = 0f, r = 0.78f, g = 0.16f, b = 0.18f),
        CarAgent( 2.7f,  160f, axisX = false, baseSpeed = 13.0f, currentSpeed = 13.0f, minBound = -400f, maxBound = 395f, dir = -1f, wheelAngleDeg = 0f, r = 0.14f, g = 0.40f, b = 0.78f),
        CarAgent(-2.7f,  260f, axisX = false, baseSpeed = 11.8f, currentSpeed = 11.8f, minBound = -400f, maxBound = 395f, dir = 1f, wheelAngleDeg = 0f, r = 0.86f, g = 0.86f, b = 0.88f),
        CarAgent( 2.7f, -340f, axisX = false, baseSpeed = 12.2f, currentSpeed = 12.2f, minBound = -400f, maxBound = 395f, dir = -1f, wheelAngleDeg = 0f, r = 0.22f, g = 0.25f, b = 0.30f),
        CarAgent(-220f,  2.7f, axisX = true,  baseSpeed = 11.4f, currentSpeed = 11.4f, minBound = -405f, maxBound = 405f, dir = 1f, wheelAngleDeg = 0f, r = 0.88f, g = 0.66f, b = 0.14f),
        CarAgent( 210f, -2.7f, axisX = true,  baseSpeed = 11.8f, currentSpeed = 11.8f, minBound = -405f, maxBound = 405f, dir = -1f, wheelAngleDeg = 0f, r = 0.88f, g = 0.90f, b = 0.92f),
        CarAgent( 100f,  2.7f, axisX = true,  baseSpeed = 10.6f, currentSpeed = 10.6f, minBound = -405f, maxBound = 405f, dir = 1f, wheelAngleDeg = 0f, r = 0.18f, g = 0.56f, b = 0.36f),
        CarAgent(-120f, -2.7f, axisX = true,  baseSpeed = 11.2f, currentSpeed = 11.2f, minBound = -405f, maxBound = 405f, dir = -1f, wheelAngleDeg = 0f, r = 0.56f, g = 0.22f, b = 0.26f),
        // Inner Ring Avenues (±48, ±104)
        CarAgent(45.3f, -150f, axisX = false, baseSpeed = 10.2f, currentSpeed = 10.2f, minBound = -395f, maxBound = 395f, dir = 1f, wheelAngleDeg = 0f, r = 0.16f, g = 0.58f, b = 0.42f),
        CarAgent(-45.3f, 120f, axisX = false, baseSpeed = 10.0f, currentSpeed = 10.0f, minBound = -395f, maxBound = 395f, dir = -1f, wheelAngleDeg = 0f, r = 0.24f, g = 0.26f, b = 0.30f),
        CarAgent(-150f, 45.3f, axisX = true,  baseSpeed = 10.4f, currentSpeed = 10.4f, minBound = -395f, maxBound = 395f, dir = 1f, wheelAngleDeg = 0f, r = 0.80f, g = 0.40f, b = 0.16f),
        CarAgent( 140f,-45.3f, axisX = true,  baseSpeed = 10.8f, currentSpeed = 10.8f, minBound = -395f, maxBound = 395f, dir = -1f, wheelAngleDeg = 0f, r = 0.44f, g = 0.26f, b = 0.62f),
        CarAgent(101.3f, -200f, axisX = false, baseSpeed = 11.0f, currentSpeed = 11.0f, minBound = -395f, maxBound = 395f, dir = 1f, wheelAngleDeg = 0f, r = 0.72f, g = 0.22f, b = 0.36f),
        CarAgent(-101.3f, 200f, axisX = false, baseSpeed = 10.6f, currentSpeed = 10.6f, minBound = -395f, maxBound = 395f, dir = -1f, wheelAngleDeg = 0f, r = 0.20f, g = 0.48f, b = 0.62f),
        CarAgent(-240f, 101.3f, axisX = true,  baseSpeed = 10.5f, currentSpeed = 10.5f, minBound = -395f, maxBound = 395f, dir = 1f, wheelAngleDeg = 0f, r = 0.76f, g = 0.72f, b = 0.66f),
        CarAgent( 240f,-101.3f, axisX = true,  baseSpeed = 11.2f, currentSpeed = 11.2f, minBound = -395f, maxBound = 395f, dir = -1f, wheelAngleDeg = 0f, r = 0.26f, g = 0.34f, b = 0.46f),
        // Outer Highway Corridors (±172, ±248, South Waterfront z=328)
        CarAgent(169.3f, 80f, axisX = false, baseSpeed = 12.0f, currentSpeed = 12.0f, minBound = -395f, maxBound = 395f, dir = 1f, wheelAngleDeg = 0f, r = 0.84f, g = 0.20f, b = 0.18f),
        CarAgent(-169.3f,-80f, axisX = false, baseSpeed = 11.8f, currentSpeed = 11.8f, minBound = -395f, maxBound = 395f, dir = -1f, wheelAngleDeg = 0f, r = 0.18f, g = 0.34f, b = 0.68f),
        CarAgent(245.3f, 180f, axisX = false, baseSpeed = 11.5f, currentSpeed = 11.5f, minBound = -120f, maxBound = 395f, dir = 1f, wheelAngleDeg = 0f, r = 0.42f, g = 0.46f, b = 0.50f),
        CarAgent(-245.3f, 140f, axisX = false, baseSpeed = 11.0f, currentSpeed = 11.0f, minBound = -120f, maxBound = 395f, dir = -1f, wheelAngleDeg = 0f, r = 0.82f, g = 0.58f, b = 0.14f),
        CarAgent(-180f, 245.3f, axisX = true, baseSpeed = 11.6f, currentSpeed = 11.6f, minBound = -395f, maxBound = 395f, dir = 1f, wheelAngleDeg = 0f, r = 0.16f, g = 0.52f, b = 0.46f),
        CarAgent( 180f,-245.3f, axisX = true, baseSpeed = 11.8f, currentSpeed = 11.8f, minBound = -240f, maxBound = 240f, dir = -1f, wheelAngleDeg = 0f, r = 0.84f, g = 0.86f, b = 0.88f),
        CarAgent(-160f, 325.3f, axisX = true, baseSpeed = 11.4f, currentSpeed = 11.4f, minBound = -395f, maxBound = 395f, dir = 1f, wheelAngleDeg = 0f, r = 0.24f, g = 0.44f, b = 0.72f),
        CarAgent( 160f, 330.7f, axisX = true, baseSpeed = 11.4f, currentSpeed = 11.4f, minBound = -395f, maxBound = 395f, dir = -1f, wheelAngleDeg = 0f, r = 0.72f, g = 0.26f, b = 0.22f)
    )

    fun createSkyClouds(): MutableList<SkyCloudProp> = mutableListOf(
        SkyCloudProp(-360f, 54f, -280f, 48f, 20f, 1.05f, 1),
        SkyCloudProp(-180f, 52f, -210f, 44f, 18f, 0.85f, 2),
        SkyCloudProp( -20f, 55f, -150f, 46f, 19f, 1.10f, 3),
        SkyCloudProp( 160f, 58f, -260f, 52f, 22f, 0.90f, 4),
        SkyCloudProp( 320f, 54f, -180f, 46f, 19f, 0.95f, 5),
        SkyCloudProp(-280f, 52f,   30f, 46f, 19f, 0.92f, 6),
        SkyCloudProp( -40f, 50f,   15f, 48f, 20f, 0.78f, 7),
        SkyCloudProp( 140f, 55f,   90f, 46f, 19f, 1.02f, 8),
        SkyCloudProp( 340f, 53f,  150f, 50f, 21f, 0.88f, 9),
        SkyCloudProp(-220f, 56f,  260f, 48f, 20f, 0.84f, 10),
        SkyCloudProp(  20f, 54f,  340f, 52f, 21f, 0.96f, 11),
        SkyCloudProp( 260f, 52f,  360f, 48f, 19f, 1.08f, 12)
    )

    fun createFlyingBirds(): MutableList<FlyingBirdAgent> = mutableListOf(
        FlyingBirdAgent(   0f,    0f, 20f, 17.5f, 0.42f, 0.0f, 0.0f, 1.05f),
        FlyingBirdAgent(   5f,   -6f, 24f, 19.0f, 0.38f, 1.4f, 1.4f, 0.95f),
        FlyingBirdAgent( -76f,   76f, 22f, 15.5f, 0.45f, 0.5f, 1.0f, 1.00f),
        FlyingBirdAgent( -72f,   80f, 25f, 17.0f, 0.40f, 2.2f, 2.3f, 0.92f),
        FlyingBirdAgent(   0f, -248f, 28f, 26.0f, 0.36f, 1.0f, 0.6f, 1.12f),
        FlyingBirdAgent(-248f,    0f, 22f, 16.5f, 0.44f, 2.8f, 1.8f, 1.00f),
        FlyingBirdAgent( 220f,  140f, 26f, 20.0f, 0.38f, 3.6f, 2.2f, 1.08f),
        FlyingBirdAgent(   0f,  410f, 28f, 14.5f, 0.48f, 0.8f, 0.5f, 1.10f),
        FlyingBirdAgent(  30f,  416f, 24f, 16.5f, 0.44f, 3.1f, 2.1f, 1.00f),
        FlyingBirdAgent(-348f, -248f, 26f, 20.5f, 0.36f, 1.5f, 0.9f, 1.18f),
        FlyingBirdAgent( 355f, -250f, 32f, 24.0f, 0.32f, 3.8f, 1.8f, 1.22f)
    )

    fun createNpcs(): MutableList<NpcAgent> {
        val list = mutableListOf<NpcAgent>()
        val skinTones = arrayOf(
            floatArrayOf(0.86f, 0.67f, 0.52f),
            floatArrayOf(0.76f, 0.55f, 0.40f),
            floatArrayOf(0.58f, 0.40f, 0.28f),
            floatArrayOf(0.90f, 0.74f, 0.62f)
        )
        val womanTops = arrayOf(
            floatArrayOf(0.82f, 0.26f, 0.38f),
            floatArrayOf(0.86f, 0.68f, 0.20f),
            floatArrayOf(0.18f, 0.60f, 0.64f),
            floatArrayOf(0.90f, 0.90f, 0.92f),
            floatArrayOf(0.54f, 0.30f, 0.68f)
        )
        val manTops = arrayOf(
            floatArrayOf(0.20f, 0.36f, 0.58f),
            floatArrayOf(0.22f, 0.24f, 0.28f),
            floatArrayOf(0.18f, 0.52f, 0.34f),
            floatArrayOf(0.74f, 0.36f, 0.20f),
            floatArrayOf(0.82f, 0.80f, 0.74f)
        )

        val sidewalkRoutes = listOf(
            // Central Downtown
            Triple(6.2f, -32f, false), Triple(-6.2f, 24f, false),
            Triple(6.2f, 32f, false), Triple(-6.2f, -28f, false),
            Triple(-30f, 6.2f, true), Triple(28f, -6.2f, true),
            Triple(34f, 6.2f, true), Triple(-24f, -6.2f, true),
            Triple(41.8f, -30f, false), Triple(54.2f, 24f, false),
            Triple(-41.8f, 48f, false), Triple(-54.2f, -24f, false),
            // Emerald City Park
            Triple(-76f, 64f, true), Triple(-70f, 84f, true),
            // North Tech District
            Triple(6.2f, -155f, false), Triple(-6.2f, -210f, false),
            Triple(6.2f, -285f, false), Triple(-41.8f, -220f, false),
            Triple(41.8f, -190f, false), Triple(18f, -178.2f, true),
            Triple(-24f, -241.8f, true),
            // West Suburbs & Royal Villas
            Triple(-140f, 6.2f, true), Triple(-215f, -6.2f, true),
            Triple(-241.8f, 32f, false), Triple(-178.2f, -45f, false),
            Triple(-165f, 54.2f, true), Triple(-290f, 6.2f, true),
            // East Commercial & Industrial
            Triple(140f, 6.2f, true), Triple(210f, 110.2f, true),
            Triple(165.8f, 145f, false), Triple(241.8f, 160f, false),
            Triple(120f, -54.2f, true), Triple(185f, 41.8f, true),
            // South Waterfront Promenade
            Triple(-24f, 403f, true), Triple(26f, 403f, true),
            Triple(-96f, 403f, true), Triple(96f, 403f, true),
            Triple(6.2f, 280f, false), Triple(-6.2f, 220f, false)
        )

        sidewalkRoutes.forEachIndexed { idx, (rx, rz, axisX) ->
            val isWoman = (idx % 2 == 1)
            val sk = skinTones[idx % skinTones.size]
            val top = if (isWoman) womanTops[idx % womanTops.size] else manTops[idx % manTops.size]
            val hair = when (idx % 4) {
                0 -> floatArrayOf(0.10f, 0.08f, 0.07f)
                1 -> floatArrayOf(0.28f, 0.16f, 0.09f)
                2 -> floatArrayOf(0.45f, 0.30f, 0.14f)
                else -> floatArrayOf(0.08f, 0.08f, 0.10f)
            }
            val bottom = if (isWoman && idx % 4 == 1) {
                floatArrayOf(0.14f, 0.16f, 0.22f)
            } else {
                floatArrayOf(0.18f, 0.24f, 0.36f)
            }
            val dir = if (idx % 2 == 0) 1f else -1f
            val minB = if (axisX) rx - 28f else rz - 28f
            val maxB = if (axisX) rx + 28f else rz + 28f
            list.add(
                NpcAgent(
                    id = idx,
                    isWoman = isWoman,
                    name = if (isWoman) "Citizen Priya #${idx + 1}" else "Citizen Rohan #${idx + 1}",
                    x = rx,
                    z = rz,
                    yawRad = if (axisX) (if (dir > 0) 1.57f else -1.57f) else (if (dir > 0) 0f else 3.14f),
                    axisX = axisX,
                    speed = 1.65f + (idx % 3) * 0.25f,
                    minBound = minB.coerceAtLeast(-440f),
                    maxBound = maxB.coerceAtMost(404f),
                    dir = dir,
                    walkPhase = idx * 1.1f,
                    pauseTimer = 0f,
                    skinR = sk[0], skinG = sk[1], skinB = sk[2],
                    hairR = hair[0], hairG = hair[1], hairB = hair[2],
                    topR = top[0], topG = top[1], topB = top[2],
                    bottomR = bottom[0], bottomG = bottom[1], bottomB = bottom[2],
                    accentR = if (isWoman) 0.88f else 0.76f,
                    accentG = if (isWoman) 0.45f else 0.60f,
                    accentB = if (isWoman) 0.62f else 0.25f
                )
            )
        }
        return list
    }

    /**
     * Builds the 40x40 (1,600) spatial Chunk Grid powering BOTH 360-degree zone streaming
     * AND O(1) spatial collision across the 10x expanded metropolis!
     */
    fun buildChunkGrid(
        enterables: List<EnterableBuilding>,
        shops: List<RoadsideShopProp> = emptyList()
    ): Array<Array<WorldChunk>> {
        val grid = Array(GRID_DIM) { cx ->
            Array(GRID_DIM) { cz ->
                val centerX = GRID_MIN + (cx + 0.5f) * CHUNK_SIZE
                val centerZ = GRID_MIN + (cz + 0.5f) * CHUNK_SIZE
                WorldChunk(cx, cz, centerX, centerZ)
            }
        }

        fun chunkAt(x: Float, z: Float): WorldChunk {
            val ix = ((x - GRID_MIN) / CHUNK_SIZE).toInt().coerceIn(0, GRID_DIM - 1)
            val iz = ((z - GRID_MIN) / CHUNK_SIZE).toInt().coerceIn(0, GRID_DIM - 1)
            return grid[ix][iz]
        }

        // Helper to register large multi-chunk footprints (buildings, long roads, fences) across all spanned chunks for O(1) physics
        fun forEachChunkInBounds(x: Float, z: Float, hw: Float, hd: Float, add: (WorldChunk) -> Unit) {
            val minIx = ((x - hw - GRID_MIN) / CHUNK_SIZE).toInt().coerceIn(0, GRID_DIM - 1)
            val maxIx = ((x + hw - GRID_MIN) / CHUNK_SIZE).toInt().coerceIn(0, GRID_DIM - 1)
            val minIz = ((z - hd - GRID_MIN) / CHUNK_SIZE).toInt().coerceIn(0, GRID_DIM - 1)
            val maxIz = ((z + hd - GRID_MIN) / CHUNK_SIZE).toInt().coerceIn(0, GRID_DIM - 1)
            for (ix in minIx..maxIx) {
                for (iz in minIz..maxIz) {
                    add(grid[ix][iz])
                }
            }
        }

        buildings.forEach { chunkAt(it.x, it.z).buildings.add(it) }
        enterables.forEach { chunkAt(it.centerX, it.centerZ).enterables.add(it) }
        shops.forEach { chunkAt(it.x, it.z).roadsideShops.add(it) }
        exteriorStaticProps.forEach { chunkAt(it.x, it.z).staticObstacles.add(it) }
        portContainers.forEach { chunkAt(it.x, it.z).portContainers.add(it) }
        trees.forEach { chunkAt(it.x, it.z).trees.add(it) }
        streetLights.forEach { chunkAt(it.x, it.z).streetLights.add(it) }
        trafficSignals.forEach { chunkAt(it.x, it.z).trafficSignals.add(it) }
        benches.forEach { chunkAt(it.x, it.z).benches.add(it) }
        kiosks.forEach { chunkAt(it.x, it.z).kiosks.add(it) }
        armyBunkers.forEach { chunkAt(it.x, it.z).armyBunkers.add(it) }
        barbedFences.forEach { fn ->
            val hw = if (fn.isVertical) 0.5f else fn.length * 0.5f
            val hd = if (fn.isVertical) fn.length * 0.5f else 0.5f
            forEachChunkInBounds(fn.x, fn.z, hw, hd) { ch ->
                if (!ch.barbedFences.contains(fn)) ch.barbedFences.add(fn)
            }
        }
        helipads.forEach { chunkAt(it.x, it.z).helipads.add(it) }
        armyVehicles.forEach { chunkAt(it.x, it.z).armyVehicles.add(it) }
        roadSegments.forEach { chunkAt(it.x, it.z).roadSegments.add(it) }
        intersections.forEach { chunkAt(it.x, it.z).intersections.add(it) }
        colonyPads.forEach { chunkAt(it.x, it.z).colonyPads.add(it) }
        colonyAlleys.forEach { chunkAt(it.x, it.z).colonyAlleys.add(it) }
        collectibles.forEach { chunkAt(it.x, it.z).collectibles.add(it) }
        grassTufts.forEach { chunkAt(it.x, it.z).grassTufts.add(it) }

        return grid
    }

    /**
     * Accurately classifies the ground surface beneath the player for surface-aware footsteps:
     * INDOOR_TILE, ASPHALT, DIRT_GRAVEL, and GRASS.
     */
    fun detectGroundSurface(
        x: Float,
        z: Float,
        insideBuilding: EnterableBuilding?,
        activeShops: List<RoadsideShopProp> = emptyList()
    ): GroundSurface {
        if (insideBuilding != null) return GroundSurface.INDOOR_TILE
        for (shop in activeShops) {
            if (shop.isInsideShop(x, z)) return GroundSurface.INDOOR_TILE
        }
        // Road + footpath/sidewalk corridors (half-width 5.0m road + 2.2m sidewalk = 7.2m)
        if (!isInsideAirportZone(x, z) && !isInsideArmyBaseZone(x, z)) {
            for (rc in roadCoords) {
                if (abs(x - rc) <= 7.2f || abs(z - rc) <= 7.2f) return GroundSurface.ASPHALT
            }
        }
        // Saini International Airport runway & apron
        if (x in 312f..372f && z in -344f..-154f) return GroundSurface.ASPHALT
        if (isInsideAirportZone(x, z)) return GroundSurface.DIRT_GRAVEL
        // Saini Military Airbase unpaved compound
        if (isInsideArmyBaseZone(x, z)) return GroundSurface.DIRT_GRAVEL
        // Port Container Yard industrial dock
        if (z in 360f..WATERFRONT_Z && (x in 56f..220f || x in -220f..-56f)) return GroundSurface.DIRT_GRAVEL
        // Waterfront boardwalk & wooden pier
        if (z >= WATERFRONT_Z - 7.5f) return GroundSurface.ASPHALT
        // Emerald City Park stays lush grass
        if (isInsideEmeraldPark(x, z)) return GroundSurface.GRASS
        // Urban dense colony pads & narrow alleys (patli galiyan) -> concrete/asphalt footsteps!
        if (x in -412f..412f && z in -412f..362f) return GroundSurface.ASPHALT
        return GroundSurface.GRASS
    }

    /**
     * Ultra-Fast O(1) Spatial 3D Solid Physics & Collision Solver:
     * Checks ONLY the 3x3 spatial chunks surrounding (nextX, nextZ) so performance stays locked at 60-120 FPS
     * across the entire 1km x 1km metropolis, including solid exterior cargo crates, oil drums,
     * compound walls, buildings, enterables, flush interior furniture, gates, vehicles, and NPCs!
     */
    fun resolvePlayerPhysics(
        nextX: Float,
        nextZ: Float,
        currentY: Float,
        enterableBuildings: List<EnterableBuilding>,
        movingCars: List<CarAgent>,
        npcs: List<NpcAgent>,
        playerRadius: Float = 0.52f,
        activeParkedCars: List<ParkedCar> = parkedCars,
        activeBulletBikes: List<BulletBikeProp> = bulletBikes,
        activeShops: List<RoadsideShopProp> = emptyList(),
        ignoreCar: ParkedCar? = null,
        ignoreBike: BulletBikeProp? = null,
        chunkGrid: Array<Array<WorldChunk>>? = null
    ): ResolvedPlayerPosition {
        var x = nextX.coerceIn(-CITY_HALF_EXTENT, CITY_HALF_EXTENT)
        var z = nextZ.coerceIn(-CITY_HALF_EXTENT, WATERFRONT_Z + 36f) // Walkable out onto Waterfront Pier

        // Keep out of ocean water unless on the central wooden Pier (|x| <= 7.2)
        if (z > WATERFRONT_Z + 1.5f && abs(x) > 6.8f) {
            z = WATERFRONT_Z + 1.5f
        }

        var supportY = if (z > WATERFRONT_Z && abs(x) <= 7.2f) 0.42f else 0f

        // Compute 3x3 chunk neighborhood around player for O(1) static collision queries!
        val minCx = ((x - GRID_MIN) / CHUNK_SIZE).toInt().coerceIn(0, GRID_DIM - 1) - 1
        val maxCx = minCx + 2
        val minCz = ((z - GRID_MIN) / CHUNK_SIZE).toInt().coerceIn(0, GRID_DIM - 1) - 1
        val maxCz = minCz + 2

        fun forNearbyChunks(action: (WorldChunk) -> Unit) {
            val grid = chunkGrid ?: return
            for (cx in minCx.coerceAtLeast(0)..maxCx.coerceAtMost(GRID_DIM - 1)) {
                for (cz in minCz.coerceAtLeast(0)..maxCz.coerceAtMost(GRID_DIM - 1)) {
                    action(grid[cx][cz])
                }
            }
        }

        // 1. Solid city buildings (skyscrapers, apartments, brick houses + compound walls, factories)
        //    + Exterior solid cargo crates & oil drums (NO walking inside boxes!)
        //    + Trees, streetlights, traffic signals, benches, kiosks, port containers, army bunkers, fences, vehicles
        if (chunkGrid != null) {
            forNearbyChunks { chunk ->
                for (b in chunk.buildings) {
                    val wallExtra = if (b.hasCompoundWall) 1.50f else 0.32f
                    if (abs(x - b.x) > b.width + 3.5f || abs(z - b.z) > b.depth + 3.5f) continue
                    pushOutAABB(x, z, b.x, b.z, b.width * 0.5f + wallExtra + playerRadius, b.depth * 0.5f + wallExtra + playerRadius).let {
                        x = it.first; z = it.second
                    }
                }
                if (currentY < 2.4f) {
                    for (obs in chunk.staticObstacles) {
                        if (currentY > obs.yFloor + obs.height) continue
                        if (abs(x - obs.x) > obs.halfW + 2.0f || abs(z - obs.z) > obs.halfD + 2.0f) continue
                        if (obs.isOilDrum) {
                            pushOutCircle(x, z, obs.x, obs.z, obs.halfW + playerRadius).let { x = it.first; z = it.second }
                        } else {
                            pushOutAABB(x, z, obs.x, obs.z, obs.halfW + playerRadius, obs.halfD + playerRadius).let { x = it.first; z = it.second }
                        }
                    }
                }
                if (currentY < 3.0f) {
                    for (t in chunk.trees) {
                        if (abs(x - t.x) > 2f || abs(z - t.z) > 2f) continue
                        pushOutCircle(x, z, t.x, t.z, 0.42f * t.scale + playerRadius).let { x = it.first; z = it.second }
                    }
                }
                if (currentY < 4.0f) {
                    for (sl in chunk.streetLights) {
                        if (abs(x - sl.x) > 1.5f || abs(z - sl.z) > 1.5f) continue
                        pushOutCircle(x, z, sl.x, sl.z, 0.22f + playerRadius).let { x = it.first; z = it.second }
                    }
                    for (ts in chunk.trafficSignals) {
                        if (abs(x - ts.x) > 1.5f || abs(z - ts.z) > 1.5f) continue
                        pushOutCircle(x, z, ts.x, ts.z, 0.26f + playerRadius).let { x = it.first; z = it.second }
                    }
                }
                if (currentY < 1.8f) {
                    for (bn in chunk.benches) {
                        if (abs(x - bn.x) > 2.5f || abs(z - bn.z) > 2.5f) continue
                        pushOutAABB(x, z, bn.x, bn.z, 1.05f + playerRadius, 0.52f + playerRadius).let { x = it.first; z = it.second }
                    }
                    for (k in chunk.kiosks) {
                        if (abs(x - k.x) > 3.5f || abs(z - k.z) > 3.5f) continue
                        pushOutAABB(x, z, k.x, k.z, 1.45f + playerRadius, 1.25f + playerRadius).let { x = it.first; z = it.second }
                    }
                }
                for (c in chunk.portContainers) {
                    if (currentY > c.y + c.height * 0.5f + 0.4f) continue
                    val hw = if (abs(c.yawDeg) > 45f) c.length * 0.5f else c.width * 0.5f
                    val hd = if (abs(c.yawDeg) > 45f) c.width * 0.5f else c.length * 0.5f
                    if (abs(x - c.x) > hw + 2f || abs(z - c.z) > hd + 2f) continue
                    pushOutAABB(x, z, c.x, c.z, hw + playerRadius, hd + playerRadius).let { x = it.first; z = it.second }
                }
                for (bk in chunk.armyBunkers) {
                    if (abs(x - bk.x) > bk.width + 2f || abs(z - bk.z) > bk.depth + 2f) continue
                    pushOutAABB(x, z, bk.x, bk.z, bk.width * 0.5f + playerRadius, bk.depth * 0.5f + playerRadius).let {
                        x = it.first; z = it.second
                    }
                }
                if (currentY < 3.4f) {
                    for (fn in chunk.barbedFences) {
                        val hw = if (fn.isVertical) 0.25f else fn.length * 0.5f
                        val hd = if (fn.isVertical) fn.length * 0.5f else 0.25f
                        if (abs(x - fn.x) > hw + 1.5f || abs(z - fn.z) > hd + 1.5f) continue
                        pushOutAABB(x, z, fn.x, fn.z, hw + playerRadius, hd + playerRadius).let { x = it.first; z = it.second }
                    }
                }
                if (currentY < 2.6f) {
                    for (av in chunk.armyVehicles) {
                        val hw = if (av.isTruck) 1.55f else 1.25f
                        val hd = if (av.isTruck) 3.10f else 2.20f
                        if (abs(x - av.x) > 4.5f || abs(z - av.z) > 4.5f) continue
                        pushOutAABB(x, z, av.x, av.z, hw + playerRadius, hd + playerRadius).let { x = it.first; z = it.second }
                    }
                }
            }
        } else {
            // Fallback full scan if chunkGrid was omitted
            for (b in buildings) {
                val wallExtra = if (b.hasCompoundWall) 1.50f else 0.32f
                if (abs(x - b.x) > b.width + 3.5f || abs(z - b.z) > b.depth + 3.5f) continue
                pushOutAABB(x, z, b.x, b.z, b.width * 0.5f + wallExtra + playerRadius, b.depth * 0.5f + wallExtra + playerRadius).let {
                    x = it.first; z = it.second
                }
            }
            for (obs in exteriorStaticProps) {
                if (abs(x - obs.x) > obs.halfW + 2f || abs(z - obs.z) > obs.halfD + 2f) continue
                pushOutAABB(x, z, obs.x, obs.z, obs.halfW + playerRadius, obs.halfD + playerRadius).let { x = it.first; z = it.second }
            }
        }

        // 2. Roadside Shops (dynamic shutter + flush interior desk/crates)
        for (shop in activeShops) {
            val hw = shop.width * 0.5f
            val hd = shop.depth * 0.5f
            if (abs(x - shop.x) > hw + 2f || abs(z - shop.z) > hd + 2f) continue
            val wt = 0.26f
            pushOutAABB(x, z, shop.x - hw + wt * 0.5f, shop.z, wt * 0.5f + playerRadius, hd + playerRadius).let { x = it.first; z = it.second }
            pushOutAABB(x, z, shop.x + hw - wt * 0.5f, shop.z, wt * 0.5f + playerRadius, hd + playerRadius).let { x = it.first; z = it.second }
            pushOutAABB(x, z, shop.x, shop.z - shop.facingDirZ * (hd - wt * 0.5f), hw + playerRadius, wt * 0.5f + playerRadius).let { x = it.first; z = it.second }
            // Shop interior flush desk & crates
            val backInnerZ = shop.z - shop.facingDirZ * (hd - wt - 0.04f)
            pushOutAABB(x, z, shop.x - 1.15f, backInnerZ + shop.facingDirZ * 0.38f, 0.76f + playerRadius, 0.38f + playerRadius).let { x = it.first; z = it.second }
            pushOutAABB(x, z, shop.x + 1.85f, backInnerZ + shop.facingDirZ * 0.54f, 0.54f + playerRadius, 0.54f + playerRadius).let { x = it.first; z = it.second }
            if (shop.gate.isBlocking) {
                pushOutAABB(x, z, shop.x, shop.frontZ, hw + playerRadius, wt + playerRadius).let { x = it.first; z = it.second }
            }
            if (shop.isInsideShop(x, z) && supportY < 0.16f) {
                supportY = 0.16f
            }
        }

        // 3. Parked & Moving Cars OBB collision (or climb on top!)
        for (pc in activeParkedCars) {
            if (pc === ignoreCar) continue
            if (abs(x - pc.x) > 3.5f || abs(z - pc.z) > 3.5f) continue
            val rad = pc.yawDeg * kotlin.math.PI.toFloat() / 180f
            val res = resolveCarOBB(x, z, currentY, pc.x, pc.z, cos(rad), sin(rad), playerRadius)
            x = res.first; z = res.second
            if (res.third > supportY) supportY = res.third
        }
        for (car in movingCars) {
            if (abs(x - car.x) > 3.5f || abs(z - car.z) > 3.5f) continue
            val cCos = if (car.axisX) 0f else car.dir
            val cSin = if (car.axisX) car.dir else 0f
            val res = resolveCarOBB(x, z, currentY, car.x, car.z, cCos, cSin, playerRadius)
            x = res.first; z = res.second
            if (res.third > supportY) supportY = res.third
        }

        // 4. NPC Pedestrians & Bullet Motorcycles
        if (currentY < 1.8f) {
            for (npc in npcs) {
                if (abs(x - npc.x) > 1.6f || abs(z - npc.z) > 1.6f) continue
                pushOutCircle(x, z, npc.x, npc.z, 0.36f + playerRadius).let { x = it.first; z = it.second }
            }
        }
        if (currentY < 1.5f) {
            for (bike in activeBulletBikes) {
                if (bike === ignoreBike) continue
                if (abs(x - bike.x) > 2.4f || abs(z - bike.z) > 2.4f) continue
                pushOutCircle(x, z, bike.x, bike.z, 0.85f + playerRadius).let { x = it.first; z = it.second }
            }
        }

        // 5. Airport Control Tower, Hangar & Parked Jetliner
        if (abs(x - AIRPORT_TOWER_X) < 6f && abs(z - AIRPORT_TOWER_Z) < 6f) {
            pushOutAABB(x, z, AIRPORT_TOWER_X, AIRPORT_TOWER_Z, 3.6f + playerRadius, 3.6f + playerRadius).let { x = it.first; z = it.second }
        }
        if (abs(x - AIRPORT_HANGAR_X) < 8f && abs(z - AIRPORT_HANGAR_Z) < 10f) {
            pushOutAABB(x, z, AIRPORT_HANGAR_X, AIRPORT_HANGAR_Z, 5.2f + playerRadius, 7.2f + playerRadius).let { x = it.first; z = it.second }
        }
        if (currentY < 3.6f && abs(x - AIRPORT_JET_X) < 12f && abs(z - AIRPORT_JET_Z) < 14f) {
            pushOutAABB(x, z, AIRPORT_JET_X, AIRPORT_JET_Z, 2.2f + playerRadius, 9.5f + playerRadius).let { x = it.first; z = it.second }
        }

        // 6. Enterable Multi-Floor Buildings: exterior walls, hand-controlled gates, flush interior furniture, and staircases
        var insideBuilding: EnterableBuilding? = null
        var floorNum = 0

        for (eb in enterableBuildings) {
            if (abs(x - eb.centerX) > eb.halfW + 1.6f || abs(z - eb.centerZ) > eb.halfD + 1.6f) continue

            val wHalf = eb.wallThick * 0.5f
            val onSecondFloor = eb.hasSecondFloor && currentY >= eb.floorHeight * 0.55f

            // Outer walls
            pushOutAABB(x, z, eb.centerX - eb.halfW + wHalf, eb.centerZ, wHalf + playerRadius, eb.halfD + playerRadius).let { x = it.first; z = it.second }
            pushOutAABB(x, z, eb.centerX + eb.halfW - wHalf, eb.centerZ, wHalf + playerRadius, eb.halfD + playerRadius).let { x = it.first; z = it.second }
            pushOutAABB(x, z, eb.centerX, eb.backZ + eb.frontDirSign * wHalf, eb.halfW + playerRadius, wHalf + playerRadius).let { x = it.first; z = it.second }

            val frontWallCenterZ = eb.frontZ - eb.frontDirSign * wHalf
            if (onSecondFloor) {
                pushOutAABB(x, z, eb.centerX, frontWallCenterZ, eb.halfW + playerRadius, wHalf + playerRadius).let { x = it.first; z = it.second }
            } else {
                val segWidth = (eb.halfW - eb.doorHalfWidth).coerceAtLeast(0.5f)
                pushOutAABB(x, z, eb.centerX - eb.doorHalfWidth - segWidth * 0.5f, frontWallCenterZ, segWidth * 0.5f + playerRadius, wHalf + playerRadius).let { x = it.first; z = it.second }
                pushOutAABB(x, z, eb.centerX + eb.doorHalfWidth + segWidth * 0.5f, frontWallCenterZ, segWidth * 0.5f + playerRadius, wHalf + playerRadius).let { x = it.first; z = it.second }
                if (eb.gate.isBlocking) {
                    pushOutAABB(x, z, eb.centerX, frontWallCenterZ, eb.doorHalfWidth + playerRadius, wHalf + playerRadius).let { x = it.first; z = it.second }
                }
            }

            // Ground floor interior living/kitchen partition wall inside Residential House (sofa sits flush against it!)
            if (eb.category == EnterableCategory.RESIDENTIAL_HOUSE && !onSecondFloor) {
                val partWallZ = eb.centerZ + eb.frontDirSign * 1.78f
                pushOutAABB(x, z, eb.centerX - 4.15f, partWallZ, 2.30f + playerRadius, 0.14f + playerRadius).let { x = it.first; z = it.second }
            }

            // Solid AABB colliders for EVERY piece of flush multi-angle furniture on the active floor
            val activeFloor = if (onSecondFloor) 2 else 1
            for (item in eb.furnitureItems) {
                if (item.floorIdx != activeFloor || item.yOffset > 0.4f) continue
                pushOutAABB(
                    x, z,
                    eb.centerX + item.localX,
                    eb.centerZ + item.localZ,
                    item.halfW + playerRadius,
                    item.halfD + playerRadius
                ).let { x = it.first; z = it.second }
            }

            if (eb.isInsideFootprint(x, z, margin = 0.25f)) {
                insideBuilding = eb
                val baseFloorY = 0.16f
                val secondFloorY = eb.floorHeight + 0.16f
                if (eb.isOnStairBounds(x, z)) {
                    val t = eb.stairProgressAt(z)
                    supportY = baseFloorY + t * eb.floorHeight
                    floorNum = if (t > 0.55f) 2 else 1
                } else if (eb.hasSecondFloor && currentY >= eb.floorHeight * 0.65f) {
                    supportY = secondFloorY
                    floorNum = 2
                } else {
                    supportY = baseFloorY
                    floorNum = 1
                }
            }
        }

        return ResolvedPlayerPosition(x, z, supportY, insideBuilding, floorNum)
    }

    private fun resolveCarOBB(
        px: Float, pz: Float, py: Float,
        cx: Float, cz: Float,
        forwardZCos: Float, forwardXSin: Float,
        playerRadius: Float
    ): Triple<Float, Float, Float> {
        val dx = px - cx
        val dz = pz - cz
        val localX = dx * forwardZCos - dz * forwardXSin
        val localZ = dx * forwardXSin + dz * forwardZCos
        val halfW = 1.02f + playerRadius
        val halfL = 2.28f + playerRadius

        if (abs(localX) < halfW && abs(localZ) < halfL) {
            if (py >= 1.25f) return Triple(px, pz, 1.48f)
            val overlapX = halfW - abs(localX)
            val overlapZ = halfL - abs(localZ)
            var adjLocalX = localX
            var adjLocalZ = localZ
            if (overlapX < overlapZ) {
                adjLocalX += if (localX >= 0f) overlapX else -overlapX
            } else {
                adjLocalZ += if (localZ >= 0f) overlapZ else -overlapZ
            }
            val newWorldX = cx + adjLocalX * forwardZCos + adjLocalZ * forwardXSin
            val newWorldZ = cz - adjLocalX * forwardXSin + adjLocalZ * forwardZCos
            return Triple(newWorldX, newWorldZ, 0f)
        }
        return Triple(px, pz, 0f)
    }

    private fun pushOutCircle(x: Float, z: Float, cx: Float, cz: Float, minDist: Float): Pair<Float, Float> {
        val dx = x - cx
        val dz = z - cz
        val distSq = dx * dx + dz * dz
        if (distSq < minDist * minDist && distSq > 0.00001f) {
            val dist = sqrt(distSq)
            return (cx + (dx / dist) * minDist) to (cz + (dz / dist) * minDist)
        }
        return x to z
    }

    private fun pushOutAABB(
        x: Float, z: Float,
        cx: Float, cz: Float,
        halfW: Float, halfD: Float
    ): Pair<Float, Float> {
        val dx = x - cx
        val dz = z - cz
        if (abs(dx) < halfW && abs(dz) < halfD) {
            val overlapX = halfW - abs(dx)
            val overlapZ = halfD - abs(dz)
            return if (overlapX < overlapZ) {
                (x + if (dx >= 0f) overlapX else -overlapX) to z
            } else {
                x to (z + if (dz >= 0f) overlapZ else -overlapZ)
            }
        }
        return x to z
    }

    /**
     * Generates clean road segments broken BETWEEN intersections across the 10x expanded city.
     * Leaves the East Airport Runway & West Army Base completely clear of crossing roads.
     */
    private fun buildCleanRoadSegments(): List<RoadSegment> {
        val list = mutableListOf<RoadSegment>()
        val xStops = floatArrayOf(-460f, -412f, -328f, -248f, -172f, -104f, -48f, 0f, 48f, 104f, 172f, 248f, 328f, 412f, 460f)
        val zStops = floatArrayOf(-460f, -412f, -328f, -248f, -172f, -104f, -48f, 0f, 48f, 104f, 172f, 248f, 328f, 404f)
        val halfRoad = 5.0f

        // North-South vertical segments
        for (rx in roadCoords) {
            for (i in 0 until zStops.lastIndex) {
                val midZ = (zStops[i] + zStops[i + 1]) * 0.5f
                if (isInsideAirportZone(rx, midZ, pad = 2f) || isInsideArmyBaseZone(rx, midZ, pad = 2f)) continue
                val startIsInter = (i > 0) && !isInsideAirportZone(rx, zStops[i], pad = 2f) && !isInsideArmyBaseZone(rx, zStops[i], pad = 2f)
                val endIsInter = (i + 1 < zStops.lastIndex) && !isInsideAirportZone(rx, zStops[i + 1], pad = 2f) && !isInsideArmyBaseZone(rx, zStops[i + 1], pad = 2f)
                val zStart = zStops[i] + if (startIsInter) halfRoad else 0f
                val zEnd = zStops[i + 1] - if (endIsInter) halfRoad else 0f
                val len = zEnd - zStart
                if (len > 4f) {
                    list.add(
                        RoadSegment(
                            x = rx, z = (zStart + zEnd) * 0.5f,
                            length = len, width = 10f, isVertical = true,
                            trimStart = startIsInter, trimEnd = endIsInter
                        )
                    )
                }
            }
        }

        // East-West horizontal segments (stop before Waterfront Z=410)
        val hCoords = floatArrayOf(-412f, -328f, -248f, -172f, -104f, -48f, 0f, 48f, 104f, 172f, 248f, 328f)
        for (rz in hCoords) {
            for (i in 0 until xStops.lastIndex) {
                val midX = (xStops[i] + xStops[i + 1]) * 0.5f
                if (isInsideAirportZone(midX, rz, pad = 2f) || isInsideArmyBaseZone(midX, rz, pad = 2f)) continue
                val startIsInter = (i > 0) && !isInsideAirportZone(xStops[i], rz, pad = 2f) && !isInsideArmyBaseZone(xStops[i], rz, pad = 2f)
                val endIsInter = (i + 1 < xStops.lastIndex) && !isInsideAirportZone(xStops[i + 1], rz, pad = 2f) && !isInsideArmyBaseZone(xStops[i + 1], rz, pad = 2f)
                val xStart = xStops[i] + if (startIsInter) halfRoad else 0f
                val xEnd = xStops[i + 1] - if (endIsInter) halfRoad else 0f
                val len = xEnd - xStart
                if (len > 4f) {
                    list.add(
                        RoadSegment(
                            x = (xStart + xEnd) * 0.5f, z = rz,
                            length = len, width = 10f, isVertical = false,
                            trimStart = startIsInter, trimEnd = endIsInter
                        )
                    )
                }
            }
        }
        return list
    }

    private fun buildRoadIntersections(): List<RoadIntersection> {
        val list = mutableListOf<RoadIntersection>()
        val hCoords = floatArrayOf(-412f, -328f, -248f, -172f, -104f, -48f, 0f, 48f, 104f, 172f, 248f, 328f)
        for (rx in roadCoords) {
            for (rz in hCoords) {
                if (isInsideAirportZone(rx, rz, pad = 2f) || isInsideArmyBaseZone(rx, rz, pad = 2f)) continue
                list.add(RoadIntersection(rx, rz, 10.04f, 10.04f))
            }
        }
        return list
    }

    /**
     * Generates realistic urban concrete/paver colony pads (eliminating empty green grass blocks!)
     * and walkable/rideable narrow paved colony alleys ("patli galiyan", 4.4m wide) linking main roads.
     */
    private fun buildColonyPadsAndAlleys(): Pair<List<ColonySurfacePad>, List<ColonyAlleySegment>> {
        val pads = mutableListOf<ColonySurfacePad>()
        val alleys = mutableListOf<ColonyAlleySegment>()
        val xBounds = floatArrayOf(-412f, -328f, -248f, -172f, -104f, -48f, 0f, 48f, 104f, 172f, 248f, 328f, 412f)
        val zBounds = floatArrayOf(-412f, -328f, -248f, -172f, -104f, -48f, 0f, 48f, 104f, 172f, 248f, 328f, 396f)
        val roadMargin = 7.12f // 5.0m half-road + 2.12m sidewalk

        fun alleyIntersectsLandmark(ax: Float, az: Float, hw: Float, hd: Float): Boolean {
            for (eb in enterableFootprints) {
                if (abs(ax - eb.centerX) < hw + eb.halfW + 1.2f && abs(az - eb.centerZ) < hd + eb.halfD + 1.2f) return true
            }
            for (shop in shopFootprints) {
                if (abs(ax - shop.x) < hw + shop.width * 0.5f + 1.0f && abs(az - shop.z) < hd + shop.depth * 0.5f + 1.0f) return true
            }
            return false
        }

        for (bx in 0 until xBounds.lastIndex) {
            val x0 = xBounds[bx] + roadMargin
            val x1 = xBounds[bx + 1] - roadMargin
            val blockW = x1 - x0
            if (blockW < 18f) continue

            for (bz in 0 until zBounds.lastIndex) {
                val z0 = zBounds[bz] + roadMargin
                val z1 = zBounds[bz + 1] - roadMargin
                val blockD = z1 - z0
                if (blockD < 18f) continue

                val midX = (x0 + x1) * 0.5f
                val midZ = (z0 + z1) * 0.5f
                if (isInsideAirportZone(midX, midZ, pad = 10f) || isInsideArmyBaseZone(midX, midZ, pad = 10f)) continue
                if (isInsideEmeraldPark(midX, midZ)) continue
                if (midZ > 356f) continue

                // Paved urban colony ground pad so blocks look like real GTA neighborhoods instead of bare grass
                val isDowntownOrResidential = abs(midX) < 180f || midX < -104f
                pads.add(
                    ColonySurfacePad(
                        x = midX, z = midZ,
                        width = blockW - 0.12f,
                        depth = blockD - 0.12f,
                        isPaverCourtyard = isDowntownOrResidential && ((bx + bz) % 2 == 0)
                    )
                )

                // Narrow paved colony alleys ("patli galiyan") cutting cleanly through the colony block
                val alleyW = 4.4f
                if (blockW >= 32f && !alleyIntersectsLandmark(midX, midZ, alleyW * 0.5f, blockD * 0.5f)) {
                    alleys.add(ColonyAlleySegment(midX, midZ, blockD + 0.16f, alleyW, isVertical = true))
                }
                if (blockD >= 32f && !alleyIntersectsLandmark(midX, midZ, blockW * 0.5f, alleyW * 0.5f)) {
                    alleys.add(ColonyAlleySegment(midX, midZ, blockW + 0.16f, alleyW, isVertical = false))
                }
            }
        }
        return pads to alleys
    }

    /**
     * Procedurally populates EVERY urban colony block across the 10x expanded 1km x 1km metropolis
     * with dense GTA-style multi-building clusters (3-4 buildings per block!) separated cleanly by
     * walkable narrow paved colony alleys ("patli galiyan")!
     */
    private fun buildSurroundingSkyscrapers(): List<BuildingBlock> {
        val list = mutableListOf<BuildingBlock>()
        val xBounds = floatArrayOf(-412f, -328f, -248f, -172f, -104f, -48f, 0f, 48f, 104f, 172f, 248f, 328f, 412f)
        val zBounds = floatArrayOf(-412f, -328f, -248f, -172f, -104f, -48f, 0f, 48f, 104f, 172f, 248f, 328f, 396f)
        val roadMargin = 7.8f

        fun conflictsWithLandmarkOrExisting(cx: Float, cz: Float, hw: Float, hd: Float): Boolean {
            if (isInsideAirportZone(cx, cz, pad = 16f) || isInsideArmyBaseZone(cx, cz, pad = 16f)) return true
            if (cx in -102f..-50f && cz in 50f..102f) return true // Emerald City Park
            if (cz > 356f) return true // Waterfront boardwalk & container docks
            for (eb in enterableFootprints) {
                if (abs(cx - eb.centerX) < hw + eb.halfW + 3.6f && abs(cz - eb.centerZ) < hd + eb.halfD + 3.6f) return true
            }
            for (shop in shopFootprints) {
                if (abs(cx - shop.x) < hw + shop.width * 0.5f + 3.2f && abs(cz - shop.z) < hd + shop.depth * 0.5f + 3.2f) return true
            }
            for (al in colonyAlleys) {
                val alHw = if (al.isVertical) al.width * 0.5f + 0.55f else al.length * 0.5f
                val alHd = if (al.isVertical) al.length * 0.5f else al.width * 0.5f + 0.55f
                if (abs(cx - al.x) < hw + alHw && abs(cz - al.z) < hd + alHd) return true
            }
            return false
        }

        fun hash(a: Int, b: Int, c: Int): Int {
            var h = a * 374761393 + b * 668265263 + c * 2147483647
            h = (h xor (h ushr 13)) * 1274126177
            return (h xor (h ushr 16)) and 0x7FFFFFFF
        }

        val glassTints = arrayOf(
            floatArrayOf(0.88f, 0.93f, 0.98f),
            floatArrayOf(0.84f, 0.88f, 0.92f),
            floatArrayOf(0.92f, 0.94f, 0.92f),
            floatArrayOf(0.94f, 0.91f, 0.86f)
        )
        val aptTints = arrayOf(
            floatArrayOf(0.92f, 0.90f, 0.86f),
            floatArrayOf(0.88f, 0.90f, 0.92f),
            floatArrayOf(0.94f, 0.89f, 0.84f),
            floatArrayOf(0.86f, 0.84f, 0.82f)
        )
        val houseTints = arrayOf(
            floatArrayOf(0.95f, 0.92f, 0.89f),
            floatArrayOf(0.92f, 0.88f, 0.84f),
            floatArrayOf(0.96f, 0.94f, 0.90f)
        )
        val factoryTints = arrayOf(
            floatArrayOf(0.84f, 0.87f, 0.90f),
            floatArrayOf(0.86f, 0.85f, 0.83f),
            floatArrayOf(0.80f, 0.84f, 0.88f)
        )

        for (bx in 0 until xBounds.lastIndex) {
            val x0 = xBounds[bx] + roadMargin
            val x1 = xBounds[bx + 1] - roadMargin
            val blockW = x1 - x0
            if (blockW < 18f) continue

            for (bz in 0 until zBounds.lastIndex) {
                val z0 = zBounds[bz] + roadMargin
                val z1 = zBounds[bz + 1] - roadMargin
                val blockD = z1 - z0
                if (blockD < 18f) continue

                val midX = (x0 + x1) * 0.5f
                val midZ = (z0 + z1) * 0.5f
                if (isInsideAirportZone(midX, midZ, pad = 10f) || isInsideArmyBaseZone(midX, midZ, pad = 10f)) continue
                if (isInsideEmeraldPark(midX, midZ)) continue
                if (midZ > 356f) continue

                // Subdivide EVERY city block into dense colony quadrants around the narrow galiyan!
                val alleyHalf = 2.85f
                val subPlots = mutableListOf<FloatArray>() // [minX, maxX, minZ, maxZ]
                if (blockW >= 30f && blockD >= 30f) {
                    val leftMaxX = midX - alleyHalf
                    val rightMinX = midX + alleyHalf
                    val topMaxZ = midZ - alleyHalf
                    val botMinZ = midZ + alleyHalf
                    subPlots.add(floatArrayOf(x0, leftMaxX, z0, topMaxZ))
                    subPlots.add(floatArrayOf(rightMinX, x1, z0, topMaxZ))
                    subPlots.add(floatArrayOf(x0, leftMaxX, botMinZ, z1))
                    subPlots.add(floatArrayOf(rightMinX, x1, botMinZ, z1))
                } else {
                    subPlots.add(floatArrayOf(x0, x1, z0, z1))
                }

                subPlots.forEachIndexed { sIdx, plot ->
                    val pMinX = plot[0]; val pMaxX = plot[1]
                    val pMinZ = plot[2]; val pMaxZ = plot[3]
                    val availW = pMaxX - pMinX
                    val availD = pMaxZ - pMinZ
                    if (availW < 9.5f || availD < 9.5f) return@forEachIndexed

                    val cx = (pMinX + pMaxX) * 0.5f
                    val cz = (pMinZ + pMaxZ) * 0.5f
                    val h = hash(bx, bz, sIdx)

                    // Leave occasional 1-in-9 courtyard open in residential suburbs for neighborhood trees/benches
                    if (cx < -104f && sIdx == 3 && (h % 5 == 0)) return@forEachIndexed

                    val style = when {
                        cz < -104f && abs(cx) < 220f -> if (h % 3 != 0) BuildingStyle.GLASS_SKYSCRAPER else BuildingStyle.APARTMENT_WINDOWS
                        cx > 104f && cz > 48f -> if (h % 4 != 0) BuildingStyle.FACTORY_INDUSTRIAL else BuildingStyle.APARTMENT_WINDOWS
                        cx < -104f && cz > -104f && cz < 280f -> if (h % 4 != 0) BuildingStyle.RED_BRICK_HOUSE else BuildingStyle.APARTMENT_WINDOWS
                        abs(cx) < 104f && abs(cz) < 104f -> when (h % 4) {
                            0 -> BuildingStyle.GLASS_SKYSCRAPER
                            1 -> BuildingStyle.APARTMENT_WINDOWS
                            2 -> BuildingStyle.RED_BRICK_HOUSE
                            else -> BuildingStyle.APARTMENT_WINDOWS
                        }
                        else -> when (h % 5) {
                            0 -> BuildingStyle.GLASS_SKYSCRAPER
                            1, 2 -> BuildingStyle.APARTMENT_WINDOWS
                            3 -> BuildingStyle.RED_BRICK_HOUSE
                            else -> BuildingStyle.FACTORY_INDUSTRIAL
                        }
                    }

                    val hasWall = (style == BuildingStyle.RED_BRICK_HOUSE && availW >= 14.5f && availD >= 14.5f && h % 2 == 0)
                    val marginPad = if (hasWall) 2.2f else 1.0f
                    val maxW = (availW - marginPad * 2f).coerceAtLeast(8.2f)
                    val maxD = (availD - marginPad * 2f).coerceAtLeast(8.2f)

                    val w: Float
                    val d: Float
                    val height: Float
                    when (style) {
                        BuildingStyle.GLASS_SKYSCRAPER -> {
                            w = (10.5f + (h % 6) * 1.4f).coerceAtMost(maxW)
                            d = (10.0f + ((h shr 3) % 6) * 1.3f).coerceAtMost(maxD)
                            height = 22.0f + ((h shr 6) % 9) * 4.5f
                        }
                        BuildingStyle.APARTMENT_WINDOWS -> {
                            w = (10.2f + (h % 5) * 1.3f).coerceAtMost(maxW)
                            d = (9.6f + ((h shr 3) % 5) * 1.2f).coerceAtMost(maxD)
                            height = 12.5f + ((h shr 6) % 6) * 3.2f
                        }
                        BuildingStyle.RED_BRICK_HOUSE -> {
                            w = (8.8f + (h % 4) * 1.1f).coerceAtMost(maxW)
                            d = (8.4f + ((h shr 3) % 4) * 1.0f).coerceAtMost(maxD)
                            height = 6.6f + ((h shr 6) % 3) * 0.7f
                        }
                        BuildingStyle.FACTORY_INDUSTRIAL -> {
                            w = (12.0f + (h % 4) * 1.6f).coerceAtMost(maxW)
                            d = (11.0f + ((h shr 3) % 4) * 1.5f).coerceAtMost(maxD)
                            height = 8.2f + ((h shr 6) % 4) * 1.5f
                        }
                    }

                    if (conflictsWithLandmarkOrExisting(cx, cz, w * 0.5f + marginPad, d * 0.5f + marginPad)) return@forEachIndexed

                    val tint = when (style) {
                        BuildingStyle.GLASS_SKYSCRAPER -> glassTints[h % glassTints.size]
                        BuildingStyle.APARTMENT_WINDOWS -> aptTints[h % aptTints.size]
                        BuildingStyle.RED_BRICK_HOUSE -> houseTints[h % houseTints.size]
                        BuildingStyle.FACTORY_INDUSTRIAL -> factoryTints[h % factoryTints.size]
                    }

                    list.add(
                        BuildingBlock(
                            x = cx, z = cz, width = w, depth = d, height = height,
                            tintR = tint[0], tintG = tint[1], tintB = tint[2],
                            hasSetbackCrown = (style == BuildingStyle.GLASS_SKYSCRAPER || (style == BuildingStyle.APARTMENT_WINDOWS && h % 2 == 0)),
                            useGlassFacade = (style == BuildingStyle.GLASS_SKYSCRAPER),
                            style = style,
                            hasCompoundWall = hasWall,
                            variantSeed = h % 12
                        )
                    )
                }
            }
        }
        return list
    }

    /**
     * Generates EVERY exterior cargo crate and oil drum next to industrial & colony buildings as BOTH
     * solid physics colliders AND exact rendered props — so the player can NEVER walk inside them!
     */
    private fun buildExteriorFactoryProps(): List<StaticPropObstacle> {
        val list = mutableListOf<StaticPropObstacle>()
        fun clearOfAlleys(px: Float, pz: Float, r: Float): Boolean {
            for (al in colonyAlleys) {
                val alHw = if (al.isVertical) al.width * 0.5f + r else al.length * 0.5f
                val alHd = if (al.isVertical) al.length * 0.5f else al.width * 0.5f + r
                if (abs(px - al.x) < alHw && abs(pz - al.z) < alHd) return false
            }
            return true
        }
        for ((idx, b) in buildings.withIndex()) {
            if (b.style == BuildingStyle.FACTORY_INDUSTRIAL) {
                val crateX = b.x - b.width * 0.5f - 0.82f
                val crateZ = b.z + 1.0f
                if (clearOfAlleys(crateX, crateZ, 0.75f)) {
                    list.add(StaticPropObstacle(crateX, crateZ, 0.56f, 0.56f, height = 1.15f, isOilDrum = false, yawDeg = 6f, size = 1.1f, yFloor = 0.04f))
                }
                if (clearOfAlleys(crateX, crateZ + 1.22f, 0.75f)) {
                    list.add(StaticPropObstacle(crateX, crateZ + 1.22f, 0.56f, 0.56f, height = 1.15f, isOilDrum = false, yawDeg = -4f, size = 1.1f, yFloor = 0.04f))
                }
                val drumX = b.x + b.width * 0.5f + 0.62f
                if (clearOfAlleys(drumX, b.z + 0.8f, 0.55f)) {
                    list.add(StaticPropObstacle(drumX, b.z + 0.8f, 0.34f, 0.34f, height = 0.95f, isOilDrum = true, yawDeg = 0f, size = 0.60f, yFloor = 0.04f))
                    list.add(StaticPropObstacle(drumX, b.z + 1.56f, 0.34f, 0.34f, height = 0.95f, isOilDrum = true, yawDeg = 42f, size = 0.60f, yFloor = 0.04f))
                }
            } else if (idx % 4 == 0 && !b.hasCompoundWall) {
                // Realistic urban alley clutter: occasional utility drum or supply crate tucked flush against apartment/house flank
                val propX = b.x + b.width * 0.5f + 0.58f
                val propZ = b.z - b.depth * 0.25f
                if (clearOfAlleys(propX, propZ, 0.65f)) {
                    if (idx % 8 == 0) {
                        list.add(StaticPropObstacle(propX, propZ, 0.34f, 0.34f, height = 0.95f, isOilDrum = true, yawDeg = 18f, size = 0.60f, yFloor = 0.04f))
                    } else {
                        list.add(StaticPropObstacle(propX, propZ, 0.48f, 0.48f, height = 0.96f, isOilDrum = false, yawDeg = 3f, size = 0.92f, yFloor = 0.04f))
                    }
                }
            }
        }
        return list
    }

    /**
     * Strict geometric validator guaranteeing no tree, grass clump, bench, or kiosk ever spawns
     * inside a road corridor, building, compound wall, enterable, shop, crate, or vehicle!
     */
    private fun isClearForProp(x: Float, z: Float, radius: Float, checkVehicles: Boolean = true): Boolean {
        if (abs(x) > CITY_HALF_EXTENT - 10f || z < -CITY_HALF_EXTENT + 10f || z > WATERFRONT_Z - 3f) return false
        if (isInsideAirportZone(x, z, pad = radius + 2f) || isInsideArmyBaseZone(x, z, pad = radius + 2f)) return false

        for (rc in roadCoords) {
            if (abs(x - rc) < 5.8f + radius) return false
            if (abs(z - rc) < 5.8f + radius) return false
        }
        for (al in colonyAlleys) {
            val alHw = if (al.isVertical) al.width * 0.5f + radius else al.length * 0.5f
            val alHd = if (al.isVertical) al.length * 0.5f else al.width * 0.5f + radius
            if (abs(x - al.x) < alHw && abs(z - al.z) < alHd) return false
        }
        for (b in buildings) {
            val extra = if (b.hasCompoundWall) 2.2f else 1.2f
            if (abs(x - b.x) < b.width * 0.5f + extra + radius && abs(z - b.z) < b.depth * 0.5f + extra + radius) {
                return false
            }
        }
        for (eb in enterableFootprints) {
            if (abs(x - eb.centerX) < eb.halfW + 1.6f + radius && abs(z - eb.centerZ) < eb.halfD + 2.2f + radius) {
                return false
            }
        }
        for (shop in shopFootprints) {
            if (abs(x - shop.x) < shop.width * 0.5f + 1.6f + radius && abs(z - shop.z) < shop.depth * 0.5f + 2.0f + radius) {
                return false
            }
        }
        for (obs in exteriorStaticProps) {
            if (abs(x - obs.x) < obs.halfW + 1.2f + radius && abs(z - obs.z) < obs.halfD + 1.2f + radius) return false
        }
        for (c in portContainers) {
            val hw = if (abs(c.yawDeg) > 45f) c.length * 0.5f else c.width * 0.5f
            val hd = if (abs(c.yawDeg) > 45f) c.width * 0.5f else c.length * 0.5f
            if (abs(x - c.x) < hw + 1.4f + radius && abs(z - c.z) < hd + 1.4f + radius) return false
        }
        for (hp in helipads) {
            if (abs(x - hp.x) < hp.size * 0.58f + radius && abs(z - hp.z) < hp.size * 0.58f + radius) return false
        }
        if (checkVehicles) {
            for (pc in parkedCars) {
                val dx = x - pc.x; val dz = z - pc.z
                if (dx * dx + dz * dz < (3.4f + radius) * (3.4f + radius)) return false
            }
            for (bk in bulletBikes) {
                val dx = x - bk.x; val dz = z - bk.z
                if (dx * dx + dz * dz < (2.4f + radius) * (2.4f + radius)) return false
            }
        }
        return true
    }

    private fun buildCityTrees(): List<TreeProp> {
        val list = mutableListOf<TreeProp>()
        var seed = 1

        fun tryAdd(x: Float, z: Float, kind: TreeKind, scale: Float) {
            if (isClearForProp(x, z, radius = 1.9f, checkVehicles = true)) {
                list.add(TreeProp(x, z, scale = scale, seed = seed++, kind = kind))
            }
        }

        // 1. Emerald City Park (-96..-56, 56..96) lush trees
        val parkCoords = listOf(
            -60f to 62f, -72f to 60f, -86f to 62f, -62f to 76f,
            -76f to 74f, -90f to 76f, -60f to 90f, -76f to 88f, -90f to 90f,
            -68f to 68f, -84f to 68f, -68f to 84f, -84f to 84f
        )
        parkCoords.forEachIndexed { i, (px, pz) ->
            tryAdd(px, pz, if (i % 2 == 0) TreeKind.PINE else TreeKind.BROAD, 1.22f + (i % 3) * 0.14f)
        }

        // 2. Waterfront Palm Tree Promenade (Z = 398m)
        var wx = -380f
        while (wx <= 380f) {
            if (abs(wx) > 12f) {
                tryAdd(wx, 398f, TreeKind.PALM, 1.12f + (seed % 3) * 0.12f)
            }
            wx += 22f
        }

        // 3. Boulevard Avenue Trees across the 10x expanded city (every block corner verge)
        val majorAvenues = floatArrayOf(-328f, -248f, -172f, -104f, -48f, 0f, 48f, 104f, 172f, 248f, 328f)
        for (ax in majorAvenues) {
            for (az in majorAvenues) {
                if (az > 340f) continue
                val tx = ax + 11.2f
                val tz = az + 11.2f
                val kind = when {
                    tz > 260f -> TreeKind.PALM
                    tz < -140f -> TreeKind.PINE
                    else -> if ((seed % 3) == 0) TreeKind.PINE else TreeKind.BROAD
                }
                tryAdd(tx, tz, kind, 0.96f + (seed % 4) * 0.10f)
                val tx2 = ax - 11.2f
                val tz2 = az - 11.2f
                tryAdd(tx2, tz2, if (tz2 < -140f) TreeKind.PINE else TreeKind.BROAD, 0.94f + (seed % 3) * 0.12f)
            }
        }
        return list
    }

    private fun buildStreetLights(): List<StreetLightProp> {
        val list = mutableListOf<StreetLightProp>()
        // Central & Major Cross Avenues (X=0, Z=0, ±48, ±104, ±172, ±248)
        val mainLines = floatArrayOf(-248f, -104f, -48f, 0f, 48f, 104f, 248f)
        for (line in mainLines) {
            var pos = -380f
            while (pos <= 380f) {
                var nearInter = false
                for (rc in roadCoords) {
                    if (abs(pos - rc) < 11f) { nearInter = true; break }
                }
                if (!nearInter) {
                    if (!isInsideAirportZone(line, pos, 6f) && !isInsideArmyBaseZone(line, pos, 6f)) {
                        list.add(StreetLightProp(line + 6.2f, pos, -1f, 0f))
                        list.add(StreetLightProp(line - 6.2f, pos, 1f, 0f))
                    }
                    if (line < 340f && !isInsideAirportZone(pos, line, 6f) && !isInsideArmyBaseZone(pos, line, 6f)) {
                        list.add(StreetLightProp(pos, line + 6.2f, 0f, -1f))
                        list.add(StreetLightProp(pos, line - 6.2f, 0f, 1f))
                    }
                }
                pos += 44f
            }
        }
        // Waterfront Boardwalk lamps
        for (x in -360..360 step 28) {
            list.add(StreetLightProp(x.toFloat(), WATERFRONT_Z - 4f, 0f, 1f))
        }
        return list
    }

    private fun buildTrafficSignals(): List<TrafficSignalProp> {
        val list = mutableListOf<TrafficSignalProp>()
        val majorJunctions = listOf(
            0f to 0f, 48f to 0f, -48f to 0f, 0f to 48f, 0f to -48f,
            104f to 0f, -104f to 0f, 0f to 104f, 0f to -104f,
            0f to -248f, -248f to 0f, 172f to 104f, 0f to 328f
        )
        for ((jx, jz) in majorJunctions) {
            list.add(TrafficSignalProp(jx + 5.8f, jz + 5.8f, -1f, 0f, axisXSignal = false))
            list.add(TrafficSignalProp(jx - 5.8f, jz - 5.8f, 1f, 0f, axisXSignal = false))
            list.add(TrafficSignalProp(jx + 5.8f, jz - 5.8f, 0f, 1f, axisXSignal = true))
            list.add(TrafficSignalProp(jx - 5.8f, jz + 5.8f, 0f, -1f, axisXSignal = true))
        }
        return list
    }

    private fun buildParkAndStreetBenches(): List<BenchProp> = listOf(
        BenchProp( 7.6f,  20f, -90f),
        BenchProp(-7.6f, -20f,  90f),
        BenchProp(-68f,   66f,   0f),
        BenchProp(-82f,   78f, 180f),
        BenchProp(-62f,   82f,  90f),
        BenchProp(-22f,  403f,   0f),
        BenchProp( 22f,  403f,   0f),
        BenchProp(-88f,  403f,   0f),
        BenchProp( 88f,  403f,   0f),
        BenchProp( 7.6f, -210f, -90f),
        BenchProp(-210f,   7.6f,   0f)
    ).filter { isClearForProp(it.x, it.z, radius = 0.8f, checkVehicles = true) }

    private fun buildCityKiosks(): List<KioskProp> = listOf(
        KioskProp( 10.5f,  26.5f, 0.82f, 0.28f, 0.22f),
        KioskProp(-10.5f, -26.5f, 0.20f, 0.56f, 0.78f),
        KioskProp(-64f,   88f, 0.22f, 0.64f, 0.42f),
        KioskProp( 14f,  400f, 0.86f, 0.62f, 0.16f),
        KioskProp( 12f, -162f, 0.24f, 0.52f, 0.74f),
        KioskProp(-162f,  12f, 0.76f, 0.42f, 0.22f)
    ).filter { isClearForProp(it.x, it.z, radius = 1.4f, checkVehicles = true) }

    private fun buildParkedCars(): List<ParkedCar> = listOf(
        // Cleanly parked curbside across all major districts so player can grab a car anywhere!
        ParkedCar( 18.5f,  -7.8f,  90f, 0.76f, 0.18f, 0.20f),
        ParkedCar(-18.5f,   7.8f, -90f, 0.16f, 0.42f, 0.78f),
        ParkedCar( 68.0f,  -7.8f,  90f, 0.88f, 0.88f, 0.90f),
        ParkedCar( 36.5f,  -7.8f, -90f, 0.20f, 0.22f, 0.26f),
        ParkedCar(-34.0f,  -7.8f,  90f, 0.86f, 0.62f, 0.14f),
        // North Tech District
        ParkedCar(  7.8f, -160f,   0f, 0.18f, 0.58f, 0.42f),
        ParkedCar( 22.0f, -240f,  90f, 0.82f, 0.22f, 0.24f),
        ParkedCar(-22.0f, -240f, -90f, 0.24f, 0.28f, 0.34f),
        // West Suburbs & Royal Villas
        ParkedCar(-155f,   7.8f, -90f, 0.74f, 0.46f, 0.18f),
        ParkedCar(-220f,  -7.8f,  90f, 0.88f, 0.86f, 0.82f),
        // East Industrial & Airport
        ParkedCar( 164f,  112f,   0f, 0.26f, 0.42f, 0.64f),
        ParkedCar( 292f, -248f,  90f, 0.86f, 0.72f, 0.18f),
        // South Waterfront Promenade
        ParkedCar(  16f,  398f,  90f, 0.18f, 0.60f, 0.44f),
        ParkedCar( -82f,  398f, -90f, 0.78f, 0.24f, 0.28f)
    )

    private fun buildBulletBikes(): List<BulletBikeProp> = listOf(
        BulletBikeProp(   8.2f,    8.2f, -35f, "Ajay's Royal Enfield Bullet 350"),
        BulletBikeProp( -13.5f,   -8.4f,  90f, "Classic Black Bullet 500"),
        BulletBikeProp(  14.5f,    8.4f, -90f, "Saini Residence Bullet 350"),
        BulletBikeProp(  62.0f,   -8.4f, -90f, "Showroom Bullet 350"),
        BulletBikeProp(  -8.4f, -162.0f,   0f, "North Skyline Bullet 500"),
        BulletBikeProp(  14.0f, -240.0f, -90f, "Tech Plaza Bullet 350"),
        BulletBikeProp(-210.0f,   -8.4f,  90f, "Royal Villa Bullet 500"),
        BulletBikeProp( 198.0f,  112.0f,  90f, "Factory Express Bullet 350"),
        BulletBikeProp(-262.0f, -248.0f,  90f, "Army Dispatch Bullet 500"),
        BulletBikeProp( 298.0f, -238.0f, -90f, "Airport Patrol Bullet 350"),
        BulletBikeProp( -12.0f,  401.5f,  15f, "Boardwalk Cruiser Bullet")
    )

    private fun buildGrassTufts(): List<GrassTuftProp> {
        val list = mutableListOf<GrassTuftProp>()
        var seed = 10
        // Around park & nearby boulevard trees
        for (i in trees.indices) {
            val t = trees[i]
            val offsets = arrayOf(floatArrayOf(1.1f, 0.9f), floatArrayOf(-1.2f, 0.7f))
            for (o in offsets) {
                seed++
                val gx = t.x + o[0]
                val gz = t.z + o[1]
                if (isClearForProp(gx, gz, radius = 0.6f, checkVehicles = true)) {
                    list.add(GrassTuftProp(gx, gz, 0.78f + (seed % 4) * 0.14f, (seed * 37f) % 180f, seed))
                }
            }
        }
        // Lush wild grass patches inside Emerald City Park (-94..-58, 58..94)
        var gx = -92f
        while (gx <= -60f) {
            var gz = 60f
            while (gz <= 92f) {
                seed++
                val jx = ((seed * 19) % 11 - 5) * 0.35f
                val jz = ((seed * 29) % 11 - 5) * 0.35f
                if (isClearForProp(gx + jx, gz + jz, radius = 0.6f, checkVehicles = true)) {
                    list.add(GrassTuftProp(gx + jx, gz + jz, 0.85f + (seed % 5) * 0.14f, (seed * 53f) % 180f, seed))
                }
                gz += 6.5f
            }
            gx += 6.5f
        }
        return list
    }

    private fun buildArmyBunkers(): List<ArmyBunkerProp> = listOf(
        ArmyBunkerProp(x = -384f, z = -268f, width = 12.5f, depth = 8.0f, height = 4.4f),
        ArmyBunkerProp(x = -318f, z = -222f, width = 7.4f, depth = 5.8f, height = 3.6f),
        ArmyBunkerProp(x = -384f, z = -224f, width = 9.0f, depth = 6.2f, height = 3.8f)
    )

    private fun buildArmyBarbedFences(): List<BarbedFenceSegment> = listOf(
        // North perimeter wall (z = -296, x = -410..-286)
        BarbedFenceSegment(x = -348f, z = -296f, length = 124f, isVertical = false),
        // South perimeter wall (z = -200, x = -410..-286)
        BarbedFenceSegment(x = -348f, z = -200f, length = 124f, isVertical = false),
        // West perimeter wall (x = -410, z = -296..-200)
        BarbedFenceSegment(x = -410f, z = -248f, length = 96f, isVertical = true),
        // East perimeter wall with wide 12m OPEN MAIN GATE at z = -254..-242
        BarbedFenceSegment(x = -286f, z = -275f, length = 42f, isVertical = true),
        BarbedFenceSegment(x = -286f, z = -221f, length = 42f, isVertical = true)
    )

    private fun buildHelipads(): List<HelipadProp> = listOf(
        // Main Army Base Helipad with active takeoff camo helicopter
        HelipadProp(x = -348f, z = -248f, size = 14.0f, hasHelicopter = true),
        // Coastal Waterfront VIP Helipad
        HelipadProp(x = 46f, z = 386f, size = 12.0f, hasHelicopter = false),
        // Airport VIP Helipad
        HelipadProp(x = 304f, z = -292f, size = 12.0f, hasHelicopter = false)
    )

    private fun buildArmyVehicles(): List<ArmyVehicleProp> = listOf(
        ArmyVehicleProp(x = -318f, z = -272f, yawDeg = 90f, isTruck = true),
        ArmyVehicleProp(x = -318f, z = -258f, yawDeg = 90f, isTruck = false),
        ArmyVehicleProp(x = -356f, z = -274f, yawDeg = 0f, isTruck = true),
        ArmyVehicleProp(x = -11.5f, z = -34.0f, yawDeg = 0f, isTruck = false)
    )

    private fun buildPortContainers(): List<PortContainerProp> = listOf(
        // East Harbor Port Dock Yard (z = 374..394, x = 80..200)
        PortContainerProp(86f, 1.45f, 380f, 7.6f, 2.8f, 2.9f, 90f, 0.78f, 0.24f, 0.22f),
        PortContainerProp(86f, 1.45f, 384f, 7.6f, 2.8f, 2.9f, 90f, 0.18f, 0.42f, 0.72f),
        PortContainerProp(86f, 4.35f, 380f, 7.6f, 2.8f, 2.9f, 90f, 0.84f, 0.64f, 0.18f),
        PortContainerProp(104f, 1.45f, 380f, 7.6f, 2.8f, 2.9f, 90f, 0.20f, 0.52f, 0.34f),
        PortContainerProp(104f, 1.45f, 384f, 7.6f, 2.8f, 2.9f, 90f, 0.80f, 0.42f, 0.18f),
        PortContainerProp(104f, 4.35f, 384f, 7.6f, 2.8f, 2.9f, 90f, 0.34f, 0.40f, 0.48f),
        PortContainerProp(124f, 1.45f, 382f, 7.6f, 2.8f, 2.9f, 90f, 0.68f, 0.20f, 0.28f),
        PortContainerProp(124f, 4.35f, 382f, 7.6f, 2.8f, 2.9f, 90f, 0.16f, 0.56f, 0.64f),
        PortContainerProp(152f, 1.45f, 382f, 7.6f, 2.8f, 2.9f, 90f, 0.22f, 0.44f, 0.70f),
        PortContainerProp(152f, 4.35f, 382f, 7.6f, 2.8f, 2.9f, 90f, 0.78f, 0.26f, 0.22f),
        // West Harbor Port Dock Yard (z = 378..388, x = -160..-82)
        PortContainerProp(-88f, 1.45f, 382f, 7.6f, 2.8f, 2.9f, 90f, 0.18f, 0.46f, 0.74f),
        PortContainerProp(-88f, 4.35f, 382f, 7.6f, 2.8f, 2.9f, 90f, 0.84f, 0.60f, 0.18f),
        PortContainerProp(-108f, 1.45f, 382f, 7.6f, 2.8f, 2.9f, 90f, 0.78f, 0.26f, 0.22f),
        PortContainerProp(-132f, 1.45f, 382f, 7.6f, 2.8f, 2.9f, 90f, 0.22f, 0.52f, 0.36f)
    )
}
