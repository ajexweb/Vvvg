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
import com.example.game.model.DensityLevel
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
    @Volatile var vehSteer: Float = 0f      // Dedicated Vehicle Buttons steering (-1 left, +1 right)
    @Volatile var vehThrottle: Float = 0f   // Dedicated Vehicle Buttons pedal (+1 gas, -1 brake/reverse)
    @Volatile var isSprinting: Boolean = false
    @Volatile var jumpQueued: Boolean = false
    @Volatile var interactQueued: Boolean = false
    @Volatile var hornQueued: Boolean = false
    @Volatile var handFightQueued: Boolean = false
    @Volatile var legFightQueued: Boolean = false
    @Volatile var flipQueued: Boolean = false
    @Volatile var releaseQueued: Boolean = false
    @Volatile var emoteQueued: Boolean = false
    @Volatile var emoteClipOrdinal: Int = 2 // Maps to PlayerAnimClip.entries
    @Volatile var toggleWeatherQueued: Boolean = false
    @Volatile var toggleDayNightQueued: Boolean = false
    @Volatile var cameraYawDelta: Float = 0f
    @Volatile var cameraPitchDelta: Float = 0f
    @Volatile var holdGateOpen: Boolean = false
    @Volatile var holdGateClose: Boolean = false

    fun resetAll() {
        moveX = 0f
        moveY = 0f
        vehSteer = 0f
        vehThrottle = 0f
        isSprinting = false
        jumpQueued = false
        interactQueued = false
        hornQueued = false
        handFightQueued = false
        legFightQueued = false
        flipQueued = false
        releaseQueued = false
        emoteQueued = false
        holdGateOpen = false
        holdGateClose = false
        cameraYawDelta = 0f
        cameraPitchDelta = 0f
    }
}

interface GameEngineListener {
    fun onWorldLoadProgress(progress: Float, stageLabel: String)
    fun onWorldReady()
    fun onTelemetryUpdate(telemetry: GameTelemetry)
    fun onFootstep(isSprint: Boolean, surface: GroundSurface)
    fun onOrbCollected(orbId: Int, newScore: Int)
    fun onZoneDiscovered(zoneId: String, zoneName: String, bonusPoints: Int)
    fun onInteractMessage(message: String)
    fun onJumpPerformed()
    fun onPlayerLand(heavy: Boolean)
    fun onPlayerWallVault()
    fun onCombatAction(isKick: Boolean)
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
    private var grassSystem: RealisticGrassSystem? = null
    private var terrainChunkManager: WorldTerrainChunkManager? = null

    // Settings synchronized live from ViewModel
    @Volatile var isPaused: Boolean = false
    @Volatile var activeSkin: CharacterSkin = GameSkins.all.first()
    @Volatile var graphicsQuality: GraphicsQuality = GraphicsQuality.MEDIUM
    @Volatile var shadowsEnabled: Boolean = false
    @Volatile var grassDensity: DensityLevel = DensityLevel.MEDIUM
    @Volatile var aiTrafficDensity: DensityLevel = DensityLevel.MEDIUM
    @Volatile var npcDensity: DensityLevel = DensityLevel.MEDIUM
    @Volatile var dynamicDayNight: Boolean = true
    @Volatile var invertCameraY: Boolean = false
    @Volatile var autoCenterVehicleCam: Boolean = true
    @Volatile var autoSprint: Boolean = false
    @Volatile var vehicleWrapRgb: FloatArray? = null
    @Volatile var cameraSensitivity: Float = 1.0f
    @Volatile var streamingRadiusMeters: Float = 52f
    @Volatile var waypointActive: Boolean = false
    @Volatile var waypointX: Float = 0f
    @Volatile var waypointZ: Float = 0f

    // Initial spawn & world condition config chosen in Lobby
    @Volatile var spawnX: Float = 0f
    @Volatile var spawnZ: Float = 8.2f
    @Volatile var initialNightMode: Boolean = false
    @Volatile var initialRainMode: Boolean = false

    // Player & Camera state
    private var playerX = 0f
    private var playerY = 0f
    private var playerZ = 8.2f
    private var playerVelY = 0f
    private var peakAirY = 0f
    private var isGrounded = true
    private var playerFacingYaw = PI.toFloat()

    // Vehicle state (drivable parked cars, drivable AI traffic cars & rideable Bullet 350 bikes!)
    private var drivenCar: ParkedCar? = null
    private var drivenAiCar: CarAgent? = null
    private var riddenBike: BulletBikeProp? = null
    private var vehicleSpeed = 0f
    private var vehicleVelY = 0f
    private var vehicleGrounded = true
    private var lastPlayerVehX: Float? = null
    private var lastPlayerVehZ: Float? = null
    private var isInWaterNow = false
    private var currentWaterWarning: String? = null
    private var playerSlopePitchDeg = 0f
    private var playerSlopeRollDeg = 0f
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

    // Multi-animation one-shot action state (FLIP / HAND_FIGHT / LEG_FIGHT / RELEASE)
    private var activeActionClip: PlayerAnimClip? = null
    private var activeActionTime: Float = 0f
    private var activeActionRate: Float = 1.2f
    private var activeActionEndTime: Float = 0f

    // True incremental per-frame asset loader (eliminates 5-10s lobby-to-loading hang!)
    private var loadStage: Int = 0
    private var coreTexLoadIdx: Int = 0
    private var coreTexLoadSteps: List<() -> Unit> = emptyList()
    private var playerClipLoadIdx: Int = 0
    private var warmupFramesRendered: Int = 0
    private var worldReadyReported: Boolean = false
    private var lastGateAudioTime = -10f
    private var lastNpcStreamCheckTime = 0f
    private var lastTrafficRelocateTime = 0f
    private var aiRngState: Int = 7391521

    private fun nextAiRand(): Float {
        aiRngState = aiRngState * 1103515245 + 12345
        return ((aiRngState ushr 16) and 0x7FFF) / 32767f
    }

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
    private var cachedCollectedOrbsSnapshot: Set<Int> = emptySet()
    private var cachedDiscoveredZonesSnapshot: Set<String> = emptySet()
    private val enterableBuildings = CityWorldData.createEnterableBuildings()
    private val roadsideShops = CityWorldData.createRoadsideShops()
    private val parkedCars = CityWorldData.createParkedCars()
    private val bulletBikes = CityWorldData.createBulletBikes()
    private val cars = CityWorldData.createCars()
    private val npcs = CityWorldData.createNpcs()
    private val skyClouds = CityWorldData.createSkyClouds()
    private val flyingBirds = CityWorldData.createFlyingBirds()
    private var chunkGrid = CityWorldData.buildChunkGrid(enterableBuildings, roadsideShops)

    // Pre-allocated per-frame visible chunk, O(1) dedup sets & shadow lists (zero GC churn)
    private val activeChunks = ArrayList<WorldChunk>(48)
    private val seenBuildings = HashSet<BuildingBlock>(48)
    private val seenEnterables = HashSet<EnterableBuilding>(12)
    private val seenRoadsideShops = HashSet<RoadsideShopProp>(12)
    private val seenPortContainers = HashSet<PortContainerProp>(24)
    private val seenArmyBunkers = HashSet<ArmyBunkerProp>(8)
    private val seenBarbedFences = HashSet<BarbedFenceSegment>(16)
    private val seenArmyVehicles = HashSet<ArmyVehicleProp>(8)
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

    private fun applyInitialSpawnAndConditions() {
        playerX = spawnX
        playerZ = spawnZ
        playerY = WorldMapData.getTerrainHeight(playerX, playerZ)
        isInWaterNow = false
        currentWaterWarning = null
        playerSlopePitchDeg = 0f
        playerSlopeRollDeg = 0f
        timeOfDay = if (initialNightMode) 0.96f else 0.36f
        nightFactor = if (initialNightMode) 1f else 0f
        weatherState = if (initialRainMode) WeatherState.RAIN else WeatherState.CLEAR
        rainIntensity = if (initialRainMode) 1f else 0f
        roadWetness = if (initialRainMode) 0.85f else 0f
    }

