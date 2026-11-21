package com.example.game.engine

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs

enum class LobbyCameraPreset(val label: String, val zoomNormalized: Float, val lookAtY: Float) {
    FULL_BODY("FULL BODY", 0.08f, 0.94f),
    UPPER_BODY("UPPER", 0.56f, 1.36f),
    FACE_ZOOM("FACE ZOOM", 0.96f, 1.66f)
}

enum class LobbyPoseMode(val label: String) {
    IDLE("IDLE"),
    WALK("WALK"),
    SPRINT("RUN")
}

class LobbyCharacterRenderer(private val context: Context) : GLSurfaceView.Renderer {

    @Volatile var targetYawDeg: Float = 12f
    @Volatile var targetZoom: Float = LobbyCameraPreset.FULL_BODY.zoomNormalized
    @Volatile var poseMode: LobbyPoseMode = LobbyPoseMode.IDLE
    @Volatile var autoSlowSpin: Boolean = false
    @Volatile var horizontalShift: Float = 0f // Optional framing offset (-0.35f..0.35f)

    private var currentYawDeg = 12f
    private var currentZoom = LobbyCameraPreset.FULL_BODY.zoomNormalized
    private var worldTime = 0f
    private var lastFrameNs = 0L

    private var shader: CityShaderProgram? = null
    private var podiumMesh: GlMesh? = null
    private var ringMesh: GlMesh? = null
    private var floorMesh: GlMesh? = null
    private var texWhite: Int = 0
    private var glbPlayer: GlbSkinnedPlayer? = null

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Rich matte tactical charcoal studio backdrop (Free Fire / BGMI character bay)
        GLES20.glClearColor(0.085f, 0.105f, 0.135f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        shader = CityShaderProgram()
        texWhite = GlTextureHelper.createSolidWhiteTexture()
        podiumMesh = GlMeshFactory.createCylinder(segments = 36, topScale = 1.0f, bottomScale = 1.08f)
        ringMesh = GlMeshFactory.createGroundRing(segments = 60, innerRatio = 0.945f)
        floorMesh = GlMeshFactory.createCylinder(segments = 36, topScale = 1.0f, bottomScale = 1.0f)

        glbPlayer = GlbSkinnedPlayer.loadFromAssets(context, "player.glb", texWhite)
        lastFrameNs = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        // Narrower studio portrait FOV (38 deg) eliminates face distortion on Face Zoom!
        Matrix.perspectiveM(projMatrix, 0, 38f, aspect, 0.15f, 40f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNs = now
        worldTime += dt

        if (autoSlowSpin) {
            targetYawDeg += dt * 14f
        }

        // Smooth studio camera interpolation
        val smooth = (dt * 9.5f).coerceAtMost(1f)
        currentYawDeg += (targetYawDeg - currentYawDeg) * smooth
        currentZoom += (targetZoom.coerceIn(0f, 1f) - currentZoom) * smooth

        // Map zoom (0f = Full Body, 0.56f = Upper Body, 1.0f = Face Zoom) to exact Avaturn proportions
        val z = currentZoom
        val camDist = 3.25f - z * 2.58f // 3.25m down to 0.67m crisp face close-up!
        // Non-linear lookAtY curve rises smoothly from waist (0.94m) to eyes/face (1.67m)
        val lookY = 0.90f + (z * 0.77f) + (z * z * 0.02f)
        val camY = lookY + (0.14f * (1f - z))
        val camX = horizontalShift * (1f - z * 0.75f)
        val lookX = camX

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        Matrix.setLookAtM(
            viewMatrix, 0,
            camX, camY, camDist,
            lookX, lookY, 0f,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        // Draw tactical metallic character bay podium under feet
        drawStudioPodium(camX, camY, camDist)

        // Update and draw rigged Avaturn player.glb
        val player = glbPlayer ?: return
        val mode = poseMode
        val isMoving = mode == LobbyPoseMode.WALK || mode == LobbyPoseMode.SPRINT
        val isSprinting = mode == LobbyPoseMode.SPRINT
        val walkCycle = when (mode) {
            LobbyPoseMode.IDLE -> 0f
            LobbyPoseMode.WALK -> worldTime * 6.4f
            LobbyPoseMode.SPRINT -> worldTime * 10.6f
        }

        player.updatePose(
            worldTime = worldTime,
            walkCycle = walkCycle,
            isMoving = isMoving,
            isSprinting = isSprinting,
            isGrounded = true,
            isRidingBike = false
        )

        // Balanced warm key light from front-right + soft studio ambient so skin & face look natural
        player.drawStandalone(
            vpMatrix = vpMatrix,
            yawDeg = currentYawDeg,
            lightDirX = 0.42f,
            lightDirY = 0.72f,
            lightDirZ = 0.55f,
            sunR = 1.0f,
            sunG = 0.96f,
            sunB = 0.90f,
            ambientScale = 0.88f,
            camX = camX,
            camY = camY,
            camZ = camDist
        )
    }

    private fun drawStudioPodium(camX: Float, camY: Float, camZ: Float) {
        val sh = shader ?: return
        val pod = podiumMesh ?: return
        val ring = ringMesh ?: return
        val floor = floorMesh ?: return

        GLES20.glUseProgram(sh.programId)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glUniform3f(sh.uLightDirLoc, 0.42f, 0.82f, 0.48f)
        GLES20.glUniform3f(sh.uSunColorLoc, 1.0f, 0.95f, 0.86f)
        GLES20.glUniform1f(sh.uAmbientScaleLoc, 0.72f)
        GLES20.glUniform3f(sh.uCameraPosLoc, camX, camY, camZ)
        GLES20.glUniform3f(sh.uFogColorLoc, 0.085f, 0.105f, 0.135f)
        GLES20.glUniform1f(sh.uFogStartLoc, 4.5f)
        GLES20.glUniform1f(sh.uFogEndLoc, 14.0f)
        GLES20.glUniform1f(sh.uFogMixLoc, 1.0f)
        GLES20.glUniform2f(sh.uTexScaleLoc, 1f, 1f)
        GLES20.glUniform2f(sh.uTexOffsetLoc, 0f, 0f)
        GLES20.glUniform1f(sh.uNormalStrengthLoc, 0f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texWhite)
        GLES20.glUniform1i(sh.uTextureLoc, 0)

        // Wide dark hangar studio floor disc
        drawPodiumMesh(
            sh, floor,
            0f, -0.22f, 0f, 12.0f, 0.10f, 12.0f,
            r = 0.11f, g = 0.13f, b = 0.17f, a = 1f,
            emissive = 0.0f, spec = 0.14f, refl = 0.08f
        )

        // Outer beveled steel base
        drawPodiumMesh(
            sh, pod,
            0f, -0.14f, 0f, 1.28f, 0.10f, 1.28f,
            r = 0.16f, g = 0.19f, b = 0.24f, a = 1f,
            emissive = 0.0f, spec = 0.35f, refl = 0.14f
        )

        // Main dark matte top turntable deck (top sits right at Y = 0.0f under character's sneakers)
        drawPodiumMesh(
            sh, pod,
            0f, -0.055f, 0f, 0.96f, 0.09f, 0.96f,
            r = 0.21f, g = 0.24f, b = 0.30f, a = 1f,
            emissive = 0.0f, spec = 0.42f, refl = 0.18f
        )

        // Subtle warm tactical gold trim ring around the platform edge
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        drawPodiumMesh(
            sh, ring,
            0f, 0.002f, 0f, 0.93f, 1f, 0.93f,
            r = 1.0f, g = 0.72f, b = 0.05f, a = 0.85f,
            emissive = 0.35f, spec = 0.2f, refl = 0.0f
        )
        drawPodiumMesh(
            sh, ring,
            0f, -0.085f, 0f, 1.24f, 1f, 1.24f,
            r = 0.75f, g = 0.80f, b = 0.88f, a = 0.35f,
            emissive = 0.12f, spec = 0.1f, refl = 0.0f
        )
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        GLES20.glDisableVertexAttribArray(sh.aPositionLoc)
        GLES20.glDisableVertexAttribArray(sh.aNormalLoc)
        GLES20.glDisableVertexAttribArray(sh.aTexCoordLoc)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun drawPodiumMesh(
        sh: CityShaderProgram,
        mesh: GlMesh,
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float, a: Float,
        emissive: Float, spec: Float, refl: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)

        mesh.bind(sh)
        GLES20.glUniformMatrix4fv(sh.uMvpMatrixLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(sh.uModelMatrixLoc, 1, false, modelMatrix, 0)
        GLES20.glUniform4f(sh.uColorTintLoc, r, g, b, a)
        GLES20.glUniform1f(sh.uEmissiveLoc, emissive)
        GLES20.glUniform1f(sh.uSpecStrengthLoc, spec)
        GLES20.glUniform1f(sh.uSpecPowerLoc, 28f)
        GLES20.glUniform1f(sh.uReflectStrengthLoc, refl)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount)
    }
}

class LobbyCharacterGLSurfaceView(context: Context) : GLSurfaceView(context) {
    val characterRenderer: LobbyCharacterRenderer

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(StencilEGLConfigChooser())
        characterRenderer = LobbyCharacterRenderer(context)
        setRenderer(characterRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}

/**
 * Interactive 3D Character Viewport for the Lobby & Character Showcase.
 * Supports 360-degree touch drag rotation, 2-finger pinch zoom (all the way to Face Zoom),
 * preset camera framing, and live pose switching.
 */
@Composable
fun InteractiveLobbyCharacter3D(
    zoomNormalized: Float,
    poseMode: LobbyPoseMode,
    yawDeg: Float,
    horizontalShift: Float = 0f,
    onYawChange: (Float) -> Unit,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "lobby_3d_character"
) {
    val context = LocalContext.current
    val glView = remember { LobbyCharacterGLSurfaceView(context) }

    LaunchedEffect(zoomNormalized, poseMode, yawDeg, horizontalShift) {
        glView.characterRenderer.targetZoom = zoomNormalized.coerceIn(0f, 1f)
        glView.characterRenderer.poseMode = poseMode
        glView.characterRenderer.targetYawDeg = yawDeg
        glView.characterRenderer.horizontalShift = horizontalShift
    }

    DisposableEffect(glView) {
        glView.onResume()
        onDispose {
            glView.onPause()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(testTag)
    ) {
        AndroidView(
            factory = { glView },
            modifier = Modifier.fillMaxSize()
        )

        // Transparent gesture overlay for 360-degree drag rotate + pinch-to-zoom!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(yawDeg, zoomNormalized) {
                    var currentYaw = yawDeg
                    var currentZoom = zoomNormalized
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (abs(pan.x) > 0.25f) {
                            currentYaw += pan.x * 0.45f
                            onYawChange(currentYaw)
                        }
                        if (abs(zoom - 1f) > 0.002f) {
                            currentZoom = (currentZoom + (zoom - 1f) * 0.72f).coerceIn(0f, 1f)
                            onZoomChange(currentZoom)
                        }
                    }
                }
        )
    }
}
