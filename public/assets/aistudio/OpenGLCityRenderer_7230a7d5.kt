package com.example.game.engine

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import com.example.R
import com.example.game.model.CharacterSkin
import com.example.game.model.CityZones
import com.example.game.model.GameSkins
import com.example.game.model.GameTelemetry
import com.example.game.model.GraphicsQuality
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
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
    fun onFootstep(isSprint: Boolean, isIndoors: Boolean)
    fun onOrbCollected(orbId: Int, newScore: Int)
    fun onZoneDiscovered(zoneId: String, zoneName: String, bonusPoints: Int)
    fun onInteractMessage(message: String)
    fun onJumpPerformed()
    fun onDoorToggled(buildingName: String, opening: Boolean)
    fun onCarHorn()
    fun onNpcGreeting(npcName: String)
}

class OpenGLCityRenderer(
    private val context: Context,
    private val input: PlayerInputState,
    private val listener: GameEngineListener,
    private val hasStencilProvider: () -> Boolean
) : GLSurfaceView.Renderer {

    private lateinit var assets: CityRenderAssets
    private lateinit var dc: GlDrawContext

    // Settings synchronized from ViewModel
    @Volatile var isPaused: Boolean = false
    @Volatile var activeSkin: CharacterSkin = GameSkins.all.first()
    @Volatile var graphicsQuality: GraphicsQuality = GraphicsQuality.HIGH
    @Volatile var cameraSensitivity: Float = 1.0f
    @Volatile var streamingRadiusMeters: Float = 56f

    // Player & Camera state
    private var playerX = 0f
    private var playerY = 0f
    private var playerZ = 8.2f
    private var playerVelY = 0f
    private var isGrounded = true
    private var playerFacingYaw = PI.toFloat()

    private var cameraYaw = 0f
    private var cameraPitch = 0.28f
    private var currentCamDist = 8.4f
    private var currentBuilding: EnterableBuilding? = null
    private var currentFloorNumber = 0

    private var walkCycle = 0f
    private var prevSinCycle = 0f
    private var isMovingNow = false

    private var stamina = 100f
    private var health = 100f
    private var sessionScore = 0
    private var totalDistance = 0f
    private var worldTime = 0f
    private var lastFrameNs = System.nanoTime()
    private var lastTelemetryReportMs = 0L
    private var lastHornTime = -5f

    // FPS measurement
    private var fpsFrameCount = 0
    private var fpsWindowStartNs = System.nanoTime()
    private var measuredFps = 60

    // Per-frame streaming & culling state
    private var camForwardX = 0f
    private var camForwardZ = -1f
    private var camPosX = 0f
    private var camPosY = 4f
    private var camPosZ = 16f
    private var visibleObjectsThisFrame = 0
    private var culledObjectsThisFrame = 0

    private val collectedOrbs = HashSet<Int>()
    private val discoveredZones = HashSet<String>()
    private val enterableBuildings = CityWorldData.createEnterableBuildings()
    private val cars = CityWorldData.createCars()
    private val npcs = CityWorldData.createNpcs()
    private var chunkGrid = CityWorldData.buildChunkGrid(enterableBuildings)

    // Pre-allocated per-frame visible chunk & shadow lists (zero GC churn)
    private val activeChunks = ArrayList<WorldChunk>(48)
    private val visBuildings = ArrayList<BuildingBlock>(32)
    private val visEnterables = ArrayList<EnterableBuilding>(8)
    private val visTrees = ArrayList<TreeProp>(48)
    private val visStreetLights = ArrayList<StreetLightProp>(32)
    private val visTrafficSignals = ArrayList<TrafficSignalProp>(12)
    private val visBenches = ArrayList<BenchProp>(12)
    private val visKiosks = ArrayList<KioskProp>(8)
    private val visParkedCars = ArrayList<ParkedCar>(8)
    private val visMovingCars = ArrayList<CarAgent>(12)
    private val visNpcs = ArrayList<NpcAgent>(28)
    private val visRoads = ArrayList<RoadSegment>(48)
    private val visIntersections = ArrayList<RoadIntersection>(16)

    @Volatile
    private var resetQueued = false

    fun queueReset() {
        resetQueued = true
    }

    private fun performReset() {
        playerX = 0f
        playerY = 0f
        playerZ = 8.2f
        playerVelY = 0f
        isGrounded = true
        playerFacingYaw = PI.toFloat()
        cameraYaw = 0f
        cameraPitch = 0.28f
        currentCamDist = 8.4f
        currentBuilding = null
        currentFloorNumber = 0
        stamina = 100f
        health = 100f
        sessionScore = 0
        totalDistance = 0f
        collectedOrbs.clear()
        discoveredZones.clear()
        enterableBuildings.clear()
        enterableBuildings.addAll(CityWorldData.createEnterableBuildings())
        cars.clear()
        cars.addAll(CityWorldData.createCars())
        npcs.clear()
        npcs.addAll(CityWorldData.createNpcs())
        chunkGrid = CityWorldData.buildChunkGrid(enterableBuildings)
        emitTelemetry()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.47f, 0.72f, 0.94f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        assets = CityRenderAssets(
            shader = CityShaderProgram(),
            shadowShader = ShadowShaderProgram(),
            cubeMesh = GlMeshFactory.createUnitCube(),
            quadMesh = GlMeshFactory.createGroundQuad(),
            cylinderMesh = GlMeshFactory.createCylinder(segments = 14, topScale = 1f, bottomScale = 1f),
            taperedCylinderMesh = GlMeshFactory.createCylinder(segments = 12, topScale = 0.56f, bottomScale = 1f),
            sphereMesh = GlMeshFactory.createSphere(stacks = 10, slices = 14),
            carBodyMesh = GlMeshFactory.createSlopedCarBody(),
            carCabinMesh = GlMeshFactory.createSlopedCarCabin(),
            ringMesh = GlMeshFactory.createGroundRing(segments = 60, innerRatio = 0.985f),
            crossedLeafMesh = GlMeshFactory.createCrossedLeafCluster(),
            texBuilding = GlTextureHelper.loadDrawableTexture(context, R.drawable.img_tex_building, repeat = true),
            texRoad = GlTextureHelper.loadDrawableTexture(context, R.drawable.img_tex_road, repeat = true),
            texGrass = GlTextureHelper.loadDrawableTexture(context, R.drawable.img_tex_grass, repeat = true),
            texBark = GlTextureHelper.loadDrawableTexture(context, R.drawable.img_tex_bark, repeat = true),
            texLeafCluster = GlTextureHelper.loadLeafBranchClusterTexture(context, R.drawable.img_tex_leaves),
            texInteriorFloor = GlTextureHelper.loadDrawableTexture(context, R.drawable.img_tex_interior_floor, repeat = true),
            texConcrete = GlTextureHelper.loadDrawableTexture(context, R.drawable.img_tex_concrete, repeat = true),
            texWhite = GlTextureHelper.createSolidWhiteTexture(),
            playerHeadMesh = GlMeshFactory.createSculptedHeadMesh(),
            playerTorsoMesh = GlMeshFactory.createSculptedTorsoMesh(),
            texPlayerFace = GlTextureHelper.loadPlayerFaceTexture(context, R.drawable.img_player_face),
            texPlayerShirt = GlTextureHelper.createGreenShirtTexture(),
            texPlayerJeans = GlTextureHelper.createDenimJeansTexture()
        )
        dc = GlDrawContext(assets)

        lastFrameNs = System.nanoTime()
        fpsWindowStartNs = lastFrameNs
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = if (height > 0) width.toFloat() / height.toFloat() else 1.77f
        Matrix.perspectiveM(dc.projMatrix, 0, 58f, aspect, 0.25f, 240f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNs = System.nanoTime()
        val dt = ((nowNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNs = nowNs

        fpsFrameCount++
        val elapsedFpsNs = nowNs - fpsWindowStartNs
        if (elapsedFpsNs >= 400_000_000L) {
            measuredFps = ((fpsFrameCount * 1_000_000_000L) / elapsedFpsNs).toInt().coerceIn(1, 240)
            fpsFrameCount = 0
            fpsWindowStartNs = nowNs
        }

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

        // Expanded camera pitch range (-0.55f look steeply up to +1.28f look down from high above!)
        val yawDelta = input.cameraYawDelta
        val pitchDelta = input.cameraPitchDelta
        input.cameraYawDelta = 0f
        input.cameraPitchDelta = 0f
        cameraYaw -= yawDelta * 0.0045f * cameraSensitivity
        val minPitch = if (currentBuilding != null) -0.22f else -0.55f
        val maxPitch = if (currentBuilding != null) 0.58f else 1.28f
        cameraPitch = (cameraPitch + pitchDelta * 0.0038f * cameraSensitivity).coerceIn(minPitch, maxPitch)

        val interactNow = input.interactQueued
        input.interactQueued = false

        // 1. Enterable building sliding doors
        var nearestInteract: String? = null
        var interactConsumed = false
        for (eb in enterableBuildings) {
            val dDoor = eb.distanceToDoor(playerX, playerZ)
            if (dDoor < 4.5f && playerY < 2.2f) {
                nearestInteract = if (eb.doorTargetOpen) "Close ${eb.name} Door" else "Open ${eb.name} Door"
                if (interactNow && !interactConsumed) {
                    eb.doorTargetOpen = !eb.doorTargetOpen
                    listener.onDoorToggled(eb.name, eb.doorTargetOpen)
                    interactConsumed = true
                } else if (dDoor < 2.8f && !eb.doorTargetOpen) {
                    eb.doorTargetOpen = true
                    listener.onDoorToggled(eb.name, true)
                }
            } else if (dDoor > 7.5f && eb.doorTargetOpen && !eb.isInsideFootprint(playerX, playerZ)) {
                eb.doorTargetOpen = false
            }

            val target = if (eb.doorTargetOpen) 1f else 0f
            val speed = 2.8f * dt
            eb.doorOpenProgress = when {
                eb.doorOpenProgress < target -> (eb.doorOpenProgress + speed).coerceAtMost(target)
                eb.doorOpenProgress > target -> (eb.doorOpenProgress - speed).coerceAtLeast(target)
                else -> target
            }
        }

        // 2. Player locomotion
        val rawX = input.moveX.coerceIn(-1f, 1f)
        val rawY = input.moveY.coerceIn(-1f, 1f)
        val mag = sqrt(rawX * rawX + rawY * rawY)
        val wantsSprint = input.isSprinting && stamina > 5f && mag > 0.15f
        stamina = if (wantsSprint) (stamina - 20f * dt).coerceAtLeast(0f) else (stamina + 18f * dt).coerceAtMost(100f)

        val moveSpeed = (if (wantsSprint) 11.2f else 5.8f) * activeSkin.speedMultiplier
        var targetX = playerX
        var targetZ = playerZ
        isMovingNow = false

        if (mag > 0.08f) {
            isMovingNow = true
            val nx = rawX / mag.coerceAtLeast(1f)
            val ny = rawY / mag.coerceAtLeast(1f)
            val fX = -sin(cameraYaw)
            val fZ = -cos(cameraYaw)
            val rX = cos(cameraYaw)
            val rZ = -sin(cameraYaw)

            val wx = fX * (-ny) + rX * nx
            val wz = fZ * (-ny) + rZ * nx
            val wMag = sqrt(wx * wx + wz * wz).coerceAtLeast(0.001f)
            val dirX = wx / wMag
            val dirZ = wz / wMag

            val stepDist = moveSpeed * mag.coerceAtMost(1f) * dt
            targetX = playerX + dirX * stepDist
            targetZ = playerZ + dirZ * stepDist

            val targetYaw = atan2(dirX, dirZ)
            playerFacingYaw = lerpAngle(playerFacingYaw, targetYaw, (dt * 12f).coerceAtMost(1f))

            val cycleRate = if (wantsSprint) 13.5f else 9.0f
            walkCycle += dt * cycleRate
            val curSin = sin(walkCycle)
            if (isGrounded && ((prevSinCycle < 0f && curSin >= 0f) || (prevSinCycle > 0f && curSin <= 0f))) {
                listener.onFootstep(wantsSprint, currentBuilding != null)
            }
            prevSinCycle = curSin
        } else {
            walkCycle *= 0.85f
            prevSinCycle = 0f
        }

        // 3. Update autonomous NPC Men & Women along sidewalks
        for (npc in npcs) {
            if (npc.pauseTimer > 0f) {
                npc.pauseTimer -= dt
                continue
            }
            val dxP = playerX - npc.x
            val dzP = playerZ - npc.z
            if (dxP * dxP + dzP * dzP < 1.3f * 1.3f && playerY < 1.8f) {
                // Pause politely when player walks right next to NPC
                npc.yawRad = lerpAngle(npc.yawRad, atan2(dxP, dzP), (dt * 6f).coerceAtMost(1f))
                if (nearestInteract == null) nearestInteract = "Greet ${npc.name}"
                if (interactNow && !interactConsumed) {
                    listener.onNpcGreeting(npc.name)
                    interactConsumed = true
                }
                continue
            }
            npc.walkPhase += dt * 7.2f
            if (npc.axisX) {
                npc.x += npc.speed * npc.dir * dt
                if (npc.x >= npc.maxBound) { npc.x = npc.maxBound; npc.dir = -1f; npc.pauseTimer = 1.4f }
                else if (npc.x <= npc.minBound) { npc.x = npc.minBound; npc.dir = 1f; npc.pauseTimer = 1.4f }
                npc.yawRad = if (npc.dir > 0f) (PI.toFloat() * 0.5f) else (-PI.toFloat() * 0.5f)
            } else {
                npc.z += npc.speed * npc.dir * dt
                if (npc.z >= npc.maxBound) { npc.z = npc.maxBound; npc.dir = -1f; npc.pauseTimer = 1.4f }
                else if (npc.z <= npc.minBound) { npc.z = npc.minBound; npc.dir = 1f; npc.pauseTimer = 1.4f }
                npc.yawRad = if (npc.dir > 0f) 0f else PI.toFloat()
            }
        }

        // 4. Update traffic cars with Traffic Signal awareness & horn alert
        val signalGreenForX = ((worldTime / 9.0f).toInt() % 2 == 0)
        for (car in cars) {
            val dxP = playerX - car.x
            val dzP = playerZ - car.z
            val forwardDist = if (car.axisX) dxP * car.dir else dzP * car.dir
            val lateralDist = if (car.axisX) abs(dzP) else abs(dxP)
            val playerBlocking = playerY < 1.6f && forwardDist in 0.5f..8.2f && lateralDist < 2.1f

            if (playerBlocking && worldTime - lastHornTime > 2.6f) {
                lastHornTime = worldTime
                listener.onCarHorn()
            }

            // Red light check near major intersections
            var redLightStop = false
            val axisGreen = if (car.axisX) signalGreenForX else !signalGreenForX
            if (!axisGreen) {
                val posAlong = if (car.axisX) car.x else car.z
                for (rc in CityWorldData.roadCoords) {
                    val distToStopLine = (rc - car.dir * 8.2f - posAlong) * car.dir
                    if (distToStopLine in 0.2f..4.2f) {
                        redLightStop = true
                        break
                    }
                }
            }

            val desiredSpeed = if (playerBlocking || redLightStop) 0f else car.baseSpeed
            car.currentSpeed += (desiredSpeed - car.currentSpeed) * (dt * 5.5f).coerceAtMost(1f)

            if (car.axisX) {
                car.x += car.currentSpeed * car.dir * dt
                if (car.x > car.maxBound) car.x = car.minBound
                if (car.x < car.minBound) car.x = car.maxBound
            } else {
                car.z += car.currentSpeed * car.dir * dt
                if (car.z > car.maxBound) car.z = car.minBound
                if (car.z < car.minBound) car.z = car.maxBound
            }
            car.wheelAngleDeg = (car.wheelAngleDeg + car.currentSpeed * dt * 165f) % 360f
        }

        // 5. Full Solid 3D Physics Collision Resolution (buildings, trees, poles, benches, kiosks, cars, NPCs, interior furniture)
        val resolved = CityWorldData.resolvePlayerPhysics(
            nextX = targetX,
            nextZ = targetZ,
            currentY = playerY,
            enterableBuildings = enterableBuildings,
            movingCars = cars,
            npcs = npcs
        )
        val movedDx = resolved.x - playerX
        val movedDz = resolved.z - playerZ
        totalDistance += sqrt(movedDx * movedDx + movedDz * movedDz)
        playerX = resolved.x
        playerZ = resolved.z
        currentBuilding = resolved.insideBuilding
        currentFloorNumber = resolved.floorNumber

        // Vertical jump & stair / car-roof elevation
        if (input.jumpQueued) {
            input.jumpQueued = false
            if (isGrounded) {
                playerVelY = 9.4f
                isGrounded = false
                listener.onJumpPerformed()
            }
        }
        val supportY = resolved.supportFloorY
        if (!isGrounded) {
            playerY += playerVelY * dt
            playerVelY -= 24f * dt
            if (playerY <= supportY) {
                playerY = supportY
                playerVelY = 0f
                isGrounded = true
            }
        } else {
            val diffY = supportY - playerY
            if (abs(diffY) > 1.2f && diffY < 0f) {
                isGrounded = false
                playerVelY = 0f
            } else {
                playerY += diffY * (dt * 18f).coerceAtMost(1f)
            }
        }

        val targetCamDist = if (currentBuilding != null) 4.4f else 8.4f
        currentCamDist += (targetCamDist - currentCamDist) * (dt * 7f).coerceAtMost(1f)

        // 6. Energy Stars & Zones & Fountain / Parked Car interactions
        for (orb in CityWorldData.collectibles) {
            if (collectedOrbs.contains(orb.id)) continue
            val dx = playerX - orb.x
            val dy = (playerY + 1.0f) - orb.y
            val dz = playerZ - orb.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist < 1.9f || (interactNow && !interactConsumed && dist < 3.6f)) {
                collectedOrbs.add(orb.id)
                sessionScore += 100
                listener.onOrbCollected(orb.id, sessionScore)
                interactConsumed = true
            } else if (dist < 4.5f && nearestInteract == null) {
                nearestInteract = "Collect Energy Star (+100)"
            }
        }

        for (zone in CityZones.all) {
            val dx = playerX - zone.x
            val dz = playerZ - zone.z
            if (dx * dx + dz * dz <= zone.radius * zone.radius && !discoveredZones.contains(zone.id)) {
                discoveredZones.add(zone.id)
                sessionScore += zone.bonusPoints
                listener.onZoneDiscovered(zone.id, zone.name, zone.bonusPoints)
            }
        }

        val distPlaza = sqrt(playerX * playerX + playerZ * playerZ)
        if (distPlaza < 5.2f && playerY < 1.5f) {
            if (nearestInteract == null) nearestInteract = "Inspect Saini Plaza Fountain"
            if (interactNow && !interactConsumed) {
                health = 100f
                stamina = 100f
                listener.onInteractMessage("Saini Plaza Fountain: Health & Stamina Restored!")
                interactConsumed = true
            }
        }

        val nowMs = System.currentTimeMillis()
        if (nowMs - lastTelemetryReportMs >= 90L) {
            lastTelemetryReportMs = nowMs
            emitTelemetry(isMovingNow, wantsSprint, nearestInteract)
        }
    }

    private fun emitTelemetry(
        isMoving: Boolean = false,
        isSprinting: Boolean = false,
        nearbyInteract: String? = null
    ) {
        val locLabel = currentBuilding?.let { b ->
            val floorText = if (currentFloorNumber >= 2) "2nd Floor" else "Ground Floor"
            "${b.name} • $floorText"
        } ?: when {
            playerZ > CityWorldData.WATERFRONT_Z -> "Saini Waterfront Pier"
            playerX in -90f..-40f && playerZ in 45f..92f -> "Emerald City Park"
            else -> "Saini City • Street Level"
        }

        listener.onTelemetryUpdate(
            GameTelemetry(
                playerX = playerX,
                playerY = playerY,
                playerZ = playerZ,
                playerYawRad = playerFacingYaw,
                cameraYawRad = cameraYaw,
                isMoving = isMoving,
                isSprinting = isSprinting,
                stamina = stamina,
                health = health,
                collectedOrbIds = collectedOrbs.toSet(),
                discoveredZoneIds = discoveredZones.toSet(),
                nearbyInteractTarget = nearbyInteract,
                distanceThisSession = totalDistance,
                sessionScore = sessionScore,
                currentFps = measuredFps,
                activeChunksCount = activeChunks.size,
                totalChunksCount = CityWorldData.GRID_DIM * CityWorldData.GRID_DIM,
                visibleObjectsCount = visibleObjectsThisFrame,
                culledObjectsCount = culledObjectsThisFrame,
                currentLocationLabel = locLabel
            )
        )
    }

    /**
     * Step 1: Collect ONLY spatial 20m x 20m WorldChunks inside the player's Circular Zone
     * AND in the Camera's forward view direction (+ near-player bubble).
     */
    private fun gatherStreamedChunksAndObjects() {
        activeChunks.clear()
        visBuildings.clear(); visEnterables.clear(); visTrees.clear()
        visStreetLights.clear(); visTrafficSignals.clear(); visBenches.clear()
        visKiosks.clear(); visParkedCars.clear(); visMovingCars.clear(); visNpcs.clear()
        visRoads.clear(); visIntersections.clear()
        visibleObjectsThisFrame = 0
        culledObjectsThisFrame = 0

        val scanRad = streamingRadiusMeters + 24f
        val minCx = (((playerX - scanRad) - CityWorldData.GRID_MIN) / CityWorldData.CHUNK_SIZE).toInt().coerceIn(0, CityWorldData.GRID_DIM - 1)
        val maxCx = (((playerX + scanRad) - CityWorldData.GRID_MIN) / CityWorldData.CHUNK_SIZE).toInt().coerceIn(0, CityWorldData.GRID_DIM - 1)
        val minCz = (((playerZ - scanRad) - CityWorldData.GRID_MIN) / CityWorldData.CHUNK_SIZE).toInt().coerceIn(0, CityWorldData.GRID_DIM - 1)
        val maxCz = (((playerZ + scanRad) - CityWorldData.GRID_MIN) / CityWorldData.CHUNK_SIZE).toInt().coerceIn(0, CityWorldData.GRID_DIM - 1)

        val chunkMaxR = streamingRadiusMeters + 16f
        val chunkMaxRSq = chunkMaxR * chunkMaxR

        for (cx in minCx..maxCx) {
            for (cz in minCz..maxCz) {
                val chunk = chunkGrid[cx][cz]
                val dxP = chunk.centerX - playerX
                val dzP = chunk.centerZ - playerZ
                val dSq = dxP * dxP + dzP * dzP
                if (dSq > chunkMaxRSq) continue

                // Near-player immediate bubble (24m) OR directional camera view cone
                if (dSq > 24f * 24f) {
                    val dxC = chunk.centerX - camPosX
                    val dzC = chunk.centerZ - camPosZ
                    val dC = sqrt(dxC * dxC + dzC * dzC).coerceAtLeast(0.001f)
                    val dot = (dxC * camForwardX + dzC * camForwardZ) / dC
                    if (dot < 0.08f) continue
                }

                activeChunks.add(chunk)
                for (r in chunk.roadSegments) {
                    if (isObjectInZoneAndCone(r.x, r.z, r.length * 0.55f)) visRoads.add(r)
                }
                for (ix in chunk.intersections) {
                    if (isObjectInZoneAndCone(ix.x, ix.z, 7.5f)) visIntersections.add(ix)
                }
                for (b in chunk.buildings) {
                    if (isObjectInZoneAndCone(b.x, b.z, maxOf(b.width, b.depth) * 0.78f)) visBuildings.add(b)
                }
                for (eb in chunk.enterables) {
                    if (isObjectInZoneAndCone(eb.centerX, eb.centerZ, 12.0f)) visEnterables.add(eb)
                }
                for (t in chunk.trees) {
                    if (isObjectInZoneAndCone(t.x, t.z, 4.2f * t.scale)) visTrees.add(t)
                }
                for (sl in chunk.streetLights) {
                    if (isObjectInZoneAndCone(sl.x, sl.z, 2.2f)) visStreetLights.add(sl)
                }
                for (ts in chunk.trafficSignals) {
                    if (isObjectInZoneAndCone(ts.x, ts.z, 3.0f)) visTrafficSignals.add(ts)
                }
                for (bn in chunk.benches) {
                    if (isObjectInZoneAndCone(bn.x, bn.z, 2.0f)) visBenches.add(bn)
                }
                for (k in chunk.kiosks) {
                    if (isObjectInZoneAndCone(k.x, k.z, 2.8f)) visKiosks.add(k)
                }
                for (pc in chunk.parkedCars) {
                    if (isObjectInZoneAndCone(pc.x, pc.z, 3.4f)) visParkedCars.add(pc)
                }
            }
        }

        // Dynamic moving cars & NPCs filtered by active circular zone + view cone
        for (car in cars) {
            if (isObjectInZoneAndCone(car.x, car.z, 3.4f)) visMovingCars.add(car)
        }
        for (npc in npcs) {
            if (isObjectInZoneAndCone(npc.x, npc.z, 1.5f)) visNpcs.add(npc)
        }
    }

    private fun isObjectInZoneAndCone(objX: Float, objZ: Float, objRadius: Float): Boolean {
        val dxP = objX - playerX
        val dzP = objZ - playerZ
        val distPlayerSq = dxP * dxP + dzP * dzP
        val maxR = streamingRadiusMeters + objRadius
        if (distPlayerSq > maxR * maxR) {
            culledObjectsThisFrame++
            return false
        }
        val nearBubble = 12.0f + objRadius
        if (distPlayerSq <= nearBubble * nearBubble) {
            visibleObjectsThisFrame++
            return true
        }
        val dxC = objX - camPosX
        val dzC = objZ - camPosZ
        val distCam = sqrt(dxC * dxC + dzC * dzC).coerceAtLeast(0.001f)
        val dotForward = (dxC * camForwardX + dzC * camForwardZ) / distCam
        val angularPadding = (objRadius / distCam).coerceAtMost(0.45f)
        return if (dotForward >= 0.30f - angularPadding) {
            visibleObjectsThisFrame++
            true
        } else {
            culledObjectsThisFrame++
            false
        }
    }

    private fun renderScene() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_STENCIL_BUFFER_BIT)
        GLES20.glUseProgram(assets.shader.programId)
        dc.boundVboId = -1

        // Camera orbit with smooth ground-clearance zoom when tilting steeply up at tall skyscrapers!
        camForwardX = -sin(cameraYaw)
        camForwardZ = -cos(cameraYaw)
        var effDist = currentCamDist
        val sinPitch = sin(cameraPitch)
        if (currentBuilding == null && sinPitch < -0.05f) {
            val maxDistAboveGround = (playerY + 1.65f - 0.38f) / (-sinPitch)
            effDist = minOf(effDist, maxDistAboveGround.coerceAtLeast(2.2f))
        }
        camPosX = playerX + sin(cameraYaw) * cos(cameraPitch) * effDist
        val desiredCamY = playerY + 1.65f + sinPitch * effDist
        camPosY = if (currentBuilding != null) {
            val floorBase = if (currentFloorNumber >= 2) currentBuilding!!.floorHeight else 0f
            desiredCamY.coerceIn(floorBase + 0.9f, floorBase + currentBuilding!!.floorHeight - 0.45f)
        } else {
            desiredCamY.coerceAtLeast(0.38f)
        }
        camPosZ = playerZ + cos(cameraYaw) * cos(cameraPitch) * effDist

        Matrix.setLookAtM(
            dc.viewMatrix, 0,
            camPosX, camPosY, camPosZ,
            playerX, playerY + 1.50f, playerZ,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(dc.vpMatrix, 0, dc.projMatrix, 0, dc.viewMatrix, 0)

        // Gather ONLY streamed chunks & visible entities in player's zone & view direction
        gatherStreamedChunksAndObjects()

        // Global Lighting & Zone-Synced Fog
        GLES20.glUniform3f(assets.shader.uLightDirLoc, 0.58f, 0.80f, 0.36f)
        GLES20.glUniform3f(assets.shader.uCameraPosLoc, camPosX, camPosY, camPosZ)
        GLES20.glUniform3f(assets.shader.uFogColorLoc, 0.62f, 0.78f, 0.93f)
        val fogEnd = (streamingRadiusMeters + 16f).coerceAtMost(graphicsQuality.fogEnd)
        GLES20.glUniform1f(assets.shader.uFogStartLoc, (streamingRadiusMeters * 0.54f).coerceAtLeast(20f))
        GLES20.glUniform1f(assets.shader.uFogEndLoc, fogEnd)

        // 1. Ground Terrain, Emerald Park Lawn, Oceanfront Bay, Waterfront Pier & Directional Roads
        dc.bindVbo(assets.quadMesh)
        // Grass ground localized around player streaming area
        dc.drawQuad(0f, 0f, -20f, 340f, 270f, assets.texGrass, 48f, 38f, 0.94f, 0.98f, 0.92f, 0f, 0.04f, 8f, 0f)
        // Sandy coastal edge & Sparkling Ocean Water (z > 114f)
        dc.drawQuad(0f, 0.01f, CityWorldData.WATERFRONT_Z + 1.5f, 340f, 4.5f, assets.texConcrete, 30f, 1f, 0.92f, 0.84f, 0.68f, 0f, 0.08f, 12f, 0f)
        dc.drawQuad(0f, -0.05f, 160f, 340f, 92f, assets.texWhite, 1f, 1f, 0.06f, 0.42f, 0.68f, 0.04f, 0.85f, 48f, 0.42f)
        // Waterfront Wooden Boardwalk & Pier
        dc.drawQuad(0f, 0.025f, CityWorldData.WATERFRONT_Z - 2.5f, 220f, 8.0f, assets.texConcrete, 36f, 2f, 0.88f, 0.87f, 0.85f, 0f, 0.14f, 16f, 0.05f)
        if (isObjectInZoneAndCone(0f, 126f, 18f)) {
            dc.bindVbo(assets.cubeMesh)
            dc.drawBox(0f, 0.21f, 126.5f, 14.0f, 0.42f, 25.0f, assets.assetsInteriorFloorOrWood(), 3f, 6f, 0.72f, 0.50f, 0.30f, 0f, 0.22f, 16f, 0.04f)
            // Pier railings
            dc.drawBox(-6.8f, 0.78f, 126.5f, 0.22f, 0.75f, 25.0f, assets.texWhite, 1f, 1f, 0.25f, 0.27f, 0.30f, 0f, 0.3f, 18f, 0.1f)
            dc.drawBox( 6.8f, 0.78f, 126.5f, 0.22f, 0.75f, 25.0f, assets.texWhite, 1f, 1f, 0.25f, 0.27f, 0.30f, 0f, 0.3f, 18f, 0.1f)
            dc.bindVbo(assets.quadMesh)
        }

        // Central plaza concrete deck
        dc.drawQuad(0f, 0.015f, 0f, 22f, 22f, assets.texConcrete, 5f, 5f, 0.92f, 0.92f, 0.94f, 0f, 0.12f, 16f, 0.04f)

        // Streamed Road Segments — East-West roads rotated 90 degrees so white dashed lines ALWAYS align cleanly!
        for (seg in visRoads) {
            val yaw = if (seg.isVertical) 0f else 90f
            dc.drawQuad(
                seg.x, 0.014f, seg.z,
                seg.width + 3.6f, seg.length + 0.6f,
                assets.texConcrete, 2.5f, seg.length / 5f,
                0.88f, 0.89f, 0.91f, 0f, 0.08f, 12f, 0.02f, yawDeg = yaw
            )
            dc.drawQuad(
                seg.x, 0.030f, seg.z,
                seg.width, seg.length,
                assets.texRoad, 1f, seg.length / 8f,
                0.96f, 0.96f, 0.98f, 0f, 0.26f, 24f, 0.08f, yawDeg = yaw
            )
        }

        // Clean Asphalt Intersections & Zebra Crosswalks (no sideways dashes!)
        for (ix in visIntersections) {
            dc.drawQuad(ix.x, 0.0315f, ix.z, ix.sizeX, ix.sizeZ, assets.texConcrete, 2f, 2f, 0.26f, 0.27f, 0.30f, 0f, 0.22f, 20f, 0.06f)
            for (s in -3..3) {
                val offset = s * 1.15f
                dc.drawQuad(ix.x + offset, 0.038f, ix.z + 6.4f, 0.50f, 2.1f, assets.texWhite, 1f, 1f, 0.94f, 0.94f, 0.92f, 0.04f, 0.1f, 12f, 0f)
                dc.drawQuad(ix.x + offset, 0.038f, ix.z - 6.4f, 0.50f, 2.1f, assets.texWhite, 1f, 1f, 0.94f, 0.94f, 0.92f, 0.04f, 0.1f, 12f, 0f)
                dc.drawQuad(ix.x + 6.4f, 0.038f, ix.z + offset, 2.1f, 0.50f, assets.texWhite, 1f, 1f, 0.94f, 0.94f, 0.92f, 0.04f, 0.1f, 12f, 0f)
                dc.drawQuad(ix.x - 6.4f, 0.038f, ix.z + offset, 2.1f, 0.50f, assets.texWhite, 1f, 1f, 0.94f, 0.94f, 0.92f, 0.04f, 0.1f, 12f, 0f)
            }
        }

        // Active Circular Streaming Zone Ring on ground
        if (currentBuilding == null) {
            dc.bindVbo(assets.ringMesh)
            Matrix.setIdentityM(dc.modelMatrix, 0)
            Matrix.translateM(dc.modelMatrix, 0, playerX, 0.052f, playerZ)
            Matrix.scaleM(dc.modelMatrix, 0, streamingRadiusMeters, 1f, streamingRadiusMeters)
            dc.commitDraw(assets.texWhite, 1f, 1f, 0.0f, 0.90f, 1.0f, 0.42f, 0.55f, 0.1f, 8f, 0f, assets.ringMesh.vertexCount)
        }

        // 2. SUN-PROJECTED SILHOUETTE SHADOW PASS (True Shape Shadows for Trees, Cars, Humans, Poles & Towers!)
        renderShapeAccurateShadowPass()

        // 3. Plaza Fountain
        if (isObjectInZoneAndCone(0f, 0f, 4.5f)) {
            drawPlazaFountain()
        }

        // 4. Enterable Multi-Floor Buildings
        for (eb in visEnterables) {
            drawEnterableBuilding(eb)
        }

        // 5. Skyscrapers
        for (b in visBuildings) {
            drawRealisticSkyscraper(b)
        }

        // 6. Realistic Multi-Part Branched Trees (Root Flare + Trunk + Branches + Twigs + Alpha Leaf Clusters)
        val treeStep = if (graphicsQuality == GraphicsQuality.LOW) 2 else 1
        for (i in visTrees.indices step treeStep) {
            CityPropDrawer.drawRealisticTree(dc, visTrees[i], worldTime, graphicsQuality)
        }

        // 7. Streetlights, Traffic Signals, Park Benches, Street Kiosks
        val lightStep = if (graphicsQuality == GraphicsQuality.LOW) 2 else 1
        for (i in visStreetLights.indices step lightStep) {
            CityPropDrawer.drawStreetLight(dc, visStreetLights[i])
        }
        val signalGreenForX = ((worldTime / 9.0f).toInt() % 2 == 0)
        for (ts in visTrafficSignals) {
            CityPropDrawer.drawTrafficSignal(dc, ts, signalGreenForX)
        }
        for (bn in visBenches) {
            CityPropDrawer.drawBench(dc, bn)
        }
        for (k in visKiosks) {
            CityPropDrawer.drawKiosk(dc, k)
        }

        // 8. Realistic Curved GTA-Style Moving & Parked Cars
        for (pc in visParkedCars) {
            CityPropDrawer.drawCar(dc, pc.x, pc.z, pc.yawDeg, 0f, pc.r, pc.g, pc.b)
        }
        for (car in visMovingCars) {
            val yawDeg = if (car.axisX) (if (car.dir > 0f) 90f else -90f) else (if (car.dir > 0f) 0f else 180f)
            CityPropDrawer.drawCar(dc, car.x, car.z, yawDeg, car.wheelAngleDeg, car.r, car.g, car.b)
        }

        // 9. Autonomous NPC Men & Women Pedestrians
        for (npc in visNpcs) {
            CityPropDrawer.drawNpcCharacter(dc, npc, worldTime)
        }

        // 10. Landmark Zone Markers & Collectibles
        dc.bindVbo(assets.sphereMesh)
        for (zone in CityZones.all) {
            if (!discoveredZones.contains(zone.id) && isObjectInZoneAndCone(zone.x, zone.z, 3.0f)) {
                dc.drawMeshTransformed(
                    zone.x, 3.5f + sin(worldTime * 2.5f) * 0.35f, zone.z,
                    0.75f, 0.75f, 0.75f, worldTime * 80f, 0f, 0f,
                    assets.texWhite, 1f, 1f, 1.0f, 0.76f, 0.08f, 0.90f, 0.60f, 0.6f, 24f, 0.25f, assets.sphereMesh
                )
            }
        }
        for (orb in CityWorldData.collectibles) {
            if (collectedOrbs.contains(orb.id) || !isObjectInZoneAndCone(orb.x, orb.z, 1.5f)) continue
            val bobY = orb.y + sin(worldTime * 3.2f + orb.id) * 0.22f
            dc.drawMeshTransformed(
                orb.x, bobY, orb.z,
                0.56f, 0.56f, 0.56f, worldTime * 110f + orb.id * 25f, 20f, 0f,
                assets.texWhite, 1f, 1f, 0.08f, 0.92f, 1.0f, 1.0f, 0.65f, 0.8f, 32f, 0.35f, assets.sphereMesh
            )
        }

        // 11. Realistic Clothed Human Male Player
        CityPropDrawer.drawHumanPlayer(
            dc = dc,
            x = playerX, y = playerY, z = playerZ,
            yawRad = playerFacingYaw,
            walkCycle = walkCycle,
            isMoving = isMovingNow,
            isSprinting = input.isSprinting && isMovingNow,
            isGrounded = isGrounded,
            worldTime = worldTime,
            skin = activeSkin
        )
    }

    private fun renderShapeAccurateShadowPass() {
        val hasStencil = hasStencilProvider()
        GLES20.glUseProgram(assets.shadowShader.programId)
        dc.boundShadowVboId = -1
        GLES20.glUniform4f(assets.shadowShader.uShadowColorLoc, 0.04f, 0.05f, 0.08f, if (hasStencil) 0.38f else 0.24f)

        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        if (hasStencil) {
            GLES20.glEnable(GLES20.GL_STENCIL_TEST)
            GLES20.glStencilFunc(GLES20.GL_EQUAL, 0, 0xFF)
            GLES20.glStencilOp(GLES20.GL_KEEP, GLES20.GL_KEEP, GLES20.GL_INCR)
        }

        // Building shadows
        dc.bindShadowVbo(assets.cubeMesh)
        for (b in visBuildings) {
            Matrix.setIdentityM(dc.modelMatrix, 0)
            Matrix.translateM(dc.modelMatrix, 0, b.x, b.height * 0.5f, b.z)
            Matrix.scaleM(dc.modelMatrix, 0, b.width, b.height, b.depth)
            dc.commitShadow(assets.texWhite, assets.cubeMesh.vertexCount)
        }
        for (eb in visEnterables) {
            Matrix.setIdentityM(dc.modelMatrix, 0)
            Matrix.translateM(dc.modelMatrix, 0, eb.centerX, 9.8f, eb.centerZ)
            Matrix.scaleM(dc.modelMatrix, 0, eb.width, 19.6f, eb.depth)
            dc.commitShadow(assets.texWhite, assets.cubeMesh.vertexCount)
        }

        // Tree shadows (branches + alpha-cutout leaves!)
        val treeStep = if (graphicsQuality == GraphicsQuality.LOW) 2 else 1
        for (i in visTrees.indices step treeStep) {
            CityPropDrawer.drawTreeShadow(dc, visTrees[i], worldTime, graphicsQuality)
        }

        // Poles
        for (sl in visStreetLights) {
            CityPropDrawer.drawPoleShadow(dc, sl.x, sl.z, 5.5f, sl.dirX, sl.dirZ)
        }
        for (ts in visTrafficSignals) {
            CityPropDrawer.drawPoleShadow(dc, ts.x, ts.z, 5.8f, ts.armDirX, ts.armDirZ)
        }

        // Parked & Moving Car shape shadows (curved body + cabin + 4 wheels)
        for (pc in visParkedCars) {
            CityPropDrawer.drawCarShadow(dc, pc.x, pc.z, pc.yawDeg)
        }
        for (car in visMovingCars) {
            val yawDeg = if (car.axisX) (if (car.dir > 0f) 90f else -90f) else (if (car.dir > 0f) 0f else 180f)
            CityPropDrawer.drawCarShadow(dc, car.x, car.z, yawDeg)
        }

        // NPC Men/Women & Player human shadows
        for (npc in visNpcs) {
            CityPropDrawer.drawHumanShadow(dc, npc.x, 0f, npc.z, npc.yawRad, npc.walkPhase, npc.pauseTimer <= 0f, npc.isWoman)
        }
        CityPropDrawer.drawHumanShadow(dc, playerX, playerY, playerZ, playerFacingYaw, walkCycle, isMovingNow, false)

        if (hasStencil) GLES20.glDisable(GLES20.GL_STENCIL_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(true)

        // Restore primary lit shader
        GLES20.glUseProgram(assets.shader.programId)
        dc.boundVboId = -1
    }

    private fun CityRenderAssets.assetsInteriorFloorOrWood() = texInteriorFloor

    private fun drawPlazaFountain() {
        dc.bindVbo(assets.cylinderMesh)
        dc.drawCylinder(0f, 0.38f, 0f, 4.9f, 0.76f, 4.9f, assets.texConcrete, 4f, 1f, 0.90f, 0.90f, 0.92f, 0f, 0.28f, 24f, 0.08f)
        dc.drawCylinder(0f, 0.76f, 0f, 4.2f, 0.14f, 4.2f, assets.texWhite, 1f, 1f, 0.12f, 0.62f, 0.88f, 0.18f, 0.85f, 48f, 0.42f)
        dc.drawCylinder(0f, 1.55f, 0f, 1.25f, 1.75f, 1.25f, assets.texConcrete, 2f, 1f, 0.88f, 0.86f, 0.82f, 0f, 0.25f, 20f, 0.06f)
        dc.drawCylinder(0f, 2.45f, 0f, 2.0f, 0.22f, 2.0f, assets.texConcrete, 2f, 1f, 0.92f, 0.85f, 0.68f, 0f, 0.35f, 24f, 0.10f)
        dc.bindVbo(assets.sphereMesh)
        dc.drawMeshTransformed(
            0f, 3.35f + sin(worldTime * 2f) * 0.14f, 0f,
            0.92f, 0.92f, 0.92f, worldTime * 45f, 0f, 0f,
            assets.texWhite, 1f, 1f, 1.0f, 0.76f, 0.12f, 1f, 0.35f,
            0.90f, 48f, 0.45f, assets.sphereMesh
        )
    }

    private fun drawRealisticSkyscraper(b: BuildingBlock) {
        dc.bindVbo(assets.cubeMesh)
        val podiumH = 4.2f
        dc.drawBox(b.x, podiumH * 0.5f, b.z, b.width + 0.55f, podiumH, b.depth + 0.55f, assets.texConcrete, 3f, 1.5f, 0.85f, 0.86f, 0.88f, 0f, 0.12f, 14f, 0.04f)
        val mainH = (b.height - podiumH).coerceAtLeast(6f)
        val uTiles = (b.width / 4.2f).coerceAtLeast(1.5f)
        val vTiles = (mainH / 6.0f).coerceAtLeast(1.5f)
        dc.drawBox(b.x, podiumH + mainH * 0.5f, b.z, b.width, mainH, b.depth, assets.texBuilding, uTiles, vTiles, b.tintR, b.tintG, b.tintB, 0.02f, 0.58f, 36f, 0.26f)
        if (b.hasSetbackCrown) {
            dc.drawBox(b.x, b.height + 0.35f, b.z, b.width + 0.35f, 0.7f, b.depth + 0.35f, assets.texConcrete, 2f, 0.5f, 0.65f, 0.68f, 0.72f, 0f, 0.15f, 14f, 0.05f)
            dc.drawBox(b.x, b.height + 2.1f, b.z, b.width * 0.55f, 2.8f, b.depth * 0.55f, assets.texConcrete, 2f, 1f, 0.52f, 0.56f, 0.62f, 0f, 0.14f, 14f, 0.04f)
        }
    }

    private fun drawEnterableBuilding(eb: EnterableBuilding) {
        val cx = eb.centerX
        val cz = eb.centerZ
        val hw = eb.halfW
        val fh = eb.floorHeight
        val totalH = fh * 2f
        val wt = eb.wallThick
        val fSign = eb.frontDirSign

        dc.bindVbo(assets.quadMesh)
        dc.drawQuad(cx, 0.08f, cz, eb.width - 0.3f, eb.depth - 0.3f, assets.texInteriorFloor, 5f, 5f, 0.98f, 0.97f, 0.95f, 0.03f, 0.48f, 34f, 0.16f)

        dc.bindVbo(assets.cubeMesh)
        val main2fW = eb.width - 3.2f
        dc.drawBox(cx - 1.45f, fh, cz, main2fW, 0.26f, eb.depth - 0.4f, assets.texInteriorFloor, 4f, 5f, 0.96f, 0.95f, 0.93f, 0.02f, 0.42f, 32f, 0.12f)
        val landingZ = eb.stairEndZ - fSign * 1.1f
        dc.drawBox(cx + hw - 1.85f, fh, landingZ, 2.9f, 0.26f, 2.4f, assets.texInteriorFloor, 1.5f, 1.5f, 0.96f, 0.95f, 0.93f, 0.02f, 0.38f, 28f, 0.10f)

        dc.drawBox(cx, totalH + 0.18f, cz, eb.width + 0.6f, 0.36f, eb.depth + 0.6f, assets.texConcrete, 4f, 4f, 0.72f, 0.75f, 0.80f, 0f, 0.16f, 16f, 0.06f)
        dc.drawBox(cx, totalH + 5.8f, cz, eb.width * 0.88f, 11.2f, eb.depth * 0.88f, assets.texBuilding, 3f, 2.4f, 0.92f, 0.95f, 0.98f, 0.02f, 0.60f, 36f, 0.28f)
        dc.drawBox(cx, totalH + 11.7f, cz, eb.width * 0.92f, 0.65f, eb.depth * 0.92f, assets.texConcrete, 3f, 0.5f, 0.62f, 0.66f, 0.72f, 0f, 0.14f, 14f, 0.05f)

        dc.drawBox(cx - hw + wt * 0.5f, totalH * 0.5f, cz, wt, totalH, eb.depth, assets.texConcrete, 4f, 2.2f, 0.88f, 0.89f, 0.91f, 0f, 0.12f, 12f, 0.04f)
        dc.drawBox(cx + hw - wt * 0.5f, totalH * 0.5f, cz, wt, totalH, eb.depth, assets.texConcrete, 4f, 2.2f, 0.88f, 0.89f, 0.91f, 0f, 0.12f, 12f, 0.04f)
        dc.drawBox(cx, totalH * 0.5f, eb.backZ - fSign * wt * 0.5f, eb.width, totalH, wt, assets.texConcrete, 4f, 2.2f, 0.88f, 0.89f, 0.91f, 0f, 0.12f, 12f, 0.04f)

        val frontWallZ = eb.frontZ - fSign * wt * 0.5f
        val segWidth = (hw - eb.doorHalfWidth).coerceAtLeast(0.5f)
        dc.drawBox(cx - eb.doorHalfWidth - segWidth * 0.5f, fh * 0.5f, frontWallZ, segWidth, fh, wt, assets.texBuilding, 1.5f, 1f, 0.92f, 0.94f, 0.98f, 0f, 0.50f, 32f, 0.22f)
        dc.drawBox(cx + eb.doorHalfWidth + segWidth * 0.5f, fh * 0.5f, frontWallZ, segWidth, fh, wt, assets.texBuilding, 1.5f, 1f, 0.92f, 0.94f, 0.98f, 0f, 0.50f, 32f, 0.22f)
        dc.drawBox(cx, fh - 0.55f, frontWallZ, eb.doorHalfWidth * 2f + 0.2f, 1.1f, wt + 0.12f, assets.texConcrete, 1f, 0.4f, 0.30f, 0.34f, 0.40f, 0f, 0.20f, 18f, 0.05f)
        dc.drawBox(cx, fh - 0.45f, eb.frontZ + fSign * 0.28f, 6.2f, 0.48f, 0.25f, assets.texWhite, 1f, 1f, eb.accentR, eb.accentG, eb.accentB, 0.65f, 0.4f, 24f, 0.15f)
        dc.drawBox(cx, 3.25f, eb.frontZ + fSign * 1.15f, 5.4f, 0.22f, 2.3f, assets.texConcrete, 2f, 1f, 0.24f, 0.28f, 0.34f, 0f, 0.25f, 20f, 0.10f)

        dc.drawBox(cx, fh + 0.65f, frontWallZ, eb.width - 0.8f, 1.15f, 0.26f, assets.texConcrete, 4f, 0.5f, 0.78f, 0.82f, 0.86f, 0f, 0.22f, 18f, 0.08f)
        dc.drawBoxAlpha(cx, fh + 2.45f, frontWallZ, eb.width - 0.9f, 2.4f, 0.12f, assets.texWhite, 0.55f, 0.78f, 0.95f, 0.26f, 0.05f, 0.8f, 40f, 0.45f)

        val slideOffset = eb.doorOpenProgress * (eb.doorHalfWidth * 0.92f)
        val doorPanelW = eb.doorHalfWidth - 0.06f
        val doorH = 3.05f
        dc.drawBoxAlpha(cx - doorPanelW * 0.5f - slideOffset, doorH * 0.5f + 0.08f, frontWallZ, doorPanelW, doorH, 0.14f, assets.texWhite, 0.42f, 0.72f, 0.88f, 0.62f, 0.08f, 0.85f, 48f, 0.45f)
        dc.drawBoxAlpha(cx + doorPanelW * 0.5f + slideOffset, doorH * 0.5f + 0.08f, frontWallZ, doorPanelW, doorH, 0.14f, assets.texWhite, 0.42f, 0.72f, 0.88f, 0.62f, 0.08f, 0.85f, 48f, 0.45f)
        dc.drawBox(cx - 0.18f - slideOffset, 1.35f, frontWallZ, 0.08f, 0.65f, 0.24f, assets.texWhite, 1f, 1f, eb.accentR, eb.accentG, eb.accentB, 0.25f, 0.8f, 36f, 0.2f)
        dc.drawBox(cx + 0.18f + slideOffset, 1.35f, frontWallZ, 0.08f, 0.65f, 0.24f, assets.texWhite, 1f, 1f, eb.accentR, eb.accentG, eb.accentB, 0.25f, 0.8f, 36f, 0.2f)

        val partWallZ = cz - fSign * 1.2f
        dc.drawBox(cx - 4.4f, fh * 0.5f, partWallZ, 4.4f, fh - 0.2f, 0.34f, assets.texConcrete, 2f, 1.5f, 0.92f, 0.90f, 0.87f, 0f, 0.10f, 12f, 0.02f)
        dc.drawBox(cx - 4.4f, fh + fh * 0.5f, partWallZ, 4.4f, fh - 0.2f, 0.34f, assets.texConcrete, 2f, 1.5f, 0.92f, 0.90f, 0.87f, 0f, 0.10f, 12f, 0.02f)

        val steps = 12
        val stairCenterX = (eb.stairMinX + eb.stairMaxX) * 0.5f
        val stairW = eb.stairMaxX - eb.stairMinX
        val stepDepth = abs(eb.stairEndZ - eb.stairStartZ) / steps
        val stepRise = fh / steps
        for (i in 0 until steps) {
            val t = (i + 0.5f) / steps
            val sz = eb.stairStartZ + (eb.stairEndZ - eb.stairStartZ) * t
            val sh = (i + 1) * stepRise
            dc.drawBox(stairCenterX, 0.08f + sh * 0.5f, sz, stairW, sh, stepDepth + 0.06f, assets.texInteriorFloor, 1f, 0.5f, 0.78f, 0.64f, 0.48f, 0f, 0.28f, 20f, 0.06f)
        }

        dc.drawBox(cx - 3.8f, 0.58f, cz + fSign * 2.2f, 2.8f, 1.0f, 0.95f, assets.texConcrete, 1.5f, 0.5f, 0.32f, 0.26f, 0.22f, 0f, 0.32f, 24f, 0.08f)
        dc.drawBox(cx - 3.8f, 1.32f, cz + fSign * 2.2f, 0.85f, 0.50f, 0.08f, assets.texWhite, 1f, 1f, 0.10f, 0.82f, 0.98f, 0.55f, 0.6f, 32f, 0.2f)
        dc.drawBox(cx - 3.6f, 0.46f, cz - fSign * 4.4f, 3.2f, 0.72f, 1.15f, assets.texWhite, 1f, 1f, eb.accentR * 0.7f, eb.accentG * 0.7f, eb.accentB * 0.7f, 0f, 0.14f, 10f, 0.02f)
        dc.drawBox(cx - 3.6f, 0.88f, cz - fSign * 4.85f, 3.2f, 0.75f, 0.28f, assets.texWhite, 1f, 1f, eb.accentR * 0.6f, eb.accentG * 0.6f, eb.accentB * 0.6f, 0f, 0.12f, 10f, 0.02f)
        dc.drawBox(cx - 3.6f, 0.36f, cz - fSign * 2.9f, 1.6f, 0.46f, 0.85f, assets.texInteriorFloor, 1f, 1f, 0.58f, 0.42f, 0.28f, 0f, 0.35f, 24f, 0.1f)

        dc.drawBox(cx - 3.4f, fh + 0.55f, cz - fSign * 3.8f, 2.6f, 0.88f, 1.1f, assets.texInteriorFloor, 1.5f, 0.8f, 0.46f, 0.32f, 0.20f, 0f, 0.36f, 28f, 0.1f)
        dc.drawBox(cx - 3.4f, fh + 1.25f, cz - fSign * 3.8f, 0.95f, 0.55f, 0.08f, assets.texWhite, 1f, 1f, 0.12f, 0.88f, 0.98f, 0.60f, 0.6f, 32f, 0.2f)
        dc.drawBox(cx - 3.2f, fh + 0.48f, cz + fSign * 3.4f, 3.0f, 0.72f, 1.1f, assets.texWhite, 1f, 1f, 0.26f, 0.32f, 0.44f, 0f, 0.12f, 10f, 0.02f)

        dc.drawBox(cx - 1.5f, fh - 0.18f, cz, 2.8f, 0.08f, 1.2f, assets.texWhite, 1f, 1f, 1.0f, 0.96f, 0.86f, 0.95f, 0.2f, 12f, 0f)
        dc.drawBox(cx - 1.5f, totalH - 0.08f, cz, 2.8f, 0.08f, 1.2f, assets.texWhite, 1f, 1f, 1.0f, 0.96f, 0.86f, 0.95f, 0.2f, 12f, 0f)

        dc.bindVbo(assets.cylinderMesh)
        dc.drawCylinder(cx - 2.3f, 1.65f, eb.frontZ + fSign * 1.9f, 0.36f, 3.2f, 0.36f, assets.texConcrete, 1f, 2f, 0.85f, 0.86f, 0.88f, 0f, 0.22f, 18f, 0.05f)
        dc.drawCylinder(cx + 2.3f, 1.65f, eb.frontZ + fSign * 1.9f, 0.36f, 3.2f, 0.36f, assets.texConcrete, 1f, 2f, 0.85f, 0.86f, 0.88f, 0f, 0.22f, 18f, 0.05f)
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
    private val stencilChooser = StencilEGLConfigChooser()
    val cityRenderer: OpenGLCityRenderer
    private var desiredFrameRate = 120f

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(stencilChooser)
        preserveEGLContextOnPause = true
        cityRenderer = OpenGLCityRenderer(context, inputState, listener) { stencilChooser.hasStencil }
        setRenderer(cityRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun applyTargetFrameRate(fps: Int) {
        desiredFrameRate = fps.toFloat()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val s = holder.surface
            if (s != null && s.isValid) {
                runCatching {
                    s.setFrameRate(
                        desiredFrameRate,
                        Surface.FRAME_RATE_COMPATIBILITY_DEFAULT
                    )
                }
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        super.surfaceCreated(holder)
        applyTargetFrameRate(desiredFrameRate.toInt())
    }
}
