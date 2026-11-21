package com.example.game.engine

import android.opengl.GLES20
import android.opengl.Matrix
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Modular 3D renderer for the custom world assets:
 * - Royal Enfield Bullet motorcycles (tex_bullet_bike_tank)
 * - 3D crossed alpha-cutout grass clumps (tex_3d_grass_blades)
 * - Saini Army Base: barbed-wire fences (tex_army_barbed_fence), concrete bunkers
 *   (tex_army_bunker_concrete), helipads (tex_army_helipad), camo helicopter & army trucks (tex_army_camouflage)
 * - Saini International Airport: runway (tex_airport_runway), glass ATC tower (tex_building_glass_facade),
 *   hangar, and parked jetliner
 * - Animated feathered birds (tex_bird_feathers) & drifting volumetric clouds (tex_env_cloud_puff)
 */
object WorldAssetsDrawer {

    private val mBase = FloatArray(16)

    // -------------------------------------------------------------------------
    // 1. ROYAL ENFIELD BULLET MOTORCYCLE (tex_bullet_bike_tank)
    // -------------------------------------------------------------------------

    fun drawBulletBike(dc: GlDrawContext, bike: BulletBikeProp, isRidden: Boolean = false) {
        val a = dc.assets
        val white = a.texWhite
        val cyl = a.cylinderMesh
        val cube = a.cubeMesh
        val sph = a.sphereMesh

        Matrix.setIdentityM(mBase, 0)
        Matrix.translateM(mBase, 0, bike.x, 0.02f, bike.z)
        Matrix.rotateM(mBase, 0, bike.yawDeg, 0f, 1f, 0f)
        // Parked kickstand lean when dismounted; upright when ridden!
        if (!isRidden) {
            Matrix.rotateM(mBase, 0, -6.5f, 0f, 0f, 1f)
        }

        // Front & rear rubber tires mapped with tex_vehicle_tire_tread + chrome spoked rims
        for (wz in floatArrayOf(0.76f, -0.74f)) {
            drawWheel(dc, mBase, 0f, 0.34f, wz, 0.68f, 0.15f, 0.82f, 0.84f, 0.86f, 0.16f, 14f, useTread = true, spinDeg = bike.wheelAngleDeg)
            drawWheel(dc, mBase, 0f, 0.34f, wz, 0.44f, 0.17f, 0.84f, 0.87f, 0.92f, 0.85f, 44f, useTread = false, spinDeg = bike.wheelAngleDeg)
        }

        // Tubular black steel chassis + lower engine cradle
        drawLocal(dc, mBase, 0f, 0.50f, 0.02f, 0.24f, 0.34f, 0.86f, 0f, 0f, white, 1f, 1f, 0.11f, 0.12f, 0.14f, 0f, 0.35f, 22f, 0.12f, cube)
        // Chrome air-cooled V-engine cylinder block & gearbox case
        drawLocal(dc, mBase, 0f, 0.50f, 0.06f, 0.36f, 0.36f, 0.38f, 0f, 0f, white, 1f, 1f, 0.78f, 0.80f, 0.84f, 0f, 0.90f, 52f, 0.40f, cyl)
        drawLocal(dc, mBase, 0f, 0.36f, -0.05f, 0.38f, 0.22f, 0.42f, 0f, 0f, white, 1f, 1f, 0.72f, 0.75f, 0.80f, 0f, 0.85f, 44f, 0.35f, cube)

        // Iconic Bullet Teardrop Fuel Tank mapped with tex_bullet_bike_tank (Black + Dual Gold Pinstripes!)
        drawLocal(
            dc, mBase, 0f, 0.85f, 0.22f,
            0.38f, 0.30f, 0.64f,
            -6f, 0f, a.texBulletBikeTank, 1f, 1f,
            1f, 1f, 1f, 0.02f, 0.95f, 64f, 0.48f, sph
        )
        // Chrome fuel filler cap
        drawLocal(dc, mBase, 0f, 1.00f, 0.28f, 0.08f, 0.03f, 0.08f, 0f, 0f, white, 1f, 1f, 0.90f, 0.92f, 0.95f, 0f, 0.95f, 56f, 0.45f, cyl)

        // Ribbed Classic Leather Dual Seat
        drawLocal(dc, mBase, 0f, 0.79f, -0.34f, 0.32f, 0.15f, 0.68f, 4f, 0f, white, 1f, 1f, 0.14f, 0.11f, 0.10f, 0f, 0.24f, 16f, 0.05f, cube)
        // Rear chrome grab rail & red brake taillight
        drawLocal(dc, mBase, 0f, 0.74f, -0.86f, 0.20f, 0.10f, 0.16f, 0f, 0f, white, 1f, 1f, 0.94f, 0.14f, 0.14f, 0.70f, 0.6f, 24f, 0.15f, cube)

        // Front & rear mudguard fenders (black + chrome trim)
        drawLocal(dc, mBase, 0f, 0.66f,  0.74f, 0.20f, 0.07f, 0.48f, -18f, 0f, white, 1f, 1f, 0.10f, 0.10f, 0.12f, 0f, 0.75f, 42f, 0.30f, cube)
        drawLocal(dc, mBase, 0f, 0.68f, -0.72f, 0.21f, 0.08f, 0.50f,  18f, 0f, white, 1f, 1f, 0.10f, 0.10f, 0.12f, 0f, 0.75f, 42f, 0.30f, cube)

        // Twin Front Telescopic Chrome Forks
        for (fx in floatArrayOf(-0.11f, 0.11f)) {
            drawLocal(dc, mBase, fx, 0.70f, 0.64f, 0.055f, 0.78f, 0.055f, -22f, 0f, white, 1f, 1f, 0.86f, 0.88f, 0.92f, 0f, 0.92f, 56f, 0.42f, cyl)
        }

        // Wide Swept Chrome Handlebars + Black Grips
        drawLocal(dc, mBase, 0f, 1.07f, 0.48f, 0.74f, 0.04f, 0.04f, 0f, 0f, white, 1f, 1f, 0.88f, 0.90f, 0.94f, 0f, 0.95f, 56f, 0.42f, cube)
        drawLocal(dc, mBase, -0.36f, 1.07f, 0.46f, 0.15f, 0.055f, 0.055f, 0f, 0f, white, 1f, 1f, 0.10f, 0.10f, 0.11f, 0f, 0.15f, 12f, 0.02f, cube)
        drawLocal(dc, mBase,  0.36f, 1.07f, 0.46f, 0.15f, 0.055f, 0.055f, 0f, 0f, white, 1f, 1f, 0.10f, 0.10f, 0.11f, 0f, 0.15f, 12f, 0.02f, cube)

        // Iconic Round Bullet Chrome Headlamp Casquette + Glowing Lens
        drawLocal(dc, mBase, 0f, 0.98f, 0.64f, 0.22f, 0.22f, 0.24f, 0f, 0f, white, 1f, 1f, 0.84f, 0.86f, 0.90f, 0f, 0.92f, 56f, 0.44f, sph)
        drawLocal(dc, mBase, 0f, 0.98f, 0.74f, 0.17f, 0.17f, 0.06f, 0f, 0f, white, 1f, 1f, 1.0f, 0.96f, 0.82f, 0.85f, 0.8f, 36f, 0.25f, sph)

        // Iconic Long Chrome Peashooter Exhaust Silencer Pipe (right side)
        drawLocal(dc, mBase, -0.21f, 0.30f, -0.28f, 0.09f, 0.09f, 1.18f, 3f, 0f, white, 1f, 1f, 0.88f, 0.90f, 0.94f, 0f, 0.96f, 64f, 0.48f, cube)
    }

