package com.example.game.engine

import android.opengl.GLES20
import android.opengl.Matrix
import com.example.game.model.GraphicsQuality
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
 * Lightweight OpenGL ES 2.0 per-vertex colored 3D Terrain & Spline-Highway Shader:
 * - Renders 4800m x 4800m smoothly blended multi-biome heightmaps + curved asphalt/dirt roads
 * - Subtle procedural micro-grain surface variation in fragment shader (no tiled artifacts!)
 * - Matches CityShaderProgram sun lighting, hemisphere ambient, ACES Filmic tone mapping & fog!
 */
class TerrainShaderProgram {
    val programId: Int

    val uVpMatrixLoc: Int
    val uLightDirLoc: Int
    val uSunColorLoc: Int
    val uAmbientScaleLoc: Int
    val uCameraPosLoc: Int
    val uFogColorLoc: Int
    val uFogStartLoc: Int
    val uFogEndLoc: Int
    val uActiveBoundsLoc: Int
    val uIsLod1Loc: Int

    val aPositionLoc: Int
    val aNormalLoc: Int
    val aColorLoc: Int

    init {
        val vertexSrc = """
            precision highp float;
            uniform mat4 uVpMatrix;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec3 aColor;

            varying vec3 vWorldPos;
            varying vec3 vNormal;
            varying vec3 vColor;

            void main() {
                vWorldPos = aPosition;
                vNormal = aNormal;
                vColor = aColor;
                gl_Position = uVpMatrix * vec4(aPosition, 1.0);
            }
        """.trimIndent()

        val fragmentSrc = """
            precision mediump float;
            uniform vec3 uLightDir;
            uniform vec3 uSunColor;
            uniform float uAmbientScale;
            uniform vec3 uCameraPos;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform vec4 uActiveBounds;
            uniform float uIsLod1;

            varying vec3 vWorldPos;
            varying vec3 vNormal;
            varying vec3 vColor;

            void main() {
                // Prevent coarse 100m distant LOD1 triangles from EVER poking dirt mounds through active high-res terrain & roads!
                if (uIsLod1 > 0.5 &&
                    vWorldPos.x > uActiveBounds.x && vWorldPos.x < uActiveBounds.z &&
                    vWorldPos.z > uActiveBounds.y && vWorldPos.z < uActiveBounds.w) {
                    discard;
                }

                vec3 n = normalize(vNormal);
                vec3 l = normalize(uLightDir);
                float ndotl = max(dot(n, l), 0.0);

                // Subtle organic procedural ground micro-grain so large vistas look detailed & natural
                float g1 = sin(vWorldPos.x * 0.42 + vWorldPos.z * 0.37) * cos(vWorldPos.z * 0.51 - vWorldPos.x * 0.29);
                float g2 = sin(vWorldPos.x * 1.45 - vWorldPos.z * 1.32);
                float grain = (g1 * 0.032 + g2 * 0.016);
                vec3 baseCol = clamp(vColor + vec3(grain), 0.03, 0.98);

                vec3 skyAmb = vec3(0.42, 0.48, 0.56) * uAmbientScale;
                vec3 gndAmb = vec3(0.24, 0.23, 0.20) * uAmbientScale;
                vec3 hemi = mix(gndAmb, skyAmb, clamp(n.y * 0.5 + 0.5, 0.0, 1.0));
                vec3 lit = baseCol * (hemi + uSunColor * ndotl * 0.92);

                // Soft ACES Filmic tone-mapping
                vec3 mapped = (lit * (2.51 * lit + 0.03)) / (lit * (2.43 * lit + 0.59) + 0.14);
                mapped = clamp(mapped, 0.0, 1.0);

                float dist = length(vWorldPos - uCameraPos);
                float fogF = clamp((dist - uFogStart) / max(uFogEnd - uFogStart, 1.0), 0.0, 1.0);
                vec3 finalRgb = mix(mapped, uFogColor, fogF * fogF);
                gl_FragColor = vec4(finalRgb, 1.0);
            }
        """.trimIndent()

        programId = compileProgram(vertexSrc, fragmentSrc)
        uVpMatrixLoc = GLES20.glGetUniformLocation(programId, "uVpMatrix")
        uLightDirLoc = GLES20.glGetUniformLocation(programId, "uLightDir")
        uSunColorLoc = GLES20.glGetUniformLocation(programId, "uSunColor")
        uAmbientScaleLoc = GLES20.glGetUniformLocation(programId, "uAmbientScale")
        uCameraPosLoc = GLES20.glGetUniformLocation(programId, "uCameraPos")
        uFogColorLoc = GLES20.glGetUniformLocation(programId, "uFogColor")
        uFogStartLoc = GLES20.glGetUniformLocation(programId, "uFogStart")
        uFogEndLoc = GLES20.glGetUniformLocation(programId, "uFogEnd")
        uActiveBoundsLoc = GLES20.glGetUniformLocation(programId, "uActiveBounds")
        uIsLod1Loc = GLES20.glGetUniformLocation(programId, "uIsLod1")

        aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition")
        aNormalLoc = GLES20.glGetAttribLocation(programId, "aNormal")
        aColorLoc = GLES20.glGetAttribLocation(programId, "aColor")
    }

    private fun compileProgram(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, src)
            GLES20.glCompileShader(id)
            return id
        }
        val v = compile(GLES20.GL_VERTEX_SHADER, vs)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        GLES20.glDeleteShader(v)
        GLES20.glDeleteShader(f)
        return p
    }
}

data class RegionalTreeInstance(val x: Float, val y: Float, val z: Float, val scale: Float, val seed: Int, val kind: TreeKind)
data class RegionalBoxObstacle(val x: Float, val z: Float, val hw: Float, val hd: Float, val height: Float = 8.0f)

/**
 * Chunk-Based 3D World Streaming Engine for the 4800m x 4800m Saini State / Bharat Open World.
 * - Lazily streams 160m x 160m heightmap terrain chunks + spline road/bridge ribbons around the player
 * - Renders 360° surrounding animated ocean, biome forests, and all iconic regional 3D landmarks
 * - LRU GPU VBO cache keeps VRAM low and Android locked at smooth 60 FPS!
 */
class WorldTerrainChunkManager {

    private val terrainShader = TerrainShaderProgram()

    private class TerrainChunkGpu(
        val key: Long,
        val cx: Int,
        val cz: Int,
        val centerX: Float,
        val centerZ: Float,
        val terrainVboId: Int,
        val terrainVertCount: Int,
        val roadVboId: Int,
        val roadVertCount: Int,
        val trees: List<RegionalTreeInstance>,
        var lastUsedFrame: Long
    )

    private val chunkCache = LinkedHashMap<Long, TerrainChunkGpu>(64, 0.75f, true)
    private var frameCounter = 0L

    // Player-centered rolling macro horizon LOD1 mesh (3600m x 3600m at 75m step, 1 draw call!)
    private var distantLodVboId: Int = 0
    private var distantLodVertCount: Int = 0
    private var lodCenterX: Float = Float.NaN
    private var lodCenterZ: Float = Float.NaN

    // Scratch arrays reused across chunk builds (zero GC pauses)
    private val scratchTerrainFloats = FloatArray(GRID_CELLS * GRID_CELLS * 6 * FLOATS_PER_VERT)
    private val scratchRoadList = ArrayList<Float>(1600 * FLOATS_PER_VERT)

    init {
        buildDistantHorizonLod(0f, 0f)
    }

    // Pre-built landmark solid collision boxes — strictly derived from road-cleared WorldMapData.regionalPois!
    companion object {
        const val TERRAIN_CHUNK_SIZE = 80f
        const val TERRAIN_GRID_DIM_X = 625 // 625 x 80m = 50,000m (-25000..+25000)
        const val TERRAIN_GRID_DIM_Z = 813 // 813 x 80m = 65,040m (-32500..+32540)
        private const val GRID_CELLS = 12 // 6.67m crisp spacing -> fast chunk baking & zero road bleed!
        private const val FLOATS_PER_VERT = 9 // pos(3) + normal(3) + rgb(3)
        private const val STRIDE_BYTES = FLOATS_PER_VERT * 4
        private const val MAX_CACHED_TERRAIN_CHUNKS = 48

        private fun chunkKey(cx: Int, cz: Int): Long =
            (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)

        // All regional landmark colliders strictly placed on road-cleared POI coordinates (ZERO objects on roads!)
        private val landmarkColliders: List<RegionalBoxObstacle> = buildList {
            for (poi in WorldMapData.regionalPois) {
                when (poi.category) {
                    PoiCategory.PETROL_PUMP -> {
                        for (px in floatArrayOf(-5.5f, 5.5f)) {
                            add(RegionalBoxObstacle(poi.x + px, poi.z - 3.5f, 0.45f, 0.45f, 5.8f))
                            add(RegionalBoxObstacle(poi.x + px, poi.z + 3.5f, 0.45f, 0.45f, 5.8f))
                        }
                        add(RegionalBoxObstacle(poi.x - 2.5f, poi.z, 0.60f, 0.48f, 2.3f))
                        add(RegionalBoxObstacle(poi.x + 2.5f, poi.z, 0.60f, 0.48f, 2.3f))
                    }
                    PoiCategory.DHABA -> {
                        add(RegionalBoxObstacle(poi.x, poi.z - 5.0f, 6.6f, 3.9f, 5.0f))
                    }
                    PoiCategory.GARAGE -> {
                        add(RegionalBoxObstacle(poi.x, poi.z, 7.1f, 5.6f, 6.4f))
                    }
                    PoiCategory.TOWN -> {
                        add(RegionalBoxObstacle(poi.x - 18f, poi.z - 12f, 7.2f, 5.8f, 8.5f))
                        add(RegionalBoxObstacle(poi.x + 18f, poi.z - 12f, 7.2f, 5.8f, 8.5f))
                        add(RegionalBoxObstacle(poi.x - 16f, poi.z + 14f, 6.8f, 5.5f, 7.5f))
                        add(RegionalBoxObstacle(poi.x + 16f, poi.z + 14f, 6.8f, 5.5f, 10.5f))
                    }
                    PoiCategory.MILITARY -> {
                        add(RegionalBoxObstacle(poi.x - 24f, poi.z - 14f, 12f, 9f, 9.0f))
                        add(RegionalBoxObstacle(poi.x + 24f, poi.z - 14f, 9f, 7.5f, 12.0f))
                    }
                    PoiCategory.HELIPAD_DOCK -> {
                        add(RegionalBoxObstacle(poi.x + 16f, poi.z - 10f, 8.2f, 6.2f, 7.6f))
                    }
                    PoiCategory.LANDMARK -> {
                        if (poi.id.contains("lighthouse") || poi.id.contains("fort")) {
                            add(RegionalBoxObstacle(poi.x, poi.z, 4.4f, 4.4f, 24.0f))
                        } else if (poi.id.contains("temple")) {
                            add(RegionalBoxObstacle(poi.x, poi.z, 10f, 10f, 18.0f))
                        } else if (poi.id.contains("wind")) {
                            add(RegionalBoxObstacle(poi.x, poi.z, 2.0f, 2.0f, 34.0f))
                            add(RegionalBoxObstacle(poi.x - 42f, poi.z + 22f, 2.0f, 2.0f, 34.0f))
                            add(RegionalBoxObstacle(poi.x + 42f, poi.z - 22f, 2.0f, 2.0f, 34.0f))
                        } else {
                            add(RegionalBoxObstacle(poi.x - 18f, poi.z - 6f, 7.5f, 5.8f, 8.0f))
                            add(RegionalBoxObstacle(poi.x + 18f, poi.z - 6f, 7.5f, 5.8f, 8.0f))
                        }
                    }
                }
            }
        }

        /**
         * 3D solid + climbable regional landmark collision resolver.
         * Returns (resolvedX, resolvedZ, supportTopY) where supportTopY is Float.NEGATIVE_INFINITY
         * unless the player is standing/landing on top of a climbable landmark roof.
         */
        fun resolveRegionalCollisions(
            xIn: Float,
            zIn: Float,
            currentY: Float,
            radius: Float
        ): Triple<Float, Float, Float> {
            // Fast skip if inside Saini Metro downtown
            if (xIn in -455f..455f && zIn in -435f..420f) return Triple(xIn, zIn, Float.NEGATIVE_INFINITY)
            var x = xIn
            var z = zIn
            var topSupport = Float.NEGATIVE_INFINITY
            for (obs in landmarkColliders) {
                val dx = x - obs.x
                val dz = z - obs.z
                val px = (obs.hw + radius) - abs(dx)
                val pz = (obs.hd + radius) - abs(dz)
                if (px > 0f && pz > 0f) {
                    val baseY = WorldMapData.getTerrainHeight(obs.x, obs.z)
                    val topY = baseY + obs.height
                    if (currentY >= topY - 0.50f) {
                        if (topY > topSupport) topSupport = topY
                    } else {
                        if (px < pz) {
                            x += if (dx >= 0f) px else -px
                        } else {
                            z += if (dz >= 0f) pz else -pz
                        }
                    }
                }
            }
            return Triple(x, z, topSupport)
        }

        fun isRegionalBlocked(x: Float, z: Float, radius: Float = 0.45f): Boolean {
            if (x in -455f..455f && z in -435f..420f) return false
            for (obs in landmarkColliders) {
                if (abs(x - obs.x) < obs.hw + radius && abs(z - obs.z) < obs.hd + radius) return true
            }
            return false
        }

        fun getMaxRegionalObstacleHeightAt(x: Float, z: Float, radius: Float = 2.0f): Float {
            var maxH = Float.NEGATIVE_INFINITY
            for (obs in landmarkColliders) {
                if (abs(x - obs.x) < obs.hw + radius && abs(z - obs.z) < obs.hd + radius) {
                    val topY = WorldMapData.getTerrainHeight(obs.x, obs.z) + obs.height
                    if (topY > maxH) maxH = topY
                }
            }
            return maxH
        }

        /**
         * Fast footprint exclusion check for all regional landmarks, forts, airbase runways,
         * petrol pumps, highway dhabas, garages, toll plazas, and village courtyards so foliage
         * never pokes through structures or paved aprons.
         */
        fun isInsideRegionalLandmark(x: Float, z: Float, pad: Float = 1.4f): Boolean {
            if (x in -455f..455f && z in -435f..420f) return false
            for (obs in landmarkColliders) {
                if (abs(x - obs.x) < obs.hw + pad && abs(z - obs.z) < obs.hd + pad) return true
            }
            // Northern Military Airbase paved runway & tarmac plateau (-960, -1140)
            if (x in -1052f..-878f && z in -1202f..-1084f) return true
            // Saini Garh Fort inner sandstone courtyard (-1520, -420)
            if (x in -1558f..-1482f && z in -458f..-382f) return true
            // Luxury Resort Island pool deck & villa patio (175, 960)
            if (x in 140f..214f && z in 944f..982f) return true
            // Regional Petrol Pumps, Highway Dhabas, Garages & NHAI Toll Plaza aprons
            for (poi in WorldMapData.regionalPois) {
                val apronR = when (poi.category) {
                    PoiCategory.PETROL_PUMP, PoiCategory.GARAGE, PoiCategory.DHABA -> 15f
                    PoiCategory.HELIPAD_DOCK -> 16f
                    else -> 0f
                }
                if (apronR > 0f) {
                    val dx = x - poi.x
                    val dz = z - poi.z
                    if (dx * dx + dz * dz < apronR * apronR) return true
                }
            }
            return false
        }
    }

