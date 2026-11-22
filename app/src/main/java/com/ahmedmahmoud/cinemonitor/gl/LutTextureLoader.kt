package com.ahmedmahmoud.cinemonitor.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

object LutTextureLoader {

    fun uploadToGpu(lut: CubeLutData): Int {
        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]

        if (textureId == 0) {
            return 0
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)

        lut.rgbBuffer.position(0)
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D,
            0,
            GLES30.GL_RGB8,
            lut.size,
            lut.size,
            lut.size,
            0,
            GLES30.GL_RGB,
            GLES30.GL_FLOAT,
            lut.rgbBuffer
        )

        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
        return textureId
    }

    fun createBuiltInCinemaLut(size: Int = 33): CubeLutData {
        val total = size * size * size
        val buffer = ByteBuffer
            .allocateDirect(total * 3 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        val scale = 1.0f / (size - 1)
        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    val rf = r * scale
                    val gf = g * scale
                    val bf = b * scale

                    val cr = smoothContrast(rf)
                    val cg = smoothContrast(gf)
                    val cb = smoothContrast(bf)

                    val outR = (cr * 1.03f + (1f - cr) * 0.01f).coerceIn(0f, 1f)
                    val outG = (cg * 1.00f).coerceIn(0f, 1f)
                    val outB = (cb * 0.94f + (1f - cb) * 0.035f).coerceIn(0f, 1f)

                    buffer.put(outR)
                    buffer.put(outG)
                    buffer.put(outB)
                }
            }
        }
        buffer.position(0)
        return CubeLutData("CineFilm Warm Rec.709", size, floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 1f, 1f), buffer)
    }

    private fun smoothContrast(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }
}
