package com.example.game.engine

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import com.example.R
import com.example.game.model.ActiveVehicleMode
import com.example.game.model.CarCrashType
import com.example.game.model.CharacterSkin
import com.example.game.model.CityZones
import com.example.game.model.GameSkins
import com.example.game.model.GameTelemetry
import com.example.game.model.GraphicsQuality
import com.example.game.model.GroundSurface
import com.example.game.model.WeatherState
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
    @Volatile var hornQueued: Boolean = false
    @Volatile var toggleWeatherQueued: Boolean = false
    @Volatile var toggleDayNightQueued: Boolean = false
    @Volatile var cameraYawDelta: Float = 0f
    @Volatile var cameraPitchDelta: Float = 0f
    @Volatile var holdGateOpen: Boolean = false
    @Volatile var holdGateClose: Boolean = false
}

interface GameEngineListener {
    fun onTelemetryUpdate(telemetry: GameTelemetry)
    fun onFootstep(isSprint: Boolean, surface: GroundSurface)
    fun onOrbCollected(orbId: Int, newScore: Int)
    fun onZoneDiscovered(zoneId: String, zoneName: String, bonusPoints: Int)
    fun onInteractMessage(message: String)
    fun onJumpPerformed()
    fun onPlayerLand(heavy: Boolean)
    fun onPlayerWallVault()
    fun onDoorToggled(buildingName: String, opening: Boolean)
    fun onIronGateToggled(gateName: String, opening: Boolean)
    fun onIronGateMoving()
    fun onIronGateSlammed(gateName: String)
    fun onCarEntered()
    fun onCarExited()
    fun onCarAccelerateRev()
    fun onCarEmergencyBrakeSkid()
    fun onCarCrash(type: CarCrashType)
    fun onCarHorn()
    fun onJetFlyby(distanceMeters: Float)
    fun onNpcGreeting(npcName: String)
    fun onWaypointReached()
    fun onAmbientAudioUpdate(
        vehicleMode: ActiveVehicleMode,
        vehicleSpeedRatio: Float,
        heliDistMeters: Float,
        heliTakeoffFactor: Float,
        oceanDistMeters: Float,
        rainIntensity: Float,
        nightFactor: Float,
        isIndoors: Boolean,
        nearTreesFactor: Float
    )
}

