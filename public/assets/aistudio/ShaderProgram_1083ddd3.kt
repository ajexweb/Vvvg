package com.example.game.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Path
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.exp
import kotlin.math.max
import kotlin.random.Random
import android.opengl.GLES20
import android.opengl.GLUtils
import androidx.annotation.DrawableRes
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CityShaderProgram {
    val programId: Int

    val uMvpMatrixLoc: Int
    val uModelMatrixLoc: Int
    val uTexScaleLoc: Int
    val uColorTintLoc: Int
    val uEmissiveLoc: Int
    val uSpecStrengthLoc: Int
    val uSpecPowerLoc: Int
    val uReflectStrengthLoc: Int
    val uLightDirLoc: Int
    val uCameraPosLoc: Int
    val uFogColorLoc: Int
    val uFogStartLoc: Int
    val uFogEndLoc: Int
    val uTextureLoc: Int

    val aPositionLoc: Int
    val aNormalLoc: Int
    val aTexCoordLoc: Int

    init {
        val vertexCode = """
            uniform mat4 uMvpMatrix;
            uniform mat4 uModelMatrix;
            uniform vec2 uTexScale;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec2 aTexCoord;
            varying vec3 vWorldNormal;
            varying vec3 vWorldPos;
            varying vec2 vTexCoord;
            void main() {
                vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
                vWorldPos = worldPos.xyz;
                vWorldNormal = normalize(mat3(uModelMatrix) * aNormal);
                vTexCoord = aTexCoord * uTexScale;
                gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
            }
        """.trimIndent()

        val fragmentCode = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec4 uColorTint;
            uniform float uEmissive;
            uniform float uSpecStrength;
            uniform float uSpecPower;
            uniform float uReflectStrength;
            uniform vec3 uLightDir;
            uniform vec3 uCameraPos;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            varying vec3 vWorldNormal;
            varying vec3 vWorldPos;
            varying vec2 vTexCoord;
            void main() {
                vec4 texel = texture2D(uTexture, vTexCoord);
                float outAlpha = texel.a * uColorTint.a;
                if (outAlpha < 0.08) discard;

                vec3 baseColor = texel.rgb * uColorTint.rgb;
                vec3 n = normalize(vWorldNormal);
                vec3 l = normalize(uLightDir);
                vec3 v = normalize(uCameraPos - vWorldPos);

                float sunDiff = max(dot(n, l), 0.0);
                float backFill = max(dot(n, normalize(vec3(-l.x, 0.35, -l.z))), 0.0) * 0.24;
                float hemi = n.y * 0.5 + 0.5;
                vec3 groundBounce = vec3(0.24, 0.23, 0.26);
                vec3 skyAmbient = vec3(0.56, 0.66, 0.78);
                vec3 ambient = mix(groundBounce, skyAmbient, hemi);
                vec3 sunColor = vec3(1.0, 0.90, 0.74) * (sunDiff * 0.92 + backFill);

                vec3 halfVec = normalize(l + v);
                float spec = pow(max(dot(n, halfVec), 0.0), uSpecPower) * uSpecStrength;

                float fresnel = pow(1.0 - max(dot(n, v), 0.0), 3.0) * uReflectStrength;
                vec3 skyReflColor = mix(vec3(0.95, 0.78, 0.58), vec3(0.50, 0.76, 0.98), clamp(n.y * 0.5 + 0.5, 0.0, 1.0));

                vec3 lit = baseColor * (ambient + sunColor)
                    + vec3(1.0, 0.94, 0.84) * spec
                    + skyReflColor * fresnel
                    + baseColor * uEmissive;

                float dist = length(vWorldPos - uCameraPos);
                float fogFactor = clamp((dist - uFogStart) / max(1.0, (uFogEnd - uFogStart)), 0.0, 1.0);
                vec3 finalRgb = mix(lit, uFogColor, fogFactor * fogFactor);
                gl_FragColor = vec4(finalRgb, outAlpha);
            }
        """.trimIndent()

        val vShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexCode)
        val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode)

        programId = GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vShader)
            GLES20.glAttachShader(prog, fShader)
            GLES20.glLinkProgram(prog)
        }
        GLES20.glDeleteShader(vShader)
        GLES20.glDeleteShader(fShader)

        uMvpMatrixLoc = GLES20.glGetUniformLocation(programId, "uMvpMatrix")
        uModelMatrixLoc = GLES20.glGetUniformLocation(programId, "uModelMatrix")
        uTexScaleLoc = GLES20.glGetUniformLocation(programId, "uTexScale")
        uColorTintLoc = GLES20.glGetUniformLocation(programId, "uColorTint")
        uEmissiveLoc = GLES20.glGetUniformLocation(programId, "uEmissive")
        uSpecStrengthLoc = GLES20.glGetUniformLocation(programId, "uSpecStrength")
        uSpecPowerLoc = GLES20.glGetUniformLocation(programId, "uSpecPower")
        uReflectStrengthLoc = GLES20.glGetUniformLocation(programId, "uReflectStrength")
        uLightDirLoc = GLES20.glGetUniformLocation(programId, "uLightDir")
        uCameraPosLoc = GLES20.glGetUniformLocation(programId, "uCameraPos")
        uFogColorLoc = GLES20.glGetUniformLocation(programId, "uFogColor")
        uFogStartLoc = GLES20.glGetUniformLocation(programId, "uFogStart")
        uFogEndLoc = GLES20.glGetUniformLocation(programId, "uFogEnd")
        uTextureLoc = GLES20.glGetUniformLocation(programId, "uTexture")

        aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition")
        aNormalLoc = GLES20.glGetAttribLocation(programId, "aNormal")
        aTexCoordLoc = GLES20.glGetAttribLocation(programId, "aTexCoord")
    }

    private fun compileShader(type: Int, code: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, code)
            GLES20.glCompileShader(shader)
        }
    }
}

/**
 * Ultra-fast sun-projected silhouette shadow shader (respects alpha cutouts on leaves!).
 */
class ShadowShaderProgram {
    val programId: Int
    val uMvpMatrixLoc: Int
    val uShadowColorLoc: Int
    val uTextureLoc: Int
    val aPositionLoc: Int
    val aTexCoordLoc: Int

    init {
        val vCode = """
            uniform mat4 uMvpMatrix;
            attribute vec3 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                vTexCoord = aTexCoord;
                gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
            }
        """.trimIndent()

        val fCode = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec4 uShadowColor;
            varying vec2 vTexCoord;
            void main() {
                float a = texture2D(uTexture, vTexCoord).a;
                if (a < 0.22) discard;
                gl_FragColor = uShadowColor;
            }
        """.trimIndent()

        val vS = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER).also { GLES20.glShaderSource(it, vCode); GLES20.glCompileShader(it) }
        val fS = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER).also { GLES20.glShaderSource(it, fCode); GLES20.glCompileShader(it) }
        programId = GLES20.glCreateProgram().also { p ->
            GLES20.glAttachShader(p, vS)
            GLES20.glAttachShader(p, fS)
            GLES20.glLinkProgram(p)
        }
        GLES20.glDeleteShader(vS)
        GLES20.glDeleteShader(fS)

        uMvpMatrixLoc = GLES20.glGetUniformLocation(programId, "uMvpMatrix")
        uShadowColorLoc = GLES20.glGetUniformLocation(programId, "uShadowColor")
        uTextureLoc = GLES20.glGetUniformLocation(programId, "uTexture")
        aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(programId, "aTexCoord")
    }
}