    /**
     * Resolves solid tree-trunk collisions against streamed biome forests (Mountain Pines, Jungle Trees, Beach Palms)
     * so neither the player nor vehicles can clip through trees outside downtown!
     * Note: Ground grass foliage is NEVER included here (100% pass-through).
     */
    fun resolveRegionalTreeCollisions(xIn: Float, zIn: Float, currentY: Float, radius: Float): Pair<Float, Float> {
        if (xIn in -455f..455f && zIn in -435f..420f) return xIn to zIn
        var x = xIn
        var z = zIn
        val pCx = ((x + WorldMapData.WORLD_HALF_X) / TERRAIN_CHUNK_SIZE).toInt().coerceIn(0, TERRAIN_GRID_DIM_X - 1)
        val pCz = ((z + WorldMapData.WORLD_HALF_Z) / TERRAIN_CHUNK_SIZE).toInt().coerceIn(0, TERRAIN_GRID_DIM_Z - 1)
        for (cx in (pCx - 1).coerceAtLeast(0)..(pCx + 1).coerceAtMost(TERRAIN_GRID_DIM_X - 1)) {
            for (cz in (pCz - 1).coerceAtLeast(0)..(pCz + 1).coerceAtMost(TERRAIN_GRID_DIM_Z - 1)) {
                val ch = chunkCache[chunkKey(cx, cz)] ?: continue
                for (t in ch.trees) {
                    if (currentY > t.y + 4.2f * t.scale) continue
                    val dx = x - t.x
                    val dz = z - t.z
                    val minDist = 0.44f * t.scale + radius
                    val dSq = dx * dx + dz * dz
                    if (dSq < minDist * minDist && dSq > 0.00001f) {
                        val d = sqrt(dSq)
                        x = t.x + (dx / d) * minDist
                        z = t.z + (dz / d) * minDist
                    }
                }
            }
        }
        return x to z
    }

    private fun getActiveChunkRadius(graphicsQuality: GraphicsQuality): Int = when (graphicsQuality) {
        GraphicsQuality.LOW -> 2
        GraphicsQuality.MEDIUM -> 2
        GraphicsQuality.HIGH -> 2 // 5x5 = 25 chunks (400m x 400m crisp LOD0 around player) -> blazing 60 FPS!
    }

    /**
     * Incrementally pre-bakes ALL initial terrain + road chunks surrounding spawn during the match
     * loading screen so there is ZERO lag spike after spawning!
     * Returns (bakedCount, totalRequiredCount).
     */
    fun prewarmStep(
        centerX: Float,
        centerZ: Float,
        graphicsQuality: GraphicsQuality,
        maxChunksPerStep: Int = 4
    ): Pair<Int, Int> {
        if (lodCenterX.isNaN() || hypot(centerX - lodCenterX, centerZ - lodCenterZ) > 450f) {
            buildDistantHorizonLod(centerX, centerZ)
        }
        val pCx = ((centerX + WorldMapData.WORLD_HALF_X) / TERRAIN_CHUNK_SIZE).toInt().coerceIn(0, TERRAIN_GRID_DIM_X - 1)
        val pCz = ((centerZ + WorldMapData.WORLD_HALF_Z) / TERRAIN_CHUNK_SIZE).toInt().coerceIn(0, TERRAIN_GRID_DIM_Z - 1)
        val radiusChunks = getActiveChunkRadius(graphicsQuality)

        val minCx = (pCx - radiusChunks).coerceAtLeast(0)
        val maxCx = (pCx + radiusChunks).coerceAtMost(TERRAIN_GRID_DIM_X - 1)
        val minCz = (pCz - radiusChunks).coerceAtLeast(0)
        val maxCz = (pCz + radiusChunks).coerceAtMost(TERRAIN_GRID_DIM_Z - 1)

        var totalNeeded = 0
        var bakedCount = 0
        var builtNow = 0

        for (ring in 0..radiusChunks) {
            for (cx in (pCx - ring).coerceAtLeast(minCx)..(pCx + ring).coerceAtMost(maxCx)) {
                for (cz in (pCz - ring).coerceAtLeast(minCz)..(pCz + ring).coerceAtMost(maxCz)) {
                    if (abs(cx - pCx) != ring && abs(cz - pCz) != ring) continue
                    totalNeeded++
                    val key = chunkKey(cx, cz)
                    if (chunkCache.containsKey(key)) {
                        bakedCount++
                    } else if (builtNow < maxChunksPerStep) {
                        val gpu = buildTerrainChunk(cx, cz, key)
                        chunkCache[key] = gpu
                        builtNow++
                        bakedCount++
                    }
                }
            }
        }
        return bakedCount to totalNeeded.coerceAtLeast(1)
    }

    /**
     * Streams and draws multi-biome 3D terrain chunks, curved highways/bridges, and 360° surrounding ocean.
     */
    fun drawTerrainAndOcean(
        dc: GlDrawContext,
        playerX: Float,
        playerZ: Float,
        camPosX: Float,
        camPosY: Float,
        camPosZ: Float,
        lDirX: Float,
        lDirY: Float,
        lDirZ: Float,
        sunR: Float,
        sunG: Float,
        sunB: Float,
        ambientScale: Float,
        fogR: Float,
        fogG: Float,
        fogB: Float,
        fogStart: Float,
        fogEnd: Float,
        worldTime: Float,
        graphicsQuality: GraphicsQuality
    ) {
        frameCounter++

        // Lazily recenter rolling 3600m x 3600m macro horizon LOD1 when player travels >450m across the 50x65km world
        if (lodCenterX.isNaN() || hypot(playerX - lodCenterX, playerZ - lodCenterZ) > 450f) {
            buildDistantHorizonLod(playerX, playerZ)
        }

        val pCx = ((playerX + WorldMapData.WORLD_HALF_X) / TERRAIN_CHUNK_SIZE).toInt().coerceIn(0, TERRAIN_GRID_DIM_X - 1)
        val pCz = ((playerZ + WorldMapData.WORLD_HALF_Z) / TERRAIN_CHUNK_SIZE).toInt().coerceIn(0, TERRAIN_GRID_DIM_Z - 1)
        val radiusChunks = getActiveChunkRadius(graphicsQuality)

        // 1. Lazily stream at most 1 new 80m x 80m terrain chunk per frame during live gameplay (zero hitching!)
        var builtThisFrame = 0
        val visibleChunks = ArrayList<TerrainChunkGpu>((radiusChunks * 2 + 1) * (radiusChunks * 2 + 1))

        val minCx = (pCx - radiusChunks).coerceAtLeast(0)
        val maxCx = (pCx + radiusChunks).coerceAtMost(TERRAIN_GRID_DIM_X - 1)
        val minCz = (pCz - radiusChunks).coerceAtLeast(0)
        val maxCz = (pCz + radiusChunks).coerceAtMost(TERRAIN_GRID_DIM_Z - 1)

        // Build closest chunks first (center-outward) so terrain right under the player is ALWAYS loaded
        for (ring in 0..radiusChunks) {
            for (cx in (pCx - ring).coerceAtLeast(minCx)..(pCx + ring).coerceAtMost(maxCx)) {
                for (cz in (pCz - ring).coerceAtLeast(minCz)..(pCz + ring).coerceAtMost(maxCz)) {
                    if (abs(cx - pCx) != ring && abs(cz - pCz) != ring) continue
                    val key = chunkKey(cx, cz)
                    var gpu = chunkCache[key]
                    if (gpu != null) {
                        gpu.lastUsedFrame = frameCounter
                        visibleChunks.add(gpu)
                    } else if (builtThisFrame < 1) {
                        gpu = buildTerrainChunk(cx, cz, key)
                        chunkCache[key] = gpu
                        visibleChunks.add(gpu)
                        builtThisFrame++
                    }
                }
            }
        }

        // Compute guaranteed fully-loaded LOD0 world bounds so coarse LOD1 is cleanly discarded underneath active terrain & roads!
        var loadedRing = -1
        for (ring in 0..radiusChunks) {
            var allLoaded = true
            val rMinX = (pCx - ring).coerceAtLeast(0)
            val rMaxX = (pCx + ring).coerceAtMost(TERRAIN_GRID_DIM_X - 1)
            val rMinZ = (pCz - ring).coerceAtLeast(0)
            val rMaxZ = (pCz + ring).coerceAtMost(TERRAIN_GRID_DIM_Z - 1)
            outerCheck@ for (cx in rMinX..rMaxX) {
                for (cz in rMinZ..rMaxZ) {
                    if (chunkCache[chunkKey(cx, cz)] == null) {
                        allLoaded = false
                        break@outerCheck
                    }
                }
            }
            if (allLoaded) loadedRing = ring else break
        }
        val activeMinX: Float
        val activeMinZ: Float
        val activeMaxX: Float
        val activeMaxZ: Float
        if (loadedRing >= 0) {
            val lMinCx = (pCx - loadedRing).coerceAtLeast(0)
            val lMaxCx = (pCx + loadedRing).coerceAtMost(TERRAIN_GRID_DIM_X - 1)
            val lMinCz = (pCz - loadedRing).coerceAtLeast(0)
            val lMaxCz = (pCz + loadedRing).coerceAtMost(TERRAIN_GRID_DIM_Z - 1)
            activeMinX = -WorldMapData.WORLD_HALF_X + lMinCx * TERRAIN_CHUNK_SIZE + 1.0f
            activeMinZ = -WorldMapData.WORLD_HALF_Z + lMinCz * TERRAIN_CHUNK_SIZE + 1.0f
            activeMaxX = -WorldMapData.WORLD_HALF_X + (lMaxCx + 1) * TERRAIN_CHUNK_SIZE - 1.0f
            activeMaxZ = -WorldMapData.WORLD_HALF_Z + (lMaxCz + 1) * TERRAIN_CHUNK_SIZE - 1.0f
        } else {
            activeMinX = 0f; activeMinZ = 0f; activeMaxX = -1f; activeMaxZ = -1f
        }

        // Evict stale chunks beyond cache budget
        if (chunkCache.size > MAX_CACHED_TERRAIN_CHUNKS) {
            val it = chunkCache.entries.iterator()
            while (chunkCache.size > MAX_CACHED_TERRAIN_CHUNKS && it.hasNext()) {
                val e = it.next()
                if (frameCounter - e.value.lastUsedFrame > 16L) {
                    if (e.value.terrainVboId != 0) GLES20.glDeleteBuffers(1, intArrayOf(e.value.terrainVboId), 0)
                    if (e.value.roadVboId != 0) GLES20.glDeleteBuffers(1, intArrayOf(e.value.roadVboId), 0)
                    it.remove()
                }
            }
        }

        // 2. Render Multi-Biome 3D Terrain & Spline Roads using lightweight TerrainShaderProgram
        GLES20.glUseProgram(terrainShader.programId)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)

