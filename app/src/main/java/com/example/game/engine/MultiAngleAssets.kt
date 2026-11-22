package com.example.game.engine

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import kotlin.math.abs

/**
 * Central Texture Manager for the complete 80-file Saini Simulator realistic multi-angle pack
 * stored in assets/textures_v2/. Supports incremental per-frame batch decoding via [stepLoadBatch]
 * so the loading screen stays responsive with zero startup freeze!
 */
class MultiAngleTextureManager(
    private val context: Context,
    fallbackTex: Int = 0
) {

    private val cache = HashMap<String, Int>(96)
    private var nextLoadIdx = 0

    private fun load(name: String, repeatX: Boolean = false, repeatY: Boolean = repeatX): Int {
        val key = "$name:$repeatX:$repeatY"
        cache[key]?.let { return it }
        val texId = GlTextureHelper.loadAssetTexture(
            context = context,
            assetPath = "textures_v2/$name.webp",
            repeat = repeatX,
            repeatY = repeatY,
            maxDim = 512
        )
        cache[key] = texId
        return texId
    }

    // --- 1. AC Outdoor Unit (6) ---
    var acFront = fallbackTex
    var acBack = fallbackTex
    var acRight = fallbackTex
    var acLeft = fallbackTex
    var acTop = fallbackTex
    var acBottom = fallbackTex

    // --- 2. Rooftop Water Tank (3) ---
    var waterTankSide = fallbackTex
    var waterTankTop = fallbackTex
    var waterTankBottom = fallbackTex

    // --- 3. King Size Bed (6) ---
    var bedTop = fallbackTex
    var bedFront = fallbackTex
    var bedBack = fallbackTex
    var bedLeft = fallbackTex
    var bedRight = fallbackTex
    var bedBottom = fallbackTex

    // --- 4. Modern Sofa (6) ---
    var sofaFront = fallbackTex
    var sofaBack = fallbackTex
    var sofaLeft = fallbackTex
    var sofaRight = fallbackTex
    var sofaTop = fallbackTex
    var sofaBottom = fallbackTex

    // --- 5. Wooden Wardrobe (6) ---
    var wardrobeFront = fallbackTex
    var wardrobeLeft = fallbackTex
    var wardrobeRight = fallbackTex
    var wardrobeBack = fallbackTex
    var wardrobeTop = fallbackTex
    var wardrobeBottom = fallbackTex

    // --- 6. Double-Door Refrigerator (6) ---
    var fridgeFront = fallbackTex
    var fridgeLeft = fallbackTex
    var fridgeRight = fallbackTex
    var fridgeBack = fallbackTex
    var fridgeTop = fallbackTex
    var fridgeBottom = fallbackTex

    // --- 7. TV Unit & LED TV (6) ---
    var tvFront = fallbackTex
    var tvBack = fallbackTex
    var tvLeft = fallbackTex
    var tvRight = fallbackTex
    var tvTop = fallbackTex
    var tvBottom = fallbackTex

    // --- 8. Modular Kitchen Counter (6) ---
    var kitchenFront = fallbackTex
    var kitchenTop = fallbackTex
    var kitchenLeft = fallbackTex
    var kitchenRight = fallbackTex
    var kitchenBackBacksplash = fallbackTex
    var kitchenBottom = fallbackTex

    // --- 9. Office Desk & PC (4) ---
    var deskFront = fallbackTex
    var deskTop = fallbackTex
    var deskBack = fallbackTex
    var deskSide = fallbackTex

    // --- 10. Factory CNC Machine (5) ---
    var cncFront = fallbackTex
    var cncLeft = fallbackTex
    var cncRight = fallbackTex
    var cncBack = fallbackTex
    var cncTop = fallbackTex

    // --- 11. Factory Wooden Cargo Crate (1) ---
    var cargoCrate = fallbackTex

    // --- 12. Factory Steel Oil Drum (3) ---
    var oilDrumSide = fallbackTex
    var oilDrumTop = fallbackTex
    var oilDrumBottom = fallbackTex

    // --- 13. Modern Building Facades & Roofs (12) ---
    var bldFactoryWall = fallbackTex
    var bldFactoryRoof = fallbackTex
    var bldAptFront = fallbackTex
    var bldAptSide = fallbackTex
    var bldAptBack = fallbackTex
    var bldAptRoof = fallbackTex
    var bldOfficeFront = fallbackTex
    var bldOfficeSide = fallbackTex
    var bldOfficeLobby = fallbackTex
    var bldHouseFront = fallbackTex
    var bldHouseSideBrick = fallbackTex
    var bldShopFront = fallbackTex

    // --- 14. Interior Floors (3) ---
    var floorEpoxy = fallbackTex
    var floorOakWood = fallbackTex
    var floorMarble = fallbackTex

    // --- 15. Hand-Controlled Interactive Gates (7) ---
    var gateShutterFront = fallbackTex
    var gateShutterBack = fallbackTex
    var gateHouseFront = fallbackTex
    var gateHouseBack = fallbackTex
    var gateVillaFront = fallbackTex
    var gateVillaBack = fallbackTex
    var gateAptSliding = fallbackTex

    private val loadSteps: List<() -> Unit> = listOf(
        // Facades & Gates first (visible immediately on city spawn)
        { bldFactoryWall = load("building_factory_warehouse_01_side_wall_corrugated_metal", repeatX = true) },
        { bldFactoryRoof = load("building_factory_warehouse_02_top_roof_metal_skylight", repeatX = true) },
        { bldAptFront = load("building_modern_apartment_01_front_balconies_windows", repeatX = true) },
        { bldAptSide = load("building_modern_apartment_02_side_wall_windows_pipe", repeatX = true) },
        { bldAptBack = load("building_modern_apartment_03_back_utility_pipes", repeatX = true) },
        { bldAptRoof = load("building_modern_apartment_04_top_rooftop_concrete", repeatX = true) },
        { bldOfficeFront = load("building_office_tower_01_front_glass_curtain_wall", repeatX = true) },
        { bldOfficeSide = load("building_office_tower_02_side_glass_steel", repeatX = true) },
        { bldOfficeLobby = load("building_office_tower_03_ground_lobby_entrance", repeatX = true, repeatY = false) },
        { bldHouseFront = load("building_residential_house_01_front_stucco_door_window", repeatX = false) },
        { bldHouseSideBrick = load("building_residential_house_02_side_brick_wall", repeatX = true) },
        { bldShopFront = load("building_shop_showroom_01_front_glass_display_sign", repeatX = false) },
        { floorEpoxy = load("interior_floor_factory_epoxy_concrete", repeatX = true) },
        { floorOakWood = load("interior_floor_natural_oak_wood_planks", repeatX = true) },
        { floorMarble = load("interior_floor_polished_marble_tiles_realistic", repeatX = true) },
        { gateShutterFront = load("gate_factory_rollup_shutter_01_front_slats", repeatX = true) },
        { gateShutterBack = load("gate_factory_rollup_shutter_02_back_interior", repeatX = true) },
        { gateHouseFront = load("gate_house_single_metal_01_front_bars_handle") },
        { gateHouseBack = load("gate_house_single_metal_02_back_latch_hinges") },
        { gateVillaFront = load("gate_luxury_villa_wood_iron_01_front") },
        { gateVillaBack = load("gate_luxury_villa_wood_iron_02_back") },
        { gateAptSliding = load("gate_sliding_apartment_iron_01_front_and_back") },
        // Props & Rooftop units
        { cargoCrate = load("factory_cargo_crate_all_6_sides_wooden") },
        { oilDrumSide = load("factory_steel_oil_drum_01_side_wrap", repeatX = true, repeatY = false) },
        { oilDrumTop = load("factory_steel_oil_drum_02_top_bung_caps") },
        { oilDrumBottom = load("factory_steel_oil_drum_03_bottom_base") },
        { acFront = load("ac_outdoor_unit_01_front_fan_grill_badge") },
        { acBack = load("ac_outdoor_unit_02_back_radiator_fins") },
        { acRight = load("ac_outdoor_unit_03_right_side_copper_pipes_valves") },
        { acLeft = load("ac_outdoor_unit_04_left_side_intake_vents") },
        { acTop = load("ac_outdoor_unit_05_top_metal_lid") },
        { acBottom = load("ac_outdoor_unit_06_bottom_base_drain_feet") },
        { waterTankSide = load("rooftop_water_tank_01_side_cylindrical_ribbed_black", repeatX = true, repeatY = false) },
        { waterTankTop = load("rooftop_water_tank_02_top_lid_and_rings") },
        { waterTankBottom = load("rooftop_water_tank_03_bottom_base") },
        // Interior furniture
        { bedTop = load("house_king_bed_01_top_mattress_duvet_pillows") },
        { bedFront = load("house_king_bed_02_front_footboard_wood_and_sheets") },
        { bedBack = load("house_king_bed_03_back_headboard_tufted_wood") },
        { bedLeft = load("house_king_bed_04_left_side_profile_bedding") },
        { bedRight = load("house_king_bed_05_right_side_profile_bedding") },
        { bedBottom = load("house_king_bed_06_bottom_wooden_slats_frame") },
        { sofaFront = load("house_modern_sofa_01_front_cushions_armrests") },
        { sofaBack = load("house_modern_sofa_02_back_upholstered_fabric") },
        { sofaLeft = load("house_modern_sofa_03_left_side_armrest") },
        { sofaRight = load("house_modern_sofa_04_right_side_armrest") },
        { sofaTop = load("house_modern_sofa_05_top_seats_and_backrest") },
        { sofaBottom = load("house_modern_sofa_06_bottom_dust_cover_feet") },
        { wardrobeFront = load("house_wooden_wardrobe_01_front_double_doors_handles") },
        { wardrobeLeft = load("house_wooden_wardrobe_02_left_side_wood_panel") },
        { wardrobeRight = load("house_wooden_wardrobe_03_right_side_wood_panel") },
        { wardrobeBack = load("house_wooden_wardrobe_04_back_plywood_panel") },
        { wardrobeTop = load("house_wooden_wardrobe_05_top_wood_surface") },
        { wardrobeBottom = load("house_wooden_wardrobe_06_bottom_base_wood") },
        { fridgeFront = load("house_refrigerator_01_front_stainless_steel_doors_dispenser") },
        { fridgeLeft = load("house_refrigerator_02_left_side_textured_metal") },
        { fridgeRight = load("house_refrigerator_03_right_side_textured_metal") },
        { fridgeBack = load("house_refrigerator_04_back_compressor_coils") },
        { fridgeTop = load("house_refrigerator_05_top_metal_hinge_covers") },
        { fridgeBottom = load("house_refrigerator_06_bottom_base_leveling_feet") },
        { tvFront = load("house_tv_unit_01_front_led_tv_and_cabinet") },
        { tvBack = load("house_tv_unit_02_back_vents_and_ports") },
        { tvLeft = load("house_tv_unit_03_left_side_wood_profile") },
        { tvRight = load("house_tv_unit_04_right_side_wood_profile") },
        { tvTop = load("house_tv_unit_05_top_wood_surface") },
        { tvBottom = load("house_tv_unit_06_bottom_cabinet_underside") },
        { kitchenFront = load("house_kitchen_counter_01_front_drawers_handles") },
        { kitchenTop = load("house_kitchen_counter_02_top_granite_sink_stove") },
        { kitchenLeft = load("house_kitchen_counter_03_left_side_panel") },
        { kitchenRight = load("house_kitchen_counter_04_right_side_panel") },
        { kitchenBackBacksplash = load("house_kitchen_counter_05_back_tiles_backsplash", repeatX = true, repeatY = false) },
        { kitchenBottom = load("house_kitchen_counter_06_bottom_base_plinth") },
        { deskFront = load("office_desk_pc_01_front_drawers_and_screen") },
        { deskTop = load("office_desk_pc_02_top_wood_keyboard_mousepad") },
        { deskBack = load("office_desk_pc_03_back_modesty_panel") },
        { deskSide = load("office_desk_pc_04_side_steel_and_wood") },
        { cncFront = load("factory_cnc_machine_01_front_door_control_screen") },
        { cncLeft = load("factory_cnc_machine_02_left_side_vents_warning") },
        { cncRight = load("factory_cnc_machine_03_right_side_vents_warning") },
        { cncBack = load("factory_cnc_machine_04_back_cables_fan") },
        { cncTop = load("factory_cnc_machine_05_top_steel_housing") }
    )

    val totalTextureCount: Int get() = loadSteps.size
    val isFullyLoaded: Boolean get() = nextLoadIdx >= loadSteps.size

    fun stepLoadBatch(batchSize: Int = 5): Pair<Int, Int> {
        val end = (nextLoadIdx + batchSize).coerceAtMost(loadSteps.size)
        while (nextLoadIdx < end) {
            loadSteps[nextLoadIdx].invoke()
            nextLoadIdx++
        }
        return nextLoadIdx to loadSteps.size
    }

    fun loadAllImmediate() {
        stepLoadBatch(loadSteps.size)
    }
}

