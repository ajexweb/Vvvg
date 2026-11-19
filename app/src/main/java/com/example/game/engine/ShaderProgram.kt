package com.example.game.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import androidx.annotation.DrawableRes
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class CityShaderProgram {
    val programId: Int

    val uMvpMatrixLoc: Int
    val uModelMatrixLoc: Int
    val uTexScaleLoc: Int
    val uColorTintLoc: Int
    val uEmissiveLoc: Int
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
                vec3 baseColor = texel.rgb * uColorTint.rgb;
                vec3 n = normalize(vWorldNormal);
                vec3 l = normalize(uLightDir);
                float sunDiff = max(dot(n, l), 0.0);
                float hemi = n.y * 0.5 + 0.5;
                vec3 ambient = mix(vec3(0.24, 0.27, 0.34), vec3(0.55, 0.62, 0.72), hemi);
                vec3 sunColor = vec3(1.0, 0.93, 0.80) * sunDiff * 0.88;
                vec3 lit = baseColor * (ambient + sunColor) + baseColor * uEmissive;

                float dist = length(vWorldPos - uCameraPos);
                float fogFactor = clamp((dist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
                vec3 finalRgb = mix(lit, uFogColor, fogFactor);
                gl_FragColor = vec4(finalRgb, texel.a * uColorTint.a);
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
}

object GlMeshFactory {
    // Interleaved: pos(3) + normal(3) + uv(2) = 8 floats per vertex
    const val STRIDE_BYTES = 8 * 4

    fun createUnitCubeBuffer(): Pair<FloatBuffer, Int> {
        val v = floatArrayOf(
            // Front (+Z)
            -0.5f, -0.5f, 0.5f, 0f, 0f, 1f, 0f, 1f,
             0.5f, -0.5f, 0.5f, 0f, 0f, 1f, 1f, 1f,
             0.5f,  0.5f, 0.5f, 0f, 0f, 1f, 1f, 0f,
            -0.5f, -0.5f, 0.5f, 0f, 0f, 1f, 0f, 1f,
             0.5f,  0.5f, 0.5f, 0f, 0f, 1f, 1f, 0f,
            -0.5f,  0.5f, 0.5f, 0f, 0f, 1f, 0f, 0f,
            // Back (-Z)
             0.5f, -0.5f, -0.5f, 0f, 0f, -1f, 0f, 1f,
            -0.5f, -0.5f, -0.5f, 0f, 0f, -1f, 1f, 1f,
            -0.5f,  0.5f, -0.5f, 0f, 0f, -1f, 1f, 0f,
             0.5f, -0.5f, -0.5f, 0f, 0f, -1f, 0f, 1f,
            -0.5f,  0.5f, -0.5f, 0f, 0f, -1f, 1f, 0f,
             0.5f,  0.5f, -0.5f, 0f, 0f, -1f, 0f, 0f,
            // Left (-X)
            -0.5f, -0.5f, -0.5f, -1f, 0f, 0f, 0f, 1f,
            -0.5f, -0.5f,  0.5f, -1f, 0f, 0f, 1f, 1f,
            -0.5f,  0.5f,  0.5f, -1f, 0f, 0f, 1f, 0f,
            -0.5f, -0.5f, -0.5f, -1f, 0f, 0f, 0f, 1f,
            -0.5f,  0.5f,  0.5f, -1f, 0f, 0f, 1f, 0f,
            -0.5f,  0.5f, -0.5f, -1f, 0f, 0f, 0f, 0f,
            // Right (+X)
             0.5f, -0.5f,  0.5f, 1f, 0f, 0f, 0f, 1f,
             0.5f, -0.5f, -0.5f, 1f, 0f, 0f, 1f, 1f,
             0.5f,  0.5f, -0.5f, 1f, 0f, 0f, 1f, 0f,
             0.5f, -0.5f,  0.5f, 1f, 0f, 0f, 0f, 1f,
             0.5f,  0.5f, -0.5f, 1f, 0f, 0f, 1f, 0f,
             0.5f,  0.5f,  0.5f, 1f, 0f, 0f, 0f, 0f,
            // Top (+Y)
            -0.5f, 0.5f,  0.5f, 0f, 1f, 0f, 0f, 1f,
             0.5f, 0.5f,  0.5f, 0f, 1f, 0f, 1f, 1f,
             0.5f, 0.5f, -0.5f, 0f, 1f, 0f, 1f, 0f,
            -0.5f, 0.5f,  0.5f, 0f, 1f, 0f, 0f, 1f,
             0.5f, 0.5f, -0.5f, 0f, 1f, 0f, 1f, 0f,
            -0.5f, 0.5f, -0.5f, 0f, 1f, 0f, 0f, 0f,
            // Bottom (-Y)
            -0.5f, -0.5f, -0.5f, 0f, -1f, 0f, 0f, 1f,
             0.5f, -0.5f, -0.5f, 0f, -1f, 0f, 1f, 1f,
             0.5f, -0.5f,  0.5f, 0f, -1f, 0f, 1f, 0f,
            -0.5f, -0.5f, -0.5f, 0f, -1f, 0f, 0f, 1f,
             0.5f, -0.5f,  0.5f, 0f, -1f, 0f, 1f, 0f,
            -0.5f, -0.5f,  0.5f, 0f, -1f, 0f, 0f, 0f
        )
        return toBuffer(v) to 36
    }

    fun createGroundQuadBuffer(): Pair<FloatBuffer, Int> {
        val v = floatArrayOf(
            -0.5f, 0f,  0.5f, 0f, 1f, 0f, 0f, 1f,
             0.5f, 0f,  0.5f, 0f, 1f, 0f, 1f, 1f,
             0.5f, 0f, -0.5f, 0f, 1f, 0f, 1f, 0f,
            -0.5f, 0f,  0.5f, 0f, 1f, 0f, 0f, 1f,
             0.5f, 0f, -0.5f, 0f, 1f, 0f, 1f, 0f,
            -0.5f, 0f, -0.5f, 0f, 1f, 0f, 0f, 0f
        )
        return toBuffer(v) to 6
    }

    private fun toBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data)
                position(0)
            }
    }
}