    fun drawBulletBikeShadow(dc: GlDrawContext, bike: BulletBikeProp) {
        val white = dc.assets.texWhite
        dc.bindShadowVbo(dc.assets.cubeMesh)
        Matrix.setIdentityM(dc.modelMatrix, 0)
        Matrix.translateM(dc.modelMatrix, 0, bike.x, 0.56f, bike.z)
        Matrix.rotateM(dc.modelMatrix, 0, bike.yawDeg, 0f, 1f, 0f)
        Matrix.scaleM(dc.modelMatrix, 0, 0.52f, 1.02f, 1.98f)
        dc.commitShadow(white, dc.assets.cubeMesh.vertexCount)
    }

    // -------------------------------------------------------------------------
    // 2. 3D CROSSED ALPHA-CUTOUT GRASS TUFTS (tex_3d_grass_blades)
    // -------------------------------------------------------------------------

    fun drawGrassTufts(dc: GlDrawContext, tufts: List<GrassTuftProp>, worldTime: Float, step: Int) {
        if (tufts.isEmpty()) return
        val mesh = dc.assets.crossedGrassMesh
        val tex = dc.assets.tex3dGrassBlades

        GLES20.glDisable(GLES20.GL_CULL_FACE)
        dc.bindVbo(mesh)
        for (i in tufts.indices step step) {
            val g = tufts[i]
            val sway = sin(worldTime * 2.1f + g.seed * 0.7f) * 5.2f
            val shade = 0.88f + (g.seed % 4) * 0.045f
            dc.drawMeshTransformed(
                g.x, 0.01f, g.z,
                1.05f * g.scale, 0.78f * g.scale, 1.05f * g.scale,
                g.yawDeg, sway * 0.6f, sway,
                tex, 1f, 1f,
                shade * 0.96f, shade * 1.04f, shade * 0.92f, 1f,
                0f, 0.06f, 8f, 0f,
                mesh
            )
        }
        GLES20.glEnable(GLES20.GL_CULL_FACE)
    }

    // -------------------------------------------------------------------------
    // 3. SAINI ARMY BASE: BARBED FENCE, CONCRETE BUNKERS, HELIPAD, CHOPPER & CAMO VEHICLES
    // -------------------------------------------------------------------------

    fun drawBarbedFence(dc: GlDrawContext, fence: BarbedFenceSegment) {
        val a = dc.assets
        val yaw = if (fence.isVertical) 90f else 0f
        val fenceHeight = 3.15f
        val uRepeat = (fence.length / 3.2f).coerceAtLeast(1f)

        // Galvanized Steel Support Posts along fence
        dc.bindVbo(a.cylinderMesh)
        val postSpacing = 4.5f
        val postCount = (fence.length / postSpacing).toInt().coerceAtLeast(2)
        for (i in 0..postCount) {
            val frac = i.toFloat() / postCount - 0.5f
            val px = if (fence.isVertical) fence.x else fence.x + frac * fence.length
            val pz = if (fence.isVertical) fence.z + frac * fence.length else fence.z
            dc.drawCylinder(px, fenceHeight * 0.48f, pz, 0.14f, fenceHeight * 0.96f, 0.14f, a.texWhite, 1f, 1f, 0.36f, 0.39f, 0.42f, 0f, 0.55f, 26f, 0.16f)
        }

        // Alpha-Cutout Chain-Link + Coiled Barbed Wire Panel (tex_army_barbed_fence)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        dc.bindVbo(a.verticalQuadMesh)
        dc.drawMeshTransformed(
            fence.x, fenceHeight * 0.5f, fence.z,
            fence.length, fenceHeight, 1f,
            yaw, 0f, 0f,
            a.texArmyBarbedFence, uRepeat, 1f,
            0.92f, 0.94f, 0.96f, 1f,
            0f, 0.45f, 24f, 0.12f,
            a.verticalQuadMesh
        )
        GLES20.glEnable(GLES20.GL_CULL_FACE)
    }

    fun drawArmyBunker(dc: GlDrawContext, bk: ArmyBunkerProp) {
        val a = dc.assets
        val tex = a.texArmyBunkerConcrete
        dc.bindVbo(a.cubeMesh)

        // Reinforced Concrete Base Plinth
        dc.drawBox(bk.x, 0.35f, bk.z, bk.width + 0.7f, 0.7f, bk.depth + 0.7f, tex, 3f, 0.6f, 0.78f, 0.79f, 0.76f, 0f, 0.10f, 10f, 0.02f)
        // Main Thick Concrete Bunker Body
        dc.drawBox(bk.x, bk.height * 0.5f, bk.z, bk.width, bk.height, bk.depth, tex, (bk.width / 3.5f).coerceAtLeast(1.5f), (bk.height / 2.2f).coerceAtLeast(1.2f), 0.90f, 0.90f, 0.87f, 0f, 0.12f, 12f, 0.03f)
        // Overhanging Blast-Proof Concrete Roof Cap
        dc.drawBox(bk.x, bk.height + 0.32f, bk.z, bk.width + 0.9f, 0.65f, bk.depth + 0.9f, tex, 3f, 0.5f, 0.74f, 0.75f, 0.72f, 0f, 0.12f, 12f, 0.03f)
        // Recessed Dark Armored Viewing / Firing Slits on East & South Walls
        dc.drawBox(bk.x + bk.width * 0.5f + 0.04f, bk.height * 0.68f, bk.z, 0.10f, 0.42f, bk.depth * 0.58f, a.texWhite, 1f, 1f, 0.08f, 0.09f, 0.10f, 0f, 0.2f, 12f, 0.05f)
        dc.drawBox(bk.x, bk.height * 0.68f, bk.z + bk.depth * 0.5f + 0.04f, bk.width * 0.58f, 0.42f, 0.10f, a.texWhite, 1f, 1f, 0.08f, 0.09f, 0.10f, 0f, 0.2f, 12f, 0.05f)
        // Heavy Steel Blast Door facing East
        dc.drawBox(bk.x + bk.width * 0.5f + 0.06f, 1.35f, bk.z + bk.depth * 0.24f, 0.12f, 2.5f, 1.6f, a.texArmyCamo, 1f, 1f, 0.85f, 0.88f, 0.82f, 0f, 0.25f, 18f, 0.06f)

        // Roof Comms Mast & Red Aviation Beacon
        dc.bindVbo(a.cylinderMesh)
        dc.drawCylinder(bk.x - bk.width * 0.25f, bk.height + 2.2f, bk.z - bk.depth * 0.2f, 0.14f, 3.6f, 0.14f, a.texWhite, 1f, 1f, 0.34f, 0.36f, 0.38f, 0f, 0.5f, 24f, 0.14f)
        dc.bindVbo(a.sphereMesh)
        dc.drawMeshTransformed(
            bk.x - bk.width * 0.25f, bk.height + 4.05f, bk.z - bk.depth * 0.2f,
            0.30f, 0.30f, 0.30f, 0f, 0f, 0f,
            a.texWhite, 1f, 1f, 1.0f, 0.16f, 0.14f, 1f, 0.90f, 0.4f, 16f, 0.1f, a.sphereMesh
        )
    }

    fun drawArmyBunkerShadow(dc: GlDrawContext, bk: ArmyBunkerProp) {
        dc.bindShadowVbo(dc.assets.cubeMesh)
        Matrix.setIdentityM(dc.modelMatrix, 0)
        Matrix.translateM(dc.modelMatrix, 0, bk.x, bk.height * 0.5f, bk.z)
        Matrix.scaleM(dc.modelMatrix, 0, bk.width + 0.4f, bk.height + 0.5f, bk.depth + 0.4f)
        dc.commitShadow(dc.assets.texWhite, dc.assets.cubeMesh.vertexCount)
    }

