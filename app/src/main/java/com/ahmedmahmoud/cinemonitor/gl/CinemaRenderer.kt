package com.ahmedmahmoud.cinemonitor.gl

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CinemaRenderer : GLSurfaceView.Renderer {

    private var programId = 0
    private var videoTextureId = 0
    private var hasInitializedTextureStorage = false
    private var currentTexWidth = 0
    private var currentTexHeight = 0
    private var lastReportedAspect = 16f / 9f

    private val pendingBitmapRef = AtomicReference<Bitmap?>(null)

    @Volatile
    var onBitmapConsumed: ((Bitmap) -> Unit)? = null

    @Volatile
    var onVideoAspectChanged: ((Float) -> Unit)? = null

    private var uCameraTextureLoc = -1
    private var uUseTestPatternLoc = -1
    private var uFlipLoc = -1
    private var uLutTextureLoc = -1
    private var uLutSizeLoc = -1
    private var uDomainMinLoc = -1
    private var uDomainMaxLoc = -1
    private var uLutIntensityLoc = -1
    private var uFalseColorModeLoc = -1
    private var uPeakingModeLoc = -1
    private var uPeakingThresholdLoc = -1
    private var uPeakingColorLoc = -1
    private var uZebraModeLoc = -1
    private var uZebraThresholdLoc = -1
    private var uMonochromeModeLoc = -1
    private var uTexelSizeLoc = -1
    private var uTimeLoc = -1

    @Volatile private var activeLutData: CubeLutData = LutTextureLoader.createBuiltInCinemaLut(33)
    private var currentLutTexId = 0
    private var currentLutSize = 33.0f
    private var domainMin = floatArrayOf(0f, 0f, 0f)
    private var domainMax = floatArrayOf(1f, 1f, 1f)

    @Volatile private var lutIntensity = 0.0f
    @Volatile private var falseColorEnabled = false
    @Volatile private var peakingEnabled = false
    @Volatile private var zebraEnabled = false
    @Volatile private var zebraThreshold = 0.90f
    @Volatile private var monochromeEnabled = false
    @Volatile private var hasLiveVideoFrame = false

    @Volatile private var flipHorizontal = false
    @Volatile private var flipVertical = false
    @Volatile private var anamorphicDesqueeze = 1.0f

    private var viewWidth = 1920
    private var viewHeight = 1080
    private val startTimeNs = System.nanoTime()

    private val quadVertices: FloatBuffer = ByteBuffer.allocateDirect(16 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(
            floatArrayOf(
                -1.0f, -1.0f, 0.0f, 1.0f,
                 1.0f, -1.0f, 1.0f, 1.0f,
                -1.0f,  1.0f, 0.0f, 0.0f,
                 1.0f,  1.0f, 1.0f, 0.0f
            )
        ).apply { position(0) }

    fun submitUsbEvfFrame(bitmap: Bitmap) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return
        val old = pendingBitmapRef.getAndSet(bitmap)
        if (old != null && old !== bitmap) {
            disposeOrReturnBitmap(old)
        }
        hasLiveVideoFrame = true
    }

    private fun disposeOrReturnBitmap(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        val callback = onBitmapConsumed
        if (callback != null) {
            callback.invoke(bitmap)
        } else {
            bitmap.recycle()
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.04f, 0.04f, 0.04f, 1f)
        hasInitializedTextureStorage = false

        programId = createShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        cacheUniformLocations()

        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        videoTextureId = textures[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, videoTextureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        val lutToRestore = activeLutData
        currentLutTexId = LutTextureLoader.uploadToGpu(lutToRestore)
        currentLutSize = lutToRestore.size.toFloat()
        domainMin = lutToRestore.domainMin
        domainMax = lutToRestore.domainMax
    }

    private fun cacheUniformLocations() {
        if (programId == 0) return
        uCameraTextureLoc = GLES30.glGetUniformLocation(programId, "uCameraTexture")
        uUseTestPatternLoc = GLES30.glGetUniformLocation(programId, "uUseTestPattern")
        uFlipLoc = GLES30.glGetUniformLocation(programId, "uFlip")
        uLutTextureLoc = GLES30.glGetUniformLocation(programId, "uLutTexture")
        uLutSizeLoc = GLES30.glGetUniformLocation(programId, "uLutSize")
        uDomainMinLoc = GLES30.glGetUniformLocation(programId, "uDomainMin")
        uDomainMaxLoc = GLES30.glGetUniformLocation(programId, "uDomainMax")
        uLutIntensityLoc = GLES30.glGetUniformLocation(programId, "uLutIntensity")
        uFalseColorModeLoc = GLES30.glGetUniformLocation(programId, "uFalseColorMode")
        uPeakingModeLoc = GLES30.glGetUniformLocation(programId, "uPeakingMode")
        uPeakingThresholdLoc = GLES30.glGetUniformLocation(programId, "uPeakingThreshold")
        uPeakingColorLoc = GLES30.glGetUniformLocation(programId, "uPeakingColor")
        uZebraModeLoc = GLES30.glGetUniformLocation(programId, "uZebraMode")
        uZebraThresholdLoc = GLES30.glGetUniformLocation(programId, "uZebraThreshold")
        uMonochromeModeLoc = GLES30.glGetUniformLocation(programId, "uMonochromeMode")
        uTexelSizeLoc = GLES30.glGetUniformLocation(programId, "uTexelSize")
        uTimeLoc = GLES30.glGetUniformLocation(programId, "uTime")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width.coerceAtLeast(1)
        viewHeight = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, viewWidth, viewHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, videoTextureId)

        val latestFrame = pendingBitmapRef.getAndSet(null)
        if (latestFrame != null && !latestFrame.isRecycled) {
            if (!hasInitializedTextureStorage || latestFrame.width != currentTexWidth || latestFrame.height != currentTexHeight) {
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, latestFrame, 0)
                currentTexWidth = latestFrame.width
                currentTexHeight = latestFrame.height
                hasInitializedTextureStorage = true

                val newBaseAspect = currentTexWidth.coerceAtLeast(1).toFloat() / currentTexHeight.coerceAtLeast(1).toFloat()
                if (kotlin.math.abs(newBaseAspect - lastReportedAspect) > 0.001f) {
                    lastReportedAspect = newBaseAspect
                    onVideoAspectChanged?.invoke(newBaseAspect)
                }
            } else {
                GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, latestFrame)
            }
            disposeOrReturnBitmap(latestFrame)
        }

        val baseAspect = if (hasLiveVideoFrame && currentTexWidth > 0 && currentTexHeight > 0) {
            currentTexWidth.toFloat() / currentTexHeight.toFloat()
        } else {
            16f / 9f
        }
        val frameAspect = baseAspect * anamorphicDesqueeze

        val safeW = viewWidth.coerceAtLeast(1)
        val safeH = viewHeight.coerceAtLeast(1)
        val screenAspect = safeW.toFloat() / safeH.toFloat()
        if (screenAspect > frameAspect) {
            val vpW = (safeH * frameAspect).toInt().coerceAtLeast(1)
            val vpX = (safeW - vpW) / 2
            GLES30.glViewport(vpX, 0, vpW, safeH)
        } else {
            val vpH = (safeW / frameAspect).toInt().coerceAtLeast(1)
            val vpY = (safeH - vpH) / 2
            GLES30.glViewport(0, vpY, safeW, vpH)
        }

        if (programId == 0) return
        GLES30.glUseProgram(programId)

        GLES30.glUniform1i(uCameraTextureLoc, 0)
        GLES30.glUniform1i(uUseTestPatternLoc, if (hasLiveVideoFrame) 0 else 1)
        GLES30.glUniform2f(
            uFlipLoc,
            if (flipHorizontal) 1.0f else 0.0f,
            if (flipVertical) 1.0f else 0.0f
        )

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, currentLutTexId)
        GLES30.glUniform1i(uLutTextureLoc, 1)
        GLES30.glUniform1f(uLutSizeLoc, currentLutSize)
        GLES30.glUniform3fv(uDomainMinLoc, 1, domainMin, 0)
        GLES30.glUniform3fv(uDomainMaxLoc, 1, domainMax, 0)
        GLES30.glUniform1f(uLutIntensityLoc, lutIntensity)

        val elapsedSec = (System.nanoTime() - startTimeNs) / 1_000_000_000.0f
        GLES30.glUniform1i(uFalseColorModeLoc, if (falseColorEnabled) 1 else 0)
        GLES30.glUniform1i(uPeakingModeLoc, if (peakingEnabled) 1 else 0)
        GLES30.glUniform1f(uPeakingThresholdLoc, 0.14f)
        GLES30.glUniform3f(uPeakingColorLoc, 1.0f, 0.15f, 0.15f)
        GLES30.glUniform1i(uZebraModeLoc, if (zebraEnabled) 1 else 0)
        GLES30.glUniform1f(uZebraThresholdLoc, zebraThreshold)
        GLES30.glUniform1i(uMonochromeModeLoc, if (monochromeEnabled) 1 else 0)
        val texW = currentTexWidth.coerceAtLeast(1).toFloat()
        val texH = currentTexHeight.coerceAtLeast(1).toFloat()
        GLES30.glUniform2f(uTexelSizeLoc, 1.0f / texW, 1.0f / texH)
        GLES30.glUniform1f(uTimeLoc, elapsedSec)

        quadVertices.position(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, quadVertices)
        GLES30.glEnableVertexAttribArray(0)

        quadVertices.position(2)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, quadVertices)
        GLES30.glEnableVertexAttribArray(1)

        runCatching { GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4) }
    }

    fun updateLutTexture(lutData: CubeLutData) {
        activeLutData = lutData
        if (currentLutTexId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(currentLutTexId), 0)
        }
        currentLutTexId = LutTextureLoader.uploadToGpu(lutData)
        currentLutSize = lutData.size.toFloat()
        domainMin = lutData.domainMin
        domainMax = lutData.domainMax
    }

    fun onLiveVideoDisconnected() {
        hasLiveVideoFrame = false
        hasInitializedTextureStorage = false
        currentTexWidth = 0
        currentTexHeight = 0
        lastReportedAspect = 16f / 9f
        onVideoAspectChanged?.invoke(16f / 9f)
        val stale = pendingBitmapRef.getAndSet(null)
        if (stale != null) {
            disposeOrReturnBitmap(stale)
        }
    }

    fun setLutIntensity(intensity: Float) { lutIntensity = intensity.coerceIn(0f, 1f) }
    fun setFalseColorEnabled(enabled: Boolean) { falseColorEnabled = enabled }
    fun setPeakingEnabled(enabled: Boolean) { peakingEnabled = enabled }
    fun setZebraEnabled(enabled: Boolean) { zebraEnabled = enabled }
    fun setZebraThreshold(threshold: Float) { zebraThreshold = threshold.coerceIn(0.5f, 1.0f) }
    fun setMonochromeEnabled(enabled: Boolean) { monochromeEnabled = enabled }
    fun setFlip(horizontal: Boolean, vertical: Boolean) {
        flipHorizontal = horizontal
        flipVertical = vertical
    }
    fun setAnamorphicDesqueeze(factor: Float) {
        anamorphicDesqueeze = factor.coerceIn(1.0f, 2.0f)
    }

    private fun createShaderProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
        if (vs == 0 || fs == 0) return 0

        val prog = GLES30.glCreateProgram()
        if (prog == 0) return 0

        GLES30.glAttachShader(prog, vs)
        GLES30.glAttachShader(prog, fs)
        GLES30.glLinkProgram(prog)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, linkStatus, 0)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)

        if (linkStatus[0] == 0) {
            GLES30.glDeleteProgram(prog)
            return 0
        }
        return prog
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        if (shader == 0) return 0
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    companion object {
        private const val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec4 aPosition;
            layout(location = 1) in vec4 aTexCoord;
            uniform vec2 uFlip;
            out vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vec2 uv = aTexCoord.xy;
                if (uFlip.x > 0.5) uv.x = 1.0 - uv.x;
                if (uFlip.y > 0.5) uv.y = 1.0 - uv.y;
                vTexCoord = uv;
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            precision highp sampler3D;

            in vec2 vTexCoord;
            out vec4 fragColor;

            uniform sampler2D uCameraTexture;
            uniform sampler3D uLutTexture;
            uniform float uLutSize;
            uniform vec3 uDomainMin;
            uniform vec3 uDomainMax;
            uniform float uLutIntensity;

            uniform int uUseTestPattern;
            uniform int uFalseColorMode;
            uniform int uPeakingMode;
            uniform float uPeakingThreshold;
            uniform vec3 uPeakingColor;
            uniform int uZebraMode;
            uniform float uZebraThreshold;
            uniform int uMonochromeMode;
            uniform vec2 uTexelSize;
            uniform float uTime;

            float getLuminance(vec3 color) {
                return dot(color, vec3(0.2126, 0.7152, 0.0722));
            }

            vec3 sampleCinemaTestPattern(vec2 uv) {
                if (uv.y < 0.70) {
                    int bar = int(floor(uv.x * 7.0));
                    if (bar == 0) return vec3(0.75, 0.75, 0.75);
                    if (bar == 1) return vec3(0.75, 0.75, 0.0);
                    if (bar == 2) return vec3(0.0,  0.75, 0.75);
                    if (bar == 3) return vec3(0.0,  0.75, 0.0);
                    if (bar == 4) return vec3(0.75, 0.0,  0.75);
                    if (bar == 5) return vec3(0.75, 0.0,  0.0);
                    return vec3(0.0, 0.0, 0.75);
                } else if (uv.y < 0.86) {
                    return vec3(uv.x);
                } else {
                    if (uv.x < 0.35) return vec3(0.76, 0.54, 0.44);
                    if (uv.x < 0.70) return vec3(0.45, 0.45, 0.45);
                    return vec3(0.04, 0.04, 0.04);
                }
            }

            vec3 applyFalseColor(float luma) {
                if (luma < 0.02) return vec3(0.25, 0.0, 0.5);
                if (luma < 0.15) return vec3(0.0, 0.2, 1.0);
                if (luma >= 0.41 && luma <= 0.48) return vec3(0.0, 0.85, 0.2);
                if (luma >= 0.52 && luma <= 0.58) return vec3(1.0, 0.45, 0.7);
                if (luma > 0.78 && luma <= 0.94) return vec3(1.0, 0.9, 0.0);
                if (luma > 0.94) return vec3(1.0, 0.0, 0.0);
                return vec3(luma);
            }

            void main() {
                vec3 color = (uUseTestPattern == 1)
                    ? sampleCinemaTestPattern(vTexCoord)
                    : texture(uCameraTexture, vTexCoord).rgb;

                if (uLutIntensity > 0.0) {
                    vec3 domainSpan = max(uDomainMax - uDomainMin, vec3(0.0001));
                    vec3 normColor = clamp((color - uDomainMin) / domainSpan, 0.0, 1.0);
                    vec3 lutCoord = normColor * ((uLutSize - 1.0) / uLutSize) + (0.5 / uLutSize);
                    vec3 lutColor = texture(uLutTexture, lutCoord).rgb;
                    color = mix(color, lutColor, uLutIntensity);
                }

                float luma = getLuminance(color);

                if (uMonochromeMode == 1) {
                    color = vec3(luma);
                }

                if (uFalseColorMode == 1) {
                    color = applyFalseColor(luma);
                }

                if (uZebraMode == 1 && luma >= uZebraThreshold) {
                    float stripe = sin((gl_FragCoord.x + gl_FragCoord.y) * 0.4 - uTime * 8.0);
                    if (stripe > 0.0) {
                        color = mix(color, vec3(1.0, 0.1, 0.1), 0.65);
                    }
                }

                if (uPeakingMode == 1) {
                    if (uUseTestPattern == 1) {
                        float edge = abs(dFdx(luma)) + abs(dFdy(luma));
                        if (edge > 0.08) color = uPeakingColor;
                    } else {
                        vec2 texel = uTexelSize;
                        float tl = getLuminance(texture(uCameraTexture, vTexCoord + vec2(-texel.x,  texel.y)).rgb);
                        float tr = getLuminance(texture(uCameraTexture, vTexCoord + vec2( texel.x,  texel.y)).rgb);
                        float bl = getLuminance(texture(uCameraTexture, vTexCoord + vec2(-texel.x, -texel.y)).rgb);
                        float br = getLuminance(texture(uCameraTexture, vTexCoord + vec2( texel.x, -texel.y)).rgb);
                        float edge = length(vec2((tr + br) - (tl + bl), (tl + tr) - (bl + br)));
                        if (edge > uPeakingThreshold) {
                            color = uPeakingColor;
                        }
                    }
                }

                fragColor = vec4(color, 1.0);
            }
        """
    }
}
