package net.rizkiepratama.hako

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.Matrix
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.rizkiepratama.hako.engine.MonitorAssistShader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** Exercises the production fragment shader on actual GLES pixels, without a USB camera. */
@RunWith(AndroidJUnit4::class)
class MonitorAssistShaderTest {
    private val size = 64
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0

    @Before fun createOffscreenRenderer() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        assertTrue(EGL14.eglInitialize(display, version, 0, version, 1))
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        assertTrue(EGL14.eglChooseConfig(display, intArrayOf(
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE
        ), 0, configs, 0, 1, count, 0))
        assertTrue("An RGBA8 GLES2 pbuffer must be available", count[0] > 0)
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        surface = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(
            EGL14.EGL_WIDTH, size, EGL14.EGL_HEIGHT, size, EGL14.EGL_NONE
        ), 0)
        assertTrue(EGL14.eglMakeCurrent(display, surface, surface, context))
        val vertex = compile(GLES20.GL_VERTEX_SHADER, MonitorAssistShader.vertex)
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, MonitorAssistShader.fragment(false))
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        assertEquals(GLES20.glGetProgramInfoLog(program), GLES20.GL_TRUE, linked[0])
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
    }

    @After fun releaseOffscreenRenderer() {
        if (program != 0) GLES20.glDeleteProgram(program)
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            EGL14.eglReleaseThread()
        }
    }

    @Test fun disabledAssistsPreserveSourceIncludingHighlightsAndEdges() {
        val source = pixels { x, y -> intArrayOf(x * 255 / 63, y * 255 / 63, if (x < 32) 0 else 255) }
        assertPixelsClose(source, render(source))
    }

    @Test fun zebraLeavesDarkAndMidtonePixelsUnmodified() {
        val source = pixels { x, _ -> val v = if (x < 32) 12 else 180; intArrayOf(v, v, v) }
        assertPixelsClose(source, render(source, zebra = true))
    }

    @Test fun zebraStripesOnlyRegionAboveLuminanceThreshold() {
        val source = pixels { x, _ -> val v = if (x < 32) 128 else 250; intArrayOf(v, v, v) }
        val output = render(source, zebra = true)
        val brightValues = mutableListOf<Int>()
        for (y in 4 until size - 4) for (x in 4 until size - 4) {
            val offset = (y * size + x) * 4
            if (x < 28) assertEquals(128.0, channel(output, offset).toDouble(), 2.0)
            if (x > 36) brightValues += channel(output, offset)
        }
        assertTrue("Highlights must contain dark zebra stripes", brightValues.any { it < 150 })
        assertTrue("Highlights must also contain light zebra stripes", brightValues.any { it > 235 })
    }

    @Test fun zebraUsesLuminanceRatherThanAnyClippedColorChannel() {
        val source = pixels { x, _ -> if (x < 32) intArrayOf(255, 0, 0) else intArrayOf(0, 255, 0) }
        assertPixelsClose(source, render(source, zebra = true))
    }

    @Test fun focusPeakingLeavesFlatFieldsUnmodified() {
        val source = pixels { _, _ -> intArrayOf(140, 90, 200) }
        assertPixelsClose(source, render(source, peaking = true))
    }

    @Test fun focusPeakingMarksSharpEdgesWithSelectedColor() {
        val source = pixels { x, _ -> val v = if (x < 32) 0 else 255; intArrayOf(v, v, v) }
        val output = render(source, peaking = true)
        for (y in 8 until size - 8) {
            assertTrue("Sharp transition must contain a red focus marker", (30..33).any { x ->
                val offset = (y * size + x) * 4
                channel(output, offset) > 220 && channel(output, offset + 1) < 40 && channel(output, offset + 2) < 40
            })
            for (x in listOf(8, 55)) {
                val offset = (y * size + x) * 4
                for (c in 0..2) assertEquals(channel(source, offset + c), channel(output, offset + c))
            }
        }
    }

    @Test fun focusPeakingDoesNotMarkGradualBlurredTransition() {
        val source = pixels { x, _ ->
            val v = ((x - 16) * 255 / 32).coerceIn(0, 255)
            intArrayOf(v, v, v)
        }
        assertPixelsClose(source, render(source, peaking = true, peakingThreshold = .2f))
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        assertEquals(GLES20.glGetShaderInfoLog(shader), GLES20.GL_TRUE, compiled[0])
        return shader
    }

    private fun pixels(color: (Int, Int) -> IntArray): ByteArray = ByteArray(size * size * 4).also { result ->
        for (y in 0 until size) for (x in 0 until size) {
            val rgb = color(x, y)
            val offset = (y * size + x) * 4
            for (c in 0..2) result[offset + c] = rgb[c].toByte()
            result[offset + 3] = 255.toByte()
        }
    }

    private fun render(source: ByteArray, zebra: Boolean = false, peaking: Boolean = false,
        peakingThreshold: Float = .16f): ByteArray {
        GLES20.glUseProgram(program)
        GLES20.glViewport(0, 0, size, size)
        GLES20.glDisable(GLES20.GL_DITHER)
        GLES20.glDisable(GLES20.GL_BLEND)
        val texture = IntArray(1)
        GLES20.glGenTextures(1, texture, 0)
        try {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            val input = ByteBuffer.allocateDirect(source.size).put(source).apply { position(0) }
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, size, size,
                0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, input)
            fun uniform(name: String) = GLES20.glGetUniformLocation(program, name)
            GLES20.glUniform1i(uniform("videoTexture"), 0)
            GLES20.glUniform2f(uniform("texelSize"), 1f / size, 1f / size)
            GLES20.glUniform1f(uniform("zebraThreshold"), .95f)
            GLES20.glUniform1f(uniform("peakingThreshold"), peakingThreshold)
            GLES20.glUniform1i(uniform("zebraEnabled"), if (zebra) 1 else 0)
            GLES20.glUniform1i(uniform("peakingEnabled"), if (peaking) 1 else 0)
            GLES20.glUniform3f(uniform("peakingColor"), 1f, 0f, 0f)
            val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
            GLES20.glUniformMatrix4fv(uniform("textureTransform"), 1, false, identity, 0)
            val positions = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
            val coordinates = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
            fun attribute(name: String, values: FloatArray): Int {
                val location = GLES20.glGetAttribLocation(program, name)
                assertTrue("Missing shader attribute: $name", location >= 0)
                val buffer = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
                    .asFloatBuffer().put(values).apply { position(0) }
                GLES20.glEnableVertexAttribArray(location)
                GLES20.glVertexAttribPointer(location, 2, GLES20.GL_FLOAT, false, 0, buffer)
                return location
            }
            val position = attribute("position", positions)
            val coordinate = attribute("inputTextureCoordinate", coordinates)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            val output = ByteBuffer.allocateDirect(source.size)
            GLES20.glReadPixels(0, 0, size, size, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, output)
            assertEquals("GL rendering error", GLES20.GL_NO_ERROR, GLES20.glGetError())
            GLES20.glDisableVertexAttribArray(position)
            GLES20.glDisableVertexAttribArray(coordinate)
            return ByteArray(source.size).also { output.position(0); output.get(it) }
        } finally {
            GLES20.glDeleteTextures(1, texture, 0)
        }
    }

    private fun channel(bytes: ByteArray, offset: Int) = bytes[offset].toInt() and 255

    private fun assertPixelsClose(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.size, actual.size)
        for (offset in expected.indices) {
            assertTrue("Changed pixel channel at byte $offset: ${channel(expected, offset)} -> ${channel(actual, offset)}",
                abs(channel(expected, offset) - channel(actual, offset)) <= 2)
        }
    }
}