    fun drawHelipadAndChopper(
        dc: GlDrawContext,
        hp: HelipadProp,
        worldTime: Float,
        chopperLiftY: Float = 0f,
        chopperRotorRate: Float = 1f
    ) {
        val a = dc.assets
        // Concrete Helipad Deck with Yellow Circle & Crisp White 'H' (tex_army_helipad)
        dc.bindVbo(a.quadMesh)
        dc.drawQuad(
            hp.x, 0.042f, hp.z,
            hp.size, hp.size,
            a.texArmyHelipad, 1f, 1f,
            0.98f, 0.98f, 0.98f,
            0.03f, 0.20f, 18f, 0.05f
        )

        // Glowing Amber Corner Perimeter Lights
        dc.bindVbo(a.sphereMesh)
        val off = hp.size * 0.47f
        val pulse = 0.65f + 0.35f * sin(worldTime * 3.5f)
        for (sx in floatArrayOf(-off, off)) {
            for (sz in floatArrayOf(-off, off)) {
                dc.drawMeshTransformed(
                    hp.x + sx, 0.16f, hp.z + sz,
                    0.28f, 0.18f, 0.28f, 0f, 0f, 0f,
                    a.texWhite, 1f, 1f, 1.0f, 0.80f, 0.14f, 1f,
                    pulse, 0.5f, 16f, 0.1f, a.sphereMesh
                )
            }
        }

        if (!hp.hasHelicopter) return

        // Active Camouflage Military Helicopter (tex_army_camouflage) with takeoff hover & spinning rotors!
        val camo = a.texArmyCamo
        val white = a.texWhite
        val hoverBob = if (chopperLiftY > 0.2f) sin(worldTime * 2.2f) * 0.35f else 0f
        val hoverYaw = if (chopperLiftY > 0.5f) 55f + sin(worldTime * 0.45f) * 18f else 55f
        Matrix.setIdentityM(mBase, 0)
        Matrix.translateM(mBase, 0, hp.x, 0.04f + chopperLiftY + hoverBob, hp.z)
        Matrix.rotateM(mBase, 0, hoverYaw, 0f, 1f, 0f)

        // Twin Landing Skids & Struts
        for (sx in floatArrayOf(-1.05f, 1.05f)) {
            drawLocal(dc, mBase, sx, 0.12f, 0.1f, 0.14f, 0.14f, 4.4f, 0f, 0f, white, 1f, 1f, 0.18f, 0.20f, 0.22f, 0f, 0.4f, 20f, 0.12f, a.cubeMesh)
            drawLocal(dc, mBase, sx * 0.82f, 0.48f,  1.05f, 0.10f, 0.72f, 0.10f, 0f, 0f, white, 1f, 1f, 0.22f, 0.24f, 0.26f, 0f, 0.4f, 20f, 0.12f, a.cylinderMesh)
            drawLocal(dc, mBase, sx * 0.82f, 0.48f, -0.95f, 0.10f, 0.72f, 0.10f, 0f, 0f, white, 1f, 1f, 0.22f, 0.24f, 0.26f, 0f, 0.4f, 20f, 0.12f, a.cylinderMesh)
        }

        // Main Camouflage Fuselage + Sloped Armored Nose
        drawLocal(dc, mBase, 0f, 1.55f,  0.10f, 2.25f, 1.75f, 4.6f, 0f, 0f, camo, 2f, 1.5f, 1f, 1f, 1f, 0f, 0.22f, 16f, 0.06f, a.cubeMesh)
        drawLocal(dc, mBase, 0f, 1.28f,  2.75f, 1.85f, 1.15f, 1.65f, 12f, 0f, camo, 1.2f, 1f, 1f, 1f, 1f, 0f, 0.22f, 16f, 0.06f, a.sphereMesh)
        // Tinted Cockpit Glass Canopy
        drawLocal(dc, mBase, 0f, 1.82f,  2.22f, 1.92f, 0.88f, 1.48f, 22f, 0f, white, 1f, 1f, 0.12f, 0.22f, 0.28f, 0.04f, 0.90f, 52f, 0.48f, a.cubeMesh)
        // Top Turbine Engine Cowling
        drawLocal(dc, mBase, 0f, 2.58f, -0.25f, 1.25f, 0.56f, 2.65f, 0f, 0f, camo, 1.5f, 1f, 0.92f, 0.94f, 0.90f, 0f, 0.25f, 18f, 0.06f, a.cubeMesh)

        // Camo Tail Boom & Vertical Stabilizer Fin
        drawLocal(dc, mBase, 0f, 1.88f, -4.15f, 0.62f, 0.68f, 4.4f, 0f, 0f, camo, 2f, 0.8f, 0.95f, 0.96f, 0.92f, 0f, 0.20f, 14f, 0.04f, a.cubeMesh)
        drawLocal(dc, mBase, 0f, 2.58f, -6.10f, 0.20f, 1.75f, 0.92f, -14f, 0f, camo, 0.8f, 1f, 0.95f, 0.96f, 0.92f, 0f, 0.20f, 14f, 0.04f, a.cubeMesh)

        // Rotor Mast + Spinning Main Rotor Blades (accelerates on takeoff!)
        drawLocal(dc, mBase, 0f, 3.05f, 0.15f, 0.26f, 0.72f, 0.26f, 0f, 0f, white, 1f, 1f, 0.20f, 0.22f, 0.24f, 0f, 0.5f, 24f, 0.14f, a.cylinderMesh)
        val rotorSpin = (worldTime * (260f + chopperRotorRate * 320f)) % 360f
        drawLocal(dc, mBase, 0f, 3.36f, 0.15f, 9.2f, 0.06f, 0.30f, 0f, rotorSpin, white, 1f, 1f, 0.14f, 0.15f, 0.16f, 0f, 0.45f, 24f, 0.12f, a.cubeMesh)
        drawLocal(dc, mBase, 0f, 3.36f, 0.15f, 0.30f, 0.06f, 9.2f, 0f, rotorSpin, white, 1f, 1f, 0.14f, 0.15f, 0.16f, 0f, 0.45f, 24f, 0.12f, a.cubeMesh)
        // Spinning Tail Rotor
        drawLocal(dc, mBase, 0.24f, 2.72f, -6.20f, 0.06f, 1.45f, 0.16f, (worldTime * (450f + chopperRotorRate * 380f)) % 360f, 0f, white, 1f, 1f, 0.16f, 0.17f, 0.18f, 0f, 0.4f, 20f, 0.10f, a.cubeMesh)
    }