class GlMesh(vertices: FloatArray) {
    val vboId: Int
    val vertexCount: Int = vertices.size / 8

    init {
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        vboId = ids[0]

        val fb: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.size * 4, fb, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun bind(shader: CityShaderProgram) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        GLES20.glEnableVertexAttribArray(shader.aPositionLoc)
        GLES20.glVertexAttribPointer(shader.aPositionLoc, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 0)
        GLES20.glEnableVertexAttribArray(shader.aNormalLoc)
        GLES20.glVertexAttribPointer(shader.aNormalLoc, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 3 * 4)
        GLES20.glEnableVertexAttribArray(shader.aTexCoordLoc)
        GLES20.glVertexAttribPointer(shader.aTexCoordLoc, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, 6 * 4)
    }

    fun bindShadow(shader: ShadowShaderProgram) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        GLES20.glEnableVertexAttribArray(shader.aPositionLoc)
        GLES20.glVertexAttribPointer(shader.aPositionLoc, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, 0)
        GLES20.glEnableVertexAttribArray(shader.aTexCoordLoc)
        GLES20.glVertexAttribPointer(shader.aTexCoordLoc, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, 6 * 4)
    }

    companion object {
        const val STRIDE_BYTES = 8 * 4
    }
}

object GlTextureHelper {
    fun loadDrawableTexture(context: Context, @DrawableRes resId: Int, repeat: Boolean = true): Int {
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        val texId = texIds[0]

        val options = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = BitmapFactory.decodeResource(context.resources, resId, options)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        val wrap = if (repeat) GLES20.GL_REPEAT else GLES20.GL_CLAMP_TO_EDGE
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        if (bitmap != null) {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
            bitmap.recycle()
        }
        return texId
    }

