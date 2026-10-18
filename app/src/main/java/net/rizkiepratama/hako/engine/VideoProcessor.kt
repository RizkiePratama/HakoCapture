package net.rizkiepratama.hako.engine

import android.content.Context
import android.opengl.GLES31
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VideoProcessor(private val context: Context) {
    private var computeProgram = 0
    private var histogramBuffer = 0
    private val bins = IntArray(256 * 4)

    fun init() {
        // Initialize OpenGL ES 3.1 context and shaders
        // This would normally be called from an EGL thread (e.g. GLSurfaceView.Renderer)
        Log.d("VideoProcessor", "Initializing OpenGL Processor")
        initHistogramBuffer()
    }

    private fun initHistogramBuffer() {
        val buffer = IntArray(1)
        GLES31.glGenBuffers(1, buffer, 0)
        histogramBuffer = buffer[0]
        GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, histogramBuffer)
        GLES31.glBufferData(
            GLES31.GL_SHADER_STORAGE_BUFFER,
            256 * 4 * 4, // 256 bins * 4 channels * 4 bytes per uint
            null,
            GLES31.GL_DYNAMIC_DRAW
        )
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, histogramBuffer)
    }

    fun processFrame(textureId: Int, width: Int, height: Int) {
        GLES31.glUseProgram(computeProgram)
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, textureId)
        
        // Clear bins
        GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, histogramBuffer)
        val zeroBuffer = ByteBuffer.allocateDirect(256 * 4 * 4).order(ByteOrder.nativeOrder())
        GLES31.glBufferSubData(GLES31.GL_SHADER_STORAGE_BUFFER, 0, 256 * 4 * 4, zeroBuffer)

        GLES31.glDispatchCompute(width / 16, height / 16, 1)
        GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT)

        // Read results back
        val resultBuffer = GLES31.glMapBufferRange(
            GLES31.GL_SHADER_STORAGE_BUFFER,
            0,
            256 * 4 * 4,
            GLES31.GL_MAP_READ_BIT
        ) as ByteBuffer
        resultBuffer.order(ByteOrder.nativeOrder())
        resultBuffer.asIntBuffer().get(bins)
        GLES31.glUnmapBuffer(GLES31.GL_SHADER_STORAGE_BUFFER)
    }

    fun getHistogramData(): IntArray = bins
}