    fun drawArmyVehicle(dc: GlDrawContext, av: ArmyVehicleProp) {
        val a = dc.assets
        val camo = a.texArmyCamo
        val white = a.texWhite

        Matrix.setIdentityM(mBase, 0)
        Matrix.translateM(mBase, 0, av.x, 0.02f, av.z)
        Matrix.rotateM(mBase, 0, av.yawDeg, 0f, 1f, 0f)

        if (av.isTruck) {
            // 6x6 Heavy Military Camo Truck
            drawLocal(dc, mBase, 0f, 1.05f, 1.95f, 2.35f, 1.25f, 1.95f, 0f, 0f, camo, 1.5f, 1f, 1f, 1f, 1f, 0f, 0.18f, 14f, 0.05f, a.cubeMesh)
            drawLocal(dc, mBase, 0f, 2.02f, 1.65f, 2.28f, 0.92f, 1.45f, 0f, 0f, camo, 1.4f, 0.8f, 0.96f, 0.98f, 0.94f, 0f, 0.18f, 14f, 0.05f, a.cubeMesh)
            // Armored Windshield
            drawLocal(dc, mBase, 0f, 2.04f, 2.39f, 1.90f, 0.56f, 0.08f, 0f, 0f, white, 1f, 1f, 0.14f, 0.22f, 0.26f, 0f, 0.80f, 44f, 0.35f, a.cubeMesh)
            // Rear Troop/Cargo Canvas Bed
            drawLocal(dc, mBase, 0f, 1.78f, -1.15f, 2.48f, 1.95f, 3.85f, 0f, 0f, camo, 2.2f, 1.4f, 0.92f, 0.95f, 0.88f, 0f, 0.12f, 10f, 0.03f, a.cubeMesh)
            // Headlights & Heavy Bumper
            drawLocal(dc, mBase, 0f, 0.62f, 3.00f, 2.45f, 0.32f, 0.24f, 0f, 0f, white, 1f, 1f, 0.16f, 0.18f, 0.16f, 0f, 0.3f, 16f, 0.08f, a.cubeMesh)
            drawLocal(dc, mBase, -0.82f, 0.98f, 2.95f, 0.28f, 0.22f, 0.10f, 0f, 0f, white, 1f, 1f, 1f, 0.96f, 0.78f, 0.75f, 0.5f, 20f, 0.1f, a.cubeMesh)
            drawLocal(dc, mBase,  0.82f, 0.98f, 2.95f, 0.28f, 0.22f, 0.10f, 0f, 0f, white, 1f, 1f, 1f, 0.96f, 0.78f, 0.75f, 0.5f, 20f, 0.1f, a.cubeMesh)

            // 6 Large Off-Road Military Tires (tex_vehicle_tire_tread)
            for (wz in floatArrayOf(1.95f, -0.85f, -2.15f)) {
                for (wx in floatArrayOf(-1.24f, 1.24f)) {
                    drawWheel(dc, mBase, wx, 0.52f, wz, 1.04f, 0.38f, 0.80f, 0.82f, 0.82f, 0.14f, 10f, useTread = true)
                    drawWheel(dc, mBase, wx + (if (wx > 0) 0.04f else -0.04f), 0.52f, wz, 0.54f, 0.34f, 0.28f, 0.32f, 0.24f, 0.25f, 16f)
                }
            }
        } else {
            // Camo Military Patrol 4x4 Jeep
            drawLocal(dc, mBase, 0f, 0.88f, 0.0f, 1.98f, 0.88f, 3.85f, 0f, 0f, camo, 1.8f, 1.2f, 1f, 1f, 1f, 0f, 0.20f, 14f, 0.06f, a.cubeMesh)
            drawLocal(dc, mBase, 0f, 1.62f, -0.35f, 1.82f, 0.76f, 2.05f, 0f, 0f, camo, 1.4f, 0.9f, 0.94f, 0.96f, 0.90f, 0f, 0.18f, 14f, 0.05f, a.cubeMesh)
            // Windshield & Headlights
            drawLocal(dc, mBase, 0f, 1.58f, 0.70f, 1.62f, 0.54f, 0.08f, -14f, 0f, white, 1f, 1f, 0.14f, 0.22f, 0.28f, 0f, 0.82f, 44f, 0.38f, a.cubeMesh)
            drawLocal(dc, mBase, -0.68f, 0.95f, 1.94f, 0.26f, 0.22f, 0.08f, 0f, 0f, white, 1f, 1f, 1f, 0.96f, 0.82f, 0.80f, 0.6f, 20f, 0.1f, a.cubeMesh)
            drawLocal(dc, mBase,  0.68f, 0.95f, 1.94f, 0.26f, 0.22f, 0.08f, 0f, 0f, white, 1f, 1f, 1f, 0.96f, 0.82f, 0.80f, 0.6f, 20f, 0.1f, a.cubeMesh)
            // Rear-mounted Spare Tire (pitched 90° so cylinder axis Y -> horizontal Z, thus sy = tire thickness 0.26f)
            drawLocal(dc, mBase, 0f, 0.95f, -2.04f, 0.78f, 0.26f, 0.78f, 90f, 0f, a.texVehicleTireTread, 3f, 1f, 0.82f, 0.84f, 0.84f, 0f, 0.14f, 10f, 0.02f, a.cylinderMesh)
            // 4 Off-Road Tires (tex_vehicle_tire_tread)
            for (wz in floatArrayOf(1.24f, -1.24f)) {
                for (wx in floatArrayOf(-1.02f, 1.02f)) {
                    drawWheel(dc, mBase, wx, 0.42f, wz, 0.84f, 0.30f, 0.80f, 0.82f, 0.84f, 0.14f, 10f, useTread = true)
                    drawWheel(dc, mBase, wx + (if (wx > 0) 0.03f else -0.03f), 0.42f, wz, 0.46f, 0.28f, 0.30f, 0.34f, 0.25f, 0.26f, 16f)
                }
            }
        }
    }

    fun drawArmyVehicleShadow(dc: GlDrawContext, av: ArmyVehicleProp) {
        dc.bindShadowVbo(dc.assets.cubeMesh)
        Matrix.setIdentityM(dc.modelMatrix, 0)
        Matrix.translateM(dc.modelMatrix, 0, av.x, if (av.isTruck) 1.3f else 0.95f, av.z)
        Matrix.rotateM(dc.modelMatrix, 0, av.yawDeg, 0f, 1f, 0f)
        if (av.isTruck) {
            Matrix.scaleM(dc.modelMatrix, 0, 2.6f, 2.5f, 6.0f)
        } else {
            Matrix.scaleM(dc.modelMatrix, 0, 2.15f, 1.85f, 4.0f)
        }
        dc.commitShadow(dc.assets.texWhite, dc.assets.cubeMesh.vertexCount)
    }

    // -------------------------------------------------------------------------
    // 4. SAINI INTERNATIONAL AIRPORT (tex_airport_runway + glass ATC tower + jetliner)
    // -------------------------------------------------------------------------

    fun drawAirportGround(
        dc: GlDrawContext,
        worldTime: Float,
        isVisible: (Float, Float, Float) -> Boolean
    ) {
        val a = dc.assets
        val rx = CityWorldData.AIRPORT_RUNWAY_X
        val rz = CityWorldData.AIRPORT_RUNWAY_Z
        val rw = CityWorldData.AIRPORT_RUNWAY_WIDTH
        val rl = CityWorldData.AIRPORT_RUNWAY_LENGTH

        if (!isVisible(rx, rz, rl * 0.6f)) return
        dc.bindVbo(a.quadMesh)
        // Wide concrete apron under runway + taxiway connecting to East road
        dc.drawQuad(rx - 4f, 0.024f, rz, rw + 22f, rl + 10f, a.texConcrete, 6f, 18f, 0.78f, 0.80f, 0.82f, 0f, 0.12f, 14f, 0.04f)

        // South Threshold (Piano Keys facing South)
        dc.drawQuad(rx, 0.064f, rz + rl * 0.5f - 14f, rw, 28f, a.texAirportRunway, 1f, 1f, 0.98f, 0.98f, 0.98f, 0f, 0.24f, 22f, 0.06f, yawDeg = 0f)
        // Center Runway Section (Repeating dashed centerline)
        dc.drawQuad(rx, 0.064f, rz, rw, rl - 56f, a.texRoad, 1f, (rl - 56f) / 10f, 0.86f, 0.86f, 0.88f, 0f, 0.24f, 22f, 0.06f, yawDeg = 0f)
        // North Threshold (Piano Keys facing North, rotated 180 deg)
        dc.drawQuad(rx, 0.064f, rz - rl * 0.5f + 14f, rw, 28f, a.texAirportRunway, 1f, 1f, 0.98f, 0.98f, 0.98f, 0f, 0.24f, 22f, 0.06f, yawDeg = 180f)

        // Runway Edge Lighting (pulsing amber/cyan beacons along both edges)
        dc.bindVbo(a.sphereMesh)
        val blink = 0.72f + 0.28f * sin(worldTime * 4.0f)
        var lz = rz - rl * 0.46f
        while (lz <= rz + rl * 0.46f) {
            dc.drawMeshTransformed(rx - rw * 0.53f, 0.20f, lz, 0.28f, 0.22f, 0.28f, 0f, 0f, 0f, a.texWhite, 1f, 1f, 0.20f, 0.86f, 1.0f, 1f, blink, 0.5f, 16f, 0.1f, a.sphereMesh)
            dc.drawMeshTransformed(rx + rw * 0.53f, 0.20f, lz, 0.28f, 0.22f, 0.28f, 0f, 0f, 0f, a.texWhite, 1f, 1f, 1.0f, 0.78f, 0.18f, 1f, blink, 0.5f, 16f, 0.1f, a.sphereMesh)
            lz += 14f
        }
    }