/**
 * Zero-allocation OpenGL ES 2.0 multi-angle asset renderer for:
 * - Solid, multi-part 3D realistic furniture & props (zero alpha-cutout holes!)
 * - All 4 hand-controlled interactive gate mechanisms in strict local space (zero random flying gates!)
 * - Diverse modern GTA-style buildings with zero corner Z-fighting & clean painted interior walls!
 */
object MultiAnglePropDrawer {

    private const val FACE_FRONT = 1 shl 0  // +Z (verts 0..5)
    private const val FACE_BACK = 1 shl 1   // -Z (verts 6..11)
    private const val FACE_LEFT = 1 shl 2   // -X (verts 12..17)
    private const val FACE_RIGHT = 1 shl 3  // +X (verts 18..23)
    private const val FACE_TOP = 1 shl 4    // +Y (verts 24..29)
    private const val FACE_BOTTOM = 1 shl 5 // -Y (verts 30..35)

    // Pre-allocated scratch matrices (zero per-frame allocations)
    private val mBase = FloatArray(16)

    // =========================================================================
    // LOW-LEVEL MULTI-FACE BOX, LOCAL-PART & 3-PART SPLIT CYLINDER DRAWERS
    // =========================================================================

    private fun drawLocalBox(
        dc: GlDrawContext,
        lx: Float, ly: Float, lz: Float,
        sx: Float, sy: Float, sz: Float,
        texId: Int,
        uScale: Float = 1f, vScale: Float = 1f,
        r: Float = 1f, g: Float = 1f, b: Float = 1f,
        emissive: Float = 0f,
        specStrength: Float = 0.22f,
        specPower: Float = 18f,
        reflectStrength: Float = 0.06f
    ) {
        dc.bindVbo(dc.assets.cubeMesh)
        System.arraycopy(mBase, 0, dc.modelMatrix, 0, 16)
        Matrix.translateM(dc.modelMatrix, 0, lx, ly, lz)
        Matrix.scaleM(dc.modelMatrix, 0, sx, sy, sz)
        dc.commitDraw(
            texId = texId, uScale = uScale, vScale = vScale,
            r = r, g = g, b = b, alpha = 1f,
            emissive = emissive, specStrength = specStrength, specPower = specPower, reflectStrength = reflectStrength,
            vertCount = 36, firstVertex = 0, alphaTest = 0f
        )
    }

    private fun drawLocalSixFaceBox(
        dc: GlDrawContext,
        lx: Float, ly: Float, lz: Float,
        sx: Float, sy: Float, sz: Float,
        texRight: Int, texLeft: Int, texTop: Int, texBottom: Int, texFront: Int, texBack: Int,
        uFront: Float = 1f, vFront: Float = 1f,
        uSide: Float = 1f, vSide: Float = 1f,
        uTop: Float = 1f, vTop: Float = 1f,
        r: Float = 1f, g: Float = 1f, b: Float = 1f,
        emissive: Float = 0f,
        specStrength: Float = 0.24f,
        specPower: Float = 22f,
        reflectStrength: Float = 0.08f,
        cutoutFacesMask: Int = 0
    ) {
        dc.bindVbo(dc.assets.cubeMesh)
        System.arraycopy(mBase, 0, dc.modelMatrix, 0, 16)
        Matrix.translateM(dc.modelMatrix, 0, lx, ly, lz)
        Matrix.scaleM(dc.modelMatrix, 0, sx, sy, sz)

        fun drawFace(firstVert: Int, texId: Int, u: Float, v: Float, faceBit: Int) {
            val isCutout = (cutoutFacesMask and faceBit) != 0
            if (isCutout) GLES20.glDisable(GLES20.GL_CULL_FACE)
            dc.commitDraw(
                texId = texId, uScale = u, vScale = v,
                r = r, g = g, b = b, alpha = 1f,
                emissive = emissive, specStrength = specStrength, specPower = specPower, reflectStrength = reflectStrength,
                vertCount = 6, firstVertex = firstVert,
                alphaTest = if (isCutout) 0.45f else 0f
            )
            if (isCutout) GLES20.glEnable(GLES20.GL_CULL_FACE)
        }

        drawFace(0, texFront, uFront, vFront, FACE_FRONT)
        drawFace(6, texBack, uFront, vFront, FACE_BACK)
        drawFace(12, texLeft, uSide, vSide, FACE_LEFT)
        drawFace(18, texRight, uSide, vSide, FACE_RIGHT)
        drawFace(24, texTop, uTop, vTop, FACE_TOP)
        drawFace(30, texBottom, uTop, vTop, FACE_BOTTOM)
    }

    fun drawSixFaceBox(
        dc: GlDrawContext,
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        yawDeg: Float,
        texRight: Int,   // +X
        texLeft: Int,    // -X
        texTop: Int,     // +Y
        texBottom: Int,  // -Y
        texFront: Int,   // +Z
        texBack: Int,    // -Z
        uFront: Float = 1f, vFront: Float = 1f,
        uSide: Float = 1f, vSide: Float = 1f,
        uTop: Float = 1f, vTop: Float = 1f,
        r: Float = 1f, g: Float = 1f, b: Float = 1f,
        emissive: Float = 0f,
        specStrength: Float = 0.24f,
        specPower: Float = 22f,
        reflectStrength: Float = 0.08f,
        cutoutFacesMask: Int = 0
    ) {
        Matrix.setIdentityM(mBase, 0)
        Matrix.translateM(mBase, 0, x, y, z)
        if (yawDeg != 0f) Matrix.rotateM(mBase, 0, yawDeg, 0f, 1f, 0f)
        drawLocalSixFaceBox(
            dc, 0f, 0f, 0f, sx, sy, sz,
            texRight, texLeft, texTop, texBottom, texFront, texBack,
            uFront, vFront, uSide, vSide, uTop, vTop,
            r, g, b, emissive, specStrength, specPower, reflectStrength, cutoutFacesMask
        )
    }