        // Extend terrain fog far enough to showcase distant snow peaks & island silhouettes on the horizon!
        val terrainFogStart = max(fogStart, 110f)
        val terrainFogEnd = max(fogEnd + 360f, 680f)

        GLES20.glUniformMatrix4fv(terrainShader.uVpMatrixLoc, 1, false, dc.vpMatrix, 0)
        GLES20.glUniform3f(terrainShader.uLightDirLoc, lDirX, lDirY, lDirZ)
        GLES20.glUniform3f(terrainShader.uSunColorLoc, sunR, sunG, sunB)
        GLES20.glUniform1f(terrainShader.uAmbientScaleLoc, ambientScale)
        GLES20.glUniform3f(terrainShader.uCameraPosLoc, camPosX, camPosY, camPosZ)
        GLES20.glUniform3f(terrainShader.uFogColorLoc, fogR, fogG, fogB)
        GLES20.glUniform1f(terrainShader.uFogStartLoc, terrainFogStart)
        GLES20.glUniform1f(terrainShader.uFogEndLoc, terrainFogEnd)
        GLES20.glUniform4f(terrainShader.uActiveBoundsLoc, activeMinX, activeMinZ, activeMaxX, activeMaxZ)

        GLES20.glEnableVertexAttribArray(terrainShader.aPositionLoc)
        GLES20.glEnableVertexAttribArray(terrainShader.aNormalLoc)
        GLES20.glEnableVertexAttribArray(terrainShader.aColorLoc)