    fun drawAirportStructures(
        dc: GlDrawContext,
        worldTime: Float,
        isVisible: (Float, Float, Float) -> Boolean
    ) {
        val a = dc.assets

        // B. Air Traffic Control Tower (uses tex_building_glass_facade for panoramic ATC cab!)
        val tx = CityWorldData.AIRPORT_TOWER_X
        val tz = CityWorldData.AIRPORT_TOWER_Z
        if (isVisible(tx, tz, 10f)) {
            dc.bindVbo(a.cubeMesh)
            dc.drawBox(tx, 2.2f, tz, 6.8f, 4.4f, 6.8f, a.texArmyBunkerConcrete, 2f, 1.5f, 0.90f, 0.91f, 0.93f, 0f, 0.14f, 14f, 0.04f)
            dc.drawBox(tx, 10.6f, tz, 4.2f, 12.6f, 4.2f, a.texConcrete, 1.5f, 3.5f, 0.92f, 0.93f, 0.95f, 0f, 0.18f, 18f, 0.06f)
            // Panoramic Glass Control Deck
            dc.drawBox(tx, 18.4f, tz, 6.6f, 3.4f, 6.6f, a.texBuildingGlassFacade, 2f, 1f, 0.96f, 0.98f, 1.0f, 0.08f, 0.85f, 48f, 0.45f)
            dc.drawBox(tx, 20.4f, tz, 7.2f, 0.65f, 7.2f, a.texConcrete, 2f, 0.5f, 0.44f, 0.48f, 0.54f, 0f, 0.2f, 16f, 0.06f)
            // Rotating Airport Radar Scanner on Roof
            dc.drawMeshTransformed(
                tx, 21.8f, tz, 3.4f, 0.75f, 0.35f,
                (worldTime * 75f) % 360f, 0f, 0f,
                a.texWhite, 1f, 1f, 0.92f, 0.94f, 0.96f, 1f, 0.08f, 0.7f, 32f, 0.25f, a.cubeMesh
            )
        }

        // C. Corrugated-Metal Airport Hangar (tex_factory_corrugated_metal)
        val hx = CityWorldData.AIRPORT_HANGAR_X
        val hz = CityWorldData.AIRPORT_HANGAR_Z
        if (isVisible(hx, hz, 12f)) {
            dc.bindVbo(a.cubeMesh)
            dc.drawBox(hx, 3.8f, hz, 9.8f, 7.6f, 13.8f, a.texFactoryCorrugatedMetal, 4f, 2.5f, 0.86f, 0.88f, 0.92f, 0f, 0.28f, 22f, 0.10f)
            // Wide Hangar Bay Shutter Door facing Runway (+X)
            dc.drawBox(hx + 4.96f, 3.0f, hz, 0.14f, 5.8f, 9.4f, a.texShopShutter, 3f, 2f, 0.84f, 0.86f, 0.90f, 0f, 0.35f, 22f, 0.12f)
            dc.drawBox(hx, 7.85f, hz, 10.4f, 0.55f, 14.4f, a.texFactoryCorrugatedMetal, 4f, 4f, 0.72f, 0.76f, 0.82f, 0f, 0.32f, 24f, 0.12f)
        }

        // D. Commercial Jetliner Parked on the Runway Apron
        val jx = CityWorldData.AIRPORT_JET_X
        val jz = CityWorldData.AIRPORT_JET_Z
        if (isVisible(jx, jz, 16f)) {
            drawJetliner(dc, jx, 0.04f, jz, yawDeg = 0f, showGear = true)
        }
    }

    fun drawJetliner(dc: GlDrawContext, x: Float, y: Float, z: Float, yawDeg: Float, showGear: Boolean) {
        val a = dc.assets
        val white = a.texWhite
        Matrix.setIdentityM(mBase, 0)
        Matrix.translateM(mBase, 0, x, y, z)
        if (yawDeg != 0f) Matrix.rotateM(mBase, 0, yawDeg, 0f, 1f, 0f)

        // Fuselage Tube: cylinder local Y axis becomes horizontal Z after pitch=90° -> sy = length 18.4f!
        drawLocal(dc, mBase, 0f, 2.55f, 0f, 2.85f, 18.4f, 2.85f, 90f, 0f, white, 1f, 1f, 0.95f, 0.96f, 0.99f, 0.02f, 0.78f, 44f, 0.34f, a.cylinderMesh)
        // Aerodynamic Nose Dome & Tapered Rear Tail Cone
        drawLocal(dc, mBase, 0f, 2.50f, 9.4f, 2.72f, 2.60f, 4.2f, 0f, 0f, white, 1f, 1f, 0.95f, 0.96f, 0.99f, 0.02f, 0.78f, 44f, 0.34f, a.sphereMesh)
        drawLocal(dc, mBase, 0f, 2.66f, -10.8f, 2.62f, 4.4f, 2.50f, -90f, 0f, white, 1f, 1f, 0.94f, 0.95f, 0.98f, 0.02f, 0.72f, 40f, 0.30f, a.taperedCylinderMesh)
        // Cockpit Windshield
        drawLocal(dc, mBase, 0f, 3.02f, 9.2f, 1.82f, 0.62f, 1.40f, 20f, 0f, white, 1f, 1f, 0.09f, 0.16f, 0.26f, 0.06f, 0.92f, 56f, 0.50f, a.cubeMesh)
        // Airline Livery Side Stripes (cleanly offset from fuselage surface so zero Z-fighting!)
        drawLocal(dc, mBase, -1.48f, 2.48f, 0.2f, 0.08f, 0.42f, 16.0f, 0f, 0f, white, 1f, 1f, 0.08f, 0.44f, 0.88f, 0.05f, 0.70f, 36f, 0.24f, a.cubeMesh)
        drawLocal(dc, mBase,  1.48f, 2.48f, 0.2f, 0.08f, 0.42f, 16.0f, 0f, 0f, white, 1f, 1f, 0.08f, 0.44f, 0.88f, 0.05f, 0.70f, 36f, 0.24f, a.cubeMesh)

        // Swept Main Wings + Vertical Wingtip Winglets
        drawLocal(dc, mBase, -6.2f, 2.05f, -0.4f, 11.4f, 0.30f, 3.3f, 0f,  15f, white, 1f, 1f, 0.88f, 0.90f, 0.94f, 0f, 0.65f, 32f, 0.22f, a.cubeMesh)
        drawLocal(dc, mBase,  6.2f, 2.05f, -0.4f, 11.4f, 0.30f, 3.3f, 0f, -15f, white, 1f, 1f, 0.88f, 0.90f, 0.94f, 0f, 0.65f, 32f, 0.22f, a.cubeMesh)
        drawLocal(dc, mBase, -11.5f, 2.65f, -1.8f, 0.18f, 1.40f, 1.25f, 0f, 0f, white, 1f, 1f, 0.08f, 0.44f, 0.88f, 0.05f, 0.65f, 32f, 0.22f, a.cubeMesh)
        drawLocal(dc, mBase,  11.5f, 2.65f, -1.8f, 0.18f, 1.40f, 1.25f, 0f, 0f, white, 1f, 1f, 0.08f, 0.44f, 0.88f, 0.05f, 0.65f, 32f, 0.22f, a.cubeMesh)

        // Under-wing Dual Turbofan Jet Engines (pitch=90° -> sy = engine length 2.95f!)
        for (ex in floatArrayOf(-4.3f, 4.3f)) {
            drawLocal(dc, mBase, ex, 1.48f, 0.55f, 1.22f, 2.95f, 1.22f, 90f, 0f, white, 1f, 1f, 0.32f, 0.36f, 0.42f, 0f, 0.78f, 40f, 0.30f, a.cylinderMesh)
            drawLocal(dc, mBase, ex, 1.48f, 2.05f, 0.98f, 0.12f, 0.98f, 90f, 0f, white, 1f, 1f, 0.08f, 0.10f, 0.14f, 0f, 0.45f, 20f, 0.10f, a.cylinderMesh)
        }
        // Tall Vertical Tail Fin & Horizontal Stabilizers
        drawLocal(dc, mBase, 0f, 5.25f, -9.8f, 0.34f, 4.4f, 3.0f, -16f, 0f, white, 1f, 1f, 0.08f, 0.44f, 0.88f, 0.05f, 0.65f, 34f, 0.22f, a.cubeMesh)
        drawLocal(dc, mBase, 0f, 2.95f, -10.2f, 7.8f, 0.22f, 1.75f, 0f, 0f, white, 1f, 1f, 0.88f, 0.90f, 0.94f, 0f, 0.60f, 30f, 0.20f, a.cubeMesh)
        // Landing Gear Struts & Treaded Tires
        if (showGear) {
            for (gx in floatArrayOf(0f, -2.4f, 2.4f)) {
                val gz = if (gx == 0f) 7.0f else -1.0f
                drawLocal(dc, mBase, gx, 0.85f, gz, 0.24f, 1.45f, 0.24f, 0f, 0f, white, 1f, 1f, 0.30f, 0.32f, 0.36f, 0f, 0.5f, 20f, 0.14f, a.cylinderMesh)
                drawWheel(dc, mBase, gx, 0.42f, gz, 0.78f, 0.38f, 0.80f, 0.82f, 0.84f, 0.14f, 10f, useTread = true)
            }
        }
    }

