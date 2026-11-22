package com.ahmedmahmoud.cinemonitor.gl

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

data class CubeLutData(
    val title: String,
    val size: Int,
    val domainMin: FloatArray,
    val domainMax: FloatArray,
    val rgbBuffer: FloatBuffer
)

object CubeLutParser {

    fun parse(inputStream: InputStream): CubeLutData {
        var title = "Custom LUT"
        var lutSize = 0
        val domainMin = floatArrayOf(0.0f, 0.0f, 0.0f)
        val domainMax = floatArrayOf(1.0f, 1.0f, 1.0f)

        var floatBuffer: FloatBuffer? = null
        var expectedEntries = 0
        var parsedEntries = 0

        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            while (true) {
                val rawLine = reader.readLine() ?: break
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

                when {
                    trimmed.startsWith("TITLE") -> {
                        title = trimmed.substringAfter("TITLE").trim().removeSurrounding(""")
                    }
                    trimmed.startsWith("LUT_3D_SIZE") -> {
                        lutSize = trimmed.substringAfter("LUT_3D_SIZE").trim().toInt()
                        require(lutSize in 2..128) { "Invalid LUT size: $lutSize" }
                        expectedEntries = lutSize * lutSize * lutSize
                        floatBuffer = ByteBuffer
                            .allocateDirect(expectedEntries * 3 * 4)
                            .order(ByteOrder.nativeOrder())
                            .asFloatBuffer()
                    }
                    trimmed.startsWith("DOMAIN_MIN") -> {
                        val parts = trimmed.substringAfter("DOMAIN_MIN").trim().split(Regex("\s+"))
                        if (parts.size >= 3) {
                            domainMin[0] = parts[0].toFloat()
                            domainMin[1] = parts[1].toFloat()
                            domainMin[2] = parts[2].toFloat()
                        }
                    }
                    trimmed.startsWith("DOMAIN_MAX") -> {
                        val parts = trimmed.substringAfter("DOMAIN_MAX").trim().split(Regex("\s+"))
                        if (parts.size >= 3) {
                            domainMax[0] = parts[0].toFloat()
                            domainMax[1] = parts[1].toFloat()
                            domainMax[2] = parts[2].toFloat()
                        }
                    }
                    trimmed.startsWith("LUT_1D_SIZE") -> {
                        throw IllegalArgumentException("1D LUT is not supported; please use a 3D .cube LUT.")
                    }
                    else -> {
                        val buf = floatBuffer ?: continue
                        if (parsedEntries >= expectedEntries) continue
                        val parts = trimmed.split(Regex("\s+"))
                        if (parts.size >= 3) {
                            val r = parts[0].toFloatOrNull() ?: continue
                            val g = parts[1].toFloatOrNull() ?: continue
                            val b = parts[2].toFloatOrNull() ?: continue
                            buf.put(r)
                            buf.put(g)
                            buf.put(b)
                            parsedEntries++
                        }
                    }
                }
            }
        }

        val finalBuffer = floatBuffer
            ?: throw IllegalArgumentException("Missing LUT_3D_SIZE header in .cube file.")

        require(parsedEntries == expectedEntries) {
            "Incomplete LUT entries: expected $expectedEntries, got $parsedEntries"
        }

        finalBuffer.position(0)
        return CubeLutData(title, lutSize, domainMin, domainMax, finalBuffer)
    }
}