    /**
     * Combines our real generated foliage photo with an organic multi-leaf twig silhouette alpha mask,
     * so tree foliage cards render real crisp leaves with transparent sunlight gaps (no spheres, no square blocks!).
     */
    fun loadLeafBranchClusterTexture(context: Context, @DrawableRes leafPhotoResId: Int): Int {
        val size = 512
        val options = BitmapFactory.Options().apply { inScaled = false }
        val srcRaw = BitmapFactory.decodeResource(context.resources, leafPhotoResId, options)
        val srcScaled = if (srcRaw != null) {
            Bitmap.createScaledBitmap(srcRaw, size, size, true).also {
                if (it != srcRaw) srcRaw.recycle()
            }
        } else {
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.rgb(42, 118, 46))
            }
        }

        // Build crisp organic multi-leaf branch silhouette mask
        val maskBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(maskBmp)
        canvas.drawColor(android.graphics.Color.TRANSPARENT)

        val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }

        // Draw ~38 radiating pointed botanical leaf blades around twig sprays
        val leafSpecs = listOf(
            // Central crown leaves
            floatArrayOf(256f, 115f, 44f, 92f, 0f),
            floatArrayOf(212f, 145f, 40f, 84f, -35f),
            floatArrayOf(300f, 145f, 40f, 84f, 35f),
            floatArrayOf(170f, 192f, 38f, 80f, -55f),
            floatArrayOf(342f, 192f, 38f, 80f, 55f),
            floatArrayOf(256f, 200f, 46f, 88f, 12f),
            floatArrayOf(214f, 232f, 42f, 82f, -24f),
            floatArrayOf(298f, 232f, 42f, 82f, 24f),
            // Lower side sprays
            floatArrayOf(128f, 252f, 36f, 76f, -72f),
            floatArrayOf(384f, 252f, 36f, 76f, 72f),
            floatArrayOf(158f, 298f, 38f, 78f, -48f),
            floatArrayOf(354f, 298f, 38f, 78f, 48f),
            floatArrayOf(212f, 310f, 42f, 78f, -18f),
            floatArrayOf(300f, 310f, 42f, 78f, 18f),
            floatArrayOf(256f, 285f, 44f, 80f, -8f),
            floatArrayOf(115f, 326f, 34f, 68f, -82f),
            floatArrayOf(397f, 326f, 34f, 68f, 82f),
            floatArrayOf(182f, 362f, 36f, 72f, -38f),
            floatArrayOf(330f, 362f, 36f, 72f, 38f),
            floatArrayOf(256f, 365f, 38f, 74f, 5f),
            // Delicate outer tip leaves
            floatArrayOf(190f, 98f, 28f, 58f, -22f),
            floatArrayOf(322f, 98f, 28f, 58f, 22f),
            floatArrayOf(110f, 198f, 26f, 56f, -68f),
            floatArrayOf(402f, 198f, 26f, 56f, 68f),
            floatArrayOf(96f, 288f, 25f, 54f, -88f),
            floatArrayOf(416f, 288f, 25f, 54f, 88f),
            floatArrayOf(148f, 405f, 28f, 56f, -52f),
            floatArrayOf(364f, 405f, 28f, 56f, 52f),
            floatArrayOf(225f, 418f, 28f, 56f, -15f),
            floatArrayOf(287f, 418f, 28f, 56f, 15f)
        )

        val p = Path()
        for (spec in leafSpecs) {
            val cx = spec[0]; val cy = spec[1]
            val lw = spec[2]; val lh = spec[3]; val rot = spec[4]
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(rot)
            p.reset()
            p.moveTo(0f, -lh * 0.5f)
            p.quadTo(lw * 0.65f, -lh * 0.1f, 0f, lh * 0.5f)
            p.quadTo(-lw * 0.65f, -lh * 0.1f, 0f, -lh * 0.5f)
            p.close()
            canvas.drawPath(p, leafPaint)
            canvas.restore()
        }

        // Composite real photo pixels onto the botanical leaf silhouette mask
        val outBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val outCanvas = Canvas(outBmp)
        outCanvas.drawBitmap(maskBmp, 0f, 0f, null)
        val srcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        outCanvas.drawBitmap(srcScaled, 0f, 0f, srcPaint)

        srcScaled.recycle()
        maskBmp.recycle()

        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        val texId = texIds[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, outBmp, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        outBmp.recycle()
        return texId
    }

    fun createSolidWhiteTexture(): Int {
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        val texId = texIds[0]

        val bmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
        return texId
    }
    /**
     * Blends the real cropped front-face photo (R.drawable.img_player_face) onto a warm skin base
     * with a soft elliptical feather mask so the 3D head has zero hard edges or back-head seam.
     */
    fun loadPlayerFaceTexture(context: Context, @DrawableRes faceResId: Int): Int {
        val size = 512
        val outBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBmp)

        // Natural Indian skin base color matched to photo
        canvas.drawColor(Color.rgb(196, 150, 118))

        // Soft hairline gradient along the top edge
        val topHairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, size * 0.22f,
                Color.rgb(24, 22, 24), Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size * 0.22f, topHairPaint)

        val options = BitmapFactory.Options().apply { inScaled = false }
        val srcRaw = BitmapFactory.decodeResource(context.resources, faceResId, options)
        if (srcRaw != null) {
            val layerId = canvas.saveLayer(0f, 0f, size.toFloat(), size.toFloat(), null)
            val dst = Rect(24, 20, size - 24, size - 20)
            canvas.drawBitmap(srcRaw, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))

            // Elliptical feather mask (narrower horizontally) so background edges never show on temples/sides
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                shader = RadialGradient(
                    size * 0.5f, size * 0.5f, size * 0.46f,
                    intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                    floatArrayOf(0f, 0.72f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.save()
            canvas.scale(0.82f, 1.0f, size * 0.5f, size * 0.5f)
            canvas.drawRect(-size * 0.3f, 0f, size * 1.3f, size.toFloat(), maskPaint)
            canvas.restore()
            canvas.restoreToCount(layerId)
            srcRaw.recycle()
        }

        return uploadBitmapToGl(outBmp, repeat = false)
    }

    /**
     * Dark olive-green cotton shirt texture: woven grain, subtle fabric folds,
     * front center button placket (u = 0.50), maroon-center buttons, and left chest pocket.
     */
    fun createGreenShirtTexture(): Int {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(62, 76, 60))

        val rng = Random(404)
        val weavePaint = Paint()
        for (i in 0 until 6500) {
            val px = rng.nextInt(size).toFloat()
            val py = rng.nextInt(size).toFloat()
            val shade = if (rng.nextBoolean()) 12 else -12
            weavePaint.color = Color.argb(
                36,
                (62 + shade).coerceIn(0, 255),
                (76 + shade).coerceIn(0, 255),
                (60 + shade).coerceIn(0, 255)
            )
            canvas.drawRect(px, py, px + 3f, py + 2f, weavePaint)
        }

        // Subtle vertical cloth folds
        val foldPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (fx in listOf(78f, 162f, 348f, 430f)) {
            foldPaint.color = Color.argb(28, 22, 28, 22)
            canvas.drawRect(fx, 0f, fx + 12f, size.toFloat(), foldPaint)
            foldPaint.color = Color.argb(20, 120, 140, 116)
            canvas.drawRect(fx + 12f, 0f, fx + 18f, size.toFloat(), foldPaint)
        }

        // Center front button placket at u = 0.50 (x = 256)
        val placketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(54, 67, 52) }
        canvas.drawRect(243f, 0f, 269f, size.toFloat(), placketPaint)
        val stitchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(42, 52, 40)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(244f, 0f, 244f, size.toFloat(), stitchPaint)
        canvas.drawLine(268f, 0f, 268f, size.toFloat(), stitchPaint)

        // Maroon-center buttons with light rim (matched to reference photo)
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(205, 198, 192) }
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(112, 34, 42) }
        for (by in listOf(74f, 152f, 232f, 314f, 396f, 466f)) {
            canvas.drawCircle(256f, by, 6.2f, rimPaint)
            canvas.drawCircle(256f, by, 4.0f, corePaint)
        }

        // Left chest pocket (character's left chest -> u ~ 0.37 -> x ~ 190)
        val pocketRect = RectF(164f, 128f, 222f, 204f)
        val pocketFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(56, 70, 54) }
        canvas.drawRoundRect(pocketRect, 6f, 6f, pocketFill)
        canvas.drawRoundRect(pocketRect, 6f, 6f, stitchPaint)
        canvas.drawLine(164f, 142f, 222f, 142f, stitchPaint)

        return uploadBitmapToGl(bmp, repeat = true)
    }

    /**
     * Washed indigo blue denim jeans texture: twill weave grain, thigh whiskering fade, and side seam stitching.
     */
    fun createDenimJeansTexture(): Int {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(42, 68, 102))

        val rng = Random(909)
        val grainPaint = Paint()
        for (i in 0 until 9000) {
            val px = rng.nextInt(size).toFloat()
            val py = rng.nextInt(size).toFloat()
            val light = rng.nextBoolean()
            grainPaint.color = if (light) Color.argb(42, 118, 148, 184) else Color.argb(46, 20, 34, 56)
            canvas.drawRect(px, py, px + 4f, py + 2f, grainPaint)
        }

        // Washed denim whiskering fade bands
        val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (fy in listOf(84f, 136f, 188f, 248f, 318f)) {
            fadePaint.color = Color.argb(34, 152, 178, 208)
            canvas.drawRect(0f, fy, size.toFloat(), fy + 18f, fadePaint)
        }

        // Ochre side seam stitching
        val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(168, 126, 72)
            strokeWidth = 2.5f
        }
        canvas.drawLine(16f, 0f, 16f, size.toFloat(), seamPaint)
        canvas.drawLine(256f, 0f, 256f, size.toFloat(), seamPaint)

        return uploadBitmapToGl(bmp, repeat = true)
    }

    private fun uploadBitmapToGl(bmp: Bitmap, repeat: Boolean): Int {
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        val texId = texIds[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        val wrap = if (repeat) GLES20.GL_REPEAT else GLES20.GL_CLAMP_TO_EDGE
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        bmp.recycle()
        return texId
    }

}