    // -------------------------------------------------------------------------
    // 4b. ROADSIDE SHOPS (tex_shop_shutter + marble floor + wall paint) & PORT CONTAINERS
    // -------------------------------------------------------------------------

    fun drawRoadsideShop(dc: GlDrawContext, shop: RoadsideShopProp, nightFactor: Float) {
        val a = dc.assets
        val m = a.multiAngle
        val hw = shop.width * 0.5f
        val hd = shop.depth * 0.5f
        val h = shop.height
        val wt = 0.26f
        val fSign = shop.facingDirZ
        val faceInYaw = if (fSign > 0f) 0f else 180f

        val marbleTex = m?.floorMarble ?: a.texInteriorMarbleTiles
        val brickTex = m?.bldHouseSideBrick ?: a.texHouseRedBrick
        val roofTex = m?.bldAptRoof ?: a.texFactoryCorrugatedMetal
        val shopFrontTex = m?.bldShopFront ?: a.texShopShutter

        // Raised concrete foundation (+0.14m) & wall-to-wall Glossy Marble Interior Floor (+0.16m)
        dc.bindVbo(a.cubeMesh)
        dc.drawBox(shop.x, 0.07f, shop.z, shop.width + 0.35f, 0.14f, shop.depth + 0.35f, roofTex, 2f, 2f, 0.72f, 0.74f, 0.76f, 0f, 0.12f, 12f, 0.03f)
        dc.bindVbo(a.quadMesh)
        dc.drawQuad(shop.x, 0.16f, shop.z, shop.width - wt * 1.92f, shop.depth - wt * 1.92f, marbleTex, 3f, 2.5f, 0.98f, 0.98f, 0.97f, 0.05f + nightFactor * 0.18f, 0.68f, 40f, 0.28f)

        // Structural Brick Side & Rear Walls (trimmed non-overlapping corners -> ZERO corner Z-fighting!)
        dc.bindVbo(a.cubeMesh)
        val sideLen = (shop.depth - wt).coerceAtLeast(0.5f)
        val sideCenterZ = shop.z + fSign * (wt * 0.5f)
        val backWallZ = shop.z - fSign * (hd - wt * 0.5f)
        dc.drawBox(shop.x - hw + wt * 0.5f, 0.14f + h * 0.5f, sideCenterZ, wt, h, sideLen, brickTex, 2.2f, 1.6f, 0.95f, 0.93f, 0.91f, 0f, 0.14f, 12f, 0.03f)
        dc.drawBox(shop.x + hw - wt * 0.5f, 0.14f + h * 0.5f, sideCenterZ, wt, h, sideLen, brickTex, 2.2f, 1.6f, 0.95f, 0.93f, 0.91f, 0f, 0.14f, 12f, 0.03f)
        dc.drawBox(shop.x, 0.14f + h * 0.5f, backWallZ, shop.width, h, wt, brickTex, 2.5f, 1.6f, 0.95f, 0.93f, 0.91f, 0f, 0.14f, 12f, 0.03f)

        // Painted interior drywall liners inset 2.5cm inside brick shell
        val linerT = 0.03f
        val innerW = (shop.width - wt * 2f - 0.04f).coerceAtLeast(1f)
        val innerSideLen = (shop.depth - wt - 0.06f).coerceAtLeast(1f)
        val backInnerZ = shop.z - fSign * (hd - wt - 0.04f)
        dc.drawBox(shop.x, 0.16f + (h - 0.04f) * 0.5f, backInnerZ - fSign * 0.015f, innerW, h - 0.04f, linerT, a.texInteriorWallPaint, 2.2f, 1.4f, 0.96f, 0.95f, 0.92f, 0.04f + nightFactor * 0.12f, 0.12f, 12f, 0.02f)
        dc.drawBox(shop.x - hw + wt + 0.025f, 0.16f + (h - 0.04f) * 0.5f, sideCenterZ, linerT, h - 0.04f, innerSideLen, a.texInteriorWallPaint, 2.0f, 1.4f, 0.95f, 0.94f, 0.91f, 0.04f + nightFactor * 0.12f, 0.12f, 12f, 0.02f)
        dc.drawBox(shop.x + hw - wt - 0.025f, 0.16f + (h - 0.04f) * 0.5f, sideCenterZ, linerT, h - 0.04f, innerSideLen, a.texInteriorWallPaint, 2.0f, 1.4f, 0.95f, 0.94f, 0.91f, 0.04f + nightFactor * 0.12f, 0.12f, 12f, 0.02f)

        // Warm interior LED ceiling light bar
        dc.drawBox(shop.x, 0.14f + h - 0.16f, shop.z, 2.4f, 0.08f, 0.65f, a.texWhite, 1f, 1f, 1.0f, 0.96f, 0.86f, 0.92f, 0.2f, 12f, 0f)
        // Roof deck & Showroom Front Fascia Sign Header
        dc.drawBox(shop.x, 0.14f + h + 0.18f, shop.z, shop.width + 0.5f, 0.36f, shop.depth + 0.6f, roofTex, 3f, 2.5f, 0.78f, 0.80f, 0.84f, 0f, 0.22f, 18f, 0.06f)
        dc.drawBox(shop.x, 0.14f + h - 0.26f, shop.frontZ + fSign * 0.18f, shop.width * 0.96f, 0.56f, 0.16f, shopFrontTex, 1f, 0.35f, 0.96f, 0.96f, 0.98f, 0.14f + nightFactor * 0.32f, 0.45f, 24f, 0.14f)

        // Furnished Shop Interior: Flush against rear inner wall (matches physics colliders exactly!)
        MultiAnglePropDrawer.drawOfficeDeskPc(dc, shop.x - 1.15f, 0.16f, backInnerZ + fSign * 0.38f, faceInYaw)
        MultiAnglePropDrawer.drawCargoCrate(dc, shop.x + 1.85f, 0.16f, backInnerZ + fSign * 0.54f, 0f, size = 1.02f)
        MultiAnglePropDrawer.drawCargoCrate(dc, shop.x + 1.85f, 0.16f + 1.02f, backInnerZ + fSign * 0.54f, 4f, size = 0.92f)
        MultiAnglePropDrawer.drawAcOutdoorUnit(dc, shop.x + hw + 0.19f, 2.2f, shop.z, 90f)

        // Hand-Controlled Interactive Roll-Up Shutter Gate (stop/leave-anywhere!)
        MultiAnglePropDrawer.drawInteractiveGate(dc, shop.gate, nightFactor)
    }

