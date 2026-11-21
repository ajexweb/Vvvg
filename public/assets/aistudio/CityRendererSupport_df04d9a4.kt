package com.example.game.engine

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.game.model.CharacterSkin
import com.example.game.model.GraphicsQuality
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Chooses an RGBA8888 + Depth16 + Stencil8 EGLConfig so planar silhouette shadows
 * never double-darken overlapping triangles. Safely falls back if stencil is unavailable.
 */
class StencilEGLConfigChooser : GLSurfaceView.EGLConfigChooser {
    @Volatile var hasStencil: Boolean = false
        private set

    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        val stencilSpec = intArrayOf(
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_DEPTH_SIZE, 16,
            EGL10.EGL_STENCIL_SIZE, 8,
            EGL10.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES2_BIT
            EGL10.EGL_NONE
        )
        val numConfigs = IntArray(1)
        val configs = arrayOfNulls<EGLConfig>(1)
        if (egl.eglChooseConfig(display, stencilSpec, configs, 1, numConfigs) && numConfigs[0] > 0 && configs[0] != null) {
            hasStencil = true
            return configs[0]!!
        }

        // Safe fallback without stencil
        val fallbackSpec = intArrayOf(
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_DEPTH_SIZE, 16,
            EGL10.EGL_RENDERABLE_TYPE, 4,
            EGL10.EGL_NONE
        )
        if (egl.eglChooseConfig(display, fallbackSpec, configs, 1, numConfigs) && numConfigs[0] > 0 && configs[0] != null) {
            hasStencil = false
            return configs[0]!!
        }

        // Last resort minimal config
        val minSpec = intArrayOf(EGL10.EGL_RENDERABLE_TYPE, 4, EGL10.EGL_NONE)
        egl.eglChooseConfig(display, minSpec, configs, 1, numConfigs)
        hasStencil = false
        return configs[0]!!
    }
}

/**
 * Container for shared GPU meshes & textures passed to prop/character drawers.
 */
class CityRenderAssets(
    val shader: CityShaderProgram,
    val shadowShader: ShadowShaderProgram,
    val cubeMesh: GlMesh,
    val quadMesh: GlMesh,
    val cylinderMesh: GlMesh,
    val taperedCylinderMesh: GlMesh,
    val sphereMesh: GlMesh,
    val carBodyMesh: GlMesh,
    val carCabinMesh: GlMesh,
    val ringMesh: GlMesh,
    val crossedLeafMesh: GlMesh,
    val texBuilding: Int,
    val texRoad: Int,
    val texGrass: Int,
    val texBark: Int,
    val texLeafCluster: Int,
    val texInteriorFloor: Int,
    val texConcrete: Int,
    val texWhite: Int,
    val playerHeadMesh: GlMesh = sphereMesh,
    val playerTorsoMesh: GlMesh = cylinderMesh,
    val texPlayerFace: Int = texWhite,
    val texPlayerShirt: Int = texWhite,
    val texPlayerJeans: Int = texWhite
)

/**
 * Reusable matrix scratchpad and low-level draw helpers (zero per-frame heap allocation).
 */
class GlDrawContext(val assets: CityRenderAssets) {
    val projMatrix = FloatArray(16)
    val viewMatrix = FloatArray(16)
    val vpMatrix = FloatArray(16)
    val modelMatrix = FloatArray(16)
    val mvpMatrix = FloatArray(16)
    val shadowProjMatrix = FloatArray(16)
    val shadowModelMatrix = FloatArray(16)

    var boundVboId = -1
    var boundShadowVboId = -1

    init {
        // Precompute sun-projected planar shadow matrix onto ground plane y = 0.044f
        // Sun direction L = (0.58f, 0.80f, 0.36f)
        val kx = 0.58f / 0.80f
        val kz = 0.36f / 0.80f
        val y0 = 0.044f
        shadowProjMatrix[0] = 1f;  shadowProjMatrix[4] = -kx; shadowProjMatrix[8] = 0f;  shadowProjMatrix[12] = kx * y0
        shadowProjMatrix[1] = 0f;  shadowProjMatrix[5] = 0f;  shadowProjMatrix[9] = 0f;  shadowProjMatrix[13] = y0
        shadowProjMatrix[2] = 0f;  shadowProjMatrix[6] = -kz; shadowProjMatrix[10] = 1f; shadowProjMatrix[14] = kz * y0
        shadowProjMatrix[3] = 0f;  shadowProjMatrix[7] = 0f;  shadowProjMatrix[11] = 0f; shadowProjMatrix[15] = 1f
    }

    fun bindVbo(mesh: GlMesh) {
        if (boundVboId != mesh.vboId) {
            mesh.bind(assets.shader)
            boundVboId = mesh.vboId
        }
    }

    fun bindShadowVbo(mesh: GlMesh) {
        if (boundShadowVboId != mesh.vboId) {
            mesh.bindShadow(assets.shadowShader)
            boundShadowVboId = mesh.vboId
        }
    }

    fun drawQuad(
        x: Float, y: Float, z: Float,
        sx: Float, sz: Float,
        texId: Int, uScale: Float, vScale: Float,
        r: Float, g: Float, b: Float,
        emissive: Float, specStrength: Float, specPower: Float, reflectStrength: Float,
        yawDeg: Float = 0f
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        if (yawDeg != 0f) Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        Matrix.scaleM(modelMatrix, 0, sx, 1f, sz)
        commitDraw(texId, uScale, vScale, r, g, b, 1f, emissive, specStrength, specPower, reflectStrength, assets.quadMesh.vertexCount)
    }

    fun drawBox(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        texId: Int, uScale: Float, vScale: Float,
        r: Float, g: Float, b: Float,
        emissive: Float, specStrength: Float, specPower: Float, reflectStrength: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        commitDraw(texId, uScale, vScale, r, g, b, 1f, emissive, specStrength, specPower, reflectStrength, assets.cubeMesh.vertexCount)
    }

    fun drawBoxAlpha(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        texId: Int,
        r: Float, g: Float, b: Float, alpha: Float,
        emissive: Float, specStrength: Float, specPower: Float, reflectStrength: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        commitDraw(texId, 1f, 1f, r, g, b, alpha, emissive, specStrength, specPower, reflectStrength, assets.cubeMesh.vertexCount)
    }

    fun drawCylinder(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        texId: Int, uScale: Float, vScale: Float,
        r: Float, g: Float, b: Float,
        emissive: Float, specStrength: Float, specPower: Float, reflectStrength: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        commitDraw(texId, uScale, vScale, r, g, b, 1f, emissive, specStrength, specPower, reflectStrength, assets.cylinderMesh.vertexCount)
    }