    fun drawSplitCylinder(
        dc: GlDrawContext,
        x: Float, y: Float, z: Float,
        diameter: Float, height: Float,
        yawDeg: Float,
        texSide: Int,
        texTop: Int,
        texBottom: Int,
        uSide: Float = 1f,
        r: Float = 1f, g: Float = 1f, b: Float = 1f,
        specStrength: Float = 0.35f,
        specPower: Float = 26f,
        reflectStrength: Float = 0.12f
    ) {
        val seg = dc.assets.splitCylinderSegments
        dc.bindVbo(dc.assets.splitCylinderMesh)
        Matrix.setIdentityM(dc.modelMatrix, 0)
        Matrix.translateM(dc.modelMatrix, 0, x, y, z)
        if (yawDeg != 0f) Matrix.rotateM(dc.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        Matrix.scaleM(dc.modelMatrix, 0, diameter, height, diameter)

        // Side wrap [0 .. seg*6-1]
        dc.commitDraw(
            texSide, uSide, 1f, r, g, b, 1f,
            0f, specStrength, specPower, reflectStrength,
            vertCount = seg * 6, firstVertex = 0, alphaTest = 0f
        )
        // Top lid [seg*6 .. seg*9-1]
        dc.commitDraw(
            texTop, 1f, 1f, r, g, b, 1f,
            0f, specStrength, specPower, reflectStrength,
            vertCount = seg * 3, firstVertex = seg * 6, alphaTest = 0f
        )
        // Bottom base [seg*9 .. seg*12-1]
        dc.commitDraw(
            texBottom, 1f, 1f, r, g, b, 1f,
            0f, specStrength * 0.5f, 14f, 0.03f,
            vertCount = seg * 3, firstVertex = seg * 9, alphaTest = 0f
        )
    }

    // =========================================================================
    // ALL 12 REUSABLE SOLID 3D MULTI-ANGLE PROP FACTORIES (A .. L)
    // =========================================================================

    /** A. AC Outdoor Unit (0.85m W x 0.58m H x 0.36m D) */
    fun drawAcOutdoorUnit(dc: GlDrawContext, x: Float, yBottom: Float, z: Float, yawDeg: Float = 0f) {
        val m = dc.assets.multiAngle ?: return
        drawSixFaceBox(
            dc = dc, x = x, y = yBottom + 0.29f, z = z,
            sx = 0.85f, sy = 0.58f, sz = 0.36f, yawDeg = yawDeg,
            texRight = m.acRight, texLeft = m.acLeft,
            texTop = m.acTop, texBottom = m.acBottom,
            texFront = m.acFront, texBack = m.acBack,
            specStrength = 0.38f, specPower = 26f, reflectStrength = 0.12f
        )
    }

    /** B. Rooftop Water Tank (Cylinder r=0.68m, h=1.45m on 0.45m dark steel stand) */
    fun drawRooftopWaterTank(dc: GlDrawContext, x: Float, yRoof: Float, z: Float, yawDeg: Float = 0f) {
        val m = dc.assets.multiAngle ?: return
        val standH = 0.45f
        val tankH = 1.45f
        val tankD = 1.36f
        dc.bindVbo(dc.assets.cubeMesh)
        dc.drawBox(x, yRoof + standH - 0.04f, z, 1.38f, 0.08f, 1.38f, dc.assets.texWhite, 1f, 1f, 0.16f, 0.18f, 0.20f, 0f, 0.45f, 24f, 0.12f)
        for (lx in floatArrayOf(-0.56f, 0.56f)) {
            for (lz in floatArrayOf(-0.56f, 0.56f)) {
                dc.drawBox(x + lx, yRoof + standH * 0.5f, z + lz, 0.09f, standH, 0.09f, dc.assets.texWhite, 1f, 1f, 0.14f, 0.16f, 0.18f, 0f, 0.45f, 24f, 0.10f)
            }
        }
        drawSplitCylinder(
            dc = dc, x = x, y = yRoof + standH + tankH * 0.5f, z = z,
            diameter = tankD, height = tankH, yawDeg = yawDeg,
            texSide = m.waterTankSide, texTop = m.waterTankTop, texBottom = m.waterTankBottom,
            uSide = 2f, specStrength = 0.28f, specPower = 20f, reflectStrength = 0.08f
        )
    }

    /**
     * C. Solid Multi-Part 3D King Size Bed (2.00m W x 1.15m H x 2.15m D, headboard flush at local -Z).
     * Zero alpha-cutout holes or floating boards!
     */
    fun drawKingBed(dc: GlDrawContext, x: Float, yFloor: Float, z: Float, yawDeg: Float = 0f) {
        val m = dc.assets.multiAngle ?: return
        val w = dc.assets.texWhite
        Matrix.setIdentityM(mBase, 0)
        Matrix.translateM(mBase, 0, x, yFloor, z)
        if (yawDeg != 0f) Matrix.rotateM(mBase, 0, yawDeg, 0f, 1f, 0f)

        // 4 sturdy dark-walnut corner feet
        for (fx in floatArrayOf(-0.92f, 0.92f)) {
            for (fz in floatArrayOf(-0.98f, 0.98f)) {
                drawLocalBox(dc, fx, 0.06f, fz, 0.10f, 0.12f, 0.10f, w, r = 0.22f, g = 0.14f, b = 0.09f, specStrength = 0.25f)
            }
        }
        // Solid walnut platform base frame
        drawLocalBox(dc, 0f, 0.22f, 0f, 2.00f, 0.22f, 2.12f, m.wardrobeTop, 2f, 2f, 0.72f, 0.58f, 0.45f, specStrength = 0.26f)
        // Thick plush quilted mattress + duvet (top mapped cleanly with m.bedTop!)
        drawLocalSixFaceBox(
            dc, 0f, 0.49f, 0.03f, 1.92f, 0.32f, 1.96f,
            texRight = w, texLeft = w, texTop = m.bedTop, texBottom = m.bedBottom, texFront = w, texBack = w,
            r = 0.94f, g = 0.93f, b = 0.91f, specStrength = 0.10f, specPower = 10f, reflectStrength = 0.01f
        )
        // Folded charcoal-slate throw blanket across foot half of mattress
        drawLocalBox(dc, 0f, 0.51f, 0.46f, 1.94f, 0.30f, 0.94f, w, r = 0.24f, g = 0.29f, b = 0.36f, specStrength = 0.08f, specPower = 8f, reflectStrength = 0.01f)
        // Tall solid tufted headboard flush at local -Z (-1.00m)
        drawLocalBox(dc, 0f, 0.62f, -1.00f, 2.02f, 1.12f, 0.14f, m.wardrobeTop, 1.5f, 1f, 0.46f, 0.32f, 0.22f, specStrength = 0.22f)
        drawLocalBox(dc, 0f, 0.80f, -0.92f, 1.84f, 0.68f, 0.06f, w, r = 0.34f, g = 0.32f, b = 0.30f, specStrength = 0.10f)
        // Low solid wooden footboard at local +Z (+1.03m)
        drawLocalBox(dc, 0f, 0.26f, 1.03f, 2.00f, 0.40f, 0.08f, m.wardrobeTop, 2f, 0.6f, 0.52f, 0.38f, 0.26f, specStrength = 0.26f)
        // Two plump 3D pillows resting near headboard
        drawLocalBox(dc, -0.48f, 0.72f, -0.68f, 0.72f, 0.16f, 0.42f, w, r = 0.96f, g = 0.95f, b = 0.93f, specStrength = 0.08f)
        drawLocalBox(dc,  0.48f, 0.72f, -0.68f, 0.72f, 0.16f, 0.42f, w, r = 0.96f, g = 0.95f, b = 0.93f, specStrength = 0.08f)
    }

    /**
     * D. Solid Multi-Part 3D Modern Sofa (2.24m W x 0.90m H x 0.92m D, back flush at local -Z).
     * Solid armrests, plush cushions, wooden legs & throw pillows — zero cutout holes!
     */
    fun drawModernSofa(dc: GlDrawContext, x: Float, yFloor: Float, z: Float, yawDeg: Float = 0f) {
        val m = dc.assets.multiAngle ?: return
        val w = dc.assets.texWhite
        Matrix.setIdentityM(mBase, 0)
        Matrix.translateM(mBase, 0, x, yFloor, z)
        if (yawDeg != 0f) Matrix.rotateM(mBase, 0, yawDeg, 0f, 1f, 0f)

        // 4 tapered walnut wood legs
        for (fx in floatArrayOf(-1.00f, 1.00f)) {
            for (fz in floatArrayOf(-0.36f, 0.36f)) {
                drawLocalBox(dc, fx, 0.06f, fz, 0.08f, 0.12f, 0.08f, w, r = 0.30f, g = 0.20f, b = 0.12f, specStrength = 0.28f)
            }
        }
        // Solid lower wooden & upholstered base frame
        drawLocalBox(dc, 0f, 0.19f, 0f, 2.20f, 0.16f, 0.88f, m.wardrobeTop, 2f, 1f, 0.56f, 0.42f, 0.28f, specStrength = 0.24f)
        // Three segmented plush seat cushions (modern charcoal-slate fabric)
        val fabricR = 0.32f; val fabricG = 0.36f; val fabricB = 0.42f
        for (cx in floatArrayOf(-0.61f, 0f, 0.61f)) {
            drawLocalBox(dc, cx, 0.36f, 0.07f, 0.59f, 0.19f, 0.72f, w, r = fabricR, g = fabricG, b = fabricB, specStrength = 0.08f, specPower = 8f, reflectStrength = 0.01f)
        }
        // Solid upholstered backrest flush at local -Z (-0.34m) + 3 back cushions
        drawLocalBox(dc, 0f, 0.54f, -0.35f, 2.22f, 0.68f, 0.20f, m.sofaBack, 1f, 1f, 0.82f, 0.85f, 0.88f, specStrength = 0.08f, specPower = 8f, reflectStrength = 0.01f)
        for (cx in floatArrayOf(-0.61f, 0f, 0.61f)) {
            drawLocalBox(dc, cx, 0.63f, -0.22f, 0.58f, 0.40f, 0.16f, w, r = fabricR * 1.08f, g = fabricG * 1.08f, b = fabricB * 1.08f, specStrength = 0.08f)
        }
        // Solid padded Left & Right Armrests
        drawLocalBox(dc, -1.01f, 0.44f, 0f, 0.22f, 0.44f, 0.90f, w, r = fabricR * 0.95f, g = fabricG * 0.95f, b = fabricB * 0.95f, specStrength = 0.08f)
        drawLocalBox(dc,  1.01f, 0.44f, 0f, 0.22f, 0.44f, 0.90f, w, r = fabricR * 0.95f, g = fabricG * 0.95f, b = fabricB * 0.95f, specStrength = 0.08f)
        // Two warm ochre accent throw pillows in corners
        drawLocalBox(dc, -0.78f, 0.57f, -0.06f, 0.26f, 0.28f, 0.14f, w, r = 0.78f, g = 0.58f, b = 0.28f, specStrength = 0.08f)
        drawLocalBox(dc,  0.78f, 0.57f, -0.06f, 0.26f, 0.28f, 0.14f, w, r = 0.78f, g = 0.58f, b = 0.28f, specStrength = 0.08f)
    }

    /** E. Solid Wooden Wardrobe (1.64m W x 2.15m H x 0.62m D, back flush at local -Z) */
    fun drawWoodenWardrobe(dc: GlDrawContext, x: Float, yFloor: Float, z: Float, yawDeg: Float = 0f) {
        val m = dc.assets.multiAngle ?: return
        drawSixFaceBox(
            dc = dc, x = x, y = yFloor + 1.075f, z = z,
            sx = 1.64f, sy = 2.15f, sz = 0.62f, yawDeg = yawDeg,
            texRight = m.wardrobeRight, texLeft = m.wardrobeLeft,
            texTop = m.wardrobeTop, texBottom = m.wardrobeBottom,
            texFront = m.wardrobeFront, texBack = m.wardrobeBack,
            specStrength = 0.28f, specPower = 22f, reflectStrength = 0.06f
        )
    }

    /** F. Solid Double-Door Stainless Refrigerator (0.84m W x 1.82m H x 0.76m D, back flush at local -Z) */
    fun drawRefrigerator(dc: GlDrawContext, x: Float, yFloor: Float, z: Float, yawDeg: Float = 0f) {
        val m = dc.assets.multiAngle ?: return
        drawSixFaceBox(
            dc = dc, x = x, y = yFloor + 0.91f, z = z,
            sx = 0.84f, sy = 1.82f, sz = 0.76f, yawDeg = yawDeg,
            texRight = m.fridgeRight, texLeft = m.fridgeLeft,
            texTop = m.fridgeTop, texBottom = m.fridgeBottom,
            texFront = m.fridgeFront, texBack = m.fridgeBack,
            specStrength = 0.74f, specPower = 44f, reflectStrength = 0.30f
        )
    }

    /**
     * G. Solid Multi-Part 3D TV Console & Slim Flatscreen LED TV (2.04m W x 1.52m H x 0.48m D, back flush at local -Z).
     * Replaces the old broken alpha-cutout TV box!
     */
    fun drawTvUnit(dc: GlDrawContext, x: Float, yFloor: Float, z: Float, yawDeg: Float = 0f) {
        val m = dc.assets.multiAngle ?: return
        val w = dc.assets.texWhite
        Matrix.setIdentityM(mBase, 0)
        Matrix.translateM(mBase, 0, x, yFloor, z)
        if (yawDeg != 0f) Matrix.rotateM(mBase, 0, yawDeg, 0f, 1f, 0f)

        // 4 matte-black steel legs
        for (fx in floatArrayOf(-0.92f, 0.92f)) {
            for (fz in floatArrayOf(-0.18f, 0.18f)) {
                drawLocalBox(dc, fx, 0.06f, fz, 0.06f, 0.12f, 0.06f, w, r = 0.12f, g = 0.13f, b = 0.15f, specStrength = 0.45f)
            }
        }
        // Solid Walnut Media Console Cabinet
        drawLocalSixFaceBox(
            dc, 0f, 0.35f, 0f, 2.04f, 0.46f, 0.46f,
            texRight = m.tvRight, texLeft = m.tvLeft, texTop = m.tvTop, texBottom = m.tvBottom,
            texFront = m.wardrobeTop, texBack = m.tvBack,
            uFront = 2.0f, vFront = 0.8f,
            r = 0.84f, g = 0.76f, b = 0.68f, specStrength = 0.28f, specPower = 22f, reflectStrength = 0.06f
        )
        // Recessed dark AV niche & matte-black soundbar on console top
        drawLocalBox(dc, 0f, 0.34f, 0.22f, 0.64f, 0.30f, 0.04f, w, r = 0.10f, g = 0.10f, b = 0.12f, specStrength = 0.30f)
        drawLocalBox(dc, 0f, 0.61f, 0.10f, 0.86f, 0.06f, 0.09f, w, r = 0.12f, g = 0.13f, b = 0.15f, specStrength = 0.42f)

        // TV Pedestal Base & Neck Stand
        drawLocalBox(dc, 0f, 0.60f, -0.06f, 0.56f, 0.04f, 0.24f, w, r = 0.10f, g = 0.11f, b = 0.13f, specStrength = 0.55f)
        drawLocalBox(dc, 0f, 0.72f, -0.09f, 0.14f, 0.22f, 0.07f, w, r = 0.10f, g = 0.11f, b = 0.13f, specStrength = 0.55f)

        // Sleek 3D 65-inch LED Flatscreen TV Chassis + Glossy Display Panel
        drawLocalBox(dc, 0f, 1.14f, -0.09f, 1.56f, 0.88f, 0.06f, w, r = 0.08f, g = 0.09f, b = 0.11f, specStrength = 0.55f, specPower = 32f, reflectStrength = 0.16f)
        // Dark reflective OLED glass screen facing +Z (subtle ambient glow + tiny red standby LED)
        drawLocalBox(dc, 0f, 1.14f, -0.052f, 1.48f, 0.80f, 0.012f, w, r = 0.12f, g = 0.17f, b = 0.24f, emissive = 0.22f, specStrength = 0.92f, specPower = 56f, reflectStrength = 0.46f)
        drawLocalBox(dc, 0f, 0.72f, -0.055f, 0.03f, 0.02f, 0.015f, w, r = 0.95f, g = 0.14f, b = 0.12f, emissive = 0.95f)
    }

    /** H. Solid Modular Kitchen Counter (2.70m W x 0.90m H x 0.70m D) + Flush Wall Backsplash + Upper Wall Cabinets */
    fun drawKitchenCounter(dc: GlDrawContext, x: Float, yFloor: Float, z: Float, yawDeg: Float = 0f) {
        val m = dc.assets.multiAngle ?: return
        Matrix.setIdentityM(mBase, 0)
        Matrix.translateM(mBase, 0, x, yFloor, z)
        if (yawDeg != 0f) Matrix.rotateM(mBase, 0, yawDeg, 0f, 1f, 0f)

        // Lower base cabinet with granite sink/stove top
        drawLocalSixFaceBox(
            dc, 0f, 0.45f, 0f, 2.70f, 0.90f, 0.70f,
            texRight = m.kitchenRight, texLeft = m.kitchenLeft,
            texTop = m.kitchenTop, texBottom = m.kitchenBottom,
            texFront = m.kitchenFront, texBack = m.kitchenBackBacksplash,
            specStrength = 0.42f, specPower = 30f, reflectStrength = 0.14f
        )
        // Flush tiled wall backsplash right above rear edge of counter (local z = -0.32m)
        drawLocalBox(dc, 0f, 1.32f, -0.32f, 2.70f, 0.84f, 0.05f, m.kitchenBackBacksplash, 2.5f, 1f, 0.98f, 0.98f, 0.98f, emissive = 0.04f, specStrength = 0.36f, specPower = 24f, reflectStrength = 0.10f)
        // Modern overhead wall kitchen cabinets mounted flush to wall above backsplash!
        drawLocalSixFaceBox(
            dc, 0f, 2.08f, -0.17f, 2.70f, 0.68f, 0.36f,
            texRight = m.kitchenRight, texLeft = m.kitchenLeft,
            texTop = m.kitchenBottom, texBottom = m.kitchenBottom,
            texFront = m.kitchenFront, texBack = m.kitchenBottom,
            uFront = 1.0f, vFront = 0.7f,
            r = 0.94f, g = 0.93f, b = 0.91f, specStrength = 0.30f, specPower = 22f, reflectStrength = 0.08f
        )
    }

    /**
     * I. Solid Multi-Part 3D Office Desk, Desktop PC, Monitor, Keyboard, Tower & Office Chair
     * (1.52m W x 0.76m D, back flush at local -Z). Completely replaces the old broken cutout PC box!
     */
    fun drawOfficeDeskPc(dc: GlDrawContext, x: Float, yFloor: Float, z: Float, yawDeg: Float = 0f) {
        val m = dc.assets.multiAngle ?: return
        val w = dc.assets.texWhite
        Matrix.setIdentityM(mBase, 0)
        Matrix.translateM(mBase, 0, x, yFloor, z)
        if (yawDeg != 0f) Matrix.rotateM(mBase, 0, yawDeg, 0f, 1f, 0f)

        // Solid Oak Tabletop (y = 0.75m, top face mapped cleanly with m.deskTop!)
        drawLocalSixFaceBox(
            dc, 0f, 0.75f, 0f, 1.52f, 0.06f, 0.76f,
            texRight = m.wardrobeTop, texLeft = m.wardrobeTop,
            texTop = m.deskTop, texBottom = m.deskBack,
            texFront = m.wardrobeTop, texBack = m.wardrobeTop,
            r = 0.94f, g = 0.92f, b = 0.89f, specStrength = 0.28f, specPower = 22f, reflectStrength = 0.06f
        )
        // Right 3-Drawer Pedestal Cabinet
        drawLocalSixFaceBox(
            dc, 0.50f, 0.36f, -0.02f, 0.44f, 0.72f, 0.68f,
            texRight = m.deskSide, texLeft = m.deskSide,
            texTop = m.deskBack, texBottom = m.deskBack,
            texFront = m.kitchenFront, texBack = m.deskBack,
            r = 0.82f, g = 0.80f, b = 0.78f, specStrength = 0.22f
        )
        // Left matte-black steel rectangular frame legs + Rear modesty panel
        drawLocalBox(dc, -0.71f, 0.36f, -0.30f, 0.05f, 0.72f, 0.05f, w, r = 0.14f, g = 0.15f, b = 0.17f, specStrength = 0.45f)
        drawLocalBox(dc, -0.71f, 0.36f,  0.30f, 0.05f, 0.72f, 0.05f, w, r = 0.14f, g = 0.15f, b = 0.17f, specStrength = 0.45f)
        drawLocalBox(dc, -0.71f, 0.04f,  0.00f, 0.05f, 0.05f, 0.64f, w, r = 0.14f, g = 0.15f, b = 0.17f, specStrength = 0.45f)
        drawLocalBox(dc, -0.18f, 0.50f, -0.34f, 1.00f, 0.44f, 0.04f, m.deskBack, 1f, 1f, 0.88f, 0.86f, 0.84f, specStrength = 0.18f)

        // Real 3D Widescreen Desktop Monitor on Stand
        drawLocalBox(dc, -0.10f, 0.79f, -0.20f, 0.26f, 0.025f, 0.18f, w, r = 0.12f, g = 0.13f, b = 0.15f, specStrength = 0.50f)
        drawLocalBox(dc, -0.10f, 0.90f, -0.24f, 0.06f, 0.22f, 0.04f, w, r = 0.12f, g = 0.13f, b = 0.15f, specStrength = 0.50f)
        drawLocalBox(dc, -0.10f, 1.08f, -0.21f, 0.68f, 0.40f, 0.035f, w, r = 0.10f, g = 0.11f, b = 0.13f, specStrength = 0.55f)
        // Bright glowing realistic desktop OS monitor screen facing +Z
        drawLocalBox(dc, -0.10f, 1.08f, -0.188f, 0.63f, 0.35f, 0.01f, w, r = 0.22f, g = 0.44f, b = 0.68f, emissive = 0.48f, specStrength = 0.85f, specPower = 48f, reflectStrength = 0.32f)

        // 3D Mechanical Keyboard, Mouse & Matte-Black PC Tower on desk right
        drawLocalBox(dc, -0.12f, 0.792f, 0.08f, 0.42f, 0.024f, 0.15f, w, r = 0.14f, g = 0.15f, b = 0.18f, specStrength = 0.35f)
        drawLocalBox(dc,  0.20f, 0.790f, 0.10f, 0.07f, 0.024f, 0.11f, w, r = 0.16f, g = 0.17f, b = 0.20f, specStrength = 0.42f)
        // PC Tower standing cleanly beside left leg
        drawLocalBox(dc, -0.52f, 0.24f, -0.08f, 0.21f, 0.48f, 0.44f, w, r = 0.12f, g = 0.13f, b = 0.16f, specStrength = 0.46f)
        drawLocalBox(dc, -0.52f, 0.42f,  0.145f, 0.06f, 0.018f, 0.01f, w, r = 0.22f, g = 0.84f, b = 0.48f, emissive = 0.85f)

        // Tucked-in Ergonomic Office Chair (seat + backrest + central gas-lift column + base)
        drawLocalBox(dc, -0.10f, 0.04f, 0.34f, 0.46f, 0.05f, 0.46f, w, r = 0.14f, g = 0.15f, b = 0.17f, specStrength = 0.45f)
        drawLocalBox(dc, -0.10f, 0.24f, 0.34f, 0.07f, 0.38f, 0.07f, w, r = 0.22f, g = 0.24f, b = 0.27f, specStrength = 0.55f)
        drawLocalBox(dc, -0.10f, 0.45f, 0.32f, 0.46f, 0.07f, 0.44f, w, r = 0.18f, g = 0.20f, b = 0.24f, specStrength = 0.14f)
        drawLocalBox(dc, -0.10f, 0.74f, 0.52f, 0.44f, 0.52f, 0.06f, w, r = 0.18f, g = 0.20f, b = 0.24f, specStrength = 0.14f)
    }

    /** J. Factory Heavy CNC Machine (2.5m W x 2.2m H x 1.8m D) */
    fun drawFactoryCncMachine(dc: GlDrawContext, x: Float, yFloor: Float, z: Float, yawDeg: Float = 0f) {
        val m = dc.assets.multiAngle ?: return
        drawSixFaceBox(
            dc = dc, x = x, y = yFloor + 1.10f, z = z,
            sx = 2.50f, sy = 2.20f, sz = 1.80f, yawDeg = yawDeg,
            texRight = m.cncRight, texLeft = m.cncLeft,
            texTop = m.cncTop, texBottom = m.cncTop,
            texFront = m.cncFront, texBack = m.cncBack,
            emissive = 0.03f, specStrength = 0.46f, specPower = 32f, reflectStrength = 0.16f
        )
    }

    /** K. Factory Wooden Cargo Crate (1.1m x 1.1m x 1.1m, stackable) */
    fun drawCargoCrate(dc: GlDrawContext, x: Float, yFloor: Float, z: Float, yawDeg: Float = 0f, size: Float = 1.1f) {
        val m = dc.assets.multiAngle ?: return
        dc.bindVbo(dc.assets.cubeMesh)
        dc.drawMeshTransformed(
            x = x, y = yFloor + size * 0.5f, z = z,
            sx = size, sy = size, sz = size,
            yawDeg = yawDeg, pitchDeg = 0f, rollDeg = 0f,
            texId = m.cargoCrate, uScale = 1f, vScale = 1f,
            r = 0.96f, g = 0.95f, b = 0.93f, alpha = 1f,
            emissive = 0f, specStrength = 0.14f, specPower = 12f, reflectStrength = 0.02f,
            mesh = dc.assets.cubeMesh
        )
    }

    /** L. Factory Steel Oil Drum (Outward-facing 3-part Split Cylinder r=0.30m, h=0.92m) */
    fun drawSteelOilDrum(dc: GlDrawContext, x: Float, yFloor: Float, z: Float, yawDeg: Float = 0f) {
        val m = dc.assets.multiAngle ?: return
        drawSplitCylinder(
            dc = dc, x = x, y = yFloor + 0.46f, z = z,
            diameter = 0.60f, height = 0.92f, yawDeg = yawDeg,
            texSide = m.oilDrumSide, texTop = m.oilDrumTop, texBottom = m.oilDrumBottom,
            uSide = 1f, specStrength = 0.52f, specPower = 34f, reflectStrength = 0.18f
        )
    }

    // =========================================================================
    // HAND-CONTROLLED INTERACTIVE GATE SYSTEM — STRICT LOCAL-SPACE TRANSFORMS
    // (ZERO double-flipping, ZERO flying gates, works identically facing +Z or -Z!)
    // =========================================================================

    fun drawInteractiveGate(dc: GlDrawContext, gate: InteractiveGate, nightFactor: Float) {
        val m = dc.assets.multiAngle ?: return
        val p = gate.openProgress.coerceIn(0f, 1f)
        val w = gate.width
        val h = gate.height
        val baseY = gate.y
        val baseYaw = if (gate.facingDirZ >= 0f) 0f else 180f

        // Single Gate Local Space: Origin = doorway center at floor level, +Z = outward, -Z = inward!
        Matrix.setIdentityM(mBase, 0)
        Matrix.translateM(mBase, 0, gate.x, baseY, gate.z)
        if (baseYaw != 0f) Matrix.rotateM(mBase, 0, baseYaw, 0f, 1f, 0f)

        // Charcoal steel side gate posts framing the opening cleanly
        val postR = if (gate.mechanism == GateMechanism.VILLA_DOUBLE_HINGE) 0.22f else 0.13f
        drawLocalBox(dc, -w * 0.5f - postR * 0.5f, h * 0.5f, 0f, postR, h + 0.08f, postR + 0.10f, dc.assets.texConcrete, 1f, 2f, 0.24f, 0.26f, 0.29f, specStrength = 0.26f)
        drawLocalBox(dc,  w * 0.5f + postR * 0.5f, h * 0.5f, 0f, postR, h + 0.08f, postR + 0.10f, dc.assets.texConcrete, 1f, 2f, 0.24f, 0.26f, 0.29f, specStrength = 0.26f)

        when (gate.mechanism) {
            GateMechanism.HOUSE_SINGLE_HINGE -> {
                // Hinged at local left post (-w/2, 0, 0); swings +95 deg INWARD into building (-Z)!
                drawLocalHingedLeaf(
                    dc = dc,
                    hingeLocalX = -w * 0.5f,
                    leafOffsetLocalX = w * 0.5f,
                    leafW = w, leafH = h, leafThick = 0.07f,
                    swingDeg = p * 95f,
                    texFront = m.gateHouseFront, texBack = m.gateHouseBack,
                    alphaCutout = true
                )
            }

            GateMechanism.VILLA_DOUBLE_HINGE -> {
                // Double-wing wood & iron gate: Left swings +92 deg INWARD, Right swings -92 deg INWARD!
                val leafW = w * 0.5f - 0.015f
                drawLocalHingedLeaf(
                    dc = dc,
                    hingeLocalX = -w * 0.5f,
                    leafOffsetLocalX = leafW * 0.5f,
                    leafW = leafW, leafH = h, leafThick = 0.08f,
                    swingDeg = p * 92f,
                    texFront = m.gateVillaFront, texBack = m.gateVillaBack,
                    alphaCutout = false
                )
                drawLocalHingedLeaf(
                    dc = dc,
                    hingeLocalX = w * 0.5f,
                    leafOffsetLocalX = -leafW * 0.5f,
                    leafW = leafW, leafH = h, leafThick = 0.08f,
                    swingDeg = -p * 92f,
                    texFront = m.gateVillaFront, texBack = m.gateVillaBack,
                    alphaCutout = false
                )
            }

            GateMechanism.APARTMENT_SLIDING -> {
                // Inner track at local z = -0.18f; slides cleanly right (+X) behind the right front wall segment!
                val slideMax = w * 0.94f
                val slideX = p * slideMax
                val trackZ = -0.18f
                // Bottom steel rail & top guide beam
                drawLocalBox(dc, slideMax * 0.5f, 0.03f, trackZ, w + slideMax + 0.30f, 0.06f, 0.14f, dc.assets.texWhite, 1f, 1f, 0.18f, 0.20f, 0.23f, specStrength = 0.55f)
                drawLocalBox(dc, slideMax * 0.5f, h + 0.05f, trackZ, w + slideMax + 0.30f, 0.10f, 0.14f, dc.assets.texWhite, 1f, 1f, 0.18f, 0.20f, 0.23f, specStrength = 0.55f)
                // Sliding See-Through Iron Gate Leaf
                drawLocalSixFaceBox(
                    dc, slideX, h * 0.5f, trackZ, w, h, 0.07f,
                    texRight = dc.assets.texWhite, texLeft = dc.assets.texWhite,
                    texTop = dc.assets.texWhite, texBottom = dc.assets.texWhite,
                    texFront = m.gateAptSliding, texBack = m.gateAptSliding,
                    r = 0.95f, g = 0.96f, b = 0.98f,
                    emissive = nightFactor * 0.04f,
                    specStrength = 0.50f, specPower = 30f, reflectStrength = 0.16f,
                    cutoutFacesMask = FACE_FRONT or FACE_BACK
                )
            }

            GateMechanism.FACTORY_ROLLUP_SHUTTER -> {
                // Top Steel Roll-Drum Hood Box above doorway
                drawLocalBox(dc, 0f, h + 0.18f, 0.06f, w + 0.34f, 0.36f, 0.38f, dc.assets.texConcrete, 2f, 0.5f, 0.24f, 0.26f, 0.30f, specStrength = 0.40f)
                // Slatted Steel Curtain shrinking strictly upward toward y = h
                val scaleY = (1f - p * 0.88f).coerceIn(0.12f, 1f)
                val curH = h * scaleY
                val centerY = h - curH * 0.5f
                val vTile = (scaleY * 1.4f).coerceAtLeast(0.18f)
                drawLocalSixFaceBox(
                    dc, 0f, centerY, 0f, w, curH, 0.08f,
                    texRight = dc.assets.texConcrete, texLeft = dc.assets.texConcrete,
                    texTop = dc.assets.texConcrete, texBottom = dc.assets.texConcrete,
                    texFront = m.gateShutterFront, texBack = m.gateShutterBack,
                    uFront = (w / 3.4f).coerceAtLeast(1f), vFront = vTile,
                    r = 0.93f, g = 0.94f, b = 0.96f,
                    emissive = nightFactor * 0.03f,
                    specStrength = 0.44f, specPower = 26f, reflectStrength = 0.14f
                )
            }
        }
    }

    private fun drawLocalHingedLeaf(
        dc: GlDrawContext,
        hingeLocalX: Float,
        leafOffsetLocalX: Float,
        leafW: Float, leafH: Float, leafThick: Float,
        swingDeg: Float,
        texFront: Int, texBack: Int,
        alphaCutout: Boolean
    ) {
        dc.bindVbo(dc.assets.cubeMesh)
        System.arraycopy(mBase, 0, dc.modelMatrix, 0, 16)
        Matrix.translateM(dc.modelMatrix, 0, hingeLocalX, leafH * 0.5f, 0f)
        if (swingDeg != 0f) Matrix.rotateM(dc.modelMatrix, 0, swingDeg, 0f, 1f, 0f)
        Matrix.translateM(dc.modelMatrix, 0, leafOffsetLocalX, 0f, 0f)
        Matrix.scaleM(dc.modelMatrix, 0, leafW, leafH, leafThick)

        val alphaTest = if (alphaCutout) 0.45f else 0f
        if (alphaCutout) GLES20.glDisable(GLES20.GL_CULL_FACE)
        dc.commitDraw(texFront, 1f, 1f, 0.96f, 0.96f, 0.96f, 1f, 0f, 0.44f, 28f, 0.14f, vertCount = 6, firstVertex = 0, alphaTest = alphaTest)
        dc.commitDraw(texBack, 1f, 1f, 0.94f, 0.94f, 0.94f, 1f, 0f, 0.40f, 24f, 0.12f, vertCount = 6, firstVertex = 6, alphaTest = alphaTest)
        if (alphaCutout) GLES20.glEnable(GLES20.GL_CULL_FACE)
        // Dark steel leaf perimeter edges
        dc.commitDraw(dc.assets.texWhite, 1f, 1f, 0.16f, 0.17f, 0.20f, 1f, 0f, 0.45f, 24f, 0.12f, vertCount = 24, firstVertex = 12, alphaTest = 0f)
    }

    // =========================================================================
    // DIVERSE MODERN GTA-STYLE CITY SKYLINE BUILDINGS (ZERO Z-FIGHTING!)
    // =========================================================================

    fun drawModernCityBuilding(dc: GlDrawContext, b: BuildingBlock, nightFactor: Float) {
        val m = dc.assets.multiAngle ?: return
        val vSeed = b.variantSeed
        // Clean raised ground concrete curb plinth (+0.14m above street) -> zero ground Z-fighting
        dc.bindVbo(dc.assets.cubeMesh)
        dc.drawBox(b.x, 0.07f, b.z, b.width + 0.50f, 0.14f, b.depth + 0.50f, m.bldAptRoof, 2f, 2f, 0.70f, 0.72f, 0.74f, 0f, 0.12f, 12f, 0.03f)

        val baseY = 0.14f
        val bodyH = (b.height - baseY).coerceAtLeast(3.8f)

        when (b.style) {
            BuildingStyle.GLASS_SKYSCRAPER -> {
                // Varied Modern Glass Skyscrapers with podiums, tiered setbacks, and distinct silhouettes!
                val lobbyH = 4.4f.coerceAtMost(bodyH * 0.28f)
                val midH = if (vSeed % 3 == 0 && bodyH > 26f) (bodyH - lobbyH) * 0.65f else (bodyH - lobbyH)
                val topTierH = (bodyH - lobbyH - midH).coerceAtLeast(0f)

                // Ground stone/glass podium lobby
                drawSixFaceBox(
                    dc = dc, x = b.x, y = baseY + lobbyH * 0.5f, z = b.z,
                    sx = b.width + 0.36f, sy = lobbyH, sz = b.depth + 0.36f, yawDeg = 0f,
                    texRight = m.bldOfficeSide, texLeft = m.bldOfficeSide,
                    texTop = m.bldAptRoof, texBottom = m.bldAptRoof,
                    texFront = m.bldOfficeLobby, texBack = m.bldOfficeLobby,
                    uFront = (b.width / 5.8f).coerceAtLeast(1f), vFront = 1f,
                    uSide = (b.depth / 4.8f).coerceAtLeast(1f), vSide = 1f,
                    r = b.tintR * 0.96f, g = b.tintG * 0.96f, b = b.tintB * 0.98f,
                    emissive = 0.04f + nightFactor * 0.28f,
                    specStrength = 0.72f, specPower = 44f, reflectStrength = 0.38f
                )
                // Main glass curtain wall shaft
                drawSixFaceBox(
                    dc = dc, x = b.x, y = baseY + lobbyH + midH * 0.5f, z = b.z,
                    sx = b.width, sy = midH, sz = b.depth, yawDeg = 0f,
                    texRight = m.bldOfficeSide, texLeft = m.bldOfficeSide,
                    texTop = m.bldAptRoof, texBottom = m.bldAptRoof,
                    texFront = m.bldOfficeFront, texBack = m.bldOfficeFront,
                    uFront = (b.width / 4.6f).coerceAtLeast(1.8f), vFront = (midH / 4.2f).coerceAtLeast(1.8f),
                    uSide = (b.depth / 4.6f).coerceAtLeast(1.8f), vSide = (midH / 4.2f).coerceAtLeast(1.8f),
                    uTop = 3f, vTop = 3f,
                    r = b.tintR, g = b.tintG, b = b.tintB,
                    emissive = 0.05f + nightFactor * 0.36f,
                    specStrength = 0.86f, specPower = 54f, reflectStrength = 0.46f
                )

                var crownRoofY = baseY + lobbyH + midH
                var crownW = b.width
                var crownD = b.depth

                // Optional stepped upper glass setback tier for architectural skyline variety!
                if (topTierH > 3.5f) {
                    crownW = b.width * 0.76f
                    crownD = b.depth * 0.76f
                    drawSixFaceBox(
                        dc = dc, x = b.x, y = crownRoofY + topTierH * 0.5f, z = b.z,
                        sx = crownW, sy = topTierH, sz = crownD, yawDeg = 0f,
                        texRight = m.bldOfficeSide, texLeft = m.bldOfficeSide,
                        texTop = m.bldAptRoof, texBottom = m.bldAptRoof,
                        texFront = m.bldOfficeFront, texBack = m.bldOfficeFront,
                        uFront = (crownW / 4.6f).coerceAtLeast(1.4f), vFront = (topTierH / 4.2f).coerceAtLeast(1.2f),
                        uSide = (crownD / 4.6f).coerceAtLeast(1.4f), vSide = (topTierH / 4.2f).coerceAtLeast(1.2f),
                        r = b.tintR * 1.02f, g = b.tintG * 1.02f, b = b.tintB * 1.02f,
                        emissive = 0.06f + nightFactor * 0.40f,
                        specStrength = 0.88f, specPower = 56f, reflectStrength = 0.48f
                    )
                    crownRoofY += topTierH
                }

                drawRoofParapet(dc, b.x, crownRoofY, b.z, crownW, crownD, m.bldAptRoof)
                if (b.hasSetbackCrown) {
                    dc.drawBox(b.x - crownW * 0.15f, crownRoofY + 1.15f, b.z - crownD * 0.14f, crownW * 0.42f, 2.3f, crownD * 0.40f, m.bldAptRoof, 2f, 1f, 0.66f, 0.68f, 0.72f, 0f, 0.16f, 14f, 0.05f)
                    if (vSeed % 2 == 0) {
                        // Tall rooftop communication spire on select towers
                        dc.bindVbo(dc.assets.cylinderMesh)
                        dc.drawCylinder(b.x, crownRoofY + 4.8f, b.z, 0.22f, 5.2f, 0.22f, dc.assets.texWhite, 1f, 1f, 0.72f, 0.74f, 0.78f, 0f, 0.6f, 28f, 0.20f)
                    } else {
                        drawRooftopWaterTank(dc, b.x + crownW * 0.22f, crownRoofY, b.z + crownD * 0.18f, 0f)
                    }
                }
            }

            BuildingStyle.APARTMENT_WINDOWS -> {
                // Ground retail/stone plinth + Realistic Floor-Scaled Apartment Tower
                val plinthH = 3.2f.coerceAtMost(bodyH * 0.30f)
                val upperH = (bodyH - plinthH).coerceAtLeast(3.5f)
                drawSixFaceBox(
                    dc = dc, x = b.x, y = baseY + plinthH * 0.5f, z = b.z,
                    sx = b.width + 0.24f, sy = plinthH, sz = b.depth + 0.24f, yawDeg = 0f,
                    texRight = m.bldAptRoof, texLeft = m.bldAptRoof,
                    texTop = m.bldAptRoof, texBottom = m.bldAptRoof,
                    texFront = if (vSeed % 3 == 0) m.bldShopFront else m.bldAptSide,
                    texBack = m.bldAptBack,
                    uFront = if (vSeed % 3 == 0) 1f else (b.width / 4.2f), vFront = 1f,
                    uSide = (b.depth / 4.2f), vSide = 1f,
                    r = b.tintR * 0.90f, g = b.tintG * 0.90f, b = b.tintB * 0.90f,
                    emissive = 0.03f + nightFactor * 0.26f,
                    specStrength = 0.24f, specPower = 18f, reflectStrength = 0.08f
                )

                val uF = (b.width / 4.4f).coerceAtLeast(1.8f)
                val uS = (b.depth / 4.4f).coerceAtLeast(1.8f)
                val vH = (upperH / 3.6f).coerceAtLeast(1.4f)
                val frontTex = if (vSeed % 2 == 0) m.bldAptFront else dc.assets.texHouseExteriorWindows
                drawSixFaceBox(
                    dc = dc, x = b.x, y = baseY + plinthH + upperH * 0.5f, z = b.z,
                    sx = b.width, sy = upperH, sz = b.depth, yawDeg = 0f,
                    texRight = m.bldAptSide, texLeft = m.bldAptSide,
                    texTop = m.bldAptRoof, texBottom = m.bldAptRoof,
                    texFront = frontTex, texBack = m.bldAptBack,
                    uFront = uF, vFront = vH,
                    uSide = uS, vSide = vH,
                    uTop = 3f, vTop = 3f,
                    r = b.tintR, g = b.tintG, b = b.tintB,
                    emissive = 0.03f + nightFactor * 0.34f,
                    specStrength = 0.28f, specPower = 22f, reflectStrength = 0.10f
                )
                drawRoofParapet(dc, b.x, b.height, b.z, b.width, b.depth, m.bldAptRoof)
                drawRooftopWaterTank(dc, b.x - b.width * 0.22f, b.height, b.z - b.depth * 0.18f, (vSeed * 25f) % 90f)
                drawAcOutdoorUnit(dc, b.x + b.width * 0.24f, b.height, b.z + b.depth * 0.20f, 0f)
                drawAcOutdoorUnit(dc, b.x + b.width * 0.5f + 0.19f, 3.8f, b.z, 90f)
            }

            BuildingStyle.RED_BRICK_HOUSE -> {
                // Realistic 2-Story Residential House: Ground floor (3.4m) has front stucco/door;
                // Upper 2nd floor uses clean windowed stucco/brick (NEVER stretching 1 door across 2 stories!).
                val gfH = 3.45f.coerceAtMost(bodyH * 0.58f)
                val ufH = (bodyH - gfH).coerceAtLeast(2.6f)
                val uS = (b.depth / 3.2f).coerceAtLeast(1.8f)

                // Ground Floor
                drawSixFaceBox(
                    dc = dc, x = b.x, y = baseY + gfH * 0.5f, z = b.z,
                    sx = b.width, sy = gfH, sz = b.depth, yawDeg = 0f,
                    texRight = m.bldHouseSideBrick, texLeft = m.bldHouseSideBrick,
                    texTop = m.bldAptRoof, texBottom = m.bldAptRoof,
                    texFront = m.bldHouseFront, texBack = m.bldHouseSideBrick,
                    uFront = 1f, vFront = 1f,
                    uSide = uS, vSide = 1.2f,
                    r = b.tintR, g = b.tintG, b = b.tintB,
                    emissive = 0.02f + nightFactor * 0.20f,
                    specStrength = 0.16f, specPower = 14f, reflectStrength = 0.04f
                )
                // Clean floor-divider cornice ledge (+0.18m trim)
                dc.bindVbo(dc.assets.cubeMesh)
                dc.drawBox(b.x, baseY + gfH, b.z, b.width + 0.34f, 0.20f, b.depth + 0.34f, m.bldAptRoof, 3f, 0.4f, 0.80f, 0.79f, 0.77f, 0f, 0.14f, 12f, 0.03f)
                // 2nd Floor (clean windows on front — NO second door!)
                drawSixFaceBox(
                    dc = dc, x = b.x, y = baseY + gfH + ufH * 0.5f, z = b.z,
                    sx = b.width - 0.12f, sy = ufH, sz = b.depth - 0.12f, yawDeg = 0f,
                    texRight = m.bldHouseSideBrick, texLeft = m.bldHouseSideBrick,
                    texTop = m.bldAptRoof, texBottom = m.bldAptRoof,
                    texFront = if (vSeed % 2 == 0) m.bldAptFront else dc.assets.texHouseExteriorWindows,
                    texBack = m.bldHouseSideBrick,
                    uFront = (b.width / 4.2f).coerceAtLeast(1.5f), vFront = 1.0f,
                    uSide = uS, vSide = 1.1f,
                    uTop = 2.5f, vTop = 2.5f,
                    r = b.tintR * 0.96f, g = b.tintG * 0.96f, b = b.tintB * 0.95f,
                    emissive = 0.02f + nightFactor * 0.26f,
                    specStrength = 0.18f, specPower = 16f, reflectStrength = 0.06f
                )
                drawRoofParapet(dc, b.x, b.height, b.z, b.width - 0.12f, b.depth - 0.12f, m.bldAptRoof)
                drawRooftopWaterTank(dc, b.x - b.width * 0.20f, b.height, b.z - b.depth * 0.18f, 0f)
                drawAcOutdoorUnit(dc, b.x + b.width * 0.5f + 0.19f, 2.3f, b.z + 0.8f, 90f)

                if (b.hasCompoundWall) {
                    // Non-overlapping corner compound wall (East & West walls shortened by wall thickness 0.30m * 2!)
                    val cw = b.width * 0.5f + 1.35f
                    val cd = b.depth * 0.5f + 1.35f
                    val wt = 0.30f
                    dc.bindVbo(dc.assets.cubeMesh)
                    dc.drawBox(b.x, 0.92f, b.z - cd, cw * 2f, 1.84f, wt, m.bldHouseSideBrick, 4.5f, 1.0f, 0.92f, 0.90f, 0.88f, 0f, 0.12f, 12f, 0.02f)
                    dc.drawBox(b.x, 0.92f, b.z + cd, cw * 2f, 1.84f, wt, m.bldHouseSideBrick, 4.5f, 1.0f, 0.92f, 0.90f, 0.88f, 0f, 0.12f, 12f, 0.02f)
                    dc.drawBox(b.x - cw + wt * 0.5f, 0.92f, b.z, wt, 1.84f, (cd * 2f - wt * 2f).coerceAtLeast(1f), m.bldHouseSideBrick, 4.5f, 1.0f, 0.92f, 0.90f, 0.88f, 0f, 0.12f, 12f, 0.02f)
                    dc.drawBox(b.x + cw - wt * 0.5f, 0.92f, b.z, wt, 1.84f, (cd * 2f - wt * 2f).coerceAtLeast(1f), m.bldHouseSideBrick, 4.5f, 1.0f, 0.92f, 0.90f, 0.88f, 0f, 0.12f, 12f, 0.02f)
                }
            }

            BuildingStyle.FACTORY_INDUSTRIAL -> {
                val uF = (b.width / 4.2f).coerceAtLeast(2f)
                val uS = (b.depth / 4.2f).coerceAtLeast(2f)
                val vH = (bodyH / 4.0f).coerceAtLeast(1.5f)
                drawSixFaceBox(
                    dc = dc, x = b.x, y = baseY + bodyH * 0.5f, z = b.z,
                    sx = b.width, sy = bodyH, sz = b.depth, yawDeg = 0f,
                    texRight = m.bldFactoryWall, texLeft = m.bldFactoryWall,
                    texTop = m.bldFactoryRoof, texBottom = m.bldAptRoof,
                    texFront = m.bldFactoryWall, texBack = m.bldFactoryWall,
                    uFront = uF, vFront = vH,
                    uSide = uS, vSide = vH,
                    uTop = 3f, vTop = 3f,
                    r = b.tintR, g = b.tintG, b = b.tintB,
                    emissive = 0f,
                    specStrength = 0.34f, specPower = 24f, reflectStrength = 0.12f
                )
                // Loading Bay Roll-up Shutter on front wall (spaced +0.10m cleanly so zero Z-fighting)
                dc.bindVbo(dc.assets.cubeMesh)
                dc.drawBox(b.x, 2.25f, b.z + b.depth * 0.5f + 0.10f, 4.8f, 4.2f, 0.12f, m.gateShutterFront, 1.5f, 1.5f, 0.90f, 0.92f, 0.95f, 0f, 0.40f, 24f, 0.12f)
                // Note: Exterior wooden crates & steel oil drums are rendered from WorldChunk.staticObstacles
                // so every single exterior crate & drum has an exact solid physics collider!
            }
        }
    }

    private fun drawRoofParapet(dc: GlDrawContext, x: Float, roofY: Float, z: Float, w: Float, d: Float, roofTex: Int) {
        dc.bindVbo(dc.assets.cubeMesh)
        val ph = 0.70f
        val pt = 0.32f
        val py = roofY + ph * 0.5f
        dc.drawBox(x, py, z + d * 0.5f - pt * 0.5f, w, ph, pt, roofTex, 3f, 0.4f, 0.76f, 0.77f, 0.79f, 0f, 0.14f, 12f, 0.04f)
        dc.drawBox(x, py, z - d * 0.5f + pt * 0.5f, w, ph, pt, roofTex, 3f, 0.4f, 0.76f, 0.77f, 0.79f, 0f, 0.14f, 12f, 0.04f)
        dc.drawBox(x - w * 0.5f + pt * 0.5f, py, z, pt, ph, (d - pt * 2f).coerceAtLeast(0.5f), roofTex, 3f, 0.4f, 0.76f, 0.77f, 0.79f, 0f, 0.14f, 12f, 0.04f)
        dc.drawBox(x + w * 0.5f - pt * 0.5f, py, z, pt, ph, (d - pt * 2f).coerceAtLeast(0.5f), roofTex, 3f, 0.4f, 0.76f, 0.77f, 0.79f, 0f, 0.14f, 12f, 0.04f)
    }

    // =========================================================================
    // FURNISHED ENTERABLE BUILDINGS (ZERO CORNER Z-FIGHTING, CLEAN DRYWALL,
    // WALL-TO-WALL FLOORS, ZERO FAKE DOORS, FLUSH-AGAINST-WALL FURNITURE!)
    // =========================================================================

    fun drawModernEnterableBuilding(dc: GlDrawContext, eb: EnterableBuilding, nightFactor: Float) {
        val m = dc.assets.multiAngle ?: return
        val cx = eb.centerX
        val cz = eb.centerZ
        val hw = eb.halfW
        val hd = eb.halfD
        val fh = eb.floorHeight
        val totalH = if (eb.hasSecondFloor) fh * 2f else fh
        val wt = eb.wallThick
        val fSign = eb.frontDirSign

        // 1. Distinct Ground Floor & 2nd Floor Finishes (wall-to-wall with ZERO exposed grey concrete strips!)
        val (gfTex, gfSpec, gfRefl) = when (eb.category) {
            EnterableCategory.OFFICE_TOWER, EnterableCategory.COMMERCIAL_SHOWROOM, EnterableCategory.LUXURY_VILLA ->
                Triple(m.floorMarble, 0.66f, 0.28f)
            EnterableCategory.INDUSTRIAL_FACTORY ->
                Triple(m.floorEpoxy, 0.34f, 0.14f)
            EnterableCategory.RESIDENTIAL_HOUSE, EnterableCategory.MODERN_APARTMENT ->
                Triple(m.floorOakWood, 0.30f, 0.10f)
        }
        val (f2Tex, f2R, f2G, f2B) = when (eb.category) {
            EnterableCategory.LUXURY_VILLA -> Quad(m.floorOakWood, 0.95f, 0.90f, 0.84f)
            EnterableCategory.OFFICE_TOWER -> Quad(m.floorEpoxy, 0.88f, 0.90f, 0.93f)
            EnterableCategory.RESIDENTIAL_HOUSE -> Quad(m.floorMarble, 0.96f, 0.94f, 0.90f)
            else -> Quad(m.floorOakWood, 0.92f, 0.88f, 0.84f)
        }

        val innerW = eb.width - wt * 2f
        val innerD = eb.depth - wt * 2f

        // Foundation curb slab (+0.14m)
        dc.bindVbo(dc.assets.cubeMesh)
        dc.drawBox(cx, 0.07f, cz, eb.width + 0.5f, 0.14f, eb.depth + 0.5f, m.bldAptRoof, 3f, 3f, 0.70f, 0.72f, 0.74f, 0f, 0.14f, 12f, 0.04f)
        // Wall-to-wall Ground Interior Floor at y = 0.16f (extends slightly under wall inner edge so ZERO concrete gap!)
        dc.bindVbo(dc.assets.quadMesh)
        dc.drawQuad(cx, 0.16f, cz, innerW + 0.06f, innerD + 0.06f, gfTex, 5f, 5f, 0.98f, 0.98f, 0.97f, 0.06f + nightFactor * 0.18f, gfSpec, 36f, gfRefl)

        // 2. Second Floor Slab & Wooden Staircase
        dc.bindVbo(dc.assets.cubeMesh)
        if (eb.hasSecondFloor) {
            val main2fW = innerW - 2.45f
            dc.drawBox(cx - 1.225f, fh, cz, main2fW, 0.26f, innerD + 0.04f, f2Tex, 4f, 5f, f2R, f2G, f2B, 0.06f + nightFactor * 0.16f, 0.34f, 28f, 0.10f)
            val landingZ = eb.stairEndZ - fSign * 1.05f
            dc.drawBox(cx + hw - wt - 1.225f, fh, landingZ, 2.45f, 0.26f, 2.25f, f2Tex, 1.5f, 1.5f, f2R, f2G, f2B, 0.06f + nightFactor * 0.16f, 0.34f, 28f, 0.10f)

            val steps = 12
            val stairCenterX = (eb.stairMinX + eb.stairMaxX) * 0.5f
            val stairW = eb.stairMaxX - eb.stairMinX
            val stepDepth = abs(eb.stairEndZ - eb.stairStartZ) / steps
            val stepRise = (fh - 0.16f) / steps
            for (i in 0 until steps) {
                val t = (i + 0.5f) / steps
                val sz = eb.stairStartZ + (eb.stairEndZ - eb.stairStartZ) * t
                val sh = (i + 1) * stepRise
                dc.drawBox(stairCenterX, 0.16f + sh * 0.5f, sz, stairW, sh, stepDepth + 0.05f, m.floorOakWood, 1.2f, 0.5f, 0.88f, 0.82f, 0.74f, 0.04f, 0.28f, 20f, 0.06f)
            }
        }

        // 3. NON-OVERLAPPING Structural Exterior Walls (ZERO corner Z-fighting!) + ZERO Fake Doors!
        // Front & Back walls span full eb.width; Left & Right walls span strictly innerD = eb.depth - wt * 2f!
        val wallEmiss = 0.04f + nightFactor * 0.16f
        val (extSideTex, extBackTex, extFrontFlankTex, extFrontUpperTex, roofTex) = when (eb.category) {
            EnterableCategory.OFFICE_TOWER ->
                Quint(m.bldOfficeSide, m.bldOfficeSide, m.bldOfficeLobby, m.bldOfficeFront, m.bldAptRoof)
            EnterableCategory.MODERN_APARTMENT ->
                Quint(m.bldAptSide, m.bldAptBack, m.bldAptFront, m.bldAptFront, m.bldAptRoof)
            EnterableCategory.RESIDENTIAL_HOUSE ->
                // Clean brick & windowed stucco — NEVER tile bldHouseFront (which has a painted door) on flanks/2nd floor!
                Quint(m.bldHouseSideBrick, m.bldHouseSideBrick, dc.assets.texHouseExteriorWindows, dc.assets.texHouseExteriorWindows, m.bldAptRoof)
            EnterableCategory.LUXURY_VILLA ->
                Quint(m.bldAptSide, m.bldAptBack, m.bldAptFront, m.bldAptFront, m.bldAptRoof)
            EnterableCategory.COMMERCIAL_SHOWROOM ->
                Quint(m.bldHouseSideBrick, m.bldAptBack, m.bldShopFront, m.bldAptFront, m.bldAptRoof)
            EnterableCategory.INDUSTRIAL_FACTORY ->
                Quint(m.bldFactoryWall, m.bldFactoryWall, m.bldFactoryWall, m.bldFactoryWall, m.bldFactoryRoof)
        }

        val wallU = (eb.depth / 4.2f).coerceAtLeast(2.2f)
        val wallV = (totalH / 4.0f).coerceAtLeast(1.2f)
        val backWallZ = eb.backZ + fSign * wt * 0.5f
        val frontWallZ = eb.frontZ - fSign * wt * 0.5f

        // Left & Right walls trimmed strictly to innerD (between back wall and front wall -> ZERO corner overlap!)
        dc.drawBox(cx - hw + wt * 0.5f, 0.14f + totalH * 0.5f, cz, wt, totalH, innerD, extSideTex, wallU, wallV, 0.94f, 0.94f, 0.94f, wallEmiss, 0.22f, 20f, 0.06f)
        dc.drawBox(cx + hw - wt * 0.5f, 0.14f + totalH * 0.5f, cz, wt, totalH, innerD, extSideTex, wallU, wallV, 0.94f, 0.94f, 0.94f, wallEmiss, 0.22f, 20f, 0.06f)
        // Back wall spans full eb.width cleanly
        dc.drawBox(cx, 0.14f + totalH * 0.5f, backWallZ, eb.width, totalH, wt, extBackTex, wallU, wallV, 0.94f, 0.94f, 0.94f, wallEmiss, 0.22f, 20f, 0.06f)

        // Ground-floor Front wall flanking the hollow interactive gate opening
        val segWidth = (hw - eb.doorHalfWidth).coerceAtLeast(0.6f)
        val flankU = (segWidth / 3.6f).coerceAtLeast(1.0f)
        dc.drawBox(cx - eb.doorHalfWidth - segWidth * 0.5f, 0.14f + fh * 0.5f, frontWallZ, segWidth, fh, wt, extFrontFlankTex, flankU, 1.0f, 0.95f, 0.95f, 0.95f, 0.03f + nightFactor * 0.18f, 0.36f, 26f, 0.14f)
        dc.drawBox(cx + eb.doorHalfWidth + segWidth * 0.5f, 0.14f + fh * 0.5f, frontWallZ, segWidth, fh, wt, extFrontFlankTex, flankU, 1.0f, 0.95f, 0.95f, 0.95f, 0.03f + nightFactor * 0.18f, 0.36f, 26f, 0.14f)
        // Clean structural lintel beam directly above gate opening (no overlap with flanking segments!)
        val lintelH = (fh - eb.gateHeight).coerceAtLeast(0.42f)
        dc.drawBox(cx, 0.14f + eb.gateHeight + lintelH * 0.5f, frontWallZ, eb.doorHalfWidth * 2f, lintelH, wt, m.bldAptRoof, 2f, 0.4f, 0.34f, 0.36f, 0.40f, 0f, 0.22f, 18f, 0.06f)

        // Second-floor Front wall spans full eb.width directly above Ground Floor (starts cleanly at y = 0.14f + fh!)
        if (eb.hasSecondFloor) {
            dc.drawBox(cx, 0.14f + fh + fh * 0.5f, frontWallZ, eb.width, fh, wt, extFrontUpperTex, 3.2f, 1.0f, 0.95f, 0.95f, 0.96f, 0.03f + nightFactor * 0.22f, 0.38f, 28f, 0.14f)
        }

        // 3b. CLEAN PAINTED INTERIOR DRYWALL LINERS + BASEBOARDS (so indoor rooms look like real homes/offices!)
        if (eb.category != EnterableCategory.INDUSTRIAL_FACTORY) {
            val (ir, ig, ib) = when (eb.category) {
                EnterableCategory.RESIDENTIAL_HOUSE -> Triple(0.94f, 0.91f, 0.86f) // Warm cream plaster
                EnterableCategory.LUXURY_VILLA -> Triple(0.96f, 0.94f, 0.90f)      // Ivory marble plaster
                EnterableCategory.MODERN_APARTMENT -> Triple(0.92f, 0.93f, 0.94f)  // Modern soft grey-white
                else -> Triple(0.90f, 0.92f, 0.95f)                                // Corporate clean off-white
            }
            val wallPaintTex = dc.assets.texInteriorWallPaint
            val linerT = 0.04f
            val linerH = totalH - 0.08f
            val linerY = 0.18f + linerH * 0.5f
            val innerHalfW = hw - wt
            val innerHalfD = hd - wt

            // Left, Right & Back painted interior drywall liners (strictly inside outer walls!)
            dc.drawBox(cx - innerHalfW + linerT * 0.5f, linerY, cz, linerT, linerH, innerD - 0.10f, wallPaintTex, 4f, 2f, ir, ig, ib, 0.06f + nightFactor * 0.12f, 0.10f, 10f, 0.01f)
            dc.drawBox(cx + innerHalfW - linerT * 0.5f, linerY, cz, linerT, linerH, innerD - 0.10f, wallPaintTex, 4f, 2f, ir, ig, ib, 0.06f + nightFactor * 0.12f, 0.10f, 10f, 0.01f)
            dc.drawBox(cx, linerY, eb.backZ + fSign * (wt + linerT * 0.5f), innerW - 0.10f, linerH, linerT, wallPaintTex, 4f, 2f, ir, ig, ib, 0.06f + nightFactor * 0.12f, 0.10f, 10f, 0.01f)

            // Front flanking interior drywall liners (around the real doorway!)
            val fLinerZ = eb.frontZ - fSign * (wt + linerT * 0.5f)
            val innerSegW = (innerHalfW - eb.doorHalfWidth - 0.04f).coerceAtLeast(0.4f)
            dc.drawBox(cx - eb.doorHalfWidth - innerSegW * 0.5f - 0.02f, 0.16f + (fh - 0.10f) * 0.5f, fLinerZ, innerSegW, fh - 0.10f, linerT, wallPaintTex, 2f, 1f, ir, ig, ib, 0.06f + nightFactor * 0.12f, 0.10f, 10f, 0.01f)
            dc.drawBox(cx + eb.doorHalfWidth + innerSegW * 0.5f + 0.02f, 0.16f + (fh - 0.10f) * 0.5f, fLinerZ, innerSegW, fh - 0.10f, linerT, wallPaintTex, 2f, 1f, ir, ig, ib, 0.06f + nightFactor * 0.12f, 0.10f, 10f, 0.01f)
            if (eb.hasSecondFloor) {
                dc.drawBox(cx, 0.14f + fh + fh * 0.5f, fLinerZ, innerW - 0.10f, fh - 0.14f, linerT, wallPaintTex, 4f, 1f, ir, ig, ib, 0.06f + nightFactor * 0.12f, 0.10f, 10f, 0.01f)
            }

            // Living-room / kitchen painted partition wall in Residential House (ground floor sofa rests flush against it!)
            if (eb.category == EnterableCategory.RESIDENTIAL_HOUSE) {
                val partZ = cz + fSign * 1.78f
                dc.drawBox(cx - 4.15f, 0.16f + (fh - 0.16f) * 0.5f, partZ, 4.55f, fh - 0.16f, 0.24f, wallPaintTex, 2f, 1f, ir * 0.96f, ig * 0.96f, ib * 0.95f, 0.05f + nightFactor * 0.12f, 0.10f, 10f, 0.01f)
            }
        }

        // Warm glowing interior LED ceiling light bars (Ground Floor & 2nd Floor)
        dc.drawBox(cx - 1.8f, fh - 0.22f, cz, 3.2f, 0.08f, 0.55f, dc.assets.texWhite, 1f, 1f, 1.0f, 0.97f, 0.88f, 0.92f, 0.2f, 12f, 0f)
        if (eb.hasSecondFloor) {
            dc.drawBox(cx - 1.8f, totalH - 0.12f, cz, 3.2f, 0.08f, 0.55f, dc.assets.texWhite, 1f, 1f, 1.0f, 0.97f, 0.88f, 0.92f, 0.2f, 12f, 0f)
        }

        // Roof Deck Slab + Optional Upper Tower Stories
        var propRoofY = 0.14f + totalH + 0.30f
        dc.drawBox(cx, 0.14f + totalH + 0.15f, cz, eb.width + 0.32f, 0.30f, eb.depth + 0.32f, roofTex, 3.5f, 3.5f, 0.82f, 0.83f, 0.85f, 0f, 0.20f, 16f, 0.05f)

        when (eb.category) {
            EnterableCategory.OFFICE_TOWER -> {
                val towerH = 14.0f
                drawSixFaceBox(
                    dc = dc, x = cx, y = propRoofY + towerH * 0.5f, z = cz,
                    sx = eb.width * 0.92f, sy = towerH, sz = eb.depth * 0.92f, yawDeg = 0f,
                    texRight = m.bldOfficeSide, texLeft = m.bldOfficeSide,
                    texTop = m.bldAptRoof, texBottom = m.bldAptRoof,
                    texFront = m.bldOfficeFront, texBack = m.bldOfficeFront,
                    uFront = 3f, vFront = 3.2f, uSide = 3f, vSide = 3.2f, uTop = 3f, vTop = 3f,
                    r = 0.92f, g = 0.95f, b = 0.98f,
                    emissive = 0.05f + nightFactor * 0.34f,
                    specStrength = 0.86f, specPower = 54f, reflectStrength = 0.44f
                )
                propRoofY += towerH
                drawRoofParapet(dc, cx, propRoofY, cz, eb.width * 0.92f, eb.depth * 0.92f, m.bldAptRoof)
            }
            EnterableCategory.MODERN_APARTMENT -> {
                val upperAptH = 9.6f
                drawSixFaceBox(
                    dc = dc, x = cx, y = propRoofY + upperAptH * 0.5f, z = cz,
                    sx = eb.width * 0.94f, sy = upperAptH, sz = eb.depth * 0.94f, yawDeg = 0f,
                    texRight = m.bldAptSide, texLeft = m.bldAptSide,
                    texTop = m.bldAptRoof, texBottom = m.bldAptRoof,
                    texFront = m.bldAptFront, texBack = m.bldAptBack,
                    uFront = 2.8f, vFront = 2.4f, uSide = 2.8f, vSide = 2.4f, uTop = 3f, vTop = 3f,
                    r = 0.94f, g = 0.94f, b = 0.92f,
                    emissive = 0.04f + nightFactor * 0.34f,
                    specStrength = 0.28f, specPower = 22f, reflectStrength = 0.12f
                )
                propRoofY += upperAptH
                drawRoofParapet(dc, cx, propRoofY, cz, eb.width * 0.94f, eb.depth * 0.94f, m.bldAptRoof)
            }
            else -> {
                drawRoofParapet(dc, cx, propRoofY, cz, eb.width, eb.depth, roofTex)
            }
        }

        // 4. Rooftop Water Tanks & Wall-Mounted AC Outdoor Units
        drawRooftopWaterTank(dc, cx - 3.6f, propRoofY, cz - 3.2f, 15f)
        drawRooftopWaterTank(dc, cx + 3.4f, propRoofY, cz - 3.2f, -20f)
        drawAcOutdoorUnit(dc, cx + hw + 0.19f, 2.6f, cz - 2.0f, 90f)
        drawAcOutdoorUnit(dc, cx - hw - 0.19f, 2.6f, cz + 1.8f, -90f)

        // 5. Render EVERY Flush-Against-Wall Interior Furniture Item from eb.furnitureItems (100% Collider-Synced!)
        val f1Y = 0.16f
        val f2Y = fh + 0.13f
        for (item in eb.furnitureItems) {
            val fy = (if (item.floorIdx >= 2) f2Y else f1Y) + item.yOffset
            val wx = cx + item.localX
            val wz = cz + item.localZ
            when (item.kind) {
                FurnitureKind.KING_BED -> drawKingBed(dc, wx, fy, wz, item.yawDeg)
                FurnitureKind.MODERN_SOFA -> drawModernSofa(dc, wx, fy, wz, item.yawDeg)
                FurnitureKind.WOODEN_WARDROBE -> drawWoodenWardrobe(dc, wx, fy, wz, item.yawDeg)
                FurnitureKind.REFRIGERATOR -> drawRefrigerator(dc, wx, fy, wz, item.yawDeg)
                FurnitureKind.TV_UNIT -> drawTvUnit(dc, wx, fy, wz, item.yawDeg)
                FurnitureKind.KITCHEN_COUNTER -> drawKitchenCounter(dc, wx, fy, wz, item.yawDeg)
                FurnitureKind.OFFICE_DESK_PC -> drawOfficeDeskPc(dc, wx, fy, wz, item.yawDeg)
                FurnitureKind.FACTORY_CNC -> drawFactoryCncMachine(dc, wx, fy, wz, item.yawDeg)
                FurnitureKind.CARGO_CRATE -> drawCargoCrate(dc, wx, fy, wz, item.yawDeg, item.propSize)
                FurnitureKind.OIL_DRUM -> drawSteelOilDrum(dc, wx, fy, wz, item.yawDeg)
            }
        }

        // 6. Hand-Controlled Interactive Gate in the Front Opening (strict local-space!)
        drawInteractiveGate(dc, eb.gate, nightFactor)
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
}