    fun drawRoadsideShopShadow(dc: GlDrawContext, shop: RoadsideShopProp) {
        dc.bindShadowVbo(dc.assets.cubeMesh)
        Matrix.setIdentityM(dc.modelMatrix, 0)
        Matrix.translateM(dc.modelMatrix, 0, shop.x, shop.height * 0.5f, shop.z)
        Matrix.scaleM(dc.modelMatrix, 0, shop.width + 0.2f, shop.height + 0.3f, shop.depth + 0.2f)
        dc.commitShadow(dc.assets.texWhite, dc.assets.cubeMesh.vertexCount)
    }

    fun drawPortContainer(dc: GlDrawContext, c: PortContainerProp) {
        val a = dc.assets
        dc.bindVbo(a.cubeMesh)
        dc.drawMeshTransformed(
            c.x, c.y, c.z,
            c.width, c.height, c.length,
            c.yawDeg, 0f, 0f,
            a.texPortContainerSteel, 2.4f, 1.0f,
            c.r, c.g, c.b, 1f,
            0f, 0.36f, 26f, 0.14f,
            a.cubeMesh
        )
    }

    fun drawPortContainerShadow(dc: GlDrawContext, c: PortContainerProp) {
        dc.bindShadowVbo(dc.assets.cubeMesh)
        Matrix.setIdentityM(dc.modelMatrix, 0)
        Matrix.translateM(dc.modelMatrix, 0, c.x, c.y, c.z)
        Matrix.rotateM(dc.modelMatrix, 0, c.yawDeg, 0f, 1f, 0f)
        Matrix.scaleM(dc.modelMatrix, 0, c.width, c.height, c.length)
        dc.commitShadow(dc.assets.texWhite, dc.assets.cubeMesh.vertexCount)
    }

    // -------------------------------------------------------------------------
    // 4c. SKY DOME (DAY/NIGHT CROSSFADE), SUN GLOW, MOON & FALLING RAIN SHEETS
    // -------------------------------------------------------------------------

    fun drawSkyDomeSunMoon(
        dc: GlDrawContext,
        camX: Float, camY: Float, camZ: Float,
        cameraYawRad: Float,
        sunAngleRad: Float,
        nightFactor: Float,
        rainIntensity: Float
    ) {
        val a = dc.assets
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        // 1. Sky Dome: tex_env_sky_day base + smooth crossfade overlay of tex_env_sky_night
        val rainDim = 1f - rainIntensity * 0.35f
        dc.bindVbo(a.skyDomeMesh)
        dc.drawMeshTransformed(
            camX, camY - 10f, camZ,
            190f, 145f, 190f,
            0f, 0f, 0f,
            a.texEnvSkyDay, 2f, 1f,
            rainDim, rainDim, rainDim + rainIntensity * 0.05f, 1f,
            1.0f, 0f, 8f, 0f,
            a.skyDomeMesh, fogMix = 0f
        )
        if (nightFactor > 0.02f) {
            dc.drawMeshTransformed(
                camX, camY - 10f, camZ,
                186f, 142f, 186f,
                0f, 0f, 0f,
                a.texEnvSkyNight, 2f, 1f,
                1f, 1f, 1f, nightFactor.coerceIn(0f, 1f),
                1.0f, 0f, 8f, 0f,
                a.skyDomeMesh, fogMix = 0f
            )
        }

        // 2. Additive Sun Glow Billboard arcing across sky during daytime (tex_env_sun_glow)
        val sunDist = 148f
        val sunX = camX + cos(sunAngleRad) * sunDist
        val sunY = camY + sin(sunAngleRad) * sunDist
        val sunZ = camZ - 32f
        val dayAlpha = ((1f - nightFactor * 1.15f) * (1f - rainIntensity * 0.65f)).coerceIn(0f, 1f)
        if (dayAlpha > 0.03f && sin(sunAngleRad) > -0.15f) {
            val yawToSun = atan2(camX - sunX, camZ - sunZ) * 180f / PI.toFloat()
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
            dc.bindVbo(a.verticalQuadMesh)
            dc.drawMeshTransformed(
                sunX, sunY, sunZ,
                34f, 34f, 1f,
                yawToSun, 0f, 0f,
                a.texEnvSunGlow, 1f, 1f,
                1.0f, 0.96f, 0.86f, dayAlpha,
                1.15f, 0f, 8f, 0f,
                a.verticalQuadMesh, fogMix = 0f
            )
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        }

        // 3. Moon Billboard opposite the sun during nighttime (tex_env_moon)
        val moonAngle = sunAngleRad + PI.toFloat()
        val moonX = camX + cos(moonAngle) * sunDist
        val moonY = camY + sin(moonAngle) * sunDist
        val moonZ = camZ + 28f
        val moonAlpha = (nightFactor * (1f - rainIntensity * 0.45f)).coerceIn(0f, 1f)
        if (moonAlpha > 0.04f && sin(moonAngle) > -0.10f) {
            val yawToMoon = atan2(camX - moonX, camZ - moonZ) * 180f / PI.toFloat()
            dc.bindVbo(a.verticalQuadMesh)
            dc.drawMeshTransformed(
                moonX, moonY, moonZ,
                22f, 22f, 1f,
                yawToMoon, 0f, 0f,
                a.texEnvMoon, 1f, 1f,
                0.96f, 0.98f, 1.0f, moonAlpha,
                1.05f, 0f, 8f, 0f,
                a.verticalQuadMesh, fogMix = 0f
            )
        }

        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(true)
    }

    fun drawRainSheets(
        dc: GlDrawContext,
        camX: Float, camY: Float, camZ: Float,
        worldTime: Float,
        rainIntensity: Float
    ) {
        if (rainIntensity <= 0.02f) return
        val a = dc.assets
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        dc.bindVbo(a.verticalQuadMesh)

        val vScroll1 = -(worldTime * 3.4f) % 1f
        val vScroll2 = -(worldTime * 2.5f + 0.37f) % 1f
        val alpha = (rainIntensity * 0.62f).coerceIn(0f, 0.72f)

        // Two concentric hexagonal rings of UV-scrolling falling rain sheets surrounding the camera
        val radii = floatArrayOf(5.8f, 11.5f)
        for (ringIdx in radii.indices) {
            val radDist = radii[ringIdx]
            val vOff = if (ringIdx == 0) vScroll1 else vScroll2
            val uScale = if (ringIdx == 0) 2.2f else 3.4f
            for (s in 0 until 6) {
                val angDeg = s * 60f + ringIdx * 30f
                val angRad = angDeg * PI.toFloat() / 180f
                val rx = camX + cos(angRad) * radDist
                val rz = camZ + sin(angRad) * radDist
                dc.drawMeshTransformed(
                    rx, camY + 1.5f, rz,
                    radDist * 1.25f, 18f, 1f,
                    -angDeg + 90f, 0f, 6f,
                    a.texEnvRainStreaks, uScale, 3.0f,
                    0.82f, 0.90f, 1.0f, alpha * (if (ringIdx == 0) 1f else 0.72f),
                    0.75f, 0f, 8f, 0f,
                    a.verticalQuadMesh,
                    uOffset = (ringIdx * 0.27f + s * 0.13f),
                    vOffset = vOff,
                    fogMix = 0f
                )
            }
        }

        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(true)
    }