object GlMeshFactory {

    fun createUnitCube(): GlMesh {
        val v = floatArrayOf(
            -0.5f, -0.5f, 0.5f, 0f, 0f, 1f, 0f, 1f,  0.5f, -0.5f, 0.5f, 0f, 0f, 1f, 1f, 1f,  0.5f, 0.5f, 0.5f, 0f, 0f, 1f, 1f, 0f,
            -0.5f, -0.5f, 0.5f, 0f, 0f, 1f, 0f, 1f,  0.5f,  0.5f, 0.5f, 0f, 0f, 1f, 1f, 0f, -0.5f, 0.5f, 0.5f, 0f, 0f, 1f, 0f, 0f,
             0.5f, -0.5f, -0.5f, 0f, 0f, -1f, 0f, 1f, -0.5f, -0.5f, -0.5f, 0f, 0f, -1f, 1f, 1f, -0.5f, 0.5f, -0.5f, 0f, 0f, -1f, 1f, 0f,
             0.5f, -0.5f, -0.5f, 0f, 0f, -1f, 0f, 1f, -0.5f,  0.5f, -0.5f, 0f, 0f, -1f, 1f, 0f,  0.5f, 0.5f, -0.5f, 0f, 0f, -1f, 0f, 0f,
            -0.5f, -0.5f, -0.5f, -1f, 0f, 0f, 0f, 1f, -0.5f, -0.5f, 0.5f, -1f, 0f, 0f, 1f, 1f, -0.5f, 0.5f, 0.5f, -1f, 0f, 0f, 1f, 0f,
            -0.5f, -0.5f, -0.5f, -1f, 0f, 0f, 0f, 1f, -0.5f,  0.5f, 0.5f, -1f, 0f, 0f, 1f, 0f, -0.5f, 0.5f, -0.5f, -1f, 0f, 0f, 0f, 0f,
             0.5f, -0.5f,  0.5f,  1f, 0f, 0f, 0f, 1f,  0.5f, -0.5f, -0.5f, 1f, 0f, 0f, 1f, 1f, 0.5f, 0.5f, -0.5f, 1f, 0f, 0f, 1f, 0f,
             0.5f, -0.5f,  0.5f,  1f, 0f, 0f, 0f, 1f,  0.5f,  0.5f, -0.5f, 1f, 0f, 0f, 1f, 0f, 0.5f, 0.5f,  0.5f, 1f, 0f, 0f, 0f, 0f,
            -0.5f,  0.5f,  0.5f, 0f, 1f, 0f, 0f, 1f,  0.5f,  0.5f,  0.5f, 0f, 1f, 0f, 1f, 1f, 0.5f, 0.5f, -0.5f, 0f, 1f, 0f, 1f, 0f,
            -0.5f,  0.5f,  0.5f, 0f, 1f, 0f, 0f, 1f,  0.5f,  0.5f, -0.5f, 0f, 1f, 0f, 1f, 0f, -0.5f, 0.5f, -0.5f, 0f, 1f, 0f, 0f, 0f,
            -0.5f, -0.5f, -0.5f, 0f, -1f, 0f, 0f, 1f,  0.5f, -0.5f, -0.5f, 0f, -1f, 0f, 1f, 1f, 0.5f, -0.5f, 0.5f, 0f, -1f, 0f, 1f, 0f,
            -0.5f, -0.5f, -0.5f, 0f, -1f, 0f, 0f, 1f,  0.5f, -0.5f,  0.5f, 0f, -1f, 0f, 1f, 0f, -0.5f, -0.5f, 0.5f, 0f, -1f, 0f, 0f, 0f
        )
        return GlMesh(v)
    }