        // 2a. Distant Low-Poly Macro Horizon LOD1 (strictly discarded inside active LOD0 bounds -> ZERO dirt bleed!)
        if (distantLodVboId != 0 && distantLodVertCount > 0) {
            GLES20.glUniform1f(terrainShader.uIsLod1Loc, 1f)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, distantLodVboId)
            GLES20.glVertexAttribPointer(terrainShader.aPositionLoc, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 0)
            GLES20.glVertexAttribPointer(terrainShader.aNormalLoc, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 3 * 4)
            GLES20.glVertexAttribPointer(terrainShader.aColorLoc, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 6 * 4)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, distantLodVertCount)
        }

        // 2b. High-Detail Active Streamed Terrain Chunks (LOD0 — 5.0m crisp vertices!)
        GLES20.glUniform1f(terrainShader.uIsLod1Loc, 0f)
        for (i in visibleChunks.indices) {
            val ch = visibleChunks[i]
            if (ch.terrainVboId != 0 && ch.terrainVertCount > 0) {
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ch.terrainVboId)
                GLES20.glVertexAttribPointer(terrainShader.aPositionLoc, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 0)
                GLES20.glVertexAttribPointer(terrainShader.aNormalLoc, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 3 * 4)
                GLES20.glVertexAttribPointer(terrainShader.aColorLoc, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 6 * 4)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, ch.terrainVertCount)
            }
        }

        // Draw curved spline highway ribbons & bridge decks with polygon offset (zero Z-fighting!)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glPolygonOffset(-1.5f, -2.0f)
        for (i in visibleChunks.indices) {
            val ch = visibleChunks[i]
            if (ch.roadVboId != 0 && ch.roadVertCount > 0) {
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ch.roadVboId)
                GLES20.glVertexAttribPointer(terrainShader.aPositionLoc, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 0)
                GLES20.glVertexAttribPointer(terrainShader.aNormalLoc, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 3 * 4)
                GLES20.glVertexAttribPointer(terrainShader.aColorLoc, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 6 * 4)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, ch.roadVertCount)
            }
        }
        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glDisableVertexAttribArray(terrainShader.aPositionLoc)
        GLES20.glDisableVertexAttribArray(terrainShader.aNormalLoc)
        GLES20.glDisableVertexAttribArray(terrainShader.aColorLoc)

        // Restore main CityShaderProgram for animated 360° Ocean + 3D Landmarks
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glUseProgram(dc.assets.shader.programId)
        dc.boundVboId = -1

        // 3. 360° Animated Ocean Plane centered cleanly on the player (follows player seamlessly everywhere!)
        val snapX = floor(playerX / 80f) * 80f
        val snapZ = floor(playerZ / 80f) * 80f
        val waveU = (worldTime * 0.028f) % 1f
        val waveV = (worldTime * 0.017f) % 1f
        dc.bindVbo(dc.assets.quadMesh)
        dc.drawQuad(
            snapX, WorldMapData.WATER_LEVEL, snapZ,
            980f, 980f,
            dc.assets.texWaterOcean, 48f, 48f,
            0.86f, 0.94f, 1.0f,
            0.03f, 0.92f, 54f, 0.50f,
            uOffset = waveU,
            vOffset = waveV,
            normalTexId = dc.assets.texWaterNormal,
            normalStrength = 0.72f
        )
    }

    /**
     * Renders streamed Biome Trees, Overseas Sea Bridge Piers, and Regional 3D Landmarks
     * (Saini Garh Fort, Wind Turbines, Shimla Mall, Jungle Temple, Waterfall, Lighthouses, Resort Villas, Dhabas, Petrol Pumps).
     */
    fun drawRegionalLandmarksAndTrees(
        dc: GlDrawContext,
        playerX: Float,
        playerZ: Float,
        worldTime: Float,
        nightFactor: Float,
        graphicsQuality: GraphicsQuality,
        streamRadius: Float
    ) {
        val pCx = ((playerX + WorldMapData.WORLD_HALF_X) / TERRAIN_CHUNK_SIZE).toInt().coerceIn(0, TERRAIN_GRID_DIM_X - 1)
        val pCz = ((playerZ + WorldMapData.WORLD_HALF_Z) / TERRAIN_CHUNK_SIZE).toInt().coerceIn(0, TERRAIN_GRID_DIM_Z - 1)
        val treeMaxDistSq = (streamRadius + 28f) * (streamRadius + 28f)
        val treeStep = if (graphicsQuality == GraphicsQuality.LOW) 2 else 1

        // 1. Streamed Biome-Specific Trees across Mainland & all 6 Islands
        for (cx in (pCx - 2).coerceAtLeast(0)..(pCx + 2).coerceAtMost(TERRAIN_GRID_DIM_X - 1)) {
            for (cz in (pCz - 2).coerceAtLeast(0)..(pCz + 2).coerceAtMost(TERRAIN_GRID_DIM_Z - 1)) {
                val ch = chunkCache[chunkKey(cx, cz)] ?: continue
                val list = ch.trees
                var i = 0
                while (i < list.size) {
                    val t = list[i]
                    val dx = t.x - playerX
                    val dz = t.z - playerZ
                    if (dx * dx + dz * dz <= treeMaxDistSq) {
                        CityPropDrawer.drawRealisticTree(
                            dc = dc,
                            t = TreeProp(t.x, t.z, t.scale, t.seed, t.kind),
                            worldTime = worldTime,
                            quality = graphicsQuality,
                            groundY = t.y
                        )
                    }
                    i += treeStep
                }
            }
        }

        val landmarkDist = streamRadius + 150f
        fun inRange(x: Float, z: Float, extra: Float = 0f): Boolean {
            val r = landmarkDist + extra
            val dx = x - playerX
            val dz = z - playerZ
            return dx * dx + dz * dz <= r * r
        }

        // 2. Overseas Sea Bridge Concrete Support Piers & Street Lamps (fast O(1) spatial query anywhere in 50x65km!)
        drawBridgePiersAndPylons(dc, playerX, playerZ, landmarkDist, nightFactor)

        // 3. Road-Cleared Regional 3D Landmarks, Island Towns, Ports, Forts, Dhabas, Hotels, Garages & Petrol Pumps
        for (poi in WorldMapData.regionalPois) {
            if (!inRange(poi.x, poi.z, 35f)) continue
            when (poi.category) {
                PoiCategory.PETROL_PUMP -> drawPetrolPumpStation(dc, poi.x, poi.z, nightFactor)
                PoiCategory.DHABA -> drawHighwayDhaba(dc, poi.x, poi.z, nightFactor)
                PoiCategory.GARAGE -> drawServiceGarage(dc, poi.x, poi.z)
                PoiCategory.MILITARY -> drawNorthernMilitaryPlateau(dc, poi.x, poi.z, worldTime)
                PoiCategory.HELIPAD_DOCK -> drawSmugglerCoveBase(dc, poi.x, poi.z)
                PoiCategory.TOWN -> {
                    when (poi.biome) {
                        WorldBiome.SNOW_PEAKS -> drawShimlaMallStation(dc, poi.x, poi.z, nightFactor)
                        WorldBiome.THAR_DESERT -> drawHeritageHaveliTown(dc, poi.x, poi.z)
                        WorldBiome.VILLAGE_PLAINS -> drawGramChaupalVillage(dc, poi.x, poi.z)
                        else -> drawLuxuryResortIsland(dc, poi.x, poi.z, nightFactor)
                    }
                }
                PoiCategory.LANDMARK -> {
                    when {
                        poi.id.contains("lighthouse") || poi.id.contains("fort") -> drawCoastalLighthouse(dc, poi.x, poi.z, worldTime, nightFactor)
                        poi.id.contains("temple") || poi.id.contains("ruins") -> drawAncientJungleTemple(dc, poi.x, poi.z)
                        poi.id.contains("wind") -> drawWindTurbineFarm(dc, poi.x, poi.z, worldTime)
                        poi.id.contains("beach") || poi.id.contains("strip") -> drawGoaBeachShacks(dc, poi.x, poi.z)
                        poi.biome == WorldBiome.THAR_DESERT -> drawSandWatchtower(dc, poi.x, poi.z)
                        else -> drawLuxuryResortIsland(dc, poi.x, poi.z, nightFactor)
                    }
                }
            }
        }
    }

    // =========================================================================
    // ROLLING PLAYER-CENTERED MACRO HORIZON LOD1 + HIGH-RES CHUNK MESH BUILDER
    // =========================================================================

    private fun buildDistantHorizonLod(centerX: Float, centerZ: Float) {
        if (distantLodVboId != 0) {
            GLES20.glDeleteBuffers(1, intArrayOf(distantLodVboId), 0)
            distantLodVboId = 0
            distantLodVertCount = 0
        }
        val snappedCx = floor(centerX / 160f) * 160f
        val snappedCz = floor(centerZ / 160f) * 160f
        lodCenterX = snappedCx
        lodCenterZ = snappedCz

        val cells = 44 // 44 x 80m = 3520m x 3520m rolling distant vista around the player!
        val step = 80f
        val halfSpan = cells * step * 0.5f
        val originX = snappedCx - halfSpan
        val originZ = snappedCz - halfSpan
        val gridN = cells + 1
        val heights = FloatArray(gridN * gridN)
        val normals = FloatArray(gridN * gridN * 3)
        val colors = FloatArray(gridN * gridN * 3)
        val tmpN = FloatArray(3)
        val tmpC = FloatArray(3)

        for (gz in 0 until gridN) {
            val wz = originZ + gz * step
            for (gx in 0 until gridN) {
                val wx = originX + gx * step
                val idx = gz * gridN + gx
                val h = WorldMapData.getTerrainHeight(wx, wz)
                heights[idx] = h - 2.4f
                WorldMapData.getTerrainNormal(wx, wz, tmpN)
                normals[idx * 3] = tmpN[0]
                normals[idx * 3 + 1] = tmpN[1]
                normals[idx * 3 + 2] = tmpN[2]
                WorldMapData.getTerrainSurfaceColor(wx, wz, h, tmpN[1], tmpC)
                colors[idx * 3] = tmpC[0]
                colors[idx * 3 + 1] = tmpC[1]
                colors[idx * 3 + 2] = tmpC[2]
            }
        }

        val verts = ArrayList<Float>(cells * cells * 4 * FLOATS_PER_VERT)
        fun emitV(gx: Int, gz: Int) {
            val wx = originX + gx * step
            val wz = originZ + gz * step
            val i = gz * gridN + gx
            verts.add(wx); verts.add(heights[i]); verts.add(wz)
            verts.add(normals[i * 3]); verts.add(normals[i * 3 + 1]); verts.add(normals[i * 3 + 2])
            verts.add(colors[i * 3]); verts.add(colors[i * 3 + 1]); verts.add(colors[i * 3 + 2])
        }

        for (gz in 0 until cells) {
            for (gx in 0 until cells) {
                val i00 = gz * gridN + gx
                val i10 = i00 + 1
                val i01 = i00 + gridN
                val i11 = i01 + 1
                if (heights[i00] < -3.0f && heights[i10] < -3.0f && heights[i01] < -3.0f && heights[i11] < -3.0f) continue
                emitV(gx, gz); emitV(gx, gz + 1); emitV(gx + 1, gz + 1)
                emitV(gx, gz); emitV(gx + 1, gz + 1); emitV(gx + 1, gz)
            }
        }

        if (verts.isNotEmpty()) {
            val arr = FloatArray(verts.size) { verts[it] }
            distantLodVertCount = arr.size / FLOATS_PER_VERT
            distantLodVboId = uploadFloatArrayVbo(arr, arr.size)
        }
    }

    private fun buildTerrainChunk(cx: Int, cz: Int, key: Long): TerrainChunkGpu {
        val minX = -WorldMapData.WORLD_HALF_X + cx * TERRAIN_CHUNK_SIZE
        val minZ = -WorldMapData.WORLD_HALF_Z + cz * TERRAIN_CHUNK_SIZE
        val centerX = minX + TERRAIN_CHUNK_SIZE * 0.5f
        val centerZ = minZ + TERRAIN_CHUNK_SIZE * 0.5f
        val cellStep = TERRAIN_CHUNK_SIZE / GRID_CELLS

        val gridN = GRID_CELLS + 1
        val heights = FloatArray(gridN * gridN)
        val normals = FloatArray(gridN * gridN * 3)
        val colors = FloatArray(gridN * gridN * 3)
        val tmpN = FloatArray(3)
        val tmpC = FloatArray(3)

        var anyAboveOcean = false
        for (gz in 0 until gridN) {
            val wz = minZ + gz * cellStep
            for (gx in 0 until gridN) {
                val wx = minX + gx * cellStep
                val idx = gz * gridN + gx
                val h = WorldMapData.getTerrainHeightForMesh(wx, wz)
                // Keep Saini Metro downtown core slightly recessed (-0.018f) beneath existing textured downtown pads
                val metroW = WorldMapData.getSainiMetroFlatWeight(wx, wz)
                val finalH = if (metroW > 0.96f && h >= -0.02f) -0.018f else h
                heights[idx] = finalH
                if (finalH > WorldMapData.WATER_LEVEL - 0.8f) anyAboveOcean = true

                WorldMapData.getTerrainNormal(wx, wz, tmpN)
                normals[idx * 3] = tmpN[0]
                normals[idx * 3 + 1] = tmpN[1]
                normals[idx * 3 + 2] = tmpN[2]

                WorldMapData.getTerrainSurfaceColor(wx, wz, h, tmpN[1], tmpC)
                colors[idx * 3] = tmpC[0]
                colors[idx * 3 + 1] = tmpC[1]
                colors[idx * 3 + 2] = tmpC[2]
            }
        }

        // Skip generating terrain VBO for pure deep-ocean chunks far from land (saves GPU!)
        var terrainVbo = 0
        var terrainVertCount = 0
        if (anyAboveOcean) {
            var outIdx = 0
            fun putVert(gx: Int, gz: Int) {
                val wx = minX + gx * cellStep
                val wz = minZ + gz * cellStep
                val i = gz * gridN + gx
                scratchTerrainFloats[outIdx++] = wx
                scratchTerrainFloats[outIdx++] = heights[i]
                scratchTerrainFloats[outIdx++] = wz
                scratchTerrainFloats[outIdx++] = normals[i * 3]
                scratchTerrainFloats[outIdx++] = normals[i * 3 + 1]
                scratchTerrainFloats[outIdx++] = normals[i * 3 + 2]
                scratchTerrainFloats[outIdx++] = colors[i * 3]
                scratchTerrainFloats[outIdx++] = colors[i * 3 + 1]
                scratchTerrainFloats[outIdx++] = colors[i * 3 + 2]
            }

            for (gz in 0 until GRID_CELLS) {
                for (gx in 0 until GRID_CELLS) {
                    // Two CCW triangles per quad cell
                    putVert(gx, gz)
                    putVert(gx, gz + 1)
                    putVert(gx + 1, gz + 1)

                    putVert(gx, gz)
                    putVert(gx + 1, gz + 1)
                    putVert(gx + 1, gz)
                }
            }
            terrainVertCount = outIdx / FLOATS_PER_VERT
            terrainVbo = uploadFloatArrayVbo(scratchTerrainFloats, outIdx)
        }

        // Build spline road & bridge deck ribbons intersecting this chunk via fast O(1) spatial lookup!
        scratchRoadList.clear()
        val pad = 20f
        val nearbyRoads = WorldMapData.getRoadSegmentsInBounds(
            minX - pad, minZ - pad,
            minX + TERRAIN_CHUNK_SIZE + pad, minZ + TERRAIN_CHUNK_SIZE + pad
        )
        for (seg in nearbyRoads) {
            val midX = (seg.ax + seg.bx) * 0.5f
            val midZ = (seg.az + seg.bz) * 0.5f
            val ownerCx = ((midX + WorldMapData.WORLD_HALF_X) / TERRAIN_CHUNK_SIZE).toInt().coerceIn(0, TERRAIN_GRID_DIM_X - 1)
            val ownerCz = ((midZ + WorldMapData.WORLD_HALF_Z) / TERRAIN_CHUNK_SIZE).toInt().coerceIn(0, TERRAIN_GRID_DIM_Z - 1)
            if (ownerCx != cx || ownerCz != cz) continue

            emitRoadSegmentRibbon(seg)
        }

        var roadVbo = 0
        var roadVertCount = 0
        if (scratchRoadList.isNotEmpty()) {
            val arr = FloatArray(scratchRoadList.size) { scratchRoadList[it] }
            roadVertCount = arr.size / FLOATS_PER_VERT
            roadVbo = uploadFloatArrayVbo(arr, arr.size)
        }

        // Deterministically place biome-authentic 3D trees in this chunk (strictly outside Saini Metro, roads, water & POIs)
        val trees = buildChunkBiomeTrees(cx, cz, minX, minZ)

        return TerrainChunkGpu(
            key = key,
            cx = cx,
            cz = cz,
            centerX = centerX,
            centerZ = centerZ,
            terrainVboId = terrainVbo,
            terrainVertCount = terrainVertCount,
            roadVboId = roadVbo,
            roadVertCount = roadVertCount,
            trees = trees,
            lastUsedFrame = frameCounter
        )
    }

    private fun emitRoadSegmentRibbon(seg: RoadSegFlat) {
        val len = seg.length.coerceAtLeast(0.01f)
        val dirX = seg.dx / len
        val dirZ = seg.dz / len
        val perpX = -dirZ
        val perpZ = dirX
        val hw = seg.roadClass.halfWidth
        // Extend ends slightly by 0.65m so curved corners join seamlessly with zero hairline cracks!
        val ax = seg.ax - dirX * 0.65f
        val az = seg.az - dirZ * 0.65f
        val bx = seg.bx + dirX * 0.65f
        val bz = seg.bz + dirZ * 0.65f
        val ay = seg.ay + 0.08f
        val by = seg.by + 0.08f

        val (r, g, b) = when (seg.roadClass) {
            RoadClass.HIGHWAY, RoadClass.RING_EXPRESSWAY -> Triple(0.20f, 0.21f, 0.23f)
            RoadClass.GHAT_MOUNTAIN -> Triple(0.24f, 0.25f, 0.27f)
            RoadClass.SEA_BRIDGE -> Triple(0.27f, 0.29f, 0.32f)
            RoadClass.DIRT_TRAIL -> Triple(0.56f, 0.44f, 0.30f)
        }

        fun pushV(px: Float, py: Float, pz: Float, nx: Float, ny: Float, nz: Float, cr: Float, cg: Float, cb: Float) {
            scratchRoadList.add(px); scratchRoadList.add(py); scratchRoadList.add(pz)
            scratchRoadList.add(nx); scratchRoadList.add(ny); scratchRoadList.add(nz)
            scratchRoadList.add(cr); scratchRoadList.add(cg); scratchRoadList.add(cb)
        }

        fun pushQuad(
            x0: Float, y0: Float, z0: Float,
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float,
            x3: Float, y3: Float, z3: Float,
            nx: Float, ny: Float, nz: Float,
            cr: Float, cg: Float, cb: Float
        ) {
            pushV(x0, y0, z0, nx, ny, nz, cr, cg, cb)
            pushV(x1, y1, z1, nx, ny, nz, cr, cg, cb)
            pushV(x2, y2, z2, nx, ny, nz, cr, cg, cb)
            pushV(x0, y0, z0, nx, ny, nz, cr, cg, cb)
            pushV(x2, y2, z2, nx, ny, nz, cr, cg, cb)
            pushV(x3, y3, z3, nx, ny, nz, cr, cg, cb)
        }

        // Main road deck quad
        pushQuad(
            ax - perpX * hw, ay, az - perpZ * hw,
            ax + perpX * hw, ay, az + perpZ * hw,
            bx + perpX * hw, by, bz + perpZ * hw,
            bx - perpX * hw, by, bz - perpZ * hw,
            0f, 1f, 0f, r, g, b
        )

        // Crisp center white/amber lane divider stripe on paved highways & bridge
        if (seg.roadClass.isPaved) {
            val sw = 0.22f
            val stripeR = if (seg.roadClass == RoadClass.HIGHWAY || seg.roadClass == RoadClass.SEA_BRIDGE) 0.95f else 0.90f
            val stripeG = if (seg.roadClass == RoadClass.HIGHWAY || seg.roadClass == RoadClass.SEA_BRIDGE) 0.78f else 0.90f
            val stripeB = if (seg.roadClass == RoadClass.HIGHWAY || seg.roadClass == RoadClass.SEA_BRIDGE) 0.22f else 0.88f
            pushQuad(
                ax - perpX * sw, ay + 0.03f, az - perpZ * sw,
                ax + perpX * sw, ay + 0.03f, az + perpZ * sw,
                bx + perpX * sw, by + 0.03f, bz + perpZ * sw,
                bx - perpX * sw, by + 0.03f, bz - perpZ * sw,
                0f, 1f, 0f, stripeR, stripeG, stripeB
            )
            // White edge road shoulder lines
            for (side in floatArrayOf(-1f, 1f)) {
                val edgeOff = side * (hw - 0.45f)
                val ew = 0.14f
                pushQuad(
                    ax + perpX * (edgeOff - ew), ay + 0.025f, az + perpZ * (edgeOff - ew),
                    ax + perpX * (edgeOff + ew), ay + 0.025f, az + perpZ * (edgeOff + ew),
                    bx + perpX * (edgeOff + ew), by + 0.025f, bz + perpZ * (edgeOff + ew),
                    bx + perpX * (edgeOff - ew), by + 0.025f, bz + perpZ * (edgeOff - ew),
                    0f, 1f, 0f, 0.90f, 0.92f, 0.94f
                )
            }
        }

        // Concrete bridge deck side barriers & slab thickness for Overseas Sea Bridge & River Suspension Bridge
        if (seg.roadClass.isBridge) {
            val railH = 0.78f // Low realistic guardrail so player can jump cleanly over the bridge edge and fall!
            val deckThick = 1.05f
            for (side in floatArrayOf(-1f, 1f)) {
                val rx = perpX * (side * (hw + 0.15f))
                val rz = perpZ * (side * (hw + 0.15f))
                // Outer concrete barrier wall
                pushQuad(
                    ax + rx, ay - deckThick, az + rz,
                    bx + rx, by - deckThick, bz + rz,
                    bx + rx, by + railH, bz + rz,
                    ax + rx, ay + railH, az + rz,
                    perpX * side, 0.15f, perpZ * side,
                    0.72f, 0.74f, 0.76f
                )
                // Top rail cap
                val capInX = perpX * (side * (hw - 0.25f))
                val capInZ = perpZ * (side * (hw - 0.25f))
                pushQuad(
                    ax + capInX, ay + railH, az + capInZ,
                    ax + rx, ay + railH, az + rz,
                    bx + rx, by + railH, bz + rz,
                    bx + capInX, by + railH, bz + capInZ,
                    0f, 1f, 0f, 0.80f, 0.82f, 0.84f
                )
            }
        } else {
            // Sloped compacted-gravel road embankment skirts on both edges so roads embed solidly into hills (zero floating edges!)
            val skirtOut = hw + 2.4f
            val skirtDrop = 0.78f
            val sr = if (seg.roadClass.isPaved) 0.38f else 0.48f
            val sg = if (seg.roadClass.isPaved) 0.36f else 0.39f
            val sb = if (seg.roadClass.isPaved) 0.31f else 0.27f
            for (side in floatArrayOf(-1f, 1f)) {
                val inX = perpX * (side * hw)
                val inZ = perpZ * (side * hw)
                val outX = perpX * (side * skirtOut)
                val outZ = perpZ * (side * skirtOut)
                pushQuad(
                    ax + inX, ay, az + inZ,
                    ax + outX, ay - skirtDrop, az + outZ,
                    bx + outX, by - skirtDrop, bz + outZ,
                    bx + inX, by, bz + inZ,
                    perpX * side * 0.45f, 0.89f, perpZ * side * 0.45f,
                    sr, sg, sb
                )
            }
        }
    }

    private fun buildChunkBiomeTrees(cx: Int, cz: Int, minX: Float, minZ: Float): List<RegionalTreeInstance> {
        val list = ArrayList<RegionalTreeInstance>(14)
        // Skip Saini Metro core (already populated with CityWorldData downtown trees!)
        val cX = minX + TERRAIN_CHUNK_SIZE * 0.5f
        val cZ = minZ + TERRAIN_CHUNK_SIZE * 0.5f
        if (cX in -460f..460f && cZ in -440f..420f) return list

        val candidates = 10
        for (i in 0 until candidates) {
            val h1 = hashInt(cx * 73 + i, cz * 97 + i * 3)
            val h2 = hashInt(cx * 131 - i * 5, cz * 59 + i * 7)
            val wx = minX + 8f + ((h1 and 0xFFFF) / 65535f) * (TERRAIN_CHUNK_SIZE - 16f)
            val wz = minZ + 8f + ((h2 and 0xFFFF) / 65535f) * (TERRAIN_CHUNK_SIZE - 16f)

            if (WorldMapData.getSainiMetroFlatWeight(wx, wz) > 0.35f) continue
            if (WorldMapData.isWater(wx, wz)) continue
            if (WorldMapData.getDistanceToRoad(wx, wz) < 12.5f) continue

            val y = WorldMapData.getTerrainHeight(wx, wz)
            if (y < 0.35f) continue

            // Keep clear of regional landmarks & POIs
            var tooClose = false
            for (poi in WorldMapData.regionalPois) {
                if (hypot(wx - poi.x, wz - poi.z) < 28f) { tooClose = true; break }
            }
            if (tooClose) continue

            val biome = WorldMapData.getBiomeAt(wx, wz)
            val roll = (h1 ushr 16) and 0xFF
            val (spawnChance, kind, scaleBase) = when (biome) {
                WorldBiome.JUNGLE_RIVER -> Triple(220, TreeKind.BROAD, 1.35f)
                WorldBiome.SNOW_PEAKS -> {
                    // Pine trees on mountain slopes below snowline (+12m..+86m)
                    if (y > 88f) continue
                    Triple(165, TreeKind.PINE, 1.25f)
                }
                WorldBiome.KONKAN_COAST -> Triple(135, TreeKind.PALM, 1.20f)
                WorldBiome.SAINI_KEYS -> Triple(155, TreeKind.PALM, 1.18f)
                WorldBiome.VILLAGE_PLAINS -> Triple(68, TreeKind.BROAD, 1.12f)
                WorldBiome.THAR_DESERT -> {
                    // Sparse desert oasis palms only near Oasis Village
                    val nearOasis = hypot(wx + 1040f, wz + 210f) < 115f
                    if (!nearOasis) continue
                    Triple(135, TreeKind.PALM, 1.05f)
                }
                WorldBiome.SAINI_METRO -> continue
            }

            if (roll < spawnChance) {
                val sc = scaleBase + ((h2 ushr 16) and 0x1F) / 31f * 0.42f
                list.add(RegionalTreeInstance(wx, y - 0.15f, wz, sc, (h1 xor h2) and 0x7FFF, kind))
            }
        }
        return list
    }

    private fun uploadFloatArrayVbo(data: FloatArray, count: Int): Int {
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        val vboId = ids[0]
        val fb = ByteBuffer.allocateDirect(count * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data, 0, count)
                position(0)
            }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, count * 4, fb, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        return vboId
    }

    private fun hashInt(a: Int, b: Int): Int {
        var h = a * 374761393 + b * 668265263 + 1013904223
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and 0x7FFFFFFF
    }

    // =========================================================================
    // ICONIC REGIONAL 3D LANDMARK DRAWERS (ALL 7 BIOMES)
    // =========================================================================

    private fun drawBridgePiersAndPylons(dc: GlDrawContext, px: Float, pz: Float, maxDist: Float, nightFactor: Float) {
        val maxDSq = maxDist * maxDist
        val nearbyRoads = WorldMapData.getRoadSegmentsInBounds(px - maxDist, pz - maxDist, px + maxDist, pz + maxDist)
        if (nearbyRoads.isEmpty()) return
        dc.bindVbo(dc.assets.cubeMesh)
        for ((idx, seg) in nearbyRoads.withIndex()) {
            if (!seg.isBridge) continue
            val mx = (seg.ax + seg.bx) * 0.5f
            val mz = (seg.az + seg.bz) * 0.5f
            val dx = mx - px; val dz = mz - pz
            if (dx * dx + dz * dz > maxDSq) continue
            val deckY = (seg.ay + seg.by) * 0.5f
            if (deckY > 2.2f) {
                val pierH = deckY + 6.5f
                val len = seg.length.coerceAtLeast(0.1f)
                val perpX = -seg.dz / len
                val perpZ = seg.dx / len
                // Twin reinforced concrete ocean piers
                dc.drawBox(mx - perpX * 4.5f, deckY - pierH * 0.5f - 0.6f, mz - perpZ * 4.5f, 2.2f, pierH, 2.2f, dc.assets.texConcrete, 1f, 3f, 0.72f, 0.74f, 0.76f, 0f, 0.10f, 12f, 0.02f)
                dc.drawBox(mx + perpX * 4.5f, deckY - pierH * 0.5f - 0.6f, mz + perpZ * 4.5f, 2.2f, pierH, 2.2f, dc.assets.texConcrete, 1f, 3f, 0.72f, 0.74f, 0.76f, 0f, 0.10f, 12f, 0.02f)
                // Highway bridge lamp post every 2nd span & widened scenic rest pull-off deck at iconic ocean viewpoints
                if (idx % 2 == 0) {
                    val lx = mx + perpX * 6.8f
                    val lz = mz + perpZ * 6.8f
                    dc.drawBox(lx, deckY + 3.2f, lz, 0.24f, 6.4f, 0.24f, dc.assets.texWhite, 1f, 1f, 0.38f, 0.42f, 0.48f, 0f, 0.4f, 20f, 0.1f)
                    val em = 0.25f + nightFactor * 0.75f
                    dc.drawBox(lx - perpX * 0.9f, deckY + 6.2f, lz - perpZ * 0.9f, 0.85f, 0.22f, 0.42f, dc.assets.texWhite, 1f, 1f, 1.0f, 0.94f, 0.78f, em, 0.2f, 12f, 0f)
                }
                if (idx % 9 == 4) {
                    val pullX = mx + perpX * 10.2f
                    val pullZ = mz + perpZ * 10.2f
                    dc.drawBox(pullX, deckY - 0.35f, pullZ, 7.2f, 0.85f, 14.0f, dc.assets.texConcrete, 2f, 4f, 0.68f, 0.70f, 0.72f, 0f, 0.12f, 12f, 0.02f)
                }
            }
        }
    }

    private fun drawSuspensionBridgeTowers(dc: GlDrawContext) {
        dc.bindVbo(dc.assets.cubeMesh)
        val deckY = WorldMapData.getTerrainHeight(1245f, -240f)
        for (tx in floatArrayOf(1185f, 1300f)) {
            dc.drawBox(tx, deckY + 6.5f, -247.5f, 1.3f, 15f, 1.3f, dc.assets.texHouseRedBrick, 1f, 4f, 0.72f, 0.42f, 0.28f, 0f, 0.12f, 12f, 0f)
            dc.drawBox(tx, deckY + 6.5f, -232.5f, 1.3f, 15f, 1.3f, dc.assets.texHouseRedBrick, 1f, 4f, 0.72f, 0.42f, 0.28f, 0f, 0.12f, 12f, 0f)
            dc.drawBox(tx, deckY + 13.0f, -240f, 1.4f, 1.4f, 16.5f, dc.assets.texHouseRedBrick, 1f, 3f, 0.76f, 0.46f, 0.30f, 0f, 0.12f, 12f, 0f)
        }
    }

    /** Rajasthan Thar Desert — Grand Terracotta Sandstone Saini Garh Fort */
    private fun drawSainiGarhFort(dc: GlDrawContext, cx: Float, cz: Float) {
        val y = WorldMapData.getTerrainHeight(cx, cz)
        val sandstone = dc.assets.texConcrete
        val r = 0.82f; val g = 0.56f; val b = 0.36f
        val dr = 0.70f; val dg = 0.46f; val db = 0.28f

        dc.bindVbo(dc.assets.cubeMesh)
        // Raised sandstone courtyard platform
        dc.drawBox(cx, y - 0.4f, cz, 78f, 1.2f, 78f, sandstone, 14f, 14f, 0.76f, 0.56f, 0.38f, 0f, 0.08f, 10f, 0f)
        // Outer Rampart Walls (North, West, East, and split South Gatehouse!)
        dc.drawBox(cx, y + 6.5f, cz - 36f, 74f, 13f, 4.8f, sandstone, 16f, 3f, r, g, b, 0f, 0.08f, 10f, 0f)
        dc.drawBox(cx - 36f, y + 6.5f, cz, 4.8f, 13f, 74f, sandstone, 16f, 3f, r, g, b, 0f, 0.08f, 10f, 0f)
        dc.drawBox(cx + 36f, y + 6.5f, cz, 4.8f, 13f, 74f, sandstone, 16f, 3f, r, g, b, 0f, 0.08f, 10f, 0f)
        // South Gatehouse with dramatic central entry arch
        dc.drawBox(cx - 21f, y + 6.5f, cz + 36f, 30f, 13f, 4.8f, sandstone, 8f, 3f, r, g, b, 0f, 0.08f, 10f, 0f)
        dc.drawBox(cx + 21f, y + 6.5f, cz + 36f, 30f, 13f, 4.8f, sandstone, 8f, 3f, r, g, b, 0f, 0.08f, 10f, 0f)
        dc.drawBox(cx, y + 11.2f, cz + 36f, 14f, 5.2f, 5.4f, sandstone, 4f, 2f, dr, dg, db, 0f, 0.10f, 12f, 0f)

        // Central Royal Palace Keep inside courtyard
        dc.drawBox(cx, y + 9.5f, cz - 12f, 24f, 19f, 18f, sandstone, 6f, 5f, 0.88f, 0.64f, 0.42f, 0f, 0.10f, 12f, 0f)
        dc.drawBox(cx, y + 20.5f, cz - 12f, 14f, 5f, 12f, sandstone, 4f, 2f, 0.92f, 0.70f, 0.46f, 0f, 0.12f, 14f, 0f)

        // 4 Cylindrical Corner Bastion Towers + Domed Rajasthani Chhatris!
        dc.bindVbo(dc.assets.cylinderMesh)
        for (bx in floatArrayOf(-36f, 36f)) {
            for (bz in floatArrayOf(-36f, 36f)) {
                dc.drawMeshTransformed(cx + bx, y + 8.5f, cz + bz, 10.5f, 17f, 10.5f, 0f, 0f, 0f, sandstone, 4f, 3f, dr, dg, db, 1f, 0f, 0.08f, 10f, 0f, dc.assets.cylinderMesh)
            }
        }
        dc.bindVbo(dc.assets.sphereMesh)
        for (bx in floatArrayOf(-36f, 36f)) {
            for (bz in floatArrayOf(-36f, 36f)) {
                dc.drawMeshTransformed(cx + bx, y + 18.2f, cz + bz, 6.2f, 5.2f, 6.2f, 0f, 0f, 0f, sandstone, 2f, 2f, 0.94f, 0.78f, 0.56f, 1f, 0f, 0.18f, 18f, 0.05f, dc.assets.sphereMesh)
            }
        }
        // Central Palace Dome
        dc.drawMeshTransformed(cx, y + 24.8f, cz - 12f, 8.4f, 6.8f, 8.4f, 0f, 0f, 0f, sandstone, 2f, 2f, 0.96f, 0.82f, 0.58f, 1f, 0f, 0.22f, 20f, 0.08f, dc.assets.sphereMesh)
    }

    private fun drawHeritageHaveliTown(dc: GlDrawContext, cx: Float, cz: Float) {
        val y = WorldMapData.getTerrainHeight(cx, cz)
        dc.bindVbo(dc.assets.cubeMesh)
        val specs = arrayOf(
            floatArrayOf(-20f, -10f, 14f, 9f, 12f),
            floatArrayOf(20f, -8f, 14f, 11f, 12f),
            floatArrayOf(0f, 22f, 15f, 8.5f, 11f)
        )
        for (s in specs) {
            val hy = WorldMapData.getTerrainHeight(cx + s[0], cz + s[1])
            dc.drawBox(cx + s[0], hy + s[3] * 0.5f, cz + s[1], s[2], s[3], s[4], dc.assets.texConcrete, 3f, 2f, 0.88f, 0.68f, 0.44f, 0f, 0.08f, 10f, 0f)
            dc.drawBox(cx + s[0], hy + s[3] + 0.5f, cz + s[1], s[2] + 0.6f, 1.0f, s[4] + 0.6f, dc.assets.texConcrete, 3f, 1f, 0.76f, 0.52f, 0.32f, 0f, 0.08f, 10f, 0f)
        }
        // Sandstone central market arch
        dc.drawBox(cx, y + 3.2f, cz - 14f, 10f, 1.2f, 1.8f, dc.assets.texConcrete, 2f, 1f, 0.86f, 0.62f, 0.38f, 0f, 0.1f, 12f, 0f)
    }

    private fun drawOasisVillage(dc: GlDrawContext, cx: Float, cz: Float) {
        val y = WorldMapData.getTerrainHeight(cx, cz)
        // Turquoise Oasis Pond
        dc.bindVbo(dc.assets.cylinderMesh)
        dc.drawMeshTransformed(cx, y + 0.12f, cz, 22f, 0.22f, 18f, 0f, 0f, 0f, dc.assets.texWaterOcean, 3f, 3f, 0.35f, 0.78f, 0.85f, 1f, 0.04f, 0.85f, 42f, 0.42f, dc.assets.cylinderMesh)
        dc.bindVbo(dc.assets.cubeMesh)
        for ((ox, oz) in listOf(-18f to -6f, 18f to 8f)) {
            val hy = WorldMapData.getTerrainHeight(cx + ox, cz + oz)
            dc.drawBox(cx + ox, hy + 2.8f, cz + oz, 11f, 5.6f, 9f, dc.assets.texConcrete, 2f, 2f, 0.86f, 0.72f, 0.54f, 0f, 0.06f, 8f, 0f)
        }
    }

    private fun drawSandWatchtower(dc: GlDrawContext, cx: Float, cz: Float) {
        val y = WorldMapData.getTerrainHeight(cx, cz)
        dc.bindVbo(dc.assets.cylinderMesh)
        dc.drawMeshTransformed(cx, y + 7.5f, cz, 6.2f, 15f, 6.2f, 0f, 0f, 0f, dc.assets.texConcrete, 3f, 4f, 0.78f, 0.56f, 0.36f, 1f, 0f, 0.08f, 10f, 0f, dc.assets.cylinderMesh)
    }

    /** Jammu & Himachal Snow Peaks — Shimla Mall Road Hill-Station Cottages & Clocktower */
    private fun drawShimlaMallStation(dc: GlDrawContext, cx: Float, cz: Float, nightFactor: Float) {
        val y = WorldMapData.getTerrainHeight(cx, cz)
        dc.bindVbo(dc.assets.cubeMesh)
        // Heritage Alpine Timber Cottages with snow-dusted pitched red/slate roofs
        for ((ox, oz) in listOf(-20f to -14f, 18f to -14f, -18f to 16f)) {
            val hy = WorldMapData.getTerrainHeight(cx + ox, cz + oz)
            dc.drawBox(cx + ox, hy + 3.2f, cz + oz, 13f, 6.4f, 10f, dc.assets.texInteriorWoodFloor, 3f, 2f, 0.78f, 0.68f, 0.56f, 0f, 0.12f, 12f, 0f)
            // Steep gabled red-alpine roof + snow cap
            dc.drawBox(cx + ox, hy + 7.4f, cz + oz, 14.2f, 2.2f, 11.2f, dc.assets.texHouseRedBrick, 3f, 2f, 0.66f, 0.22f, 0.20f, 0f, 0.14f, 14f, 0f)
            dc.drawBox(cx + ox, hy + 8.7f, cz + oz, 13.4f, 0.6f, 10.4f, dc.assets.texWhite, 1f, 1f, 0.94f, 0.96f, 0.99f, 0f, 0.22f, 18f, 0f)
        }
        // Shimla Ridge Heritage Clocktower
        dc.drawBox(cx, y + 8.0f, cz, 5.2f, 16f, 5.2f, dc.assets.texConcrete, 2f, 4f, 0.88f, 0.84f, 0.76f, 0f, 0.12f, 12f, 0f)
        dc.drawBox(cx, y + 17.5f, cz, 4.2f, 3.2f, 4.2f, dc.assets.texHouseRedBrick, 1f, 1f, 0.68f, 0.20f, 0.18f, 0f, 0.18f, 16f, 0f)
        val glow = 0.35f + nightFactor * 0.65f
        dc.drawBox(cx, y + 13.5f, cz + 2.7f, 2.2f, 2.2f, 0.2f, dc.assets.texWhite, 1f, 1f, 1.0f, 0.96f, 0.82f, glow, 0.2f, 12f, 0f)
    }

    private fun drawMountainViewpointDeck(dc: GlDrawContext, cx: Float, cz: Float) {
        val y = WorldMapData.getTerrainHeight(cx, cz)
        dc.bindVbo(dc.assets.cubeMesh)
        dc.drawBox(cx, y + 0.4f, cz, 18f, 0.8f, 14f, dc.assets.texInteriorWoodFloor, 4f, 3f, 0.74f, 0.62f, 0.48f, 0f, 0.12f, 12f, 0f)
        // Tall Himalayan Prayer Flag / Indian Tricolor Mast
        dc.drawBox(cx, y + 7.5f, cz - 5f, 0.28f, 15f, 0.28f, dc.assets.texWhite, 1f, 1f, 0.85f, 0.88f, 0.92f, 0f, 0.5f, 24f, 0.15f)
        dc.drawBox(cx + 2.1f, y + 13.8f, cz - 5f, 4.0f, 0.7f, 0.12f, dc.assets.texWhite, 1f, 1f, 1.0f, 0.56f, 0.18f, 0.15f, 0.1f, 8f, 0f)
        dc.drawBox(cx + 2.1f, y + 13.1f, cz - 5f, 4.0f, 0.7f, 0.12f, dc.assets.texWhite, 1f, 1f, 0.98f, 0.98f, 0.99f, 0.15f, 0.1f, 8f, 0f)
        dc.drawBox(cx + 2.1f, y + 12.4f, cz - 5f, 4.0f, 0.7f, 0.12f, dc.assets.texWhite, 1f, 1f, 0.12f, 0.58f, 0.26f, 0.15f, 0.1f, 8f, 0f)
    }

    private fun drawNorthernMilitaryPlateau(dc: GlDrawContext, cx: Float, cz: Float, worldTime: Float) {
        val y = WorldMapData.getTerrainHeight(cx, cz)
        dc.bindVbo(dc.assets.quadMesh)
        dc.drawQuad(cx, y + 0.08f, cz, 110f, 84f, dc.assets.texConcrete, 18f, 14f, 0.64f, 0.66f, 0.62f, 0f, 0.10f, 12f, 0.03f)
        dc.bindVbo(dc.assets.cubeMesh)
        // Reinforced Fighter Hangar & Command Bunker
        dc.drawBox(cx - 35f, y + 5.5f, cz - 25f, 26f, 11f, 18f, dc.assets.texArmyBunkerConcrete, 4f, 2f, 0.62f, 0.64f, 0.58f, 0f, 0.12f, 12f, 0f)
        dc.drawBox(cx + 35f, y + 4.0f, cz - 25f, 18f, 8f, 14f, dc.assets.texArmyBunkerConcrete, 3f, 2f, 0.58f, 0.62f, 0.55f, 0f, 0.12f, 12f, 0f)
        // Spinning Air-Defense Radar Dish
        dc.drawBox(cx + 35f, y + 10.5f, cz - 25f, 1.4f, 5f, 1.4f, dc.assets.texWhite, 1f, 1f, 0.45f, 0.48f, 0.44f, 0f, 0.3f, 16f, 0.08f)
        dc.drawMeshTransformed(cx + 35f, y + 13.5f, cz - 25f, 6.2f, 2.2f, 0.6f, (worldTime * 48f) % 360f, 0f, 0f, dc.assets.texWhite, 1f, 1f, 0.78f, 0.80f, 0.76f, 1f, 0f, 0.55f, 28f, 0.2f, dc.assets.cubeMesh)
    }

    /** Saini Countryside — Traditional Gram Chaupal Village */
    private fun drawGramChaupalVillage(dc: GlDrawContext, cx: Float, cz: Float) {
        dc.bindVbo(dc.assets.cubeMesh)
        for ((ox, oz) in listOf(-18f to -12f, 18f to -14f, -16f to 16f)) {
            val hy = WorldMapData.getTerrainHeight(cx + ox, cz + oz)
            dc.drawBox(cx + ox, hy + 2.6f, cz + oz, 12f, 5.2f, 10f, dc.assets.texHouseRedBrick, 3f, 2f, 0.88f, 0.72f, 0.62f, 0f, 0.08f, 10f, 0f)
            dc.drawBox(cx + ox, hy + 5.5f, cz + oz, 12.8f, 0.65f, 10.8f, dc.assets.texConcrete, 3f, 3f, 0.74f, 0.72f, 0.68f, 0f, 0.08f, 10f, 0f)
        }
        // Village Overhead Water Tank Tower
        val wy = WorldMapData.getTerrainHeight(cx + 22f, cz + 18f)
        dc.bindVbo(dc.assets.cylinderMesh)
        dc.drawMeshTransformed(cx + 22f, wy + 7f, cz + 18f, 2.2f, 14f, 2.2f, 0f, 0f, 0f, dc.assets.texConcrete, 1f, 3f, 0.76f, 0.78f, 0.80f, 1f, 0f, 0.1f, 12f, 0f, dc.assets.cylinderMesh)
        dc.drawMeshTransformed(cx + 22f, wy + 15.5f, cz + 18f, 8.2f, 4.8f, 8.2f, 0f, 0f, 0f, dc.assets.texConcrete, 3f, 2f, 0.84f, 0.86f, 0.88f, 1f, 0f, 0.15f, 16f, 0f, dc.assets.cylinderMesh)
    }

    /** Bharat Wind Farm Ridge — Towering Spinning Wind Turbines */
    private fun drawWindTurbineFarm(dc: GlDrawContext, cx: Float, cz: Float, worldTime: Float) {
        val offsets = arrayOf(-45f to -28f, 0f to 0f, 48f to 22f, 22f to -46f)
        for ((idx, off) in offsets.withIndex()) {
            val tx = cx + off.first
            val tz = cz + off.second
            val ty = WorldMapData.getTerrainHeight(tx, tz)
            dc.bindVbo(dc.assets.taperedCylinderMesh)
            dc.drawMeshTransformed(tx, ty + 18f, tz, 1.9f, 36f, 1.9f, 0f, 0f, 0f, dc.assets.texWhite, 1f, 1f, 0.92f, 0.94f, 0.96f, 1f, 0f, 0.38f, 24f, 0.12f, dc.assets.taperedCylinderMesh)
            dc.bindVbo(dc.assets.cubeMesh)
            dc.drawBox(tx, ty + 36.2f, tz + 0.8f, 2.2f, 2.2f, 4.6f, dc.assets.texWhite, 1f, 1f, 0.94f, 0.95f, 0.97f, 0f, 0.42f, 28f, 0.14f)
            // 3 Rotating Turbine Blades
            val spinBase = (worldTime * 54f + idx * 45f) % 360f
            for (b in 0 until 3) {
                val angDeg = spinBase + b * 120f
                val rad = angDeg * PI.toFloat() / 180f
                val bx = tx + cos(rad) * 7.2f
                val by = ty + 36.2f + sin(rad) * 7.2f
                dc.drawMeshTransformed(
                    bx, by, tz + 3.2f,
                    14.4f, 0.85f, 0.22f,
                    0f, 0f, angDeg,
                    dc.assets.texWhite, 1f, 1f, 0.96f, 0.97f, 0.98f, 1f, 0f, 0.45f, 28f, 0.15f, dc.assets.cubeMesh
                )
            }
        }
    }

    /** Dense Jungle — Ancient Stepped Stone Temple Ruins */
    private fun drawAncientJungleTemple(dc: GlDrawContext, cx: Float, cz: Float) {
        val y = WorldMapData.getTerrainHeight(cx, cz)
        val stone = dc.assets.texConcrete
        dc.bindVbo(dc.assets.cubeMesh)
        // Stepped Shikhara Pyramid Tiers (moss-tinted ancient carved granite)
        dc.drawBox(cx, y + 1.5f, cz, 22f, 3.0f, 22f, stone, 6f, 2f, 0.56f, 0.58f, 0.48f, 0f, 0.08f, 10f, 0f)
        dc.drawBox(cx, y + 5.0f, cz, 16f, 4.4f, 16f, stone, 5f, 2f, 0.52f, 0.55f, 0.45f, 0f, 0.08f, 10f, 0f)
        dc.drawBox(cx, y + 9.5f, cz, 11f, 5.0f, 11f, stone, 4f, 2f, 0.50f, 0.54f, 0.44f, 0f, 0.08f, 10f, 0f)
        dc.drawBox(cx, y + 14.0f, cz, 6.5f, 4.4f, 6.5f, stone, 3f, 2f, 0.58f, 0.56f, 0.44f, 0f, 0.10f, 12f, 0f)
        dc.drawBox(cx, y + 17.5f, cz, 2.8f, 3.0f, 2.8f, stone, 1f, 1f, 0.72f, 0.62f, 0.36f, 0f, 0.25f, 20f, 0.08f)
    }

    /** Dense Jungle — Animated Cascading Waterfall Cliff */
    private fun drawJungleWaterfall(dc: GlDrawContext, cx: Float, cz: Float, worldTime: Float) {
        val baseY = WorldMapData.getTerrainHeight(cx, cz)
        val vScroll = (-worldTime * 0.65f) % 1f
        dc.bindVbo(dc.assets.verticalQuadMesh)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        dc.drawMeshTransformed(
            cx, baseY + 13.5f, cz - 14f,
            18f, 28f, 1f,
            0f, 12f, 0f,
            dc.assets.texWaterOcean, 2f, 3f,
            0.84f, 0.94f, 1.0f, 0.88f,
            0.22f, 0.85f, 42f, 0.45f, dc.assets.verticalQuadMesh
        )
        // White waterfall mist foam at plunge pool base
        val pulse = 1f + sin(worldTime * 3.2f) * 0.08f
        dc.bindVbo(dc.assets.sphereMesh)
        dc.drawMeshTransformed(
            cx, baseY + 1.2f, cz - 8f,
            19f * pulse, 3.8f, 11f * pulse,
            0f, 0f, 0f,
            dc.assets.texWhite, 1f, 1f, 0.92f, 0.96f, 1.0f, 0.55f, 0.18f, 0.2f, 12f, 0f, dc.assets.sphereMesh
        )
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        if (vScroll > 999f) Unit // keep compiler happy
    }

    private fun drawForestCheckpost(dc: GlDrawContext, cx: Float, cz: Float) {
        val y = WorldMapData.getTerrainHeight(cx, cz)
        dc.bindVbo(dc.assets.cubeMesh)
        dc.drawBox(cx, y + 2.4f, cz - 14f, 10f, 4.8f, 7.6f, dc.assets.texInteriorWoodFloor, 3f, 2f, 0.62f, 0.50f, 0.36f, 0f, 0.10f, 10f, 0f)
        dc.drawBox(cx, y + 5.1f, cz - 14f, 11.4f, 0.7f, 8.8f, dc.assets.texArmyCamo, 3f, 2f, 0.28f, 0.42f, 0.24f, 0f, 0.10f, 10f, 0f)
    }

    /** Goa / Konkan Coast — Colorful Sunset Beach Shacks */
    private fun drawGoaBeachShacks(dc: GlDrawContext, cx: Float, cz: Float) {
        dc.bindVbo(dc.assets.cubeMesh)
        val tints = arrayOf(
            Triple(0.18f, 0.68f, 0.78f),
            Triple(0.88f, 0.44f, 0.26f),
            Triple(0.24f, 0.66f, 0.44f)
        )
        for (i in 0 until 3) {
            val sx = cx + (i - 1) * 18f
            val sz = cz
            val y = WorldMapData.getTerrainHeight(sx, sz)
            val (r, g, b) = tints[i]
            dc.drawBox(sx, y + 2.1f, sz, 9.5f, 4.2f, 7.2f, dc.assets.texInteriorWoodFloor, 2f, 2f, r, g, b, 0f, 0.10f, 12f, 0f)
            // Thatched beach roof
            dc.drawBox(sx, y + 4.6f, sz, 11.2f, 1.1f, 8.8f, dc.assets.texConcrete, 3f, 2f, 0.82f, 0.68f, 0.42f, 0f, 0.06f, 8f, 0f)
        }
    }

    /** Coastal Cliffside Lighthouse (Red-and-White Striped Tower + Rotating Beacon) */
    private fun drawCoastalLighthouse(dc: GlDrawContext, cx: Float, cz: Float, worldTime: Float, nightFactor: Float) {
        val y = WorldMapData.getTerrainHeight(cx, cz)
        dc.bindVbo(dc.assets.cylinderMesh)
        // Alternating Red & White Lighthouse Bands
        dc.drawMeshTransformed(cx, y + 3.5f, cz, 7.6f, 7.0f, 7.6f, 0f, 0f, 0f, dc.assets.texWhite, 1f, 1f, 0.94f, 0.95f, 0.97f, 1f, 0f, 0.22f, 18f, 0.05f, dc.assets.cylinderMesh)
        dc.drawMeshTransformed(cx, y + 10.5f, cz, 6.8f, 7.0f, 6.8f, 0f, 0f, 0f, dc.assets.texWhite, 1f, 1f, 0.86f, 0.16f, 0.18f, 1f, 0f, 0.22f, 18f, 0.05f, dc.assets.cylinderMesh)
        dc.drawMeshTransformed(cx, y + 17.5f, cz, 6.0f, 7.0f, 6.0f, 0f, 0f, 0f, dc.assets.texWhite, 1f, 1f, 0.94f, 0.95f, 0.97f, 1f, 0f, 0.22f, 18f, 0.05f, dc.assets.cylinderMesh)
        dc.drawMeshTransformed(cx, y + 23.0f, cz, 5.4f, 4.0f, 5.4f, 0f, 0f, 0f, dc.assets.texWhite, 1f, 1f, 0.86f, 0.16f, 0.18f, 1f, 0f, 0.22f, 18f, 0.05f, dc.assets.cylinderMesh)
        // Lantern room & rotating glowing light beam
        val em = 0.60f + nightFactor * 0.40f
        dc.drawMeshTransformed(cx, y + 26.8f, cz, 4.4f, 3.6f, 4.4f, 0f, 0f, 0f, dc.assets.texWhite, 1f, 1f, 1.0f, 0.96f, 0.78f, 1f, em, 0.9f, 48f, 0.4f, dc.assets.cylinderMesh)
        dc.bindVbo(dc.assets.sphereMesh)
        dc.drawMeshTransformed(cx, y + 29.5f, cz, 4.8f, 3.2f, 4.8f, 0f, 0f, 0f, dc.assets.texWhite, 1f, 1f, 0.84f, 0.16f, 0.18f, 1f, 0f, 0.3f, 20f, 0.1f, dc.assets.sphereMesh)
        // Sweeping Beacon Beam
        val yaw = (worldTime * 42f) % 360f
        val rad = yaw * PI.toFloat() / 180f
        dc.bindVbo(dc.assets.cubeMesh)
        dc.drawMeshTransformed(
            cx + sin(rad) * 14f, y + 26.8f, cz + cos(rad) * 14f,
            1.6f, 1.6f, 26f, yaw, 0f, 0f,
            dc.assets.texWhite, 1f, 1f, 1.0f, 0.96f, 0.74f, 0.26f + nightFactor * 0.22f,
            1.0f, 0.1f, 8f, 0f, dc.assets.cubeMesh
        )
    }

    private fun drawFishingJetty(dc: GlDrawContext, cx: Float, cz: Float) {
        val y = WorldMapData.getTerrainHeight(cx, cz).coerceAtLeast(0.45f)
        dc.bindVbo(dc.assets.cubeMesh)
        dc.drawBox(cx, y, cz + 18f, 7.5f, 0.55f, 38f, dc.assets.texInteriorWoodFloor, 2f, 10f, 0.72f, 0.56f, 0.40f, 0f, 0.14f, 12f, 0f)
    }

    /** Saini Keys Archipelago — Luxury Resort Island Modern White Villas & Pool Deck */
    private fun drawLuxuryResortIsland(dc: GlDrawContext, cx: Float, cz: Float, nightFactor: Float) {
        val y = WorldMapData.getTerrainHeight(cx, cz)
        dc.bindVbo(dc.assets.cubeMesh)
        // Turquoise infinity pool terrace
        dc.drawBox(cx, y + 0.35f, cz + 16f, 26f, 0.7f, 14f, dc.assets.texConcrete, 6f, 4f, 0.92f, 0.90f, 0.86f, 0f, 0.18f, 18f, 0.05f)
        dc.drawBox(cx, y + 0.74f, cz + 16f, 20f, 0.15f, 9f, dc.assets.texWaterOcean, 3f, 2f, 0.16f, 0.82f, 0.92f, 0.18f + nightFactor * 0.25f, 0.9f, 48f, 0.45f)
        // Twin White Miami/Goa-style Modern Resort Villas
        for (ox in floatArrayOf(-23f, 27f)) {
            val vy = WorldMapData.getTerrainHeight(cx + ox, cz - 6f)
            dc.drawBox(cx + ox, vy + 4.2f, cz - 6f, 16f, 8.4f, 12f, dc.assets.texWhite, 1f, 1f, 0.96f, 0.96f, 0.95f, 0f, 0.24f, 20f, 0.08f)
            dc.drawBox(cx + ox, vy + 4.2f, cz + 0.15f, 13f, 5.2f, 0.35f, dc.assets.texBuildingGlassFacade, 2f, 1f, 0.36f, 0.62f, 0.82f, nightFactor * 0.45f, 0.92f, 56f, 0.50f)
        }
    }

    private fun drawSmugglerCoveBase(dc: GlDrawContext, cx: Float, cz: Float) {
        val y = WorldMapData.getTerrainHeight(cx, cz)
        dc.bindVbo(dc.assets.quadMesh)
        dc.drawQuad(cx, y + 0.08f, cz, 16f, 16f, dc.assets.texArmyHelipad, 1f, 1f, 0.92f, 0.92f, 0.92f, 0f, 0.15f, 14f, 0.04f)
        dc.bindVbo(dc.assets.cubeMesh)
        dc.drawBox(cx + 17f, y + 3.8f, cz - 10f, 16f, 7.6f, 12f, dc.assets.texShopShutter, 3f, 2f, 0.56f, 0.60f, 0.64f, 0f, 0.24f, 20f, 0.08f)
    }

    private fun drawPetrolPumpStation(dc: GlDrawContext, cx: Float, cz: Float, nightFactor: Float) {
        val y = WorldMapData.getTerrainHeight(cx, cz)
        dc.bindVbo(dc.assets.cubeMesh)
        dc.drawBox(cx, y + 0.18f, cz, 16f, 0.36f, 12f, dc.assets.texConcrete, 4f, 3f, 0.70f, 0.72f, 0.74f, 0f, 0.12f, 12f, 0f)
        // High Fuel Canopy (Tactical Cyan/Crimson Bharat Fuel trim)
        dc.drawBox(cx, y + 5.6f, cz, 15f, 1.1f, 10.5f, dc.assets.texWhite, 1f, 1f, 0.92f, 0.94f, 0.96f, 0.10f + nightFactor * 0.45f, 0.3f, 20f, 0.1f)
        dc.drawBox(cx, y + 5.6f, cz + 5.3f, 15.2f, 0.55f, 0.2f, dc.assets.texWhite, 1f, 1f, 0.95f, 0.22f, 0.26f, 0.20f + nightFactor * 0.55f, 0.2f, 16f, 0f)
        for (px in floatArrayOf(-5.5f, 5.5f)) {
            dc.drawBox(cx + px, y + 2.8f, cz - 3.5f, 0.65f, 5.6f, 0.65f, dc.assets.texWhite, 1f, 1f, 0.85f, 0.88f, 0.92f, 0f, 0.35f, 20f, 0.1f)
            dc.drawBox(cx + px, y + 2.8f, cz + 3.5f, 0.65f, 5.6f, 0.65f, dc.assets.texWhite, 1f, 1f, 0.85f, 0.88f, 0.92f, 0f, 0.35f, 20f, 0.1f)
        }
        // Fuel Dispenser Islands
        for (fx in floatArrayOf(-2.5f, 2.5f)) {
            dc.drawBox(cx + fx, y + 1.25f, cz, 0.95f, 2.1f, 0.65f, dc.assets.texWhite, 1f, 1f, 0.16f, 0.58f, 0.86f, 0.08f, 0.45f, 24f, 0.15f)
        }
    }

    private fun drawHighwayDhaba(dc: GlDrawContext, cx: Float, cz: Float, nightFactor: Float) {
        val y = WorldMapData.getTerrainHeight(cx, cz)
        dc.bindVbo(dc.assets.cubeMesh)
        dc.drawBox(cx, y + 2.3f, cz - 5f, 13f, 4.6f, 7.5f, dc.assets.texHouseRedBrick, 3f, 2f, 0.86f, 0.72f, 0.52f, 0f, 0.08f, 10f, 0f)
        dc.drawBox(cx, y + 4.8f, cz - 3f, 14.5f, 0.5f, 11.5f, dc.assets.texWhite, 1f, 1f, 0.18f, 0.52f, 0.78f, 0.06f + nightFactor * 0.25f, 0.18f, 14f, 0f)
        // Warm Dhaba string-light signboard
        dc.drawBox(cx, y + 5.4f, cz + 2.6f, 9.0f, 0.85f, 0.25f, dc.assets.texWhite, 1f, 1f, 1.0f, 0.72f, 0.22f, 0.35f + nightFactor * 0.65f, 0.2f, 12f, 0f)
    }

    private fun drawServiceGarage(dc: GlDrawContext, cx: Float, cz: Float) {
        val y = WorldMapData.getTerrainHeight(cx, cz)
        dc.bindVbo(dc.assets.cubeMesh)
        dc.drawBox(cx, y + 3.2f, cz, 14f, 6.4f, 11f, dc.assets.texConcrete, 3f, 2f, 0.48f, 0.52f, 0.58f, 0f, 0.16f, 16f, 0.04f)
        dc.drawBox(cx, y + 2.2f, cz + 5.55f, 8.5f, 4.2f, 0.22f, dc.assets.texShopShutter, 2f, 1f, 0.74f, 0.78f, 0.82f, 0f, 0.35f, 22f, 0.10f)
    }

    /** Jammu & Himachal — Rohtang Mountain Tunnel Pass Portal Arch */
    private fun drawRohtangTunnelPass(dc: GlDrawContext, cx: Float, cz: Float, nightFactor: Float) {
        val y = WorldMapData.getTerrainHeight(cx, cz)
        dc.bindVbo(dc.assets.cubeMesh)
        // Twin concrete mountain tunnel retaining walls + vaulted portal canopy over Ghat Highway
        dc.drawBox(cx - 8.2f, y + 4.4f, cz, 3.2f, 8.8f, 28f, dc.assets.texConcrete, 2f, 6f, 0.56f, 0.58f, 0.60f, 0f, 0.10f, 12f, 0f)
        dc.drawBox(cx + 8.2f, y + 4.4f, cz, 3.2f, 8.8f, 28f, dc.assets.texConcrete, 2f, 6f, 0.56f, 0.58f, 0.60f, 0f, 0.10f, 12f, 0f)
        dc.drawBox(cx, y + 8.8f, cz, 19.8f, 3.4f, 28.5f, dc.assets.texConcrete, 4f, 6f, 0.50f, 0.52f, 0.55f, 0f, 0.08f, 10f, 0f)
        // Snow drift cap on tunnel roof + glowing amber portal safety strips
        dc.drawBox(cx, y + 10.8f, cz, 18.8f, 0.9f, 27.0f, dc.assets.texWhite, 1f, 1f, 0.92f, 0.95f, 0.98f, 0f, 0.18f, 16f, 0f)
        val em = 0.45f + nightFactor * 0.55f
        dc.drawBox(cx, y + 7.2f, cz + 14.3f, 12f, 0.45f, 0.25f, dc.assets.texWhite, 1f, 1f, 1.0f, 0.72f, 0.18f, em, 0.2f, 12f, 0f)
        dc.drawBox(cx, y + 7.2f, cz - 14.3f, 12f, 0.45f, 0.25f, dc.assets.texWhite, 1f, 1f, 1.0f, 0.72f, 0.18f, em, 0.2f, 12f, 0f)
    }

    /** Jammu & Himachal — Chandra Frozen Alpine Lake Wooden Jetty */
    private fun drawFrozenLakeJetty(dc: GlDrawContext, cx: Float, cz: Float) {
        val y = WorldMapData.getTerrainHeight(cx, cz)
        dc.bindVbo(dc.assets.cubeMesh)
        dc.drawBox(cx, y + 0.45f, cz - 12f, 5.2f, 0.45f, 26f, dc.assets.texInteriorWoodFloor, 2f, 8f, 0.68f, 0.54f, 0.40f, 0f, 0.14f, 12f, 0f)
        for (pz in floatArrayOf(-2f, -12f, -22f)) {
            dc.drawBox(cx - 2.3f, y + 1.05f, cz + pz, 0.25f, 1.4f, 0.25f, dc.assets.texInteriorWoodFloor, 1f, 1f, 0.58f, 0.44f, 0.30f, 0f, 0.1f, 10f, 0f)
            dc.drawBox(cx + 2.3f, y + 1.05f, cz + pz, 0.25f, 1.4f, 0.25f, dc.assets.texInteriorWoodFloor, 1f, 1f, 0.58f, 0.44f, 0.30f, 0f, 0.1f, 10f, 0f)
        }
    }

    /** Rajasthan Thar Desert — Indian NHAI Highway Toll Plaza Canopy */
    private fun drawExpresswayTollPlaza(dc: GlDrawContext, cx: Float, cz: Float, nightFactor: Float) {
        val y = WorldMapData.getTerrainHeight(cx, cz)
        dc.bindVbo(dc.assets.cubeMesh)
        // Overhead NHAI Toll Canopy spanning the Desert Expressway
        dc.drawBox(cx, y + 6.4f, cz + 14f, 22f, 1.5f, 7.5f, dc.assets.texWhite, 1f, 1f, 0.90f, 0.93f, 0.96f, 0.12f + nightFactor * 0.45f, 0.3f, 20f, 0.1f)
        dc.drawBox(cx, y + 6.4f, cz + 17.8f, 22.2f, 0.65f, 0.2f, dc.assets.texWhite, 1f, 1f, 0.10f, 0.58f, 0.88f, 0.25f + nightFactor * 0.55f, 0.2f, 14f, 0f)
        for (px in floatArrayOf(-9.5f, 0f, 9.5f)) {
            dc.drawBox(cx + px, y + 3.0f, cz + 14f, 1.4f, 6.0f, 3.6f, dc.assets.texConcrete, 1f, 2f, 0.78f, 0.80f, 0.82f, 0f, 0.14f, 14f, 0f)
        }
    }

    /** Saini Countryside — Mustard Khet Irrigation Canal Stone Bridge */
    private fun drawCanalStoneBridge(dc: GlDrawContext, cx: Float, cz: Float) {
        val y = WorldMapData.getTerrainHeight(cx, cz)
        dc.bindVbo(dc.assets.cubeMesh)
        // Blue irrigation canal water strip + rustic stone bridge deck & parapets
        dc.drawBox(cx, y - 0.18f, cz, 42f, 0.15f, 7.5f, dc.assets.texWaterOcean, 6f, 2f, 0.24f, 0.62f, 0.74f, 0.04f, 0.85f, 40f, 0.35f)
        dc.drawBox(cx, y + 0.18f, cz, 9.5f, 0.45f, 11.5f, dc.assets.texConcrete, 3f, 3f, 0.74f, 0.70f, 0.62f, 0f, 0.08f, 10f, 0f)
        dc.drawBox(cx - 4.6f, y + 0.95f, cz, 0.55f, 1.15f, 11.5f, dc.assets.texHouseRedBrick, 1f, 4f, 0.72f, 0.54f, 0.42f, 0f, 0.08f, 10f, 0f)
        dc.drawBox(cx + 4.6f, y + 0.95f, cz, 0.55f, 1.15f, 11.5f, dc.assets.texHouseRedBrick, 1f, 4f, 0.72f, 0.54f, 0.42f, 0f, 0.08f, 10f, 0f)
    }
}