    fun drawMeshTransformed(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        yawDeg: Float, pitchDeg: Float, rollDeg: Float,
        texId: Int, uScale: Float, vScale: Float,
        r: Float, g: Float, b: Float, alpha: Float,
        emissive: Float, specStrength: Float, specPower: Float, reflectStrength: Float,
        mesh: GlMesh
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        if (yawDeg != 0f) Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        if (pitchDeg != 0f) Matrix.rotateM(modelMatrix, 0, pitchDeg, 1f, 0f, 0f)
        if (rollDeg != 0f) Matrix.rotateM(modelMatrix, 0, rollDeg, 0f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        commitDraw(texId, uScale, vScale, r, g, b, alpha, emissive, specStrength, specPower, reflectStrength, mesh.vertexCount)
    }

    fun commitDraw(
        texId: Int,
        uScale: Float, vScale: Float,
        r: Float, g: Float, b: Float, alpha: Float,
        emissive: Float, specStrength: Float, specPower: Float, reflectStrength: Float,
        vertCount: Int
    ) {
        val sh = assets.shader
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(sh.uMvpMatrixLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(sh.uModelMatrixLoc, 1, false, modelMatrix, 0)
        GLES20.glUniform2f(sh.uTexScaleLoc, uScale, vScale)
        GLES20.glUniform4f(sh.uColorTintLoc, r, g, b, alpha)
        GLES20.glUniform1f(sh.uEmissiveLoc, emissive)
        GLES20.glUniform1f(sh.uSpecStrengthLoc, specStrength)
        GLES20.glUniform1f(sh.uSpecPowerLoc, specPower)
        GLES20.glUniform1f(sh.uReflectStrengthLoc, reflectStrength)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(sh.uTextureLoc, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertCount)
    }

    /**
     * Projects the current [modelMatrix] onto the ground using the sun shadow matrix
     * and draws the silhouette (respecting leaf alpha cutouts!).
     */
    fun commitShadow(texId: Int, vertCount: Int) {
        val sh = assets.shadowShader
        Matrix.multiplyMM(shadowModelMatrix, 0, shadowProjMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, shadowModelMatrix, 0)
        GLES20.glUniformMatrix4fv(sh.uMvpMatrixLoc, 1, false, mvpMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(sh.uTextureLoc, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertCount)
    }
}

/**
 * Draws realistic multi-part trees, curved GTA-style vehicles, street props,
 * realistic human player & NPC men/women, AND true silhouette cast shadows.
 */
object CityPropDrawer {

    // -------------------------------------------------------------------------
    // 1. REALISTIC MULTI-PART BOTANICAL TREES (Root Flare + Bark + Primary Branches + Twigs + Leaf Clusters)
    // -------------------------------------------------------------------------

    fun drawRealisticTree(dc: GlDrawContext, t: TreeProp, worldTime: Float, quality: GraphicsQuality) {
        val s = t.scale
        val sway = sin(worldTime * 1.55f + t.seed) * 1.9f
        val barkTex = dc.assets.texBark
        val trunkMesh = dc.assets.taperedCylinderMesh

        // A. Root Flare / Buttress Base + Main Tapered Trunk
        dc.bindVbo(trunkMesh)
        dc.drawMeshTransformed(
            t.x, 0.35f * s, t.z,
            0.95f * s, 0.70f * s, 0.95f * s,
            (t.seed * 45f) % 360f, 0f, 0f,
            barkTex, 2f, 1f, 0.88f, 0.80f, 0.72f, 1f, 0f, 0.04f, 6f, 0f, trunkMesh
        )
        dc.drawMeshTransformed(
            t.x, 1.85f * s, t.z,
            0.58f * s, 3.3f * s, 0.58f * s,
            (t.seed * 37f) % 360f, sway * 0.18f, 0f,
            barkTex, 2f, 2.8f, 0.94f, 0.87f, 0.80f, 1f, 0f, 0.05f, 8f, 0f, trunkMesh
        )

        // B. Primary Branches + Secondary Twigs ("Taniya")
        val primaryCount = if (quality == GraphicsQuality.LOW) 3 else 4
        val twigClusters = ArrayList<FloatArray>(24)

        for (b in 0 until primaryCount) {
            val baseYaw = b * (360f / primaryCount) + (t.seed * 31f)
            val rad = baseYaw * PI.toFloat() / 180f
            val dirX = cos(rad)
            val dirZ = sin(rad)
            val originY = (2.65f + (b % 2) * 0.42f) * s
            val branchLen = (2.15f + (b % 3) * 0.25f) * s
            val midX = t.x + dirX * branchLen * 0.38f
            val midZ = t.z + dirZ * branchLen * 0.38f
            val midY = originY + branchLen * 0.34f

            dc.bindVbo(trunkMesh)
            Matrix.setIdentityM(dc.modelMatrix, 0)
            Matrix.translateM(dc.modelMatrix, 0, midX, midY, midZ)
            Matrix.rotateM(dc.modelMatrix, 0, -baseYaw + 90f, 0f, 1f, 0f)
            Matrix.rotateM(dc.modelMatrix, 0, 48f + (b % 2) * 7f + sway * 0.35f, 1f, 0f, 0f)
            Matrix.scaleM(dc.modelMatrix, 0, 0.24f * s, branchLen, 0.24f * s)
            dc.commitDraw(barkTex, 1f, 2f, 0.90f, 0.83f, 0.76f, 1f, 0f, 0.04f, 6f, 0f, trunkMesh.vertexCount)

            val tipX = t.x + dirX * branchLen * 0.76f + sway * 0.02f
            val tipZ = t.z + dirZ * branchLen * 0.76f
            val tipY = originY + branchLen * 0.66f
            twigClusters.add(floatArrayOf(tipX, tipY + 0.35f * s, tipZ, 2.35f * s, baseYaw))

            // Secondary twigs ("taniya") forking left & right off each primary branch
            val subCount = if (quality == GraphicsQuality.LOW) 1 else 3
            for (tw in 0 until subCount) {
                val twOffsetYaw = baseYaw + (tw - 1) * 38f
                val twRad = twOffsetYaw * PI.toFloat() / 180f
                val twDirX = cos(twRad)
                val twDirZ = sin(twRad)
                val twStartFrac = 0.48f + tw * 0.22f
                val sx = t.x + dirX * branchLen * twStartFrac
                val sz = t.z + dirZ * branchLen * twStartFrac
                val sy = originY + branchLen * twStartFrac * 0.85f
                val twLen = 1.25f * s

                Matrix.setIdentityM(dc.modelMatrix, 0)
                Matrix.translateM(dc.modelMatrix, 0, sx + twDirX * twLen * 0.38f, sy + twLen * 0.30f, sz + twDirZ * twLen * 0.38f)
                Matrix.rotateM(dc.modelMatrix, 0, -twOffsetYaw + 90f, 0f, 1f, 0f)
                Matrix.rotateM(dc.modelMatrix, 0, 56f + sway * 0.5f, 1f, 0f, 0f)
                Matrix.scaleM(dc.modelMatrix, 0, 0.10f * s, twLen, 0.10f * s)
                dc.commitDraw(barkTex, 1f, 1.5f, 0.86f, 0.79f, 0.72f, 1f, 0f, 0.03f, 6f, 0f, trunkMesh.vertexCount)

                val lx = sx + twDirX * twLen * 0.82f + sway * 0.025f
                val lz = sz + twDirZ * twLen * 0.82f
                val ly = sy + twLen * 0.62f
                twigClusters.add(floatArrayOf(lx, ly, lz, (1.85f + (tw % 2) * 0.3f) * s, twOffsetYaw))
            }
        }

        // Upper central leader crown twig
        twigClusters.add(floatArrayOf(t.x + sway * 0.02f, 4.95f * s, t.z, 2.75f * s, t.seed * 23f))
        twigClusters.add(floatArrayOf(t.x + 0.55f * s + sway * 0.02f, 4.45f * s, t.z - 0.45f * s, 2.25f * s, t.seed * 41f))
        twigClusters.add(floatArrayOf(t.x - 0.55f * s + sway * 0.02f, 4.50f * s, t.z + 0.45f * s, 2.25f * s, t.seed * 67f))

        // C. Crossed Botanical Alpha-Cutout Leaf Clusters (Double-Sided!)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        val leafMesh = dc.assets.crossedLeafMesh
        val leafTex = dc.assets.texLeafCluster
        dc.bindVbo(leafMesh)

        for (i in twigClusters.indices) {
            val c = twigClusters[i]
            val shade = 0.88f + (i % 4) * 0.05f
            dc.drawMeshTransformed(
                c[0], c[1], c[2],
                c[3], c[3] * 0.92f, c[3],
                c[4], sway * 0.55f, sway * 0.35f,
                leafTex, 1f, 1f,
                shade * 0.96f, shade * 1.04f, shade * 0.90f, 1f,
                0f, 0.08f, 10f, 0f,
                leafMesh
            )
        }
        GLES20.glEnable(GLES20.GL_CULL_FACE)
    }

    fun drawTreeShadow(dc: GlDrawContext, t: TreeProp, worldTime: Float, quality: GraphicsQuality) {
        val s = t.scale
        val sway = sin(worldTime * 1.55f + t.seed) * 1.9f
        val trunkMesh = dc.assets.taperedCylinderMesh

        dc.bindShadowVbo(trunkMesh)
        Matrix.setIdentityM(dc.modelMatrix, 0)
        Matrix.translateM(dc.modelMatrix, 0, t.x, 1.85f * s, t.z)
        Matrix.scaleM(dc.modelMatrix, 0, 0.58f * s, 3.3f * s, 0.58f * s)
        dc.commitShadow(dc.assets.texWhite, trunkMesh.vertexCount)

        val primaryCount = if (quality == GraphicsQuality.LOW) 3 else 4
        for (b in 0 until primaryCount) {
            val baseYaw = b * (360f / primaryCount) + (t.seed * 31f)
            val rad = baseYaw * PI.toFloat() / 180f
            val originY = (2.65f + (b % 2) * 0.42f) * s
            val branchLen = (2.15f + (b % 3) * 0.25f) * s
            val midX = t.x + cos(rad) * branchLen * 0.38f
            val midZ = t.z + sin(rad) * branchLen * 0.38f
            val midY = originY + branchLen * 0.34f

            Matrix.setIdentityM(dc.modelMatrix, 0)
            Matrix.translateM(dc.modelMatrix, 0, midX, midY, midZ)
            Matrix.rotateM(dc.modelMatrix, 0, -baseYaw + 90f, 0f, 1f, 0f)
            Matrix.rotateM(dc.modelMatrix, 0, 48f + (b % 2) * 7f, 1f, 0f, 0f)
            Matrix.scaleM(dc.modelMatrix, 0, 0.24f * s, branchLen, 0.24f * s)
            dc.commitShadow(dc.assets.texWhite, trunkMesh.vertexCount)
        }

        // Leaf cluster cutout shadows — casts realistic dappled leafy ground shadow!
        val leafMesh = dc.assets.crossedLeafMesh
        val leafTex = dc.assets.texLeafCluster
        dc.bindShadowVbo(leafMesh)

        // Central crown shadow
        Matrix.setIdentityM(dc.modelMatrix, 0)
        Matrix.translateM(dc.modelMatrix, 0, t.x + sway * 0.02f, 4.95f * s, t.z)
        Matrix.rotateM(dc.modelMatrix, 0, t.seed * 23f, 0f, 1f, 0f)
        Matrix.scaleM(dc.modelMatrix, 0, 2.75f * s, 2.55f * s, 2.75f * s)
        dc.commitShadow(leafTex, leafMesh.vertexCount)

        for (b in 0 until primaryCount) {
            val baseYaw = b * (360f / primaryCount) + (t.seed * 31f)
            val rad = baseYaw * PI.toFloat() / 180f
            val originY = (2.65f + (b % 2) * 0.42f) * s
            val branchLen = (2.15f + (b % 3) * 0.25f) * s
            val tipX = t.x + cos(rad) * branchLen * 0.76f + sway * 0.02f
            val tipZ = t.z + sin(rad) * branchLen * 0.76f
            val tipY = originY + branchLen * 0.66f + 0.35f * s

            Matrix.setIdentityM(dc.modelMatrix, 0)
            Matrix.translateM(dc.modelMatrix, 0, tipX, tipY, tipZ)
            Matrix.rotateM(dc.modelMatrix, 0, baseYaw, 0f, 1f, 0f)
            Matrix.scaleM(dc.modelMatrix, 0, 2.35f * s, 2.15f * s, 2.35f * s)
            dc.commitShadow(leafTex, leafMesh.vertexCount)
        }
    }

    // -------------------------------------------------------------------------
    // 2. REALISTIC GTA-STYLE CARS (Moving & Parked) + SHAPE SHADOWS
    // -------------------------------------------------------------------------

    fun drawCar(
        dc: GlDrawContext,
        x: Float, z: Float, yawDeg: Float,
        wheelAngleDeg: Float,
        r: Float, g: Float, b: Float
    ) {
        val white = dc.assets.texWhite

        // Sculpted metallic chassis
        dc.bindVbo(dc.assets.carBodyMesh)
        Matrix.setIdentityM(dc.modelMatrix, 0)
        Matrix.translateM(dc.modelMatrix, 0, x, 0.06f, z)
        Matrix.rotateM(dc.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        dc.commitDraw(white, 1f, 1f, r, g, b, 1f, 0.02f, 0.85f, 48f, 0.36f, dc.assets.carBodyMesh.vertexCount)

        // Cabin interior seats & dashboard visible through translucent windshield
        dc.bindVbo(dc.assets.cubeMesh)
        drawCarLocalPart(dc, x, z, yawDeg, -0.34f, 0.94f, -0.32f, 0.42f, 0.48f, 0.32f, 0.20f, 0.18f, 0.16f, 0f)
        drawCarLocalPart(dc, x, z, yawDeg,  0.34f, 0.94f, -0.32f, 0.42f, 0.48f, 0.32f, 0.20f, 0.18f, 0.16f, 0f)
        drawCarLocalPart(dc, x, z, yawDeg,  0.00f, 0.96f,  0.48f, 1.38f, 0.22f, 0.28f, 0.14f, 0.15f, 0.17f, 0f)

        // Tinted glossy glass greenhouse cabin
        dc.bindVbo(dc.assets.carCabinMesh)
        Matrix.setIdentityM(dc.modelMatrix, 0)
        Matrix.translateM(dc.modelMatrix, 0, x, 0.06f, z)
        Matrix.rotateM(dc.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        dc.commitDraw(white, 1f, 1f, 0.12f, 0.18f, 0.25f, 0.84f, 0.03f, 0.95f, 64f, 0.56f, dc.assets.carCabinMesh.vertexCount)

        // 4 Cylindrical Rubber Tires + Alloy Rims
        dc.bindVbo(dc.assets.cylinderMesh)
        val wheels = arrayOf(
            floatArrayOf(-0.90f, 0.34f,  1.34f),
            floatArrayOf( 0.90f, 0.34f,  1.34f),
            floatArrayOf(-0.90f, 0.34f, -1.28f),
            floatArrayOf( 0.90f, 0.34f, -1.28f)
        )
        for (wp in wheels) {
            Matrix.setIdentityM(dc.modelMatrix, 0)
            Matrix.translateM(dc.modelMatrix, 0, x, 0.02f, z)
            Matrix.rotateM(dc.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.translateM(dc.modelMatrix, 0, wp[0], wp[1], wp[2])
            Matrix.rotateM(dc.modelMatrix, 0, 90f, 0f, 0f, 1f)
            Matrix.rotateM(dc.modelMatrix, 0, wheelAngleDeg, 0f, 1f, 0f)
            Matrix.scaleM(dc.modelMatrix, 0, 0.68f, 0.24f, 0.68f)
            dc.commitDraw(white, 1f, 1f, 0.11f, 0.12f, 0.13f, 1f, 0f, 0.14f, 12f, 0.02f, dc.assets.cylinderMesh.vertexCount)

            // Alloy rim center
            Matrix.setIdentityM(dc.modelMatrix, 0)
            Matrix.translateM(dc.modelMatrix, 0, x, 0.02f, z)
            Matrix.rotateM(dc.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.translateM(dc.modelMatrix, 0, wp[0] + (if (wp[0] > 0f) 0.035f else -0.035f), wp[1], wp[2])
            Matrix.rotateM(dc.modelMatrix, 0, 90f, 0f, 0f, 1f)
            Matrix.rotateM(dc.modelMatrix, 0, wheelAngleDeg, 0f, 1f, 0f)
            Matrix.scaleM(dc.modelMatrix, 0, 0.42f, 0.20f, 0.42f)
            dc.commitDraw(white, 1f, 1f, 0.80f, 0.84f, 0.90f, 1f, 0f, 0.85f, 44f, 0.32f, dc.assets.cylinderMesh.vertexCount)
        }

        // Front grille, splitter bumper, rear diffuser, mirrors, headlights & taillights
        dc.bindVbo(dc.assets.cubeMesh)
        drawCarLocalPart(dc, x, z, yawDeg,  0.00f, 0.40f,  2.26f, 0.92f, 0.18f, 0.08f, 0.12f, 0.13f, 0.15f, 0f)
        drawCarLocalPart(dc, x, z, yawDeg,  0.00f, 0.30f, -2.23f, 1.56f, 0.16f, 0.12f, 0.14f, 0.15f, 0.18f, 0f)
        // Side mirrors
        drawCarLocalPart(dc, x, z, yawDeg, -0.98f, 1.00f,  0.54f, 0.16f, 0.11f, 0.20f, r, g, b, 0f)
        drawCarLocalPart(dc, x, z, yawDeg,  0.98f, 1.00f,  0.54f, 0.16f, 0.11f, 0.20f, r, g, b, 0f)
        // Headlights
        drawCarLocalPart(dc, x, z, yawDeg, -0.60f, 0.48f,  2.22f, 0.32f, 0.12f, 0.10f, 1.0f, 0.97f, 0.86f, 0.95f)
        drawCarLocalPart(dc, x, z, yawDeg,  0.60f, 0.48f,  2.22f, 0.32f, 0.12f, 0.10f, 1.0f, 0.97f, 0.86f, 0.95f)
        // LED Taillights
        drawCarLocalPart(dc, x, z, yawDeg, -0.60f, 0.66f, -2.21f, 0.36f, 0.12f, 0.08f, 1.0f, 0.14f, 0.16f, 0.90f)
        drawCarLocalPart(dc, x, z, yawDeg,  0.60f, 0.66f, -2.21f, 0.36f, 0.12f, 0.08f, 1.0f, 0.14f, 0.16f, 0.90f)
    }

    fun drawCarShadow(dc: GlDrawContext, x: Float, z: Float, yawDeg: Float) {
        val white = dc.assets.texWhite
        dc.bindShadowVbo(dc.assets.carBodyMesh)
        Matrix.setIdentityM(dc.modelMatrix, 0)
        Matrix.translateM(dc.modelMatrix, 0, x, 0.06f, z)
        Matrix.rotateM(dc.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        dc.commitShadow(white, dc.assets.carBodyMesh.vertexCount)

        dc.bindShadowVbo(dc.assets.carCabinMesh)
        Matrix.setIdentityM(dc.modelMatrix, 0)
        Matrix.translateM(dc.modelMatrix, 0, x, 0.06f, z)
        Matrix.rotateM(dc.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        dc.commitShadow(white, dc.assets.carCabinMesh.vertexCount)

        dc.bindShadowVbo(dc.assets.cylinderMesh)
        val wheels = arrayOf(
            floatArrayOf(-0.90f, 0.34f,  1.34f),
            floatArrayOf( 0.90f, 0.34f,  1.34f),
            floatArrayOf(-0.90f, 0.34f, -1.28f),
            floatArrayOf( 0.90f, 0.34f, -1.28f)
        )
        for (wp in wheels) {
            Matrix.setIdentityM(dc.modelMatrix, 0)
            Matrix.translateM(dc.modelMatrix, 0, x, 0.02f, z)
            Matrix.rotateM(dc.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.translateM(dc.modelMatrix, 0, wp[0], wp[1], wp[2])
            Matrix.rotateM(dc.modelMatrix, 0, 90f, 0f, 0f, 1f)
            Matrix.scaleM(dc.modelMatrix, 0, 0.68f, 0.24f, 0.68f)
            dc.commitShadow(white, dc.assets.cylinderMesh.vertexCount)
        }
    }

    private fun drawCarLocalPart(
        dc: GlDrawContext, cx: Float, cz: Float, yawDeg: Float,
        lx: Float, ly: Float, lz: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float, emissive: Float
    ) {
        Matrix.setIdentityM(dc.modelMatrix, 0)
        Matrix.translateM(dc.modelMatrix, 0, cx, 0.06f, cz)
        Matrix.rotateM(dc.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        Matrix.translateM(dc.modelMatrix, 0, lx, ly, lz)
        Matrix.scaleM(dc.modelMatrix, 0, sx, sy, sz)
        dc.commitDraw(dc.assets.texWhite, 1f, 1f, r, g, b, 1f, emissive, 0.55f, 26f, 0.18f, dc.assets.cubeMesh.vertexCount)
    }

    // -------------------------------------------------------------------------
    // 3. STREETLIGHTS, TRAFFIC SIGNALS, PARK BENCHES & KIOSKS + SHADOWS
    // -------------------------------------------------------------------------

    fun drawStreetLight(dc: GlDrawContext, sl: StreetLightProp) {
        val white = dc.assets.texWhite
        dc.bindVbo(dc.assets.taperedCylinderMesh)
        dc.drawMeshTransformed(
            sl.x, 2.75f, sl.z, 0.22f, 5.5f, 0.22f, 0f, 0f, 0f,
            white, 1f, 1f, 0.26f, 0.30f, 0.36f, 1f, 0f, 0.45f, 24f, 0.15f, dc.assets.taperedCylinderMesh
        )
        dc.bindVbo(dc.assets.cubeMesh)
        dc.drawBox(sl.x + sl.dirX * 0.72f, 5.45f, sl.z + sl.dirZ * 0.72f, if (abs(sl.dirX) > 0.1f) 1.5f else 0.14f, 0.12f, if (abs(sl.dirZ) > 0.1f) 1.5f else 0.14f, white, 1f, 1f, 0.26f, 0.30f, 0.36f, 0f, 0.45f, 24f, 0.15f)
        dc.bindVbo(dc.assets.sphereMesh)
        dc.drawMeshTransformed(
            sl.x + sl.dirX * 1.38f, 5.34f, sl.z + sl.dirZ * 1.38f,
            0.42f, 0.22f, 0.34f, 0f, 0f, 0f,
            white, 1f, 1f, 1.0f, 0.92f, 0.64f, 1f, 0.85f, 0.4f, 16f, 0.1f, dc.assets.sphereMesh
        )
    }

    fun drawTrafficSignal(dc: GlDrawContext, ts: TrafficSignalProp, signalGreenForX: Boolean) {
        val white = dc.assets.texWhite
        dc.bindVbo(dc.assets.taperedCylinderMesh)
        dc.drawMeshTransformed(
            ts.x, 2.9f, ts.z, 0.28f, 5.8f, 0.28f, 0f, 0f, 0f,
            white, 1f, 1f, 0.18f, 0.20f, 0.24f, 1f, 0f, 0.4f, 22f, 0.12f, dc.assets.taperedCylinderMesh
        )
        dc.bindVbo(dc.assets.cubeMesh)
        val armX = ts.x + ts.armDirX * 1.6f
        val armZ = ts.z + ts.armDirZ * 1.6f
        dc.drawBox(
            armX, 5.55f, armZ,
            if (abs(ts.armDirX) > 0.1f) 3.2f else 0.16f, 0.16f, if (abs(ts.armDirZ) > 0.1f) 3.2f else 0.16f,
            white, 1f, 1f, 0.18f, 0.20f, 0.24f, 0f, 0.4f, 22f, 0.12f
        )
        val headX = ts.x + ts.armDirX * 2.5f
        val headZ = ts.z + ts.armDirZ * 2.5f
        dc.drawBox(headX, 5.05f, headZ, 0.44f, 1.15f, 0.44f, white, 1f, 1f, 0.12f, 0.13f, 0.15f, 0f, 0.2f, 16f, 0.05f)

        val isGreen = if (ts.axisXSignal) signalGreenForX else !signalGreenForX
        dc.bindVbo(dc.assets.sphereMesh)
        // Red bulb
        dc.drawMeshTransformed(
            headX, 5.38f, headZ, 0.26f, 0.26f, 0.48f, 0f, 0f, 0f, white, 1f, 1f,
            if (!isGreen) 1.0f else 0.22f, if (!isGreen) 0.14f else 0.05f, 0.08f, 1f,
            if (!isGreen) 0.95f else 0f, 0.3f, 12f, 0f, dc.assets.sphereMesh
        )
        // Green bulb
        dc.drawMeshTransformed(
            headX, 4.72f, headZ, 0.26f, 0.26f, 0.48f, 0f, 0f, 0f, white, 1f, 1f,
            0.12f, if (isGreen) 0.98f else 0.20f, if (isGreen) 0.42f else 0.10f, 1f,
            if (isGreen) 0.95f else 0f, 0.3f, 12f, 0f, dc.assets.sphereMesh
        )
    }

    fun drawBench(dc: GlDrawContext, bn: BenchProp) {
        val wood = dc.assets.texInteriorFloor
        val white = dc.assets.texWhite
        dc.bindVbo(dc.assets.cubeMesh)
        // Wooden seat + backrest
        dc.drawMeshTransformed(bn.x, 0.46f, bn.z, 2.0f, 0.10f, 0.72f, bn.yawDeg, 0f, 0f, wood, 1f, 1f, 0.78f, 0.56f, 0.36f, 1f, 0f, 0.2f, 16f, 0.04f, dc.assets.cubeMesh)
        val rad = bn.yawDeg * PI.toFloat() / 180f
        val backX = bn.x - sin(rad) * 0.30f
        val backZ = bn.z - cos(rad) * 0.30f
        dc.drawMeshTransformed(backX, 0.78f, backZ, 2.0f, 0.42f, 0.10f, bn.yawDeg, -10f, 0f, wood, 1f, 1f, 0.74f, 0.52f, 0.32f, 1f, 0f, 0.2f, 16f, 0.04f, dc.assets.cubeMesh)
        // Iron legs
        val legXOff = cos(rad) * 0.78f
        val legZOff = -sin(rad) * 0.78f
        dc.drawMeshTransformed(bn.x - legXOff, 0.23f, bn.z - legZOff, 0.14f, 0.46f, 0.68f, bn.yawDeg, 0f, 0f, white, 1f, 1f, 0.18f, 0.20f, 0.24f, 1f, 0f, 0.4f, 20f, 0.1f, dc.assets.cubeMesh)
        dc.drawMeshTransformed(bn.x + legXOff, 0.23f, bn.z + legZOff, 0.14f, 0.46f, 0.68f, bn.yawDeg, 0f, 0f, white, 1f, 1f, 0.18f, 0.20f, 0.24f, 1f, 0f, 0.4f, 20f, 0.1f, dc.assets.cubeMesh)
    }

    fun drawKiosk(dc: GlDrawContext, k: KioskProp) {
        val white = dc.assets.texWhite
        dc.bindVbo(dc.assets.cubeMesh)
        // Base stall
        dc.drawBox(k.x, 0.55f, k.z, 2.7f, 1.1f, 2.2f, dc.assets.texConcrete, 1.5f, 1f, 0.88f, 0.88f, 0.90f, 0f, 0.2f, 14f, 0.05f)
        // Back wall & colored striped awning roof
        dc.drawBox(k.x, 1.65f, k.z - 0.85f, 2.7f, 1.3f, 0.32f, dc.assets.texConcrete, 1f, 1f, 0.30f, 0.32f, 0.38f, 0f, 0.2f, 14f, 0.04f)
        dc.drawBox(k.x, 2.55f, k.z, 3.2f, 0.35f, 2.8f, white, 1f, 1f, k.r, k.g, k.b, 0.08f, 0.35f, 20f, 0.08f)
        // Glowing sign menu
        dc.drawBox(k.x, 1.75f, k.z - 0.64f, 1.6f, 0.65f, 0.08f, white, 1f, 1f, 0.98f, 0.88f, 0.52f, 0.75f, 0.3f, 18f, 0.1f)
    }

    fun drawPoleShadow(dc: GlDrawContext, x: Float, z: Float, height: Float, armDx: Float, armDz: Float) {
        dc.bindShadowVbo(dc.assets.taperedCylinderMesh)
        Matrix.setIdentityM(dc.modelMatrix, 0)
        Matrix.translateM(dc.modelMatrix, 0, x, height * 0.5f, z)
        Matrix.scaleM(dc.modelMatrix, 0, 0.24f, height, 0.24f)
        dc.commitShadow(dc.assets.texWhite, dc.assets.taperedCylinderMesh.vertexCount)

        dc.bindShadowVbo(dc.assets.cubeMesh)
        Matrix.setIdentityM(dc.modelMatrix, 0)
        Matrix.translateM(dc.modelMatrix, 0, x + armDx * 0.72f, height - 0.05f, z + armDz * 0.72f)
        Matrix.scaleM(dc.modelMatrix, 0, if (abs(armDx) > 0.1f) 1.5f else 0.16f, 0.14f, if (abs(armDz) > 0.1f) 1.5f else 0.16f)
        dc.commitShadow(dc.assets.texWhite, dc.assets.cubeMesh.vertexCount)
    }

    // -------------------------------------------------------------------------
    // 4. REALISTIC HUMAN MALE PLAYER & GTA-STYLE NPC MEN / WOMEN + HUMAN SHADOWS
    // -------------------------------------------------------------------------

    // Smooth 2-joint FK animation buffers (zero per-frame heap allocation)
    private var lastAnimTime = -1f
    private var moveBlend = 0f
    private var sprintBlend = 0f
    private var airBlend = 0f

    private val mRoot = FloatArray(16)
    private val mPelvis = FloatArray(16)
    private val mTorso = FloatArray(16)
    private val mHead = FloatArray(16)
    private val mUpper = FloatArray(16)
    private val mLower = FloatArray(16)

    fun drawHumanPlayer(
        dc: GlDrawContext,
        x: Float, y: Float, z: Float,
        yawRad: Float,
        walkCycle: Float,
        isMoving: Boolean,
        isSprinting: Boolean,
        isGrounded: Boolean,
        worldTime: Float,
        skin: CharacterSkin
    ) {
        drawAjayRealisticPlayer(
            dc = dc,
            x = x, y = y, z = z,
            yawRad = yawRad,
            walkCycle = walkCycle,
            isMoving = isMoving,
            isSprinting = isSprinting,
            isGrounded = isGrounded,
            worldTime = worldTime
        )
    }

    /**
     * Deep-detailed realistic Ajay character:
     * Real photo-mapped sculpted face, 3D wavy hair & beard contour, dark olive-green shirt
     * (folded collar, maroon buttons, chest pocket, rolled-up cuffs), red kalawa on left wrist,
     * washed blue denim jeans, sneakers, and smooth 2-joint FK animation (Idle/Walk/Sprint/Jump).
     */
    private fun drawAjayRealisticPlayer(
        dc: GlDrawContext,
        x: Float, y: Float, z: Float,
        yawRad: Float,
        walkCycle: Float,
        isMoving: Boolean,
        isSprinting: Boolean,
        isGrounded: Boolean,
        worldTime: Float
    ) {
        val dt = if (lastAnimTime < 0f) 0.016f else (worldTime - lastAnimTime).coerceIn(0.001f, 0.05f)
        lastAnimTime = worldTime

        val targetMove = if (isMoving && isGrounded) 1f else 0f
        val targetSprint = if (isSprinting && isMoving && isGrounded) 1f else 0f
        val targetAir = if (!isGrounded) 1f else 0f

        moveBlend += (targetMove - moveBlend) * (dt * 11f).coerceAtMost(1f)
        sprintBlend += (targetSprint - sprintBlend) * (dt * 8.5f).coerceAtMost(1f)
        airBlend += (targetAir - airBlend) * (dt * 12f).coerceAtMost(1f)

        val yawDeg = yawRad * 180f / PI.toFloat()
        val breath = sin(worldTime * 2.1f) * (1f - moveBlend * 0.6f)
        val strideAmp = 28f + sprintBlend * 18f
        val kneeMax = 38f + sprintBlend * 34f
        val hipBob = (abs(sin(walkCycle)) * (0.042f + sprintBlend * 0.038f)) * moveBlend + breath * 0.008f
        val hipSwayX = sin(walkCycle) * 0.018f * moveBlend
        val hipRollDeg = sin(walkCycle) * (2.2f + sprintBlend * 1.8f) * moveBlend
        val torsoLeanDeg = (3.5f + sprintBlend * 10.5f) * moveBlend + airBlend * 7f
        val torsoTwistDeg = -sin(walkCycle) * (6.5f + sprintBlend * 6.5f) * moveBlend

        val assets = dc.assets
        val cyl = assets.cylinderMesh
        val tapered = assets.taperedCylinderMesh
        val sph = assets.sphereMesh
        val white = assets.texWhite
        val texFace = assets.texPlayerFace
        val texShirt = assets.texPlayerShirt
        val texJeans = assets.texPlayerJeans

        // Photo-matched palette
        val skR = 0.78f; val skG = 0.60f; val skB = 0.47f
        val hrR = 0.07f; val hrG = 0.07f; val hrB = 0.08f

        // Root transform
        Matrix.setIdentityM(mRoot, 0)
        Matrix.translateM(mRoot, 0, x, y, z)
        Matrix.rotateM(mRoot, 0, yawDeg, 0f, 1f, 0f)

        // Pelvis transform
        System.arraycopy(mRoot, 0, mPelvis, 0, 16)
        Matrix.translateM(mPelvis, 0, hipSwayX, 0.90f + hipBob, 0f)
        Matrix.rotateM(mPelvis, 0, hipRollDeg, 0f, 0f, 1f)

        // 1. Pelvis (Washed Blue Denim) + Dark Leather Belt
        drawChildPart(dc, mPelvis, 0f, -0.02f, 0f, 0.37f, 0.17f, 0.25f, 0f, texJeans, 1.4f, 0.6f, 1f, 1f, 1f, 0.08f, 10f, cyl)
        drawChildPart(dc, mPelvis, 0f, 0.055f, 0f, 0.385f, 0.045f, 0.26f, 0f, white, 1f, 1f, 0.14f, 0.11f, 0.09f, 0.28f, 20f, cyl)

        // 2. Two-Joint Articulated Legs (Hip -> Thigh -> Knee -> Shin -> Sneaker)
        for (idx in 0..1) {
            val side = if (idx == 0) 1f else -1f
            val phase = walkCycle + (if (idx == 0) 0f else PI.toFloat())

            val walkHip = sin(phase) * strideAmp * moveBlend
            val airHip = (if (idx == 0) -22f else 18f) * airBlend
            val hipDeg = walkHip + airHip

            val swingLift = max(0f, -cos(phase))
            val pushOff = max(0f, sin(phase)) * 0.35f
            val walkKnee = (5f + swingLift * kneeMax + pushOff * kneeMax * 0.4f) * moveBlend
            val airKnee = (if (idx == 0) 36f else 54f) * airBlend
            val kneeDeg = walkKnee + airKnee

            System.arraycopy(mPelvis, 0, mUpper, 0, 16)
            Matrix.translateM(mUpper, 0, side * 0.115f, -0.04f, 0f)
            Matrix.rotateM(mUpper, 0, hipDeg, 1f, 0f, 0f)

            drawChildPart(dc, mUpper, 0f, -0.21f, 0f, 0.175f, 0.43f, 0.185f, 0f, texJeans, 1f, 1.2f, 1f, 1f, 1f, 0.08f, 10f, tapered)

            System.arraycopy(mUpper, 0, mLower, 0, 16)
            Matrix.translateM(mLower, 0, 0f, -0.42f, 0f)
            drawChildPart(dc, mLower, 0f, 0f, 0.01f, 0.145f, 0.14f, 0.15f, 0f, texJeans, 0.8f, 0.8f, 0.94f, 0.94f, 0.96f, 0.06f, 10f, sph)
            Matrix.rotateM(mLower, 0, kneeDeg, 1f, 0f, 0f)

            drawChildPart(dc, mLower, 0f, -0.20f, 0f, 0.145f, 0.40f, 0.15f, 0f, texJeans, 1f, 1.1f, 0.93f, 0.93f, 0.95f, 0.07f, 10f, tapered)
            drawChildPart(dc, mLower, 0f, -0.39f, 0f, 0.148f, 0.045f, 0.152f, 0f, texJeans, 1f, 0.3f, 0.82f, 0.84f, 0.90f, 0.05f, 8f, cyl)

            val ankleCounter = (-hipDeg * 0.25f - kneeDeg * 0.25f).coerceIn(-18f, 20f)
            Matrix.translateM(mLower, 0, 0f, -0.42f, 0f)
            Matrix.rotateM(mLower, 0, ankleCounter, 1f, 0f, 0f)
            drawChildPart(dc, mLower, 0f, -0.02f, 0.055f, 0.145f, 0.095f, 0.265f, 0f, white, 1f, 1f, 0.17f, 0.18f, 0.21f, 0.20f, 18f, sph)
            drawChildPart(dc, mLower, 0f, -0.058f, 0.055f, 0.150f, 0.036f, 0.275f, 0f, white, 1f, 1f, 0.93f, 0.94f, 0.96f, 0.14f, 14f, cyl)
        }

        // 3. Sculpted Torso (Dark Olive-Green Untucked Shirt + 3D Collar)
        System.arraycopy(mPelvis, 0, mTorso, 0, 16)
        Matrix.translateM(mTorso, 0, 0f, 0.06f, 0f)
        Matrix.rotateM(mTorso, 0, torsoLeanDeg, 1f, 0f, 0f)
        Matrix.rotateM(mTorso, 0, torsoTwistDeg, 0f, 1f, 0f)

        val chestBreath = 1f + breath * 0.015f
        drawChildPart(
            dc, mTorso, 0f, 0.27f, 0.005f,
            0.44f * chestBreath, 0.58f, 0.44f * chestBreath, 0f,
            texShirt, 1f, 1f, 1f, 1f, 1f, 0.08f, 10f, assets.playerTorsoMesh
        )

        // 3D Folded Shirt Collar
        drawChildPart(dc, mTorso, 0f, 0.545f, -0.01f, 0.19f, 0.065f, 0.18f, 0f, texShirt, 0.5f, 0.3f, 0.88f, 0.90f, 0.86f, 0.06f, 10f, cyl)
        drawChildPart(dc, mTorso, -0.058f, 0.535f, 0.075f, 0.075f, 0.055f, 0.035f, 18f, texShirt, 0.3f, 0.3f, 0.95f, 0.96f, 0.92f, 0.06f, 10f, assets.cubeMesh)
        drawChildPart(dc, mTorso,  0.058f, 0.535f, 0.075f, 0.075f, 0.055f, 0.035f, 18f, texShirt, 0.3f, 0.3f, 0.95f, 0.96f, 0.92f, 0.06f, 10f, assets.cubeMesh)

        // 4. Two-Joint Arms (Rolled Olive-Green Sleeves + Forearm + Left Wrist Red Kalawa + Hand)
        val armSwingAmp = 24f + sprintBlend * 24f
        for (idx in 0..1) {
            val side = if (idx == 0) 1f else -1f
            val armPhase = walkCycle + (if (idx == 0) PI.toFloat() else 0f)

            val walkShoulder = sin(armPhase) * armSwingAmp * moveBlend
            val airShoulder = (if (idx == 0) -28f else -18f) * airBlend
            val shoulderDeg = walkShoulder + airShoulder

            val forwardArmFactor = max(0f, -sin(armPhase))
            val walkElbow = -(14f + sprintBlend * 56f + forwardArmFactor * (22f + sprintBlend * 22f)) * moveBlend - (12f * (1f - moveBlend))
            val airElbow = -38f * airBlend
            val elbowDeg = walkElbow + airElbow

            System.arraycopy(mTorso, 0, mUpper, 0, 16)
            Matrix.translateM(mUpper, 0, side * 0.255f, 0.485f, 0f)
            drawChildPart(dc, mUpper, 0f, 0f, 0f, 0.145f, 0.145f, 0.145f, 0f, texShirt, 0.5f, 0.5f, 0.96f, 0.98f, 0.95f, 0.08f, 10f, sph)
            Matrix.rotateM(mUpper, 0, side * (4f + airBlend * 14f), 0f, 0f, 1f)
            Matrix.rotateM(mUpper, 0, shoulderDeg, 1f, 0f, 0f)

            drawChildPart(dc, mUpper, 0f, -0.145f, 0f, 0.122f, 0.29f, 0.122f, 0f, texShirt, 0.8f, 0.8f, 0.96f, 0.98f, 0.95f, 0.07f, 10f, tapered)

            System.arraycopy(mUpper, 0, mLower, 0, 16)
            Matrix.translateM(mLower, 0, 0f, -0.29f, 0f)
            drawChildPart(dc, mLower, 0f, 0f, 0f, 0.112f, 0.112f, 0.112f, 0f, texShirt, 0.4f, 0.4f, 0.92f, 0.94f, 0.90f, 0.06f, 10f, sph)
            Matrix.rotateM(mLower, 0, elbowDeg, 1f, 0f, 0f)

            // Rolled shirt cuff + bare skin forearm
            drawChildPart(dc, mLower, 0f, -0.045f, 0f, 0.118f, 0.095f, 0.118f, 0f, texShirt, 0.5f, 0.4f, 0.86f, 0.88f, 0.84f, 0.08f, 10f, cyl)
            drawChildPart(dc, mLower, 0f, -0.175f, 0f, 0.092f, 0.195f, 0.092f, 0f, white, 1f, 1f, skR, skG, skB, 0.10f, 12f, tapered)

            // Red Kalawa on left wrist
            if (side > 0f) {
                drawChildPart(dc, mLower, 0f, -0.252f, 0f, 0.086f, 0.018f, 0.086f, 0f, white, 1f, 1f, 0.88f, 0.16f, 0.12f, 0.12f, 10f, cyl)
            }

            // Sculpted hand + thumb
            drawChildPart(dc, mLower, 0f, -0.305f, 0.01f, 0.068f, 0.105f, 0.095f, 0f, white, 1f, 1f, skR, skG, skB, 0.10f, 12f, sph)
            drawChildPart(dc, mLower, -side * 0.032f, -0.295f, 0.045f, 0.032f, 0.055f, 0.038f, 25f, white, 1f, 1f, skR * 0.96f, skG * 0.96f, skB * 0.96f, 0.08f, 10f, sph)
        }

        // 5. Neck, Real Face-Mapped Sculpted Head, Ears, Beard Volume & Wavy Hair
        System.arraycopy(mTorso, 0, mHead, 0, 16)
        Matrix.translateM(mHead, 0, 0f, 0.56f, 0.008f)
        Matrix.rotateM(mHead, 0, -torsoLeanDeg * 0.72f, 1f, 0f, 0f)
        Matrix.rotateM(mHead, 0, -torsoTwistDeg * 0.55f + sin(worldTime * 0.9f) * 2.2f * (1f - moveBlend), 0f, 1f, 0f)

        drawChildPart(dc, mHead, 0f, 0.055f, 0f, 0.118f, 0.13f, 0.118f, 0f, white, 1f, 1f, skR * 0.94f, skG * 0.93f, skB * 0.92f, 0.08f, 12f, cyl)
        drawChildPart(dc, mHead, 0f, 0.225f, 0.012f, 0.305f, 0.315f, 0.305f, 0f, texFace, 1f, 1f, 1f, 1f, 1f, 0.10f, 14f, assets.playerHeadMesh)

        // Ears
        drawChildPart(dc, mHead, -0.132f, 0.215f, 0.005f, 0.042f, 0.082f, 0.052f, 0f, white, 1f, 1f, skR * 0.92f, skG * 0.90f, skB * 0.88f, 0.08f, 10f, sph)
        drawChildPart(dc, mHead,  0.132f, 0.215f, 0.005f, 0.042f, 0.082f, 0.052f, 0f, white, 1f, 1f, skR * 0.92f, skG * 0.90f, skB * 0.88f, 0.08f, 10f, sph)

        // Subtle 3D jawline beard depth
        drawChildPart(dc, mHead, 0f, 0.112f, 0.032f, 0.205f, 0.095f, 0.215f, 0f, white, 1f, 1f, hrR, hrG, hrB, 0.04f, 8f, sph)

        // Layered 3D Dark Wavy Hair
        drawChildPart(dc, mHead, 0f, 0.285f, -0.022f, 0.285f, 0.215f, 0.295f, 0f, white, 1f, 1f, hrR, hrG, hrB, 0.12f, 18f, sph)
        drawChildPart(dc, mHead, 0f, 0.355f,  0.035f, 0.235f, 0.115f, 0.210f, 0f, white, 1f, 1f, hrR, hrG, hrB, 0.14f, 20f, sph)
        drawChildPart(dc, mHead, -0.055f, 0.365f, 0.055f, 0.125f, 0.085f, 0.125f, 0f, white, 1f, 1f, hrR, hrG, hrB, 0.14f, 20f, sph)
        drawChildPart(dc, mHead,  0.052f, 0.362f, 0.048f, 0.130f, 0.088f, 0.120f, 0f, white, 1f, 1f, hrR, hrG, hrB, 0.14f, 20f, sph)
        drawChildPart(dc, mHead, 0f, 0.225f, -0.105f, 0.250f, 0.205f, 0.165f, 0f, white, 1f, 1f, hrR, hrG, hrB, 0.10f, 16f, sph)
    }

    private fun drawChildPart(
        dc: GlDrawContext,
        parentMatrix: FloatArray,
        lx: Float, ly: Float, lz: Float,
        sx: Float, sy: Float, sz: Float,
        pitchDeg: Float,
        texId: Int, uScale: Float, vScale: Float,
        r: Float, g: Float, b: Float,
        specStrength: Float, specPower: Float,
        mesh: GlMesh
    ) {
        dc.bindVbo(mesh)
        System.arraycopy(parentMatrix, 0, dc.modelMatrix, 0, 16)
        Matrix.translateM(dc.modelMatrix, 0, lx, ly, lz)
        if (pitchDeg != 0f) Matrix.rotateM(dc.modelMatrix, 0, pitchDeg, 1f, 0f, 0f)
        Matrix.scaleM(dc.modelMatrix, 0, sx, sy, sz)
        dc.commitDraw(texId, uScale, vScale, r, g, b, 1f, 0.02f, specStrength, specPower, 0.02f, mesh.vertexCount)
    }

    fun drawNpcCharacter(dc: GlDrawContext, npc: NpcAgent, worldTime: Float) {
        drawArticulatedHuman(
            dc = dc,
            x = npc.x, y = 0f, z = npc.z,
            yawRad = npc.yawRad,
            walkCycle = npc.walkPhase,
            isMoving = npc.pauseTimer <= 0f,
            isSprinting = false,
            isGrounded = true,
            worldTime = worldTime,
            isWoman = npc.isWoman,
            skR = npc.skinR, skG = npc.skinG, skB = npc.skinB,
            hrR = npc.hairR, hrG = npc.hairG, hrB = npc.hairB,
            shR = npc.topR, shG = npc.topG, shB = npc.topB,
            jkR = npc.topR * 0.90f, jkG = npc.topG * 0.90f, jkB = npc.topB * 0.90f,
            pnR = npc.bottomR, pnG = npc.bottomG, pnB = npc.bottomB,
            soR = if (npc.isWoman) 0.88f else 0.22f,
            soG = if (npc.isWoman) 0.86f else 0.22f,
            soB = if (npc.isWoman) 0.86f else 0.25f,
            acR = npc.accentR, acG = npc.accentG, acB = npc.accentB,
            hasOpenJacket = !npc.isWoman
        )
    }

    private fun drawArticulatedHuman(
        dc: GlDrawContext,
        x: Float, y: Float, z: Float,
        yawRad: Float,
        walkCycle: Float,
        isMoving: Boolean,
        isSprinting: Boolean,
        isGrounded: Boolean,
        worldTime: Float,
        isWoman: Boolean,
        skR: Float, skG: Float, skB: Float,
        hrR: Float, hrG: Float, hrB: Float,
        shR: Float, shG: Float, shB: Float,
        jkR: Float, jkG: Float, jkB: Float,
        pnR: Float, pnG: Float, pnB: Float,
        soR: Float, soG: Float, soB: Float,
        acR: Float, acG: Float, acB: Float,
        hasOpenJacket: Boolean
    ) {
        val yawDeg = yawRad * 180f / PI.toFloat()
        val swingDeg = if (isGrounded && isMoving) sin(walkCycle) * (if (isSprinting) 40f else 32f) else if (!isGrounded) 22f else 0f
        val strideBob = if (isGrounded && isMoving) abs(cos(walkCycle)) * 0.05f else sin(worldTime * 2.2f + x) * 0.012f
        val forwardLean = if (isMoving) (if (isSprinting) 9f else 3.5f) else 0f
        val heightScale = if (isWoman) 0.94f else 1.0f
        val shoulderScale = if (isWoman) 0.84f else 1.04f

        val cyl = dc.assets.cylinderMesh
        val sph = dc.assets.sphereMesh

        // 1. Legs + Knee Spheres + Calves + Sneakers
        val hipX = 0.135f * shoulderScale
        for (side in intArrayOf(1, -1)) {
            val sx = side * hipX
            val legAngle = side * swingDeg
            drawHumanLimb(dc, x, y, z, sx, 0.86f * heightScale + strideBob, 0f, 0f, -0.21f * heightScale, 0f, 0.165f * shoulderScale, 0.43f * heightScale, 0.175f, yawDeg, legAngle, pnR, pnG, pnB, cyl)
            drawHumanLimb(dc, x, y, z, sx, 0.86f * heightScale + strideBob, 0f, 0f, -0.43f * heightScale, 0.01f, 0.145f * shoulderScale, 0.14f, 0.15f, yawDeg, legAngle * 0.9f, pnR * 0.95f, pnG * 0.95f, pnB * 0.95f, sph)
            drawHumanLimb(dc, x, y, z, sx, 0.86f * heightScale + strideBob, 0f, 0f, -0.60f * heightScale, 0f, 0.140f * shoulderScale, 0.39f * heightScale, 0.145f, yawDeg, legAngle * 0.85f, pnR * 0.92f, pnG * 0.92f, pnB * 0.92f, cyl)
            drawHumanLimb(dc, x, y, z, sx, 0.86f * heightScale + strideBob, 0f, 0f, -0.79f * heightScale, 0.06f, 0.155f, 0.115f, 0.27f, yawDeg, legAngle * 0.7f, soR, soG, soB, sph)
        }

        // 2. Pelvis & Belt
        drawHumanPart(dc, x, y, z, 0f, 0.89f * heightScale + strideBob, 0f, 0.42f * shoulderScale, 0.16f, 0.27f, yawDeg, forwardLean * 0.4f, pnR * 0.85f, pnG * 0.85f, pnB * 0.85f, cyl)
        drawHumanPart(dc, x, y, z, 0f, 0.96f * heightScale + strideBob, 0f, 0.43f * shoulderScale, 0.055f, 0.28f, yawDeg, forwardLean * 0.4f, 0.15f, 0.12f, 0.10f, cyl)

        // 3. Torso & Styled Clothes
        val torsoY = 1.24f * heightScale + strideBob
        if (hasOpenJacket) {
            drawHumanPart(dc, x, y, z, 0f, torsoY, 0.035f, 0.38f * shoulderScale, 0.54f * heightScale, 0.24f, yawDeg, forwardLean, shR, shG, shB, cyl)
            drawHumanPart(dc, x, y, z, -0.135f * shoulderScale, torsoY, 0.01f, 0.23f * shoulderScale, 0.56f * heightScale, 0.27f, yawDeg, forwardLean, jkR, jkG, jkB, cyl)
            drawHumanPart(dc, x, y, z,  0.135f * shoulderScale, torsoY, 0.01f, 0.23f * shoulderScale, 0.56f * heightScale, 0.27f, yawDeg, forwardLean, jkR, jkG, jkB, cyl)
            drawHumanPart(dc, x, y, z, 0f, torsoY + 0.01f, -0.05f, 0.42f * shoulderScale, 0.56f * heightScale, 0.22f, yawDeg, forwardLean, jkR, jkG, jkB, cyl)
            drawHumanPart(dc, x, y, z, 0f, 1.52f * heightScale + strideBob, -0.01f, 0.25f, 0.085f, 0.23f, yawDeg, forwardLean, acR, acG, acB, cyl)
        } else {
            // Woman fitted top + waist scarf/belt accent
            drawHumanPart(dc, x, y, z, 0f, torsoY, 0.01f, 0.36f * shoulderScale, 0.52f * heightScale, 0.24f, yawDeg, forwardLean, shR, shG, shB, cyl)
            drawHumanPart(dc, x, y, z, 0f, 1.50f * heightScale + strideBob, 0.01f, 0.24f, 0.07f, 0.20f, yawDeg, forwardLean, acR, acG, acB, cyl)
        }

        // 4. Shoulders, Sleeves, Forearms & Hands
        val shX = 0.265f * shoulderScale
        val armPivotY = 1.44f * heightScale + strideBob
        val sleeveR = if (hasOpenJacket) jkR else shR
        val sleeveG = if (hasOpenJacket) jkG else shG
        val sleeveB = if (hasOpenJacket) jkB else shB
        for (side in intArrayOf(1, -1)) {
            val sx = side * shX
            val armAngle = -side * swingDeg * 0.85f
            drawHumanPart(dc, x, y, z, sx, 1.46f * heightScale + strideBob, 0f, 0.165f, 0.165f, 0.165f, yawDeg, forwardLean, sleeveR, sleeveG, sleeveB, sph)
            drawHumanLimb(dc, x, y, z, sx * 1.04f, armPivotY, 0f, 0f, -0.16f * heightScale, 0f, 0.125f, 0.33f * heightScale, 0.125f, yawDeg, armAngle, sleeveR, sleeveG, sleeveB, cyl)
            drawHumanLimb(dc, x, y, z, sx * 1.04f, armPivotY, 0f, 0f, -0.43f * heightScale, 0.02f, 0.105f, 0.25f * heightScale, 0.105f, yawDeg, armAngle * 1.08f, skR, skG, skB, cyl)
            drawHumanLimb(dc, x, y, z, sx * 1.04f, armPivotY, 0f, 0f, -0.58f * heightScale, 0.03f, 0.105f, 0.115f, 0.105f, yawDeg, armAngle * 1.08f, skR, skG, skB, sph)
        }

        // 5. Neck, Sculpted Face (Ears, Nose, Eyes) & Styled Hair
        val headY = 1.75f * heightScale + strideBob
        drawHumanPart(dc, x, y, z, 0f, 1.58f * heightScale + strideBob, 0.01f, 0.12f, 0.14f, 0.12f, yawDeg, forwardLean * 0.3f, skR * 0.95f, skG * 0.95f, skB * 0.95f, cyl)
        drawHumanPart(dc, x, y, z, 0f, headY, 0.02f, 0.265f, 0.31f, 0.275f, yawDeg, forwardLean * 0.2f, skR, skG, skB, sph)
        // Ears
        drawHumanPart(dc, x, y, z, -0.135f, headY, 0.01f, 0.045f, 0.085f, 0.055f, yawDeg, forwardLean * 0.2f, skR * 0.92f, skG * 0.90f, skB * 0.88f, sph)
        drawHumanPart(dc, x, y, z,  0.135f, headY, 0.01f, 0.045f, 0.085f, 0.055f, yawDeg, forwardLean * 0.2f, skR * 0.92f, skG * 0.90f, skB * 0.88f, sph)
        // Nose & Eyes
        drawHumanPart(dc, x, y, z, 0f, headY - 0.01f, 0.155f, 0.046f, 0.058f, 0.048f, yawDeg, forwardLean * 0.2f, skR * 0.93f, skG * 0.91f, skB * 0.89f, sph)
        drawHumanPart(dc, x, y, z, -0.058f, headY + 0.02f, 0.135f, 0.055f, 0.036f, 0.035f, yawDeg, forwardLean * 0.2f, 0.09f, 0.10f, 0.12f, sph)
        drawHumanPart(dc, x, y, z,  0.058f, headY + 0.02f, 0.135f, 0.055f, 0.036f, 0.035f, yawDeg, forwardLean * 0.2f, 0.09f, 0.10f, 0.12f, sph)

        // Hair (Woman long swept hair + ponytail vs Man sculpted short hair + quiff)
        drawHumanPart(dc, x, y, z, 0f, headY + 0.07f, -0.015f, 0.285f, 0.22f, 0.295f, yawDeg, forwardLean * 0.2f, hrR, hrG, hrB, sph)
        if (isWoman) {
            drawHumanPart(dc, x, y, z, 0f, headY - 0.10f, -0.145f, 0.20f, 0.34f, 0.14f, yawDeg, forwardLean * 0.2f, hrR, hrG, hrB, sph)
        } else {
            drawHumanPart(dc, x, y, z, 0f, headY + 0.12f, 0.065f, 0.21f, 0.095f, 0.17f, yawDeg, forwardLean * 0.2f, hrR, hrG, hrB, sph)
        }
    }

    fun drawHumanShadow(
        dc: GlDrawContext,
        x: Float, y: Float, z: Float,
        yawRad: Float,
        walkCycle: Float,
        isMoving: Boolean,
        isWoman: Boolean
    ) {
        val yawDeg = yawRad * 180f / PI.toFloat()
        val hs = if (isWoman) 0.94f else 1.0f
        val strideAmp = if (isMoving) 32f + (if (!isWoman) sprintBlend * 14f else 0f) else 0f
        val white = dc.assets.texWhite

        dc.bindShadowVbo(dc.assets.cylinderMesh)
        for (idx in 0..1) {
            val side = if (idx == 0) 1f else -1f
            val phase = walkCycle + (if (idx == 0) 0f else PI.toFloat())
            val hipDeg = if (isMoving) sin(phase) * strideAmp else 0f
            val kneeDeg = if (isMoving) max(0f, -cos(phase)) * (38f + sprintBlend * 28f) else 0f

            // Thigh shadow
            Matrix.setIdentityM(mUpper, 0)
            Matrix.translateM(mUpper, 0, x, y, z)
            Matrix.rotateM(mUpper, 0, yawDeg, 0f, 1f, 0f)
            Matrix.translateM(mUpper, 0, side * 0.12f, 0.86f * hs, 0f)
            Matrix.rotateM(mUpper, 0, hipDeg, 1f, 0f, 0f)

            System.arraycopy(mUpper, 0, dc.modelMatrix, 0, 16)
            Matrix.translateM(dc.modelMatrix, 0, 0f, -0.21f * hs, 0f)
            Matrix.scaleM(dc.modelMatrix, 0, 0.16f, 0.43f * hs, 0.16f)
            dc.commitShadow(white, dc.assets.cylinderMesh.vertexCount)

            // Bent shin shadow
            System.arraycopy(mUpper, 0, dc.modelMatrix, 0, 16)
            Matrix.translateM(dc.modelMatrix, 0, 0f, -0.42f * hs, 0f)
            Matrix.rotateM(dc.modelMatrix, 0, kneeDeg, 1f, 0f, 0f)
            Matrix.translateM(dc.modelMatrix, 0, 0f, -0.21f * hs, 0f)
            Matrix.scaleM(dc.modelMatrix, 0, 0.14f, 0.42f * hs, 0.14f)
            dc.commitShadow(white, dc.assets.cylinderMesh.vertexCount)

            // Arm shadow
            val armDeg = if (isMoving) -sin(phase) * strideAmp * 0.85f else 0f
            Matrix.setIdentityM(dc.modelMatrix, 0)
            Matrix.translateM(dc.modelMatrix, 0, x, y, z)
            Matrix.rotateM(dc.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.translateM(dc.modelMatrix, 0, side * 0.26f, 1.44f * hs, 0f)
            Matrix.rotateM(dc.modelMatrix, 0, armDeg, 1f, 0f, 0f)
            Matrix.translateM(dc.modelMatrix, 0, 0f, -0.27f * hs, 0f)
            Matrix.scaleM(dc.modelMatrix, 0, 0.12f, 0.54f * hs, 0.12f)
            dc.commitShadow(white, dc.assets.cylinderMesh.vertexCount)
        }

        // Torso shadow
        Matrix.setIdentityM(dc.modelMatrix, 0)
        Matrix.translateM(dc.modelMatrix, 0, x, y, z)
        Matrix.rotateM(dc.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        Matrix.translateM(dc.modelMatrix, 0, 0f, 1.24f * hs, 0f)
        Matrix.scaleM(dc.modelMatrix, 0, 0.42f, 0.58f * hs, 0.26f)
        dc.commitShadow(white, dc.assets.cylinderMesh.vertexCount)

        // Head shadow
        dc.bindShadowVbo(dc.assets.sphereMesh)
        Matrix.setIdentityM(dc.modelMatrix, 0)
        Matrix.translateM(dc.modelMatrix, 0, x, y, z)
        Matrix.rotateM(dc.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        Matrix.translateM(dc.modelMatrix, 0, 0f, 1.75f * hs, 0.02f)
        Matrix.scaleM(dc.modelMatrix, 0, 0.27f, 0.31f, 0.28f)
        dc.commitShadow(white, dc.assets.sphereMesh.vertexCount)
    }

    private fun drawHumanPart(
        dc: GlDrawContext,
        wx: Float, wy: Float, wz: Float,
        lx: Float, ly: Float, lz: Float,
        sx: Float, sy: Float, sz: Float,
        yawDeg: Float, pitchDeg: Float,
        r: Float, g: Float, b: Float,
        mesh: GlMesh
    ) {
        dc.bindVbo(mesh)
        Matrix.setIdentityM(dc.modelMatrix, 0)
        Matrix.translateM(dc.modelMatrix, 0, wx, wy, wz)
        Matrix.rotateM(dc.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        Matrix.translateM(dc.modelMatrix, 0, lx, ly, lz)
        if (pitchDeg != 0f) Matrix.rotateM(dc.modelMatrix, 0, pitchDeg, 1f, 0f, 0f)
        Matrix.scaleM(dc.modelMatrix, 0, sx, sy, sz)
        dc.commitDraw(dc.assets.texWhite, 1f, 1f, r, g, b, 1f, 0.02f, 0.14f, 14f, 0.03f, mesh.vertexCount)
    }

    private fun drawHumanLimb(
        dc: GlDrawContext,
        wx: Float, wy: Float, wz: Float,
        pivotX: Float, pivotY: Float, pivotZ: Float,
        offsetX: Float, offsetY: Float, offsetZ: Float,
        sx: Float, sy: Float, sz: Float,
        yawDeg: Float, swingDeg: Float,
        r: Float, g: Float, b: Float,
        mesh: GlMesh
    ) {
        dc.bindVbo(mesh)
        Matrix.setIdentityM(dc.modelMatrix, 0)
        Matrix.translateM(dc.modelMatrix, 0, wx, wy, wz)
        Matrix.rotateM(dc.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        Matrix.translateM(dc.modelMatrix, 0, pivotX, pivotY, pivotZ)
        if (swingDeg != 0f) Matrix.rotateM(dc.modelMatrix, 0, swingDeg, 1f, 0f, 0f)
        Matrix.translateM(dc.modelMatrix, 0, offsetX, offsetY, offsetZ)
        Matrix.scaleM(dc.modelMatrix, 0, sx, sy, sz)
        dc.commitDraw(dc.assets.texWhite, 1f, 1f, r, g, b, 1f, 0.02f, 0.12f, 12f, 0.02f, mesh.vertexCount)
    }
}
