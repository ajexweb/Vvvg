package com.example.game.engine

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.R
import com.example.game.model.CharacterSkin
import com.example.game.model.CityZones
import com.example.game.model.GameSkins
import com.example.game.model.GameTelemetry
import com.example.game.model.GraphicsQuality
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PlayerInputState {
    @Volatile var moveX: Float = 0f
    @Volatile var moveY: Float = 0f
    @Volatile var isSprinting: Boolean = false
    @Volatile var jumpQueued: Boolean = false
    @Volatile var interactQueued: Boolean = false
    @Volatile var cameraYawDelta: Float = 0f
    @Volatile var cameraPitchDelta: Float = 0f
}

interface GameEngineListener {
    fun onTelemetryUpdate(telemetry: GameTelemetry)
    fun onFootstep(isSprint: Boolean)
    fun onOrbCollected(orbId: Int, newScore: Int)
    fun onZoneDiscovered(zoneId: String, zoneName: String, bonusPoints: Int)
    fun onInteractMessage(message: String)
    fun onJumpPerformed()
}

class OpenGLCityRenderer(
    private val context: Context,
    private val input: PlayerInputState,
    private val listener: GameEngineListener
) : GLSurfaceView.Renderer {

    private lateinit var shader: CityShaderProgram
    private lateinit var cubeBuf: FloatBuffer
    private var cubeVertCount = 0
    private lateinit var quadBuf: FloatBuffer
    private var quadVertCount = 0

    private var texBuilding = 0
    private var texRoad = 0
    private var texGrass = 0
    private var texWhite = 0

    // Pre-allocated matrices to avoid per-frame GC allocations
    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    // Simulation state
    @Volatile var isPaused: Boolean = false
    @Volatile var activeSkin: CharacterSkin = GameSkins.all.first()
    @Volatile var graphicsQuality: GraphicsQuality = GraphicsQuality.HIGH
    @Volatile var cameraSensitivity: Float = 1.0f

    private var playerX = 0f
    private var playerY = 0f
    private var playerZ = 5.4f
    private var playerVelY = 0f
    private var isGrounded = true
    private var playerFacingYaw = PI.toFloat() // Facing Saini Plaza fountain (-Z)

    private var cameraYaw = 0f
    private var cameraPitch = 0.32f
    private var walkCycle = 0f
    private var prevSinCycle = 0f

    private var stamina = 100f
    private var health = 100f
    private var sessionScore = 0
    private var totalDistance = 0f
    private var worldTime = 0f
    private var lastFrameNs = System.nanoTime()
    private var lastTelemetryReportMs = 0L

    private val collectedOrbs = HashSet<Int>()
    private val discoveredZones = HashSet<String>()
    private val cars = CityWorldData.createCars()

    @Volatile
    private var resetQueued = false

    fun queueReset() {
        resetQueued = true
    }

    private fun performReset() {
        playerX = 0f
        playerY = 0f
        playerZ = 5.4f
        playerVelY = 0f
        isGrounded = true
        playerFacingYaw = PI.toFloat()
        cameraYaw = 0f
        cameraPitch = 0.32f
        stamina = 100f
        health = 100f
        sessionScore = 0
        totalDistance = 0f
        collectedOrbs.clear()
        discoveredZones.clear()
        cars.clear()
        cars.addAll(CityWorldData.createCars())
        emitTelemetry()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.56f, 0.76f, 0.93f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        shader = CityShaderProgram()
        val cube = GlMeshFactory.createUnitCubeBuffer()
        cubeBuf = cube.first
        cubeVertCount = cube.second
        val quad = GlMeshFactory.createGroundQuadBuffer()
        quadBuf = quad.first
        quadVertCount = quad.second

        texBuilding = GlTextureHelper.loadDrawableTexture(context, R.drawable.img_tex_building, repeat = true)
        texRoad = GlTextureHelper.loadDrawableTexture(context, R.drawable.img_tex_road, repeat = true)
        texGrass = GlTextureHelper.loadDrawableTexture(context, R.drawable.img_tex_grass, repeat = true)
        texWhite = GlTextureHelper.createSolidWhiteTexture()

        lastFrameNs = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = if (height > 0) width.toFloat() / height.toFloat() else 1.77f
        Matrix.perspectiveM(projMatrix, 0, 56f, aspect, 0.4f, 240f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNs = System.nanoTime()
        val dt = ((nowNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNs = nowNs

        if (resetQueued) {
            resetQueued = false
            performReset()
        }

        if (!isPaused) {
            updateSimulation(dt)
        }

        renderScene()
    }

    private fun updateSimulation(dt: Float) {
        worldTime += dt

        // Consume camera drag deltas smoothly
        val yawDelta = input.cameraYawDelta
        val pitchDelta = input.cameraPitchDelta
        input.cameraYawDelta = 0f
        input.cameraPitchDelta = 0f
        cameraYaw -= yawDelta * 0.0045f * cameraSensitivity
        cameraPitch = (cameraPitch + pitchDelta * 0.0035f * cameraSensitivity).coerceIn(0.14f, 0.85f)

        // Compute world movement vector relative to camera yaw
        val rawX = input.moveX.coerceIn(-1f, 1f)
        val rawY = input.moveY.coerceIn(-1f, 1f)
        val mag = sqrt(rawX * rawX + rawY * rawY)

        val wantsSprint = input.isSprinting && stamina > 5f && mag > 0.15f
        if (wantsSprint) {
            stamina = (stamina - 22f * dt).coerceAtLeast(0f)
        } else {
            stamina = (stamina + 16f * dt).coerceAtMost(100f)
        }

        val skinSpeed = activeSkin.speedMultiplier
        val baseSpeed = if (wantsSprint) 11.2f else 6.4f
        val moveSpeed = baseSpeed * skinSpeed

        var isMovingNow = false
        if (mag > 0.08f) {
            isMovingNow = true
            val nx = rawX / mag.coerceAtLeast(1f)
            val ny = rawY / mag.coerceAtLeast(1f)
            // Forward on screen (ny < 0) moves along camera look vector (-sin(yaw), -cos(yaw))
            // Right on screen (nx > 0) moves along camera right (+cos(yaw), -sin(yaw))
            val forwardX = -sin(cameraYaw)
            val forwardZ = -cos(cameraYaw)
            val rightX = cos(cameraYaw)
            val rightZ = -sin(cameraYaw)

            val worldMoveX = (forwardX * (-ny) + rightX * nx)
            val worldMoveZ = (forwardZ * (-ny) + rightZ * nx)
            val worldMag = sqrt(worldMoveX * worldMoveX + worldMoveZ * worldMoveZ).coerceAtLeast(0.001f)
            val dirX = worldMoveX / worldMag
            val dirZ = worldMoveZ / worldMag

            val stepDist = moveSpeed * mag.coerceAtMost(1f) * dt
            val (resolvedX, resolvedZ) = CityWorldData.resolvePlayerCollision(
                playerX + dirX * stepDist,
                playerZ + dirZ * stepDist
            )
            val movedDx = resolvedX - playerX
            val movedDz = resolvedZ - playerZ
            totalDistance += sqrt(movedDx * movedDx + movedDz * movedDz)
            playerX = resolvedX
            playerZ = resolvedZ

            val targetYaw = atan2(dirX, dirZ)
            playerFacingYaw = lerpAngle(playerFacingYaw, targetYaw, (dt * 12f).coerceAtMost(1f))

            // Stride animation + dynamic footstep trigger
            val cycleRate = if (wantsSprint) 14f else 9.5f
            walkCycle += dt * cycleRate
            val curSin = sin(walkCycle)
            if (isGrounded && ((prevSinCycle < 0f && curSin >= 0f) || (prevSinCycle > 0f && curSin <= 0f))) {
                listener.onFootstep(wantsSprint)
            }
            prevSinCycle = curSin
        } else {
            walkCycle *= 0.85f
            prevSinCycle = 0f
        }

        // Jump physics
        if (input.jumpQueued) {
            input.jumpQueued = false
            if (isGrounded) {
                playerVelY = 9.8f
                isGrounded = false
                listener.onJumpPerformed()
            }
        }
        if (!isGrounded) {
            playerY += playerVelY * dt
            playerVelY -= 24f * dt
            if (playerY <= 0f) {
                playerY = 0f
                playerVelY = 0f
                isGrounded = true
            }
        }

        // Move traffic cars along roads
        for (car in cars) {
            if (car.axisX) {
                car.x += car.speed * car.dir * dt
                if (car.x > car.maxBound) car.x = car.minBound
                if (car.x < car.minBound) car.x = car.maxBound
            } else {
                car.z += car.speed * car.dir * dt
                if (car.z > car.maxBound) car.z = car.minBound
                if (car.z < car.minBound) car.z = car.maxBound
            }
        }

        // Check collectibles auto-pickup & interact
        val interactNow = input.interactQueued
        input.interactQueued = false

        var nearestInteract: String? = null
        for (orb in CityWorldData.collectibles) {
            if (collectedOrbs.contains(orb.id)) continue
            val dx = playerX - orb.x
            val dz = playerZ - orb.z
            val dist = sqrt(dx * dx + dz * dz)
            if (dist < 1.85f || (interactNow && dist < 3.8f)) {
                collectedOrbs.add(orb.id)
                sessionScore += 100
                listener.onOrbCollected(orb.id, sessionScore)
            } else if (dist < 4.5f) {
                nearestInteract = "Collect Energy Star (+100)"
            }
        }

        // Check exploration zones discovery
        for (zone in CityZones.all) {
            val dx = playerX - zone.x
            val dz = playerZ - zone.z
            val dist = sqrt(dx * dx + dz * dz)
            if (dist <= zone.radius && !discoveredZones.contains(zone.id)) {
                discoveredZones.add(zone.id)
                sessionScore += zone.bonusPoints
                listener.onZoneDiscovered(zone.id, zone.name, zone.bonusPoints)
            }
        }

        // Fountain plaza interaction
        val distPlaza = sqrt(playerX * playerX + playerZ * playerZ)
        if (distPlaza < 5.0f) {
            nearestInteract = "Inspect Saini Plaza Fountain"
            if (interactNow) {
                health = 100f
                stamina = 100f
                listener.onInteractMessage("Saini Plaza Fountain: Health & Stamina Restored!")
            }
        } else if (interactNow && nearestInteract == null) {
            val currentZone = CityZones.all.firstOrNull {
                val dx = playerX - it.x
                val dz = playerZ - it.z
                sqrt(dx * dx + dz * dz) <= it.radius + 4f
            }
            if (currentZone != null) {
                listener.onInteractMessage("${currentZone.name}: ${currentZone.description}")
            }
        }

        val nowMs = System.currentTimeMillis()
        if (nowMs - lastTelemetryReportMs >= 80L) {
            lastTelemetryReportMs = nowMs
            emitTelemetry(isMovingNow, wantsSprint, nearestInteract)
        }
    }

    private fun emitTelemetry(
        isMoving: Boolean = false,
        isSprinting: Boolean = false,
        nearbyInteract: String? = null
    ) {
        listener.onTelemetryUpdate(
            GameTelemetry(
                playerX = playerX,
                playerZ = playerZ,
                playerYawRad = playerFacingYaw,
                isMoving = isMoving,
                isSprinting = isSprinting,
                stamina = stamina,
                health = health,
                collectedOrbIds = collectedOrbs.toSet(),
                discoveredZoneIds = discoveredZones.toSet(),
                nearbyInteractTarget = nearbyInteract,
                distanceThisSession = totalDistance,
                sessionScore = sessionScore
            )
        )
    }

    private fun renderScene() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(shader.programId)

        // Third-person camera positioning behind player
        val camDist = 9.5f
        val camX = playerX + sin(cameraYaw) * cos(cameraPitch) * camDist
        val camY = (playerY + 1.8f + sin(cameraPitch) * camDist).coerceAtLeast(1.2f)
        val camZ = playerZ + cos(cameraYaw) * cos(cameraPitch) * camDist

        Matrix.setLookAtM(
            viewMatrix, 0,
            camX, camY, camZ,
            playerX, playerY + 1.55f, playerZ,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        // Global lighting & atmospheric golden-hour fog
        GLES20.glUniform3f(shader.uLightDirLoc, 0.55f, 0.85f, 0.35f)
        GLES20.glUniform3f(shader.uCameraPosLoc, camX, camY, camZ)
        GLES20.glUniform3f(shader.uFogColorLoc, 0.66f, 0.80f, 0.92f)
        GLES20.glUniform1f(shader.uFogStartLoc, 45f)
        GLES20.glUniform1f(shader.uFogEndLoc, graphicsQuality.fogEnd)

        // 1. Grass Terrain Ground
        bindMesh(quadBuf)
        drawQuad(0f, 0f, 0f, 190f, 190f, texGrass, 28f, 28f, 0.95f, 1.0f, 0.95f, 0f)

        // 2. Concrete Sidewalks & Central Plaza Deck
        drawQuad(0f, 0.015f, 0f, 22f, 22f, texWhite, 1f, 1f, 0.68f, 0.70f, 0.74f, 0f)
        for (road in CityWorldData.roads) {
            // Sidewalk border slightly wider & beneath road
            drawQuad(
                road.x, 0.012f, road.z,
                road.width + 3.2f, road.depth + 3.2f,
                texWhite, 1f, 1f, 0.62f, 0.64f, 0.68f, 0f
            )
        }

        // 3. Real Asphalt Roads (using generated img_tex_road)
        for (road in CityWorldData.roads) {
            val uScale = if (road.isVertical) 1f else road.width / 8f
            val vScale = if (road.isVertical) road.depth / 8f else 1f
            drawQuad(
                road.x, 0.028f, road.z,
                road.width, road.depth,
                texRoad, uScale, vScale, 1f, 1f, 1f, 0f
            )
        }

        // 4. Cube-based 3D Geometry
        bindMesh(cubeBuf)

        // Central Saini Plaza Monument Fountain
        drawBox(0f, 0.35f, 0f, 4.6f, 0.7f, 4.6f, texWhite, 1f, 1f, 0.75f, 0.77f, 0.80f, 0f)
        drawBox(0f, 0.75f, 0f, 3.8f, 0.25f, 3.8f, texWhite, 1f, 1f, 0.15f, 0.65f, 0.88f, 0.15f)
        drawBox(0f, 1.8f, 0f, 1.2f, 2.2f, 1.2f, texWhite, 1f, 1f, 0.88f, 0.75f, 0.35f, 0.10f)
        // Rotating gold obelisk crystal above fountain
        drawRotatedBox(0f, 3.6f + sin(worldTime * 2f) * 0.2f, 0f, 0.7f, 0.7f, 0.7f, worldTime * 55f, 45f, 1f, 0.75f, 0.08f, 0.45f)

        // 5. Textured Skyscrapers (using generated img_tex_building)
        for (b in CityWorldData.buildings) {
            val uTiles = (b.width / 4.5f).coerceAtLeast(1f)
            val vTiles = (b.height / 6.5f).coerceAtLeast(1f)
            drawBox(
                b.x, b.height * 0.5f, b.z,
                b.width, b.height, b.depth,
                texBuilding, uTiles, vTiles,
                b.tintR, b.tintG, b.tintB, 0f
            )
            if (b.hasRooftopCap) {
                drawBox(
                    b.x, b.height + 0.4f, b.z,
                    b.width * 0.86f, 0.8f, b.depth * 0.86f,
                    texWhite, 1f, 1f, 0.32f, 0.36f, 0.42f, 0f
                )
            }
        }

        // 6. Trees (multi-tiered foliage + trunk)
        val treeLimit = when (graphicsQuality) {
            GraphicsQuality.LOW -> CityWorldData.trees.size / 2
            GraphicsQuality.MEDIUM -> (CityWorldData.trees.size * 3) / 4
            GraphicsQuality.HIGH -> CityWorldData.trees.size
        }
        for (i in 0 until treeLimit) {
            val t = CityWorldData.trees[i]
            val s = t.scale
            // Shadow
            drawBox(t.x, 0.02f, t.z, 2.6f * s, 0.02f, 2.6f * s, texWhite, 1f, 1f, 0.08f, 0.14f, 0.08f, 0f)
            // Trunk
            drawBox(t.x, 1.3f * s, t.z, 0.55f * s, 2.6f * s, 0.55f * s, texWhite, 1f, 1f, 0.42f, 0.27f, 0.14f, 0f)
            // Lower & Upper Lush Canopies
            drawBox(t.x, 3.0f * s, t.z, 2.6f * s, 1.6f * s, 2.6f * s, texWhite, 1f, 1f, 0.14f, 0.56f, 0.22f, 0f)
            drawBox(t.x, 4.1f * s, t.z, 1.8f * s, 1.3f * s, 1.8f * s, texWhite, 1f, 1f, 0.20f, 0.68f, 0.28f, 0f)
        }

        // 7. Streetlights
        val lightStep = if (graphicsQuality == GraphicsQuality.LOW) 2 else 1
        for (i in CityWorldData.streetLights.indices step lightStep) {
            val sl = CityWorldData.streetLights[i]
            drawBox(sl.x, 2.6f, sl.z, 0.18f, 5.2f, 0.18f, texWhite, 1f, 1f, 0.24f, 0.28f, 0.34f, 0f)
            drawBox(sl.x + sl.dirX * 0.7f, 5.15f, sl.z + sl.dirZ * 0.7f, 1.4f, 0.14f, 0.18f, texWhite, 1f, 1f, 0.24f, 0.28f, 0.34f, 0f)
            drawBox(sl.x + sl.dirX * 1.3f, 5.02f, sl.z + sl.dirZ * 1.3f, 0.42f, 0.18f, 0.34f, texWhite, 1f, 1f, 1.0f, 0.90f, 0.55f, 0.75f)
        }

        // 8. Cruising City Vehicles
        for (car in cars) {
            val yaw = if (car.axisX) {
                if (car.dir > 0f) 90f else -90f
            } else {
                if (car.dir > 0f) 0f else 180f
            }
            drawCar(car.x, car.z, yaw, car.r, car.g, car.b)
        }

        // 9. Exploration Zone Landmark Beacons
        for (zone in CityZones.all) {
            val discovered = discoveredZones.contains(zone.id)
            val r = if (discovered) 0.10f else 1.0f
            val g = if (discovered) 0.85f else 0.74f
            val b = if (discovered) 0.55f else 0.05f
            drawRotatedBox(
                zone.x, 0.08f, zone.z,
                3.4f, 0.10f, 3.4f,
                worldTime * 35f, 0f,
                r, g, b, 0.45f
            )
            // Floating diamond marker above undiscovered zones
            if (!discovered) {
                drawRotatedBox(
                    zone.x, 3.2f + sin(worldTime * 2.5f) * 0.35f, zone.z,
                    0.85f, 0.85f, 0.85f,
                    worldTime * 90f, 45f,
                    1.0f, 0.76f, 0.05f, 0.65f
                )
            }
        }

        // 10. Floating Collectible Stars
        for (orb in CityWorldData.collectibles) {
            if (collectedOrbs.contains(orb.id)) continue
            val bobY = 1.15f + sin(worldTime * 3.2f + orb.id) * 0.25f
            drawRotatedBox(
                orb.x, bobY, orb.z,
                0.55f, 0.55f, 0.55f,
                worldTime * 110f + orb.id * 25f, 35f,
                0.0f, 0.90f, 1.0f, 0.65f
            )
        }

        // 11. Articulated 3D Player Character
        drawPlayerCharacter()
    }

    private fun drawCar(x: Float, z: Float, yawDeg: Float, r: Float, g: Float, b: Float) {
        // Ground shadow
        drawRotatedBox(x, 0.04f, z, 2.1f, 0.02f, 4.1f, yawDeg, 0f, 0.08f, 0.08f, 0.10f, 0f)
        // Car lower chassis
        drawRotatedBox(x, 0.52f, z, 1.85f, 0.65f, 3.8f, yawDeg, 0f, r, g, b, 0f)
        // Car cabin roof
        drawRotatedBox(x, 1.05f, z, 1.55f, 0.52f, 2.0f, yawDeg, 0f, r * 0.85f, g * 0.85f, b * 0.85f, 0f)
    }

    private fun drawPlayerCharacter() {
        val yawDeg = (playerFacingYaw * 180f / PI.toFloat())
        val swing = if (isGrounded) sin(walkCycle) * 32f else 20f
        val skin = activeSkin
        val jr = skin.jacketColorRgb[0]
        val jg = skin.jacketColorRgb[1]
        val jb = skin.jacketColorRgb[2]
        val pr = skin.pantsColorRgb[0]
        val pg = skin.pantsColorRgb[1]
        val pb = skin.pantsColorRgb[2]
        val ar = skin.accentColorRgb[0]
        val ag = skin.accentColorRgb[1]
        val ab = skin.accentColorRgb[2]

        // Contact Shadow on ground
        drawBox(playerX, 0.035f, playerZ, 0.95f, 0.02f, 0.95f, texWhite, 1f, 1f, 0.06f, 0.08f, 0.10f, 0f)

        // Helper transforms around player root (playerX, playerY, playerZ) + yawDeg
        // Left & Right Legs
        drawCharacterPart(0.18f, 0.42f, 0f, 0.24f, 0.84f, 0.26f, yawDeg, swing, pr, pg, pb, 0f)
        drawCharacterPart(-0.18f, 0.42f, 0f, 0.24f, 0.84f, 0.26f, yawDeg, -swing, pr, pg, pb, 0f)

        // Torso / Jacket
        drawCharacterPart(0f, 1.22f, 0f, 0.64f, 0.82f, 0.36f, yawDeg, 0f, jr, jg, jb, 0.05f)
        // Accent backpack / chest stripe
        drawCharacterPart(0f, 1.26f, -0.22f, 0.44f, 0.56f, 0.16f, yawDeg, 0f, ar, ag, ab, 0.20f)

        // Left & Right Arms
        drawCharacterPart(0.44f, 1.22f, 0f, 0.20f, 0.74f, 0.22f, yawDeg, -swing * 0.85f, jr, jg, jb, 0f)
        drawCharacterPart(-0.44f, 1.22f, 0f, 0.20f, 0.74f, 0.22f, yawDeg, swing * 0.85f, jr, jg, jb, 0f)

        // Head
        drawCharacterPart(0f, 1.88f, 0f, 0.44f, 0.44f, 0.44f, yawDeg, 0f, 0.92f, 0.76f, 0.64f, 0f)
        // Stylish Visor / Cap Accent
        drawCharacterPart(0f, 2.02f, 0.06f, 0.46f, 0.16f, 0.48f, yawDeg, 0f, ar, ag, ab, 0.25f)
    }

    private fun drawCharacterPart(
        localX: Float, localY: Float, localZ: Float,
        sx: Float, sy: Float, sz: Float,
        yawDeg: Float, pitchSwingDeg: Float,
        r: Float, g: Float, b: Float, emissive: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, playerX, playerY, playerZ)
        Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        Matrix.translateM(modelMatrix, 0, localX, localY, localZ)
        if (pitchSwingDeg != 0f) {
            Matrix.rotateM(modelMatrix, 0, pitchSwingDeg, 1f, 0f, 0f)
        }
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        commitDraw(texWhite, 1f, 1f, r, g, b, emissive, cubeVertCount)
    }

    private fun bindMesh(buf: FloatBuffer) {
        buf.position(0)
        GLES20.glEnableVertexAttribArray(shader.aPositionLoc)
        GLES20.glVertexAttribPointer(shader.aPositionLoc, 3, GLES20.GL_FLOAT, false, GlMeshFactory.STRIDE_BYTES, buf)

        buf.position(3)
        GLES20.glEnableVertexAttribArray(shader.aNormalLoc)
        GLES20.glVertexAttribPointer(shader.aNormalLoc, 3, GLES20.GL_FLOAT, false, GlMeshFactory.STRIDE_BYTES, buf)

        buf.position(6)
        GLES20.glEnableVertexAttribArray(shader.aTexCoordLoc)
        GLES20.glVertexAttribPointer(shader.aTexCoordLoc, 2, GLES20.GL_FLOAT, false, GlMeshFactory.STRIDE_BYTES, buf)
    }

    private fun drawQuad(
        x: Float, y: Float, z: Float,
        sx: Float, sz: Float,
        texId: Int, uScale: Float, vScale: Float,
        r: Float, g: Float, b: Float, emissive: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.scaleM(modelMatrix, 0, sx, 1f, sz)
        commitDraw(texId, uScale, vScale, r, g, b, emissive, quadVertCount)
    }

    private fun drawBox(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        texId: Int, uScale: Float, vScale: Float,
        r: Float, g: Float, b: Float, emissive: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        commitDraw(texId, uScale, vScale, r, g, b, emissive, cubeVertCount)
    }

    private fun drawRotatedBox(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        yawDeg: Float, tiltDeg: Float,
        r: Float, g: Float, b: Float, emissive: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        if (tiltDeg != 0f) Matrix.rotateM(modelMatrix, 0, tiltDeg, 1f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        commitDraw(texWhite, 1f, 1f, r, g, b, emissive, cubeVertCount)
    }

    private fun commitDraw(
        texId: Int,
        uScale: Float,
        vScale: Float,
        r: Float,
        g: Float,
        b: Float,
        emissive: Float,
        vertCount: Int
    ) {
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(shader.uMvpMatrixLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(shader.uModelMatrixLoc, 1, false, modelMatrix, 0)
        GLES20.glUniform2f(shader.uTexScaleLoc, uScale, vScale)
        GLES20.glUniform4f(shader.uColorTintLoc, r, g, b, 1.0f)
        GLES20.glUniform1f(shader.uEmissiveLoc, emissive)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(shader.uTextureLoc, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertCount)
    }

    private fun lerpAngle(a: Float, b: Float, t: Float): Float {
        var diff = (b - a) % (2f * PI.toFloat())
        if (diff < -PI.toFloat()) diff += 2f * PI.toFloat()
        if (diff > PI.toFloat()) diff -= 2f * PI.toFloat()
        return a + diff * t
    }
}

class CityGLSurfaceView(
    context: Context,
    val inputState: PlayerInputState,
    listener: GameEngineListener
) : GLSurfaceView(context) {
    val cityRenderer: OpenGLCityRenderer

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        cityRenderer = OpenGLCityRenderer(context, inputState, listener)
        setRenderer(cityRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