    fun createGroundQuad(): GlMesh {
        val v = floatArrayOf(
            -0.5f, 0f,  0.5f, 0f, 1f, 0f, 0f, 1f,
             0.5f, 0f,  0.5f, 0f, 1f, 0f, 1f, 1f,
             0.5f, 0f, -0.5f, 0f, 1f, 0f, 1f, 0f,
            -0.5f, 0f,  0.5f, 0f, 1f, 0f, 0f, 1f,
             0.5f, 0f, -0.5f, 0f, 1f, 0f, 1f, 0f,
            -0.5f, 0f, -0.5f, 0f, 1f, 0f, 0f, 0f
        )
        return GlMesh(v)
    }

    /**
     * Crossed 3D foliage cluster (3 intersecting tilted leaf planes) for true botanical canopies!
     * Double-sided (GL_CULL_FACE disabled for foliage) so leaves look full from every angle.
     */
    fun createCrossedLeafCluster(): GlMesh {
        val out = ArrayList<Float>(36 * 8)

        fun addTiltedCard(yawDeg: Float, tiltDeg: Float, liftY: Float) {
            val yaw = yawDeg * PI.toFloat() / 180f
            val tilt = tiltDeg * PI.toFloat() / 180f
            val cy = cos(yaw); val sy = sin(yaw)
            val ct = cos(tilt); val st = sin(tilt)

            fun xform(lx: Float, ly: Float, lz: Float): FloatArray {
                val ty = ly * ct - lz * st
                val tz = ly * st + lz * ct
                val wx = lx * cy + tz * sy
                val wz = -lx * sy + tz * cy
                return floatArrayOf(wx, ty + liftY, wz)
            }

            val p0 = xform(-0.5f, -0.5f, 0f)
            val p1 = xform( 0.5f, -0.5f, 0f)
            val p2 = xform( 0.5f,  0.5f, 0f)
            val p3 = xform(-0.5f,  0.5f, 0f)
            val n = xform(0f, 0.35f, 0.9f)
            val nl = sqrt(n[0]*n[0] + n[1]*n[1] + n[2]*n[2]).coerceAtLeast(0.001f)
            val nx = n[0]/nl; val ny = (n[1]-liftY)/nl; val nz = n[2]/nl

            fun push(p: FloatArray, u: Float, v: Float) {
                out.add(p[0]); out.add(p[1]); out.add(p[2])
                out.add(nx); out.add(ny.coerceAtLeast(0.25f)); out.add(nz)
                out.add(u); out.add(v)
            }

            push(p0, 0f, 1f); push(p1, 1f, 1f); push(p2, 1f, 0f)
            push(p0, 0f, 1f); push(p2, 1f, 0f); push(p3, 0f, 0f)
        }

        addTiltedCard(0f, 18f, 0f)
        addTiltedCard(60f, -16f, 0.04f)
        addTiltedCard(120f, 20f, -0.02f)
        return GlMesh(out.toFloatArray())
    }

    fun createCylinder(segments: Int = 14, topScale: Float = 1.0f, bottomScale: Float = 1.0f): GlMesh {
        val out = ArrayList<Float>(segments * 12 * 8)
        val rTop = 0.5f * topScale
        val rBot = 0.5f * bottomScale

        fun push(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, v: Float) {
            out.add(x); out.add(y); out.add(z)
            out.add(nx); out.add(ny); out.add(nz)
            out.add(u); out.add(v)
        }

        val slopeY = (rBot - rTop)
        for (i in 0 until segments) {
            val a0 = (2.0 * PI * i / segments).toFloat()
            val a1 = (2.0 * PI * (i + 1) / segments).toFloat()
            val u0 = i.toFloat() / segments
            val u1 = (i + 1).toFloat() / segments

            val c0 = cos(a0); val s0 = sin(a0)
            val c1 = cos(a1); val s1 = sin(a1)

            val nLen0 = sqrt(c0 * c0 + slopeY * slopeY + s0 * s0).coerceAtLeast(0.001f)
            val nLen1 = sqrt(c1 * c1 + slopeY * slopeY + s1 * s1).coerceAtLeast(0.001f)

            push(c0 * rBot, -0.5f, s0 * rBot, c0 / nLen0, slopeY / nLen0, s0 / nLen0, u0, 1f)
            push(c1 * rBot, -0.5f, s1 * rBot, c1 / nLen1, slopeY / nLen1, s1 / nLen1, u1, 1f)
            push(c1 * rTop,  0.5f, s1 * rTop, c1 / nLen1, slopeY / nLen1, s1 / nLen1, u1, 0f)

            push(c0 * rBot, -0.5f, s0 * rBot, c0 / nLen0, slopeY / nLen0, s0 / nLen0, u0, 1f)
            push(c1 * rTop,  0.5f, s1 * rTop, c1 / nLen1, slopeY / nLen1, s1 / nLen1, u1, 0f)
            push(c0 * rTop,  0.5f, s0 * rTop, c0 / nLen0, slopeY / nLen0, s0 / nLen0, u0, 0f)

            push(0f, 0.5f, 0f, 0f, 1f, 0f, 0.5f, 0.5f)
            push(c1 * rTop, 0.5f, s1 * rTop, 0f, 1f, 0f, 0.5f + 0.5f * c1, 0.5f + 0.5f * s1)
            push(c0 * rTop, 0.5f, s0 * rTop, 0f, 1f, 0f, 0.5f + 0.5f * c0, 0.5f + 0.5f * s0)

            push(0f, -0.5f, 0f, 0f, -1f, 0f, 0.5f, 0.5f)
            push(c0 * rBot, -0.5f, s0 * rBot, 0f, -1f, 0f, 0.5f + 0.5f * c0, 0.5f + 0.5f * s0)
            push(c1 * rBot, -0.5f, s1 * rBot, 0f, -1f, 0f, 0.5f + 0.5f * c1, 0.5f + 0.5f * s1)
        }
        return GlMesh(out.toFloatArray())
    }

