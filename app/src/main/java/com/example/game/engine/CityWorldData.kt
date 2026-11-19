package com.example.game.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class BuildingBlock(
    val x: Float,
    val z: Float,
    val width: Float,
    val depth: Float,
    val height: Float,
    val tintR: Float,
    val tintG: Float,
    val tintB: Float,
    val hasRooftopCap: Boolean = true
)

data class RoadStrip(
    val x: Float,
    val z: Float,
    val width: Float,
    val depth: Float,
    val isVertical: Boolean
)

data class TreeProp(
    val x: Float,
    val z: Float,
    val scale: Float
)

data class StreetLightProp(
    val x: Float,
    val z: Float,
    val dirX: Float,
    val dirZ: Float
)

data class CarAgent(
    var x: Float,
    var z: Float,
    val axisX: Boolean,
    val speed: Float,
    val minBound: Float,
    val maxBound: Float,
    var dir: Float,
    val r: Float,
    val g: Float,
    val b: Float
)

data class CollectibleItem(
    val id: Int,
    val x: Float,
    val z: Float
)

object CityWorldData {
    const val CITY_HALF_EXTENT = 74f

    val roads: List<RoadStrip> = listOf(
        // Main Boulevards
        RoadStrip(0f, 0f, 10f, 152f, isVertical = true),
        RoadStrip(0f, 0f, 152f, 10f, isVertical = false),
        // Outer Ring Roads
        RoadStrip(-42f, 0f, 8f, 152f, isVertical = true),
        RoadStrip(42f, 0f, 8f, 152f, isVertical = true),
        RoadStrip(0f, -42f, 152f, 8f, isVertical = false),
        RoadStrip(0f, 42f, 152f, 8f, isVertical = false)
    )

    val buildings: List<BuildingBlock> = buildCitySkyscrapers()

    val trees: List<TreeProp> = buildCityTrees()

    val streetLights: List<StreetLightProp> = buildStreetLights()

    val collectibles: List<CollectibleItem> = listOf(
        CollectibleItem(0, 0f, 7f),
        CollectibleItem(1, 0f, -7f),
        CollectibleItem(2, 16f, 6f),
        CollectibleItem(3, -16f, -6f),
        CollectibleItem(4, 42f, -42f),
        CollectibleItem(5, 42f, -20f),
        CollectibleItem(6, 42f, 20f),
        CollectibleItem(7, 42f, 42f),
        CollectibleItem(8, -42f, 42f),
        CollectibleItem(9, -33f, 34f),
        CollectibleItem(10, -50f, 50f),
        CollectibleItem(11, -42f, -42f),
        CollectibleItem(12, -42f, -18f),
        CollectibleItem(13, 20f, 42f),
        CollectibleItem(14, -20f, -42f),
        CollectibleItem(15, 22f, -22f),
        CollectibleItem(16, -22f, 22f),
        CollectibleItem(17, 60f, 0f),
        CollectibleItem(18, -60f, 0f),
        CollectibleItem(19, 0f, 60f)
    )

    fun createCars(): MutableList<CarAgent> = mutableListOf(
        CarAgent(-2.5f, -55f, axisX = false, speed = 8.5f, minBound = -68f, maxBound = 68f, dir = 1f, r = 0.85f, g = 0.18f, b = 0.22f),
        CarAgent(2.5f, 52f, axisX = false, speed = 9.0f, minBound = -68f, maxBound = 68f, dir = -1f, r = 0.14f, g = 0.55f, b = 0.90f),
        CarAgent(-52f, 2.5f, axisX = true, speed = 8.0f, minBound = -68f, maxBound = 68f, dir = 1f, r = 0.95f, g = 0.72f, b = 0.10f),
        CarAgent(55f, -2.5f, axisX = true, speed = 7.5f, minBound = -68f, maxBound = 68f, dir = -1f, r = 0.92f, g = 0.94f, b = 0.96f),
        CarAgent(40f, -30f, axisX = false, speed = 7.2f, minBound = -65f, maxBound = 65f, dir = 1f, r = 0.18f, g = 0.75f, b = 0.45f),
        CarAgent(-24f, 40f, axisX = true, speed = 7.8f, minBound = -65f, maxBound = 65f, dir = -1f, r = 0.55f, g = 0.30f, b = 0.85f)
    )

