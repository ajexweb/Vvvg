package com.example.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.SoundPool
import androidx.annotation.RawRes
import com.example.R
import com.example.game.model.ActiveVehicleMode
import com.example.game.model.CarCrashType
import com.example.game.model.GroundSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.random.Random

/**
 * Zero-Stutter Saini Simulator Master Audio Engine (Saini_Simulator_Master_SFX_2026).
 *
 * CRITICAL ZERO-LAG ARCHITECTURE:
 * - ALL SoundPool loading, SoundPool.play() calls, and MediaPlayer creation/updates execute
 *   strictly off the OpenGL & Main UI threads on a dedicated background audio executor thread.
 * - Live ambient loop updates coalesce via AtomicBoolean so the GL thread NEVER blocks even 0.1ms.
 * - Loop MediaPlayers are pre-warmed asynchronously on startup so entering a vehicle, approaching
 *   the ocean, or toggling rain NEVER causes a 1-second FPS drop or hitch!
 */
class GameAudioEngine(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val audioExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "SainiAudioEngine").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    @Volatile var musicVolume: Float = 0.65f
    @Volatile var sfxVolume: Float = 0.85f
    @Volatile var isMuted: Boolean = false
    @Volatile var ambientAudioEnabled: Boolean = true
    @Volatile private var isLobbyActive = false
    @Volatile private var isGameplayAudioActive = false
    @Volatile private var isReleased = false

    private val soundPool: SoundPool
    private val loadedSounds = ConcurrentHashMap.newKeySet<Int>()
    @Volatile private var lastFootstepId = -1

    // Coalesced snapshot for zero-allocation non-blocking live ambient updates
    private val liveUpdatePending = AtomicBoolean(false)
    @Volatile private var snapVehicleMode = ActiveVehicleMode.ON_FOOT
    @Volatile private var snapVehicleSpeedRatio = 0f
    @Volatile private var snapHeliDist = 999f
    @Volatile private var snapHeliTakeoff = 0f
    @Volatile private var snapOceanDist = 999f
    @Volatile private var snapRainIntensity = 0f
    @Volatile private var snapNightFactor = 0f
    @Volatile private var snapIsIndoors = false
    @Volatile private var snapNearTrees = 0f

    // 1. FOOTSTEPS (16 variants — subtle, realistic, random pick + 0.95..1.05 pitch jitter)
    @Volatile private var footAsphaltWalkIds: IntArray = IntArray(0)
    @Volatile private var footAsphaltRunIds: IntArray = IntArray(0)
    @Volatile private var footDirtGravelIds: IntArray = IntArray(0)
    @Volatile private var footGrassIds: IntArray = IntArray(0)
    @Volatile private var footIndoorTileIds: IntArray = IntArray(0)

    // 2. PLAYER MOVEMENT (4)
    @Volatile private var sfxPlayerJumpId: Int = 0
    @Volatile private var sfxPlayerLandSoftId: Int = 0
    @Volatile private var sfxPlayerLandHeavyId: Int = 0
    @Volatile private var sfxPlayerWallVaultId: Int = 0

    // 3. CAR DOORS & GATES (5 + glass slide door)
    @Volatile private var sfxCarDoorOpenId: Int = 0
    @Volatile private var sfxCarDoorCloseId: Int = 0
    @Volatile private var sfxCarCentralLockId: Int = 0
    @Volatile private var sfxIronGateOpenId: Int = 0
    @Volatile private var sfxIronGateCloseSlamId: Int = 0
    @Volatile private var sfxDoorSlideId: Int = 0

    // 4. CAR CRASH & BRAKES (4)
    @Volatile private var sfxCarHighSpeedBrakeCrashId: Int = 0
    @Volatile private var sfxCarCrashHeavyId: Int = 0
    @Volatile private var sfxCarCrashLightId: Int = 0
    @Volatile private var sfxCarEmergencyBrakeSkidId: Int = 0

    // 5. VEHICLES ENGINE & DRIVING (one-shots + horn + jet flyby)
    @Volatile private var sfxCarEngineStartId: Int = 0
    @Volatile private var sfxCarEngineAccelRevId: Int = 0
    @Volatile private var sfxCarHornId: Int = 0
    @Volatile private var sfxJetFlybyId: Int = 0

    // 6. MODERN UI & LOBBY (5)
    @Volatile private var sfxUiClickId: Int = 0
    @Volatile private var sfxUiSkinEquipId: Int = 0
    @Volatile private var sfxUiGameStartDeployId: Int = 0
    @Volatile private var sfxItemCollectCashId: Int = 0
    @Volatile private var sfxCityZoneDiscoveredId: Int = 0

    // 7. SEAMLESS LOOP TRACKS (Pre-warmed asynchronously off-thread!)
    private val lobbyThemeLoop = LoopTrack(R.raw.lobby_ambient_theme_loop, isAmbient = true)
    private val cityAmbienceLoop = LoopTrack(R.raw.city_open_world_ambience_loop, isAmbient = true)
    private val carEngineLoop = LoopTrack(R.raw.car_engine_idle_loop, isAmbient = false)
    private val bulletBikeLoop = LoopTrack(R.raw.sfx_bullet_bike_thump_loop, isAmbient = false)
    private val helicopterLoop = LoopTrack(R.raw.sfx_army_helicopter_loop, isAmbient = false)
    private val birdsLoop = LoopTrack(R.raw.sfx_env_birds_chirping, isAmbient = true)
    private val oceanLoop = LoopTrack(R.raw.sfx_env_ocean_waves_loop, isAmbient = true)
    private val rainLoop = LoopTrack(R.raw.sfx_env_rain_loop, isAmbient = true)

    private val gameplayLoops = listOf(
        cityAmbienceLoop,
        carEngineLoop,
        bulletBikeLoop,
        helicopterLoop,
        birdsLoop,
        oceanLoop,
        rainLoop
    )

    private val allLoops = listOf(lobbyThemeLoop) + gameplayLoops

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(12)
            .setAudioAttributes(attrs)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSounds.add(sampleId)
            }
        }

        // Load all SFX + pre-warm MediaPlayers asynchronously on background audio thread (ZERO startup/gameplay stutter!)
        runOnAudioThread {
            if (isReleased) return@runOnAudioThread
            footAsphaltWalkIds = intArrayOf(
                loadSfx(R.raw.foot_asphalt_walk_01),
                loadSfx(R.raw.foot_asphalt_walk_02),
                loadSfx(R.raw.foot_asphalt_walk_03),
                loadSfx(R.raw.foot_asphalt_walk_04)
            )
            footAsphaltRunIds = intArrayOf(
                loadSfx(R.raw.foot_asphalt_run_01),
                loadSfx(R.raw.foot_asphalt_run_02),
                loadSfx(R.raw.foot_asphalt_run_03),
                loadSfx(R.raw.foot_asphalt_run_04)
            )
            footDirtGravelIds = intArrayOf(
                loadSfx(R.raw.foot_dirt_gravel_01),
                loadSfx(R.raw.foot_dirt_gravel_02),
                loadSfx(R.raw.foot_dirt_gravel_03)
            )
            footGrassIds = intArrayOf(
                loadSfx(R.raw.foot_grass_01),
                loadSfx(R.raw.foot_grass_02),
                loadSfx(R.raw.foot_grass_03)
            )
            footIndoorTileIds = intArrayOf(
                loadSfx(R.raw.foot_indoor_tile_01),
                loadSfx(R.raw.foot_indoor_tile_02)
            )

            sfxPlayerJumpId = loadSfx(R.raw.player_jump)
            sfxPlayerLandSoftId = loadSfx(R.raw.player_land_soft)
            sfxPlayerLandHeavyId = loadSfx(R.raw.player_land_heavy)
            sfxPlayerWallVaultId = loadSfx(R.raw.player_wall_vault)

            sfxCarDoorOpenId = loadSfx(R.raw.car_door_open)
            sfxCarDoorCloseId = loadSfx(R.raw.car_door_close)
            sfxCarCentralLockId = loadSfx(R.raw.car_central_lock_unlock)
            sfxIronGateOpenId = loadSfx(R.raw.iron_gate_open)
            sfxIronGateCloseSlamId = loadSfx(R.raw.iron_gate_close_slam)
            sfxDoorSlideId = loadSfx(R.raw.sfx_door_slide_open)

            sfxCarHighSpeedBrakeCrashId = loadSfx(R.raw.car_high_speed_brake_crash)
            sfxCarCrashHeavyId = loadSfx(R.raw.car_crash_heavy_impact)
            sfxCarCrashLightId = loadSfx(R.raw.car_crash_light_bump)
            sfxCarEmergencyBrakeSkidId = loadSfx(R.raw.car_emergency_brake_skid)

            sfxCarEngineStartId = loadSfx(R.raw.car_engine_start)
            sfxCarEngineAccelRevId = loadSfx(R.raw.car_engine_accelerate_rev)
            sfxCarHornId = loadSfx(R.raw.car_horn)
            sfxJetFlybyId = loadSfx(R.raw.sfx_airport_jet_flyby)

            sfxUiClickId = loadSfx(R.raw.ui_click_tactile)
            sfxUiSkinEquipId = loadSfx(R.raw.ui_skin_equip)
            sfxUiGameStartDeployId = loadSfx(R.raw.ui_game_start_deploy)
            sfxItemCollectCashId = loadSfx(R.raw.item_collect_cash)
            sfxCityZoneDiscoveredId = loadSfx(R.raw.city_zone_discovered)

            // Pre-warm primary loop players off-thread so starting gameplay never stalls
            lobbyThemeLoop.prewarm()
            cityAmbienceLoop.prewarm()
            carEngineLoop.prewarm()
            bulletBikeLoop.prewarm()
        }
    }

    private fun loadSfx(@RawRes resId: Int): Int = runCatching {
        soundPool.load(appContext, resId, 1)
    }.getOrDefault(0)

    private inline fun runOnAudioThread(crossinline block: () -> Unit) {
        if (isReleased) return
        runCatching {
            audioExecutor.execute {
                if (!isReleased) block()
            }
        }
    }

    // -------------------------------------------------------------------------
    // MASTER MUTE & VOLUME SYNC
    // -------------------------------------------------------------------------

    fun applyAudioSettings(musicVol: Float, sfxVol: Float, muted: Boolean, ambientEnabled: Boolean = true) {
        musicVolume = musicVol.coerceIn(0f, 1f)
        sfxVolume = sfxVol.coerceIn(0f, 1f)
        isMuted = muted
        ambientAudioEnabled = ambientEnabled

        runOnAudioThread {
            if (isMuted) {
                allLoops.forEach { it.pause() }
            } else if (isLobbyActive) {
                lobbyThemeLoop.update(targetVolume = musicVolume * 0.36f, pitchRate = 1.0f, fadeRate = 1.0f)
            }
        }
    }

    // -------------------------------------------------------------------------
    // 7. AMBIENCE & LOBBY THEME LIFECYCLE
    // -------------------------------------------------------------------------

    fun startLobbyMusic() {
        isGameplayAudioActive = false
        isLobbyActive = true
        runOnAudioThread {
            gameplayLoops.forEach { it.stopAndReset() }
            if (!isMuted && musicVolume > 0.01f) {
                lobbyThemeLoop.update(targetVolume = musicVolume * 0.36f, pitchRate = 1.0f, fadeRate = 1.0f)
            } else {
                lobbyThemeLoop.pause()
            }
        }
    }

    fun stopLobbyMusic() {
        isLobbyActive = false
        runOnAudioThread { lobbyThemeLoop.stopAndReset() }
    }

    fun startGameplayAudio() {
        isLobbyActive = false
        isGameplayAudioActive = true
        runOnAudioThread {
            lobbyThemeLoop.stopAndReset()
            // Pre-warm remaining world loops lazily in background
            helicopterLoop.prewarm()
            birdsLoop.prewarm()
            oceanLoop.prewarm()
            rainLoop.prewarm()
        }
    }

    fun pauseGameplayLoops() {
        isGameplayAudioActive = false
        runOnAudioThread { gameplayLoops.forEach { it.pause() } }
    }

    fun resumeGameplayLoops() {
        if (isReleased) return
        isLobbyActive = false
        isGameplayAudioActive = true
        runOnAudioThread { lobbyThemeLoop.stopAndReset() }
    }

    fun stopGameplayLoops() {
        isGameplayAudioActive = false
        runOnAudioThread { gameplayLoops.forEach { it.stopAndReset() } }
    }

    // -------------------------------------------------------------------------
    // LIVE CONTINUOUS AUDIO MIXER (NON-BLOCKING COALESCED SNAPSHOT!)
    // -------------------------------------------------------------------------

    fun updateLiveAudio(
        vehicleMode: ActiveVehicleMode,
        vehicleSpeedRatio: Float,
        heliDistMeters: Float,
        heliTakeoffFactor: Float,
        oceanDistMeters: Float,
        rainIntensity: Float,
        nightFactor: Float,
        isIndoors: Boolean,
        nearTreesFactor: Float
    ) {
        if (isReleased || !isGameplayAudioActive) return
        snapVehicleMode = vehicleMode
        snapVehicleSpeedRatio = vehicleSpeedRatio
        snapHeliDist = heliDistMeters
        snapHeliTakeoff = heliTakeoffFactor
        snapOceanDist = oceanDistMeters
        snapRainIntensity = rainIntensity
        snapNightFactor = nightFactor
        snapIsIndoors = isIndoors
        snapNearTrees = nearTreesFactor

        // Only enqueue if no update is currently pending -> ZERO queue buildup & ZERO GL stall!
        if (liveUpdatePending.compareAndSet(false, true)) {
            runOnAudioThread {
                liveUpdatePending.set(false)
                processLiveAudioSnapshot()
            }
        }
    }

    private fun processLiveAudioSnapshot() {
        if (isReleased || !isGameplayAudioActive) return
        if (isMuted) {
            gameplayLoops.forEach { it.pause() }
            return
        }

        val sfxMaster = sfxVolume.coerceIn(0f, 1f)
        val envMaster = if (ambientAudioEnabled) (musicVolume * 0.65f + sfxVolume * 0.35f).coerceIn(0f, 1f) else 0f
        val isIndoors = snapIsIndoors
        val indoorDamp = if (isIndoors) 0.28f else 1.0f

        // 0. Open-World City Ambience Loop
        val cityAmbVol = 0.28f * envMaster * (if (isIndoors) 0.24f else 1.0f)
        cityAmbienceLoop.update(targetVolume = cityAmbVol, pitchRate = 1.0f, fadeRate = 0.25f)

        // 1. Car Engine Idle Loop
        if (snapVehicleMode == ActiveVehicleMode.CAR) {
            val vol = (0.34f + snapVehicleSpeedRatio * 0.46f) * sfxMaster
            val pitch = (0.86f + snapVehicleSpeedRatio * 0.72f).coerceIn(0.80f, 1.65f)
            carEngineLoop.update(targetVolume = vol, pitchRate = pitch, fadeRate = 0.45f)
        } else {
            carEngineLoop.update(targetVolume = 0f, pitchRate = 1.0f, fadeRate = 0.75f)
        }

        // 2. Royal Enfield Bullet 350 Thump Loop
        if (snapVehicleMode == ActiveVehicleMode.BULLET_BIKE) {
            val vol = (0.40f + snapVehicleSpeedRatio * 0.46f) * sfxMaster
            val pitch = (0.84f + snapVehicleSpeedRatio * 0.80f).coerceIn(0.75f, 1.72f)
            bulletBikeLoop.update(targetVolume = vol, pitchRate = pitch, fadeRate = 0.45f)
        } else {
            bulletBikeLoop.update(targetVolume = 0f, pitchRate = 1.0f, fadeRate = 0.75f)
        }

        // 3. Army Base Helicopter Rotor Loop
        val heliRange = 135f
        val heliAtten = ((heliRange - snapHeliDist) / heliRange).coerceIn(0f, 1f)
        val heliVol = (heliAtten * heliAtten) * (0.42f + snapHeliTakeoff * 0.44f) * sfxMaster * indoorDamp
        val heliPitch = (0.88f + snapHeliTakeoff * 0.32f).coerceIn(0.82f, 1.32f)
        helicopterLoop.update(targetVolume = heliVol, pitchRate = heliPitch, fadeRate = 0.25f)

        // 4. Ocean Waves Loop
        val oceanRange = 88f
        val oceanAtten = ((oceanRange - snapOceanDist) / oceanRange).coerceIn(0f, 1f)
        val oceanVol = (oceanAtten * oceanAtten) * 0.66f * envMaster * indoorDamp
        oceanLoop.update(targetVolume = oceanVol, pitchRate = 1.0f, fadeRate = 0.22f)

        // 5. Rain Loop
        val rainVol = snapRainIntensity.coerceIn(0f, 1f) * 0.66f * envMaster * (if (isIndoors) 0.30f else 1.0f)
        rainLoop.update(targetVolume = rainVol, pitchRate = 1.0f, fadeRate = 0.22f)

        // 6. Daytime Birds Chirping Loop
        val dayFactor = (1f - snapNightFactor * 1.4f).coerceIn(0f, 1f)
        val dryFactor = (1f - snapRainIntensity * 1.5f).coerceIn(0f, 1f)
        val openTreeWeight = (0.22f + snapNearTrees.coerceIn(0f, 1f) * 0.78f)
        val birdsVol = if (isIndoors) 0f else dayFactor * dryFactor * openTreeWeight * 0.38f * envMaster
        birdsLoop.update(targetVolume = birdsVol, pitchRate = 1.0f, fadeRate = 0.16f)
    }

    // -------------------------------------------------------------------------
    // 1. FOOTSTEPS (Subtle, Low-Volume, Randomized Variants + 0.95..1.05 Pitch)
    // -------------------------------------------------------------------------

    fun playFootstep(isSprint: Boolean, surface: GroundSurface) {
        if (!canPlaySfx()) return
        val pool = when (surface) {
            GroundSurface.ASPHALT -> if (isSprint) footAsphaltRunIds else footAsphaltWalkIds
            GroundSurface.DIRT_GRAVEL -> footDirtGravelIds
            GroundSurface.GRASS -> footGrassIds
            GroundSurface.INDOOR_TILE -> footIndoorTileIds
        }
        if (pool.isEmpty()) return
        val soundId = pickRandomVariant(pool)
        val baseGain = when (surface) {
            GroundSurface.ASPHALT -> if (isSprint) 0.36f else 0.24f
            GroundSurface.DIRT_GRAVEL -> if (isSprint) 0.38f else 0.26f
            GroundSurface.GRASS -> if (isSprint) 0.34f else 0.23f
            GroundSurface.INDOOR_TILE -> if (isSprint) 0.36f else 0.25f
        }
        val pitchJitter = 0.95f + Random.nextFloat() * 0.10f
        playPoolSoundAsync(soundId, baseGain * sfxVolume, pitchJitter)
    }

    private fun pickRandomVariant(variants: IntArray): Int {
        if (variants.size == 1) return variants[0]
        var picked = variants[Random.nextInt(variants.size)]
        if (picked == lastFootstepId) {
            picked = variants[(variants.indexOf(picked) + 1) % variants.size]
        }
        lastFootstepId = picked
        return picked
    }

    // -------------------------------------------------------------------------
    // 2. PLAYER MOVEMENT
    // -------------------------------------------------------------------------

    fun playJump() {
        if (!canPlaySfx()) return
        playPoolSoundAsync(sfxPlayerJumpId, 0.66f * sfxVolume, 0.97f + Random.nextFloat() * 0.06f)
    }

    fun playPlayerLand(heavy: Boolean) {
        if (!canPlaySfx()) return
        val id = if (heavy) sfxPlayerLandHeavyId else sfxPlayerLandSoftId
        val gain = if (heavy) 0.76f else 0.45f
        playPoolSoundAsync(id, gain * sfxVolume, 0.96f + Random.nextFloat() * 0.08f)
    }

    fun playWallVault() {
        if (!canPlaySfx()) return
        playPoolSoundAsync(sfxPlayerWallVaultId, 0.70f * sfxVolume, 1.0f)
    }

    // -------------------------------------------------------------------------
    // 3. CAR DOORS & GATES
    // -------------------------------------------------------------------------

    fun playCarEnterSequence() {
        if (!canPlaySfx()) return
        playPoolSoundAsync(sfxCarCentralLockId, 0.60f * sfxVolume, 1.04f)
        playPoolSoundAsync(sfxCarDoorOpenId, 0.70f * sfxVolume, 1.0f)
        scope.launch {
            delay(300L)
            if (canPlaySfx()) playPoolSoundAsync(sfxCarDoorCloseId, 0.72f * sfxVolume, 1.0f)
            delay(180L)
            if (canPlaySfx()) playPoolSoundAsync(sfxCarEngineStartId, 0.78f * sfxVolume, 1.0f)
        }
    }

    fun playCarExitSequence() {
        if (!canPlaySfx()) return
        playPoolSoundAsync(sfxCarDoorOpenId, 0.68f * sfxVolume, 0.98f)
        scope.launch {
            delay(280L)
            if (canPlaySfx()) playPoolSoundAsync(sfxCarDoorCloseId, 0.72f * sfxVolume, 0.98f)
            delay(210L)
            if (canPlaySfx()) playPoolSoundAsync(sfxCarCentralLockId, 0.58f * sfxVolume, 0.96f)
        }
    }

    fun playIronGate(opening: Boolean) {
        if (!canPlaySfx()) return
        val id = if (opening) sfxIronGateOpenId else sfxIronGateCloseSlamId
        val gain = if (opening) 0.70f else 0.78f
        playPoolSoundAsync(id, gain * sfxVolume, 1.0f)
    }

    fun playDoorSound(opening: Boolean) {
        if (!canPlaySfx()) return
        playPoolSoundAsync(sfxDoorSlideId, 0.66f * sfxVolume, if (opening) 1.02f else 0.90f)
    }

    // -------------------------------------------------------------------------
    // 4. CAR CRASH & BRAKES
    // -------------------------------------------------------------------------

    fun playCarCrash(type: CarCrashType) {
        if (!canPlaySfx()) return
        when (type) {
            CarCrashType.HIGH_SPEED_BRAKE_CRASH ->
                playPoolSoundAsync(sfxCarHighSpeedBrakeCrashId, 0.90f * sfxVolume, 1.0f)
            CarCrashType.HEAVY_IMPACT ->
                playPoolSoundAsync(sfxCarCrashHeavyId, 0.86f * sfxVolume, 0.97f + Random.nextFloat() * 0.06f)
            CarCrashType.LIGHT_BUMP ->
                playPoolSoundAsync(sfxCarCrashLightId, 0.64f * sfxVolume, 0.95f + Random.nextFloat() * 0.10f)
        }
    }

    fun playCarEmergencyBrakeSkid() {
        if (!canPlaySfx()) return
        playPoolSoundAsync(sfxCarEmergencyBrakeSkidId, 0.74f * sfxVolume, 0.96f + Random.nextFloat() * 0.08f)
    }

    // -------------------------------------------------------------------------
    // 5. VEHICLES ENGINE & DRIVING
    // -------------------------------------------------------------------------

    fun playCarAccelerateRev() {
        if (!canPlaySfx()) return
        playPoolSoundAsync(sfxCarEngineAccelRevId, 0.70f * sfxVolume, 0.97f + Random.nextFloat() * 0.08f)
    }

    fun playCarHorn() {
        if (!canPlaySfx()) return
        playPoolSoundAsync(sfxCarHornId, 0.82f * sfxVolume, 1.0f)
    }

    fun playJetFlyby(distanceMeters: Float) {
        if (!canPlaySfx()) return
        val maxRange = 220f
        val atten = ((maxRange - distanceMeters) / maxRange).coerceIn(0f, 1f)
        if (atten <= 0.04f) return
        playPoolSoundAsync(sfxJetFlybyId, (0.20f + atten * 0.74f) * sfxVolume, 1.0f)
    }

    // -------------------------------------------------------------------------
    // 6. MODERN UI & LOBBY (Zero-Latency Off-Thread Preloaded WAVs)
    // -------------------------------------------------------------------------

    fun playClick() {
        if (!canPlaySfx()) return
        playPoolSoundAsync(sfxUiClickId, 0.60f * sfxVolume, 0.98f + Random.nextFloat() * 0.04f)
    }

    fun playSkinEquip() {
        if (!canPlaySfx()) return
        playPoolSoundAsync(sfxUiSkinEquipId, 0.76f * sfxVolume, 1.0f)
    }

    fun playGameStartDeploy() {
        if (!canPlaySfx()) return
        playPoolSoundAsync(sfxUiGameStartDeployId, 0.84f * sfxVolume, 1.0f)
    }

    fun playPickup() {
        if (!canPlaySfx()) return
        playPoolSoundAsync(sfxItemCollectCashId, 0.76f * sfxVolume, 1.0f)
    }

    fun playZoneDiscovered() {
        if (!canPlaySfx()) return
        playPoolSoundAsync(sfxCityZoneDiscoveredId, 0.82f * sfxVolume, 1.0f)
    }

    // -------------------------------------------------------------------------
    // NON-BLOCKING INTERNAL DISPATCH
    // -------------------------------------------------------------------------

    private fun canPlaySfx(): Boolean = !isReleased && !isMuted && sfxVolume > 0.01f

    private fun playPoolSoundAsync(soundId: Int, volume: Float, rate: Float) {
        if (soundId <= 0 || volume <= 0.004f || !loadedSounds.contains(soundId)) return
        val clamped = volume.coerceIn(0f, 1f)
        val clampedRate = rate.coerceIn(0.5f, 2.0f)
        runOnAudioThread {
            runCatching {
                soundPool.play(soundId, clamped, clamped, 1, 0, clampedRate)
            }
        }
    }

    fun release() {
        isReleased = true
        isLobbyActive = false
        isGameplayAudioActive = false
        scope.cancel()
        runCatching {
            audioExecutor.execute {
                allLoops.forEach { it.release() }
                runCatching { soundPool.release() }
            }
            audioExecutor.shutdown()
        }
    }

    /**
     * Background-thread-only looping MediaPlayer wrapper.
     * Pre-warms off-thread and rate-limits PlaybackParams updates so Android AudioFlinger
     * never stalls the graphics pipeline.
     */
    private inner class LoopTrack(
        @RawRes private val rawResId: Int,
        private val isAmbient: Boolean
    ) {
        private var player: MediaPlayer? = null
        private var currentVolume: Float = 0f
        private var lastAppliedVolume: Float = -1f
        private var lastAppliedPitch: Float = 1f
        private var lastPitchUpdateMs: Long = 0L

        fun prewarm() {
            if (!isReleased) ensureCreated()
        }

        fun update(targetVolume: Float, pitchRate: Float, fadeRate: Float) {
            if (isReleased || isMuted) {
                pause()
                return
            }
            val clampedTarget = targetVolume.coerceIn(0f, 1f)
            currentVolume += (clampedTarget - currentVolume) * fadeRate.coerceIn(0.05f, 1f)

            if (currentVolume < 0.012f && clampedTarget < 0.012f) {
                currentVolume = 0f
                val mp = player
                if (mp != null) {
                    runCatching {
                        if (mp.isPlaying) {
                            mp.setVolume(0f, 0f)
                            lastAppliedVolume = 0f
                            mp.pause()
                        }
                    }
                }
                return
            }

            val mp = ensureCreated() ?: return
            runCatching {
                if (abs(currentVolume - lastAppliedVolume) > 0.015f) {
                    mp.setVolume(currentVolume, currentVolume)
                    lastAppliedVolume = currentVolume
                }
                if (!mp.isPlaying) {
                    mp.start()
                }
                val now = System.currentTimeMillis()
                if (!isAmbient && abs(pitchRate - lastAppliedPitch) > 0.07f && (now - lastPitchUpdateMs) >= 220L) {
                    lastAppliedPitch = pitchRate
                    lastPitchUpdateMs = now
                    val params = PlaybackParams().setPitch(pitchRate).setSpeed(pitchRate)
                    mp.playbackParams = params
                }
            }
        }

        fun pause() {
            val mp = player ?: return
            runCatching {
                if (mp.isPlaying) mp.pause()
            }
        }

        fun stopAndReset() {
            currentVolume = 0f
            lastAppliedVolume = 0f
            lastAppliedPitch = 1f
            val mp = player ?: return
            runCatching {
                if (mp.isPlaying) mp.pause()
                mp.seekTo(0)
                mp.setVolume(0f, 0f)
            }
        }

        fun release() {
            currentVolume = 0f
            val mp = player
            player = null
            if (mp != null) {
                runCatching {
                    if (mp.isPlaying) mp.stop()
                    mp.release()
                }
            }
        }

        private fun ensureCreated(): MediaPlayer? {
            if (player != null) return player
            val created = runCatching {
                MediaPlayer.create(appContext, rawResId)?.apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(
                                if (isAmbient) AudioAttributes.CONTENT_TYPE_MUSIC
                                else AudioAttributes.CONTENT_TYPE_SONIFICATION
                            )
                            .build()
                    )
                    isLooping = true
                    setVolume(0f, 0f)
                }
            }.getOrNull()
            player = created
            return created
        }
    }
}