class OpenGLCityRenderer(
    private val context: Context,
    private val input: PlayerInputState,
    private val listener: GameEngineListener,
    private val hasStencilProvider: () -> Boolean
) : GLSurfaceView.Renderer {

    private lateinit var assets: CityRenderAssets
    private lateinit var dc: GlDrawContext
    private var glbPlayer: GlbSkinnedPlayer? = null

    // Settings synchronized from ViewModel
    @Volatile var isPaused: Boolean = false
    @Volatile var activeSkin: CharacterSkin = GameSkins.all.first()
    @Volatile var graphicsQuality: GraphicsQuality = GraphicsQuality.HIGH
    @Volatile var cameraSensitivity: Float = 1.0f
    @Volatile var streamingRadiusMeters: Float = 62f
    @Volatile var waypointActive: Boolean = false
    @Volatile var waypointX: Float = 0f
    @Volatile var waypointZ: Float = 0f

    // Player & Camera state
    private var playerX = 0f
    private var playerY = 0f
    private var playerZ = 8.2f
    private var playerVelY = 0f
    private var peakAirY = 0f
    private var isGrounded = true
    private var playerFacingYaw = PI.toFloat()

    // Vehicle state (drivable cars & rideable Bullet 350 bikes!)
    private var drivenCar: ParkedCar? = null
    private var riddenBike: BulletBikeProp? = null
    private var vehicleSpeed = 0f
    private var lastCarRevTime = -10f
    private var lastCarSkidTime = -10f
    private var lastCarCrashTime = -10f
    private var lastHardBrakeTime = -10f

    private var cameraYaw = 0f
    private var cameraPitch = 0.22f
    private var currentCamDist = 4.6f
    private var lastManualCameraDragTime = -10f
    private var currentBuilding: EnterableBuilding? = null
    private var currentFloorNumber = 0

    private var walkCycle = 0f
    private var prevSinCycle = 0f
    private var isMovingNow = false
    private var nearbyGate: InteractiveGate? = null
    private var gatePushBlend = 0f
    private var lastGateAudioTime = -10f

    // Time-of-Day & Weather Cycle
    private var timeOfDay = 0.36f // 0=midnight, 0.25=sunrise, 0.5=noon, 0.75=sunset
    private var nightFactor = 0f
    private var weatherState = WeatherState.CLEAR
    private var rainIntensity = 0f
    private var roadWetness = 0f

    // Overhead Airport Jetliner Flyby & Army Base Helicopter cycle
    private var jetFlybyCycle = 6f
    private var jetFlybyTriggeredThisPass = false
    private var jetCurrentZ = -460f
    private var chopperLiftY = 0f
    private var chopperRotorRate = 0.25f

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
    private val roadsideShops = CityWorldData.createRoadsideShops()
    private val parkedCars = CityWorldData.createParkedCars()
    private val bulletBikes = CityWorldData.createBulletBikes()
    private val cars = CityWorldData.createCars()
    private val npcs = CityWorldData.createNpcs()
    private val skyClouds = CityWorldData.createSkyClouds()
    private val flyingBirds = CityWorldData.createFlyingBirds()
    private var chunkGrid = CityWorldData.buildChunkGrid(enterableBuildings, roadsideShops)

    // Pre-allocated per-frame visible chunk & shadow lists (zero GC churn)
    private val activeChunks = ArrayList<WorldChunk>(48)
    private val visBuildings = ArrayList<BuildingBlock>(32)
    private val visEnterables = ArrayList<EnterableBuilding>(8)
    private val visRoadsideShops = ArrayList<RoadsideShopProp>(8)
    private val visStaticObstacles = ArrayList<StaticPropObstacle>(48)
    private val visPortContainers = ArrayList<PortContainerProp>(16)
    private val visTrees = ArrayList<TreeProp>(48)
    private val visStreetLights = ArrayList<StreetLightProp>(32)
    private val visTrafficSignals = ArrayList<TrafficSignalProp>(12)
    private val visBenches = ArrayList<BenchProp>(12)
    private val visKiosks = ArrayList<KioskProp>(8)
    private val visParkedCars = ArrayList<ParkedCar>(8)
    private val visBulletBikes = ArrayList<BulletBikeProp>(8)
    private val visGrassTufts = ArrayList<GrassTuftProp>(96)
    private val visArmyBunkers = ArrayList<ArmyBunkerProp>(4)
    private val visBarbedFences = ArrayList<BarbedFenceSegment>(8)
    private val visHelipads = ArrayList<HelipadProp>(4)
    private val visArmyVehicles = ArrayList<ArmyVehicleProp>(6)
    private val visMovingCars = ArrayList<CarAgent>(12)
    private val visNpcs = ArrayList<NpcAgent>(28)
    private val visBirds = ArrayList<FlyingBirdAgent>(12)
    private val visRoads = ArrayList<RoadSegment>(48)
    private val visIntersections = ArrayList<RoadIntersection>(16)
    private val visColonyPads = ArrayList<ColonySurfacePad>(24)
    private val visColonyAlleys = ArrayList<ColonyAlleySegment>(40)

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
        drivenCar = null
        riddenBike = null
        vehicleSpeed = 0f
        cameraYaw = 0f
        cameraPitch = 0.22f
        currentCamDist = 4.6f
        currentBuilding = null
        currentFloorNumber = 0
        timeOfDay = 0.36f
        weatherState = WeatherState.CLEAR
        rainIntensity = 0f
        roadWetness = 0f
        stamina = 100f
        health = 100f
        sessionScore = 0
        totalDistance = 0f
        collectedOrbs.clear()
        discoveredZones.clear()
        enterableBuildings.clear()
        enterableBuildings.addAll(CityWorldData.createEnterableBuildings())
        roadsideShops.clear()
        roadsideShops.addAll(CityWorldData.createRoadsideShops())
        parkedCars.clear()
        parkedCars.addAll(CityWorldData.createParkedCars())
        bulletBikes.clear()
        bulletBikes.addAll(CityWorldData.createBulletBikes())
        cars.clear()
        cars.addAll(CityWorldData.createCars())
        npcs.clear()
        npcs.addAll(CityWorldData.createNpcs())
        chunkGrid = CityWorldData.buildChunkGrid(enterableBuildings, roadsideShops)
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
            texPlayerJeans = GlTextureHelper.createDenimJeansTexture(),
            verticalQuadMesh = GlMeshFactory.createVerticalQuad(),
            crossedGrassMesh = GlMeshFactory.createCrossedGrassMesh(),
            skyDomeMesh = GlMeshFactory.createSkyDomeMesh(),
            // Batch 1 custom textures
            texAirportRunway = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_airport_runway, repeat = false),
            tex3dGrassBlades = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_3d_grass_blades, repeat = false),
            texArmyBarbedFence = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_army_barbed_fence, repeat = true, repeatY = false),
            texArmyCamo = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_army_camouflage, repeat = true),
            texArmyHelipad = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_army_helipad, repeat = false),
            texArmyBunkerConcrete = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_army_bunker_concrete, repeat = true),
            texBuildingGlassFacade = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_building_glass_facade, repeat = true),
            texBirdFeathers = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_bird_feathers, repeat = false),
            texBulletBikeTank = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_bullet_bike_tank, repeat = false),
            texEnvCloudPuff = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_env_cloud_puff, repeat = false),
            // Batch 2: Sky & Weather (5)
            texEnvSkyDay = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_env_sky_day, repeat = true, repeatY = false),
            texEnvSkyNight = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_env_sky_night, repeat = true, repeatY = false),
            texEnvSunGlow = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_env_sun_glow, repeat = false),
            texEnvMoon = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_env_moon, repeat = false),
            texEnvRainStreaks = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_env_rain_streaks, repeat = true),
            // Batch 2: Water & Roads (5)
            texWaterOcean = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_water_ocean, repeat = true),
            texWaterNormal = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_water_normal, repeat = true),
            texRoadAsphaltDetailed = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_road_asphalt_detailed, repeat = true),
            texIntersectionAsphalt = GlTextureHelper.createCleanIntersectionAsphaltTexture(),
            texColonyAlley = GlTextureHelper.createColonyAlleyTexture(),
            texFootpathPaverBlocks = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_footpath_paver_blocks, repeat = true),
            texWaterRoadPuddleReflect = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_water_road_puddle_reflect, repeat = true),
            // Batch 2: Trees & Foliage (4)
            texTreeBark = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_tree_bark, repeat = true),
            texTreeLeavesBroad = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_tree_leaves_broad, repeat = false),
            texTreeLeavesPalm = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_tree_leaves_palm, repeat = false),
            texTreeLeavesPine = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_tree_leaves_pine, repeat = false),
            // Batch 2: Buildings, Shops & Interiors (6)
            texHouseRedBrick = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_house_red_brick, repeat = true),
            texHouseExteriorWindows = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_house_exterior_windows, repeat = true),
            texShopShutter = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_shop_shutter, repeat = false),
            texInteriorWallPaint = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_interior_wall_paint, repeat = true),
            texInteriorWoodFloor = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_interior_wood_floor, repeat = true),
            texInteriorMarbleTiles = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_interior_marble_tiles, repeat = true),
            // Batch 2: Industrial, Port & Vehicles (3)
            texFactoryCorrugatedMetal = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_factory_corrugated_metal, repeat = true),
            texPortContainerSteel = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_port_container_steel, repeat = true),
            texVehicleTireTread = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_vehicle_tire_tread, repeat = true),
            // Batch 3: Complete 80-Texture Realistic Multi-Angle Pack + Split Cylinder VBO
            splitCylinderMesh = GlMeshFactory.createSplitCylinder(segments = 16),
            splitCylinderSegments = 16,
            multiAngle = MultiAngleTextureManager(context)
        )
        dc = GlDrawContext(assets)
        glbPlayer = GlbSkinnedPlayer.loadFromAssets(context, "player.glb", assets.texWhite)

        lastFrameNs = System.nanoTime()
        fpsWindowStartNs = lastFrameNs
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = if (height > 0) width.toFloat() / height.toFloat() else 1.77f
        // Near plane 0.45f doubles depth-buffer precision across the city, preventing distant Z-fighting
        Matrix.perspectiveM(dc.projMatrix, 0, 56f, aspect, 0.45f, 340f)
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

        // --- 0. Time-of-day & Weather state toggles and smooth transitions ---
        if (input.toggleDayNightQueued) {
            input.toggleDayNightQueued = false
            timeOfDay = if (nightFactor > 0.45f) 0.44f else 0.96f
            listener.onInteractMessage(if (timeOfDay > 0.75f) "Night Mode — City Lights & Moon Active" else "Daylight Mode — Sunlit Sky Active")
        } else {
            timeOfDay = (timeOfDay + dt / 240f) % 1f
        }
        val sunAngleRad = (timeOfDay - 0.25f) * 2f * PI.toFloat()
        val sunElev = sin(sunAngleRad)
        nightFactor = ((0.18f - sunElev) / 0.42f).coerceIn(0f, 1f)

        if (input.toggleWeatherQueued) {
            input.toggleWeatherQueued = false
            weatherState = if (weatherState == WeatherState.CLEAR) WeatherState.RAIN else WeatherState.CLEAR
            listener.onInteractMessage(
                if (weatherState == WeatherState.RAIN) "Monsoon Rain Started — Wet Reflective Roads!"
                else "Sky Clearing — Roads Drying..."
            )
        }
        val targetRain = if (weatherState == WeatherState.RAIN) 1f else 0f
        rainIntensity += (targetRain - rainIntensity) * (dt * 1.4f).coerceAtMost(1f)
        roadWetness = if (weatherState == WeatherState.RAIN) {
            (roadWetness + dt * 0.35f).coerceAtMost(1f)
        } else {
            (roadWetness - dt * 0.08f).coerceAtLeast(0f)
        }

        // --- 0b. Overhead Airport Jetliner Flyby & Army Base Helicopter flight cycle ---
        jetFlybyCycle += dt
        if (jetFlybyCycle >= 24f) {
            jetFlybyCycle = 0f
            jetFlybyTriggeredThisPass = false
        }
        val jetProgress = (jetFlybyCycle / 11.5f).coerceIn(0f, 1f)
        jetCurrentZ = -460f + jetProgress * 920f
        if (!jetFlybyTriggeredThisPass && jetFlybyCycle in 3.6f..5.4f) {
            jetFlybyTriggeredThisPass = true
            val dxJ = playerX - CityWorldData.AIRPORT_RUNWAY_X
            val dzJ = playerZ - jetCurrentZ
            val distJ = sqrt(dxJ * dxJ + dzJ * dzJ)
            listener.onJetFlyby(distJ)
        }

        val heliCycle = worldTime % 22f
        val targetChopperY = when {
            heliCycle < 5f -> 0f
            heliCycle < 9f -> ((heliCycle - 5f) / 4f) * 9.5f
            heliCycle < 15f -> 9.5f
            heliCycle < 19f -> ((19f - heliCycle) / 4f) * 9.5f
            else -> 0f
        }
        chopperLiftY += (targetChopperY - chopperLiftY) * (dt * 3.5f).coerceAtMost(1f)
        chopperRotorRate = (0.28f + (chopperLiftY / 9.5f) * 0.72f).coerceIn(0.25f, 1f)

        // Free 360-degree camera orbit (identical freedom on foot AND inside vehicles like GTA 5!)
        val yawDelta = input.cameraYawDelta
        val pitchDelta = input.cameraPitchDelta
        input.cameraYawDelta = 0f
        input.cameraPitchDelta = 0f
        if (abs(yawDelta) > 0.35f || abs(pitchDelta) > 0.35f) {
            lastManualCameraDragTime = worldTime
        }
        cameraYaw -= yawDelta * 0.0045f * cameraSensitivity
        val minPitch = if (currentBuilding != null) -0.22f else -0.55f
        val maxPitch = if (currentBuilding != null) 0.58f else 1.28f
        cameraPitch = (cameraPitch + pitchDelta * 0.0038f * cameraSensitivity).coerceIn(minPitch, maxPitch)

        val interactNow = input.interactQueued
        input.interactQueued = false
        val hornNow = input.hornQueued
        input.hornQueued = false

        var nearestInteract: String? = null
        var interactConsumed = false

        // --- 1. Hand-Controlled Interactive Gate System (All 4 Mechanisms + Stop/Leave-Anywhere) ---
        val inVehicle = drivenCar != null || riddenBike != null
        var closestGate: InteractiveGate? = null
        var closestGateDist = 3.25f

        if (!inVehicle && playerY < 2.2f) {
            for (eb in enterableBuildings) {
                val d = eb.gate.distanceTo(playerX, playerZ)
                if (d < closestGateDist) {
                    closestGateDist = d
                    closestGate = eb.gate
                }
            }
            for (shop in roadsideShops) {
                val d = shop.gate.distanceTo(playerX, playerZ)
                if (d < closestGateDist) {
                    closestGateDist = d
                    closestGate = shop.gate
                }
            }
        }
        nearbyGate = closestGate

        var isPushingGateNow = false
        if (closestGate != null) {
            val prevProgress = closestGate.openProgress
            val gateSpeed = 0.62f * dt

            if (input.holdGateOpen && !input.holdGateClose) {
                isPushingGateNow = true
                if (closestGate.openProgress < 1f) {
                    closestGate.openProgress = (closestGate.openProgress + gateSpeed).coerceAtMost(1f)
                    if (worldTime - lastGateAudioTime > 0.90f) {
                        lastGateAudioTime = worldTime
                        listener.onIronGateMoving()
                    }
                }
            } else if (input.holdGateClose && !input.holdGateOpen) {
                isPushingGateNow = true
                if (closestGate.openProgress > 0f) {
                    closestGate.openProgress = (closestGate.openProgress - gateSpeed).coerceAtLeast(0f)
                    if (closestGate.openProgress <= 0.001f && prevProgress > 0.001f) {
                        closestGate.openProgress = 0f
                        lastGateAudioTime = worldTime
                        listener.onIronGateSlammed(closestGate.name)
                    } else if (worldTime - lastGateAudioTime > 0.90f) {
                        lastGateAudioTime = worldTime
                        listener.onIronGateMoving()
                    }
                }
            } else if (interactNow && !interactConsumed) {
                // Quick tap USE gently nudges the gate open/closed by 25% if player taps USE instead of holding
                interactConsumed = true
                val opening = closestGate.openProgress < 0.85f
                closestGate.openProgress = if (opening) {
                    (closestGate.openProgress + 0.28f).coerceAtMost(1f)
                } else {
                    (closestGate.openProgress - 0.28f).coerceAtLeast(0f)
                }
                if (closestGate.openProgress <= 0.001f) {
                    closestGate.openProgress = 0f
                    listener.onIronGateSlammed(closestGate.name)
                } else {
                    listener.onIronGateMoving()
                }
            }

            if (isPushingGateNow) {
                // Face toward the gate gently while player's right arm pushes/pulls it
                val gdx = closestGate.x - playerX
                val gdz = closestGate.z - playerZ
                if (gdx * gdx + gdz * gdz > 0.04f) {
                    playerFacingYaw = lerpAngle(playerFacingYaw, atan2(gdx, gdz), (dt * 9f).coerceAtMost(1f))
                }
            }
        }
        // Smooth right-arm gate push animation blend
        val targetPush = if (isPushingGateNow) 1f else 0f
        gatePushBlend += (targetPush - gatePushBlend) * (dt * 12f).coerceAtMost(1f)

        // Keep legacy progress mirrors synced for compatibility
        for (eb in enterableBuildings) {
            eb.doorOpenProgress = eb.gate.openProgress
        }
        for (shop in roadsideShops) {
            shop.shutterOpenProgress = shop.gate.openProgress
        }

        // --- 2. Vehicle Driving / Bullet Riding OR On-Foot Player Locomotion ---
        val rawX = input.moveX.coerceIn(-1f, 1f)
        val rawY = input.moveY.coerceIn(-1f, 1f)
        val mag = sqrt(rawX * rawX + rawY * rawY)
        var wantsSprint = false

        val activeCar = drivenCar
        val activeBike = riddenBike

        if (activeCar != null || activeBike != null) {
            nearestInteract = if (activeCar != null) "Exit Sports Car" else "Dismount Bullet 350"
            if (hornNow && activeCar != null) {
                listener.onCarHorn()
            }
            if (interactNow && !interactConsumed) {
                interactConsumed = true
                val yawRad = (if (activeCar != null) activeCar.yawDeg else activeBike!!.yawDeg) * PI.toFloat() / 180f
                val sideDist = if (activeCar != null) 2.2f else 1.45f
                val exitResolved = CityWorldData.resolvePlayerPhysics(
                    nextX = playerX + cos(yawRad) * sideDist,
                    nextZ = playerZ - sin(yawRad) * sideDist,
                    currentY = 0f,
                    enterableBuildings = enterableBuildings,
                    movingCars = cars,
                    npcs = npcs,
                    activeParkedCars = parkedCars,
                    activeBulletBikes = bulletBikes,
                    activeShops = roadsideShops,
                    chunkGrid = chunkGrid
                )
                playerX = exitResolved.x
                playerZ = exitResolved.z
                playerY = exitResolved.supportFloorY
                drivenCar = null
                riddenBike = null
                vehicleSpeed = 0f
                if (activeCar != null) {
                    listener.onCarExited()
                    listener.onInteractMessage("Exited Car")
                } else {
                    listener.onInteractMessage("Dismounted Bullet 350")
                }
            } else {
                val isCar = activeCar != null
                val maxFwd = (if (isCar) 20f else 18f) * (if (input.isSprinting) 1.38f else 1f)
                val maxRev = if (isCar) -7.5f else -4.5f
                val accel = if (isCar) 15f else 18f
                val throttle = -rawY
                val prevSpeed = vehicleSpeed

                // Detect hard acceleration / engine revving in car
                if (isCar && ((throttle > 0.72f && prevSpeed in 0.2f..5.5f) || (input.isSprinting && throttle > 0.55f)) && worldTime - lastCarRevTime > 2.4f) {
                    lastCarRevTime = worldTime
                    listener.onCarAccelerateRev()
                }

                // Detect emergency braking / tire skid at speed
                val isEmergencyBraking = isCar && ((throttle < -0.42f && prevSpeed > 8.5f) || (abs(rawX) > 0.78f && prevSpeed > 13.5f))
                if (isEmergencyBraking) {
                    lastHardBrakeTime = worldTime
                    if (worldTime - lastCarSkidTime > 1.8f) {
                        lastCarSkidTime = worldTime
                        listener.onCarEmergencyBrakeSkid()
                    }
                }

                if (abs(throttle) > 0.08f) {
                    vehicleSpeed = (vehicleSpeed + throttle * accel * dt).coerceIn(maxRev, maxFwd)
                } else {
                    val drag = (1f - dt * 3.8f).coerceAtLeast(0f)
                    vehicleSpeed *= drag
                    if (abs(vehicleSpeed) < 0.08f) vehicleSpeed = 0f
                }

                val steerFactor = (abs(vehicleSpeed) / 4.0f).coerceIn(0.25f, 1.0f) * (if (vehicleSpeed >= -0.1f) 1f else -1f)
                val turnRateDeg = if (isCar) 115f else 142f
                val curYawDeg = if (isCar) activeCar!!.yawDeg else activeBike!!.yawDeg
                val nextYawDeg = (curYawDeg - rawX * turnRateDeg * steerFactor * dt) % 360f
                val rad = nextYawDeg * PI.toFloat() / 180f
                val curX = if (isCar) activeCar!!.x else activeBike!!.x
                val curZ = if (isCar) activeCar!!.z else activeBike!!.z
                val nextX = curX + sin(rad) * vehicleSpeed * dt
                val nextZ = curZ + cos(rad) * vehicleSpeed * dt

                val resolved = CityWorldData.resolvePlayerPhysics(
                    nextX = nextX,
                    nextZ = nextZ,
                    currentY = 0f,
                    enterableBuildings = enterableBuildings,
                    movingCars = cars,
                    npcs = npcs,
                    playerRadius = if (isCar) 1.35f else 0.72f,
                    activeParkedCars = parkedCars,
                    activeBulletBikes = bulletBikes,
                    activeShops = roadsideShops,
                    ignoreCar = activeCar,
                    ignoreBike = activeBike,
                    chunkGrid = chunkGrid
                )
                val movedDx = resolved.x - curX
                val movedDz = resolved.z - curZ
                val movedDist = sqrt(movedDx * movedDx + movedDz * movedDz)
                totalDistance += movedDist
                val impactSpeed = abs(vehicleSpeed)
                if (impactSpeed > 1.6f && movedDist < impactSpeed * dt * 0.35f) {
                    if (isCar && worldTime - lastCarCrashTime > 0.65f) {
                        lastCarCrashTime = worldTime
                        val crashType = when {
                            impactSpeed >= 8.5f && (worldTime - lastHardBrakeTime) < 1.25f -> CarCrashType.HIGH_SPEED_BRAKE_CRASH
                            impactSpeed >= 9.5f -> CarCrashType.HEAVY_IMPACT
                            else -> CarCrashType.LIGHT_BUMP
                        }
                        listener.onCarCrash(crashType)
                    }
                    vehicleSpeed *= 0.25f // Solid collision rebound damping
                }

                if (isCar) {
                    activeCar!!.x = resolved.x
                    activeCar.z = resolved.z
                    activeCar.yawDeg = nextYawDeg
                    activeCar.wheelAngleDeg = (activeCar.wheelAngleDeg + vehicleSpeed * dt * 165f) % 360f
                } else {
                    activeBike!!.x = resolved.x
                    activeBike.z = resolved.z
                    activeBike.yawDeg = nextYawDeg
                    activeBike.wheelAngleDeg = (activeBike.wheelAngleDeg + vehicleSpeed * dt * 185f) % 360f
                }
                playerX = resolved.x
                playerZ = resolved.z
                playerY = 0f
                playerFacingYaw = rad
                currentBuilding = null
                currentFloorNumber = 0
                isMovingNow = abs(vehicleSpeed) > 0.25f
                input.jumpQueued = false
                // GTA 5 Smart Vehicle Camera:
                // 100% free 360-deg look whenever player drags; after 1.65s idle while driving, smoothly
                // aligns BEHIND vehicle when driving forward (rad + PI) and IN FRONT looking back when reversing (rad)!
                val timeSinceDrag = worldTime - lastManualCameraDragTime
                if (timeSinceDrag > 1.65f && abs(vehicleSpeed) > 0.85f) {
                    val desiredCamYaw = if (vehicleSpeed >= 0f) (rad + PI.toFloat()) else rad
                    val alignRate = if (vehicleSpeed >= 0f) 3.2f else 2.5f
                    cameraYaw = lerpAngle(cameraYaw, desiredCamYaw, (dt * alignRate).coerceAtMost(1f))
                    cameraPitch += (0.24f - cameraPitch) * (dt * 1.4f).coerceAtMost(1f)
                }
            }
        } else {
            // On-Foot Locomotion
            wantsSprint = input.isSprinting && stamina > 5f && mag > 0.15f
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
                    val surface = CityWorldData.detectGroundSurface(playerX, playerZ, currentBuilding, roadsideShops)
                    listener.onFootstep(wantsSprint, surface)
                }
                prevSinCycle = curSin
            } else {
                walkCycle *= 0.85f
                prevSinCycle = 0f
            }

            // Full Solid 3D Physics Collision Resolution
            val resolved = CityWorldData.resolvePlayerPhysics(
                nextX = targetX,
                nextZ = targetZ,
                currentY = playerY,
                enterableBuildings = enterableBuildings,
                movingCars = cars,
                npcs = npcs,
                activeParkedCars = parkedCars,
                activeBulletBikes = bulletBikes,
                activeShops = roadsideShops,
                chunkGrid = chunkGrid
            )
            val movedDx = resolved.x - playerX
            val movedDz = resolved.z - playerZ
            totalDistance += sqrt(movedDx * movedDx + movedDz * movedDz)
            playerX = resolved.x
            playerZ = resolved.z
            currentBuilding = resolved.insideBuilding
            currentFloorNumber = resolved.floorNumber

            // Detect if player is pushing against a wall/obstacle to trigger Wall Vault on jump!
            val blockedDist = sqrt((targetX - resolved.x) * (targetX - resolved.x) + (targetZ - resolved.z) * (targetZ - resolved.z))
            val isPressedAgainstObstacle = isMovingNow && blockedDist > 0.025f

            // Vertical jump, wall vault & soft/heavy landing detection
            if (input.jumpQueued) {
                input.jumpQueued = false
                if (isGrounded) {
                    if (isPressedAgainstObstacle) {
                        playerVelY = 10.6f
                        isGrounded = false
                        peakAirY = playerY
                        listener.onPlayerWallVault()
                    } else {
                        playerVelY = 9.4f
                        isGrounded = false
                        peakAirY = playerY
                        listener.onJumpPerformed()
                    }
                }
            }
            val supportY = resolved.supportFloorY
            if (!isGrounded) {
                playerY += playerVelY * dt
                if (playerY > peakAirY) peakAirY = playerY
                playerVelY -= 24f * dt
                if (playerY <= supportY) {
                    val fallHeight = (peakAirY - supportY).coerceAtLeast(0f)
                    playerY = supportY
                    playerVelY = 0f
                    isGrounded = true
                    listener.onPlayerLand(heavy = fallHeight >= 2.2f)
                    peakAirY = supportY
                }
            } else {
                val diffY = supportY - playerY
                if (abs(diffY) > 1.2f && diffY < 0f) {
                    isGrounded = false
                    playerVelY = 0f
                    peakAirY = playerY
                } else {
                    playerY += diffY * (dt * 18f).coerceAtMost(1f)
                }
            }
        }

        // --- 3. Update autonomous NPC Men & Women along sidewalks ---
        for (npc in npcs) {
            if (npc.pauseTimer > 0f) {
                npc.pauseTimer -= dt
                continue
            }
            val dxP = playerX - npc.x
            val dzP = playerZ - npc.z
            if (!inVehicle && dxP * dxP + dzP * dzP < 1.3f * 1.3f && playerY < 1.8f) {
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

        // --- 4. Update traffic cars with Traffic Signal awareness & horn alert ---
        val signalGreenForX = ((worldTime / 9.0f).toInt() % 2 == 0)
        for (car in cars) {
            val dxP = playerX - car.x
            val dzP = playerZ - car.z
            val forwardDist = if (car.axisX) dxP * car.dir else dzP * car.dir
            val lateralDist = if (car.axisX) abs(dzP) else abs(dxP)
            val playerBlocking = playerY < 1.6f && forwardDist in 0.5f..8.2f && lateralDist < 2.2f

            if (playerBlocking && worldTime - lastHornTime > 2.6f) {
                lastHornTime = worldTime
                listener.onCarHorn()
            }

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

        // --- 5. Closer cinematic third-person camera distance (Free Fire / GTA style) ---
        val targetCamDist = when {
            drivenCar != null -> 7.2f
            riddenBike != null -> 5.4f
            currentBuilding != null -> 3.3f
            else -> 4.6f
        }
        currentCamDist += (targetCamDist - currentCamDist) * (dt * 7f).coerceAtMost(1f)

        // --- 6. Collectibles, Zone Discovery, Clouds, Birds & Vehicle Enter Interactions ---
        for (orb in CityWorldData.collectibles) {
            if (collectedOrbs.contains(orb.id)) continue
            val dx = playerX - orb.x
            val dy = (playerY + 1.0f) - orb.y
            val dz = playerZ - orb.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist < 2.2f || (interactNow && !interactConsumed && dist < 3.8f)) {
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

        for (c in skyClouds) {
            c.x += c.speedX * dt
            if (c.x > 480f) c.x = -480f
        }
        for (bird in flyingBirds) {
            bird.angle = (bird.angle + bird.angularSpeed * dt) % (2f * PI.toFloat())
            bird.wingPhase += dt * 7.8f
        }

        // Enter/Drive Parked Cars & Mount/Ride Royal Enfield Bullet 350 Motorcycles!
        if (!inVehicle && playerY < 1.5f) {
            for (bike in bulletBikes) {
                val dxB = playerX - bike.x
                val dzB = playerZ - bike.z
                if (dxB * dxB + dzB * dzB < 3.4f * 3.4f) {
                    if (nearestInteract == null) nearestInteract = "Ride ${bike.label}"
                    if (interactNow && !interactConsumed) {
                        riddenBike = bike
                        vehicleSpeed = 0f
                        lastManualCameraDragTime = -10f
                        interactConsumed = true
                        listener.onInteractMessage("Riding ${bike.label} — 350cc Thumper Active!")
                        break
                    }
                }
            }
        }
        if (!inVehicle && riddenBike == null && playerY < 1.5f) {
            for (pc in parkedCars) {
                val dxC = playerX - pc.x
                val dzC = playerZ - pc.z
                if (dxC * dxC + dzC * dzC < 3.8f * 3.8f) {
                    if (nearestInteract == null) nearestInteract = "Drive Sports Car"
                    if (interactNow && !interactConsumed) {
                        drivenCar = pc
                        vehicleSpeed = 0f
                        lastManualCameraDragTime = -10f
                        interactConsumed = true
                        listener.onCarEntered()
                        listener.onInteractMessage("Driving Car — Drag to look freely, HORN or EXIT anytime!")
                        break
                    }
                }
            }
        }

        // Check active map waypoint arrival
        if (waypointActive) {
            val wdx = playerX - waypointX
            val wdz = playerZ - waypointZ
            if (wdx * wdx + wdz * wdz <= 7.5f * 7.5f) {
                waypointActive = false
                listener.onWaypointReached()
            }
        }

        // Clean open central chauraha intersection (no fountain obstacle)
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastTelemetryReportMs >= 90L) {
            lastTelemetryReportMs = nowMs
            emitTelemetry(isMovingNow, wantsSprint, nearestInteract)
            emitAmbientAudioUpdate()
        }
    }

    private fun emitAmbientAudioUpdate() {
        val mode = when {
            drivenCar != null -> ActiveVehicleMode.CAR
            riddenBike != null -> ActiveVehicleMode.BULLET_BIKE
            else -> ActiveVehicleMode.ON_FOOT
        }
        val speedRatio = (abs(vehicleSpeed) / 22f).coerceIn(0f, 1f)

        // Distance to nearest active helicopter on Army Base helipad
        var heliDist = 999f
        for (hp in CityWorldData.helipads) {
            if (!hp.hasHelicopter) continue
            val dx = playerX - hp.x
            val dz = playerZ - hp.z
            val d = sqrt(dx * dx + dz * dz + chopperLiftY * chopperLiftY)
            if (d < heliDist) heliDist = d
        }

        // Distance to ocean/beach/port shoreline (z >= WATERFRONT_Z)
        val oceanDist = (CityWorldData.WATERFRONT_Z - playerZ).coerceAtLeast(0f)

        // Proximity to trees/park for daytime birds chirping
        var nearbyTrees = 0
        for (t in visTrees) {
            val dx = playerX - t.x
            val dz = playerZ - t.z
            if (dx * dx + dz * dz < 26f * 26f) {
                nearbyTrees++
                if (nearbyTrees >= 6) break
            }
        }
        val nearTreesFactor = (nearbyTrees / 6f).coerceIn(0f, 1f)

        listener.onAmbientAudioUpdate(
            vehicleMode = mode,
            vehicleSpeedRatio = speedRatio,
            heliDistMeters = heliDist,
            heliTakeoffFactor = (chopperLiftY / 9.5f).coerceIn(0f, 1f),
            oceanDistMeters = oceanDist,
            rainIntensity = rainIntensity,
            nightFactor = nightFactor,
            isIndoors = currentBuilding != null,
            nearTreesFactor = nearTreesFactor
        )
    }

    private fun emitTelemetry(
        isMoving: Boolean = false,
        isSprinting: Boolean = false,
        nearbyInteract: String? = null
    ) {
        val insideShop = roadsideShops.firstOrNull { it.isInsideShop(playerX, playerZ) }
        val locLabel = currentBuilding?.let { b ->
            val floorText = if (currentFloorNumber >= 2) "2nd Floor" else "Ground Floor"
            "${b.name} • $floorText"
        } ?: insideShop?.let { "${it.name} • Interior" } ?: when {
            playerZ > CityWorldData.WATERFRONT_Z -> "Saini Waterfront Pier"
            playerZ in 350f..CityWorldData.WATERFRONT_Z && (playerX > 55f || playerX < -55f) -> "Saini Harbor & Container Port"
            playerX > 290f && playerZ in -370f..-130f -> "Saini International Airport"
            playerX < -280f && playerZ in -320f..-180f -> "Saini Army Base"
            playerX in -98f..-54f && playerZ in 54f..98f -> "Emerald City Park"
            playerX > 120f && playerZ in 40f..340f -> "Saini Industrial Factory Zone"
            playerZ < -170f && playerX in -160f..160f -> "North Skyline Financial District"
            playerX < -150f && playerZ in -120f..340f -> "West Suburbs & Villa Estates"
            else -> "Saini City • Street Level"
        }

        val mode = when {
            drivenCar != null -> ActiveVehicleMode.CAR
            riddenBike != null -> ActiveVehicleMode.BULLET_BIKE
            else -> ActiveVehicleMode.ON_FOOT
        }
        val timeLabel = when {
            nightFactor > 0.65f -> "Night"
            nightFactor > 0.22f -> if (timeOfDay < 0.5f) "Dawn" else "Sunset"
            else -> "Day"
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
                currentLocationLabel = locLabel,
                weatherState = weatherState,
                isNight = nightFactor > 0.5f,
                timeOfDayLabel = timeLabel,
                vehicleMode = mode,
                vehicleSpeedKmh = (abs(vehicleSpeed) * 3.6f).toInt(),
                nearbyGateName = nearbyGate?.name,
                nearbyGateProgress = nearbyGate?.openProgress ?: 0f
            )
        )
    }

    /**
     * Collect ALL spatial 20m x 20m WorldChunks inside the player's 360-degree Circular Zone.
     * Everything inside the circular zone stays loaded (even behind the camera); everything
     * outside the zone is unloaded/culled.
     */
    private fun gatherStreamedChunksAndObjects() {
        activeChunks.clear()
        visBuildings.clear(); visEnterables.clear(); visRoadsideShops.clear(); visStaticObstacles.clear(); visPortContainers.clear()
        visTrees.clear(); visStreetLights.clear(); visTrafficSignals.clear(); visBenches.clear()
        visKiosks.clear(); visParkedCars.clear(); visMovingCars.clear(); visNpcs.clear()
        visBulletBikes.clear(); visGrassTufts.clear(); visArmyBunkers.clear()
        visBarbedFences.clear(); visHelipads.clear(); visArmyVehicles.clear(); visBirds.clear()
        visRoads.clear(); visIntersections.clear(); visColonyPads.clear(); visColonyAlleys.clear()
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

                activeChunks.add(chunk)
                for (r in chunk.roadSegments) {
                    if (isObjectInZoneAndCone(r.x, r.z, r.length * 0.55f)) visRoads.add(r)
                }
                for (ix in chunk.intersections) {
                    if (isObjectInZoneAndCone(ix.x, ix.z, 7.5f)) visIntersections.add(ix)
                }
                for (pad in chunk.colonyPads) {
                    if (isObjectInZoneAndCone(pad.x, pad.z, maxOf(pad.width, pad.depth) * 0.65f)) visColonyPads.add(pad)
                }
                for (al in chunk.colonyAlleys) {
                    if (isObjectInZoneAndCone(al.x, al.z, al.length * 0.58f)) visColonyAlleys.add(al)
                }
                for (b in chunk.buildings) {
                    if (isObjectInZoneAndCone(b.x, b.z, maxOf(b.width, b.depth) * 0.78f)) visBuildings.add(b)
                }
                for (eb in chunk.enterables) {
                    if (isObjectInZoneAndCone(eb.centerX, eb.centerZ, 12.0f)) visEnterables.add(eb)
                }
                for (shop in chunk.roadsideShops) {
                    if (isObjectInZoneAndCone(shop.x, shop.z, 5.2f)) visRoadsideShops.add(shop)
                }
                for (obs in chunk.staticObstacles) {
                    if (isObjectInZoneAndCone(obs.x, obs.z, obs.size + 1.4f)) visStaticObstacles.add(obs)
                }
                for (c in chunk.portContainers) {
                    if (isObjectInZoneAndCone(c.x, c.z, maxOf(c.length, c.width) * 0.65f)) visPortContainers.add(c)
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
                for (g in chunk.grassTufts) {
                    if (isObjectInZoneAndCone(g.x, g.z, 1.4f)) visGrassTufts.add(g)
                }
                for (bk in chunk.armyBunkers) {
                    if (isObjectInZoneAndCone(bk.x, bk.z, maxOf(bk.width, bk.depth) * 0.7f)) visArmyBunkers.add(bk)
                }
                for (fn in chunk.barbedFences) {
                    if (isObjectInZoneAndCone(fn.x, fn.z, fn.length * 0.55f)) visBarbedFences.add(fn)
                }
                for (hp in chunk.helipads) {
                    if (isObjectInZoneAndCone(hp.x, hp.z, hp.size * 0.75f)) visHelipads.add(hp)
                }
                for (av in chunk.armyVehicles) {
                    if (isObjectInZoneAndCone(av.x, av.z, 4.0f)) visArmyVehicles.add(av)
                }
            }
        }

        // Dynamic drivable cars, rideable Bullet bikes, moving traffic, NPCs & soaring birds
        for (pc in parkedCars) {
            if (pc === drivenCar || isObjectInZoneAndCone(pc.x, pc.z, 3.4f)) visParkedCars.add(pc)
        }
        for (bike in bulletBikes) {
            if (bike === riddenBike || isObjectInZoneAndCone(bike.x, bike.z, 2.2f)) visBulletBikes.add(bike)
        }
        for (car in cars) {
            if (isObjectInZoneAndCone(car.x, car.z, 3.4f)) visMovingCars.add(car)
        }
        for (npc in npcs) {
            if (isObjectInZoneAndCone(npc.x, npc.z, 1.5f)) visNpcs.add(npc)
        }
        for (bird in flyingBirds) {
            val bx = bird.centerX + cos(bird.angle) * bird.orbitRadius
            val bz = bird.centerZ + sin(bird.angle) * bird.orbitRadius
            if (isObjectInZoneAndCone(bx, bz, 6.0f)) visBirds.add(bird)
        }
    }

    private fun isObjectInZoneAndCone(objX: Float, objZ: Float, objRadius: Float): Boolean {
        val dxP = objX - playerX
        val dzP = objZ - playerZ
        val distPlayerSq = dxP * dxP + dzP * dzP
        val maxR = streamingRadiusMeters + objRadius
        return if (distPlayerSq <= maxR * maxR) {
            visibleObjectsThisFrame++
            true
        } else {
            culledObjectsThisFrame++
            false
        }
    }

    private fun renderScene() {
        val sunAngleRad = (timeOfDay - 0.25f) * 2f * PI.toFloat()

        // Dynamic sky/fog colors smoothly adapting to Day, Night & Rain!
        val dayFogR = 0.62f * (1f - rainIntensity * 0.35f) + 0.38f * rainIntensity * 0.35f
        val dayFogG = 0.78f * (1f - rainIntensity * 0.38f) + 0.44f * rainIntensity * 0.38f
        val dayFogB = 0.93f * (1f - rainIntensity * 0.38f) + 0.52f * rainIntensity * 0.38f
        val fogR = dayFogR * (1f - nightFactor) + 0.05f * nightFactor
        val fogG = dayFogG * (1f - nightFactor) + 0.08f * nightFactor
        val fogB = dayFogB * (1f - nightFactor) + 0.16f * nightFactor

        GLES20.glClearColor(fogR, fogG, fogB, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_STENCIL_BUFFER_BIT)
        GLES20.glUseProgram(assets.shader.programId)
        dc.boundVboId = -1

        // Camera orbit with ground-clearance AND building-wall collision clipping (prevents camera entering buildings!)
        camForwardX = -sin(cameraYaw)
        camForwardZ = -cos(cameraYaw)
        var effDist = currentCamDist
        val sinPitch = sin(cameraPitch)
        val cosPitch = cos(cameraPitch)
        val lookAtY = playerY + (if (drivenCar != null) 1.55f else 1.48f)
        if (currentBuilding == null && sinPitch < -0.05f) {
            val maxDistAboveGround = (lookAtY - 0.55f) / (-sinPitch)
            effDist = minOf(effDist, maxDistAboveGround.coerceAtLeast(1.8f))
        }
        val rayDirX = sin(cameraYaw) * cosPitch
        val rayDirZ = cos(cameraYaw) * cosPitch
        if (currentBuilding == null) {
            effDist = clampCameraDistanceAgainstWalls(playerX, playerZ, rayDirX, rayDirZ, effDist)
        }

        camPosX = playerX + rayDirX * effDist
        val desiredCamY = lookAtY + 0.15f + sinPitch * effDist
        camPosY = if (currentBuilding != null) {
            val floorBase = if (currentFloorNumber >= 2) currentBuilding!!.floorHeight else 0f
            desiredCamY.coerceIn(floorBase + 0.9f, floorBase + currentBuilding!!.floorHeight - 0.45f)
        } else {
            desiredCamY.coerceAtLeast(0.55f)
        }
        camPosZ = playerZ + rayDirZ * effDist

        Matrix.setLookAtM(
            dc.viewMatrix, 0,
            camPosX, camPosY, camPosZ,
            playerX, lookAtY, playerZ,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(dc.vpMatrix, 0, dc.projMatrix, 0, dc.viewMatrix, 0)

        // Gather streamed chunks & visible entities in player's zone & view direction
        gatherStreamedChunksAndObjects()

        // Global Lighting (Day/Night/Rain dynamic sun & moon light + fog)
        val lDirX = cos(sunAngleRad) * 0.65f
        val lDirY = abs(sin(sunAngleRad)).coerceAtLeast(0.42f)
        val lDirZ = 0.36f
        val rainDim = 1f - rainIntensity * 0.28f
        val sunR = ((1.00f * (1f - nightFactor) + 0.44f * nightFactor) * rainDim)
        val sunG = ((0.97f * (1f - nightFactor) + 0.54f * nightFactor) * rainDim)
        val sunB = ((0.92f * (1f - nightFactor) + 0.84f * nightFactor) * rainDim)
        val ambScale = ((1.00f * (1f - nightFactor) + 0.46f * nightFactor) * (1f - rainIntensity * 0.16f))

        GLES20.glUniform3f(assets.shader.uLightDirLoc, lDirX, lDirY, lDirZ)
        GLES20.glUniform3f(assets.shader.uSunColorLoc, sunR, sunG, sunB)
        GLES20.glUniform1f(assets.shader.uAmbientScaleLoc, ambScale)
        GLES20.glUniform3f(assets.shader.uCameraPosLoc, camPosX, camPosY, camPosZ)
        GLES20.glUniform3f(assets.shader.uFogColorLoc, fogR, fogG, fogB)
        val fogStart = (streamingRadiusMeters * 0.54f).coerceAtLeast(20f)
        val fogEnd = (streamingRadiusMeters + 16f).coerceAtMost(graphicsQuality.fogEnd)
        GLES20.glUniform1f(assets.shader.uFogStartLoc, fogStart)
        GLES20.glUniform1f(assets.shader.uFogEndLoc, fogEnd)

        // 0. Sky Dome (tex_env_sky_day <-> tex_env_sky_night), Additive Sun Glow & Moon!
        if (currentBuilding == null) {
            WorldAssetsDrawer.drawSkyDomeSunMoon(
                dc = dc,
                camX = camPosX, camY = camPosY, camZ = camPosZ,
                cameraYawRad = cameraYaw,
                sunAngleRad = sunAngleRad,
                nightFactor = nightFactor,
                rainIntensity = rainIntensity
            )
        }

        // 1. Ground Terrain, Animated Normal-Mapped Ocean Water, Paver Footpaths & Detailed Asphalt Roads (10x 1km x 1km Metropolis)
        dc.bindVbo(assets.quadMesh)
        dc.drawQuad(0f, 0f, -50f, 1080f, 940f, assets.texGrass, 140f, 120f, 0.94f, 0.98f, 0.92f, 0f, 0.04f, 8f, 0f)
        // Sandy coastal edge
        dc.drawQuad(0f, 0.01f, CityWorldData.WATERFRONT_Z + 1.5f, 1080f, 4.5f, assets.texConcrete, 96f, 1f, 0.92f, 0.84f, 0.68f, 0f, 0.08f, 12f, 0f)
        // Animated Ocean Water Mesh (tex_water_ocean diffuse + tex_water_normal bump map + scrolling UV waves!)
        val waveU = (worldTime * 0.030f) % 1f
        val waveV = (worldTime * 0.018f) % 1f
        dc.drawQuad(
            0f, -0.05f, CityWorldData.WATERFRONT_Z + 90f, 1120f, 180f,
            assets.texWaterOcean, 64f, 14f,
            0.90f, 0.96f, 1.0f,
            0.03f, 0.92f, 52f, 0.48f,
            uOffset = waveU,
            vOffset = waveV,
            normalTexId = assets.texWaterNormal,
            normalStrength = 0.72f
        )
        // Waterfront Paver Boardwalk (tex_footpath_paver_blocks) & Wooden Pier (tex_interior_wood_floor)
        dc.drawQuad(0f, 0.025f, CityWorldData.WATERFRONT_Z - 2.5f, 960f, 8.2f, assets.texFootpathPaverBlocks, 200f, 2.2f, 0.96f, 0.96f, 0.95f, 0f, 0.16f + roadWetness * 0.35f, 18f, 0.06f + roadWetness * 0.25f)
        // Port Container Dock Concrete Yards (East & West Waterfront)
        dc.drawQuad(140f, 0.020f, 384f, 136f, 30f, assets.texConcrete, 26f, 6f, 0.74f, 0.76f, 0.78f, 0f, 0.12f, 14f, 0.04f)
        dc.drawQuad(-120f, 0.020f, 384f, 96f, 30f, assets.texConcrete, 18f, 6f, 0.74f, 0.76f, 0.78f, 0f, 0.12f, 14f, 0.04f)

        val pierCenterZ = CityWorldData.WATERFRONT_Z + 18f
        if (isObjectInZoneAndCone(0f, pierCenterZ, 24f)) {
            dc.bindVbo(assets.cubeMesh)
            dc.drawBox(0f, 0.21f, pierCenterZ, 14.0f, 0.42f, 36.0f, assets.texInteriorWoodFloor, 4f, 10f, 0.88f, 0.76f, 0.64f, 0f, 0.22f, 16f, 0.04f)
            dc.drawBox(-6.8f, 0.78f, pierCenterZ, 0.22f, 0.75f, 36.0f, assets.texWhite, 1f, 1f, 0.25f, 0.27f, 0.30f, 0f, 0.3f, 18f, 0.1f)
            dc.drawBox( 6.8f, 0.78f, pierCenterZ, 0.22f, 0.75f, 36.0f, assets.texWhite, 1f, 1f, 0.25f, 0.27f, 0.30f, 0f, 0.3f, 18f, 0.1f)
            dc.bindVbo(assets.quadMesh)
        }

        // 1a. Urban Colony Concrete/Paver Pads (Y=0.014m) & Narrow Paved Colony Alleys / "Patli Galiyan" (Y=0.036m)
        for (pad in visColonyPads) {
            val padTex = if (pad.isPaverCourtyard) assets.texFootpathPaverBlocks else assets.texConcrete
            val repScale = if (pad.isPaverCourtyard) 3.4f else 6.0f
            val tint = if (pad.isPaverCourtyard) 0.86f else 0.78f
            dc.drawQuad(
                pad.x, 0.014f, pad.z, pad.width, pad.depth,
                padTex, (pad.width / repScale).coerceAtLeast(2f), (pad.depth / repScale).coerceAtLeast(2f),
                tint, tint, tint + 0.01f, 0f, 0.08f + roadWetness * 0.20f, 12f, 0.02f + roadWetness * 0.10f
            )
        }
        for (al in visColonyAlleys) {
            val yaw = if (al.isVertical) 0f else 90f
            dc.drawQuad(
                al.x, 0.036f, al.z,
                al.width, al.length,
                assets.texColonyAlley, 1f, (al.length / 4.2f).coerceAtLeast(2f),
                0.92f - roadWetness * 0.12f, 0.92f - roadWetness * 0.10f, 0.94f - roadWetness * 0.08f,
                0f, 0.18f + roadWetness * 0.36f, 20f, 0.05f + roadWetness * 0.22f,
                yawDeg = yaw
            )
        }

        // Streamed Road Segments & Non-Overlapping Sidewalks (ZERO corner footpath Z-fighting!)
        // Every road draws 2 separate 2.12m sidewalk strips trimmed back by 2.12m where intersecting another road,
        // and each intersection draws its 4 corner 2.12m x 2.12m sidewalk pads ONCE!
        val wetSpec = 0.26f + roadWetness * 0.62f
        val wetRefl = 0.08f + roadWetness * 0.48f
        val swWidth = 2.12f
        val swSpec = 0.10f + roadWetness * 0.30f
        val swRefl = 0.03f + roadWetness * 0.18f

        for (seg in visRoads) {
            val yaw = if (seg.isVertical) 0f else 90f
            val trimS = if (seg.trimStart) swWidth else 0f
            val trimE = if (seg.trimEnd) swWidth else 0f
            val swLen = (seg.length - trimS - trimE).coerceAtLeast(0.5f)
            val swShift = (trimS - trimE) * 0.5f
            val sideOffset = seg.width * 0.5f + swWidth * 0.5f // 5.0 + 1.06 = 6.06m

            if (seg.isVertical) {
                val swZ = seg.z + swShift
                dc.drawQuad(seg.x - sideOffset, 0.025f, swZ, swWidth, swLen, assets.texFootpathPaverBlocks, 0.85f, swLen / 2.6f, 0.96f, 0.96f, 0.96f, 0f, swSpec, 14f, swRefl)
                dc.drawQuad(seg.x + sideOffset, 0.025f, swZ, swWidth, swLen, assets.texFootpathPaverBlocks, 0.85f, swLen / 2.6f, 0.96f, 0.96f, 0.96f, 0f, swSpec, 14f, swRefl)
            } else {
                val swX = seg.x + swShift
                dc.drawQuad(swX, 0.025f, seg.z - sideOffset, swLen, swWidth, assets.texFootpathPaverBlocks, swLen / 2.6f, 0.85f, 0.96f, 0.96f, 0.96f, 0f, swSpec, 14f, swRefl)
                dc.drawQuad(swX, 0.025f, seg.z + sideOffset, swLen, swWidth, assets.texFootpathPaverBlocks, swLen / 2.6f, 0.85f, 0.96f, 0.96f, 0.96f, 0f, swSpec, 14f, swRefl)
            }

            dc.drawQuad(
                seg.x, 0.056f, seg.z,
                seg.width, seg.length,
                assets.texRoadAsphaltDetailed, 1f, seg.length / 8f,
                0.96f - roadWetness * 0.14f, 0.96f - roadWetness * 0.12f, 0.98f - roadWetness * 0.08f,
                0f, wetSpec, 32f, wetRefl, yawDeg = yaw
            )
            // Blended wet puddle reflection overlay during rain / post-rain wetness (tex_water_road_puddle_reflect)
            if (roadWetness > 0.02f) {
                dc.drawQuad(
                    seg.x, 0.072f, seg.z,
                    seg.width * 0.92f, seg.length,
                    assets.texWaterRoadPuddleReflect, 1.5f, seg.length / 10f,
                    0.85f, 0.92f, 1.0f,
                    0.06f, 0.96f, 56f, 0.68f,
                    yawDeg = yaw,
                    alpha = (roadWetness * 0.58f).coerceIn(0f, 0.65f)
                )
            }
        }

        // Clean Stripe-Free 4-Way Asphalt Intersections, Corner Footpath Pads, Zebra Crosswalks & White Stop-Bars!
        for (ix in visIntersections) {
            val cornerOff = ix.sizeX * 0.5f + swWidth * 0.5f // 5.0 + 1.06 = 6.06m
            dc.drawQuad(ix.x - cornerOff, 0.025f, ix.z - cornerOff, swWidth, swWidth, assets.texFootpathPaverBlocks, 0.85f, 0.85f, 0.96f, 0.96f, 0.96f, 0f, swSpec, 14f, swRefl)
            dc.drawQuad(ix.x + cornerOff, 0.025f, ix.z - cornerOff, swWidth, swWidth, assets.texFootpathPaverBlocks, 0.85f, 0.85f, 0.96f, 0.96f, 0.96f, 0f, swSpec, 14f, swRefl)
            dc.drawQuad(ix.x - cornerOff, 0.025f, ix.z + cornerOff, swWidth, swWidth, assets.texFootpathPaverBlocks, 0.85f, 0.85f, 0.96f, 0.96f, 0.96f, 0f, swSpec, 14f, swRefl)
            dc.drawQuad(ix.x + cornerOff, 0.025f, ix.z + cornerOff, swWidth, swWidth, assets.texFootpathPaverBlocks, 0.85f, 0.85f, 0.96f, 0.96f, 0.96f, 0f, swSpec, 14f, swRefl)

            // Clean dark weathered intersection asphalt (ZERO yellow lines passing through the center!)
            dc.drawQuad(
                ix.x, 0.062f, ix.z, ix.sizeX + 0.16f, ix.sizeZ + 0.16f,
                assets.texIntersectionAsphalt, 1f, 1f,
                0.98f - roadWetness * 0.14f, 0.98f - roadWetness * 0.12f, 1.0f - roadWetness * 0.08f,
                0f, wetSpec, 28f, wetRefl
            )
            if (roadWetness > 0.02f) {
                dc.drawQuad(
                    ix.x, 0.074f, ix.z,
                    ix.sizeX * 0.90f, ix.sizeZ * 0.90f,
                    assets.texWaterRoadPuddleReflect, 1.4f, 1.4f,
                    0.85f, 0.92f, 1.0f, 0.06f, 0.96f, 56f, 0.68f,
                    alpha = (roadWetness * 0.54f).coerceIn(0f, 0.60f)
                )
            }
            for (s in -3..3) {
                val offset = s * 1.15f
                dc.drawQuad(ix.x + offset, 0.080f, ix.z + 6.4f, 0.50f, 2.1f, assets.texWhite, 1f, 1f, 0.94f, 0.94f, 0.92f, 0.04f, 0.1f, 12f, 0f)
                dc.drawQuad(ix.x + offset, 0.080f, ix.z - 6.4f, 0.50f, 2.1f, assets.texWhite, 1f, 1f, 0.94f, 0.94f, 0.92f, 0.04f, 0.1f, 12f, 0f)
                dc.drawQuad(ix.x + 6.4f, 0.080f, ix.z + offset, 2.1f, 0.50f, assets.texWhite, 1f, 1f, 0.94f, 0.94f, 0.92f, 0.04f, 0.1f, 12f, 0f)
                dc.drawQuad(ix.x - 6.4f, 0.080f, ix.z + offset, 2.1f, 0.50f, assets.texWhite, 1f, 1f, 0.94f, 0.94f, 0.92f, 0.04f, 0.1f, 12f, 0f)
            }
            // Crisp white painted stop-bars behind each pedestrian crosswalk
            dc.drawQuad(ix.x + 2.35f, 0.080f, ix.z + 8.05f, 4.1f, 0.34f, assets.texWhite, 1f, 1f, 0.92f, 0.92f, 0.90f, 0.04f, 0.1f, 12f, 0f)
            dc.drawQuad(ix.x - 2.35f, 0.080f, ix.z - 8.05f, 4.1f, 0.34f, assets.texWhite, 1f, 1f, 0.92f, 0.92f, 0.90f, 0.04f, 0.1f, 12f, 0f)
            dc.drawQuad(ix.x + 8.05f, 0.080f, ix.z - 2.35f, 0.34f, 4.1f, assets.texWhite, 1f, 1f, 0.92f, 0.92f, 0.90f, 0.04f, 0.1f, 12f, 0f)
            dc.drawQuad(ix.x - 8.05f, 0.080f, ix.z + 2.35f, 0.34f, 4.1f, assets.texWhite, 1f, 1f, 0.92f, 0.92f, 0.90f, 0.04f, 0.1f, 12f, 0f)
        }

        // 1b. Saini Army Base Compound Concrete Pad & Saini International Airport Runway Tarmac
        if (isObjectInZoneAndCone(CityWorldData.ARMY_BASE_CENTER_X, CityWorldData.ARMY_BASE_CENTER_Z, 68f)) {
            dc.bindVbo(assets.quadMesh)
            dc.drawQuad(CityWorldData.ARMY_BASE_CENTER_X, 0.026f, CityWorldData.ARMY_BASE_CENTER_Z, 124f, 96f, assets.texConcrete, 24f, 20f, 0.72f, 0.73f, 0.68f, 0f, 0.08f, 12f, 0.02f)
        }
        WorldAssetsDrawer.drawAirportGround(dc, worldTime) { x, z, r -> isObjectInZoneAndCone(x, z, r) }

        // Update rigged Avaturn skeletal pose (including right-arm hand gate push!) before both shadow & main passes!
        if (drivenCar == null) {
            glbPlayer?.updatePose(
                worldTime = worldTime,
                walkCycle = if (riddenBike != null) 0f else walkCycle,
                isMoving = if (riddenBike != null) false else isMovingNow,
                isSprinting = if (riddenBike != null) false else (input.isSprinting && isMovingNow),
                isGrounded = isGrounded,
                isRidingBike = riddenBike != null,
                gateHandReach = gatePushBlend
            )
        }

        // 2. SUN-PROJECTED SILHOUETTE SHADOW PASS
        renderShapeAccurateShadowPass()

        // 3. Airport 3D Structures (Control Tower, Corrugated Hangar, Parked & Flying Jetliners)
        WorldAssetsDrawer.drawAirportStructures(dc, worldTime) { x, z, r -> isObjectInZoneAndCone(x, z, r) }
        // Overhead Flying Jetliner soaring across Airport & City sky
        if (jetFlybyCycle < 11.5f) {
            WorldAssetsDrawer.drawJetliner(dc, CityWorldData.AIRPORT_RUNWAY_X, 26f, jetCurrentZ, yawDeg = 0f, showGear = false)
        }

        // 4. Enterable Multi-Floor Buildings, Roadside Shops & Port Shipping Containers
        for (eb in visEnterables) {
            drawEnterableBuilding(eb)
        }
        for (shop in visRoadsideShops) {
            WorldAssetsDrawer.drawRoadsideShop(dc, shop, nightFactor)
        }
        for (c in visPortContainers) {
            WorldAssetsDrawer.drawPortContainer(dc, c)
        }

        // 5. Skyscrapers, Multi-Story Windowed Apartments, Red-Brick Houses, Corrugated Factories & Solid Exterior Crates/Drums
        for (b in visBuildings) {
            drawRealisticSkyscraper(b)
        }
        for (obs in visStaticObstacles) {
            if (obs.isOilDrum) {
                MultiAnglePropDrawer.drawSteelOilDrum(dc, obs.x, obs.yFloor, obs.z, obs.yawDeg)
            } else {
                MultiAnglePropDrawer.drawCargoCrate(dc, obs.x, obs.yFloor, obs.z, obs.yawDeg, obs.size)
            }
        }

        // 5b. Army Base Structures: Concrete Bunkers, Helipads + Animated Camo Helicopter, Camo Vehicles, Barbed Fences
        for (hp in visHelipads) {
            WorldAssetsDrawer.drawHelipadAndChopper(dc, hp, worldTime, chopperLiftY, chopperRotorRate)
        }
        for (bk in visArmyBunkers) {
            WorldAssetsDrawer.drawArmyBunker(dc, bk)
        }
        for (av in visArmyVehicles) {
            WorldAssetsDrawer.drawArmyVehicle(dc, av)
        }
        for (fn in visBarbedFences) {
            WorldAssetsDrawer.drawBarbedFence(dc, fn)
        }

        // 6. Realistic Multi-Part Branched Trees (Broad, Palm, Pine with tex_tree_bark) & 3D Grass Tufts
        val treeStep = if (graphicsQuality == GraphicsQuality.LOW) 2 else 1
        for (i in visTrees.indices step treeStep) {
            CityPropDrawer.drawRealisticTree(dc, visTrees[i], worldTime, graphicsQuality)
        }
        val grassStep = if (graphicsQuality == GraphicsQuality.LOW) 3 else if (graphicsQuality == GraphicsQuality.MEDIUM) 2 else 1
        WorldAssetsDrawer.drawGrassTufts(dc, visGrassTufts, worldTime, grassStep)

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

        // 8. Realistic Curved GTA-Style Cars & Royal Enfield Bullet Motorcycles (with spinning tex_vehicle_tire_tread!)
        for (pc in visParkedCars) {
            CityPropDrawer.drawCar(dc, pc.x, pc.z, pc.yawDeg, pc.wheelAngleDeg, pc.r, pc.g, pc.b)
        }
        for (bike in visBulletBikes) {
            WorldAssetsDrawer.drawBulletBike(dc, bike, isRidden = (bike === riddenBike))
        }
        for (car in visMovingCars) {
            val yawDeg = if (car.axisX) (if (car.dir > 0f) 90f else -90f) else (if (car.dir > 0f) 0f else 180f)
            CityPropDrawer.drawCar(dc, car.x, car.z, yawDeg, car.wheelAngleDeg, car.r, car.g, car.b)
        }

        // 9. Autonomous NPC Men & Women Pedestrians + Soaring Feathered Birds
        for (npc in visNpcs) {
            CityPropDrawer.drawNpcCharacter(dc, npc, worldTime)
        }
        WorldAssetsDrawer.drawFlyingBirds(dc, visBirds)

        // 10. Subtle Golden Collectible Coins (no bulky neon cyan/yellow floating spheres cluttering streets)
        dc.bindVbo(assets.cylinderMesh)
        for (orb in CityWorldData.collectibles) {
            if (collectedOrbs.contains(orb.id) || !isObjectInZoneAndCone(orb.x, orb.z, 1.5f)) continue
            val bobY = orb.y + sin(worldTime * 3.0f + orb.id) * 0.16f
            dc.drawMeshTransformed(
                orb.x, bobY, orb.z,
                0.42f, 0.09f, 0.42f, worldTime * 110f + orb.id * 25f, 90f, 0f,
                assets.texWhite, 1f, 1f, 1.0f, 0.76f, 0.12f, 1.0f, 0.38f, 0.85f, 36f, 0.40f, assets.cylinderMesh
            )
        }

        // 11. Rigged Avaturn 3D Player Character (assets/player.glb — hidden inside car cabin when driving, seated on Bullet when riding!)
        if (drivenCar == null) {
            val riderYOffset = if (riddenBike != null) 0.42f else 0f
            val rigged = glbPlayer
            if (rigged != null) {
                rigged.draw(
                    dc = dc,
                    x = playerX, y = playerY + riderYOffset, z = playerZ,
                    yawRad = playerFacingYaw,
                    lightDirX = lDirX, lightDirY = lDirY, lightDirZ = lDirZ,
                    sunR = sunR, sunG = sunG, sunB = sunB,
                    ambientScale = ambScale,
                    camX = camPosX, camY = camPosY, camZ = camPosZ,
                    fogR = fogR, fogG = fogG, fogB = fogB,
                    fogStart = fogStart, fogEnd = fogEnd
                )
            } else {
                CityPropDrawer.drawHumanPlayer(
                    dc = dc,
                    x = playerX, y = playerY + riderYOffset, z = playerZ,
                    yawRad = playerFacingYaw,
                    walkCycle = if (riddenBike != null) 0f else walkCycle,
                    isMoving = if (riddenBike != null) false else isMovingNow,
                    isSprinting = if (riddenBike != null) false else (input.isSprinting && isMovingNow),
                    isGrounded = isGrounded,
                    worldTime = worldTime,
                    skin = activeSkin
                )
            }
        }

        // 12. Active Map Waypoint Tactical Beacon (visible from afar in 3D city!)
        if (waypointActive) {
            val pulse = 0.85f + 0.15f * sin(worldTime * 4.5f)
            GLES20.glDepthMask(false)
            GLES20.glDisable(GLES20.GL_CULL_FACE)
            dc.bindVbo(assets.cylinderMesh)
            dc.drawMeshTransformed(
                waypointX, 26f, waypointZ,
                0.72f * pulse, 52f, 0.72f * pulse,
                0f, 0f, 0f,
                assets.texWhite, 1f, 1f,
                1.0f, 0.80f, 0.16f, 0.28f,
                1.0f, 0.1f, 8f, 0f, assets.cylinderMesh
            )
            dc.bindVbo(assets.ringMesh)
            dc.drawMeshTransformed(
                waypointX, 0.12f, waypointZ,
                3.4f * pulse, 1f, 3.4f * pulse,
                0f, 0f, 0f,
                assets.texWhite, 1f, 1f,
                1.0f, 0.82f, 0.18f, 0.75f,
                1.0f, 0.1f, 8f, 0f, assets.ringMesh
            )
            GLES20.glEnable(GLES20.GL_CULL_FACE)
            GLES20.glDepthMask(true)
        }

        // 13. Volumetric Drifting Sky Clouds & Falling UV-Scrolling Rain Sheets (tex_env_rain_streaks)
        if (currentBuilding == null) {
            WorldAssetsDrawer.drawSkyClouds(dc, skyClouds, cameraYaw)
            if (rainIntensity > 0.02f) {
                WorldAssetsDrawer.drawRainSheets(dc, camPosX, camPosY, camPosZ, worldTime, rainIntensity)
            }
        }
    }

    private fun renderShapeAccurateShadowPass() {
        val hasStencil = hasStencilProvider()
        val shadowAlphaBase = if (hasStencil) 0.38f else 0.24f
        val shadowAlpha = shadowAlphaBase * (1f - nightFactor * 0.45f) * (1f - rainIntensity * 0.50f)
        if (shadowAlpha <= 0.04f) return

        GLES20.glUseProgram(assets.shadowShader.programId)
        dc.boundShadowVboId = -1
        GLES20.glUniform4f(assets.shadowShader.uShadowColorLoc, 0.04f, 0.05f, 0.08f, shadowAlpha)

        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        // Polygon offset eliminates any shadow-ground Z-fighting flicker at shallow camera angles
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glPolygonOffset(-2.0f, -2.0f)
        if (hasStencil) {
            GLES20.glEnable(GLES20.GL_STENCIL_TEST)
            GLES20.glStencilFunc(GLES20.GL_EQUAL, 0, 0xFF)
            GLES20.glStencilOp(GLES20.GL_KEEP, GLES20.GL_KEEP, GLES20.GL_INCR)
        }

        // Building, Enterable, Roadside Shop & Port Container shadows
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
        for (shop in visRoadsideShops) {
            WorldAssetsDrawer.drawRoadsideShopShadow(dc, shop)
        }
        for (c in visPortContainers) {
            WorldAssetsDrawer.drawPortContainerShadow(dc, c)
        }
        dc.bindShadowVbo(assets.cubeMesh)
        for (obs in visStaticObstacles) {
            Matrix.setIdentityM(dc.modelMatrix, 0)
            Matrix.translateM(dc.modelMatrix, 0, obs.x, obs.yFloor + obs.height * 0.5f, obs.z)
            Matrix.scaleM(dc.modelMatrix, 0, obs.halfW * 2f, obs.height, obs.halfD * 2f)
            dc.commitShadow(assets.texWhite, assets.cubeMesh.vertexCount)
        }

        // Tree shadows (branches + alpha-cutout broad/palm/pine leaves!)
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

        // Parked & Moving Car, Bullet Bike, Army Bunker & Army Vehicle shadows
        for (pc in visParkedCars) {
            CityPropDrawer.drawCarShadow(dc, pc.x, pc.z, pc.yawDeg)
        }
        for (bike in visBulletBikes) {
            WorldAssetsDrawer.drawBulletBikeShadow(dc, bike)
        }
        for (bk in visArmyBunkers) {
            WorldAssetsDrawer.drawArmyBunkerShadow(dc, bk)
        }
        for (av in visArmyVehicles) {
            WorldAssetsDrawer.drawArmyVehicleShadow(dc, av)
        }
        for (car in visMovingCars) {
            val yawDeg = if (car.axisX) (if (car.dir > 0f) 90f else -90f) else (if (car.dir > 0f) 0f else 180f)
            CityPropDrawer.drawCarShadow(dc, car.x, car.z, yawDeg)
        }

        // NPC Men/Women & Player human shadows
        for (npc in visNpcs) {
            CityPropDrawer.drawHumanShadow(dc, npc.x, 0f, npc.z, npc.yawRad, npc.walkPhase, npc.pauseTimer <= 0f, npc.isWoman)
        }
        if (drivenCar == null) {
            val riderYOffset = if (riddenBike != null) 0.42f else 0f
            val rigged = glbPlayer
            if (rigged != null) {
                rigged.drawShadow(dc, playerX, playerY + riderYOffset, playerZ, playerFacingYaw, shadowAlpha)
            } else {
                CityPropDrawer.drawHumanShadow(dc, playerX, playerY + riderYOffset, playerZ, playerFacingYaw, walkCycle, isMovingNow, false)
            }
        }

        if (hasStencil) GLES20.glDisable(GLES20.GL_STENCIL_TEST)
        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(true)

        // Restore primary lit shader
        GLES20.glUseProgram(assets.shader.programId)
        dc.boundVboId = -1
    }

    /**
     * Prevents the third-person camera from clipping inside solid buildings, enterables, or shops
     * (which caused backface-culled walls and floating-ceiling glitches when backing near buildings).
     */
    private fun clampCameraDistanceAgainstWalls(
        px: Float, pz: Float,
        dirX: Float, dirZ: Float,
        maxDist: Float
    ): Float {
        var clamped = maxDist
        fun clipBox(cx: Float, cz: Float, hw: Float, hd: Float) {
            // Quick bounding reject
            if (abs(px - cx) > hw + maxDist + 1f || abs(pz - cz) > hd + maxDist + 1f) return
            val minX = cx - hw; val maxX = cx + hw
            val minZ = cz - hd; val maxZ = cz + hd
            var tMin = 0f
            var tMax = clamped
            if (abs(dirX) < 1e-5f) {
                if (px < minX || px > maxX) return
            } else {
                val invX = 1f / dirX
                var t1 = (minX - px) * invX
                var t2 = (maxX - px) * invX
                if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
                tMin = maxOf(tMin, t1)
                tMax = minOf(tMax, t2)
                if (tMin > tMax) return
            }
            if (abs(dirZ) < 1e-5f) {
                if (pz < minZ || pz > maxZ) return
            } else {
                val invZ = 1f / dirZ
                var t1 = (minZ - pz) * invZ
                var t2 = (maxZ - pz) * invZ
                if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
                tMin = maxOf(tMin, t1)
                tMax = minOf(tMax, t2)
                if (tMin > tMax) return
            }
            if (tMin in 0.05f..clamped) {
                clamped = (tMin - 0.35f).coerceAtLeast(1.35f)
            }
        }
        for (b in visBuildings) {
            val pad = if (b.hasCompoundWall) 0.55f else 0.45f
            clipBox(b.x, b.z, b.width * 0.5f + pad, b.depth * 0.5f + pad)
        }
        for (eb in visEnterables) {
            clipBox(eb.centerX, eb.centerZ, eb.halfW + 0.35f, eb.halfD + 0.35f)
        }
        for (shop in visRoadsideShops) {
            clipBox(shop.x, shop.z, shop.width * 0.5f + 0.35f, shop.depth * 0.5f + 0.35f)
        }
        return clamped
    }

    private fun drawRealisticSkyscraper(b: BuildingBlock) {
        MultiAnglePropDrawer.drawModernCityBuilding(dc, b, nightFactor)
    }

    private fun drawEnterableBuilding(eb: EnterableBuilding) {
        MultiAnglePropDrawer.drawModernEnterableBuilding(dc, eb, nightFactor)
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