    fun createSphere(stacks: Int = 10, slices: Int = 14): GlMesh {
        val out = ArrayList<Float>(stacks * slices * 6 * 8)
        val r = 0.5f

        fun push(nx: Float, ny: Float, nz: Float, u: Float, v: Float) {
            out.add(nx * r); out.add(ny * r); out.add(nz * r)
            out.add(nx); out.add(ny); out.add(nz)
            out.add(u); out.add(v)
        }

        for (i in 0 until stacks) {
            val phi0 = (-PI / 2.0 + PI * i / stacks).toFloat()
            val phi1 = (-PI / 2.0 + PI * (i + 1) / stacks).toFloat()
            val v0 = 1f - i.toFloat() / stacks
            val v1 = 1f - (i + 1).toFloat() / stacks
            val y0 = sin(phi0); val r0 = cos(phi0)
            val y1 = sin(phi1); val r1 = cos(phi1)

            for (j in 0 until slices) {
                val th0 = (2.0 * PI * j / slices).toFloat()
                val th1 = (2.0 * PI * (j + 1) / slices).toFloat()
                val u0 = j.toFloat() / slices
                val u1 = (j + 1).toFloat() / slices

                val x00 = r0 * cos(th0); val z00 = r0 * sin(th0)
                val x01 = r0 * cos(th1); val z01 = r0 * sin(th1)
                val x10 = r1 * cos(th0); val z10 = r1 * sin(th0)
                val x11 = r1 * cos(th1); val z11 = r1 * sin(th1)

                push(x00, y0, z00, u0, v0); push(x01, y0, z01, u1, v0); push(x11, y1, z11, u1, v1)
                push(x00, y0, z00, u0, v0); push(x11, y1, z11, u1, v1); push(x10, y1, z10, u0, v1)
            }
        }
        return GlMesh(out.toFloatArray())
    }

    /**
     * Realistic multi-curve GTA-style sports-sedan chassis with sculpted hood, sloped fenders,
     * rocker panels, and aerodynamic rear deck.
     */
    fun createSlopedCarBody(): GlMesh {
        val out = ArrayList<Float>(240 * 8)

        fun addQuad(p0: FloatArray, p1: FloatArray, p2: FloatArray, p3: FloatArray) {
            val e1x = p1[0] - p0[0]; val e1y = p1[1] - p0[1]; val e1z = p1[2] - p0[2]
            val e2x = p2[0] - p0[0]; val e2y = p2[1] - p0[1]; val e2z = p2[2] - p0[2]
            var nx = e1y * e2z - e1z * e2y
            var ny = e1z * e2x - e1x * e2z
            var nz = e1x * e2y - e1y * e2x
            val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
            nx /= len; ny /= len; nz /= len

            fun v(p: FloatArray, u: Float, vt: Float) {
                out.add(p[0]); out.add(p[1]); out.add(p[2])
                out.add(nx); out.add(ny); out.add(nz)
                out.add(u); out.add(vt)
            }
            v(p0, 0f, 1f); v(p1, 1f, 1f); v(p2, 1f, 0f)
            v(p0, 0f, 1f); v(p2, 1f, 0f); v(p3, 0f, 0f)
        }

        // [halfWidthBottom, halfWidthShoulder, yBottom, yTop, z]
        val sections = arrayOf(
            floatArrayOf(0.84f, 0.80f, 0.28f, 0.78f, -2.22f), // Rear bumper lip
            floatArrayOf(0.95f, 0.90f, 0.24f, 0.94f, -1.92f), // Trunk corner
            floatArrayOf(0.98f, 0.93f, 0.21f, 0.95f, -1.18f), // Rear haunches
            floatArrayOf(0.96f, 0.92f, 0.21f, 0.91f,  0.00f), // Mid doors
            floatArrayOf(0.97f, 0.92f, 0.21f, 0.90f,  0.88f), // A-pillar cowl
            floatArrayOf(0.95f, 0.89f, 0.22f, 0.78f,  1.58f), // Sculpted hood mid
            floatArrayOf(0.89f, 0.82f, 0.24f, 0.64f,  2.06f), // Front fender curve
            floatArrayOf(0.80f, 0.74f, 0.26f, 0.50f,  2.26f)  // Aggressive front splitter nose
        )

        for (i in 0 until sections.lastIndex) {
            val a = sections[i]
            val b = sections[i + 1]
            val aBL = floatArrayOf(-a[0], a[2], a[4]); val aBR = floatArrayOf(a[0], a[2], a[4])
            val aTL = floatArrayOf(-a[1], a[3], a[4]); val aTR = floatArrayOf(a[1], a[3], a[4])
            val bBL = floatArrayOf(-b[0], b[2], b[4]); val bBR = floatArrayOf(b[0], b[2], b[4])
            val bTL = floatArrayOf(-b[1], b[3], b[4]); val bTR = floatArrayOf(b[1], b[3], b[4])

            addQuad(bTL, bTR, aTR, aTL)
            addQuad(aBL, aBR, bBR, bBL)
            addQuad(aBL, bBL, bTL, aTL)
            addQuad(bBR, aBR, aTR, bTR)
        }

        val f = sections.last()
        addQuad(
            floatArrayOf(-f[0], f[2], f[4]), floatArrayOf(f[0], f[2], f[4]),
            floatArrayOf(f[1], f[3], f[4]), floatArrayOf(-f[1], f[3], f[4])
        )
        val r = sections.first()
        addQuad(
            floatArrayOf(r[0], r[2], r[4]), floatArrayOf(-r[0], r[2], r[4]),
            floatArrayOf(-r[1], r[3], r[4]), floatArrayOf(r[1], r[3], r[4])
        )
        return GlMesh(out.toFloatArray())
    }