    fun resolvePlayerCollision(
        nextX: Float,
        nextZ: Float,
        playerRadius: Float = 0.65f
    ): Pair<Float, Float> {
        var x = nextX.coerceIn(-CITY_HALF_EXTENT, CITY_HALF_EXTENT)
        var z = nextZ.coerceIn(-CITY_HALF_EXTENT, CITY_HALF_EXTENT)

        // Central plaza fountain cylinder/box collision (radius ~2.4)
        val fountainRadius = 2.6f + playerRadius
        val fDistSq = x * x + z * z
        if (fDistSq < fountainRadius * fountainRadius && fDistSq > 0.0001f) {
            val fDist = kotlin.math.sqrt(fDistSq)
            x = (x / fDist) * fountainRadius
            z = (z / fDist) * fountainRadius
        }

        // Smooth AABB sliding collision against all skyscrapers
        for (b in buildings) {
            val halfW = b.width * 0.5f + playerRadius
            val halfD = b.depth * 0.5f + playerRadius
            val dx = x - b.x
            val dz = z - b.z
            if (abs(dx) < halfW && abs(dz) < halfD) {
                val overlapX = halfW - abs(dx)
                val overlapZ = halfD - abs(dz)
                if (overlapX < overlapZ) {
                    x += if (dx >= 0f) overlapX else -overlapX
                } else {
                    z += if (dz >= 0f) overlapZ else -overlapZ
                }
            }
        }
        return x to z
    }

    private fun buildCitySkyscrapers(): List<BuildingBlock> {
        val list = mutableListOf<BuildingBlock>()
        // City quadrants inside road grid: centers around +-21 and +-58
        val centers = listOf(
            // Inner quadrants (around Saini Plaza)
            Triple(21f, -21f, 26f),
            Triple(16f, -15f, 16f),
            Triple(27f, -26f, 34f),
            Triple(-21f, -21f, 22f),
            Triple(-27f, -16f, 28f),
            Triple(-16f, -27f, 18f),
            Triple(21f, 21f, 24f),
            Triple(27f, 16f, 19f),
            Triple(16f, 27f, 30f),
            // Note: (-21, 21) partially open park & mid-rise
            Triple(-27f, 16f, 15f),
            Triple(-16f, 27f, 14f),
            // Financial District (+X, -Z) tall skyscrapers
            Triple(56f, -22f, 38f),
            Triple(58f, -56f, 46f),
            Triple(22f, -56f, 34f),
            Triple(62f, -35f, 28f),
            // Sunset Boulevard (+X, +Z)
            Triple(56f, 22f, 24f),
            Triple(58f, 56f, 28f),
            Triple(22f, 56f, 22f),
            // Harbor Overlook (-X, -Z)
            Triple(-56f, -22f, 26f),
            Triple(-58f, -56f, 32f),
            Triple(-22f, -56f, 24f),
            // Emerald Park outer edge (-X, +Z) - keep (-42, 42) open for park!
            Triple(-60f, 24f, 18f),
            Triple(-22f, 58f, 20f),
            Triple(-60f, 58f, 22f)
        )

        centers.forEachIndexed { idx, (cx, cz, height) ->
            val w = 7.5f + ((idx * 3) % 4) * 1.1f
            val d = 7.5f + ((idx * 5) % 4) * 1.1f
            val warm = (idx % 3 == 0)
            list.add(
                BuildingBlock(
                    x = cx,
                    z = cz,
                    width = w,
                    depth = d,
                    height = height,
                    tintR = if (warm) 0.96f else 0.85f,
                    tintG = if (warm) 0.93f else 0.91f,
                    tintB = if (warm) 0.88f else 0.98f
                )
            )
        }
        return list
    }

    private fun buildCityTrees(): List<TreeProp> {
        val list = mutableListOf<TreeProp>()
        // Emerald City Park cluster around (-36..-48, 32..52) & (-20, 20)
        val parkCoords = listOf(
            -33f to 31f, -35f to 49f, -49f to 33f, -51f to 49f,
            -31f to 52f, -52f to 22f, -20f to 16f, -16f to 20f,
            -34f to 22f, -49f to 54f,
            // Boulevard avenue trees
            8f to 12f, -8f to 12f, 8f to -12f, -8f to -12f,
            8f to 30f, -8f to 30f, 8f to -30f, -8f to -30f,
            12f to 8f, 12f to -8f, -12f to 8f, -12f to -8f,
            30f to 8f, 30f to -8f, -30f to 8f, -30f to -8f,
            49f to -34f, 35f to -50f, 50f to 34f, -35f to -34f
        )
        parkCoords.forEachIndexed { i, (x, z) ->
            list.add(TreeProp(x, z, scale = 0.85f + (i % 3) * 0.2f))
        }
        return list
    }

    private fun buildStreetLights(): List<StreetLightProp> {
        val list = mutableListOf<StreetLightProp>()
        val offsets = listOf(-56f, -28f, -14f, 14f, 28f, 56f)
        for (o in offsets) {
            list.add(StreetLightProp(5.6f, o, -1f, 0f))
            list.add(StreetLightProp(-5.6f, o, 1f, 0f))
            list.add(StreetLightProp(o, 5.6f, 0f, -1f))
            list.add(StreetLightProp(o, -5.6f, 0f, 1f))
        }
        return list
    }
}