    private fun performReset() {
        applyInitialSpawnAndConditions()
        playerVelY = 0f
        isGrounded = true
        playerFacingYaw = PI.toFloat()
        drivenCar = null
        drivenAiCar = null
        riddenBike = null
        lastPlayerVehX = null
        lastPlayerVehZ = null
        vehicleSpeed = 0f
        vehicleVelY = 0f
        vehicleGrounded = true
        cameraYaw = 0f
        cameraPitch = 0.22f
        currentCamDist = 4.6f
        currentBuilding = null
        currentFloorNumber = 0
        stamina = 100f
        health = 100f
        sessionScore = 0
        totalDistance = 0f
        collectedOrbs.clear()
        discoveredZones.clear()
        cachedCollectedOrbsSnapshot = emptySet()
        cachedDiscoveredZonesSnapshot = emptySet()
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
        // Lightweight instant GPU init (<20ms): compile shaders & basic meshes only.
        // All heavy textures, 6 GLB animations, 18-asset grass & 3D terrain chunks load incrementally
        // across frames in onDrawFrame() so the loading screen appears IMMEDIATELY with ZERO hang!
        loadStage = 0
        coreTexLoadIdx = 0
        playerClipLoadIdx = 0
        warmupFramesRendered = 0
        worldReadyReported = false
        glbPlayer = null
        grassSystem = null
        terrainChunkManager = null
        applyInitialSpawnAndConditions()
        listener.onWorldLoadProgress(0.05f, "Initializing 3D Graphics Engine...")

        GLES20.glClearColor(0.04f, 0.06f, 0.10f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val whiteTex = GlTextureHelper.createSolidWhiteTexture()
        val multiAngleMgr = MultiAngleTextureManager(context, fallbackTex = whiteTex)

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
            texBuilding = whiteTex,
            texRoad = whiteTex,
            texGrass = whiteTex,
            texBark = whiteTex,
            texLeafCluster = whiteTex,
            texInteriorFloor = whiteTex,
            texConcrete = whiteTex,
            texWhite = whiteTex,
            playerHeadMesh = GlMeshFactory.createSculptedHeadMesh(),
            playerTorsoMesh = GlMeshFactory.createSculptedTorsoMesh(),
            texPlayerFace = whiteTex,
            texPlayerShirt = whiteTex,
            texPlayerJeans = whiteTex,
            verticalQuadMesh = GlMeshFactory.createVerticalQuad(),
            crossedGrassMesh = GlMeshFactory.createCrossedGrassMesh(),
            skyDomeMesh = GlMeshFactory.createSkyDomeMesh(),
            splitCylinderMesh = GlMeshFactory.createSplitCylinder(segments = 16),
            splitCylinderSegments = 16,
            multiAngle = multiAngleMgr
        )
        dc = GlDrawContext(assets)

        // Queue core world textures to decode incrementally (5 per frame -> zero stutter!)
        coreTexLoadSteps = listOf(
            { assets.texBuilding = GlTextureHelper.loadDrawableTexture(context, R.drawable.img_tex_building, repeat = true) },
            { assets.texRoad = GlTextureHelper.loadDrawableTexture(context, R.drawable.img_tex_road, repeat = true) },
            { assets.texGrass = GlTextureHelper.loadDrawableTexture(context, R.drawable.img_tex_grass, repeat = true) },
            { assets.texBark = GlTextureHelper.loadDrawableTexture(context, R.drawable.img_tex_bark, repeat = true) },
            { assets.texLeafCluster = GlTextureHelper.loadLeafBranchClusterTexture(context, R.drawable.img_tex_leaves) },
            { assets.texInteriorFloor = GlTextureHelper.loadDrawableTexture(context, R.drawable.img_tex_interior_floor, repeat = true) },
            { assets.texConcrete = GlTextureHelper.loadDrawableTexture(context, R.drawable.img_tex_concrete, repeat = true) },
            { assets.texPlayerFace = GlTextureHelper.loadPlayerFaceTexture(context, R.drawable.img_player_face) },
            { assets.texPlayerShirt = GlTextureHelper.createGreenShirtTexture() },
            { assets.texPlayerJeans = GlTextureHelper.createDenimJeansTexture() },
            { assets.texAirportRunway = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_airport_runway, repeat = false) },
            { assets.tex3dGrassBlades = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_3d_grass_blades, repeat = false) },
            { assets.texArmyBarbedFence = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_army_barbed_fence, repeat = true, repeatY = false) },
            { assets.texArmyCamo = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_army_camouflage, repeat = true) },
            { assets.texArmyHelipad = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_army_helipad, repeat = false) },
            { assets.texArmyBunkerConcrete = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_army_bunker_concrete, repeat = true) },
            { assets.texBuildingGlassFacade = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_building_glass_facade, repeat = true) },
            { assets.texBirdFeathers = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_bird_feathers, repeat = false) },
            { assets.texBulletBikeTank = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_bullet_bike_tank, repeat = false) },
            { assets.texEnvCloudPuff = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_env_cloud_puff, repeat = false) },
            { assets.texEnvSkyDay = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_env_sky_day, repeat = true, repeatY = false) },
            { assets.texEnvSkyNight = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_env_sky_night, repeat = true, repeatY = false) },
            { assets.texEnvSunGlow = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_env_sun_glow, repeat = false) },
            { assets.texEnvMoon = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_env_moon, repeat = false) },
            { assets.texEnvRainStreaks = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_env_rain_streaks, repeat = true) },
            { assets.texWaterOcean = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_water_ocean, repeat = true) },
            { assets.texWaterNormal = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_water_normal, repeat = true) },
            { assets.texRoadAsphaltDetailed = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_road_asphalt_detailed, repeat = true) },
            { assets.texIntersectionAsphalt = GlTextureHelper.createCleanIntersectionAsphaltTexture() },
            { assets.texColonyAlley = GlTextureHelper.createColonyAlleyTexture() },
            { assets.texFootpathPaverBlocks = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_footpath_paver_blocks, repeat = true) },
            { assets.texWaterRoadPuddleReflect = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_water_road_puddle_reflect, repeat = true) },
            { assets.texTreeBark = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_tree_bark, repeat = true) },
            { assets.texTreeLeavesBroad = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_tree_leaves_broad, repeat = false) },
            { assets.texTreeLeavesPalm = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_tree_leaves_palm, repeat = false) },
            { assets.texTreeLeavesPine = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_tree_leaves_pine, repeat = false) },
            { assets.texHouseRedBrick = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_house_red_brick, repeat = true) },
            { assets.texHouseExteriorWindows = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_house_exterior_windows, repeat = true) },
            { assets.texShopShutter = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_shop_shutter, repeat = false) },
            { assets.texInteriorWallPaint = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_interior_wall_paint, repeat = true) },
            { assets.texInteriorWoodFloor = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_interior_wood_floor, repeat = true) },
            { assets.texInteriorMarbleTiles = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_interior_marble_tiles, repeat = true) },
            { assets.texFactoryCorrugatedMetal = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_factory_corrugated_metal, repeat = true) },
            { assets.texPortContainerSteel = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_port_container_steel, repeat = true) },
            { assets.texVehicleTireTread = GlTextureHelper.loadDrawableTexture(context, R.drawable.tex_vehicle_tire_tread, repeat = true) }
        )

        lastFrameNs = System.nanoTime()
        fpsWindowStartNs = lastFrameNs
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = if (height > 0) width.toFloat() / height.toFloat() else 1.77f
        // Near plane 0.55f + far 1050f showcases distant snow peaks & island horizons with zero Z-fighting
        Matrix.perspectiveM(dc.projMatrix, 0, 56f, aspect, 0.55f, 1050f)
    }

    private fun stepIncrementalWorldLoading() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        when (loadStage) {
            // Stage 0: Core world textures (5 per frame) -> 0.05..0.24
            0 -> {
                val end = (coreTexLoadIdx + 5).coerceAtMost(coreTexLoadSteps.size)
                while (coreTexLoadIdx < end) {
                    coreTexLoadSteps[coreTexLoadIdx].invoke()
                    coreTexLoadIdx++
                }
                val frac = coreTexLoadIdx.toFloat() / coreTexLoadSteps.size.coerceAtLeast(1)
                listener.onWorldLoadProgress(0.05f + frac * 0.19f, "Loading World Textures ($coreTexLoadIdx/${coreTexLoadSteps.size})...")
                if (coreTexLoadIdx >= coreTexLoadSteps.size) loadStage = 1
            }
            // Stage 1: Multi-Angle 80-texture pack (6 per frame) -> 0.24..0.50
            1 -> {
                val mgr = assets.multiAngle
                if (mgr != null) {
                    val (done, total) = mgr.stepLoadBatch(batchSize = 6)
                    val frac = done.toFloat() / total.coerceAtLeast(1)
                    listener.onWorldLoadProgress(0.24f + frac * 0.26f, "Loading City Buildings & Props ($done/$total)...")
                    if (done >= total) loadStage = 2
                } else {
                    loadStage = 2
                }
            }
            // Stage 2: Player GLB base mesh + 5 .sanim motion clips (1 per frame!) -> 0.50..0.60
            2 -> {
                if (glbPlayer == null && playerClipLoadIdx == 0) {
                    glbPlayer = GlbSkinnedPlayer.loadBaseFromAssets(context, "playerstand.glb", assets.texWhite)
                    listener.onWorldLoadProgress(0.52f, "Loading Player Character Rig...")
                    if (glbPlayer == null) {
                        loadStage = 3
                    } else {
                        playerClipLoadIdx = 1
                    }
                } else {
                    val clipIdx = playerClipLoadIdx - 1
                    glbPlayer?.loadExtraClipStep(context, clipIdx)
                    val totalClips = GlbSkinnedPlayer.extraClipCount
                    val frac = playerClipLoadIdx.toFloat() / totalClips.coerceAtLeast(1)
                    listener.onWorldLoadProgress(0.52f + frac * 0.08f, "Loading Player Animations ($playerClipLoadIdx/$totalClips)...")
                    playerClipLoadIdx++
                    if (clipIdx + 1 >= totalClips) loadStage = 3
                }
            }
            // Stage 3: Init 18-Asset Grass Engine (1 frame) -> 0.63
            3 -> {
                grassSystem = RealisticGrassSystem(context)
                listener.onWorldLoadProgress(0.63f, "Packing 18-Type 3D Foliage Atlas...")
                loadStage = 4
            }
            // Stage 4: Init 50km x 65km Multi-Island Terrain Engine (1 frame) -> 0.66
            4 -> {
                terrainChunkManager = WorldTerrainChunkManager()
                listener.onWorldLoadProgress(0.66f, "Initializing 50 km × 65 km Multi-Island World...")
                loadStage = 5
            }
            // Stage 5: Bake GTA-6 Satellite Map Bitmap off UI thread (1 frame) -> 0.70
            5 -> {
                WorldMapData.getOrCreateSatelliteBitmap()
                listener.onWorldLoadProgress(0.70f, "Building Satellite Map Radar...")
                loadStage = 6
            }
            // Stage 6: Pre-bake nearby 3D terrain chunks (2 per frame) -> 0.70..0.82
            6 -> {
                val (done, total) = terrainChunkManager?.prewarmStep(
                    centerX = playerX,
                    centerZ = playerZ,
                    graphicsQuality = graphicsQuality,
                    maxChunksPerStep = 2
                ) ?: (1 to 1)
                val frac = (done.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
                listener.onWorldLoadProgress(0.70f + frac * 0.12f, "Streaming 3D Terrain & Highways ($done/$total)...")
                if (done >= total) loadStage = 7
            }
            // Stage 7: Pre-bake nearby 18-asset grass chunks (3 per frame; skipped when grass OFF!) -> 0.82..0.94
            7 -> {
                val (done, total) = grassSystem?.prewarmStep(
                    centerX = playerX,
                    centerZ = playerZ,
                    graphicsQuality = graphicsQuality,
                    grassDensity = grassDensity,
                    maxChunksPerStep = 3
                ) ?: (1 to 1)
                val frac = (done.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
                listener.onWorldLoadProgress(0.82f + frac * 0.12f, "Growing 3D Biome Foliage ($done/$total)...")
                if (done >= total) loadStage = 8
            }
            // Stage 8: Warm up GPU shader pipelines with 3 real frames -> 0.94..1.00
            else -> {
                renderScene()
                warmupFramesRendered++
                val warmFrac = (warmupFramesRendered / 3f).coerceIn(0f, 1f)
                if (warmupFramesRendered < 3) {
                    listener.onWorldLoadProgress(0.94f + warmFrac * 0.05f, "Warming Up 60 FPS GPU Shaders...")
                } else {
                    worldReadyReported = true
                    lastFrameNs = System.nanoTime()
                    fpsWindowStartNs = lastFrameNs
                    fpsFrameCount = 0
                    listener.onWorldLoadProgress(1.0f, "Deploying to Saini City...")
                    listener.onWorldReady()
                }
            }
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNs = System.nanoTime()
        val dt = ((nowNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNs = nowNs

        if (!worldReadyReported) {
            stepIncrementalWorldLoading()
            return
        }

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
        } else if (dynamicDayNight) {
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
        val pitchSign = if (invertCameraY) -1f else 1f
        cameraPitch = (cameraPitch + pitchDelta * pitchSign * 0.0038f * cameraSensitivity).coerceIn(minPitch, maxPitch)

        val interactNow = input.interactQueued
        input.interactQueued = false
        val hornNow = input.hornQueued
        input.hornQueued = false

        var nearestInteract: String? = null
        var interactConsumed = false

        // --- 1. Hand-Controlled Interactive Gate System (All 4 Mechanisms + Stop/Leave-Anywhere) ---
        val inVehicle = drivenCar != null || drivenAiCar != null || riddenBike != null
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
        val activeAiCar = drivenAiCar
        val activeBike = riddenBike
        val isDrivingCar = activeCar != null || activeAiCar != null

        if (isDrivingCar || activeBike != null) {
            nearestInteract = when {
                activeAiCar != null -> "Exit Traffic Car"
                activeCar != null -> "Exit Sports Car"
                else -> "Dismount Bullet 350"
            }
            if (hornNow && isDrivingCar) {
                listener.onCarHorn()
                // Honking scares nearby NPCs so they sprint out of the road!
                for (npc in npcs) {
                    val ndx = npc.x - playerX
                    val ndz = npc.z - playerZ
                    if (ndx * ndx + ndz * ndz < 14f * 14f) {
                        npc.fleeTimer = 3.8f
                        npc.fleeFromX = playerX
                        npc.fleeFromZ = playerZ
                        npc.pauseTimer = 0f
                    }
                }
            }
            if (interactNow && !interactConsumed) {
                interactConsumed = true
                val vehYawDeg = when {
                    activeCar != null -> activeCar.yawDeg
                    activeAiCar != null -> activeAiCar.yawDeg
                    else -> activeBike!!.yawDeg
                }
                val yawRad = vehYawDeg * PI.toFloat() / 180f
                val sideDist = if (isDrivingCar) 2.2f else 1.45f
                val exitResolved = CityWorldData.resolvePlayerPhysics(
                    nextX = playerX + cos(yawRad) * sideDist,
                    nextZ = playerZ - sin(yawRad) * sideDist,
                    currentY = playerY,
                    enterableBuildings = enterableBuildings,
                    movingCars = cars,
                    npcs = npcs,
                    activeParkedCars = parkedCars,
                    activeBulletBikes = bulletBikes,
                    activeShops = roadsideShops,
                    ignoreCar = activeCar,
                    ignoreAiCar = activeAiCar,
                    ignoreBike = activeBike,
                    chunkGrid = chunkGrid,
                    prevX = playerX,
                    prevZ = playerZ,
                    chopperLiftY = chopperLiftY
                )
                playerX = exitResolved.x
                playerZ = exitResolved.z
                playerY = exitResolved.supportFloorY
                playerVelY = 0f
                isGrounded = true
                // Permanent Player Vehicle Persistence: NEVER despawn or vanish the player's left-behind vehicle!
                when {
                    activeCar != null -> {
                        activeCar.isPlayerOwned = true
                        lastPlayerVehX = activeCar.x
                        lastPlayerVehZ = activeCar.z
                    }
                    activeAiCar != null -> {
                        // Convert hijacked AI traffic car into a permanent player-owned ParkedCar so it NEVER vanishes!
                        val savedCar = ParkedCar(
                            x = activeAiCar.x,
                            z = activeAiCar.z,
                            yawDeg = activeAiCar.yawDeg,
                            r = activeAiCar.r,
                            g = activeAiCar.g,
                            b = activeAiCar.b,
                            wheelAngleDeg = activeAiCar.wheelAngleDeg,
                            pitchDeg = activeAiCar.pitchDeg,
                            rollDeg = activeAiCar.rollDeg,
                            isPlayerOwned = true
                        )
                        parkedCars.add(savedCar)
                        lastPlayerVehX = savedCar.x
                        lastPlayerVehZ = savedCar.z
                        // Recycle the AI slot far away so traffic continues flowing naturally
                        activeAiCar.isPlayerControlled = false
                        activeAiCar.isTurning = false
                        activeAiCar.parkedAfterExitTimer = 0f
                        activeAiCar.currentSpeed = activeAiCar.baseSpeed * 0.7f
                        activeAiCar.x = activeAiCar.x + 260f
                        activeAiCar.z = activeAiCar.z + 260f
                    }
                    activeBike != null -> {
                        activeBike.isPlayerOwned = true
                        activeBike.steerDeg = 0f
                        lastPlayerVehX = activeBike.x
                        lastPlayerVehZ = activeBike.z
                    }
                }
                drivenCar = null
                drivenAiCar = null
                riddenBike = null
                vehicleSpeed = 0f
                vehicleVelY = 0f
                vehicleGrounded = true
                if (isDrivingCar) {
                    listener.onCarExited()
                    listener.onInteractMessage("Car Parked — Saved Permanently on Map")
                } else {
                    listener.onInteractMessage("Bullet 350 Parked — Saved Permanently on Map")
                }
            } else {
                val isCar = isDrivingCar
                val onBridgeNow = WorldMapData.isOnBridge(playerX, playerZ)
                val waterDepthStart = if (onBridgeNow) 0f else WorldMapData.getWaterDepth(playerX, playerZ)
                val waterSpeedMult = when {
                    waterDepthStart > 0.78f -> 0.34f
                    waterDepthStart > 0.22f -> 0.66f
                    else -> 1.0f
                }
                val maxFwd = (if (isCar) 24f else 21f) * (if (input.isSprinting) 1.45f else 1f) * waterSpeedMult
                val maxRev = (if (isCar) -7.5f else -4.5f) * waterSpeedMult
                val accel = (if (isCar) 15.5f else 18.5f) * waterSpeedMult
                // Blend dedicated Vehicle Steer/Pedals buttons cleanly with joystick/keyboard!
                val steerInput = (if (abs(input.vehSteer) > 0.05f) input.vehSteer else rawX).coerceIn(-1f, 1f)
                val throttle = (if (abs(input.vehThrottle) > 0.05f) input.vehThrottle else -rawY).coerceIn(-1f, 1f)
                val prevSpeed = vehicleSpeed

                // Detect hard acceleration / engine revving in car
                if (isCar && vehicleGrounded && ((throttle > 0.72f && prevSpeed in 0.2f..5.5f) || (input.isSprinting && throttle > 0.55f)) && worldTime - lastCarRevTime > 2.4f) {
                    lastCarRevTime = worldTime
                    listener.onCarAccelerateRev()
                }

                // Detect emergency braking / tire skid at speed
                val isEmergencyBraking = isCar && vehicleGrounded && ((throttle < -0.42f && prevSpeed > 8.5f) || (abs(steerInput) > 0.78f && prevSpeed > 13.5f))
                if (isEmergencyBraking) {
                    lastHardBrakeTime = worldTime
                    if (worldTime - lastCarSkidTime > 1.8f) {
                        lastCarSkidTime = worldTime
                        listener.onCarEmergencyBrakeSkid()
                    }
                }

                if (vehicleGrounded) {
                    if (abs(throttle) > 0.08f) {
                        vehicleSpeed = (vehicleSpeed + throttle * accel * dt).coerceIn(maxRev, maxFwd)
                    } else {
                        val dragRate = if (waterDepthStart > 0.22f) 6.5f else 3.8f
                        val drag = (1f - dt * dragRate).coerceAtLeast(0f)
                        vehicleSpeed *= drag
                        if (abs(vehicleSpeed) < 0.08f) vehicleSpeed = 0f
                    }
                } else {
                    // Slight air drag while airborne off ramps/bridges
                    vehicleSpeed *= (1f - dt * 0.25f).coerceAtLeast(0f)
                }

                val steerFactor = (abs(vehicleSpeed) / 4.0f).coerceIn(0.25f, 1.0f) * (if (vehicleSpeed >= -0.1f) 1f else -1f) * (if (vehicleGrounded) 1f else 0.20f)
                val turnRateDeg = if (isCar) 115f else 142f
                val curYawDeg = when {
                    activeCar != null -> activeCar.yawDeg
                    activeAiCar != null -> activeAiCar.yawDeg
                    else -> activeBike!!.yawDeg
                }
                val nextYawDeg = (curYawDeg - steerInput * turnRateDeg * steerFactor * dt) % 360f
                val rad = nextYawDeg * PI.toFloat() / 180f
                val curX = when {
                    activeCar != null -> activeCar.x
                    activeAiCar != null -> activeAiCar.x
                    else -> activeBike!!.x
                }
                val curZ = when {
                    activeCar != null -> activeCar.z
                    activeAiCar != null -> activeAiCar.z
                    else -> activeBike!!.z
                }
                val nextX = curX + sin(rad) * vehicleSpeed * dt
                val nextZ = curZ + cos(rad) * vehicleSpeed * dt
                val vehRadius = if (isCar) 1.35f else 0.72f

                val resolved = CityWorldData.resolvePlayerPhysics(
                    nextX = nextX,
                    nextZ = nextZ,
                    currentY = playerY,
                    enterableBuildings = enterableBuildings,
                    movingCars = cars,
                    npcs = npcs,
                    playerRadius = vehRadius,
                    activeParkedCars = parkedCars,
                    activeBulletBikes = bulletBikes,
                    activeShops = roadsideShops,
                    ignoreCar = activeCar,
                    ignoreAiCar = activeAiCar,
                    ignoreBike = activeBike,
                    chunkGrid = chunkGrid,
                    prevX = curX,
                    prevZ = curZ,
                    isAirborne = !vehicleGrounded,
                    chopperLiftY = chopperLiftY
                )
                // Also prevent vehicles from passing through regional forest trees!
                val treeAdj = terrainChunkManager?.resolveRegionalTreeCollisions(resolved.x, resolved.z, playerY, vehRadius)
                val finalVehX = treeAdj?.first ?: resolved.x
                val finalVehZ = treeAdj?.second ?: resolved.z

                val movedDx = finalVehX - curX
                val movedDz = finalVehZ - curZ
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

                // 3D Multi-Wheel Slope Pitch, Roll & Elevation Sampling (eliminates nose-clipping on ramps/hills/bridges!)
                val halfWb = if (isCar) 1.38f else 0.72f
                val halfTr = if (isCar) 0.88f else 0.32f
                val orient = sampleVehicleOrientation(finalVehX, finalVehZ, nextYawDeg, halfWb, halfTr)
                val supportY = maxOf(resolved.supportFloorY, orient.supportY)

                if (!vehicleGrounded) {
                    playerY += vehicleVelY * dt
                    if (playerY > peakAirY) peakAirY = playerY
                    vehicleVelY -= 22f * dt
                    if (playerY <= supportY) {
                        val fallHeight = (peakAirY - supportY).coerceAtLeast(0f)
                        playerY = supportY
                        vehicleVelY = 0f
                        vehicleGrounded = true
                        if (fallHeight >= 2.2f) {
                            if (isCar) listener.onCarCrash(if (fallHeight >= 4.5f) CarCrashType.HEAVY_IMPACT else CarCrashType.LIGHT_BUMP)
                            else listener.onPlayerLand(heavy = true)
                        }
                        peakAirY = supportY
                    }
                } else {
                    val diffY = supportY - playerY
                    if (diffY < -0.65f) {
                        // Drove off a bridge edge, cliff, or elevated ledge -> launch into real 3D free-fall!
                        vehicleGrounded = false
                        vehicleVelY = 0f
                        peakAirY = playerY
                    } else {
                        playerY += diffY * (dt * 22f).coerceAtMost(1f)
                    }
                }

                // Allow entering water freely — show clear HUD water warning instead of teleporting back!
                val onBridgeAfter = WorldMapData.isOnBridge(finalVehX, finalVehZ)
                val waterDepthHere = if (onBridgeAfter) 0f else WorldMapData.getWaterDepth(finalVehX, finalVehZ)
                isInWaterNow = waterDepthHere > 0.16f
                currentWaterWarning = when {
                    waterDepthHere > 0.75f -> "WARNING: DEEP WATER — ENGINE FLOODING"
                    waterDepthHere > 0.16f -> "CAUTION: DRIVING IN WATER"
                    else -> null
                }

                val tiltRate = (dt * 10.5f).coerceAtMost(1f)
                val targetPitch = if (vehicleGrounded) orient.pitchDeg else (vehicleVelY * -1.4f).coerceIn(-24f, 24f)
                val targetRoll = if (vehicleGrounded) orient.rollDeg else 0f

                when {
                    activeCar != null -> {
                        activeCar.x = finalVehX
                        activeCar.z = finalVehZ
                        activeCar.yawDeg = nextYawDeg
                        activeCar.wheelAngleDeg = (activeCar.wheelAngleDeg + vehicleSpeed * dt * 165f) % 360f
                        activeCar.pitchDeg += (targetPitch - activeCar.pitchDeg) * tiltRate
                        activeCar.rollDeg += (targetRoll - activeCar.rollDeg) * tiltRate
                    }
                    activeAiCar != null -> {
                        activeAiCar.x = finalVehX
                        activeAiCar.z = finalVehZ
                        activeAiCar.yawDeg = nextYawDeg
                        activeAiCar.targetYawDeg = nextYawDeg
                        activeAiCar.currentSpeed = abs(vehicleSpeed)
                        activeAiCar.wheelAngleDeg = (activeAiCar.wheelAngleDeg + vehicleSpeed * dt * 165f) % 360f
                        activeAiCar.pitchDeg += (targetPitch - activeAiCar.pitchDeg) * tiltRate
                        activeAiCar.rollDeg += (targetRoll - activeAiCar.rollDeg) * tiltRate
                    }
                    else -> {
                        activeBike!!.x = finalVehX
                        activeBike.z = finalVehZ
                        activeBike.yawDeg = nextYawDeg
                        activeBike.wheelAngleDeg = (activeBike.wheelAngleDeg + vehicleSpeed * dt * 185f) % 360f
                        activeBike.pitchDeg += (targetPitch - activeBike.pitchDeg) * tiltRate
                        activeBike.rollDeg += (targetRoll - activeBike.rollDeg) * tiltRate
                        val desiredSteer = (-steerInput * 24f).coerceIn(-26f, 26f)
                        activeBike.steerDeg += (desiredSteer - activeBike.steerDeg) * (dt * 12f).coerceAtMost(1f)
                    }
                }
                playerX = finalVehX
                playerZ = finalVehZ
                playerFacingYaw = rad
                currentBuilding = null
                currentFloorNumber = 0
                isMovingNow = abs(vehicleSpeed) > 0.25f
                input.jumpQueued = false
                // GTA 5 Smart Vehicle Camera:
                // 100% free 360-deg look whenever player drags; after 1.65s idle while driving, smoothly
                // aligns BEHIND vehicle when driving forward (rad + PI) and IN FRONT looking back when reversing (rad)!
                val timeSinceDrag = worldTime - lastManualCameraDragTime
                if (autoCenterVehicleCam && timeSinceDrag > 1.65f && abs(vehicleSpeed) > 0.85f) {
                    val desiredCamYaw = if (vehicleSpeed >= 0f) (rad + PI.toFloat()) else rad
                    val alignRate = if (vehicleSpeed >= 0f) 3.2f else 2.5f
                    cameraYaw = lerpAngle(cameraYaw, desiredCamYaw, (dt * alignRate).coerceAtMost(1f))
                    cameraPitch += (0.24f - cameraPitch) * (dt * 1.4f).coerceAtMost(1f)
                }
            }
        } else {
            // On-Foot Locomotion (with realistic water wading/swimming drag & Auto-Sprint support!)
            val preWaterDepth = if (WorldMapData.isOnBridge(playerX, playerZ)) 0f else WorldMapData.getWaterDepth(playerX, playerZ)
            val waterMoveMult = when {
                preWaterDepth > 1.05f -> 0.56f
                preWaterDepth > 0.20f -> 0.76f
                else -> 1.0f
            }
            val autoSprintTriggered = autoSprint && mag > 0.84f
            wantsSprint = (input.isSprinting || autoSprintTriggered) && stamina > 5f && mag > 0.15f && preWaterDepth < 0.85f
            stamina = if (wantsSprint) (stamina - 20f * dt).coerceAtLeast(0f) else (stamina + 18f * dt).coerceAtMost(100f)

            val moveSpeed = (if (wantsSprint) 11.2f else 5.8f) * activeSkin.speedMultiplier * waterMoveMult
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

            // Full Solid 3D Physics Collision Resolution (with climbable crates/boxes/cars & real bridge falls!)
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
                chunkGrid = chunkGrid,
                prevX = playerX,
                prevZ = playerZ,
                isAirborne = !isGrounded,
                chopperLiftY = chopperLiftY
            )
            val treeAdj = terrainChunkManager?.resolveRegionalTreeCollisions(resolved.x, resolved.z, playerY, 0.52f)
            val finalX = treeAdj?.first ?: resolved.x
            val finalZ = treeAdj?.second ?: resolved.z

            val movedDx = finalX - playerX
            val movedDz = finalZ - playerZ
            totalDistance += sqrt(movedDx * movedDx + movedDz * movedDz)
            playerX = finalX
            playerZ = finalZ
            currentBuilding = resolved.insideBuilding
            currentFloorNumber = resolved.floorNumber

            // Detect if player is pushing against a wall/box/car to trigger Wall Vault on jump!
            val blockedDist = sqrt((targetX - finalX) * (targetX - finalX) + (targetZ - finalZ) * (targetZ - finalZ))
            val isPressedAgainstObstacle = isMovingNow && blockedDist > 0.025f

            // Vertical jump, wall vault, combat/parkour actions & soft/heavy landing detection
            if (input.jumpQueued) {
                input.jumpQueued = false
                if (isGrounded) {
                    activeActionClip = null
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

            // Trigger real GLB combat & parkour animations (Hand Fight, Leg Fight, Flip, Release)
            if (isGrounded) {
                if (input.handFightQueued) {
                    input.handFightQueued = false
                    activeActionClip = PlayerAnimClip.HAND_FIGHT
                    activeActionTime = 0f
                    activeActionRate = 1.28f
                    activeActionEndTime = glbPlayer?.getClipDuration(PlayerAnimClip.HAND_FIGHT) ?: 2.17f
                    listener.onCombatAction(isKick = false)
                } else if (input.legFightQueued) {
                    input.legFightQueued = false
                    activeActionClip = PlayerAnimClip.LEG_FIGHT
                    activeActionTime = 0f
                    activeActionRate = 1.22f
                    activeActionEndTime = glbPlayer?.getClipDuration(PlayerAnimClip.LEG_FIGHT) ?: 1.60f
                    listener.onCombatAction(isKick = true)
                } else if (input.flipQueued) {
                    input.flipQueued = false
                    activeActionClip = PlayerAnimClip.FLIP
                    activeActionTime = 0f
                    activeActionRate = 1.24f
                    activeActionEndTime = glbPlayer?.getClipDuration(PlayerAnimClip.FLIP) ?: 2.17f
                    listener.onJumpPerformed()
                } else if (input.releaseQueued) {
                    input.releaseQueued = false
                    activeActionClip = PlayerAnimClip.RELEASE
                    activeActionTime = 0.04f
                    activeActionRate = 1.15f
                    activeActionEndTime = (glbPlayer?.getClipDuration(PlayerAnimClip.RELEASE) ?: 2.0f).coerceAtMost(1.55f)
                    listener.onPlayerLand(heavy = true)
                } else if (input.emoteQueued) {
                    input.emoteQueued = false
                    val clips = PlayerAnimClip.entries
                    val chosen = clips[input.emoteClipOrdinal.coerceIn(0, clips.lastIndex)]
                    activeActionClip = chosen
                    activeActionTime = 0f
                    activeActionRate = 1.12f
                    activeActionEndTime = (glbPlayer?.getClipDuration(chosen) ?: 2.0f).coerceAtMost(2.4f)
                    listener.onCombatAction(isKick = chosen == PlayerAnimClip.LEG_FIGHT)
                }
            } else {
                input.handFightQueued = false
                input.legFightQueued = false
                input.flipQueued = false
                input.releaseQueued = false
                input.emoteQueued = false
            }

            // Advance active one-shot clip and cleanly release back to STAND/RUN when complete (or if player sprints away)
            val curAct = activeActionClip
            if (curAct != null) {
                activeActionTime += dt * activeActionRate
                val allowMoveCancel = (curAct == PlayerAnimClip.HAND_FIGHT || curAct == PlayerAnimClip.LEG_FIGHT || curAct == PlayerAnimClip.RELEASE) &&
                    isMovingNow && mag > 0.45f && activeActionTime > 0.42f
                if (activeActionTime >= activeActionEndTime || allowMoveCancel) {
                    activeActionClip = null
                }
            }

            // Center-of-Mass Multi-Point Foot Grounding & Natural Hill/Ramp Slope Tilt (ZERO foot sinking into hills/dirt!)
            val terrainCenter = WorldMapData.getTerrainHeight(playerX, playerZ)
            val onElevatedPropOrIndoor = currentBuilding != null || resolved.supportFloorY > terrainCenter + 0.16f
            val onBridgeFoot = WorldMapData.isOnBridge(playerX, playerZ)
            val waterDepthHere = if (onBridgeFoot || onElevatedPropOrIndoor) 0f else WorldMapData.getWaterDepth(playerX, playerZ)
            val isSwimmingDeep = waterDepthHere > 1.05f && !onBridgeFoot && !onElevatedPropOrIndoor

            val supportY: Float
            if (isSwimmingDeep) {
                // Free ocean/river swimming buoyancy: keep player floating smoothly at waterline!
                supportY = WorldMapData.WATER_LEVEL - 0.86f + sin(worldTime * 2.4f) * 0.045f
                playerSlopePitchDeg += (0f - playerSlopePitchDeg) * (dt * 8f).coerceAtMost(1f)
                playerSlopeRollDeg += (0f - playerSlopeRollDeg) * (dt * 8f).coerceAtMost(1f)
            } else if (onElevatedPropOrIndoor) {
                supportY = resolved.supportFloorY
                playerSlopePitchDeg += (0f - playerSlopePitchDeg) * (dt * 10f).coerceAtMost(1f)
                playerSlopeRollDeg += (0f - playerSlopeRollDeg) * (dt * 10f).coerceAtMost(1f)
            } else {
                val footFwdX = sin(playerFacingYaw)
                val footFwdZ = cos(playerFacingYaw)
                val footPosX = cos(playerFacingYaw)
                val footPosZ = -sin(playerFacingYaw)
                val hF = WorldMapData.getTerrainHeight(playerX + footFwdX * 0.28f, playerZ + footFwdZ * 0.28f)
                val hB = WorldMapData.getTerrainHeight(playerX - footFwdX * 0.28f, playerZ - footFwdZ * 0.28f)
                val hL = WorldMapData.getTerrainHeight(playerX + footPosX * 0.22f, playerZ + footPosZ * 0.22f)
                val hR = WorldMapData.getTerrainHeight(playerX - footPosX * 0.22f, playerZ - footPosZ * 0.22f)
                val comAvg = terrainCenter * 0.45f + (hF + hB + hL + hR) * 0.1375f
                supportY = maxOf(resolved.supportFloorY, maxOf(terrainCenter, comAvg))
                val targetSlopeP = if (isGrounded) {
                    (-atan2(hF - hB, 0.56f) * 180f / PI.toFloat() * 0.46f).coerceIn(-15f, 15f)
                } else 0f
                val targetSlopeR = if (isGrounded) {
                    (atan2(hL - hR, 0.44f) * 180f / PI.toFloat() * 0.46f).coerceIn(-11f, 11f)
                } else 0f
                playerSlopePitchDeg += (targetSlopeP - playerSlopePitchDeg) * (dt * 10f).coerceAtMost(1f)
                playerSlopeRollDeg += (targetSlopeR - playerSlopeRollDeg) * (dt * 10f).coerceAtMost(1f)
            }

            if (!isGrounded) {
                playerY += playerVelY * dt
                if (playerY > peakAirY) peakAirY = playerY
                playerVelY -= 24f * dt
                if (playerY <= supportY) {
                    val fallHeight = (peakAirY - supportY).coerceAtLeast(0f)
                    playerY = supportY
                    playerVelY = 0f
                    isGrounded = true
                    val isHeavy = fallHeight >= 2.2f && !isSwimmingDeep
                    listener.onPlayerLand(heavy = isHeavy)
                    if (isHeavy) {
                        // Dramatic superhero/tactical crouch-land absorption using playerrelease.glb!
                        activeActionClip = PlayerAnimClip.RELEASE
                        activeActionTime = 0.36f
                        activeActionRate = 1.25f
                        activeActionEndTime = 1.38f
                    }
                    peakAirY = supportY
                }
            } else {
                val diffY = supportY - playerY
                if (diffY < -0.55f && !isSwimmingDeep) {
                    // Stepped off a box, crate, car roof, container, ledge, cliff, or bridge -> genuine 3D free-fall!
                    isGrounded = false
                    playerVelY = 0f
                    peakAirY = playerY
                    activeActionClip = null
                } else {
                    playerY += diffY * (dt * 20f).coerceAtMost(1f)
                }
            }

            // Free Water Entry with Clear HUD Warning (NO invisible shoreline walls, NO forced teleport-back!)
            isInWaterNow = waterDepthHere > 0.16f
            currentWaterWarning = when {
                waterDepthHere > 1.05f -> "WARNING: DEEP OCEAN WATER"
                waterDepthHere > 0.16f -> "CAUTION: ENTERING WATER"
                else -> null
            }
        }

        // --- 3. Intelligent Free-Roaming Reactive NPCs (panic/flee from fast cars & honks, ZERO wall clipping!) ---
        val streamCheckNow = (worldTime - lastNpcStreamCheckTime) > 2.0f
        if (streamCheckNow) lastNpcStreamCheckTime = worldTime
        val anyVehicleActive = drivenCar != null || drivenAiCar != null || riddenBike != null

        val maxActiveNpcs = when (npcDensity) {
            DensityLevel.OFF -> 0
            DensityLevel.LOW -> (npcs.size / 3).coerceAtLeast(4)
            DensityLevel.MEDIUM -> (npcs.size * 2 / 3).coerceAtLeast(8)
            DensityLevel.HIGH -> npcs.size
        }
        for (npcIdx in 0 until maxActiveNpcs.coerceAtMost(npcs.size)) {
            val npc = npcs[npcIdx]
            // Relocate very distant NPCs (>260m away) into valid walkable spots near the player so all towns feel alive!
            val distToPlayerSq = (npc.x - playerX) * (npc.x - playerX) + (npc.z - playerZ) * (npc.z - playerZ)
            if (streamCheckNow && distToPlayerSq > 260f * 260f) {
                for (attempt in 0 until 6) {
                    val ang = nextAiRand() * 2f * PI.toFloat()
                    val rad = 58f + nextAiRand() * 58f
                    val rx = (playerX + cos(ang) * rad).coerceIn(-WorldMapData.PLAYABLE_BOUND_X + 60f, WorldMapData.PLAYABLE_BOUND_X - 60f)
                    val rz = (playerZ + sin(ang) * rad).coerceIn(-WorldMapData.PLAYABLE_BOUND_Z + 60f, WorldMapData.PLAYABLE_BOUND_Z - 60f)
                    if (!CityWorldData.isNpcBlocked(rx, rz, 0.55f, chunkGrid, enterableBuildings, roadsideShops, parkedCars, cars, bulletBikes)) {
                        npc.x = rx
                        npc.z = rz
                        npc.headingRad = nextAiRand() * 2f * PI.toFloat()
                        npc.yawRad = npc.headingRad
                        npc.fleeTimer = 0f
                        npc.pauseTimer = 0f
                        break
                    }
                }
            }
            if (distToPlayerSq > 175f * 175f) continue

            // Check vehicle threats: fast player vehicle or close AI car -> trigger panic & sprint away!
            if (anyVehicleActive && abs(vehicleSpeed) > 3.0f && distToPlayerSq < 7.8f * 7.8f) {
                npc.fleeTimer = 4.2f
                npc.fleeFromX = playerX
                npc.fleeFromZ = playerZ
                npc.pauseTimer = 0f
            } else {
                for (car in cars) {
                    if (car.currentSpeed < 2.2f) continue
                    val cdx = npc.x - car.x
                    val cdz = npc.z - car.z
                    if (cdx * cdx + cdz * cdz < 5.2f * 5.2f) {
                        npc.fleeTimer = 3.2f
                        npc.fleeFromX = car.x
                        npc.fleeFromZ = car.z
                        npc.pauseTimer = 0f
                        break
                    }
                }
            }

            // Friendly greeting when player approaches calmly on foot
            val dxP = playerX - npc.x
            val dzP = playerZ - npc.z
            if (!anyVehicleActive && npc.fleeTimer <= 0f && dxP * dxP + dzP * dzP < 2.1f * 2.1f && abs(playerY - WorldMapData.getTerrainHeight(npc.x, npc.z)) < 1.8f) {
                npc.yawRad = lerpAngle(npc.yawRad, atan2(dxP, dzP), (dt * 6f).coerceAtMost(1f))
                if (nearestInteract == null) nearestInteract = "Greet ${npc.name}"
                if (interactNow && !interactConsumed) {
                    listener.onNpcGreeting(npc.name)
                    interactConsumed = true
                }
                continue
            }

            if (npc.fleeTimer <= 0f && npc.pauseTimer > 0f) {
                npc.pauseTimer -= dt
                continue
            }

            val isFleeing = npc.fleeTimer > 0f
            if (isFleeing) {
                npc.fleeTimer = (npc.fleeTimer - dt).coerceAtLeast(0f)
                val awayAng = atan2(npc.x - npc.fleeFromX, npc.z - npc.fleeFromZ)
                npc.headingRad = lerpAngle(npc.headingRad, awayAng, (dt * 9f).coerceAtMost(1f))
            } else {
                npc.wanderTimer -= dt
                if (npc.wanderTimer <= 0f) {
                    npc.wanderTimer = 2.2f + nextAiRand() * 3.8f
                    if (nextAiRand() < 0.14f) {
                        npc.pauseTimer = 1.0f + nextAiRand() * 1.4f
                        continue
                    }
                    // Organic free-roaming turn
                    npc.headingRad += (nextAiRand() - 0.5f) * 1.35f
                }
            }

            val moveSpd = if (isFleeing) 5.2f else npc.speed
            val stepDist = moveSpd * dt
            var moved = false
            val probeOffsets = floatArrayOf(0f, 0.55f, -0.55f, 1.15f, -1.15f, 2.0f, -2.0f, PI.toFloat())
            for (angOff in probeOffsets) {
                val tryAng = npc.headingRad + angOff
                val candX = npc.x + sin(tryAng) * stepDist
                val candZ = npc.z + cos(tryAng) * stepDist
                val blocked = CityWorldData.isNpcBlocked(
                    x = candX,
                    z = candZ,
                    npcRadius = 0.45f,
                    chunkGrid = chunkGrid,
                    enterables = enterableBuildings,
                    activeShops = roadsideShops,
                    activeParkedCars = parkedCars,
                    movingCars = cars,
                    activeBulletBikes = bulletBikes
                )
                if (!blocked) {
                    val regTreeAdj = terrainChunkManager?.resolveRegionalTreeCollisions(candX, candZ, WorldMapData.getTerrainHeight(candX, candZ), 0.45f)
                    npc.x = regTreeAdj?.first ?: candX
                    npc.z = regTreeAdj?.second ?: candZ
                    if (angOff != 0f) npc.headingRad = tryAng
                    npc.yawRad = lerpAngle(npc.yawRad, npc.headingRad, (dt * 8.5f).coerceAtMost(1f))
                    npc.walkPhase += dt * (if (isFleeing) 14.5f else 7.4f)
                    moved = true
                    break
                }
            }
            if (!moved) {
                npc.headingRad += PI.toFloat() * 0.75f
                npc.pauseTimer = if (isFleeing) 0f else 0.5f
            }
        }

        // --- 4. Intelligent Map-Wide AI Traffic Cars (Intersection Turns, Side-Steer Overtaking, Horn, Adaptive Speed!) ---
        updateIntelligentAiTraffic(dt)

        // --- 5. Closer cinematic third-person camera distance (Free Fire / GTA style) ---
        val targetCamDist = when {
            drivenCar != null || drivenAiCar != null -> 7.2f
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
            if (c.x - playerX > 480f) c.x -= 960f
            if (c.x - playerX < -480f) c.x += 960f
            if (c.z - playerZ > 480f) c.z -= 960f
            if (c.z - playerZ < -480f) c.z += 960f
        }
        for (bird in flyingBirds) {
            bird.angle = (bird.angle + bird.angularSpeed * dt) % (2f * PI.toFloat())
            bird.wingPhase += dt * 7.8f
            // Solid 3D obstacle clearance so flying birds NEVER clip through skyscrapers, towers, trees, or mountains!
            val bx = bird.centerX + cos(bird.angle) * bird.orbitRadius
            val bz = bird.centerZ + sin(bird.angle) * bird.orbitRadius
            val aheadAngle = bird.angle + 0.22f
            val ax = bird.centerX + cos(aheadAngle) * bird.orbitRadius
            val az = bird.centerZ + sin(aheadAngle) * bird.orbitRadius
            val curCeil = CityWorldData.getMaxObstacleHeightAt(bx, bz, 3.8f, chunkGrid, enterableBuildings)
            val aheadCeil = CityWorldData.getMaxObstacleHeightAt(ax, az, 4.2f, chunkGrid, enterableBuildings)
            val requiredAlt = bird.baseAltitude.coerceAtLeast(maxOf(curCeil, aheadCeil) + 7.5f)
            bird.altitude += (requiredAlt - bird.altitude) * (dt * 4.2f).coerceAtMost(1f)
            if (bird.altitude < curCeil + 3.8f) {
                bird.altitude = curCeil + 3.8f
            }
        }

        // Enter/Drive Parked Cars & Mount/Ride Royal Enfield Bullet 350 Motorcycles (works at any mountain/island elevation!)
        val playerGroundHere = WorldMapData.getTerrainHeight(playerX, playerZ)
        val nearGroundForVehicle = abs(playerY - playerGroundHere) < 2.2f
        if (!inVehicle && nearGroundForVehicle) {
            for (bike in bulletBikes) {
                val dxB = playerX - bike.x
                val dzB = playerZ - bike.z
                if (dxB * dxB + dzB * dzB < 3.4f * 3.4f) {
                    if (nearestInteract == null) nearestInteract = "Ride ${bike.label}"
                    if (interactNow && !interactConsumed) {
                        riddenBike = bike
                        vehicleSpeed = 0f
                        vehicleVelY = 0f
                        vehicleGrounded = true
                        playerY = WorldMapData.getTerrainHeight(bike.x, bike.z)
                        lastManualCameraDragTime = -10f
                        interactConsumed = true
                        listener.onInteractMessage("Riding ${bike.label} — 350cc Thumper Active!")
                        break
                    }
                }
            }
        }
        if (!inVehicle && riddenBike == null && drivenCar == null && drivenAiCar == null && nearGroundForVehicle) {
            for (pc in parkedCars) {
                val dxC = playerX - pc.x
                val dzC = playerZ - pc.z
                if (dxC * dxC + dzC * dzC < 3.8f * 3.8f) {
                    if (nearestInteract == null) nearestInteract = "Drive Sports Car"
                    if (interactNow && !interactConsumed) {
                        drivenCar = pc
                        vehicleSpeed = 0f
                        vehicleVelY = 0f
                        vehicleGrounded = true
                        playerY = WorldMapData.getTerrainHeight(pc.x, pc.z)
                        lastManualCameraDragTime = -10f
                        interactConsumed = true
                        listener.onCarEntered()
                        listener.onInteractMessage("Driving Car — Drag to look freely, HORN or EXIT anytime!")
                        break
                    }
                }
            }
        }
        // Drive ANY Moving AI Traffic Car on the map!
        if (!inVehicle && riddenBike == null && drivenCar == null && drivenAiCar == null && nearGroundForVehicle) {
            for (car in cars) {
                val dxC = playerX - car.x
                val dzC = playerZ - car.z
                if (dxC * dxC + dzC * dzC < 4.5f * 4.5f) {
                    if (nearestInteract == null) nearestInteract = "Drive Traffic Car"
                    if (interactNow && !interactConsumed) {
                        drivenAiCar = car
                        car.isPlayerControlled = true
                        car.parkedAfterExitTimer = 0f
                        vehicleSpeed = 0f
                        vehicleVelY = 0f
                        vehicleGrounded = true
                        playerY = WorldMapData.getTerrainHeight(car.x, car.z)
                        lastManualCameraDragTime = -10f
                        interactConsumed = true
                        listener.onCarEntered()
                        listener.onInteractMessage("Driving Traffic Car — Free-roam anywhere across Saini State!")
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
        if (nowMs - lastTelemetryReportMs >= 115L) {
            lastTelemetryReportMs = nowMs
            emitTelemetry(isMovingNow, wantsSprint, nearestInteract)
            emitAmbientAudioUpdate()
        }
    }

    private fun getActiveTrafficCount(): Int = when (aiTrafficDensity) {
        DensityLevel.OFF -> 0
        DensityLevel.LOW -> (cars.size / 3).coerceAtLeast(4)
        DensityLevel.MEDIUM -> (cars.size * 2 / 3).coerceAtLeast(8)
        DensityLevel.HIGH -> cars.size
    }

    private data class VehicleSurfaceOrient(
        val supportY: Float,
        val pitchDeg: Float,
        val rollDeg: Float
    )

    /**
     * Multi-wheel 3D terrain height sampling for realistic vehicle suspension pitch & roll
     * across hills, ramps, sea bridges, and coastal roads (zero nose-clipping!).
     */
    private fun sampleVehicleOrientation(
        x: Float,
        z: Float,
        yawDeg: Float,
        halfWheelbase: Float,
        halfTrack: Float
    ): VehicleSurfaceOrient {
        val rad = yawDeg * PI.toFloat() / 180f
        val fwdX = sin(rad)
        val fwdZ = cos(rad)
        val posX = cos(rad)
        val posZ = -sin(rad)

        val hC = WorldMapData.getTerrainHeight(x, z)
        val hF = WorldMapData.getTerrainHeight(x + fwdX * halfWheelbase, z + fwdZ * halfWheelbase)
        val hR = WorldMapData.getTerrainHeight(x - fwdX * halfWheelbase, z - fwdZ * halfWheelbase)
        val hPosX = WorldMapData.getTerrainHeight(x + posX * halfTrack, z + posZ * halfTrack)
        val hNegX = WorldMapData.getTerrainHeight(x - posX * halfTrack, z - posZ * halfTrack)

        val avgY = (hF + hR + hPosX + hNegX) * 0.25f
        val pitchDeg = (-atan2(hF - hR, 2f * halfWheelbase) * 180f / PI.toFloat()).coerceIn(-30f, 30f)
        val rollDeg = (atan2(hPosX - hNegX, 2f * halfTrack) * 180f / PI.toFloat()).coerceIn(-25f, 25f)
        return VehicleSurfaceOrient(supportY = maxOf(hC, avgY), pitchDeg = pitchDeg, rollDeg = rollDeg)
    }

    private fun updateIntelligentAiTraffic(dt: Float) {
        val activeCarLimit = getActiveTrafficCount().coerceAtMost(cars.size)
        if (activeCarLimit <= 0) return
        val signalGreenForX = ((worldTime / 9.0f).toInt() % 2 == 0)
        val splines = WorldMapData.roadSplines
        val relocateCheck = (worldTime - lastTrafficRelocateTime) > 2.5f
        if (relocateCheck) lastTrafficRelocateTime = worldTime
        val playerOutsideMetro = abs(playerX) > 420f || playerZ < -410f || playerZ > 410f

        for (i in 0 until activeCarLimit) {
            val car = cars[i]
            if (car.isPlayerControlled) continue
            if (car.parkedAfterExitTimer > 0f) {
                car.parkedAfterExitTimer = (car.parkedAfterExitTimer - dt).coerceAtLeast(0f)
                car.currentSpeed = 0f
                continue
            }
            if (car.turnCooldown > 0f) car.turnCooldown = (car.turnCooldown - dt).coerceAtLeast(0f)
            if (car.hornCooldown > 0f) car.hornCooldown = (car.hornCooldown - dt).coerceAtLeast(0f)

            val rad = car.yawDeg * PI.toFloat() / 180f
            val fwdX = sin(rad)
            val fwdZ = cos(rad)
            // Right vector in world XZ when facing (fwdX, fwdZ): local +X is LEFT, local -X is RIGHT
            val localPosX = cos(rad)
            val localPosZ = -sin(rad)

            // 1. GTA 6-Style Forward Vision Cone (center lane + left/right pass lane check, ZERO magnetic sliding!)
            var minAheadDist = 99f
            var centerBlocked = false
            var leftLaneBlocked = false
            var rightLaneBlocked = false
            var hardStop = false
            var shouldHonk = false

            fun scanEntity(ox: Float, oz: Float, halfW: Float, halfL: Float, isPlayerEntity: Boolean) {
                val dx = ox - car.x
                val dz = oz - car.z
                if (abs(dx) > 22f || abs(dz) > 22f) return
                val fwd = dx * fwdX + dz * fwdZ
                val lat = dx * localPosX + dz * localPosZ
                if (fwd in 0.6f..(19.0f + halfL)) {
                    if (abs(lat) < (1.42f + halfW)) {
                        centerBlocked = true
                        if (fwd < minAheadDist) minAheadDist = fwd
                        if (isPlayerEntity && fwd < 13f) shouldHonk = true
                        if (!isPlayerEntity && fwd < 9.5f) shouldHonk = true
                        if (fwd < (4.2f + halfL) && abs(lat) < (1.18f + halfW * 0.65f)) {
                            hardStop = true
                        }
                    }
                    // Check if +3.0m or -3.0m side-pass lanes are occupied
                    if (abs(lat - 3.0f) < (1.40f + halfW)) leftLaneBlocked = true
                    if (abs(lat + 3.0f) < (1.40f + halfW)) rightLaneBlocked = true
                }
            }

            val playerGroundDiff = abs(playerY - WorldMapData.getTerrainHeight(car.x, car.z))
            if (playerGroundDiff < 2.4f) {
                val pW = if (drivenCar != null || drivenAiCar != null) 1.30f else 0.72f
                val pL = if (drivenCar != null || drivenAiCar != null) 2.40f else 0.72f
                scanEntity(playerX, playerZ, pW, pL, isPlayerEntity = true)
            }
            for (j in 0 until activeCarLimit) {
                if (i == j) continue
                val other = cars[j]
                scanEntity(other.x, other.z, 1.25f, 2.40f, isPlayerEntity = false)
            }
            for (pc in parkedCars) {
                scanEntity(pc.x, pc.z, 1.25f, 2.40f, isPlayerEntity = false)
            }
            for (bike in bulletBikes) {
                scanEntity(bike.x, bike.z, 0.78f, 1.15f, isPlayerEntity = false)
            }

            val distPlayerSq = (playerX - car.x) * (playerX - car.x) + (playerZ - car.z) * (playerZ - car.z)
            val playerBoardingNear = drivenCar == null && drivenAiCar == null && riddenBike == null && distPlayerSq < 4.8f * 4.8f

            if (shouldHonk && car.hornCooldown <= 0f && distPlayerSq < 44f * 44f && worldTime - lastHornTime > 1.9f) {
                lastHornTime = worldTime
                car.hornCooldown = 3.4f
                listener.onCarHorn()
            }

            // Also verify solid buildings/dhabas/hotels/trees along side-pass lanes so AI NEVER swerves into a wall!
            val probeDist = 6.5f
            if (!leftLaneBlocked) {
                val lx = car.x + fwdX * probeDist + localPosX * 3.0f
                val lz = car.z + fwdZ * probeDist + localPosZ * 3.0f
                if (CityWorldData.isVehicleSolidBlocked(lx, lz, 1.35f, chunkGrid, enterableBuildings, roadsideShops)) {
                    leftLaneBlocked = true
                }
            }
            if (!rightLaneBlocked) {
                val rx = car.x + fwdX * probeDist - localPosX * 3.0f
                val rz = car.z + fwdZ * probeDist - localPosZ * 3.0f
                if (CityWorldData.isVehicleSolidBlocked(rx, rz, 1.35f, chunkGrid, enterableBuildings, roadsideShops)) {
                    rightLaneBlocked = true
                }
            }

            // Human-like lateral steering: ONLY steer sideways proportional to forward speed (ZERO magnetic sliding when stopped!)
            val desiredAvoid = when {
                !centerBlocked -> 0f
                !leftLaneBlocked && !car.isTurning -> 2.85f
                !rightLaneBlocked && !car.isTurning -> -2.85f
                else -> {
                    // Both side lanes blocked -> brake cleanly & wait like a real driver!
                    if (minAheadDist < 9.0f) hardStop = true
                    car.avoidOffset * 0.5f
                }
            }
            val moveRatio = (car.currentSpeed / 6.0f).coerceIn(0f, 1f)
            if (moveRatio > 0.08f) {
                val avoidRate = (if (desiredAvoid != 0f) 2.6f else 1.8f) * moveRatio
                car.avoidOffset += (desiredAvoid - car.avoidOffset) * (dt * avoidRate).coerceAtMost(1f)
            }

            val prevX = car.x
            val prevZ = car.z

            if (car.splineIndex >= 0 && car.splineIndex < splines.size) {
                // --- 50 KM x 65 KM MULTI-ISLAND & MAINLAND HIGHWAY SPLINE AI ---
                if (relocateCheck && playerOutsideMetro && distPlayerSq > 540f * 540f) {
                    var bestSp = -1
                    var bestDistAlong = 0f
                    var bestDSq = Float.MAX_VALUE
                    for (si in splines.indices) {
                        val sp = splines[si]
                        if (!sp.roadClass.isPaved) continue
                        var acc = 0f
                        for (pi in 0 until sp.points.size - 1) {
                            val (ax, az) = sp.points[pi]
                            val (bx, bz) = sp.points[pi + 1]
                            val segL = sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az))
                            val mx = (ax + bx) * 0.5f
                            val mz = (az + bz) * 0.5f
                            val d2 = (mx - playerX) * (mx - playerX) + (mz - playerZ) * (mz - playerZ)
                            if (d2 in (95f * 95f)..(340f * 340f) && d2 < bestDSq) {
                                bestDSq = d2
                                bestSp = si
                                bestDistAlong = acc + segL * 0.5f
                            }
                            acc += segL
                        }
                    }
                    if (bestSp >= 0) {
                        car.splineIndex = bestSp
                        car.splineDist = bestDistAlong
                        car.avoidOffset = 0f
                        car.isTurning = false
                    }
                }

                val sp = splines[car.splineIndex]
                val totalLen = computeSplineLength(sp)
                val targetSpd = when {
                    hardStop || playerBoardingNear -> 0f
                    centerBlocked && minAheadDist < 15f -> car.baseSpeed * 0.45f
                    else -> car.baseSpeed
                }
                car.currentSpeed += (targetSpd - car.currentSpeed) * (dt * 4.5f).coerceAtMost(1f)
                car.splineDist += car.currentSpeed * car.splineDir * dt
                if (sp.closedLoop) {
                    if (car.splineDist >= totalLen) car.splineDist -= totalLen
                    if (car.splineDist < 0f) car.splineDist += totalLen
                } else {
                    if (car.splineDist >= totalLen - 12f) {
                        car.splineDist = totalLen - 12f
                        car.splineDir = -1f
                    } else if (car.splineDist <= 12f) {
                        car.splineDist = 12f
                        car.splineDir = 1f
                    }
                }
                val sample = sampleRoadSpline(sp, car.splineDist, car.splineDir)
                val spYawRad = sample.third
                val laneRightX = cos(spYawRad)
                val laneRightZ = -sin(spYawRad)
                val totalLat = 2.65f + car.avoidOffset
                val candX = sample.first + laneRightX * totalLat
                val candZ = sample.second + laneRightZ * totalLat
                if (!CityWorldData.isVehicleSolidBlocked(candX, candZ, 1.25f, chunkGrid, enterableBuildings, roadsideShops)) {
                    car.x = candX
                    car.z = candZ
                } else {
                    // Stay cleanly on paved centerline if roadside structure is near shoulder
                    car.avoidOffset *= 0.4f
                    car.x = sample.first + laneRightX * 2.1f
                    car.z = sample.second + laneRightZ * 2.1f
                }
                val targetYaw = spYawRad * 180f / PI.toFloat()
                car.yawDeg = lerpAngleDeg(car.yawDeg, targetYaw, (dt * 5.8f).coerceAtMost(1f))
                car.wheelAngleDeg = (car.wheelAngleDeg + car.currentSpeed * dt * 165f) % 360f
            } else {
                // --- SAINI METRO CITY GRID AI (GTA 6 Smooth Curved Bezier Intersection Turns — NO 90° Snaps, NO Loops!) ---
                var redLightStop = false
                if (!car.isTurning) {
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
                }

                val targetSpd = when {
                    hardStop || redLightStop || playerBoardingNear -> 0f
                    car.isTurning -> (car.baseSpeed * 0.62f).coerceAtLeast(3.8f)
                    centerBlocked && minAheadDist < 15.5f -> car.baseSpeed * 0.45f
                    else -> car.baseSpeed
                }
                car.currentSpeed += (targetSpd - car.currentSpeed) * (dt * 4.8f).coerceAtMost(1f)

                if (car.isTurning) {
                    // Advance smoothly along quadratic Bezier arc around intersection corner!
                    val arcLen = 11.2f
                    car.turnT += (car.currentSpeed * dt) / arcLen
                    val t = car.turnT.coerceIn(0f, 1f)
                    val u = 1f - t
                    val bx = u * u * car.turnStartX + 2f * u * t * car.turnCtrlX + t * t * car.turnEndX
                    val bz = u * u * car.turnStartZ + 2f * u * t * car.turnCtrlZ + t * t * car.turnEndZ
                    // Derivative tangent of Bezier curve for natural curved steering angle
                    val tanX = 2f * u * (car.turnCtrlX - car.turnStartX) + 2f * t * (car.turnEndX - car.turnCtrlX)
                    val tanZ = 2f * u * (car.turnCtrlZ - car.turnStartZ) + 2f * t * (car.turnEndZ - car.turnCtrlZ)
                    if (!CityWorldData.isVehicleSolidBlocked(bx, bz, 1.25f, chunkGrid, enterableBuildings, roadsideShops)) {
                        car.x = bx
                        car.z = bz
                        if (tanX * tanX + tanZ * tanZ > 0.01f) {
                            val tangentYaw = atan2(tanX, tanZ) * 180f / PI.toFloat()
                            car.yawDeg = lerpAngleDeg(car.yawDeg, tangentYaw, (dt * 11f).coerceAtMost(1f))
                        }
                    } else {
                        car.currentSpeed *= 0.2f
                    }
                    if (car.turnT >= 1f) {
                        car.isTurning = false
                        car.turnT = 0f
                        car.turnCooldown = 5.5f
                    }
                } else {
                    // Check if approaching a valid city intersection to initiate a smooth curved Bezier turn (NO 90° snap!)
                    if (car.turnCooldown <= 0f && !redLightStop && minAheadDist > 9f && car.currentSpeed > 2.0f) {
                        val posAlong = if (car.axisX) car.x else car.z
                        for (rc in CityWorldData.roadCoords) {
                            val distBeforeCenter = (rc - posAlong) * car.dir
                            // Trigger turn entry 5.5m..7.8m BEFORE intersection center so the Bezier arc sweeps cleanly around corner!
                            if (distBeforeCenter in 5.2f..7.8f && rc in -180f..180f) {
                                val interKey = (rc.toInt() shl 16) xor car.roadLaneCoord.toInt()
                                if (interKey != car.lastInterKey) {
                                    car.lastInterKey = interKey
                                    val turnRoll = nextAiRand()
                                    if (turnRoll < 0.34f) {
                                        val newAxisX = !car.axisX
                                        val newDir = if (nextAiRand() < 0.5f) 1f else -1f
                                        val newLaneCoord = rc + (if (newAxisX) (newDir * 2.7f) else (-newDir * 2.7f))
                                        val startX = car.x
                                        val startZ = car.z
                                        val ctrlX = if (car.axisX) newLaneCoord else car.x
                                        val ctrlZ = if (car.axisX) car.z else newLaneCoord
                                        val endX = if (newAxisX) (rc + newDir * 6.5f) else newLaneCoord
                                        val endZ = if (newAxisX) newLaneCoord else (rc + newDir * 6.5f)

                                        car.axisX = newAxisX
                                        car.dir = newDir
                                        car.roadLaneCoord = newLaneCoord
                                        car.avoidOffset = 0f
                                        car.isTurning = true
                                        car.turnT = 0f
                                        car.turnStartX = startX
                                        car.turnStartZ = startZ
                                        car.turnCtrlX = ctrlX
                                        car.turnCtrlZ = ctrlZ
                                        car.turnEndX = endX
                                        car.turnEndZ = endZ
                                    } else {
                                        car.turnCooldown = 2.8f
                                    }
                                }
                                break
                            }
                        }
                    }

                    val baseYaw = if (car.axisX) (if (car.dir > 0f) 90f else -90f) else (if (car.dir > 0f) 0f else 180f)
                    val swerveYaw = if (moveRatio > 0.1f) ((desiredAvoid - car.avoidOffset) * 4.5f).coerceIn(-18f, 18f) else 0f
                    car.targetYawDeg = baseYaw + swerveYaw
                    car.yawDeg = lerpAngleDeg(car.yawDeg, car.targetYawDeg, (dt * 6.5f).coerceAtMost(1f))

                    var nextX = car.x
                    var nextZ = car.z
                    if (car.axisX) {
                        nextX += car.currentSpeed * car.dir * dt
                        if (moveRatio > 0.08f) {
                            val targetZ = car.roadLaneCoord + car.avoidOffset * (if (car.dir > 0f) -1f else 1f)
                            nextZ += (targetZ - nextZ) * (dt * 3.8f * moveRatio).coerceAtMost(1f)
                        }
                        if (nextX > car.maxBound) { nextX = car.minBound; car.lastInterKey = Int.MIN_VALUE }
                        if (nextX < car.minBound) { nextX = car.maxBound; car.lastInterKey = Int.MIN_VALUE }
                    } else {
                        nextZ += car.currentSpeed * car.dir * dt
                        if (moveRatio > 0.08f) {
                            val targetX = car.roadLaneCoord + car.avoidOffset * (if (car.dir > 0f) 1f else -1f)
                            nextX += (targetX - nextX) * (dt * 3.8f * moveRatio).coerceAtMost(1f)
                        }
                        if (nextZ > car.maxBound) { nextZ = car.minBound; car.lastInterKey = Int.MIN_VALUE }
                        if (nextZ < car.minBound) { nextZ = car.maxBound; car.lastInterKey = Int.MIN_VALUE }
                    }
                    if (!CityWorldData.isVehicleSolidBlocked(nextX, nextZ, 1.25f, chunkGrid, enterableBuildings, roadsideShops)) {
                        car.x = nextX
                        car.z = nextZ
                    } else {
                        car.currentSpeed *= 0.25f
                        car.avoidOffset *= 0.4f
                    }
                }
                car.wheelAngleDeg = (car.wheelAngleDeg + car.currentSpeed * dt * 165f) % 360f
            }

            // Smooth 3D surface slope alignment for nearby moving AI cars (zero nose-clipping on hills/bridges!)
            if (distPlayerSq < 115f * 115f) {
                val aiOrient = sampleVehicleOrientation(car.x, car.z, car.yawDeg, 1.38f, 0.88f)
                car.pitchDeg += (aiOrient.pitchDeg - car.pitchDeg) * (dt * 8.5f).coerceAtMost(1f)
                car.rollDeg += (aiOrient.rollDeg - car.rollDeg) * (dt * 8.5f).coerceAtMost(1f)
            }
            // Safety clamp if car moved outside world bounds
            if (abs(car.x) > WorldMapData.PLAYABLE_BOUND_X - 40f || abs(car.z) > WorldMapData.PLAYABLE_BOUND_Z - 40f) {
                car.x = prevX
                car.z = prevZ
                car.splineDir = -car.splineDir
            }
        }

        // Gentle Car-to-Car separation ONLY when both are overlapping tightly (without causing magnetic sideways slides!)
        for (i in 0 until activeCarLimit) {
            val a = cars[i]
            if (a.isPlayerControlled) continue
            for (j in i + 1 until activeCarLimit) {
                val b = cars[j]
                if (b.isPlayerControlled) continue
                val dx = a.x - b.x
                val dz = a.z - b.z
                val dSq = dx * dx + dz * dz
                if (dSq < 3.1f * 3.1f && dSq > 0.001f) {
                    val d = sqrt(dSq)
                    val push = (3.1f - d) * 0.45f
                    val nx = dx / d; val nz = dz / d
                    val ax = a.x + nx * push; val az = a.z + nz * push
                    val bx = b.x - nx * push; val bz = b.z - nz * push
                    if (!CityWorldData.isVehicleSolidBlocked(ax, az, 1.2f, chunkGrid, enterableBuildings, roadsideShops)) {
                        a.x = ax; a.z = az
                    }
                    if (!CityWorldData.isVehicleSolidBlocked(bx, bz, 1.2f, chunkGrid, enterableBuildings, roadsideShops)) {
                        b.x = bx; b.z = bz
                    }
                }
            }
        }
    }

    private fun computeSplineLength(sp: RoadSpline): Float {
        var total = 0f
        val pts = sp.points
        for (i in 0 until pts.size - 1) {
            val dx = pts[i + 1].first - pts[i].first
            val dz = pts[i + 1].second - pts[i].second
            total += sqrt(dx * dx + dz * dz)
        }
        if (sp.closedLoop && pts.size >= 2) {
            val dx = pts[0].first - pts.last().first
            val dz = pts[0].second - pts.last().second
            total += sqrt(dx * dx + dz * dz)
        }
        return total.coerceAtLeast(1f)
    }

    private fun sampleRoadSpline(sp: RoadSpline, distIn: Float, dir: Float): Triple<Float, Float, Float> {
        val pts = sp.points
        if (pts.size < 2) return Triple(0f, 0f, 0f)
        val segCount = if (sp.closedLoop) pts.size else pts.size - 1
        var rem = distIn.coerceAtLeast(0f)
        for (i in 0 until segCount) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            val dx = b.first - a.first
            val dz = b.second - a.second
            val len = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)
            if (rem <= len || i == segCount - 1) {
                val t = (rem / len).coerceIn(0f, 1f)
                val x = a.first + dx * t
                val z = a.second + dz * t
                val h = atan2(dx * dir, dz * dir)
                return Triple(x, z, h)
            }
            rem -= len
        }
        return Triple(pts[0].first, pts[0].second, 0f)
    }

    private fun lerpAngleDeg(a: Float, b: Float, t: Float): Float {
        var diff = (b - a) % 360f
        if (diff < -180f) diff += 360f
        if (diff > 180f) diff -= 360f
        return a + diff * t
    }

    private fun emitAmbientAudioUpdate() {
        val mode = when {
            drivenCar != null || drivenAiCar != null -> ActiveVehicleMode.CAR
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

        // Distance to 360-degree curved coastline / ocean / beaches / islands
        val oceanDist = WorldMapData.getOceanDistance(playerX, playerZ)

        // Proximity to trees/park/jungle for daytime birds chirping
        var nearbyTrees = 0
        for (t in visTrees) {
            val dx = playerX - t.x
            val dz = playerZ - t.z
            if (dx * dx + dz * dz < 26f * 26f) {
                nearbyTrees++
                if (nearbyTrees >= 6) break
            }
        }
        val biomeHere = WorldMapData.getBiomeAt(playerX, playerZ)
        val biomeBirdBoost = when (biomeHere) {
            WorldBiome.JUNGLE_RIVER -> 0.85f
            WorldBiome.VILLAGE_PLAINS, WorldBiome.SNOW_PEAKS -> 0.55f
            WorldBiome.KONKAN_COAST, WorldBiome.SAINI_KEYS -> 0.35f
            else -> 0f
        }
        val nearTreesFactor = maxOf((nearbyTrees / 6f).coerceIn(0f, 1f), biomeBirdBoost)

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
        val inMetroCore = abs(playerX) <= 450f && playerZ in -420f..410f
        val locLabel = currentBuilding?.let { b ->
            val floorText = if (currentFloorNumber >= 2) "2nd Floor" else "Ground Floor"
            "${b.name} • $floorText"
        } ?: insideShop?.let { "${it.name} • Interior" } ?: if (!inMetroCore) {
            WorldMapData.getRegionLabel(playerX, playerZ)
        } else when {
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
            drivenCar != null || drivenAiCar != null -> ActiveVehicleMode.CAR
            riddenBike != null -> ActiveVehicleMode.BULLET_BIKE
            else -> ActiveVehicleMode.ON_FOOT
        }
        val timeLabel = when {
            nightFactor > 0.65f -> "Night"
            nightFactor > 0.22f -> if (timeOfDay < 0.5f) "Dawn" else "Sunset"
            else -> "Day"
        }

        if (cachedCollectedOrbsSnapshot.size != collectedOrbs.size) {
            cachedCollectedOrbsSnapshot = collectedOrbs.toSet()
        }
        if (cachedDiscoveredZonesSnapshot.size != discoveredZones.size) {
            cachedDiscoveredZonesSnapshot = discoveredZones.toSet()
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
                collectedOrbIds = cachedCollectedOrbsSnapshot,
                discoveredZoneIds = cachedDiscoveredZonesSnapshot,
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
                nearbyGateProgress = nearbyGate?.openProgress ?: 0f,
                isInWater = isInWaterNow,
                waterWarning = currentWaterWarning,
                parkedCarX = lastPlayerVehX,
                parkedCarZ = lastPlayerVehZ
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
        seenBuildings.clear(); seenEnterables.clear(); seenRoadsideShops.clear()
        seenPortContainers.clear(); seenArmyBunkers.clear(); seenBarbedFences.clear(); seenArmyVehicles.clear()
        visBuildings.clear(); visEnterables.clear(); visRoadsideShops.clear(); visStaticObstacles.clear(); visPortContainers.clear()
        visTrees.clear(); visStreetLights.clear(); visTrafficSignals.clear(); visBenches.clear()
        visKiosks.clear(); visParkedCars.clear(); visMovingCars.clear(); visNpcs.clear()
        visBulletBikes.clear(); visGrassTufts.clear(); visArmyBunkers.clear()
        visBarbedFences.clear(); visHelipads.clear(); visArmyVehicles.clear(); visBirds.clear()
        visRoads.clear(); visIntersections.clear(); visColonyPads.clear(); visColonyAlleys.clear()
        visibleObjectsThisFrame = 0
        culledObjectsThisFrame = 0

        // Skip downtown 20m chunk grid scan entirely when cruising outside Saini Metro (saves CPU!)
        val nearMetroGrid = abs(playerX) < 560f && playerZ in -560f..560f
        if (nearMetroGrid) {
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
                    if (isObjectInZoneAndCone(b.x, b.z, maxOf(b.width, b.depth) * 0.78f) && seenBuildings.add(b)) visBuildings.add(b)
                }
                for (eb in chunk.enterables) {
                    if (isObjectInZoneAndCone(eb.centerX, eb.centerZ, 12.0f) && seenEnterables.add(eb)) visEnterables.add(eb)
                }
                for (shop in chunk.roadsideShops) {
                    if (isObjectInZoneAndCone(shop.x, shop.z, 5.2f) && seenRoadsideShops.add(shop)) visRoadsideShops.add(shop)
                }
                for (obs in chunk.staticObstacles) {
                    if (isObjectInZoneAndCone(obs.x, obs.z, obs.size + 1.4f)) visStaticObstacles.add(obs)
                }
                for (c in chunk.portContainers) {
                    if (isObjectInZoneAndCone(c.x, c.z, maxOf(c.length, c.width) * 0.65f) && seenPortContainers.add(c)) visPortContainers.add(c)
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
                    if (isObjectInZoneAndCone(bk.x, bk.z, maxOf(bk.width, bk.depth) * 0.7f) && seenArmyBunkers.add(bk)) visArmyBunkers.add(bk)
                }
                for (fn in chunk.barbedFences) {
                    if (isObjectInZoneAndCone(fn.x, fn.z, fn.length * 0.55f) && seenBarbedFences.add(fn)) visBarbedFences.add(fn)
                }
                for (hp in chunk.helipads) {
                    if (isObjectInZoneAndCone(hp.x, hp.z, hp.size * 0.75f)) visHelipads.add(hp)
                }
                for (av in chunk.armyVehicles) {
                    if (isObjectInZoneAndCone(av.x, av.z, 4.0f) && seenArmyVehicles.add(av)) visArmyVehicles.add(av)
                }
            }
        }
        } // end if (nearMetroGrid)

        // Dynamic drivable cars, rideable Bullet bikes, moving traffic (scaled by Traffic Density), NPCs & soaring birds
        for (pc in parkedCars) {
            if (pc === drivenCar || isObjectInZoneAndCone(pc.x, pc.z, 3.4f)) visParkedCars.add(pc)
        }
        for (bike in bulletBikes) {
            if (bike === riddenBike || isObjectInZoneAndCone(bike.x, bike.z, 2.2f)) visBulletBikes.add(bike)
        }
        val activeTrafficLimit = getActiveTrafficCount().coerceAtMost(cars.size)
        for (ci in 0 until activeTrafficLimit) {
            val car = cars[ci]
            if (car === drivenAiCar || isObjectInZoneAndCone(car.x, car.z, 3.6f)) visMovingCars.add(car)
        }
        if (drivenAiCar != null && !visMovingCars.contains(drivenAiCar)) {
            visMovingCars.add(drivenAiCar!!)
        }
        val activeNpcLimit = when (npcDensity) {
            DensityLevel.OFF -> 0
            DensityLevel.LOW -> (npcs.size / 3).coerceAtLeast(4)
            DensityLevel.MEDIUM -> (npcs.size * 2 / 3).coerceAtLeast(8)
            DensityLevel.HIGH -> npcs.size
        }.coerceAtMost(npcs.size)
        for (ni in 0 until activeNpcLimit) {
            val npc = npcs[ni]
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
        if (distPlayerSq > maxR * maxR) {
            culledObjectsThisFrame++
            return false
        }
        // Real camera frustum half-space culling: skip distant objects strictly behind the camera (~50% GPU draw savings!)
        val dxC = objX - camPosX
        val dzC = objZ - camPosZ
        val forwardProj = dxC * camForwardX + dzC * camForwardZ
        if (forwardProj < -(objRadius + 9.5f)) {
            culledObjectsThisFrame++
            return false
        }
        visibleObjectsThisFrame++
        return true
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
        val playerGroundY = WorldMapData.getTerrainHeight(playerX, playerZ)
        if (currentBuilding == null && sinPitch < -0.05f) {
            val maxDistAboveGround = (lookAtY - (playerGroundY + 0.55f)) / (-sinPitch)
            effDist = minOf(effDist, maxDistAboveGround.coerceAtLeast(1.8f))
        }
        val rayDirX = sin(cameraYaw) * cosPitch
        val rayDirZ = cos(cameraYaw) * cosPitch
        if (currentBuilding == null) {
            effDist = clampCameraDistanceAgainstWalls(playerX, playerZ, rayDirX, rayDirZ, effDist)
        }

        camPosX = playerX + rayDirX * effDist
        camPosZ = playerZ + rayDirZ * effDist
        val desiredCamY = lookAtY + 0.15f + sinPitch * effDist
        camPosY = if (currentBuilding != null) {
            val floorBase = if (currentFloorNumber >= 2) currentBuilding!!.floorHeight else 0f
            desiredCamY.coerceIn(floorBase + 0.9f, floorBase + currentBuilding!!.floorHeight - 0.45f)
        } else {
            val camGroundY = WorldMapData.getTerrainHeight(camPosX, camPosZ)
            desiredCamY.coerceAtLeast(camGroundY + 0.60f)
        }

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

        // 1. 4800m x 4800m Multi-Biome Chunk-Streamed 3D Terrain, Curved Highways/Bridges & 360° Animated Ocean!
        terrainChunkManager?.drawTerrainAndOcean(
            dc = dc,
            playerX = playerX,
            playerZ = playerZ,
            camPosX = camPosX,
            camPosY = camPosY,
            camPosZ = camPosZ,
            lDirX = lDirX,
            lDirY = lDirY,
            lDirZ = lDirZ,
            sunR = sunR,
            sunG = sunG,
            sunB = sunB,
            ambientScale = ambScale,
            fogR = fogR,
            fogG = fogG,
            fogB = fogB,
            fogStart = fogStart,
            fogEnd = fogEnd,
            worldTime = worldTime,
            graphicsQuality = graphicsQuality
        )

        // 1a. Saini Metro Core Ground, Waterfront Boardwalk, Port Concrete Yards & Wooden Pier (only when near Metro!)
        val renderMetroSurface = abs(playerX) < 560f && playerZ in -560f..560f
        if (renderMetroSurface) {
            dc.bindVbo(assets.quadMesh)
            dc.drawQuad(0f, 0f, -8.5f, 896f, 832f, assets.texGrass, 120f, 110f, 0.74f, 0.78f, 0.64f, 0f, 0.03f, 6f, 0f)
            // Sandy coastal edge along Saini Waterfront
            dc.drawQuad(0f, 0.01f, CityWorldData.WATERFRONT_Z + 1.5f, 896f, 4.5f, assets.texConcrete, 90f, 1f, 0.92f, 0.84f, 0.68f, 0f, 0.08f, 12f, 0f)
            // Waterfront Paver Boardwalk (tex_footpath_paver_blocks) & Wooden Pier (tex_interior_wood_floor)
            dc.drawQuad(0f, 0.025f, CityWorldData.WATERFRONT_Z - 2.5f, 880f, 8.2f, assets.texFootpathPaverBlocks, 185f, 2.2f, 0.96f, 0.96f, 0.95f, 0f, 0.16f + roadWetness * 0.35f, 18f, 0.06f + roadWetness * 0.25f)
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

        // Update rigged 6-animation skeletal pose (STAND / RUN / FLIP / HAND_FIGHT / LEG_FIGHT / RELEASE + gate push!)
        if (drivenCar == null && drivenAiCar == null) {
            glbPlayer?.updatePose(
                worldTime = worldTime,
                walkCycle = if (riddenBike != null) 0f else walkCycle,
                isMoving = if (riddenBike != null) false else isMovingNow,
                isSprinting = if (riddenBike != null) false else (input.isSprinting && isMovingNow),
                isGrounded = isGrounded,
                isRidingBike = riddenBike != null,
                gateHandReach = gatePushBlend,
                forcedActionClip = if (riddenBike != null) null else activeActionClip,
                actionTimeSec = activeActionTime,
                loopForcedClip = false
            )
        }

        // 2. SUN-PROJECTED SILHOUETTE SHADOW PASS (controlled directly by Settings -> Shadows!)
        if (shadowsEnabled && abs(playerX) < 465f && playerZ in -435f..420f) {
            renderShapeAccurateShadowPass()
        }

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

        // 6. Realistic Multi-Part Branched Trees & World-Wide 18-Asset Multi-Biome Chunk-Streamed Grass Engine
        val treeStep = if (graphicsQuality == GraphicsQuality.LOW) 2 else 1
        for (i in visTrees.indices step treeStep) {
            CityPropDrawer.drawRealisticTree(dc, visTrees[i], worldTime, graphicsQuality)
        }
        val grassPushRadius = when {
            drivenCar != null || drivenAiCar != null -> 2.65f
            riddenBike != null -> 1.55f
            else -> 1.25f
        }
        val grassFogStart = fogStart.coerceAtLeast(110f)
        val grassFogEnd = (fogEnd + 320f).coerceAtLeast(620f)
        grassSystem?.drawVisibleGrass(
            vpMatrix = dc.vpMatrix,
            worldTime = worldTime,
            playerX = playerX,
            playerY = playerY,
            playerZ = playerZ,
            playerPushRadius = grassPushRadius,
            camPosX = camPosX,
            camPosY = camPosY,
            camPosZ = camPosZ,
            camFwdX = camForwardX,
            camFwdZ = camForwardZ,
            lDirX = lDirX,
            lDirY = lDirY,
            lDirZ = lDirZ,
            sunR = sunR,
            sunG = sunG,
            sunB = sunB,
            ambientScale = ambScale,
            fogR = fogR,
            fogG = fogG,
            fogB = fogB,
            fogStart = grassFogStart,
            fogEnd = grassFogEnd,
            rainIntensity = rainIntensity,
            graphicsQuality = graphicsQuality,
            grassDensity = grassDensity
        )
        // Restore main CityShaderProgram & VBO tracking cleanly for subsequent city props
        GLES20.glUseProgram(assets.shader.programId)
        dc.boundVboId = -1

        // 6b. Multi-Biome Regional Forests, Overseas Sea Bridge Piers & Iconic 3D Landmarks across all 7 Regions!
        terrainChunkManager?.drawRegionalLandmarksAndTrees(
            dc = dc,
            playerX = playerX,
            playerZ = playerZ,
            worldTime = worldTime,
            nightFactor = nightFactor,
            graphicsQuality = graphicsQuality,
            streamRadius = streamingRadiusMeters
        )

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

        // 8. Realistic Curved GTA-Style Cars & Royal Enfield Bullet Motorcycles (with 3D Slope Pitch/Roll & Seated Bike Rider!)
        val wrap = vehicleWrapRgb
        for (pc in visParkedCars) {
            val isActive = pc === drivenCar
            val gY: Float
            val pitch: Float
            val roll: Float
            if (isActive) {
                gY = playerY
                pitch = pc.pitchDeg
                roll = pc.rollDeg
            } else {
                val o = sampleVehicleOrientation(pc.x, pc.z, pc.yawDeg, 1.38f, 0.88f)
                gY = o.supportY
                pitch = o.pitchDeg
                roll = o.rollDeg
                pc.pitchDeg = pitch
                pc.rollDeg = roll
            }
            val cr = if (isActive && wrap != null) wrap[0] else pc.r
            val cg = if (isActive && wrap != null) wrap[1] else pc.g
            val cb = if (isActive && wrap != null) wrap[2] else pc.b
            CityPropDrawer.drawCar(dc, pc.x, pc.z, pc.yawDeg, pc.wheelAngleDeg, cr, cg, cb, groundY = gY, pitchDeg = pitch, rollDeg = roll)
        }
        for (bike in visBulletBikes) {
            val isRiddenNow = bike === riddenBike
            val gY = if (isRiddenNow) {
                playerY
            } else {
                val o = sampleVehicleOrientation(bike.x, bike.z, bike.yawDeg, 0.72f, 0.32f)
                bike.pitchDeg = o.pitchDeg
                bike.rollDeg = o.rollDeg
                o.supportY
            }
            WorldAssetsDrawer.drawBulletBike(
                dc = dc,
                bike = bike,
                isRidden = isRiddenNow,
                groundY = gY,
                riderSpeed = if (isRiddenNow) vehicleSpeed else 0f,
                worldTime = worldTime
            )
        }
        for (car in visMovingCars) {
            val isActive = car === drivenAiCar
            val gY = if (isActive) playerY else sampleVehicleOrientation(car.x, car.z, car.yawDeg, 1.38f, 0.88f).supportY
            val cr = if (isActive && wrap != null) wrap[0] else car.r
            val cg = if (isActive && wrap != null) wrap[1] else car.g
            val cb = if (isActive && wrap != null) wrap[2] else car.b
            CityPropDrawer.drawCar(dc, car.x, car.z, car.yawDeg, car.wheelAngleDeg, cr, cg, cb, groundY = gY, pitchDeg = car.pitchDeg, rollDeg = car.rollDeg)
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

        // 11. Rigged Avaturn 3D Player Character (hidden inside cars; seated bike rider drawn inside drawBulletBike!)
        if (drivenCar == null && drivenAiCar == null && riddenBike == null) {
            val rigged = glbPlayer
            if (rigged != null) {
                rigged.draw(
                    dc = dc,
                    x = playerX, y = playerY, z = playerZ,
                    yawRad = playerFacingYaw,
                    lightDirX = lDirX, lightDirY = lDirY, lightDirZ = lDirZ,
                    sunR = sunR, sunG = sunG, sunB = sunB,
                    ambientScale = ambScale,
                    camX = camPosX, camY = camPosY, camZ = camPosZ,
                    fogR = fogR, fogG = fogG, fogB = fogB,
                    fogStart = fogStart, fogEnd = fogEnd,
                    slopePitchDeg = playerSlopePitchDeg,
                    slopeRollDeg = playerSlopeRollDeg
                )
            } else {
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
        }

        // 12. Active Map Waypoint Tactical Beacon (anchored cleanly to 3D terrain elevation!)
        if (waypointActive) {
            val wy = WorldMapData.getTerrainHeight(waypointX, waypointZ)
            val pulse = 0.85f + 0.15f * sin(worldTime * 4.5f)
            GLES20.glDepthMask(false)
            GLES20.glDisable(GLES20.GL_CULL_FACE)
            dc.bindVbo(assets.cylinderMesh)
            dc.drawMeshTransformed(
                waypointX, wy + 26f, waypointZ,
                0.72f * pulse, 52f, 0.72f * pulse,
                0f, 0f, 0f,
                assets.texWhite, 1f, 1f,
                1.0f, 0.27f, 0.33f, 0.28f,
                1.0f, 0.1f, 8f, 0f, assets.cylinderMesh
            )
            dc.bindVbo(assets.ringMesh)
            dc.drawMeshTransformed(
                waypointX, wy + 0.14f, waypointZ,
                3.4f * pulse, 1f, 3.4f * pulse,
                0f, 0f, 0f,
                assets.texWhite, 1f, 1f,
                1.0f, 0.27f, 0.33f, 0.75f,
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

        // Tree shadows (limited to nearby 42m for silky smooth mobile FPS!)
        val treeShadowStep = if (graphicsQuality == GraphicsQuality.MEDIUM) 2 else 1
        for (i in visTrees.indices step treeShadowStep) {
            val t = visTrees[i]
            val tdx = t.x - playerX; val tdz = t.z - playerZ
            if (tdx * tdx + tdz * tdz > 44f * 44f) continue
            CityPropDrawer.drawTreeShadow(dc, t, worldTime, graphicsQuality)
        }

        // Poles (nearby 38m)
        for (sl in visStreetLights) {
            val dx = sl.x - playerX; val dz = sl.z - playerZ
            if (dx * dx + dz * dz <= 38f * 38f) CityPropDrawer.drawPoleShadow(dc, sl.x, sl.z, 5.5f, sl.dirX, sl.dirZ)
        }
        for (ts in visTrafficSignals) {
            val dx = ts.x - playerX; val dz = ts.z - playerZ
            if (dx * dx + dz * dz <= 38f * 38f) CityPropDrawer.drawPoleShadow(dc, ts.x, ts.z, 5.8f, ts.armDirX, ts.armDirZ)
        }

        // Parked & Moving Car, Bullet Bike, Army Bunker & Army Vehicle shadows (aligned to 3D terrain slope!)
        for (pc in visParkedCars) {
            val gY = if (pc === drivenCar) playerY else WorldMapData.getTerrainHeight(pc.x, pc.z)
            CityPropDrawer.drawCarShadow(dc, pc.x, pc.z, pc.yawDeg, groundY = gY, pitchDeg = pc.pitchDeg, rollDeg = pc.rollDeg)
        }
        for (bike in visBulletBikes) {
            val gY = if (bike === riddenBike) playerY else WorldMapData.getTerrainHeight(bike.x, bike.z)
            WorldAssetsDrawer.drawBulletBikeShadow(dc, bike, groundY = gY)
        }
        for (bk in visArmyBunkers) {
            WorldAssetsDrawer.drawArmyBunkerShadow(dc, bk)
        }
        for (av in visArmyVehicles) {
            WorldAssetsDrawer.drawArmyVehicleShadow(dc, av)
        }
        for (car in visMovingCars) {
            val gY = if (car === drivenAiCar) playerY else WorldMapData.getTerrainHeight(car.x, car.z)
            CityPropDrawer.drawCarShadow(dc, car.x, car.z, car.yawDeg, groundY = gY, pitchDeg = car.pitchDeg, rollDeg = car.rollDeg)
        }

        // NPC Men/Women & Player human shadows
        for (npc in visNpcs) {
            val dx = npc.x - playerX; val dz = npc.z - playerZ
            if (dx * dx + dz * dz <= 36f * 36f) {
                CityPropDrawer.drawHumanShadow(dc, npc.x, 0f, npc.z, npc.yawRad, npc.walkPhase, npc.pauseTimer <= 0f || npc.fleeTimer > 0f, npc.isWoman)
            }
        }
        if (drivenCar == null && drivenAiCar == null && riddenBike == null) {
            val rigged = glbPlayer
            if (rigged != null) {
                rigged.drawShadow(dc, playerX, playerY, playerZ, playerFacingYaw, shadowAlpha, slopePitchDeg = playerSlopePitchDeg, slopeRollDeg = playerSlopeRollDeg)
            } else {
                CityPropDrawer.drawHumanShadow(dc, playerX, playerY, playerZ, playerFacingYaw, walkCycle, isMovingNow, false)
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
    private var desiredFrameRate = 60f
    private var currentRenderScale = 1.0f
    private var nativeWidth = 0
    private var nativeHeight = 0

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(stencilChooser)
        preserveEGLContextOnPause = true
        cityRenderer = OpenGLCityRenderer(context, inputState, listener) { stencilChooser.hasStencil }
        setRenderer(cityRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            nativeWidth = w
            nativeHeight = h
            applyRenderResolutionScale(currentRenderScale)
        }
    }

    fun applyRenderResolutionScale(scale: Float) {
        val clamped = scale.coerceIn(0.65f, 1.0f)
        currentRenderScale = clamped
        if (nativeWidth <= 0 || nativeHeight <= 0) return
        if (clamped >= 0.99f) {
            runCatching { holder.setSizeFromLayout() }
        } else {
            val targetW = (nativeWidth * clamped).toInt().coerceAtLeast(640)
            val targetH = (nativeHeight * clamped).toInt().coerceAtLeast(360)
            runCatching { holder.setFixedSize(targetW, targetH) }
        }
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
        applyRenderResolutionScale(currentRenderScale)
    }
}