    fun createSlopedCarCabin(): GlMesh {
        val out = ArrayList<Float>(60 * 8)

        fun addQuad(p0: FloatArray, p1: FloatArray, p2: FloatArray, p3: FloatArray) {
            val e1x = p1[0] - p0[0]; val e1y = p1[1] - p0[1]; val e1z = p1[2] - p0[2]
            val e2x = p2[0] - p0[0]; val e2y = p2[1] - p0[1]; val e2z = p2[2] - p0[2]
            var nx = e1y * e2z - e1z * e2y
            var ny = e1z * e2x - e1x * e2z
            var nz = e1x * e2y - e1y * e2x
            val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
            nx /= len; ny /= len; nz /= len

            fun v(p: FloatArray, u: Float, vt: Float) {
                out.add(p[0]); out.add(p[1]); out.add(p[2])
                out.add(nx); out.add(ny); out.add(nz)
                out.add(u); out.add(vt)
            }
            v(p0, 0f, 1f); v(p1, 1f, 1f); v(p2, 1f, 0f)
            v(p0, 0f, 1f); v(p2, 1f, 0f); v(p3, 0f, 0f)
        }

        val bFL = floatArrayOf(-0.82f, 0.88f,  0.86f); val bFR = floatArrayOf(0.82f, 0.88f,  0.86f)
        val bRL = floatArrayOf(-0.84f, 0.90f, -1.42f); val bRR = floatArrayOf(0.84f, 0.90f, -1.42f)
        val tFL = floatArrayOf(-0.67f, 1.44f,  0.18f); val tFR = floatArrayOf(0.67f, 1.44f,  0.18f)
        val tRL = floatArrayOf(-0.67f, 1.42f, -0.78f); val tRR = floatArrayOf(0.67f, 1.42f, -0.78f)

        addQuad(bFL, bFR, tFR, tFL)
        addQuad(bRR, bRL, tRL, tRR)
        addQuad(bRL, bFL, tFL, tRL)
        addQuad(bFR, bRR, tRR, tFR)
        addQuad(tFL, tFR, tRR, tRL)
        return GlMesh(out.toFloatArray())
    }

    fun createGroundRing(segments: Int = 60, innerRatio: Float = 0.985f): GlMesh {
        val out = ArrayList<Float>(segments * 6 * 8)

        fun push(x: Float, z: Float, u: Float, v: Float) {
            out.add(x); out.add(0f); out.add(z)
            out.add(0f); out.add(1f); out.add(0f)
            out.add(u); out.add(v)
        }

        for (i in 0 until segments) {
            val a0 = (2.0 * PI * i / segments).toFloat()
            val a1 = (2.0 * PI * (i + 1) / segments).toFloat()
            val c0 = cos(a0); val s0 = sin(a0)
            val c1 = cos(a1); val s1 = sin(a1)
            push(c0 * innerRatio, s0 * innerRatio, 0f, 0f)
            push(c0, s0, 1f, 0f)
            push(c1, s1, 1f, 1f)
            push(c0 * innerRatio, s0 * innerRatio, 0f, 0f)
            push(c1, s1, 1f, 1f)
            push(c1 * innerRatio, s1 * innerRatio, 0f, 1f)
        }
        return GlMesh(out.toFloatArray())
    }

    /**
     * Sculpted human head mesh (true outward CCW winding, natural jaw taper, brow ridge, nose bridge, chin)
     * with frontal planar UV projection so the real face photo maps cleanly onto +Z without stretching.
     */
    fun createSculptedHeadMesh(stacks: Int = 20, slices: Int = 24): GlMesh {
        val out = ArrayList<Float>(stacks * slices * 6 * 8)

        fun evalHead(phi: Float, th: Float): FloatArray {
            val cp = cos(phi)
            val sp = sin(phi)
            val ct = cos(th)
            val st = sin(th)

            var x = cp * ct * 0.44f
            var y = sp * 0.54f
            var z = cp * st * 0.47f

            if (sp < 0f) {
                val jawT = -sp
                x *= (1f - jawT * 0.26f)
                z *= (1f - jawT * 0.12f)
            }
            val front = max(0f, st * cp)
            if (front > 0.1f) {
                val brow = exp(-((sp - 0.26f) * (sp - 0.26f)) / 0.025f) * front
                z += brow * 0.022f
                val noseX = exp(-(ct * ct) / 0.04f)
                val noseY = exp(-((sp + 0.04f) * (sp + 0.04f)) / 0.03f)
                z += noseX * noseY * front * 0.055f
                val chin = exp(-((sp + 0.72f) * (sp + 0.72f)) / 0.035f) * front
                z += chin * 0.025f
            }
            return floatArrayOf(x, y, z)
        }

        fun evalVertex(phi: Float, th: Float): FloatArray {
            val p = evalHead(phi, th)
            val eps = 0.02f
            val pPh1 = evalHead((phi + eps).coerceAtMost((PI * 0.499).toFloat()), th)
            val pPh0 = evalHead((phi - eps).coerceAtLeast((-PI * 0.499).toFloat()), th)
            val pTh1 = evalHead(phi, th + eps)
            val pTh0 = evalHead(phi, th - eps)

            val tPhiX = pPh1[0] - pPh0[0]; val tPhiY = pPh1[1] - pPh0[1]; val tPhiZ = pPh1[2] - pPh0[2]
            val tThX = pTh1[0] - pTh0[0];  val tThY = pTh1[1] - pTh0[1];  val tThZ = pTh1[2] - pTh0[2]

            var nx = tPhiY * tThZ - tPhiZ * tThY
            var ny = tPhiZ * tThX - tPhiX * tThZ
            var nz = tPhiX * tThY - tPhiY * tThX
            val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
            nx /= len; ny /= len; nz /= len

            val u: Float
            val v: Float
            if (p[2] >= -0.02f) {
                u = (0.5f + (p[0] / 0.46f) * 0.46f).coerceIn(0.03f, 0.97f)
                v = (0.5f - (p[1] / 0.56f) * 0.48f).coerceIn(0.03f, 0.97f)
            } else {
                u = if (p[0] >= 0f) 0.97f else 0.03f
                v = (0.5f - (p[1] / 0.56f) * 0.42f).coerceIn(0.06f, 0.94f)
            }
            return floatArrayOf(p[0], p[1], p[2], nx, ny, nz, u, v)
        }

        fun push(vert: FloatArray) {
            for (f in vert) out.add(f)
        }

        for (i in 0 until stacks) {
            val phi0 = (-PI * 0.495 + PI * 0.99 * i / stacks).toFloat()
            val phi1 = (-PI * 0.495 + PI * 0.99 * (i + 1) / stacks).toFloat()
            for (j in 0 until slices) {
                val th0 = (2.0 * PI * j / slices).toFloat()
                val th1 = (2.0 * PI * (j + 1) / slices).toFloat()
                val v00 = evalVertex(phi0, th0)
                val v01 = evalVertex(phi0, th1)
                val v10 = evalVertex(phi1, th0)
                val v11 = evalVertex(phi1, th1)
                push(v00); push(v11); push(v01)
                push(v00); push(v10); push(v11)
            }
        }
        return GlMesh(out.toFloatArray())
    }

