package com.example.game.engine

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

enum class PlayerAnimClip {
    STAND,
    RUN,
    FLIP,
    HAND_FIGHT,
    LEG_FIGHT,
    RELEASE
}

/**
 * Native OpenGL ES 2.0 glTF 2.0 (.glb) GPU-Skinned Multi-Animation Character Loader & Animator.
 * Loads assets/playerstand.glb (52-joint rigged character, 5 skinned meshes, embedded PBR textures,
 * alpha MASK & BLEND hair materials, STAND clip) + all 5 extracted lossless motion-capture clips:
 *   - playerrun.glb       -> RUN (walking & sprinting locomotion)
 *   - playerflip.glb      -> FLIP (acrobatic flip, jump & parkour)
 *   - playerhandfight.glb -> HAND_FIGHT (punch combo)
 *   - plauerlegfight.glb  -> LEG_FIGHT (kick attack)
 *   - playerrelease.glb   -> RELEASE (drop / tactical landing & release)
 * with smooth zero-GC crossfade blending between every state.
 */
class GlbSkinnedPlayer private constructor(
    private val shader: SkinnedMeshShader,
    private val nodes: Array<GlbNode>,
    private val rootNodeIndices: IntArray,
    private val jointNodeIndices: IntArray,
    private val inverseBindMatrices: FloatArray,
    private val meshes: List<SkinnedSubMesh>,
    private val clips: MutableMap<PlayerAnimClip, GlbAnimClip>
) {

    private val nodeByName: Map<String, Int> = nodes.withIndex().associate { (i, n) -> n.name to i }

    fun loadExtraClipStep(context: Context, stepIndex: Int): Boolean {
        if (stepIndex !in EXTRA_CLIP_SPECS.indices) return false
        val (clipEnum, sanimPath, stripXZ) = EXTRA_CLIP_SPECS[stepIndex]
        loadSanimClip(context, sanimPath, nodeByName, stripXZ)?.let { loaded ->
            clips[clipEnum] = loaded
        }
        return stepIndex + 1 < EXTRA_CLIP_SPECS.size
    }

    // Key anatomical bone indices (resolved once on load)
    private val idxHips = nodeByName["Hips"] ?: -1
    private val idxSpine = nodeByName["Spine"] ?: -1
    private val idxSpine1 = nodeByName["Spine1"] ?: -1
    private val idxSpine2 = nodeByName["Spine2"] ?: -1
    private val idxNeck = nodeByName["Neck"] ?: -1
    private val idxHead = nodeByName["Head"] ?: -1
    private val idxLeftUpLeg = nodeByName["LeftUpLeg"] ?: -1
    private val idxLeftLeg = nodeByName["LeftLeg"] ?: -1
    private val idxLeftFoot = nodeByName["LeftFoot"] ?: -1
    private val idxRightUpLeg = nodeByName["RightUpLeg"] ?: -1
    private val idxRightLeg = nodeByName["RightLeg"] ?: -1
    private val idxRightFoot = nodeByName["RightFoot"] ?: -1
    private val idxLeftArm = nodeByName["LeftArm"] ?: -1
    private val idxLeftForeArm = nodeByName["LeftForeArm"] ?: -1
    private val idxRightArm = nodeByName["RightArm"] ?: -1
    private val idxRightForeArm = nodeByName["RightForeArm"] ?: -1

    // Zero-GC per-frame animation scratch buffers
    private val curT = Array(nodes.size) { FloatArray(3) }
    private val curR = Array(nodes.size) { FloatArray(4) }
    private val curS = Array(nodes.size) { FloatArray(3) }
    private val prevT = Array(nodes.size) { FloatArray(3) }
    private val prevR = Array(nodes.size) { FloatArray(4) }
    private val localMat = Array(nodes.size) { FloatArray(16) }
    private val worldMat = Array(nodes.size) { FloatArray(16) }
    private val skinMat = FloatArray(16)
    private val jointRows = FloatArray(MAX_JOINTS * 12) // 52 * 3 vec4 rows
    private val modelMat = FloatArray(16)
    private val shadowModelMat = FloatArray(16)

    // Multi-clip playback & crossfade state
    private var lastWorldTime = -1f
    private var activeClip: PlayerAnimClip = PlayerAnimClip.STAND
    private var hasInitializedPose = false
    private var crossfadeElapsed = 1f
    private var crossfadeDuration = 0.14f
    private var standTime = 0f
    private var runTime = 0f
    private var airFlipTime = 0.52f
    private var bikeBlend = 0f

    // Reusable quaternion scratch arrays
    private val qTmp1 = FloatArray(4)
    private val qTmp2 = FloatArray(4)
    private val qTmp3 = FloatArray(4)
    private val qTarget = FloatArray(4)

    fun getClipDuration(clip: PlayerAnimClip): Float {
        return clips[clip]?.duration ?: 1.2f
    }

    fun updatePose(
        worldTime: Float,
        walkCycle: Float,
        isMoving: Boolean,
        isSprinting: Boolean,
        isGrounded: Boolean,
        isRidingBike: Boolean,
        gateHandReach: Float = 0f,
        forcedActionClip: PlayerAnimClip? = null,
        actionTimeSec: Float = 0f,
        loopForcedClip: Boolean = false
    ) {
        val dt = if (lastWorldTime < 0f) 0.016f else (worldTime - lastWorldTime).coerceIn(0f, 0.05f)
        lastWorldTime = worldTime

        val targetBike = if (isRidingBike) 1f else 0f
        bikeBlend += (targetBike - bikeBlend) * (dt * 12f).coerceAtMost(1f)

        // Determine desired real GLB animation clip
        val desiredClip: PlayerAnimClip = when {
            forcedActionClip != null -> forcedActionClip
            isRidingBike -> PlayerAnimClip.STAND
            !isGrounded -> PlayerAnimClip.FLIP
            isMoving -> PlayerAnimClip.RUN
            else -> PlayerAnimClip.STAND
        }

        // Snapshot previous pose & trigger smooth crossfade whenever clip changes
        if (desiredClip != activeClip) {
            if (hasInitializedPose) {
                for (i in nodes.indices) {
                    prevT[i][0] = curT[i][0]; prevT[i][1] = curT[i][1]; prevT[i][2] = curT[i][2]
                    prevR[i][0] = curR[i][0]; prevR[i][1] = curR[i][1]; prevR[i][2] = curR[i][2]; prevR[i][3] = curR[i][3]
                }
                crossfadeElapsed = 0f
                crossfadeDuration = when {
                    desiredClip == PlayerAnimClip.HAND_FIGHT || desiredClip == PlayerAnimClip.LEG_FIGHT -> 0.10f
                    desiredClip == PlayerAnimClip.FLIP || desiredClip == PlayerAnimClip.RELEASE -> 0.11f
                    else -> 0.16f
                }
            }
            if (desiredClip == PlayerAnimClip.FLIP && forcedActionClip == null) {
                airFlipTime = 0.52f // Jump straight into upward launch phase
            }
            activeClip = desiredClip
        }

        // 1. Reset every node to rest pose
        for (i in nodes.indices) {
            val n = nodes[i]
            curT[i][0] = n.restT[0]; curT[i][1] = n.restT[1]; curT[i][2] = n.restT[2]
            curR[i][0] = n.restR[0]; curR[i][1] = n.restR[1]; curR[i][2] = n.restR[2]; curR[i][3] = n.restR[3]
            curS[i][0] = n.restS[0]; curS[i][1] = n.restS[1]; curS[i][2] = n.restS[2]
        }

        // 2. Sample active motion-capture clip from the 6 user GLBs
        val clip = clips[desiredClip] ?: clips[PlayerAnimClip.STAND]
        if (clip != null && clip.duration > 0.01f) {
            val sampleTime = when {
                forcedActionClip != null -> {
                    if (loopForcedClip) {
                        val mod = actionTimeSec % clip.duration
                        if (mod < 0f) mod + clip.duration else mod
                    } else {
                        actionTimeSec.coerceIn(0f, clip.duration)
                    }
                }
                desiredClip == PlayerAnimClip.RUN -> {
                    val rate = if (isSprinting) 1.24f else 0.80f
                    runTime = (runTime + dt * rate) % clip.duration
                    runTime
                }
                desiredClip == PlayerAnimClip.FLIP -> {
                    airFlipTime = (airFlipTime + dt * 1.18f).coerceAtMost(1.55f)
                    airFlipTime
                }
                else -> {
                    standTime = (standTime + dt) % clip.duration
                    standTime
                }
            }
            sampleClip(clip, sampleTime)
        }

        // 3. Smooth crossfade from previous clip pose into newly sampled pose (ZERO snap/pop!)
        if (hasInitializedPose && crossfadeElapsed < crossfadeDuration) {
            crossfadeElapsed += dt
            val w = (crossfadeElapsed / crossfadeDuration).coerceIn(0f, 1f)
            // Smoothstep easing for organic transitions
            val smoothW = w * w * (3f - 2f * w)
            for (i in nodes.indices) {
                curT[i][0] = lerp(prevT[i][0], curT[i][0], smoothW)
                curT[i][1] = lerp(prevT[i][1], curT[i][1], smoothW)
                curT[i][2] = lerp(prevT[i][2], curT[i][2], smoothW)
                quatSlerp(prevR[i], curR[i], smoothW, qTmp3)
                curR[i][0] = qTmp3[0]; curR[i][1] = qTmp3[1]; curR[i][2] = qTmp3[2]; curR[i][3] = qTmp3[3]
            }
        }
        hasInitializedPose = true

        // 3b. Bullet 350 seated riding posture overlay when riding motorcycle
        if (bikeBlend > 0.005f && forcedActionClip == null) {
            applyBikeRidingPose(bikeBlend)
        }

        // 3c. Hand-controlled gate push/pull reach pose on Right Arm & Torso when holding Open/Close Gate!
        val reachW = gateHandReach.coerceIn(0f, 1f)
        if (reachW > 0.01f && forcedActionClip == null) {
            val pushPulse = sin(worldTime * 7.5f) * 6f
            blendBoneEulerDelta(idxSpine1, 7f, -10f, 0f, reachW * 0.7f)
            blendBoneEulerDelta(idxSpine2, 5f, -8f, 0f, reachW * 0.7f)
            if (idxRightArm >= 0) {
                quatFromAxisAngle(1f, 0f, 0f, 22f, qTmp1)
                quatFromAxisAngle(0f, 1f, 0f, 76f + pushPulse, qTmp2)
                quatMul(qTmp2, qTmp1, qTmp3)
                quatMul(nodes[idxRightArm].restR, qTmp3, qTarget)
                quatSlerpInPlace(curR[idxRightArm], qTarget, reachW)
            }
            blendBoneEulerDelta(idxRightForeArm, 0f, 0f, -18f - pushPulse * 0.5f, reachW)
        }

        // 4. Compute hierarchical world matrices from Armature root
        for (rootIdx in rootNodeIndices) {
            computeWorldRecursive(rootIdx, null)
        }

        // 5. Build GPU skinning matrix rows: jointMat = worldMat[jointNode] * inverseBindMatrix[j]
        val jointCount = minOf(jointNodeIndices.size, MAX_JOINTS)
        for (j in 0 until jointCount) {
            val nodeIdx = jointNodeIndices[j]
            Matrix.multiplyMM(skinMat, 0, worldMat[nodeIdx], 0, inverseBindMatrices, j * 16)
            val base = j * 12
            jointRows[base + 0] = skinMat[0];  jointRows[base + 1] = skinMat[4];  jointRows[base + 2] = skinMat[8];  jointRows[base + 3] = skinMat[12]
            jointRows[base + 4] = skinMat[1];  jointRows[base + 5] = skinMat[5];  jointRows[base + 6] = skinMat[9];  jointRows[base + 7] = skinMat[13]
            jointRows[base + 8] = skinMat[2];  jointRows[base + 9] = skinMat[6];  jointRows[base + 10] = skinMat[10]; jointRows[base + 11] = skinMat[14]
        }
    }

    private fun applyBikeRidingPose(weight: Float) {
        val w = weight.coerceIn(0f, 1f)
        if (idxHips >= 0) {
            val n = nodes[idxHips]
            curT[idxHips][0] = lerp(curT[idxHips][0], n.restT[0], w)
            curT[idxHips][1] = lerp(curT[idxHips][1], n.restT[1] - 0.22f, w)
            curT[idxHips][2] = lerp(curT[idxHips][2], n.restT[2], w)
            quatSlerpInPlace(curR[idxHips], n.restR, w)
        }
        blendBoneEulerDelta(idxSpine, 5.6f, 0f, 0f, w)
        blendBoneEulerDelta(idxSpine1, 4.9f, 0f, 0f, w)
        blendBoneEulerDelta(idxSpine2, 3.5f, 0f, 0f, w)
        blendBoneEulerDelta(idxNeck, -6.3f, 0f, 0f, w)
        blendBoneEulerDelta(idxHead, -4.2f, 0f, 0f, w)

        blendBoneEulerDelta(idxLeftUpLeg, 62f, 0f, -14f, w)
        blendBoneEulerDelta(idxRightUpLeg, 62f, 0f, 14f, w)
        blendBoneEulerDelta(idxLeftLeg, -68f, 0f, 0f, w)
        blendBoneEulerDelta(idxRightLeg, -68f, 0f, 0f, w)
        blendBoneEulerDelta(idxLeftFoot, 10f, 0f, 0f, w)
        blendBoneEulerDelta(idxRightFoot, 10f, 0f, 0f, w)

        if (idxLeftArm >= 0) {
            quatFromAxisAngle(1f, 0f, 0f, 62f, qTmp1)
            quatFromAxisAngle(0f, 1f, 0f, -54f, qTmp2)
            quatMul(qTmp2, qTmp1, qTmp3)
            quatMul(nodes[idxLeftArm].restR, qTmp3, qTarget)
            quatSlerpInPlace(curR[idxLeftArm], qTarget, w)
        }
        if (idxRightArm >= 0) {
            quatFromAxisAngle(1f, 0f, 0f, 62f, qTmp1)
            quatFromAxisAngle(0f, 1f, 0f, 54f, qTmp2)
            quatMul(qTmp2, qTmp1, qTmp3)
            quatMul(nodes[idxRightArm].restR, qTmp3, qTarget)
            quatSlerpInPlace(curR[idxRightArm], qTarget, w)
        }
        blendBoneEulerDelta(idxLeftForeArm, 0f, 0f, 24f, w)
        blendBoneEulerDelta(idxRightForeArm, 0f, 0f, -24f, w)
    }

    private fun blendBoneEulerDelta(nodeIdx: Int, rxDeg: Float, ryDeg: Float, rzDeg: Float, weight: Float) {
        if (nodeIdx < 0) return
        quatFromEulerXYZ(rxDeg, ryDeg, rzDeg, qTmp1)
        quatMul(nodes[nodeIdx].restR, qTmp1, qTarget)
        quatSlerpInPlace(curR[nodeIdx], qTarget, weight)
    }

    fun draw(
        dc: GlDrawContext,
        x: Float,
        y: Float,
        z: Float,
        yawRad: Float,
        lightDirX: Float, lightDirY: Float, lightDirZ: Float,
        sunR: Float, sunG: Float, sunB: Float,
        ambientScale: Float,
        camX: Float, camY: Float, camZ: Float,
        fogR: Float, fogG: Float, fogB: Float,
        fogStart: Float, fogEnd: Float,
        slopePitchDeg: Float = 0f,
        slopeRollDeg: Float = 0f
    ) {
        val yawDeg = yawRad * 180f / PI.toFloat()
        Matrix.setIdentityM(modelMat, 0)
        // Clean ground lift (+0.068f at street level) keeps sneakers crisp on top of road/intersection quads!
        val groundY = if (y < 0.07f && y > -0.10f) max(y + 0.008f, 0.068f) else y + 0.008f
        Matrix.translateM(modelMat, 0, x, groundY, z)
        Matrix.rotateM(modelMat, 0, yawDeg, 0f, 1f, 0f)
        if (slopePitchDeg != 0f) Matrix.rotateM(modelMat, 0, slopePitchDeg, 1f, 0f, 0f)
        if (slopeRollDeg != 0f) Matrix.rotateM(modelMat, 0, slopeRollDeg, 0f, 0f, 1f)

        GLES20.glUseProgram(shader.programId)
        GLES20.glUniformMatrix4fv(shader.uVpMatrixLoc, 1, false, dc.vpMatrix, 0)
        GLES20.glUniformMatrix4fv(shader.uModelMatrixLoc, 1, false, modelMat, 0)
        GLES20.glUniform4fv(shader.uJointRowsLoc, MAX_JOINTS * 3, jointRows, 0)
        GLES20.glUniform3f(shader.uLightDirLoc, lightDirX, lightDirY, lightDirZ)
        GLES20.glUniform3f(shader.uSunColorLoc, sunR, sunG, sunB)
        GLES20.glUniform1f(shader.uAmbientScaleLoc, ambientScale)
        GLES20.glUniform3f(shader.uCameraPosLoc, camX, camY, camZ)
        GLES20.glUniform3f(shader.uFogColorLoc, fogR, fogG, fogB)
        GLES20.glUniform1f(shader.uFogStartLoc, fogStart)
        GLES20.glUniform1f(shader.uFogEndLoc, fogEnd)
        GLES20.glUniform1f(shader.uShadowModeLoc, 0f)

        // Pass 1: Opaque meshes first (avaturn_body, avaturn_shoes_0, avaturn_look_0)
        // Pass 2: Alpha MASK mesh (avaturn_hair_0, cutoff 0.5)
        // Pass 3: Alpha BLEND mesh last (avaturn_hair_1, double-sided, depth write off)
        for (pass in 0..2) {
            if (pass == 2) {
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glDepthMask(false)
            } else {
                GLES20.glDisable(GLES20.GL_BLEND)
            }
            for (mesh in meshes) {
                if (mesh.alphaMode != pass) continue
                if (mesh.doubleSided) GLES20.glDisable(GLES20.GL_CULL_FACE) else GLES20.glEnable(GLES20.GL_CULL_FACE)

                GLES20.glUniform1f(shader.uAlphaCutoffLoc, if (mesh.alphaMode == ALPHA_MASK) mesh.alphaCutoff else 0f)
                GLES20.glUniform1f(shader.uHasNormalLoc, if (mesh.normalTexId != 0) 1f else 0f)
                GLES20.glUniform4f(shader.uBaseFactorLoc, mesh.baseR, mesh.baseG, mesh.baseB, mesh.baseA)

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mesh.baseTexId)
                GLES20.glUniform1i(shader.uBaseTexLoc, 0)

                if (mesh.normalTexId != 0) {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mesh.normalTexId)
                    GLES20.glUniform1i(shader.uNormalTexLoc, 1)
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                }

                bindAndDrawSubMesh(mesh)
            }
            if (pass == 2) GLES20.glDepthMask(true)
        }

        // Restore OpenGL state cleanly for the city renderer
        disableAttribArrays()
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES20.glUseProgram(dc.assets.shader.programId)
        dc.boundVboId = -1
    }

    /**
     * Renders a true rigged silhouette ground shadow for the Avaturn character during the shadow pass!
     */
    fun drawShadow(
        dc: GlDrawContext,
        x: Float,
        y: Float,
        z: Float,
        yawRad: Float,
        shadowAlpha: Float,
        slopePitchDeg: Float = 0f,
        slopeRollDeg: Float = 0f
    ) {
        val yawDeg = yawRad * 180f / PI.toFloat()
        Matrix.setIdentityM(modelMat, 0)
        // Small ground lift matches street road height so shadow stays attached cleanly
        val groundY = if (y < 0.07f && y > -0.10f) max(y + 0.008f, 0.068f) else y + 0.008f
        Matrix.translateM(modelMat, 0, x, groundY, z)
        Matrix.rotateM(modelMat, 0, yawDeg, 0f, 1f, 0f)
        if (slopePitchDeg != 0f) Matrix.rotateM(modelMat, 0, slopePitchDeg, 1f, 0f, 0f)
        if (slopeRollDeg != 0f) Matrix.rotateM(modelMat, 0, slopeRollDeg, 0f, 0f, 1f)
        Matrix.multiplyMM(shadowModelMat, 0, dc.shadowProjMatrix, 0, modelMat, 0)

        GLES20.glUseProgram(shader.programId)
        GLES20.glUniformMatrix4fv(shader.uVpMatrixLoc, 1, false, dc.vpMatrix, 0)
        GLES20.glUniformMatrix4fv(shader.uModelMatrixLoc, 1, false, shadowModelMat, 0)
        GLES20.glUniform4fv(shader.uJointRowsLoc, MAX_JOINTS * 3, jointRows, 0)
        GLES20.glUniform1f(shader.uShadowModeLoc, 1f)
        GLES20.glUniform4f(shader.uBaseFactorLoc, 0.04f, 0.05f, 0.08f, shadowAlpha)
        GLES20.glUniform1f(shader.uHasNormalLoc, 0f)

        for (mesh in meshes) {
            if (mesh.alphaMode == ALPHA_BLEND) continue // Keep shadow silhouette crisp using opaque + masked meshes
            GLES20.glUniform1f(shader.uAlphaCutoffLoc, if (mesh.alphaMode == ALPHA_MASK) mesh.alphaCutoff else 0f)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mesh.baseTexId)
            GLES20.glUniform1i(shader.uBaseTexLoc, 0)
            bindAndDrawSubMesh(mesh)
        }

        disableAttribArrays()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES20.glUseProgram(dc.assets.shadowShader.programId)
        dc.boundShadowVboId = -1
    }

    /**
     * Standalone studio renderer used in the Lobby & Character Showcase screen
     * (supports 360-degree spin, pinch zoom, and crisp Face Zoom without requiring GlDrawContext).
     */
    fun drawStandalone(
        vpMatrix: FloatArray,
        yawDeg: Float,
        lightDirX: Float, lightDirY: Float, lightDirZ: Float,
        sunR: Float, sunG: Float, sunB: Float,
        ambientScale: Float,
        camX: Float, camY: Float, camZ: Float
    ) {
        Matrix.setIdentityM(modelMat, 0)
        Matrix.rotateM(modelMat, 0, yawDeg, 0f, 1f, 0f)

        GLES20.glUseProgram(shader.programId)
        GLES20.glUniformMatrix4fv(shader.uVpMatrixLoc, 1, false, vpMatrix, 0)
        GLES20.glUniformMatrix4fv(shader.uModelMatrixLoc, 1, false, modelMat, 0)
        GLES20.glUniform4fv(shader.uJointRowsLoc, MAX_JOINTS * 3, jointRows, 0)
        GLES20.glUniform3f(shader.uLightDirLoc, lightDirX, lightDirY, lightDirZ)
        GLES20.glUniform3f(shader.uSunColorLoc, sunR, sunG, sunB)
        GLES20.glUniform1f(shader.uAmbientScaleLoc, ambientScale)
        GLES20.glUniform3f(shader.uCameraPosLoc, camX, camY, camZ)
        GLES20.glUniform3f(shader.uFogColorLoc, 0.1f, 0.12f, 0.15f)
        GLES20.glUniform1f(shader.uFogStartLoc, 40f)
        GLES20.glUniform1f(shader.uFogEndLoc, 90f)
        GLES20.glUniform1f(shader.uShadowModeLoc, 0f)

        for (pass in 0..2) {
            if (pass == 2) {
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glDepthMask(false)
            } else {
                GLES20.glDisable(GLES20.GL_BLEND)
            }
            for (mesh in meshes) {
                if (mesh.alphaMode != pass) continue
                if (mesh.doubleSided) GLES20.glDisable(GLES20.GL_CULL_FACE) else GLES20.glEnable(GLES20.GL_CULL_FACE)

                GLES20.glUniform1f(shader.uAlphaCutoffLoc, if (mesh.alphaMode == ALPHA_MASK) mesh.alphaCutoff else 0f)
                GLES20.glUniform1f(shader.uHasNormalLoc, if (mesh.normalTexId != 0) 1f else 0f)
                GLES20.glUniform4f(shader.uBaseFactorLoc, mesh.baseR, mesh.baseG, mesh.baseB, mesh.baseA)

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mesh.baseTexId)
                GLES20.glUniform1i(shader.uBaseTexLoc, 0)

                if (mesh.normalTexId != 0) {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mesh.normalTexId)
                    GLES20.glUniform1i(shader.uNormalTexLoc, 1)
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                }

                bindAndDrawSubMesh(mesh)
            }
            if (pass == 2) GLES20.glDepthMask(true)
        }

        disableAttribArrays()
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun bindAndDrawSubMesh(mesh: SkinnedSubMesh) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.vboId)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.iboId)

        val stride = 16 * 4 // 64 bytes
        if (shader.aPosLoc >= 0) {
            GLES20.glEnableVertexAttribArray(shader.aPosLoc)
            GLES20.glVertexAttribPointer(shader.aPosLoc, 3, GLES20.GL_FLOAT, false, stride, 0)
        }
        if (shader.aNormLoc >= 0) {
            GLES20.glEnableVertexAttribArray(shader.aNormLoc)
            GLES20.glVertexAttribPointer(shader.aNormLoc, 3, GLES20.GL_FLOAT, false, stride, 12)
        }
        if (shader.aUvLoc >= 0) {
            GLES20.glEnableVertexAttribArray(shader.aUvLoc)
            GLES20.glVertexAttribPointer(shader.aUvLoc, 2, GLES20.GL_FLOAT, false, stride, 24)
        }
        if (shader.aJointsLoc >= 0) {
            GLES20.glEnableVertexAttribArray(shader.aJointsLoc)
            GLES20.glVertexAttribPointer(shader.aJointsLoc, 4, GLES20.GL_FLOAT, false, stride, 32)
        }
        if (shader.aWeightsLoc >= 0) {
            GLES20.glEnableVertexAttribArray(shader.aWeightsLoc)
            GLES20.glVertexAttribPointer(shader.aWeightsLoc, 4, GLES20.GL_FLOAT, false, stride, 48)
        }

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indexCount, GLES20.GL_UNSIGNED_SHORT, 0)
    }

    private fun disableAttribArrays() {
        if (shader.aPosLoc >= 0) GLES20.glDisableVertexAttribArray(shader.aPosLoc)
        if (shader.aNormLoc >= 0) GLES20.glDisableVertexAttribArray(shader.aNormLoc)
        if (shader.aUvLoc >= 0) GLES20.glDisableVertexAttribArray(shader.aUvLoc)
        if (shader.aJointsLoc >= 0) GLES20.glDisableVertexAttribArray(shader.aJointsLoc)
        if (shader.aWeightsLoc >= 0) GLES20.glDisableVertexAttribArray(shader.aWeightsLoc)
    }

    // -------------------------------------------------------------------------
    // ANIMATION SAMPLING & HIERARCHY MATH
    // -------------------------------------------------------------------------

    private fun sampleClip(clip: GlbAnimClip, timeSec: Float) {
        for (ch in clip.channels) {
            val times = ch.times
            if (times.isEmpty()) continue
            val lastIdx = times.lastIndex
            var i0 = 0
            var i1 = 0
            var frac = 0f

            if (timeSec <= times[0]) {
                i0 = 0; i1 = 0; frac = 0f
            } else if (timeSec >= times[lastIdx]) {
                i0 = lastIdx; i1 = lastIdx; frac = 0f
            } else {
                // Binary search keyframe bracket
                var lo = 0
                var hi = lastIdx
                while (lo + 1 < hi) {
                    val mid = (lo + hi) ushr 1
                    if (times[mid] <= timeSec) lo = mid else hi = mid
                }
                i0 = lo
                i1 = hi
                val span = (times[i1] - times[i0]).coerceAtLeast(1e-5f)
                frac = ((timeSec - times[i0]) / span).coerceIn(0f, 1f)
            }

            val v = ch.values
            val ni = ch.nodeIndex
            if (ch.isRotation) {
                val b0 = i0 * 4
                val b1 = i1 * 4
                qTmp1[0] = v[b0]; qTmp1[1] = v[b0 + 1]; qTmp1[2] = v[b0 + 2]; qTmp1[3] = v[b0 + 3]
                qTmp2[0] = v[b1]; qTmp2[1] = v[b1 + 1]; qTmp2[2] = v[b1 + 2]; qTmp2[3] = v[b1 + 3]
                quatSlerp(qTmp1, qTmp2, frac, curR[ni])
            } else {
                val b0 = i0 * 3
                val b1 = i1 * 3
                var tx = lerp(v[b0], v[b1], frac)
                val ty = lerp(v[b0 + 1], v[b1 + 1], frac)
                var tz = lerp(v[b0 + 2], v[b1 + 2], frac)
                if (clip.stripRootXZ && ni == idxHips && v.size >= 3) {
                    val rest = nodes[ni].restT
                    tx = rest[0] + (tx - v[0]).coerceIn(-0.22f, 0.22f)
                    tz = rest[2] + (tz - v[2]).coerceIn(-0.22f, 0.22f)
                }
                curT[ni][0] = tx
                curT[ni][1] = ty
                curT[ni][2] = tz
            }
        }
    }

    private fun computeWorldRecursive(nodeIdx: Int, parentWorld: FloatArray?) {
        val lm = localMat[nodeIdx]
        composeTRS(curT[nodeIdx], curR[nodeIdx], curS[nodeIdx], lm)
        if (parentWorld == null) {
            System.arraycopy(lm, 0, worldMat[nodeIdx], 0, 16)
        } else {
            Matrix.multiplyMM(worldMat[nodeIdx], 0, parentWorld, 0, lm, 0)
        }
        val children = nodes[nodeIdx].children
        for (c in children) {
            computeWorldRecursive(c, worldMat[nodeIdx])
        }
    }

    companion object {
        private const val TAG = "GlbSkinnedPlayer"
        private const val MAX_JOINTS = 52
        private const val ALPHA_OPAQUE = 0
        private const val ALPHA_MASK = 1
        private const val ALPHA_BLEND = 2

        private val EXTRA_CLIP_SPECS = listOf(
            Triple(PlayerAnimClip.RUN, "animations/playerrun.sanim", false),
            Triple(PlayerAnimClip.FLIP, "animations/playerflip.sanim", false),
            Triple(PlayerAnimClip.HAND_FIGHT, "animations/playerhandfight.sanim", false),
            Triple(PlayerAnimClip.LEG_FIGHT, "animations/plauerlegfight.sanim", false),
            Triple(PlayerAnimClip.RELEASE, "animations/playerrelease.sanim", true)
        )

        val extraClipCount: Int get() = EXTRA_CLIP_SPECS.size

        fun loadFromAssets(context: Context, assetPath: String, fallbackWhiteTex: Int): GlbSkinnedPlayer? {
            val player = loadBaseFromAssets(context, assetPath, fallbackWhiteTex) ?: return null
            for (i in EXTRA_CLIP_SPECS.indices) {
                player.loadExtraClipStep(context, i)
            }
            return player
        }

        fun loadBaseFromAssets(context: Context, assetPath: String, fallbackWhiteTex: Int): GlbSkinnedPlayer? {
            return runCatching {
                val bytes = context.assets.open(assetPath).use { it.readBytes() }
                val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val magic = bb.int
                val version = bb.int
                bb.int // total length
                if (magic != 0x46546C67 || version != 2) {
                    Log.e(TAG, "Invalid GLB header in $assetPath")
                    return null
                }

                // Chunk 0: JSON
                val jsonLen = bb.int
                val jsonType = bb.int
                if (jsonType != 0x4E4F534A) return null
                val jsonBytes = ByteArray(jsonLen)
                bb.get(jsonBytes)
                val rootJson = JSONObject(String(jsonBytes, Charsets.UTF_8))

                // Chunk 1: BIN
                val binLen = bb.int
                val binType = bb.int
                if (binType != 0x004E4942) return null
                val binOffsetInFile = bb.position()
                // CRITICAL: .slice() rebases index 0 to binOffsetInFile, and .order() MUST be set after slice()!
                val binSlice = ByteBuffer.wrap(bytes, binOffsetInFile, binLen).slice().order(ByteOrder.LITTLE_ENDIAN)

                val bufferViews = rootJson.getJSONArray("bufferViews")
                val accessors = rootJson.getJSONArray("accessors")
                val nodesArr = rootJson.getJSONArray("nodes")
                val meshesArr = rootJson.getJSONArray("meshes")
                val materialsArr = rootJson.optJSONArray("materials") ?: JSONArray()
                val texturesArr = rootJson.optJSONArray("textures") ?: JSONArray()
                val imagesArr = rootJson.optJSONArray("images") ?: JSONArray()
                val skinsArr = rootJson.getJSONArray("skins")
                val animsArr = rootJson.optJSONArray("animations") ?: JSONArray()

                // 1. Parse Nodes
                val nodes = Array(nodesArr.length()) { i ->
                    val nj = nodesArr.getJSONObject(i)
                    val name = nj.optString("name", "node_$i")
                    val t = readVec3(nj.optJSONArray("translation"), 0f, 0f, 0f)
                    val r = readVec4(nj.optJSONArray("rotation"), 0f, 0f, 0f, 1f)
                    val s = readVec3(nj.optJSONArray("scale"), 1f, 1f, 1f)
                    val chArr = nj.optJSONArray("children")
                    val children = if (chArr != null) IntArray(chArr.length()) { chArr.getInt(it) } else IntArray(0)
                    GlbNode(name, t, r, s, children)
                }

                val scenesArr = rootJson.optJSONArray("scenes")
                val rootNodeIndices = if (scenesArr != null && scenesArr.length() > 0) {
                    val sn = scenesArr.getJSONObject(0).getJSONArray("nodes")
                    IntArray(sn.length()) { sn.getInt(it) }
                } else intArrayOf(nodes.lastIndex)

                // 2. Parse Skin 0
                val skin0 = skinsArr.getJSONObject(0)
                val jointsJson = skin0.getJSONArray("joints")
                val jointNodeIndices = IntArray(jointsJson.length()) { jointsJson.getInt(it) }
                val ibmAccessorIdx = skin0.getInt("inverseBindMatrices")
                val inverseBindMatrices = readFloatAccessor(accessors, bufferViews, binSlice, ibmAccessorIdx, 16)

                // 3. Decode & Upload Embedded Textures (cached by image index!)
                val loadedImageTex = HashMap<Int, Int>()
                fun getTextureId(texObj: JSONObject?): Int {
                    if (texObj == null) return 0
                    val texIdx = texObj.optInt("index", -1)
                    if (texIdx < 0 || texIdx >= texturesArr.length()) return 0
                    val imgIdx = texturesArr.getJSONObject(texIdx).optInt("source", -1)
                    if (imgIdx < 0 || imgIdx >= imagesArr.length()) return 0
                    loadedImageTex[imgIdx]?.let { return it }
                    val imgObj = imagesArr.getJSONObject(imgIdx)
                    val bvIdx = imgObj.optInt("bufferView", -1)
                    if (bvIdx < 0) return 0
                    val bv = bufferViews.getJSONObject(bvIdx)
                    val byteOff = bv.optInt("byteOffset", 0)
                    val byteLen = bv.getInt("byteLength")
                    val texId = uploadEmbeddedImage(bytes, binOffsetInFile + byteOff, byteLen)
                    loadedImageTex[imgIdx] = texId
                    return texId
                }

                // 4. Parse all 5 Skinned Meshes into GPU VBO + IBO
                val subMeshes = ArrayList<SkinnedSubMesh>(meshesArr.length())
                for (mi in 0 until meshesArr.length()) {
                    val mj = meshesArr.getJSONObject(mi)
                    val prims = mj.getJSONArray("primitives")
                    for (pi in 0 until prims.length()) {
                        val pj = prims.getJSONObject(pi)
                        val attrs = pj.getJSONObject("attributes")
                        val posArr = readFloatAccessor(accessors, bufferViews, binSlice, attrs.getInt("POSITION"), 3)
                        val normArr = if (attrs.has("NORMAL")) readFloatAccessor(accessors, bufferViews, binSlice, attrs.getInt("NORMAL"), 3) else FloatArray(posArr.size)
                        val uvArr = if (attrs.has("TEXCOORD_0")) readFloatAccessor(accessors, bufferViews, binSlice, attrs.getInt("TEXCOORD_0"), 2) else FloatArray((posArr.size / 3) * 2)
                        val jointsArr = readJointsAccessor(accessors, bufferViews, binSlice, attrs.getInt("JOINTS_0"))
                        val weightsArr = readFloatAccessor(accessors, bufferViews, binSlice, attrs.getInt("WEIGHTS_0"), 4)
                        val indicesArr = readUShortAccessor(accessors, bufferViews, binSlice, pj.getInt("indices"))

                        val vertCount = posArr.size / 3
                        val interleaved = FloatArray(vertCount * 16)
                        for (v in 0 until vertCount) {
                            val o = v * 16
                            val p3 = v * 3
                            val p2 = v * 2
                            val p4 = v * 4
                            interleaved[o + 0] = posArr[p3]
                            interleaved[o + 1] = posArr[p3 + 1]
                            interleaved[o + 2] = posArr[p3 + 2]
                            interleaved[o + 3] = normArr[p3]
                            interleaved[o + 4] = normArr[p3 + 1]
                            interleaved[o + 5] = normArr[p3 + 2]
                            interleaved[o + 6] = uvArr[p2]
                            interleaved[o + 7] = uvArr[p2 + 1]
                            interleaved[o + 8] = jointsArr[p4]
                            interleaved[o + 9] = jointsArr[p4 + 1]
                            interleaved[o + 10] = jointsArr[p4 + 2]
                            interleaved[o + 11] = jointsArr[p4 + 3]
                            // Normalize weights defensively
                            val w0 = weightsArr[p4]; val w1 = weightsArr[p4 + 1]; val w2 = weightsArr[p4 + 2]; val w3 = weightsArr[p4 + 3]
                            val wSum = (w0 + w1 + w2 + w3).let { if (it > 1e-4f) it else 1f }
                            interleaved[o + 12] = w0 / wSum
                            interleaved[o + 13] = w1 / wSum
                            interleaved[o + 14] = w2 / wSum
                            interleaved[o + 15] = w3 / wSum
                        }

                        val buffers = IntArray(2)
                        GLES20.glGenBuffers(2, buffers, 0)
                        val vboId = buffers[0]
                        val iboId = buffers[1]

                        val vBuf = ByteBuffer.allocateDirect(interleaved.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
                        vBuf.put(interleaved).position(0)
                        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
                        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, interleaved.size * 4, vBuf, GLES20.GL_STATIC_DRAW)

                        val iBuf = ByteBuffer.allocateDirect(indicesArr.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
                        iBuf.put(indicesArr).position(0)
                        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboId)
                        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indicesArr.size * 2, iBuf, GLES20.GL_STATIC_DRAW)

                        // Parse Material
                        val matIdx = pj.optInt("material", -1)
                        var baseTex = fallbackWhiteTex
                        var normTex = 0
                        var alphaMode = ALPHA_OPAQUE
                        var alphaCutoff = 0.5f
                        var doubleSided = false
                        var baseR = 1f; var baseG = 1f; var baseB = 1f; var baseA = 1f

                        if (matIdx in 0 until materialsArr.length()) {
                            val matObj = materialsArr.getJSONObject(matIdx)
                            val pbr = matObj.optJSONObject("pbrMetallicRoughness")
                            val bt = getTextureId(pbr?.optJSONObject("baseColorTexture"))
                            if (bt != 0) baseTex = bt
                            normTex = getTextureId(matObj.optJSONObject("normalTexture"))
                            doubleSided = matObj.optBoolean("doubleSided", false)
                            alphaCutoff = matObj.optDouble("alphaCutoff", 0.5).toFloat()
                            alphaMode = when (matObj.optString("alphaMode", "OPAQUE")) {
                                "MASK" -> ALPHA_MASK
                                "BLEND" -> ALPHA_BLEND
                                else -> ALPHA_OPAQUE
                            }
                            pbr?.optJSONArray("baseColorFactor")?.let { bf ->
                                baseR = bf.optDouble(0, 1.0).toFloat()
                                baseG = bf.optDouble(1, 1.0).toFloat()
                                baseB = bf.optDouble(2, 1.0).toFloat()
                                baseA = bf.optDouble(3, 1.0).toFloat()
                            }
                        }

                        subMeshes.add(
                            SkinnedSubMesh(
                                vboId = vboId,
                                iboId = iboId,
                                indexCount = indicesArr.size,
                                baseTexId = baseTex,
                                normalTexId = normTex,
                                alphaMode = alphaMode,
                                alphaCutoff = alphaCutoff,
                                doubleSided = doubleSided,
                                baseR = baseR, baseG = baseG, baseB = baseB, baseA = baseA
                            )
                        )
                    }
                }
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
                GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)

                // 5. Parse Embedded STAND Animation 0 + Load All 5 Companion .sanim Clips!
                val clipMap = HashMap<PlayerAnimClip, GlbAnimClip>(6)
                if (animsArr.length() > 0) {
                    val animObj = animsArr.getJSONObject(0)
                    val chArr = animObj.getJSONArray("channels")
                    val sampArr = animObj.getJSONArray("samplers")
                    val channels = ArrayList<GlbAnimChannel>(chArr.length())
                    var maxDur = 0f
                    for (ci in 0 until chArr.length()) {
                        val ch = chArr.getJSONObject(ci)
                        val target = ch.getJSONObject("target")
                        val path = target.optString("path", "")
                        if (path != "rotation" && path != "translation") continue
                        val nodeIdx = target.optInt("node", -1)
                        if (nodeIdx < 0 || nodeIdx >= nodes.size) continue
                        val samp = sampArr.getJSONObject(ch.getInt("sampler"))
                        val times = readFloatAccessor(accessors, bufferViews, binSlice, samp.getInt("input"), 1)
                        val comps = if (path == "rotation") 4 else 3
                        val values = readFloatAccessor(accessors, bufferViews, binSlice, samp.getInt("output"), comps)
                        if (times.isNotEmpty() && times.last() > maxDur) maxDur = times.last()
                        channels.add(GlbAnimChannel(nodeIdx, path == "rotation", times, values))
                    }
                    clipMap[PlayerAnimClip.STAND] = GlbAnimClip(maxDur, channels, stripRootXZ = false)
                }

                val shader = SkinnedMeshShader()
                if (!shader.isValid) {
                    Log.e(TAG, "SkinnedMeshShader failed to link — falling back to default character")
                    return null
                }
                Log.i(TAG, "Loaded base $assetPath (embedded clips: ${clipMap.keys})")
                GlbSkinnedPlayer(shader, nodes, rootNodeIndices, jointNodeIndices, inverseBindMatrices, subMeshes, clipMap)
            }.onFailure { e ->
                Log.e(TAG, "Failed to load $assetPath: ${e.message}", e)
            }.getOrNull()
        }

        private fun loadSanimClip(
            context: Context,
            assetPath: String,
            nodeByName: Map<String, Int>,
            stripRootXZ: Boolean
        ): GlbAnimClip? {
            return runCatching {
                val raw = context.assets.open(assetPath).use { it.readBytes() }
                val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
                val b0 = bb.get().toInt().toChar()
                val b1 = bb.get().toInt().toChar()
                val b2 = bb.get().toInt().toChar()
                val b3 = bb.get().toInt().toChar()
                if (b0 != 'S' || b1 != 'A' || b2 != 'N' || b3 != 'M') return null
                bb.int // version
                val duration = bb.float
                val channelCount = bb.int
                val channels = ArrayList<GlbAnimChannel>(channelCount)
                for (c in 0 until channelCount) {
                    val nameLen = bb.short.toInt() and 0xFFFF
                    val nameBytes = ByteArray(nameLen)
                    bb.get(nameBytes)
                    val nodeName = String(nameBytes, Charsets.UTF_8)
                    val isRotation = bb.get().toInt() != 0
                    val keyCount = bb.int
                    val times = FloatArray(keyCount) { bb.float }
                    val comps = if (isRotation) 4 else 3
                    val values = FloatArray(keyCount * comps) { bb.float }
                    val nodeIdx = nodeByName[nodeName] ?: -1
                    if (nodeIdx >= 0 && keyCount > 0) {
                        channels.add(GlbAnimChannel(nodeIdx, isRotation, times, values))
                    }
                }
                GlbAnimClip(duration, channels, stripRootXZ)
            }.onFailure { e ->
                Log.e(TAG, "Failed to load $assetPath: ${e.message}")
            }.getOrNull()
        }

        private fun uploadEmbeddedImage(bytes: ByteArray, offset: Int, length: Int): Int {
            val bounds = BitmapFactory.Options().apply {
                inScaled = false
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, offset, length, bounds)
            var sample = 1
            var w = bounds.outWidth
            var h = bounds.outHeight
            while (w > 512 || h > 512) {
                sample *= 2
                w /= 2
                h /= 2
            }
            val opts = BitmapFactory.Options().apply {
                inScaled = false
                inSampleSize = sample
            }
            val bmp = BitmapFactory.decodeByteArray(bytes, offset, length, opts) ?: return 0
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
            bmp.recycle()
            return ids[0]
        }

        private fun readFloatAccessor(accessors: JSONArray, bufferViews: JSONArray, bin: ByteBuffer, accIdx: Int, comps: Int): FloatArray {
            val acc = accessors.getJSONObject(accIdx)
            val bv = bufferViews.getJSONObject(acc.getInt("bufferView"))
            val count = acc.getInt("count")
            val baseOff = bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
            val stride = bv.optInt("byteStride", comps * 4).let { if (it <= 0) comps * 4 else it }
            val out = FloatArray(count * comps)
            for (i in 0 until count) {
                val p = baseOff + i * stride
                val o = i * comps
                for (c in 0 until comps) {
                    out[o + c] = bin.getFloat(p + c * 4)
                }
            }
            return out
        }

        private fun readJointsAccessor(accessors: JSONArray, bufferViews: JSONArray, bin: ByteBuffer, accIdx: Int): FloatArray {
            val acc = accessors.getJSONObject(accIdx)
            val bv = bufferViews.getJSONObject(acc.getInt("bufferView"))
            val count = acc.getInt("count")
            val compType = acc.getInt("componentType")
            val baseOff = bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
            val elemSize = if (compType == 5121) 1 else 2
            val stride = bv.optInt("byteStride", 4 * elemSize).let { if (it <= 0) 4 * elemSize else it }
            val out = FloatArray(count * 4)
            for (i in 0 until count) {
                val p = baseOff + i * stride
                val o = i * 4
                for (c in 0 until 4) {
                    val idx = if (compType == 5121) {
                        bin.get(p + c).toInt() and 0xFF
                    } else {
                        bin.getShort(p + c * 2).toInt() and 0xFFFF
                    }
                    out[o + c] = idx.coerceIn(0, MAX_JOINTS - 1).toFloat()
                }
            }
            return out
        }

        private fun readUShortAccessor(accessors: JSONArray, bufferViews: JSONArray, bin: ByteBuffer, accIdx: Int): ShortArray {
            val acc = accessors.getJSONObject(accIdx)
            val bv = bufferViews.getJSONObject(acc.getInt("bufferView"))
            val count = acc.getInt("count")
            val baseOff = bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
            val compType = acc.getInt("componentType")
            val out = ShortArray(count)
            if (compType == 5123) {
                for (i in 0 until count) out[i] = bin.getShort(baseOff + i * 2)
            } else if (compType == 5125) {
                for (i in 0 until count) out[i] = bin.getInt(baseOff + i * 4).toShort()
            } else {
                for (i in 0 until count) out[i] = (bin.get(baseOff + i).toInt() and 0xFF).toShort()
            }
            return out
        }

        private fun readVec3(arr: JSONArray?, dx: Float, dy: Float, dz: Float): FloatArray {
            if (arr == null || arr.length() < 3) return floatArrayOf(dx, dy, dz)
            return floatArrayOf(arr.getDouble(0).toFloat(), arr.getDouble(1).toFloat(), arr.getDouble(2).toFloat())
        }

        private fun readVec4(arr: JSONArray?, dx: Float, dy: Float, dz: Float, dw: Float): FloatArray {
            if (arr == null || arr.length() < 4) return floatArrayOf(dx, dy, dz, dw)
            return floatArrayOf(arr.getDouble(0).toFloat(), arr.getDouble(1).toFloat(), arr.getDouble(2).toFloat(), arr.getDouble(3).toFloat())
        }

        // --- Fast zero-allocation math utilities ---
        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

        private fun composeTRS(t: FloatArray, q: FloatArray, s: FloatArray, m: FloatArray) {
            val x = q[0]; val y = q[1]; val z = q[2]; val w = q[3]
            val xx = x * x; val yy = y * y; val zz = z * z
            val xy = x * y; val xz = x * z; val yz = y * z
            val wx = w * x; val wy = w * y; val wz = w * z
            val sx = s[0]; val sy = s[1]; val sz = s[2]

            m[0] = (1f - 2f * (yy + zz)) * sx
            m[1] = (2f * (xy + wz)) * sx
            m[2] = (2f * (xz - wy)) * sx
            m[3] = 0f

            m[4] = (2f * (xy - wz)) * sy
            m[5] = (1f - 2f * (xx + zz)) * sy
            m[6] = (2f * (yz + wx)) * sy
            m[7] = 0f

            m[8] = (2f * (xz + wy)) * sz
            m[9] = (2f * (yz - wx)) * sz
            m[10] = (1f - 2f * (xx + yy)) * sz
            m[11] = 0f

            m[12] = t[0]; m[13] = t[1]; m[14] = t[2]; m[15] = 1f
        }

        private fun quatFromAxisAngle(ax: Float, ay: Float, az: Float, deg: Float, out: FloatArray) {
            val half = deg * (PI.toFloat() / 360f)
            val s = sin(half)
            out[0] = ax * s; out[1] = ay * s; out[2] = az * s; out[3] = cos(half)
        }

        private fun quatFromEulerXYZ(rxDeg: Float, ryDeg: Float, rzDeg: Float, out: FloatArray) {
            val hx = rxDeg * (PI.toFloat() / 360f)
            val hy = ryDeg * (PI.toFloat() / 360f)
            val hz = rzDeg * (PI.toFloat() / 360f)
            val cx = cos(hx); val sx = sin(hx)
            val cy = cos(hy); val sy = sin(hy)
            val cz = cos(hz); val sz = sin(hz)
            out[0] = sx * cy * cz + cx * sy * sz
            out[1] = cx * sy * cz - sx * cy * sz
            out[2] = cx * cy * sz + sx * sy * cz
            out[3] = cx * cy * cz - sx * sy * sz
        }

        private fun quatMul(a: FloatArray, b: FloatArray, out: FloatArray) {
            val ax = a[0]; val ay = a[1]; val az = a[2]; val aw = a[3]
            val bx = b[0]; val by = b[1]; val bz = b[2]; val bw = b[3]
            out[0] = aw * bx + ax * bw + ay * bz - az * by
            out[1] = aw * by - ax * bz + ay * bw + az * bx
            out[2] = aw * bz + ax * by - ay * bx + az * bw
            out[3] = aw * bw - ax * bx - ay * by - az * bz
        }

        private fun quatSlerpInPlace(a: FloatArray, b: FloatArray, t: Float) {
            quatSlerp(a, b, t, a)
        }

        private fun quatSlerp(a: FloatArray, b: FloatArray, t: Float, out: FloatArray) {
            var bx = b[0]; var by = b[1]; var bz = b[2]; var bw = b[3]
            var dot = a[0] * bx + a[1] * by + a[2] * bz + a[3] * bw
            if (dot < 0f) {
                dot = -dot; bx = -bx; by = -by; bz = -bz; bw = -bw
            }
            val w0: Float
            val w1: Float
            if (dot > 0.9995f) {
                w0 = 1f - t; w1 = t
            } else {
                val theta = acos(dot.coerceIn(-1f, 1f))
                val invSin = 1f / sin(theta)
                w0 = sin((1f - t) * theta) * invSin
                w1 = sin(t * theta) * invSin
            }
            val rx = a[0] * w0 + bx * w1
            val ry = a[1] * w0 + by * w1
            val rz = a[2] * w0 + bz * w1
            val rw = a[3] * w0 + bw * w1
            val invLen = 1f / sqrt(rx * rx + ry * ry + rz * rz + rw * rw).coerceAtLeast(1e-6f)
            out[0] = rx * invLen; out[1] = ry * invLen; out[2] = rz * invLen; out[3] = rw * invLen
        }
    }

    private class GlbNode(
        val name: String,
        val restT: FloatArray,
        val restR: FloatArray,
        val restS: FloatArray,
        val children: IntArray
    )

    private class SkinnedSubMesh(
        val vboId: Int,
        val iboId: Int,
        val indexCount: Int,
        val baseTexId: Int,
        val normalTexId: Int,
        val alphaMode: Int,
        val alphaCutoff: Float,
        val doubleSided: Boolean,
        val baseR: Float, val baseG: Float, val baseB: Float, val baseA: Float
    )

    private class GlbAnimChannel(
        val nodeIndex: Int,
        val isRotation: Boolean,
        val times: FloatArray,
        val values: FloatArray
    )

    private class GlbAnimClip(
        val duration: Float,
        val channels: List<GlbAnimChannel>,
        val stripRootXZ: Boolean = false
    )

    /**
     * Hardware GPU Linear-Blend Skinning Shader (52 joints packed into 156 vec4 rows)
     * with PBR-style hemisphere + sun directional + normal-map micro-detail + alpha MASK/BLEND support.
     */
    private class SkinnedMeshShader {
        val programId: Int
        val aPosLoc: Int
        val aNormLoc: Int
        val aUvLoc: Int
        val aJointsLoc: Int
        val aWeightsLoc: Int

        val uVpMatrixLoc: Int
        val uModelMatrixLoc: Int
        val uJointRowsLoc: Int
        val uBaseTexLoc: Int
        val uNormalTexLoc: Int
        val uHasNormalLoc: Int
        val uBaseFactorLoc: Int
        val uAlphaCutoffLoc: Int
        val uShadowModeLoc: Int
        val uLightDirLoc: Int
        val uSunColorLoc: Int
        val uAmbientScaleLoc: Int
        val uCameraPosLoc: Int
        val uFogColorLoc: Int
        val uFogStartLoc: Int
        val uFogEndLoc: Int
        val isValid: Boolean

        init {
            val vSrc = """
                attribute vec3 aPos;
                attribute vec3 aNorm;
                attribute vec2 aUv;
                attribute vec4 aJoints;
                attribute vec4 aWeights;

                uniform mat4 uVpMatrix;
                uniform mat4 uModelMatrix;
                uniform vec4 uJointRows[${MAX_JOINTS * 3}];

                varying vec3 vWorldPos;
                varying vec3 vWorldNormal;
                varying vec2 vUv;

                void main() {
                    int j0 = int(aJoints.x + 0.5) * 3;
                    int j1 = int(aJoints.y + 0.5) * 3;
                    int j2 = int(aJoints.z + 0.5) * 3;
                    int j3 = int(aJoints.w + 0.5) * 3;

                    vec4 r0 = uJointRows[j0] * aWeights.x + uJointRows[j1] * aWeights.y
                            + uJointRows[j2] * aWeights.z + uJointRows[j3] * aWeights.w;
                    vec4 r1 = uJointRows[j0 + 1] * aWeights.x + uJointRows[j1 + 1] * aWeights.y
                            + uJointRows[j2 + 1] * aWeights.z + uJointRows[j3 + 1] * aWeights.w;
                    vec4 r2 = uJointRows[j0 + 2] * aWeights.x + uJointRows[j1 + 2] * aWeights.y
                            + uJointRows[j2 + 2] * aWeights.z + uJointRows[j3 + 2] * aWeights.w;

                    vec4 p4 = vec4(aPos, 1.0);
                    vec3 skinnedPos = vec3(dot(r0, p4), dot(r1, p4), dot(r2, p4));
                    vec3 rawNorm = vec3(dot(r0.xyz, aNorm), dot(r1.xyz, aNorm), dot(r2.xyz, aNorm));
                    vec3 skinnedNorm = normalize(rawNorm + vec3(0.0, 0.0001, 0.0));

                    vec4 worldPos = uModelMatrix * vec4(skinnedPos, 1.0);
                    vWorldPos = worldPos.xyz;
                    vWorldNormal = normalize((uModelMatrix * vec4(skinnedNorm, 0.0)).xyz + vec3(0.0, 0.0001, 0.0));
                    vUv = aUv;
                    gl_Position = uVpMatrix * worldPos;
                }
            """.trimIndent()

            val fSrc = """
                precision mediump float;
                uniform sampler2D uBaseTex;
                uniform sampler2D uNormalTex;
                uniform float uHasNormal;
                uniform vec4 uBaseFactor;
                uniform float uAlphaCutoff;
                uniform float uShadowMode;
                uniform vec3 uLightDir;
                uniform vec3 uSunColor;
                uniform float uAmbientScale;
                uniform vec3 uCameraPos;
                uniform vec3 uFogColor;
                uniform float uFogStart;
                uniform float uFogEnd;

                varying vec3 vWorldPos;
                varying vec3 vWorldNormal;
                varying vec2 vUv;

                void main() {
                    vec4 texColor = texture2D(uBaseTex, vUv);
                    float outAlpha = texColor.a * uBaseFactor.a;
                    if (uAlphaCutoff > 0.01 && outAlpha < uAlphaCutoff) discard;
                    if (uShadowMode > 0.5) {
                        gl_FragColor = vec4(uBaseFactor.rgb, uBaseFactor.a);
                        return;
                    }

                    vec3 n = normalize(vWorldNormal);
                    if (!gl_FrontFacing) n = -n;
                    if (uHasNormal > 0.5) {
                        vec3 nm = texture2D(uNormalTex, vUv).rgb * 2.0 - 1.0;
                        n = normalize(n + vec3(nm.x * 0.24, nm.y * 0.24, 0.0));
                    }

                    vec3 l = normalize(uLightDir);
                    vec3 v = normalize(uCameraPos - vWorldPos);
                    vec3 h = normalize(l + v);

                    float ndotl = max(dot(n, l), 0.0);
                    float hemi = n.y * 0.5 + 0.5;
                    vec3 ambient = mix(vec3(0.30, 0.31, 0.35), vec3(0.62, 0.68, 0.78), hemi) * uAmbientScale;
                    vec3 sunLight = uSunColor * (ndotl * 0.72);
                    float rim = pow(1.0 - max(dot(n, v), 0.0), 3.0) * 0.16;
                    float spec = pow(max(dot(n, h), 0.0), 28.0) * 0.18 * (0.4 + 0.6 * uAmbientScale);

                    vec3 albedo = texColor.rgb * uBaseFactor.rgb;
                    vec3 lit = albedo * (ambient + sunLight) + uSunColor * spec + vec3(rim * 0.9, rim * 0.95, rim);

                    float dist = length(uCameraPos - vWorldPos);
                    float fogFactor = clamp((uFogEnd - dist) / max(uFogEnd - uFogStart, 1.0), 0.0, 1.0);
                    vec3 finalRgb = mix(uFogColor, lit, fogFactor);
                    gl_FragColor = vec4(finalRgb, outAlpha);
                }
            """.trimIndent()

            val vs = compile(GLES20.GL_VERTEX_SHADER, vSrc)
            val fs = compile(GLES20.GL_FRAGMENT_SHADER, fSrc)
            programId = GLES20.glCreateProgram()
            GLES20.glAttachShader(programId, vs)
            GLES20.glAttachShader(programId, fs)
            GLES20.glLinkProgram(programId)

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)
            isValid = linkStatus[0] == GLES20.GL_TRUE
            if (!isValid) {
                Log.e(TAG, "SkinnedMeshShader link error: ${GLES20.glGetProgramInfoLog(programId)}")
            }

            aPosLoc = GLES20.glGetAttribLocation(programId, "aPos")
            aNormLoc = GLES20.glGetAttribLocation(programId, "aNorm")
            aUvLoc = GLES20.glGetAttribLocation(programId, "aUv")
            aJointsLoc = GLES20.glGetAttribLocation(programId, "aJoints")
            aWeightsLoc = GLES20.glGetAttribLocation(programId, "aWeights")

            uVpMatrixLoc = GLES20.glGetUniformLocation(programId, "uVpMatrix")
            uModelMatrixLoc = GLES20.glGetUniformLocation(programId, "uModelMatrix")
            uJointRowsLoc = GLES20.glGetUniformLocation(programId, "uJointRows")
            uBaseTexLoc = GLES20.glGetUniformLocation(programId, "uBaseTex")
            uNormalTexLoc = GLES20.glGetUniformLocation(programId, "uNormalTex")
            uHasNormalLoc = GLES20.glGetUniformLocation(programId, "uHasNormal")
            uBaseFactorLoc = GLES20.glGetUniformLocation(programId, "uBaseFactor")
            uAlphaCutoffLoc = GLES20.glGetUniformLocation(programId, "uAlphaCutoff")
            uShadowModeLoc = GLES20.glGetUniformLocation(programId, "uShadowMode")
            uLightDirLoc = GLES20.glGetUniformLocation(programId, "uLightDir")
            uSunColorLoc = GLES20.glGetUniformLocation(programId, "uSunColor")
            uAmbientScaleLoc = GLES20.glGetUniformLocation(programId, "uAmbientScale")
            uCameraPosLoc = GLES20.glGetUniformLocation(programId, "uCameraPos")
            uFogColorLoc = GLES20.glGetUniformLocation(programId, "uFogColor")
            uFogStartLoc = GLES20.glGetUniformLocation(programId, "uFogStart")
            uFogEndLoc = GLES20.glGetUniformLocation(programId, "uFogEnd")
        }

        private fun compile(type: Int, src: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, src)
            GLES20.glCompileShader(id)
            val status = IntArray(1)
            GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Shader compile error ($type): ${GLES20.glGetShaderInfoLog(id)}")
            }
            return id
        }
    }
}