    // -------------------------------------------------------------------------
    // 5. FLYING FEATHERED BIRDS (tex_bird_feathers) & DRIFTING CLOUDS (tex_env_cloud_puff)
    // -------------------------------------------------------------------------

    fun drawFlyingBirds(dc: GlDrawContext, birds: List<FlyingBirdAgent>) {
        if (birds.isEmpty()) return
        val a = dc.assets
        val tex = a.texBirdFeathers

        for (bird in birds) {
            val bx = bird.centerX + cos(bird.angle) * bird.orbitRadius
            val bz = bird.centerZ + sin(bird.angle) * bird.orbitRadius
            // Tangent velocity direction for realistic circular banking flight
            val vx = -sin(bird.angle) * bird.angularSpeed
            val vz =  cos(bird.angle) * bird.angularSpeed
            val headingDeg = atan2(vx, vz) * 180f / PI.toFloat()
            val flapDeg = sin(bird.wingPhase) * 34f
            val bobY = sin(bird.wingPhase) * 0.16f
            val s = bird.scale

            Matrix.setIdentityM(mBase, 0)
            Matrix.translateM(mBase, 0, bx, bird.altitude + bobY, bz)
            Matrix.rotateM(mBase, 0, headingDeg, 0f, 1f, 0f)
            Matrix.rotateM(mBase, 0, -14f, 0f, 0f, 1f) // Slight inward banking roll

            // Feathered Bird Body + Tail Fan
            drawLocal(dc, mBase, 0f, 0f, 0f, 0.28f * s, 0.22f * s, 0.74f * s, 0f, 0f, tex, 1f, 1f, 1f, 1f, 1f, 0.05f, 0.14f, 12f, 0.02f, a.sphereMesh)
            drawLocal(dc, mBase, 0f, 0.02f, -0.44f * s, 0.36f * s, 0.04f * s, 0.34f * s, 6f, 0f, tex, 1f, 1f, 0.90f, 0.90f, 0.92f, 0.05f, 0.10f, 10f, 0f, a.cubeMesh)

            // Animated Flapping Left & Right Feathered Wings
            dc.bindVbo(a.cubeMesh)
            for (side in floatArrayOf(-1f, 1f)) {
                System.arraycopy(mBase, 0, dc.modelMatrix, 0, 16)
                Matrix.translateM(dc.modelMatrix, 0, side * 0.12f * s, 0.04f * s, 0.04f * s)
                Matrix.rotateM(dc.modelMatrix, 0, side * flapDeg, 0f, 0f, 1f)
                Matrix.translateM(dc.modelMatrix, 0, side * 0.56f * s, 0f, -0.04f * s)
                Matrix.scaleM(dc.modelMatrix, 0, 1.12f * s, 0.04f * s, 0.38f * s)
                dc.commitDraw(tex, 1f, 1f, 0.98f, 0.98f, 1.0f, 1f, 0.06f, 0.15f, 12f, 0.03f, a.cubeMesh.vertexCount)
            }
        }
    }

    fun drawSkyClouds(dc: GlDrawContext, clouds: List<SkyCloudProp>, cameraYawRad: Float) {
        if (clouds.isEmpty()) return
        val a = dc.assets
        val camYawDeg = cameraYawRad * 180f / PI.toFloat()

        // Soft alpha-blended volumetric billboards without polluting depth buffer
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        dc.bindVbo(a.verticalQuadMesh)

        for (c in clouds) {
            // Render a 2-puff layered cloud cluster for genuine soft depth
            dc.drawMeshTransformed(
                c.x, c.y, c.z,
                c.scaleX, c.scaleY, 1f,
                camYawDeg, 0f, 0f,
                a.texEnvCloudPuff, 1f, 1f,
                1.0f, 0.99f, 0.98f, 0.86f,
                0.55f, 0f, 8f, 0f,
                a.verticalQuadMesh
            )
            dc.drawMeshTransformed(
                c.x + c.scaleX * 0.24f, c.y - 1.6f, c.z + 3.2f,
                c.scaleX * 0.72f, c.scaleY * 0.72f, 1f,
                camYawDeg, 0f, 0f,
                a.texEnvCloudPuff, 1f, 1f,
                0.96f, 0.97f, 1.0f, 0.74f,
                0.50f, 0f, 8f, 0f,
                a.verticalQuadMesh
            )
        }

        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(true)
    }

    // -------------------------------------------------------------------------
    // SHARED ZERO-ALLOCATION LOCAL TRANSFORM HELPERS
    // -------------------------------------------------------------------------

    private fun drawWheel(
        dc: GlDrawContext,
        parent: FloatArray,
        lx: Float, ly: Float, lz: Float,
        diameter: Float, width: Float,
        r: Float, g: Float, b: Float,
        specStrength: Float, specPower: Float,
        useTread: Boolean = false,
        spinDeg: Float = 0f
    ) {
        val cyl = dc.assets.cylinderMesh
        dc.bindVbo(cyl)
        System.arraycopy(parent, 0, dc.modelMatrix, 0, 16)
        Matrix.translateM(dc.modelMatrix, 0, lx, ly, lz)
        Matrix.rotateM(dc.modelMatrix, 0, 90f, 0f, 0f, 1f)
        if (spinDeg != 0f) Matrix.rotateM(dc.modelMatrix, 0, spinDeg, 0f, 1f, 0f)
        Matrix.scaleM(dc.modelMatrix, 0, diameter, width, diameter)
        val texId = if (useTread) dc.assets.texVehicleTireTread else dc.assets.texWhite
        val uScale = if (useTread) 4f else 1f
        dc.commitDraw(texId, uScale, 1f, r, g, b, 1f, 0f, specStrength, specPower, 0.08f, cyl.vertexCount)
    }

    private fun drawLocal(
        dc: GlDrawContext,
        parent: FloatArray,
        lx: Float, ly: Float, lz: Float,
        sx: Float, sy: Float, sz: Float,
        pitchDeg: Float, yawDeg: Float,
        texId: Int, uScale: Float, vScale: Float,
        r: Float, g: Float, b: Float,
        emissive: Float, specStrength: Float, specPower: Float, reflectStrength: Float,
        mesh: GlMesh
    ) {
        dc.bindVbo(mesh)
        System.arraycopy(parent, 0, dc.modelMatrix, 0, 16)
        Matrix.translateM(dc.modelMatrix, 0, lx, ly, lz)
        if (yawDeg != 0f) Matrix.rotateM(dc.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        if (pitchDeg != 0f) Matrix.rotateM(dc.modelMatrix, 0, pitchDeg, 1f, 0f, 0f)
        Matrix.scaleM(dc.modelMatrix, 0, sx, sy, sz)
        dc.commitDraw(texId, uScale, vScale, r, g, b, 1f, emissive, specStrength, specPower, reflectStrength, mesh.vertexCount)
    }
}