    /**
     * Sculpted male shirt torso mesh: broad shoulders, chest fullness, tapered waist,
     * untucked curved shirttail, and closed top/bottom caps (u = 0.50 exact front placket).
     */
    fun createSculptedTorsoMesh(rings: Int = 14, slices: Int = 22): GlMesh {
        val out = ArrayList<Float>((rings * slices * 6 + slices * 6) * 8)

        fun evalTorso(t: Float, s: Float): FloatArray {
            val th = (-PI * 0.5 + 2.0 * PI * s).toFloat()
            val ct = cos(th)
            val st = sin(th)

            val rx = when {
                t < 0.35f -> 0.45f - 0.04f * (t / 0.35f)
                t < 0.85f -> 0.41f + 0.11f * ((t - 0.35f) / 0.50f)
                else -> 0.52f - 0.28f * ((t - 0.85f) / 0.15f)
            }
            val rz = when {
                t < 0.35f -> 0.29f - 0.02f * (t / 0.35f)
                t < 0.78f -> 0.27f + 0.07f * ((t - 0.35f) / 0.43f)
                else -> 0.34f - 0.13f * ((t - 0.78f) / 0.22f)
            }

            val chestBump = if (st > 0f) st * exp(-((t - 0.72f) * (t - 0.72f)) / 0.035f) * 0.032f else 0f
            val hemCurve = if (t < 0.25f) (1f - t / 0.25f) * 0.032f * cos(2f * th) else 0f

            val x = ct * rx
            val y = (t - 0.5f) + hemCurve
            val z = st * (rz + chestBump)
            return floatArrayOf(x, y, z)
        }

        fun evalVertex(t: Float, s: Float): FloatArray {
            val p = evalTorso(t, s)
            val dt = 0.015f
            val ds = 0.01f
            val pT1 = evalTorso((t + dt).coerceAtMost(1f), s)
            val pT0 = evalTorso((t - dt).coerceAtLeast(0f), s)
            val pS1 = evalTorso(t, s + ds)
            val pS0 = evalTorso(t, s - ds)

            val tTx = pT1[0] - pT0[0]; val tTy = pT1[1] - pT0[1]; val tTz = pT1[2] - pT0[2]
            val tSx = pS1[0] - pS0[0]; val tSy = pS1[1] - pS0[1]; val tSz = pS1[2] - pS0[2]

            var nx = tTy * tSz - tTz * tSy
            var ny = tTz * tSx - tTx * tSz
            var nz = tTx * tSy - tTy * tSx
            val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
            nx /= len; ny /= len; nz /= len

            return floatArrayOf(p[0], p[1], p[2], nx, ny, nz, s, 1f - t)
        }

        fun push(vert: FloatArray) {
            for (f in vert) out.add(f)
        }

        fun pushRaw(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, v: Float) {
            out.add(x); out.add(y); out.add(z)
            out.add(nx); out.add(ny); out.add(nz)
            out.add(u); out.add(v)
        }

        for (i in 0 until rings) {
            val t0 = i.toFloat() / rings
            val t1 = (i + 1).toFloat() / rings
            for (j in 0 until slices) {
                val s0 = j.toFloat() / slices
                val s1 = (j + 1).toFloat() / slices
                val v00 = evalVertex(t0, s0)
                val v01 = evalVertex(t0, s1)
                val v10 = evalVertex(t1, s0)
                val v11 = evalVertex(t1, s1)
                push(v00); push(v11); push(v01)
                push(v00); push(v10); push(v11)
            }
        }

        // Closed top & bottom caps (prevents hollow view under hem or collar)
        for (j in 0 until slices) {
            val s0 = j.toFloat() / slices
            val s1 = (j + 1).toFloat() / slices
            val top0 = evalTorso(1f, s0)
            val top1 = evalTorso(1f, s1)
            pushRaw(0f, 0.5f, 0f, 0f, 1f, 0f, 0.1f, 0.05f)
            pushRaw(top0[0], top0[1], top0[2], 0f, 1f, 0f, 0.1f, 0.05f)
            pushRaw(top1[0], top1[1], top1[2], 0f, 1f, 0f, 0.1f, 0.05f)

            val bot0 = evalTorso(0f, s0)
            val bot1 = evalTorso(0f, s1)
            pushRaw(0f, -0.5f, 0f, 0f, -1f, 0f, 0.1f, 0.95f)
            pushRaw(bot1[0], bot1[1], bot1[2], 0f, -1f, 0f, 0.1f, 0.95f)
            pushRaw(bot0[0], bot0[1], bot0[2], 0f, -1f, 0f, 0.1f, 0.95f)
        }

        return GlMesh(out.toFloatArray())
    }
}
